//usr/bin/env [ $0 -nt $0.jar ] && kotlinc -d $0.jar $0; [ $0.jar -nt $0 ] && kotlin -cp $0.jar VimjournalKt "$@"; exit 0

import java.io.BufferedReader
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit.MINUTES
import java.util.LinkedList
import java.util.NoSuchElementException

val usage = """

usage: vimjournal.kt [command] [parameters]

commands:
  format
  filter-from <seq> 
  filter-rating <string>
  filter-summary <string>
  filter-tagged <string>
  make-flashcards
  make-text-flashcards
  show-durations 
  sort
  sort-rough
  sort-by-summary 
  sort-by-rating
  sort-tags
  strip-durations
  sum-durations
  sum-durations-by-tag <tag>
  test

"""

fun main(args: Array<String>) {
    when (if (args.isNotEmpty()) args[0] else "") {
        "format" -> parse().forEach { it.print() }
        "filter-from" -> parse().filter { it.seq > args[1] }.sortedBy { it.seq }.forEach { it.print() }
        "filter-rating" -> parse().filter { it.rating.contains(Regex(args[1])) }.forEach { it.print() }
        "filter-summary" -> parse().filter { it.summary.contains(Regex(args[1])) }.forEach { it.print() }
        "filter-tagged" -> parse().filter { record ->
            record.tags.filter { tag -> tag.contains(Regex(args[1])) }.size > 0
        }.forEach { it.print() }
        "make-flashcards" -> parse().forEachIndexed { i, it -> it.makeFlashcard(i) }
        "make-text-flashcards" -> parse().forEachIndexed { i, it -> it.makeTextFlashcards(i) }
        "show-durations" -> parse().withDurations().forEach {
            println("${it.first.format()} +${it.second}") 
        }
        "sort" -> parse().sortedBy { it.seq }.forEach { it.print() }
        "sort-rough" -> parse().sortedBy { it.seq.take(8) }.forEach { it.print() }
        "sort-by-summary" -> parse().sortedBy { it.summary }.forEach { it.print() }
        "sort-by-rating" -> parse().sortedBy { it.priority }.forEach { it.print() }
        "sort-tags" -> parse().forEach { it.sortTags().print() }
        "strip-durations" -> parse().stripDurationTags().forEach { it.print() }
        "sum-durations" -> println(parse().sumDurations())
        "sum-durations-by-tag" -> parse().sumDurationsByTagFor(args[1]).entries.forEach {
            println(String.format("% 8.2f %s", it.value / 60.0, it.key))
        }
        "test" -> test()
        else -> println(usage)
    }
}

data class Record(
    val seq: String,
    val summary: String = "",
    val rating: String = ">",
    val tags: List<String> = listOf(),
    val body: String = "",
    val marker: Char = ' ') {

    val priority = when (rating) {
        "*" -> 1
        "+" -> 2
        "=" -> 3
        "~" -> 3
        "-" -> 4
        "x" -> 5
        "." -> 5
        else -> 6
    }
}

fun Record_format_spec() {
    Record("XXXXXXXX_XXXX", "").format() returns "XXXXXXXX_XXXX >|"
    Record("XXXXXXXX_XXXX", "hello world").format() returns "XXXXXXXX_XXXX >| hello world"
    Record("XXXXXXXX_XXXX", "hello world", rating="+").format() returns "XXXXXXXX_XXXX +| hello world"
    Record("XXXXXXXX_XXXX", "hello world", tags=listOf("#foo", "!bar")).format() returns "XXXXXXXX_XXXX >| hello world #foo !bar"
    Record("XXXXXXXX_XXXX", "hello world", body="body").format() returns "XXXXXXXX_XXXX >| hello world\n\nbody\n"
    Record("XXXXXXXX_XXXX", tags=listOf("#foo", "!bar")).format() returns "XXXXXXXX_XXXX >| #foo !bar"
    Record("XXXXXXXX_XXXX", "hello world", marker='!').format() returns "XXXXXXXX_XXXX!>| hello world"
    Record("XXXXXXXX_XXXX", " hello world", marker='!').format() returns "XXXXXXXX_XXXX!>|  hello world"
}
fun Record.format() = buildString {
    append(seq).append(marker)
    append(rating).append('|')
    if (!summary.isBlank()) append(' ').append(summary)
    if (!tags.isEmpty()) append(tags.joinToString(" ", " "))
    if (!body.isBlank()) append("\n\n").append(body).append('\n')
}
fun Record.print() = println(format())

