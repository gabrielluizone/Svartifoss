package com.svartifoss.snfell.watch.tile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItemAsset
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
        return fromAssetOrLast(context, dataClient, item.assets[CommPaths.ASSET_ALBUM_ART])
    }

    /**
     * The accent of the cover [asset], falling back to the previous album colour.
     *
     * Both Tiles ask on every render - the media Tile refreshes at least once a minute and on each
     * track change, the shortcuts Tile alongside it - and each ask used to read the cover out of
     * the Data Layer, decode it, run a Palette pass over it and write the result to disk, for a
     * cover that had not changed since the last ask. The Data Layer addresses assets by content,
     * so an asset id already seen (under the same accent source) is answered from memory, and the
     * colour is only written when it differs from the one stored.
     */
    suspend fun fromAssetOrLast(context: Context, dataClient: DataClient, asset: DataItemAsset?): Int? {
        if (asset == null) return lastKnown(context)
        val source = accentSource(context)
        lastExtracted?.takeIf { it.assetId == asset.id && it.source == source }?.let { cached ->
            return cached.accent ?: lastKnown(context)
        }
        val bitmap = try {
            BitmapUtils.deserialize(dataClient.getByteArrayAsset(asset))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not remembered: an asset that could not be read now may well be readable next time.
            return lastKnown(context)
        }
        val fresh = extract(bitmap, source)
        lastExtracted = Extracted(asset.id, source, fresh)
        if (fresh == null) return lastKnown(context)
        val preferences = context.getSharedPreferences(CACHE_PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.contains(KEY_LAST_ACCENT) || preferences.getInt(KEY_LAST_ACCENT, 0) != fresh) {
            preferences.edit().putInt(KEY_LAST_ACCENT, fresh).apply()
        }
        return fresh
    }

    /** The accent last extracted and what it was extracted from - see [fromAssetOrLast]. */
    private class Extracted(val assetId: String, val source: AlbumAccentSource, val accent: Int?)

    @Volatile
    private var lastExtracted: Extracted? = null

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

    private fun extract(bitmap: Bitmap?, source: AlbumAccentSource): Int? {
        if (bitmap == null) return null
        return try {
            val palette = Palette.from(bitmap).generate()
            val swatches = palette.swatches.map { SwatchInfo(it.rgb, it.population) }
            selectPrimaryAccent(
                palette.getVibrantSwatch()?.let { SwatchInfo(it.rgb, it.population) },
                swatches,
                source
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
