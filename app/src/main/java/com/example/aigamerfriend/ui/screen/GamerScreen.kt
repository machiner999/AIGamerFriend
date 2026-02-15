package com.example.aigamerfriend.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aigamerfriend.ui.component.AIFace
import com.example.aigamerfriend.ui.component.CameraPreview
import com.example.aigamerfriend.ui.component.StatusOverlay
import com.example.aigamerfriend.util.PermissionHelper
import androidx.annotation.VisibleForTesting
import com.example.aigamerfriend.viewmodel.GamerViewModel
import com.example.aigamerfriend.viewmodel.SessionState

@VisibleForTesting
internal fun isSessionActive(state: SessionState): Boolean =
    state is SessionState.Connected ||
        state is SessionState.Connecting ||
        state is SessionState.Reconnecting

@Composable
fun GamerScreen(viewModel: GamerViewModel = viewModel()) {
    val context = LocalContext.current
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val currentEmotion by viewModel.currentEmotion.collectAsStateWithLifecycle()
    var hasPermissions by remember { mutableStateOf(PermissionHelper.hasAllPermissions(context)) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            hasPermissions = permissions.values.all { it }
        }

    // Stop session when leaving the screen
    DisposableEffect(Unit) {
        onDispose { viewModel.stopSession() }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        if (hasPermissions) {
            // Camera preview area (70%)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.7f),
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onFrameCaptured = { bitmap -> viewModel.sendVideoFrame(bitmap) },
                )

                StatusOverlay(
                    state = sessionState,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = sessionState is SessionState.Connected,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    AIFace(
                        emotion = currentEmotion,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }

            // Control area (30%)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.3f)
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                StatusText(sessionState)

                Spacer(modifier = Modifier.height(16.dp))

                SessionButton(
                    state = sessionState,
                    onStart = { viewModel.startSession() },
                    onStop = { viewModel.stopSession() },
                )
            }
        } else {
            // Permission request view
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "カメラとマイクの許可が必要です",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "TVに映るゲーム画面を撮影し、友達のようにリアクションするために、カメラとマイクへのアクセスが必要です。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
                    },
                ) {
                    Text("許可する")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        context.startActivity(intent)
                    },
                ) {
                    Text("設定を開く")
                }
            }
        }
    }
}

@Composable
private fun StatusText(state: SessionState) {
    val (text, color) =
        when (state) {
            is SessionState.Idle -> "タップしてゲーム友達を呼ぼう" to MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            is SessionState.Connecting -> "接続中..." to Color(0xFFFFD600)
            is SessionState.Connected -> "ゲーム友達が見てるよ！" to Color(0xFF00E676)
            is SessionState.Reconnecting -> "再接続中..." to Color(0xFFFFD600)
            is SessionState.Error -> (state as SessionState.Error).message to Color(0xFFFF1744)
        }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SessionButton(
    state: SessionState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val isActive = isSessionActive(state)

    Button(
        onClick = if (isActive) onStop else onStart,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (isActive) Color(0xFFFF1744) else MaterialTheme.colorScheme.primary,
            ),
    ) {
        Text(
            text = if (isActive) "終了" else "開始",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
