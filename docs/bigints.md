# The bundled module `Bigints`

In ga68 on x86_64, `long int` and `long long int` have 64 bits, and so
they have in a68j (BACKEND.md, "How the modes map"). An overflow goes
unnoticed in both: 21! as a `long long int` comes out as
`-4249290049419214848`. Algol 68 Genie gives `long long int` 72 digits
by default (more with `PR precision=N PR`) and stops at an overflow, so
programs written for it, such as many Rosetta Code solutions, give wrong
results with ga68 and a68j.

The module `Bigints` provides integers of any size, mode `Bigint`, with
the operators of an `int`. The values are `java.math.BigInteger`
objects. The module comes with `a68j-backend.jar`, like `Lists`, and needs
no option:

```
access Bigints (
    Bigint f := BIG 1;
    for i to 30 do f *:= i od;
    puts (big_whole (f, 0) + "'n");
    Bigint m = BIG 2 ** 127 - 1;
    puts (big_whole (m MOD 1000, 0) + " " +
          (big_probable_prime (m) | "prime" | "composite") + "'n")
)
```

```sh
java -jar a68j-backend.jar -run prog.a68
265252859812191058636308480000000
727 prime
```

It needs Java, so it works with a68j only.

## What it provides

For Bigints `a` and `b` and a value `n` of mode `int`, `long int` or
`long long int`:

| | |
|---|---|
| `BIG n`, `BIG s` | the Bigint of n, or of the decimal digits in the string (or char) s |
| `TOINT a`, `TOLONGINT a` | a as an `int` or `long int` |
| `big_whole (a, width)` | a as a string, with the rules of `whole` |
| `big_text (a, radix)`, `big_from_text (s, radix)` | a in the radix 2 to 36, and back |
| `- a`, `+ a`, `ABS a` | negation, a itself, the absolute value |
| `SIGN a`, `ODD a` | -1, 0 or 1; whether a is odd |
| `a + b`, `a - b`, `a * b`, `a % b`, `a OVER b`, `a MOD b`, `a %* b` | a Bigint result; each operand may also be an `int`, `long int` or `long long int` |
| `a = b`, `a /= b`, `a < b`, `a <= b`, `a > b`, `a >= b`, `EQ`, `NE`, `LT`, `LE`, `GT`, `GE` | the same operand modes |
| `a ** e`, `a UP e`, `a ^ e` | the power, with an `int` exponent e |
| `x +:= b`, `x -:= b`, `x *:= b`, `x %:= b`, `x %*:= b`, `PLUSAB`, `MINUSAB`, `TIMESAB`, `OVERAB`, `MODAB` | for a variable x of mode `ref Bigint`; b is a Bigint, `int`, `long int` or `long long int`; they yield x |
| `big_gcd (a, b)` | the greatest common divisor |
| `big_sqrt (a)` | the largest integer whose square is at most a |
| `big_mod_pow (a, e, m)` | `a ** e MOD m`, for e >= 0 and m > 0 |
| `big_probable_prime (a)` | whether a is a prime, with an error probability below 2 ** -100 |

## The details

- **The mode.** A Bigint is a Java object (java-modules.md, section 2):
  `mode Bigint = ref Bigint_object`. It can be passed around and kept in
  variables (`Bigint x := BIG 1`, a variable of mode `ref Bigint`), but
  not dereferenced or assigned to. The values never change: every
  operator yields a new Bigint, and an assigning operator such as `+:=`
  puts the new Bigint into the variable.
- **`=` and `is`.** `=` compares the values, `is` the objects:
  `BIG 5 = BIG 5` is true, `BIG 5 is BIG 5` false.
- **Priorities.** The operators have the priorities of the prelude, so
  `BIG 2 + BIG 3 * BIG 4 ** 2` is 50 and `- BIG 2 ** 2` is 4 (monadic
  operators bind most tightly, as for `int`). The module declares no
  priority, so a program can access it together with `Lists`,
  `Hashmaps`, `Texts` and `Genie_Transput`. `TOINT` of `Texts` takes a
  string, that of `Bigints` a Bigint, so both can be used side by side.
- **Mixed operands.** Every dyadic operator also takes an `int`,
  `long int` or `long long int` as either operand, so `f * i`,
  `1 + f` and `f MOD 10 = 0` need no `BIG`. `BIG` itself takes a
  `char` as well, since a one-character denotation such as `"7"` is a
  `char` and an operand is not rowed to a string.
- **Division.** `%` and `OVER` truncate toward zero, and `MOD` and `%*`
  yield a value from 0 to `ABS b - 1`, as for `int`: `BIG -7 % 2` is -3,
  `BIG -7 MOD 3` is 2, `BIG 7 MOD -3` is 1.
- **Power.** `0 ** 0` is 1.
- **Strings.** `BIG s` accepts decimal digits with a sign `+` or `-`
  directly before them and white space around them (blank, tab, newline,
  carriage return, form feed, vertical tab), as `TOINT` of `Texts`.
  `big_from_text` accepts the same with the digits of the radix, letters
  in either case. Only ASCII digits and letters count.
