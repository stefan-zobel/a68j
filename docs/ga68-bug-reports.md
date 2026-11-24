# Bug reports for ga68 and libga68

These are defects in ga68 (the GNU Algol 68 front end of GCC) and in
libga68 that came up while porting ga68 to the JVM. Each entry has a
small program that shows the defect, what happens, what should happen,
the cause in the sources, and a suggested fix. The entries are written
so that they can be filed one by one.

Version: GCC trunk 17.0.0 20261031 (commit 3f35c3d2d116), x86_64 Linux,
glibc. The programs use SUPPER stropping and are compiled with

    ga68 -fstropping=supper prog.a68 -o prog

Algol 68 Genie 3.13.4 (`a68g`) is the reference where a program has an
obvious expected result. Line numbers refer to the commit above.

Entries 2 to 4 have been filed in the GCC Bugzilla (PR 128380, PR 128382
and PR 128383, linked at the entries); the others have not. Entry 1 was
filed as PR 128378 and has been fixed in GCC; it has been removed, and
the other entries keep their numbers. Entries 10
to 13 have reproducers and the measured behavior, but their causes in the
sources have not been examined yet. Entries 16 to 22 were found while the
examples of the book were checked; the causes of 16 to
19 have been examined only as far as their entries say. Entry 23 was
found in the source; what ga68 does with it has not been measured. Entry
24 was found while the module `Genie_Formats` was written; its cause has
not been examined. Entry 25 was found while the option `-fgenie` was
written. Entry 26 was found with a Rosetta Code solution that stage 2 of
`Genie_Formats` lets run further; its cause has been narrowed down by
measurement only. Entry 27 was found when a68j included a file from a
directory with a Windows short name (`STEFAN~1`). Entry 28 was found
with the Rosetta Code solution `matrix-multiplication`, which a68j
compiles with `-fgenie` since structures are items of `print`. Entry 29
was found when the hash functions of `tests/rosetta/hashes` were tried on
the bytes of files.

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
| 4 | libga68 `standard.a68.in` | `fixed` on `real` gives wrong digits, e.g. "15999999.0" for 1.6e7 | fixed |
| 5 | libga68 `standard.a68.in` | `float` on `long real` is wrong in the last digits for large and small exponents | fixed |
| 6 | libga68 `ga68-posix.c` | `fputs`, `perror`, `fopen`, `fcreate`, `getenv` and `fconnect` use the result of `_libga68_u32_to_u8` without checking it for NULL | defined result |
| 7 | libga68 `ga68-posix.c` | `argv (0)` reads `argv[-1]` and crashes | returns "" |
| 8 | ga68 `a68-low-strings.cc` | `a68_string_from_char` allocates 1 byte for a 4-byte character | not affected |
| 9 | ga68 | a routine text that is called after the procedure that yielded it has returned jumps into a dead stack frame | correct |
| 10 | ga68 | `flex [1 : n] char x` gets the bounds `[1 : 0]` | reproduces |
| 11 | ga68 | `%*:=` (`MODAB`) computes the C remainder, unlike `MOD` | fixed |
| 12 | ga68 | a `string` value that is not a variable, united into a union of `string` or `[] char` and `[,] char`: internal compiler error in `verify_gimple`, or the value becomes the `[,] char` component | correct |
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
| 24 | ga68 `a68-low-generator.cc` | a variable of a mode exported by a module that is, or contains, a flexible row (other than `string`): internal compiler error in `fill_in_buffer` | correct |
| 25 | ga68 `a68-parser-scanner.cc` | `PR include "dir/file" PR` in a file given without a directory looks for `/dir/file` | reproduces; correct with `-fgenie` |
| 26 | ga68 | a field of mode `string` selected from a row of structures (`name of items`) crashes the program when the selection is bound to an identifier or passed as an argument | correct |
| 27 | ga68 `a68-parser-scanner.cc` | an included file is not found when its path has a `~` anywhere in it, also in the name of a directory | reproduces; correct with `-fgenie`, and for Windows short names |
| 28 | libga68 `standard.a68.in` | `fixed` writes a "0" before the point when a value below 1 rounds up to 1, e.g. "01.00" | fixed |
| 29 | libga68 `ga68-posix.c` | `fgetc` takes the bytes after an invalid UTF-8 byte into the same character without checking them, so a newline or a letter after it is lost, and a sequence cut off by the end of the file gives the end of the file | reproduces |

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
`ABS width * errorchar`, which is the empty string for width 0.
`tests/java/standard-prelude-defects.a68` checks the fix.
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