// sort only on numeric values, so we can mix exact and approximate date/times
fun Record_compareTo_spec() {
    Record("19990101_0000").compareTo(Record("19990101_0000")) returns 0
    Record("19990101_0000").compareTo(Record("19990101_XXXX")) returns 0
    Record("19990101_1200").compareTo(Record("19990101_XXXX")) returns 0
    Record("19990101_XXXX").compareTo(Record("19990101_0000")) returns 0
    Record("19990101_AAAA").compareTo(Record("19990101_0000")) returns 0
    Record("19990101_XXXX").compareTo(Record("19990101_1200")) returns 0
    Record("19990101_XXXX").compareTo(Record("19990101_XXXX")) returns 0
    Record("19990101_0000").compareTo(Record("19990101_0001")) returns -1
    Record("19990101_0000").compareTo(Record("19990102_0000")) returns -1
    Record("19990102_0000").compareTo(Record("19990101_0000")) returns 1
    Record("19990101_0001").compareTo(Record("19990101_0000")) returns 1
}
fun Record.compareTo(other: Record): Int {
    val stop = minOf(seqStopRegex.find(this.seq)?.range?.start ?: this.seq.length,
                     seqStopRegex.find(other.seq)?.range?.start ?: this.seq.length)
    return this.seq.take(stop).compareTo(other.seq.take(stop))
}
val seqStopRegex = Regex("[A-Za-z]")

fun String_isHeader_spec() {
    "00000000_0000 >|".isHeader() returns true
    "0000XXXX_XXXX >|".isHeader() returns true
    "0000XXXX_YYYY >|".isHeader() returns true
    "20210120_2210 >|".isHeader() returns true
    "20210120_2210 *|".isHeader() returns true
    "20210120_2210 >| ".isHeader() returns true
    "20210120_2210 >| hello world".isHeader() returns true
    "20210120_2210 >| hello world\n".isHeader() returns true
    "20210120_2210 >| hello world #truth".isHeader() returns true
    "20210120_2210>| hello world".isHeader() returns false
    "20210120_2210   hello world".isHeader() returns false
    "202101202210  >| hello world".isHeader() returns false
    "foo".isHeader() returns false
    "".isHeader() returns false
}
fun String.isHeader(): Boolean = matches(headerRegex);
val ratingChars = "->x=~+*."
val headerRegex = Regex("^[0-9A-Z_]{13} [$ratingChars]\\|.*\n?$")

