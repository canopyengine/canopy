package io.canopy.engine.commands

import kotlin.reflect.KClass
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.Collections
import io.canopy.engine.core.exceptions.CanopyException
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.input.binds.Key
import kotlinx.coroutines.CancellationException

/**
 * Declarative terminal command editor. State, definitions, handlers and reusable instances are engine-owned.
 * Starts closed; opening captures terminal input without pausing gameplay. Draft and bounded transcript survive
 * closing and reusable tree exit. The terminal host supplies focus and presentation automatically.
 * All configuration, parsing and execution run synchronously on the serialized lifecycle thread.
 * Permanent destruction invalidates gameplay reads and releases command/presentation ownership.
 */
class CommandPrompt(name: String, block: CommandPrompt.() -> Unit = {}) : Node<CommandPrompt>(name, block = block) {
    private val runtime by nodeProperty(PromptState())

    init {
        val owned = runtime
        onDestroy {
            owned.commands.clear()
            owned.transcript.clear()
            owned.host = null
        }
    }

    /** Editor prefix; defaults to `> `. */
    var prompt: String
        get() = runtime.prefix
        set(value) {
            runtime.prefix = value
        }

    /** Raw-mode activation shortcut; defaults to Escape, or null to disable. Line mode uses exact `:console`. */
    var toggleKey: Key?
        get() = runtime.toggleKey
        set(value) {
            runtime.toggleKey = value
        }

    /**
     * Guarded activation state; closing preserves draft, definitions and transcript while releasing input focus.
     * Independent of node rendering visibility; access belongs to the serialized lifecycle thread.
     */
    var isOpen: Boolean
        get() = runtime.open
        set(value) {
            runtime.open = value
        }

    /** Maximum retained transcript entries, positive and defaulting to 100. Changes immediately trim old entries. */
    var transcriptLimit: Int
        get() = runtime.limit
        set(value) {
            val state = runtime
            require(value > 0) { "Transcript limit must be positive" }
            state.limit = value
            trim(state)
        }

    /** Current editable draft; reads retain normal node lifetime guards. */
    val draft: String get() = runtime.draft

    /** Independent read-only snapshot of retained command echoes, replies, help and errors. */
    val transcript: List<String> get() = Collections.unmodifiableList(runtime.transcript.toList())

    /** Opens the command editor and captures input without changing gameplay pause state. Idempotent. */
    fun open() {
        isOpen = true
    }

    /** Closes the command editor and releases focus without clearing the draft or output. Idempotent. */
    fun close() {
        isOpen = false
    }

    /** Toggles command editor activation without reconstructing command instances. */
    fun toggle() {
        isOpen = !isOpen
    }

    /** Registers an inline definition; argument factories append ordered required positional arguments. */
    fun command(name: String, description: String = "", block: CommandDefinitionBuilder<Command>.() -> Unit) {
        runtime // Configuration callbacks must not run for a disposed owner.
        register(CommandDefinitionBuilder<Command>(InlineCommand(name, description), false).apply(block).build())
    }

    /** Registers a fresh public no-argument command instance, configured once for this prompt. */
    fun <T : Command> command(type: KClass<T>, block: CommandDefinitionBuilder<T>.() -> Unit = {}) {
        runtime // Validate owner before invoking a user constructor.
        val instance = try {
            require(Modifier.isPublic(type.java.modifiers)) { "Command class must be public" }
            type.java.getConstructor().newInstance()
        } catch (error: Exception) {
            val cause = (error as? InvocationTargetException)?.targetException ?: error
            if (cause is Error || cause is CancellationException || cause is CanopyException) throw cause
            throw IllegalArgumentException(
                "Command ${type.qualifiedName} needs a usable public no-argument constructor",
                cause
            )
        }
        register(CommandDefinitionBuilder(instance).apply(block).build())
    }

    /** Reified class registration with the same fresh-instance and constructor rules as [command]. */
    inline fun <reified T : Command> command(noinline block: CommandDefinitionBuilder<T>.() -> Unit = {}) =
        command(T::class, block)

    private fun register(definition: CommandDefinition) {
        val commands = runtime.commands
        require(definition.name !in commands) { "Command '${definition.name}' already registered" }
        commands[definition.name] = definition
    }

