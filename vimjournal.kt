//usr/bin/env [ $0 -nt $0.jar ] && kotlinc -d $0.jar $0; [ $0.jar -nt $0 ] && kotlin -cp $0.jar VimjournalKt "$@"; exit 0

import java.io.BufferedReader
import java.io.File
import java.lang.System.err
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit.MINUTES
import java.util.LinkedList
import java.util.LinkedHashMap
import java.util.NoSuchElementException

fun main(args: Array<String>) {
    val usage = LinkedHashMap<String, String>()
    val c = if (args.isNotEmpty()) args[0] else "help"

    usage.put("add-stops", "add missing stop times from sequential records")
    if (c == "add-stops") {
        var prev = Record(ZERO_SEQ, tags=listOf("+0"))
        parse().filter { it.isForegroundAction() }.forEach {
            if (!prev.isExact() || prev.getTaggedDuration() != null) {
                println(prev.formatHeader())
            } else if (prev.isExact() && it.isExact()) {
                println("${prev.formatHeader()} +${it.seq.drop(9)}")
            } else {
                throw AssertionError("Next record not exact: ${prev.formatHeader()}")
            }
            prev = it
        }
        println(prev.formatHeader())
    }

    usage.put("convert-durations", "convert durations to stop time")
    if (c == "convert-durations") parse().forEach { it.convertDurationToStopTime().print() }

    usage.put("diff-durations file1 file2", "compare files record by record, outputting records where the duration differs")
    if (c == "diff-durations") File(args[1]).parse().zip(File(args[2]).parse()).forEach {
         if (Math.abs(it.first.getCalculatedDuration() - it.second.getCalculatedDuration()) > 0) {
            println(it.first.formatHeader())
            println(it.second.formatHeader())
            println()
        }
    }

    usage.put("diff-times file1 file2", "compare files record by record, outputting records where the start or stop times differ")
    if (c == "diff-times") File(args[1]).parse().zip(File(args[2]).parse()).forEach {
        if (it.first.getStartTime() != it.second.getStartTime() ||
                it.first.getStopTime() != it.second.getStopTime()) {
            println(it.first.formatHeader())
            println(it.second.formatHeader())
            println()
        }
    }

    usage.put("diff-times-1 file1 file2", "compare files record by record, outputting records where the start or stop times differ by more than one minute")
    if (c == "diff-times-1") File(args[1]).parse().zip(File(args[2]).parse()).forEach {
        if (minutesBetween(it.first.getStartTime(), it.second.getStartTime()) > 1 ||
                minutesBetween(it.first.getStopTime(), it.second.getStopTime()) > 1) {
            println(it.first.formatHeader())
            println(it.second.formatHeader())
            println()
        }
    }

    usage.put("format", "output records with standard formatting (does not sort tags)")
    if (c == "format") parse().forEach { it.print() }

    usage.put("format-naked", "output records in lowercase with no rating or tags")
    if (c == "format-naked") parse().forEach { println("${it.seq} !| ${it.summary.lowercase()}") }

    usage.put("format-timelog", "output records in lowercase with stop times and no tags")
    if (c == "format-timelog") parse().forEach { println("${it.seq} !| ${it.formatStop().drop(1)} ${it.summary.lowercase()}") }

    usage.put("format-tsv", "output record headers in tab separated format")
    if (c == "format-tsv") parse().forEach { println(it.formatHeaderAsTSV()) }

    usage.put("filter-from seq", "output records after `seq`, inclusive (presumes input is presorted)")
    if (c == "filter-from") parse().dropWhile { it.exactSeq < args[1] }.forEach { it.print() }

    usage.put("filter-indented", "output records whose summary begins with a space character")
    if (c == "filter-indented") parse().filter { it.isIndented() }.forEach { it.print() }

    usage.put("filter-outdented", "output records whose summary does not begin with a space character")
    if (c == "filter-outdented") parse().filter { !it.isIndented() }.forEach { it.print() }

    usage.put("filter-rating regex", "output records whose rating matches `regex`")
    if (c == "filter-rating") parse().filter { it.rating.contains(Regex(args[1])) }.forEach { it.print() }

    usage.put("filter-summary regex", "output records whose summary matches `regex`")
    if (c == "filter-summary") parse().filter { it.summary.contains(Regex(args[1])) }.forEach { it.print() }

    usage.put("filter-tagged regex", "output records with a tag matching `regex`")
    if (c == "filter-tagged") parse().filter { record ->
        record.tags.filter { tag -> tag.contains(Regex(args[1])) }.size > 0
    }.forEach { it.print() }

    usage.put("filter-to seq", "output records before `seq`, inclusive (presumes input is presorted)")
    if (c == "filter-to") parse().takeWhile { it.exactSeq <= args[1] }.forEach { it.print() }

    usage.put("find-anachronisms", "find records which are out of order")
    if (c == "find-anachronisms") {
        var prevSeq = ZERO_SEQ
        parse().forEach {
            if (it.exactSeq < prevSeq) println(it.formatHeader())
            prevSeq = it.exactSeq
        }
    }

    usage.put("find-missing-stops", "output records which are missing a stop tag because the next record seq is approximate")
    if (c == "find-missing-stops") {
        var prev = Record(ZERO_SEQ)
        parse().filter { !it.isIndented() }.forEach {
            if (prev.isExact() && prev.getTaggedDuration() == null && !it.isExact()) println(prev.formatHeader())
            prev = it
        }
    }

    usage.put("find-overlaps", "output records whose stop time overlaps the next records start time")
    if (c == "find-overlaps") {
        var prev = Record(ZERO_SEQ)
        parse().filter { it.isForegroundAction() }.forEach {
            if (it.isExact() && it.getStartTime() < prev.getStopTime()) println(prev.formatHeader())
            prev = it
        }
    }

    usage.put("make-flashcards", "export records as png flashcards for nokia phones (requires ImageMagick, writes files to current dir)")
    if (c == "make-flashcards") parse().forEachIndexed { i, it -> it.makeFlashcard(i) }

    usage.put("make-text-flashcards", "export records as text flashcards for nokia phones (writes files to current dir)")
    if (c == "make-text-flashcards") parse().forEachIndexed { i, it -> it.makeTextFlashcards(i) }

    usage.put("patch-times file patch_file", "update start and stop times in `file` to match records in `patch_file`, where they differ by one minute or less")
    if (c == "patch-times") {
        val patches = File(args[2]).parse().iterator()
        var patch = patches.next()
        File(args[1]).parse().forEach() {
            if (it.isExact() && minutesBetween(it.getStartTime(), patch.getStartTime()) <= 1) {
                it.replaceStop(patch.formatStop()).copy(seq = patch.seq).print()
                if (patches.hasNext()) patch = patches.next()
            } else {
                it.print()
            }
        }
    }

    usage.put("show-durations", "append the duration of each record")
    if (c == "show-durations") parse().withDurations().forEach {
        println("${it.format()} +${it.duration}")
    }

    usage.put("sort", "sort records by seq: approximate seqs are stable")
    if (c == "sort") parse().sortedBy { it.exactSeq }.forEach { it.print() }

    usage.put("sort-rough", "sort records by first eight characters of the record seq (day)")
    if (c == "sort-rough") parse().sortedBy { it.seq.take(8) }.forEach { it.print() }

    usage.put("sort-by-summary", "sort records by summary")
    if (c == "sort-by-summary") parse().sortedBy { it.summary }.forEach { it.print() }

    usage.put("sort-by-rating", "sort records by rating")
    if (c == "sort-by-rating") parse().sortedBy { it.priority }.forEach { it.print() }

    usage.put("sort-tags", "sort record tags in the following order: ${sortTagsOrder}")
    if (c == "sort-tags") parse().forEach { it.sortTags().print() }

    usage.put("strip-durations", "remove durations where the gap until the next record is less than ${MIN_GAP_MINUTES} minutes")
    if (c == "strip-durations") parse().stripDurationTags().forEach { it.print() }

    usage.put("strip-stops", "remove stop tags where there is no gap between records")
    if (c == "strip-stops") parse().stripStopTags().forEach { it.print() }

    usage.put("sum", "sum the duration in minutes of all records (stop times must be appended first)")
    if (c == "sum") println(parse().withDurations().sumOf { it.duration })

    usage.put("sum-durations-by-tag tag", "sum the duration in minutes of records matching `tag`, grouped by tag")
    if (c == "sum-durations-by-tag") parse().sumDurationsByTagFor(args[1]).entries.forEach {
        println(String.format("% 8.2f %s", it.value / 60.0, it.key))
    }

    usage.put("test", "run unit tests and output any failures")
    if (c == "test") test()

    usage.put("help", "print usage")
    if (c == "help" || usage.keys.find { it.startsWith(c) } == null) {
        err.println("\nUsage: vimjournal.kt [command] [parameters]\n")
        err.println("Unless specified, all commands read from stdin and write to stdout.\n")
        usage.forEach { err.println(String.format("  %-30s %s", it.key, it.value)) }
        err.println()
    }
}

