//usr/bin/env [ $0 -nt $0.jar ] && kotlinc -d $0.jar $0; [ $0.jar -nt $0 ] && java -cp $CLASSPATH:$0.jar VimjournalKt $@; exit 0

import java.io.BufferedReader
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit.MINUTES
import java.util.LinkedList
import java.util.NoSuchElementException
  
data class Entry(
    val seq: String,
    val zone: String = "XXX",
    val header: String = "",
    val tags: List<String> = listOf(),
    val body: String = "",
    val rating: String = "",
    val seqtype: String = "",
)

fun def_format() {
    Entry("XXXXXXXX_XXXX", "ABC", "").format() returns "XXXXXXXX_XXXX ABC  │"
    Entry("XXXXXXXX_XXXX", "ABC", "hello world").format() returns "XXXXXXXX_XXXX ABC  │ hello world"
    Entry("XXXXXXXX_XXXX", "ABC", "hello world", rating="+").format() returns "XXXXXXXX_XXXX ABC +│ hello world"
    Entry("XXXXXXXX_XXXX", "ABC", "hello world", seqtype=".").format() returns "XXXXXXXX_XXXX.ABC  │ hello world"
    Entry("XXXXXXXX_XXXX", "ABC", "hello world", listOf("#foo", "!bar")).format() returns "XXXXXXXX_XXXX ABC  │ hello world #foo !bar"
    Entry("XXXXXXXX_XXXX", "ABC", "hello world", body="body").format() returns "XXXXXXXX_XXXX ABC  │ hello world\n\nbody\n"
    Entry("XXXXXXXX_XXXX", "ABC", "", listOf("#foo", "!bar")).format() returns "XXXXXXXX_XXXX ABC  │ #foo !bar"
}
fun Entry.format() = buildString {
    append(seq)
    append(seqtype.ifBlank{' '})
    append(zone)
    append(' ')
    append(rating.ifBlank{' '})
    append('│')
    if (!header.isBlank()) append(' ').append(header)
    if (!tags.isEmpty()) append(tags.joinToString(" ", " "))
    if (!body.isBlank()) append("\n\n").append(body).append("\n")
}

fun def_isJournalHeader() {
    "00000000_0000 ABC  │".isJournalHeader() returns true
    "0000XXXX_XXXX ABC  │".isJournalHeader() returns true
    "20210120_2210 ABC  │".isJournalHeader() returns true
    "20210120_2210 ABC  │ ".isJournalHeader() returns true
    "20210120_2210 ABC  │ hello world".isJournalHeader() returns true
    "20210120_2210.ABC  │ hello world".isJournalHeader() returns true
    "20210120_2210'ABC  │ hello world".isJournalHeader() returns true
    "20210120_2210 ABC *│ hello world".isJournalHeader() returns true
    "20210120_2210 ABC  │ hello world\n".isJournalHeader() returns true
    "20210120_2210 ABC  │ hello world #truth".isJournalHeader() returns true
    "0000XXXX_YYYY ABC  │".isJournalHeader() returns false
    "20210120_2210 ABC │ hello world".isJournalHeader() returns false
    "20210120_2210 ABC   hello world".isJournalHeader() returns false
    "202101202210 ABC  │ hello world".isJournalHeader() returns false
    "foo".isJournalHeader() returns false
    "".isJournalHeader() returns false
}
fun String.isJournalHeader(): Boolean = matches(headerRegex);
val headerRegex = Regex("^[0-9X_]{13}[\\p{Punct} ]... .│.*\n?$")

