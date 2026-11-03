# Java integration: module ideas

The state of the Java integration (`-fnest-java`, `nest Java "…"` formal
holes, the modules `Lists` and `Hashmaps`) is described in
[java-modules.md](java-modules.md). This file collects ideas for further
modules backed by Java APIs. The open points of the integration itself
are done, and so is the idea B1, the modules `Texts` and `Regex`
([texts.md](texts.md), [regex.md](regex.md)).

## Ideas for Java modules

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
needed less often than `Texts`, `Regex`, B2 and B3.

### B5. Less central

HTTP (`java.net.http.HttpClient`) and JSON would also fit technically.
JSON has no parser in the JDK, so it would need a library on the class
path (`-fjava-classpath`). Both are less central for Algol 68 programs.
