# Formats: the rewrite tool and the bundled module `Genie_Formats`

ga68 does not compile format texts (`$ ... $`) and has no mode `format`.
Formatted transput in the style of Algol 68 Genie (`printf`, `putf`,
`readf`, `getf`) is made of two parts, both written in plain Algol 68:

- **The rewrite tool** (`tools/formats/`) turns the format texts of a
  program into calls of the module `Genie_Formats`. It runs on a68j, ga68
  and Algol 68 Genie.
- **The module `Genie_Formats`** (`backend/lib/genieformats.a68`) parses
  the format text at run time and edits or reads the values. It is bundled with
  a68j, and it compiles with ga68 as an ordinary module. It accesses
  `Genie_Transput` ([genie-transput.md](genie-transput.md)).

With the option `-fformats`, for a program written for a68j, the
compiler parses the format texts itself and lowers each one to a call of
the module. The option `-fgenie`, for a program written for Algol 68
Genie, does the same, and runs the tool inside the compiler on each
source file for the rest of what Algol 68 Genie accepts (see the
sections on the two options below).

This is stages 1 to 3 of the roadmap in [TODO-formats.md](TODO-formats.md):
output with insertions, alignments, replicators, collections, the general
pattern `g`, the format pattern `f` (stage 1), pictures, the choice
patterns `b` and `c`, radix pictures and the alignment `k` (stage 2), and
formatted input with all of them, and the complex pattern `i` in output and
input (stage 3). Only the pattern `h` is missing.

## Example

A program written for Algol 68 Genie:

```
BEGIN
   INT k := 3;
   FORMAT row = $n(k)(g(-4))l$;
   printf ((row, 1, 2, 3));
   printf (($"x = "g(8, 2)l$, 3.14159))
END
```

The tool, with `--ga68`, writes:

```
ACCESS GENIE_TRANSPUT, GENIE_FORMATS (BEGIN
   INT k := 3;
   GFORMAT row = gfmtd ("n(@1)(g(-4))l", (INT gfindex) INT: CASE gfindex IN k ESAC, SKIP);
   printf ((row, 1, 2, 3));
   printf ((gfmt ("""x = ""g(8,2)l"), 3.14159))
END )
```

a68j does both steps in one, with the option `-fgenie`:

```sh
java -jar a68j-backend.jar -fgenie -run prog.a68
```

By hand, a68j and ga68 compile the result, in UPPER stropping:

```sh
java -jar a68j-backend.jar -fstropping=upper -I tools/formats \
     -run tools/formats/rewrite-a68j.a68 --ga68 < prog.a68 > prog-j.a68
java -jar a68j-backend.jar -fstropping=upper -run prog-j.a68
```

The tool can also be compiled once (`-d DIR`, main class `RewriteA68j`),
or run with Algol 68 Genie, whose arguments for the program come after
`--`:

```sh
cd tools/formats && a68g rewrite-a68g.a68 -- --ga68 < prog.a68 > prog-j.a68
```

With ga68, the modules are compiled from `backend/lib` and the tool from
`tools/formats`:

```sh
ga68 -c backend/lib/genietransput.a68
ga68 -I. -c backend/lib/genieformats.a68
ga68 -I. -I tools/formats -fstropping=upper tools/formats/rewrite-a68j.a68 genietransput.o -o rewrite
./rewrite --ga68 < prog.a68 > prog-j.a68
ga68 -I. -fstropping=upper prog-j.a68 genieformats.o genietransput.o -o prog
```

## The rewrite

The tool is a filter: it reads a program from standard input and writes it
to standard output, with the same number of lines, so that the line
numbers of messages stay right. It changes the following, and nothing
else:

| Original | Rewritten |
|---|---|
| a format text `$ ... $` without dynamic parts | `gfmt ("...")`, with the text of the format text as the string |
| a format text with dynamic parts | `gfmtd ("...", ints, formats)` |
| a unit in `n(...)`, `g(...)`, `h(...)` that is an integer denotation, such as `g(-8, 2)` | stays in the string: `g(-8,2)` |
| any other unit there, such as `n(k + 1)` | `n(@1)`; the unit becomes alternative 1 of `ints`: `(INT gfindex) INT: CASE gfindex IN k + 1 ESAC` |
| the unit of `f(...)` | `f(@1)`; the unit becomes alternative 1 of `formats`: `(INT gfindex) GFORMAT: CASE gfindex IN ... ESAC` |
| the mode `FORMAT` | `GFORMAT` (`Gformat` in SUPPER stropping) |
| an access list with `Genie_Transput`, in a program that uses formats | `, Genie_Formats` is added to the list |

