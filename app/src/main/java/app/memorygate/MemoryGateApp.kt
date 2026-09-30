package app.memorygate

import android.app.Application
import android.content.Context

class MemoryGateApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MemoryGateApp).container