data class Record(
        val seq: String,
        val summary: String = "",
        val rating: String = ">",
        val tags: List<String> = listOf(),
        val body: String = "",
        val exactSeq: String = makeExactSeq(seq),
        val duration: Int = 0) {

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
val ZERO_SEQ = "00010101_0000"

fun Record_format_spec() {
    Record("XXXXXXXX_XXXX", "hello world", body="body").format() returns "XXXXXXXX_XXXX >| hello world\n\nbody\n"
}
fun Record.format() = buildString {
    append(formatHeader())
    if (!body.isBlank()) append("\n\n").append(body).append('\n')
}
fun Record.print() = println(format())

fun Record_formatHeader_spec() {
    Record("XXXXXXXX_XXXX", "").formatHeader() returns "XXXXXXXX_XXXX >|"
    Record("XXXXXXXX_XXXX", "hello world").formatHeader() returns "XXXXXXXX_XXXX >| hello world"
    Record("XXXXXXXX_XXXX", "hello world", rating="+").formatHeader() returns "XXXXXXXX_XXXX +| hello world"
    Record("XXXXXXXX_XXXX", "hello world", tags=listOf("#foo", "!bar")).formatHeader() returns "XXXXXXXX_XXXX >| hello world #foo !bar"
    Record("XXXXXXXX_XXXX", tags=listOf("#foo", "!bar")).formatHeader() returns "XXXXXXXX_XXXX >| #foo !bar"
}
fun Record.formatHeader() = buildString {
    append(seq).append(' ')
    append(rating).append('|')
    if (!summary.isBlank()) append(' ').append(summary)
    if (!tags.isEmpty()) append(tags.joinToString(" ", " "))
}
fun Record.formatHeaderAsTSV() = buildString {
    append(seq).append('\t')
    append(rating).append('\t')
    if (!summary.isBlank()) append('\t').append(summary)
    if (!tags.isEmpty()) append(tags.joinToString("\t", "\t"))
}

fun String_isHeader_spec() {
    "00000000_0000 >|".isHeader() returns true
    "0000XXXX_XXXX >|".isHeader() returns true
    "0000XXXX_YYYY >|".isHeader() returns false
    "20210120_2210 >|".isHeader() returns true
    "20210120_2210 *|".isHeader() returns true
    "20210120_2210 >| ".isHeader() returns true
    "20210120_2210 >| hello world".isHeader() returns true
    "20210120_2210 >| hello world\n".isHeader() returns true
    "20210120_2210 >| hello world #truth".isHeader() returns true
    "20210120_2210!>| hello world".isHeader() returns false
    "20210120_2210>| hello world".isHeader() returns false
    "20210120_2210   hello world".isHeader() returns false
    "202101202210  >| hello world".isHeader() returns false
    "foo".isHeader() returns false
    "".isHeader() returns false
}
fun String.isHeader(): Boolean = matches(headerRegex);
val ratingChars = "->x=~+*.!"
val headerRegex = Regex("^[0-9X_]{13} [$ratingChars]\\|.*\n?$")

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
}
fun String.parseRecord(body: String = ""): Record {
    val tagIndex = tagStartRegex.find(this)?.range?.start ?: lastIndex
    val seq = slice(0..12)
    val record = Record(
        seq = seq,
        summary = slice(17..tagIndex).trimEnd(),
        rating = slice(14..14).trim(),
        tags = drop(tagIndex + 1).parseTags(),
        body = body,
        exactSeq = if (seq.compareSeq(lastSeq) == 0) lastSeq else makeExactSeq(seq)
    )
    lastSeq = record.exactSeq // or record.getStopTime()?
    return record
}
var lastSeq = ZERO_SEQ

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

