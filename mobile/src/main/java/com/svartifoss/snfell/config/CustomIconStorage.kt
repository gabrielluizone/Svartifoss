package com.svartifoss.snfell.config

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.collection.ArraySet
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.config.actionlist.ActionList
import com.svartifoss.snfell.config.buttons.ButtonConfig
import com.svartifoss.snfell.di.GlobalConfig
import dagger.Lazy
import dagger.Reusable
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

@Reusable
class CustomIconStorage @Inject constructor(private val context: Context,
                                            @GlobalConfig private val configLazy: Lazy<ActionConfig>) {
    companion object {
        private const val PREF_NAME = "custom_icon_storage"
        private const val PREF_KEY_NUM_SAVES = "num_saves"

        private const val GC_SAVES_THRESHOLD = 10

        private const val MAX_MEMORY_LRU_STORE_SIZE_BYTES = 5_000_000

        /** Stable, bounded and collision-resistant filename for a complete URI. The old mapping
         * deleted punctuation, so different URIs such as `a:b` and `ab` silently shared a PNG. */
        private fun hashedFileName(uri: Uri): String {
            val digest = MessageDigest.getInstance("SHA-256")
                    .digest(uri.toString().toByteArray(Charsets.UTF_8))
            return digest.joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            } + ".png"
        }

        private fun legacyFileName(uri: Uri): String =
                uri.toString().replace(Regex("[^\\w.\\-_]"), "") + ".png"

        /** Shared with the Watch preview loader so both consumers resolve the exact same file.
         * Existing installs are migrated lazily without deleting the legacy copy immediately;
         * the normal garbage collector removes it only after every referenced URI was resolved. */
        internal fun resolveStoredIconFile(filesDir: File, uri: Uri): File {
            val folder = File(filesDir, "icon_store").apply { mkdirs() }
            val target = File(folder, hashedFileName(uri))
            if (!target.exists()) {
                val legacy = File(folder, legacyFileName(uri))
                if (legacy.exists()) {
                    try {
                        legacy.copyTo(target, overwrite = false)
                    } catch (e: IOException) {
                        Timber.w(e, "Could not migrate custom icon filename")
                    }
                }
            }
            return target
        }
    }

    private val storageFolder = File(context.filesDir, "icon_store")
    private val metaPreferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val memoryIconStore = object : LruCache<Uri, Bitmap>(MAX_MEMORY_LRU_STORE_SIZE_BYTES) {
        override fun sizeOf(key: Uri, value: Bitmap): Int {
            return value.byteCount
        }
    }

    init {
        storageFolder.mkdirs()
    }

    operator fun get(iconUri: Uri): Drawable? {
        var bitmap = memoryIconStore[iconUri]
        if (bitmap == null) {
            bitmap = loadImageFromFile(iconUri) ?: return null
            memoryIconStore.put(iconUri, bitmap)
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    operator fun get(action: PhoneAction): Drawable {
        val customUri = action.customIconUri

        return if (customUri == null) {
            action.defaultIcon
        } else {
            get(customUri) ?: action.defaultIcon
        }
    }

    private fun loadImageFromFile(uri: Uri): Bitmap? {
        val file = getFileForUri(uri)
        if (!file.exists()) {
            return null
        }

        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: IOException) {
            Timber.e(e, "Image loading error")
            null
        } catch (e: OutOfMemoryError) {
            // Try loading smaller image. Blurry image is better than crash
            loadImageFromFileLowMemory(file, 100)
        }
    }

    private fun loadImageFromFileLowMemory(file: File, size: Int): Bitmap? {
        return try {
            decodeSampledBitmapFromFile(file, size, size)
        } catch (e: IOException) {
            Timber.e(e, "Image loading error")
            null
        } catch (e: OutOfMemoryError) {
            if (size > 50) {
                // We still cannot load the image. Try loading smaller one.
                loadImageFromFileLowMemory(file, size / 2)
            } else {
                // We cannot load even very small 50x50 image
                // It is better to display default image than crashing.
                Timber.e("Image loading out of memory")
                return null
            }
        }
    }

    fun setIcon(iconUri: Uri, icon: Bitmap) {
        incrementSets()

        memoryIconStore.put(iconUri, icon)

        try {
            FileOutputStream(getFileForUri(iconUri)).use {
                icon.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (e: IOException) {
            Timber.e(e, "Image saving error")
        }
    }

    private fun incrementSets() {
        var numSets = metaPreferences.getInt(PREF_KEY_NUM_SAVES, 0)
        if (++numSets > GC_SAVES_THRESHOLD) {
            gc()
            numSets = 0
        }

        metaPreferences.edit().putInt(PREF_KEY_NUM_SAVES, numSets).apply()
    }

    private fun gc() {
        val utilizedIconUris = ArraySet<String>()

        val actionConfig = this.configLazy.get()
        utilizedIconUris.addAll(getAllCustomUriFiles(actionConfig.getPlayingConfig()))
        utilizedIconUris.addAll(getAllCustomUriFiles(actionConfig.getStoppedConfig()))
        utilizedIconUris.addAll(getAllCustomUriFiles(actionConfig.getActionList()))

        (storageFolder.listFiles() ?: emptyArray())
                .filter { !utilizedIconUris.contains(it.name) }
                .forEach { it.delete() }

    }

    private fun getAllCustomUriFiles(config: ButtonConfig): Collection<String> {
        return config.getAllActions()
                .mapNotNull { it.value.customIconUri }
                .map { getFileForUri(it).name }
    }

    private fun getAllCustomUriFiles(config: ActionList): Collection<String> {
        return config.actions
                .mapNotNull { it.customIconUri }
                .map { getFileForUri(it).name }
    }


    private fun getFileForUri(uri: Uri): File {
        return resolveStoredIconFile(context.filesDir, uri)
    }

    private fun calculateInSampleSize(
            options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width smaller than the requested height and width.
            while (width / inSampleSize >= reqWidth || height / inSampleSize >= reqHeight) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun decodeSampledBitmapFromFile(file: File, reqWidth: Int, reqHeight: Int): Bitmap {

        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, options)

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }
}
