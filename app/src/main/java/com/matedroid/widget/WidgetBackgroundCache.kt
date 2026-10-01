package com.matedroid.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Two-level cache (memory + disk) for the widget background bitmap.
 *
 * Generating the background is expensive while charging (three blurred glow
 * layers + rim light, ~8-20 MB of transient bitmap allocations), but its
 * content depends only on the car's appearance and charging state — not on
 * battery level, widget size or any text. Caching by that key lets most
 * widget updates (battery ticks, lock state, location) reuse the previous
 * bitmap instead of re-rendering it.
 *
 * The disk layer keeps cache hits across process death between the periodic
 * widget update jobs.
 */
object WidgetBackgroundCache {

    /** Bump to invalidate disk entries whenever the background rendering changes. */
    private const val CACHE_VERSION = 1
    private const val MAX_MEMORY_ENTRIES = 3
    private const val MAX_DISK_ENTRIES = 6
    private const val DIR_NAME = "widget_bg"

    data class Key(
        val exteriorColor: String?,
        val model: String?,
        val trimBadging: String?,
        val wheelType: String?,
        val overrideVariant: String?,
        val overrideWheel: String?,
        val isCharging: Boolean,
        val isDcCharging: Boolean
    )

    private val memoryCache = object : LinkedHashMap<Key, Bitmap>(MAX_MEMORY_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>): Boolean =
            size > MAX_MEMORY_ENTRIES
    }

    @Synchronized
    fun getOrCreate(context: Context, key: Key, builder: () -> Bitmap): Bitmap {
        memoryCache[key]?.let { return it }

        val file = diskFile(context, key)
        if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)?.let { decoded ->
                memoryCache[key] = decoded
                return decoded
            }
        }

        val bitmap = builder()
        memoryCache[key] = bitmap
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file.parentFile?.let(::trimDisk)
        }
        return bitmap
    }

    private fun diskFile(context: Context, key: Key): File {
        val raw = listOf(
            CACHE_VERSION, key.exteriorColor, key.model, key.trimBadging, key.wheelType,
            key.overrideVariant, key.overrideWheel, key.isCharging, key.isDcCharging
        ).joinToString("|")
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray())
        val name = digest.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
        return File(File(context.cacheDir, DIR_NAME), "$name.png")
    }

    private fun trimDisk(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_DISK_ENTRIES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_DISK_ENTRIES)
            .forEach { it.delete() }
    }
}
