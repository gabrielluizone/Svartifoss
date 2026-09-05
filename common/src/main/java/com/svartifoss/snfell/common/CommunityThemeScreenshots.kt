package com.svartifoss.snfell.common

/**
 * How an author's watch screenshot is normalized before it is submitted.
 *
 * Every number here has a counterpart the submission cannot cross: the publisher re-parses the
 * encoded result as a RIFF container and refuses anything that is not a plain, still, metadata-free
 * WebP of a square inside [MIN_PIXELS]..[MAX_PIXELS], and `firestore.rules` bounds the base64
 * envelope at [MAX_BASE64_LENGTH]. This object exists so the phone produces something those two
 * accept on the first try, rather than discovering the bound as a generic write failure.
 *
 * Android-free on purpose, so the arithmetic that decides what an author's picture becomes is
 * pinned by a plain JVM test rather than through the Activity that calls it.
 */
object CommunityThemeScreenshots {

    /**
     * The surfaces an author may attach, mirroring `SHOT_SURFACES` in the publisher and the literal
     * list in `firestore.rules`. One value: the Player is where a person spends nearly all of their
     * time, and every other surface costs a moderator another image to judge.
     */
    const val SURFACE_PLAYER = "player"

    val SURFACES: List<String> = listOf(SURFACE_PLAYER)

    /**
     * Matches the common round watch framebuffer (450/454 on most models), so an ordinary
     * screenshot is re-encoded rather than resampled. Deliberately never upscaled: enlarging a
     * small picture invents detail and only costs bytes.
     */
    const val TARGET_PIXELS = 450

    /**
     * Where the publisher commits a screenshot, relative to the theme catalogue, and what it names
     * the file.
     *
     * Written here rather than at the reader, because the publisher's `SHOTS_DIRECTORY` and
     * `shotPath` have to agree with it across a language boundary and a 404 is not distinguishable
     * from an author who attached nothing: every picture would silently stop appearing, and the
     * gallery would look exactly as it does today. `CommunityThemeScreenshotContractTest` pins it.
     */
    const val SHOTS_DIRECTORY = "shots"

    fun fileName(themeId: String, surface: String): String = "$themeId-$surface.webp"

    /** Refused below this, because the publisher refuses it too and a blurry card helps nobody. */
    const val MIN_PIXELS = 128
    const val MAX_PIXELS = 512

    /** The transport envelope `validNewThemeScreenshot` enforces. */
    const val MAX_BASE64_LENGTH = 128 * 1024

    /** Roughly what [MAX_BASE64_LENGTH] of base64 can carry, and what the publisher accepts. */
    const val MAX_BYTES = 96 * 1024

    /**
     * Tried in order until the encoded image fits [MAX_BYTES].
     *
     * A 450x450 screenshot of album artwork lands around 45-70 KB at the first rung, so the rest
     * are for the unusual picture -- heavy noise, a busy photograph behind a translucent face --
     * that would otherwise be refused at the write with nothing an author could act on.
     */
    val QUALITY_LADDER: List<Int> = listOf(85, 75, 65, 55)

    /**
     * The square edge to encode, or 0 when the source is too small to be worth submitting.
     *
     * @param shorterSide the shorter edge of the source image, after any sampling.
     */
    fun targetSize(shorterSide: Int): Int = when {
        shorterSide < MIN_PIXELS -> 0
        else -> minOf(shorterSide, TARGET_PIXELS)
    }

    /**
     * The `BitmapFactory` sample size to decode a source of [shorterSide] with.
     *
     * A phone gallery holds pictures far larger than a watch screen, and decoding one at full size
     * to immediately shrink it is how an attach slot runs out of memory. Powers of two only, and
     * never so aggressive that the result drops below [TARGET_PIXELS] -- sampling past the target
     * would soften the image for no saving that the scale below does not already make.
     */
    fun sampleSize(shorterSide: Int): Int {
        var sample = 1
        while (shorterSide / (sample * 2) >= TARGET_PIXELS) sample *= 2
        return sample
    }

    /** The left edge of the centred square crop of a [width] x [height] source. */
    fun cropLeft(width: Int, height: Int): Int = ((width - minOf(width, height)) / 2).coerceAtLeast(0)

    /** The top edge of the centred square crop of a [width] x [height] source. */
    fun cropTop(width: Int, height: Int): Int = ((height - minOf(width, height)) / 2).coerceAtLeast(0)

    /**
     * Whether an encoded result is worth sending at all.
     *
     * The character class is the one `firestore.rules` applies, checked here so a rejection is a
     * message about the picture rather than an opaque `PERMISSION_DENIED` after Google Sign-In.
     */
    fun isSubmittableEncoding(base64: String): Boolean =
            base64.length in 4..MAX_BASE64_LENGTH && BASE64.matches(base64)

    private val BASE64 = Regex("^[A-Za-z0-9+/]+={0,2}$")
}
