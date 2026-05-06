package com.amfaro.jarify.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "JarifySettings", storages = [Storage("jarify.xml")])
@Service(Service.Level.APP)
class JarifySettings : SimplePersistentStateComponent<JarifySettings.State>(State()) {

    class State : BaseState() {
        var executable by string("jarify")
        var configPath by string("")
        var onlyForDuckDb by property(true)
    }

    val executable: String
        get() = state.executable?.takeIf { it.isNotBlank() } ?: "jarify"

    val configPath: String?
        get() = state.configPath?.takeIf { it.isNotBlank() }

    val onlyForDuckDb: Boolean
        get() = state.onlyForDuckDb

    fun update(executable: String, configPath: String, onlyForDuckDb: Boolean) {
        state.executable = executable.ifBlank { "jarify" }
        state.configPath = configPath
        state.onlyForDuckDb = onlyForDuckDb
    }

    companion object {
        fun getInstance(): JarifySettings = service()
    }
}
