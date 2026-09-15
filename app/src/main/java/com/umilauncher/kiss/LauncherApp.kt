package com.umilauncher.kiss

import android.app.Application
import com.umilauncher.kiss.data.AppRepository
import com.umilauncher.kiss.data.FavoritesStore
import com.umilauncher.kiss.data.IconCache

/**
 * Como um launcher fica residente na memória o tempo inteiro (o sistema
 * evita matá-lo, mas PODE fazê-lo sob pressão severa), reagir a
 * onTrimMemory é a diferença entre "o launcher some por 1s ao reabrir"
 * (aceitável) e "o launcher some, e o app que o usuário queria abrir
 * também é morto junto" (ruim, e mais provável quando o processo do
 * launcher está inchado).
 */
class LauncherApp : Application() {

    lateinit var appRepository: AppRepository
        private set
    lateinit var iconCache: IconCache
        private set
    lateinit var favoritesStore: FavoritesStore
        private set

    override fun onCreate() {
        super.onCreate()
        appRepository = AppRepository(this)
        iconCache = IconCache(this)
        favoritesStore = FavoritesStore(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        iconCache.trimMemory(level)
    }
}
