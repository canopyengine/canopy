package io.canopy.engine.commands

import kotlin.test.*
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.core.exceptions.CanopyException
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Behavior
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.input.events.TextInputEvent
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class CommandPromptTests {
    private class TestApp : App<AppConfig>() {
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
    }
    private class Presentation : CommandPromptPresentation {
        var lineMode = false
        override val isLineInput get() = lineMode
        val snapshots = mutableListOf<CommandPromptSnapshot>()
        var hides = 0
        override fun render(snapshot: CommandPromptSnapshot) {
            snapshots += snapshot
        }
        override fun hide() {
            hides++
        }
    }
    private class Input : InputManager() {
        val pressed = mutableSetOf<InputBind>()
        override fun pollPressed(bind: InputBind) = bind in pressed
    }

    private class InputNode(name: String, calls: MutableList<String>, consume: Boolean = false) :
        Node<InputNode>(name) {
        private val handler by nodeProperty<(InputEvent) -> Unit>({ event ->
            calls += name
            if (consume) event.consume()
        })
        override fun onInput(event: InputEvent) {
            handler(event)
        }
    }

    private lateinit var app: TestApp
    private lateinit var presentation: Presentation
    private lateinit var host: CommandPromptHost
    private lateinit var scenes: SceneManager
    private lateinit var input: Input

    @BeforeEach
    fun setup() {
        app = TestApp()
        presentation = Presentation()
        host = CommandPromptHost(app, presentation)
        scenes = SceneManager()
        input = Input()
        ManagersRegistry.withScope {
            register(host)
            register(scenes)
            register(input)
        }
    }

    @AfterEach
    fun cleanup() {
        ManagersRegistry.exit()
    }

    private fun enter(block: CommandPrompt.() -> Unit = {}): CommandPrompt =
        CommandPrompt("Console", block).also { scenes.currScene = it }

    @Test
    fun `typed arguments bind only for validated execution and delegate forwards invoke`() {
        // Arrange
        lateinit var species: CommandArgument<String>
        lateinit var countArgument: CommandArgument<Int>
        var result = ""
        val prompt = enter {
            command("spawn") {
                species = choiceArgument("species", listOf("wolf", "fox"))
                countArgument = intArgument("count", 1..3)
                val count by countArgument
                execute { result = "${species()}:$count" }
            }
        }
        assertFailsWith<IllegalStateException> { species() }
        // Act
        prompt.submit("spawn fox 3")
        // Assert
        assertEquals("fox:3", result)
        assertFailsWith<IllegalStateException> { countArgument() }
        listOf("spawn Fox 1", "spawn wolf 0", "spawn wolf 4", "spawn wolf many", "spawn wolf", "spawn wolf 1 extra")
            .forEach(prompt::submit)
        assertEquals("fox:3", result)
        assertTrue(prompt.transcript.last().startsWith("Usage: spawn"))
    }

    private enum class Color { Red, Blue }

    @Test
    fun `quotes typed validators exact enum and boolean reject before handlers`() {
        // Arrange
        var runs = 0
        var result = ""
        val prompt = enter {
            command("set") {
                val text = stringArgument("name") { validate("must not be blank") { it.isNotBlank() } }
                val color by enumArgument<Color>("color")
                val flag by booleanArgument("flag")
                execute {
                    runs++
                    result = "${text()}:$color:$flag"
                }
            }
        }
        // Act
        prompt.submit("set 'hello world' Red true")
        listOf("set '' Red true", "set name red true", "set name Red TRUE", "set 'unfinished Red true", "set name\\")
            .forEach(prompt::submit)
        // Assert
        assertEquals(1, runs)
        assertEquals("hello world:Red:true", result)
        assertEquals(listOf("a b", "c\"d", ""), tokenize("'a b' c\\\"d \"\""))
    }

    @Test
    fun `nested submissions restore bindings and ordinary failure clears invocation`() {
        lateinit var outer: CommandArgument<Int>
        val reads = mutableListOf<Int>()
        val prompt = enter {
            command("outer") {
                outer = intArgument("n")
                execute {
                    reads += outer()
                    prompt.submit("inner 2")
                    reads += outer()
                    reply("retained")
                    error("stop")
                }
                execute(CommandExecutionMode.Append) { reads += 99 }
            }
            command("inner") {
                val n by intArgument("n")
                execute {
                    assertFailsWith<IllegalStateException> { outer() }
                    reads += n
                }
            }
        }
        prompt.submit("outer 1")
        assertEquals(listOf(1, 2, 1), reads)
        assertFailsWith<IllegalStateException> { outer() }
        assertTrue("retained" in prompt.transcript)
        assertTrue(prompt.transcript.any { it == "Error: stop" })
    }

    class CounterCommand : Command {
        override val name = "counter"
        override val description = "Count executions"
        val amount = intArgument("amount")
        override val arguments = listOf(amount)
        var count = 0
        override fun execute(context: CommandContext) {
            count += amount()
            context.reply("count=$count")
        }
    }

    abstract class ConstructorCommand : Command {
        override val name = "constructor"
        override val description = "Constructor fixture"
        override val arguments = emptyList<CommandArgument<*>>()
        override fun execute(context: CommandContext) = Unit
    }

    class RequiredConstructorCommand(val required: String) : ConstructorCommand()
    class PrivateConstructorCommand private constructor() : ConstructorCommand()
    abstract class AbstractConstructorCommand : ConstructorCommand()
    class ThrowingConstructorCommand : ConstructorCommand() {
        init {
            error("constructor failed")
        }
    }
    class CancelledConstructorCommand : ConstructorCommand() {
        init {
            throw CancellationException("constructor cancelled")
        }
    }
    class FatalConstructorCommand : ConstructorCommand() {
        init {
            throw AssertionError("constructor fatal")
        }
    }

    @Test
    fun `invalid constructors fail descriptively without installing definitions and preserve fatal types`() {
        val prompt = enter()
        listOf(
            RequiredConstructorCommand::class,
            PrivateConstructorCommand::class,
            AbstractConstructorCommand::class,
            ThrowingConstructorCommand::class
        ).forEach { type ->
            val failure = assertFailsWith<IllegalArgumentException> { prompt.command(type) }
            assertTrue(failure.message!!.contains(type.qualifiedName!!))
            assertTrue(failure.message!!.contains("public no-argument constructor"))
        }
        val cancellation = assertFailsWith<CancellationException> { prompt.command<CancelledConstructorCommand>() }
        assertEquals("constructor cancelled", cancellation.message)
        val fatal = assertFailsWith<AssertionError> { prompt.command<FatalConstructorCommand>() }
        assertEquals("constructor fatal", fatal.message)
        prompt.submit("help")
        assertEquals(listOf("> help", "help - Show available commands"), prompt.transcript)
        prompt.command("constructor") { execute { reply("registered successfully") } }
        prompt.submit("constructor")
        assertEquals("registered successfully", prompt.transcript.last())
    }

    @Test
    fun `class registration uses fresh templates with ordered append and override`() {
        lateinit var first: CounterCommand
        lateinit var second: CounterCommand
        val prompt = enter {
            command<CounterCommand> {
                first = template
                execute(CommandExecutionMode.Append) { reply("after") }
            }
            command(CounterCommand::class) {
                second = template
                name = "other"
                arguments = emptyList()
                execute { reply("replacement") }
            }
        }
        prompt.submit("counter 2")
        prompt.submit("other")
        assertEquals(2, first.count)
        assertEquals(0, second.count)
        assertNotSame(first, second)
        assertEquals(listOf("count=2", "after", "> other", "replacement"), prompt.transcript.takeLast(4))
        assertFailsWith<IllegalStateException> {
            prompt.command<CounterCommand> {
                name = "bad"
                arguments = emptyList()
                execute(CommandExecutionMode.Append) {}
            }
        }
    }

    @Test
    fun `names are exact help is generated and registration rejects duplicate metadata`() {
        val prompt = enter { command("hello", "A greeting") { execute { reply("hi") } } }
        assertFailsWith<IllegalArgumentException> { prompt.command("hello") {} }
        assertFailsWith<IllegalArgumentException> { prompt.command("help") {} }
        assertFailsWith<IllegalArgumentException> {
            prompt.command("duplicate") {
                stringArgument("same")
                intArgument("same")
            }
        }
        prompt.submit("Hello")
        assertTrue(prompt.transcript.last().contains("unknown command"))
        prompt.submit("help")
        assertEquals("hello - A greeting", prompt.transcript.last())
        prompt.submit("help extra")
        assertEquals("Usage: help", prompt.transcript.last())
    }

    @Test
    fun `pause and resume templates stay responsive through focused raw input`() {
        val prompt = enter {
            command<PauseCommand>()
            command<ResumeCommand>()
        }
        input.enqueue(KeyInputEvent(Key.ESCAPE, state = InputState.JustPressed))
        input.enqueue(TextInputEvent("pause"))
        input.enqueue(KeyInputEvent(Key.ENTER, state = InputState.JustPressed))
        input.processEvents()
        assertTrue(prompt.isVisible)
        assertTrue(app.isPaused)
        input.enqueue(TextInputEvent("resume"))
        input.enqueue(KeyInputEvent(Key.ENTER, state = InputState.JustPressed))
        input.processEvents()
        assertFalse(app.isPaused)
        assertTrue(input.eventsThisFrame.isEmpty())
    }

    @Test
    fun `focus suppresses mapping polling and later queue events after hide on enter`() {
        val prompt = enter { command("hide") { execute { prompt.hide() } } }
        input.mapActions("move" to listOf(InputBind.W))
        input.pressed += InputBind.W
        prompt.show()
        input.enqueue(TextInputEvent("hide"))
        input.enqueue(KeyInputEvent(Key.ENTER, state = InputState.JustPressed))
        input.enqueue(KeyInputEvent(Key.W_KEY, state = InputState.JustPressed))
        input.enqueue(TextInputEvent("w"))
        input.processEvents()
        assertFalse(prompt.isVisible)
        assertEquals("", prompt.draft)
        assertTrue(input.eventsThisFrame.isEmpty())
        assertTrue(input.actionStates.isEmpty())
        assertFalse(input.isPressed(InputBind.W))
        input.processEvents()
        assertTrue(input.isActionJustPressed("move"))
        assertTrue(input.isPressed(InputBind.W))
    }

    @Test
    fun `printable toggle consumes its paired text and unicode backspace removes one code point`() {
        val prompt = enter { toggleKey = Key.W_KEY }
        input.enqueue(KeyInputEvent(Key.W_KEY, state = InputState.JustPressed))
        input.enqueue(TextInputEvent("W"))
        input.enqueue(TextInputEvent("fox😀"))
        input.enqueue(KeyInputEvent(Key.BACKSPACE, state = InputState.JustPressed))
        input.processEvents()
        assertTrue(prompt.isVisible)
        assertEquals("fox", prompt.draft)
    }

    @Test
    fun `fallback exact toggle is not submitted and other colon lines execute once`() {
        presentation.lineMode = true
        var runs = 0
        val prompt = enter { command(":consolex") { execute { runs++ } } }
        input.enqueue(TextInputEvent(":console"))
        input.enqueue(KeyInputEvent(Key.ENTER, state = InputState.JustPressed))
        input.processEvents()
        assertTrue(prompt.isVisible)
        assertEquals(emptyList(), prompt.transcript)
        input.enqueue(TextInputEvent(":consolex"))
        input.enqueue(KeyInputEvent(Key.ENTER, state = InputState.JustPressed))
        input.processEvents()
        assertEquals(1, runs)
        assertEquals("", prompt.draft)
    }

    @Test
    fun `reusable exit releases focus preserves draft and command instances and destroy guards reads`() {
        lateinit var command: CounterCommand
        val prompt = enter { command<CounterCommand> { command = template } }
        prompt.show()
        input.enqueue(TextInputEvent("draft"))
        input.processEvents()
        host.onUpdate(0f)
        prompt.nodeExitTree()
        assertFalse(prompt.isVisible)
        assertEquals(1, presentation.hides)
        prompt.nodeEnterTree()
        assertEquals("draft", prompt.draft)
        prompt.submit("counter 1")
        assertEquals(1, command.count)
        prompt.show()
        host.onUpdate(0f)
        prompt.queueFree()
        scenes.onUpdate(0f)
        assertFalse(prompt.isValid)
        assertFailsWith<CanopyException> { prompt.transcript }
        assertFailsWith<CanopyException> { prompt.transcriptLimit = 0 }
        var configured = false
        assertFailsWith<CanopyException> { prompt.command("disposed") { configured = true } }
        assertFalse(configured)
        assertEquals(2, presentation.hides)
        input.processEvents() // The captured frame stays suppressed until the next input frame.
        assertFalse(input.blocksGameplay)
    }

    @Test
    fun `bounded immutable transcript tracks appended output independently of trimming`() {
        val prompt = enter {
            transcriptLimit = 2
            command("say") { execute { reply("same") } }
        }
        prompt.show()
        prompt.submit("say")
        host.onUpdate(0f)
        val first = presentation.snapshots.last()
        prompt.submit("say")
        host.onUpdate(0f)
        val second = presentation.snapshots.last()
        assertEquals(first.transcript, second.transcript)
        assertEquals(first.outputSequence + 2, second.outputSequence)
        prompt.transcriptLimit = 1
        host.onUpdate(0f)
        assertEquals(second.outputSequence, presentation.snapshots.last().outputSequence)
        assertEquals(2, first.transcript.size)
        assertFailsWith<UnsupportedOperationException> { (first.transcript as MutableList<String>).add("bad") }
    }

    @Test
    fun `fatal and cancellation propagate with binding cleanup`() {
        lateinit var argument: CommandArgument<Int>
        val fatal = AssertionError("fatal")
        val cancelled = CancellationException("cancel")
        val prompt = enter {
            command("fatal") {
                argument = intArgument("n")
                execute {
                    argument()
                    throw fatal
                }
            }
            command("cancel") { execute { throw cancelled } }
        }
        assertSame(fatal, assertFailsWith<AssertionError> { prompt.submit("fatal 1") })
        assertFailsWith<IllegalStateException> { argument() }
        assertSame(cancelled, assertFailsWith<CancellationException> { prompt.submit("cancel") })
    }

    @Test
    fun `host rejects second entered prompt and exit failures still release focus`() {
        val root = EmptyNode("root")
        val first = CommandPrompt("first")
        root.addChild(first)
        scenes.currScene = root
        assertFailsWith<CanopyException> { root.addChild(CommandPrompt("second")) }
        first.show()
        host.onUpdate(0f)
        first.onRemoval { error("cleanup failure") }
        assertFailsWith<CanopyException> { root.nodeExitTree() }
        assertEquals(1, presentation.hides)
        assertFalse(first.isVisible)
        assertFalse(input.blocksGameplay)
    }

    @Test
    fun `consumed input stops remaining children siblings and behaviors in tree order`() {
        val calls = mutableListOf<String>()
        val root = InputNode("root", calls)
        val first = InputNode("first", calls, consume = true)
        first.addChild(InputNode("grandchild", calls))
        first.behavior = object : Behavior<InputNode>(first) {
            override fun onInput(event: InputEvent) {
                calls += "behavior"
            }
        }
        root.addChild(first)
        root.addChild(InputNode("sibling", calls))
        scenes.currScene = root
        root.nodeInput(TextInputEvent("first dispatch"))
        assertEquals(listOf("root", "first"), calls)
        calls.clear()
        root.nodeInput(TextInputEvent("independent dispatch"))
        assertEquals(listOf("root", "first"), calls)
    }

    @Test
    fun `overridden definition still owns its fresh template independently of execution handlers`() {
        val template = CounterCommand()
        val definition = CommandDefinitionBuilder(template).apply {
            arguments = emptyList()
            execute {}
        }.build()
        assertSame(template, definition.template)
        assertEquals(1, definition.handlers.size)
        assertTrue(definition.arguments.isEmpty())
    }

    @Test
    fun `render and removal failures clear presentation and allow another prompt to enter`() {
        var failRender = true
        var hides = 0
        val failing = object : CommandPromptPresentation {
            override fun render(snapshot: CommandPromptSnapshot) {
                if (failRender) error("render")
            }
            override fun hide() {
                hides++
                error("hide")
            }
        }
        ManagersRegistry.exit()
        host = CommandPromptHost(app, failing)
        scenes = SceneManager()
        ManagersRegistry.withScope {
            register(host)
            register(scenes)
        }
        val first = enter()
        first.show()
        assertFailsWith<IllegalStateException> { host.onUpdate(0f) }
        assertFailsWith<CanopyException> { first.nodeExitTree() }
        assertEquals(1, hides)
        assertFalse(first.isVisible)
        failRender = false
        val second = CommandPrompt("next")
        scenes.currScene = second
        second.show()
        host.onUpdate(0f)
        second.hide()
        assertFailsWith<IllegalStateException> { host.onUpdate(0f) }
    }
}
