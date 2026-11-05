# The bundled module `Genie_Transput`

ga68, and a68j with it, has no transput of the Revised Report: the standard
prelude has no `print`, `read`, `newline`, `put`, `get`, `open` or
formats. Input and output go through the POSIX prelude (`puts`, `fputs`,
`fgetc`, `fopen` ...). The module `Genie_Transput` provides the part of the
Algol 68 Genie transput that programs use most, written in plain Algol 68
on top of the POSIX prelude.
It comes with `a68j-backend.jar`, like `Lists` and `Hashmaps`, and needs no
option:

```
access Genie_Transput (
    int x = 42;
    print (("x = ", x, newline));
    print ((3.25, " ", true, newline));
    int n = read_int;
    print (("n = ", n, newline))
)
```

```sh
echo 7 | java -jar a68j-backend.jar -run prog.a68
x =         +42
+3.250000e +0 T
n =          +7
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

## In UPPER stropping

In UPPER stropping the module name is a bold word, written in capitals.
`GENIETRANSPUT` works too, because module names are compared without case
and without underscores. The procedures and values of the module stay in
lower case:

```
ACCESS GENIE_TRANSPUT
BEGIN
   INT n = 42;
   print (("n = ", n, newline))
END
```

```sh
java -jar a68j-backend.jar -fstropping=upper -run prog.a68
```

The modes `Outitem` and `Initem` are written `OUTITEM` and `INITEM`. The
modes `File` and `Channel` cannot be named, however: in UPPER stropping
`FILE` and `CHANNEL` denote the standard modes of the same name, not
those of the module, and a declaration such as `REF FILE f = stand_out`
fails with "cannot be coerced to REF FILE". Calls such as `print (...)`
or `put (stand_out, ...)` do not need these names. A program that does
need them can access a small module in SUPPER stropping that gives the
modes other names:

```
module Upper_Files =
access pub Genie_Transput
def
   pub mode Gfile = File;
   pub mode Gchannel = Channel;
   skip
fed
```

```sh
java -jar a68j-backend.jar upperfiles.a68 -d lib/libupperfiles.jar
java -jar a68j-backend.jar -fstropping=upper -I lib -run prog.a68
```

```
ACCESS UPPER_FILES (
   REF GFILE f = stand_out;
   GCHANNEL c = stand_out_channel;
   put (f, ("put possible: ", put_possible OF c, newline))
)
```

Because of `access pub`, the program sees all of `Genie_Transput` through
`UPPER_FILES` as well.

## What it provides

| Name | Meaning |
|---|---|
| `File`, `stand_in`, `stand_out`, `stand_error` | files and the three standard files; a `File` is a structure of the module, not the `file` of the Revised Report (ga68 does not provide that yet) |
| `Channel`, `stand_in_channel`, `stand_out_channel`, `stand_back_channel` | channels: a file opened on them may be read, written, or both |
| `open (f, name, channel)`, `establish (f, name, channel)`, `close (f)`, `reset (f)` | opening and closing files (below) |
| `associate (f, s)` | `get` and `put` on the string variable `s` instead of a file (below) |
| `newline`, `space`, `newpage`, `backspace` | layout procedures, `proc (ref File) void`, for output and for input |
| `Outitem` | the modes `print` and `put` accept (below) |
| `put (f, items)`, `print (items)`, `write (items)` | output to `f`, or to `stand_out` |
| `Initem` | the modes `read` and `get` accept: names of `int`, `long int`, `long long int`, `real`, `long real`, `bool`, `char` and `string`, and the layout procedures |
| `get (f, items)`, `read (items)` | input from `f`, or from `stand_in` |
| `read_int`, `read_long_int`, `read_real`, `read_long_real`, `read_bool`, `read_char`, `read_string`, `read_line` | read one value from `stand_in`; `read_string` reads the rest of the line, `read_line` also consumes the newline |
| `eof (f)`, `eoln (f)`, `make_term (f, s)` | end of file; whether the next character is a newline; the characters of `s` end a string that is read |
| `on_logical_file_end (f, p)`, `on_value_error (f, p)` | event handlers, `proc (ref File) bool` (below) |
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

## Reading

Reading follows Algol 68 Genie (measured with Algol 68 Genie 3.1.2):

- Numbers (`int`, `long int`, `long long int`, `real`, `long real`) skip
  white space, including newlines. A number has an optional sign directly
  before its digits and ends at the first character that does not belong
  to it: `12abc` gives 12, and the next character read is `a`; `7e2` read
  as `int` gives 7. A real is digits with an optional point and fraction
  and an optional exponent: `5.`, `.5e-1` and `1E5` are reals.
- A `char` is the next character; at the end of a line it is the first
  character of the next line that is not empty. At the end of the file
  the first `char` read is `REPR 0`, without an event; the next ones
  are the logical file end event. A `bool` is read as a `char` and must
  be `T` or `F` (a blank is a value error).
- A `string` is the rest of the line, up to the newline (which is not
  consumed) or a character of the terminator set by `make_term`; on an
  empty line it is `""`.
- `newline` skips the rest of the line, including the newline; `space`
  skips one character.
- `eof (f)` and `eoln (f)` need a file in read mood: before the first
  `get` (after `open`, `establish`, `associate` or `reset`) they are the
  error "file is in undetermined mood". `eoln` is true if the next
  character is a newline; at the end of the file it is false, and the
  first `eoln` there is the logical file end event, unless `eof` has
  already found the end.

A `long real` is correctly rounded: the first 18 significant digits are
scaled by a power of ten in double-double arithmetic, with `+ - * /`
only, so ga68 and a68j read the same bits (a `real` is the `long real`
shortened). Characters are read one at a time with `fgetc`, so reading is
simple rather than fast.

## Files

```
File f;
if open (f, "data.txt", stand_in_channel) /= 0
then print (("cannot open data.txt", newline))
else on_logical_file_end (f, (ref File g) bool: goto done);
     do string line; get (f, (line, newline)); print ((line, newline)) od;
