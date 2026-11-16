# TODO: formatted transput (`printf`, `readf`, format texts)

Status: stages 1 and 2 of the roadmap below are implemented, the
rewrite tool and the module `Genie_Formats` with pictures, choices, radix
and `k` ([genie-formats.md](genie-formats.md)), and so are the option
`-fgenie`, which runs programs written for Algol 68 Genie as they are,
and the option `-fformats` of stage 4, which compiles the format texts of
a program written for a68j through the same tool. Stage 3 is not
implemented. This note records how much formats matter, what
ga68 provides, the options, and the roadmap in stages.

## Why formats

Formats are the most common reason why ga68 does not compile a Rosetta
Code solution (see `tests/rosetta/README.md`). Counted on the 501
solutions in `tests/rosetta/results.tsv` that ga68 does not compile
(2 Nov 2026):

| | Solutions |
|---|---|
| use a format text (`$ ... $`) or `printf`, `readf`, `putf`, `getf` | 178 |
| of these, the first error that ga68 reports concerns formats | 154 |
| of these, every error that ga68 reports concerns formats | 137 |
| declare `FORMAT` identifiers or variables | 64 |
| call `printf` / `putf` / `getf` / `readf` | 156 / 23 / 13 / 1 |

The 137 are an upper bound for what formats alone would gain. ga68 stops
after the syntax errors, so mode errors and missing procedures further on
only show once the format texts parse.

A rough token scan of the 639 format texts in the 178 programs shows what
they use. Most use `l` (new line, 160 programs), `g` (general pattern,
141) and literal insertions (137). Pictures with `d` and `z` and
exponents also come up, as do replicators computed at run time
(`n(...)`, `g(w, d)` with expressions, about 53 programs).

## What ga68 provides

Nothing that works, but the front end still contains the format code
inherited from Algol 68 Genie:

- **Present:**
  - the scanner tokenizes nested format texts (`tokenise_source` with
    `in_format`, `a68-parser-scanner.cc`);
  - the bottom-up parser reduces `FORMAT_TEXT` and picture lists
    (`reduce_format_texts`, `a68-parser-bottom-up.cc`);
  - the mode checker and the scope checker handle `M_FORMAT`.
- **Missing:**
  - the keyword table defines neither `$` as `FORMAT_DELIMITER_SYMBOL` nor
    the bold word `format`;
  - the standard prelude has no mode `FORMAT` (`a68_modes.FORMAT` is never
    set);
  - nothing lowers a format text to GIMPLE, and libga68 has no `printf`.

As a result a format text is read as ordinary tokens. A typical error:
"construct beginning with '(' … followed by '$' … is an invalid
collateral clause". a68j's front end is a port of ga68's, so it is in the
same state.

## The consequence for testing

So far a68j is tested by running every program natively with ga68 and on
the JVM and comparing the results. ga68 cannot compile a program with
formats, so this comparison is impossible for them. Measured against
Algol 68 Genie, the reference would be `a68g` alone, as in the
measurements for the reading part of `Genie_Transput`. The rewrite tool of
the roadmap below makes the native comparison possible again.

## Options

1. **Formats in a68j only.**
   - Change the a68j front end: add the `$` delimiter and the bold word
     `format` to the keyword table, add `FORMAT` to the prelude, and lower
     format texts to values of a runtime mode.
   - Add `printf`, `putf`, `readf` and `getf` to `Genie_Transput`.
   - Programs with formats then compile with a68j but not with ga68, so
     a68j accepts more than ga68 for the first time. That would have to be
     documented, and perhaps put behind an option (like `-fstropping`).
   - Tests against a68g only.
2. **Formats in ga68 (upstream).** The same work in C++, in GCC, with a
   runtime in libga68 or in an Algol 68 module. a68j would then port it,
   and the native comparison would work again. This is outside this
   project and depends on the ga68 maintainers.