fun String_parseTags_spec() {
    "".parseTags().isEmpty() returns true
    "#foo".parseTags() returns listOf("#foo")
    "nontag #foo".parseTags() returns listOf("#foo")
    "#foo !bar".parseTags() returns listOf("#foo", "!bar")
    "#foo !bar ".parseTags() returns listOf("#foo", "!bar")
    " #foo !bar".parseTags() returns listOf("#foo", "!bar")
    "#foo   !bar ".parseTags() returns listOf("#foo", "!bar")
    "##foo !bar ".parseTags() returns listOf("##foo", "!bar")
    "#foo bar !baz".parseTags() returns listOf("#foo bar", "!baz")
    "#foo '#bar !baz".parseTags() returns listOf("#foo '#bar", "!baz")
    "& #foo !bar".parseTags() returns listOf("&", "#foo", "!bar")
    "&  #foo !bar".parseTags() returns listOf("&", "#foo", "!bar")
    "&1 #foo !bar".parseTags() returns listOf("&1", "#foo", "!bar")
    "#foo & !bar".parseTags() returns listOf("#foo", "&", "!bar")
    "#foo & bar".parseTags() returns listOf("#foo & bar")
    "#foo & bar !baz".parseTags() returns listOf("#foo & bar","!baz")
    "#foo bar !baz &".parseTags() returns listOf("#foo bar", "!baz", "&")
    "#foo bar !baz & ".parseTags() returns listOf("#foo bar", "!baz", "&")
    "#foo bar !baz &2".parseTags() returns listOf("#foo bar", "!baz", "&2")
    "#foo bar !baz :https://wikipedia.org/foo".parseTags() returns listOf("#foo bar", "!baz", ":https://wikipedia.org/foo")
    "#foo bar :https://wikipedia.org/foo !baz".parseTags() returns listOf("#foo bar", ":https://wikipedia.org/foo", "!baz")
}
fun String.parseTags(): List<String> {
    var matches = tagStartRegex.findAll(this).toList()
    return matches.mapIndexed { i, it ->
        var stop = if (i < matches.lastIndex) matches[i + 1].range.start - 1 else lastIndex
        slice(it.range.start..stop).trim()
    }
}
val tagChars = "/+#=!>@:&"
val tagStartRegex = Regex("(^| )[$tagChars](?=([^ |>]| +[$tagChars]| *$))")

fun String_parseRecord_spec() {
    "XXXXXXXX_XXXX >|".parseRecord() returns Record("XXXXXXXX_XXXX")
    "XXXXXXXX_XXXX +|".parseRecord() returns Record("XXXXXXXX_XXXX", rating="+")
    "XXXXXXXX_XXXX >| hello world".parseRecord() returns Record("XXXXXXXX_XXXX", "hello world")
    "XXXXXXXX_XXXX >| hello world ".parseRecord() returns Record("XXXXXXXX_XXXX", "hello world")
    "XXXXXXXX_XXXX >| hello world!".parseRecord() returns Record("XXXXXXXX_XXXX", "hello world!")
    "XXXXXXXX_XXXX >|  hello world".parseRecord() returns Record("XXXXXXXX_XXXX", " hello world")
    "XXXXXXXX_XXXX >| hello world #tag !bar".parseRecord().summary returns "hello world"
    "XXXXXXXX_XXXX >| hello world #tag !bar".parseRecord().tags returns listOf("#tag", "!bar")
    "XXXXXXXX_XXXX >| hello world  #tag !bar".parseRecord().summary returns "hello world"
    "XXXXXXXX_XXXX >| hello world #tag '!bar".parseRecord().summary returns "hello world"
    "XXXXXXXX_XXXX >| hello world '#tag !bar".parseRecord().summary returns "hello world '#tag"
    "XXXXXXXX_XXXX >| hello world '#tag' !bar".parseRecord().summary returns "hello world '#tag'"
    "XXXXXXXX_XXXX >| hello world &".parseRecord().summary returns "hello world"
    "XXXXXXXX_XXXX >| hello world & #tag !bar".parseRecord().summary returns "hello world"
    "XXXXXXXX_XXXX >| hello world &1 #tag !bar".parseRecord().summary returns "hello world"
    "XXXXXXXX_XXXX >| hello world & '#tag !bar".parseRecord().summary returns "hello world & '#tag"
    "XXXXXXXX_XXXX >| hello world #tag !bar &".parseRecord().summary returns "hello world"
    "XXXXXXXX_XXXX >| hello world #tag !bar &1".parseRecord().summary returns "hello world"
    "XXXXXXXX_XXXX >| hello & world".parseRecord().summary returns "hello & world"
    "XXXXXXXX_XXXX >| hello & world #tag !bar".parseRecord().summary returns "hello & world"
    "XXXXXXXX_XXXX >| 🩢🩣🩤 #tag !bar".parseRecord().summary returns "🩢🩣🩤"
    "XXXXXXXX_XXXX!>| hello world".parseRecord() returns Record("XXXXXXXX_XXXX", "hello world", marker='!')
    "XXXXXXXX_XXXX!>|  hello world".parseRecord() returns Record("XXXXXXXX_XXXX", " hello world", marker='!')
}
fun String.parseRecord() = parseRecord("")
fun String.parseRecord(body: String): Record {
    val tagIndex = tagStartRegex.find(this)?.range?.start ?: lastIndex
    return Record(
        seq = slice(0..12),
        marker = get(13),
        summary = slice(17..tagIndex).trimEnd(),
        rating = slice(14..14).trim(),
        tags = drop(tagIndex + 1).parseTags(),
        body = body
    )
}

