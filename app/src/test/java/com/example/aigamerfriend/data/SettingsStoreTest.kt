package com.example.aigamerfriend.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val counter = AtomicInteger(0)

    private fun runSettingsTest(block: suspend TestScope.(SettingsStore) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            val testFile = java.io.File(tmpFolder.root, "settings_${counter.getAndIncrement()}.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher() + Job()),
                produceFile = { testFile },
            )
            val store = SettingsStore(dataStore)
            block(store)
        }

    @Test
    fun `onboarding initially not shown`() = runSettingsTest { store ->
        assertFalse(store.isOnboardingShown())
    }

    @Test
    fun `setOnboardingShown marks as shown`() = runSettingsTest { store ->
        store.setOnboardingShown()
        assertTrue(store.isOnboardingShown())
    }

    @Test
    fun `default voice name is AOEDE`() = runSettingsTest { store ->
        assertEquals("AOEDE", store.voiceNameFlow().first())
    }

    @Test
    fun `setVoiceName updates flow`() = runSettingsTest { store ->
        store.setVoiceName("KORE")
        assertEquals("KORE", store.voiceNameFlow().first())
    }

    @Test
    fun `default reaction intensity is normal`() = runSettingsTest { store ->
        assertEquals("ふつう", store.reactionIntensityFlow().first())
    }

    @Test
    fun `setReactionIntensity updates flow`() = runSettingsTest { store ->
        store.setReactionIntensity("テンション高め")
        assertEquals("テンション高め", store.reactionIntensityFlow().first())
    }
}
