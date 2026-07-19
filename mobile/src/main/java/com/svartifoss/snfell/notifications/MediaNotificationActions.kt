package com.svartifoss.snfell.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.session.MediaSession
import android.os.Build
import android.service.notification.StatusBarNotification
import android.support.v4.media.session.MediaSessionCompat
import androidx.appcompat.content.res.AppCompatResources
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/** A notification action safe to serialize through the Wear Data Layer. */
data class MediaNotificationAction(
        val id: String,
        val label: String,
        val semantic: String,
        val iconPng: ByteArray?
)

/** MediaStyle identifies the buttons the app considers suitable for its compact surface. Mirror
 * that exact set on the watch: filling empty watch slots from the expanded notification made a
 * two-action Spotify panel grow an unrelated third bubble. If compact metadata is absent or
 * malformed, fall back to every notification action for legacy players. */
internal fun orderedNotificationActionIndices(
        actionCount: Int,
        compactIndices: IntArray?
): List<Int> {
    if (actionCount <= 0) return emptyList()
    val compact = (compactIndices ?: IntArray(0)).asSequence()
            .filter { it in 0 until actionCount }
            .distinct()
            .toList()
    return compact.ifEmpty { (0 until actionCount).toList() }
}

/** Dislike is deliberately not a watch shortcut. It is easy to hit accidentally on a tiny round
 * display and YouTube Music's fourth dislike action used to distort the three-button layout. */
internal fun <T> discardDislikeActions(actions: List<T>, semanticOf: (T) -> String): List<T> =
        actions.filterNot { semanticOf(it) == MediaActionSemantics.DISLIKE }

/**
 * Process-local bridge between [android.service.notification.NotificationListenerService] and
 * MusicService. Notification actions cannot be sent directly to the watch because PendingIntent
 * and remote-package Icon objects are phone-only. This registry keeps the PendingIntent here,
 * sends a stable id plus rasterized icon to the watch, then resolves the id again when tapped.
 */
object MediaNotificationActions {
    private const val ACTION_PREFIX = "notification:"
    private const val ICON_SIZE_PX = 48
    private const val MAX_ACTIONS = 3

    private data class StoredAction(
            val notificationActionIndex: Int,
            val identity: MediaActionIdentity,
            val publicAction: MediaNotificationAction,
            val pendingIntent: PendingIntent
    )

    private data class StoredNotification(
            val packageName: String,
            val notificationKey: String,
            val sessionToken: MediaSession.Token?,
            val postedAt: Long,
            val actions: List<StoredAction>
    )

