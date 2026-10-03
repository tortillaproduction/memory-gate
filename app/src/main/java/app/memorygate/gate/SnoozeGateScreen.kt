package app.memorygate.gate

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.memorygate.R
import app.memorygate.domain.Target
import app.memorygate.domain.TargetType
import app.memorygate.ui.common.AppIcon
import app.memorygate.ui.common.FileImage
import kotlin.math.roundToInt

/** 円の直径の上限・下限（SPEC 7.5.1） */
private val CIRCLE_MAX = 320.dp
private val CIRCLE_MIN = 64.dp

/** 円とタイトルの間の余白 */
private val CIRCLE_GAP = 24.dp

/** 円の下の内容と、最下部の緊急退避ボタンの間に最低限あける余白 */
private val BOTTOM_GAP = 16.dp

/** 背景の上でも読めるよう、文字に影を付ける */
private fun TextStyle.withShadow() = copy(color = Color.White, shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 8f))

/**
 * スヌーズ用ゲート（SPEC 7.5）。
 * 背景（昼の空・夜の星空）を画面全体に敷き、画面の高さの約 35% の位置に丸い画像、その下にタイトル・「開く」ボタン・
 * 「スヌーズを止める」ボタン、最下部に緊急退避ボタンを置く。
 */
@Composable
fun SnoozeGateScreen(
    state: SnoozeGateUiState,
    onOpen: () -> Unit,
    onStopSnooze: () -> Unit,
    onEscape: () -> Unit,
) {
    var confirmingStop by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 背景はステータスバー・ナビゲーションバーの裏まで隙間なく覆う
        Image(
            painter = painterResource(if (state.daytime) R.drawable.snooze_bg_day else R.drawable.snooze_bg_night),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val target = state.target ?: return@Box
        Layout(
            contents = listOf(
                { SnoozeCircle(target) },
                { SnoozeContent(target, state.launchable, onOpen, onStop = { confirmingStop = true }) },
                {
                    EmergencyEscapeButton(
                        onTrigger = onEscape,
                        color = Color.White,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.35f)),
                    )
                },
            ),
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        ) { (circle, content, escape), constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            // ボタン類を先に測り、残りの高さで円の大きさを決める（小さい画面では円を縮める）
            val contentPlaceable = content.single().measure(loose)
            val escapePlaceable = escape.single().measure(loose)
            val gap = CIRCLE_GAP.toPx()
            val diameter = SnoozeCircleLayout.diameter(
                width = width.toFloat(),
                height = height.toFloat(),
                contentHeight = contentPlaceable.height.toFloat(),
                bottomHeight = escapePlaceable.height + BOTTOM_GAP.toPx(),
                gap = gap,
                maxDiameter = CIRCLE_MAX.toPx(),
                minDiameter = CIRCLE_MIN.toPx(),
            ).roundToInt()
            val circlePlaceable = circle.single().measure(Constraints.fixed(diameter, diameter))
            layout(width, height) {
                // 円の中心を画面の高さの約 35% に置き、その下にタイトルとボタン類、最下部に緊急退避ボタン
                val circleTop = SnoozeCircleLayout.circleTop(height.toFloat(), diameter.toFloat()).roundToInt()
                circlePlaceable.place((width - diameter) / 2, circleTop)
                contentPlaceable.place((width - contentPlaceable.width) / 2, circleTop + diameter + gap.roundToInt())
                escapePlaceable.place((width - escapePlaceable.width) / 2, height - escapePlaceable.height)
            }
        }
        if (confirmingStop) {
            AlertDialog(
                onDismissRequest = { confirmingStop = false },
                text = { Text("${target.title}のスヌーズを OFF にしますか？編集画面からいつでも ON に戻せます。") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmingStop = false
                        onStopSnooze()
                    }) { Text("OFF にする") }
                },
                dismissButton = { TextButton(onClick = { confirmingStop = false }) { Text("キャンセル") } },
            )
        }
    }
}

/** 円の下に置くタイトル・「開く」ボタン・「スヌーズを止める」ボタン */
@Composable
private fun SnoozeContent(target: Target, launchable: Boolean, onOpen: () -> Unit, onStop: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            target.title,
            style = MaterialTheme.typography.headlineSmall.withShadow(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpen, enabled = launchable) { Text("開く") }
        if (!launchable) {
            Text(
                "アプリが見つかりません",
                style = MaterialTheme.typography.bodyMedium.withShadow(),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        // 控えめなテキストボタン（白文字・半透明の下地）
        TextButton(
            onClick = onStop,
            colors = ButtonDefaults.textButtonColors(
                containerColor = Color.Black.copy(alpha = 0.35f),
                contentColor = Color.White,
            ),
        ) { Text("スヌーズを止める", style = MaterialTheme.typography.labelLarge) }
    }
}

/**
 * 丸く切り抜いた画像（白い細い縁取り）。大きさは呼び出し側の制約（直径）に合わせる。
 * 画像は縦横比を変えずに円いっぱいに表示し、画像の中心を円の中心に合わせる。
 * 画像がなければアプリのアイコン（Web は地球のアイコン）
 */
@Composable
private fun SnoozeCircle(target: Target) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.85f))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val iconSize = maxWidth * 0.6f
        val path = target.snoozeImagePath
        if (path != null) {
            FileImage(
                path,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
            ) { FallbackIcon(target, iconSize) }
        } else {
            FallbackIcon(target, iconSize)
        }
    }
}

@Composable
private fun FallbackIcon(target: Target, size: Dp) {
    val pkg = target.packageName
    if (target.type == TargetType.APP && pkg != null) {
        AppIcon(pkg, size = size)
    } else {
        Icon(
            Icons.Outlined.Public,
            contentDescription = "Web",
            tint = Color(0xFF1E88E5),
            modifier = Modifier.size(size),
        )
    }
}
