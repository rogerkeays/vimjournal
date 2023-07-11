# Vimjournal

*Vimjournal* is a simple text format and utilities for organising large amounts of information. Although Vimjournal does not depend on VIM, it does provide syntax highlighting and support to make editing *Vimjournal* logs easier.

A *Vimjournal* log is an append-only text file normally ending in `.log`. There are generally two types: compact and detailed. Compact logs contain one record per line. For example, here is a time log:

    20200709_1423 |= remove use of unsafe reflection: --add-opens is better /code =jamaica @lao-home:thakhek
    20200709_1555 |- debug jshell compatibility problems: broken state /debug =jamaica @lao-home:thakhek
    20200709_1624 |= update known issues: jshell, shell parameters /write =jamaica @lao-home:thakhek
    20200709_1650 |= run unchecked test builds: all passed /test =jamaica @lao-home:thakhek
    20200709_1856 |+ run along the river /run @riverside:thakhek
    20200709_1945 |- look for my lost plastic waterbottle: left it at the riverside mamak /hunt /walk @riverside:thakhek

Or an expense log:

    20200906_1830 |- USD 2           clothes CASH       sunnies @market
    20200907_2000 |- USD 4.5         hardware CASH      aima earphones x3 @mr-diy:sorya-centre
    20200907_2000 |- USD 2           sport CASH         silicon swimming caps x2 @mr-diy:sorya-centre
    20200908_1445 |- USD 30          tax CASH           visa extension @immigration-office
    20200909_1700 |- USD 6.25        travel CASH        bus to the city @sla-hostel
    20200911_1010 |- USD 1.25        hardware CASH      laptop charge repair @route-33
    20200911_1355 |- USD 14          accomm CASH        khmer house bungalow @khmer-house

Or quotes:

    20200228_1632 |= journalists never let facts get in the way of a good narrative --patrick mckenzie #truth
    20200305_2200 |+ if you play well, nobody listens, and if you play badly, nobody talks --algernon, the importance of being earnest #music
    20200310_1055 |* excellence is a habit --aristotle #productivity >philosophizethis
    20200329_1738 |+ never give up and good luck will find you --falkor, the never-ending story #motivation
    20200423_1035 |= i'll pick the lock but will not turn the key --bad religion #psychology

Detailed logs allow you to add any unstructured text after the record header. For example, an issue log:

    20200706_1054 |= ✔ unchecked throws warnings when catching checked exceptions /java =jamaica

    ==PROBLEM

    javac shows warnings when catching checked exceptions

        Test.java:18: warning: unreachable catch clause
            } catch (UnsupportedEncodingException e) {} // okay, never thrown
              ^
        thrown types  have already been caught

    ==PROPOSALS

      - this warning only appears for checked exceptions
      - in github JDK, the source is Flow.FlowAnalyzer.checkCaughtType() (line 1693)
      - this doesn't exist in older compilers, it is just Flow.checkCaughtType()
      - subclassing the nested class is likely to get nasty
      ...

    20200708_2023 |= ✔ hibernate build cannot find unchecked plugin /java =jamaica

    ==PROBLEM

    hibernate build fails with "error: plug-in not found: unchecked"

    ==SYNOPSIS

    adding the jar to the classpath has no effect
    error caused by -processorpath parameters passed by hibernate to javac
    see hibernate-core/target/tmp/compileJava/java-compiler-args.txt
    ...

Or programming snippets:

    20200603_1337 |- create a virtual property in kotlin /kotlin >sorting by rating =vimjournal @lobby

    // behaves as a function
    class X {
        val priority: Int
            get() { 1 }
    }

    // better to use a plain value
    class X {
        val priority = 1
    }

    20200605_1740 |= calculate a modulus in bash /bash >sorting flashcards @lobby

    i=21
    echo $((i % 5))   # 1

    20200617_0852 |= run monkey island in dosbox /dos @lobby

    // sound doesn't work, requires separate cd
    // https://www.vogons.org/viewtopic.php?t=17826
    cd /home/library/games/dos/monkey-island-1
    dosbox
    mount -t cdrom c .
    c:
    monkey.exe

    20200617_1054 |+ mount iso image /linux >downloading dos games =library @lobby

    mount -o loop -t iso9660 ./MyImage.iso /tmp/disk/

    20200630_1142 |+ print a method's stack trace /java =fluent:jamaica >compiler hacking @office

    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
    for (int i = 1; i < elements.length; i++) {
         StackTraceElement s = elements[i];
         System.out.println("\tat " + s.getClassName() + "." + s.getMethodName() + "(" + s.getFileName() + ":" + s.getLineNumber() + ")");
    }

