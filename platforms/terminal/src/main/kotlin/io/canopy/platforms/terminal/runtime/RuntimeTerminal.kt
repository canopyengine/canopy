package io.canopy.platforms.terminal.runtime

import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.ajalt.mordant.terminal.terminalinterface.jna.TerminalInterfaceProviderJna

internal fun runtimeTerminal(): Terminal {
    val terminal = Terminal(interactive = true)
    if (System.getProperty("os.name") != "Mac OS X") return terminal
    val input = checkNotNull(TerminalInterfaceProviderJna().load()) { "macOS terminal input requires Mordant JNA" }
    return Terminal(interactive = true, terminalInterface = MacosTerminalInterface(input, terminal.terminalInterface))
}

/**
 * Mordant 3.1.0's macOS FFM termios buffer is 64 bytes; Darwin writes 72, corrupting native memory.
 * JNA uses the correct layout. Delegate all input/settings to it, retaining FFM's safe ioctl size query
 * so resizing does not spawn stty every frame. Remove when the upstream FFM termios layout is corrected.
 */
internal class MacosTerminalInterface(input: TerminalInterface, private val geometry: TerminalInterface) :
    TerminalInterface by input {
    override fun getTerminalSize() = geometry.getTerminalSize()
    override fun shouldAutoUpdateSize() = geometry.shouldAutoUpdateSize()
}
