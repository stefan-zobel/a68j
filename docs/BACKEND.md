# a68j stage 3: JVM backend — design rules

The backend translates the tree that the front end (stages 0–2) produces
after semantic analysis into JVM class files, using the Class-File API
(`java.lang.classfile`, JDK 25). It is new code, not a port: GCC's lowering
(`gcc/algol68/a68-low*.cc`) produces GENERIC trees. But the lowering files
are the **reference for the semantics** of every construct (evaluation order,
bounds checks, copying, runtime errors), and the backend is organised along
the same lines, one generator class per lowering file.

Front end: `src/main/java` (Java 21). Backend: `backend/src/main/java`
(Java 25), packages `a68j.jvm` (compiler) and `a68j.rt` (runtime library).

## 1. Value representation

Every mode has a JVM representation (`JType.of(a68, moid)`):

| Mode | JVM | Notes |
|---|---|---|
| VOID | (none) | a voided unit leaves nothing on the stack |
| BOOL | `boolean` (Z) | |
| CHAR | `int` (I) | UCS-4 code point, as ga68 (`uint32`) |
| SHORT SHORT INT, SHORT INT, INT | `int` (I) | results narrowed with `i2b`/`i2s` for the short modes |
| LONG INT, LONG LONG INT | `long` (J) | ga68 on x86_64: both 64 bit |
| SHORT SHORT BITS … BITS | `int` (I) | unsigned semantics in comparisons, shifts, conversions |
| LONG BITS, LONG LONG BITS | `long` (J) | |
| REAL | `float` (F) | ga68: C `float` |
| LONG REAL, LONG LONG REAL | `double` (D) | LONG LONG REAL is `long double` in ga68: known deviation |
| REF m | `a68j.rt.Ref` | `NIL` is `null` |
| [] m, FLEX [] m (incl. STRING) | `a68j.rt.Row` | descriptor + element storage (`Object[]`, or unboxed, see below) |
| STRUCT (...) | `a68j.rt.Struct` | `Object[] f` of boxed fields; value semantics |
| UNION (...) | `a68j.rt.Union` | `int mode` (moid number) + boxed `Object value` |
| PROC ... | `a68j.rt.Proc` | `Object call(Object[] args)` |
| FILE, CHANNEL | `int` (I) | as ga68 (file descriptor) |
| anything else | `Object` | |

Boxed form (inside rows, structs, unions, refs, frames, proc args/results):
`Boolean`, `Integer`, `Long`, `Float`, `Double`, or the object itself.
`Gen.box(moid)` / `Gen.unbox(moid)` convert between the two. VOID results
of a `Proc` are `null`.

Unboxed rows: the element storage of a row (`Row.store`) is an `Object[]` of
boxed elements, or for a row of a primitive mode an `int[]` (INT, CHAR,
BITS and the short modes), `long[]`, `float[]`, `double[]` or `boolean[]`.
Generators of such rows (`[n] INT a`), copies (`Row.copy`), strings
(`Row.ofString`, the string operators) and `[] BOOL` from BITS make unboxed
storage; row displays and slices of rows of structures (`re OF zs`, elements
reached through `Row.path`) stay boxed. Both kinds can occur for the same
mode, so every access goes through `Row.get/set` or the typed
`getInt/setInt …`, which test the storage class. The code generator uses the
typed accessors for the frequent element patterns (`GenRows.loadElem`,
`storeElem`): `a[i]` as a value, `a[i] := e` and `a[i] +:= e` voided, so
neither the element's name (`ElemRef`) nor a box is made. The same holds for
fields: `x OF p` and `x OF p := e` through a name `p` use the structure
directly (`Rt.derefStruct`, `Rt.assignField`) instead of a `FieldRef`. The
order of evaluation is unchanged: the element or field is selected (bounds
and NIL checked) before the source is evaluated. `(a[i] +:= e)` whose new
value is used (dereferenced) does not make the element's name either.

Element indices are `int`s (`Row.index1`, `Row.indexOf`, `Rt.elemI(Row,
int)` ...): bounds and subscripts are INTs, so the index of an element in
bounds fits, and the JIT recognises a subscript that is a loop counter.
For a slice with one subscript the code generator emits the bounds check
and the index (`Row.lwb0`, `n0`, `stride0`) and the load or store of the
element of compact storage (`instanceof int[]` ..., else `Rt.elemI`)
inline, without calls. This matters because the JIT compiles a long method
such as `program()` for on-stack replacement when its first hot loop runs,
and compiles the code after that loop without a profile: there it inlines
hardly any call ("low call site frequency", "virtual call", "too big"), so
every `ps[j]` stayed a chain of calls. `ruth-aaron-numbers` went from 14.9
to 8.2 s with this. For the same reason INT `OVER` and `MOD` are `idiv`/`irem`
inline (with the correction of a negative remainder for MOD); only a
divisor 0 or -1 calls `Rt.over`/`Rt.mod` (SIGFPE as ga68, or the result).