fun def_parseTags() {
    parseTags("").isEmpty() returns true
    parseTags("#foo") returns listOf("#foo")
    parseTags("nontag #foo") returns listOf("#foo")
    parseTags("#foo !bar") returns listOf("#foo", "!bar")
    parseTags("#foo !bar ") returns listOf("#foo", "!bar")
    parseTags(" #foo !bar") returns listOf("#foo", "!bar")
    parseTags("#foo   !bar ") returns listOf("#foo", "!bar")
    parseTags("##foo !bar ") returns listOf("##foo", "!bar")
    parseTags("#foo bar !baz") returns listOf("#foo bar", "!baz")
    parseTags("#foo '#bar !baz") returns listOf("#foo '#bar", "!baz")
    parseTags("& #foo !bar") returns listOf("&", "#foo", "!bar")
    parseTags("&  #foo !bar") returns listOf("&", "#foo", "!bar")
    parseTags("&1 #foo !bar") returns listOf("&1", "#foo", "!bar")
    parseTags("#foo & !bar") returns listOf("#foo", "&", "!bar")
    parseTags("#foo & bar") returns listOf("#foo & bar")
    parseTags("#foo & bar !baz") returns listOf("#foo & bar","!baz")
    parseTags("#foo bar !baz &") returns listOf("#foo bar", "!baz", "&")
    parseTags("#foo bar !baz & ") returns listOf("#foo bar", "!baz", "&")
    parseTags("#foo bar !baz &2") returns listOf("#foo bar", "!baz", "&2")
    parseTags("#foo bar !baz :https://wikipedia.org/foo") returns listOf("#foo bar", "!baz", ":https://wikipedia.org/foo")
    parseTags("#foo bar :https://wikipedia.org/foo !baz") returns listOf("#foo bar", ":https://wikipedia.org/foo", "!baz")
}
fun parseTags(input: String): List<String> {
    var matches = tagStartRegex.findAll(input).toList()
    return matches.mapIndexed { i, it ->
        var stop = if (i < matches.lastIndex) matches[i + 1].range.start - 1 else input.lastIndex
        input.slice(it.range.start..stop).trim()
    }
}
val tagChars = "/+#=!>@:&"
val tagStartRegex = Regex("(^| )[$tagChars](?=([^ │]| +[$tagChars]| *$))")

fun def_parseEntry() {
    parseEntry("XXXXXXXX_XXXX ABC  │") returns Entry("XXXXXXXX_XXXX", "ABC")
    parseEntry("XXXXXXXX_XXXX ABC +│") returns Entry("XXXXXXXX_XXXX", "ABC", rating="+")
    parseEntry("XXXXXXXX_XXXX.ABC  │") returns Entry("XXXXXXXX_XXXX", "ABC", seqtype=".")
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world") returns Entry("XXXXXXXX_XXXX", "ABC", "hello world")
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world ") returns Entry("XXXXXXXX_XXXX", "ABC", "hello world")
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world!") returns Entry("XXXXXXXX_XXXX", "ABC", "hello world!")
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar").header returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar").tags returns listOf("#tag", "!bar")
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world  #tag !bar").header returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag '!bar").header returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world '#tag !bar").header returns "hello world '#tag"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world '#tag' !bar").header returns "hello world '#tag'"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world &").header returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world & #tag !bar").header returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world &1 #tag !bar").header returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world & '#tag !bar").header returns "hello world & '#tag"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar &").header returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar &1").header returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello & world").header returns "hello & world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello & world #tag !bar").header returns "hello & world"
    parseEntry("XXXXXXXX_XXXX ABC  │ 🩢🩣🩤 #tag !bar").header returns "🩢🩣🩤"
}
fun parseEntry(header: String) = parseEntry(header, "")
fun parseEntry(header: String, body: String): Entry {
    val tagIndex = tagStartRegex.find(header)?.range?.start ?: header.lastIndex
    return Entry(
        seq = header.slice(0..12),
        seqtype = header.slice(13..13).trim(),
        zone = header.slice(14..16),
        rating = header.slice(18..18).trim(),
        header = header.slice(20..tagIndex).trim(),
        tags = parseTags(header.drop(tagIndex + 1)),
        body = body.trim())
}

