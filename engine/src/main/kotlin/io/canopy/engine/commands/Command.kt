package io.canopy.engine.commands

import java.util.Collections
import io.canopy.engine.app.App

/** Reusable synchronous command; registrations construct one public no-argument instance per definition. */
interface Command {
    /** Exact command name; whitespace and the reserved name `help` are not permitted. */
    val name: String

    /** Description shown by generated help. */
    val description: String

    /** Explicit ordered list of required positional argument definitions used by this command. */
    val arguments: List<CommandArgument<*>>

    /** Executes after all arguments parse and validate. Read arguments with invocation or Kotlin delegation. */
    fun execute(context: CommandContext)
}

/** Current synchronous invocation, invalidated after execution so replies cannot leak into later submissions. */
class CommandContext internal constructor(
    /** Owning terminal application's existing lifecycle controls and manager utilities. */
    val app: App<*>,
    /** Prompt owning this invocation. Gameplay access keeps the node's normal lifetime guards. */
    val prompt: CommandPrompt,
) {
    internal var active = true

    /** Appends output to this prompt's bounded transcript while the invocation is active. */
    fun reply(text: String) {
        check(active) { "Command invocation has ended" }
        prompt.appendOutput(text)
    }
}

/** Controls whether a declaration replaces execution or extends its current handler chain. */
enum class CommandExecutionMode { Override, Append }

/**
 * Configures inline or reusable definitions. [template] retains the fresh typed class instance for configuration.
 * Changes to a template's original argument schema require overriding its execution before appending handlers.
 * All handlers share the same validated invocation; an exception stops later handlers without rolling back replies.
 */
class CommandDefinitionBuilder<T : Command> internal constructor(
    /** Fresh command instance associated with this registration. */
    val template: T,
    inheritedExecution: Boolean = true,
) {
    /** Configured exact name used for dispatch and help. */
    var name: String = template.name

    /** Configured help description. */
    var description: String = template.description
    private val originalArguments = template.arguments.toList()
    private var configuredArguments: List<CommandArgument<*>> = Collections.unmodifiableList(originalArguments)
    private var inheritsExecution = inheritedExecution
    private val handlers = mutableListOf<CommandContext.() -> Unit>().apply {
        if (inheritedExecution) add { template.execute(this) }
    }

    /** Required positional definitions, copied on assignment; inline factories append in declaration order. */
    var arguments: List<CommandArgument<*>>
        get() = configuredArguments
        set(value) {
            configuredArguments = Collections.unmodifiableList(value.toList())
        }

    /** Replaces execution by default, or appends after checking that the inherited schema is unchanged. */
    fun execute(mode: CommandExecutionMode = CommandExecutionMode.Override, handler: CommandContext.() -> Unit) {
        if (mode == CommandExecutionMode.Override) {
            handlers.clear()
            inheritsExecution = false
        } else {
            check(!inheritsExecution || arguments == originalArguments) {
                "Changing template arguments requires overriding execution before appending"
            }
        }
        handlers += handler
    }

    /** Appends a required string argument to this definition. */
    fun stringArgument(
        name: String,
        block: CommandArgumentValidation<String>.() -> Unit = {},
    ): CommandArgument<String> = addArgument(io.canopy.engine.commands.stringArgument(name, block))

    /** Appends a required decimal integer argument with inclusive bounds. */
    fun intArgument(
        name: String,
        range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
        block: CommandArgumentValidation<Int>.() -> Unit = {},
    ): CommandArgument<Int> = addArgument(io.canopy.engine.commands.intArgument(name, range, block))

    /** Appends a required exact string choice. */
    fun choiceArgument(
        name: String,
        choices: List<String>,
        block: CommandArgumentValidation<String>.() -> Unit = {},
    ): CommandArgument<String> = addArgument(io.canopy.engine.commands.choiceArgument(name, choices, block))

    /** Appends a required exact enum constant. */
    inline fun <reified E : Enum<E>> enumArgument(
        name: String,
        noinline block: CommandArgumentValidation<E>.() -> Unit = {},
    ): CommandArgument<E> = addArgument(io.canopy.engine.commands.enumArgument(name, block))

    /** Appends a required boolean accepting exactly `true` or `false`. */
    fun booleanArgument(
        name: String,
        block: CommandArgumentValidation<Boolean>.() -> Unit = {},
    ): CommandArgument<Boolean> = addArgument(io.canopy.engine.commands.booleanArgument(name, block))

    @PublishedApi
    internal fun <A> addArgument(argument: CommandArgument<A>): CommandArgument<A> {
        arguments = arguments + argument
        return argument
    }

    internal fun build(): CommandDefinition {
        require(name.isNotBlank() && name.none(Char::isWhitespace) && name != "help") {
            "Command names must be nonempty single words; 'help' is reserved"
        }
        require(arguments.map { it.name }.distinct().size == arguments.size) { "Duplicate argument names in '$name'" }
        check(!inheritsExecution || arguments == originalArguments) {
            "Changing template arguments requires overriding execution"
        }
        return CommandDefinition(name, description, template, arguments.toList(), handlers.toList())
    }
}

/** Pauses gameplay while keeping the prompt responsive. */
class PauseCommand : Command {
    override val name = "pause"
    override val description = "Pause gameplay"
    override val arguments = emptyList<CommandArgument<*>>()
    override fun execute(context: CommandContext) {
        context.app.pause()
        context.reply("Simulation paused")
    }
}

/** Resumes gameplay after a pause. */
class ResumeCommand : Command {
    override val name = "resume"
    override val description = "Resume gameplay"
    override val arguments = emptyList<CommandArgument<*>>()
    override fun execute(context: CommandContext) {
        context.app.resume()
        context.reply("Simulation resumed")
    }
}

internal class CommandDefinition(
    val name: String,
    val description: String,
    val template: Command,
    val arguments: List<CommandArgument<*>>,
    val handlers: List<CommandContext.() -> Unit>,
) {
    fun usage(): String = (listOf(name) + arguments.map { "<${it.name}:${it.typeName}>" }).joinToString(" ")
}

internal class InlineCommand(override val name: String, override val description: String) : Command {
    override val arguments = emptyList<CommandArgument<*>>()
    override fun execute(context: CommandContext) = Unit
}
