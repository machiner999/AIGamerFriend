package com.example.aigamerfriend.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aigamerfriend.ui.theme.NeonBlue
import com.example.aigamerfriend.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HelpBottomSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF121212),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "使い方ガイド",
                style = MaterialTheme.typography.titleLarge,
                color = NeonGreen,
            )

            Spacer(modifier = Modifier.height(8.dp))

            HelpText(
                "ゲーム画面にカメラを向けるだけで、AIの友達「ユウ」が音声でリアクションしてくれるアプリです。" +
                    "一人でゲームをしていても、隣に友達がいるような感覚で楽しめます。",
            )

            HelpSectionTitle("必要な環境")
            HelpBullet("Android 8.0 以上のスマートフォン")
            HelpBullet("インターネット接続（Wi-Fi推奨）")
            HelpBullet("カメラとマイクの使用許可")

            HelpSectionTitle("はじめかた")
            HelpText("1. アプリを起動する")
            HelpText("初回起動時、カメラとマイクの使用許可を求められます。「許可する」をタップしてください。")
            HelpNote("もし許可し忘れた場合は、端末の「設定」→「アプリ」→「AIGamerFriend」→「権限」からカメラとマイクを許可できます。")
            Spacer(modifier = Modifier.height(8.dp))
            HelpText("2. オンボーディング画面")
            HelpText("初回起動時に案内画面が表示されます。内容を確認したら、画面をタップして閉じてください。")

            HelpSectionTitle("基本的な使い方")
            HelpText("画面下部の「開始」ボタンをタップすると、ユウとのセッションが始まります。")
            HelpNote("設定で「自動スタート」をオンにすると、アプリ起動時に自動でセッションが開始されます。")
            Spacer(modifier = Modifier.height(8.dp))
            HelpText("スマートフォンの背面カメラをTVやモニターのゲーム画面に向けてください。ユウがゲーム画面を見て、音声でリアクションしてくれます。")
            Spacer(modifier = Modifier.height(8.dp))
            HelpText("マイクが有効な状態なら、ユウに話しかけることもできます。ゲームの感想を言ったり、質問したりしてみましょう。")
            Spacer(modifier = Modifier.height(8.dp))
            HelpText("ユウはゲームに関する情報をWEB検索で調べることもできます。攻略法やキャラクターの情報など、気になることを聞いてみてください。")
            Spacer(modifier = Modifier.height(8.dp))
            HelpText("ミュートボタン（マイクのアイコン）をタップすると、マイクを一時的にオフにできます。")

            Spacer(modifier = Modifier.height(12.dp))
            HelpText("音声コマンド（ハンズフリー操作）:")
            HelpTable(
                headers = listOf("言葉の例", "操作"),
                rows = listOf(
                    listOf("「終わり」「やめる」「ストップ」", "セッション終了"),
                    listOf("「ミュート」「黙って」「静かにして」", "ミュート切替"),
                ),
            )
            HelpNote("ゲームの文脈での「終わり」には反応しません。ミュート中は音声が届かないため、ミュート解除は画面タッチで行ってください。")

            Spacer(modifier = Modifier.height(8.dp))
            HelpText("「終了」ボタンをタップするか、「終わり」「ストップ」などと声をかけるとセッションが終了します。")

            HelpSectionTitle("画面の見かた")
            HelpTable(
                headers = listOf("場所", "表示内容"),
                rows = listOf(
                    listOf("左上", "接続状態（LIVE / 接続中 / 再接続中 / エラー）"),
                    listOf("左上の下", "検出されたゲーム名（検出時のみ）"),
                    listOf("中央下", "ユウの顔（感情で表情が変化）"),
                    listOf("最下部", "コントロールパネル"),
                ),
            )

            HelpSectionTitle("ユウの表情について")
            HelpText("ユウはゲームの展開に応じて7つの表情を見せてくれます。")
            HelpTable(
                headers = listOf("表情", "どんなとき"),
                rows = listOf(
                    listOf("ふつう", "落ち着いている状態"),
                    listOf("うれしい", "楽しいシーンや良いプレイのとき"),
                    listOf("大興奮", "すごいプレイや盛り上がる場面で"),
                    listOf("おどろき", "予想外の展開に驚いたとき"),
                    listOf("考え中", "状況を分析しているとき"),
                    listOf("心配", "ピンチの場面や不安なとき"),
                    listOf("かなしい", "残念な結果や負けたとき"),
                ),
            )

            HelpSectionTitle("設定")
            HelpText("画面下部の歯車アイコンをタップすると設定画面が開きます。")
            Spacer(modifier = Modifier.height(8.dp))
            HelpText("声の種類: AOEDE（デフォルト）、KORE、PUCK、CHARON、FENNIRの5種類から選べます。")
            Spacer(modifier = Modifier.height(4.dp))
            HelpText("リアクションの強さ: おとなしめ / ふつう（デフォルト）/ テンション高めの3段階。")
            Spacer(modifier = Modifier.height(4.dp))
            HelpText("自動スタート: オンにすると、アプリ起動時に自動でセッションが開始されます。")
            Spacer(modifier = Modifier.height(4.dp))
            HelpText("記憶のクリア: 過去のセッションでユウが覚えた内容をすべて削除できます。")

            HelpSectionTitle("自動再接続について")
            HelpText("長時間使っていると、通信が一時的に切れることがあります。通常は自動で再接続されるので、そのままお待ちください。")

            HelpSectionTitle("トラブルシューティング")
            HelpText("「エラー」と表示される・接続できない:")
            HelpBullet("インターネット接続を確認してください（Wi-Fi推奨）")
            HelpBullet("「開始」ボタンを再度タップしてみてください")
            HelpBullet("アプリを再起動してみてください")
            Spacer(modifier = Modifier.height(8.dp))
            HelpText("ユウが反応しない:")
            HelpBullet("ミュートになっていないか確認してください")
            HelpBullet("背面カメラがゲーム画面をしっかり映しているか確認してください")
            HelpBullet("セッションが開始されているか（LIVE表示）確認してください")
            Spacer(modifier = Modifier.height(8.dp))
            HelpText("音が聞こえない:")
            HelpBullet("端末の音量が十分か確認してください")
            HelpBullet("セッションがLIVE状態か確認してください")
        }
    }
}

@Composable
private fun HelpSectionTitle(title: String) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = NeonGreen,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun HelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun HelpBullet(text: String) {
    Text(
        text = "・$text",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun HelpNote(text: String) {
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(NeonBlue.copy(alpha = 0.6f)),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun HelpTable(headers: List<String>, rows: List<List<String>>) {
    Spacer(modifier = Modifier.height(8.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            headers.forEachIndexed { index, header ->
                Text(
                    text = header,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(if (index == 0) 1f else 1f),
                )
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                row.forEachIndexed { index, cell ->
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.weight(if (index == 0) 1f else 1f),
                    )
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}