    private val notifications = LinkedHashMap<String, StoredNotification>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val actionIdGeneration = UUID.randomUUID().toString()
    private val actionIdSequence = AtomicLong()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun update(context: Context, sbn: StatusBarNotification) {
        val notification = sbn.notification
        val sessionToken = notificationMediaSessionToken(notification)
        val isMedia = notification.category == Notification.CATEGORY_TRANSPORT ||
                sessionToken != null ||
                notification.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true

        // Re-use an id only while the action really is the same. In particular, many players
        // replace the PendingIntent and icon in the same notification slot when play becomes
        // pause. A versioned id makes a delayed tap from the watch harmless: once the slot has
        // changed, the old id is no longer present in this registry.
        val previous = synchronized(this) { notifications[sbn.key] }
        val canReusePreviousIds = previous?.packageName == sbn.packageName &&
                previous.sessionToken == sessionToken
        val reusablePreviousActions = if (canReusePreviousIds) previous?.actions.orEmpty() else emptyList()
        val usedPreviousActionIndices = HashSet<Int>()
        val notificationActions = notification.actions.orEmpty()
        val compactIndices = try {
            notification.extras?.getIntArray(Notification.EXTRA_COMPACT_ACTIONS)
        } catch (_: RuntimeException) {
            null
        }

        val stored = if (isMedia) {
            orderedNotificationActionIndices(notificationActions.size, compactIndices)
                    .asSequence()
                    .mapNotNull { index ->
                        val action = notificationActions[index]
                        val intent = action.actionIntent ?: return@mapNotNull null
                        val label = action.title?.toString().orEmpty()
                        val semantic = notificationActionSemantic(action, label)
                        val actionDrawable = loadActionDrawable(context, sbn, action)
                        val iconPng = actionDrawable?.let(::rasterizePng)
                        val identity = mediaActionIdentity(
                                sourceId = null,
                                label = label,
                                semantic = semantic,
                                iconPng = iconPng
                        )
                        // Notification apps are free to insert or reorder actions between
                        // updates. Match by the immutable execution target and complete visible
                        // identity, never by array position alone.
                        val previousMatch = findStableActionMatch(
                                previous = reusablePreviousActions,
                                currentIdentity = identity,
                                alreadyUsed = usedPreviousActionIndices,
                                identityOf = StoredAction::identity,
                                hasSameExecutionTarget = { it.pendingIntent == intent }
                        )
                        previousMatch?.let { usedPreviousActionIndices += it.index }
                        val id = previousMatch?.value?.publicAction?.id ?: run {
                            "$ACTION_PREFIX${sbn.key}:$actionIdGeneration:" +
                                    "${actionIdSequence.incrementAndGet()}:$index"
                        }
                        StoredAction(
                                notificationActionIndex = index,
                                identity = identity,
                                publicAction = MediaNotificationAction(
                                        id = id,
                                        label = label,
                                        semantic = semantic,
                                        iconPng = iconPng
                                ),
                                pendingIntent = intent
                        )
                    }
                    .toList()
                    .let { discardDislikeActions(it) { stored -> stored.publicAction.semantic } }
                    .take(MAX_ACTIONS)
                    .takeIf { it.isNotEmpty() }
                    ?.let { actions ->
                        StoredNotification(
                                packageName = sbn.packageName,
                                notificationKey = sbn.key,
                                sessionToken = sessionToken,
                                postedAt = sbn.postTime,
                                actions = actions
                        )
                    }
        } else {
            null
        }

        val changed = synchronized(this) {
            if (stored == null) {
                notifications.remove(sbn.key) != null
            } else {
                val old = notifications.put(sbn.key, stored)
                old == null || !old.hasSamePublicContent(stored)
            }
        }
        if (changed) notifyChanged()
    }

    fun remove(notificationKey: String) {
        val changed = synchronized(this) { notifications.remove(notificationKey) != null }
        if (changed) notifyChanged()
    }

    fun clear() {
        val changed = synchronized(this) {
            val hadEntries = notifications.isNotEmpty()
            notifications.clear()
            hadEntries
        }
        if (changed) notifyChanged()
    }

    /**
     * Returns actions belonging to the controller that is actually active. Package matching is
     * still the compatibility fallback because some legacy/OEM notifications omit a readable
     * media-session token, but an exact token match always wins when one is available.
     */
    fun actionsForSession(
            packageName: String,
            sessionToken: MediaSession.Token?
    ): List<MediaNotificationAction> = synchronized(this) {
        val packageNotifications = notifications.values
                .asSequence()
                .filter { it.packageName == packageName }
                .toList()

        val exactSession = sessionToken?.let { activeToken ->
            packageNotifications.asSequence()
                    .filter { it.sessionToken == activeToken }
                    .maxByOrNull { it.postedAt }
        }
        val packageFallback = packageNotifications.asSequence()
                // A missing notification token is the compatibility case. Do not borrow a
                // notification that positively identifies a different session of the same app.
                .filter { sessionToken == null || it.sessionToken == null }
                .maxByOrNull { it.postedAt }

        (exactSession ?: packageFallback)
                ?.actions
                ?.map { it.publicAction }
                .orEmpty()
    }