Inside a loop, the descriptor of a one-dimensional row that the loop
cannot change (a row variable whose name never escapes and that the loop
does not assign, or an identity of a row mode) is read once on entry
into JVM locals (`Hoist`): the row, its typed store, `lwb0`, `offset`,
and `n0` (0 unless the stride is 1 and the store is typed). An access is
then a bound check against the locals and `arr[off + i]`; everything else
(a bound error, NIL, another stride, an `Object[]` store) takes the code
above. `A68J_HOIST_ROWS=off` turns it off (docs/optimizations.md,
section 8).

Value semantics: storing a STRUCT or ROW value into a name, a field or an
element copies it (`Rt.copyValue(Object)`, deep for nested structs/rows), as
in ga68 (`a68_lower_memcpy` of the value). This includes a united value whose
value is a row or a structure (`union (void, string)` assigned a slice of a
buffer keeps its own characters). Names (`Ref`) are never copied.

String and character denotations: the front end reads the source byte by byte
(ISO 8859-1, like ga68), so a non-ASCII character is a sequence of UTF-8
bytes in the symbol. `GenUnits.processBreaks` expands the string breaks and
then decodes UTF-8, like `a68_string_process_breaks` and `a68_u8_to_u32`:
"Möbius" has 6 characters, `ABS "ö"[1]` is 246.

## 2. Names (`a68j.rt.Ref`)

`abstract class Ref { Object get(); void set(Object v); boolean same(Ref o); }`
Subclasses: `Cell` (LOC and HEAP generators, variable declarations),
`IntCell`, `LongCell`, `FloatCell`, `DoubleCell`, `BoolCell` (the same for
primitive modes, holding the value unboxed in a field `v`),
`ElemRef` (row element: the row's storage array, boxed or primitive, + index), `FieldRef` (struct + field
index), `RowRef` for FLEX names that can be re-assigned with a new row.
`:=:` and `:/=:` use `Rt.isSame(Ref, Ref)` (null-safe). Dereferencing `NIL`
is the runtime error "dereferencing NIL" (see §6).

Dereferencing a name of a primitive mode and assigning to it go through
`Rt.derefI/assignI` (L, F, D, Z): an `IntCell` is read and written directly,
any other name (an element, a field) through `get/set`. A variable declared
in the compilation unit (not a direct one, §3) is known to be the `Cell` or
typed cell made by its generator (`Gen.ownCell`): dereferencing it,
`x := e`, `x +:= e` and slicing a row variable use the field of that class
(`getfield IntCell.v`, `Cell.value`) after an inline NIL check (a variable
whose declaration has not been elaborated yet is NIL), again without a
call the JIT would have to inline. The voided patterns
`a[i]`, `a[i] := e`, `a[i] +:= e`, `x OF p`, `x OF p := e` through a name do
not make the name of the element or field at all (§1).

## 3. Identifiers, frames, procedures

- Each routine text (and the particular program) is compiled to a private
  static method `r<N>(Frame env, Object[] args) -> Object` of the program
  class. A procedure value is `new Closure(MethodHandle, Frame)`; the
  `MethodHandle` is loaded with `ldc` of a `DirectMethodHandleDesc`.
- `a68j.rt.Frame { final Frame up; final Object[] s; }`. One frame per
  invocation of a routine, holding every identifier of that routine that is
  **captured** (used inside a nested routine text). `up` is the frame of the
  lexically enclosing routine. Other identifiers live in JVM locals.
- `Capture` (pre-pass) decides storage per `Tag`: `Storage.local(slot, jtype)`
  or `Storage.frame(depth-owner, index)`; access walks `up` links.
- Variables (`ref m x`, `loc m`, `heap m`) hold a `Ref` in their storage.
- **Direct variables** (`Direct`): a LOC variable of a primitive mode (INT,
  REAL, BOOL, CHAR, BITS and their LONG/SHORT forms) that is not captured and
  whose name is never used as a value — every applied occurrence is
  dereferenced, the destination of a voided assignation, or the left operand
  of a standard assigning operator that is voided or dereferenced (`x +:= 1`,
  `a[n +:= 1]`) — holds its value unboxed in a JVM local; these patterns
  compile to loads and stores (`GenCoercions.dereferencing/voiding`,
  `Intrinsics.assigningOperatorDirect`). `A68J_DEBUG_DIRECT=1` lists them
  while compiling.
