# a68j: Optimizations of the JVM backend

This document explains how a68j generates fast code and why the code looks
the way it does. The reference with the class names is `docs/BACKEND.md`; the
results of the measurements are in `README.md` (stages 3e and 3f and
later).

One rule holds for everything: optimizations do not change the behavior.
The order of evaluation, the bound checks, the checks for NIL and the
error messages with their line numbers stay exactly as without the
optimization, that is, as in ga68. Every change is checked against the
ga68 test suite (471 of 472), the extra tests (26 of 26, as of 1 Nov
2026), the Rosetta programs (534 the same as ga68 and 2 the same except
for rounding) and the module tests as library jars (25 of 25).

## 1. Values without boxes

| What | Representation |
|---|---|
| INT, CHAR, BITS, short modes | `int` |
| LONG INT, LONG BITS | `long` |
| REAL / LONG REAL | `float` / `double` |
| BOOL | `boolean` |
| Rows of these modes | `int[]`, `long[]`, `float[]`, `double[]`, `boolean[]` in `Row.store` |
| Variables of these modes | a JVM local (*direct variable*), otherwise `IntCell` … with the field `v` |

**Direct variables.** A variable like `int n := 0` is kept unboxed in a
JVM local if its name is never passed on as a value. Only these uses are
allowed then:
- its value is read,
- it is the destination of an assignation (`n := …`),
- it is the left operand of an assigning operator (`n +:= 1`), also when
  the new value is used, as in `a[n +:= 1]`.

In addition, no inner procedure may use it. Otherwise the variable lives
in an `IntCell`.

## 2. Direct calls

`proc f = (int n) int: …` and `op X = …` cannot change. A call of them is
therefore an `invokestatic` of a typed method (`r1t(Frame, int) int`),
without a closure, without an argument array and without boxes. Routines
that capture no identifiers of their environment and have no labels make
no frame.

Since 31 Oct 2026 this also holds for two more kinds of calls.

**Java methods** (`nest Java`, `-fnest-java`). An identifier or operator
declared with a formal hole cannot change either. Examples:
- `proc (Intlist, int) void j_add = nest Java "a68j.jlib.IntList.add"`
- `op (int, int) int MAXJ = nest Java "java.lang.Math.max"`

A call of it is therefore an `invokestatic` of the Java method itself.
The arguments are evaluated unboxed and converted as in the bridge method
(STRING → `String`, `[] int` → `int[]`, INT → `long` …). Before, every
call went through a closure: box the arguments, `Object[]`,
`MethodHandle`, bridge method, unbox. As a value, for example as a
parameter, the identifier stays a procedure as before. This is
implemented in `Gen.javaProcs` (filled by `Capture`) and
`GenJava.directCall`.

**Procedures and operators of compiled modules.** A module also compiles
every public procedure and every public operator declared with a routine
text (`pub proc f = (int n) int: …`, `pub op +:= = …`) into a public,
typed entry method `pub$i(int) int`. The `i` is the position in
`pubKeys()`. The method elaborates the module if that has not happened
yet, and calls the typed method of the routine with the frame of the
module.

Since 1 Nov 2026 a public identifier or operator declared with a Java
formal hole of a procedure mode (`pub proc (int) int j_abs = nest Java
"java.lang.Math.abs"`) gets such an entry too. After the module is
elaborated, it converts the arguments and calls the Java method as a
direct call does (`GenJava.emitPubEntry`). Before, a program called such
a hole through the bridge method, with boxed arguments in an `Object[]`.

A program calls such procedures with `invokedynamic`, with the arguments
unboxed. The bootstrap `Modules.bsmCall` links the call site the first
time it runs:
- to `pub$i` if the module has an entry method of exactly this type;
- otherwise to the boxed procedure value, for which the arguments are
  packed into an `Object[]`. This concerns module classes of an older
  a68j and tags declared neither in a procedure declaration with a routine
  text nor with a Java formal hole, such as the identity declaration
  `pub proc (int) int f = (int n) int: …`.

After that a call costs only two `invokestatic`. `A68J_DEBUG_LINK=1`
shows how each call site was linked.

`Lists`, `Hashmaps` and all calls of `whole`, `fixed` and `float` (module
`Standard`) profit from this. For `Lists` and `Hashmaps` both come
together: program → `pub$i` → routine of the module → Java method, all
without boxes. The run time without the start of the JVM:

| | before | now |
|---|---|---|
| `tests/java/lists-speed.a68` | 0.32 s | 0.22 s |
| `tests/java/hashmaps-speed.a68` | 0.75 s | 0.52 s |

For comparison, the same programs in Java, with the start of the JVM:
`ArrayList` 0.13 s, `HashMap` 0.45 s.

A public formal hole called 20 million times from a program (`s +:=
j_abs (i - 10 000 000) MOD 7` with `j_abs` from the module of
`tests/java/modules/jholes.a68`), with compilation and the start of the
JVM: 0.81 to 0.96 s through the bridge method, 0.35 to 0.42 s through
`pub$i`. The same loop with the `ABS` of Algol 68 takes 0.39 to 0.43 s.

## 3. Code that is fast without a profile

This is the most important insight of stage 3f.

An Algol 68 program becomes **one** large method `program()`. The JVM
normally compiles a method with the optimizing JIT (C2) only after it has
been called often. But `program()` is called only once. So the JVM
compiles it by **on-stack replacement** (OSR), in the middle of the first
hot loop.

This has a problem: the code *after* that loop has never run and
therefore has no profile. For code without a profile C2 hardly ever
inlines helper methods. The log (`-XX:+PrintInlining`) then shows "low
call site frequency", "virtual call" and "too big". An access `ps[j]` in
a later loop thus stayed a chain of real calls: `Rt.deref` → `Ref.get` →
`Row.index1` → `Rt.elemI`.

That is why a68j now generates the frequent patterns so that the JIT has
nothing to inline:

- **Index computation:** the bound check and the index of `a[i]` are in
  the bytecode itself and computed in `int`. Because the index is an
  `int`, the JIT recognizes a loop counter as an array index and can save
  checks of its own. With `long` this did not work.
- **Element access:** `a[i]` reads directly from the `int[]` (after
  `instanceof int[]`). Only rows with another storage, such as fields of
  rows of structures, go through the runtime.
- **Variables:** a variable declared in the program itself is certainly
  the `Cell` or `IntCell` that its generator made. Reading, assigning,
  `+:=` and slicing therefore use its field directly. The check for NIL
  remains where a variable is NIL before its declaration (HEAP variables
  and variables with rows; section 9).
- **Division:** `OVER` and `MOD` on INT are `idiv` and `irem`. Only a
  divisor 0 or -1 calls the runtime (SIGFPE as in ga68); for a constant
  divisor greater than 0 there is no check at all (section 9).

Effect: `ruth-aaron-numbers` takes 7–8 s instead of 14.9 s (ga68 9.0 s).

## 4. Smaller optimizations

- **Conditions:** `if a < b` and `while i <= n` jump directly with
  `if_icmp…`, without making a BOOL value first.
- **Loops:** without `for`/`from`/`by`/`to` there is no hidden counter;
  this corresponds to `a68_lower_loop_clause` in ga68.
- **Generators:** `[1:n] int a` makes the row directly (`Row.make1`)
  instead of interpreting a description of the mode at run time.
- **Bounds:** `LWB a` and `UPB a` read the bound without uniting the row
  to a ROWS value.
- **Random numbers:** `random` is no longer `synchronized` and does
  without `%`. The sequence is the same as that of glibc's `rand()`, and
  `percolation-mean-run-density` takes 4 s instead of 13.5 s.

- **`string +:=` with spare room** (31 Oct 2026): see the next section.
- **No needless copies** (1 Nov 2026): an identity (`[] int d = c`) and a
  parameter get a copy of their value, so that a later change of the
  variable it came from does not change it too. But if the value itself
  comes from an identity or a parameter of a row, struct or union mode, or
  from a slice or field of one (`Circle c = circles[i]`,
  `[] int e = d[2:3]`, `f (points of p)`), then no name refers to it and
  neither side can change it. The copy is then left out
  (`GenUnits.ownedValue`, `Gen.valueConsts`). Assignations to names still
  always copy. A Java bridge that passes a row unchanged to Java copies it
  itself.
