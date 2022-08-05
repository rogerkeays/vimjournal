//usr/bin/env [ $0 -nt $0.jar ] && kotlinc -d $0.jar $0; [ $0.jar -nt $0 ] && java -cp $CLASSPATH:$0.jar CatuniqKt $@; exit 0

import java.io.File

val seen = HashSet<String>()

fun main(args: Array<String>) {
    args.takeLastWhile { it != "-" }.forEach { File(it).forEachLine { seen.add(hash(it)) } } 
    args.takeWhile { it != "-" }.forEach { catuniq(it) }
}

fun hash(str: String): String { 
    return str.replace(Regex("^.*│"), "")  // remove old journal prefix
              .replace(Regex("^............. \\|. "), "") // remove new journal prefix
              .replace(Regex(" [/+#=!>@:][^/+#=!>: ].*"), "") // remove trailing tags
              .replace(Regex(" --.*$"), "")  // remove attributions
              .replace(Regex("[^a-zA-Z]"), "") // normalize
              .lowercase()
}

fun catuniq(filename: String) {
    println("\n======= " + filename + "\n")
    File(filename).forEachLine {
        var hash = hash(it)
        if (!seen.contains(hash)) {
          seen.add(hash)
          println(filename.split("/").last() + ":\t $it")
        }
    }
}

fun catdup(filename: String) {
    println("\n======= " + filename + "\n")
    File(filename).forEachLine { 
        var hash = hash(it)
        if (hash.isNotBlank() && seen.contains(hash)) println(it)
    }
} 