`ints` or `formats` is `SKIP` when there is no such unit. Comments inside a
format text are dropped; a format text over several lines becomes a
concatenation `"..." + "..."` over the same lines. A format text inside a
unit is rewritten as well.

The option `--ga68` also prepares a program written for Algol 68 Genie for
ga68 and a68j:

- a program without an access clause is put into
  `ACCESS GENIE_TRANSPUT, GENIE_FORMATS ( ... )` (the parenthesis and the
  access are added to the first and last line; `GENIE_FORMATS` only if the
  program needs it). The option `--no-wrap` leaves this out;
- an apostrophe in a string denotation is doubled (in ga68 it starts a
  string break);
- in UPPER stropping, `FILE` and `CHANNEL` become `GFILE` and `GCHANNEL`.
  In ga68 `FILE` and `CHANNEL` are reserved words that do not denote the
  modes of `Genie_Transput` (see [genie-transput.md](genie-transput.md),
  "In UPPER stropping"); `Genie_Formats` exports these modes as `Gfile`
  and `Gchannel`, so a program that uses them accesses `Genie_Formats`
  even without formats;
- in UPPER stropping, `ANDF` and `ORF` become `ANDTH` and `OREL`, the
  spelling of ga68 for the same short-circuit operations, and `DOWNTO`
  becomes `BY -1 TO` (since version 0.8.1). Algol 68 Genie negates the
  step of a loop with `DOWNTO`, whose default is 1, so this is the same
  loop. In a loop with a `BY` part, `DOWNTO` is an error of the tool
  ("DOWNTO after BY is not supported");
- in UPPER stropping, a pragmat `PR read "file" PR` (`read` or `include`,
  in any case) becomes `PR include "file" PR`, and any other pragmat,
  such as `PR precision=100 PR`, becomes a blank with the newlines of the
  pragmat: ga68 rejects the pragmats it does not know, and it knows only
  `include` in small letters. The tool does not rewrite the file that is
  included; `-fgenie` does.

QUOTE stropping is not handled.

### Stropping

The tool reads UPPER and SUPPER stropping on every system, and the output
keeps the stropping of the input:

- the options `--upper` and `--supper` decide;
- else a first line `{ dg-options "-fstropping=..." }`;
- else the tool reads the program in both ways and counts the reserved
  words outside comments and strings (`BEGIN`, `INT`, `PROC` ... or
  `begin`, `int`, `proc` ...); the larger count wins, UPPER on a tie.

| | UPPER | SUPPER |
|---|---|---|
| comments | `# #`, `CO`, `COMMENT`, `NOTE ... ETON`, `{ }` | `{ }` |
| pragmats | `PR`, `PRAGMAT`, `ORDER ... REDRO`, `[[ ]]` | `[[ ]]` |
| written by the tool | `GFORMAT`, `INT`, `CASE`, `SKIP`, `GENIE_FORMATS` | `Gformat`, `int`, `case`, `skip`, `Genie_Formats` |

### Why the format text stays a string

The tool knows only the lexical structure of Algol 68 and the four
letters `n`, `g`, `h` and `f` that take units in a format text. The
grammar of formats is in the module alone, so later stages extend the
module, not the tool. A unit that is not a constant is passed in a
procedure, so that it is elaborated each time the format reaches it, as in
Algol 68 Genie. The cost is a parse of the text each time a format text is
elaborated.

## The option `-fformats`

`java -jar a68j-backend.jar -fformats -run prog.a68` compiles a program
written for a68j (or ga68) that contains format texts, in either stropping
(`-fstropping`, SUPPER by default). The compiler parses the format texts
itself:

- The front end recognizes `$` and the mode `format` (`FORMAT` in UPPER
  stropping), which is the mode `Gformat` of `Genie_Formats`; messages
  call it `format`.
- An access list that names `Genie_Transput` gets `Genie_Formats` as
  well if the program uses formats: a format text, the mode `format`, or
  one of `printf`, `putf`, `readf`, `getf` and `writef`. The program must
  access `Genie_Transput` itself, as any program that uses `print` does;
  a format text without access to `Genie_Formats` is an error.
- The back end lowers each format text to a call of `gfmt` or `gfmtd` of
  the module, which builds the same value that the rewrite tool builds: a
  text in the syntax that the module parses when the program runs, and,
  for the dynamic parts (`n(k)`, the parameters of `g(...)`, `f(...)`),
  procedures without parameters that yield their values. A dynamic part
  is elaborated each time the format uses it, in the environment of the
  format text. A constant parameter, as in `g(-6, 2)`, goes into the text.