fun Record_getTime_spec() {
    Record("20000101_0000").getTime() returns LocalDateTime.of(2000, 1, 1, 0, 0)
    Record("20000101_XXXX").getTime() returns LocalDateTime.of(2000, 1, 1, 0, 0)
    Record("XXXXXXXX_XXXX").getTime() returns LocalDateTime.of(1, 1, 1, 0, 0)
}
fun Record.getTime(): LocalDateTime = LocalDateTime.parse(exactSeq, dateTimeFormat)
val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

// inexact dates are treated as starting at midnight so that stop times can be expressed as a duration
fun Record.getStartTime(): LocalDateTime {
    val time = getTime()
    return if (isExact()) time else LocalDateTime.of(time.getYear(), time.getMonth(), time.getDayOfMonth(), 0, 0, 0)
}

fun Record_getStopTime_spec() {
    Record("19990101_0000").getStopTime() returns LocalDateTime.of(1999, 1, 1, 0, 0)
    Record("19990101_XXXX").getStopTime() returns LocalDateTime.of(1999, 1, 1, 0, 0)
    Record("19990101_0100", tags=listOf("+15")).getStopTime() returns LocalDateTime.of(1999, 1, 1, 1, 15)
    Record("19990101_XXXX", tags=listOf("+15")).getStopTime() returns LocalDateTime.of(1999, 1, 1, 0, 15)
    Record("19990101_0100", tags=listOf("+1000")).getStopTime() returns LocalDateTime.of(1999, 1, 1, 10, 0)
    Record("19990101_XXXX", tags=listOf("+1000")).getStopTime() returns LocalDateTime.of(1999, 1, 1, 10, 0)
    Record("19990101_2330", tags=listOf("+1000")).getStopTime() returns LocalDateTime.of(1999, 1, 2, 10, 0)
}
fun Record.getStopTime(): LocalDateTime {
    val startTime = getStartTime()
    val durationTag = tags.filter { it.matches(durationRegex) }.lastOrNull()
    return if (durationTag == null) {
        startTime
    } else if (durationTag.length == 5) {
        val stopTime = LocalTime.parse(durationTag, stopTimeFormat)
        val plusDays = if (startTime.toLocalTime().isAfter(stopTime)) 1L else 0L
        LocalDateTime.of(startTime.toLocalDate(), stopTime).plusDays(plusDays)
    } else {
        startTime.plusMinutes(durationTag.substring(1).toLong())
    }
}
val durationRegex = Regex("(\\+[0-9]+)")

