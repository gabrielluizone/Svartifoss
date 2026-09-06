package com.svartifoss.snfell.common

import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the tribute face to the app it honours - matejdro's WearMusicCenter, which this is a fork
 * of - and to the rule that a face owns its composition and never its settings.
 *
 * The face is unusual in exactly one way and it is worth stating: almost nothing about how it
 * looks is code. Two proportional text bands are the whole composition; the white artist line, the
 * system typeface, the absent progress and mini buttons and the evenly darkened cover are all
 * ordinary preference values, shipped as per-face defaults so the face arrives looking like the
 * original while staying as editable as every other. That makes the defaults themselves the thing
 * a regression would quietly take away, and nothing else would notice: the face would still
 * render, just as this app rather than as the one it is honouring.
 */
class MatejdroFaceTest {

    private val face = "matejdro"

    private fun default(definition: PreferenceDefinition<*>): String? =
            FaceScopedPreferences.perFaceDefault(face, definition.key)

    /**
     * Registered, and deliberately archived.
     *
     * Both halves matter and they pull in opposite directions. It has to stay in the canonical
     * registry or it stops resolving for anyone already wearing it - retiring a face must never
     * silently change what is on a wrist. And it has to stay in [ArchivedFaces.KEYS] because it is
     * a period piece: it reproduces one 2017 screen, so it belongs behind **Show archived options**
     * rather than in the picker everybody scrolls. That second half is also what keeps it out of
     * the community gallery's base-face registries, which are "allowed minus archived" - so a face
     * moving between those two states is never a one-line change.
     */
    @Test
    fun `the face is a registered renderer, kept behind the archived switch`() {
        assertTrue(
                "The tribute face must be in the canonical registry both sides validate against",
                face in ThemeAppearance.ALLOWED_BASE_FACES)
        assertTrue(
                "It is archived on purpose; the pickers hide it unless archived options are shown",
                face in ArchivedFaces.KEYS)
        assertEquals(face, ThemeAppearance.normalizeBaseFace(face))
    }

    /**
     * The original drew both lines in `@color/white`. Following the album here would reproduce
     * *this* app's look rather than the one being honoured - and unlike the fixed lilac that had
     * to be taken back off Ribbon, white is achromatic, so it competes with no cover.
     */
    @Test
    fun `the artist line defaults to the original's white`() {
        assertEquals("custom", default(MiscPreferences.WEAR_ARTIST_COLOR_MODE))
        assertEquals("#FFFFFF", default(MiscPreferences.WEAR_ARTIST_CUSTOM_COLOR))
    }

    /**
     * The original bundled no font and set no `fontFamily` anywhere: it drew the Wear OS system
     * face. "roboto" is this catalogue's key for exactly that, so the homage needs nothing
     * shipped and adds no licence - which is the whole reason it can be faithful at all.
     */
    @Test
    fun `the text defaults to the system typeface the original used`() {
        assertEquals("roboto", default(MiscPreferences.WEAR_FONT))
    }

    /**
     * Both bands in the original use Android's uniform auto-size. The generic artist default is
     * deliberately static for the other faces, so this face must opt into the smart cascade or a
     * long artist name remains at the band ceiling and is clipped instead of shrinking.
     */
    @Test
    fun `the artist line auto-sizes within its band`() {
        assertEquals(TitleTextMode.SMART, default(MiscPreferences.WEAR_ARTIST_TEXT_MODE))
    }

    /**
     * The first implementation only changed the default, which cannot beat the explicit `static`
     * value an earlier build had already written. The repair must replace that one stale value
     * once, then get out of the way so a later deliberate Static choice is still respected.
     */
    @Test
    fun `an existing fixed artist value is repaired exactly once`() {
        assertEquals(
                TitleTextMode.SMART,
                MatejdroArtistAutosizeMigration.replacementFor(
                        storedValue = "static",
                        alreadyHandled = false))
        assertNull(
                MatejdroArtistAutosizeMigration.replacementFor(
                        storedValue = TitleTextMode.SMART,
                        alreadyHandled = false))
        assertNull(
                MatejdroArtistAutosizeMigration.replacementFor(
                        storedValue = "static",
                        alreadyHandled = true))
    }

    /** Four quadrant hints were the entire control surface; the original showed nothing else. */
    @Test
    fun `the modern chrome defaults off`() {
        assertEquals("false", default(MiscPreferences.WEAR_EDGE_PROGRESS_VISIBLE))
        assertEquals("false", default(MiscPreferences.WEAR_INTERNAL_PROGRESS_VISIBLE))
        assertEquals("false", default(MiscPreferences.WEAR_SHOW_SOURCE_ICON))
        assertEquals(ActivityVisibility.NEVER, default(MiscPreferences.WEAR_MINI_BUTTONS_MODE))
        assertEquals(ActivityVisibility.NEVER, default(MiscPreferences.WEAR_TRACK_TIME_MODE))
    }

