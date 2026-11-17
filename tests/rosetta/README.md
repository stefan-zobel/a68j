# Rosetta Code programs as tests

This directory holds all ALGOL 68 solutions from
[Rosetta Code](https://rosettacode.org/wiki/Category:ALGOL_68), prepared
for ga68 and a68j. They add larger, realistic programs to the ga68 test
suite. Every program that ga68 compiles and runs also runs on the JVM; the
two results (output and exit status) are compared.

## Origin and license

The sources come from [RosettaCodeData](https://github.com/acmeism/RosettaCodeData)
(`Task/*/ALGOL-68/*.alg` of the checkout used): 1328 solutions to 1127 tasks.
The texts of Rosetta Code are licensed under the
[GNU Free Documentation License 1.3](https://www.gnu.org/licenses/fdl-1.3.html);
the authors are recorded in the version history of the respective Rosetta
Code page (linked in the header of each prepared file).

## Contents

```
orig/<name>.a68     the solution unchanged, as on Rosetta Code
<name>.a68          the same solution, prepared for ga68, in GNU notation (SUPPER)
index.tsv           all solutions with task and result of the preparation
results.tsv         result per solution: what ga68 does, what the JVM does, note
notes.tsv           explanation of the known differences (for results.tsv)
passing.md          list of the solutions that run normally with ga68 and a68j
                    and give the same result (with links to Rosetta Code)
passing.txt         the same names, one per line (e.g. for run_jvm.py)
```

`tools/rosetta_list.py --dir tests/rosetta -o tests/rosetta/passing.md`
generates `passing.md` and `passing.txt` from `results.tsv`. The links to
Rosetta Code come from the headers of the prepared programs; with
`--rcd <RosettaCodeData>` they come from the checkout instead.

## Preparation

The solutions are written for Algol 68 Genie. `tools/rosetta_prep.py`
turns them into programs for ga68 and changes only the following:

- The program runs inside an access clause of the module `Genie_Transput`
  (`access Genie_Transput ( <solution> )`), which is bundled with a68j
  (`backend/lib/genietransput.a68`, see `docs/genie-transput.md`). ga68 has
  only the simple POSIX input and output (`puts`, `fputs`, `fgetc`,
  `fopen`); on top of it the module emulates `print`, `put`, `write`,
  `read`, `get`, `newline`, `space`, `stand in`/`stand out`/`stand error`,
  `open`, `establish`, `close`, `on logical file end`, `char in string`,
  `is digit` etc. of Algol 68 Genie. Numbers are printed as there
  (right-aligned with sign, `REAL` in floating-point form).
- An apostrophe in a string denotation is written `''` (in ga68, `'`
  starts an escape sequence).
- Pragmats are removed (e.g. `PR precision=... PR`).
- 205 solutions read library files (`PR read "primes.incl.a68" PR` and
  the like). On Rosetta Code these exist only on discussion pages and are
  not included here, so these solutions are not generated. If the files are
  placed in `include/`, `rosetta_prep.py` turns the pragmat into
  `PR include "include/..." PR`.

- A solution with formatted transput (`$ ... $`, `FORMAT`, `printf` ...)
  is given to the format rewrite tool (`tools/formats`, see
  `docs/genie-formats.md`). It turns the format texts into calls of the
  bundled module `Genie_Formats`, which the program then accesses as well
  (`access Genie_Transput, Genie_Formats (...)`), and keeps the lines. The
  tool rewrites 175 solutions; it rejects 3 that are not valid programs
  for Algol 68 Genie either, and these stay as they are.
- Finally the program is converted from UPPER to SUPPER stropping, the
  notation of GNU Algol 68 and the default of ga68 (`tools/a68supper.py`,
  see below).

Every prepared file starts with `{ dg-modules "genietransput" }`, or
`{ dg-modules "genietransput genieformats" }` for a solution with formats; for
ga68 the test scripts then compile the modules from `backend/lib` first,
a68j has them built in (`tools/modtest.py`). Line n of the solution is line
n + 8 of the prepared file.

### GNU notation (SUPPER)

The solutions on Rosetta Code are written in UPPER stropping
(`BEGIN INT max sequence length = 16; ... END`). `tools/a68supper.py`
converts them to SUPPER following the rules of the ga68 scanner, in the
style of the libga68 preludes (`standard.a68.in`, `posix.a68`):

| UPPER | SUPPER |
|---|---|
| reserved words `BEGIN`, `INT`, `PROC`, `OF` | `begin`, `int`, `proc`, `of` |
| modes declared in the program `MODE NODE = ...` | `mode Node = ...` |
| operators `ABS`, `UPB`, `OP DIVISORSUM` | unchanged, upper case |
| identifiers with spaces `max sequence length` | `max_sequence_length` |
| identifiers that are reserved in SUPPER: `out`, `in`, `file`, `word` | `out_`, `in_`, `file_`, `word_` |
| the modes `FILE` and `CHANNEL` | `File` and `Channel`, the modes of `Genie_Transput` (the reserved words `file` and `channel` of ga68 denote modes that ga68 does not provide yet) |
| comments `# ... #`, `CO ... CO`, `COMMENT ... COMMENT` | `{ ... }` |
| `a[b[i]]` | `a[b[i] ]` (in SUPPER, `]]` closes a pragmat) |

The trailing underscore is the way ga68 provides for this: it is not part
of the identifier, but it prevents the word from being read as a reserved
word. ga68 scans string denotations inside `{ ... }` comments; if a comment
contains a single `"` (or non-ASCII characters in quotes), that `"` becomes
`'`. Strings, formats and the line layout stay unchanged.

The conversion was checked with ga68 itself: for all 1123 programs the
UPPER and SUPPER versions give the same output and the same exit status;
if ga68 rejects a program, it rejects both versions, almost always with
the first error on the same line (exception: `flow-control-structures-4`,
a format for which ga68 reports the errors in a different order).

## Results (a68j 0.8.1, programs in SUPPER)

| | Solutions |
|---|---|
| on Rosetta Code | 1328 |
| prepared (without missing library files) | 1123 |
| ga68 does not compile them (Algol 68 Genie extensions, see below) | 399 |
| ga68 compiles them, but the output is not reproducible (`amb`: a scope violation, see below) | 1 |
| ga68 compiles them, but natively they run longer than 2 minutes (3 of them loop forever by design) | 7 |
| ga68 compiles and runs them | 716 |
| **of these identical to ga68 on the JVM** | **699** |
| of these compared with the corrected result in `fixed/` (a68j does not reproduce a defect of ga68, see below) | 27 |
| of these end normally (exit status 0); the others end with the same runtime error or signal as natively | 642 |
| of these the same up to rounding (`approx`) | 6 |

The solutions with formats are rewritten by the format rewrite tool (see
Preparation): of the 175, ga68 compiles 87 (5 of them since `Genie_Formats`
provides formatted input). Among them is
`knapsack-problem-unbounded`, which crashes natively (entry 26 of
[`docs/ga68-bug-reports.md`](../../docs/ga68-bug-reports.md)) and is
compared with its result in `fixed`. Three of them are the same only up to
rounding, because ga68 writes wrong digits of a `REAL` (entries 4 and 5),
`command-line-arguments` prints the path of the program, and
`y-combinator-2` crashes natively only.

The most common reasons why ga68 does not compile a solution: formats
(`FORMAT`, `printf`), the automatic widening `INT` → `LONG INT` of Algol
68 Genie, constructs that ga68 does not lower yet, and Genie procedures
that `Genie_Transput` does not provide (`system`, `on line end` ...). a68j
with `-fgenie` does the widening between lengths as Algol 68 Genie does
(since version 0.8.1): of the 420 originals that ga68 rejected with
version 0.8.0, it compiles 137 (25 before), and 47 of the 112 new ones
give the output of Algol 68 Genie 3.13.4. With `long long real`, `long
long bits` and the complex modes as items of `print` and `printf` (also
since version 0.8.1), 3 more compile: `accumulator-factory` (which Algol
68 Genie stops with a scope error), `infinity` and `sylvesters-sequence`
(both print `LONG LONG` values with the precision of Algol 68 Genie
there). ga68 now compiles `infinity` as well. With `ANDF`, `ORF` and
`DOWNTO` (also since version 0.8.1), `loops-downward-for-2` compiles too
and gives the output of Algol 68 Genie up to the width of its `INT`
fields; the five other solutions that stopped at these words stop at a
later point now. Since `Genie_Formats` provides formatted input (`getf`,
`readf`), five solutions that stopped at it compile, with `-fgenie` and
with ga68 alike, and give the same result on the JVM:
`guess-the-number`, `non-decimal-radices-convert-1`,
`non-decimal-radices-input`, `search-a-list-1` and `tokenize-a-string`.
The ten others with formatted input stop at other things (events that
`Genie_Transput` does not have, structures as items). Since
`Genie_Transput` provides `next random` and `rnd` of Algol 68 Genie
(ga68 declares both in its standard prelude, but does not implement
them), 17 more originals compile with `-fgenie`, and 15 of their
prepared versions with ga68 (the other two stop at the widening and at
`DOWNTO`). Their numbers come from the generator of ga68, which a68j has
as well: 14 give the same output on the JVM, and `long-stairs` the same
up to rounding (entry 4: ga68 writes wrong digits of a `REAL`). Of the
30 originals that stop with `clause does not yield a value` under
`-fgenie`, 29 end with a declaration, which Algol 68 Genie rejects with
the same message; in the 30th, `enumerations-2`, the rewrite tool does
not handle a conformity clause of format texts inside `f(...)` (when the
compiler parses the format texts itself, as `-fgenie` does since version
0.9.0, it gets past them and stops at a `SHORT SHORT INT` later). With
the format texts parsed by the compiler and with the rewrite tool
(`A68J_FORMATS=rewrite`), all 1328 originals give the same output and
exit status, the 220 that use formats among them. A structure, a row of
rows or a `[] CHAR` is an item of `print` and `printf` with `-fgenie`,
and a name of one an item of `read` and `readf`, straightened into its
parts as in Algol 68 Genie: 19 more originals compile (867 before, 886
now, of 1328), among them `playing-cards` and
`doubly-linked-list-element-insertion`. 10 of them give the output of
Algol 68 Genie 3.13.4, 5 the same up to rounding (4 up to the widths of
its numbers, and `matrix-multiplication` up to the sign of values near
0, which a68j writes as `-0.00` with its 32-bit `REAL`; before the
correction of entry 28 of the bug reports it also wrote `01.00` for
`1.00`), and 2 are random
(`forest-fire`, `playing-cards`); `unicode-strings` relies on the `CHAR`
of Algol 68 Genie being a byte, and `text-processing-1` needs an input
file that is not there. Most
of the others rely on the multiple precision of `LONG LONG INT` or on the
64-bit `INT` and `REAL` of Algol 68 Genie. The list is in `results.tsv`. Since `Genie_Transput` provides
reading and files (since version 0.5.0), 60 more solutions compile; 59 of them give
the same result on the JVM. With `associate` (transput to a string), 6
more compile, and all 6 give the same result.

The remaining differences (details in `notes.tsv`):

- **Rounding of the mathematical functions** (`cordic`,
  `particle-swarm-optimization`, both `approx`): ga68 computes `sin`, `tan`,
  `ln` etc. with the C library (`sinf`, `tanf` …), a68j with
  `java.lang.Math` (pure Java, deliberately without C calls). The result
  differs in the last digit; in `particle-swarm-optimization` the
  optimization method amplifies the difference to about 10⁻⁴.
- **Order of evaluation** (`tree-traversal`, `ulam-numbers`): Algol 68 does
  not specify whether the destination or the source of an assignation is
  elaborated first, nor the order of the operands of a formula. Here ga68
  follows the rules of GCC, which depend on the shape of the expression.
- **ga68 crashes, a68j does not** (`inner-classes`, `y-combinator-1`,
  `compiler-simple-file-inclusion-pre-processor`: procedures that outlive
  the procedure in which they were created;
  `successive-prime-differences`: ga68 needs more than 4.7 GB and is
  killed; `yin-and-yang`: partial parametrization, which a68j implements
  as Algol 68 Genie does, see `tests/partial`).
- **Both fail** (`self-numbers`: a row with 2·10⁹ elements).
- **80-bit `long long real`** (`infinity`): ga68 prints
  `long long max real` of its 80-bit `long long real`, a68j that of a
  double.
- **Not reproducible natively** (`amb`): the solution yields routines that
  use the parameters of an operator after the operator has returned. The
  source marks this as a scope violation, and Algol 68 Genie stops with
  a scope error. Compiled with ga68, the program reads dead stack frames
  and fails with a different bound in every run; on the JVM it prints the
  answer, `that thing grows slowly`.

`ruth-aaron-numbers` used to need too much memory on the JVM (rows of
`INT` as `Object[]` of `Integer`); with the unboxed rows it runs as
fast as natively (14.5 s, ga68 13.1 s).

## Bugs found in ga68

These four are entries 10 to 13 of
[`docs/ga68-bug-reports.md`](../../docs/ga68-bug-reports.md), checked
again with ga68 at GCC commit 3f35c3d2d116.

- A `string` value that is not a variable, united into a union of
  `string` (or `[] char`) and `[,] char`, causes an internal compiler
  error (`verify_gimple`) or becomes the `[,] char` component.
- A subscript that is a clause with statements (`v[(puts ("x"); i)]`)
  causes an internal compiler error in `a68_lower_slice`; a field
  selection on such a clause as the destination of an assignation causes
  "gimplification failed".
- `FLEX [1 : n] CHAR x` has the bounds `[1 : 0]` instead of `[1 : n]`
  (a68j reproduces this).
- `MODAB` (`%*:=`) computes with the C remainder (negative for a negative
  left operand), whereas `MOD` is correct. a68j reproduced this until
  version 0.6.0 and computes `MOD` since version 0.7.0;
  `subtractive-generator` and `execute-computer-zero` now give the right
  result, which is in `fixed/` (see below).
- `fixed` and `float` on a `REAL` give wrong digits from about the seventh
  one on, and `float` on a `LONG REAL` wrong last digits for large and
  small exponents (entries 4 and 5 of the bug reports). a68j reproduced
  this until version 0.6.0 and gives the digits of the exact value since
  version 0.7.0. 23 solutions that were identical to ga68 print other
  digits now, mostly in the last place; their results are in `fixed/`.
  In 20 of them the difference is within the tolerance of `run_jvm.py`;
  `monty-hall-problem` (`49.99%` instead of `50.00%` for 49.99499…),
  `percolation-mean-run-density` (`0.0899` for 0.08994999…) and
  `pythagoras-tree` (`34.688` for 34.6875) differ more. Five more
  solutions change without a result in `fixed/`: ga68 does not compile
  `chebyshev-coefficients`, `multiple-regression` and
  `numerical-integration-romberg-integration`, and `cordic` and
  `particle-swarm-optimization` differ from ga68 anyway (libm, above).
- `fixed` writes a "0" before the point of a value below 1 that rounds
  up to 1, e.g. `01.000` (entry 28). a68j reproduced this until version
  0.8.1; since version 0.9.0 it writes `1.000`, and the result of
  `gauss-jordan-matrix-inversion` is in `fixed/`.
- Partial parametrization (`f (3, )`) is compiled, and the program
  crashes.
- `NIL` accesses and procedures used outside their scope crash without a
  message; large local rows live on the stack.

The defects in libga68 and in ga68 found while porting, each with a small
program that shows it, are in [`docs/ga68-bug-reports.md`](../../docs/ga68-bug-reports.md).

## Running

```sh
# once: fetch and prepare the sources
git clone --filter=blob:none --no-checkout --depth 1 https://github.com/acmeism/RosettaCodeData rcd
(cd rcd && git sparse-checkout set --no-cone '/Task/*/ALGOL-68/*' '/Task/*/00-META.yaml' && git checkout)
python3 tools/rosetta_prep.py --data rcd --out tests/rosetta

# reference with ga68, then comparison on the JVM
python3 tools/run_native.py --ga68 .../xgcc --libdir .../libga68/.libs --out /tmp/native-rc \
    --timeout 10 --runs 2 --stack 1048576 tests/rosetta/*.a68
python3 tools/run_jvm.py --native /tmp/native-rc --fixed tests/rosetta/fixed --out /tmp/jvm-rc \
    --jit tiered tests/rosetta/*.a68
python3 tools/rosetta_report.py --dir tests/rosetta --native /tmp/native-rc --jvm /tmp/jvm-rc \
    --notes tests/rosetta/notes.tsv
```

Every run of a program starts in a new empty directory, natively and on
the JVM, since programs may create files. In a virtual machine with little
memory, limit the parallel runs (`-j`): a solution such as
`successive-prime-differences` takes gigabytes natively, and slow solutions
exceed the time limit when several run at once. Run the solutions that
timed out again with a longer limit (`--timeout 120 -j 1`) into a second
directory and give it to `rosetta_report.py` as a second `--native` and
`--jvm`; later directories take precedence. `--runs 2` runs every program
twice and sorts out programs whose output changes; `--stack` lets ga68
use large local rows. `run_jvm.py` compares
floating-point numbers in the output with a tolerance (`--rel-eps`, default
1e-4, `--abs-eps` 1e-6); programs that differ only in this way count as
"the same up to rounding" (`approx`), not as identical.