fun String_parse_spec() {
    "XXXXXXXX_XXXX >| hello world".parse().first().summary returns "hello world"
    "XXXXXXXX_XXXX >| hello world\nbody\n".parse().first().body returns "body"
    "XXXXXXXX_XXXX >| hello world\n\n  body\n".parse().first().body returns "  body"
    "XXXXXXXX_XXXX >| hello world\n\n  body".parse().first().body returns "  body"
    "XXXXXXXX_XXXX >| hello world\nXXXXXXXX_XXXX >| hello world 2".parse().count() returns 2
    "XXXXXXXX_XXXX >| hello world ##tag !bar\n\nbody goes here\n\n".parse().first().body returns "body goes here"
    "XXXXXXXX_XXXX >| hello world ##tag !bar\r\n\r\nbody goes here\r\n\r\n".parse().first().body returns "body goes here"
    "XXXXXXXX_XXXX >| hello world #tag !bar\n\nbody goes here\n".parse().first().tags returns listOf("#tag", "!bar")
    "XXXXXXXX_XXXX >| hello world #tag !bar\n\nbody #notag goes here\n".parse().first().tags returns listOf("#tag", "!bar")
    "XXXXXXXX_XXXX >| hello world\n\nbody #notag goes here\n".parse().first().tags.isEmpty() returns true
    "// vim: modeline\nXXXXXXXX_XXXX >| hello world\nbody\n".parse().first().summary returns "hello world"
}
fun File.parse() = parse(reader().buffered())
fun String.parse() = parse(reader().buffered())
fun parse(input: BufferedReader = System.`in`.bufferedReader()): Sequence<Record> {
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
        if (header != null) header.parseRecord(body.trim('\n')) else null
    }
}
val linefeed = System.getProperty("line.separator")

fun Record_isExact_spec() {
    Record("20000101_0000").isExact() returns true
    Record("20000101_XXXX").isExact() returns false
    Record("XXXXXXXX_XXXX").isExact() returns false
}
fun Record.isExact() = seq.matches(exactDateTimeRegex)
val exactDateTimeRegex = Regex("[0-9]{8}_[0-9]{4}")

fun Record_getDateTime_spec() {
    Record("20000101_0000").getDateTime() returns LocalDateTime.of(2000, 1, 1, 0, 0);
    { Record("20000101_XXXX").getDateTime() } throws DateTimeParseException::class;
    { Record("XXXXXXXX_XXXX").getDateTime() } throws DateTimeParseException::class;
}
fun Record.getDateTime(): LocalDateTime = LocalDateTime.parse(seq, dateTimeFormat)
val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

fun Record_getTaggedDuration_spec() {
    Record("XXXXXXXX_XXXX").getTaggedDuration() returns null
    Record("XXXXXXXX_XXXX", tags=listOf("+0")).getTaggedDuration() returns 0
    Record("XXXXXXXX_XXXX", tags=listOf("+word")).getTaggedDuration() returns null
    Record("XXXXXXXX_XXXX", tags=listOf("/code", "+15")).getTaggedDuration() returns 15
    Record("XXXXXXXX_XXXX", tags=listOf("/code", "+15", "+30")).getTaggedDuration() returns 30
    Record("XXXXXXXX_XXXX", tags=listOf("/code!")).getTaggedDuration() returns 0
    Record("XXXXXXXX_XXXX", tags=listOf("+15", "/code!")).getTaggedDuration() returns 0
    Record("XXXXXXXX_XXXX", tags=listOf("/code!", "+15")).getTaggedDuration() returns 15
}
fun Record.getTaggedDuration(): Int? {
    val durationTag = tags.filter { it.matches(durationRegex) }.lastOrNull()
    if (durationTag == null) return null
    if (durationTag.endsWith("!")) return 0
    return durationTag.substring(1).toInt()
}
val durationRegex = Regex("(\\+[0-9]+|/.*!)")