- **Conditions:** an enquiry clause that is a single comparison of two INT or
  LONG INT values (not BITS) with a standard operator jumps with
  `if_icmp<cond>`/`lcmp` without making a BOOL (`GenClauses.branchIfFalse`,
  `Intrinsics.branchIfFalse`); comparisons that yield a BOOL use
  `if_icmp<cond>` too instead of `Integer.compare`. A loop without FOR, FROM,
  BY and TO has no iterator (as `a68_lower_loop_clause`).
- **Generators** of a one-dimensional row of a primitive mode (`[1:n] int`)
  call `Row.make1(lwb, upb, kind)` instead of interpreting the mode
  description in `a68j.rt.Gen`; `LWB a`/`UPB a` of a row read its first
  bound without uniting the row to ROWS.
- Operators declared by the user are procedures (the OP tag holds a Proc).
- **Direct calls:** a procedure or brief operator declared with a routine
  text (`proc f = (int n) int: ...`, `op X = ...`; `Gen.staticProcs`) is
  elaborated when its range is entered and cannot change, so a call of it
  (`f (x)`, `X y`, `a X b`, deproceduring `f`) invokes its method directly
  with the frame the routine text would have captured (`GenUnits.directCall`,
  found by walking the static chain). Such a routine has a typed entry
  `r<N>t(Frame env, <parameters unboxed>) -> <result unboxed>`; the boxed
  `r<N>(Frame, Object[])` only unboxes and calls it, for closures. The order
  of evaluation is unchanged (the procedure value has no side effects).
- **Direct calls of Java formal holes** (-fnest-java): an identifier or
  operator declared with a Java formal hole of a procedure mode
  (`Gen.javaProcs`, found by `Capture`) is called with invokestatic of the
  Java method itself, the arguments unboxed and converted as in the bridge
  method (`GenJava.directCall`); a Row parameter gets a copy as before.
  Used as a value it is the closure over the bridge as before.
- **Typed entries of modules:** a module class has for every public
  procedure or operator declared with a routine text at the top of the DEF
  part a `public static pub$i(parameters unboxed) -> result unboxed`
  (`Gen.emitTypedPubEntries`; i is the index in `pubKeys()`), which
  elaborates the module if needed (as `pub()`) and calls the typed method
  of the routine with the module's frame. A public identifier or operator
  declared with a Java formal hole of a procedure mode gets one too; it
  converts the arguments and calls the Java method as a direct call does
  (`GenJava.emitPubEntry`). Calls of module tags
  (`Intrinsics.callModuleTag`) are an invokedynamic with the arguments
  unboxed; the bootstrap `Modules.bsmCall` links it to `pub$i` if the
  module has one of exactly that type, otherwise to the boxed procedure
  value (`Proc.call` with an `Object[]`, adapted with `asType`), so module
  classes of older a68j versions still work. `A68J_DEBUG_LINK=1` reports
  each linkage.
- **Values shared without a copy:** identities and parameters of a ROW,
  STRUCT or UNION mode (`Gen.valueConsts`) hold values that no name refers
  to and that cannot change. A value taken from one of them (the identifier,
  or a slice or selection of it; `GenUnits.ownedValue`) is bound to another
  identity or passed as an argument without `Rt.copyValue`. Assignations
  always copy. A Java bridge passing a Row as it is copies it itself.
- **No SKIP value nobody sees:** `GenDecls.prologue` gives an identity of a
  ROW, STRUCT or UNION mode null instead of a SKIP value built by
  `a68j.rt.Gen` when its serial clause has no labels, it is not captured
  (lives in a JVM local), and it does not occur in the text before its
  declaration (`GenDecls.skipNotNeeded`, `Gen.numberNodes`).
- **Routines without a frame:** a routine text without captured identifiers
  and labels (`Routine.hasFrame`) makes no frame; its `frameLocal` is its
  environment `env`. `Gen.loadFrame` counts one `up` step only for the
  routines that have a frame, so the static chain stays correct. Jumps need
  the frame of the routine that declares the label, so such routines keep
  theirs.

## 4. Standard environment and modules

