# Bug reports for ga68 and libga68

These are defects in ga68 (the GNU Algol 68 front end of GCC) and in
libga68 that came up while porting ga68 to the JVM. Each entry has a
small program that shows the defect, what happens, what should happen,
the cause in the sources, and a suggested fix. The entries are written
so that they can be filed one by one.

Version: GCC trunk 17.0.0 20261031 (commit 3f35c3d2d116), x86_64 Linux,
glibc. The programs use SUPPER stropping and are compiled with

    ga68 -fstropping=supper prog.a68 -o prog

Algol 68 Genie 3.1.2 (`a68g`) served as the reference where a program
has an obvious expected result. Line numbers refer to the commit above.

Entries 2 to 4 have been filed in the GCC Bugzilla (PR 128380, PR 128382
and PR 128383, linked at the entries); the others have not. Entry 1 was
filed as PR 128378 and has been fixed in GCC; it has been removed, and
the other entries keep their numbers. Entries 10
to 13 have reproducers and the measured behavior, but their causes in the
sources have not been examined yet. Entries 16 to 22 were found while the
examples of the book were checked; the causes of 16 to
19 have been examined only as far as their entries say. Entry 23 was
found in the source; what ga68 does with it has not been measured.

The last column of the summary says what a68j does: it reproduces the
behavior of ga68 where the output is still deterministic, and is correct
where ga68 crashes or reads uninitialized memory. Since version 0.7.0
a68j corrects some of the defects it used to reproduce ("fixed" in the
last column). The expected results of the tests whose output changes
with that are in `tests/ga68/fixed`, `tests/extra/fixed` and
`tests/rosetta/fixed`; they take
the place of the results of ga68 (`tools/run_jvm.py --fixed`).

| # | Component | Summary | a68j |
|---|---|---|---|
| 2 | libga68 `standard.a68.in` | `string_to_real` ignores the exponent part | fixed |
| 3 | libga68 `standard.a68.in` | `fixed` with width 0 loops forever for large values, and `fixed` and `float` with any width for an infinite value | fixed |
| 4 | libga68 `standard.a68.in` | `fixed` on `real` gives wrong digits, e.g. "15999999.0" for 1.6e7 | reproduces |
| 5 | libga68 `standard.a68.in` | `float` on `long real` is wrong in the last digits for large exponents | reproduces |
| 6 | libga68 `ga68-posix.c` | `fputs`, `perror`, `fopen`, `fcreate`, `getenv` and `fconnect` use the result of `_libga68_u32_to_u8` without checking it for NULL | defined result |
| 7 | libga68 `ga68-posix.c` | `argv (0)` reads `argv[-1]` and crashes | returns "" |
| 8 | ga68 `a68-low-strings.cc` | `a68_string_from_char` allocates 1 byte for a 4-byte character | not affected |
| 9 | ga68 | a routine text that is called after the procedure that yielded it has returned jumps into a dead stack frame | correct |
| 10 | ga68 | `flex [1 : n] char x` gets the bounds `[1 : 0]` | reproduces |
| 11 | ga68 | `%*:=` (`MODAB`) computes the C remainder, unlike `MOD` | fixed |
| 12 | ga68 | a conformity clause on a union of `string` and `[,] char`: internal compiler error in `verify_gimple` | correct |
| 13 | ga68 | a subscript, or the clause of a selection that is assigned to, with statements in it: internal compiler error | correct |
| 14 | ga68 `a68-low-clauses.cc` | a row display whose rows differ in bounds is not checked, and a longer later row is written past the allocated elements | run-time error |
| 15 | ga68 `a68-low-clauses.cc` | a row display copies a row with a stride (`m[, 1]`) as if it were contiguous | correct |
| 16 | ga68 `a68-low.cc` | a row display assigned to a row variable whose mode is a mode indicant (`Vec v := (1.0, 2.0, 3.0)`): internal compiler error in `a68_low_assignation` | correct |
| 17 | ga68 | a procedure of the standard prelude (`sin`) as an argument or in a row display: internal compiler error in `a68_type_moid` | correct |
| 18 | ga68 | assigning a `[] [] int` to a `flex [] flex [] int`: internal compiler error in `fold_convert_loc` | correct |
| 19 | ga68 | partial parametrization is accepted by the front end and crashes the back end | correct (as Algol 68 Genie) |
| 20 | libga68 `ga68.map` | the operators on `compl` are not exported from the shared libga68 | correct |
| 21 | ga68 `a68-low.cc` | a row variable declared in a module lives on the stack of the module's prelude | correct |
| 22 | ga68 `a68-low.cc` | the postlude of a module is never executed | fixed |
| 23 | ga68 `a68-parser-bottom-up.cc` | the text of an invalid phrase is used as a format string, so an operator with `%` in it (`%*:=`) breaks the syntax error message | fixed |