fun Record_getSkips_spec() {
    Record("XXXXXXXX_XXXX").getSkips() returns 0
    Record("XXXXXXXX_XXXX", tags=listOf("&")).getSkips() returns 1
    Record("XXXXXXXX_XXXX", tags=listOf("&", "#foo")).getSkips() returns 1
    Record("XXXXXXXX_XXXX", tags=listOf("&1", "#foo")).getSkips() returns 1
    Record("XXXXXXXX_XXXX", tags=listOf("#foo", "&")).getSkips() returns 1
    Record("XXXXXXXX_XXXX", tags=listOf("#foo", "&2")).getSkips() returns 2
    Record("XXXXXXXX_XXXX", tags=listOf("&2", "#foo")).getSkips() returns 2
    Record("XXXXXXXX_XXXX", tags=listOf("&2", "#foo", "&3")).getSkips() returns 3
}
fun Record.getSkips(): Int {
    val skipsTag = tags.filter { it.matches(skipsRegex) }.lastOrNull()
    if (skipsTag == null) return 0
    if (skipsTag.length == 1) return 1
    return skipsTag.substring(1).toInt()
}
val skipsRegex = Regex("&[0-9]*")

fun Sequence_pairs_spec() {
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

fun Sequence_withDurations_spec() {
    sequenceOf<Record>().withDurations().count() returns 0
    sequenceOf(
         Record("20000101_0000"))
        .withDurations().first().second returns 0
    sequenceOf(
         Record("20000101_0000", tags=listOf("+15")))
        .withDurations().first().second returns 15
    sequenceOf(
         Record("20000101_0000"),
         Record("20000101_0015"),
         Record("20000101_0030", tags=listOf("+10")))
        .withDurations().map { it.second }.toList() returns listOf(15, 15, 10)
    sequenceOf(
         Record("20000102_1030", tags=listOf("&")),
         Record("20000102_1045"),
         Record("20000102_1115"))
        .withDurations().first().second returns 45
    sequenceOf(
         Record("20000102_1030", tags=listOf("+5", "&")),
         Record("20000102_1045"),
         Record("20000102_1115"))
        .withDurations().first().second returns 5
    sequenceOf(
         Record("20000102_1030", tags=listOf("&2")),
         Record("20000102_1045"),
         Record("20000102_1115"),
         Record("20000102_1145"))
        .withDurations().first().second returns 75
}
fun Sequence<Record>.withDurations(filter: (Record) -> Boolean = { true }): Sequence<Pair<Record, Int>> {
    val i = iterator()
    val window = LinkedList<Record>()
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
                                "WARNING: tagged duration overlaps next record by $overlap minutes:\n  ${current.format()}\n  ${next.format()}\n")
                        }
                        window.add(next)
                    }
                } else {
                    duration = 0
                    try {
                        val consume = current.getSkips() + 1
                        while (consume > window.size) { window.add(i.next()) }
                        try {
                            duration = current.getDateTime().until(
                                window[consume - 1].getDateTime(), MINUTES).toInt()
                        } catch (e: DateTimeParseException) {
                            System.err.println("WARNING: ${e.message}")
                        }
                    } catch (e: NoSuchElementException) {}
                }
                return@generateSequence Pair(current, duration!!)
            }
        }
        return@generateSequence null
    }
}

