package com.amfaro.jarify.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sanity checks on the persisted `BaseState` shape so a typo in the
 * `onlyForDuckDb` default or property delegate doesn't slip through. Full
 * `JarifySettings`/`JarifyConfigurable` round-trip needs a `BasePlatformTestCase`
 * harness that this module doesn't currently set up.
 */
class JarifySettingsStateTest {

    @Test fun `defaults match the documented opt-out spec`() {
        val state = JarifySettings.State()
        assertEquals("jarify", state.executable)
        assertEquals("", state.configPath)
        assertTrue("onlyForDuckDb must default to true (opt-out)", state.onlyForDuckDb)
    }

    @Test fun `onlyForDuckDb round-trips through the property delegate`() {
        val state = JarifySettings.State()
        state.onlyForDuckDb = false
        assertEquals(false, state.onlyForDuckDb)
        state.onlyForDuckDb = true
        assertEquals(true, state.onlyForDuckDb)
    }
}