- **No SKIP value that nobody sees** (1 Nov 2026): like ga68, a68j first
  gives every identity the SKIP value when its range is entered, in case it
  is used before its declaration. For rows, structs and unions this value
  has to be built first. That is left out if
  - the range has no labels (ga68 allows `goto` across a declaration),
  - no inner routine captures the identity,
  - and it does not occur in the text before its declaration
  (`GenDecls.skipNotNeeded`). Then every use is preceded by the
  declaration.
- **Copy of a row of structs:** a one-dimensional row is copied element
  by element directly instead of through index arrays (`Row.copy`).
- **`k UPB a`, `k LWB a`** read the bound without uniting the row to
  `ROWS` each time (one `Union` per call).

- **Counted loops:** `for i [from a] to b do … od` with step 1 now has the
  form that the JIT recognizes as a counted loop. Before, every iteration
  checked the sign of the step and an overflow. Now the loop looks like
  this:
  ```
  i := a; to := b; if i > to: end
  top: body; if i >= to: end; i := i + 1; back to top
  ```
  `i >= to` after the body also covers the overflow (at `i = max_int`),
  so the behavior stays that of ga68 (`GenClauses.countedLoop`, test
  `tests/extra/for-loop-1.a68`). Loops with another step keep the general
  form.

| program | before | now | ga68 `-O0` | ga68 `-O2` |
|---|---|---|---|---|
| `solve-triangle-solitaire-puzzle` | 0.58 s | 0.25 s | 0.14 s | 0.09 s |
| `total-circles-area` | 0.31 s | 0.14 s | 0.05 s | 0.01 s |

(measured one at a time, with the start of the JVM of about 0.06 to
0.1 s)

`tests/extra/value-sharing-1.a68` checks against ga68 that all values stay
as they were: also with a jump across a declaration and with a use before
the declaration.

### Appending to strings: `s +:= x`

Until then `s +:= x` built a new row of length n + m with `Std.$plus_s_s`
and stored it. So every append cost O(n); appending `s +:= "x"` 100 000
times took 6.3 s. Now `STRING +:= STRING` and `STRING +:= CHAR` go to
`Std.append_s_s` and `append_s_c` (`Intrinsics.applyAssigning`, for
variables in JVM locals as well as for names). These grow like
`java.util.ArrayList`:

- **Spare room:** the row that `append_s_s` returns lies over an `int[]`
  that is half as large again as needed. Its field `Row.spare` says: the
  room behind my last element belongs to me.
- **Appending in place:** if the old row has `spare` and enough room,
  `append_s_s` writes the new characters behind its end. It gives the new,
  longer row (same array) `spare` and takes it from the old one.
  Otherwise it copies into a new, larger array.
- **Why this is safe:** no other row descriptor over the same array
  reaches behind the end of the row with `spare`. Slices, older values and
  copies end before it. Their elements are not changed, so they see the
  same value as before. Exactly one descriptor has `spare`; every other
  one does not and copies when appended to. Assignations and identity
  declarations copy anyway (`Rt.copyValue`; `Row.copy` does not take over
  the spare room), and names of elements of a FLEX row cannot be stored
  (ga68: "attempt at storing a transient name").
- **Bounds:** as with `+`, the result has the bounds `[1 : n + m]`. A row
  with another lower bound, with a stride ≠ 1 or without an `int[]` is
  copied at the first append and grows in place after that.
- **Memory:** at most half the length plus 4 characters of spare room per
  string that was appended to. Copies are compact.

| | before | after |
|---|---|---|
| `s +:= "x"` 100 000 times (with the start of the JVM) | 6.3 s | 0.39 s |
| `s +:= whole (i MOD 7, 0)` 1 000 000 times, `t +:= "ab"` / `"c"` 400 000 times | — | 0.68 s |

ga68 itself does not free the memory of old strings. In the attempt with
100 000 appends it was killed by the system (out of memory).

Checked with `tests/extra/string-append-1.a68` (same output as ga68): old
values, copies, slices, rows of strings, `s +:= s`, `s +:= s[i:j]`,
`s +:= ""`, slices with bounds ≠ 1, appending through `ref string`
parameters.

**Other FLEX rows:** for `flex [] int` and the like Algol 68 has no `+:=`.
A program writes, for example,
`[UPB a + 1] int b; b[: UPB a] := a; b[UPB b] := i; a := b`, and the
language demands this copy. That is what the lists of the module `Lists`
are for (`docs/java-modules.md`). One could still, for `a := b` with a
local row `b` that is dead afterwards, take over its array instead of
copying it. That saves half, but stays O(n) per append.

## 5. Start

Short programs lose mostly at the start of the JVM. The AOT cache of JDK
25 saves about 0.2 s per program (see `docs/libraries-and-startup.md`).

## 6. Measuring

### How to measure correctly (experience of 1 Nov 2026)

The first measurement of all Rosetta programs with `run_jvm.py --jit
tiered -j 4` gave factors of up to 25 against ga68. That was almost
entirely an error of measurement, for two reasons:
- **JVMs in parallel:** four JVMs at the same time hinder each other a
  lot, above all the JIT threads.
- **Compilation measured too:** `-run` includes the compilation (0.1 to
  0.5 s).

Correct is:

```sh
# 1. compile all programs (this may run in parallel; the module
#    Genie_Transput they use comes with the jar)
ls tests/rosetta/*.a68 | JAR=backend/a68j-backend.jar OUT=/tmp/perf xargs -P 8 -n 1 sh tools/perf_compile.sh
# 2. run them one after the other and save the times
python3 tools/perf_run.py backend/a68j-backend.jar /tmp/perf /tmp/perf.json
# 3. compare two versions alternately (5 runs each, the minimum counts;
#    STAT=median for the median, see section 7)
A_JAR=old.jar A_DIR=/tmp/perf-old B_JAR=new.jar B_DIR=/tmp/perf python3 tools/perf_ab.py name1 name2 ...
```

ga68 likewise (first compile everything in parallel, then run one at a
time):

```sh
python3 tools/run_native.py --ga68 …/xgcc --libdir …/libga68/.libs --out /tmp/nat-O2 \
    --stack unlimited -j 2 --sequential-runs --cflags=-O2 tests/rosetta/*.a68
```

Even so the machine varies: two complete runs of the same version once
differed by 4 s (68 s against 72.8 s, median +10 ms per program).
Differences of single programs should therefore always be confirmed with
`perf_ab.py`.

**Result** (1 Nov 2026): 528 Rosetta programs for which a68j, ga68 `-O0`
and ga68 `-O2` have the same exit status. All were measured one at a time
and one after the other, ga68 too (`run_native.py --sequential-runs
--cflags=-O2`):

| | sum |
|---|---|
| ga68 `-O0` (the references of the tests are compiled like this) | 30.2 s |
| ga68 `-O2` | 16.7 s |
| a68j with the start of the JVM | 71.3 s |
| a68j without the start of the JVM (about 0.08 s per program) | about 29 s |

For the 22 programs that run at least 0.3 s with ga68:

| | sum | median of the ratios |
|---|---|---|
| a68j with the start of the JVM | 14.3 s | |
| ga68 `-O0` | 23.5 s | a68j / `-O0`: 0.82 |
| ga68 `-O2` | 13.2 s | a68j / `-O2`: 2.2 |

So a68j is about as fast as ga68 without optimization, and mostly faster
for longer programs; against ga68 with `-O2` it takes a good twice as long
in the median. The reference times in `native-rs` that I compared with at
first (52.5 s) had themselves been measured in parallel and were therefore
too high.

What remains:
- **Start of the JVM:** about 55 ms. The AOT cache of the jar saves only
  about 15 ms of it when running.
