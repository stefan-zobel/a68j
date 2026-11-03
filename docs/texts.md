# The bundled module `Texts`

Algol 68 has strings as `flex [] char`, slices, `+` and comparisons, but
no case conversion, trimming, splitting, joining, replacing, padding or
conversion of a string to a number. Programs build these by hand (in the
Algol 68 Genie solutions of Rosetta Code at least 98 programs do, see
"How many programs need it" below). The module `Texts` provides them,
written in plain Algol 68. It comes with `a68j-backend.jar`, like
`Genie_Transput`, and needs no option:

```
access Texts (
    [] string fields = "  alpha, Beta ,,gamma " SPLIT ",";
    for i to UPB fields
    do puts ("[" + UPPER TRIM fields[i] + "]" + ("x" + whole (i, 0) RJUST 4) + "'n")
    od;
    puts (join (words ("  the   quick brown fox "), "-") + "'n");
    puts (whole (TOINT " 42 " + 1, 0) + "'n")
)
```

```sh
java -jar a68j-backend.jar -run prog.a68
[ALPHA]  x1
[BETA]  x2
[]  x3
[GAMMA]  x4
the-quick-brown-fox
43
```

## With ga68

The module uses no Java, so the same source
([`backend/lib/texts.a68`](../backend/lib/texts.a68)) compiles with ga68 as
an ordinary module, and the program gives the same output:

```sh
ga68 -c texts.a68
ga68 -I. prog.a68 texts.o
```

## What it provides

| Name | Meaning |
|---|---|
| `UPPER s`, `LOWER s` | `s` in upper or lower case; also for a `char`, giving a `char` |
| `TRIM s`, `LTRIM s`, `RTRIM s` | `s` without white space at both ends, at the start, at the end |
| `TOINT s`, `TOREAL s` | the number in `s`; also for a `char`; anything else is a runtime error |
| `TOLONGINT s`, `TOLONGREAL s` | the same for `long int` and `long real` |
| `s STARTS p`, `s ENDS p` | whether `s` begins or ends with `p` (priority 5, as `<`) |
| `s SPLIT sep` | the fields of `s` between the separators, as `[] string` (priority 6, as `+`) |
| `s LJUST w`, `s RJUST w`, `s CENTRE w` | `s` filled with blanks to the width `w`: left-aligned, right-aligned, centred (priority 6) |
| `words (s)` | the words of `s`: the parts between runs of white space |
| `join (parts, sep)` | the strings of `parts` with `sep` between them |
| `replace_all (s, old, new)` | `s` with every occurrence of `old` replaced by `new` |
| `string_to_int (s, ref int v) bool` | `true` and the number in `v` if `s` is an int, else `false` and `v` unchanged |
| `string_to_real (s, ref real v) bool` | the same for a real |
| `string_to_long_int (s, ref long int v) bool`, `string_to_long_real (s, ref long real v) bool` | the same for `long int` and `long real` |

The interface follows the standard prelude: monadic operators for what
transforms one value (as `ABS`, `REPR`, `ENTIER`), dyadic operators where
an expression with two operands reads naturally, and procedures for three
or more parameters, an output parameter, or a row as the main argument.
Monadic operators bind more tightly than dyadic ones, so
`UPPER TRIM s + "!"` is `(UPPER (TRIM s)) + "!"`, and with the priority 6
`"x" + s RJUST 4` is `("x" + s) RJUST 4`.

## The details

- **`char` operands.** An operand of an operator is only firm, so a
  `char` (and a one-letter denotation such as `","` is a `char`) is not
  widened to a `string` there. Every dyadic operator therefore takes a
  `char` as each of its string operands: `s SPLIT ","`,
  `"a" STARTS "ab"`, `"x" RJUST 3`. The procedures take strings, and a
  `char` argument is widened as usual.
- **ASCII.** `UPPER` and `LOWER` change the letters `a` to `z` and `A` to
  `Z` only, as `to_upper` and `to_lower` of `Genie_Transput` and Algol 68
  Genie do; other characters stay as they are. White space is blank, tab,
  newline, carriage return, form feed and vertical tab.
- **Results** are new strings with the lower bound 1, whatever the bounds
  of the operands; `LWB (UPPER "abc"[@5])` is 1.
- **`SPLIT`** takes the separator literally and finds the separators from
  left to right, without overlapping. Empty fields are kept: a string
  with n separators has n + 1 fields, so `"a,,b"` gives three fields,
  `","` two empty fields and `""` one empty field. An empty separator is a
  runtime error. `words` drops empty fields instead.
- **`replace_all`** replaces from left to right, without overlapping
  (`replace_all ("aaaa", "aa", "b")` is `"bb"`); the new text is not
  searched again. An empty `old` is a runtime error.
