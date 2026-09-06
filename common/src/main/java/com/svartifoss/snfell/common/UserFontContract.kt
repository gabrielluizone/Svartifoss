package com.svartifoss.snfell.common

/**
 * What counts as an importable typeface, and how the two devices agree they hold the same one.
 *
 * The font itself is the one piece of user-supplied *content* in this app that has to reach the
 * wrist. Every other setting is a short string in the preference snapshot; a font is a binary an
 * order of magnitude larger than that whole snapshot, so it travels the way album art does - as a
 * Data Layer **asset** on its own DataItem ([CommPaths.DATA_USER_FONT]) - and not as a preference.
 *
 * This object is the part both sides must agree on: which files are worth accepting, how large one
 * may be, and the [fingerprint] that lets the watch answer "do I already have this?" without
 * re-reading megabytes it was just sent.
 *
 * Pure and free of `android.*` so the decisions here are pinned by a JVM test rather than through
 * the importer that calls them.
 */
object UserFontContract {

    /**
     * Ceiling on an imported font, in bytes.
     *
     * Play Services will carry a far larger asset than this, so the bound is not the transport's -
     * it is the *wait*. An asset crosses Bluetooth at a few hundred KB/s in good conditions, and
     * until it lands the watch renders the previous family, so an 8 MB CJK face would leave the
     * wrist looking broken for most of a minute with nothing on screen explaining why. Two
     * megabytes covers essentially every single-weight Latin TTF/OTF (the bundled Google Sans Flex
     * variable master, which carries six axes and every weight between them, is about four) while
     * keeping the transfer to a few seconds.
     *
     * Enforced on the phone at import, where the file can be rejected with an explanation, rather
     * than at transmit where the only available report is a log line.
     */
    const val MAX_FONT_BYTES: Int = 2 * 1024 * 1024

    /**
     * A font too small to be one.
     *
     * A valid TTF/OTF cannot fit in a few hundred bytes - the required tables alone exceed it - so
     * anything under this is a truncated download, an empty file the picker handed back, or an
     * error page saved with the wrong extension. Rejecting it here means the platform's font
     * parser is never asked to make sense of it.
     */
    const val MIN_FONT_BYTES: Int = 1024

    /**
     * Extensions offered in the file picker and accepted on import.
     *
     * TrueType and OpenType, including the collection containers. Deliberately **not** WOFF or
     * WOFF2: they are the same outlines in a web transport wrapper that Android's font loader
     * cannot read at all, so accepting them would produce a file that imports cleanly, transmits
     * successfully, and renders as nothing on both devices.
     */
    val ACCEPTED_EXTENSIONS: Set<String> = setOf("ttf", "otf", "ttc", "otc")

    /**
     * MIME types to hand the system picker.
     *
     * Font MIME reporting is unreliable across providers - the same `.ttf` comes back as
     * `font/ttf`, `application/x-font-ttf`, `application/octet-stream` or nothing at all depending
     * on which app is sharing it - so this list exists to make the *common* case filter nicely, and
     * the importer validates the actual bytes rather than trusting any of it. A picker filtered to
     * these types alone would grey out files that are perfectly good fonts.
     */
    val PICKER_MIME_TYPES: Array<String> = arrayOf(
            "font/ttf", "font/otf", "font/sfnt", "font/collection",
            "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt",
            "application/octet-stream")

    /** True when [fileName]'s extension is one this app can actually render. */
    fun hasAcceptedExtension(fileName: String?): Boolean =
            fileName?.substringAfterLast('.', "")?.lowercase() in ACCEPTED_EXTENSIONS

    /**
     * Whether [byteCount] is a plausible font size, which is the only size check either side makes.
     */
    fun isAcceptableSize(byteCount: Long): Boolean =
            byteCount in MIN_FONT_BYTES.toLong()..MAX_FONT_BYTES.toLong()

    /**
     * A short, stable identifier for one font's bytes.
     *
     * Sent beside the asset so the watch can skip re-loading a typeface it already holds. It is
     * **not** a security check and does not need to be a cryptographic digest - both ends of this
     * are the same user's two devices, and the failure it guards against is a redundant file write
     * and typeface rebuild, not a forgery. What it does need is to change whenever the bytes do,
     * including for two fonts of identical length, which a length-only key would miss - swapping
     * one weight of a family for another is exactly the case where the sizes come out close.
     *
     * Deliberately samples rather than hashing every byte: this runs on the watch's main thread
     * during a DataItem callback, and a full pass over two megabytes there is the kind of cost that
     * shows up as a dropped frame on a face that is otherwise idle.
     */
    fun fingerprint(bytes: ByteArray): String {
        var hash = -0x7ee3623bL // FNV-1a 64-bit offset basis, truncated to fit a signed Long.
        val stride = maxOf(1, bytes.size / SAMPLE_COUNT)
        var index = 0
        while (index < bytes.size) {
            hash = (hash xor bytes[index].toLong()) * FNV_PRIME
            index += stride
        }
        // The length participates directly so two files that happen to sample identically - a
        // truncated copy of the same font being the realistic case - still differ.
        hash = (hash xor bytes.size.toLong()) * FNV_PRIME
        return java.lang.Long.toHexString(hash)
    }

    private const val SAMPLE_COUNT = 4096
    private const val FNV_PRIME = 0x100000001b3L
}