done: close (f)
fi
```

As in Algol 68 Genie, `open` and `establish` only record the name and the
channel; the first `get` opens the file for reading, the first `put`
creates it or truncates it. A file is then read or written (its "mood")
until it is closed or reset.

| Call | Result |
|---|---|
| `open (f, name, channel)` | 0 if the file exists and can be opened, else 1 (also for a directory); a file that does not exist yet can still be written |
| `establish (f, name, channel)` | 0; the first `put` fails if the file exists already |
| `close (f)` | closes the file (not for `stand_in`, `stand_out`, `stand_error`) |
| `reset (f)` | closes the file; the next `get` reads it from the start, the next `put` writes it anew |

`open (stand_out, name, stand_out_channel)` redirects `print` to a file.

## Strings

`associate (f, s)` makes `f` read from and write to the string variable
`s` (a `ref string`), as in Algol 68 Genie:

```
string s := "12 34"; int a, b;
File f; associate (f, s);
get (f, (a, b));                    { a = 12, b = 34 }
reset (f); put (f, (whole (a + b, 0), newline));
print (s)                           { "46" and a newline }
```

- `get` reads the characters of `s` from the start, with the same rules
  as for a file; the end of the string is the end of the file. The
  position is kept when `s` is changed in between.
- The first `put` empties `s`; every `put` appends to the string that
  `s` holds then (so `s` may be changed between two calls). `newline` and
  `newpage` write the characters `"'n"` and `"'f"`.
- As with a file, `f` is either read or written until it is reset;
  `reset (f)` reads `s` from the start again, or lets the next `put`
  empty it. `close (f)` ends the association.
A transput error ends the program with a message on stderr and exit
status 1, as in Algol 68 Genie, for example `runtime error: cannot open
"x.txt" for getting, no such file or directory`, `runtime error: file is in
read mood` or `runtime error: end of file reached`.

## Events

`on_logical_file_end (f, p)` and `on_value_error (f, p)` set the handler of
the end of the file and of input that does not fit the mode (or a number
out of range). If the handler returns `true`, the event is handled: the
item being read is left unchanged and reading goes on with the next item.
If it returns `false`, or no handler is set, the program ends with an
error. A handler may also jump out with `goto`. `open`, `establish` and
`associate` reset the handlers. Exceptions to "unchanged": a `char` or a
`string` read at the end of the file becomes `REPR 0` or `""`, as in
Algol 68 Genie.

The end of the file is an event when a number, `bool` or `string` is read
there, for a `char` from the second one on (see Reading), for `newline`
only if the last line is not empty (a last line without a newline still
reads as a string), and for the first `eoln` there.

## Differences from Algol 68 Genie

- `int` has 32 bits (as in ga68): `2147483648` read as `int` is a value
  error.
- A handler is called once per item. Once the end of the file has been
  reached, Algol 68 Genie calls it several times for one item (twice for
  a `char` or `newline`, three times for the other modes), and after an
  event of `eoln` it treats the next number as a value error.
- After a handled value error the item is unchanged; Algol 68 Genie
  assigns what it has read so far (`1.5` for `1.5e+x`).
- An exponent without digits before it (`e5`) is not part of a real; Algol
  68 Genie consumes it.
- `reset` is allowed on every opened file; Algol 68 Genie refuses it for
  `stand_out_channel` ("cannot reset file").
- Reading with `getchar` or `fgetc` on the same file loses the character
  that `get` has read ahead.

## Not provided

- formats (`$...$`, `printf`): ga68 does not compile format texts, see
  [`TODO-formats.md`](TODO-formats.md);
- `create`, the events other than
  logical file end and value error, reading rows, binary transput
  (`put_bin`, `get_bin`), and the channels other than the three above
  (`stand_draw_channel` ...);
- rows of rows (`[] [] int` ...) as items of `print` or `put`: the
  union `OUTITEM` has rows of the plain modes only, and a union cannot
  cover rows of any depth; a68g straightens them (Rosetta
  `flatten-a-list-2` fails to compile for this reason);
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
  accepts and uses all output procedures of the module;
  `tests/extra/genie-transput-read-1.a68` writes a file and reads it back
  with all the reading procedures, events and opening procedures;
  `tests/extra/genie-transput-associate-1.a68` reads from and writes to
  strings; ga68 and a68j give the same output. The associate test gives the
  same output with Algol 68 Genie too (converted to UPPER stropping),
  except for the widths of the numbers and the position in the last error
  message.
- All Rosetta Code programs (`tests/rosetta`) run inside
  `access Genie_Transput ( ... )`; `tools/a68supper.py` converts their
  `FILE` and `CHANNEL` to `File` and `Channel`.

In `{ dg-modules "genietransput" }` the test runners treat the module as a
bundled one (`tools/modtest.py`): `run_native.py` compiles the source in
`backend/lib` with ga68, and `run_jvm.py` uses the module in the jar.
