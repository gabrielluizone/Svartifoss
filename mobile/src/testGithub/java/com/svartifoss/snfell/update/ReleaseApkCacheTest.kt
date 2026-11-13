package com.svartifoss.snfell.update

import com.svartifoss.snfell.update.ReleaseApkCache.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReleaseApkCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var cache: ReleaseApkCache
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = File(folder.root, "updates")
        cache = ReleaseApkCache(directory)
    }

    private fun touch(name: String, ageMs: Long = 0L): File =
            File(directory.also { it.mkdirs() }, name).apply {
                writeBytes(byteArrayOf(1, 2, 3))
                setLastModified(System.currentTimeMillis() - ageMs)
            }

    @Test
    fun `phone and watch files for one release do not collide`() {
        val phone = cache.apkFor(Kind.PHONE, "4.4")
        val watch = cache.apkFor(Kind.WATCH, "4.4")
        assertEquals("phone-4.4.apk", phone.name)
        assertEquals("wear-4.4.apk", watch.name)
    }

    @Test
    fun `a tag can not carry a path into the filesystem`() {
        val file = cache.apkFor(Kind.WATCH, "../../evil/tag")
        assertEquals(directory, file.parentFile)
        assertFalse(file.name.contains('/'))
    }

    @Test
    fun `files for any other release are removed when a release is kept`() {
        val keep = touch("wear-4.4.apk")
        val old = touch("wear-4.3.1.apk")
        val oldPartial = touch("phone-4.3.1.apk.part")

        cache.prune(keepTag = "4.4")

        assertTrue(keep.exists())
        assertFalse(old.exists())
        assertFalse(oldPartial.exists())
    }

    @Test
    fun `a device that is up to date loses its files and the other keeps them`() {
        val phone = touch("phone-4.4.apk")
        val phonePartial = touch("phone-4.4.apk.part")
        val watch = touch("wear-4.4.apk")

        cache.prune(keepTag = "4.4", finished = setOf(Kind.PHONE))

        assertFalse(phone.exists())
        assertFalse(phonePartial.exists())
        assertTrue(watch.exists())
    }

    /** The case behind the whole change: a complete download whose next step failed stays. */
    @Test
    fun `a completed download for the current release survives a prune`() {
        val watch = touch("wear-4.4.apk")
        val partial = touch("phone-4.4.apk.part")

        cache.prune(keepTag = "4.4", finished = emptySet())

        assertTrue(watch.exists())
        assertTrue(partial.exists())
    }

    @Test
    fun `an unfinished download is dropped once it is two weeks old`() {
        val fresh = touch("phone-4.4.apk.part", ageMs = 13L * 24 * 60 * 60 * 1000)
        val stale = touch("wear-4.4.apk.part", ageMs = 15L * 24 * 60 * 60 * 1000)
        val completeButOld = touch("wear-4.4.apk", ageMs = 30L * 24 * 60 * 60 * 1000)

        cache.prune(keepTag = "4.4")

        assertTrue(fresh.exists())
        assertFalse(stale.exists())
        // Only unfinished downloads expire by age; a complete file is judged by its release.
        assertTrue(completeButOld.exists())
    }

    @Test
    fun `an unknown newest release keeps every release`() {
        val a = touch("wear-4.3.apk")
        val b = touch("wear-4.4.apk")

        cache.prune(keepTag = null)

        assertTrue(a.exists())
        assertTrue(b.exists())
    }

    @Test
    fun `files that are not the updater's are never touched`() {
        val other = touch("something.txt")
        val lookalike = touch("watch-4.4.apk")

        cache.prune(keepTag = "9.9", finished = Kind.entries.toSet())

        assertTrue(other.exists())
        assertTrue(lookalike.exists())
    }

    @Test
    fun `discarding a kind removes completed and partial files of every release`() {
        val files = listOf(
                touch("phone-4.3.apk"), touch("phone-4.4.apk"), touch("phone-4.4.apk.part"))
        val watch = touch("wear-4.4.apk")

        cache.discard(Kind.PHONE)

        files.forEach { assertFalse(it.exists()) }
        assertTrue(watch.exists())
    }

    @Test
    fun `discarding one release removes its file and its partial`() {
        val apk = touch("wear-4.4.apk")
        val partial = touch("wear-4.4.apk.part")
        val other = touch("wear-4.3.apk")

        cache.discard(Kind.WATCH, "4.4")

        assertFalse(apk.exists())
        assertFalse(partial.exists())
        assertTrue(other.exists())
    }

    @Test
    fun `names are parsed back into what they are`() {
        assertEquals(Triple(Kind.WATCH, "4.4", false), cache.parse("wear-4.4.apk")?.let {
            Triple(it.kind, it.safeTag, it.partial)
        })
        assertEquals(Triple(Kind.PHONE, "4.4-beta2", true), cache.parse("phone-4.4-beta2.apk.part")?.let {
            Triple(it.kind, it.safeTag, it.partial)
        })
        assertNull(cache.parse("phone-.apk"))
        assertNull(cache.parse("readme.txt"))
        assertNull(cache.parse("wear-4.4.zip"))
    }

    @Test
    fun `an empty or missing directory is not an error`() {
        cache.prune(keepTag = "4.4", finished = Kind.entries.toSet())
        cache.discard(Kind.PHONE)
        cache.discard(Kind.WATCH, "4.4")
    }
}
