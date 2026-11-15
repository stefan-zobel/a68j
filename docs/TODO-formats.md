# TODO: formatted transput (`printf`, `readf`, format texts)

Status: not started. This note records how much formats matter, what ga68
provides, the options, and a possible roadmap in stages. It is the basis
for a design to be agreed on before any work starts.

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
measurements for the reading part of `Genie_Transput`.

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
   - Only static format texts can be rewritten. Formats in variables,
     dynamic replicators and choice patterns (`b`, `c`) generally cannot,
     and the rewrite would be a Rosetta tool, not a language feature.
4. **Formats as a library without compiler support.**
   - `Genie_Transput` gets `printf (string, []Simplout)` that interprets a
     format given as a string at run time, e.g.
     `printf ("g(0)l", x)`.
   - Programs would have to be changed by hand or by a tool (option 3 with
     a much simpler rewrite: `$...$` becomes a string denotation).
     Dynamic replicators that refer to identifiers then need another
     form.

Option 4 together with the simple rewrite of option 3 is the smallest
step that keeps the native comparison. Option 1 is the complete solution
for a68j. The choice needs a decision.

## Scope of a complete implementation (option 1)

Following the Revised Report, section 10.3.4, and what Algol 68 Genie
accepts:

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
expected while the stages below are tested.

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
well, such as the widening of `INT` to `LONG INT`.

The most frequent features are:

- `l`, in 153 solutions;
- literals, in 137;
- `g`, in 135, and `g` with parameters in 73.

Input is rare: `getf` appears in 13 solutions and `readf` in one.

The book has 11 examples with formats in chapter 9 and its answers. All of
them run with Algol 68 Genie only (`run: a68g`), and their output is
recorded, so they make a ready-made test set.

## Roadmap

The roadmap follows option 1. It keeps the program's transput and the
format runtime apart, and it puts the new syntax behind a compiler option.

### Structure

**The module `Genie_Formats`, for a68j only.**

- It accesses `Genie_Transput` and provides `printf`, `putf`, `readf` and
  `getf`. Their row of items is `Outitem` or `Initem` extended with the mode
  `FORMAT`.
- `Genie_Transput` stays unchanged. It can still be compiled with ga68, as
  `docs/genie-transput.md` and the book state; `FORMAT` must not enter its
  `Outitem`.

**The core, inside the module.**

- The core edits a value according to a picture, or reads one. It also
  walks a format with its replicators and matches the items with the
  pictures.
- It works on a descriptor of ordinary Algol 68 modes: rows of pieces,
  `proc int` for dynamic replicators and parameters, and `proc` yielding a
  format for `f(...)`.
- So the core can be compiled and tested with ga68 natively, with
  descriptors written by hand.
- The general pattern `g` maps to the prelude's `whole`, `fixed` and
  `float`. `g(w, d)` therefore gives the same result as `fixed (x, w, d)`.
- The editing of pictures follows the Revised Report, sections 10.3.4
  and 10.3.5, which define it in Algol 68.

**The compiler part.**

- The front end recognizes `$`, `format` and the prelude modes only with
  the option `-fformats`.
- The back end lowers a format text into a descriptor that is built once,
  plus closures for its dynamic parts.
- Other needs:
  - `FORMAT` in `JType` and in unions;
  - a mode code for `FORMAT` in the module interfaces, since the ga68
    export format has none;
  - a rule for format texts in `FrameScope` (`-Wframe-scope`).
- `-Wscope` needs no change, because `Scope` already handles format texts.

### Stages

0. **The core alone (optional as a stage of its own).**
   - The core as an ordinary module, tested against ga68.
   - Without compiler support, a user could only reach it through a
     replacement notation, such as `edit ("zzd.dd", x)`. Programs with
     `$...$` still do not compile.
   - So this stage is a foundation rather than a feature. It is only worth
     releasing on its own together with a source rewrite (option 3) that
     keeps the native comparison with ga68 for static formats.
1. **Compiler part and output of stage 1 above.**
   - All the compiler risk is in this stage. The runtime is small, because
     `g` uses the existing conversion procedures.
   - This stage covers about 60 % of the Rosetta solutions with formats.
2. **Pictures, choice patterns, radix.** This is library work in Algol 68
   only, without compiler changes. It covers about 92 % of the solutions,
   and nearly all format examples of the book become testable.
3. **Formatted input (`readf`, `getf`).** It is rarely needed and is again
   library work only. It completes the set, including the book example
   `ch09/readf`.

### Risks

**The back end (stage 1).**

- The dynamic parts of a format text (`n(...)`, `g(...)`, `f(...)`) are
  elaborated later, in the environment of the format text, so they are
  closures.
- They must pass through every pre-pass: `Capture`, `Direct`, `Hoist`,
  `Cut`, `Outline` and `FrameScope`.
- Mitigation: lower each dynamic part as an ordinary routine text, so that
  `n(k)` becomes the equivalent of `(int: k)`. The existing, well-tested
  handling of routine texts then applies.

**The front end.**

- The format code is untested, as described above.
- Recognizing `$` departs from the 1:1 port, and the diagnostics for
  programs with `$` then differ from ga68's.
- In SUPPER stropping, `format` becomes a reserved word. No test, Rosetta
  solution or book example uses it as an identifier.

**The option `-fformats` contains the risk.**

- Without the option, a68j does not recognize `$`. Existing programs are
  compiled exactly as before, and the front end stays identical to ga68,
  diagnostics included.
- Only programs compiled with the option can be affected by defects of the
  new code.
- The option can become the default once formats have proved themselves.

**Semantics.**

- Where the Revised Report and Algol 68 Genie differ, a68j follows Algol 68
  Genie, because the programs to run (Rosetta Code, the book) are written
  for it.
- An example is a format that is not used up when the items end. In the
  Report, the rest waits for the next `putf` on the same file; Algol 68
  Genie stops the program instead.

**The reference.**

- The only reference is Algol 68 Genie.
- Pictures (`d`, `z`, ...) give output that does not depend on the
  implementation, so it compares directly.
- `g` without parameters differs in the widths and digits, because of the
  32-bit `int` and `real` of a68j, as `print` already does.

### Run-time performance

- A constant format is built once.
- Each item costs a union dispatch and the editing of its picture. The
  editing costs about what `whole`, `fixed` and `float` cost, and the output
  itself through `emit` dominates.
- So formatted output is expected to run at about the speed of `print`.
- Programs without formats pay nothing.
- A later optimization could compile a constant format with items of
  statically known modes directly into calls, without the interpreter.

### ga68

- Programs with format texts compile with ga68 in none of the stages, until
  ga68 itself provides formats. The transput of libga68 is being rewritten
  in Algol 68 (`libga68/transput.a68.in`), and formats have not been part
  of that discussion so far.
- The core, being plain Algol 68, also compiles with ga68.
- If ga68 adds formats later, a68j should follow its representation. The
  compiler part should therefore stay small, with the logic in the module.

### Tests

- A test directory, run like `tests/partial`, with outputs recorded with
  Algol 68 Genie:
  - the 11 book examples;
  - small programs for each feature, including the dynamic parts and
    formats stored in variables;
  - format texts in loops, for `-Wframe-scope`.
- The Rosetta solutions that use formats, run against Algol 68 Genie.
- With and without `-fformats`, all existing test suites must stay
  unchanged.
