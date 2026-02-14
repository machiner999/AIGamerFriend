package com.example.aigamerfriend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.aigamerfriend.ui.screen.GamerScreen
import com.example.aigamerfriend.ui.theme.AIGamerFriendTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIGamerFriendTheme {
                GamerScreen()
            }
        }
    }
}