- **Tight loops:** for loops over millions of elements the JIT compiled
  the one large method `program()` again by OSR for every loop and threw
  it away afterwards ("uncommon trap", see section 3). Loops now become
  methods of their own; see section 7 for what that does and does not
  achieve.

### Tools

```sh
python3 tools/run_jvm.py --jit tiered -j 1 --native <ga68 results> --out <dir> programs.a68
```

`--jit tiered` turns on the full JIT (otherwise only C1), `-j 1` runs the
programs one after the other. The time is in `<dir>/<name>.json`. On the
development machine long runs without a change of the code vary by about
±15 %. Comparisons are therefore only worth it for large differences or
with several runs.

To find the hot spots, JFR is useful
(`-XX:StartFlightRecording=filename=x.jfr,settings=profile`, then
`jfr print --events jdk.ExecutionSample x.jfr`); for questions of inlining,
`-XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation
-XX:+PrintInlining`. Careful: JFR sometimes attributes samples in code
compiled by OSR to neighboring calls that do not run at all.

## 7. Loops as methods of their own (1 Nov 2026)

### The problem

HotSpot does not compile a method with more than 8000 bytes of bytecode
at all, neither with C1 nor with C2 (`-XX:HugeMethodLimit=8000` with the
default `-XX:+DontCompileHugeMethods`). Such a method runs in the
interpreter for as long as the program runs. Before this change a68j
compiled the particular program into one method `program()` and each
routine text into one method, so a long main program, or a long
procedure, with the hot loops inside it, was never compiled.

The second, smaller problem is the one of section 3: `program()` runs
once, so its loops are compiled by on-stack replacement (OSR), each time
the whole method, with no profile for the code after the hot loop.

### What a68j does now

`backend/src/main/java/a68j/jvm/Outline.java`:

- **Loops of the particular program:** every loop clause of the
  particular program becomes a private static method `loopN` and is
  called where it stood; an inner loop becomes a method that the method
  of the outer loop calls. (Until the examination below only the loops
  not inside another loop were methods; that is still available as
  `A68J_LOOP_METHODS=outer`.)
- **Methods that are too large:** `Gen.compileClass` generates the class,
  measures the bytecode of every method, and if a method has more than
  8000 bytes, generates the class again with the outermost loops of that
  method as methods of their own. A loop method that is still too large
  gets the same treatment for its inner loops. A method still too large
  after that gets the compact element access (section 10), and after
  that its serial clauses are cut into parts that are methods of their
  own (section 11); at most sixteen rounds in all. Nothing is estimated:
  the sizes are those of the generated code. (A
  first attempt to estimate the size from the number of nodes in the tree
  was useless: the bytes per node of the 2270 methods of the Rosetta
  programs ranged from 0.4 to 6.7.) Measurements and the plan for methods
  without loops: [`TODO-method-size.md`](TODO-method-size.md).
- **What the loop method gets:** the frame of the enclosing method and
  the value of every JVM local the loop uses. Identities and the names of
  variables never change, so a copy is enough. An own variable is passed
  with the exact class of its location (`Cell`, `IntCell`, ...), not as
  `Ref`. Variables kept unboxed in JVM locals (`Direct`) that the loop
  assigns are passed together in one `long[]` (float and double as their
  raw bits); their values are copied back when the loop ends, also when it
  is left by a jump or a runtime error. The loop is compiled exactly as
  before, in the same routine, so frames, jumps and labels work as in the
  enclosing method.
- **Switches:** `-fno-loop-methods` turns all loop methods off (the
  compact element access of section 10 remains). For experiments
  the environment variable `A68J_LOOP_METHODS` selects `none`, `outer` (only
  the outermost loops of the particular program) or `all` (the default);
  `A68J_DEBUG_LOOPS=1` reports the methods found too large.

### Measurements

**A long main program,** `tests/perf/big-program-1.a68`: 14 small kernels
(sieve, Collatz, matrix product, N queens, …) one after the other in the
main program, 10 234 bytes of bytecode for `program()` before the change.
`tests/perf/big-program-2.a68` is the same in a procedure called once
(10 115 bytes). Two runs each, full JIT, times with the start of the JVM;
the output is the same as that of ga68:

| | big-program-1 | big-program-2 |
|---|---|---|
| a68j before (`program()` / `r1t` never compiled) | 41.0 s, 41.3 s | 41.4 s, 41.4 s |
| a68j before, with `-XX:-DontCompileHugeMethods` | 16.6 s, 3.5 s | |
| **a68j now** (largest method 3194 / 3104 bytes) | **1.64 s, 1.73 s** | **1.64 s, 1.73 s** |
| ga68 `-O0` | 2.84 s | |
| ga68 `-O2` | 0.89 s | |

Even with huge methods allowed (second row) the old code is slow and
erratic: C2 compiles the large method by OSR again and again.

**Longer runs:** big-program-1 with `scale = 40` instead of 4 (the bubble
sort alone then makes 5·10⁹ comparisons; since 1 Nov 2026 in the
repository as `tests/perf/big-program-1-long.a68`), one run each, the same
output everywhere:

| | time |
|---|---|
| a68j now (B, the default) | 32.6 s |
| a68j with all loops of the program as methods (C) | 31.5 s |
| a68j `-fno-loop-methods` with `-XX:-DontCompileHugeMethods` | 44.1 s |
| ga68 `-O0` | 88.0 s |
| ga68 `-O2` | 27.6 s |

(a68j `-fno-loop-methods` with the default JVM options was not run: at
`scale = 4` it took 25 times as long as now.) On a long run a68j is now
within 20 % of ga68 `-O2`, and still a third faster than with huge
methods allowed but compiled by OSR.

**The Rosetta programs** that run longer than 1 s (none of them has a
method larger than 8000 bytes, so only the first rule applies). This is
the first measurement, before the examination below (which supersedes
it). Three runs each, alternating, the minimum counts (`tools/perf_ab.py`);
A = before, B = outer loops of the program (then the default), C = all
loops of the program:

| program | A | B | C |
|---|---|---|---|
| proper-divisors-2 | 13.92 | 12.84 | 9.52 |
| composite-numbers-k-… | 10.41 | 10.66 | 10.00 |
| distribution-of-0-digits-in-factorial-series | 8.04 | 9.70 | 11.73 |
| fermat-pseudoprimes | 7.97 | 7.93 | 7.88 |
| self-describing-numbers | 6.94 | 6.89 | 7.00 |
| ruth-aaron-numbers | 6.51 | 4.85 | 4.89 |
| rhonda-numbers | 5.35 | 5.32 | 5.44 |
| colorful-numbers | 3.80 | 3.71 | 3.20 |
| increasing-gaps-between-consecutive-niven-numbers | 3.38 | 3.14 | 3.73 |
| percolation-mean-run-density | 3.20 | 3.28 | 3.41 |
| long-primes | 2.04 | 2.09 | 2.05 |
| iterated-digits-squaring | 1.24 | 1.33 | 1.43 |
| **sum** | **72.8** | **71.7** | **70.3** |

For these programs the effect is small on the whole and goes both ways
for single programs; repeated runs of the same version differ by up to
10–15 % on this machine (colorful-numbers: 2.91 s and 3.80 s for A in two
series). Clear cases:

- ruth-aaron-numbers gets 25 % faster with B and C.
- distribution-of-0-digits-in-factorial-series gets 20 % slower with B
  and 45 % with C. The bytecode of its hot inner loop is the same in all
  three versions, and there is no deoptimization in B or C
  (`-XX:+PrintCompilation`). Turning off loop predication, unrolling,
  partial peeling or unswitching one at a time does not change the time.
  The cause was found with the machine code, see the next section.

### Examined with hsdis (1 Nov 2026)

`tools/build_hsdis.sh` builds the disassembler plugin of HotSpot from
the OpenJDK sources with Capstone (Ubuntu's `libhsdis0-fcml` only has the
interface of JDK 8 and gives hex dumps). With it,
`-XX:CompileCommand=print,Class::method` shows the machine code of C2.
In all cases below the hot loop runs in an OSR compilation, in A inside
`program()`, in B inside the loop method: so it was OSR against OSR, not
OSR against an ordinary compilation.

