# tools

Helper programs of a68j: generators for checked-in sources, the
differential test harness against ga68, test scripts and performance
measurement. Every script describes its usage and options in its header
comment; this file gives the overview. Paths are written from the project
root.

## Generators

The generated files are checked in; change the generator, not its output.

| Tool | Generates |
|---|---|
| `gen_lists.py` | the module `Lists`: `backend/lib/lists.a68` and the classes `backend/src/main/java/a68j/jlib/*List.java` (`docs/java-modules.md`) |
| `gen_hashmaps.py` | the module `Hashmaps`: `backend/lib/hashmaps.a68` and the classes `backend/src/main/java/a68j/jlib/*Map.java` (`docs/java-modules.md`) |
| `gen_tmlanguage.py` | the TextMate grammar `editors/vscode-algol68/syntaxes/algol68.tmLanguage.json` (`docs/editor.md`) |
| `grammar_check.sh` | no output; compares the grammar with the token dump of the a68j scanner for all test programs |

## Differential testing against ga68

ga68 is the reference: a program must give the same output, exit status
and first runtime error line on the JVM as the native ga68 executable.

| Tool | Purpose |
|---|---|
| `run_native.py` | compiles and runs programs with ga68 and records the results, one `DIR/<name>.json` per program |
| `run_jvm.py` | compiles and runs the same programs with `backend/a68j-backend.jar` and compares them with the results of `run_native.py` (`--native DIR` is required) |
| `modtest.py` | helper module of both runners: module packets (`dg-modules`, including the bundled module `genietransput` from `backend/lib`) and module map options of the multi-packet tests |
| `compare.py` | compares the token stream, the syntax tree or the tree after semantic analysis of the front end with ga68, for every `.a68` file below a directory |
| `ga68-token-dump.patch` | patch for ga68 that writes the dumps `compare.py` compares (enabled by environment variables); the a68j side is `a68j.tools.TokenDump` and `a68j.tools.SyntaxDump` |
| `mutate.py` | writes mutated copies of programs (random byte insertions, deletions, case flips, truncations) to test the error paths of the scanner against ga68 |
| `mutate_tokens.py` | the same at the token level (words and symbols deleted, duplicated, swapped or replaced), so that the mutants scan but no longer parse; tests the error paths of the parser |

Example, the extra tests:

```sh
python3 tools/run_native.py --ga68 …/xgcc --libdir …/libga68/.libs --out /tmp/native tests/extra/*.a68
python3 tools/run_jvm.py --native /tmp/native tests/extra/*.a68
```

## Rosetta Code

See `tests/rosetta/README.md` for the whole procedure.

| Tool | Purpose |
|---|---|
| `rosetta_prep.py` | prepares the ALGOL 68 solutions of a RosettaCodeData checkout for ga68 and a68j (`tests/rosetta/`) |
| `a68supper.py` | converts an UPPER-stropped program to SUPPER stropping; used by `rosetta_prep.py` |
| `rosetta_report.py` | summarizes the runs of ga68 and a68j in `tests/rosetta/results.tsv` |
| `rosetta_list.py` | writes `tests/rosetta/passing.md` and `passing.txt` from `results.tsv` |

## Test scripts

| Tool | Tests |
|---|---|
| `java_tests.sh [JAVA]` | the Java connection (`tests/java`, `docs/java-modules.md`) |
| `par_tests.sh [JAVA [REPEAT]]` | PAR and SEMA (`tests/par`, `docs/par-and-sema.md`) |
| `skip_modes_test.sh [JAVA]` | the modes without a SKIP value in ga68 (`file`, `channel`, `bytes`, `long bytes`): a68j rejects the same declarations as ga68 |
| `loop_methods_test.sh NATIVE_EXTRA NATIVE_ROSETTA [JAR]` | the extra tests and the Rosetta programs with every loop outlined into a method of its own (`A68J_LOOP_METHODS=every`) |

## Performance

See `docs/optimizations.md` for how the measurements are made.

| Tool | Purpose |
|---|---|
| `perf_compile.sh FILE.a68` | compiles one Rosetta program to classes, so that the timing does not include the compiler |
| `perf_run.py` | runs the compiled programs one after the other and records the times |
| `perf_ab.py` | compares two or three compiled versions program by program with alternating runs |
| `MethodSizes.java` | prints the bytecode size of each method of class files (HotSpot does not JIT-compile methods above 8000 bytes) |
| `build_hsdis.sh` | builds the HotSpot disassembler plugin, so that `-XX:+PrintAssembly` prints x86-64 instructions |
