package com.svartifoss.snfell.notifications

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Portable semantic names shared with the watch through music.proto. Strings keep the wire
 * format forward-compatible with players exposing actions that this version does not know yet. */
internal object MediaActionSemantics {
    const val UNKNOWN = "unknown"
    const val LIKE = "like"
    const val DISLIKE = "dislike"
    const val SHUFFLE = "shuffle"
    const val REPEAT = "repeat"
    const val PREVIOUS = "previous"
    const val NEXT = "next"
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val STOP = "stop"
    const val QUEUE = "queue"
    const val SEEK_BACKWARD = "seek_backward"
    const val SEEK_FORWARD = "seek_forward"
}

/** Everything about an action that the user can see or that identifies its meaning. Execution
 * target equality is checked separately because PendingIntent deliberately stays phone-side. */
internal data class MediaActionIdentity(
        val sourceId: String?,
        val semantic: String,
        val normalizedLabel: String,
        val iconDigest: String?
)

internal fun mediaActionIdentity(
        sourceId: String?,
        label: String,
        semantic: String,
        iconPng: ByteArray?
): MediaActionIdentity = MediaActionIdentity(
        sourceId = sourceId?.takeIf { it.isNotBlank() },
        semantic = semantic,
        normalizedLabel = normalizeActionText(label),
        iconDigest = iconPng?.takeIf { it.isNotEmpty() }?.let(::stableDigest)
)

/** Finds a previous action only when both its complete public identity and its execution target
 * match. This makes IDs stable across notification reordering without ever moving an icon onto a
 * different PendingIntent. [alreadyUsed] prevents duplicate-looking buttons sharing one ID. */
internal fun <T> findStableActionMatch(
        previous: List<T>,
        currentIdentity: MediaActionIdentity,
        alreadyUsed: Set<Int> = emptySet(),
        identityOf: (T) -> MediaActionIdentity,
        hasSameExecutionTarget: (T) -> Boolean
): IndexedValue<T>? = previous.withIndex().firstOrNull { candidate ->
    candidate.index !in alreadyUsed &&
            identityOf(candidate.value) == currentIdentity &&
            hasSameExecutionTarget(candidate.value)
}

/** Produces a snapshot command rather than exposing a raw MediaSession custom-action id. The
 * phone re-computes the same id before execution, so an old watch icon cannot trigger a newly
 * repurposed custom action that happens to reuse the app's source id. */
internal fun customActionSnapshotId(
        sourceId: String,
        label: String,
        semantic: String,
        iconResourceId: Int
): String {
    val snapshot = listOf(
            sourceId,
            normalizeActionText(label),
            semantic,
            iconResourceId.toString()
    ).joinToString(separator = "\u0000")
    return "custom:${stableDigest(snapshot.toByteArray(Charsets.UTF_8)).take(32)}"
}

internal fun isCustomActionSnapshotId(id: String): Boolean = id.startsWith("custom:")

/** Best-effort semantic inference for notification labels and MediaSession action ids. Matching
 * uses normalized whole tokens, avoiding errors such as classifying "display" as "play". */
internal fun inferMediaActionSemantic(vararg hints: String?): String {
    val normalizedHints = hints.mapNotNull { it?.takeIf { hint -> hint.isNotBlank() } }
            .map(::normalizeActionText)
    val words = normalizedHints.asSequence()
            .flatMap { it.split(NON_ALPHANUMERIC).asSequence() }
            .filter { it.isNotBlank() }
            .toSet()

    fun hasAny(vararg candidates: String): Boolean = candidates.any(words::contains)
    fun hasPhrase(vararg candidates: String): Boolean = candidates.any { phrase ->
        normalizedHints.any { hint -> hint.contains(phrase) }
    }

    return when {
        (hasAny("thumbs", "thumb") && hasAny("down")) ||
                hasAny("dislike", "unlike", "descurtir") ||
                hasPhrase("nao gostei", "no me gusta", "je n aime pas") ->
            MediaActionSemantics.DISLIKE

        (hasAny("thumbs", "thumb") && hasAny("up")) ||
                hasAny("like", "liked", "favorite", "favourite", "favorito", "favorita",
                        "curtir", "curtido", "heart", "gostei") ||
                hasPhrase("me gusta", "j aime") ->
            MediaActionSemantics.LIKE

        hasAny("shuffle", "random", "aleatorio", "aleatoria", "embaralhar", "mezclar",
                "zufall") || hasPhrase("ordre aleatoire") ->
            MediaActionSemantics.SHUFFLE

        hasAny("repeat", "repeating", "loop", "repetir", "repeticao", "boucle",
                "wiederholen") ->
            MediaActionSemantics.REPEAT

        hasAny("pause", "pausar", "pausa") -> MediaActionSemantics.PAUSE
        hasAny("play", "resume", "tocar", "reproduzir", "reproducir", "reprendre") ->
            MediaActionSemantics.PLAY
        hasAny("stop", "parar", "detener") -> MediaActionSemantics.STOP

        hasAny("previous", "prev", "anterior", "zuruck", "precedent", "precedente") ->
            MediaActionSemantics.PREVIOUS
        hasAny("next", "proxima", "proximo", "siguiente", "suivant", "seguinte") ->
            MediaActionSemantics.NEXT

        hasAny("rewind", "retroceder") || hasPhrase("seek backward", "voltar 10") ->
            MediaActionSemantics.SEEK_BACKWARD
        hasAny("forward", "avancar") || hasPhrase("seek forward", "pular 10") ->
            MediaActionSemantics.SEEK_FORWARD

        hasAny("queue", "fila") || hasPhrase("up next", "a seguir") ->
            MediaActionSemantics.QUEUE
        else -> MediaActionSemantics.UNKNOWN
    }
}

private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
private val COMBINING_MARKS = Regex("\\p{M}+")

private fun normalizeActionText(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .trim()

private fun stableDigest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
