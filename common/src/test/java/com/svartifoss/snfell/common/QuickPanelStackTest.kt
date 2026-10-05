package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stack decides what the watch's busiest screen contains, so the decisions worth pinning are
 * the ones that stay invisible when they go wrong: that a panel nobody has edited keeps the panel
 * it always was, that a value this build only half understands is refused instead of partly drawn,
 * and that one panel has exactly one spelling.
 */
class QuickPanelStackTest {

    @Test
    fun `an absent value has no explicit stack and resolves to the original panel`() {
        assertNull(QuickPanelStack.parse(null))
        assertNull(QuickPanelStack.parse(""))
        assertNull(QuickPanelStack.parse("   "))
        assertFalse(QuickPanelStack.isExplicit(null))
        assertEquals(QuickPanelStack.implicitStack(), QuickPanelStack.resolve(null))
        assertEquals(QuickPanelStack.implicitStack(), QuickPanelStack.resolve("garbage"))
    }

    @Test
    fun `the implicit stack is exactly what the panel always drew`() {
        // Title and artist, the round buttons, Up Next, then the actions menu repeated as rows. A
        // block the old panel did not have must never appear here: the first edit starts from this
        // list, and a surprise block would make adopting the stack a visible change.
        assertEquals(
                listOf(
                        QuickPanelBlockType.HEADER,
                        QuickPanelBlockType.BUTTONS,
                        QuickPanelBlockType.UP_NEXT,
                        QuickPanelBlockType.ACTIONS),
                QuickPanelStack.implicitStack().map { it.type })
        assertTrue(QuickPanelStack.implicitStack().all {
            it.visibility == QuickPanelVisibility.ALWAYS && it.options.isEmpty()
        })
    }

    @Test
    fun `a stack round trips through its shortest form`() {
        val blocks = listOf(
                QuickPanelBlock(QuickPanelBlockType.HEADER),
                QuickPanelBlock(QuickPanelBlockType.BUTTONS),
                QuickPanelBlock(QuickPanelBlockType.VOLUME).withOption("step", "20"),
                QuickPanelBlock(QuickPanelBlockType.TOOLS)
                        .withTools(listOf(QuickPanelTool.LYRICS, QuickPanelTool.SPEED)),
                QuickPanelBlock(QuickPanelBlockType.FAVORITES, QuickPanelVisibility.NOT_PLAYING)
                        .withOption("mode", "rows"),
                QuickPanelBlock(QuickPanelBlockType.ACTIONS).withActionsMode(ActionsMode.BUTTON))
        val encoded = QuickPanelStack.encode(blocks)

        assertEquals(
                "1|header|buttons|volume.step:20|tools.list:lyrics+speed" +
                        "|favorites.v:i.mode:rows|actions.mode:button",
                encoded)
        assertEquals(blocks, QuickPanelStack.parse(encoded))
    }

    @Test
    fun `one panel has one encoding`() {
        // Defaults are dropped, so a block that was tuned back to its default encodes the same as
        // one never touched - and an explicit default in an incoming value reads back the same too.
        val untouched = QuickPanelBlock(QuickPanelBlockType.VOLUME)
        val spelledOut = untouched.withOption("step", "10")
        assertEquals(
                QuickPanelStack.encode(listOf(untouched)),
                QuickPanelStack.encode(listOf(spelledOut)))
        assertEquals("1|volume", QuickPanelStack.encode(listOf(spelledOut)))

        val explicit = QuickPanelStack.parse("1|volume.step:10")
        assertNotNull(explicit)
        assertEquals("1|volume", QuickPanelStack.encode(explicit!!))
    }

    @Test
    fun `seek steps are canonical whatever order they arrive in`() {
        val block = QuickPanelBlock(QuickPanelBlockType.SEEK).withSeekSteps(listOf(30, 10))
        assertEquals(listOf(10, 30), block.seekSteps)
        // Which is the default, so nothing is written.
        assertEquals("1|seek", QuickPanelStack.encode(listOf(block)))

        val other = QuickPanelBlock(QuickPanelBlockType.SEEK).withSeekSteps(listOf(30, 5))
        assertEquals("1|seek.steps:5+30", QuickPanelStack.encode(listOf(other)))
        assertEquals(listOf(5, 30), QuickPanelStack.parse("1|seek.steps:30+5")!!.single().seekSteps)
    }

