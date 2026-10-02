package app.memorygate.gate

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.memorygate.R
import app.memorygate.domain.Target
import app.memorygate.domain.TargetType
import app.memorygate.ui.common.AppIcon
import app.memorygate.ui.common.FileImage

private val CIRCLE_SIZE = 160.dp

/** 背景の上でも読めるよう、文字に影を付ける */
private fun TextStyle.withShadow() = copy(color = Color.White, shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 8f))

/**
 * スヌーズ用ゲート（SPEC 7.5）。
 * 背景（昼の空・夜の星空）を画面全体に敷き、画面の高さの約 35% の位置に丸い画像、その下にタイトルと「開く」ボタン、
 * 最下部に緊急退避ボタンを置く。
 */
@Composable
fun SnoozeGateScreen(
    state: SnoozeGateUiState,
    onOpen: () -> Unit,
    onEscape: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 背景はステータスバー・ナビゲーションバーの裏まで隙間なく覆う
        Image(
            painter = painterResource(if (state.daytime) R.drawable.snooze_bg_day else R.drawable.snooze_bg_night),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val target = state.target ?: return@Box
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            // 丸い画像の中心を画面の高さの約 35% に置く
            val circleTop = (maxHeight * 0.35f - CIRCLE_SIZE / 2).coerceAtLeast(0.dp)
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(circleTop))
                SnoozeCircle(target)
                Spacer(Modifier.height(24.dp))
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
                Button(onClick = onOpen, enabled = state.launchable) { Text("開く") }
                if (!state.launchable) {
                    Text(
                        "アプリが見つかりません",
                        style = MaterialTheme.typography.bodyMedium.withShadow(),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmergencyEscapeButton(
                        onTrigger = onEscape,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

/** 丸く切り抜いた画像（白い細い縁取り）。画像がなければアプリのアイコン（Web は地球のアイコン） */
@Composable
private fun SnoozeCircle(target: Target) {
    Box(
        Modifier
            .size(CIRCLE_SIZE)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.85f))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val path = target.snoozeImagePath
        if (path != null) {
            FileImage(path, Modifier.fillMaxSize()) { FallbackIcon(target) }
        } else {
            FallbackIcon(target)
        }
    }
}

@Composable
private fun FallbackIcon(target: Target) {
    val pkg = target.packageName
    if (target.type == TargetType.APP && pkg != null) {
        AppIcon(pkg, size = CIRCLE_SIZE * 0.6f)
    } else {
        Icon(
            Icons.Outlined.Public,
            contentDescription = "Web",
            tint = Color(0xFF1E88E5),
            modifier = Modifier.size(CIRCLE_SIZE * 0.6f),
        )
    }
}