    /**
     * Submits a command synchronously from an entered host. Parses and validates every argument before handlers.
     * Ordinary errors become transcript output; cancellation, fatal errors and engine exceptions preserve their types.
     * Nested submissions restore outer bindings. Every invocation is cleared after success or failure.
     */
    fun submit(line: String) {
        val state = runtime
        check(isInsideTree && state.host != null) { "Command prompt must be entered in a host before submitting" }
        if (line.isBlank()) return
        appendOutput(state.prefix + line)
        val tokens = try {
            tokenize(line)
        } catch (error: IllegalArgumentException) {
            appendOutput("Error: ${error.message}")
            return
        }
        val commandName = tokens.firstOrNull() ?: return
        if (commandName == "help") {
            if (tokens.size != 1) {
                appendOutput("Usage: help")
            } else {
                appendOutput("help - Show available commands")
                state.commands.values.toList().forEach { appendOutput("${it.usage()} - ${it.description}") }
            }
            return
        }
        val definition = state.commands[commandName]
        if (definition == null) {
            appendOutput("Error: unknown command '$commandName'. Type help for available commands.")
            return
        }
        val context = CommandContext(state.host!!.app, this)
        try {
            require(tokens.size - 1 == definition.arguments.size) { "Wrong argument count" }
            val bindings = definition.arguments.mapIndexed { index, argument ->
                argument to argument.parseAndValidate(tokens[index + 1])
            }.toMap()
            CommandBindings.withInvocation(bindings) {
                definition.handlers.forEach { handler ->
                    check(isInsideTree) { "Command prompt left the tree during execution" }
                    context.handler()
                }
            }
        } catch (error: Throwable) {
            if (error is Error || error is CancellationException || error is CanopyException) throw error
            // A handler may destroy its owner: do not revive or read disposed prompt state to report an error.
            if (isValid) {
                appendOutput("Error: ${error.message ?: error::class.simpleName}")
                appendOutput("Usage: ${definition.usage()}")
            }
        } finally {
            context.active = false
        }
    }

    internal fun appendOutput(text: String) {
        val state = runtime
        state.transcript += text
        state.outputSequence++
        trim(state)
    }

    internal fun presentationSnapshot(): CommandPromptSnapshot {
        val state = runtime
        return CommandPromptSnapshot(state.prefix, state.draft, transcript, state.outputSequence)
    }

    private fun trim(state: PromptState) {
        while (state.transcript.size > state.limit) state.transcript.removeFirst()
    }

    internal fun appendText(text: String) {
        runtime.draft += text.filterNot(Char::isISOControl)
    }

    internal fun backspace() {
        val state = runtime
        if (state.draft.isNotEmpty()) {
            val count = if (state.draft.length >= 2 &&
                state.draft.takeLast(2).let {
                    it[0].isHighSurrogate() && it[1].isLowSurrogate()
                }
            ) {
                2
            } else {
                1
            }
            state.draft = state.draft.dropLast(count)
        }
    }

    internal fun submitDraft() {
        val line = runtime.draft
        runtime.draft = ""
        submit(line)
    }

    override fun onEnterTree() {
        val host = ManagersRegistry.getManager(CommandPromptHost::class)
        val owned = runtime
        onRemoval {
            owned.open = false
            owned.host = null
            host.detach(this)
        }
        host.attach(this)
        owned.host = host
    }
}

private class PromptState {
    var prefix = "> "
    var toggleKey: Key? = Key.ESCAPE
    var open = false
    var limit = 100
    var draft = ""
    var host: CommandPromptHost? = null
    var outputSequence = 0L
    val transcript = mutableListOf<String>()
    val commands = linkedMapOf<String, CommandDefinition>()
}

/** Shell-like positional tokenizer supporting single/double quotes and escaped characters. */
internal fun tokenize(line: String): List<String> {
    val tokens = mutableListOf<String>()
    val token = StringBuilder()
    var quote: Char? = null
    var escaped = false
    var started = false
    for (char in line) {
        when {
            escaped -> {
                token.append(char)
                escaped = false
                started = true
            }
            char == '\\' -> {
                escaped = true
                started = true
            }
            quote != null -> if (char == quote) quote = null else token.append(char)
            char == '\'' || char == '"' -> {
                quote = char
                started = true
            }
            char.isWhitespace() -> if (started) {
                tokens += token.toString()
                token.clear()
                started = false
            }
            else -> {
                token.append(char)
                started = true
            }
        }
    }
    require(quote == null) { "Unclosed quote" }
    require(!escaped) { "Trailing escape" }
    if (started) tokens += token.toString()
    return tokens
}
