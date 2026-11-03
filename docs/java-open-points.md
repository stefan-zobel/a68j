# Java integration: open points and module ideas

The state of the Java integration (`-fnest-java`, `nest Java "…"` formal
holes, the modules `Lists` and `Hashmaps`) is described in
[java-modules.md](java-modules.md). This file has two parts: what is still
open in the integration itself (part A), and ideas for further modules
backed by Java APIs (part B). All points of part A are done; they are
kept here as a record.

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

### A2. Optional Java stack trace (done)

Done on 1 Nov 2026: with the environment variable `A68J_JAVA_TRACE` set,
`Rt.abortJava` prints the Java stack trace after the runtime error
message (java-modules.md, "Java exceptions"; test `nest-java-trace`). The
original note:

A Java exception ends the program with a runtime error that gives the
Algol 68 position, the exception and its causes (java-modules.md,
"Java exceptions"). The Java stack trace is not shown. For the authors of
Java modules it would help to have it on request, for instance with an
option or an environment variable, printed after the runtime error
message.

This is a debugging aid only; the message without it stays as it is.

### A3. Class initialization errors on the static field path (done)

Done on 1 Nov 2026: the `getstatic` is in the same exception handler as
the calls (`GenJava.guarded`), so the program ends with a runtime error at
the formal hole that names the `ExceptionInInitializerError` and its
cause (test `nest-java-init-error`, class `tests/java/demo/Broken.java`).
The original note:

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
programs, in my estimate. B1 is the exception to "Java": its string
functions are planned in plain Algol 68, and only its regular
expressions use Java.

Two rules from java-modules.md apply to all of them:

- A Java object is an opaque Algol 68 name, `mode X = ref X_object` with
  `mode X_object = struct (int x_java_object)`, as for `Stringlist`.
