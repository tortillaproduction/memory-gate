package app.memorygate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import app.memorygate.ui.AppNavHost
import app.memorygate.ui.common.NavigationBarTapToggle
import app.memorygate.ui.common.NavigationBars
import app.memorygate.ui.theme.MemoryGateTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = appContainer.settingsRepository
        setContent {
            MemoryGateTheme {
                // 初回起動時（オンボーディング未完了）はオンボーディングから始める
                val onboardingCompleted by produceState<Boolean?>(null) {
                    value = settings.onboardingCompleted.first()
                }
                Surface(Modifier.fillMaxSize()) {
                    NavigationBarTapToggle {
                        onboardingCompleted?.let { AppNavHost(startWithOnboarding = !it) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ナビゲーションバーは既定で非表示（SPEC 7.0）
        NavigationBars.hide(window)
    }
}
