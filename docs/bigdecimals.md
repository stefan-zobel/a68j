# The bundled module `Bigdecimals`

In ga68 and a68j, `real` has 32 bits and `long real` and
`long long real` 64 (BACKEND.md, "How the modes map"). Neither holds a
decimal fraction such as 0.1 exactly, and neither has more than about 16
significant digits. Algol 68 Genie gives `long long real` 70 decimal
digits by default (more with `PR precision=N PR`).

The module `Bigdecimals` provides decimals of any precision, mode
`Bigdecimal`, with the operators of a `real`. The values are
`java.math.BigDecimal` objects. The module comes with `a68j-backend.jar`,
like `Bigints`, and needs no option:

```
access Bigdecimals (
    bigdec_set_precision (50);
    puts (bigdec_text (bigdec_sqrt (DEC 2)) + "'n");
    puts (bigdec_text (DEC 0.1 + 0.2) + " " +
          (DEC 0.1 + 0.2 = DEC "0.3" | "equal" | "not equal") + "'n");
    puts (bigdec_fixed (BIG 22 / BIG 7, 0, 20) + "'n")
)
```

```sh
java -jar a68j-backend.jar -run prog.a68
1.4142135623730950488016887242096980785696718753769
0.3 equal
3.14285714285714285714
```

It needs Java, so it works with a68j only. The module accesses `Bigints`
publicly (`access pub Bigints`), so a program that accesses
`Bigdecimals` has the Bigints as well. It must not access `Bigints` a
second time: `access Bigints, Bigdecimals` declares everything of
`Bigints` twice, as `access Lists, Hashmaps` does for `Lists`
(java-modules.md, "`Lists` and `Hashmaps` together").

## What it provides

For Bigdecimals `a` and `b`, a value `n` of mode `int`, `long int`,
`long long int`, `real`, `long real`, `long long real` or `Bigint`, and
Bigints `i` and `j`:

| | |
|---|---|
| `DEC n`, `DEC s` | the Bigdecimal of n, or of the decimal number in the string (or char) s, rounded to the precision |
| `TOREAL a`, `TOLONGREAL a` | a as a `real` or `long real` |
| `ROUND a`, `ENTIER a` | the nearest Bigint (half away from zero), the largest Bigint not greater than a |
| `- a`, `+ a`, `ABS a` | negation, a rounded to the precision, the absolute value |
| `SIGN a` | -1, 0 or 1 |
| `a + b`, `a - b`, `a * b`, `a / b` | a Bigdecimal result; each operand may also be an n |
| `i / j` | the quotient as a Bigdecimal; i or j may also be an `int`, `long int` or `long long int` |
| `a = b`, `a /= b`, `a < b`, `a <= b`, `a > b`, `a >= b`, `EQ`, `NE`, `LT`, `LE`, `GT`, `GE` | the same operand modes as `a + b` |
| `a ** e`, `a UP e`, `a ^ e` | the power, with an `int` exponent e, also negative |
| `x +:= b`, `x -:= b`, `x *:= b`, `x /:= b`, `PLUSAB`, `MINUSAB`, `TIMESAB`, `DIVAB` | for a variable x of mode `ref Bigdecimal`; b is a Bigdecimal or an n; they yield x |
| `bigdec_set_precision (d)`, `bigdec_precision` | set the precision to d >= 1 significant digits; the precision |
| `bigdec_sqrt (a)` | the square root, for a >= 0 |
| `bigdec_whole (a, width)`, `bigdec_fixed (a, width, after)`, `bigdec_float (a, width, after, exp)` | a as a string, with the rules of `whole`, `fixed` and `float` |
| `bigdec_text (a)` | a as a string without exponent and without trailing zeros |

## The details

- **The mode.** A Bigdecimal is a Java object (java-modules.md,
  section 2): `mode Bigdecimal = ref Bigdecimal_object`. It can be passed
  around and kept in variables (`Bigdecimal x := DEC 1`), but not
  dereferenced or assigned to. The values never change: every operator
  yields a new Bigdecimal, and an assigning operator such as `+:=` puts
  the new Bigdecimal into the variable.
- **The precision.** Every operation rounds its result to the precision,
  a number of significant decimal digits, as Algol 68 Genie does for
  `long long real`. At the start it is 70, Genie's default.
  `bigdec_set_precision (d)` changes it at any time, also several times;
  the operations after the change round to it, and the values already
  computed keep their digits. `+ a` rounds a to the current precision.
  So a computation can run with guard digits and round at the end:

  ```
  bigdec_set_precision (40);
  Bigdecimal sum := DEC 0;
  for i to 30 do sum +:= DEC 1 / i od;
  bigdec_set_precision (30);
  puts (bigdec_text (+ sum) + "'n")      { 3.99498713092039107050177366412 }
  ```

  Rounding is half away from zero (`RoundingMode.HALF_UP`): with 10
  digits, `DEC 2 / 3` is 0.6666666667.
