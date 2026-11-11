package io.canopy.tooling.compiler

import java.nio.file.Files
import java.nio.file.Path
import io.canopy.tooling.diagnostics.Diagnostic
import io.canopy.tooling.diagnostics.DiagnosticFormat
import io.canopy.tooling.diagnostics.SourceLine
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens

/** Shared across all passes of one compilation. Compiler locations stay separate from presentation text. */
internal class DiagnosticMessages(
    private val delegate: MessageCollector,
    private val color: Boolean = false,
    private val root: String? = null,
) : MessageCollector by delegate {
    private val sources = mutableMapOf<String, String?>()
    private val emitted = mutableSetOf<String>()
    private val invalid = mutableSetOf<String>()
    private var incompatibleRuntime = false

    fun source(file: IrFile): String? {
        val name = file.fileEntry.name
        if (name !in sources) {
            sources[name] = runCatching {
                val raw = Files.readString(Path.of(name))
                val lf = raw.replace("\r\n", "\n").replace('\r', '\n')
                // Kotlin's source offsets use the parser's normalized text, including on Windows.
                listOf(raw, lf, lf.removePrefix("\uFEFF")).firstOrNull { it.length == file.fileEntry.maxOffset }
            }.getOrNull()
        }
        return sources[name]
    }

    fun assignment(file: IrFile, start: Int, end: Int): Int? {
        val text = source(file) ?: return null
        if (start !in 0..text.length || end !in start..text.length) return null
        val lexer = KotlinLexer()
        lexer.start(text, start, end)
        var result: Int? = null
        while (lexer.tokenType != null) {
            if (lexer.tokenType == KtTokens.EQ) result = lexer.tokenStart
            lexer.advance()
        }
        return result
    }

    fun explicitType(file: IrFile, start: Int, end: Int): Boolean {
        val text = source(file) ?: return true
        if (start !in 0..text.length || end !in start..text.length) return true
        val lexer = KotlinLexer()
        lexer.start(text, start, end)
        var property = false
        while (lexer.tokenType != null) {
            if (lexer.tokenType == KtTokens.VAL_KEYWORD || lexer.tokenType == KtTokens.VAR_KEYWORD) property = true
            if (property && lexer.tokenType == KtTokens.COLON) return true
            lexer.advance()
        }
        return false
    }

    fun emit(
        code: String,
        title: String,
        file: IrFile? = null,
        element: IrElement? = null,
        label: String? = null,
        help: String? = null,
        start: Int = element?.startOffset ?: -1,
        length: Int = 1,
        replacement: String? = null,
        note: String? = null,
        compatibility: Boolean = code.endsWith("_ABI"),
    ) {
        val setup = compatibility
        val declarationKey = "${file?.fileEntry?.name}:${element?.startOffset}"
        if (code == "CANOPY_UNMANAGED_NODE_STATE" && declarationKey in invalid) return
        if (incompatibleRuntime) return
        if (!emitted.add(if (setup) "runtime-build" else "$code:${file?.fileEntry?.name}:$start:$title:$label")) return
        if (setup) incompatibleRuntime = true
        invalid += declarationKey
        val offset = start.coerceAtLeast(0)
        val entry = file?.fileEntry
        val line = runCatching { entry?.getLineNumber(offset)?.plus(1) }.getOrNull() ?: 1
        val column = runCatching { entry?.getColumnNumber(offset)?.plus(1) }.getOrNull() ?: 1
        val location = if (file == null || setup || start < 0) {
            null
        } else {
            CompilerMessageLocation.create(
                file.fileEntry.name,
                line,
                column,
                null
            )
        }
        val sourceLine = if (file == null || setup || start < 0) {
            null
        } else {
            source(file)?.lineSequence()
                ?.drop(line - 1)?.firstOrNull()?.let {
                    SourceLine(displayPath(file.fileEntry.name), line, column, it, length)
                }
        }
        delegate.report(
            CompilerMessageSeverity.ERROR,
            DiagnosticFormat.render(
                Diagnostic(code, title, sourceLine, label, help, replacement, note),
                color,
                includeSeverity = false
            ),
            location
        )
    }

    fun legacy(code: String, explanation: String, file: IrFile? = null, element: IrElement? = null) {
        val title = when (code) {
            "CANOPY_UNMANAGED_NODE_STATE" -> "This Node retains unmanaged state"
            "CANOPY_UNSUPPORTED_NODE_PROPERTY" -> "This property needs explicit Node storage"
            "CANOPY_UI_UNSUPPORTED" -> "This UI declaration is not supported"
            else -> explanation.substringBefore('.').trim()
        }
        if (code.endsWith("_ABI") || explanation.contains("runtime ABI")) {
            emit(
                code,
                "Engine and compiler builds are incompatible",
                label = "The resolved engine is missing helpers required by this compiler.",
                help = "Publish the engine and compiler from the same checkout, then rebuild your project.",
                compatibility = true
            )
        } else {
            emit(code, title, file, element, label = explanation)
        }
    }

    private fun displayPath(name: String): String = runCatching {
        val path = Path.of(name).toAbsolutePath().normalize()
        val base = root?.let { Path.of(it).toAbsolutePath().normalize() }
        if (base != null && path.startsWith(base)) {
            base.relativize(path).toString().replace('\\', '/')
        } else {
            path.fileName.toString()
        }
    }.getOrDefault(name.substringAfterLast('/').substringAfterLast('\\'))
}
