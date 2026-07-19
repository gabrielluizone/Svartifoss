package com.svartifoss.snfell.watch.communication

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.svartifoss.snfell.common.actions.StandardIcons
import com.matejdro.wearutils.messages.getByteArrayAsset
import com.matejdro.wearutils.miscutils.BitmapUtils

suspend fun DataClient.getIcon(dataItem: DataItem, iconKey: String, actionKey: String): Drawable? {
    val resources = this.applicationContext.resources

    if (dataItem.assets.containsKey(iconKey)) {
        // We have override custom icon. Lets use that
        val iconAsset = dataItem.assets[iconKey]
        val iconBitmap = BitmapUtils.deserialize(iconAsset?.let { this.getByteArrayAsset(it) })

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
