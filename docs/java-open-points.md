# Java integration: open points and module ideas

The state of the Java integration (`-fnest-java`, `nest Java "…"` formal
holes, the modules `Lists` and `Hashmaps`) is described in
[java-modules.md](java-modules.md). This file has two parts: what is still
open in the integration itself (part A), and ideas for further modules
backed by Java APIs (part B). None of the open points blocks the use of
the integration; A1 is done and kept here as a record.

## Part A. Open points

### A1. Overloads that differ only in a reference parameter (done)

Done on 1 Nov 2026. A Java object (a name of a mode such as `Stringlist`)
fits every parameter of a reference type equally well, since a68j does not
know its class when the program is compiled. Of two overloads that differ
only there, the first one in the order of `getMethods()` used to be taken
silently, so that

```
proc (string, List) string join = nest Java "java.lang.String.join"
```

took `join(CharSequence, CharSequence...)`, not
`join(CharSequence, Iterable)`, and the call ended in a runtime error.

Now several overloads that fit at the same lowest cost are a compile-time
error that names them (`GenJava.best`, java-modules.md section 1a, "Which
method", test `nest-java-ambiguous`). The author of the hole makes it
unique with a mode that fits only one overload or with a small Java
wrapper method.

Checking all tests for such ties found a second one, not caused by Java
objects: a `char` fitted a Java `int` and a Java `char` at the same cost,
so of `Character.isDigit(int)` and `isDigit(char)` one was taken by
chance. A `char` is a code point, so the `int` is now preferred, and a
character beyond U+FFFF is no longer cut to 16 bits (test
`nest-java-char`).

### A2. Optional Java stack trace

A Java exception ends the program with a runtime error that gives the
Algol 68 position, the exception and its causes (java-modules.md,
"Java exceptions"). The Java stack trace is not shown. For the authors of
Java modules it would help to have it on request, for instance with an
option or an environment variable, printed after the runtime error
message.

This is a debugging aid only; the message without it stays as it is.

### A3. Class initialization errors on the static field path

A formal hole `nest Java "Class.member"` whose mode is not a procedure
reads a public static field (`GenJava.method`, the branch for non-procedure
modes). The `getstatic` there is not wrapped like the calls are. If the
class throws in its static initializer, the program gets a raw
`ExceptionInInitializerError` instead of an Algol 68 runtime error with
the position of the hole.

Fields read through `"Class::field"` as procedures go through the same
code as method calls, so this concerns only the plain static field path.
It is rare and a matter of polish.

## Part B. Ideas for Java modules

Modules in the style of `Lists` and `Hashmaps`, where a Java API fills a
gap in the Algol 68 library (the prelude of the Revised Report and its
transput). They are ordered by how much they would help in everyday
programs, in my estimate.

Two rules from java-modules.md apply to all of them:

- A Java object is an opaque Algol 68 name, `mode X = ref X_object` with
  `mode X_object = struct (int x_java_object)`, as for `Stringlist`.
- A module that should be usable together with `Lists` must not declare
  a priority for `AT`, `INDEX`, `HAS` or `REMOVE`; it takes
  `access pub Lists` instead (java-modules.md, "`Lists` and `Hashmaps`
  together").

### B1. `Texts`: regular expressions and string functions

**The gap.** Algol 68 has strings as `flex [] char`, slices and
`char in string`, but no regular expressions, no split, no replace, no
case conversion, no trimming and no formatting into a string. Every small
program that parses input or searches text builds these by hand. This is,
in my estimate, the most painful gap.

**The Java side.** `java.util.regex.Pattern` and `Matcher`, and the
methods of `String` (`split`, `replace`, `replaceAll`, `strip`,
`toUpperCase`, `toLowerCase`, `formatted`, `repeat`).

**How it fits.**

- Plain string functions need no objects: `proc (string) string upper`,
  `proc (string, string, string) string replace_all` and so on are direct
  `nest Java` holes on `String` methods.
- `Pattern` and `Matcher` are opaque objects. `find`, `matches`, `group`,
  `start` and `end` give `bool`, `string` and `int`. Algol 68 indexes from
  1, Java from 0, so `start` and `end` should be shifted to Algol 68
  positions.
- `split` could return a `Stringlist` from `Lists` (with
  `access pub Lists`) or a `flex [] string`.
- A replace with a procedure (`Matcher.replaceAll(Function)`) works with
  the functional interfaces of section 1a: `proc (Match) string`.

**Prerequisite.** Several of these methods are overloaded with
`CharSequence` and `Iterable` parameters. Since A1 is done, a hole that
does not choose one of them is a compile-time error, so the module needs
modes that fit one overload, or a small Java helper class.

### B2. `Files`: files and directories

**The gap.** The transput has no test whether a file exists, no directory
listing, no way to read a whole file into a string or write one from a
string, and no delete, rename, copy or creation of directories. Without
them Algol 68 is hard to use for small tools and scripts.

**The Java side.** `java.nio.file.Files` and `Path`: `exists`,
`isDirectory`, `readString`, `writeString`, `readAllLines`, `list`,
`walk`, `delete`, `move`, `copy`, `createDirectories`, `size`,
`getLastModifiedTime`.

**How it fits.** Most of it can work with paths as strings, so no object
mode is needed: `proc (string) bool file_exists`,
`proc (string) string read_file`, `proc (string, string) void write_file`.
A directory listing gives a `Stringlist` or a `flex [] string`. Errors such
as a missing file become runtime errors with the Algol 68 position, as for
every Java exception; a variant that returns a `bool` for success may be
wanted for scripts that test before they act.

### B3. `Bigints`: arbitrary precision numbers

**The gap and the appeal.** With the user-defined operators of Algol 68, a
module with `mode Bigint` and `op +`, `op -`, `op *`, `op %`, `op mod`,
`op **`, `op <` and so on gives the natural notation `a + b * c` at any
precision. The standard operator symbols already have priorities in the
prelude, so the module declares none. This would show what Algol 68 and a
Java library can do together, and many Rosetta Code tasks (factorials,
large Fibonacci numbers, exact fractions) need it.

**The Java side.** `java.math.BigInteger` and, for decimals,
`BigDecimal`. Both are immutable, which fits values in Algol 68.

**How it fits.** `Bigint` is an opaque object. Conversions:
`op big = (int) Bigint`, from and to `string`, and `whole`-like output.
Mixed operators (`Bigint` with `int`) avoid many explicit conversions.

**To check first.** Whether this closes a real gap depends on how far
`long long int` reaches in a68j. That has not been checked yet.

### B4. `Times`: date and time

**The gap.** The prelude has no clock time, no calendar dates and no
date arithmetic.

**The Java side.** `java.time`: `LocalDate`, `LocalDateTime`, `Duration`,
`DateTimeFormatter`, `Instant`.

**How it fits.** Immutable objects as opaque names; parse and format with
strings; differences and additions with operators as in B3. Useful, but
needed less often than B1 to B3.

### B5. Less central

HTTP (`java.net.http.HttpClient`) and JSON would also fit technically.
JSON has no parser in the JDK, so it would need a library on the class
path (`-fjava-classpath`). Both are less central for Algol 68 programs.
