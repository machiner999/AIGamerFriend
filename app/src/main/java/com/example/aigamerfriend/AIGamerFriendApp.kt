package com.example.aigamerfriend

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.memoryDataStore by preferencesDataStore(name = "session_memory")
val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

class AIGamerFriendApp : Application()