fun Record.getCalculatedDuration(): Long = getStartTime().until(getStopTime(), MINUTES)

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
    val durationTag = tags.filter { it.matches(durationOrInstantRegex) }.lastOrNull()
    if (durationTag == null) return null
    if (durationTag.endsWith("!")) return 0
    return durationTag.substring(1).toInt()
}
val durationOrInstantRegex = Regex("(\\+[0-9]+|/.*!)")

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
        .withDurations().first().duration returns 0
    sequenceOf(
         Record("20000101_0000", tags=listOf("+15")))
        .withDurations().first().duration returns 15
    sequenceOf(
         Record("20000101_0000"),
         Record("20000101_0015"),
         Record("20000101_0030", tags=listOf("+10")))
        .withDurations().map { it.duration }.toList() returns listOf(15, 15, 10)
    sequenceOf(
         Record("20000102_1030", tags=listOf("&")),
         Record("20000102_1045"),
         Record("20000102_1115"))
        .withDurations().first().duration returns 45
    sequenceOf(
         Record("20000102_1030", tags=listOf("+5", "&")),
         Record("20000102_1045"),
         Record("20000102_1115"))
        .withDurations().first().duration returns 5
    sequenceOf(
         Record("20000102_1030", tags=listOf("&2")),
         Record("20000102_1045"),
         Record("20000102_1115"),
         Record("20000102_1145"))
        .withDurations().first().duration returns 75
}
fun Sequence<Record>.withDurations(): Sequence<Record> {
    val i = iterator()
    val window = LinkedList<Record>()
    return generateSequence {
        while (window.isNotEmpty() || i.hasNext()) {
            val current = if (window.isNotEmpty()) window.remove() else i.next()
            var duration = current.getTaggedDuration()
            if (duration != null) {
                if (current.isExact() && current.getSkips() == 0 && i.hasNext()) {
                    val next = i.next()
                    if (next.isExact()) {
                        val stop = current.getTime().plusMinutes(duration.toLong())
                        val overlap = next.getTime().until(stop, MINUTES)
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
                        duration = current.getTime().until(
                            window[consume - 1].getTime(), MINUTES).toInt()
                    } catch (e: DateTimeParseException) {
                        System.err.println("WARNING: ${e.message}")
                    }
                } catch (e: NoSuchElementException) {}
            }
            return@generateSequence current.copy(duration = duration!!)
        }
        return@generateSequence null
    }
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
    withDurations().filter(filter).forEach {
        for (tag in it.tags) {
            if (!tag.matches(excludeTagRegex)) {
                totals.put(tag, totals.get(tag)?.plus(it.duration) ?: it.duration)
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
            && Math.abs(MINUTES.between(first.getTime().plusMinutes(duration.toLong()),
                                        second.getTime())) < MIN_GAP_MINUTES) {
        first.copy(tags = first.tags.filterNot { it.matches(Regex("\\+[0-9]+")) })
    } else {
        first
    }
}
val MIN_GAP_MINUTES = 2

fun Sequence<Record>.stripStopTags(): Sequence<Record> = pairs().map { (first, second) ->
    if (second != null && first.getStopTime() == second.getStartTime()) {
        first.copy(tags = first.tags.filterNot { it.matches(STOP_REGEX) })
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

fun Record_convertDurationToStopTime_spec() {
    Record("20250103_1600").convertDurationToStopTime() returns Record("20250103_1600")
    Record("20250103_1600", tags=listOf("@brisbane")).convertDurationToStopTime() returns
        Record("20250103_1600", tags=listOf("@brisbane"))
    Record("20250103_1600", tags=listOf("+15")).convertDurationToStopTime() returns
        Record("20250103_1600", tags=listOf("+1615"))
    Record("20250103_2300", tags=listOf("+75")).convertDurationToStopTime() returns
        Record("20250103_2300", tags=listOf("+0015"))
    Record("20250103_1600", tags=listOf("+1615")).convertDurationToStopTime() returns
        Record("20250103_1600", tags=listOf("+1615"))
    Record("20250103_XXXX", tags=listOf("+15")).convertDurationToStopTime() returns
        Record("20250103_XXXX", tags=listOf("+15"))
}
fun Record.convertDurationToStopTime(): Record {
    val durationTag = tags.filter { it.matches(convertDurationRegex) }.lastOrNull()
    if (durationTag == null || !this.isExact()) return this
    return this.copy(tags = tags.map {
        if (it.matches(convertDurationRegex)) {
            this.getTime().plusMinutes(durationTag.substring(1).toLong())
                .format(stopTimeFormat)
        } else {
            it
        }
    })
}
val convertDurationRegex = Regex("(\\+[0-9][0-9]?[0-9]?)") // three digits or less
val stopTimeFormat = DateTimeFormatter.ofPattern("+HHmm")

fun String_compareSeq_spec() {
    "19990102_1000".compareSeq("19990102_0999") returns 1
    "19990102_1000".compareSeq("19990102_1000") returns 0
    "19990102_1000".compareSeq("19990102_1001") returns -1
    "19990102_1000".compareSeq("19990101_XXXX") returns 1
    "19990102_1000".compareSeq("19990102_XXXX") returns 0
    "19990102_1000".compareSeq("19990103_XXXX") returns -1
    "19990102_XXXX".compareSeq("19990102_XXXX") returns 0
    "19990102_XXXX".compareSeq("19990102_XXX1") returns 0
    "19990102_XXXX".compareSeq("19990102_1000") returns 0
    "199701XX_XXXX".compareSeq("1997XXXX_XXXX") returns 0
}
fun String.compareSeq(other: String) : Int {
    val i = this.iterator()
    val j = other.iterator()
    while (i.hasNext()) {
        val a = i.next()
        val b = j.next()
        if (a.isLetter() || b.isLetter()) return 0
        if (a != b) return a.compareTo(b)
    }
    return 0
}

fun makeExactSeq_spec() {
    makeExactSeq("1999XXXX_XXXX") returns "19990101_0000"
    makeExactSeq("1999XXX9_XX99") returns "19990109_0099"
}
fun makeExactSeq(s: String) : String {
    return s.mapIndexed { i, c -> if (c.isLetter()) ZERO_SEQ[i] else c }.joinToString("")
}

fun Record.isIndented(): Boolean = summary.startsWith(" ")
fun Record.isBackgroundAction(): Boolean = summary.startsWith("& ") || summary.startsWith("and ")
fun Record.isForegroundAction(): Boolean = !isIndented() && !isBackgroundAction()

fun Record_isInstant_spec() {
    Record("20000101_0000").isInstant() returns false
    Record("20000101_0000", tags=listOf("/run")).isInstant() returns false
    Record("20000101_0000", tags=listOf("/run", "+45")).isInstant() returns false
    Record("20000101_0000", tags=listOf("/fail!")).isInstant() returns true
    Record("20000101_0000", tags=listOf("@office", "/fail!")).isInstant() returns true
}
fun Record.isInstant(): Boolean {
    tags.forEach { if (it.matches(instantRegex)) return true }
    return false
}
val instantRegex = Regex("/.*!")

fun minutesBetween(a: LocalDateTime, b: LocalDateTime): Long = Math.abs(MINUTES.between(a, b))

fun Record.removeStop(): Record = copy(tags = tags.filterNot { it.matches(STOP_REGEX) })
fun Record.replaceStop(stop: String): Record = copy(tags = tags.map { if (it.matches(STOP_REGEX)) stop else it })
val STOP_REGEX = Regex("(\\+[0-9]{4})")

fun Record.formatStop(): String = stopTimeFormat.format(getStopTime())

// simple test functions, since kotlin.test is not on the default classpath
fun test() = ::main.javaClass.enclosingClass.declaredMethods.filter { it.name.endsWith("_spec") }.forEach { it(null) }
infix fun Any?.returns(result: Any?) { if (this != result) throw AssertionError("Expected: $result, got $this") }
infix fun (() -> Any).throws(ex: kotlin.reflect.KClass<out Throwable>) {
    try {
        invoke()
        throw AssertionError("Exception expected: $ex")
    } catch (e: Throwable) {
        if (!ex.java.isAssignableFrom(e.javaClass)) throw AssertionError("Expected: $ex, got $e")
    }
}

