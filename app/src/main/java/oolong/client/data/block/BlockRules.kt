package oolong.client.data.block

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import oolong.client.arch.ApplicationScope
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.database.BlockDao
import oolong.client.model.BlockRule
import oolong.client.model.BlockRule.Condition
import oolong.client.model.BlockRule.TextField
import oolong.client.model.ContentKind
import oolong.client.model.PostContent
import oolong.client.model.TextSpan
import oolong.client.model.ThreadSummary
import oolong.client.model.User
import oolong.client.model.flat
import oolong.client.model.tests
import oolong.client.model.wordText
import java.text.Normalizer
import java.util.BitSet
import java.util.IdentityHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Where content is shown, for rules that apply only in some places. */
enum class BlockPlace { Feeds, Forum, Thread, Messages }

/** What block rules look at: who wrote it, what it says, what else it holds, and where. */
data class Blockable(
    val author: User,
    val content: String,
    val place: BlockPlace,
    val title: String = "",
    /** The forum it is in, by name without 吧; null when unknown. */
    val forum: String? = null,
    val kinds: Set<ContentKind> = emptySet(),
    /** How many letters and digits its own words have; emoticons, links, mentions, spaces and punctuation don't count. */
    val length: Int = letterCount(content),
    /** Whether whoever started the thread wrote it. */
    val byThreadAuthor: Boolean = false,
)

/** [content] by [author] as block rules see it: its words for keywords, and what else it holds. */
fun Blockable(
    author: User,
    content: List<PostContent>,
    place: BlockPlace,
    title: String = "",
    forum: String? = null,
    byThreadAuthor: Boolean = false,
): Blockable {
    val kinds = mutableSetOf<ContentKind>()
    var length = 0
    content.forEach { block ->
        when (block) {
            is PostContent.Text -> block.spans.forEach { span ->
                when (span) {
                    is TextSpan.Plain -> {
                        length += letterCount(span.text)
                        if (span.text.hasEmoji()) kinds += ContentKind.Emoticon
                    }
                    is TextSpan.Topic -> length += letterCount(span.text)
                    is TextSpan.Link -> kinds += ContentKind.Link
                    is TextSpan.VideoLink -> kinds += ContentKind.Video
                    is TextSpan.Mention -> kinds += ContentKind.Mention
                    is TextSpan.Emoticon -> kinds += ContentKind.Emoticon
                }
            }
            is PostContent.Image -> kinds += if (block.sticker) ContentKind.Emoticon else ContentKind.Image
            is PostContent.Video -> kinds += ContentKind.Video
            is PostContent.Voice -> kinds += ContentKind.Voice
        }
    }
    // Emoticons are pictures; their names are not words the post says
    return Blockable(author, content.wordText, place, title, forum, kinds, length, byThreadAuthor)
}

/** Content a [rule] matched, and whether it is hidden rather than folded. */
data class BlockVerdict(val rule: BlockRule, val hidden: Boolean)

/** The user's block rules, as they stand. */
fun interface BlockRules {
    /** How [item] is blocked, or null when it is shown. */
    fun check(item: Blockable): BlockVerdict?

    /** Whether nothing is blocked whatever is checked, so there is no need to look at items. */
    val empty: Boolean get() = false
}

/** How the rules block [content] by [author], looked at only when there are rules to look with. */
fun BlockRules.check(
    author: User,
    content: List<PostContent>,
    place: BlockPlace,
    title: String = "",
    forum: String? = null,
    byThreadAuthor: Boolean = false,
): BlockVerdict? = if (empty) null else check(Blockable(author, content, place, title, forum, byThreadAuthor))

/** Whether the rules block something by [author] saying [content] inside a thread. */
fun BlockRules.blocks(author: User, content: String): Boolean =
    check(Blockable(author, content, BlockPlace.Thread)) != null

/**
 * How the rules block [thread] in a list at [place], by its author, its forum, or its first floor,
 * or what lists know of it where that is missing. A recommended reply goes by its own author and
 * what it says.
 */