fun Sequence_sumDurations_spec() {
    sequenceOf<Record>().sumDurations() returns 0
    sequenceOf(
         Record("20000101_0000"))
        .sumDurations() returns 0
    sequenceOf(
         Record("20000101_0000", tags=listOf("+15")))
        .sumDurations() returns 15
    sequenceOf(
         Record("20000101_0000"),
         Record("20000101_0015"))
        .sumDurations() returns 15
    sequenceOf(
         Record("20000101_0000"),
         Record("20000101_0015", tags=listOf("+10")))
        .sumDurations() returns 25
    sequenceOf(
         Record("20000101_0030", tags=listOf("+10")),
         Record("20000101_0145"),
         Record("20000101_0230"))
        .sumDurations() returns 55
    sequenceOf(
         Record("20000102_1030", tags=listOf("&")),
         Record("20000102_1045"),
         Record("20000102_1115"))
        .sumDurations() returns 75
}
fun Sequence<Record>.sumDurations(filter: (Record) -> Boolean = { true }): Int {
    var total = 0
    withDurations(filter).forEach { (_, duration) -> total += duration }
    return total
}
fun Sequence<Record>.sumDurationsFor(tagChar: Char) = sumDurations { record -> 
    record.tags.find { it.startsWith(tagChar) } != null
}
fun Sequence<Record>.sumDurationsFor(tag: String) = sumDurations { record -> 
    record.tags.find { it == tag || it.startsWith("$tag.") } != null
}

fun Sequence_sumDurationsByTag_spec() {
    sequenceOf<Record>().sumDurationsByTag() returns mapOf<String, Int>()
    sequenceOf(
         Record("20000101_0000", tags=listOf("=p1")))
        .sumDurationsByTag()["=p1"] returns 0
    sequenceOf(
         Record("20000101_0000", tags=listOf("=p1", "+15")))
        .sumDurationsByTag()["=p1"] returns 15
    sequenceOf(
         Record("20000101_0000", tags=listOf("=p1", "+15")))
        .sumDurationsByTag()["=p2"] returns null
    sequenceOf(
         Record("20000101_0000", tags=listOf("=p1", "+15")))
        .sumDurationsByTag().containsKey("+15") returns false
    sequenceOf(
         Record("20000101_0000", tags=listOf("/code", "=p1")),
         Record("20000101_0015", tags=listOf("/debug", "=p1")),
         Record("20000101_0030", tags=listOf("/code", "+10", "=p2")))
        .sumDurationsByTag()["=p1"] returns 30
    sequenceOf(
         Record("20000101_0030", tags=listOf("/code", "+10", "=p2")),
         Record("20000101_0145", tags=listOf("/debug", "=p2")),
         Record("20000101_0230", tags=listOf("/cook")))
        .sumDurationsByTag()["=p2"] returns 55
    sequenceOf(
         Record("20000102_1030", tags=listOf("/wake", "&")),
         Record("20000102_1045", tags=listOf("/recall")),
         Record("20000102_1115", tags=listOf("/cook")))
        .sumDurationsByTag()["/wake"] returns 45
    sequenceOf(
         Record("20000102_1030", tags=listOf("/wake", "+5", "&")),
         Record("20000102_1045", tags=listOf("/recall")),
         Record("20000102_1115", tags=listOf("/cook")))
        .sumDurationsByTag()["/wake"] returns 5
    sequenceOf(
         Record("20000102_1030", tags=listOf("/wake", "&2")),
         Record("20000102_1045", tags=listOf("/recall")),
         Record("20000102_1115", tags=listOf("/stretch")),
         Record("20000102_1145", tags=listOf("/cook")))
        .sumDurationsByTag()["/wake"] returns 75
    sequenceOf(
         Record("20000102_1200", tags=listOf("/code", "=p3", "&")),
         Record("20000102_1230", tags=listOf("/search", "=p3")),
         Record("20000102_1300", tags=listOf("/cook")))
        .sumDurationsByTag()["=p3"] returns 90
    sequenceOf(
         Record("20000102_1200", tags=listOf("/code", "=p3", "&2")),
         Record("20000102_1230", tags=listOf("/search", "=p3")),
         Record("20000102_1300", tags=listOf("/cook")),
         Record("20000102_1400", tags=listOf("/trawl")))
        .sumDurationsByTag()["=p3"] returns 150
}
fun Sequence<Record>.sumDurationsByTag(filter: (Record) -> Boolean = { true }): Map<String, Int> {
    var totals = mutableMapOf<String, Int>().toSortedMap()
    withDurations(filter).forEach { (record, duration) ->
        for (tag in record.tags) {
            if (!tag.matches(excludeTagRegex)) {
                totals.put(tag, totals.get(tag)?.plus(duration) ?: duration)
            }
        }
    }
    return totals;
}
val excludeTagRegex = Regex("^\\+[0-9]+")

