package com.svartifoss.snfell.common

/**
 * The ordered list of blocks the watch's quick-actions panel is built from.
 *
 * The panel used to be one fixed composition - track title and artist, three round buttons, the
 * Up Next row, then the whole actions menu repeated as rows. Its looks were highly configurable
 * (layouts, styles, backgrounds) and its *content* was not configurable at all: there was no way to
 * drop a part of it, reorder it, or put anything else on it, so a panel that had to serve as
 * remote control, launcher and volume knob all at once could only ever be the one thing it was.
 *
 * A stack replaces that. Each [QuickPanelBlock] is one self-contained part of the panel; the user
 * chooses which parts exist, in what order, and when each one is shown. Like
 * [BackgroundLayerStack] it is still plain data - an enumerated type, an enumerated visibility and
 * a few enumerated options per block, no text, no URL, no action - so it adds nothing a hostile
 * value could abuse, and everything action-bearing (the button slots, the actions the favourites
 * block lists) keeps living in the stores that already hold actions.
 *
 * ## Absent versus explicit
 *
 * An **absent** value (`""`, or anything this file refuses to parse) means nobody has composed a
 * panel, and every renderer keeps the original arrangement, driven by the original keys
 * (`wear_quick_panel_layout` and friends). That is also what a watch build that predates this key
 * does with it, so a phone that has moved on degrades to the old panel rather than to none.
 * [implicitStack] says that arrangement as a stack, which is what the editor shows before the first
 * edit and what that first edit is seeded from, so adopting the stack is visually a no-op.
 *
 * Parsing fails closed, as the background stack's does, and for the same reason: dropping the one
 * block a reader did not understand would silently render a different panel than the one that was
 * saved. A stack with no blocks at all is refused too. Unlike a background, an empty panel is not a
 * decision anyone makes; it is what a corrupted value looks like.
 */
object QuickPanelStack {

    /** The grammar's own version. Anything else fails closed, so a later field cannot be misread. */
    const val FORMAT_VERSION = "1"

    /** Every block type at most once, so this is also the number of types, with room to spare. */
    const val MAX_BLOCKS = 12

    /**
     * Ceiling on the encoded value. The longest legal stack is well under it; the check only exists
     * so a value from somewhere unexpected is refused before it is split.
     */
    const val MAX_ENCODED_LENGTH = 360

    private const val BLOCK_SEPARATOR = '|'
    private const val FIELD_SEPARATOR = '.'
    private const val PAIR_SEPARATOR = ':'
    private const val LIST_SEPARATOR = '+'
    private const val VISIBILITY_KEY = "v"

    /** The seconds a seek block may offer, smallest first. Anything else would make a chip
     *  whose label promises a jump the shared seek path was never tested at. */
    val SEEK_STEP_VOCABULARY: List<Int> = listOf(5, 10, 15, 30, 60)

    /**
     * Two steps are four chips (back and forward for each), which is as many as fit across a round
     * 192dp screen while each stays a 48dp touch target. A third would make six chips of about
     * 33dp, too small to hit with a finger.
     */
    const val MAX_SEEK_STEPS = 2
    const val MAX_TOOLS = 8