fun BlockRules.check(thread: ThreadSummary, place: BlockPlace): BlockVerdict? {
    if (empty) return null
    val reply = thread.reply
    if (reply != null) {
        return check(Blockable(reply.author, thread.firstPost, place, title = thread.title, forum = thread.forum.name))
    }
    val item = if (thread.firstPost.isNotEmpty()) {
        Blockable(thread.author, thread.firstPost, place, title = thread.title, forum = thread.forum.name, byThreadAuthor = true)
    } else {
        Blockable(
            thread.author, thread.abstract, place, title = thread.title, forum = thread.forum.name,
            kinds = buildSet {
                if (thread.images.isNotEmpty()) add(ContentKind.Image)
                if (thread.video != null) add(ContentKind.Video)
            },
            byThreadAuthor = true,
        )
    }
    return check(item)
}

/**
 * Items of a list as the block rules have them, remembered for each item, so a list that grows by
 * a page or has one item changed is only checked for what is new. Whatever else the check depends
 * on, such as the rules, goes in the key, and a new key checks everything again.
 */
class BlockMemo<T : Any, R : Any> {
    private var key: Any? = null
    private var done = IdentityHashMap<T, R>()

    @Synchronized
    fun map(key: Any, items: List<T>, transform: (T) -> R): List<R> {
        val before = if (key == this.key) done else IdentityHashMap()
        val now = IdentityHashMap<T, R>(items.size)
        val result = items.map { item -> now.getOrPut(item) { before[item] ?: transform(item) } }
        this.key = key
        done = now
        return result
    }
}

/** Rules that fold whatever [predicate] picks, for previews and tests. */
fun blockRulesMatching(predicate: (Blockable) -> Boolean): BlockRules {
    val rule = BlockRule(condition = Condition.AllOf(emptyList()))
    return BlockRules { item -> if (predicate(item)) BlockVerdict(rule, hidden = false) else null }
}

/** What the block settings add to the rules. */
data class BlockOptions(
    /** Content the rules match is hidden, not folded, unless a rule says otherwise. */
    val hideByDefault: Boolean = false,
    /** Users below this level are folded or hidden; null when off. */
    val lowLevel: Int? = null,
)

/**
 * The rules in force at [now]: an exception that matches shows the content whatever else does;
 * otherwise the first rule that matches blocks it. [onHit] hears of each block.
 */
fun blockRulesOf(
    rules: List<BlockRule>,
    options: BlockOptions = BlockOptions(),
    now: Long = System.currentTimeMillis(),
    onHit: (BlockRule, Blockable) -> Unit = { _, _ -> },
): BlockRules {
    val active = rules.filter { it.isActive(now) } +
        listOfNotNull(options.lowLevel?.let { BlockRule(id = LowLevelRuleId, condition = Condition.LevelBelow(it)) })
    if (active.isEmpty()) return NoBlockRules
    // Every rule's keywords are looked for together, in one pass over each text
    val words = LinkedHashMap<String, Int>()
    val (exceptions, blocks) = active.map { CompiledRule(it, words) }.partition { it.rule.action == BlockRule.Action.Allow }
    val finder = WordFinder(words.keys.toList())
    return BlockRules { item ->
        // Whatever rules look at is worked out once for all of them
        val subject = Subject(item, finder)
        if (exceptions.any { it.matches(subject) }) return@BlockRules null
        val rule = blocks.firstOrNull { it.matches(subject) }?.rule ?: return@BlockRules null
        onHit(rule, item)
        val hidden = when (rule.action) {
            BlockRule.Action.Hide -> true
            BlockRule.Action.Fold -> false
            else -> options.hideByDefault
        }
        BlockVerdict(rule, hidden)
    }
}

/** The id of the rule the low level setting stands for, which is not stored. */
const val LowLevelRuleId = -1L

private object NoBlockRules : BlockRules {
    override val empty: Boolean get() = true

    override fun check(item: Blockable): BlockVerdict? = null
}

/** An item with what tests compare, each worked out when first needed. */
private class Subject(val item: Blockable, private val finder: WordFinder) {
    private val normalized = arrayOfNulls<String>(TextField.entries.size)
    private val found = arrayOfNulls<BitSet>(TextField.entries.size)
    private val both by lazy(LazyThreadSafetyMode.NONE) { item.title + "\n" + item.content }
    val names by lazy(LazyThreadSafetyMode.NONE) {
        listOf(item.author.name, item.author.nameShow).filter { it.isNotBlank() }.distinct()
    }
    val normalizedNames by lazy(LazyThreadSafetyMode.NONE) { names.map(::normalizeForMatching) }