fun def_parse() {
    parse("XXXXXXXX_XXXX ABC  │ hello world").first().header returns "hello world"
    parse("XXXXXXXX_XXXX ABC  │ hello world\nbody\n").first().body returns "body"
    parse("XXXXXXXX_XXXX ABC  │ hello world\nXXXXXXXX_XXXX ABC  │ hello world2").count() returns 2
    parse("XXXXXXXX_XXXX ABC  │ hello world ##tag !bar\n\nbody goes here\n\n").first().body returns "body goes here"
    parse("XXXXXXXX_XXXX ABC  │ hello world ##tag !bar\r\n\r\nbody goes here\r\n\r\n").first().body returns "body goes here"
    parse("XXXXXXXX_XXXX ABC  │ hello world #tag !bar\n\nbody goes here\n").first().tags returns listOf("#tag", "!bar")
    parse("XXXXXXXX_XXXX ABC  │ hello world #tag !bar\n\nbody #notag goes here\n").first().tags returns listOf("#tag", "!bar")
    parse("XXXXXXXX_XXXX ABC  │ hello world\n\nbody #notag goes here\n").first().tags.isEmpty() returns true
    parse("// vim: modeline\nXXXXXXXX_XXXX ABC  │ hello world\nbody\n").first().header returns "hello world"
}
fun parse(file: File) = parse(file.reader().buffered())
fun parse(input: String) = parse(input.reader().buffered())
fun parse(input: BufferedReader): Sequence<Entry> = generateSequence {
    var header = input.readLine()
    while (header != null && !header.isJournalHeader()) {
        header = input.readLine()
    }
    var body = ""
    var line: String?
    while (true) {
        input.mark(8192)
        line = input.readLine()
        if (line == null || line.isJournalHeader()) break
        body += linefeed + line
    }
    input.reset()
    if (header != null) parseEntry(header, body) else null
}
val linefeed = System.getProperty("line.separator")

fun def_getDateTime() {
    Entry("20000101_0000").getDateTime() returns LocalDateTime.of(2000, 1, 1, 0, 0);
    { Entry("20000101_XXXX").getDateTime() } throws DateTimeParseException::class;
    { Entry("XXXXXXXX_XXXX").getDateTime() } throws DateTimeParseException::class;
}
fun Entry.getDateTime(): LocalDateTime = LocalDateTime.parse(seq, dateTimeFormat)
val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

fun def_isExactSeq() {
    Entry("20000101_0000").isExactSeq() returns true
    Entry("20000101_XXXX").isExactSeq() returns false
    Entry("XXXXXXXX_XXXX").isExactSeq() returns false
    Entry("20000101_0000", seqtype=".").isExactSeq() returns false
}
fun Entry.isExactSeq() = seq.matches(exactDateTimeRegex) && seqtype.isEmpty()
val exactDateTimeRegex = Regex("[0-9]{8}_[0-9]{4}")

fun def_getTimeSpent() {
    Entry("XXXXXXXX_XXXX").getTimeSpent() returns null
    Entry("XXXXXXXX_XXXX", tags=listOf("+0")).getTimeSpent() returns 0
    Entry("XXXXXXXX_XXXX", tags=listOf("+word")).getTimeSpent() returns null
    Entry("XXXXXXXX_XXXX", tags=listOf("/code", "+15")).getTimeSpent() returns 15
    Entry("XXXXXXXX_XXXX", tags=listOf("/code", "+15", "+30")).getTimeSpent() returns 30
    Entry("XXXXXXXX_XXXX", tags=listOf("/code!")).getTimeSpent() returns 0
    Entry("XXXXXXXX_XXXX", tags=listOf("+15", "/code!")).getTimeSpent() returns 0
    Entry("XXXXXXXX_XXXX", tags=listOf("/code!", "+15")).getTimeSpent() returns 15
}
fun Entry.getTimeSpent(): Int? {
    val timeSpentTag = tags.filter { it.matches(timeSpentRegex) }.lastOrNull()
    if (timeSpentTag == null) return null
    if (timeSpentTag.endsWith("!")) return 0
    return timeSpentTag.substring(1).toInt()
}
val timeSpentRegex = Regex("(\\+[0-9]+|/.*!)")

