# TODO: ideas for later

Ideas that are not planned yet. The larger topics have files of their own:
[`TODO-formats.md`](TODO-formats.md) and
[`TODO-method-size.md`](TODO-method-size.md).

## Compact element access in hot code too

Today the compact element access of a row (`Gen#compactAccess`: a call of
`Row#index1`, `Rt.elemI` and the like instead of the inline code) is used
only in code that runs once and in methods that are too large for the JIT
([`optimizations.md`](optimizations.md), sections 9 and 10); hot code keeps
the inline access. If C2 inlines the calls, the compact access could be as
fast in hot code as well, and the code would be smaller everywhere.

To find out: measure the performance tests and the Rosetta programs with
`A68J_COMPACT_ACCESS=every` against the default, alternately
(`tools/perf_ab.py`), and compare the machine code of C2 for a few hot
loops. Only if there is no measurable loss would the default change.