1. **The type of the parameters.** distribution-of-0-digits: the inner
   loop accesses the row `rfs` through its variable. The bytecode of an
   access is `checkcast Cell; getfield value; checkcast Row` in both
   versions. In `program()` C2 knows from the type flow from `new Cell`
   on that the local is a `Cell`, and the first `checkcast` is gone; in
   the loop method the variable was a parameter of type `Ref` (abstract),
   so each access in the loop had a class check of the `Cell` and a
   reload of the reference from the stack. C2 trusts the declared types
   of parameters, and `Cell` is final: the parameter now has the exact
   class (`Gen#ownCell`). B: 9.9 s → 9.0 s (A 8.2–8.8 s).
2. **The holders.** The rest was register pressure: the method kept the
   frame, the two cells and the four arrays of length 1 for `trail`,
   `first`, `total` and `rfs_len` live during the whole loop (the arrays
   are written at its end); the cells were then kept on the stack instead
   of in registers. With every loop a method (`all`) the inner `while`
   loop got five holders, and its code was full of moves between general
   and xmm registers (the oop map at the safepoint of the loop listed six
   live references). Now all assigned variables travel in one `long[]`:
   `all` 10.4 s → 8.45 s, as fast as without loop methods.
3. **OSR in the middle of a nested loop.** proper-divisors-2 is a sieve,
   `for i … do for j from i + i by i … do pdc[j] +:= 1 od od`, inside a
   `to 2 do` loop. With only the outermost loop as a method (`outer`) C2
   compiles that method by OSR entered at the inner `j` loop, with the
   `i` loop around it in the same compilation: 14–16 s. With every loop a
   method the `j` loop is a method called 64 million times, which C2
   compiles as an ordinary method with a full profile: 10.8–11.8 s. This
   is why all loops of the main program are methods now.
4. **A spill.** iterated-digits-squaring got slower (1.29 s → 1.45 s)
   although its code got shorter (289 instead of 305 instructions, the
   class checks gone): C2's register allocator keeps the variable `rest`
   of the inner loop in a stack slot, a store and three loads on the
   dependency chain of each iteration. That is chance in the register
   allocation, not missing information; nothing to do in a68j.

**How to measure:** several of these programs are bimodal, a run lands
in a fast or a slow state of the JIT: proper-divisors-2 with `outer` once
took 10.2 s and then six times 14–15 s; colorful-numbers takes 2.7–2.9 s
or 3.7–3.9 s. The minimum of three runs, used for the first table, then
depends on luck. `tools/perf_ab.py` now has `STAT=median` and `LOG=FILE`
(all times as JSON). Five runs each, alternating, median; A = no loop
methods (`-fno-loop-methods`), E = outermost loops, one holder, exact
types; F = all loops (the new default):

| program | A | E | F |
|---|---|---|---|
| proper-divisors-2 | 14.98 | 15.42 | 10.91 |
| composite-numbers-k-… | 10.52 | 11.27 | 10.37 |
| distribution-of-0-digits-in-factorial-series | 8.35 | 8.92 | 8.47 |
| fermat-pseudoprimes | 8.15 | 8.25 | 8.13 |
| self-describing-numbers | 6.95 | 7.06 | 7.21 |
| ruth-aaron-numbers | 6.70 | 4.90 | 4.80 |
| rhonda-numbers | 5.38 | 5.38 | 5.40 |
| colorful-numbers | 3.63 | 2.87 | 3.69 |
| increasing-gaps-between-consecutive-niven-numbers | 3.52 | 3.46 | 3.50 |
| percolation-mean-run-density | 3.26 | 3.40 | 3.36 |
| long-primes | 2.14 | 2.07 | 2.11 |
| iterated-digits-squaring | 1.29 | 1.45 | 1.48 |
| **sum** | **74.87** | **74.47** | **69.43** |

The ranges of the five runs overlap for self-describing-numbers and
percolation-mean-run-density; colorful-numbers is bimodal in E and F
(A is always slow). The long programs of the previous section are
unchanged (big-program-1 1.56 s, the run with `scale = 40` 33.3 s against
33.0 s before).

**Still open then:** in all versions the inner loop of
distribution-of-0-digits loaded `Cell.value`, the `Row` and its fields
(bounds, stride, offset, the `int[]`) anew in each access, with a class
check of the `Row` and of the `int[]` each time; C2 does not move these
loads out of the loop. Done in section 8: the descriptor now stays in
locals during the loop.

### Compared with ga68 (1 Nov 2026, after stage 3p)

The long programs measured again against ga68, with the default `all`.
The three versions run alternately; times are without compilation, those
of a68j include the start of the JVM. The output (the 14 result lines) is
the same for all three:

| | big-program-1 (`scale = 4`, median of 5) | big-program-1-long (`scale = 40`, median of 3) |
|---|---|---|
| a68j | 1.84 s | 36.8 s |
| ga68 `-O0` | 2.97 s | 97.6 s |
| ga68 `-O2` | 1.05 s | 30.1 s |

On the long run a68j takes 22 % longer than ga68 `-O2` and is 2.6 times
as fast as ga68 `-O0`; the ratio to `-O2` is the same as in the first
measurement above (32.6 s against 27.6 s). All three times are about
10 % higher than then: the cloud machine was slower that day, not the
code. On the short run a68j takes 75 % longer than `-O2`; part of that is
the start of the JVM and the time until C2 has compiled the loops.

**ga68 needs a larger stack:** with the usual limit of 8 MB
(`ulimit -s 8192`) the programs compiled by ga68 crash with a segmentation
fault in the last kernel (the divisor sieve), at both scales and also
big-program-2, after printing the other 13 lines. Their time then looks
shorter than it is. ga68 therefore runs with `ulimit -s unlimited`; a68j
does not need it, since it keeps rows on the heap. Always compare the
output, not only the time.

### How to measure

```sh
# a program: compile once, run several times (the JVM start is included)
java -jar backend/a68j-backend.jar tests/perf/big-program-1.a68 -d /tmp/bp
time java -XX:+UseSerialGC -cp backend/a68j-backend.jar:/tmp/bp BigProgram1
# the same without loop methods
java -jar backend/a68j-backend.jar -fno-loop-methods tests/perf/big-program-1.a68 -d /tmp/bp0
# method sizes of the generated classes (8000 is the limit of the JIT)
java tools/MethodSizes.java -min 4000 /tmp/bp/*.class
# Rosetta programs, three versions: VARIANTS=A,B,C with A_JAR, A_DIR, B_..., C_...
RUNS=5 STAT=median LOG=/tmp/times.jsonl VARIANTS=A,B,C ... python3 tools/perf_ab.py name1 name2 ...
# machine code of C2 (build the plugin once with tools/build_hsdis.sh)
LD_LIBRARY_PATH=$HOME/hsdis java -XX:+UnlockDiagnosticVMOptions \
  -XX:CompileCommand=print,BigProgram1::loop3 -XX:PrintAssemblyOptions=intel \
  -cp backend/a68j-backend.jar:/tmp/bp BigProgram1 > asm.txt
# ga68 for comparison (GCC build tree in $B, libga68 in $L); a larger stack
# is needed, see "Compared with ga68"
ga68 -B$B/gcc -B$L -L$L/.libs -O2 tests/perf/big-program-1-long.a68 -o /tmp/bpl
(ulimit -s unlimited; LD_LIBRARY_PATH=$L/.libs time /tmp/bpl)
```

## 8. Row descriptors kept in locals during a loop (1 Nov 2026)

### The problem

An element access `a[i]` of a one-dimensional row of a primitive mode is
already inline, without calls (section 3, docs/BACKEND.md section 1). But for a
row variable it still read, in every access:

1. `Cell.value` (the row in the variable) and a class check that it is a
   `Row`,
2. the fields `lwb0`, `n0` and `stride0` for the bound check and the index,
3. the field `store` and a class check that it is an `int[]` (or `long[]`
   ...), and the field `offset`,