    /** Resolves an id only inside the currently active package/session. */
    fun execute(
            actionId: String,
            packageName: String,
            sessionToken: MediaSession.Token?
    ): Boolean {
        val action = synchronized(this) {
            notifications.values.asSequence()
                    .filter { notification ->
                        notification.packageName == packageName &&
                                (sessionToken == null || notification.sessionToken == null ||
                                        notification.sessionToken == sessionToken)
                    }
                    .flatMap { it.actions.asSequence() }
                    .firstOrNull { it.publicAction.id == actionId }
        } ?: return false

        return try {
            action.pendingIntent.send()
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        }
    }

    fun isNotificationAction(actionId: String): Boolean = actionId.startsWith(ACTION_PREFIX)

    /** Rasterizes a framework MediaSession.CustomAction resource in the publishing app's own
     * package context. Resource ids are package-local and therefore cannot cross to Wear as-is. */
    fun loadRemoteActionIcon(
            context: Context,
            packageName: String,
            resourceId: Int
    ): ByteArray? {
        if (resourceId == 0) return null
        return loadRemoteResourceDrawable(context, packageName, resourceId)?.let(::rasterizePng)
    }

    private fun notifyChanged() {
        listeners.forEach { it.invoke() }
    }

    /** Data classes compare ByteArray by reference; notification icons need content equality. */
    private fun StoredNotification.hasSamePublicContent(other: StoredNotification): Boolean =
            packageName == other.packageName &&
                    sessionToken == other.sessionToken &&
                    actions.size == other.actions.size &&
                    actions.zip(other.actions).all { (left, right) ->
                        left.publicAction.id == right.publicAction.id &&
                                left.publicAction.label == right.publicAction.label &&
                                left.publicAction.semantic == right.publicAction.semantic &&
                                left.publicAction.iconPng.contentEqualsNullable(
                                        right.publicAction.iconPng
                                )
                    }

