# Partial parametrization

Programs that use partial parametrization: a call that leaves arguments
out, `f (3, )`, yields a procedure of the missing parameters, with the
given arguments stored in it (C. H. Lindsey, *Specification of partial
parametrization proposal*, ALGOL Bulletin 39, AB39.3.1, 1976). It is not
part of the Revised Report. Algol 68 Genie implements it, and so does
a68j (`a68j.rt.Partial`, `docs/BACKEND.md`); ga68 accepts it in the front
end and crashes in the back end (`docs/ga68-bug-reports.md`, report 19).

ga68 therefore cannot be the reference. The expected output `NAME.out`
of each program `NAME.a68` was recorded with Algol 68 Genie 3.13.4;
`NAME.in` is the standard input of a program that reads.

```sh
python3 tools/partial_tests.py                    # run with a68j, compare
python3 tools/partial_tests.py --record --a68g A68G   # record the outputs again
```

a68j has the 32-bit `int` and `real` of ga68, Algol 68 Genie a 64-bit
`int` and `real`: numbers are printed in other widths and with fewer
digits. The runner therefore accepts output that differs only in blanks
and in floating-point numbers within the tolerance of `tools/run_jvm.py`
(status `approx`).

## The programs

| Program | Origin |
|---|---|
| `add`, `stages`, `compose`, `power`, `when` | the examples of appendix G of the book (`book/examples/appG`), without the excerpt marks |
| `closures-value-capture`, `currying`, `first-class-functions`, `function-composition-2`, `yin-and-yang` | Rosetta Code solutions, copies of `tests/rosetta` (prepared by `tools/rosetta_prep.py`; GNU Free Documentation License 1.3, see `tests/rosetta/README.md`) |
| `procedures` | `src/test-set/27-procedures.a68` of Algol 68 Genie 3.13.4 (GNU General Public License, version 3 or later), adapted for a68j and ga68 as its header describes |
| `standard-procedures` | written for a68j: procedures of the standard prelude (`sin ()`, `char_in_string ("b", pos, )`), a procedure variable as the primary, every argument left out, and the arguments evaluated once |

The example `scope` of appendix G is not here: Algol 68 Genie stops it with
a run-time error because a stored reference outlives its cell, and a68j
checks the lifetime rule nowhere (as ga68), so it runs to the end. The
second test of the Algol 68 Genie test set that uses partial
parametrization, `40-zeroin.a68`, uses the same operator as `procedures`
together with transput of Algol 68 Genie (`puts`, `gets` on strings) and
operators of mixed modes that ga68 does not have, and is not included.
