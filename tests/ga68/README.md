# The ga68 test suite

`execute` is a copy of the directory `gcc/testsuite/algol68/execute` of
GCC, the run-time tests of ga68, the Algol 68 front end of GCC. Each
program is compiled and run; the tests check its output and exit status.
The subdirectory `mcgt` holds further tests of the same kind, `modules`
programs of several packets with the module packets they use (see
`modules/README`).

- **Origin:** GCC, <https://gcc.gnu.org/git/gcc.git>. `GCC-COMMIT` names
  the commit and its date that the copy was made from.
- **License:** as GCC, the GNU General Public License, version 3 or later
  (see the headers of the `.exp` files and `COPYING` in the root of a68j).
  The files are copied unchanged.
- **Not included:** `gcc/testsuite/algol68/compile`, tests that only
  compile and check the error messages and warnings of GCC, which a68j
  does not use. Nor the MCTS test set, which GCC does not distribute
  either (`README.mcts` in GCC).

`expected` holds the results of ga68 for these programs, one
`<name>.json` per program, as written by `tools/run_native.py --reference`:
the exit status of the compilation (`compile`) and its messages (`cerr`),
the exit status of the program (`exit`; -6 for an abort, as for a runtime
error) and its output (`stdout`, `stderr`). The file names in them have no
directory. The programs that ga68 did not compile or that timed out are
not compared. `tests/extra/expected` holds the results of ga68 for the
programs in `tests/extra` in the same way.

## Running the tests

```
S=tests/ga68/execute
python3 tools/run_jvm.py --native tests/ga68/expected -v $S/*.a68 $S/mcgt/*.a68 $S/modules/*.a68
python3 tools/run_jvm.py --native tests/extra/expected -v tests/extra/*.a68
```

## Updating the copy

```
tools/update_ga68_suite.sh GCC [XGCC LIBGA68]
```

copies `gcc/testsuite/algol68/execute` of the GCC checkout `GCC` again and
writes its commit to `GCC-COMMIT`. With the ga68 driver `XGCC`
(`…/gcc/xgcc` of a GCC build) and the directory of its libga68
(`…/x86_64-pc-linux-gnu/libga68/.libs`), it also records `expected` and
`tests/extra/expected` again. `git diff tests/ga68 tests/extra/expected`
then shows what changed.
