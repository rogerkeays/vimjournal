//usr/bin/env [ $0 -nt $0.jar ] && kotlinc -d $0.jar $0; [ $0.jar -nt $0 ] && java -cp $CLASSPATH:$0.jar VimjournalKt $@; exit 0

import java.io.BufferedReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit.MINUTES
  
data class Entry(
    val seq: String,
    val seqtype: String,
    val zone: String,
    val rating: String,
    val header: String,
    val tags: List<String>,
    val body: String
)

fun Entry.print() {
    print(seq)
    print(if (seqtype.isBlank()) " " else seqtype)
    print(zone)
    print(" ")
    print(if (rating.isBlank()) " " else rating)
    print("│ ")
    print(header)
    println(tags.joinToString(" ", prefix=if (header.isEmpty()) "" else " "))
    if (! body.isBlank()) {
        println()
        println(body)
        println()
    }
}

fun def_isJournalHeader() {
    test { "XXXXXXXX_XXXX ABC  │".isJournalHeader() }
    test { "00000000_0000 ABC  │".isJournalHeader() }
    test { "20210120_2210 KEP  │".isJournalHeader() }
    test { "20210120_2210 KEP  │ ".isJournalHeader() }
    test { "20210120_2210 KEP  │ hello world".isJournalHeader() }
    test { "20210120_2210 KEP *│ hello world".isJournalHeader() }
    test { "20210120_2210 KEP  │ hello world #truth".isJournalHeader() }
    test { "20210120_2210.KEP  │ hello world".isJournalHeader() }
    test { "20210120_2210 KEP  │ hello world\n".isJournalHeader() }
    test { ! "XXXXXXXX_XXXY XXX  │".isJournalHeader() }
    test { ! "202101202210 KEP  │ hello world".isJournalHeader() }
    test { ! "20210120_2210 KEP │ hello world".isJournalHeader() }
    test { ! "20210120_2210 KEP   hello world".isJournalHeader() }
    test { ! "foo".isJournalHeader() }
    test { ! "".isJournalHeader() }
}
val headerRegex = Regex("^[0-9X_]{13}[.! ]... .│.*\n?$")
fun String.isJournalHeader(): Boolean = matches(headerRegex);

fun def_parseTags() {
    test { parseTags("").isEmpty() }
    test { parseTags("#foo") == listOf("#foo") }
    test { parseTags("#foo !bar") == listOf("#foo", "!bar") }
    test { parseTags(" #foo !bar") == listOf("#foo", "!bar") }
    test { parseTags("#foo !bar ") == listOf("#foo", "!bar") }
    test { parseTags("#foo   !bar ") == listOf("#foo", "!bar") }
    test { parseTags("#foo bar !baz") == listOf("#foo bar", "!baz") }
    test { parseTags("#foo ##bar !baz") == listOf("#foo ##bar", "!baz") }
    test { parseTags("&1 #foo bar !baz") == listOf("&1", "#foo bar", "!baz") }
    test { parseTags("#foo bar !baz &") == listOf("#foo bar", "!baz", "&") }
    test { parseTags("#foo bar !baz & ") == listOf("#foo bar", "!baz", "&") }
    test { parseTags("#foo bar !baz &2") == listOf("#foo bar", "!baz", "&2") }
    test { parseTags("#foo bar !baz :https://wikipedia.org/foo") == listOf("#foo bar", "!baz", ":https://wikipedia.org/foo") }
}
val tagChars = "/+#=!>@:&"
val tagStartRegex = Regex("(^| )[$tagChars]([^$tagChars │]|\\s*$)")
fun parseTags(input: String): List<String> {
    var matches = tagStartRegex.findAll(input).toList()
    return matches.mapIndexed { i, it ->
        var stop = if (i < matches.lastIndex) matches[i + 1].range.start - 1 else input.lastIndex
        input.slice(it.range.start..stop).trim()
    }
}