    fun text(field: TextField): String = when (field) {
        TextField.Both -> both
        TextField.Title -> item.title
        TextField.Body -> item.content
    }

    /** [text] as keywords are matched against it. */
    fun words(field: TextField): String =
        normalized[field.ordinal] ?: normalizeForMatching(text(field)).also { normalized[field.ordinal] = it }

    /** The numbers of the rules' keywords that [field] contains. */
    fun found(field: TextField): BitSet = found[field.ordinal] ?: finder.find(words(field)).also { found[field.ordinal] = it }
}

private typealias Test = (Subject) -> Boolean

/** A test and roughly how much it costs, so tests joined together run the cheap ones first. */
private class Compiled(val test: Test, val cost: Int)

/** Comparing fields of the item and of its author. */
private const val CostCheap = 0

/** Looking for words in text normalized once per item. */
private const val CostWords = 1

/** Running a pattern over the text. */
private const val CostPattern = 2

private class CompiledRule(val rule: BlockRule, words: MutableMap<String, Int>) {
    // A test that can't be judged, such as a broken pattern, leaves the whole rule doing nothing:
    // turned around, it would otherwise match everything
    private val test: Test = compile(rule.condition, words)?.test ?: { false }

    // A rule only about forums blocks their threads elsewhere; opening the forum itself shows them
    private val forumOnly = rule.condition.tests.let { tests -> tests.isNotEmpty() && tests.all { it is Condition.Forum } } &&
        rule.condition.flat()?.tests?.none { it.negated } == true

    fun matches(subject: Subject): Boolean {
        val place = subject.item.place
        if (!rule.scope.covers(place)) return false
        if (forumOnly && place == BlockPlace.Forum) return false
        return test(subject)
    }
}

/**
 * [condition] as a test, or null when it can't be judged. Tests joined together run the cheap ones
 * first: which runs first changes nothing but how soon the answer is known.
 */
private fun compile(condition: Condition, words: MutableMap<String, Int>): Compiled? = when (condition) {
    is Condition.AllOf -> condition.conditions.map { compile(it, words) ?: return null }.sortedBy { it.cost }
        .takeIf { it.isNotEmpty() }
        ?.let { parts -> Compiled({ subject -> parts.all { it.test(subject) } }, parts.maxOf { it.cost }) }
    is Condition.AnyOf -> condition.conditions.map { compile(it, words) ?: return null }.sortedBy { it.cost }
        .takeIf { it.isNotEmpty() }
        ?.let { parts -> Compiled({ subject -> parts.any { it.test(subject) } }, parts.maxOf { it.cost }) }
    is Condition.Not -> compile(condition.condition, words)?.let { part -> Compiled({ subject -> !part.test(subject) }, part.cost) }

    is Condition.Text -> when (val pattern = condition.pattern) {
        is BlockRule.Pattern.Regex -> regexOf(pattern)?.let { regex ->
            Compiled({ subject -> regex.containsMatchIn(subject.text(condition.field)) }, CostPattern)
        }
        // By the numbers [WordFinder] gives its words
        is BlockRule.Pattern.Words -> wordsOf(pattern)?.map { word -> words.getOrPut(word) { words.size } }?.let { ids ->
            Compiled(
                { subject ->
                    val found = subject.found(condition.field)
                    ids.all(found::get)
                },
                CostWords,
            )
        }
    }

    // A missing id (0) or blank name must never match, otherwise one rule saved without a name
    // would block every user whose name the server left empty
    is Condition.User -> if (condition.uid == 0L && condition.name.isBlank()) {
        null
    } else {
        cheap { subject ->
            val author = subject.item.author
            (condition.uid != 0L && condition.uid == author.id) ||
                // A name typed in, without an id, may be either name the user is shown by
                (condition.name.isNotBlank() && (condition.name == author.name || (condition.uid == 0L && condition.name == author.nameShow)))
        }
    }
    is Condition.AuthorName -> when (val pattern = condition.pattern) {
        is BlockRule.Pattern.Regex -> regexOf(pattern)?.let { regex ->
            Compiled({ subject -> subject.names.any { regex.containsMatchIn(it) } }, CostPattern)
        }
        is BlockRule.Pattern.Words -> wordsOf(pattern)?.let { wanted ->
            Compiled({ subject -> subject.normalizedNames.any { name -> wanted.all { it in name } } }, CostWords)
        }
    }
    is Condition.LevelBelow -> condition.level.takeIf { it > 0 }?.let { level -> cheap { subject -> subject.item.author.level in 1 until level } }
    Condition.ThreadAuthor -> cheap { subject -> subject.item.byThreadAuthor }
    is Condition.Ip -> condition.region.trim().takeIf { it.isNotEmpty() }?.let { region ->
        cheap { subject -> subject.item.author.ipLocation.trim().equals(region, ignoreCase = true) }
    }
    is Condition.Forum -> forumKey(condition.name).takeIf { it.isNotEmpty() }?.let { key ->
        cheap { subject -> subject.item.forum?.let(::forumKey) == key }
    }
    is Condition.ForumName -> namePattern(condition.pattern)?.let { test ->
        Compiled(
            { subject -> subject.item.forum?.let(test) == true },
            if (condition.pattern is BlockRule.Pattern.Regex) CostPattern else CostWords,
        )
    }
    is Condition.Has -> cheap { subject -> condition.kind in subject.item.kinds }
    is Condition.Only -> cheap { subject -> subject.item.kinds == setOf(condition.kind) && subject.item.length == 0 }
    is Condition.ShorterThan -> condition.length.takeIf { it > 0 }?.let { length -> cheap { subject -> subject.item.length < length } }
}

