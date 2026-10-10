package io.canopy.adapters.logback

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.util.Properties
import java.util.UUID

/** Owns files separately from Logback, including cross-process leases and recovery of previous current logs. */
internal class ManagedLogFiles private constructor(
    val directory: Path,
    val rollingDirectory: Path,
    val json: Boolean,
    private val runDirectory: Path,
    private val current: Boolean,
    private val lease: Lease,
    private val currentLease: Lease?,
    private val base: Path,
    private val retention: LogbackLogging.Retention,
) : AutoCloseable {
    data class HistoryResult(val runs: Int, val bytes: Long)

    /** Only Canopy-owned, unlocked completed/crashed runs are eligible; active and unknown data are never removed. */
    fun cleanupHistory(): HistoryResult = cleanupHistory(base, retention)

    override fun close() {
        try {
            if (!current) {
                val manifest = readManifest(runDirectory.resolve(RUN_MANIFEST))
                if (manifest != null) {
                    writeManifest(
                        runDirectory.resolve(RUN_MANIFEST),
                        manifest.apply {
                            setProperty("state", "complete")
                        }
                    )
                }
            }
        } finally {
            try {
                lease.close()
            } finally {
                currentLease?.close()
            }
        }
        // Append handles and leases are closed before completed-run cleanup; no logger output is emitted here.
        cleanupHistory()
    }

    companion object {
        private const val SCHEMA = "canopy-managed-logs-v1"
        private const val RUN_MANIFEST = ".canopy-run.properties"
        private const val RUN_LOCK = ".canopy-run.lock"
        private const val CURRENT_MANIFEST = ".canopy-current.properties"
        private const val CURRENT_LOCK = ".canopy-current.lock"
        private const val HISTORY_LOCK = ".canopy-history.lock"
        private val TEXT_FILES = listOf("engine.log", "app.log")

        fun open(config: LogbackLogging.Config, runId: String): ManagedLogFiles {
            val base = safeDirectory(config.baseLogDir)
            requireLogDirectoryName(runId)
            require(
                runId.lowercase() !in setOf("history", "engine.log", "app.log", "engine.jsonl", "app.jsonl") &&
                    !runId.lowercase().startsWith(".canopy-")
            ) { "runId must not use a reserved log layout name" }
            val historyPath = base.resolve("history")
            val history = if (config.mode == LogbackLogging.Mode.STANDARD) {
                runCatching { safeDirectory(historyPath) }.getOrNull()
            } else {
                null
            }
            for (candidate in listOfNotNull(
                base.resolve(runId),
                historyPath.takeIf { Files.isDirectory(it, NOFOLLOW_LINKS) }?.resolve(runId)
            )) {
                if (Files.exists(candidate, NOFOLLOW_LINKS)) {
                    throw java.nio.file.FileAlreadyExistsException(candidate.toString())
                }
            }
            if (config.mode == LogbackLogging.Mode.DIAGNOSTIC) {
                return isolated(base.resolve(runId), runId, json = true, base = base, retention = config.retention)
            }
            val currentLease = Lease.tryOpen(base.resolve(CURRENT_LOCK))
            if (currentLease != null) {
                try {
                    // Unknown files and interrupted archive failures keep their contents; use a separate run instead.
                    if (history != null && archiveCurrent(base, history)) {
                        val files = isolated(
                            history.resolve(runId),
                            runId,
                            json = false,
                            current = true,
                            base = base,
                            retention = config.retention
                        )
                        try {
                            writeManifest(base.resolve(CURRENT_MANIFEST), manifest(runId, "current"))
                            TEXT_FILES.forEach { Files.createFile(base.resolve(it)) }
                            return ManagedLogFiles(
                                base,
                                files.directory,
                                false,
                                files.runDirectory,
                                true,
                                files.lease,
                                currentLease,
                                base,
                                config.retention
                            )
                        } catch (failure: Throwable) {
                            files.close()
                            throw failure
                        }
                    }
                } catch (failure: Throwable) {
                    currentLease.close()
                    throw failure
                }
                currentLease.close()
            }
            return isolated(
                (history ?: base).resolve(runId),
                runId,
                json = false,
                base = base,
                retention = config.retention
            )
        }

        private fun isolated(
            path: Path,
            runId: String,
            json: Boolean,
            current: Boolean = false,
            base: Path,
            retention: LogbackLogging.Retention,
        ): ManagedLogFiles {
            val directory = Files.createDirectory(path)
            var lease: Lease? = null
            try {
                lease = checkNotNull(Lease.tryOpen(directory.resolve(RUN_LOCK))) { "New log run directory is locked" }
                writeManifest(
                    directory.resolve(RUN_MANIFEST),
                    manifest(runId, if (current) "current" else "active", json),
                    new = true
                )
                return ManagedLogFiles(directory, directory, json, directory, current, lease, null, base, retention)
            } catch (failure: Throwable) {
                lease?.close()
                throw failure
            }
        }

        /** Returns false for unowned files or recovery blocked by external file handles; never truncates previous data. */
        private fun archiveCurrent(base: Path, history: Path): Boolean {
            val marker = base.resolve(CURRENT_MANIFEST)
            val current = readManifest(marker)
            if (current == null) {
                return !Files.exists(marker, NOFOLLOW_LINKS) &&
                    TEXT_FILES.none { Files.exists(base.resolve(it), NOFOLLOW_LINKS) }
            }
            val previous = history.resolve(current.getProperty("runId"))
            if (!Files.isDirectory(previous, NOFOLLOW_LINKS)) return false
            val previousMarker = previous.resolve(RUN_MANIFEST)
            val manifest = readManifest(previousMarker) ?: return false
            if (manifest.getProperty("runId") != current.getProperty("runId")) return false
            val lease = Lease.tryOpen(previous.resolve(RUN_LOCK)) ?: return false
            lease.use {
                val unsafe = TEXT_FILES.any { name ->
                    val source = base.resolve(name)
                    val destination = previous.resolve(name)
                    val sourceExists = Files.exists(source, NOFOLLOW_LINKS)
                    val destinationExists = Files.exists(destination, NOFOLLOW_LINKS)
                    val invalidSource = sourceExists && !Files.isRegularFile(source, NOFOLLOW_LINKS)
                    val invalidDestination = destinationExists && !Files.isRegularFile(destination, NOFOLLOW_LINKS)
                    invalidSource || invalidDestination || (sourceExists && destinationExists)
                }
                if (unsafe) return false
                try {
                    for (name in TEXT_FILES) {
                        val source = base.resolve(name)
                        if (Files.exists(source, NOFOLLOW_LINKS)) Files.move(source, previous.resolve(name))
                    }
                    writeManifest(previousMarker, manifest.apply { setProperty("state", "complete") })
                    return true
                } catch (_: java.io.IOException) {
                    // A partial move is recoverable on the next launch: do not replace already archived files.
                    return false
                }
            }
        }

        private data class HistoryEntry(val directory: Path, val startedAt: String, val bytes: Long)

        private fun cleanupHistory(base: Path, retention: LogbackLogging.Retention): HistoryResult {
            return try {
                val lease = Lease.tryOpen(base.resolve(HISTORY_LOCK)) ?: return HistoryResult(0, 0)
                lease.use { cleanupUnlockedHistory(base, retention) }
            } catch (_: java.io.IOException) {
                HistoryResult(0, 0)
            }
        }

        private fun cleanupUnlockedHistory(base: Path, retention: LogbackLogging.Retention): HistoryResult {
            fun children(directory: Path): List<Path> = try {
                if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) {
                    emptyList()
                } else {
                    Files.list(directory).use {
                        it.toList()
                    }
                }
            } catch (_: java.io.IOException) {
                emptyList()
            }
            val candidates = children(base) + children(base.resolve("history"))
            val entries = candidates.mapNotNull { directory ->
                try {
                    if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) return@mapNotNull null
                    val marker = readManifest(directory.resolve(RUN_MANIFEST)) ?: return@mapNotNull null
                    if (marker.getProperty("state") == "current" ||
                        marker.getProperty("runId") != directory.fileName.toString()
                    ) {
                        return@mapNotNull null
                    }
                    val lease = Lease.tryOpen(directory.resolve(RUN_LOCK)) ?: return@mapNotNull null
                    lease.use {
                        val files = ownedFiles(directory, marker) ?: return@mapNotNull null
                        HistoryEntry(directory, marker.getProperty("startedAt"), files.sumOf { Files.size(it) })
                    }
                } catch (_: java.io.IOException) {
                    null
                }
            }.sortedByDescending { java.time.Instant.parse(it.startedAt) }
            var bytes = entries.sumOf { it.bytes }
            var runs = entries.size
            for (entry in entries.asReversed()) {
                if (bytes <= retention.targetBytes && runs <= retention.maxRuns) break
                if (deleteHistory(entry)) {
                    bytes -= entry.bytes
                    runs--
                }
            }
            return HistoryResult(runs, bytes)
        }

        private fun ownedFiles(directory: Path, marker: Properties): List<Path>? = try {
            val files = Files.list(directory).use { it.toList() }
            val extensions = if (marker.getProperty("json") == "true") "(?:log|jsonl)" else "log"
            val logName = Regex("(?:engine|app)(?:\\.\\d{4}-\\d{2}-\\d{2}\\.\\d+)?\\.$extensions")
            files.takeIf { paths ->
                paths.all { path ->
                    Files.isRegularFile(path, NOFOLLOW_LINKS) &&
                        (
                            path.fileName.toString() in setOf(RUN_MANIFEST, RUN_LOCK) ||
                                logName.matches(path.fileName.toString())
                            )
                }
            }
        } catch (_: java.io.IOException) {
            null
        }

        private fun deleteHistory(entry: HistoryEntry): Boolean {
            val lease = Lease.tryOpen(entry.directory.resolve(RUN_LOCK)) ?: return false
            var deleted = false
            try {
                val marker = readManifest(entry.directory.resolve(RUN_MANIFEST)) ?: return false
                if (marker.getProperty("startedAt") != entry.startedAt ||
                    marker.getProperty("state") == "current"
                ) {
                    return false
                }
                val files = ownedFiles(entry.directory, marker) ?: return false
                // Windows cannot delete an open lock file; remove data while leased, then release the final handle.
                files.filter { it.fileName.toString() !in setOf(RUN_LOCK, RUN_MANIFEST) }.forEach(Files::delete)
                Files.delete(entry.directory.resolve(RUN_MANIFEST))
                deleted = true
            } catch (_: java.io.IOException) {
                return false
            } finally {
                lease.close()
                if (deleted) {
                    runCatching {
                        Files.deleteIfExists(entry.directory.resolve(RUN_LOCK))
                        Files.delete(entry.directory)
                    }
                }
            }
            return true
        }

        private fun manifest(runId: String, state: String, json: Boolean = false): Properties = Properties().apply {
            setProperty("schema", SCHEMA)
            setProperty("runId", runId)
            setProperty("state", state)
            setProperty("json", json.toString())
            setProperty("startedAt", java.time.Instant.now().toString())
        }

        private fun readManifest(path: Path): Properties? {
            return try {
                if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.size(path) > 8192) return null
                Properties().apply { Files.newInputStream(path, NOFOLLOW_LINKS).use(::load) }.takeIf {
                    it.getProperty("schema") == SCHEMA &&
                        runCatching { requireLogDirectoryName(it.getProperty("runId", "")) }.isSuccess &&
                        it.getProperty("state") in setOf("current", "active", "complete") &&
                        it.getProperty("json") in setOf("true", "false") &&
                        runCatching { java.time.Instant.parse(it.getProperty("startedAt")) }.isSuccess
                }
            } catch (_: java.io.IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun writeManifest(path: Path, manifest: Properties, new: Boolean = false) {
            if (new) {
                Files.newOutputStream(path, CREATE_NEW, WRITE).use { manifest.store(it, "Canopy-owned logs") }
                return
            }
            check(!Files.isSymbolicLink(path)) { "Log ownership marker must not be a symbolic link" }
            val temporary = path.resolveSibling(".canopy-manifest-${UUID.randomUUID()}.tmp")
            try {
                Files.newOutputStream(temporary, CREATE_NEW, WRITE).use { manifest.store(it, "Canopy-owned logs") }
                try {
                    Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, path, REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }

        private fun safeDirectory(path: Path): Path {
            val absolute = path.toAbsolutePath().normalize()
            check(!Files.isSymbolicLink(absolute)) { "Managed log directories must not be symbolic links: $absolute" }
            Files.createDirectories(absolute)
            check(Files.isDirectory(absolute, NOFOLLOW_LINKS)) { "Managed log path must be a directory: $absolute" }
            // Resolve user-selected ancestors once (e.g. a symlinked home or macOS /tmp); never follow managed children.
            return absolute.toRealPath()
        }
    }

    private class Lease private constructor(private val channel: FileChannel, private val lock: FileLock) :
        AutoCloseable {
        override fun close() {
            try {
                lock.release()
            } finally {
                channel.close()
            }
        }

        companion object {
            fun tryOpen(path: Path): Lease? {
                if (Files.exists(path, NOFOLLOW_LINKS) && !Files.isRegularFile(path, NOFOLLOW_LINKS)) return null
                val channel = try {
                    FileChannel.open(path, CREATE, WRITE, NOFOLLOW_LINKS)
                } catch (_: java.io.IOException) {
                    return null
                }
                return try {
                    val lock = try {
                        channel.tryLock()
                    } catch (_: OverlappingFileLockException) {
                        null
                    }
                    if (lock == null) {
                        channel.close()
                        null
                    } else {
                        Lease(channel, lock)
                    }
                } catch (failure: Throwable) {
                    channel.close()
                    throw failure
                }
            }
        }
    }
}
