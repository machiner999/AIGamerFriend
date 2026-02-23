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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aigamerfriend.BuildConfig
import com.example.aigamerfriend.ui.component.AIFace
import com.example.aigamerfriend.ui.component.CameraPreview
import com.example.aigamerfriend.ui.component.StatusOverlay
import com.example.aigamerfriend.util.PermissionHelper
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import com.example.aigamerfriend.model.Emotion
import com.example.aigamerfriend.viewmodel.GamerViewModel
import com.example.aigamerfriend.viewmodel.SessionState

private val NeonGreen = Color(0xFF00E676)
private val NeonBlue = Color(0xFF00D4FF)

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

@OptIn(ExperimentalMaterial3Api::class)
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
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(16.dp),
            ) {
                StatusOverlay(
                    state = sessionState,
                    isDelayed = isResponseDelayed,
                )

                // Game name label
                AnimatedVisibility(
                    visible = gameName != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = gameName ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonBlue,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // AI Face (above the control panel)
            AnimatedVisibility(
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
                isMuted = isMuted,
                onToggleMute = { viewModel.toggleMute() },
                audioLevel = audioLevel,
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

    // Settings bottom sheet
    if (showSettings) {
        SettingsBottomSheet(
            voiceName = voiceName,
            onVoiceNameChange = { viewModel.setVoiceName(it) },
            reactionIntensity = reactionIntensity,
            onReactionIntensityChange = { viewModel.setReactionIntensity(it) },
            onClearMemory = { viewModel.clearMemory() },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun OnboardingOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(24.dp),
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "ゲーム画面にカメラを向けよう",
                style = MaterialTheme.typography.titleLarge,
                color = NeonGreen,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "TVやモニターのゲーム画面を背面カメラで撮影すると、AIの友達「ユウ」が音声でリアクションするよ！",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "タップして閉じる",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun AudioLevelIndicator(level: Float, modifier: Modifier = Modifier) {
    val barHeights = listOf(0.3f, 0.6f, 0.8f, 1.0f)
    val maxHeight = 20.dp
    val minHeight = 6.dp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        barHeights.forEach { threshold ->
            val targetHeight = if (level >= threshold) {
                minHeight + (maxHeight - minHeight) * (level / 1.0f).coerceAtMost(1f)
            } else {
                minHeight
            }
            val animatedHeight by animateFloatAsState(
                targetValue = targetHeight.value,
                label = "barHeight",
            )
            val isActive = level >= threshold * 0.5f
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isActive) NeonGreen else Color.White.copy(alpha = 0.2f)),
            )
        }
    }
}

@Composable
private fun StatusText(state: SessionState, modifier: Modifier = Modifier) {
    val (text, color) =
        when (state) {
            is SessionState.Idle -> "タップしてゲーム友達を呼ぼう" to Color.White.copy(alpha = 0.7f)
            is SessionState.Connecting -> "接続中..." to Color(0xFFFFD600)
            is SessionState.Connected -> "ゲーム友達が見てるよ！" to NeonGreen
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
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    audioLevel: Float,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = isSessionActive(state)

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

        if (isActive) {
            AudioLevelIndicator(
                level = audioLevel,
                modifier = Modifier.padding(end = 8.dp),
            )

            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMuted) "ミュート解除" else "ミュート",
                    tint = if (isMuted) Color(0xFFFF1744) else Color.White,
                )
            }
        }

        SessionButton(
            state = state,
            onStart = onStart,
            onStop = onStop,
        )

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "設定",
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsBottomSheet(
    voiceName: String,
    onVoiceNameChange: (String) -> Unit,
    reactionIntensity: String,
    onReactionIntensityChange: (String) -> Unit,
    onClearMemory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showClearConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "設定",
                style = MaterialTheme.typography.titleLarge,
                color = NeonGreen,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Voice selection
            Text(
                text = "声の種類",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val voices = listOf("AOEDE", "KORE", "PUCK", "CHARON", "FENRIR")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                voices.forEach { voice ->
                    FilterChip(
                        selected = voiceName == voice,
                        onClick = { onVoiceNameChange(voice) },
                        label = { Text(voice) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = NeonGreen,
                            labelColor = Color.White.copy(alpha = 0.7f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.White.copy(alpha = 0.2f),
                            selectedBorderColor = NeonGreen,
                            enabled = true,
                            selected = voiceName == voice,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Reaction intensity
            Text(
                text = "リアクションの強さ",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val intensities = listOf("おとなしめ", "ふつう", "テンション高め")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                intensities.forEach { intensity ->
                    FilterChip(
                        selected = reactionIntensity == intensity,
                        onClick = { onReactionIntensityChange(intensity) },
                        label = { Text(intensity) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = NeonGreen,
                            labelColor = Color.White.copy(alpha = 0.7f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.White.copy(alpha = 0.2f),
                            selectedBorderColor = NeonGreen,
                            enabled = true,
                            selected = reactionIntensity == intensity,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(20.dp))

            // Clear memory
            OutlinedButton(
                onClick = { showClearConfirm = true },
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFF1744)),
                ),
            ) {
                Text(
                    text = "記憶をクリア",
                    color = Color(0xFFFF1744),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Version
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("記憶をクリア") },
            text = { Text("過去のセッションの記憶をすべて削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearMemory()
                        showClearConfirm = false
                    },
                ) {
                    Text("クリア", color = Color(0xFFFF1744))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("キャンセル")
                }
            },
        )
    }
}
