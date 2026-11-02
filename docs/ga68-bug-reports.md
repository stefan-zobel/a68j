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

As of 2 November 2026 entries 1 to 4 have been filed in the GCC
Bugzilla (PR 128378, PR 128380, PR 128382 and PR 128383, linked at the entries); the others
have not. Entries 10
to 13 have reproducers and the measured behavior, but their causes in the
sources have not been examined yet.

The last column of the summary says what a68j does: it reproduces the
behavior of ga68 where the output is still deterministic, and is correct
where ga68 crashes or reads uninitialized memory.

| # | Component | Summary | a68j |
|---|---|---|---|
| 1 | libga68 `ga68.map` | `POSIX_fileodefault` is misspelled, so a program that uses `file_o_default` does not link against the shared libga68 | links |
| 2 | libga68 `standard.a68.in` | `string_to_real` ignores the exponent part | reproduces |
| 3 | libga68 `standard.a68.in` | `fixed` with width 0 loops forever for values of 1e10 and above | reproduces |
| 4 | libga68 `standard.a68.in` | `fixed` on `real` gives wrong digits, e.g. "15999999.0" for 1.6e7 | reproduces |
| 5 | libga68 `standard.a68.in` | `float` on `long real` is wrong in the last digits for large exponents | reproduces |
| 6 | libga68 `ga68-posix.c` | `fputs`, `perror`, `fopen`, `fcreate`, `getenv` and `fconnect` use the result of `_libga68_u32_to_u8` without checking it for NULL | defined result |
| 7 | libga68 `ga68-posix.c` | `argv (0)` reads `argv[-1]` and crashes | returns "" |
| 8 | ga68 `a68-low-strings.cc` | `a68_string_from_char` allocates 1 byte for a 4-byte character | not affected |
| 9 | ga68 | a routine text that is called after the procedure that yielded it has returned jumps into a dead stack frame | correct |
| 10 | ga68 | `flex [1 : n] char x` gets the bounds `[1 : 0]` | reproduces |
| 11 | ga68 | `%*:=` (`MODAB`) computes the C remainder, unlike `MOD` | reproduces |
| 12 | ga68 | a conformity clause on a union of `string` and `[,] char`: internal compiler error in `verify_gimple` | correct |
| 13 | ga68 | a subscript, or the clause of a selection that is assigned to, with statements in it: internal compiler error | correct |

## 1. `ga68.map` exports `POSIX_fileodeault` instead of `POSIX_fileodefault`

Filed as [PR 128378](https://gcc.gnu.org/bugzilla/show_bug.cgi?id=128378).

The version script `libga68/ga68.map` (line 45) lists `POSIX_fileodeault`.
The variable in `ga68-posix.c` is `_libga68_file_o_default`, and the
symbol that ga68 emits for the POSIX prelude constant `file_o_default` is
`POSIX_fileodefault`. Because the version script names a symbol that does
not exist, `POSIX_fileodefault` stays local in `libga68.so`.

```
begin
    int fd = fopen ("/etc/hostname", file_o_default);
    puts (whole (fd, 0) + "'n")
end
```

With the default (static) libga68 the program links and prints 3. With
`-shared-libga68`:

```
fod.a68:(.text+0xf8): undefined reference to `POSIX_fileodefault'
collect2: error: ld returned 1 exit status
```

`nm -D libga68.so | grep fileo` shows `POSIX_fileordonly`,
`POSIX_fileordwr`, `POSIX_fileotrunc` and `POSIX_fileowronly`, but no
`POSIX_fileodefault`.

Fix: spell the entry `POSIX_fileodefault` in `ga68.map`.

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
value is 0, so the condition stays true and the loop never ends. For
`long real` the same happens from 1e19 on.

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
operator. a68g prints 2 for both. The cause has not been examined.

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