private fun cheap(test: Test) = Compiled(test, CostCheap)

private fun regexOf(pattern: BlockRule.Pattern.Regex): Regex? =
    pattern.pattern.takeIf { it.isNotEmpty() }?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }

private fun wordsOf(pattern: BlockRule.Pattern.Words): List<String>? =
    pattern.words.map(::normalizeForMatching).filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }

/** A test of a name against [pattern]. */
private fun namePattern(pattern: BlockRule.Pattern): ((String) -> Boolean)? = when (pattern) {
    is BlockRule.Pattern.Regex -> regexOf(pattern)?.let { regex -> { name -> regex.containsMatchIn(name) } }
    is BlockRule.Pattern.Words -> wordsOf(pattern)?.let { words ->
        { name -> normalizeForMatching(name).let { normalized -> words.all { it in normalized } } }
    }
}

private fun BlockRule.Scope.covers(place: BlockPlace) = when (this) {
    BlockRule.Scope.All -> true
    BlockRule.Scope.Feeds -> place == BlockPlace.Feeds
    BlockRule.Scope.Forum -> place == BlockPlace.Forum
    BlockRule.Scope.Thread -> place == BlockPlace.Thread
}

private fun forumKey(name: String) = name.trim().removeSuffix("吧").lowercase()

/** How many letters and digits [text] has. */
fun letterCount(text: String): Int {
    var count = 0
    var i = 0
    while (i < text.length) {
        val code = text.codePointAt(i)
        if (Character.isLetterOrDigit(code)) count++
        i += Character.charCount(code)
    }
    return count
}

/** Whether [this] has emoji or other pictographs, which Unicode files as symbols. */
private fun String.hasEmoji(): Boolean {
    var i = 0
    while (i < length) {
        val code = codePointAt(i)
        if (Character.getType(code) == Character.OTHER_SYMBOL.toInt()) return true
        i += Character.charCount(code)
    }
    return false
}

/**
 * [text] as keywords are matched against it: full-width forms made plain, case folded, and
 * spaces, punctuation and symbols dropped, so "剧 透" and "ＡＢＣ" match "剧透" and "abc".
 */
