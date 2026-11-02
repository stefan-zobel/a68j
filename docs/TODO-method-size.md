# TODO: methods too large for the JVM and for its JIT

Status, 2 Nov 2026: steps 1 and 2 of the plan are done (section 9 of
[`optimizations.md`](optimizations.md), results below); step 3 is open.
Section 7 of `optimizations.md` describes the loop methods. The
measurements in the following sections are those before steps 1 and 2.

## The limits

| Limit | Value | What happens above it |
|---|---|---|
| bytecode of one method (class file format, JVMS 4.7.3) | 65 535 bytes | the class cannot be written; a68j reports a compile-time error (before step 1: a Java stack trace) |
| `-XX:HugeMethodLimit` with the default `-XX:+DontCompileHugeMethods` | 8000 bytes | the method is never compiled, neither by C1 nor by C2: it runs in the interpreter |
| `-XX:FreqInlineSize` | 325 bytes | C2 does not inline a hot callee ("hot method too big") |
| `-XX:MaxInlineSize` | 35 bytes | the same for a callee that is not hot |
| `-XX:InlineSmallCode` | 2500 bytes of machine code | C2 does not inline a callee that is already compiled into more ("already compiled into a big method") |
| parameter slots of a method | 255 | `Outline` passes at most 200 |
| locals of a method, entries of the constant pool | 65 535 each | not reached by any program measured |
| one string constant | 65 535 bytes of modified UTF-8 | handled: the keys of a module are split (`Gen#emitModuleAccess`) |

## What a68j does today

`Outline` (section 7 of `optimizations.md`) makes every loop of the
particular program a method of its own. `Gen#compileClass` measures the
generated methods; a method above 8000 bytes, or above 65 535 bytes
(since step 1), is generated again with its outermost loops as methods,
for at most four rounds. **A method without a loop stays as it is,**
however large it is, and so does a loop body without an inner loop;
above 65 535 bytes that is a compile-time error (step 1). The code is
smaller since step 2.

## Measurements (2 Nov 2026)

### The programs of the repository

All programs of `tests/rosetta`, `tests/perf` and `tests/extra` that a68j
compiles (669 programs, 6485 methods), with the default options:

| Methods with more than | 1000 bytes | 2000 | 4000 | 8000 |
|---|---|---|---|---|
| number | 246 | 56 | 8 | 2 |

- No Rosetta program (635 compile) has a method above 8000 bytes; the
  largest is `program` of `decreasing-contiguous-subsequences` (6558).
- The two methods above 8000 bytes are the main programs of
  `tests/extra/genie-transput-read-1` (9734 bytes for 141 lines) and
  `unboxed-1` (9126 bytes for 91 lines). They contain no loop at the top
  level and run once, so the interpreter costs nothing measurable there.
- One more method was above 8000 bytes before the loops were split off:
  `r1t` of `tests/perf/big-program-2` (10 636 bytes).

### Bytes per line

From the line number tables of all these class files: 18 955 source lines
produce code, **64 bytes per line** on average; lines with a call of
`print`, `put`, `get` or `puts` produce 85. So a main program of about
1000 lines without loops reaches the 65 535 bytes.

The cost of single constructs, measured as the growth of `program()` per
copy (100 copies in the main program, `-fno-loop-methods`; `i` is a
variable in an `IntCell`, `j` a direct variable, `a` a `[1:10] int`):

| Construct | Bytes | Construct | Bytes |
|---|---|---|---|
| `i := 7` | 21 | `i := a[j]` | 164 |
| `i := i + j` | 41 | `a[j] := i` | 176 |
| `i +:= 1` | 28 | `a[j] +:= 1` | 237 |
| `i := j MOD 7` | 71 | `i := u of p` | 40 |
| `i := j OVER 7` | 53 | `i := f (j)` | 25 |
| `i := j MOD i` | 88 | `i := (c \| 1 \| 2)` | 31 |
| `x := x * 2.0 + 1.0` | 10 | `if i > j then i := 0 fi` | 48 |
| `s := s + "x"` | 45 | `s := whole (i, 0)` | 59 |
| `puts ("x")` | 17 | `puts (whole (i, 0))` | 49 |
| `print (i)` | 41 | `print ((i, j, x, newline))` | 120 |

The element access dominates. It is inline on purpose (section 3 of
`optimizations.md`): C2 does not inline helper methods into code without a
profile. It has a fast path (the row from the cell, bounds, store and
offset as fields, `instanceof int[]`) and a slow path (`Rt.deref`,
`Row.index1`, `Rt.elemI`), both in the bytecode, about 140 bytes. `MOD` is
inline with its checks for 0, -1 and the sign, also for a constant divisor.