A tag from the standard environment (`tag.symbolTable == a68.standenv`) or
from an imported module (`tag.moif != null`) is an **intrinsic**.
`a68j.jvm.Intrinsics` either emits inline bytecode (`+` on INT is `iadd`,
followed by narrowing for SHORT modes) or calls a static method of
`a68j.rt.Std` (STANDARD prelude) or `a68j.rt.Posix` (POSIX module, e.g.
`puts`). Std method names are the mangled Algol name (monadic operators
prefixed `op`) followed by one code per operand mode, because modes with
the same JVM type need different code: `opabs_i` (ABS INT), `opabs_bi`
(ABS BITS), `opabs_c` (ABS CHAR), `$plus_s_s` (+ on strings), `lwb_i_rows`.
Codes: `i si ssi li lli`, `bi sbi ssbi lbi llbi`, `r lr llr`, `c`, `b`,
`s` (strings), `cx lcx llcx` (COMPL, LONG COMPL and WORD COMPL, LONG LONG
COMPL), `rows`, `row`, `ref`, `struct`, `union`, `proc`. Standard
procedures used as values become a `Proc` that calls the method by
reflection (`Std.procValue`); a deproceduring of a standard procedure calls
it directly. `a68j.jvm.StdCheck` lists the standard-environment tags without
a method. The flag
`Rt.strict` (-std=algol68) selects the strict variants (e.g. BIN, ABS on BITS). Reference for the semantics:
`a68-low-prelude.cc`, `a68-low-ints.cc`, `-reals`, `-bits`, `-bools`,
`-chars`, `-strings`, `-complex`, and libga68 (`/home/me/src/gcc/libga68`).

**Compiled modules.** The Algol 68 part of libga68 (`standard.a68`: whole,
fixed, float, the COMPL operators, bits pack, ...) is compiled by the backend
itself at build time (`-fbuilding-libga68 -fclass=a68j.lib.Standard`). A
module (`module X = def ... fed`) compiles to a class whose `program`
method elaborates the DEF part; every identifier declared there lives in the
module's frame, which is kept in a static field. `pubKeys()` gives the keys
of the public tags (mangled name plus a structural description of the mode,
`Intrinsics.moduleKey`, independent of the order of union members), `pub()`
elaborates the module once and gives their boxed values. Code using a tag of
a compiled module loads it with `ldc` of a dynamic constant bootstrapped by
`a68j.rt.Modules.bsm(cls, key)` (resolved once per use site) and calls the
`Proc`.

**Separate compilation of modules** (prelude packets, `module X = ...
fed`). Compiling `x.a68` writes the class (named after the module:
`MODULE_X` gives `Module_x`) and `x.m68`, the module interface: the bytes ga68
writes to the `.a68_exports` section (`Exports`, a port of a68-exports.cc,
byte-identical with ga68 for all module packets of the testsuite). A program
or module accessing `X` finds `x.m68` through `-I` (or `-fmodules-map`,
`-fmodules-map-file`) with the ported `Imports`; the classes must be on the
class path when running. As `lower_module_text`, the class has `prelude()`
(increments a sentinel; the first call runs the preludes of the modules of
its revelation part and then elaborates the DEF part) and `postlude()` (Sic:
increments the sentinel too and would run only at 0, i.e. never, so the
postlude code is not compiled). An ACCESS clause calls the preludes, the
controlled clause and the postludes (`GenClauses.accessClause`; Sic as
`a68_lower_revelation_ludes`: modules of nested ACCESS clauses are included).

**Library jars** (the analogue of `libX.so`/`libX.a`): with `-d libX.jar`
the compiler adds the class of the packet and its export data (entry
`a68j/exports/<base>.m68`) to the jar, creating it if needed, so several
module packets can be compiled into one library one after the other. When
an ACCESS clause needs the exports of `X`, the ported search
(`Imports.tryPacketInDirectory`, `a68_try_suffixes`) tries in each -I/-L
directory `X.m68`, then `libX.jar`, then ga68's `libX.so`, `libX.a`, `X.o`.
As the linker concatenates the `.a68_exports` sections of the objects of a
shared library, the export data of a jar is the concatenation of its
`a68j/exports/*.m68` entries (sorted by name); `-fmodules-map=lib=A,B`
maps several modules to one jar exactly as for ga68. A jar without export
entries is an error ("exists but does not contain any export data") and
ends the search, as for an object file. `-run` puts the jars it read
(`A68.libraryJars`) and the other `lib*.jar` of the -I/-L directories (for
the modules accessed by those modules: with ga68 all would be linked) on the
class path of the program; `Modules.bsm` loads module classes through the
loader of the program. A separately compiled program needs the jars on its
class path (`java -cp a68j-backend.jar:lib/libX.jar:out Prog`).
`run_jvm.py --modules-as-jars` compiles every module packet of the
multi-packet tests into `libX.jar` instead of `X.m68` and a class
directory; all 25 pass.