    /**
     * The explicit stack [raw] encodes, or null when there is none.
     *
     * Null means "keep the original panel", which is why a value that is only partly understood is
     * refused outright instead of being read as far as it goes.
     */
    fun parse(raw: String?): List<QuickPanelBlock>? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.length > MAX_ENCODED_LENGTH) return null
        val parts = value.split(BLOCK_SEPARATOR)
        if (parts.first() != FORMAT_VERSION) return null
        val encodedBlocks = parts.drop(1)
        if (encodedBlocks.isEmpty() || encodedBlocks.size > MAX_BLOCKS) return null

        val blocks = ArrayList<QuickPanelBlock>(encodedBlocks.size)
        val seen = HashSet<QuickPanelBlockType>()
        for (encoded in encodedBlocks) {
            val block = parseBlock(encoded) ?: return null
            // One of each: the editor offers a type only while it is missing, and a renderer that
            // keeps one view per type would otherwise add the same view to the panel twice.
            if (!seen.add(block.type)) return null
            blocks += block
        }
        return blocks
    }

    /** True when [raw] is a stack this build renders, i.e. [parse] accepts it. */
    fun isExplicit(raw: String?): Boolean = parse(raw) != null

    /**
     * The persisted form of [blocks], always parseable by [parse] (for a non-empty list).
     *
     * Defaults are left out, so two stacks that mean the same encode identically, and a stack the
     * user has not tuned stays short.
     */
    fun encode(blocks: List<QuickPanelBlock>): String =
            (listOf(FORMAT_VERSION) + blocks.take(MAX_BLOCKS).map(::encodeBlock))
                    .joinToString(BLOCK_SEPARATOR.toString())

    /**
     * The original panel, said as a stack: title and artist, the round buttons, Up Next, and the
     * actions menu repeated as rows.
     *
     * Not tidied. It is what the watch has always drawn, and the first edit starts from it, so a
     * block the old panel did not have must not appear here.
     */
    fun implicitStack(): List<QuickPanelBlock> = listOf(
            QuickPanelBlock(QuickPanelBlockType.HEADER),
            QuickPanelBlock(QuickPanelBlockType.BUTTONS),
            QuickPanelBlock(QuickPanelBlockType.UP_NEXT),
            QuickPanelBlock(QuickPanelBlockType.ACTIONS))

    /**
     * The stack a renderer should draw: the explicit one, else [implicitStack].
     *
     * Every consumer goes through this rather than testing the raw value itself, so "is there a
     * stack" is answered the same way on the watch, in the phone's preview and in the editor.
     */
    fun resolve(raw: String?): List<QuickPanelBlock> = parse(raw) ?: implicitStack()

    /**
     * What an editor should start from: the explicit stack when there is one, else the implicit one.
     * The same answer as [resolve]; named for the job so the call site reads as what it means.
     */
    fun editable(raw: String?): List<QuickPanelBlock> = resolve(raw)

    /** The blocks of [blocks] that are on screen while playback is [playing]. */
    fun visibleFor(blocks: List<QuickPanelBlock>, playing: Boolean): List<QuickPanelBlock> =
            blocks.filter { it.visibility.shownWhen(playing) }

    /** The block types [blocks] does not contain yet - what "Add block" can offer. */
    fun missingTypes(blocks: List<QuickPanelBlock>): List<QuickPanelBlockType> {
        val present = blocks.mapTo(HashSet()) { it.type }
        return QuickPanelBlockType.entries.filterNot { it in present }
    }

    /** A new block of [type] on its defaults. */
    fun newBlock(type: QuickPanelBlockType): QuickPanelBlock = QuickPanelBlock(type)

    /**
     * [blocks] with the entry at [index] moved by [delta], or the list unchanged when it cannot be.
     * A move off either end is a no-op rather than a wrap, for the reason
     * [BackgroundLayerStack.move] gives: the caller is a pair of arrows someone is holding down.
     */
    fun move(blocks: List<QuickPanelBlock>, index: Int, delta: Int): List<QuickPanelBlock> {
        val target = index + delta
        if (index !in blocks.indices || target !in blocks.indices) return blocks
        val reordered = blocks.toMutableList()
        reordered.add(target, reordered.removeAt(index))
        return reordered
    }

    /** [blocks] with the entry at [from] relocated so it sits at [to]. */
    fun moveTo(blocks: List<QuickPanelBlock>, from: Int, to: Int): List<QuickPanelBlock> {
        if (from !in blocks.indices || to !in blocks.indices || from == to) return blocks
        val reordered = blocks.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return reordered
    }

    /** [blocks] without the entry at [index]. */
    fun remove(blocks: List<QuickPanelBlock>, index: Int): List<QuickPanelBlock> {
        if (index !in blocks.indices) return blocks
        return blocks.toMutableList().also { it.removeAt(index) }
    }

    /** [blocks] with [block] at the end, if its type is not there already and there is room. */
    fun add(blocks: List<QuickPanelBlock>, block: QuickPanelBlock): List<QuickPanelBlock> =
            if (blocks.size >= MAX_BLOCKS || blocks.any { it.type == block.type }) {
                blocks
            } else {
                blocks + block
            }

    /** [blocks] with the block of [replacement]'s type swapped for it. */
    fun replace(blocks: List<QuickPanelBlock>, replacement: QuickPanelBlock): List<QuickPanelBlock> =
            blocks.map { if (it.type == replacement.type) replacement else it }

    private fun parseBlock(encoded: String): QuickPanelBlock? {
        val fields = encoded.split(FIELD_SEPARATOR)
        val type = QuickPanelBlockType.fromToken(fields.first()) ?: return null

        var visibility = QuickPanelVisibility.ALWAYS
        val options = LinkedHashMap<String, String>()
        for (field in fields.drop(1)) {
            val colon = field.indexOf(PAIR_SEPARATOR)
            if (colon <= 0 || colon == field.lastIndex) return null
            val key = field.substring(0, colon)
            val value = field.substring(colon + 1)
            if (key == VISIBILITY_KEY) {
                visibility = QuickPanelVisibility.fromToken(value) ?: return null
            } else {
                if (key in options) return null
                options[key] = canonicalOption(type, key, value) ?: return null
            }
        }
        return QuickPanelBlock(type, visibility, options.toMap())
    }

    private fun encodeBlock(block: QuickPanelBlock): String {
        val fields = mutableListOf(block.type.token)
        if (block.visibility != QuickPanelVisibility.ALWAYS) {
            fields += "$VISIBILITY_KEY$PAIR_SEPARATOR${block.visibility.token}"
        }
        block.options.toSortedMap().forEach { (key, value) ->
            // Only what differs from the default is written, and only what the type accepts: an
            // option the type does not know cannot be carried here by accident.
            val canonical = canonicalOption(block.type, key, value) ?: return@forEach
            if (canonical != block.type.defaultOption(key)) {
                fields += "$key$PAIR_SEPARATOR$canonical"
            }
        }
        return fields.joinToString(FIELD_SEPARATOR.toString())
    }

    /**
     * [value] in its one canonical spelling for [key] on a [type] block, or null when the type does
     * not accept it. This is the whole vocabulary of options - nothing else gets through [parse].
     */
    internal fun canonicalOption(type: QuickPanelBlockType, key: String, value: String): String? =
            when (type to key) {
                QuickPanelBlockType.VOLUME to OPTION_STEP ->
                    value.takeIf { it in VOLUME_STEPS.map(Int::toString) }

                QuickPanelBlockType.SEEK to OPTION_STEPS -> canonicalSeekSteps(value)
                QuickPanelBlockType.SEEK to OPTION_BAR -> value.takeIf { it == "0" || it == "1" }

                QuickPanelBlockType.TOOLS to OPTION_LIST -> canonicalTools(value)

                QuickPanelBlockType.FAVORITES to OPTION_MODE ->
                    value.takeIf { FavoritesMode.fromToken(it) != null }

                QuickPanelBlockType.FAVORITES to OPTION_MAX ->
                    value.takeIf { it in FAVORITES_LIMITS.map(Int::toString) }

                QuickPanelBlockType.ACTIONS to OPTION_MAX ->
                    value.takeIf { it in ACTIONS_LIMITS.map(Int::toString) }

                else -> null
            }

    /** Distinct values from [SEEK_STEP_VOCABULARY], at most [MAX_SEEK_STEPS], ascending. */
    private fun canonicalSeekSteps(value: String): String? {
        val steps = value.split(LIST_SEPARATOR).map { it.toIntOrNull() ?: return null }
        if (steps.isEmpty() || steps.size > MAX_SEEK_STEPS) return null
        if (steps.any { it !in SEEK_STEP_VOCABULARY }) return null
        if (steps.toSet().size != steps.size) return null
        return steps.sorted().joinToString(LIST_SEPARATOR.toString())
    }

    /** Distinct tool tokens, at most [MAX_TOOLS], in the order given - the order is the point. */
    private fun canonicalTools(value: String): String? {
        val tokens = value.split(LIST_SEPARATOR)
        if (tokens.isEmpty() || tokens.size > MAX_TOOLS) return null
        if (tokens.any { QuickPanelTool.fromToken(it) == null }) return null
        if (tokens.toSet().size != tokens.size) return null
        return tokens.joinToString(LIST_SEPARATOR.toString())
    }

    const val OPTION_STEP = "step"
    const val OPTION_STEPS = "steps"
    const val OPTION_BAR = "bar"
    const val OPTION_LIST = "list"
    const val OPTION_MODE = "mode"
    const val OPTION_MAX = "max"

    /** Volume change per press, in percent of the full range. */
    val VOLUME_STEPS: List<Int> = listOf(5, 10, 20)

    /** How many favourites to list; 0 is "all of them". */
    val FAVORITES_LIMITS: List<Int> = listOf(0, 3, 6, 9, 12)

    /** How many of the menu's rows the full-list block repeats; 0 is "all of them". */
    val ACTIONS_LIMITS: List<Int> = listOf(0, 3, 5, 8)
}

