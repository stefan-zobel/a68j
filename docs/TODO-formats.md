# TODO: formatted transput (`printf`, `readf`, format texts)

Status: not started. This note records how much formats matter, what ga68
provides, the options, and a plan. It is the basis for a design to be
agreed on before any work starts.

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

## Plan (after the decision)

1. Decide on the option and on how a68j marks programs that ga68 cannot
   compile.
2. Write the design: the runtime representation of a format (a tree of
   pictures and replicators with thunks for dynamic parts), the front-end
   changes, and the `Genie_Transput` interface.
3. Implement and test the output side first (`printf`, `putf`), because
   156 of the 178 solutions use `printf`; then `readf` and `getf`.
4. Test with a new `tests/extra` program measured against a68g, and run
   the Rosetta solutions that use formats against a68g.