3. **A source-to-source rewrite.**
   - A tool like `tools/a68supper.py` replaces format texts and the calls
     that use them by calls of `Genie_Transput` procedures (e.g.
     `printf (($g(0)l$, x))` → `print ((whole (x, 0), newline))`).
   - Both ga68 and a68j compile the result, so the native comparison
     stays.
   - Rewritten into calls of `print` and the conversion procedures, only
     static format texts can be handled. Formats in variables, dynamic
     replicators and choice patterns (`b`, `c`) generally cannot.
   - Rewritten into calls of a format library instead (option 4, with a
     format as a value of an ordinary mode), nearly every format can be
     handled; see the roadmap below.
4. **Formats as a library without compiler support.**
   - `Genie_Transput` gets `printf (string, []Simplout)` that interprets a
     format given as a string at run time, e.g.
     `printf ("g(0)l", x)`.
   - Programs would have to be changed by hand or by a tool (option 3 with
     a much simpler rewrite: `$...$` becomes a string denotation).
     Dynamic replicators that refer to identifiers then need another
     form.

The roadmap combines options 4 and 3: a format library in plain Algol 68
that interprets the text of a format text at run time, and a rewrite tool
that turns format texts into calls of it; the dynamic parts become
procedures, so they keep their meaning.
Both ga68 and a68j compile the result. Compiler support in a68j
(option 1) comes last and is optional.

## Scope of a complete implementation

Following the Revised Report, section 10.3.4, and what Algol 68 Genie
accepts. In the roadmap, `Genie_Formats` and the rewrite tool provide
this, and the mode `FORMAT` becomes the mode `Gformat` of the module:

- **Format texts and the mode `FORMAT`:** format texts as units;
  identifiers and variables of mode `FORMAT`; format texts nested with
  `f(...)`; the scope rules for format texts, which the scope checker
  already has.
- **Insertions:** `l`, `p`, `x`, `y`, `k`, `q` and literals, each with an
  optional replicator.
- **Replicators:** constant (`3x`) and dynamic (`n(expr)`), evaluated
  when the format is used.
- **Patterns:**
  - integral, real and complex pictures with `d`, `z`, `s`, sign moulds
    `+`/`-`, the point, and `e`;
  - string patterns with `a`;
  - bits pictures with radix (`2r`, `16r`);
  - boolean `b` and integral choice `c`;
  - the general pattern `g` with and without parameters, and `h`;
  - the format pattern `f`.
- **Transput:**
  - `printf`, `putf`, `readf` and `getf`, with the format as the first
    element of the row of items;
  - format exhaustion (the format starts again);
  - the events of formatted transput (`on format end`, `on value error`);
  - the association of items with patterns, including the rule that a
    format text in the item list replaces the current format.
- **Measured, not guessed:** every rule is checked against a68g, as in
  `docs/genie-transput.md`, including the error texts.

## What is missing in the a68j front end

The format code of the front end is complete up to three missing pieces:

- the keyword `$` (`FORMAT_DELIMITER_SYMBOL`), which switches the scanner
  into format mode;
- the bold word `format` (`FORMAT_SYMBOL`);
- the prelude modes `FORMAT` and `COLLITEM`. `Modes.FORMAT` and
  `Modes.COLLITEM` are never set, although `Taxes` already creates tags
  with them for format texts.

This code has never run in ga68, so defects in it are likely and must be
expected. It matters only for stage 4 of the roadmap.

## Which features the Rosetta solutions need

A scan of the 639 format texts in the 178 solutions grouped the features
into three stages. A solution counts for the first stage that covers all of
its formats. The last column counts the solutions for which the first error
that ga68 reports concerns formats.

| Stage | Features | Solutions, cumulative | first ga68 error is a format |
|---|---|---|---|
| 1 | `printf`, `putf`; values of mode `FORMAT`; insertions (`l`, `x`, `y`, `p`, `k`, `q`, literals); static and dynamic replicators; `g`, `g(...)`, `h`; the format pattern `f(...)` | 105 | 95 |
| 2 | adds pictures with `d`, `z`, `s`, `+`, `-`, `.`, `e`, `i`; `a`; the choice patterns `b(...)` and `c(...)`; radix `r` | 164 | 142 |
| 3 | adds `readf` and `getf` | 178 | 155 |

These are upper bounds. Some of these solutions fail for other reasons as
well, such as the widening of `INT` to `LONG INT`. The scan counted `k`
and `h` with stage 1; `Genie_Formats` provides `k` with stage 2, and no
solution uses `h`, which is not provided yet.

