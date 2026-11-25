# Changelog

## 1.0.0 (in development)

- Procedure values are compiled to classes of their own: calls through
  them are faster, and programs start faster.
- Bundled module `Math`: the mathematical procedures of Algol 68 Genie
  that the prelude lacks (`arctan2`, `sinh`, `gamma`, `complex_exp`, ...).
- `Files`: `read_bytes` and `write_bytes`.
- `-fgenie`: `COMPLEX`, `null_char` and access to `Math`.
- Internal compiler errors are reported as ga68 reports them.
- An oversized widened denotation is a compile-time error.
- Corrected: `fgetc` lost the bytes after invalid UTF-8 (ga68 bug report
  29).
- Corrected: `flex [1 : n] char` got the bounds 1:0, and rows in a
  structure could get the bounds of another field.
- Corrected: a program that runs out of memory ends with the message of
  ga68 (`Virtual memory exhausted`) instead of a Java stack trace, and an
  include file name that the file system cannot name gives "included
  file not found" instead of an internal compiler error.

## 0.9.0

- Formatted input: `readf` and `getf` in `Genie_Formats`.
- `-fformats` compiles format texts directly into calls of
  `Genie_Formats`, with errors reported by the compiler; `-fgenie` does
  the same.
- Bundled modules `Files` (files and directories), `Times` (dates, times
  and durations) and `Bigdecimals` (decimals of any precision).
- Java interop: the parameter types in a formal hole select the overload.
- `Genie_Transput`: `next_random` and `rnd`.
- Widening to `long compl`.

## 0.8.1

- `-fgenie` widens between lengths (`LONG INT x := 1`) as Algol 68 Genie
  does, and accepts `ANDF`, `ORF` and `DOWNTO`.
- `Genie_Transput` and `Genie_Formats` handle `long long real`,
  `long long bits` and `compl`.

## 0.8.0

- `-fgenie`: runs programs written for Algol 68 Genie, such as Rosetta
  Code solutions, as they are.
- Bundled module `Genie_Formats` and a rewrite tool for formatted output
  (`$...$`, `printf`); `-fformats` for programs written for a68j.
- The `-W` options of ga68 and `-fno-assert` are accepted, unknown options
  are rejected.
- Warning `-Wframe-scope` for scope violations that a68j cannot detect at
  run time.

## 0.7.0

- VS Code extension for UPPER and SUPPER stropping.
- Corrected: `fixed`, `float` and `string_to_real` on large values,
  `modab`, the comparison of reals with NaN, the postlude of modules, and
  the module keys of large recursive modes.
- Absolute Windows paths in the include search.
- Releases are published on Maven Central.

## 0.6.0

- Partial parametrization along the lines of Algol 68 Genie.
- Bundled module `Bigints`: integers of any size.
- An undefined value gives a runtime error instead of a Java stack trace.

## 0.5.0

First release.

- Front end: a file-by-file port of ga68, the Algol 68 front end of GCC,
  with the same diagnostics.
- Back end: JVM class files through the JDK's ClassFile API; programs run
  directly or as executable jars, with a separate runtime jar.
- Java interop with `nest Java` (`-fnest-java`): static methods,
  constructors, instance methods and fields.
- Real threads for `par` clauses (`-fpar-threads`).
- Bundled modules `Lists`, `Hashmaps`, `Genie_Transput`, `Texts` and
  `Regex`.
- The book *Algol 68 in Modern Terms*.