- A module that should be usable together with `Lists` must not declare
  a priority for `AT`, `INDEX`, `HAS` or `REMOVE`; it takes
  `access pub Lists` instead (java-modules.md, "`Lists` and `Hashmaps`
  together").

### B1. `Texts` and `Regex`: string functions and regular expressions

**Status (3 Nov 2026): implemented** as planned below, see
[texts.md](texts.md) and [regex.md](regex.md); the differences from
this plan are noted where they occur.

**The gap.** Algol 68 has strings as `flex [] char`, slices and
`char in string`, but no regular expressions, no split, no replace, no
case conversion of strings, no trimming, no padding and no conversion of
a string to a number. Every small program that parses input or formats
text builds these by hand.

**Measured.** In the 1328 Algol 68 Genie solutions of Rosetta Code
(`tests/rosetta/orig`), at least 98 programs declare such helpers
themselves (found by the names of their `PROC` and `OP` declarations,
each match checked by hand, so this is a lower bound); 29 of them belong
to tasks about text, the others need the helpers on the side. By kind:
number to string 29, padding and centring 31, upper and lower case 14,
string to number 11, trimming 10, splitting 10, prefix, suffix and index
9, replace 3, join 3. A further 31 programs use `associate` on a string
only, to format or parse with the transput, and 9 use the regular
expressions of Algol 68 Genie (`grep in string`, `sub in string`). For
comparison, only 9 programs use the file system (B2), all of them in
tasks about the file system.

**The plan in short.** Two modules, so that the string functions stay
portable:

- `Texts`, in plain Algol 68 like `Genie_Transput`: bundled with a68j,
  and it compiles with ga68 as well. Characters are classified in ASCII,
  as in `Genie_Transput` and Algol 68 Genie. With ASCII, a Java version
  would add nothing but speed, so there is no Java in it.
- `Regex`, backed by `java.util.regex`, with the procedures of Algol 68
  Genie under their names. It needs Java, so it runs with a68j only.

#### `Texts`

The interface follows the standard prelude: monadic operators for what
transforms one value (as `ABS`, `REPR`, `ENTIER`), dyadic operators where
an expression with two operands reads naturally, and procedures for three
or more parameters, an output parameter, or a row as the main argument.
The Rosetta authors do the same; most of their helpers are operators
(`OP TOUPPER`, `OP TRIM`, `OP PAD`, `OP TOINT`).

| Name | Meaning |
|---|---|
| `UPPER s`, `LOWER s` | the string (or `char`) in upper or lower case, ASCII letters only |
| `TRIM s`, `LTRIM s`, `RTRIM s` | without white space (blank, tab, newline, CR, FF, VT) at both ends, at the start, at the end |
| `TOINT s`, `TOREAL s` | the number in `s` (white space around it allowed); anything else is a runtime error (added: `TOLONGINT`, `TOLONGREAL` and `string_to_long_int`, `string_to_long_real`, as `real` has single precision; texts.md) |
| `s STARTS p`, `s ENDS p` | whether `s` begins or ends with `p` (priority 5, as `<`) |
| `s SPLIT sep` | the fields of `s` between the separators, as `[] string`; `sep` is taken literally, empty fields are kept (`"a,,b"` gives three fields); priority 6, as `+` |
| `s LJUST w`, `s RJUST w`, `s CENTRE w` | `s` filled with blanks to the width `w`, left-aligned, right-aligned or centred; a longer `s` is returned unchanged; priority 6 |
| `words (s)` | the words of `s`, separated by runs of white space, without empty fields |
| `join (parts, sep)` | the strings of `parts` with `sep` between them |
| `replace_all (s, old, new)` | `s` with every occurrence of `old` replaced by `new`, literally |
| `string_to_int (s, ref int v)`, `string_to_real (s, ref real v)` | `true` and the value in `v` if `s` is a number, else `false` and `v` unchanged (the pattern of `char in string`) |

Rules for the design:

- **`char` as well as `string`.** An operand of an operator is only firm,
  so `","` is not widened to a `string` there. Every dyadic operator
  also exists with a `char` operand, as in `Hashmaps`.
- **Positions** are Algol 68 indices, relative to `LWB s`.
- **No clash with `Genie_Transput`.** A program often accesses both, and
  two modules that export the same identifier cannot be accessed
  together (`multiple declaration of identifier`, checked with a68j). So
  `Texts` does not declare the searches again (`char_in_string`,
  `string_in_string`, `last_char_in_string`) nor `to_upper`, `to_lower`
  and the `is_` procedures; they stay in `Genie_Transput`. Neither module
  declares a priority the other one declares, and `Texts` declares none
  of `AT`, `INDEX`, `HAS` and `REMOVE` of `Lists`.
- **`TOREAL` and `string_to_real`** should read the same bits as `read`
  of `Genie_Transput` (correctly rounded, see genie-transput.md). Whether
  the reader can be shared through a third module that both access
  without `pub`, instead of a copy, is to be checked. (Done: it can, but
  every user of `Genie_Transput` with ga68 would then need that module
  too, so `Texts` has a copy; texts.md.)

#### `Regex`

The procedures of Algol 68 Genie, with the same modes and results
(checked with Algol 68 Genie 3.1.2):

| Name | Meaning |
|---|---|
| `grep_in_string (pattern, s, ref int start, ref int end) int` | 0 if `pattern` matches in `s`, with the first and last position of the match (relative to `LWB s`); 1 if not, with `start` and `end` unchanged; 2 if the pattern is invalid. `start` and `end` may be `nil` |
| `grep_in_substring (pattern, s, ref int start, ref int end) int` | the same, but `^` does not match at the start of `s` (`$` does match at its end) |
| `sub_in_string (pattern, replacement, ref string s) int` | replaces the first match in `s` by `replacement`, taken literally (no `\1`, `$0` or `&`); 0, 1 or 2 as above |

Where it differs from Algol 68 Genie, and must be documented:

- **Which match.** Algol 68 Genie uses POSIX regular expressions, which
  take the leftmost longest match; Java takes the leftmost match of the
  first alternative that fits. `a|ab` in `xabc` gives positions 2 to 3 in
  Algol 68 Genie and 2 to 2 in Java (both measured).
- **POSIX classes.** Java does not know `[[:alpha:]]` and does not reject
  it either: on `ab:c` the pattern `[[:alpha:]]+` finds `a` in Java
  (measured), not `ab`. The module translates the twelve POSIX classes
  into their Java equivalents (`\p{Alpha}` ...) before compiling the
  pattern. Equivalence classes and collating elements (`[[=a=]]`,
  `[[.x.]]`) give 2. (Done differently: those of one character are taken
  as the character, as Algol 68 Genie accepts them; regex.md.)
- **Java syntax.** Patterns that only Java accepts (lazy quantifiers,
  `\d`, look-ahead) work instead of giving 2.

Inside, a small Java class (as for `Lists`, java-modules.md section 5)
compiles and caches the patterns, maps between Algol 68 positions (code
points) and Java positions (UTF-16 units, which differ beyond U+FFFF),
emulates the `^` of `grep_in_substring` (a matcher without anchoring
bounds on a region that does not start at 0), and uses
`Matcher.quoteReplacement`. (Done differently: `^` becomes a
look-behind, and the replacement is built in Algol 68, so
`quoteReplacement` is not needed; regex.md, "Inside".) More than the procedures of Algol 68 Genie
(replace all, all matches, groups) can follow later as a second step.

The direct gain in Rosetta Code is small: most of the 9 programs that
use these procedures also need something else that ga68 lacks, such as
HTTP or a format.

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
