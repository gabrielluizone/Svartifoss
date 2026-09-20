package com.svartifoss.snfell.watch.tile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearutils.messages.getByteArrayAsset
import com.matejdro.wearutils.miscutils.BitmapUtils
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.common.ColorHarmony
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.SwatchInfo
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.selectPrimaryAccent
import com.svartifoss.snfell.watch.theme.WatchTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Album colour shared by both Tiles, including their app-opening empty states. */
internal object TileAlbumAccent {
    /**
     * Reads the cover attached to the latest music state. The last successful result is retained
     * because the phone legitimately publishes an artwork-free idle state when playback ends; an
     * "Open Svartifoss" action should still look like the music surface the user just left, rather
     * than snapping back to the app's default green.
     */
    suspend fun readCurrentOrLast(context: Context): Int? {
        val dataClient = Wearable.getDataClient(context)
        val item = try {
            val buffer = dataClient.getDataItems(
                Uri.parse("wear://*${CommPaths.DATA_MUSIC_STATE}"),
                DataClient.FILTER_LITERAL
            ).await()
            try {
                buffer.firstOrNull()?.freeze()
            } finally {
                buffer.release()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return lastKnown(context)
        }
        if (item == null) return lastKnown(context)

        val bitmap = try {
            val asset = item.assets[CommPaths.ASSET_ALBUM_ART]
            val bytes = asset?.let { dataClient.getByteArrayAsset(it) }
            BitmapUtils.deserialize(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return fromCoverOrLast(context, bitmap)
    }

    /** Extracts and remembers a new cover colour, falling back to the previous album colour. */
    fun fromCoverOrLast(context: Context, bitmap: Bitmap?): Int? {
        val fresh = extract(context, bitmap)
        if (fresh != null) {
            context.getSharedPreferences(CACHE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_ACCENT, fresh)
                .apply()
            return fresh
        }
        return lastKnown(context)
    }

    fun lastKnown(context: Context): Int? {
        val preferences = context.getSharedPreferences(CACHE_PREFERENCES, Context.MODE_PRIVATE)
        return if (preferences.contains(KEY_LAST_ACCENT)) {
            preferences.getInt(KEY_LAST_ACCENT, WatchTheme.ACCENT_DEFAULT)
        } else {
            null
        }
    }

    /** Keeps dark or excessively saturated cover colours legible on the black Tile background. */
    fun displayColor(raw: Int?): Int {
        val base = raw ?: return WatchTheme.ACCENT_DEFAULT
        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        if (hsv[2] < 0.55f) hsv[2] = 0.55f
        if (hsv[1] > 0.9f) hsv[1] = 0.9f
        return Color.HSVToColor(hsv)
    }

    /** Picks black or white content for a filled accent control. */
    fun contentColor(accent: Int): Int {
        val luminance =
            (0.299 * Color.red(accent) + 0.587 * Color.green(accent) +
                0.114 * Color.blue(accent)) / 255.0
        return if (luminance > 0.6) WatchTheme.BACKGROUND_BLACK else WatchTheme.COLOR_WHITE
    }

    private fun extract(context: Context, bitmap: Bitmap?): Int? {
        if (bitmap == null) return null
        return try {
            val palette = Palette.from(bitmap).generate()
            val swatches = palette.swatches.map { SwatchInfo(it.rgb, it.population) }
            selectPrimaryAccent(
                palette.getVibrantSwatch()?.let { SwatchInfo(it.rgb, it.population) },
                swatches,
                accentSource(context)
            )?.let(ColorHarmony::promoteNeutralAccent)
        } catch (e: Exception) {
            null
        }
    }

    private fun accentSource(context: Context): AlbumAccentSource {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return AlbumAccentSource.fromPreference(
            FaceScopedPreferences.getString(
                preferences,
                MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE,
                ThemeAppearance.resolve(preferences)
            )
        )
    }

    private const val CACHE_PREFERENCES = "tile_album_accent"
    private const val KEY_LAST_ACCENT = "last_accent"
}