    /**
     * `android:alpha="0.333"` on a full-screen ImageView is an even filter over the whole cover,
     * which is [PlayerShadingStyle.FULL_FILTER] at the intensity that leaves a third of the
     * artwork showing. Recomputed here rather than restated, so the day the style's own base alpha
     * moves this says so instead of silently drawing a differently-lit face.
     */
    @Test
    fun `the cover is dimmed to the alpha the original drew it at`() {
        assertEquals(
                PlayerShadingStyle.FULL_FILTER.preferenceValue,
                default(MiscPreferences.WEAR_PLAYER_SHADING_STYLE))

        val strength = default(MiscPreferences.ALBUM_ART_DIM_STRENGTH)
        assertNotNull("The dim has to be stated; the global default is a much lighter one", strength)
        val resultingAlpha = FULL_FILTER_BASE_ALPHA * strength!!.toInt() / 100f
        assertEquals(
                "The filter must leave the artwork at the original's own alpha",
                1f - FaceGeometry.Matejdro.COVER_ALPHA,
                resultingAlpha,
                .01f)

        val range = AppearanceNumericRanges.RANGES[MiscPreferences.ALBUM_ART_DIM_STRENGTH.key]
        assertNotNull("The dim strength is a bounded numeric setting", range)
        assertTrue(
                "A default outside its own range would be unsubmittable as a community theme",
                strength.toInt() in range!!)
    }

    /**
     * The dim strength is the only `Int`-typed key with a per-face default, and
     * [FaceScopedPreferences.getInt]'s built-in-face branch used to never consult
     * [FaceScopedPreferences.perFaceDefault] at all - only the custom-theme branch did, which is
     * the exact shape [FaceScopedBooleanDefaultTest] already documents for booleans. That meant
     * the plain built-in Matejdro face - what almost everyone who picks it is actually using -
     * never drew at the tribute's intended dim level; only a saved custom theme built from it did.
     * This calls [FaceScopedPreferences.getInt] end to end, against an empty preference file, so a
     * regression here fails on the function a renderer actually calls rather than on the registry
     * entry alone.
     */
    @Test
    fun `getInt reaches the built-in face's dim strength default`() {
        val prefs = EmptyPreferences
        val resolved = FaceScopedPreferences.getInt(
                prefs, MiscPreferences.ALBUM_ART_DIM_STRENGTH, AppearanceContext.BuiltIn(face))
        assertEquals(
                default(MiscPreferences.ALBUM_ART_DIM_STRENGTH)!!.toInt(),
                resolved)
        assertTrue(
                "The built-in resolution must actually reach the tribute's own dim, not the " +
                        "much lighter global default",
                resolved != MiscPreferences.ALBUM_ART_DIM_STRENGTH.defaultValue)
    }

    /** One third and two thirds, and the two must account for the whole text area - a face that
     *  left a gap would centre neither band where the original put it. */
    @Test
    fun `the two text bands divide the screen in the original's proportions`() {
        assertEquals(1f / 3f, FaceGeometry.Matejdro.ARTIST_BAND_FRACTION, .0001f)
        assertEquals(2f / 3f, FaceGeometry.Matejdro.TITLE_BAND_FRACTION, .0001f)
        assertEquals(
                1f,
                        FaceGeometry.Matejdro.ARTIST_BAND_FRACTION +
                        FaceGeometry.Matejdro.TITLE_BAND_FRACTION,
                .0001f)
    }

    /** The weighted title band, rather than Classic's XML default, decides how many lines fit. */
    @Test
    fun `the long title ceiling is larger than Classic's two-line default`() {
        assertTrue(FaceGeometry.Matejdro.TITLE_MAX_LINES > FaceGeometry.Classic.ARTIST_MAX_LINES)
    }

    /**
     * The face must not quietly acquire a *layout* opinion. Everything it declares is a value the
     * user can change from the Watch tab; a per-face default naming a key outside the scoped
     * registry would be a setting the settings screen cannot reach or a theme cannot capture.
     */
    @Test
    fun `every default it declares is an ordinary scoped appearance setting`() {
        val declared = FaceScopedPreferences.SCOPED_KEYS.filter {
            FaceScopedPreferences.perFaceDefault(face, it) != null
        }
        assertTrue("The face is expressed through its defaults; it must declare some", declared.isNotEmpty())
        declared.forEach { key ->
            assertTrue(
                    "$key must be exportable, or the wrist never hears about it",
                    MiscPreferences.EXPORTABLE.any { it.key == key })
        }
    }

    private companion object {
        /** `PlayerShadingDrawable`'s FULL_FILTER alpha, which the dim strength scales. */
        const val FULL_FILTER_BASE_ALPHA = .55f
    }

    /** Nothing stored anywhere - proves a resolved value came from the registry, not from a
     *  stray preference the fake happened to answer. */
    private object EmptyPreferences : android.content.SharedPreferences {
        override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any?>()
        override fun getString(key: String?, defValue: String?): String? = defValue
        override fun getStringSet(
                key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = false
        override fun edit(): android.content.SharedPreferences.Editor =
                throw UnsupportedOperationException()
        override fun registerOnSharedPreferenceChangeListener(
                listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
                listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }
}