fun def_getSkips() {
    Entry("XXXXXXXX_XXXX").getSkips() returns 0
    Entry("XXXXXXXX_XXXX", tags=listOf("&")).getSkips() returns 1
    Entry("XXXXXXXX_XXXX", tags=listOf("&", "#foo")).getSkips() returns 1
    Entry("XXXXXXXX_XXXX", tags=listOf("&1", "#foo")).getSkips() returns 1
    Entry("XXXXXXXX_XXXX", tags=listOf("#foo", "&")).getSkips() returns 1
    Entry("XXXXXXXX_XXXX", tags=listOf("#foo", "&2")).getSkips() returns 2
    Entry("XXXXXXXX_XXXX", tags=listOf("&2", "#foo")).getSkips() returns 2
    Entry("XXXXXXXX_XXXX", tags=listOf("&2", "#foo", "&3")).getSkips() returns 3
}
fun Entry.getSkips(): Int {
    val skipsTag = tags.filter { it.matches(skipsRegex) }.lastOrNull()
    if (skipsTag == null) return 0
    if (skipsTag.length == 1) return 1
    return skipsTag.substring(1).toInt()
}
val skipsRegex = Regex("&[0-9]*")

fun def_collectTimeSpent() {
    listOf<Entry>().collectTimeSpent() returns mapOf("" to 0)
    listOf(Entry("20000101_0000", tags=listOf("=p1"))).collectTimeSpent()[""] returns 0
    listOf(Entry("20000101_0000", tags=listOf("=p1"))).collectTimeSpent()["=p1"] returns 0
    listOf(Entry("20000101_0000", tags=listOf("=p1", "+15"))).collectTimeSpent()["=p1"] returns 15
    listOf(Entry("20000101_0000", tags=listOf("=p1", "+15"))).collectTimeSpent()[""] returns 15
    listOf(Entry("20000101_0000", tags=listOf("=p1", "+15"))).collectTimeSpent()["=p2"] returns null
    listOf(Entry("20000101_0000", tags=listOf("=p1", "+15"))).collectTimeSpent().containsKey("+15") returns false
    listOf(Entry("20000101_0000", tags=listOf("/code", "=p1")),
           Entry("20000101_0015", tags=listOf("/debug", "=p1")),
           Entry("20000101_0030", tags=listOf("/code", "+10", "=p2")))
        .collectTimeSpent()["=p1"] returns 30

    listOf(Entry("20000101_0030", tags=listOf("/code", "+10", "=p2")),
           Entry("20000101_0145", tags=listOf("/debug", "=p2")),
           Entry("20000101_0230", tags=listOf("/cook")))
        .collectTimeSpent()["=p2"] returns 55

    listOf(Entry("20000102_1030", tags=listOf("/wake", "&")),
           Entry("20000102_1045", tags=listOf("/recall")),
           Entry("20000102_1115", tags=listOf("/cook")))
        .collectTimeSpent()["/wake"] returns 45

    listOf(Entry("20000102_1030", tags=listOf("/wake", "+5", "&")),
           Entry("20000102_1045", tags=listOf("/recall")),
           Entry("20000102_1115", tags=listOf("/cook")))
        .collectTimeSpent()["/wake"] returns 5

    listOf(Entry("20000102_1030", tags=listOf("/wake", "&2")),
           Entry("20000102_1045", tags=listOf("/recall")),
           Entry("20000102_1115", tags=listOf("/stretch")),
           Entry("20000102_1145", tags=listOf("/cook")))
        .collectTimeSpent()["/wake"] returns 75

    listOf(Entry("20000102_1200", tags=listOf("/code", "=p3", "&")),
           Entry("20000102_1230", tags=listOf("/search", "=p3")),
           Entry("20000102_1300", tags=listOf("/cook")))
        .collectTimeSpent()["=p3"] returns 90

    listOf(Entry("20000102_1200", tags=listOf("/code", "=p3", "&2")),
           Entry("20000102_1230", tags=listOf("/search", "=p3")),
           Entry("20000102_1300", tags=listOf("/cook")),
           Entry("20000102_1400", tags=listOf("/trawl")))
        .collectTimeSpent()["=p3"] returns 150
}
fun List<Entry>.collectTimeSpent(filter: (Entry) -> Boolean = { true }) = asSequence().collectTimeSpent(filter)
fun Sequence<Entry>.collectTimeSpent(filter: (Entry) -> Boolean = { true }): Map<String, Int> {
    var totals = mutableMapOf("" to 0).toSortedMap()
    val i = iterator()
    val window = LinkedList<Entry>()
    if (i.hasNext()) window.add(i.next())
    while (!window.isEmpty()) {
        val current = window.remove()
        if (filter.invoke(current)) {
            var timeSpent = current.getTimeSpent()
            if (timeSpent != null) {
                totals.inc(current, timeSpent)
            } else {
                try {
                    val consume = current.getSkips() + 1
                    while (consume > window.size) { window.add(i.next()) }
                    timeSpent = current.getDateTime().until(window[consume - 1].getDateTime(), MINUTES).toInt()
                    totals.inc(current, timeSpent)
                } catch (e: NoSuchElementException) {
                    totals.inc(current, 0)
                }
            }
        }
        if (window.isEmpty() && i.hasNext()) window.add(i.next())
    }
    return totals;
}
fun MutableMap<String, Int>.inc(entry: Entry, amount: Int) {
    for (tag in entry.tags) {
        if (!tag.matches(dontIncRegex)) {
            put(tag, get(tag)?.plus(amount) ?: amount)
        }
    }
    put(entry.zone, get(entry.zone)?.plus(amount) ?: 0)
    put("", get("")?.plus(amount) ?: 0)
}
val dontIncRegex = Regex("^\\+[0-9]+")

