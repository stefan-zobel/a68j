# TODO: ideas for later

Ideas that are not planned yet. The larger topic of formats has a file of
its own: [`TODO-formats.md`](TODO-formats.md).

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

## Module keys of large recursive modes

A module whose public identifiers have large mutually recursive modes
cannot be used from another compilation unit. The compiler stops with an
uncaught `java.lang.IllegalArgumentException: string too long` from
`ClassFile.build`, called from `Gen.compile`.

The cause is `Intrinsics#moduleKey`, which spells out the structure of a
mode down to a depth of 12 (`modeKey`). For a union of many references to
structures that contain the union again, the text grows exponentially with
the depth:

- `Intrinsics#loadModuleTag` and the call site code pass the key as one
  bootstrap argument, which is a single class file constant of at most
  65535 bytes. A longer key ends in the exception above.
- `Gen#emitModuleAccess` splits `pubKeys()` into chunks, so the module
  itself still compiles, but the keys end up in its class file. In one
  module of 278 lines, with a `union` of 21 and one of 13 alternatives of this
  kind, the class file had 26 MB, nearly all of it string constants.

Possible solution: keep the key readable while it is short, and otherwise
replace the description of the mode with a digest of fixed length, such as
the name followed by the SHA-256 of the description. Both sides build the
key with `moduleKey`, so they stay consistent and neither `pubKeys()` nor
`Modules` has to change.

The digest alone would still build the long text at every use. So `modeKey`
should memoize the result for each pair of mode and depth, or the description
of a nested mode should itself be reduced to its digest once it is long. The
key then stays short at every level.

A test needs a module that exports procedures on such modes, and a program
that accesses them. Its class file should stay small.
