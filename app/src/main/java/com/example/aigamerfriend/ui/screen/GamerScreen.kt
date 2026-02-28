package com.example.aigamerfriend.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import com.example.aigamerfriend.model.Emotion
import com.example.aigamerfriend.ui.component.AIFace
import com.example.aigamerfriend.ui.component.CameraPreview
import com.example.aigamerfriend.ui.component.GlassControlPanel
import com.example.aigamerfriend.ui.component.OnboardingOverlay
import com.example.aigamerfriend.ui.component.SettingsBottomSheet
import com.example.aigamerfriend.ui.component.StatusOverlay
import com.example.aigamerfriend.ui.component.glassPanel
import com.example.aigamerfriend.ui.theme.NeonBlue
import com.example.aigamerfriend.ui.theme.NeonGreen
import com.example.aigamerfriend.ui.theme.StatusWarning
import com.example.aigamerfriend.ui.theme.accentColor
import com.example.aigamerfriend.util.PermissionHelper
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
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val gameName by viewModel.gameName.collectAsStateWithLifecycle()
    val isResponseDelayed by viewModel.isResponseDelayed.collectAsStateWithLifecycle()
    val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()
    val voiceName by viewModel.voiceName.collectAsStateWithLifecycle()
    val reactionIntensity by viewModel.reactionIntensity.collectAsStateWithLifecycle()
    val autoStart by viewModel.autoStart.collectAsStateWithLifecycle()
    var hasPermissions by remember { mutableStateOf(PermissionHelper.hasAllPermissions(context)) }
    var showSettings by remember { mutableStateOf(false) }

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

    // Keep screen on while session is active
    val activity = context as? ComponentActivity
    val keepScreenOn = isSessionActive(sessionState)
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Stop session when leaving the screen
    DisposableEffect(Unit) {
        onDispose { viewModel.stopSession() }
    }

    // Auto-start session when enabled and permissions are granted
    LaunchedEffect(hasPermissions, autoStart) {
        if (hasPermissions && autoStart && sessionState is SessionState.Idle) {
            viewModel.startSession()
        }
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
        modifier = Modifier
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
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            ) {
                StatusOverlay(
                    state = sessionState,
                    isDelayed = isResponseDelayed,
                )

                // Game name label with slide-in + gradient background
                AnimatedVisibility(
                    visible = gameName != null,
                    enter = fadeIn() + slideInHorizontally { -it },
                    exit = fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .glassPanel(
                                shape = RoundedCornerShape(16.dp),
                                bgAlpha = 0.6f,
                                borderAlpha = 0.1f,
                            )
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        NeonBlue.copy(alpha = 0.15f),
                                        Color.Transparent,
                                    ),
                                ),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = gameName ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonBlue,
                        )
                    }
                }
            }

            // Connecting pulse ring (shown at AIFace position while connecting)
            AnimatedVisibility(
                visible = sessionState is SessionState.Connecting,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
            ) {
                ConnectingPulseRing()
            }

            // AI Face (above the control panel) with enhanced entrance
            AnimatedVisibility(
                visible = sessionState is SessionState.Connected,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.3f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
                ),
                exit = fadeOut() + scaleOut(targetScale = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
            ) {
                AIFace(
                    emotion = currentEmotion,
                    audioLevel = audioLevel,
                )
            }

            // Glass control panel at bottom
            GlassControlPanel(
                state = sessionState,
                isMuted = isMuted,
                onToggleMute = { viewModel.toggleMute() },
                audioLevel = audioLevel,
                currentEmotion = currentEmotion,
                onStart = { viewModel.startSession() },
                onStop = { viewModel.stopSession() },
                onOpenSettings = { showSettings = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // Onboarding overlay
            AnimatedVisibility(
                visible = showOnboarding,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                OnboardingOverlay(onDismiss = { viewModel.dismissOnboarding() })
            }
        } else {
            // Permission request view
            Column(
                modifier = Modifier
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

    // Settings bottom sheet
    if (showSettings) {
        SettingsBottomSheet(
            voiceName = voiceName,
            onVoiceNameChange = { viewModel.setVoiceName(it) },
            reactionIntensity = reactionIntensity,
            onReactionIntensityChange = { viewModel.setReactionIntensity(it) },
            autoStart = autoStart,
            onAutoStartChange = { viewModel.setAutoStart(it) },
            onClearMemory = { viewModel.clearMemory() },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun ConnectingPulseRing() {
    val transition = rememberInfiniteTransition(label = "connectPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "connectPulseScale",
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "connectPulseAlpha",
    )

    Box(
        modifier = Modifier
            .size(180.dp)
            .drawBehind {
                val radius = size.minDimension / 2f * pulseScale
                drawCircle(
                    color = StatusWarning.copy(alpha = pulseAlpha),
                    radius = radius,
                    style = Stroke(width = 2.dp.toPx()),
                )
            },
    )
}