and then the JVM's own range check of the array. C2 did not move any of
this out of the loop. The machine code of the inner loop of
distribution-of-0-digits (three accesses to `rfs` and `zc` per iteration)
shows it three times per iteration: a load of `Cell.value`, `cmp` with the
class word of `Row`, the three fields, `imul` with `stride0`, `cmp` with
the class word of `int[]`, and the array length check; several values of
the loop lived in xmm registers for lack of general registers.

### What a68j does now

`backend/src/main/java/a68j/jvm/Hoist.java`. On entry of a loop clause
(`GenClauses.loopInline`, so also in loop methods) a68j reads the
descriptor of each row that the loop subscripts and cannot change into
JVM locals: the row, its typed store (`int[]` ..., or null), `lwb0`,
`offset`, and `n0`, set to 0 unless the stride is 1 and the store is an
array of the element type. An access is then

```
i = sub - lo;  if (i < 0 || i >= n) -> old path;  arr[off + i]
```

The old path loads the row as before and calls `Row.index1`, which
reports the bound error (or `Rt.deref` reports NIL); for a row with another
stride or an `Object[]` store it computes the index, and the element is
read through `Rt.elemI` .... So these rows cost what they cost before.

Which rows: the subscripted identifier of a one-dimensional element
access of mode INT, REAL, BOOL, CHAR, BITS ... (also `LONG`) in the loop
clause, outside routine texts, declared outside the loop and held in a JVM
local, that is

- a row variable whose name never escapes (`Hoist.analyse`, like
  `Direct`: every applied occurrence is dereferenced, sliced, or the
  destination of a voided assignation or assigning operator) and that no
  routine captures. Then only an assignation in the text of the loop can
  give it another row; if the loop has one, the row is not hoisted;
- an identity of a row mode, which includes the parameters of a
  procedure: its value never changes.

The elements are not cached; they live in the shared store and may
change. A nested loop uses the locals of the enclosing loop. The
environment variable `A68J_HOIST_ROWS=off` turns it off, and
`A68J_DEBUG_HOIST=1` lists the row variables found.

**Why only stride 1.** A first version also took rows with any stride
into the fast path (`arr[off + i * stride]`, the stride in a local).
That was slower: with an unknown factor C2 neither removes its own range
check of the array nor unrolls a counted loop. With stride 1 C2 unrolls
the loop `for i to 1000 do s +:= row[i] od` 16 times with one add per
element. Other strides hardly occur: an identity or a parameter copies a
slice like `m[, 2]` (as ga68 does); only a slice of an identity is shared
(`[,] int mm = m; [] int c = mm[, 2]`, see `GenUnits.ownedValue`). For
that case the old path takes as long as before (3.7–3.9 s in all
versions of a test with 5·10⁹ accesses).

**NIL.** The variable is NIL on entry only if the loop runs before its
declaration (the declaration is outside the loop, so it stays NIL for the
whole loop); then `n` is 0 and an access reports NIL through the old
path. The only difference to before: the subscript is evaluated before
that error. ga68 crashes on such a program with a segmentation fault.

### Measurements

The 12 Rosetta programs of section 7, five runs each, alternating,
median (`tools/perf_ab.py`); A without hoisting, B with any stride in the
fast path, C stride 1 only (now the default):

| program | A | B | C |
|---|---|---|---|
| proper-divisors-2 | 13.52 | 13.61 | 13.00 |
| composite-numbers-k-… | 12.20 | 12.49 | 12.04 |
| distribution-of-0-digits-in-factorial-series | 10.80 | 6.61 | 6.72 |
| fermat-pseudoprimes | 8.61 | 8.62 | 8.65 |
| self-describing-numbers | 8.55 | 8.19 | 8.40 |
| ruth-aaron-numbers | 5.48 | 5.45 | 4.97 |
| rhonda-numbers | 6.16 | 6.20 | 6.15 |
| colorful-numbers | 4.29 | 4.61 | 3.49 |
| increasing-gaps-between-consecutive-niven-numbers | 4.10 | 4.04 | 4.06 |
| percolation-mean-run-density | 3.68 | 3.78 | 3.80 |
| long-primes | 2.21 | 2.26 | 2.25 |
| iterated-digits-squaring | 1.81 | 1.71 | 1.87 |
| **sum** | **81.40** | **77.58** | **75.39** |

- distribution-of-0-digits −38 %; ruth-aaron-numbers −9 % and
  proper-divisors-2 −4 % (the five runs of A and C do not overlap);
  colorful-numbers, bimodal in A, was always in the fast state with C.
- Three programs (composite-numbers, increasing-gaps, percolation) give
  the same class files in all versions: their differences, up to 3 %,
  are the noise of the machine.
- A control run of the final code against A on the nine programs that
  change (three runs each): 61.4 s against 55.3 s.
- The long programs: big-program-1-long 38.3 s → 37.2 s (three runs
  each, not overlapping); big-program-1 and big-program-2 (under 2 s) no
  measurable difference.
- All times of this day were 10–20 % higher than those of section 7 for
  the same code (A); the cloud machine varies from day to day. Only
  versions measured alternately in the same run can be compared.

### The machine code

The inner loop of distribution-of-0-digits (C2, ordinary compilation of
`loop4`, the `while` loop), per iteration:

| | before | now |
|---|---|---|
| loads of `Cell.value` | 3 | 0 |
| class checks (`Row`, `int[]`) | 6 | 0 |
| loads of row fields | 15 | 0 |
| bound checks (two compares) | 3 | 3 |
| range checks of the array | 3 | 3 |

What is left: the Algol 68 bound check and the JVM's range check, which
C2 cannot merge in a `while` loop (it is not a counted loop), and a few
spills to the stack (the hoisted values of two rows take registers).

### Tests

`tests/extra/hoist-rows-1.a68` (rows that may and may not be hoisted:
assigned in the loop, assigned through a procedure, FLEX shortened,
STRING extended, copied and shared slices of a two-dimensional row, a row
of fields, names of elements, nested loops, loops in procedures) and
`hoist-rows-2.a68` (a bound error in a hoisted access). The ga68 test
suite, `tests/extra`, the Java and PAR tests and the Rosetta programs give
the same results as before, also with `A68J_LOOP_METHODS=none` and
`every`.

## 9. Smaller code (2 Nov 2026)

### The problem

The JVM allows at most 65 535 bytes of code in a method, and HotSpot
compiles no method above 8000 bytes (the limits and the measurements
before this section: [`TODO-method-size.md`](TODO-method-size.md)). The
generated code had about 64 bytes per source line, so a main program of
about 1000 lines without loops could not be compiled at all, and a68j
stopped with a Java stack trace. Most of the size came from code that is
inline on purpose (section 3): the element access with its fast and its
slow path (150 to 240 bytes), `MOD` and `OVER` with their checks (50 to
70 bytes), and the check for NIL at every use of a variable that lives in
a frame (12 bytes).

### What a68j does now

None of this changes the behavior: the order of evaluation, the checks
and the messages stay as before.

- **A compile-time error instead of the stack trace** (`Gen.TooLarge`).
  The class file library reports a method that is too large only when it
  writes the class, by an `IllegalArgumentException` whose message names
  the method; `Gen.compileClass` takes the method from it. It first tries
  the same as for a method too large for the JIT: the outermost loops of
  the method become methods of their own, and the class is generated
  again. If that cannot help, a68j reports the method like other errors,
  with the kind of code, its lines and its size, and exits with status 1:

  ```
  p.a68:2:12: error: the routine text (lines 2 to 4003) needs 163740 bytes of JVM code, more than the 65535 that a method may have
  p.a68:2:12: note: a68j compiles each routine text and each loop as a method of its own; code outside of them is not divided yet, so a long sequence of statements can be split into procedures
  ```

  (Since section 11 such code is divided; the error remains for what
  cannot be, and its note says what that is.)