a68j reproduced the defect until version 0.6.0. Since version 0.7.0
`fixed` and `float` in its copy of the standard prelude
(`backend/lib/standard.a68`) convert a `real` as a `long real`, which
holds it exactly (fixed). The digits are then those of the exact value of
the `real`, up to about the sixteenth significant digit, where the
arithmetic of `long real` ends; `float` also gets the correction of entry
5. Digits that only looked right change too: `fixed (0.1, 0, 10)` gives
`.1000000015`, the value of the 32-bit `real` nearest to 0.1, instead of
`.1000000000`, and `print (9.81)` through `Genie_Transput`, which calls
`float`, gives `+9.810000e +0` instead of `+9.810001e +0`. The
conversion is not measurably slower. `tests/java/standard-prelude-defects.a68`
checks the fix; the Rosetta Code solutions whose output changes are
listed in `tests/rosetta/README.md`.

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

Small exponents are affected in the same way, by the multiplications by
10.0. With a68j until version 0.6.0, which has the same `standardize`,
`float (long 1e-300, 22, 14, 4)` gives `+9.99999999999999e-301`. The
long variants of `string_to_real` call `standardize` to compare with
`{L} max_real`, and there a68j until version 0.6.0 accepted
`"+1.79769313486232^+308"`, which is larger than `long_max_real`, and
yielded an infinite value. Neither has been run with ga68.

Fix: scale with one division by a power of ten computed at once (or in
higher precision), or use a correctly rounded conversion as in
report 4.

a68j reproduced the defect until version 0.6.0. Since version 0.7.0
`long_standardize` and `long_long_standardize` in its copy of the
standard prelude (`backend/lib/standard.a68`) estimate the exponent with
the decimal logarithm and scale with one division or multiplication by a
power of ten, corrected by a few steps where the estimate is off (fixed).
The four values above give the expected results of the table, `long
1e-300` gives `+1.00000000000000e-300`, and `"+1.79769313486232^+308"`
is rejected. `tests/java/standard-prelude-defects.a68`
checks the fix.

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

## 12. A union of `string` and `[,] char`: internal compiler error or the wrong component

```
begin union (string, [,] char) u := "abc";
      case u in (string s): puts (s + "'n"), ([,] char m): puts ("m'n") esac
end
```

ga68 stops with `error: non-trivial conversion in 'component_ref'` and
`internal compiler error: 'verify_gimple' failed`. The cause has not
been examined.

Measured again with ga68 at commit bb5c3bfcb28b, the defect is not in
the conformity clause but in uniting: a union that has a one-dimensional
and a two-dimensional row of `char` as components (`string` or `[] char`
together with `[,] char`) goes wrong when a `string` value that is not
held by a variable is united into it. It does not matter whether the
one-dimensional component is `string` or `[] char`:

```
begin mode U = union (int, [] char, [,] char);
      proc f = string: "abc";
      U u = f;
      case u in ([] char s): puts (s + "'n") out puts ("other'n") esac
end
```

`error: invalid conversion in gimple call` and `internal compiler
error: 'verify_gimple' failed`. With `[] string r = ("abc", "de"); U u =
r[2]` in place of the call: `error: non-trivial conversion in 'mem_ref'`
and the same internal compiler error.

