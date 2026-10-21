package com.svartifoss.snfell.watch.communication

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.svartifoss.snfell.common.BitmapBorderTrim
import com.svartifoss.snfell.common.SampledBitmaps
import com.svartifoss.snfell.common.actions.StandardIcons
import com.matejdro.wearutils.messages.getByteArrayAsset

/** The bitmap [dataItem] carries under [key], or null when there is none or it cannot be read.
 *  No fallback and no trimming: for the pictures that have no stand-in, like an app's glyph. */
suspend fun DataClient.getAssetBitmap(dataItem: DataItem, key: String): Bitmap? {
    val asset = dataItem.assets[key] ?: return null
    return try {
        SampledBitmaps.decode(getByteArrayAsset(asset),
                SampledBitmaps.screenPx(applicationContext.resources))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

suspend fun DataClient.getIcon(
        dataItem: DataItem,
        iconKey: String,
        actionKey: String,
        trimCoverArt: Boolean = false
): Drawable? {
    val resources = this.applicationContext.resources

    if (dataItem.assets.containsKey(iconKey)) {
        // We have override custom icon. Lets use that
        val iconAsset = dataItem.assets[iconKey]
        val iconBitmap = SampledBitmaps.decode(iconAsset?.let { this.getByteArrayAsset(it) },
                SampledBitmaps.screenPx(resources))
                ?.let { bitmap -> if (trimCoverArt) BitmapBorderTrim.trim(bitmap) else bitmap }

        // A malformed/truncated Data Layer asset used to produce a BitmapDrawable containing a
        // null bitmap. That prevented every fallback and left an empty pill on screen.
        if (iconBitmap != null) {
            return BitmapDrawable(resources, iconBitmap)
        }
    }

    // We did not receive icon via bluetooth. Check if it is one of the standard icons
    // that we have pre-stored on the watch
    val iconRes = StandardIcons.getIcon(actionKey)
    val resolvedRes = if (iconRes != 0) iconRes else
        com.svartifoss.snfell.common.R.drawable.action_custom
    return ResourcesCompat.getDrawable(resources, resolvedRes, null)
}
