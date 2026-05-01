package com.amfaro.jarify.install

import com.amfaro.jarify.cli.JarifyCli
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.TimeUnit

/**
 * On project open, verify that the configured `jarify` binary is callable.
 * If not, surface a notification offering to run `uv tool install jarify`.
 */
class JarifyInstallStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (JarifyCli.isAvailable()) return
        ApplicationManager.getApplication().invokeLater {
            notifyMissing(project)
        }
    }

    private fun notifyMissing(project: Project) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup("Jarify")
        val notification = group.createNotification(
            "Jarify not found",
            "The <code>jarify</code> CLI was not found. Install via <code>uv tool install jarify</code>?",
            NotificationType.INFORMATION,
        )
        notification.addAction(NotificationAction.createSimple("Install with uv") {
            notification.expire()
            runInstall(project)
        })
        notification.addAction(NotificationAction.createSimple("Open settings") {
            notification.expire()
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "Jarify")
        })
        notification.notify(project)
    }

    private fun runInstall(project: Project) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Installing jarify…", true) {
            override fun run(indicator: ProgressIndicator) {
                val (code, output) = try {
                    val proc = ProcessBuilder("uv", "tool", "install", "jarify")
                        .redirectErrorStream(true)
                        .start()
                    val finished = proc.waitFor(120, TimeUnit.SECONDS)
                    if (!finished) {
                        proc.destroyForcibly()
                        -1 to "uv tool install timed out after 120s"
                    } else {
                        proc.exitValue() to proc.inputStream.bufferedReader().readText()
                    }
                } catch (e: Exception) {
                    -1 to (e.message ?: "failed to spawn uv")
                }
                val group = NotificationGroupManager.getInstance().getNotificationGroup("Jarify")
                if (code == 0) {
                    group.createNotification(
                        "jarify installed",
                        "jarify was installed via <code>uv</code>.",
                        NotificationType.INFORMATION,
                    ).notify(project)
                } else {
                    group.createNotification(
                        "jarify install failed",
                        "Install manually with: <code>uv tool install jarify</code><br/><br/><pre>$output</pre>",
                        NotificationType.ERROR,
                    ).notify(project)
                }
            }
        })
    }
}
