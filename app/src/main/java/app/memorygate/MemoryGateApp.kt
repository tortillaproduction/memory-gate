package app.memorygate

import android.app.Application
import android.content.Context
import kotlinx.coroutines.launch

class MemoryGateApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // アップデート後の初回起動時に、誘導先ごとのスヌーズ設定をアプリ全体の設定へ引き継ぐ
        container.applicationScope.launch { container.snoozeSettingsMigrator.migrateIfNeeded() }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MemoryGateApp).container
