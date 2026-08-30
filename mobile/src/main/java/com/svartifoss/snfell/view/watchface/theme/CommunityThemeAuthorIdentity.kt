package com.svartifoss.snfell.view.watchface.theme

import java.util.Locale

/**
 * The public identity reserved by one Community themes account.
 *
 * [authorKey] is also the Firestore document id of the reservation. Keeping the canonicalization
 * Android-free lets the client UI, transaction writer and JVM tests share exactly one policy;
 * `firestore.rules` independently mirrors the same checks at the trust boundary.
 */
internal data class CommunityThemeAuthorIdentity(
        val ownerUid: String,
        val authorName: String,
        val authorKey: String
)

internal sealed interface CommunityThemeAuthorIdentityLoadResult {
    data class Claimed(val identity: CommunityThemeAuthorIdentity) :
            CommunityThemeAuthorIdentityLoadResult

    object Unclaimed : CommunityThemeAuthorIdentityLoadResult
    object NotAuthenticated : CommunityThemeAuthorIdentityLoadResult
    data class Failed(val error: Throwable) : CommunityThemeAuthorIdentityLoadResult
}

/** Pure policy for a globally unique, account-lifetime author name. */
internal object CommunityThemeAuthorNames {
    const val MAX_LENGTH = 48
    const val ANONYMOUS_CREDIT = "Anonymous"
    private const val KEY_PREFIX = "v1:"

    /*
     * The reservation key must be reproducible by Kotlin, JavaScript and Firestore Rules without
     * Unicode normalization disagreements. Public aliases therefore use an ASCII handle grammar;
     * a period, underscore, apostrophe, ordinary space or hyphen may appear between letters and
     * numbers. Display capitalization is preserved.
     */
    private val ALLOWED = Regex(
            "^[A-Za-z0-9](?:[A-Za-z0-9._' -]*[A-Za-z0-9])?$")
    private val REPEATED_SPACES = Regex(" +")
    private val RESERVED = setOf(
            "anonymous",
            "deleted account",
            "account-erased",
            "svartifoss")

    fun normalize(raw: String): String? {
        val normalized = raw.trim(' ').replace(REPEATED_SPACES, " ")
        return normalized.takeIf {
            it.length in 1..MAX_LENGTH &&
                    ALLOWED.matches(it) &&
                    it.none { character -> character != ' ' && character.isWhitespace() } &&
                    it.lowercase(Locale.ROOT) !in RESERVED
        }
    }

    fun keyForCanonicalName(authorName: String): String =
            KEY_PREFIX + authorName.lowercase(Locale.ROOT)

    fun identity(ownerUid: String, rawName: String): CommunityThemeAuthorIdentity? {
        val authorName = normalize(rawName) ?: return null
        return CommunityThemeAuthorIdentity(
                ownerUid = ownerUid,
                authorName = authorName,
                authorKey = keyForCanonicalName(authorName))
    }

    fun parse(ownerUid: String, raw: Map<String, Any?>?): CommunityThemeAuthorIdentity? {
        raw ?: return null
        if (raw.keys != ACCOUNT_FIELDS ||
                raw["ownerUid"] != ownerUid ||
                (raw["accountSchemaVersion"] as? Number)?.toLong() !=
                ACCOUNT_SCHEMA_VERSION.toLong()) {
            return null
        }
        val name = raw["authorName"] as? String ?: return null
        val key = raw["authorKey"] as? String ?: return null
        val normalized = normalize(name) ?: return null
        if (normalized != name || key != keyForCanonicalName(name)) return null
        return CommunityThemeAuthorIdentity(ownerUid, name, key)
    }

    fun claimMatches(identity: CommunityThemeAuthorIdentity, raw: Map<String, Any?>?): Boolean {
        raw ?: return false
        return raw.keys == NAME_FIELDS &&
                raw["ownerUid"] == identity.ownerUid &&
                (raw["nameSchemaVersion"] as? Number)?.toLong() ==
                NAME_SCHEMA_VERSION.toLong() &&
                raw["authorName"] == identity.authorName &&
                raw["authorKey"] == identity.authorKey
    }

    const val ACCOUNT_SCHEMA_VERSION = 1
    const val NAME_SCHEMA_VERSION = 1
    val ACCOUNT_FIELDS = setOf(
            "ownerUid", "accountSchemaVersion", "authorName", "authorKey", "createdAt")
    val NAME_FIELDS = setOf(
            "ownerUid", "nameSchemaVersion", "authorName", "authorKey", "createdAt")
}