- **Division by a constant** (`Intrinsics#constantDivision`): `OVER`,
  `MOD`, `%`, `%*` and the assigning forms `%:=`, `%*:=`, `OVERAB`,
  `MODAB` on INT and LONG INT, when the divisor is an integral denotation
  greater than 0. Such a divisor is neither 0 nor -1, so none of the checks
  of the general case is needed: `idiv`, or `irem` and for `MOD` the
  correction of a negative remainder (`dup`, `ifge`, add the divisor).
  About 12 bytes instead of 50 to 70, and fewer instructions in hot loops.
  The results are those of `Rt.over` and `Rt.mod`; the test compares
  them with ga68 for negative operands, the most negative INT, a divisor
  of `max int`, LONG INT and SHORT INT (for `MODAB` with the fixed result,
  `docs/ga68-bug-reports.md`, entry 11).
- **No check for NIL where a variable cannot be NIL** (`Gen#loadOwnCell`).
  A LOC variable without rows (`GenDecls#cellAtEntry`) gets its location
  in the prologue of its serial clause, which runs before every phrase of
  the clause and runs no code of the program (it makes procedure values,
  SKIP values and locations only). Every use of the variable is in a phrase
  of that clause or in a procedure called from one, so the location is
  never NIL there, also when a procedure uses the variable before its
  declaration (then it has its SKIP value, as in ga68). HEAP variables and
  variables with rows get their location at the declaration and keep the
  check. 12 bytes less per use of a variable in a frame (one used by a
  procedure or a loop method), and one cast less.
- **The shortest instruction for a constant** (`Gen#constant`, used
  instead of `CodeBuilder#ldc` everywhere): `iconst` or `bipush` for an
  int from -128 to 127, `lconst` for the long 0 and 1, `ldc` otherwise
  (never longer than `sipush`).
- **Code that runs once** (`Gen#runsOnce`): the code of the method of the
  particular program (or module) outside of every loop and before the
  first label of every serial clause around it runs at most once. There an
  element access of a one-dimensional row is a call of `Row#index1` and
  `Rt.elemI`, `Rt.setElemI`, ... instead of the inline fast and slow
  paths, with the same checks and messages. The particular program itself
  is a serial clause with a label, as the standard prelude puts the label
  of `stop` after it; from a label on, a jump can run the code again, so
  that code stays inline. Procedures, loop methods and the units of a
  parallel clause are methods of their own and are never code that runs
  once. Speed does not matter for such code, and section 3 does not apply
  to it: it is not in a loop whose code C2 compiles without a profile.
- **Uniting a plain value** (`GenUnits#unitedPlainValue`,
  `GenCoercions#uniting`): an item of `print` or the argument of `whole`
  is a value of a primitive mode united to a union. The copy of a value
  (`Rt.copyValue`, for value semantics) returns such a Union unchanged, as
  it only copies a Union of a structure or a row, so it is left out; and
  `Rt.unite (int, int)` and its siblings box the value themselves, one
  call instead of two. 9 bytes less per item.

### Measurements

Sizes, the 669 programs of the repository and the generated programs:
[`TODO-method-size.md`](TODO-method-size.md), "Results of steps 1 and 2".
In short: 9.4 % less code over all methods, 14 % less in `program()`, no
method above 8000 bytes left (there were two), and a main program of
copies of `s := (s * 7 + k) MOD 1000003` fits about 3100 copies into one
method instead of 800.

Time: the 12 Rosetta programs of section 7, five runs each, alternating,
median (`tools/perf_ab.py`); A the version before (commit `ebc7769`), B
with all of this section:

| program | A | B |
|---|---|---|
| proper-divisors-2 | 11.40 | 11.37 |
| composite-numbers-k-… | 10.31 | 10.79 |
| distribution-of-0-digits-in-factorial-series | 5.79 | 5.87 |
| fermat-pseudoprimes | 8.52 | 8.25 |
| self-describing-numbers | 7.51 | 7.77 |
| ruth-aaron-numbers | 4.97 | 4.88 |
| rhonda-numbers | 5.87 | 5.91 |
| colorful-numbers | 3.16 | 4.04 |
| increasing-gaps-between-consecutive-niven-numbers | 3.69 | 3.54 |
| percolation-mean-run-density | 3.63 | 3.86 |
| long-primes | 2.14 | 2.17 |
| iterated-digits-squaring | 1.62 | 1.53 |
| **sum** | **68.61** | **69.99** |

- The runs of A and B overlap for every program. The three largest
  differences, measured again with eleven runs each: colorful-numbers
  3.09 against 3.11 (bimodal in both, two and one runs in the slow state
  of about 4 s), percolation-mean-run-density 3.58 against 3.58,
  composite-numbers 11.30 against 11.14. So no difference is measurable;
  fermat-pseudoprimes, the only program whose five runs barely overlap
  (8.31–8.56 against 8.06–8.46), is perhaps 3 % faster.
- big-program-1 1.61 against 1.61 s, big-program-2 1.58 against 1.54 s
  (five runs), big-program-1-long 33.98 against 34.03 s (three runs);
  the same output.
- In hot code only the division by a constant and the missing checks
  for NIL are new; the other changes concern code that runs once. Why
  these two change the time so little was not examined (the machine code
  of C2 before and after was not compared).

### Tests

`tests/extra/division-constant-1.a68` (the divisions by a constant,
compared with ga68), `tests/extra/run-once-code-1.a68` (element accesses
of all primitive modes in code that runs once, a label after which the
code is inline again, a variable used by a procedure before its
declaration, a bound error with its line) and `tools/method_size_test.py`
(results where the code fits, the error where it does not). The ga68
test suite (471 of 472), `tests/extra` (33 of 33), the module tests as
library jars (25 of 25), the Java, PAR and SKIP mode tests and the
Rosetta programs give the same results as before; `tests/extra` and the
Rosetta programs also with `A68J_LOOP_METHODS=every`.

## 10. Compact element access in methods too large for the JIT (2 Nov 2026)

### The problem

A method above 8000 bytes runs in the interpreter (section 7). The loop
methods help only where the size is in loops. A procedure without a loop
can be large too, and the element access is most of it, as it is inline
in procedures (152 to 235 bytes per access, section 9). An example is
the step of an interpreter, a case clause over the opcodes:

```
proc step = (int code) void:
  case code + 1 in
    (r[0] := (r[3] * 2 + r[7]) MOD 65521; if r[0] > r[3] then r[7] +:= 1 fi),
    ...
  esac;
for t to 2000000 do step (t MOD n) od
```

Each branch made about 1050 bytes: with 20 branches `step` had 20 937
bytes and ran in the interpreter, and with about 50 or more it could not
be compiled at all (60 branches: 76 243 bytes).

### What a68j does now

`Gen.compileClass` has a second remedy for a method that is too large
(`Gen#remedy`): if the method is still above 8000 bytes (or above
65 535) after its outermost loops became methods of their own, or if it
has no loops, the next round generates it with the compact element access
of code that runs once (`Gen#compactAccess`: `Row#index1`, `Rt.elemI`,
`Rt.setElemI`, ... with the same checks and messages). The choice is per
method and by its measured size; every other method keeps the inline fast
paths. So a program none of whose methods is too large is compiled
exactly as before.

The reason for the inline fast paths (section 3) does not apply to such a
method: without the remedy it is not compiled by the JIT at all. A method
that is still above 8000 bytes when compact runs in the interpreter as
before, with less code.

`A68J_COMPACT_ACCESS=every` makes every element access compact, for the
tests; `A68J_DEBUG_LOOPS=1` reports the remedies.

### Measurements

The interpreter step above, `step` called 2 000 000 times, the time of
the whole run (JVM start included), five runs each, alternating:

| branches | before | after |
|---|---|---|
| 20: size of `step` | 20 937 bytes | 5 852 bytes |
| 20: time | 1.93 to 2.37 s | 0.34 to 0.46 s |
| 60: size of `step` | 76 243 bytes, a compile-time error | 17 578 bytes |
| 60: time (three runs) | | 1.21 to 1.49 s |
| 200: size of `step` | 258 267 bytes | 68 304 bytes, still a compile-time error |