Or how about a vocabulary log:

    20200329_0933 |= fr: an opinion >pimsleur 3.17 @bukit-china

    un avis /a.vi/

    from old french avis, from vis, from latin visus

    20200331_2009 |- en: instructive or intended to teach /adj >etudes article @kitchen

    didactic /daɪˈdæk.tɪk/, /dɪˈdæk.tɪk/

    sometimes such a sort of exercises formed actual pieces of real artistic
    music, but [are] written predominantly for didactic purposes

    from french didactique, from ancient greek διδακτικός (didaktikós, “skilled
    in teaching”), from διδακτός (didaktós, “taught, learnt”), from διδάσκω
    (didáskō, “i teach, educate”)

    20200420_1100 |= fr: to happen; to go by /verb >pimsleur @bukit-china

    se passer

    mon voyages se bien passaient

    from middle french passer, from old french passer, from vulgar latin
    *passāre, derived from latin passus (“step”, noun)

    20200420_1110 |= fr: a success /noun >pimsleur @bukit-china

    un succès /syk.sɛ/

    la réunion avec notre nouveau client a été un succès

    borrowed from latin successus

    20200420_1444 |- en: to replace a contract with one or more new contracts /verb >selfwealth @library

    novate

    your chess sponsorship agreement with openmarkets will be novated to fnz
    custodians (australia) pty ltd

In VIM you will use <TAB> to open and close detailed records, making it easy to scan large amounts of information.

The basic format of the records are:

    YYYYMMDD_HHMM |[rating] [title] [tags]

Rating is one of `* + = - x` in order of highest to lowest. Tags begin with a special character and have the following intended uses:

    prefix   scope   usage
    ------------------------------------------------------
    /        file    categories specific to that log file
    +(text)  global  people
    +(nums)  record  duration
    #        global  topic
    =        global  project
    !        global  problem, goal
    >        record  context
    @        record  place
    :        record  data, url
    &n       record  skips, used to indicate a log entry overlaps the n following ones

All tags support a reverse-hierarchy syntax using `:` like this: `@melbourne:australia`, `@sydney:australia`, `@opera-house:sydney:melbourne`. This is useful for project tags: `=unchecked:jamaica`, `=fluent:jamaica` etc. The reverse-hierarchy is used so we use code-completion instead of writing them out in full. Nobody wants to do that.

## Usage in VIM

Add [Vimjournal.vim](https://raw.githubusercontent.com/rogerkeays/vimjournal/main/vimjournal.vim) to your `$HOME/.vim/ftdetect` directory. You can also symlink it to your github clone of this project if you chose.

In addition to syntax highlighting, *Vimjournal* adds up the following shortcut keys:

    <C-t> append a record
    <TAB> open and close a detailed record
    <C-l> browse the journal's directory
    <C-o> open a new record below the current one (compact logs only)
    <C-x> insert a ✘ in insert mode
    <C-z> insert a ✔ in insert mode
    <C-h> search backwards for non-sequential records (compact logs only)
    <C-n> search forwards for non-sequential records (compact logs only)

If you are editing a very large compact log, the following modeline will disable the code folding only necessary for detailed logs, making loading much faster:

    // vim: foldmethod=manual nofoldenable nowrap

## Command Line Tools

The `vimjournal.kt` Kotlin script contains some utilities for handling *Vimjournal* log files. It reads from stdin and writes to stdout. The basic usage is:

    cat input.log | vimjournal.kt [command] > output.log

The commands are:

    filter-from <seq>
    filter-rating <string>
    filter-summary <string>
    make-flashcards (makes png images which can be used as simple flashcards for your phone)
    show-durations (appends the time between records as a + tag)
    sort
    sort-by-summary
    sort-by-rating
    sort-tags
    sum-durations <tag> (calculate the time spent on entries matching the given tag)

See [the source code](https://github.com/rogerkeays/vimjournal/blob/main/vimjournal.kt) for exact operation.

Being plain text means you don't need too many special tools to work with *Vimjournal* log files. `grep` on its own is going to give you a lot of mileage. Two particularly useful functions you might want to add to your shell are:

    j() { grep -i "|.*$*" $JOURNAL/*.log | less; } # search journal headers
    jj() { grep -i "$*" $JOURNAL/*.log | less; }   # search journal headers and content

## Organising Your Logs

The real power of *Vimjournal* comes from organising your information in a way that you can find it. Experience shows that the best way to organise information is by type, then by date. *Vimjournal* encourages you to organise your information by date, but it is still possible to end up with a big ball of mud using *Vimjournal* if you mix different types of data in one log. It might be tempting to put, for example, all of the information for a project in one file, but this would be like organising your kitchen by recipe. It's going to be very cluttered if your have more than three or four recipes. Organise by type and use tags to cross-reference logs.

*Vimjournal* is useful for recorded all sorts of types of data. For example: time logs, expenses, programming snippets, quotes, dreams, contact information, ideas, links, recipes, reading notes, questions, and observations, and just about anything else you can imagine.

## Known Issues

  * Syntax highlight doesn't work when the records are folded.
  * Compact logs need to use a modeline to turn off code folding. This saves creating a new file type.

## Related Resources

  * [Vimcash](https://github.com/rogerkeays/vimcash): an accounting system based on *Vimjournal*.
  * [Vimliner](https://github.com/rogerkeays/vimliner): the simplest outliner for VIM.
  * [More stuff you never knew you wanted](https://rogerkeays.com).