- **Output.** `big_whole (a, width)` follows the rules of `whole` for an
  `int`:
  - width 0 gives as many characters as needed, with a sign only for a
    negative value;
  - any other width gives `ABS width` characters, filled with blanks on
    the left: a positive width always shows a sign, a negative width only
    a minus sign;
  - a width too small gives `ABS width` times `*`.

  `big_text` writes small letters and a minus sign for a negative value.
  `print` and `whole` cannot take a Bigint, as procedures cannot be
  overloaded: `print ((big_whole (a, 0), newline))` with
  `Genie_Transput`.
- **Runtime errors.** These print the position of the program and end it
  with exit status 134, as an index out of range in `Lists`:
  - a `nil` Bigint;
  - a division by zero;
  - a negative exponent of `**`;
  - `TOINT` or `TOLONGINT` of a value beyond the limits;
  - a string that `BIG` or `big_from_text` does not accept;
  - a radix outside 2 to 36;
  - `big_sqrt` of a negative value;
  - `big_mod_pow` with a negative exponent or a modulus that is not
    positive.

  For example:

  ```
  prog.a68:5: runtime error: division by zero
  ```

## Differences from `int`, ga68 and Algol 68 Genie

| Case | `int` in ga68 and a68j | `Bigint` |
|---|---|---|
| Integer division by zero | ends the program with SIGFPE and no message (exit status 136) | a runtime error with the position |
| `2 ** -1` | 1 (the Revised Report leaves it undefined) | a runtime error |
| Overflow | silent | cannot happen |

The `long long int` of Algol 68 Genie has a fixed number of digits, set
by `PR precision=N PR`. A Bigint grows as needed, so a program ported
from Algol 68 Genie drops the pragmat and declares `Bigint` where it
declared `long long int`. The changes are:

- `LENG LENG 1` becomes `BIG 1`;
- `whole (x, 0)` becomes `big_whole (x, 0)`.

## Speed

Measured with JDK 25, as the time of the operations alone, without the
start of the JVM:

| | |
|---|---|
| 10⁷ additions of a small `int` to a Bigint (`s +:= i`) | 0.25 s, about 25 ns each |
| 20000! by 20 000 products | 0.12 s |
| `big_whole` of 20000! (77 338 digits) | 0.07 s |
| (20000!) ** 2 (154 675 digits) | 0.01 s |

- **Small values.** The 10⁷ additions with `long long int` take 0.01 s,
  so a Bigint is about 30 times slower on small values. This is the cost
  of a new object for each result.
- **Large values.** The same factorial in plain Algol 68, as products of
  rows of digits in base 10⁹, takes 0.49 s.
  `BigInteger` multiplies large numbers with the algorithms of Karatsuba
  and Toom-Cook and converts them to decimal digits by divide and conquer.
  A plain Algol 68 version would be quadratic in both and would also need
  a long division of its own.

## Rosetta Code

71 of the 1328 Algol 68 Genie solutions of Rosetta Code
(`tests/rosetta/orig`) use `LONG LONG INT`, and 5 of them raise its
precision with `PR precision=N PR`, up to 183 231 digits for the task
Arbitrary-precision integers (included). Examples are the Lucas-Lehmer
test, RSA code, the partition function p, and the Bernoulli, Bell and
Stirling numbers. These programs need `Bigints` on a68j wherever their
values do not fit into 64 bits.

## Not provided

- **Division with a fractional result (`/`).** It would yield a decimal.
  A later module `Bigdecimals` on `java.math.BigDecimal` would add it.
  That module would access `Bigints` with `access pub Bigints`, as
  `Hashmaps` does with `Lists`, and the generator
  (`tools/gen_bignums.py`) is laid out for a second kind of number.
- **Bit operations** (`AND`, `OR`, `SHL` and so on) on Bigints.
- **`long long int` results.** `TOLONGINT` gives a `long int`, which in
  a68j holds the same values.

## Inside

- `backend/lib/bigints.a68` is generated by `tools/gen_bignums.py`.
  Each operator calls a formal hole of the static methods of
  `a68j.jlib.BigInt`.
- `a68j.jlib.BigInt` extends `a68j.rt.JavaObject` and keeps a
  `BigInteger`. Every method with two operands comes in three forms:
  `(BigInt, BigInt)`, `(BigInt, long)` and `(long, BigInt)`. So a mixed
  operator is a single call; an `int` and a `long long int` are passed as
  a Java `long`.
- Errors are raised with `JavaObject.error`, which prints the position of
  the program that used the operator.

## Tests

`tests/java`, run by `tools/java_tests.sh`:

- **`bigints-basic.a68`** uses every operator for every combination of
  operand modes:
  - it compares 14 013 results with the same operation on `long int`
    (nine values of both signs, among them 0, 1 and -1);
  - it compares `big_whole` with `whole` for the widths -12 to 12;
  - it uses the procedures and the conversions.
- **`bigints-big.a68`:**
  - 1000!;
  - fib (1000);
  - 5 ** 4 ** 3 ** 2, whose 183 231 digits begin with
    `62060698786608744707` and end with `92256259918212890625`;
  - the Lucas-Lehmer test for the Mersenne primes up to 2 ** 127 - 1.
- **`bigints-error-1.a68` to `bigints-error-5.a68`:** division by zero,
  a negative exponent, `nil`, `TOINT` beyond the limits, a string that is
  not an integer.
- **`bigints-modules.a68`:** `Bigints`, `Lists`, `Texts` and
  `Genie_Transput` in one access clause.