/** One part of the quick panel. See [QuickPanelStack]. */
enum class QuickPanelBlockType(val token: String) {
    /** Track title and artist. */
    HEADER("header"),

    /** The round buttons: three assignable slots, or the playing app's own actions. */
    BUTTONS("buttons"),

    /** The row that previews the next track and opens the queue. */
    UP_NEXT("upnext"),

    /** A volume row: lower, a level bar, raise. */
    VOLUME("volume"),

    /** The position and chips that jump by a few seconds. */
    SEEK("seek"),

    /** A row of small built-in tools, some of which show their own state. */
    TOOLS("tools"),

    /** The actions the user starred for the panel, as a grid of covers or as rows. */
    FAVORITES("favorites"),

    /** The whole actions menu repeated as rows - what the panel has always listed. */
    ACTIONS("actions"),

    /** One row that opens the full actions menu. */
    MENU_LINK("menu");

    /** The value of option [key] when the stack does not name one. */
    fun defaultOption(key: String): String? = when (this to key) {
        VOLUME to QuickPanelStack.OPTION_STEP -> "10"
        SEEK to QuickPanelStack.OPTION_STEPS -> "10+30"
        SEEK to QuickPanelStack.OPTION_BAR -> "1"
        TOOLS to QuickPanelStack.OPTION_LIST -> QuickPanelTool.DEFAULT_LIST
        FAVORITES to QuickPanelStack.OPTION_MODE -> FavoritesMode.GRID.token
        FAVORITES to QuickPanelStack.OPTION_MAX -> "6"
        ACTIONS to QuickPanelStack.OPTION_MAX -> "0"
        else -> null
    }

