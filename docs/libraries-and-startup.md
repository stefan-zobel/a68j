# a68j: library jars, AOT cache and test tools

This guide describes three things added in a later stage: module libraries as
jars, the AOT cache against startup time, and the corresponding options of
the test tools. The technical details are in `docs/BACKEND.md` (§4 and §9).

All examples start from the project root directory and assume a built
backend (`backend/build.sh`, JDK 25).

## 1. Library jars

### Why

In ga68 a module packet (`module X = def ... fed`) is compiled into an
object file. The interface of the module (which modes, operators and
identifiers it exports) is stored in that file as the section
`.a68_exports`. Several object files can be combined into a library
`libX.a` or `libX.so`. When a program with `access X` is compiled, ga68
looks for the interface in `X.m68`, `libX.so`, `libX.a` or `X.o`.

Until now a68j had only the first form: the class `Module_x.class` and the
interface `x.m68` as two separate files in a directory. A **library jar**
`libX.jar` is now the JVM counterpart of `libX.so`: one file with the
classes and the interfaces of one or more modules.

### Compiling a module into a jar

If the target of `-d` ends in `.jar`, the compiler writes there instead of
into a directory:

```sh
java -jar backend/a68j-backend.jar tests/rosetta/rcprelude.a68 -d lib/librcprelude.jar
```

The jar then contains:

```
META-INF/MANIFEST.MF
Rcprelude.class                  the compiled module code
a68j/exports/rcprelude.m68       the interface, byte for byte like ga68's .a68_exports
```

If the jar already exists, it is extended (entries with the same name are
replaced). This way several modules are added to one library one after
another.

### Using a module from the jar

When a program (or another module) with `access X` is compiled, the front
end searches every directory given with `-I` and `-L` in this order:

1. `X.m68`
2. **`libX.jar`** (new, in the place of the shared library)
3. `libX.so`, `libX.a`, `X.o` (as in ga68; a68j also reads their section
   `.a68_exports`)

`X` is the file name that ga68 derives from the module name
(`Rc_Prelude` → `rcprelude`), or the name from `-fmodules-map`. If a jar
that was found contains no interface, that is an error and the search
stops there, exactly as for an object file without export data.

```sh
# compile and run right away
java -jar backend/a68j-backend.jar -I lib -run tests/rosetta/100-prisoners.a68

# compile and run separately
java -jar backend/a68j-backend.jar -I lib tests/rosetta/factorions.a68 -d out
java -cp backend/a68j-backend.jar:lib/librcprelude.jar:out Factorions
```

With `-run`, a68j puts the jars on the class path itself. These are the
jars that were read plus all other `lib*.jar` in the `-I`/`-L`
directories, because a module can itself use modules whose interface the
program never reads (ga68 would simply link all libraries). A separately
compiled program needs the jars given with `-cp`.

Both `-I DIR` and `-IDIR` (likewise `-L`) are allowed, as in GCC.

### Several modules in one library

As with a `.so` made of several object files, the interface of a jar is
the concatenation of all entries `a68j/exports/*.m68`. So that the
compiler finds the modules of a shared library, they are mapped to the
library name with `-fmodules-map`, as in ga68:

```sh
MAP=-fmodules-map=m18=Module_18a,Module_18b,Module_18c
java -jar backend/a68j-backend.jar -I lib $MAP module18a.a68 -d lib/libm18.jar
java -jar backend/a68j-backend.jar -I lib $MAP module18b.a68 -d lib/libm18.jar   # uses Module_18a
java -jar backend/a68j-backend.jar -I lib $MAP module18c.a68 -d lib/libm18.jar
java -jar backend/a68j-backend.jar -I lib $MAP -run program-18.a68
```

(`module18a.a68` etc. come from `gcc/testsuite/algol68/execute/modules`.)
The map can also be given in a file (`-fmodules-map-file=...`), in the
same format as in ga68: `base=Module,Module:base2=Module`.

### Checked

`tools/run_jvm.py --modules-as-jars` compiles every module packet of the
multi-packet tests of the ga68 test suite into its own `libX.jar` instead
of `X.m68` plus a class directory. All 25 programs then run exactly as
with ga68.

## 2. AOT cache against startup time

### Why

On the JVM a short program spends most of its time on startup: the JVM
itself, loading the classes of the compiler and the runtime, and
compiling the program in the same process. JDK 25 can store this
preparatory work in an **AOT cache** (JEP 483, 514, 515). The cache holds
the classes already loaded and linked, plus method profiles that let the
JIT get up to speed sooner.

### Creating it

Besides the jar, `backend/build.sh` automatically creates
`backend/a68j-backend.aot`. To do so the JVM compiles and runs the
training program `backend/lib/aot-training.a68` once, with
`-XX:AOTCacheOutput`. The training program uses the common language
features: rows, strings, structures, unions, procedures, operators, loops
and transput. With `A68J_AOT=no backend/build.sh` this step is skipped.

### Using it

```sh
java -XX:AOTCache=backend/a68j-backend.aot -jar backend/a68j-backend.jar -run program.a68
```

A typical short Rosetta program then starts about 0.2 s faster, for
example `dividuus-numbers` in 0.45 s instead of 0.63 s (ga68 0.43 s).

### Limits

- The cache belongs to exactly this jar at this path and to this JDK.
  Further class path entries may follow the jar, such as module
  directories or library jars.
- If the cache does not fit, for example because the jar was rebuilt or
  a different JDK is running, the JVM silently ignores it. The program
  then runs normally, just without the time saving. `-Xlog:aot` shows the
  reason.
- The cache is not put into the source zip (16 MB, and it depends on the
  machine); `backend/build.sh` creates it anew.

## 3. Test tools

| Tool | New |
|---|---|
| `tools/run_jvm.py --aot auto\|none\|FILE` | AOT cache for the test runs. Default `auto`: the `.aot` file next to the jar, if present |
| `tools/run_jvm.py --modules-as-jars` | compile the module packets of the multi-packet tests as library jars (section 1) |
| `tools/rosetta_list.py` | generates `tests/rosetta/passing.md` and `passing.txt`: the Rosetta solutions that end normally with ga68 and give the same result on the JVM |
| `java -cp backend/a68j-backend.jar a68j.jvm.StdCheck file.a68` | lists standard operations without a runtime method; now marks those that ga68 itself cannot compile (`[ga68: unimplemented]`) |

As before, `run_jvm.py` compares output, exit status and first error line
with the recorded runs of ga68. For timing comparisons use
`--jit tiered -j 1`. Without `--jit tiered` only the C1 compiler runs,
which is two to four times slower for long programs.
