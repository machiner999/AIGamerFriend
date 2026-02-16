package com.example.aigamerfriend.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aigamerfriend.ui.component.AIFace
import com.example.aigamerfriend.ui.component.CameraPreview
import com.example.aigamerfriend.ui.component.StatusOverlay
import com.example.aigamerfriend.util.PermissionHelper
import androidx.annotation.VisibleForTesting
import com.example.aigamerfriend.model.Emotion
import com.example.aigamerfriend.viewmodel.GamerViewModel
import com.example.aigamerfriend.viewmodel.SessionState

@VisibleForTesting
internal fun isSessionActive(state: SessionState): Boolean =
    state is SessionState.Connected ||
        state is SessionState.Connecting ||
        state is SessionState.Reconnecting

@VisibleForTesting
internal enum class HapticType { CONFIRM, REJECT, TICK }

@VisibleForTesting
internal fun hapticForSessionTransition(previous: SessionState, current: SessionState): HapticType? =
    when {
        current is SessionState.Connected && previous !is SessionState.Connected -> HapticType.CONFIRM
        current is SessionState.Error && previous !is SessionState.Error -> HapticType.REJECT
        else -> null
    }

@VisibleForTesting
internal fun hapticForEmotionChange(previous: Emotion, current: Emotion): HapticType? =
    if (current != previous) HapticType.TICK else null

private fun View.performHaptic(type: HapticType) {
    val constant = when (type) {
        HapticType.CONFIRM ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.KEYBOARD_TAP
        HapticType.REJECT ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
        HapticType.TICK -> HapticFeedbackConstants.CLOCK_TICK
    }
    performHapticFeedback(constant)
}

@Composable
fun GamerScreen(viewModel: GamerViewModel = viewModel()) {
    val context = LocalContext.current
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val currentEmotion by viewModel.currentEmotion.collectAsStateWithLifecycle()
    var hasPermissions by remember { mutableStateOf(PermissionHelper.hasAllPermissions(context)) }

    LifecycleResumeEffect(Unit) {
        hasPermissions = PermissionHelper.hasAllPermissions(context)
        onPauseOrDispose {}
    }

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

    // Haptic feedback for state changes
    val view = LocalView.current
    var previousSessionState by remember { mutableStateOf(sessionState) }
    LaunchedEffect(sessionState) {
        hapticForSessionTransition(previousSessionState, sessionState)?.let { view.performHaptic(it) }
        previousSessionState = sessionState
    }
    var previousEmotion by remember { mutableStateOf(currentEmotion) }
    LaunchedEffect(currentEmotion) {
        hapticForEmotionChange(previousEmotion, currentEmotion)?.let { view.performHaptic(it) }
        previousEmotion = currentEmotion
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        if (hasPermissions) {
            // Full-screen camera preview
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onFrameCaptured = { bitmap -> viewModel.sendVideoFrame(bitmap) },
            )

            // Status overlay (top-left, respects safe area)
            StatusOverlay(
                state = sessionState,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(16.dp),
            )

            // AI Face (above the control panel)
            androidx.compose.animation.AnimatedVisibility(
                visible = sessionState is SessionState.Connected,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp),
            ) {
                AIFace(emotion = currentEmotion)
            }

            // Glass control panel at bottom
            GlassControlPanel(
                state = sessionState,
                onStart = { viewModel.startSession() },
                onStop = { viewModel.stopSession() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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
private fun StatusText(state: SessionState, modifier: Modifier = Modifier) {
    val (text, color) =
        when (state) {
            is SessionState.Idle -> "タップしてゲーム友達を呼ぼう" to Color.White.copy(alpha = 0.7f)
            is SessionState.Connecting -> "接続中..." to Color(0xFFFFD600)
            is SessionState.Connected -> "ゲーム友達が見てるよ！" to Color(0xFF00E676)
            is SessionState.Reconnecting -> "再接続中..." to Color(0xFFFFD600)
            is SessionState.Error -> state.message to Color(0xFFFF1744)
        }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        textAlign = TextAlign.Start,
        modifier = modifier,
    )
}

@Composable
private fun GlassControlPanel(
    state: SessionState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatusText(
            state = state,
            modifier = Modifier.weight(1f),
        )

        SessionButton(
            state = state,
            onStart = onStart,
            onStop = onStop,
        )
    }
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