## 2. `string_to_real` ignores the exponent part

Filed as [PR 128380](https://gcc.gnu.org/bugzilla/show_bug.cgi?id=128380).

`string_to_real` (and the `long` and `long long` variants generated from
the same template) in `libga68/standard.a68.in`, line 392:

```
bool safe := if e < UPB s
             then {L} int tmp := {K} expart;
                  bool b = string_to_{L_}int (s[e+1:], 10, tmp);
                  expart = {S} tmp;
                  b
             else true
             fi;
```

`expart = {S} tmp` is a comparison whose result is voided, not an
assignment, so `expart` stays 0.

```
begin
    real r := 0.0;
    bool b = string_to_real ("+1.5^+2", r);
    puts ((b | "true " | "false ") + float (r, 12, 4, 3) + "'n");
    long real lr := long 0.0;
    bool lb = string_to_long_real ("+1.5^+2", lr);
    puts ((lb | "true " | "false ") + float (lr, 14, 6, 3) + "'n")
end
```

Output:

```
true +15.0000e -1
true +15.000000e -1
```

Expected: 150, i.e. `+15.0000e +1` and `+15.000000e +1`.

Fix: `expart := {S} tmp;`.

a68j reproduced the defect until version 0.6.0. Since version 0.7.0 its
copy of the standard prelude (`backend/lib/standard.a68`) has this fix
(fixed). The exponent is read as `string_to_int` reads an integer, so it
needs a sign: in `"+1.5^2"` the 2 is taken for the sign, and the result
is 1.5. The procedures `string_to_real` and `string_to_long_real` of the
module `Texts` and `get` of `Genie_Transput` have their own scanner and
were not affected. `tests/java/standard-prelude-defects.a68` checks the
fix.

## 3. `fixed` with width 0 loops forever for large values

Filed as [PR 128382](https://gcc.gnu.org/bugzilla/show_bug.cgi?id=128382).

`fixed` in `libga68/standard.a68.in`, line 217:

```
while y + {L} .5 * {L} .1 ** after >= {L} 10 ** length
do length +:= 1 od;
```

`{L} 10` is an integer denotation, so `{L} 10 ** length` is computed in
`{L} int`. For `real` this is a 32-bit `int`, so `10 ** 10` already
overflows (ga68 gives 1410065408), and from `10 ** 32` on the wrapped
value is 0, so the condition stays true and the loop never ends. A
value above `max_int` (about 2.1e9) is larger than every wrapped power,
so the loop already does not end for `fixed (9.9e9, 0, 0)`; below that
it may end at a wrapped power that happens to be larger. For `long real`
the same happens above `long_max_int` (about 9.2e18), for example with
`long 9.9e18`.

```
begin
    puts (fixed (1.0e9, 0, 1) + "'n");
    puts (fixed (1.0e10, 14, 1) + "'n");
    puts (fixed (long 1.0e18, 0, 1) + "'n");
    puts (whole (10 ** 10, 0) + " " + whole (10 ** 32, 0) + "'n");
    puts (fixed (long 1.0e19, 0, 1) + "'n")
end
```

Output (`timeout 10`, exit status 124):

```
1000000000.0
+10000000000.0
1000000000000000000.0
1410065408 0
```

The second line shows that a width other than 0 avoids the loop. With
`fixed (1.0e10, 0, 1)` a68g prints `10000000000.0`.

Fix: compare with a real power, `{L} 10.0 ** length`, as the other powers
of ten in `fixed`, `subfixed` and `standardize` already do.

An infinite value also makes `fixed` loop forever, whatever the width, and
this fix does not help it. This case is not part of PR 128382.

```
begin
    long real z := long 0.0;
    long real inf := long 1.0 / z;
    puts (fixed (inf, 14, 1) + "'n")
end
```

The program does not end (`timeout 8`, exit status 124; GCC trunk 17.0.0
20261104), and neither does it with width 0. `subfixed` contains

```
while y >= {L} 10.0 ** before do before +:= 1 od;
```

`y` stays infinite, the power of ten becomes infinite after some passes,
and `inf >= inf` holds, so the loop never ends. The loop in `fixed` behaves
the same way once it compares with a real power: limited to 5000 passes,
it ran all of them with `long 10.0 ** length`.

`float` does not end for an infinite value either: `standardize` contains

```
while y >= g do y *:= {L} .1; p +:= 1 od;
```

and an infinite `y` stays infinite.

A possible fix: add `ABS x <= {L} max_real` to the first condition of
`fixed` and of `float`, so that such a value reaches the `else` part,
which yields `ABS width * errorchar`.

a68j reproduced both defects until version 0.6.0; `print` of an infinite
value through `Genie_Transput`, which calls `float`, did not end either.
Since version 0.7.0 its copy of the standard prelude
(`backend/lib/standard.a68`) compares with `{L} 10.0 ** length` and has
the condition `ABS x <= {L} max_real` in `fixed` and `float` (fixed). An
infinite value, and a NaN, for which the condition is false too, give
`ABS width * errorchar`, which is the empty string for width 0. The digits
of a large value are still those of entry 4 (`fixed (9.9e9, 0, 0)` gives
`9899999618`). `tests/java/standard-prelude-defects.a68` checks the fix.
The Rosetta Code solution
`experimental-verification-of-the-nktg-law-in-earth-orbit-based-on-nasa-s-2025-earth-dataset`
prints an infinite `real` with `float`. It times out with ga68, and did
so with a68j until version 0.6.0; a68j now prints asterisks for that value
(`tests/rosetta/notes.tsv`).

## 4. `fixed` on `real` gives wrong digits

Filed as [PR 128383](https://gcc.gnu.org/bugzilla/show_bug.cgi?id=128383).

```
begin
    puts (fixed (1.6e7, 0, 1) + "'n");
    puts (fixed (123456.0, 0, 2) + "'n")
end
```

| | ga68 | expected (a68g) |
|---|---|---|
| `fixed (1.6e7, 0, 1)` | `15999999.0` | `16000000.0` |
| `fixed (123456.0, 0, 2)` | `123456.01` | `123456.00` |

Both values are exact in a 32-bit `real`. `subfixed` (line 306) follows
the algorithm of the Revised Report, which assumes exact arithmetic:

- it first adds the rounding term `.5 * .1 ** after`;
- it divides by `10.0 ** before`;
- then it takes the digits one by one with `y *:= 10.0` and `y -:= c`.

With a 24-bit significand this goes wrong in two ways:

- **The rounding term is rounded itself.** `123456.0 + 0.005` becomes
  123456.0078125, the next float, which then rounds to `.01`.
- **The scaling is inexact.** `16000000.0 / 10.0 ** 8` is a little below
  0.16, so every digit after the first comes out as 9.

The scaling also spoils the digits after the point: `fixed (1234567.125,
0, 6)` gives `1234567.046165` instead of `1234567.125000`, although
1234567.125 is exact in a 32-bit `real` too. Redoing `subfixed` step by
step in 32-bit arithmetic (numpy `float32`) gives the same digits:
`1234567.125 / 10.0 ** 7` is 0.12345670908..., not 0.1234567125, and
after the seven digits before the point only the error is left. With
`long real` the same call gives `1234567.125000`.

Fix: convert in `long real` for `real` arguments, or, in general, use a
correctly rounded binary-to-decimal conversion (such as the C library's
`snprintf` with `%.*f`/`%.*e` and a precision large enough for the
significand) and do the Algol 68 formatting on the resulting digits.

## 5. `float` on `long real` is wrong in the last digits

```
begin
    puts (float (long 1e308, 22, 14, 4) + "'n");
    puts (float (long 1e300, 22, 14, 4) + "'n");
    puts (float (long 1e100, 22, 14, 4) + "'n");
    puts (float (long 1e22, 22, 14, 4) + "'n")
end
```

| | ga68 | expected (a68g) |
|---|---|---|
| `long 1e308` | `+1.00000000000002e+308` | `+1.00000000000000e+308` |
| `long 1e300` | `+1.00000000000002e+300` | `+1.00000000000000e+300` |
| `long 1e100` | `+1.00000000000001e+100` | `+1.00000000000000e+100` |
| `long 1e22` | `+1.00000000000000e +22` | `+1.00000000000000e +22` |

The values themselves are the correctly rounded doubles (a68j reads the
same denotations bit for bit). The error comes from `standardize`
(line 333), which brings `y` into range one decimal digit at a time:

```
while y >= g do y *:= {L} .1; p +:= 1 od;
(y /= {L} 0.0 | while y  < h do y *:= {L} 10.0; p -:= 1 od);
```

For 1e308 that is about 290 multiplications by the inexact 0.1, and
every one of them can add half a unit in the last place.

Fix: scale with one division by a power of ten computed at once (or in
higher precision), or use a correctly rounded conversion as in
report 4.

## 6. The POSIX procedures do not check the result of `_libga68_u32_to_u8`

`_libga68_u32_to_u8` (`ga68-unistr.c`, line 315) returns NULL and sets
`errno` to EILSEQ when the string holds a value that has no UTF-8
encoding: a surrogate, a value of 0x110000 or more, or the end-of-file
character -1 that `fgetc` yields. In that case it does not set
`*lengthp`. None of its callers in `ga68-posix.c` check for NULL:

| Procedure | Line | What happens |
|---|---|---|
| `fputs` | 223 | `write (fd, NULL, <uninitialized>)`; usually -1 with EFAULT, so `fputs` returns 0, but with a zero garbage length it would return 0 with an unchanged `errno`; EILSEQ is always overwritten by the `errno` of `write` |
| `perror` | 64 | `write (2, NULL, <uninitialized>)`, then the message |
| `fopen`, `fcreate` | 103, 145 | `open (NULL, ...)`/`creat (NULL, ...)`: -1 with `errno` EFAULT |
| `getenv` | 194 | `getenv (NULL)`: segmentation fault |
| `fconnect` | 373 | `gethostbyname (NULL)` |

```
begin
    char eof = fgetc (fopen ("/dev/null", file_o_rdonly));
    string bad = "ab" + eof;
    int n = fputs (stdout, bad);
    int e = errno;
    puts ("'nfputs " + whole (n, 0) + " errno " + whole (e, 0) + "'n");
    int fd = fopen (bad, file_o_rdonly);
    int e2 = errno;
    puts ("fopen " + whole (fd, 0) + " errno " + whole (e2, 0) + "'n");
    puts ("getenv: " + getenv (bad) + "'n")
end
```

Output, then exit status 139:

```
fputs 0 errno 14
fopen -1 errno 14
Segmentation fault
```

Valgrind on the `fputs` call alone:

```
Syscall param write(count) contains uninitialised byte(s)
   at write (write.c:26)
   by _libga68_posixfputs (ga68-posix.c:225)
```

Fix: in every caller, if the result is NULL, set `_libga68_errno` to
EILSEQ and return the error value of the procedure (`fputs` 0,
`fopen`/`fcreate`/`fconnect` -1, `getenv` the empty string, `perror`
only the message for `errno`).

a68j does this for `fputs` (0, `errno` 84) and `getenv` (""); for
`fopen` and `fcreate` it returns -1 with `errno` 14 like ga68, so that
programs see the same values.

## 7. `argv (0)` reads `argv[-1]`

`_libga68_posixargv` (`ga68-posix.c`, line 174):

```
if (n < 0 || n > _libga68_argc)
  { /* Return an empty string.  */ ... }
else
  {
    char *arg = _libga68_argv[n - 1];
```

For `n = 0` the test passes and `_libga68_argv[-1]` is read.

```
begin puts ("argc " + whole (argc, 0) + "'n"); puts ("argv 0: <" + argv (0) + ">'n") end
```

`./argv0 x y` prints `argc 3` and then crashes with a segmentation fault;
valgrind reports an invalid read in `strlen` called from
`_libga68_posixargv` (`ga68-posix.c:183`).

Fix: `if (n < 1 || n > _libga68_argc)`.

## 8. `a68_string_from_char` allocates 1 byte for a character

`a68_string_from_char` (`gcc/algol68/a68-low-strings.cc`, line 231)
builds a one-character string for `char + char`, `char * int` and
`int * char` (`a68-low-prelude.cc`, lines 968 to 988):

```
tree elements = a68_lower_tmpvar ("elements%", char_pointer_type,
                                  a68_lower_malloc (M_CHAR,
                                                    size_one_node));
```

A `char` is a 32-bit code point, so the store that follows writes 4 bytes
into a block of 1 byte.

```
begin
    char c = "y";
    string s = "x" + c;
    puts (s + "'n")
end
```

The program prints `xy`, but valgrind reports for each operand:

```
Invalid write of size 4
   at __algol68_main (prelude:1)
 Address 0x4b66190 is 0 bytes inside a block of size 1 alloc'd
   at malloc
   by _libga68_malloc (ga68-alloc.c:127)
   by _libga68_malloc_leaf (ga68-alloc.c:136)
```

`-fdump-tree-original` shows `_libga68_malloc_leaf (1)` followed by
`*elements% = 120`.

Fix: allocate `size_in_bytes (a68_char_type)`.

## 9. Calling a routine after the procedure that yielded it has returned

ga68 compiles a routine text inside a procedure as a GCC nested function
of that procedure. When the routine is used as a value, its address is a
trampoline in the stack frame of the enclosing procedure (the linker
warns that the object "requires executable stack"). If the routine is
called after that procedure has returned, the call jumps into a dead
frame.

This is valid Algol 68 when the routine uses only names of an outer
range: the scope of a routine text is the innermost range that declares
an identifier it uses, here the main program, which is also the scope of
`h`. a68g runs the program without a scope error.

```
begin
    mode Holder = struct (proc (int) bool handler);
    bool seen := false;
    Holder h;
    proc install = (ref Holder hd) void:
        handler of hd := (int n) bool: (seen := true; n > 0);
    install (h);
    proc deep = (int n) int: (n = 0 | 0 | [8] int a; a[1] := n; a[1] + deep (n - 1));
    int d = deep (50);
    bool r = (handler of h) (1);
    puts ("seen " + (seen | "T" | "F") + " " + (r | "T" | "F") + " " + whole (d, 0) + "'n")
end
```

- ga68: segmentation fault (exit status 139).
- a68g and a68j: `seen T T 1275`.

gdb stops at an address on the stack:

```
Program received signal SIGSEGV, Segmentation fault.
0x00007fffffff9ac8 in ?? ()
#1  0x0000000000401512 in __algol68_main () at tramp3.a68:10
```

The call to `deep` is only there to overwrite the dead frame; without it
the program may happen to work. The Rosetta Code solution
`compiler-simple-file-inclusion-pre-processor` crashes the same way: it
installs a logical-file-end handler in a procedure and the handler is
called later (see `tests/rosetta/notes.tsv`).

Possible fix: lift a routine text that uses no names of the enclosing
procedure out of it (give it the static chain of the range it really
depends on), or at least diagnose the case.

## 10. `flex [1 : n] char x` gets the bounds `[1 : 0]`

```
begin int n = 5; flex [1 : n] char x;
      puts (whole (LWB x, 0) + " " + whole (UPB x, 0) + "'n")
end
```

ga68 prints `1 0`; a68g gives the bounds 1 and 5, as the declarer says.
The cause has not been examined.

## 11. `%*:=` computes the C remainder

```
begin int a := -7; a %*:= 3;
      puts (whole (a, 0) + " " + whole (-7 MOD 3, 0) + "'n")
end
```

ga68 prints `-1 2`: `MOD` is correct (the result is never negative), but
`a %*:= b`, which is `a := a MOD b`, gives the remainder of the C `%`
operator. a68g prints 2 for both. ga68 lowers `MODAB` in
`a68_lower_modab3` with `TRUNC_MOD_EXPR`, without the correction of a
negative remainder that `MOD` has.

The Rosetta Code solutions `subtractive-generator` (`sgi -:= 1 MODAB
max_state` gives -1, then a bound error) and `execute-computer-zero` (an
8-bit machine, `0-1` gives -1 instead of 255) fail because of this.

a68j computes `MOD` here (fixed).

## 12. Internal compiler error for a union of `string` and `[,] char`

```
begin union (string, [,] char) u := "abc";
      case u in (string s): puts (s + "'n"), ([,] char m): puts ("m'n") esac
end
```

ga68 stops with `error: non-trivial conversion in 'component_ref'` and
`internal compiler error: 'verify_gimple' failed`. The cause has not
been examined.

## 13. Internal compiler errors for clauses with statements in a subscript or a selection

```
begin [3] int v := (1, 2, 3); int i := 2;
      puts (whole (v[(puts ("x"); i)], 0) + "'n")
end
```

ga68: `internal compiler error: in a68_lower_slice, at
algol68/a68-low-units.cc:792`. The expected output is `x2`.

```
begin mode P = struct (int a, b); P p := (1, 2);
      a of (puts ("x"); p) := 5;
      puts (whole (a of p, 0) + "'n")
end
```

ga68: `internal compiler error: gimplification failed`. The expected
output is `x5`. The causes have not been examined.

## 14. A row display whose rows differ in bounds is not checked

```
begin [,] int a = ((1, 2), (3, 4, 5));
      puts (whole (1 UPB a, 0) + " " + whole (2 UPB a, 0) + ":");
      for i to 1 UPB a do for j to 2 UPB a do puts (" " + whole (a[i, j], 0)) od od;
      puts ("'n")
end
```

ga68 prints `2 3: 1 2 3 4 5 0`; with the rows `(1, 2, 3), (4, 5)` it
prints `2 2: 1 2 3 4`. a68g stops with the run-time error "rows have
different bounds", and the comment in the source says that a run-time
error is intended.

The cause is in `a68_lower_collateral_clause` (`a68-low-clauses.cc`). The
branch that should check the bounds of the second and later rows (line
1283) is empty, with the comment `XXX use sub_multiple_lb, ...`; the code
that would take the bounds of the first row is under `#if 0`. The
elements are allocated for the size of the first row (lines 1254 to
1263), but each row is copied with its own size (line 1304), so a longer
later row is written past the end of the allocated memory, as in the
program above (4 elements allocated, 5 copied). The bounds of the
dimensions after the first are expressions of the variable
`sub_multiple` (line 1277), which at the end holds the last row: the
result gets the bounds of the last row.

Suggested fix: keep the bounds of the first row in temporaries (the code
under `#if 0`), compare the bounds of every later row with them, call a
run-time error in libga68 when they differ, and build the descriptor of
the result from the temporaries.

## 15. A row display copies a row with a stride as if it were contiguous

```
begin [,] int m = ((1, 2), (3, 4));
      [,] int b = (m[, 1], m[, 2]);
      for i to 1 UPB b do for j to 2 UPB b do puts (" " + whole (b[i, j], 0)) od od;
      puts ("'n")
end
```

ga68 prints ` 1 2 2 3`; the expected output, which a68g gives, is
` 1 3 2 4`. The columns `m[, 1]` and `m[, 2]` have a stride of two
elements, but `a68_lower_collateral_clause` copies each row of the
display with one `memcpy` of its number of elements from its first
element (line 1304), which ignores the stride. The comment above it (line
1294) already notes that strides need an iteration.

Suggested fix: copy the elements one by one along the strides of the
row, or use `memcpy` only when the row is contiguous.

## 16. Internal compiler error for a row variable whose mode is an indicant

```
begin mode Vec = [1 : 3] real;
      Vec v := (1.0, 2.0, 3.0);
      puts (fixed (v[2], 0, 1) + "'n")
end
```

ga68: `internal compiler error: in a68_low_assignation, at
algol68/a68-low.cc:1053`, called from `a68_lower_variable_declaration`.
The expected output, which a68g gives, is `2.0`. The same happens with
`Vec v; v := (1.0, 2.0, 3.0)`. The program works when the declarer is
written out, `[1 : 3] real v := (1.0, 2.0, 3.0)`, and in an identity
declaration, `Vec v = (1.0, 2.0, 3.0)`.

The assertion that fails is `TREE_TYPE (TREE_TYPE (lhs)) == TREE_TYPE
(rhs)`: the type of the name and the type of the row display differ.
Why they differ has not been examined.

## 17. Internal compiler error for a procedure of the standard prelude used as a value

```
begin proc ap = (proc (real) real f, real a) real: f (a);
      puts (fixed (ap (sin, 1.0), 0, 4) + "'n")
end
```

ga68: `internal compiler error: in a68_type_moid, at
algol68/a68-lang.cc:121`. The expected output, which a68g gives, is
`.8415`. A row display does the same, `[] proc (real) real fs = (sin,
cos)`, and so does `sqrt` instead of `sin`. A procedure declared in the
program, `proc my_sqrt = (real x) real: sqrt (x)`, works as the argument.

The backtrace goes from `collect_call_arguments` (a68-low-units.cc:1157)
or from `a68_lower_collateral_clause` to `a68_low_dup` (a68-low.cc:736),
which asks `a68_type_moid` for the mode of the type of the value; the
assertion there fails because no mode is attached to that type. Where
the type of a lowered standard procedure should get its mode has not been
examined.

## 18. Internal compiler error when a row of rows is assigned to a flexible row of rows

```
begin [] [] int jag = ((1, 2), (3, 4, 5));
      flex [1 : 0] flex [1 : 0] int rag;
      rag := jag;
      puts (whole (UPB rag[2], 0) + "'n")
end
```

ga68: `internal compiler error: in fold_convert_loc, at
fold-const.cc:2620`, in `gimplify_modify_expr`. The expected output, which
a68g gives, is `3`. The same happens when `jag` is the initial value of
`rag`. Assigning the row display itself, `rag := ((1, 2), (3, 4, 5))`,
works. The cause has not been examined.

## 19. Partial parametrization crashes the back end

```
begin proc add = (real x, y) real: x + y;
      proc (real) real g := add (3, );
      puts (fixed (g (4), 0, 1) + "'n")
end
```

Partial parametrization (Lindsey, ALGOL Bulletin 39.3.1) is not part of
the Revised Report, but the ga68 front end accepts the call with a
missing argument. The back end then stops with `internal compiler error:
in fold_convert_loc, at fold-const.cc:2620`. a68g, which implements the
extension, prints `7.0`. ga68 should either implement it or reject the
program with a message. The cause has not been examined.

## 20. The operators on `compl` are not exported from the shared libga68

```
begin compl z = 1 I 2, w = 3 I 4;
      compl a = z + w, b = z - w, c = z * w, d = z / w, e = CONJ z;
      real f = ABS z, g = ARG z;
      puts (fixed (re of a, 0, 1) + " " + fixed (im of a, 0, 1) + " "
            + fixed (re of b, 0, 1) + " " + fixed (im of b, 0, 1) + " "
            + fixed (re of c, 0, 1) + " " + fixed (im of c, 0, 1) + " "
            + fixed (re of d, 0, 2) + " " + fixed (im of d, 0, 2) + " "
            + fixed (im of e, 0, 1) + " " + fixed (f, 0, 4) + " " + fixed (g, 0, 4) + "'n")
end
```

With the default (static) libga68 the program prints `4.0 6.0 -2.0 -2.0
-5.0 10.0 .44 .08 -2.0 2.2361 1.1071`, as a68g does. With
`-shared-libga68` it does not link:

```
undefined reference to `STANDARD_u_4'
undefined reference to `STANDARD_m_8'
undefined reference to `STANDARD_r_11'
```

and also `STANDARD_s_14`, `STANDARD_CONJ15`, `STANDARD_ABS18` and
`STANDARD_ARG19`. The operators are defined in Algol 68 in
`standard.a68.in` (section 10.2.3.7, from line 32) and are in
`libga68.so`, but as local symbols: the version script `ga68.map` lists
only some symbols of the standard prelude by name (lines 74 to 99) and
makes all others local (`local: *;`, lines 101 and 102). `libga68.a`
defines 74 global `STANDARD_` symbols, `libga68.so` exports 25. This is
the same kind of defect as entry 1.

Suggested fix: export every `STANDARD_` symbol that ga68 can emit a
reference to, for example with a pattern `STANDARD_*;` in `ga68.map`.

## 21. A row variable declared in a module lives on the stack of the module's prelude

`store.a68`:

```
module Store =
def
   [1 : 100] int items;
   int count := 0;
   pub proc add = (int x) void: (count +:= 1; items[count] := x);
   pub proc get = (int i) int: items[i];
   skip
fed
```

`use-store.a68`:

```
access Store
begin
   [1 : 50] int local;
   for i to 50 do local[i] := -i od;
   for i to 3 do add (i * 10) od;
   puts (whole (get (3), 0) + " " + whole (local[50], 0) + "'n")
end
```

```
ga68 -fstropping=supper -c store.a68
ga68 -fstropping=supper use-store.a68 store.o -o use-store
```

The program stops with `store.a68:5: runtime error: bound 1 out of range
[-193273528365:-201863462959]`; the expected output is `30 -50`. With
`heap [1 : 100] int items` the program prints `30 -50`.

The declarations of the module text are lowered into the function
`STORE__prelude` (a68-low.cc:1353). For `[1 : 100] int items` the
GIMPLE dump of `store.a68` shows that the descriptor (40 bytes) and the
elements (400 bytes) are allocated with `__builtin_alloca_with_align` in
that function, and that their address is stored in the global
`STORE_items`. When `STORE__prelude` returns, `STORE_items` points into
a dead stack frame, which the accessing program overwrites with its own
variables.

Suggested fix: allocate the storage of the variables of a module text
statically or on the heap, not on the stack of the prelude.

## 22. The postlude of a module is never executed

`greet.a68`:

```
module Greet =
def
   pub proc hello = void: puts ("hello'n");
   skip
postlude
   puts ("bye'n")
fed
```

`use-greet.a68`:

```
access Greet
begin hello
end
```

The program prints only `hello`; `bye` is missing. The accessing program
does call `GREET__postlude`.

The prelude of a module increments a sentinel and does its work only when
the sentinel is 1. The postlude is meant to decrement it and to do its
work when it is 0, but the code under the comment `/* Decrease sentinel.
*/` (a68-low.cc:1392) builds a `POSTINCREMENT_EXPR`, so the sentinel
becomes 2 and the postlude, including the calls of the postludes of the
modules it accesses and of the standard postludes, is skipped.

Suggested fix: use `POSTDECREMENT_EXPR` there. The fix has not been
tested by rebuilding ga68.

With the fix, the outermost access clause activates the module and
revokes it at its end, as the section "Module activation" of the ga68
manual describes, and an access clause after that activates it again:
its prelude runs again, with new storage. The modules testsuite of ga68
expects this: the postlude of `module16.a68` checks a value that
`program-16.a68` assigns, and `program-1.a68`, which accesses `Module1`
eleven times one after the other, then prints `Hello from module` and
`Bye from module` eleven times each.

a68j reproduced the defect until version 0.6.0. Since version 0.7.0 it
decrements the sentinel (fixed). As a module can be activated more than
once, code using a module reads each of its public tags from the
module's current activation at every use, instead of once. The
programs `program-1`, `program-2`, `program-8` and `program-11` of the
modules testsuite print `Bye from module` after each activation; their
results are in `tests/ga68/fixed`. `tests/java/module-activation.a68`
checks repeated and nested activation.

## 23. A syntax error message uses the program text as a format string

```
begin
  op (ref int, int) ref int f = %*:=;
  skip
end
```

The program is invalid, and ga68 should report

```
error: construct beginning with 'begin' in line 1 followed by an
operator plan in line 2 and then 'f' and '=' and '%*:=' and ';' and a
skip unit in line 3 and 'end' in line 4 is an invalid serial clause
```

`recover_from_error` (a68-parser-bottom-up.cc) builds this message from
the text of `a68_phrase_to_text`, which puts each symbol in `%<...%>`,
and passes it to `a68_error` as the format string:

```
const char *seq = a68_phrase_to_text (p, &w);
...
std::string fmt = seq;
fmt += " is an invalid %e";
a68_error (w, fmt.c_str (), &a);
```

The symbols are inserted unchanged, so a `%` in an operator becomes a
directive of the format: `%*` and `%:` in `%*`, `%*:=` and `%:=` are not
valid directives, and in `%<%%>` for the operator `%` the `%%` is a
literal `%` and the closing quote is lost. What GCC's pretty printer
does with the unknown directives has not been measured; it is not a
correct message in either case.

Suggested fix: double every `%` of a symbol in `a68_phrase_to_text`
before it goes into the text, or pass the text as an argument of `%s`
and let `a68_phrase_to_text` produce plain quotes.

a68j used to stop with an uncaught `IllegalArgumentException` for `%*`,
`%*:=` and `%:=`, and to print `'%>` for `%`. It doubles the `%` of a symbol in `TopDown.phraseToText` and reports the
error as above (fixed); messages for symbols without `%` are unchanged.

## Not a ga68 bug: Rosetta Code "Amb"

The solution `amb` yields routines from operators that use the
operator's parameters (`self`, `b`) after the operator has returned. The
source marks this with `{ scope violation }`, and a68g stops with
"ITERPAGE value is exported out of its scope". Compiled with ga68, the
program reads dead stack frames and fails with a different bound in every
run, e.g. `runtime error: bound 1 out of range
[140737134168144:140737134168448]`. Since Algol 68 leaves the effect
undefined, this is a program error. ga68 could warn about it, as a68g
does at compile time ("potential scope violation").