    private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
        this === other -> true
        this == null || other == null -> false
        else -> contentEquals(other)
    }

    @Suppress("DEPRECATION")
    private fun notificationMediaSessionToken(notification: Notification): MediaSession.Token? {
        return try {
            when (val rawToken = notification.extras?.get(Notification.EXTRA_MEDIA_SESSION)) {
                is MediaSession.Token -> rawToken
                is MediaSessionCompat.Token -> rawToken.token as? MediaSession.Token
                else -> null
            }
        } catch (_: RuntimeException) {
            // Malformed/OEM Parcelable extras must not take down the notification listener.
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun loadActionDrawable(
            context: Context,
            sbn: StatusBarNotification,
            action: Notification.Action
    ): Drawable? {
        val remoteContext = createThemedPackageContext(context, sbn.packageName)

        try {
            // A resource Icon without an explicit package name resolves relative to the Context
            // passed here. Trying the publisher's context first avoids accidentally loading our
            // own unrelated drawable that happens to share the same integer resource id.
            action.getIcon()?.let { icon ->
                remoteContext?.let(icon::loadDrawable)?.let { return it }
                icon.loadDrawable(context)?.let { return it }
            }
        } catch (_: Exception) {
            // Some OEM/media apps publish a resource Icon whose package context is not
            // resolved by Icon.loadDrawable. The explicit remote-context fallback below can.
        }

        val resourceId = action.icon
        if (resourceId == 0) return null
        return loadRemoteResourceDrawable(context, sbn.packageName, resourceId)
    }

    private fun loadRemoteResourceDrawable(
            context: Context,
            packageName: String,
            resourceId: Int
    ): Drawable? {
        val remoteContext = createThemedPackageContext(context, packageName) ?: return null
        return try {
            AppCompatResources.getDrawable(remoteContext, resourceId)
        } catch (_: Exception) {
            null
        }
    }

    /** A package Context created via [Context.createPackageContext] carries no theme, so any
     * `?attr/...` reference inside a remote app's icon (common in Material-styled vector
     * drawables) resolves against undefined/default values instead of that app's own theme. Apply
     * the publisher's declared theme when the framework can resolve one. */
    private fun createThemedPackageContext(context: Context, packageName: String): Context? {
        val remoteContext = try {
            context.createPackageContext(packageName, 0)
        } catch (_: Exception) {
            return null
        }
        try {
            val themeResId = context.packageManager.getApplicationInfo(packageName, 0).theme
            if (themeResId != 0) {
                remoteContext.setTheme(themeResId)
            }
        } catch (_: Exception) {
            // Theming is a best-effort legibility improvement; an untheme'd context still works
            // for icons that don't reference theme attributes.
        }
        return remoteContext
    }

    private fun notificationActionSemantic(
            action: Notification.Action,
            label: String
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            when (action.semanticAction) {
                Notification.Action.SEMANTIC_ACTION_THUMBS_UP -> return MediaActionSemantics.LIKE
                Notification.Action.SEMANTIC_ACTION_THUMBS_DOWN ->
                    return MediaActionSemantics.DISLIKE
            }
        }
        return inferMediaActionSemantic(label)
    }

    /** A launcher-style AdaptiveIconDrawable pads its visible glyph inside a much larger safe
     * zone (its intrinsic size describes the full canvas, not just the glyph), so fitting by
     * intrinsic size alone would render it far smaller than every other action icon. Draw only
     * its foreground layer instead when one is present. */
    private fun unwrapAdaptiveIcon(drawable: Drawable): Drawable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                drawable is android.graphics.drawable.AdaptiveIconDrawable) {
            drawable.foreground?.let { return it }
        }
        return drawable
    }

    private fun rasterizePng(sourceDrawable: Drawable): ByteArray? = try {
        val drawable = unwrapAdaptiveIcon(sourceDrawable).mutate()
        // Media notification action icons are monochrome templates the system tints itself. Some
        // apps (e.g. YouTube Music) build them from vector paths whose fills reference theme
        // attributes that don't resolve in our rasterization context, so parts render missing or
        // garbled. Force a flat white tint so the whole glyph always draws; the watch then tints
        // it to the panel's chrome colour. The button's active/inactive state stays readable
        // because the icon's own shape (e.g. filled vs outlined thumb) still carries it.
        drawable.setTint(Color.WHITE)
        drawable.setTintMode(android.graphics.PorterDuff.Mode.SRC_IN)
        val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = ICON_SIZE_PX / 10
        val available = ICON_SIZE_PX - inset * 2
        val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: available
        val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: available
        val scale = minOf(
                available.toFloat() / intrinsicWidth,
                available.toFloat() / intrinsicHeight
        )
        val width = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
        val height = (intrinsicHeight * scale).toInt().coerceAtLeast(1)
        val left = (ICON_SIZE_PX - width) / 2
        val top = (ICON_SIZE_PX - height) / 2
        drawable.setBounds(left, top, left + width, top + height)
        drawable.draw(canvas)
        val normalized = normalizeTemplateBitmap(bitmap)
        ByteArrayOutputStream().use { output ->
            normalized.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    } catch (_: Exception) {
        null
    }

    /** Crops transparent/intrinsic padding and places the visible glyph on a common optical
     * canvas. Players ship wildly different vector viewBoxes: without this pass Spotify looked
     * tiny, while YouTube Music's asymmetric padding pushed glyphs off-centre. */
    private fun normalizeTemplateBitmap(source: Bitmap): Bitmap {
        var left = source.width
        var top = source.height
        var right = -1
        var bottom = -1
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                if (Color.alpha(source.getPixel(x, y)) > 12) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        if (right < left || bottom < top) return source

        val visibleWidth = right - left + 1
        val visibleHeight = bottom - top + 1
        val target = ICON_SIZE_PX * .77f
        val scale = minOf(target / visibleWidth, target / visibleHeight)
        val width = visibleWidth * scale
        val height = visibleHeight * scale
        val destination = RectF(
                (ICON_SIZE_PX - width) / 2f,
                (ICON_SIZE_PX - height) / 2f,
                (ICON_SIZE_PX + width) / 2f,
                (ICON_SIZE_PX + height) / 2f)
        return Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(
                    source,
                    Rect(left, top, right + 1, bottom + 1),
                    destination,
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

}
