package com.example.aigamerfriend.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsStore(private val dataStore: DataStore<Preferences>) {
    companion object {
        private val KEY_ONBOARDING_SHOWN = booleanPreferencesKey("onboarding_shown")
        private val KEY_VOICE_NAME = stringPreferencesKey("voice_name")
        private val KEY_REACTION_INTENSITY = stringPreferencesKey("reaction_intensity")
    }

    suspend fun isOnboardingShown(): Boolean =
        dataStore.data.map { it[KEY_ONBOARDING_SHOWN] ?: false }.first()

    suspend fun setOnboardingShown() {
        dataStore.edit { it[KEY_ONBOARDING_SHOWN] = true }
    }

    fun voiceNameFlow(): Flow<String> =
        dataStore.data.map { it[KEY_VOICE_NAME] ?: "AOEDE" }

    suspend fun setVoiceName(name: String) {
        dataStore.edit { it[KEY_VOICE_NAME] = name }
    }

    fun reactionIntensityFlow(): Flow<String> =
        dataStore.data.map { it[KEY_REACTION_INTENSITY] ?: "ふつう" }

    suspend fun setReactionIntensity(intensity: String) {
        dataStore.edit { it[KEY_REACTION_INTENSITY] = intensity }
    }
}
