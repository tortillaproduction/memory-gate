package app.memorygate.gate

import app.memorygate.data.SettingsRepository
import app.memorygate.data.TargetRepository
import app.memorygate.domain.GateLogic
import java.time.Clock

/** ゲート画面の操作に伴うデータ更新（SPEC 4.4 / 4.5 / 7.5.1） */
class GateInteractor(
    private val targetRepository: TargetRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) {
    /**
     * 4.4 ゲートのボタンを押したとき: 誘導先の lastVisitedAt = now、gatePassedDate = today。
     * 誘導先を開けない場合（アプリが見つからないなど）は呼ばないこと。
     */
    suspend fun onTargetOpened(targetId: Long) {
        targetRepository.setLastVisitedAt(targetId, clock.millis())
        settingsRepository.setGatePassedDate(GateLogic.today(clock))
    }

    /**
     * スヌーズ用ゲートの「スヌーズを止める」: アプリ全体のスヌーズを OFF にする（スヌーズ設定画面でスイッチを OFF にしたのと同じ。
     * 間隔・時間帯の設定は残す）。lastVisitedAt と gatePassedDate は更新しない。
     */
    suspend fun onSnoozeStopped() {
        settingsRepository.setSnoozeEnabled(false)
    }

    // 4.5 緊急退避・4.6 戻るボタンでは lastVisitedAt / gatePassedDate を更新しないため、ここには処理を置かない。
}