fun Sequence_sumDurationsByTagFor_spec() {
    sequenceOf(
         Record("20000101_0000", tags=listOf("=p1")))
        .sumDurationsByTagFor("=p1")["=p1"] returns 0
    sequenceOf(
         Record("20000101_0000", tags=listOf("=p1", "+15")))
        .sumDurationsByTagFor("=p1")["=p1"] returns 15
    sequenceOf(
         Record("20000101_0000", tags=listOf("=p1", "+15")))
        .sumDurationsByTagFor("=p2")["=p1"] returns null
    sequenceOf(
         Record("20000101_0000", tags=listOf("=p1", "+15")))
        .sumDurationsByTagFor("=p1")["=p2"] returns null
    sequenceOf(
         Record("20000101_0000", tags=listOf("/code", "=p1")),
         Record("20000101_0015", tags=listOf("/debug", "=p1")),
         Record("20000101_0030", tags=listOf("/code", "+10", "=p2")))
        .sumDurationsByTagFor("=p1")["/code"] returns 15
    sequenceOf(
         Record("20000101_0030", tags=listOf("/code", "+10", "=p2")),
         Record("20000101_0145", tags=listOf("/debug", "=p2.x")),
         Record("20000101_0230", tags=listOf("/cook")))
        .sumDurationsByTagFor("=p2")["=p2.x"] returns 45
    sequenceOf(
         Record("20000101_0030", tags=listOf("/code", "+10", "=p2")),
         Record("20000101_0145", tags=listOf("/debug", "=p2x")),
         Record("20000101_0230", tags=listOf("/cook")))
        .sumDurationsByTagFor("=p2")["=p2x"] returns null
    sequenceOf(
         Record("20000101_0030", tags=listOf("/code", "+10", "=p1")),
         Record("20000101_0145", tags=listOf("/code", "=p2")),
         Record("20000101_0230", tags=listOf("/cook")))
        .sumDurationsByTagFor('=')["/code"] returns 55
}
fun Sequence<Record>.sumDurationsByTagFor(tagChar: Char) = sumDurationsByTag { record -> 
    record.tags.find { it.startsWith(tagChar) } != null
}
fun Sequence<Record>.sumDurationsByTagFor(tag: String) = sumDurationsByTag { record -> 
    record.tags.find { it == tag || it.startsWith("$tag.") } != null
}

