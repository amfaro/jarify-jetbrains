package com.amfaro.jarify.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JTextField

class JarifyConfigurable : Configurable {

    private val settings = JarifySettings.getInstance()
    private val executableField = JTextField()
    private val configPathField = JTextField()

    override fun getDisplayName(): String = "Jarify"

    override fun createComponent(): JComponent {
        executableField.text = settings.state.executable ?: "jarify"
        configPathField.text = settings.state.configPath ?: ""
        return panel {
            row("Executable:") {
                cell(executableField).align(AlignX.FILL).resizableColumn()
                    .comment("Path to the <code>jarify</code> binary. Defaults to <code>jarify</code> on \$PATH.")
            }
            row("Config path:") {
                cell(configPathField).align(AlignX.FILL).resizableColumn()
                    .comment("Optional path to a <code>jarify.toml</code> configuration file.")
            }
        }
    }

    override fun isModified(): Boolean =
        executableField.text != (settings.state.executable ?: "jarify") ||
            configPathField.text != (settings.state.configPath ?: "")

    override fun apply() {
        settings.update(executableField.text, configPathField.text)
    }

    override fun reset() {
        executableField.text = settings.state.executable ?: "jarify"
        configPathField.text = settings.state.configPath ?: ""
    }
}