### Generated programs

Three programs with 6000 or 400 copies of `s := (s * 7 + k) MOD 1000003`
(83 bytes each in this form):

- **A**, 6000 copies in the main program, no loop: a68j stops with
  `java.lang.IllegalArgumentException: Code length 495164 is outside the
  allowed range in program(Frame,Object[])Object` and a Java stack trace.
  Already 1500 copies (121 664 bytes) fail; ga68 compiles and runs all of
  them.
- **B**, 400 copies in a procedure without loops (29 504 bytes), called
  200 000 times;
- **C**, 400 copies in the body of a loop without an inner loop
  (29 540 bytes), run 200 000 times.

One run each, the machine busy with other work, so only the order of
magnitude counts:

| | B | C |
|---|---|---|
| ga68 `-O0` | 0.62 s | 0.59 s |
| a68j (the method runs in the interpreter) | 2.44 s | 2.26 s |
| a68j with `-XX:-DontCompileHugeMethods` | 1.34 s | 1.82 s |

### Compilation and inlining

- `-XX:+PrintCompilation` on seven long programs (proper-divisors-2,
  ruth-aaron-numbers, colorful-numbers, distribution-of-0-digits,
  percolation-mean-run-density, big-program-1 and -2): no compilation is
  skipped or bailed out.
- `-XX:+PrintInlining` on eleven long Rosetta programs: three methods of
  the programs are not inlined because they are too large
  (self-describing-numbers `r1t`, 712 bytes; colorful-numbers `r1t`,
  1865; two loop methods of increasing-gaps-between-consecutive-niven-numbers).
  self-describing-numbers with `-XX:FreqInlineSize=2000`: 10.41, 15.19,
  10.13 s against 10.97, 13.77, 10.92 s with the default, alternating; at
  most about 5 %, within the noise.

## Conclusions

1. The real risk is the hard limit, not the JIT: a straight main program
   or procedure of about 1000 lines cannot be compiled at all, and the
   error is a Java stack trace.
2. A method above 8000 bytes that is hot does not occur in the programs of
   the repository; generated code (B, C) runs about four times as long as
   with ga68 `-O0`.
3. The inlining limits cost little; smaller code helps there too.
4. Code is large mainly because of the inline element access and the
   inline `MOD` and `OVER`.

## Plan

Each step is measured on its own: method sizes and bytes per line over
the same 669 programs, the ga68 test suite, `tests/extra`, the Java and PAR
tests and all Rosetta programs with the same results as before, and the
twelve long Rosetta programs and `big-program-1`/`-2` with
`tools/perf_ab.py` (five runs, median, alternating), against the version
before the step.

### Step 1: a clear error instead of a Java stack trace (done)

If a method is still above 65 535 bytes after all rounds, report it like
other compile errors: which method (the main program, a procedure, a
loop), its source lines, its size, the limit. Small; remains useful as the
last resort after steps 2 and 3.

### Step 2: smaller code (done, except the last point)

- **`MOD`, `OVER`, `%*` and their assigning forms with a constant
  divisor:** for a divisor greater than 0 the checks for 0 and -1 are not
  needed and the sign correction is shorter (`irem`, `dup`, `ifge`, add the
  divisor). About 15 bytes instead of 50 to 71, and fewer instructions in
  hot loops too.
- **Code that runs once:** the code of the main program outside every loop
  runs at most once unless a jump goes back to a label before it. There
  the element access (and other inline fast paths) can be a call of the
  runtime with the same checks and messages, about 15 to 25 bytes instead
  of 140 to 240. This does not change the behavior and costs no time: that
  code runs once. Not for serial clauses with labels, not in procedures
  (they may be called often), not in loop methods.
- **To examine:** keeping only the fast path of the element access inline
  and making the slow path one shared call. That concerns hot code, so it
  needs the time measurements, not only the sizes.

### Step 3: more than loops as methods of their own

The general solution for both limits. In a method that is still above
8000 bytes after its loops are methods (or that has no loop):

- **The rest of a serial clause:** cut the serial clause after phrase k;
  the phrases from k + 1 on become a method that gets the frame and the
  JVM locals it uses, as a loop method does (values, and direct variables
  in the one `long[]` holder), and returns the value of the serial clause.
  Declarations in the rest stay local to it. A rest that is still too
  large is cut again, so 6000 statements become a chain of a few methods.
  Cuts only after the last label of the serial clause: a jump from the rest
  back to a label before the cut leaves the method by an exception, as a
  jump out of a loop method does today (`GenClauses#jump`), but a jump into
  the middle of another method is not possible.