POSIX is not compiled: `a68j.rt.Posix` implements it in Java on
file descriptors (0, 1, 2 standard streams, 3... files) with libga68's errno
behaviour. A file is a `FileChannel`, whose open options correspond to the
flags of open(2): `fopen` never creates a file (the default mode tries
read-write, read-only and write-only, as libga68), a descriptor of
`fcreate` is write-only, and a descriptor refuses the direction it was not
opened for (EBADF). errno is copied from a modelled C errno by `fopen`,
`fcreate`, `fclose`, `fputs`/`puts` and `lseek`, also when they succeed,
as libga68 does; every failing operation, including the read of `fgetc`
and the write of `fputc`, sets that C errno, and nothing clears it. A
string with a character that has no UTF-8 encoding (a surrogate, or the
EOF character) is not written: `fputs` and `puts` return 0 and set errno
to EILSEQ (84), `fopen` and `fcreate` fail with EFAULT (14), as libga68
passes a null pointer then. `fsize` and `lseek` also work on descriptors 0
to 2 (a redirected file has its size and position, a pipe or a terminal
size 0 and ESPIPE), and `fcreate` gives a new file the permission bits of
its mode less the umask, as creat (the bits 07000 by the "unix" attribute
view of the JDK); `lseek` on a device that ignores the position, such as
`/dev/null`, yields 0 for any offset, as in Linux.

## 5. Code generator structure

`a68j.jvm.Gen` holds the state (ClassBuilder, current CodeBuilder, locals,
current routine, label maps) and dispatches on the node attribute like
`a68_lower_tree`. Generators, all `static` methods taking `(Gen g, Node p)`:

| Class | Reference | Constructs |
|---|---|---|
| `GenClauses` | a68-low-clauses.cc | serial, closed, conditional, case, conformity, loop, collateral, labels, jumps, completers |
| `GenUnits` | a68-low-units.cc | identifiers, denotations, skip, nihil, formulas, assignation, identity relation, andf/orf, slice, selection, cast, call, generator, routine text |
| `GenDecls` | a68-low-decls.cc | identity/variable/procedure/operator declarations |
| `GenCoercions` | a68-low-coercions.cc | dereferencing, deproceduring, proceduring, uniting, rowing, widening, voiding |
| `GenRows` | a68-low-multiples.cc, -ranges, -structs, -unions | descriptors, bounds checks, trimmers, struct/union helpers |
| `GenClauses.accessClause`, `Gen.emitModuleAccess` | a68-low-clauses.cc, a68-low.cc (`lower_module_text`) | ACCESS clauses, module classes (prelude, postlude, public tags) |
| `Capture`, `Direct` | — | pre-passes: frame slots of captured identifiers; direct (unboxed) variables |

Contract: `g.unit(p)` evaluates `p` and leaves a value of `JType.of(p.type)`
on the operand stack (nothing for VOID). Line numbers: `g.line(p)` emits a
`LineNumberTable` entry so JVM stack traces point at the Algol source.

## 6. Runtime errors and exit status

As libga68 (`ga68-error.c`): message `FILE:LINE: runtime error: TEXT` on
stderr, then abort. The JVM cannot raise SIGABRT, so `Rt.error` prints and
calls `Runtime.halt(134)`; the test harness treats native signal 6 and JVM
exit 134 as equal. Integer `%`, `%*` (OVER, MOD) with divisor 0, or of the
most negative value by -1, trap with SIGFPE on x86-64 and end the ga68
program without a message; `Rt.over/mod` end with status 136 (128 + 8)
instead. Normal end: exit status 0.

`MODAB` (`%*:=`) is lowered by ga68 with `TRUNC_MOD_EXPR`, the remainder of
C, which is negative for a negative left operand (`-255 %*:= 256` gives
-255, while `-255 %* 256` is 1); `Rt.rem` does the same.

The generated `main` runs the program (`run0`) in a thread with a stack of
128 MB (`Rt.start`), because deep recursion is common in Algol 68 programs
and the JVM's default stack is small. A `StackOverflowError` ends the
program like the segmentation fault of a native program: no message, status
139 (128 + 11).