About 290 bytes per branch instead of about 1050 (from 20 and 60
branches; 341 per branch at 200, so the limit of 65 535 bytes is now a
little below 200 branches instead of about 50, and the limit of 8000
bytes about 27 branches instead of 7). For comparison: the 20 branches made procedures of their own by hand
(about 820 bytes each, all compiled) took 0.21 to 0.26 s, ga68 `-O0`
0.06 to 0.08 s. With 60 branches `step` is still above 8000 bytes and
runs in the interpreter; only dividing it (step 3 of
[`TODO-method-size.md`](TODO-method-size.md)) helps there.

Only one-dimensional rows of the primitive modes have a compact access
(as in section 9); an access with several subscripts stays inline.

### Tests

- The class files of all 1214 programs of `tests/extra`, `tests/perf`,
  `tests/par`, `tests/java` and `tests/rosetta` and of the 502 programs
  of the ga68 test suite, compiled by the version before and after this
  section, are identical byte for byte, and so are the compiler's status
  and messages. None of these programs has a method that gets the remedy,
  so none can run differently.
- With `A68J_COMPACT_ACCESS=every` (`tools/compact_access_test.sh`): the
  ga68 test suite, `tests/extra` (also together with
  `A68J_LOOP_METHODS=every`), the Rosetta programs and the Java and PAR
  tests give the same results as without it, program by program.
- `tools/method_size_test.py` has three more cases, the interpreter step
  with 20, 60 and 200 branches: the right results, and the method sizes
  below 8000 and 65 535 bytes, or the compile-time error. The version
  before this section fails the first two.

## 11. Serial clauses cut into methods of their own (2 Nov 2026)

### The problem

After sections 7, 9 and 10 a method could still be too large: a long
main program or procedure without loops, a loop body without an inner
loop, or a procedure that is one large case clause. Above 8000 bytes it
ran in the interpreter; above 65 535 bytes it was a compile-time error
(section 9). The measurements and the plan are in
[`TODO-method-size.md`](TODO-method-size.md) (step 3).

### What a68j does now

A third remedy in `Gen.compileClass` (`Gen#remedy`), for a method that is
still too large after its loops became methods of their own and its
element accesses compact: its serial clauses are cut into parts that are
methods of their own (class `Cut`).

- **A part is the rest of a serial clause:** the clause is cut before
  phrase k, and the phrases from k on become a method `partN` that the
  clause calls in their place; its result is the value of the clause. A
  rest that is still too large is cut again, so a long sequence of
  statements becomes a chain of methods. With k = 0 the whole clause is a
  part: for a branch of a conditional or case clause, or a closed clause,
  inside a phrase that is too large itself. Only rests, never a piece in
  the middle: what a rest declares, only later phrases can see.
- **As for a loop method** (section 7): the part is compiled exactly as
  before in the same routine, so frames, labels and jumps work as in the
  enclosing method. The method gets the frame and the values of the JVM
  locals it uses; the direct variables it assigns travel in the one
  `long[]` holder and are copied back when it ends, normally or by an
  exception. At most 200 parameter slots; where a part would need more,
  the clause is not cut there.
- **Where a clause may be cut** (`Cut#admissible`): only a serial clause
  (the declarations of an enquiry clause are visible in its THEN and DO
  parts); no identifier declared in the rest occurs before the rest or
  outside the clause, so the rest gives its declarations their initial
  values itself at its start (`GenClauses#prologue`), which is the same
  as at the start of the clause; with labels, the whole clause or a rest
  after the last label. A jump from such a rest back to a label leaves the
  method by an exception and the clause starts again at the label, as for
  a jump out of a loop method; a rest after a label has no declarations
  (none may follow a labeled unit).
- **Where the clauses are cut is measured:** in the rounds after the
  first, each phrase of a method that is being remedied gets a
  `CharacterRange` entry (debug information the JVM ignores); in the
  class file its labels give the exact size of the phrase
  (`CodeAttribute#labelToBci`; the `CodeBuilder` itself gives no
  positions). `Cut#plan` then cuts, bottom up, each serial clause whose
  phrases take more than 7000 bytes into rests of at most 7000 bytes, and
  if the method is still larger, makes the largest clauses inside it
  whole parts until it fits. The next round measures again; the last
  round is generated once more without the entries. A method above
  65 535 bytes cannot be written and so not measured: it is first divided
  roughly, in proportion to the number of nodes of its phrases, into parts
  of about 30 000 bytes that the next round measures.
- **A part inherits** what its method is: code that runs once (compact,
  section 9), compact access by size (section 10), loops as methods.
  At most sixteen rounds instead of eight.

What remains a compile-time error above 65 535 bytes: a single unit that
is too large (a row display of 20 000 elements; since section 12 only if
they are not constants), and code whose parts would each need more than
200 parameters. The note of the error says so.

`A68J_CUT=every` cuts every serial clause before every phrase where it
may and makes every serial clause a method of its own, a test of the
mechanism; `A68J_DEBUG_LOOPS=1` reports the remedies.

### Measurements

Generated programs (copies of `s := (s * 7 + k) MOD 1000003`, and the
interpreter step of section 10 with 2 000 000 calls); the time of the
whole run (JVM start included), five runs each, alternating, before and
after this section:

| program | before | after |
|---|---|---|
| 400 copies in a procedure without loops, 200 000 calls | 16 144 bytes, 2.39 to 2.95 s | the procedure and 2 parts (6999, 6985, 2174 bytes), 0.89 to 1.09 s |
| interpreter step, 60 branches | 17 578 bytes, 1.17 to 1.36 s | below 8000 bytes, 0.40 to 0.50 s |
| interpreter step, 200 branches | 68 304 bytes, a compile-time error | below 8000 bytes, 1.15 to 1.31 s |
| 6000 copies in the main program | 125 771 bytes, a compile-time error | the main program and 18 parts, at most 7101 bytes, 0.10 to 0.14 s |

Compiling the program with 6000 copies takes seven rounds and 10.1 s (one
run). The 400 copies in the body of a loop need no cut since section 9
(7353 bytes). For comparison, before section 9 the procedure ran 2.44 s,
ga68 `-O0` 0.62 s (TODO-method-size.md).

`whole-1.a68` of the ga68 test suite, whose main program of 917 lines had
29 225 bytes (it has no loops), is now four methods of at most 7081 bytes;
it runs once, so its time hardly changes (0.13 to 0.16 s before, 0.14
to 0.16 s after, five runs each).

### Tests

- The class files of 1715 of the 1716 programs of the repository and of
  the ga68 test suite, compiled by the version before and after this
  section, are identical byte for byte, and so are the compiler's status
  and messages; the one that differs is `whole-1.a68` above, which gives
  the same result as with ga68.
- With `A68J_CUT=every` (`tools/cut_test.sh`): the ga68 test suite, the
  extra tests, the PAR tests and the Rosetta programs give the same
  results as without it, program by program; four Rosetta programs that
  run long (colorful-numbers, distribution-of-0-digits-in-factorial-series,
  increasing-gaps-between-consecutive-niven-numbers, rhonda-numbers) need
  more than the 30 s of the test there, as every loop body is cut too, and
  give the same results with 600 s. Of the Java tests, `nest-java-trace`
  differs only in its Java stack trace, which shows the part methods
  (`part4` instead of `program`).
- `tests/extra/serial-cuts-1.a68`: declarations before and after a cut, a
  procedure called before its declaration, the values of serial clauses
  of several modes, variables in JVM locals assigned in a part, jumps from
  a part back to a label and out of nested clauses, and a runtime error in
  a part, with its line; the same results as ga68, with and without
  `A68J_CUT=every`.
- `tools/method_size_test.py`: the main program, procedure and loop that
  were too large and the interpreter step with 200 branches now give the
  right results, and no method of a case that compiles has more than 8000
  bytes; two new cases keep the compile-time error (a row display of
  20 000 elements, and parts that would need the values of 250
  variables). 10 of 10.

## 12. Row displays of constants as tables (2 Nov 2026)

### The problem