- **Branches of conditional and case clauses, closed clauses:** for a large
  procedure without loops, such as the step of an interpreter with a long
  case clause, each large branch becomes a method; the dispatch stays
  small.
- **Where to cut without estimating:** a label before each phrase gives
  the exact size of each phrase in the generated code of the previous
  round (`CodeAttribute#labelToBci`); the next round cuts so that each
  part stays below 8000 bytes. The sizes stay measured, as today; an
  estimate from the number of nodes was useless (0.4 to 6.7 bytes per
  node, section 7 of `optimizations.md`).
- **Tests:** a script like `tools/loop_methods_test.sh` with the generated
  programs A, B and C, jumps across cuts in both directions, declarations
  before and after a cut, direct variables assigned in the rest, every
  kind of value of the serial clause (INT, REAL, a structure, a row, a
  union, VOID), runtime errors with their line numbers inside a rest, and a
  test mode that cuts every serial clause (like `A68J_LOOP_METHODS=every`)
  over all test suites.

### Not planned

- **Larger inlining limits:** a68j cannot set options of the JVM that runs
  the program, and the gain measured is within the noise. Smaller code
  (step 2) moves more methods below 325 bytes anyway.
- **`-XX:-DontCompileHugeMethods` as a recommendation:** C2 then compiles
  the large method by OSR again and again (section 7 of
  `optimizations.md`: 16.6 s and 3.5 s for the same program, against
  1.64 s with loop methods).

## Results of steps 1 and 2 (2 Nov 2026)

What was done: section 9 of `optimizations.md`. Besides the plan: no
check for NIL at the uses of a LOC variable without rows, the shortest
instruction for every constant, and no copy of a plain value united to a
union (the items of `print`).

The constructs of the table above, in the same programs (the copies are
in the main program, so this is code that runs once). Both columns are
the growth of all methods per copy with loop methods on; the copies
contain no loop, so that gives the values of the table above:

| Construct | before | after |
|---|---|---|
| `i := 7` | 21 | 10 |
| `i := i + j` | 41 | 19 |
| `i +:= 1` | 28 | 16 |
| `i := j MOD 7` | 71 | 20 |
| `i := j OVER 7` | 53 | 13 |
| `i := j MOD i` | 88 | 66 |
| `i := a[j]` | 163 | 42 |
| `a[j] := i` | 175 | 42 |
| `a[j] +:= 1` | 237 | 51 |
| `i := u of p` | 40 | 28 |
| `i := f (j)` | 25 | 14 |
| `if i > j then i := 0 fi` | 48 | 25 |
| `s := whole (i, 0)` | 59 | 37 |
| `puts (whole (i, 0))` | 49 | 28 |
| `print (i)` | 41 | 27 |
| `print ((i, j, x, newline))` | 120 | 71 |

The element access is compact only in code that runs once; in procedures
and loops it stays inline (152 to 235 bytes). `MOD` and `OVER` with a
constant divisor are short everywhere.

The 669 programs of the repository (the same as above):

| | before | after |
|---|---|---|
| bytes of code, all methods | 1 390 402 | 1 259 765 (-9.4 %) |
| of which `program()` | 368 807 | 316 325 (-14 %) |
| methods above 1000 / 2000 / 4000 / 8000 bytes | 246 / 56 / 8 / 2 | 213 / 47 / 5 / 0 |
| bytes per source line, all / lines of `program()` / transput lines | 63.6 / 61.9 / 85.4 | 57.0 / 52.5 / 71.4 |

The two methods above 8000 bytes are now 6881 (`genie-transput-read-1`)
and 4153 bytes (`unboxed-1`).

The generated programs of `tools/method_size_test.py`, copies of
`s := (s * 7 + k) MOD 1000003`, bytes per copy and the most copies that
fit into one method:

| where | before | after |
|---|---|---|
| main program, no loop | 81 (about 800) | 21 (about 3100) |
| body of a loop | 103 (about 630) | 19 (about 3400) |
| procedure without a loop | 133 (about 490) | 41 (about 1600) |

So the limit has moved by a factor of about 1.2 for typical main
programs (52.5 instead of 61.9 bytes per line, so about 1250 lines
instead of 1050; lines with transput dominate) and by
three to five for code like the generated programs. A main program that
is too large and contains a loop is now often compiled: the loop becomes
a method of its own after the first attempt. Only step 3 removes the
limit.

## Order

Step 1 (small), then step 2 (`MOD`/`OVER` first, then the code that runs
once), then step 3. Steps 1 and 2 are done; the limit of about 1000
lines rose by a factor of about 1.2 for typical code (results above).
Only step 3 removes it.
