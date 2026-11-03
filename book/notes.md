# Notes: implementation differences found while writing the book

Differences between ga68, a68j and Algol 68 Genie that turned up while the
examples were checked. They are recorded here to be dealt with later. The
book itself mentions a difference only where it matters to the reader:
where an implementation contradicts the Revised Report, or where the
reader would otherwise be surprised (sizes of `int` and `real`, and
similar).

| Found in | Program | ga68 | a68j | a68g | Revised Report |
|---|---|---|---|---|---|
| ch. 2 | `2 ** -1` | 0 | 1 | | undefined (10.2.3.4 p: `(b >= 0 | ... )` has no else part) |
| ch. 2 | `print (9.81)` | `+9.810001e +0` | `+9.810001e +0` | | last digit wrong: `float` of a 32-bit `real` (ga68 bug report #4) |
| ch. 4 | `mode Vec = [1:3] real; Vec v := (1.0, 2.0, 3.0)` | internal compiler error in `a68_low_assignation` (a68-low.cc:1053) | works | | valid program; the same with `[1:3] real v := ...` works in ga68 |
| ch. 4 | `print` of a structure | not accepted by `Genie_Transput` (no straightening of structures) | same | works | `print` straightens structures (10.3.2.3) |
| ch. 5 | `proc ap = (proc (real) real f, real a) real: f (a); ap (sin, 1.0)` | internal compiler error (endless recursion in `collect_call_arguments`, a68-low-units.cc:1162) when a procedure of the standard prelude (`sin`, `sqrt`) is passed directly as an argument or used in a row display such as `[] proc (real) real fs = (sin, cos)`; a routine text or a declared procedure works | works | | valid program |
| ch. 5 | a routine text that uses a local variable, assigned to an outer `proc` variable or returned from its block | not detected; the program runs | not detected; the program runs | warning at compile time, run-time error "value is exported out of its scope" | scope rule (RR 5.2.1.2, 7.2.2): the scope of the value must not be newer than that of the name |
| ch. 6 | `compl` operations `+ - * /`, `ABS`, `ARG`, `CONJ` | link errors: `STANDARD_u_4`, `STANDARD_m_8`, `STANDARD_r_11`, `STANDARD_s_14`, `STANDARD_ABS18`, `STANDARD_ARG19`, `STANDARD_CONJ15` undefined (libga68 lacks them) | work | work | RR 10.2.3.8 |
| ch. 6 | `long long real` | x87 extended precision (`long_long_small_real` = 1.08e-19) | the same as `long real` (2.22e-16) | multi-precision | implementation-defined |
| ch. 6 | `print (long_max_real)` | `+1.79769313486235e+308` | same | | last digits wrong (should be ...232e+308); `float` on `long real` near the limit |
| ch. 7 | a reference to a local cell assigned to an outer variable or returned from its block (`ref int p; begin int x := 1; p := x end`) | not detected; the program runs | not detected; the program runs | warning, run-time error "value is exported out of its scope" | the assignment requires that the value be not newer in scope than the name (RR 5.2.1.2 b); otherwise "the further elaboration is undefined" (RR 2.1.4.3 b), so a check is allowed, not required |
| app. E | a module that declares an array variable, `[1:100] int items;`, used from the accessing program | run-time error "bound 3 out of range [76:...]": the array lives on the stack of the module's prelude, which has returned | works | (no modules) | ga68 extension; scalars work, `heap [1:100] int items` works |
| app. A | `2 UP 3` (`UP` as the power operator, RR 10.2.3.1: `↑ = 8, ** = 8, ... UP = 8`) | "dyadic operator int UP int has not been declared" | same | | `UP` is a standard spelling of `**` |
| app. E | a module with a `postlude` part (`def ... postlude puts ("..."'n") fed`), accessed by a program | the postlude is not executed | not executed (as ga68) | (no modules) | GNU extension; the postlude should run when the program ends |
| review | `print` of numbers | no blank before a number that does not begin a line | same | same | RR 10.3.3.1: `(c OF cpos /= 1 | " " PLUSTO s)` puts a blank before every number not at the start of a line; mentioned in ch. 2 |
| review | a `ref flex [] int` argument for a `ref [] int` parameter | accepted | accepted | accepted | different name modes (RR 2.1.3.4 f); dereferencing only deflexes values; mentioned in ch. 7 |
| ch. 7 | `[] [] int jag = ...; flex [1:0] flex [1:0] int rag; rag := jag` (also as the initial value) | internal compiler error in `fold_convert_loc` (fold-const.cc:2620); assigning a row display works | works | works | valid program |
| ch. 7 | `flex [] int b = (1, 2, 3)` (an identity declaration with `flex`) | accepted | accepted | accepted | `flex` occurs only under `ref` (RR 4.6.1, 7.4.1); mentioned in ch. 7 |
| ch. 7 | `loc` cell allocated in a loop body that declares something, stored in an outer variable | not detected | not detected | run-time error "value is exported out of its scope" | the cell's range is the loop body (RR 5.2.3.2 b); without a declaration in the body the program is valid |
| ch. 7 | a routine that uses a parameter of the enclosing procedure (`compose`, `adder`) or a name declared in the block it is returned from, even with `heap` data | not detected; the program runs and gives the expected result | same | run-time error "exported out of its scope" | scope of a routine (RR 7.2.2 c, 5.4.1.2) |
| ch. 7 | conformity clause without `out`, no alternative matches, value used | yields 0 | yields 0 | yields 1 | the clause yields `skip` (RR 3.4.2 b): undefined; not a bug, mentioned in ch. 7 |
| ch. 8 | `LEVEL`, `DOWN`, `UP` (a68j: without the option `-fpar-threads`) | "fatal error: no lowering routine installed for construct" | the same message | works | a68j supports semaphores only with `-fpar-threads`; its message should say so |
| ch. 4 | `mode Student = struct (string name, [1:3] int marks); [] Student class = (("Ann", (1, 2, 3, 4)), ...); Student s := class[1]` | no bounds check: `s` holds four marks | same | run-time error "rows have different bounds" | the assignment requires equal bounds of the row field (RR 5.2.1.2 b) |
| ch. 4 | `proc f = ([] int b, ref [] int a) void: (a[1] := 99; print (b[1])); f (x, x)` | prints 1 | prints 1 | prints 99 | `b` is a copy (identity declaration of a row value); a68g shares the storage |
| review | `for i from 5 by 0 to 1 while ... do` (a step of 0, start above the final value) | the loop is not entered | same | the loop runs (the final value is ignored) | RR 3.5.2: with `b = 0` the final value is not tested; mentioned in ch. 3 |
| app. G | `proc add = (real x, y) real: x + y; proc (real) real g := add (3, )` (partial parametrization, Lindsey AB 39.3.1) | front end accepts it; "internal compiler error: in fold_convert_loc, at fold-const.cc:2620" | "a68j backend: partial parametrisation not supported yet" | works | not in the RR; an extension released by the WG 2.1 subcommittee (1976). ga68 should reject it with a message instead of crashing |
| app. G | `proc int tick = incr (counter, 1)` (a call with all arguments in a context that requires `proc int`) | (see above) | (see above) | compile error "INT cannot be coerced to PROC INT" | in the proposal the call yields a routine without parameters, which is deprocedured only where the context requires a value (its example `PROC REAL ncossini = (p \| ncos \| nsin) (i)`); a68g calls the routine at once; mentioned in app. G |
| app. F | `nest C "abs"` (formal hole for C) | works | "a68j backend: formal hole not supported yet" (only `nest Java` with `-fnest-java`) | syntax error: `NEST` not known | GNU extension, after the modules proposal (Lindsey and Boom, AB 43) |