    @Test
    fun `an option the type does not accept is refused rather than ignored`() {
        // A value from a newer build or a corrupted sync. Reading the rest of it would draw a
        // different panel than the one that was saved.
        assertNull(QuickPanelStack.parse("1|header.step:10"))
        assertNull(QuickPanelStack.parse("1|volume.step:7"))
        assertNull(QuickPanelStack.parse("1|volume.wat:1"))
        assertNull(QuickPanelStack.parse("1|seek.steps:7"))
        assertNull(QuickPanelStack.parse("1|seek.steps:10+10"))
        // Three steps would be six chips on a screen that fits four touch targets across.
        assertNull(QuickPanelStack.parse("1|seek.steps:5+10+30"))
        // The chips are Material's replay and forward arrows, which exist for 5, 10 and 30 only.
        assertNull(QuickPanelStack.parse("1|seek.steps:15"))
        assertNull(QuickPanelStack.parse("1|seek.steps:60"))
        assertNull(QuickPanelStack.parse("1|tools.list:speed+speed"))
        assertNull(QuickPanelStack.parse("1|tools.list:teleport"))
        assertNull(QuickPanelStack.parse("1|favorites.mode:carousel"))
        assertNull(QuickPanelStack.parse("1|favorites.max:7"))
        assertNull(QuickPanelStack.parse("1|actions.max:4"))
        assertNull(QuickPanelStack.parse("1|actions.mode:carousel"))
    }

    @Test
    fun `an unknown version, type or visibility fails closed`() {
        assertNull(QuickPanelStack.parse("2|header"))
        assertNull(QuickPanelStack.parse("header|buttons"))
        assertNull(QuickPanelStack.parse("1|header|hologram"))
        assertNull(QuickPanelStack.parse("1|header.v:x"))
        assertNull(QuickPanelStack.parse("1|header.v:"))
        assertNull(QuickPanelStack.parse("1|header.:p"))
    }

    @Test
    fun `an empty or duplicated stack is refused`() {
        // An empty panel is not a decision anyone makes - it is what a corrupted value looks like.
        assertNull(QuickPanelStack.parse("1"))
        // One of each type: a renderer that keeps one view per type would add it twice.
        assertNull(QuickPanelStack.parse("1|header|buttons|header"))
    }

    @Test
    fun `an oversized value is refused before it is split`() {
        val huge = "1|" + "header|".repeat(200)
        assertTrue(huge.length > QuickPanelStack.MAX_ENCODED_LENGTH)
        assertNull(QuickPanelStack.parse(huge))
    }

    @Test
    fun `visibility selects blocks by whether music is playing`() {
        val blocks = listOf(
                QuickPanelBlock(QuickPanelBlockType.HEADER),
                QuickPanelBlock(QuickPanelBlockType.SEEK, QuickPanelVisibility.PLAYING),
                QuickPanelBlock(QuickPanelBlockType.FAVORITES, QuickPanelVisibility.NOT_PLAYING))

        assertEquals(
                listOf(QuickPanelBlockType.HEADER, QuickPanelBlockType.SEEK),
                QuickPanelStack.visibleFor(blocks, playing = true).map { it.type })
        assertEquals(
                listOf(QuickPanelBlockType.HEADER, QuickPanelBlockType.FAVORITES),
                QuickPanelStack.visibleFor(blocks, playing = false).map { it.type })
    }

    @Test
    fun `typed accessors fall back to the documented defaults`() {
        val tools = QuickPanelBlock(QuickPanelBlockType.TOOLS)
        assertEquals(
                listOf(QuickPanelTool.SPEED, QuickPanelTool.LYRICS, QuickPanelTool.QUEUE),
                tools.tools)
        assertEquals(0.10f, QuickPanelBlock(QuickPanelBlockType.VOLUME).volumeStep, 0.0001f)
        assertEquals(listOf(10, 30), QuickPanelBlock(QuickPanelBlockType.SEEK).seekSteps)
        assertTrue(QuickPanelBlock(QuickPanelBlockType.SEEK).seekShowsBar)
        assertEquals(FavoritesMode.GRID, QuickPanelBlock(QuickPanelBlockType.FAVORITES).favoritesMode)
        assertEquals(6, QuickPanelBlock(QuickPanelBlockType.FAVORITES).maxEntries)
        // The full-list block has always listed everything, so that is what "unset" means there.
        assertEquals(0, QuickPanelBlock(QuickPanelBlockType.ACTIONS).maxEntries)
        assertEquals(ActionsMode.LIST, QuickPanelBlock(QuickPanelBlockType.ACTIONS).actionsMode)
    }

