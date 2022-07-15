//usr/bin/env [ $0 -nt $0.jar ] && kotlinc -d $0.jar $0; [ $0.jar -nt $0 ] && java -cp $CLASSPATH:$0.jar VimjournalKt $@; exit 0

import java.io.BufferedReader
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit.MINUTES
import java.util.LinkedList
import java.util.NoSuchElementException

fun main(args: Array<String>) {
    when (if (args.isNotEmpty()) args[0] else "") {
        "from" -> parse().filter { it.seq > args[1] }.sortedBy { it.seq }.forEach { it.print() }
        "sort-summary" -> parse().sortedBy { it.summary }.forEach { it.print() }
        "sum-durations" -> parse().sumDurationsByTagFor(args[1]).entries.forEach {
            println(String.format("% 8.2f %s", it.value / 60.0, it.key))
        }
        "test" -> test()
        else -> println("usage: vimjournal.kt [from <seq> | sort-summary | sum-durations <tag>]")
    }
}
  
data class Entry(
    val seq: String,
    val zone: String = "XXX",
    val summary: String = "",
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
    if (!summary.isBlank()) append(' ').append(summary)
    if (!tags.isEmpty()) append(tags.joinToString(" ", " "))
    if (!body.isBlank()) append("\n\n").append(body).append("\n")
}
fun Entry.print() = println(format())

fun def_isHeader() {
    "00000000_0000 ABC  │".isHeader() returns true
    "0000XXXX_XXXX ABC  │".isHeader() returns true
    "20210120_2210 ABC  │".isHeader() returns true
    "20210120_2210 ABC  │ ".isHeader() returns true
    "20210120_2210 ABC  │ hello world".isHeader() returns true
    "20210120_2210.ABC  │ hello world".isHeader() returns true
    "20210120_2210'ABC  │ hello world".isHeader() returns true
    "20210120_2210 ABC *│ hello world".isHeader() returns true
    "20210120_2210 ABC  │ hello world\n".isHeader() returns true
    "20210120_2210 ABC  │ hello world #truth".isHeader() returns true
    "0000XXXX_YYYY ABC  │".isHeader() returns false
    "20210120_2210 ABC │ hello world".isHeader() returns false
    "20210120_2210 ABC   hello world".isHeader() returns false
    "202101202210 ABC  │ hello world".isHeader() returns false
    "foo".isHeader() returns false
    "".isHeader() returns false
}
fun String.isHeader(): Boolean = matches(headerRegex);
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
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar").summary returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar").tags returns listOf("#tag", "!bar")
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world  #tag !bar").summary returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag '!bar").summary returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world '#tag !bar").summary returns "hello world '#tag"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world '#tag' !bar").summary returns "hello world '#tag'"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world &").summary returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world & #tag !bar").summary returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world &1 #tag !bar").summary returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world & '#tag !bar").summary returns "hello world & '#tag"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar &").summary returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar &1").summary returns "hello world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello & world").summary returns "hello & world"
    parseEntry("XXXXXXXX_XXXX ABC  │ hello & world #tag !bar").summary returns "hello & world"
    parseEntry("XXXXXXXX_XXXX ABC  │ 🩢🩣🩤 #tag !bar").summary returns "🩢🩣🩤"
}
fun parseEntry(header: String) = parseEntry(header, "")
fun parseEntry(header: String, body: String): Entry {
    val tagIndex = tagStartRegex.find(header)?.range?.start ?: header.lastIndex
    return Entry(
        seq = header.slice(0..12),
        seqtype = header.slice(13..13).trim(),
        zone = header.slice(14..16),
        rating = header.slice(18..18).trim(),
        summary = header.slice(20..tagIndex).trim(),
        tags = parseTags(header.drop(tagIndex + 1)),
        body = body)
}