```
begin mode U = union (int, [] char, [,] char);
      string v := "ab";
      U u = v + "c";
      case u
      in ([] char s): puts ("[] char " + s + "'n"),
         ([,] char m): puts ("[,] char " + whole (1 UPB m, 0) + " " + whole (2 UPB m, 0) + "'n"),
         (int i): puts ("int " + whole (i, 0) + "'n")
      esac
end
```

This compiles, and the program prints `[,] char 3 12`: the value is
united as the `[,] char` component, with bounds made up from its
descriptor. With `out` in place of the other branches, the program
printed `other` in some runs and stopped with `Virtual memory exhausted`
in others. The expected output is `[] char abc`.

The other values of a `string` or `[] char` mode unite correctly into
the same union: a variable, a slice (`t[3 : 5]`), a denotation, and a
call of a procedure that yields `[] char`. A union of the same shape for
`int`, `real` or `bool` (`[] int` and `[,] int`) is not affected, also
not for `flex` values yielded by calls or selected from rows.

For this reason the modules `Genie_Transput` and `Genie_Formats` have no
`[,] char` among their items.

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

Checked again with commit bb5c3bfcb28b: the defect remains, under other
names. Since commit 4b22e5581 ("add GNU extensions in Algol 68 to
libga68"), the program refers to the operators of the new module `GNU`
(`libga68/gnu.a68.in`, which defines them for `compl` and `word compl`),
and with `-shared-libga68` the undefined references are `GNU_u_4`,
`GNU_m_8`, `GNU_r_11`, `GNU_s_14`, `GNU_CONJ15`, `GNU_ABS18` and
`GNU_ARG19`. `ga68.map` names no `GNU_` symbol at all: `libga68.a`
defines 42 global `GNU_` symbols, `libga68.so` exports none. The static
program prints the same line as before.

Suggested fix: export every `STANDARD_` and `GNU_` symbol that ga68 can
emit a reference to, for example with the patterns `STANDARD_*;` and
`GNU_*;` in `ga68.map`.

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

## 24. A variable of a flexible row mode exported by a module: internal compiler error

`rows.a68`:

```
module Rows =
def
   pub mode Vec = flex [1 : 0] int;
   skip
fed
```

`use-rows.a68`:

```
access Rows
begin
   Vec v;
   v := (1, 2, 3);
   puts (whole (UPB v, 0) + "'n")
end
```

```
ga68 -fstropping=supper -c rows.a68
ga68 -fstropping=supper use-rows.a68 rows.o -o use-rows
```

The second command stops with `internal compiler error: Segmentation
fault` in `fill_in_buffer` (a68-low-generator.cc:147, called from
`a68_low_generator`, line 510, and `a68_lower_variable_declaration`,
a68-low-decls.cc:334). The expected output is `3`. The same happens for
`ref Vec v = heap Vec` and for a structure exported by a module with a
field `flex [1 : 0] int` or `flex [1 : 0] proc int`; a field `string`
works, and so do all these modes when they are declared in the program
itself. Measured with GCC trunk 17.0.0 20261104; the cause in the
sources has not been examined.

The module `Genie_Formats` (`backend/lib/genieformats.a68`) avoids
flexible rows in its exported mode `Gformat` for this reason.

## 25. An included file with a directory is not found from a file without one

`include/d.a68`:

```
INT x = 1;
```

`p.a68`, in the same directory as `include` (UPPER stropping):

```
PR include "include/d.a68" PR
puts (whole (x, 0))
```

```
ga68 -fstropping=upper p.a68 -o p
```

ga68 reports `p.a68:1:4: error: included file not found`. The expected
result is a program that prints `1`, as with `ga68 -fstropping=upper
./p.a68` or with a filename without a directory. Algol 68 Genie finds such a file
(`PR read "include/d.a68" PR`) relative to the directory of the program,
whether the program is given with a directory or not.

`find_include_file` (a68-parser-scanner.cc, line 895; measured with GCC
trunk 17.0.0 20261104) takes the directory of the including file
(`ldirname (curfile)`) and uses `.` instead only if both this directory
and the directory of the filename are empty:

```
if (strlen (sourcedir) == 0 && strlen (fnbdir) == 0)
  { ... sourcedir = "." ... }
tmpfpath = a68_relpath (sourcedir, fnbdir, incfile);
```

With an empty `sourcedir` and `fnbdir` = `include`, `a68_relpath`
concatenates `"" + "/" + "include" + "/" + "d.a68"`, an absolute path
in the root directory. A relative `-I` directory does not help either
(`-I.`): its path is not absolute, so the same path is tried again. An
absolute one does.

Suggested fix: use `.` whenever `sourcedir` is empty.

a68j reproduces the error, except with the option `-fgenie`, which runs
programs written for Algol 68 Genie; there the file is found as Algol 68
Genie finds it (`tests/formats/genie/read-first.a68`).

## 26. A row of strings selected from a row of structures crashes the program

```
begin mode Item = struct (string name, int value);
      [] Item items = (("panacea", 1), ("ichor", 2), ("gold", 3));
      [] string n = name of items;
      puts ("declared'n");
      puts (n[2] + "'n")
end
```

The program ends with a segmentation fault before it writes anything.
The expected output, which a68g gives, is `declared` and `ichor`.
Measured with GCC trunk 17.0.0 20261104:

- The same happens when `items` is a variable (`[3] Item items := ...`),
  when `name` is the second field, when the selection is passed as an
  argument of mode `[] string` or of a union with it, and when it is
  taken from a slice (`name of items[2 : 3]`).
- Indexing the selection directly works: `(name of items)[2]` and
  `name of items[2]` give `ichor`.
- A field of mode `int` works in all of these places
  (`[] int n = value of items`).

`a68_lower_selection` (a68-low-units.cc, line 871) makes the selection a
descriptor with the bounds and strides of the row of structures and an
elements pointer offset to the field. The crash therefore probably lies
where such a descriptor is copied or bound when its elements are rows
themselves (`string`); this has not been examined in the sources.

The Rosetta Code solution `knapsack-problem-unbounded` crashes in this
way (`printf` of `name OF items`). a68j gives the expected result.

## 27. An included file is not found when its path has a `~` in it

`/home/u/tilde~dir/lib.a68`:

```
string x = "ok";
```

`/home/u/tilde~dir/main.a68`:

```
begin [[include "lib.a68"]]
  puts (x + "'n")
end
```

```
ga68 -fstropping=supper /home/u/tilde~dir/main.a68 -o main
```

ga68 reports `/home/u/tilde~dir/main.a68:1:9: error: included file not
found`. The expected result is a program that prints `ok`, as it does
when the directory is called `plaindir`, or when ga68 is run in the
directory as `ga68 main.a68`. The same happens with a `~` in the
directory of the filename (`[[include "a~b/lib.a68"]]`). Algol 68 Genie
finds the file in both cases.

`a68_relpath` (a68-parser-scanner.cc, line 866; measured with GCC trunk
17.0.0 20261116, commit bb5c3bfcb28b) joins the directory of the
including file, the directory of the filename and the filename, and
gives up if the result has a `~` anywhere:

```
/* Home directory shortcut ~ is a shell extension.  */
if (strchr (q, '~') != NO_TEXT) {
  return NO_TEXT;
}
```

The comment is about `~/` as a shorthand for the home directory, but the
test rejects every `~`, also one in the name of a directory, which is an
ordinary character in a path. In Algol 68 Genie 3.13.4 (`a68g-path.c`,
`a68g_relpath`) only a directory of the filename that starts with `~` is
taken as one in the home directory (`$HOME`); a `~` elsewhere stays as
it is.

Suggested fix: remove the test, or do what Algol 68 Genie does.

a68j reproduces the error, except with the option `-fgenie`, where the
file is found as Algol 68 Genie finds it
(`tests/formats/genie/read-tilde.a68`; the error itself is
`tests/options/include-tilde.a68`). On Windows a68j also replaces short
(8.3) names such as `STEFAN~1` in the directory by the long ones before
the test, since such names are not chosen by the user and ga68 does not
see them on Linux.

## 28. `fixed` writes "01.00" for a value below 1 that rounds up to 1

```
begin
    puts ("[" + fixed (0.999999, -6, 2) + "] [" + fixed (0.999999, 6, 2) + "] ["
          + fixed (0.6, -6, 0) + "] [" + fixed (0.994, -6, 2) + "]'n")
end
```

| | ga68 | expected (a68g) |
|---|---|---|
| `fixed (0.999999, -6, 2)` | ` 01.00` | `  1.00` |
| `fixed (0.999999, 6, 2)` | `+01.00` | ` +1.00` |
| `fixed (0.6, -6, 0)` | `    01` | `     1` |
| `fixed (0.994, -6, 2)` | `  0.99` | `  0.99` |

Measured with GCC trunk at commit bb5c3bfcb28b; the line number refers
to that commit. `fixed` (`libga68/standard.a68.in`, line 223) puts a
"0" before the point when there is room for it and the value is below 1:

```
then (length > UPB s AND y < {L} 1.0 | "0" +=: s);
```

`y` is the value before rounding, but `subfixed` rounds it: 0.999999
with two digits after the point becomes `1.00`, which already has a
digit before the point, and the "0" is added in front of it. The test
`y < 1.0` agrees with whether `subfixed` wrote a digit before the point
only for values that do not round up to 1. Values that round up to 10
or more are not affected (`fixed (9.999, -6, 2)` gives ` 10.00`), and
neither is width 0, where the width is computed with the rounded value.
Algol 68 Genie 3.13.4 (`transput-formatting.c`, line 492) adds the "0"
only when the string is empty or starts with the point.

Suggested fix: test the result of `subfixed` instead of `y`:

```
then (length > UPB s AND (UPB s = 0 | true | s[1] = ".") | "0" +=: s);
```

a68j reproduced the defect until version 0.8.1. Since version 0.9.0
`fixed` in its copy of the standard prelude (`backend/lib/standard.a68`)
makes this test (fixed). For 10 values (0.999999, 0.996, 0.994, 0.3,
0.6, 0.0, 0.05, 9.999, 99.996, -0.999), the widths 2 to 6 and -2 to -6, and 0,
1 and 2 digits after the point, a68j then gives the results of Algol 68
Genie in all 300 cases. `tests/java/standard-prelude-defects.a68` checks
the fix.

## 29. `fgetc` loses the bytes after an invalid UTF-8 byte

```
begin
    string s := "";
    do int c = ABS getchar;
       if c = -1 then finish fi;
       s +:= " " + whole (c, 0)
    od;
    finish: puts (s + "'n")
end
```

The program prints the code of each character it reads from standard
input. The input is given with `printf`, e.g.
`printf 'caf\351\nab\n' | ./prog` for "café" in Latin-1:

| Input | ga68 | expected |
|---|---|---|
| `caf\351\nab\n` | ` 99 97 102 65533 98 10` | ` 99 97 102 65533 10 97 98 10` |
| `\377\nA\n` | ` 65533` | ` 65533 10 65 10` |
| `\200A\n` | ` 65533 10` | ` 65533 65 10` |
| `x\342\202` (the euro sign without its last byte) | ` 120` | ` 120 65533` |
| `\360\237\230\200\n` (U+1F600) | ` 128512 10` | ` 128512 10` |

The expected results replace each maximal subpart of an invalid sequence
with one U+FFFD, as the Unicode Standard recommends (chapter 3, "U+FFFD
Substitution of Maximal Subparts"); Python's `decode ("utf-8",
"replace")` gives them. Valid UTF-8 is read correctly.

Measured with GCC trunk at commit bb5c3bfcb28b; the line numbers refer
to that commit. `_libga68_posixfgetc` (`libga68/ga68-posix.c`, line 263)
takes the number of bytes of a character from its first byte alone: one
more for every byte from 128 to 223, two for 224 to 239, three for 240
to 255. It then reads that many bytes with `read` without looking at
them and decodes the group with `_libga68_u8_mbtouc`; if the group is not
a valid character, it returns U+FFFD for all of it (line 298). So:

- A byte that cannot start a character (a continuation byte 128 to 191,
  192, 193, or 245 to 255) still takes the next one to three bytes, and
  they are lost even when they are a newline or an ASCII letter. A text
  in Latin-1 loses one to three characters after each letter with an
  accent.
- A first byte followed by a byte that is not a continuation byte loses
  that byte as well.
- When the input ends within the group, `fgetc` returns the end of file
  character (line 291), and the bytes read before are lost without a
  U+FFFD.

`fgets` and `gets` (lines 315 and 361) read with `fgetc`, so a newline
taken into a group does not end the line: two lines are read as one.

Suggested fix: read a byte as part of a character only if it can
continue it, and keep a byte that cannot for the next call. `read` has
no way to put a byte back, so `fgetc` keeps it itself, one byte for each
descriptor:

```c
#define MAX_PUSHED_BACK 1024

/* For each descriptor, the byte that ended an invalid sequence, plus 1,
   or 0.  */
static int pushed_back[MAX_PUSHED_BACK];

static int
read_byte (int fd)
{
  uint8_t c;
  if (fd >= 0 && fd < MAX_PUSHED_BACK && pushed_back[fd] != 0)
    {
      int b = pushed_back[fd] - 1;
      pushed_back[fd] = 0;
      return b;
    }
  return read (fd, &c, 1) == 1 ? c : -1;
}

uint32_t
_libga68_posixfgetc (int fd)
{
  int c = read_byte (fd);
  if (c < 0)
    return EOF_PSEUDO_CHARACTER;
  if (c < 0x80)
    return c;

  int more, lo = 0x80, hi = 0xBF;
  uint32_t res;
  if (c >= 0xC2 && c <= 0xDF)
    more = 1, res = c & 0x1F;
  else if (c >= 0xE0 && c <= 0xEF)
    {
      more = 2, res = c & 0x0F;
      if (c == 0xE0)
        lo = 0xA0;
      else if (c == 0xED)
        hi = 0x9F;
    }
  else if (c >= 0xF0 && c <= 0xF4)
    {
      more = 3, res = c & 0x07;
      if (c == 0xF0)
        lo = 0x90;
      else if (c == 0xF4)
        hi = 0x8F;
    }
  else
    return REPLACEMENT_CHARACTER;

  for (int i = 0; i < more; ++i)
    {
      int d = read_byte (fd);
      if (d < 0)
        return REPLACEMENT_CHARACTER;
      if (d < lo || d > hi)
        {
          if (fd >= 0 && fd < MAX_PUSHED_BACK)
            pushed_back[fd] = d + 1;
          return REPLACEMENT_CHARACTER;
        }
      res = (res << 6) | (d & 0x3F);
      lo = 0x80, hi = 0xBF;
    }
  return res;
}
```

The limits `lo` and `hi` of the second byte leave out overlong forms,
surrogates and values above U+10FFFF, which are not valid UTF-8. A sequence cut off by the end of the input gives U+FFFD, and
the next call the end of file. `lseek` (line 446) and `fclose` must
then drop a byte kept for the descriptor, and `lseek` with `SEEK_CUR`
must count it. Compiled on its own, this `fgetc` gives the expected
results of the table, and for `x\300\200y\n`, `\355\240\200z` and
`\342\202\254` the same results as Python.

a68j reproduces the defect: `fgetc` of its POSIX prelude
(`backend/src/main/java/a68j/rt/Posix.java`) is a port of the code above
and gives the results of ga68 in the table. The module `Genie_Transput`
reads with `fgetc` as well, so with `-fgenie` a line of Latin-1 text
loses characters, and a line that ends within a group is lost. (Algol 68
Genie, where a `char` is a byte, reads the bytes 128 to 254 unchanged.)

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
