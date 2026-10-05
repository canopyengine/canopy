package io.canopy.engine.commands

import kotlin.reflect.KProperty

/**
 * Required positional command argument. [invoke] is the canonical read; Kotlin delegation forwards to it.
 * Reads resolve the current validated invocation on the lifecycle thread and fail outside command execution.
 * Arguments are read-only and do not establish reactive subscriptions or retain previous submitted values.
 */
class CommandArgument<T> internal constructor(
    /** Unique argument name used in generated usage and validation messages. */
    val name: String,
    /** Human-readable accepted type/value description used in help. */
    val typeName: String,
    private val parse: (String) -> T,
    private val validators: List<Pair<String, (T) -> Boolean>>,
) {
    init {
        require(name.isNotBlank() && name.none(Char::isWhitespace)) { "Argument names must be nonempty single words" }
    }

    /** Reads this argument in the current invocation; throws if it is not bound or execution has ended. */
    @Suppress("UNCHECKED_CAST")
    operator fun invoke(): T {
        val invocation = CommandBindings.current()
        check(invocation != null && this in invocation) { "Argument '$name' can only be read in its command execution" }
        return invocation.getValue(this) as T
    }

    /** Delegated reads use exactly the same binding as [invoke]. */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = invoke()

    internal fun parseAndValidate(text: String): T {
        val parsed = parse(text)
        validators.forEach { (message, predicate) -> require(predicate(parsed)) { "$name: $message" } }
        return parsed
    }
}

/** Collects typed predicates applied before any execution handler runs. */
class CommandArgumentValidation<T> internal constructor() {
    internal val validators = mutableListOf<Pair<String, (T) -> Boolean>>()

    /** Adds a validation predicate and its user-facing failure message. */
    fun validate(message: String, predicate: (T) -> Boolean) {
        require(message.isNotBlank()) { "Validation message must not be blank" }
        validators += message to predicate
    }
}

/** Creates a required string argument, accepting quoted or unquoted positional text. */
fun stringArgument(name: String, block: CommandArgumentValidation<String>.() -> Unit = {}): CommandArgument<String> =
    createArgument(name, "string", { it }, block)

/** Creates a required decimal integer argument constrained to an inclusive [range]. */
fun intArgument(
    name: String,
    range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    block: CommandArgumentValidation<Int>.() -> Unit = {},
): CommandArgument<Int> {
    require(!range.isEmpty()) { "Integer argument range must not be empty" }
    return createArgument(name, "int ($range)", { text ->
        val parsed = text.toIntOrNull() ?: throw IllegalArgumentException("$name: expected an integer")
        require(parsed in range) { "$name: expected a value in $range" }
        parsed
    }, block)
}

/** Creates a required choice argument; declared strings match exactly, including case. */
fun choiceArgument(
    name: String,
    choices: List<String>,
    block: CommandArgumentValidation<String>.() -> Unit = {},
): CommandArgument<String> {
    val accepted = choices.toList()
    require(accepted.isNotEmpty() && accepted.distinct().size == accepted.size) {
        "Choices must be nonempty and unique"
    }
    return createArgument(name, accepted.joinToString("|"), { text ->
        require(text in accepted) { "$name: expected ${accepted.joinToString()}" }
        text
    }, block)
}

/** Creates a required enum argument matching the exact declared enum constant name. */
inline fun <reified T : Enum<T>> enumArgument(
    name: String,
    noinline block: CommandArgumentValidation<T>.() -> Unit = {},
): CommandArgument<T> = enumArgumentValues(name, enumValues<T>().toList(), block)

@PublishedApi
internal fun <T : Enum<T>> enumArgumentValues(
    name: String,
    values: List<T>,
    block: CommandArgumentValidation<T>.() -> Unit,
): CommandArgument<T> = createArgument(name, values.joinToString("|") { it.name }, { text ->
    values.firstOrNull { it.name == text } ?: throw IllegalArgumentException("$name: expected ${values.joinToString()}")
}, block)

/** Creates a required boolean argument accepting only the exact strings `true` and `false`. */
fun booleanArgument(name: String, block: CommandArgumentValidation<Boolean>.() -> Unit = {}): CommandArgument<Boolean> =
    createArgument(name, "true|false", { text ->
        text.toBooleanStrictOrNull() ?: throw IllegalArgumentException("$name: expected true or false")
    }, block)

private fun <T> createArgument(
    name: String,
    type: String,
    parse: (String) -> T,
    block: CommandArgumentValidation<T>.() -> Unit,
): CommandArgument<T> =
    CommandArgument(name, type, parse, CommandArgumentValidation<T>().apply(block).validators.toList())

/** Binding stacks restore outer command invocations after nested submissions and never retain completed values. */
internal object CommandBindings {
    private val bindings = ThreadLocal<ArrayDeque<Map<CommandArgument<*>, Any?>>>()
    fun current(): Map<CommandArgument<*>, Any?>? = bindings.get()?.lastOrNull()
    fun <T> withInvocation(values: Map<CommandArgument<*>, Any?>, block: () -> T): T {
        val stack = bindings.get() ?: ArrayDeque<Map<CommandArgument<*>, Any?>>().also(bindings::set)
        stack.addLast(values)
        try {
            return block()
        } finally {
            stack.removeLast()
            if (stack.isEmpty()) bindings.remove()
        }
    }
}
