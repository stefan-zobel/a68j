# Real PAR and SEMA (option `-fpar-threads`)

As of 31 Oct 2026. An extension of a68j over ga68; without the option
a68j behaves exactly like ga68.

## What this is about

In the Revised Report `par (u1, u2, u3)` means that the units are
elaborated **concurrently**. They can synchronize with semaphores (mode
`sema`, operators `LEVEL`, `UP`, `DOWN`, RR 10.2.4, after Dijkstra).

ga68 does not do this:
- PAR is elaborated like a collateral clause, **one unit after the other**
  in the same thread (`a68_lower_parallel_clause`: "for now treat like a
  VOID collateral clause").
- For `LEVEL`, `UP`, `DOWN` ga68 has no lowering routine; a program with
  them stops at compile time with
  `fatal error: no lowering routine installed for construct.  jemarch has been lazy`.

Without the option a68j does exactly the same (the same message, the
same line and column). With `-fpar-threads` the units run in threads of
their own, and SEMA is available.

## Usage

```sh
java -jar backend/a68j-backend.jar -fpar-threads -run tests/par/par-buffer.a68
# or compile and start separately:
java -jar backend/a68j-backend.jar -fpar-threads tests/par/par-buffer.a68 -d out
java -cp backend/a68j-backend.jar:out ParBuffer
```

An example (producer and consumer with a bounded buffer,
`tests/par/par-buffer.a68`):

```
sema free = LEVEL size, full = LEVEL 0, mutex = LEVEL 1;
par (
   for i to n                                  { producer }
   do DOWN free;
      DOWN mutex; buffer[put] := i; put := (put + 1) MOD size; UP mutex;
      UP full
   od,
   to n                                        { consumer }
   do DOWN full;
      DOWN mutex; int v = buffer[get]; get := (get + 1) MOD size; UP mutex;
      UP free;
      sum +:= v; count +:= 1
   od
);
```

The option applies to the compilation unit: a module that uses PAR must
also be compiled with `-fpar-threads` for its PAR clauses to get threads.

## What exactly happens

| Construct | Implementation |
|---|---|
| `par (u1, …, un)` | each unit becomes a method of its own (like a routine without parameters) and runs as a closure over the current frame in a thread of its own (`a68j.rt.Par.run`) |
| end of the clause | only when **all** units have finished (RR 3.3.2.c) |
| `goto` out of a unit | the other units are cancelled, then the jump continues in the thread of the clause |
| runtime error in a unit | likewise: the others are cancelled, then the usual error message with the line, status 134 |
| `LEVEL n` (INT → SEMA) | a new semaphore with the value n |
| `LEVEL s` (SEMA → INT) | the current value |
| `UP s` | value + 1; waiting units are woken up |
| `DOWN s` | waits until the value is positive, then value − 1 |

**Variables:** what a unit uses from outside lives in the frame of the
enclosing routine (as for closures); each unit has a frame of its own for
its own declarations. As in the Report, simultaneous access to the same
variable is the program's business: that is what the semaphores are for.
At the end of the clause the parent thread sees all changes made by the
units (Java memory model: `join` ensures this).

**SEMA inside:** `mode sema = struct (ref int F)` as in the Report; the
`ref int` is an `IntCell` whose monitor (`synchronized`/`wait`/
`notifyAll`) protects the value. So `LEVEL s` always gives the exact value.

**Cancellation:** cancelled units are interrupted (`Thread.interrupt`). A
unit that is waiting in `DOWN` ends at once. A unit that only computes
runs on to its end (a check in every loop would slow down all programs);
its results are lost.

**Deadlock:** a `DOWN` on a semaphore with a value ≤ 0 while no unit of a
PAR clause is running (so nobody can do an `UP` any more) is a runtime
error: `deadlock: DOWN on a semaphore with value 0 outside a parallel
clause`. Units waiting for each other are not detected; the program then
hangs (undefined in the Report).

## Threads: JDK 25 and JDK 28

Starting and waiting is done by the class `a68j.rt.ParRun`, in two
versions in the same **multi-release jar**:

| JVM | Class | Implementation |
|---|---|---|
| JDK 25–27 | `a68j/rt/ParRun.class` | plain threads, `join`, cancellation by `interrupt` |
| JDK 28 and later | `META-INF/versions/28/a68j/rt/ParRun.class` | **structured concurrency** (JEP 543): each unit is a subtask of a `StructuredTaskScope` with `Joiner.awaitAllSuccessfulOrThrow`; the first failure cancels the scope |

The JVM picks the fitting class itself; both behave the same. In JDK 28
ea+21 the API is final, `--enable-preview` is not needed. The rest
(`Par`, SEMA) is the same for both versions.

`backend/build.sh` compiles the JDK 28 version (`src/main/java28`) if a
JDK 28 is present: `$JDK28` or `/opt/jdk28/jdk-28`. Otherwise the jar
contains only the JDK 25 version, which also runs on JDK 28. The jar is
then built with the `jar` tool of JDK 28, because that of JDK 25 cannot
check class files of JDK 28.

**Platform or virtual threads:** by default they are platform threads
with the same large stack (128 MB) as the main thread, because the stack
of a virtual thread is limited by `-Xss` (measured: with the default
setting it ends already at 100 000 levels of recursion), and deep
recursion is common in Algol 68 programs. With `-Da68j.par=virtual` they
are virtual threads (then enlarge the stack with `-Xss` if needed).

## A thread-safe runtime

Shared mutable state of the runtime and what happens to it:

| State | Solution |
|---|---|
| random number generator (`random`, a ring of 31 numbers) | synchronized as soon as a PAR clause has run once (`Par.threads`); before that without a lock, so no cost for programs without PAR |
| file table and buffers of the POSIX transput (`fopen`, `fputc` …) | the methods are `synchronized` (uncontended locks cost practically nothing) |
| `errno` | shared by all threads (like a single process without a thread-local errno) |
| transput at the Algol 68 level (the state of a `file`) | not protected: output of several units to the same file can mix; protect it with a semaphore, as in the Report |

## Tests

`tests/par/` contains the programs; `tools/par_tests.sh [JAVA [REPEAT]]`
runs them:

- with `-fpar-threads`, several times (5 by default), compared with
  `X.out`;
- without the option, compared with `X.ga68` (the output and messages of
  ga68).

| Test | checks |
|---|---|
| `par-sum` | four units sum through a mutex semaphore, no lost additions |
| `par-buffer` | producer/consumer with a buffer of size 4 (Dijkstra) |
| `par-jump` | `goto` out of a unit while the other one waits in `DOWN` and is cancelled |
| `par-error` | a runtime error in a unit |
| `par-nested` | nested PAR in a recursive procedure, local variables of the units |
| `par-sequential` | PAR without SEMA (also runs with ga68) |
| `par-deadlock` | `DOWN` with no unit running |

Result on 31 Oct 2026: 14 of 14 on JDK 25 and on JDK 28 ea+21, with 20
repetitions each of the runs with threads.

The Rosetta solutions with PAR/SEMA (`concurrent-computing`,
`metered-concurrency`, `synchronous-concurrency`) already fail earlier on
things that ga68 cannot do (formats, `open`), so they are not in the
tests.

## Open points

- Cancelling units that compute (would need a check at the ends of loops,
  only with the option).
- Detecting deadlocks between units (for example by a time limit with a
  thread dump).
- `errno` and transput state per thread.
