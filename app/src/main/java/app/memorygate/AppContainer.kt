package app.memorygate

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.memorygate.apps.InstalledAppsRepository
import app.memorygate.data.DataStoreSettingsRepository
import app.memorygate.data.GuardedAppRepository
import app.memorygate.data.RoomGuardedAppRepository
import app.memorygate.data.RoomTargetRepository
import app.memorygate.data.SettingsRepository
import app.memorygate.data.TargetRepository
import app.memorygate.data.db.AppDatabase
import app.memorygate.gate.GateInteractor
import app.memorygate.gate.GateStateCache
import app.memorygate.gate.TargetLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 手動 DI コンテナ。[MemoryGateApp] が 1 つだけ保持する */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** 日付判定に使う時計。端末のローカルタイムゾーン */
    val clock: Clock = DeviceClock

    /** アプリのプロセスと同じ寿命のスコープ */
    val applicationScope = CoroutineScope(SupervisorJob())

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val targetRepository: TargetRepository by lazy { RoomTargetRepository(database.targetDao()) }
    val guardedAppRepository: GuardedAppRepository by lazy { RoomGuardedAppRepository(database.guardedAppDao()) }
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(appContext.settingsDataStore) }
    val installedAppsRepository: InstalledAppsRepository by lazy { InstalledAppsRepository(appContext) }

    val gateStateCache: GateStateCache by lazy {
        GateStateCache(applicationScope, targetRepository, guardedAppRepository, settingsRepository, clock)
    }
    val gateInteractor: GateInteractor by lazy { GateInteractor(targetRepository, settingsRepository, clock) }
    val targetLauncher: TargetLauncher by lazy { TargetLauncher(appContext) }
}
