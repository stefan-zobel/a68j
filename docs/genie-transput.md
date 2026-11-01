# The bundled module `Genie_Transput`

ga68, and a68j with it, has no transput of the Revised Report: the standard
prelude has no `print`, `newline`, `put` or formats. Output goes through
the POSIX prelude (`puts`, `putchar`, `fputs` ...). The module
`Genie_Transput` provides the part of the Algol 68 Genie transput that
programs use most, written in plain Algol 68 on top of the POSIX prelude.
It comes with `a68j-backend.jar`, like `Lists` and `Hashmaps`, and needs no
option:

```
access Genie_Transput (
    int x = 42;
    print (("x = ", x, newline));
    print ((3.25, " ", true, newline))
)
```

```sh
java -jar a68j-backend.jar -run prog.a68
x =         +42
+3.250000e +0 T
```

The module is accessed explicitly, as any other module. a68j does not
put it into every program, so a program that compiles with a68j also
compiles with ga68.

## With ga68

The module uses no Java, so the same source
([`backend/lib/genietransput.a68`](../backend/lib/genietransput.a68))
compiles with ga68 as an ordinary module, and the program gives the same
output:

```sh
ga68 -c genietransput.a68
ga68 -I. prog.a68 genietransput.o
```

## What it provides

| Name | Meaning |
|---|---|
| `File`, `stand_in`, `stand_out`, `stand_error` | the three standard channels; a `File` is a `struct (int fd, int line)`, not the `file` of the Revised Report |
| `newline`, `space`, `newpage`, `backspace` | layout procedures, `proc (ref File) void` |
| `Outitem` | the modes `print` and `put` accept (below) |
| `put (f, items)`, `print (items)`, `write (items)` | output to `f`, or to `stand_out` |
| `int_width`, `long_int_width`, `long_long_int_width`, `real_width`, `long_real_width`, `long_long_real_width`, `exp_width`, `long_exp_width`, `long_long_exp_width` | the environment enquiries of the transput (RR 10.3.1.1) |
| `char_in_string`, `last_char_in_string`, `string_in_string` | string searching (RR 10.3.2.4, Algol 68 Genie); `pos` may be `nil` |
| `is_upper`, `is_lower`, `is_alpha`, `is_digit`, `is_alnum`, `is_space`, `is_xdigit`, `to_upper`, `to_lower` | character classes of Algol 68 Genie; they consider ASCII letters and digits only |

`print` and `put` accept `int`, `long int`, `long long int`, `real`,
`long real`, `bool`, `char`, `string`, `bits` and `long bits`; rows
`[] int`, `[] long int`, `[] real`, `[] long real`, `[] bool`,
`[] string` and `[] bits`; `[,] int`, `[,] real` and `[,] bool`; and the
layout procedures. `newline` counts the lines of a `File` in `line of f`.

## Layout

The layout follows Algol 68 Genie:

| Value | Printed as |
|---|---|
| `42`, `-7` | `        +42`, `         -7` (right-aligned with a sign in a field of `int_width + 1` characters) |
| `long 1234567890123` | `      +1234567890123` (field of `long_int_width + 1`) |
| `3.25`, `1e10` | `+3.250000e +0`, `+1.000000e+10` |
| `long 2.5` | `+2.50000000000000e  +0` |
| `true`, `false` | `T`, `F` |
| `16r5` (`bits`) | 32 characters `T` and `F` |
| a row | its elements one after the other, each in its own layout |

## Not provided

- reading (`read`, `get`; `stand_in` exists, but nothing reads from it);
- opening files (`open`, `establish`, `close`);
- formats (`$...$`, `printf`);
- values of mode `long long real` (80-bit `long long real` is out of scope
  for a68j);
- `[,] char`: a union with both `string` and `[,] char` makes ga68 crash
  in a conformity clause (internal compiler error in `verify_gimple`).

## The name

The module is not called `Transput`, because ga68 reserves the module name
`TRANSPUT` for libga68 (`A68_MODULE_FILES` in `gcc/algol68/a68-lang.cc`).
A module named `Transput` compiles, but `access Transput` then fails with
`cannot find module 'TRANSPUT'`, in ga68 and in a68j alike.

## Tests

- `tests/extra/genie-transput-1.a68` prints every mode that `print`
  accepts and uses all procedures of the module; ga68 and a68j give the same
  output.
- All Rosetta Code programs (`tests/rosetta`) run inside
  `access Genie_Transput ( ... )`.

In `{ dg-modules "genietransput" }` the test runners treat the module as a
bundled one (`tools/modtest.py`): `run_native.py` compiles the source in
`backend/lib` with ga68, and `run_jvm.py` uses the module in the jar.