`GenRows#collateral` stores each element of a row display with code of
its own. A display is a single unit, so neither loops as methods nor
`Cut` (sections 7 and 11) can divide it. Measured in the class files
(`tools/MethodSizes.java`) before this section, for displays in the main
program:

| display | bytes of code per element | above 8000 bytes from | above 65 535 bytes from |
|---|---|---|---|
| `[] int`, `[] real` of denotations | about 8 | about 1000 elements | about 8200 elements |
| `[] int` with negative values | about 8.5 | about 920 | about 7700 |
| `[] string` | about 17 | about 470 | about 3850 |
| `[,] int`, square | about 6.8 per value | about 34 × 34 | about 95 × 95 (100 × 100 failed) |
| `[,] int`, two columns | about 16.5 per value | about 240 rows | about 2000 rows |

Above 65 535 bytes the display was a compile-time error (20 000 INTs:
after 7 to 8 s, as `Gen#compileClass` first tried all its remedies; ga68
compiles it in 0.8 s). Above 8000 bytes it ran in the interpreter, which
matters where it is elaborated often, in a procedure (table below). A
display of 1000 to 1100 INTs is at that edge. In the programs of the
repository the largest displays are all of denotations: 901 INTs
(`decreasing-contiguous-subsequences`), a grid of 20 × 20
(`largest-product-in-a-grid`) and 256 (`perlin-noise`). `javac` has the
same limit for an array initializer ("code too large").

Also, the value of a display was copied once more where it went
(`Rt.copyValue`, `[] int t = (...)`), and each STRING and each row in a
display was copied before it was stored: 6 bytes of code each and the
time of the copy, for a value nothing else refers to.

### What a68j does now

- **No copy of a new value** (`GenUnits#freshValue`): a display and a
  string denotation make a new value that nothing else refers to, so
  wherever it goes it is not copied. A STRING in a display takes 11
  instead of 17 bytes, a row of `[,] int` with two columns 26 instead of
  33.
- **Tables** (class `Tables`, runtime class `a68j.rt.Table`): a display
  of INT, CHAR, BITS, BOOL, REAL (and their lengths) or STRING elements,
  of one dimension or nested displays of the same length at each depth,
  with at least 128 constant elements (denotations, their widenings, and
  the standard monadic `-` and `+` of them) is a table. Its values are
  written as text into a string constant of the class (in pieces of at
  most 16 384 chars, as a constant holds at most 65 535 bytes); the class
  initializer decodes the text once into a static field; each elaboration
  of the display makes a new row of copies of the values
  (`getstatic`, `Table.row()`: 6 bytes whatever the size). The elements
  that are not constants are elaborated in their order and stored into
  the copy before the row is made (`Table.store()`, `Table.row(Object)`);
  constants have no effects, so that they are not elaborated in their
  place cannot be observed. Equal displays share one table. A display of
  two dimensions whose rows differ in length is no table and remains the
  run-time error of `Row#ofRows`.
- **The minimum of 128** was measured (below): with fewer elements the
  code of the display is small and was faster than the table.
  `A68J_TABLE_MIN=N` sets another minimum, `A68J_TABLE_MIN=1` makes a
  table of every display that can be one (`tools/table_test.sh`).

What remains a compile-time error: a display of thousands of elements
that are not constants (`tools/method_size_test.py`, 20 000 copies of a
variable), which only generated code has.

### Measurements

A procedure with a local `[] int t = (...)` of N denotations, called
200 000 times, and the same display in the main program followed by a
hot loop over it (100 000 000 steps); the time of the whole run (JVM
start included), five runs each, alternating, before and after this
section; ga68 `-O2` the best of five runs:

| program | before | after | ga68 `-O2` |
|---|---|---|---|
| procedure, N = 1000 | 7592 bytes, 0.57 to 0.63 s | 169 bytes, 0.33 to 0.38 s | 0.38 s |
| procedure, N = 1100 | 8197 bytes, 1.97 to 2.18 s | 169 bytes, 0.32 to 0.38 s | 0.41 s |
| procedure, N = 2000 | 15 219 bytes, 3.46 to 3.78 s | 169 bytes, 0.45 to 0.52 s | 0.76 s |
| procedure, N = 6000 | 46 147 bytes, 9.33 to 10.41 s | 169 bytes, 1.00 to 1.06 s | 3.02 s |
| main program, N = 6000, then the loop | 45 683 bytes, 0.62 to 0.68 s | 218 bytes, 0.62 to 0.69 s | |

In the main program the display runs once and the loop is a method of
its own (section 7), so the time does not change. With the copy of the
display gone (first item above) but without tables, the procedure took
0.31, 1.63, 2.82 and 8.22 s (best of five).

The minimum: the same procedure with N denotations, about 40 000 000
elements in all (10 000 000 calls for N = 4), with and without a table
(`A68J_TABLE_MIN`), the best of five runs:

| N | without a table | table |
|---|---|---|
| 4 | 0.19 s | 0.42 s |
| 8 | 0.16 s | 0.28 s |
| 16 | 0.13 s | 0.22 s |
| 32 | 0.13 s | 0.18 s |
| 64 | 0.125 s | 0.140 s |
| 96 | 0.129 s | 0.136 s |
| 128 | 0.129 s | 0.125 s |
| 256 | 0.140 s | 0.141 s |

Compiling (one run each, alternating): 8000 INTs 2.6 s before, 2.0 s
after (class file 141 461 bytes before, 49 207 after); 20 000 INTs a
compile-time error after 7.3 s before, 6.6 s after (119 762 bytes);
20 000 STRINGs an error after 8.4 s, 6.4 s after; a matrix of 300 × 300
INTs an error after 5.8 s, 4.2 s after. Most of the time is now the
front end: `MoidsMisc.addToSoidList` appends each of the 20 000 elements
to the end of a list, as `a68_add_to_soid_list` of ga68 does, but by a
recursion with a new `Ref` at each step (JFR: 384 of 475 samples
show it in the frames printed); ga68 compiles 20 000 INTs in 0.8 s.

### Tests

- The class files of the 1662 programs of `tests/rosetta`, the ga68 test
  suite (with `mcgt`) and `tests/extra`, compiled by the version before
  and after this section: 955 identical byte for byte; 702 differ only
  in the calls of `Rt.copyValue` left out (5263 calls with their 4713
  `checkcast Row` and 550 `checkcast Struct`; with it some constants
  are loaded by `ldc` instead of `ldc_w`, and 6 bytes less dead code in
  `value-sharing-1`); 5 have tables: `bin-given-limits`,
  `decreasing-contiguous-subsequences`, `largest-product-in-a-grid`,
  `perlin-noise` and the new test below. The ga68 test suite (521 of
  522, the other one `posix-argc-argv-1` as before) and the Rosetta
  programs (537 the same as ga68, 2 the same except for rounding, the
  same 9 others and 1 unsupported as before) give the same results
  program by program before and after; the extra tests 39 of 39, the
  Java tests 46 of 46, the PAR tests 14 of 14.
- With `A68J_TABLE_MIN=1` (`tools/table_test.sh`): the same results,
  program by program, for the ga68 test suite and the Rosetta programs,
  and all extra, Java and PAR tests pass; a display of two dimensions
  whose rows differ in length is still the run-time error.
- `tests/extra/row-display-tables-1.a68`: tables of INT (with the
  largest and negative values), REAL (widened INTs, small and large
  exponents), LONG REAL, LONG INT, CHAR, BOOL, STRING (empty, quotes,
  breaks, a rowed character that is not a constant) and BITS, of two
  and three dimensions, constants mixed with calls that count (their
  order), a new row at each elaboration (a flexible variable, a
  generator and an identity of the same display, and a loop), as a field
  and as an argument; the same output as ga68 with the default minimum,
  with `A68J_TABLE_MIN=1` and without tables.
- `tools/method_size_test.py`: new cases with 20 000 INTs, 20 000
  STRINGs and a matrix of 300 × 300 INTs give the right results with no
  method above 8000 bytes; the case of the compile-time error is now a
  display of 20 000 copies of a variable. 13 of 13.