fun Sequence_stripDurationTags_spec() {
    sequenceOf(
         Record("20000101_0030", tags=listOf("+10")),
         Record("20000101_0040"))
        .stripDurationTags().first().tags.contains("+10") returns false
    sequenceOf(
         Record("20000101_0030", tags=listOf("+10")),
         Record("20000101_0041"))
        .stripDurationTags().first().tags.contains("+10") returns false
    sequenceOf(
         Record("20000101_0030", tags=listOf("+10")),
         Record("20000101_0039"))
        .stripDurationTags().first().tags.contains("+10") returns false
    sequenceOf(
         Record("20000101_0030", tags=listOf("+10", "&")),
         Record("20000101_0040"))
        .stripDurationTags().first().tags.contains("+10") returns true
    sequenceOf(
         Record("20000101_0030", tags=listOf("+10")),
         Record("20000101_0045"))
        .stripDurationTags().first().tags.contains("+10") returns true
    sequenceOf(
         Record("XXXXXXXX_XXXX", tags=listOf("+10")),
         Record("20000101_0010"))
        .stripDurationTags().first().tags.contains("+10") returns true
    sequenceOf(
         Record("20000101_0030", tags=listOf("+10")),
         Record("XXXXXXXX_XXXX"))
        .stripDurationTags().first().tags.contains("+10") returns true
}
fun Sequence<Record>.stripDurationTags(): Sequence<Record> = pairs().map { (first, second) ->
    val duration = first.getTaggedDuration() ?: 0
    if (duration > 0
            && second != null
            && first.isExact() && second.isExact()
            && first.tags.find { it.startsWith("&") } == null
            && Math.abs(MINUTES.between(first.getDateTime().plusMinutes(duration.toLong()),
                                        second.getDateTime())) < 2) {
        first.copy(tags = first.tags.filterNot { it.matches(Regex("\\+[0-9]+")) })
    } else {
        first
    }
}

fun String_wrap_spec() {
    "12345".wrap(1) returns "1\n2\n3\n4\n5"
    "12345".wrap(2) returns "12\n34\n5"
    "12345".wrap(5) returns "12345"
    "12345".wrap(6) returns "12345"
    "1\n2345".wrap(2) returns "1\n23\n45"
    "12\n345".wrap(2) returns "12\n34\n5"
    "12\n345".wrap(3) returns "12\n345"
}
fun String.wrap(width: Int): String {
    var i = 0
    return asSequence().fold(StringBuilder()) { acc, ch -> 
        if (i > 0 && i % width == 0 && ch != '\n') acc.append("\n")
        if (ch == '\n') i = 0 else i++
        acc.append(ch)
    }.toString()
}

// export a record as an image flashcard suitable for nokia phones
fun Record.makeFlashcard(i: Int) {
    Runtime.getRuntime().exec(arrayOf(
        "convert", "-size", "240x320", "xc:black",
        "-font", "FreeMono-Bold", "-pointsize", "24",
        "-fill", "white", "-annotate", "+12+24", ("$seq\n$summary " + tags.joinToString(" ")).wrap(15),
        "-fill", "yellow", "-annotate", "+12+185", body.wrap(15),
        "%04d.X00.png".format(i)))
}

// export a record as a text flashcard suitable for nokia phones
fun Record.makeTextFlashcards(i: Int) {
    File("%04d.txt".format(i)).writeText(seq + "\n" + summary + " " + tags.joinToString(" "))
    File("%04d.00.txt".format(i)).writeText(seq + "\n" + body)
}

fun Record_sortTags_spec() {
    Record("XXXXXXXX_XXXX").sortTags() returns Record("XXXXXXXX_XXXX")
    Record("XXXXXXXX_XXXX", tags=listOf("@7")).sortTags() returns 
        Record("XXXXXXXX_XXXX", tags=listOf("@7"))
    Record("XXXXXXXX_XXXX", tags=listOf("@7", "/1")).sortTags() returns 
        Record("XXXXXXXX_XXXX", tags=listOf("/1", "@7"))
    Record("XXXXXXXX_XXXX", tags=listOf("@2", "@1")).sortTags() returns 
        Record("XXXXXXXX_XXXX", tags=listOf("@2", "@1"))
}
fun Record.sortTags(): Record {
    return this.copy(tags = tags.sortedWith { a, b -> 
        sortTagsOrder.indexOf(a[0]) - sortTagsOrder.indexOf(b[0])
    })
}
val sortTagsOrder = "/+#!=>@:&"

// simple test functions, since kotlin.test is not on the default classpath
fun test(klass: Class<*> = ::test.javaClass.enclosingClass, suffix: String = "_spec") {
    klass.declaredMethods.filter { it.name.endsWith(suffix) }.forEach { it(null) }
}
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

