package com.umilauncher.kiss.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache de ícones em memória, com tamanho calculado dinamicamente a partir
 * da classe de memória do aparelho (ActivityManager.getMemoryClass()).
 *
 * Por quê isso importa no A11 Pro (4GB RAM, Android "go-adjacent" na prática):
 *  - Ícones em resolução nativa (muitas vezes 192px+ nas launcher icons XML
 *    adaptativas) consomem ~150KB cada em ARGB_8888. Com 150+ apps instalados
 *    isso passa de 20MB só de ícones se não houver controle.
 *  - Nós baixamos a resolução para ICON_SIZE_PX (~48dp equivalente) ANTES de
 *    cachear, e limitamos o cache a uma fração pequena da memória do processo.
 *  - Usamos LruCache (não HashMap) para que ícones raramente vistos sejam
 *    descartados automaticamente sob pressão, em vez de crescer sem limite.
 */
class IconCache(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    // Tamanho final do ícone em pixels. Reduzir aqui é o maior ganho de
    // memória possível: escala quadraticamente (96px = 4x a memória de 48px).
    private val iconSizePx: Int = (48 * appContext.resources.displayMetrics.density).toInt()
        .coerceAtLeast(1)

    private val cache: LruCache<String, Bitmap>

    init {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // getMemoryClass() retorna o heap por-app em MB que o sistema concede.
        // Em aparelhos de 4GB isso costuma ficar entre 128–256MB.
        val memoryClassMb = am.memoryClass

        // Reserva no máximo ~6% do heap disponível para ícones. Isso é
        // deliberadamente conservador: o launcher fica residente o tempo
        // todo, então cada MB gasto aqui é um MB que nunca volta para o
        // sistema enquanto o launcher estiver rodando.
        val cacheSizeBytes = (memoryClassMb * 1024 * 1024 * 0.06).toInt()
            .coerceIn(2 * 1024 * 1024, 12 * 1024 * 1024) // entre 2MB e 12MB

        cache = object : LruCache<String, Bitmap>(cacheSizeBytes) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
    }

    fun getCached(key: String): Bitmap? = cache.get(key)

    /** Carrega, redimensiona e cacheia. Chamar sempre em Dispatchers.IO. */
    suspend fun loadIcon(app: AppInfo): Bitmap = withContext(Dispatchers.IO) {
        cache.get(app.key)?.let { return@withContext it }

        val drawable: Drawable = try {
            packageManager.getActivityIcon(app.componentName())
        } catch (e: PackageManager.NameNotFoundException) {
            packageManager.defaultActivityIcon
        }

        val bitmap = drawable.toBitmapDownscaled(iconSizePx)
        cache.put(app.key, bitmap)
        bitmap
    }

    fun trimMemory(level: Int) {
        // Chamado a partir de Application.onTrimMemory(). Em níveis críticos
        // esvaziamos o cache totalmente - preferimos recarregar ícones do
        // disco depois (rápido) a manter o processo do launcher "gordo" e
        // ser o alvo preferencial do low-memory killer do Android.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            cache.evictAll()
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            cache.trimToSize(cache.maxSize() / 2)
        }
    }
}

private fun Drawable.toBitmapDownscaled(sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bitmap
}