- Errors in a format text are errors of the compiler, with line and
  column, as in Algol 68 Genie: syntax errors (`$+d.2d +d.2d$` needs a
  comma between the two patterns), the modes of the units (`n("x")`,
  `f(1)`), and what the module does not have (`h`, C-style patterns such
  as `%5d`, `g` with more than three parameters).
- A format text may continue over several lines, as in Algol 68 Genie:
  `$3` at the end of a line and `2d$` on the next is the replicator 32,
  and two literals on two lines are two insertions.
- A parameter of `g` that is a clause yielding a row, such as
  `g(c | 6 | 7)`, is one parameter: its row must have one element at run
  time. Parameters given as a row in another way are not supported.
- A structure, a row of rows or a `[] char` is an item of `printf` and
  `print`, and a name of a structure or of a row an item of `readf` and
  `read`, straightened into its parts (see "Differences and limits").
- `-Wframe-scope` reports a format text whose dynamic part uses an
  identifier of a loop and that outlives the iteration, as it does for a
  routine text.

The examples with formats in chapter 9 of the book are run this way.

With `A68J_FORMATS=rewrite` in the environment, `-fformats` rewrites each
source file with the tool instead, as before the compiler parsed format
texts (the tool without `--ga68`: format texts become calls of `gfmt` and
`gfmtd`, `format` becomes `Gformat`); this fallback is meant to go
away. An error that the tool finds is then an error of the compiler at
its line, and errors in a format text are found when the program runs.

With `-fgenie`, `-fformats` changes nothing.

ga68 has no such option: there the tool, built as in "Example", rewrites
the program first, without `--ga68`:

```sh
./rewrite < prog.a68 > prog-j.a68
ga68 -I. prog-j.a68 genieformats.o genietransput.o -o prog
```

## The option `-fgenie`

`java -jar a68j-backend.jar -fgenie -run prog.a68` runs a program written
for Algol 68 Genie, such as a Rosetta Code solution, as it is. It implies
UPPER stropping (`-fstropping=supper` with it is an error) and does what
the tool with `--ga68` does by hand:

- The compiler rewrites the main file and each included file with the
  tool, in the same JVM: `backend/build.sh` compiles the tool with the
  prologue `tools/formats/rewrite-fgenie.a68` into the class
  `a68j.lib.Genierewrite`, which `a68j.jvm.GenieRewrite` calls. The
  options are `--upper --ga68 --no-wrap --keep-formats` (without
  `--keep-formats` with `A68J_FORMATS=rewrite`). A CR before a newline is
  dropped first.
- The compiler parses the format texts and lowers them as with
  `-fformats` (since version 0.9.0).
- The program is put into
  `ACCESS GENIE_TRANSPUT, GENIE_FORMATS ( ... )` by the compiler, on lines
  of its own that belong to the environ, like the prelude. A file included
  by a pragmat on the first line, which goes before that line, is inside
  the access clause too, and the lines of the program keep their numbers.
- `PR read "file" PR` and `PR READ "file" PR` include the file once, the
  first time. The file is looked for relative to the file that reads it,
  then in the `-I` directories. ga68 does not find a file with a
  directory from a program given without one (entry 25 of
  [ga68-bug-reports.md](ga68-bug-reports.md)); with `-fgenie` a68j finds
  it, as Algol 68 Genie does. The same holds for a file whose path has a
  `~` in it (entry 27): with `-fgenie` a directory of the filename that
  starts with `~` is one in the home directory, and a `~` elsewhere is an
  ordinary character. On Windows a68j replaces short names such as
  `STEFAN~1` by the long ones in any case.
- Values widen between lengths, as in Algol 68 Genie (since version
  0.8.1):
  - `int` to `long int` to `long long int`, `real` to `long real`, `compl`
    to `long compl`, `bits` to `long bits`, and on to the `real` and
    `compl` modes of the longer length (`long real q := 1`).
  - ga68 follows the Revised Report here, so without `-fgenie` this is an
    error. With it, `long int x := 1` is accepted, and so is an `int`
    argument for a `long int` parameter.
  - Formulas with operands of different lengths need no operators of
    their own: `x + 1`, `x < 10` and `x +:= 1` use the operator of the
    longer mode. As in Algol 68 Genie, the front end looks for it among
    the standard operators when no operator fits the operands as they
    are.
  - A denotation that is widened has the value of its text at the longer
    length. `long int n := 3000000000` does not wrap at 32 bits, and
    `long real r := 0.1` is the `long real` nearest to 0.1, not a widened
    `real`.