    companion object {
        fun fromToken(token: String?): QuickPanelBlockType? = entries.firstOrNull { it.token == token }
    }
}

/** When a block is on screen. */
enum class QuickPanelVisibility(val token: String) {
    ALWAYS("a"),

    /** Only while something is playing. */
    PLAYING("p"),

    /** Only while nothing is playing - paused or stopped. */
    NOT_PLAYING("i");

    fun shownWhen(playing: Boolean): Boolean = when (this) {
        ALWAYS -> true
        PLAYING -> playing
        NOT_PLAYING -> !playing
    }

    companion object {
        fun fromToken(token: String?): QuickPanelVisibility? =
                entries.firstOrNull { it.token == token }
    }
}

/** How the favourites block lays its entries out. */
enum class FavoritesMode(val token: String) {
    /** Covers or icons in a grid, three to a line, each with its name beneath. */
    GRID("grid"),

    /** Full-width rows, like the actions menu's. */
    ROWS("rows");

    companion object {
        fun fromToken(token: String?): FavoritesMode? = entries.firstOrNull { it.token == token }
    }
}

/**
 * The built-in tools a tools block may offer, in the order the editor lists them.
 *
 * Each one is something the watch already does on its own - opening a screen it has, or sending a
 * command the phone already understands - so a tool never needs an action from the phone's list.
 */
