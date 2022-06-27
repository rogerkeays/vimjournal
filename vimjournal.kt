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
    val zone: String,
    val header: String,
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
val headerRegex = Regex("^[0-9X_]{13}[.! ]... .│.*\n?$")

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
    parseEntry("XXXXXXXX_XXXX ABC  │") returns Entry("XXXXXXXX_XXXX", "ABC", "")
    parseEntry("XXXXXXXX_XXXX ABC +│") returns Entry("XXXXXXXX_XXXX", "ABC", "", rating="+")
    parseEntry("XXXXXXXX_XXXX.ABC  │") returns Entry("XXXXXXXX_XXXX", "ABC", "", seqtype=".")
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world") returns Entry("XXXXXXXX_XXXX", "ABC", "hello world")
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world ") returns Entry("XXXXXXXX_XXXX", "ABC", "hello world")
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
        tags = parseTags(header.drop(tagIndex)),
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
    parseEntry("20000101_0000 ABC  │ ").getDateTime() returns LocalDateTime.of(2000, 1, 1, 0, 0);
    { parseEntry("XXXXXXXX_XXXX ABC  │ ").getDateTime() } throws DateTimeParseException::class;
    { parseEntry("20000101_XXXX ABC  │ ").getDateTime() } throws DateTimeParseException::class;
}
fun Entry.getDateTime(): LocalDateTime = LocalDateTime.parse(seq, dateTimeFormat)
val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

fun def_getTimeSpent() {
    parseEntry("XXXXXXXX_XXXX ABC  │ ").getTimeSpent() returns null
    parseEntry("XXXXXXXX_XXXX ABC  │ +0").getTimeSpent() returns 0
    parseEntry("XXXXXXXX_XXXX ABC  │ /code +15").getTimeSpent() returns 15
    parseEntry("XXXXXXXX_XXXX ABC  │ /code +15 +30").getTimeSpent() returns 30
    parseEntry("XXXXXXXX_XXXX ABC  │ /code!").getTimeSpent() returns 0
    parseEntry("XXXXXXXX_XXXX ABC  │ +15 /code!").getTimeSpent() returns 0
    parseEntry("XXXXXXXX_XXXX ABC  │ /code! +15").getTimeSpent() returns 15
}
fun Entry.getTimeSpent(): Int? {
    val timeSpentTag = tags.filter { it.matches(timeSpentRegex) }.lastOrNull()
    if (timeSpentTag == null) return null
    if (timeSpentTag.endsWith("!")) return 0
    return timeSpentTag.substring(1).toInt()
}
val timeSpentRegex = Regex("(\\+[0-9]+|/.*!)")

fun def_getSkips() {
    parseEntry("XXXXXXXX_XXXX ABC  │ ").getSkips() returns 0
    parseEntry("XXXXXXXX_XXXX ABC  │ &").getSkips() returns 1
    parseEntry("XXXXXXXX_XXXX ABC  │ & ").getSkips() returns 1
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world").getSkips() returns 0
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world &").getSkips() returns 1
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world & #foo").getSkips() returns 1
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world &1 #foo").getSkips() returns 1
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world #foo &").getSkips() returns 1
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world #foo &2").getSkips() returns 2
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world &2 #foo").getSkips() returns 2
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world &2 #foo &3").getSkips() returns 3
}
fun Entry.getSkips(): Int {
    val skipsTag = tags.filter { it.matches(skipsRegex) }.lastOrNull()
    if (skipsTag == null) return 0
    if (skipsTag.length == 1) return 1
    return skipsTag.substring(1).toInt()
}
val skipsRegex = Regex("&[0-9]*")