The most frequent features are:

- `l`, in 153 solutions;
- literals, in 137;
- `g`, in 135, and `g` with parameters in 73.

Input is rare: `getf` appears in 13 solutions and `readf` in one.

The book has 11 examples with formats in chapter 9 and its answers. They
used to run with Algol 68 Genie only (`run: a68g`). Since stage 2, 10 of
them also run with a68j (`-fformats`) and ga68 (through the tool), and
`book/tools/run_examples.py` checks them on all three (the key
`formats: yes`); only `ch09/readf` waits for stage 3.

## Roadmap

The intended roadmap puts all the format logic into a library in plain
Algol 68. A rewrite tool, also written in Algol 68, turns the format texts
of a program into calls of this library. The rewritten program compiles
with ga68 and with a68j alike. Compiler support in a68j comes last and is
optional.

This order puts the parts without risk for the compiler first. Each stage
is useful on its own, and the native comparison with ga68 stays possible
for programs with formats.

### Structure

The parts of stage 1 exist; [genie-formats.md](genie-formats.md) describes
them in full.

**The module `Genie_Formats`** (`backend/lib/genieformats.a68`).

- It is written in plain Algol 68 without Java, like `Genie_Transput`, so
  it compiles with ga68 as an ordinary module. It accesses `Genie_Transput`
  and is bundled with a68j. `Genie_Transput` changed only once for it:
  since stage 2 it counts the column of each line it writes, for `k`.
- It provides the mode `Gformat`, not `Format`: in Algol 68 Genie `FORMAT`
  is a reserved word, and a rewritten program must be able to run there
  too (for the tests). A `Gformat` holds the parsed format text and two
  procedures that yield the dynamic parts of a given index: one for the
  replicators and the parameters of `g`, one for the formats of `f(...)`.
  It holds no rows of procedures, because of a defect of ga68 with
  flexible rows in exported modes (entry 24 of
  [ga68-bug-reports.md](ga68-bug-reports.md)).
- It provides `gfmt` and `gfmtd`, which parse the text of a format text,
  and `printf`, `putf` and `writef`; their items are those of `Outitem` of
  `Genie_Transput` and formats. Stage 3 adds `readf` and `getf`.
- It walks a format with its replicators and matches the items with the
  patterns, with the semantics of Algol 68 Genie.
- The general pattern `g` maps to `whole`, `fixed` and `float`.
  `g(w, d)` therefore gives the same result as `fixed (x, w, d)`.
