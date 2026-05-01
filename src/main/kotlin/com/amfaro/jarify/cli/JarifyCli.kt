package com.amfaro.jarify.cli

import com.amfaro.jarify.settings.JarifySettings
import com.intellij.util.EnvironmentUtil
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CliResult(val exitCode: Int, val stdout: String, val stderr: String)

object JarifyCli {

    fun executable(): String = JarifySettings.getInstance().executable

    fun buildConfigArgs(): List<String> {
        val configPath = JarifySettings.getInstance().configPath
        return if (configPath != null) listOf("--config", configPath) else emptyList()
    }

    fun runWithStdin(args: List<String>, stdin: String, timeoutMillis: Long = 10_000L): CliResult {
        val cmd = listOf(executable()) + args
        return try {
            val proc = ProcessBuilder(cmd)
                .apply { environment().putAll(EnvironmentUtil.getEnvironmentMap()) }
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
        val proc = ProcessBuilder(executable(), "--version")
                .apply { environment().putAll(EnvironmentUtil.getEnvironmentMap()) }
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