fun def_parse() {
    parse("XXXXXXXX_XXXX ABC  │ hello world").first().summary returns "hello world"
    parse("XXXXXXXX_XXXX ABC  │ hello world\nbody\n").first().body returns "body"
    parse("XXXXXXXX_XXXX ABC  │ hello world\n\n  body\n").first().body returns "  body"
    parse("XXXXXXXX_XXXX ABC  │ hello world\n\n  body").first().body returns "  body"
    parse("XXXXXXXX_XXXX ABC  │ hello world\nXXXXXXXX_XXXX ABC  │ hello world2").count() returns 2
    parse("XXXXXXXX_XXXX ABC  │ hello world ##tag !bar\n\nbody goes here\n\n").first().body returns "body goes here"
    parse("XXXXXXXX_XXXX ABC  │ hello world ##tag !bar\r\n\r\nbody goes here\r\n\r\n").first().body returns "body goes here"
    parse("XXXXXXXX_XXXX ABC  │ hello world #tag !bar\n\nbody goes here\n").first().tags returns listOf("#tag", "!bar")
    parse("XXXXXXXX_XXXX ABC  │ hello world #tag !bar\n\nbody #notag goes here\n").first().tags returns listOf("#tag", "!bar")
    parse("XXXXXXXX_XXXX ABC  │ hello world\n\nbody #notag goes here\n").first().tags.isEmpty() returns true
    parse("// vim: modeline\nXXXXXXXX_XXXX ABC  │ hello world\nbody\n").first().summary returns "hello world"
}
fun parse(file: File) = parse(file.reader().buffered())
fun parse(input: String) = parse(input.reader().buffered())
fun parse(input: BufferedReader = System.`in`.bufferedReader()): Sequence<Entry> {
    return generateSequence {
        var header = input.readLine()
        while (header != null && !header.isHeader()) {
            header = input.readLine()
        }
        var body = ""
        var line: String?
        while (true) {
            input.mark(8192)
            line = input.readLine()
            if (line == null || line.isHeader()) break
            body += linefeed + line
        }
        input.reset()
        if (header != null) parseEntry(header, body.trim('\n')) else null
    }
}
val linefeed = System.getProperty("line.separator")

fun def_isExact() {
    Entry("20000101_0000").isExact() returns true
    Entry("20000101_XXXX").isExact() returns false
    Entry("XXXXXXXX_XXXX").isExact() returns false
    Entry("20000101_0000", seqtype=".").isExact() returns false
}
fun Entry.isExact() = seq.matches(exactDateTimeRegex) && seqtype.isEmpty()
val exactDateTimeRegex = Regex("[0-9]{8}_[0-9]{4}")

fun def_getDateTime() {
    Entry("20000101_0000").getDateTime() returns LocalDateTime.of(2000, 1, 1, 0, 0);
    { Entry("20000101_XXXX").getDateTime() } throws DateTimeParseException::class;
    { Entry("XXXXXXXX_XXXX").getDateTime() } throws DateTimeParseException::class;
}
fun Entry.getDateTime(): LocalDateTime = LocalDateTime.parse(seq, dateTimeFormat)
val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

fun def_getTaggedDuration() {
    Entry("XXXXXXXX_XXXX").getTaggedDuration() returns null
    Entry("XXXXXXXX_XXXX", tags=listOf("+0")).getTaggedDuration() returns 0
    Entry("XXXXXXXX_XXXX", tags=listOf("+word")).getTaggedDuration() returns null
    Entry("XXXXXXXX_XXXX", tags=listOf("/code", "+15")).getTaggedDuration() returns 15
    Entry("XXXXXXXX_XXXX", tags=listOf("/code", "+15", "+30")).getTaggedDuration() returns 30
    Entry("XXXXXXXX_XXXX", tags=listOf("/code!")).getTaggedDuration() returns 0
    Entry("XXXXXXXX_XXXX", tags=listOf("+15", "/code!")).getTaggedDuration() returns 0
    Entry("XXXXXXXX_XXXX", tags=listOf("/code!", "+15")).getTaggedDuration() returns 15
}
fun Entry.getTaggedDuration(): Int? {
    val durationTag = tags.filter { it.matches(durationRegex) }.lastOrNull()
    if (durationTag == null) return null
    if (durationTag.endsWith("!")) return 0
    return durationTag.substring(1).toInt()
}
val durationRegex = Regex("(\\+[0-9]+|/.*!)")

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