fun def_collectTimeSpentOn() {
    listOf(Entry("20000101_0000", tags=listOf("=p1"))).collectTimeSpentOn("=p1")["=p1"] returns 0
    listOf(Entry("20000101_0000", tags=listOf("=p1", "+15"))).collectTimeSpentOn("=p1")["=p1"] returns 15
    listOf(Entry("20000101_0000", tags=listOf("=p1", "+15"))).collectTimeSpentOn("=p2")["=p1"] returns null
    listOf(Entry("20000101_0000", tags=listOf("=p1", "+15"))).collectTimeSpentOn("=p1")["=p2"] returns null
    listOf(Entry("20000101_0000", tags=listOf("/code", "=p1")),
           Entry("20000101_0015", tags=listOf("/debug", "=p1")),
           Entry("20000101_0030", tags=listOf("/code", "+10", "=p2")))
        .collectTimeSpentOn("=p1")["/code"] returns 15

    listOf(Entry("20000101_0030", tags=listOf("/code", "+10", "=p2")),
           Entry("20000101_0145", tags=listOf("/debug", "=p2.x")),
           Entry("20000101_0230", tags=listOf("/cook")))
        .collectTimeSpentOn("=p2")["=p2.x"] returns 45

    listOf(Entry("20000101_0030", tags=listOf("/code", "+10", "=p2")),
           Entry("20000101_0145", tags=listOf("/debug", "=p2x")),
           Entry("20000101_0230", tags=listOf("/cook")))
        .collectTimeSpentOn("=p2")["=p2x"] returns null
}
fun List<Entry>.collectTimeSpentOn(tag: String) = asSequence().collectTimeSpentOn(tag)
fun Sequence<Entry>.collectTimeSpentOn(tag: String) = collectTimeSpent { entry -> 
    entry.tags.find { it == tag || it.startsWith("$tag.") } != null
}
fun Sequence<Entry>.printTimeSpentInHoursOn(tag: String) {
    collectTimeSpentOn(tag).entries.forEach { 
        println(String.format("% 8.2f %s", it.value / 60.0, it.key)) 
    }
}

