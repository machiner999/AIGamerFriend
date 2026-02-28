package com.example.aigamerfriend.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.aigamerfriend.BuildConfig
import com.example.aigamerfriend.ui.theme.NeonBlue
import com.example.aigamerfriend.ui.theme.NeonGreen
import com.example.aigamerfriend.ui.theme.StatusError

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsBottomSheet(
    voiceName: String,
    onVoiceNameChange: (String) -> Unit,
    reactionIntensity: String,
    onReactionIntensityChange: (String) -> Unit,
    autoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onClearMemory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showClearConfirm by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val sectionShape = RoundedCornerShape(16.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121212),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "設定",
                style = MaterialTheme.typography.titleLarge,
                color = NeonGreen,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Voice selection section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(shape = sectionShape, bgAlpha = 0.3f, borderAlpha = 0.06f)
                    .padding(16.dp),
            ) {
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
                                selectedContainerColor = NeonGreen.copy(alpha = 0.3f),
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reaction intensity section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(shape = sectionShape, bgAlpha = 0.3f, borderAlpha = 0.06f)
                    .padding(16.dp),
            ) {
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
                                selectedContainerColor = NeonGreen.copy(alpha = 0.3f),
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto-start section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(shape = sectionShape, bgAlpha = 0.3f, borderAlpha = 0.06f)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自動スタート",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "アプリ起動時に自動でセッションを開始",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
                Switch(
                    checked = autoStart,
                    onCheckedChange = onAutoStartChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonGreen,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Clear memory
            OutlinedButton(
                onClick = { showClearConfirm = true },
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(StatusError),
                ),
            ) {
                Text(
                    text = "記憶をクリア",
                    color = StatusError,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = { showHelp = true }) {
                Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = NeonBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("使い方ガイド", color = NeonBlue)
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                    Text("クリア", color = StatusError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("キャンセル")
                }
            },
        )
    }

    if (showHelp) {
        HelpBottomSheet(onDismiss = { showHelp = false })
    }
}