Standard identifiers and operators that ga68 declares without a lowering
routine (`LOWERER_UNIMPL` in a68-parser-prelude.cc: the SEMA operators
`LEVEL`, `UP`, `DOWN`, BYTES, `next random`, `first random`, `rnd`,
`long infinity` ...; 93 entries, `Tag.unimplemented`, set by
`Prelude.idfUnimpl/opUnimpl`) are a compile-time error as in ga68
(`a68_lower_unimplemented`): `FILE:LINE:COL: fatal error: no lowering
routine installed for construct.  jemarch has been lazy`, then
`compilation terminated.`, status 1 (`Gen.Fatal`, checked in tree order
before code generation). The 12 Rosetta solutions ga68 rejects this way
are rejected by a68j at the same line and column. In the same walk
(`Gen.checkLowering`), a68j rejects the declarations and clauses for which
ga68 cannot build a SKIP value (`a68_get_skip_tree`): ga68 has none for
`file`, `channel`, `bytes` and `long bytes`, nor for a structure with such
a field or a union whose first alternative is one, and needs one for an
identity declaration, a LOC variable declaration without rows, a SKIP and
the result of a closed, conditional, case or conformity clause. The
message is ga68's, `fatal error: get skip tree: cannot compute SKIP for
mode file`; `tools/skip_modes_test.sh` holds 19 cases measured against
ga68. A PARALLEL_CLAUSE is
elaborated like a VOID collateral clause, one unit after the other, as
`a68_lower_parallel_clause` ("for now treat like a VOID collateral clause").

**-fpar-threads** (an a68j extension, `Options.parThreads`; details in
`docs/par-and-sema.md`): the SEMA operators are allowed
(`Gen.isSemaOperator`) and compiled to `a68j.rt.Par.level/up/down`
(`Intrinsics`, monadic operators); SEMA is `STRUCT (REF INT)` as in the
Report, its `IntCell`'s monitor guards the value. Capture makes every unit
of a PARALLEL_CLAUSE a routine of its own (`Routine.parUnit`, a method
`(Frame, Object[]) -> Object` like a pseudo-routine, with its own frame;
what it uses from outside goes to the frames of the enclosing routines), and
`GenClauses.parallelClause` passes an array of closures over the current
frame to `Par.run`. `ParRun` starts and joins the threads: with plain
threads in the JDK 25 version, with a `StructuredTaskScope`
(`Joiner.awaitAllSuccessfulOrThrow`) in the JDK 28 version
(`src/main/java28`, `META-INF/versions/28` of the multi-release jar). The
first unit that fails (a `Jump` out of the clause, a runtime error) cancels
the others by interrupting them (`DOWN` then ends with `Par.Cancelled`), and
its exception is rethrown in the thread of the clause. Threads are platform
threads with the 128 MB stack of the main thread (`-Da68j.par=virtual`:
virtual threads). Once a PAR has run (`Par.threads`) `random` is
synchronized; the POSIX file functions always are.

**-fnest-java** (an a68j extension, `Options.nestJava`; details in
`docs/java-modules.md`): a formal hole `nest Java "pkg.Class.member"` (the
front end accepts the language indication `Java`/`JAVA` and checks the
mode with `MoidsMisc.isJavaMode`). `GenJava` looks the class up when the
program is compiled (`GenJava.loader`: library jars, `-fjava-classpath`,
the compiler's class path) and finds the public static method whose
parameters and result fit the procedure mode, or the static field; a
method reference `"pkg.Class::name"` or `"pkg.Class::new"` names an
instance method (the object is the first parameter), a static method or a
constructor, or, if no method fits, a public field that is read or written
(`GenJava.Target`, `GenJava.findField`). Of several overloads the cheapest
is taken; several equally cheap ones are a compile-time error
(`GenJava.best`). Type arguments in a method
reference (`"java.util.ArrayList<String>::get"`, parsed by `JavaGenerics`)
bind the type variables of the class in the types of the member
(`GenJava.Target.bound`); such a result is checked by `JavaConv.checked`.
Objects of classes that do not extend
`Ref` are wrapped in an `a68j.rt.JavaHandle`. For a
method it emits a bridge `(Frame, Object[]) -> Object` (unbox, convert with
`a68j.rt.JavaConv`, invokestatic, convert back, box), emitted after the
routines (`Gen.javaBridges`); the value of the hole is a `Closure` over it.
A Java object is a name that refers to nothing: its class extends
`a68j.rt.JavaObject` (a `Ref` whose `get`/`set` are runtime errors). Modes
like `REF STRUCT (INT intlist java object)` differ only in the field name,
so module keys (`Intrinsics.moduleKey`) include the field names of
structures. The bundled Algol 68 modules (`Intrinsics.BUNDLED_MODULES`:
`Lists`, `Hashmaps` and `Genie_Transput`, `backend/lib/*.a68`; the last one
uses no Java, see `docs/genie-transput.md`) are compiled by `build.sh` to
`a68j.lib.X` with their export data in the jar (`Compile.bundledLibDir`;
the compiler copies the export data to a cache directory
`a68j-lib-<user>-<hash>` in java.io.tmpdir, one per user, since /tmp may be
shared, or to a private temporary directory if that one cannot be
written).

Not supported (`Gen.Unsupported`, the compiler stops): partial
parametrisation (`f (3, )`, an Algol 68 Genie extension), which ga68 compiles
into calls with missing arguments.

A method with more than the 65 535 bytes of code that the JVM allows, which
ga68 has no counterpart for, is a compile-time error of a68j
(`Gen.TooLarge`): `FILE:LINE:COL: error: the routine text (lines 2 to
4003) needs 163740 bytes of JVM code, more than the 65535 that a method
may have`, a note, status 1. Before that a68j tries to make the loops of
the method methods of their own, to access the elements of rows in it
compactly, and to cut its serial clauses into parts that are methods of
their own (`Cut`); what remains is a single unit too large for one method
and a part that would need more than 200 parameters
(docs/optimizations.md, sections 7, 9, 10 and 11; docs/TODO-method-size.md).

## 7. Values of generators and SKIP

`a68j.rt.Gen` builds fresh values from a compact description of the mode
(`GenRows.desc`, e.g. `R1:S2:il` for `[] STRUCT (INT a, LONG INT b)`) and the
bounds of the actual declarer, collected like `collect_declarer_bounds` in
a68-low-generator.cc and evaluated once per declarer. SKIP values are as
`a68_get_skip_tree`: 0, `" "` for CHAR, NIL, the first union alternative,
empty rows.

Generated values follow ga68's `fill_in_buffer`: INT, BOOL, CHAR, REAL and
names get their SKIP value; any other mode without rows is filled with
zeroes (`memset`), marked `z` in the description, so its CHARs are NUL and a
union in it is united with its first mode (overhead 0); a union with rows
has the overhead -1, so that no conformity case matches; the elements of a
row of INT, BOOL, CHAR, REAL or names are zeroes too (a `[n] CHAR` holds NUL
characters). A LOC variable without rows is not generated in ga68 but is a C
variable initialised with its SKIP value (`GenRows.generateSkip`): a CHAR is
a blank, a union is united with its first mode, also inside a structure.
`tests/extra/generator-init-1.a68` checks these cases against ga68.

A united value carries a number for the mode of its value, deflexed
(`JType.unionTag`), so that STRING and `[] CHAR` agree as in
`a68_united_mode_index`. The number is a hash of the structure of the mode
(`JType.structuralKey`: field names, union members in any order, recursion by
back references; names of modes written in lower case without underscores,
so that the stropping regime does not matter), not the mode number, so that a
value united in one module conforms in another compilation, also when the
module is written in SUPPER and the program in UPPER stropping; a conformity case compares that number with the
number of its specifier (or of every member of a united specifier, which then
gets the united value itself).

**Declarations before use.** As in ga68, the identifiers declared in a serial
clause exist from the start of the clause (`GenDecls.prologue`): identities
and operators hold SKIP until their declaration, LOC variables without rows
get their location at the start (the declaration only performs the initial
assignment), HEAP variables and variables with rows are NIL until their
declaration generates them, and procedures and brief operators are
elaborated at the start, so that they can be called earlier.

**Rows.** A slice with trimmers builds a new descriptor sharing the elements
(`Row.Trim`, as `lower_subscript_for_trimmers`, with the same bound checks);
slicing a name yields a new `Cell` holding the trimmed row. Assignation of a
row to a non-flexible name stores the elements into the existing row after
checking the bounds (`Row.storeInto`); a flexible name gets the new row. A
selection from a row of structures gives a row view with a field path
(`Row.path`): element access goes through `Row.get/set/ref`, so a name of
such a row aliases the fields. Rowing `[] A -> [,] A` adds a dimension
sharing the elements; rowing a non-row name `REF A -> REF [] A` gives a row
[1:1] whose element is reached through the name (path entry
`Row.THROUGH_NAME`), so it aliases the variable as in ga68.

**Parallel clauses** are elaborated like a VOID collateral clause, one unit
after the other, as `a68_lower_parallel_clause` does. SEMA operators have no
lowering in ga68 (fatal error), so they are not supported either.

## 8. Jumps and labels

A jump throws `a68j.rt.Jump(frame, label)` (without stack trace), where
`frame` is the activation of the routine that declares the label. A serial
clause with labels becomes a region entered through a `tableswitch` on a
state local (0 = start, k = k-th label) and protected by a handler for
`Jump`: if the jump belongs to this activation and one of its labels, the
handler sets the state and re-enters the region, otherwise it rethrows. The
identifiers of the clause are initialised before the region (see
declarations before use), so that the verifier accepts the re-entry. The JVM
clears the operand stack on entry to a handler, so a serial clause with
labels must be evaluated with an empty operand stack. `Capture` therefore
tracks whether each node is evaluated at stack depth 0 of its routine
(phrases, clause parts, identity declarations, coercions of those); a
labeled serial clause anywhere else (an operand, an assignation source, a
variable initialiser) becomes a pseudo-routine: a separate method without
parameters whose frame links to the enclosing one, called at its place. A
jump used as a `PROC VOID` (proceduring) is a `Proc` throwing the `Jump`.

## 9. Testing

`tools/run_native.py` records, for every program in
`gcc/testsuite/algol68/execute`, the exit status and output of the binary
that ga68 produces (`/home/me/native/*.json`). `tools/run_jvm.py`
compiles the same programs with a68j, runs them on the JVM and compares
stdout, exit status and the first stderr line; both runners start every
run of a program in a new empty directory. For the multi-packet tests
(`execute/modules`, `{ dg-modules "a b" }`) both runners compile the module
packets first (natively to `.o` files, on the JVM to classes and `.m68`
files in a temporary directory). `tests/extra/` holds additional programs for
cases the testsuite does not cover (division by zero, rowed names, PAR,
direct variables, unions across modules and stropping regimes, UTF-8
strings, copying of united rows, MODAB, deep recursion, unboxed rows and
names, initial values of generators, the bundled module `Genie_Transput`
with its output, its reading and files and `associate`, the file
procedures of the POSIX module and errno, `fsize` and `lseek` on the
standard descriptors); their native
results are recorded the same way (`--native /home/me/native-extra`).

`tests/rosetta/` holds the ALGOL 68 solutions of Rosetta Code, prepared for
ga68 by `tools/rosetta_prep.py` and converted to SUPPER stropping by
`tools/a68supper.py`: each runs inside `access Genie_Transput (...)`,
the bundled module that provides the Algol 68 Genie transput (`print`,
`newline`, `read`, `open` ...) on top of ga68's POSIX prelude (for ga68 the runners compile
it from `backend/lib`, `tools/modtest.py`). The runners take `--default-opts` (options
for files without `dg-options`, e.g. `-fstropping=upper` for UPPER
programs); `run_jvm.py` also `--jit tiered` (full JIT instead of C1 only)
and `--aot auto|none|FILE` (the AOT cache, see below), and
`--rel-eps/--abs-eps` (status `approx`: the output differs only in
floating-point numbers within the tolerance, as for the rounding of `sin`
or `ln` by the C library and by `java.lang.Math`); `run_native.py`
also `--runs N` (programs with changing output are marked nondeterministic
and skipped) and `--stack` (ga68 allocates LOC rows on the stack). Known
differences are listed in `tests/rosetta/notes.tsv`; evaluation order is
the main one: Algol 68 leaves the order of the operands of a formula and of
the two sides of an assignation open, and ga68 inherits GCC's order, which
depends on the shape of the trees (the source of an assignation is
gimplified first unless the destination goes through a checked
dereference; operands of commutative operators may be swapped by `fold`).
a68j evaluates destinations and left operands first.

**Start-up and the AOT cache.** A run of a short program is mostly JVM
start, compiling the program in the same JVM and loading the classes of the
compiler and the runtime. `backend/build.sh` therefore also makes
`backend/a68j-backend.aot`, an AOT cache (JDK 25: JEP 483, 514, 515) from a
training run of `backend/lib/aot-training.a68` (compiled and run once with
`-XX:AOTCacheOutput`): the classes are stored loaded and linked, with the
method profiles of the training run. `java -XX:AOTCache=backend/a68j-backend.aot
-jar backend/a68j-backend.jar -run prog.a68` saves about 0.2 s per program
(0.65 s → 0.45 s for a typical short Rosetta solution). The cache belongs to
this jar at this path (the class path may add directories after it, such as
module directories) and to this JDK; the JVM silently ignores a cache that
does not match, e.g. after rebuilding the jar without it (`A68J_AOT=no`).
`run_jvm.py` uses the `.aot` file next to the jar when there is one.