fun normalizeForMatching(text: String): String {
    // Most posts hold only characters that normalizing leaves as they are, or maps by a fixed
    // offset, which is far quicker to do by hand
    val out = CharArray(text.length)
    var length = 0
    for (c in text) {
        if (!isPlain(c)) return slowNormalizeForMatching(text)
        val char = if (c in '\uFF01'..'\uFF5E') c - FullWidthOffset else c
        if (char in 'A'..'Z') {
            out[length++] = char + ('a' - 'A')
        } else if (char in 'a'..'z' || char in '0'..'9' || (char >= '\u0080' && Character.isLetterOrDigit(char))) {
            out[length++] = char
        }
    }
    return String(out, 0, length)
}

/** [normalizeForMatching] the long way, for any text. */
internal fun slowNormalizeForMatching(text: String): String {
    val plain = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
    return buildString(plain.length) {
        var i = 0
        while (i < plain.length) {
            val code = plain.codePointAt(i)
            if (Character.isLetterOrDigit(code)) appendCodePoint(code)
            i += Character.charCount(code)
        }
    }
}

/** How far full-width forms of ASCII are from ASCII. */
private const val FullWidthOffset = 0xFEE0

/**
 * Whether NFKC leaves [c] as it is, or, for full-width ASCII, moves it by [FullWidthOffset]:
 * ASCII, common CJK ideographs, kana without the marks that combine, and common punctuation.
 */
private fun isPlain(c: Char): Boolean = when (c) {
    in '\u0000'..'\u007F' -> true
    in '\u4E00'..'\u9FFF', in '\u3400'..'\u4DBF' -> true
    in '\u3041'..'\u3096', in '\u30A1'..'\u30FE' -> true
    in '\uFF01'..'\uFF5E' -> true
    in '\u3000'..'\u3003', in '\u3008'..'\u3011', in '\u2010'..'\u2027' -> true
    else -> false
}

interface BlockRuleSource {
    /** The current rules, and new ones whenever the user changes them or their settings. */
    val rules: Flow<BlockRules>
}

@Singleton
class DatabaseBlockRuleSource @Inject constructor(
    private val dao: BlockDao,
    settings: Settings,
    @ApplicationScope private val scope: CoroutineScope,
) : BlockRuleSource {
    private val hits = HitCounter()

    override val rules: Flow<BlockRules> = combine(
        // Hit counts change as content is blocked; matching doesn't look at them
        dao.all().map { entities -> entities.map { it.toBlockRule().copy(hits = 0, lastHitAt = null) } }.distinctUntilChanged(),
        combine(
            settings.flow(SettingKeys.HideBlockedContent),
            settings.flow(SettingKeys.BlockLowLevel),
            settings.flow(SettingKeys.BlockLowLevelBelow),
        ) { hideByDefault, lowLevelOn, below ->
            BlockOptions(hideByDefault, lowLevel = (below.toIntOrNull() ?: DefaultLowLevel).takeIf { lowLevelOn })
        },
    ) { rules, options -> blockRulesOf(rules, options, onHit = hits::record) }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    /**
     * Counts each piece of content once per rule while the app runs, however often lists show it
     * again, and writes the counts a few seconds after they change.
     */
    private inner class HitCounter {
        private val seen = LinkedHashSet<Long>()
        private val pending = mutableMapOf<Long, Long>()
        private var flushing = false

        @Synchronized
        fun record(rule: BlockRule, item: Blockable) {
            if (rule.id <= 0) return
            val key = rule.id * 31 + (item.author.id * 31 + item.content.hashCode()) * 1_000_003
            if (!seen.add(key)) return
            if (seen.size > MaxSeen) seen.remove(seen.first())
            pending[rule.id] = (pending[rule.id] ?: 0) + 1
            if (!flushing) {
                flushing = true
                scope.launch {
                    delay(FlushDelayMillis)
                    flush()
                }
            }
        }

        private suspend fun flush() {
            val counts = synchronized(this) {
                flushing = false
                pending.toMap().also { pending.clear() }
            }
            val at = System.currentTimeMillis()
            counts.forEach { (id, count) -> dao.addHits(id, count, at) }
        }
    }

    private companion object {
        const val MaxSeen = 5_000
        const val FlushDelayMillis = 5_000L
    }
}

const val DefaultLowLevel = 3
