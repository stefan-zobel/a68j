# The bundled module `Math`

The standard prelude of ga68 has `sqrt`, `exp`, `ln`, `sin`, `cos`,
`tan`, `arcsin`, `arccos` and `arctan`. Algol 68 Genie has more: the
hyperbolic functions and their inverses, `arctan2`, the cube root, the
gamma function, the functions of a complex number, and other names for
some of them (`acos`, `atan2`, `cexp`). The module `Math` provides these
under the names and modes of Algol 68 Genie, on top of `java.lang.Math`.
It comes with `a68j-backend.jar` and needs no option; as it needs Java,
it runs with a68j only.

```
access Math (
    puts (fixed (arctan2 (1, -1), 0, 4) + " " + fixed (sinh (1), 0, 4) + " " +
          fixed (gamma (4.5), 0, 4) + "'n");
    compl r = complex_sqrt (-3 I -4);
    puts (fixed (RE r, 0, 4) + " " + fixed (IM r, 0, 4) + "'n")
)
```

```sh
java -jar a68j-backend.jar -run prog.a68
2.3562 1.1752 11.6317
1.0000 -2.0000
```

## What it provides

A name after a slash is another name of Algol 68 Genie for the same
procedure; both are declared.

| Name | Meaning |
|---|---|
| `acos`, `asin`, `atan` | `arccos`, `arcsin` and `arctan` of the prelude |
| `arctan2 / atan2 (real y, x) real` | the angle of the point (x, y) in (-π, π]; the first argument is y, as in C: `arctan2 (1, 0)` is π/2 |
| `sinh`, `cosh`, `tanh` | the hyperbolic functions |
| `arcsinh / asinh`, `arccosh / acosh`, `arctanh / atanh` | their inverses |
| `cbrt (real x) real` | the cube root, negative for a negative x |
| `gamma (real x) real` | the gamma function: (x - 1)! for the positive integers, `gamma (0.5)` = √π |
| `lgamma (real x) real` | ln \|gamma (x)\| |
| `ln_gamma (real x) real` | ln gamma (x), NaN where gamma (x) is negative |
| `complex_exp / cexp`, `complex_ln / cln`, `complex_sqrt / csqrt` | e^z, the principal logarithm (imaginary part in (-π, π]), the principal root (real part not negative) |
| `complex_sin / csin`, `complex_cos / ccos`, `complex_tan / ctan` | the trigonometric functions of a `compl` |
| `complex_sinh / csinh`, `complex_cosh / ccosh`, `complex_tanh / ctanh` | the hyperbolic functions of a `compl` |

The procedures take and yield `real` and `compl`. They compute in double
precision (`java.lang.Math`, or `long real` for the functions of a
`compl`) and round to `real` at the end.

## Differences from Algol 68 Genie

- **Errors:** an argument outside the domain gives NaN, a pole an
  infinity: `arccosh (0.5)` and `gamma (-3)` are NaN, `arctanh (1)`,
  `gamma (0)` and `lgamma (-4)` an infinity, `arctan2 (0, 0)` is 0.
  This is what `arccos (2)` and `ln (0)` of the prelude give in a68j and
  ga68. Algol 68 Genie stops with a runtime error.
- **`real` has single precision** in a68j (as in ga68), with a largest
  value of about 3.4e38. So `gamma (x)` is an infinity above about 35
  and `sinh (x)` above about 89, where Algol 68 Genie, with double
  precision, still gives a value. Results agree with Algol 68 Genie to
  about seven digits.
- **Not provided:**
  - the `long real` and `long long real` forms (`long sinh`, `dsinh`
    and so on);
  - the complex inverse functions (`complex arcsin` and the others);
  - `erf`, `erfc` and their inverses, `beta`, the incomplete gamma
    functions, `fact`, `choose`;
  - the forms in degrees (`sindg` and the others), `sinpi` and the other
    forms in multiples of π;
  - `cot`, `sec`, `csc` and their inverses, `cas`, `ln1p`.

## `-fgenie`

With `-fgenie` the program is put into an access clause of
`Genie_Transput`, `Genie_Formats`, `Regex`, `Files`, `Times` and `Math`
([genie-formats.md](genie-formats.md)), so a program written for Algol
68 Genie can call `arctan2`, `sinh`, `gamma` or `complex exp` as it is.
A declaration of the program with the same name hides the one of the
module: some Rosetta Code solutions declare their own `sinh` or `gamma`.

## Inside

`backend/lib/math.a68` declares the procedures of `real` as formal holes
of `java.lang.Math` (`atan2`, `sinh`, `cosh`, `tanh`, `cbrt`) and of
`a68j.jlib.MathFunctions`, which has what `java.lang.Math` lacks: the
inverse hyperbolic functions, and the gamma function after Lanczos
(g = 7, nine coefficients) with the reflection formula below 1/2. The
functions of a `compl` are written in Algol 68 in the module, with the
usual formulas in `long real`:

- the exponential function is e^x (cos y + i sin y);
- the logarithm is ln hypot (x, y) + i atan2 (y, x);
- the root is computed from the real part √((|x| + |z|) / 2);
- the tangent is (sin 2x + i sinh 2y) / (cos 2x + cosh 2y), and the
  hyperbolic tangent its counterpart. For a large imaginary part (real
  part of `ctanh`) their limit is used, where cosh would overflow.

## Tests

- `tests/java/math-basic.a68`: the module without `-fgenie`. It checks
  the values and the other names, the inverse functions against the
  functions, the identities of the functions of a `compl`, and NaN and
  the infinities.
- `tests/formats/genie/math.a68`: `-fgenie` with every name of Algol 68
  Genie, `null char`, `COMPLEX` and `LONG COMPLEX`, and a declaration that
  hides `sinh`. It is compared with the output of Algol 68 Genie, to four
  decimals.
