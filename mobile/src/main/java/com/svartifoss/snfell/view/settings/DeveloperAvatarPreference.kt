package com.svartifoss.snfell.view.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.svartifoss.snfell.R

/**
 * The developer's photo, circular, as a drawable both the "About the developer" row and its
 * dialog can show.
 *
 * The bytes are bundled (`drawable-nodpi/developer_avatar.webp`) rather than fetched from
 * GitHub: an avatar is not worth a ninth feature network path, nor the privacy-policy line
 * that would come with it, and a picture that only appears once the phone is online is worse
 * than one that is simply there. `nodpi` because both call sites size their own ImageView, so
 * the bitmap must not be re-scaled per density bucket on the way in.
 *
 * The rounding is done at runtime instead of being baked into the file, so the same square
 * source serves a 22dp row icon and a 44dp dialog portrait without a second asset.
 */
internal fun developerAvatar(context: Context): Drawable? {
    val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.developer_avatar)
            ?: return null
    return RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply {
        isCircular = true
    }
}

/**
 * The "About the developer" row, carrying the photo above in place of a glyph.
 *
 * Two things have to be undone for a photograph to survive the row: `pref_item.xml` tints its
 * icon with `lyra_pref_icon_selector` (right for every other row, and a flat silhouette here),
 * and the tint is applied at inflation, so clearing it belongs in the bind rather than in the
 * XML that every other row shares.
 */
class DeveloperAvatarPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : Preference(context, attrs) {

    init {
        developerAvatar(context)?.let { icon = it }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        (holder.findViewById(android.R.id.icon) as? ImageView)?.imageTintList = null
    }
}
