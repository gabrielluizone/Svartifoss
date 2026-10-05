package com.svartifoss.snfell.actions

import android.content.Context
import android.content.ContentResolver
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.PersistableBundle
import androidx.annotation.CallSuper
import com.svartifoss.snfell.music.StreamingCollection
import com.svartifoss.snfell.music.StreamingShortcutDescription
import com.svartifoss.snfell.view.actionconfigs.ActionConfigFragment
import com.svartifoss.snfell.view.buttonconfig.ActionPickerViewModel
import com.matejdro.wearutils.serialization.Bundlable
import timber.log.Timber

abstract class PhoneAction : Bundlable {
    open val configFragment: Class<out ActionConfigFragment<out PhoneAction>>? = null

    /** True when tapping this entry in the action picker opens another chooser (a sub-list or
     *  an external picker) instead of finalizing the selection - the picker marks such rows
     *  with a chevron and a "multiple options" hint so they don't read as a single action. */
    open val opensMoreOptions: Boolean
        get() = false

    protected val context: Context
    var customIconUri: Uri? = null
    var customTitle: String? = null

    /**
     * Whether the user starred this entry of the watch's actions menu to show in the quick panel's
     * favourites block. Only read for entries of the actions menu - on a button or a panel slot it
     * means nothing - but it lives here because the menu is a list of plain actions, and keeping
     * it on the action means it is saved, restored and copied with everything else about the entry.
     */
    var inQuickPanel: Boolean = false

    constructor(context: Context) : super() {
        this.context = context
    }

    constructor(context: Context, bundle: PersistableBundle) : super(bundle) {
        this.context = context

        bundle.getString(KEY_CUSTOM_ICON_URI)?.also {
            customIconUri = Uri.parse(it)
        }

        customTitle = bundle.getString(KEY_CUSTOM_TITLE)
        inQuickPanel = bundle.getBoolean(KEY_IN_QUICK_PANEL, false)
    }

    abstract fun onActionPicked(actionPicker: ActionPickerViewModel)
    protected abstract fun retrieveTitle(): String
    abstract val defaultIcon: Drawable

    /**
     * The drawable resource [defaultIcon] came from, declared only when it depends on this
     * action's own parameters.
     *
     * Null means "whatever the action key implies", which is true of almost every action and is
     * what lets the phone skip transferring an icon the watch can draw from its own copy of the
     * same vector. An action that picks between drawables - `SetRepeatModeAction` is the first -
     * has to say so, or the watch resolves the one entry its map holds for the key and draws the
     * wrong glyph. See `needsTransmittedIcon`.
     */
    open val defaultIconRes: Int?
        get() = null

    /** Whether the action icon is a monochrome template that should follow its destination tint.
     * Built-in vectors default to true; actions backed by launcher artwork override
     * [defaultIconTintable]. User-picked gallery images remain full color, while packaged vector
     * resources remain tintable. */
    open val defaultIconTintable: Boolean
        get() = true

    /** Whether [defaultIcon] is genuine cover/artwork content (e.g. a streaming shortcut's fetched
     * thumbnail) suitable for filling a whole quick-panel pill's background, as opposed to a
     * generic app-launcher icon or glyph. Mirrors [defaultIconTintable]: only consulted while the
     * default icon is showing - see [isCoverArt]. */
    open val defaultIsCoverArt: Boolean
        get() = false

    /** Optional URI that the watch should open directly on the paired phone. This bypasses
     * Android's background-Activity restriction that applies to MusicService. */
    open val remoteUri: String?
        get() = null

    /**
     * Signed milliseconds this action moves playback by, for the actions that move it by a known
     * amount; null for every other action.
     *
     * Sent to the watch with the action so it can draw the new position the moment the button is
     * pressed, the way it already draws a play/pause or a seek on the ring - instead of waiting
     * for the phone to run it and the player to publish where it went.
     */
    open val seekOffsetMs: Long?
        get() = null

    /**
     * For an action that starts one particular streaming destination - a saved shortcut, the
     * account's liked songs - what the watch shows on that destination's own screen when the
     * action is picked from a list (the actions menu, the quick panel's rows): its service and
     * kind, and whether Shuffle is offered beside Play. Null for every other action, which then
     * runs at once as it always has.
     */
    open val streamingShortcut: StreamingShortcutDescription?
        get() = null

    /**
     * The cover this action is *listed* with - in the watch's actions menu and the quick panel's
     * rows, on the shortcut's own screen, and on the phone's Actions tab - for an action that
     * starts a streaming destination. Null for every other action, which is listed with its icon.
     *
     * Separate from [defaultIcon] on purpose: buttons keep that one. A round mini button or quick
     * panel slot holds a glyph, and a square cover shrunk into one reads as a sticker rather than
     * as the action; a row has room for the picture. Ignored once the user picked an icon of their
     * own, which replaces both. See `ShortcutCovers` for which picture it is.
     */
    open val listCover: Drawable?
        get() = null

    /**
     * The per-account collection this action starts, for the ones that start one - so the phone
     * can fetch that collection's official artwork (opt-in) only for collections actually in use.
     */
    open val streamingCollection: StreamingCollection?
        get() = null

    val iconTintable: Boolean
        get() = customIconUri?.let {
            it.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE
        } ?: defaultIconTintable

    /** A user-picked custom icon is never treated as cover art - it replaced the action's own
     * artwork, so it is no longer "the shortcut's cover". */
    val isCoverArt: Boolean
        get() = customIconUri == null && defaultIsCoverArt

    val title: String
        get() = customTitle ?: retrieveTitle()

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)

        bundle.putString(KEY_CUSTOM_ICON_URI, customIconUri?.toString())
        bundle.putString(KEY_CUSTOM_TITLE, customTitle)
        // Written only when set, so a list nobody has starred anything in stays byte-identical to
        // what earlier builds saved - and reads back false on a build that never heard of it.
        if (inQuickPanel) bundle.putBoolean(KEY_IN_QUICK_PANEL, true)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        if (other.javaClass != this.javaClass) return false

        return isEqualToAction(other as PhoneAction)
    }

    override fun hashCode(): Int {
        return 0
    }

    @CallSuper
    protected open fun isEqualToAction(other: PhoneAction): Boolean {
        return customIconUri == other.customIconUri &&
                customTitle == other.customTitle &&
                inQuickPanel == other.inQuickPanel
    }

    companion object {
        const val KEY_CUSTOM_ICON_URI = "CUSTOM_ICON_URI"
        const val KEY_CUSTOM_TITLE = "CUSTOM_TITLE"
        const val KEY_IN_QUICK_PANEL = "IN_QUICK_PANEL"

        @Suppress("UNCHECKED_CAST")
        fun <T : PhoneAction> deserialize(context: Context, bundle: PersistableBundle?): T? {
            val className = bundle?.getString(CLASS_KEY) ?: return null

            return try {
                val cls = Class.forName(className)
                val constructor = cls.getConstructor(Context::class.java, PersistableBundle::class.java)

                constructor.newInstance(context, bundle) as T?
            } catch (e: ReflectiveOperationException) {
                Timber.e(e, "PhoneAction deserialization error")
                null
            }
        }
    }
}