enum class QuickPanelTool(val token: String) {
    /** Cycles the playback speed. Shows the current one. */
    SPEED("speed"),

    /** Opens the synced-lyrics screen. */
    LYRICS("lyrics"),

    /** Opens the playback queue. */
    QUEUE("queue"),

    /** Opens the dedicated volume screen. */
    VOLUME("volume"),

    /** Opens the dedicated progress screen. */
    PROGRESS("progress"),

    /** Opens the watch face picker. */
    FACES("faces"),

    /** Starts a voice search. */
    SEARCH("search"),

    /** Opens the full actions menu. */
    MENU("menu");

    companion object {
        /** What a tools block offers before anyone has chosen. */
        const val DEFAULT_LIST = "speed+lyrics+queue"

        fun fromToken(token: String?): QuickPanelTool? = entries.firstOrNull { it.token == token }

        /** Decodes a canonical tool list, skipping nothing: [QuickPanelStack.parse] already
         *  refused any list holding a token this build does not know. */
        fun decode(list: String): List<QuickPanelTool> =
                list.split('+').mapNotNull(::fromToken)
    }
}

/**
 * One entry in a [QuickPanelStack]: a type, when it is shown, and its enumerated options.
 *
 * [options] holds only values [QuickPanelStack] accepts, spelled canonically; the typed accessors
 * below are how everything reads them, so no caller parses a string of its own.
 */
data class QuickPanelBlock(
        val type: QuickPanelBlockType,
        val visibility: QuickPanelVisibility = QuickPanelVisibility.ALWAYS,
        val options: Map<String, String> = emptyMap()
) {
    private fun option(key: String): String? = options[key] ?: type.defaultOption(key)

    /** Volume change per press, as a fraction of the full range. */
    val volumeStep: Float
        get() = (option(QuickPanelStack.OPTION_STEP)?.toIntOrNull() ?: 10) / 100f

    /** The seconds the seek chips jump by, smallest first; each is offered backwards and forwards. */
    val seekSteps: List<Int>
        get() = option(QuickPanelStack.OPTION_STEPS)
                ?.split('+')?.mapNotNull(String::toIntOrNull)
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(10, 30)

    /** Whether the seek block draws the time and the progress bar above its chips. */
    val seekShowsBar: Boolean
        get() = option(QuickPanelStack.OPTION_BAR) != "0"

    /** The tools of a tools block, in the order they are drawn. */
    val tools: List<QuickPanelTool>
        get() = QuickPanelTool.decode(
                option(QuickPanelStack.OPTION_LIST) ?: QuickPanelTool.DEFAULT_LIST)

    val favoritesMode: FavoritesMode
        get() = FavoritesMode.fromToken(option(QuickPanelStack.OPTION_MODE)) ?: FavoritesMode.GRID

    /** How many entries a favourites or actions block lists; 0 means all of them. */
    val maxEntries: Int
        get() = option(QuickPanelStack.OPTION_MAX)?.toIntOrNull() ?: 0

    /** This block with option [key] set to [value], or unchanged when the type does not accept it. */
    fun withOption(key: String, value: String): QuickPanelBlock {
        val canonical = QuickPanelStack.canonicalOption(type, key, value) ?: return this
        return copy(options = options + (key to canonical))
    }

    fun withVisibility(visibility: QuickPanelVisibility): QuickPanelBlock =
            copy(visibility = visibility)

    fun withSeekSteps(steps: List<Int>): QuickPanelBlock =
            withOption(QuickPanelStack.OPTION_STEPS, steps.joinToString("+"))

    fun withTools(tools: List<QuickPanelTool>): QuickPanelBlock =
            withOption(QuickPanelStack.OPTION_LIST, tools.joinToString("+") { it.token })
}
