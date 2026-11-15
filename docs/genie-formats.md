# Formats: the rewrite tool and the bundled module `Genie_Formats`

ga68, and a68j with it, does not compile format texts (`$ ... $`) and has
no mode `format`. Formatted output in the style of Algol 68 Genie
(`printf`, `putf`) is made of two parts, both written in plain Algol 68:

- **The rewrite tool** (`tools/formats/`) turns the format texts of a
  program into calls of the module `Genie_Formats`. It runs on a68j, ga68
  and Algol 68 Genie.
- **The module `Genie_Formats`** (`backend/lib/genieformats.a68`) parses
  the format text at run time and edits the values. It is bundled with
  a68j, and it compiles with ga68 as an ordinary module. It accesses
  `Genie_Transput` ([genie-transput.md](genie-transput.md)), which stays
  unchanged.

This is stage 1 of the roadmap in [TODO-formats.md](TODO-formats.md):
output with insertions, alignments, replicators, collections, the general
pattern `g` and the format pattern `f`. The patterns of pictures, choices
and formatted input follow in later stages.

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

a68j and ga68 compile the result, in UPPER stropping:

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
  access are added to the first and last line);
- an apostrophe in a string denotation is doubled (in ga68 it starts a
  string break);
- in UPPER stropping, `FILE` and `CHANNEL` become `GFILE` and `GCHANNEL`.
  In ga68 `FILE` and `CHANNEL` are reserved words that do not denote the
  modes of `Genie_Transput` (see [genie-transput.md](genie-transput.md),
  "In UPPER stropping"); `Genie_Formats` exports these modes as `Gfile`
  and `Gchannel`.

Pragmats (such as `PR read "file" PR`) and QUOTE stropping are not
handled; `tools/rosetta_prep.py` takes care of pragmats for the Rosetta
Code programs.

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

## Where the parts run

| Part | Algol 68 Genie | ga68 | a68j |
|---|---|---|---|
| the rewrite tool (one source in UPPER stropping, a prologue per system) | yes | yes | yes |
| input of the tool | UPPER and SUPPER | UPPER and SUPPER | UPPER and SUPPER |
| the module `Genie_Formats` | no: Algol 68 Genie has no modules, and it does not need them, since it has formats | yes | yes (bundled) |
| a rewritten program | for tests only, with the module as declarations in front of it | yes, UPPER and SUPPER | yes, UPPER and SUPPER |

The tool gives the same output, byte for byte, on all three systems. Its
body `rewrite.a68` uses no `FILE` and no underscores in tags (Algol 68
Genie takes `_` as a letter, ga68 ignores it), and reads standard input
through the procedures of its prologue: `rewrite-a68g.a68` for Algol 68
Genie, `rewrite-a68j.a68` (with `Genie_Transput`) for a68j and ga68.

## What `Genie_Formats` provides

| Name | Meaning |
|---|---|
| `Gformat` | the mode of formats |
| `gfmt (text)`, `gfmtd (text, ints, formats)` | a format from the text of a format text, and the procedures of its dynamic parts |
| `Gfout` | the values `putf` accepts: those of `Outitem` of `Genie_Transput`, and formats |
| `putf (f, items)`, `printf (items)`, `writef (items)` | formatted output to `f`, or to `stand_out` |
| `Gfile`, `Gchannel` | `File` and `Channel` of `Genie_Transput`, under names that UPPER stropping can use |

The format items of stage 1:

- insertions: strings, and the alignments `x` and `q` (a space), `y`
  (backspace), `l` (newline) and `p` (newpage), with replicators;
- replicators: constants and `n(...)`, for insertions, alignments,
  patterns and collections `(...)`;
- the general pattern `g`: `g` edits a value as `print` does, `g(w)` as
  `whole (x, w)`, `g(w, d)` as `fixed (x, w, d)`, `g(w, d, e)` as
  `float (x, w, d, e)`; the forms with parameters take numbers only;
- the format pattern `f(...)`: the format it yields is used in place.

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

## Differences and limits

- The layout procedures (`newline` ...) may be items of `putf`; they are
  called and use no pattern. Algol 68 Genie does not allow them.
- Errors in a format text are found when the format text is elaborated,
  not when the program is compiled.
- Plain `g` writes numbers with the widths of `print`, which depend on
  the 32-bit `int` and `real` of ga68 and a68j.
- Not supported yet: the patterns of pictures (`d z s e i . + -`),
  choices (`b`, `c`), the string pattern `a`, the radix pattern `r`, `h`,
  the alignment `k`, and formatted input (`getf`, `readf`). A format text
  that uses them passes the tool and ends the program with
  `format item ... is not supported yet`.
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
- Structures are not items: Algol 68 Genie prints the fields of a
  structure, but the union `Gfout` cannot cover the structures of a
  program (`print` of `Genie_Transput` has the same limit). Neither can
  it cover rows of rows, `[] char` or the `short` and `long long` modes
  other than `long long int`.
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

## Tests

`tools/formats_tests.py` runs both kinds of tests on every system given:
a68j (default), Algol 68 Genie (`--a68g`) and ga68 (`--ga68 --libdir`).

- `tests/formats/rewrite/NAME.in`: input of the tool, with
  `NAME.expected` (or `NAME.err` for an error) and optional `NAME.args`.
  The output must be the same on every system and keep the lines.
- `tests/formats/NAME.a68`: programs written for Algol 68 Genie, with
  their output `NAME.out` recorded by Algol 68 Genie (`NAME.err` for a
  program that must fail). The tool rewrites each program, and a68j and
  ga68 run it with `Genie_Formats`. Algol 68 Genie runs the rewritten
  program as well, with the module turned into declarations in UPPER
  stropping (`book/tools/supper_to_upper.py`): this compares the module
  with the formats of Algol 68 Genie itself, with the same widths of
  numbers.

`tools/rosetta_prep.py` gives the Rosetta Code solutions that use
formats to the tool (see `tests/rosetta/README.md`).