fun def_pairs() {
    sequenceOf<Int>().pairs().toList() returns listOf<Int>()
    sequenceOf(1).pairs().toList() returns listOf(Pair(1, null))
    sequenceOf(1, 2).pairs().toList() returns listOf(Pair(1, 2), Pair(2, null))
    sequenceOf(1, 2, 3).pairs().toList() returns listOf(Pair(1, 2), Pair(2, 3), Pair(3, null))
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

fun def_withDurations() {
    sequenceOf<Entry>().withDurations().count() returns 0
    sequenceOf(
         Entry("20000101_0000"))
        .withDurations().first().second returns 0
    sequenceOf(
         Entry("20000101_0000", tags=listOf("+15")))
        .withDurations().first().second returns 15
    sequenceOf(
         Entry("20000101_0000"),
         Entry("20000101_0015"),
         Entry("20000101_0030", tags=listOf("+10")))
        .withDurations().map { it.second }.toList() returns listOf(15, 15, 10)
    sequenceOf(
         Entry("20000102_1030", tags=listOf("&")),
         Entry("20000102_1045"),
         Entry("20000102_1115"))
        .withDurations().first().second returns 45
    sequenceOf(
         Entry("20000102_1030", tags=listOf("+5", "&")),
         Entry("20000102_1045"),
         Entry("20000102_1115"))
        .withDurations().first().second returns 5
    sequenceOf(
         Entry("20000102_1030", tags=listOf("&2")),
         Entry("20000102_1045"),
         Entry("20000102_1115"),
         Entry("20000102_1145"))
        .withDurations().first().second returns 75
}
fun Sequence<Entry>.withDurations(filter: (Entry) -> Boolean = { true }): Sequence<Pair<Entry, Int>> {
    val i = iterator()
    val window = LinkedList<Entry>()
    return generateSequence {
        while (window.isNotEmpty() || i.hasNext()) {
            val current = if (window.isNotEmpty()) window.remove() else i.next()
            if (filter.invoke(current)) {
                var duration = current.getTaggedDuration()
                if (duration != null) {
                    if (current.isExact() && current.getSkips() == 0 && i.hasNext()) {
                        val next = i.next()
                        if (next.isExact()) {
                            val stop = current.getDateTime().plusMinutes(duration.toLong())
                            val overlap = next.getDateTime().until(stop, MINUTES)
                            if (overlap > 0) System.err.println(
                                "WARNING: tagged duration overlaps next entry by $overlap minutes: ${current.format()}")
                        }
                        window.add(next)
                    }
                } else {
                    try {
                        val consume = current.getSkips() + 1
                        while (consume > window.size) { window.add(i.next()) }
                        duration = current.getDateTime().until(window[consume - 1].getDateTime(), MINUTES).toInt()
                    } catch (e: NoSuchElementException) {
                        duration = 0
                    }
                }
                return@generateSequence Pair(current, duration!!)
            }
        }
        return@generateSequence null
    }
}

fun def_sumDurations() {
    sequenceOf<Entry>().sumDurations() returns 0
    sequenceOf(
         Entry("20000101_0000"))
        .sumDurations() returns 0
    sequenceOf(
         Entry("20000101_0000", tags=listOf("+15")))
        .sumDurations() returns 15
    sequenceOf(
         Entry("20000101_0000"),
         Entry("20000101_0015"))
        .sumDurations() returns 15
    sequenceOf(
         Entry("20000101_0000"),
         Entry("20000101_0015", tags=listOf("+10")))
        .sumDurations() returns 25
    sequenceOf(
         Entry("20000101_0030", tags=listOf("+10")),
         Entry("20000101_0145"),
         Entry("20000101_0230"))
        .sumDurations() returns 55
    sequenceOf(
         Entry("20000102_1030", tags=listOf("&")),
         Entry("20000102_1045"),
         Entry("20000102_1115"))
        .sumDurations() returns 75
}
fun Sequence<Entry>.sumDurations(filter: (Entry) -> Boolean = { true }): Int {
    var total = 0
    withDurations(filter).forEach { (entry, duration) -> total += duration }
    return total
}
fun Sequence<Entry>.sumDurationsFor(tagChar: Char) = sumDurations { entry -> 
    entry.tags.find { it.startsWith(tagChar) } != null
}
fun Sequence<Entry>.sumDurationsFor(tag: String) = sumDurations { entry -> 
    entry.tags.find { it == tag || it.startsWith("$tag.") } != null
}

fun def_sumDurationsByTag() {
    sequenceOf<Entry>().sumDurationsByTag() returns mapOf<String, Int>()
    sequenceOf(
         Entry("20000101_0000", tags=listOf("=p1")))
        .sumDurationsByTag()["=p1"] returns 0
    sequenceOf(
         Entry("20000101_0000", tags=listOf("=p1", "+15")))
        .sumDurationsByTag()["=p1"] returns 15
    sequenceOf(
         Entry("20000101_0000", tags=listOf("=p1", "+15")))
        .sumDurationsByTag()["=p2"] returns null
    sequenceOf(
         Entry("20000101_0000", tags=listOf("=p1", "+15")))
        .sumDurationsByTag().containsKey("+15") returns false
    sequenceOf(
         Entry("20000101_0000", tags=listOf("/code", "=p1")),
         Entry("20000101_0015", tags=listOf("/debug", "=p1")),
         Entry("20000101_0030", tags=listOf("/code", "+10", "=p2")))
        .sumDurationsByTag()["=p1"] returns 30
    sequenceOf(
         Entry("20000101_0030", tags=listOf("/code", "+10", "=p2")),
         Entry("20000101_0145", tags=listOf("/debug", "=p2")),
         Entry("20000101_0230", tags=listOf("/cook")))
        .sumDurationsByTag()["=p2"] returns 55
    sequenceOf(
         Entry("20000102_1030", tags=listOf("/wake", "&")),
         Entry("20000102_1045", tags=listOf("/recall")),
         Entry("20000102_1115", tags=listOf("/cook")))
        .sumDurationsByTag()["/wake"] returns 45
    sequenceOf(
         Entry("20000102_1030", tags=listOf("/wake", "+5", "&")),
         Entry("20000102_1045", tags=listOf("/recall")),
         Entry("20000102_1115", tags=listOf("/cook")))
        .sumDurationsByTag()["/wake"] returns 5
    sequenceOf(
         Entry("20000102_1030", tags=listOf("/wake", "&2")),
         Entry("20000102_1045", tags=listOf("/recall")),
         Entry("20000102_1115", tags=listOf("/stretch")),
         Entry("20000102_1145", tags=listOf("/cook")))
        .sumDurationsByTag()["/wake"] returns 75
    sequenceOf(
         Entry("20000102_1200", tags=listOf("/code", "=p3", "&")),
         Entry("20000102_1230", tags=listOf("/search", "=p3")),
         Entry("20000102_1300", tags=listOf("/cook")))
        .sumDurationsByTag()["=p3"] returns 90
    sequenceOf(
         Entry("20000102_1200", tags=listOf("/code", "=p3", "&2")),
         Entry("20000102_1230", tags=listOf("/search", "=p3")),
         Entry("20000102_1300", tags=listOf("/cook")),
         Entry("20000102_1400", tags=listOf("/trawl")))
        .sumDurationsByTag()["=p3"] returns 150
}
fun Sequence<Entry>.sumDurationsByTag(filter: (Entry) -> Boolean = { true }): Map<String, Int> {
    var totals = mutableMapOf<String, Int>().toSortedMap()
    withDurations(filter).forEach { (entry, duration) ->
        for (tag in entry.tags) {
            if (!tag.matches(excludeTagRegex)) {
                totals.put(tag, totals.get(tag)?.plus(duration) ?: duration)
            }
        }
    }
    return totals;
}
val excludeTagRegex = Regex("^\\+[0-9]+")

fun def_sumDurationsByTagFor() {
    sequenceOf(
         Entry("20000101_0000", tags=listOf("=p1")))
        .sumDurationsByTagFor("=p1")["=p1"] returns 0
    sequenceOf(
         Entry("20000101_0000", tags=listOf("=p1", "+15")))
        .sumDurationsByTagFor("=p1")["=p1"] returns 15
    sequenceOf(
         Entry("20000101_0000", tags=listOf("=p1", "+15")))
        .sumDurationsByTagFor("=p2")["=p1"] returns null
    sequenceOf(
         Entry("20000101_0000", tags=listOf("=p1", "+15")))
        .sumDurationsByTagFor("=p1")["=p2"] returns null
    sequenceOf(
         Entry("20000101_0000", tags=listOf("/code", "=p1")),
         Entry("20000101_0015", tags=listOf("/debug", "=p1")),
         Entry("20000101_0030", tags=listOf("/code", "+10", "=p2")))
        .sumDurationsByTagFor("=p1")["/code"] returns 15
    sequenceOf(
         Entry("20000101_0030", tags=listOf("/code", "+10", "=p2")),
         Entry("20000101_0145", tags=listOf("/debug", "=p2.x")),
         Entry("20000101_0230", tags=listOf("/cook")))
        .sumDurationsByTagFor("=p2")["=p2.x"] returns 45
    sequenceOf(
         Entry("20000101_0030", tags=listOf("/code", "+10", "=p2")),
         Entry("20000101_0145", tags=listOf("/debug", "=p2x")),
         Entry("20000101_0230", tags=listOf("/cook")))
        .sumDurationsByTagFor("=p2")["=p2x"] returns null
    sequenceOf(
         Entry("20000101_0030", tags=listOf("/code", "+10", "=p1")),
         Entry("20000101_0145", tags=listOf("/code", "=p2")),
         Entry("20000101_0230", tags=listOf("/cook")))
        .sumDurationsByTagFor('=')["/code"] returns 55
}
fun Sequence<Entry>.sumDurationsByTagFor(tagChar: Char) = sumDurationsByTag { entry -> 
    entry.tags.find { it.startsWith(tagChar) } != null
}
fun Sequence<Entry>.sumDurationsByTagFor(tag: String) = sumDurationsByTag { entry -> 
    entry.tags.find { it == tag || it.startsWith("$tag.") } != null
}

fun def_stripDurationTags() {
    sequenceOf(
         Entry("20000101_0030", tags=listOf("+10")),
         Entry("20000101_0040"))
        .stripDurationTags().first().tags.contains("+10") returns false
    sequenceOf(
         Entry("20000101_0030", tags=listOf("+10", "&")),
         Entry("20000101_0040"))
        .stripDurationTags().first().tags.contains("+10") returns true
    sequenceOf(
         Entry("20000101_0030", tags=listOf("+10")),
         Entry("20000101_0045"))
        .stripDurationTags().first().tags.contains("+10") returns true
    sequenceOf(
         Entry("XXXXXXXX_XXXX", tags=listOf("+10")),
         Entry("20000101_0010"))
        .stripDurationTags().first().tags.contains("+10") returns true
    sequenceOf(
         Entry("20000101_0030", tags=listOf("+10")),
         Entry("XXXXXXXX_XXXX"))
        .stripDurationTags().first().tags.contains("+10") returns true
}
fun Sequence<Entry>.stripDurationTags(): Sequence<Entry> = pairs().map { (first, second) ->
    val duration = first.getTaggedDuration() ?: 0
    if (duration > 0
            && second != null
            && first.isExact() && second.isExact()
            && first.tags.find { it.startsWith("&") } == null
            && second.getDateTime() == first.getDateTime().plusMinutes(duration.toLong())) {
        first.copy(tags = first.tags.filterNot { it.matches(Regex("\\+[0-9]+")) })
    } else {
        first
    }
}

fun test() {
    def_format()
    def_isHeader()
    def_parseTags()
    def_parseEntry()
    def_parse()
    def_isExact()
    def_getDateTime()
    def_getTaggedDuration()
    def_getSkips()
    def_pairs()
    def_withDurations()
    def_sumDurations()
    def_sumDurationsByTag()
    def_sumDurationsByTagFor()
    def_stripDurationTags()
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

