package com.amfaro.jarify.cli

import com.amfaro.jarify.settings.JarifySettings
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CliResult(val exitCode: Int, val stdout: String, val stderr: String)

object JarifyCli {

    fun executable(): String = JarifySettings.getInstance().executable

    fun buildConfigArgs(): List<String> {
        val configPath = JarifySettings.getInstance().configPath
        return if (configPath != null) listOf("--config", configPath) else emptyList()
    }

    /**
     * Resolves [name] to an absolute path via the user's login shell (`$SHELL -l -c "command -v <name>"`).
     * Falls back to [name] unchanged if the shell probe fails (e.g. the user set an absolute path).
     */
    fun resolveExecutable(name: String): String {
        if (name.startsWith("/")) return name          // already absolute
        val shell = System.getenv("SHELL")?.takeIf { it.isNotBlank() } ?: return name
        return try {
            val proc = ProcessBuilder(shell, "-l", "-c", "command -v $name")
                .redirectErrorStream(true)
                .start()
            if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                return name
            }
            val resolved = proc.inputStream.bufferedReader().readText().trim()
            if (proc.exitValue() == 0 && resolved.isNotEmpty()) resolved else name
        } catch (_: Exception) {
            name
        }
    }

    fun runWithStdin(args: List<String>, stdin: String, timeoutMillis: Long = 10_000L): CliResult {
        val cmd = listOf(resolveExecutable(executable())) + args
        return try {
            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start()

            // Drain stdout/stderr concurrently to avoid deadlocking on the
            // ~64 KB OS pipe buffer when jarify emits large output.
            val stdoutFuture = drainAsync(proc.inputStream)
            val stderrFuture = drainAsync(proc.errorStream)

            try {
                proc.outputStream.use { it.write(stdin.toByteArray(Charsets.UTF_8)) }
            } catch (_: IOException) {
                // EPIPE: process closed stdin early — non-fatal
            }

            val finished = proc.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return CliResult(-1, "", "jarify timed out after ${timeoutMillis}ms")
            }
            val stdout = stdoutFuture.get(timeoutMillis, TimeUnit.MILLISECONDS)
            val stderr = stderrFuture.get(timeoutMillis, TimeUnit.MILLISECONDS)
            CliResult(proc.exitValue(), stdout, stderr)
        } catch (e: IOException) {
            CliResult(-1, "", e.message ?: "failed to spawn ${cmd.joinToString(" ")}")
        }
    }

    private fun drainAsync(stream: java.io.InputStream): java.util.concurrent.Future<String> {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "jarify-stream-drain").apply { isDaemon = true }
        }
        return try {
            executor.submit<String> { stream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
        } finally {
            executor.shutdown()
        }
    }

    fun isAvailable(): Boolean = try {
        val proc = ProcessBuilder(resolveExecutable(executable()), "--version")
            .redirectErrorStream(true)
            .start()
        if (!proc.waitFor(5, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            false
        } else {
            proc.exitValue() == 0
        }
    } catch (_: Exception) {
        false
    }
}