fun def_parseEntry() {
    test { parseEntry("XXXXXXXX_XXXX ABC  │") == Entry("XXXXXXXX_XXXX", "", "ABC", "", "", listOf(), "") }
    test { parseEntry("XXXXXXXX_XXXX.ABC  │").seqtype == "." }
    test { parseEntry("XXXXXXXX_XXXX.ABC +│").rating == "+" }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world").header == "hello world" }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world ").header == "hello world" }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar").header == "hello world" }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world  #tag !bar").header == "hello world" }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar").tags == listOf("#tag", "!bar") }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar ").tags == listOf("#tag", "!bar") }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world  #tag !bar ").tags == listOf("#tag", "!bar") }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world ##tag !bar").header == "hello world ##tag" }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world ##tag !bar").tags == listOf("!bar") }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world &1 #tag !bar").header == "hello world" }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello & world #tag !bar").header == "hello & world" }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world & #tag !bar").header == "hello world &" }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar &").header == "hello world" }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world #tag !bar &1").header == "hello world" }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ 🩢🩣🩤 #tag !bar").header == "🩢🩣🩤" }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ 🩢🩣🩤 #tag !bar").tags == listOf("#tag", "!bar") }
}
fun parseEntry(header: String) = parseEntry(header, "")
fun parseEntry(header: String, body: String): Entry {
    val tagIndex = header.indexOf(tagStartRegex) ?: header.lastIndex
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
    test { parse("XXXXXXXX_XXXX ABC  │ hello world").first().header == "hello world" }
    test { parse("XXXXXXXX_XXXX ABC  │ hello world\nbody\n").first().body == "body" }
    test { parse("XXXXXXXX_XXXX ABC  │ hello world\nXXXXXXXX_XXXX ABC  │ hello world2").count() == 2 }
    test { parse("XXXXXXXX_XXXX ABC  │ hello world ##tag !bar\n\nbody goes here\n\n").first().body == "body goes here" }
    test { parse("XXXXXXXX_XXXX ABC  │ hello world ##tag !bar\r\n\r\nbody goes here\r\n\r\n").first().body == "body goes here" }
    test { parse("XXXXXXXX_XXXX ABC  │ hello world #tag !bar\n\nbody goes here\n").first().tags == listOf("#tag", "!bar") }
    test { parse("XXXXXXXX_XXXX ABC  │ hello world #tag !bar\n\nbody #notag goes here\n").first().tags == listOf("#tag", "!bar") }
    test { parse("XXXXXXXX_XXXX ABC  │ hello world\n\nbody #notag goes here\n").first().tags.isEmpty() }
    test { parse("// vim: modeline\nXXXXXXXX_XXXX ABC  │ hello world\nbody\n").first().header == "hello world" }
}
val linefeed = System.getProperty("line.separator")
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

fun def_getDateTime() {
    testThrows<DateTimeParseException> { parseEntry("XXXXXXXX_XXXX ABC  │ ").getDateTime() }
    testThrows<DateTimeParseException> { parseEntry("20000101_XXXX ABC  │ ").getDateTime() }
    test { parseEntry("20000101_0000 ABC  │ ").getDateTime() == LocalDateTime.of(2000, 1, 1, 0, 0) }
}
val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
fun Entry.getDateTime(): LocalDateTime = LocalDateTime.parse(seq, dateTimeFormat)

fun def_getTimeSpent() {
    test { parseEntry("XXXXXXXX_XXXX ABC  │ ").getTimeSpent() == null }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ +0").getTimeSpent() == 0 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ /code!").getTimeSpent() == 0 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ /code +15").getTimeSpent() == 15 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ /code +15 +30").getTimeSpent() == 30 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ +15 /code!").getTimeSpent() == 0 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ /code! +15").getTimeSpent() == 15 }
}
val timeSpentRegex = Regex("(\\+[0-9]+|/.*!)")
fun Entry.getTimeSpent(): Int? {
    val timeSpentTag = tags.filter { it.matches(timeSpentRegex) }.lastOrNull()
    if (timeSpentTag == null) return null
    if (timeSpentTag.endsWith("!")) return 0
    return timeSpentTag.substring(1).toInt()
}

fun def_getSkips() {
    test { parseEntry("XXXXXXXX_XXXX ABC  │ ").getSkips() == 0 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ &").getSkips() == 1 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ & ").getSkips() == 1 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world").getSkips() == 0 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world &").getSkips() == 1 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world & #foo").getSkips() == 0 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world &1 #foo").getSkips() == 1 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world #foo &").getSkips() == 1 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world #foo &2").getSkips() == 2 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world &2 #foo").getSkips() == 2 }
    test { parseEntry("XXXXXXXX_XXXX ABC  │ hello world &2 #foo &3").getSkips() == 3 }
}
val skipsRegex = Regex("&[0-9]*")
fun Entry.getSkips(): Int {
    val skipsTag = tags.filter { it.matches(skipsRegex) }.lastOrNull()
    if (skipsTag == null) return 0
    if (skipsTag.length == 1) return 1
    return skipsTag.substring(1).toInt()
}

