package app.memorygate.gate

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 緊急退避に必要な長押し時間 */
private const val HOLD_MILLIS = 3_000L

/**
 * 緊急退避ボタン（SPEC 4.5）。3 秒間長押しすると [onTrigger] を呼ぶ。
 * 押している間は進捗リングを表示し、途中で離すとリセットする。
 * アニメーション速度の設定に影響されないよう、フレーム時刻から経過時間を計算する。
 */
@Composable
fun EmergencyEscapeButton(
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val currentOnTrigger by rememberUpdatedState(onTrigger)

    Row(
        modifier = modifier
            .semantics { role = Role.Button }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // 消費して、背景タップによるナビゲーションバーの切り替えを起こさない
                    awaitFirstDown(requireUnconsumed = false).consume()
                    val job = scope.launch {
                        val start = withFrameMillis { it }
                        while (true) {
                            val elapsed = withFrameMillis { it } - start
                            progress = (elapsed.toFloat() / HOLD_MILLIS).coerceAtMost(1f)
                            if (elapsed >= HOLD_MILLIS) break
                        }
                        currentOnTrigger()
                    }
                    waitForUpOrCancellation()?.consume()
                    if (!job.isCompleted) job.cancel()
                    progress = 0f
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(18.dp),
            color = color,
            strokeWidth = 2.dp,
            trackColor = color.copy(alpha = 0.2f),
        )
        Text("長押しで退避（3秒）", style = MaterialTheme.typography.labelMedium, color = color)
    }
}