Messages of the compiler and run-time errors give the lines of the source
files. Their columns can differ where the tool changed a line (a doubled
apostrophe, `GFILE`, `ANDTH`, `BY -1 TO`). An error that the tool finds
is an error of the compiler at its line.

Since version 0.9.0, `-fgenie` leaves the format texts and the mode
`FORMAT` to the compiler, as `-fformats` does (the tool runs with
`--keep-formats`): an error in a format text is found when the program
is compiled, with its line and column. With `A68J_FORMATS=rewrite` in the
environment, the tool rewrites the format texts too, as until version
0.8.1, and errors in format texts are found when the program runs; this
fallback is meant to go away. `tools/formats_tests.py` runs every program
test and Genie test both ways, and all 220 Rosetta Code solutions that
use formats give the same output and exit status both ways.

With `-fgenie` (and with `-fformats`), a structure, a row of rows or a
`[] CHAR` is an item of `print` and `printf`, and a name of a structure
or of a row an item of `read` and `readf`, as in Algol 68 Genie (see
"Differences and limits").

These remain differences to Algol 68 Genie, with or without `-fgenie`:
`long long` modes without multiple precision, the 32-bit `int` and
`real` (a `real` variable widened to `long real` keeps the precision of
its 32 bits), `_` in
tags (not significant in ga68), no QUOTE stropping, and the parts of
formats that are not supported
yet (see "Differences and limits").