fun def_calculateSpentTime() {
    test { parse("").calculateSpentTime("") == 0}
    test { parse("20000101_0000 ABC  │ =p1").calculateSpentTime("=p1") == 0}
    test { parse("20000101_0000 ABC  │ =p1 +15").calculateSpentTime("") == 0 }
    test { parse("20000101_0000 ABC  │ =p1 +15").calculateSpentTime("=p2") == 0 }
    test { parse("20000101_0000 ABC  │ =p1 +15").calculateSpentTime("=p1") == 15 }
    test { parse("""
        20000101_0000 ABC  │ write code /code =p1
        20000101_0015 ABC  │ debug code /debug =p1
        20000101_0030 ABC  │ switch projects /code +10 =p2""".trimIndent())
        .calculateSpentTime("=p1") == 30 }

    test { parse("""
        20000101_0030 ABC  │ switch projects /code +10 =p2
        20000101_0145 ABC  │ debug new project /debug =p2
        20000101_0230 ABC  │ make a mango shake /cook""".trimIndent())
        .calculateSpentTime("=p2") == 55 }

    test { parse("""
        20000102_1030 ABC  │ get up /wake &
        20000102_1045 ABC  │ recall my dreams /recall
        20000102_1115 ABC  │ make pancakes /cook""".trimIndent())
        .calculateSpentTime("/wake") == 45 }

    test { parse("""
        20000102_1030 ABC  │ get up /wake +5 &
        20000102_1045 ABC  │ recall my dreams /recall
        20000102_1115 ABC  │ make pancakes /cook""".trimIndent())
        .calculateSpentTime("/wake") == 5 }

    test { parse("""
        20000102_1030 ABC  │ get up /wake &2
        20000102_1045 ABC  │ recall my dreams /recall
        20000102_1115 ABC  │ stretch /stretch
        20000102_1145 ABC  │ make pancakes /cook""".trimIndent())
        .calculateSpentTime("/wake") == 75 }

    testThrows<EntrySequenceException> { parse("""
        20000102_1200 ABC  │ start coding /code =p3 &
        20000102_1230 ABC  │ research kotlin /search =p3
        20000102_1300 ABC  │ make a sandwich /cook""".trimIndent())
        .calculateSpentTime("=p3") }
}
class EntrySequenceException(message: String): Exception(message)
fun Sequence<Entry>.calculateSpentTime(tag: String): Int = calculateSpentTime { it.tags.contains(tag) }
fun Sequence<Entry>.calculateSpentTime(filter: (Entry) -> Boolean): Int {
    var total = 0
    var pop = true
    val i = iterator()
    lateinit var current: Entry
    while (i.hasNext()) {
        if (pop) current = i.next(); pop = true
        if (filter.invoke(current)) {
            val timeSpent = current.getTimeSpent()
            if (timeSpent != null) {
                total += timeSpent
            } else {
                val startTime = current.getDateTime()
                val skips = current.getSkips()
                for (skip in 0 .. skips) { 
                    if (i.hasNext()) {
                        current = i.next() 
                        if (skip < skips && filter.invoke(current))
                            throw EntrySequenceException("Entry overlaps: " + current)
                    } else {
                        break
                    }
                }
                total += startTime.until(current.getDateTime(), MINUTES).toInt()
                pop = false
            }
        }
    }
    return total;
}

fun main() {
    parse(System.`in`.bufferedReader())
      .filter { it.seq > "20220625" }
      .sortedBy { it.seq }
      .forEach { it.print() }
}

// run all the tests
fun test() {
    def_isJournalHeader()
    def_parseTags()
    def_parseEntry()
    def_parse()
    def_getDateTime()
    def_getTimeSpent()
    def_getSkips()
    def_calculateSpentTime()
}

// the first index matching the given regexp, or null if none is found
fun String.indexOf(regex: Regex): Int? = regex.find(this)?.range?.start

// kotlin.test not on the default classpath, so use our own assert function
fun test(code: () -> Boolean) { if (! code.invoke()) throw AssertionError() }
inline fun <reified T: Throwable> testThrows(code: () -> Unit) { 
    try { 
        code.invoke() 
        throw AssertionError("Exception expected")
    } catch (e: Throwable) { 
        if (!(e is T)) throw e
    } 
}