- **Decimal fractions.** The radix is 10, so `DEC 0.1 + 0.2 = DEC "0.3"`
  is true, as for `long long real` in Algol 68 Genie, whose radix is
  10⁷.
- **`=` and `is`.** `=` compares the values, `is` the objects:
  `DEC "1.0" = DEC "1.00"` is true, `DEC 1 is DEC 1` false.
- **Priorities.** The operators have the priorities of the prelude. The
  module declares no priority, so a program can access it together with
  `Hashmaps` (and so `Lists`), `Texts` and `Genie_Transput`.
- **Mixed operands.** Every dyadic operator also takes an `int`,
  `long int`, `long long int`, `real`, `long real`, `long long real` or
  `Bigint` as either operand, so `x * 2`, `1 / x` and `x < 0.5` need no
  `DEC`. The other operand is converted exactly, and only the result is
  rounded: with 10 digits, `DEC "-0.4" + long 12345678945` is
  12345678940, the rounded sum, not 12345678950, which a rounded operand
  would give.
- **Reals.** `DEC x` of a `real` or `long real` takes its shortest decimal
  (`Float.toString`, `Double.toString`), not its binary value: `DEC 0.1`
  is 0.1, not 0.100000001490116119384765625. An infinite real or a NaN is
  a runtime error. `TOREAL` and `TOLONGREAL` round to the nearest value.
- **Division of Bigints.** `/` yields a `real` for two `int`s, and so it
  yields a Bigdecimal for two Bigints: `BIG 1 / BIG 8` is 0.125.
  `Bigints` itself has no `/`.
- **`ROUND` and `ENTIER`** yield a Bigint, as those of Algol 68 Genie for
  a `long long real` yield a `long long int`: `ROUND DEC "2.5"` is 3,
  `ROUND DEC "-2.5"` is -3, `ENTIER DEC "-2.5"` is -3.
- **Power.** The exponent may be negative: `DEC 2 ** -3` is 0.125.
  `0 ** 0` is 1, and `0 ** -1` is a division by zero.
- **Strings.** `DEC s` accepts a sign `+` or `-`, digits with or without
  a point (`12`, `1.5`, `.5`, `5.`), and an exponent with `e` or `E`
  (`1.5e-3`), with white space around them, as `BIG s`. Only ASCII
  digits count.
- **Output.** `bigdec_whole`, `bigdec_fixed` and `bigdec_float` follow the
  rules of `whole`, `fixed` and `float` of the prelude for a real (the
  Revised Report, 10.3.2.1), computed exactly:
  - width 0 gives as many characters as needed, with a sign only for a
    negative value; any other width gives `ABS width` characters, filled
    with blanks on the left, and a positive width always shows a sign;
  - a width too small for `after` places gives fewer places, and if even
    no place fits, `ABS width` times `*`;
  - `bigdec_whole (a, width)` is `bigdec_fixed (a, width, 0)`;
  - digits beyond the precision are zeros: with 70 digits,
    `bigdec_fixed (DEC 1 / 3, 0, 80)` gives 70 threes and 10 zeros.

  `bigdec_text` writes all digits without an exponent, so
  `bigdec_text (DEC "1e-20")` is `0.00000000000000000001`. `print` and
  `fixed` cannot take a Bigdecimal, as procedures cannot be overloaded:
  `print ((bigdec_fixed (a, 0, 10), newline))` with `Genie_Transput`.
- **Runtime errors.** These print the position of the program and end it
  with exit status 134, as an index out of range in `Lists`:
  - a `nil` Bigdecimal;
  - a division by zero;
  - a string that `DEC` does not accept;
  - `DEC` of an infinite real or a NaN;
  - a precision below 1;
  - `bigdec_sqrt` of a negative value;
  - `TOREAL` or `TOLONGREAL` of a value beyond the limits;
  - an exponent beyond the limits of `BigDecimal` (about 10 ** 2147483647).

  For example:

  ```
  prog.a68:5: runtime error: division by zero
  ```

## Differences from Algol 68 Genie

The `long long real` of Algol 68 Genie is the model of `Bigdecimal`. The
differences:

| Case | `long long real` in Algol 68 Genie | `Bigdecimal` |
|---|---|---|
| Setting the precision | once per program, by `PR precision=N PR` or `--precision`; not below the default of 70 | `bigdec_set_precision (d)` at any time, any d >= 1 |
| Digits | rounded up to a multiple of 7 (40 becomes 42), with guard digits | exactly d |
| The last digit of a result | may be truncated in the output | rounded |
| `fixed (9.9995, 0, 3)` | `10.00`: Genie counts the digits before the point before rounding | `10.000`, as the Revised Report |
| `fixed` with width 0 and many places | fewer places when the digits do not fit its internal precision (1e30 with 50 places gives 42) | all places |
| `exp`, `ln`, `sin`, `pi` and so on | provided | not provided |

`tests/java/bigdecimals-output.a68` compares `bigdec_whole`,
`bigdec_fixed` and `bigdec_float` with Genie's `whole`, `fixed` and
`float` of a `long long real` for 11 values and 17 fields each: only the
three fields of the table above differ.

## Rosetta Code

11 of the 1328 Algol 68 Genie solutions of Rosetta Code
(`tests/rosetta/orig`) use `LONG LONG REAL`, 7 of them with
`PR precision=N PR`. Some need only the operators and `sqrt`, such as
`arithmetic-geometric-mean-calculate-pi`; others need `exp`, `ln` or
`pi`, which `Bigdecimals` does not provide, such as `ramanujans-constant`.
A program ported to `Bigdecimals` declares `Bigdecimal` where it declared
`long long real`, sets the precision with `bigdec_set_precision` instead
of the pragmat, and writes `bigdec_fixed (x, w, d)` instead of
`fixed (x, w, d)`.

## Not provided

- **Mathematical functions** besides `bigdec_sqrt`: `exp`, `ln`, `log`,
  the trigonometric functions and `pi`. `BigDecimal` has none of them;
  they would need series with guard digits of their own.
- **`long long real` with multiple precision.** Representing
  `long long int` and `long long real` by `Bigint` and `Bigdecimal` under
  `-fgenie` would make the primitive modes Java objects, with all their
  operators, transput and widening. That is a theoretical possibility
  only, and unlikely to happen.

## Open question

The precision is one setting for the whole program, state of the module
that every operation reads. With `-fpar-threads`, the units of a parallel
clause run in threads of their own and share it: a thread that changes
the precision changes it for the others. The setting is a `volatile`
field, so every thread sees the last value set, but a program that sets
different precisions in parallel units gets results that depend on the
timing. Whether the precision should rather belong to a thread is open.

## Inside

- `backend/lib/bigdecimals.a68` is generated by `tools/gen_bignums.py`,
  with `backend/lib/bigints.a68`. Each operator calls a formal hole of the
  static methods of `a68j.jlib.BigDec`.
- `a68j.jlib.BigDec` extends `a68j.rt.JavaObject` and keeps a
  `BigDecimal`. The precision is a `MathContext` in a `volatile` static
  field. The operations with two operands exist only for two
  Bigdecimals; a mixed operator converts the other operand first with a
  method `exact...` (no rounding), a call more than in `Bigints`. The
  conversions have distinct names (`ofLong`, `ofFloat`, `ofBigint`,
  `parse`), since a Java object fits every parameter of a reference type
  at the same cost and `of(String)` next to `of(BigInt)` would make a
  formal hole ambiguous (java-modules.md, "Which method").
- `ROUND`, `ENTIER` and `/` of Bigints use the package-private methods
  `BigInt.wrap` and `BigInt.value`, which no formal hole sees.
- Errors are raised with `JavaObject.error`, which prints the position of
  the program that used the operator.

## Tests

`tests/java`, run by `tools/java_tests.sh`:

- **`bigdecimals-basic.a68`:** the conversions, every operator with every
  operand mode on either side, the quotient of Bigints, the power with a
  negative exponent, comparisons by value, `ROUND` and `ENTIER`, the
  assigning operators.
- **`bigdecimals-precision.a68`:** changing the precision while the
  program runs, values that keep their digits, `+ a`, a mixed operator
  that rounds once, `bigdec_sqrt (DEC 2)` with 100 digits, a sum with
  guard digits.
- **`bigdecimals-output.a68`:** `bigdec_whole`, `bigdec_fixed`,
  `bigdec_float` and `bigdec_text`, compared with Algol 68 Genie 3.13.4
  (see "Differences from Algol 68 Genie").
- **`bigdecimals-error-1.a68` to `bigdecimals-error-6.a68`:** `nil`, a
  division by zero, a string that is not a number, a precision of 0,
  the square root of a negative value, `TOREAL` beyond the limits.
- **`bigdecimals-modules.a68`:** `Bigdecimals`, `Hashmaps`, `Texts` and
  `Genie_Transput` in one access clause, with Bigints from `Bigdecimals`.