The option replaces the pre-processor on the Rosetta Code page about
a68j (<https://rosettacode.org/wiki/A68j>); unlike it, it also handles
formats and `PR READ`, and it needs no second command.

## Where the parts run

| Part | Algol 68 Genie | ga68 | a68j |
|---|---|---|---|
| the rewrite tool (one source in UPPER stropping, a prologue per system) | yes | yes | yes, also inside the compiler (`-fgenie`) |
| format texts parsed by the compiler and lowered to calls of the module | (formats are built in) | no | yes (`-fformats`) |
| input of the tool | UPPER and SUPPER | UPPER and SUPPER | UPPER and SUPPER |
| the module `Genie_Formats` | no: Algol 68 Genie has no modules, and it does not need them, since it has formats | yes | yes (bundled) |
| a rewritten program | for tests only, with the module as declarations in front of it | yes, UPPER and SUPPER | yes, UPPER and SUPPER |

The tool gives the same output, byte for byte, on all three systems. Its
body `rewrite.a68` uses no `FILE` and no underscores in tags (Algol 68
Genie takes `_` as a letter, ga68 ignores it). Its prologue provides the
input, the options and the handling of errors, and writes the result:
`rewrite-a68g.a68` for Algol 68 Genie, `rewrite-a68j.a68` (with
`Genie_Transput`) for a68j and ga68, and `rewrite-fgenie.a68` for
`-fgenie`, which includes the body inside a procedure, so that each call
starts afresh, and turns an error into a result instead of an exit.

## What `Genie_Formats` provides

| Name | Meaning |
|---|---|
| `Gformat` | the mode of formats |
| `gfmt (text)`, `gfmtd (text, ints, formats)` | a format from the text of a format text, and the procedures of its dynamic parts |
| `Gfout` | the values `putf` accepts: those of `Outitem` of `Genie_Transput`, and formats |
| `putf (f, items)`, `printf (items)`, `writef (items)` | formatted output to `f`, or to `stand_out` |
| `Gfin` | the names `getf` accepts: those of `Initem` of `Genie_Transput`, names of `compl`, `long compl`, `bits` and `long bits`, rows of `int`, `real` and `string`, and formats |
| `getf (f, items)`, `readf (items)` | formatted input from `f`, or from `stand_in` |
| `Gfile`, `Gchannel` | `File` and `Channel` of `Genie_Transput`, under names that UPPER stropping can use |

The format items:

- insertions: strings, and the alignments `x` and `q` (a space), `y`
  (backspace), `l` (newline), `p` (newpage) and `k` (see "Pictures,
  choices, radix, `k`"), with replicators;
- replicators: constants and `n(...)`, for insertions, alignments,
  patterns and collections `(...)`;
- the general pattern `g`: `g` edits a value as `print` does, `g(w)` as
  `whole (x, w)`, `g(w, d)` as `fixed (x, w, d)`, `g(w, d, e)` as
  `float (x, w, d, e)`; the forms with parameters take numbers only;
- the format pattern `f(...)`: the format it yields is used in place;
- pictures, the complex pattern `i`, the choice patterns `b` and `c`, and
  radix pictures (see below).

The semantics follow Algol 68 Genie, checked against it:

- The items are straightened: a row gives its elements one by one.
- A value is edited by the next pattern; the insertions before it and the
  insertions after it, up to the next pattern, are written.
- When a format ends and values are left, it starts again. A value that
  finds no pattern at all ends the program with
  `patterns exhausted in format`; patterns that are left when the values
  end (or when the next format item comes) end it with
  `number of pictures does not match number of arguments`.
- Replicators `n(...)`, the parameters of `g` and the format of `f(...)`
  are elaborated each time they are reached.
- A format error prints `runtime error: ...` to standard error and ends
  the program with exit status 1, as errors of `Genie_Transput` do.

## Pictures, choices, radix, `k`

The rules were measured with Algol 68 Genie; the tests in
`tests/formats` (`pictures-1` to `pictures-3`, `choice-1`, `bits-1`,
`strings-1`, `align-1`, `errors-4` to `errors-14`) record them. They are
checked with Algol 68 Genie 3.13.4.

**One picture.** Frames without a comma between them form one picture,
which takes one value; insertions between its frames belong to it.
`$3d 2d$` writes 12345 as `12345`, `$3d"-"2d$` as `123-45`, `$3d l 2d$`
as `123`, a newline and `45`. A pattern of another kind starts a new
pattern (`g 3d`, `3d b`), and `3(d)` is a collection of three pictures.

| Picture | Frames | Values |
|---|---|---|
| integral | `d`, `z`, one sign `+` or `-` | `int`, `long int`, `long long int` |
| real | as integral, then `.` and `d`, `z`; `e` and an integral part for the exponent | the integers and `real`, `long real` |
| radix | `2r` to `16r`, then `d`, `z` | `bits`, `long bits` |
| string | `a` | `char` for one frame, a `string` of exactly as many characters |
| complex | a real picture, `i`, a real picture | `compl`, `long compl`, and the integers and reals (with the imaginary part 0) |

Any frame may have a replicator (`3d`, `n(k)d`) and `s` before it, which
suppresses the frame: it takes its digit or character but writes nothing
(`3sd` writes 123 as nothing).

**Digits.**

- The value must fit the digit frames; leading zeros fill the rest.
- In an integral part, `z` writes a blank for a zero as long as no other
  digit and no `d` has been written: `zdz` writes 5 as ` 05`, `3z.dd`
  writes 0.0 as `   .00`.
- After the point, `z` writes the digit, like `d`.
- An insertion among such blanks is written as blanks: `zz","zzd`
  writes 12 as `    12` and 1234 as ` 1,234`.
- A real is rounded to its fraction frames (`d.d` writes 0.96 as `1.0`).
  With `e`, the mantissa gets exactly as many integral digits as it has
  frames (`zd.de+2d` writes 0.5 as `50.0e-02`, `.3de2d` 12.5 as
  `.125e02`).

**Signs.** A `-` frame writes `-` or a blank, a `+` frame `+` or `-`.

| Picture | -5 | 5 |
|---|---|---|
| `-zzd` | `-  5` | `   5` |
| `"x"` after the sign: `-"x"2d` | `x-05` | `x 05` |
| `zz-d` (floating: `z` before the sign) | `  -5` | `   5` |
| `-zzd.dd` (a real: the sign always floats) | `  -5.00` | `   5.00` |
| `zzz+d` | `   -5` | `   +5` |

- In an integral picture and in an exponent, a sign frame without `z`
  before it is written before the next frame.
- `z` frames before the sign frame are digit positions: `zz-d` writes
  -123 as `-123`.

**Errors**, as in Algol 68 Genie:

- a value that does not fit gives `error transputting INT value` (with
  the mode of the value);
- a negative value without a sign frame gives
  `error transputting sign in INT value` (or `REAL` ...);
- a string of another length gives `error transputting [] CHAR value`;
- a value of a mode that the pattern does not take gives
  `cannot transput REAL value with integral-pattern` (`real`, `bits`,
  `string`, `boolean`, `choice`, `general`).

**Choices.**

- `b("yes", "no")` writes the first string for true and the second for
  false; `b` alone writes `T` or `F`.
- `c("a", "bb", "ccc")` writes the string of the integer 1, 2 or 3, and
  nothing for any other integer.

**`k`.** `nk` writes blanks up to the character position `n` of the line,
counted from 1, and never moves back: `$"ab"10k"x"$` writes `x` in column
10. `Genie_Transput` counts the characters of each line that it writes
(`col` of a `File`).

**Complex numbers.** A complex picture is a real picture, the frame `i`
and a real picture: `$-d.2di-d.2d$` writes `1.5 I -2.25` as ` 1.50i-2.25`.
`si` suppresses the `i`. With any other pattern a complex value is two
values, its parts, each edited by its own pattern; a `[] compl` gives its
elements. The frame `i` must follow a real picture
(`error in format text d i d at "i"`; Algol 68 Genie rejects such a format
text when it parses the program).

## Formatted input

`getf (f, items)` and `readf (items)` read with the same formats. The
rules follow the source of Algol 68 Genie 3.13.4 (`transput-formatted.c`)
and are checked against it by the tests `read-*`, `complex-2` and
`errors-15` to `errors-21` in `tests/formats`.

- Each name takes the next pattern; the insertions before it are read.
  The insertions after the last value are read at the end of `getf`
  (`$gl$` reads a value and the rest of its line). A format restarts, and
  patterns that are left are an error, as in output. Rows are read element
  by element.
- **`g`** reads as `get` does; its parameters are elaborated and ignored.
  A string ends at the end of the line (or at a character of
  `make_term`).
- **Pictures read one character for each frame.**
  - In a number, the frames of a part up to its sign frame (its sign mould)
    take a blank, `+`, `-`, or a digit, which then counts as the sign `+`;
    after the sign they take digits. `z` takes a digit or a blank (as 0),
    `d` a digit, the point frame `.` and the exponent frame `e` (not `E`).
    The characters are collected and converted as `get` converts them:
    `$-zzd$` reads `-  7` as -7, `$zz-d$` reads `  -7` as -7,
    `$2d"-"2d$` reads `12-34` as 1234.
  - A radix picture takes the digits of the radix; `16r` takes `a` to `f`
    in either case. A digit that is not one of the radix ends the program
    with `error in BITS denotation`, as in Algol 68 Genie. The radix may
    be dynamic (`n(base)r2d`).
  - `a` takes any character, a newline too: `$3a$` reads `ab`, a newline
    and `c` as one string.
  - A complex picture reads a real, the frame `i` (`i` or `I`), and a
    real. Without it, a complex name takes two patterns, one for each
    part.
- **Choices.** `c(...)` reads the longest of its strings that the input
  starts with and gives its number; the characters read after it are read
  again (`$c("a","ab","abc")$` reads `abx` as 2, and `x` is read next). If
  no string fits, a value error, and the name keeps its value (in Algol 68
  Genie it becomes uninitialized). `b("yes",
  "no")` reads one of its strings; `b` alone reads `T` or `F`.
- **Insertions are skipped, not compared**, as in Algol 68 Genie: a string
  skips as many characters as it has. `x` and `q` skip one character, `l`
  the rest of the line with the newline, `p` the rest of the page, and `k`
  moves to a position of the line (see below).
- **Events.**
  - A wrong character is the value error event of the file, and the digit
    0 is taken: `$4d$` reads `1x34` as 1034. Without a handler that
    returns true the program ends with `error transputting INT value`.
  - The end of the file inside a pattern is the logical file end event
    (`end of file reached` without a handler); so is a value error when
    the last character read was the end of the file. The handler may jump
    out of `getf`, as in chapter 9 of the book.
- A name of a mode that the pattern does not take ends the program with
  `cannot transput REAL value with integral-pattern` and the like (Algol
  68 Genie names the pattern differently, such as `int-symbol`).

`Genie_Transput` provides the procedures `gt_start_get`, `gt_peek_char`,
`gt_next_char`, `gt_unread`, `gt_at_end`, `gt_file_end` and
`gt_value_error` for this: `Genie_Formats` reads one character at a time,
gives back characters after a choice, and calls the event handlers of the
file.

## Differences and limits

- The layout procedures (`newline` ...) may be items of `putf` and
  `getf`; they are called and use no pattern. Algol 68 Genie does not
  allow them.
- With the rewrite tool (ga68, `-fgenie`), errors in a format text are
  found when the format text is elaborated, not when the program is
  compiled. With `-fformats` the compiler finds them, as Algol 68 Genie
  does.
- Plain `g` writes numbers with the widths of `print`, which depend on
  the 32-bit `int` and `real` of ga68 and a68j.
- Not supported yet: the pattern `h`. A format text that uses it passes
  the tool and ends the program with `format item h is not supported yet`;
  with `-fformats` it is an error of the compiler.
- A format whose dynamic part uses an identifier of a procedure may be
  used after the procedure has returned: the procedure of the dynamic
  part keeps the frame alive. Algol 68 Genie stops such a program with
  "value is exported out of its scope".
- A complex value (`compl`, `long compl`, `long long compl`) with a
  pattern that is not complex is two values, its parts, each edited by its
  own pattern, as in Algol 68 Genie (since version 0.8.1). `long long
  real` and `long long bits` are items as well; a picture edits a `long
  long real` with the precision of a `long real`.
- `getf` takes names of the modes of `Gfin` only: no `long long real`, no
  `long long compl`, no `long long bits`, and no structures (as in
  output). `g` does not read `bits`; a radix picture does.
- `y` in formatted input ends the program with
  `format item y is not possible in formatted input`; Algol 68 Genie ends
  it with `error transputting CHAR value` (`errors-20`).
- **Where Algol 68 Genie is defective, `Genie_Formats` differs from
  it.** The tests with a file `NAME.fixed` record this:
  - `-zz.dd` writes -0.5 as `  -.50`; Algol 68 Genie drops the sign and a
    column (`  .50`) (`fixed-1`);
  - a number 0 with only `z` frames is written as blanks; Algol 68 Genie
    drops the rest of the line (`fixed-1`);
  - `c(...)` with a `long int` gives
    `cannot transput LONG INT value with choice-pattern`; Algol 68 Genie
    says `VOID value out of bounds` (`fixed-3`);
  - `k` counts the position from the start of the line; Algol 68 Genie
    counts it from where `printf` starts, as if `print` had written
    nothing before on the line (`fixed-4`, `align-1`);
  - in input, `nk` reads up to the position `n` of the line, as in output,
    never back and never beyond the newline; Algol 68 Genie skips `n - 1`
    characters, fewer if the pattern has read some already
    (`read-fixed-1`);
  - in input, a suppressed digit frame (`sd`, `sz`) reads nothing and
    gives the digit 0; Algol 68 Genie reads a character for it and adds a
    0 before it, so that `$d sd d$` reads `123` as 1023 (`read-fixed-1`);
  - Algol 68 Genie does not read a `long bits` with a radix picture (the
    name stays uninitialized); `Genie_Formats` does
    (`tests/extra/genie-formats-read-1`).
- Radix pictures take the radixes 2 to 16, as Algol 68 Genie does; the
  Revised Report has 2, 4, 8 and 16.
- **ga68: procedures that yield a format with dynamic parts.** The
  procedure `ints` of the format is a routine text, created where the
  format text is elaborated. In ga68 a routine text lives only as long as
  the procedure call in which it was created; a format with a dynamic part
  that is made inside a procedure and used after the procedure has
  returned crashes the program. Constant parameters (`g(-8, 2)`) are not
  dynamic parts, so most formats are not affected. a68j and Algol 68
  Genie are not affected.
- **ga68: no rows in `Gformat`.** A mode exported by a module that is a
  structure with a flexible row field crashes ga68 when a program declares
  a variable of that mode (entry 24 of
  [ga68-bug-reports.md](ga68-bug-reports.md)). For this reason a format
  holds one procedure per kind of dynamic part, which selects the unit by
  its index, and not a row of procedures.
- **ga68: event handlers inside procedures.** A handler that is a routine
  text inside a procedure, such as `on_value_error (f, (ref File g) bool:
  ...)` in a procedure that opens a file, crashes the program when the
  event comes after that procedure has returned (entry 9 of
  [ga68-bug-reports.md](ga68-bug-reports.md)). Declare the handler
  outside the procedure. a68j and Algol 68 Genie are not affected.
- Structures as items: the union `Gfout` cannot cover the structures of
  a program (`print` of `Genie_Transput` has the same limit), nor rows of
  rows, `[] char` or the `short` modes. With `-fgenie` and `-fformats`
  the compiler straightens such an item into its parts, as Algol 68 Genie
  does: a structure is its fields in order, a row its elements in the
  order of their subscripts, each straightened in turn until the parts
  are of modes of the union; in `printf` each part takes a pattern. A
  name of a structure or of a row is the names of its fields or elements
  in the same way, as an item of `readf` (`Gfin`) and of `read`
  (`Initem`); a name of a flexible row is not, as in Algol 68 Genie, and
  neither is a name of a `[] char` in `readf`, which Algol 68 Genie reads
  as one string with one pattern, whereas `Gfin` has no name of such a
  row (`read` reads it one character at a time, which reads the same). A
  structure with a name or a procedure in it other than a layout
  procedure stays an error. ga68, and a68j without these options, accept
  the members of the unions only.
- Algol 68 Genie checks the scope of procedures at run time and refuses to
  put a routine text of the program into the heap. `putf` therefore keeps
  its state in a local row: formats and collections may be nested 8
  deep, which is checked.

## Run-time performance

A format text is parsed when it is elaborated; the parsed texts are kept
in a small cache, so a format text in a loop is parsed once. Measured on
a68j with one million lines of one number each:

| | Time |
|---|---|
| `print ((whole (i, 0), newline))` | 1.4 s |
| `printf (($g(0)l$, i))`, rewritten | 3.1 s |
| `printf ((f, i))` with `FORMAT f = $g(0)l$` declared once | 3.0 s |
| `printf (($g(w)l$, i))`, with the dynamic part `w` | 3.2 s |

Formatted output therefore takes a little more than twice the time of
`print`. Most of the difference is the state of `putf`, a local row of
frames that every call creates.

Pictures cost a little more than `g`, measured with `-fgenie` on the
same machine as each other, one million lines each:

| | Time |
|---|---|
| `printf (($g(0)l$, i))` | 7.4 s |
| `printf (($7zd l$, i))` | 9.8 s |
| `printf (($g(0,2) l$, i / 7))` | 9.3 s |
| `printf (($-6zd.2d l$, i / 7))` | 11.8 s |

A picture is edited in one string, which is written with one `put` up to
each alignment in it.

## Tests

`tools/formats_tests.py` runs the tests on every system given: a68j,
a68j with `-fgenie`, and a68j with `-fgenie` and `A68J_FORMATS=rewrite`
(by default), Algol 68 Genie (`--a68g`) and ga68 (`--ga68 --libdir`).

- `tests/formats/rewrite/NAME.in`: input of the tool, with
  `NAME.expected` (or `NAME.err` for an error) and optional `NAME.args`
  (`upper-ga68-keep-formats` with `--keep-formats`).
  The output must be the same on every system and keep the lines.
- `tests/formats/NAME.a68`: programs written for Algol 68 Genie, with
  their output `NAME.out` recorded by Algol 68 Genie (`NAME.err` for a
  program that must fail). The tool rewrites each program, and a68j and
  ga68 run it with `Genie_Formats`. Algol 68 Genie runs the rewritten
  program as well, with the module turned into declarations in UPPER
  stropping (`book/tools/supper_to_upper.py`): this compares the module
  with the formats of Algol 68 Genie itself, with the same widths of
  numbers. a68j also compiles the original program with `-fgenie`, with
  the rewrite and with the lowering of the format texts; `NAME.lowered.err`
  is the error expected with the lowering where the compiler finds an
  error that the rewrite leaves to the run time (`errors-21`). A
  file `NAME.fixed` marks a deliberate difference to Algol 68 Genie (see
  "Differences and limits"): `NAME.out` or `NAME.err` is written by hand,
  and the test does not run on Algol 68 Genie, whose version of the
  module cannot do `k`. In that version the procedures `gt_...` of
  `Genie_Transput` are made of the transput of Algol 68 Genie; it cannot
  call the value error handler of a file, so a test that needs it has a
  file `NAME.native`: its output is recorded with Algol 68 Genie, but the
  module version does not run it.
- `tests/formats/genie/NAME.a68`: programs for what `-fgenie` does
  besides formats (`PR read` on the first line, in the middle and nested,
  `FILE`, other pragmats), run as they are with `-fgenie` and with Algol
  68 Genie; `NAME.err` checks the line of a run-time error in the main
  file or in an included file, with `-fgenie` only. Among them are two
  solutions of our own for Rosetta Code tasks that had no ALGOL 68
  solution, `rosetta-json` ("JSON") and `rosetta-json-pointer` ("JSON
  pointer"), which read the library `json.incl.a68` with `PR read`, as
  on Rosetta Code. Their output is the same, byte for byte, with Algol 68
  Genie, with `-fgenie` and with ga68 (prepared as in
  `tools/rosetta_prep.py`, with the library in place of the pragmat).
  `straighten-1` and `straighten-read-1` have structures and rows of rows
  as items of `print`, `printf`, `read` and `readf`.
- `tests/options/genie-*.a68` (`tools/options_tests.sh`): the messages of
  the compiler with `-fgenie`, among them an error in a format text,
  found by the compiler, and one found by the tool
  (`A68J_FORMATS=rewrite`).
- `tests/options/formats-*.a68`: `-fformats` in SUPPER and UPPER
  stropping, an error found by the tool (`A68J_FORMATS=rewrite`),
  `-fformats` with `-fgenie`, errors in format texts (syntax, modes, what
  the module does not have, no access to `Genie_Formats`), a format text
  over several lines, dynamic parts and their environments
  (`formats-closure`), structures as items, and the diagnostic without
  `-fformats`. `tests/scope/formats.a68` checks `-Wframe-scope` on a
  format text in a loop, and `tests/java/formats-module.a68` a format
  exported by a module.

`tools/rosetta_prep.py` gives the Rosetta Code solutions that use
formats to the tool (see `tests/rosetta/README.md`).