- **Padding.** A string at least as long as the width is returned
  unchanged. In `CENTRE` an odd number of blanks puts the extra blank on
  the right: `"ab" CENTRE 5` is `" ab  "`.
- **Numbers.** White space around the number is allowed, and a sign `+`
  or `-` directly before it. An int is decimal digits only; it may reach
  `-max_int - 1`, and a value beyond the limits of `int` is not an int. A
  real is digits with an optional point and an optional exponent (`e` or
  `E`, an optional sign, digits): `5`, `-0.5`, `.5`, `5.`, `1e10`,
  `2.5E-3`, as `read` of `Genie_Transput` accepts it. The conversion is
  the one of `Genie_Transput` (genie-transput.md, "Reading"), so `TOREAL s`
  and `read` give the same value: the number is converted to a correctly
  rounded `long real`, which `TOREAL` shortens to a `real`. A real beyond
  `max_real` is not a real, and one too small for `real` becomes 0; the
  same holds for `long real` and `long_max_real`. Note that `real` has
  single precision in ga68 and a68j (`max_real` is about 3.4e38) and
  `long real` double precision, so `TOLONGREAL` is the one to use for
  more than about seven digits. Likewise `TOLONGINT` reads up to
  `long_max_int` (`3000000000` is not an int, but a long int).
- **Runtime errors** (a string that is not a number for `TOINT`,
  `TOREAL`, `TOLONGINT` or `TOLONGREAL`, an empty separator, an empty
  `old`) print a message on stand
  error and end the program with exit status 1, as a transput error of
  `Genie_Transput` does:

  ```
  runtime error: TOINT of "12x", which is not an int
  ```

  `string_to_int`, `string_to_real` and their `long` versions test a
  string without this error.

## With `Genie_Transput` and `Lists`

A program can access `Texts` together with `Genie_Transput`,
`Lists` or `Hashmaps` (`access Genie_Transput, Texts ( ... )`). Two
modules that export the same identifier, or declare the priority of the
same operator, cannot be accessed together (java-modules.md, section 4),
so `Texts` declares none of the names of these modules: the searches
`char_in_string`, `last_char_in_string` and `string_in_string`, the
character classes `is_upper` ... and `to_upper`, `to_lower` stay in
`Genie_Transput`, and `Texts` declares none of the operators of `Lists`.

The conversion of decimal digits to a real is copied from
`Genie_Transput`, and the two copies must stay the same. A third module
that both access without `pub` would work as well (checked with ga68 and
a68j), but then every program and test that uses `Genie_Transput` with
ga68 would have to compile and link it too.

## How many programs need it

In the 1328 Algol 68 Genie solutions of Rosetta Code
(`tests/rosetta/orig`), at least 98 programs declare such helpers
themselves (found by the names of their `PROC` and `OP` declarations,
each match checked by hand, so this is a lower bound); 29 of them belong
to tasks about text, the others need the helpers on the side. By kind:
number to string 29, padding and centring 31, upper and lower case 14,
string to number 11, trimming 10, splitting 10, prefix, suffix and index
9, replace 3, join 3. A further 31 programs use `associate` on a string
only, to format or parse with the transput, and 9 use the regular
expressions of Algol 68 Genie (`grep in string`, `sub in string`). For
comparison, only 9 programs use the file system (java-open-points.md
B2), all of them in tasks about the file system.

## Not provided

- Unicode: letters beyond ASCII are not changed by `UPPER` and `LOWER`.
- Searching: `char_in_string`, `string_in_string` and
  `last_char_in_string` are in `Genie_Transput`.
- Regular expressions: they need Java and are in the module `Regex`
  ([regex.md](regex.md)).

## Tests

- `tests/extra/texts-1.a68` uses every operator and procedure, with empty
  strings, lower bounds other than 1, `char` operands and the limits of
  `int`, `long int` and `long real`.
- `tests/extra/texts-2.a68` accesses `Genie_Transput` and `Texts`
  together and reads 3000 pseudo-random numbers of each of `real`, `int`,
  `long real` and `long int` both with `Texts` and with `get` on an
  associated string: the values are the same.
- `tests/extra/texts-error-1.a68` and `texts-error-2.a68`: the runtime
  errors of `TOINT` and `SPLIT`.
- `tests/java/texts-hashmaps.a68` accesses `Hashmaps`, `Genie_Transput`
  and `Texts` together (a68j only, as `Hashmaps` needs Java).

ga68 and a68j give the same output (`tests/extra/expected`). In
`{ dg-modules "texts" }` the test runners treat the module as a bundled
one (`tools/modtest.py`), as `Genie_Transput`.