    @Test
    fun `the actions block is one block that is a list or a button`() {
        val button = QuickPanelStack.parse("1|header|actions.mode:button")!!.last()
        assertEquals(ActionsMode.BUTTON, button.actionsMode)

        // Switching to the button keeps the row limit, so switching back finds it where it was.
        val limited = QuickPanelBlock(QuickPanelBlockType.ACTIONS).withOption("max", "5")
        val asButton = limited.withActionsMode(ActionsMode.BUTTON)
        assertEquals(5, asButton.maxEntries)
        assertEquals(ActionsMode.LIST, asButton.withActionsMode(ActionsMode.LIST).actionsMode)
        // And a list is the spelling that needs no extra field, so an untouched block stays short.
        assertEquals("1|actions", QuickPanelStack.encode(listOf(asButton.withActionsMode(ActionsMode.LIST)
                .withOption("max", "0"))))

        // The separate "menu link" block this replaced is gone: its token is an unknown type, and a
        // value that still carries it fails closed instead of drawing a panel without it.
        assertNull(QuickPanelStack.parse("1|header|menu"))
    }

    @Test
    fun `every tool can be offered at once and round trips`() {
        // The sleep timer made nine; the ceiling follows the enum instead of being a number to forget.
        val all = QuickPanelTool.entries
        assertTrue(all.size <= QuickPanelStack.MAX_TOOLS)
        val block = QuickPanelBlock(QuickPanelBlockType.TOOLS).withTools(all)
        assertEquals(all, block.tools)
        val encoded = QuickPanelStack.encode(listOf(block))
        assertEquals(all, QuickPanelStack.parse(encoded)!!.single().tools)
        assertTrue(encoded.length <= QuickPanelStack.MAX_ENCODED_LENGTH)
        assertEquals(QuickPanelTool.SLEEP, QuickPanelTool.fromToken("sleep"))
    }

    @Test
    fun `a bad option leaves the block untouched`() {
        val volume = QuickPanelBlock(QuickPanelBlockType.VOLUME)
        assertSame(volume, volume.withOption("step", "13"))
        assertSame(volume, volume.withOption("nope", "1"))
        // And an empty tool list cannot be set: a tools block with nothing in it draws nothing.
        val tools = QuickPanelBlock(QuickPanelBlockType.TOOLS)
        assertSame(tools, tools.withTools(emptyList()))
    }

    @Test
    fun `add offers only missing types and never duplicates one`() {
        val start = QuickPanelStack.implicitStack()
        val missing = QuickPanelStack.missingTypes(start)
        assertFalse(QuickPanelBlockType.HEADER in missing)
        assertTrue(QuickPanelBlockType.VOLUME in missing)

        val added = QuickPanelStack.add(start, QuickPanelStack.newBlock(QuickPanelBlockType.VOLUME))
        assertEquals(start.size + 1, added.size)
        // Adding a type that is already there changes nothing.
        assertEquals(added, QuickPanelStack.add(added, QuickPanelStack.newBlock(QuickPanelBlockType.VOLUME)))
    }

    @Test
    fun `the stack never grows past its ceiling`() {
        assertTrue(QuickPanelBlockType.entries.size <= QuickPanelStack.MAX_BLOCKS)
        val all = QuickPanelBlockType.entries.map(QuickPanelStack::newBlock)
        assertEquals(all, QuickPanelStack.parse(QuickPanelStack.encode(all)))
    }

    @Test
    fun `move shifts one step and is a no-op off either end`() {
        val blocks = QuickPanelStack.implicitStack()
        assertEquals(blocks, QuickPanelStack.move(blocks, 0, -1))
        assertEquals(blocks, QuickPanelStack.move(blocks, blocks.lastIndex, 1))
        val moved = QuickPanelStack.move(blocks, 1, 1)
        assertEquals(
                listOf(
                        QuickPanelBlockType.HEADER,
                        QuickPanelBlockType.UP_NEXT,
                        QuickPanelBlockType.BUTTONS,
                        QuickPanelBlockType.ACTIONS),
                moved.map { it.type })
    }

    @Test
    fun `moveTo relocates an entry to a position`() {
        val blocks = QuickPanelStack.implicitStack()
        val moved = QuickPanelStack.moveTo(blocks, 3, 0)
        assertEquals(QuickPanelBlockType.ACTIONS, moved.first().type)
        assertEquals(blocks, QuickPanelStack.moveTo(blocks, 2, 2))
        assertEquals(blocks, QuickPanelStack.moveTo(blocks, 9, 0))
    }

    @Test
    fun `remove and replace keep the rest in order`() {
        val blocks = QuickPanelStack.implicitStack()
        assertEquals(
                listOf(
                        QuickPanelBlockType.HEADER,
                        QuickPanelBlockType.UP_NEXT,
                        QuickPanelBlockType.ACTIONS),
                QuickPanelStack.remove(blocks, 1).map { it.type })
        assertEquals(blocks, QuickPanelStack.remove(blocks, 40))

        val limited = QuickPanelBlock(QuickPanelBlockType.ACTIONS).withOption("max", "5")
        val replaced = QuickPanelStack.replace(blocks, limited)
        assertEquals(5, replaced.last().maxEntries)
        assertEquals(blocks.size, replaced.size)
    }
}