fun def_pairs() {
    sequenceOf<Int>().pairs().toList() returns listOf<Int>()
    sequenceOf(1).pairs().toList() returns listOf(Pair(1, null))
    sequenceOf(1, 2).pairs().toList() returns listOf(Pair(1, 2), Pair(2, null))
    sequenceOf(1, 2, 3).pairs().toList() returns listOf(Pair(1, 2), Pair(2, 3), Pair(3, null))
    sequenceOf("a", "b", "c", "d").pairs().toList() returns listOf(Pair("a", "b"), Pair("b", "c"), Pair("c", "d"), Pair("d", null))
}
fun <T> Sequence<T>.pairs(): Sequence<Pair<T, T?>> {
    val i = iterator()
    var first = i.nextOrNull()
    var second = i.nextOrNull()
    return generateSequence {
        if (first == null) null else {
            val result = Pair(first!!, second)
            first = second
            second = i.nextOrNull()
            result
        }
    }
}
fun <T> Iterator<T>.nextOrNull() = if (hasNext()) next() else null

fun def_scrubTimeSpentTags() {
    listOf(Entry("20000101_0030", tags=listOf("+10")),
           Entry("20000101_0040"))
        .scrubTimeSpentTags()[0].tags.contains("+10") returns false

    listOf(Entry("20000101_0030", tags=listOf("+10", "&")),
           Entry("20000101_0040"))
        .scrubTimeSpentTags()[0].tags.contains("+10") returns true

    listOf(Entry("20000101_0030", tags=listOf("+10")),
           Entry("20000101_0045"))
        .scrubTimeSpentTags()[0].tags.contains("+10") returns true

    listOf(Entry("XXXXXXXX_XXXX", tags=listOf("+10")),
           Entry("20000101_0010"))
        .scrubTimeSpentTags()[0].tags.contains("+10") returns true

    listOf(Entry("20000101_0030", tags=listOf("+10")),
           Entry("XXXXXXXX_XXXX"))
        .scrubTimeSpentTags()[0].tags.contains("+10") returns true
}
fun List<Entry>.scrubTimeSpentTags(): List<Entry> = asSequence().scrubTimeSpentTags().toList()
fun Sequence<Entry>.scrubTimeSpentTags(): Sequence<Entry> = pairs().map { (first, second) ->
    val timeSpent = first.getTimeSpent() ?: 0
    if (timeSpent > 0
            && second != null
            && first.isExactSeq() && second.isExactSeq()
            && first.tags.find { it.startsWith("&") } == null
            && second.getDateTime() == first.getDateTime().plusMinutes(timeSpent.toLong())) {
        first.copy(tags = first.tags.filterNot { it.matches(Regex("\\+[0-9]+")) })
    } else {
        first
    }
}

fun main() {
    parse(System.`in`.bufferedReader()).scrubTimeSpentTags().forEach { println(it.format()) }
}

fun test() {
    def_format()
    def_isJournalHeader()
    def_parseTags()
    def_parseEntry()
    def_parse()
    def_getDateTime()
    def_isExactSeq()
    def_getTimeSpent()
    def_getSkips()
    def_collectTimeSpent()
    def_collectTimeSpentOn()
    def_pairs()
    def_scrubTimeSpentTags()
}

// kotlin.test not on the default classpath, so use our own test functions
infix fun Any?.returns(result: Any?) { 
    if (this != result) throw AssertionError("Expected: $result, got $this") 
}
infix fun (() -> Any).throws(ex: kotlin.reflect.KClass<out Throwable>) { 
    try { 
        invoke() 
        throw AssertionError("Exception expected: $ex")
    } catch (e: Throwable) { 
        if (!ex.java.isAssignableFrom(e.javaClass)) throw AssertionError("Expected: $ex, got $e")
    } 
}