- The editing of pictures (stage 2) follows Algol 68 Genie, measured
  rule by rule; where Algol 68 Genie is defective, it follows the Revised
  Report, sections 10.3.4 and 10.3.5 (see genie-formats.md, "Differences
  and limits").

**The rewrite tool, a program in Algol 68** (`tools/formats/`).

- It reads a program in UPPER or SUPPER stropping and copies it,
  character by character, with the same lines. It recognizes comments,
  pragmats, string denotations and bold words.
- It replaces each format text by a call of `gfmt` or `gfmtd`. The string
  is the format text itself, so the grammar of formats is in the module
  only, and the tool did not change for stage 2 (only a defect was
  fixed: in SUPPER stropping it now also replaces the mode `format`).
- A unit inside `n(...)`, `g(...)` or `h(...)` that is an integer
  denotation stays in the string. Any other unit there, and the unit of
  `f(...)`, becomes `@` and an index, and an alternative of a procedure
  `(INT gfindex) INT: CASE gfindex IN ... ESAC`, so that it is
  elaborated when the format reaches it. A unit that is a clause, as in
  `f(c | a | b)`, is put in parentheses.
- It replaces the mode `FORMAT` by `GFORMAT`, and adds `Genie_Formats` to
  an access list with `Genie_Transput`. `printf` and the other procedures
  keep their names.
- The tool needs no mode information. Whether an item of `printf` is a
  format or a value is decided by the compiler, through the modes of the
  item union.
- It runs on a68j, ga68 and Algol 68 Genie with the same output, and it is
  itself a test program for a68j.
- The pre-processor on the Rosetta Code page about a68j
  (<https://rosettacode.org/wiki/A68j>, section "A pre-processor") has the
  same structure. It leaves formatted transput out. The option `-fgenie`
  (below) makes it unnecessary.
- The Python tools of the repository (`rosetta_prep.py`, the test runner
  `formats_tests.py`) call it through a68j instead of a second
  implementation in Python.

An example of the rewrite:

```
FORMAT f = $n(k) g(-6, 2), " items" l$;
printf ((f, x))
```

becomes

```
GFORMAT f = gfmtd ("n(@1) g(-6,2), "" items"" l", (INT gfindex) INT: CASE gfindex IN k ESAC, SKIP);
printf ((f, x))
```

### Stages

The feature stages are those of the table above. The rewrite tool is
complete with stage 1; stages 2 and 3 extend the module only. The option
`-fgenie` came between stages 1 and 2 (see "The option `-fgenie`"); the
stages do not depend on it, nor it on them.

1. **`Genie_Formats` and the rewrite tool, output with insertions,
   replicators, `g` and `f(...)`.** Implemented.
   - `tools/rosetta_prep.py` gives the Rosetta Code solutions with formats
     to the tool. It rewrites 175 of the 178; the other three are not
     valid programs for Algol 68 Genie either.
   - ga68 compiles 81 of the 175, and 76 of them end within the time limit.
     Of these, 71 give the same result on the JVM, and 3 the same up to
     rounding. The others: `command-line-arguments` prints the path of
     the program, and `y-combinator-2` crashes natively only (a procedure
     that outlives the procedure that created it).
   - 23 of the 76 stopped with `format item ... is not supported yet`, on
     both sides: they needed stage 2.
   - Most of the 94 solutions that ga68 does not compile fail for reasons
     that have nothing to do with formats: the widening `INT` to
     `LONG INT` of Algol 68 Genie, Genie procedures that `Genie_Transput`
     does not provide, constructs that ga68 does not lower. Structures as
     items of `printf` (Algol 68 Genie prints their fields) are not
     possible with a union of modes, as with `print`.
2. **Pictures, choice patterns, radix.** Implemented, with the alignment
   `k`.
   - This stage covers about 92 % of the solutions (164 of 178), and
     nearly all format examples of the book become testable.
   - The rules were measured with Algol 68 Genie first and recorded as
     tests (`tests/formats`); four defects of Algol 68 Genie are not
     followed (tests with `NAME.fixed`).
   - Of the 175 rewritten solutions, ga68 compiles 81, and 76 end within
     the time limit; none stops with `format item ... is not supported
     yet` any more. 71 give the same result on the JVM, 3 the same up to
     rounding, and the two others are those of stage 1.
     `knapsack-problem-unbounded` now runs further and crashes natively
     (entry 26 of [ga68-bug-reports.md](ga68-bug-reports.md)); it is
     compared with its result in `tests/rosetta/fixed`.
   - With `-fgenie`, all 175 give the same result as their rewritten
     versions.
   - The format examples of the book, chapter 9, run on a68j with
     `-fformats` and on ga68 through the tool (except `readf`): all give
     the output of Algol 68 Genie, up to the widths of `g` without
     parameters. The chapter describes formats for all three
     implementations now.
3. **Formatted input (`readf`, `getf`).**
   - Input is rarely needed. This stage completes the set (178 of 178),
     including the book example `ch09/readf`.
4. **Compiler support in a68j, the option `-fformats`.** Implemented as
   the candidate "Rewriting before the scanner" (see "Stage 4 in detail").
   - With it, a68j compiles programs with format texts directly, without
     running the rewrite tool by hand. ga68 does not compile such
     programs, so this stage adds convenience, not reach.
   - The other candidate, lowering in the back end, stays open and
     optional. It would keep the option and its behavior.

### The option `-fgenie`

Implemented; [genie-formats.md](genie-formats.md), "The option
`-fgenie`", describes it for users. a68j runs a program written for
Algol 68 Genie, such as a Rosetta Code solution, with one command:

```sh
java -jar a68j-backend.jar -fgenie -run prog.a68
```

It replaces both the manual steps of the rewrite tool and the
pre-processor on the Rosetta Code page about a68j. The latter needs two
compilations to set up, and two commands and a temporary file for each
program.

**What it does.** `-fgenie` implies `-fstropping=upper`.

| Difference to Algol 68 Genie | The pre-processor of the Rosetta Code page | `-fgenie` |
|---|---|---|
| `PR read "file" PR` | inlines the file, once per file; `read` in lower case only | the tool turns `read` and `READ` into `include`, and the front end includes the file, once per file. 38 of the 167 Rosetta solutions with `read` write `READ`. |
| other pragmats (`PR precision=100 PR`) | kept | blanked by the tool: ga68 rejects pragmats it does not know |
| `'` in string denotations | doubled | doubled by the tool (as with `--ga68`) |
| `FILE`, `CHANNEL` | `GFILE`, `GCHANNEL`, from a module of its own | `GFILE`, `GCHANNEL` by the tool, from `Genie_Formats` |
| a program needs `ACCESS` to `Genie_Transput` | wrapped in `ACCESS ... BEGIN ... END` | wrapped by the compiler (see below) |
| format texts | not handled | rewritten by the tool, as far as the stages go |

**How the tool is called.** In the compiler, the tool runs in the same
JVM, without a second process:

- The body `rewrite.a68` keeps its form, declarations and then the
  program part, and gets its input, its options and its handling of
  errors from a prologue (`next line`, `options`, `fail`). It ends with
  the declaration of the result, `rewritten`, which the prologue writes.
- The third prologue, `tools/formats/rewrite-fgenie.a68`, is a module
  that includes the body inside the procedure `genie rewrite`. Each call
  thus starts with a new state, and shares nothing with the program that
  `-run` starts afterwards in the same JVM. Its `fail` jumps to a label at
  the end of the procedure, which yields the error instead of the text.
- `backend/build.sh` compiles it into the class `a68j.lib.Genierewrite`,
  which is not in the runtime jar and not accessible from programs.
  `a68j.jvm.GenieRewrite` gets the procedure through the module class
  (`pubKeys`, `pub$v`, see `a68j.rt.Modules`) and calls it as a `Proc`.
- Redirecting the transput of the tool instead would have needed changes
  in the output path of every program. Standard input (`Posix.STDIN`) and
  standard output (`Rt.OUT`) are fixed, `posix exit` ends the JVM, and so
  do runtime errors. A runtime error in the tool itself still ends the
  compiler with status 134, like a crash of the compiler.

**Where the compiler applies it.** The front end reads source text in two
places: the main file (`Scanner.readSourceFile`) and `includeFiles`. Both
call the filter `A68.sourceFilter` (`SourceFilter`), which is null
without `-fgenie`; both hooks are a68j additions to the 1:1 port and are
marked as such.

- The tool does not wrap the program (`--no-wrap`); the scanner does. The
  include pragmat of ga68 inserts the file before the line of the
  pragmat. A wrapper on line 1 would therefore come after a file included
  by a pragmat on line 1, and the included text would be outside the
  `ACCESS` (5 of the 167 Rosetta solutions with `read`). The scanner adds
  the wrapper as two lines of the environ, after the prelude and before
  the postlude. These lines are not counted, so diagnostics keep the line
  numbers of the source.
- Included files are not wrapped.
- An included file is looked for relative to the including file, then in
  the `-I` directories. ga68 does not find a file with a directory from a
  file given without one (entry 25 of
  [ga68-bug-reports.md](ga68-bug-reports.md)); with `-fgenie` a68j finds
  it, as Algol 68 Genie does. On Windows, the directory of an included
  file is now taken at a backslash as well, as libiberty does there, so
  that a file included by an included file is found.
- A CR before LF is dropped before the rewrite: the tool would put it
  into the string that replaces a format text over several lines.
- An error found by the tool is reported by the scanner at its line of
  the file.

**A defect fixed on the way.** With `--ga68`, the tool turned `FILE` into
`GFILE`, but it added `Genie_Formats`, which exports `GFILE`, to the access
list only when the program used formats. A program with `FILE` and no
format text therefore failed with "indicant GFILE has not been declared
properly". `FILE` and `CHANNEL` now count as a need for `Genie_Formats`.

**What `-fgenie` does not change.** These remain differences to Algol 68
Genie, as with the pre-processor:

- `LONG LONG` modes without multiple precision;
- the 32-bit `INT`;
- `_` is not significant in tags;
- no QUOTE stropping;
- structures as items of `print` and `printf`.

**Stages 2 and 3 need no change to it.**

- The tool takes over the format text as a string and takes out only the
  units of `n(...)`, `g(...)`, `h(...)` and `f(...)`.
  - The pictures (`d z s e i . + -`), `a`, `k` and the radix (`16r`) are
    fixed characters.
  - The alternatives of `b(...)` and `c(...)` are insertions, that is,
    strings.
  - The only dynamic part in them is a replicator `n(...)`, which the tool
    already finds anywhere in a format text.
- The union `Gfout` already has the modes of stage 2: `bool` for `b`,
  `bits` and `long bits` for `r`, `string` and `char` for `a`.
- The tool already recognizes `readf` and `getf` for stage 3.
- `Genie_Formats` is bundled, so a release contains the stage it has
  reached. Programs that stop with `format item ... is not supported yet`
  run without a change to the compiler once the module provides the item;
  this held for stage 2.

**Order.** `-fgenie` first, then stage 2, then `-fformats` and chapter 9
of the book (all done). The format examples of the chapter rely on
pictures, so it was updated only once they ran on a68j.

**Relation to stage 4.** `-fgenie` does the second candidate of stage 4,
"Rewriting before the scanner", for programs written for Algol 68 Genie.
`-fformats` does it for programs written for a68j, with the same hooks,
without the changes of `--ga68` and in the stropping of the program:
`genie rewrite` is called with `for genie` false.

**Tests.**

- `tests/formats/rewrite`: `FILE` without formats, pragmats with and
  without `--ga68`, `--no-wrap`;
- `tests/formats`: every program test also runs with `-fgenie`, from the
  original program;
- `tests/formats/genie`: `PR read` on line 1, in the middle, twice and
  nested with `PR READ`, `FILE` without formats, other pragmats, and the
  lines of runtime errors in the main file and in an included file;
- `tests/options/genie-*`: `-fstropping=supper` with `-fgenie`, the line
  of a diagnostic after a format text over two lines, and an error found
  by the tool;
- `tests/options/formats-*`: `-fformats` in SUPPER and UPPER stropping,
  an error found by the tool, and `-fformats` with `-fgenie`.

### Stage 4 in detail

**The front end.** With the lowering in the back end, it would recognize
`$`, `format` and the prelude modes only with `-fformats` (see "What is
missing in the a68j front end"). With the rewrite, which `-fformats` does
now, the front end sees no format text.

**How a format text becomes a `Gformat` is an open design question.** The
prelude mode `FORMAT` of the front end is not the mode `Gformat` of the
module. There are two candidates:

- **Lowering in the back end.**
  - The back end lowers a format text into the same value that the
    rewritten program builds: the text, plus closures for the dynamic
    parts.
  - It needs `FORMAT` in `JType` and in unions, a way to make `FORMAT` and
    `Gformat` agree, a mode code for `FORMAT` in the module interfaces (the
    ga68 export format has none), and a rule for format texts in
    `FrameScope` (`-Wframe-scope`).
  - The dynamic parts are elaborated later, in the environment of the
    format text, so they are closures. They must pass through every
    pre-pass: `Capture`, `Direct`, `Hoist`, `Cut`, `Outline` and
    `FrameScope`. Mitigation: lower each of them as an ordinary routine
    text, which the existing, well-tested handling of routine texts then
    covers.
  - `-Wscope` needs no change, because `Scope` already handles format
    texts.
- **Rewriting before the scanner.** Implemented.
  - With `-fformats`, a68j applies the rewrite of the tool to the source
    before scanning. The tool adds `Genie_Formats` to an access list that
    names `Genie_Transput`, which the program needs anyway. `-fgenie`
    provides the hooks for this (see "The option `-fgenie`").
  - The back end does not change at all.
  - The rewrite keeps the lines of the source, so diagnostics point to the
    right line. Diagnostics name the mode `GFORMAT`, not `FORMAT`.

### Risks

**The library and the tool (stages 1 to 3).**

- They are ordinary Algol 68 programs and change neither the compiler nor
  existing programs.
- Two defects of ga68 shaped the representation of formats: routine texts
  that outlive the procedure call that created them, and flexible rows in
  modes exported by a module (see [genie-formats.md](genie-formats.md),
  "Differences and limits").

**The option `-fformats` contains the risk of stage 4.** With the
rewrite, it changes neither the front end nor the back end; the points
below about recognizing `$` apply to the lowering in the back end.

- Without the option, a68j does not recognize `$`. Existing programs are
  compiled exactly as before, and the front end stays identical to ga68,
  diagnostics included.
- Only programs compiled with the option can be affected by defects of the
  new code.
- Recognizing `$` departs from the 1:1 port, and the format code of the
  front end has never run in ga68.
- In SUPPER stropping, `format` becomes a reserved word. No test, Rosetta
  solution or book example uses it as an identifier.

**Semantics.**

- Where the Revised Report and Algol 68 Genie differ, `Genie_Formats`
  follows Algol 68 Genie, because the programs to run (Rosetta Code, the
  book) are written for it.
- An example is a format that is not used up when the items end. In the
  Report, the rest waits for the next `putf` on the same file; Algol 68
  Genie stops the program instead, and so does `Genie_Formats`.

**Lifetimes.** The procedure of the dynamic parts of a rewritten format,
`(INT gfindex) INT: CASE gfindex IN k ESAC`, must not outlive the block
of `k`. The same holds for the original format text by the lifetime rule
of the Report.

**Stropping.** The tool reads UPPER and SUPPER stropping and keeps the
stropping of the program. `rosetta_prep.py` gives it the solutions in
UPPER stropping and converts the result to SUPPER, so that the units of
the format texts are converted as well.

### Run-time performance

- A format text is parsed once: the module keeps the parsed texts in a
  small cache.
- Each item costs a union dispatch and the editing of its picture. The
  editing costs about what `whole`, `fixed` and `float` cost.
- Measured in stage 1, formatted output takes a little more than twice
  the time of `print` (see [genie-formats.md](genie-formats.md)).
- Programs without formats pay nothing.
- A later optimization in stage 4 could compile a constant format with
  items of statically known modes directly into calls, without the
  interpreter.

### ga68

- Rewritten programs compile with ga68, as do `Genie_Formats` and the
  tool. So the native comparison with ga68 works for programs with
  formats too.
- Programs with format texts themselves compile with ga68 only when ga68
  provides formats. The transput of libga68 is being rewritten in Algol 68
  (`libga68/transput.a68.in`), and formats have not been part of that
  discussion so far.
- If ga68 adds formats later, stage 4 should follow its representation.
  This is one more reason to keep the logic in the module.

### Tests

- **`Genie_Formats`:** `tests/formats`, with outputs recorded with Algol
  68 Genie, run by `tools/formats_tests.py` natively with ga68, on the JVM,
  and with Algol 68 Genie itself (with the module as declarations in UPPER
  stropping). It contains small programs for each feature, including the
  dynamic parts and formats stored in variables, and the pictures,
  choices, radix pictures and `k` of stage 2. A file `NAME.fixed` marks a
  deliberate difference to Algol 68 Genie, whose output there is
  defective; such a test does not run on Algol 68 Genie. The format
  examples of the book, chapter 9, are checked on all three
  implementations by `book/tools/run_examples.py`.
- **The rewrite tool:** `tests/formats/rewrite`, pairs of input and
  expected output in both stroppings, the same on all three systems.
- **Rosetta Code:** the solutions that use formats, rewritten and compared
  natively with ga68 like the others. The output is also checked against
  Algol 68 Genie.
  - Pictures (`d`, `z`, ...) give output that does not depend on the
    implementation.
  - `g` without parameters differs in the widths and digits, because of the
    32-bit `int` and `real` of ga68 and a68j, as `print` already does.
- **Stage 4:** format texts in loops, for `-Wframe-scope`. With and without
  `-fformats`, all existing test suites must stay unchanged.