fun def_collectTimeSpent() {
    parse("").collectTimeSpent() returns mapOf("" to 0)
    parse("20000101_0000 ABC  │ =p1").collectTimeSpent()[""] returns 0
    parse("20000101_0000 ABC  │ =p1").collectTimeSpent()["=p1"] returns 0
    parse("20000101_0000 ABC  │ =p1 +15").collectTimeSpent()["=p1"] returns 15
    parse("20000101_0000 ABC  │ =p1 +15").collectTimeSpent()[""] returns 15
    parse("20000101_0000 ABC  │ =p1 +15").collectTimeSpent()["=p2"] returns null
    parse("20000101_0000 ABC  │ =p1 +15").collectTimeSpent().containsKey("+15") returns false
    parse("""
        20000101_0000 ABC  │ write code /code =p1
        20000101_0015 ABC  │ debug code /debug =p1
        20000101_0030 ABC  │ switch projects /code +10 =p2""".trimIndent())
        .collectTimeSpent()["=p1"] returns 30

    parse("""
        20000101_0030 ABC  │ switch projects /code +10 =p2
        20000101_0145 ABC  │ debug new project /debug =p2
        20000101_0230 ABC  │ make a mango shake /cook""".trimIndent())
        .collectTimeSpent()["=p2"] returns 55

    parse("""
        20000102_1030 ABC  │ get up /wake &
        20000102_1045 ABC  │ recall my dreams /recall
        20000102_1115 ABC  │ make pancakes /cook""".trimIndent())
        .collectTimeSpent()["/wake"] returns 45

    parse("""
        20000102_1030 ABC  │ get up /wake +5 &
        20000102_1045 ABC  │ recall my dreams /recall
        20000102_1115 ABC  │ make pancakes /cook""".trimIndent())
        .collectTimeSpent()["/wake"] returns 5

    parse("""
        20000102_1030 ABC  │ get up /wake &2
        20000102_1045 ABC  │ recall my dreams /recall
        20000102_1115 ABC  │ stretch /stretch
        20000102_1145 ABC  │ make pancakes /cook""".trimIndent())
        .collectTimeSpent()["/wake"] returns 75

    parse("""
        20000102_1200 ABC  │ start coding /code =p3 &
        20000102_1230 ABC  │ research kotlin /search =p3
        20000102_1300 ABC  │ make a sandwich /cook""".trimIndent())
        .collectTimeSpent()["=p3"] returns 90

    parse("""
        20000102_1200 ABC  │ start coding /code =p3 &2
        20000102_1230 ABC  │ research kotlin /search =p3
        20000102_1300 ABC  │ make a sandwich /cook
        20000102_1400 ABC  │ surf the internet /trawl""".trimIndent())
        .collectTimeSpent()["=p3"] returns 150
}
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
    parse("20000101_0000 ABC  │ =p1").collectTimeSpentOn("=p1")["=p1"] returns 0
    parse("20000101_0000 ABC  │ =p1 +15").collectTimeSpentOn("=p1")["=p1"] returns 15
    parse("20000101_0000 ABC  │ =p1 +15").collectTimeSpentOn("=p2")["=p1"] returns null
    parse("20000101_0000 ABC  │ =p1 +15").collectTimeSpentOn("=p1")["=p2"] returns null
    parse("""
        20000101_0000 ABC  │ write code /code =p1
        20000101_0015 ABC  │ debug code /debug =p1
        20000101_0030 ABC  │ switch projects /code +10 =p2""".trimIndent())
        .collectTimeSpentOn("=p1")["/code"] returns 15

    parse("""
        20000101_0030 ABC  │ switch projects /code +10 =p2
        20000101_0145 ABC  │ debug new project /debug =p2.x
        20000101_0230 ABC  │ make a mango shake /cook""".trimIndent())
        .collectTimeSpentOn("=p2")["=p2.x"] returns 45

    parse("""
        20000101_0030 ABC  │ switch projects /code +10 =p2
        20000101_0145 ABC  │ debug new project /debug =p2x
        20000101_0230 ABC  │ make a mango shake /cook""".trimIndent())
        .collectTimeSpentOn("=p2")["=p2x"] returns null
}
fun Sequence<Entry>.collectTimeSpentOn(tag: String) = collectTimeSpent { entry -> 
    entry.tags.find { it == tag || it.startsWith("$tag.") } != null
}

fun Sequence<Entry>.printTimeSpentInHoursOn(tag: String) {
    collectTimeSpentOn(tag).entries.sortedBy { it.value }.forEach { 
        println(String.format("% 8.2f %s", it.value / 60.0, it.key)) 
    }
}

fun main() {
    parse(System.`in`.bufferedReader())
      .filter { it.seq > "20220625" }
      .sortedBy { it.seq }
      .forEach { println(it.format()) }
}

fun test() {
    def_format()
    def_isJournalHeader()
    def_parseTags()
    def_parseEntry()
    def_parse()
    def_getDateTime()
    def_getTimeSpent()
    def_getSkips()
    def_collectTimeSpent()
    def_collectTimeSpentOn()
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

