# Porting rules: ga68 C front-end → Java (a68j)

These rules make independently ported files fit together without manual
glue. Follow them literally.

Read first, as examples of the style: `Scanner.java` (port of
a68-parser-scanner.cc, verified identical to ga68), `Parser.java` (port of the
helpers in a68-parser.cc), `Keywords.java`, `Diagnostics.java`, `Fmt.java`,
`Ref.java`, and the type classes (`Node`, `NodeInfo`, `Moid`, `Pack`, `Tag`,
`Table`, `Soid`, `Moif`, `Extract`, `Job`, `A68`, `Options`, `Modes`, ...).

## 1. Fidelity

- Port **1:1**. Same functions, same order, same control flow, same comments
  (translated to Java comments). Do not refactor, simplify or "fix" anything.
- Quirks or apparent bugs of the original are preserved and marked with a
  comment starting with `Sic:`.
- Keep the exact text of every diagnostic format string.
- GCC-specific code (GENERIC `tree`s, lowering, `CTYPE`, `TAX_TREE_DECL`,
  `LOWERER`, linemaps, GTY) is omitted; leave a short comment
  `// GCC-specific, omitted: ...` where something was dropped.

## 2. Classes and names

One Java class per C file, `public final class` with a private constructor and
**static methods only** (no mutable static fields):

| C file | Java class |
|---|---|
| a68-parser.cc | `Parser` (exists) |
| a68-parser-keywords.cc | `Keywords` (exists; see §5) |
| a68-parser-scanner.cc | `Scanner` (exists) |
| a68-diagnostics.cc | `Diagnostics` (exists, instance `a68.diagnostics`) |
| a68-pretty-print.h | `Fmt` (exists) |
| a68-parser-pragmat.cc | `Pragmat` |
| a68-parser-brackets.cc | `Brackets` |
| a68-parser-top-down.cc | `TopDown` |
| a68-parser-bottom-up.cc | `BottomUp` |
| a68-parser-extract.cc | `Extractor` |
| a68-parser-victal.cc | `Victal` |
| a68-parser-taxes.cc | `Taxes` |
| a68-parser-modes.cc | `ModeBuilder` |
| a68-parser-prelude.cc | `Prelude` |
| a68-postulates.cc | `Postulates` |
| a68-moids-to-string.cc | `MoidsToString` (a stub exists; replace it) |
| a68-parser-moids-equivalence.cc | `MoidsEquivalence` |
| a68-moids-sorting.cc | `MoidsSorting` |
| a68-parser-moids-check.cc | `MoidsCheck` |
| a68-parser-moids-coerce.cc | `MoidsCoerce` |
| a68-moids-misc.cc | `MoidsMisc` |
| a68-moids-diagnostics.cc | `MoidsDiagnostics` |
| a68-parser-scope.cc | `Scope` |
| a68-parser-sprops.cc | `Sprops` |
| a68-parser-serial-dsa.cc | `SerialDsa` |
| a68-imports.cc (decoding) + ELF section reading | `Imports` |

Method names: drop the `a68_` prefix and convert to lowerCamelCase.
`a68_extract_indicants` → `Extractor.extractIndicants`; static
`skip_pack_declarer` → private `skipPackDeclarer`. C overloads stay overloads.

**Every** ported function, public or private, takes `A68 a68` as its first
parameter (even if unused). Then the C parameters in the same order.

## 3. Types

| C | Java |
|---|---|
| `NODE_T *` `MOID_T *` `TAG_T *` `TABLE_T *` `PACK_T *` `LINE_T *` `SOID_T *` `MOIF_T *` `EXTRACT_T *` `POSTULATE_T *` `KEYWORD_T *` `TOKEN_T *` | `Node` `Moid` `Tag` `Table` `Pack` `Line` `Soid` `Moif` `Extract` `Postulate` `Keyword` `Token` |
| `MODULE_T *` | `Job` |
| `const char *` / `char *` holding a string | `String` |
| `bool` | `boolean` |
| `int`, `unsigned`, `size_t` | `int` (`long` for 64-bit values like `uint64_t`) |
| any `int` or `enum` that holds an **attribute** value (IDENTIFIER, OP_SYMBOL, STRONG, ...) — e.g. the `a` of `a68_add_tag`, `att`, `sort`, `expect` | `Attribute` |
| `T **` out/in-out parameter | `Ref<T>` (`Ref.box(x)` for locals, `Ref.of(getter, setter)` for fields, e.g. `&TOP_MOID (&A68_JOB)` → `Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v)`) |
| `int *` / `bool *` out-parameter | `int[]` / `boolean[]` of length 1 |
| variadic list terminated by `STOP` or `NO_MOID` | Java varargs, terminator omitted |
| `char *` data buffer + `size_t` size | `byte[]` |

Keep out-parameters as out-parameters (do not turn them into return values).
Drop `LOWERER_T` parameters entirely.

## 4. Macros and globals

Field macros map to the fields of the Java type classes (camelCase of the C
member): `NEXT (p)` → `p.next`, `SUB (p)` → `p.sub`, `ATTRIBUTE (p)` →
`p.attribute`, `MOID (p)` → `p.type`, `INFO (p)` → `p.info`, `NSYMBOL (p)` →
`p.info.symbol`, `TABLE (p)` → `p.symbolTable`, `TAX (p)` → `p.tag`,
`PRIO (INFO (p))` → `p.info.priority`, `PRIO (tag)` → `tag.priority`,
`PRIO (table)` → `table.priority` (the priority tag list), `LEX_LEVEL (p)` →
`p.symbolTable.level`, `TAG_TABLE (t)` → `t.symbolTable`, `PACK (m)` →
`m.pack`, `DIM (m)` → `m.dim`, `EQUIVALENT (m)` → `m.equivalentMode`,
`DEFLEXED (m)` → `m.deflexedMode`, `DEFLEX (m)` → `m.deflex()`, ...
`IS (p, X)` → `p.is(X)`, `ISNT (p, X)` → `!p.is(X)`. `FORWARD (p)` →
`p = p.next`. `NEXT_SUB (p)` → `p.sub.next`, `SUB_NEXT (p)` → `p.next.sub` etc.
If a Java type lacks a field the C code needs, report it (§8); do not edit
shared files.

Globals: `A68 (x)` → `a68.x` (camelCase), `A68_JOB` → `a68.job`,
`A68_PARSER (x)` → `a68.parserState.x`, `A68_STANDENV` → `a68.standenv`,
`TOP_NODE (&A68_JOB)` → `a68.job.topNode`, `TOP_MOID (&A68_JOB)` →
`a68.job.topMoid`, `OPTION_STROPPING (&A68_JOB)` →
`a68.options().stropping` (`Stropping.UPPER/SUPPER`), `OPTION_STRICT` →
`a68.options().strict`, `OPTION_BRACKETS` → `a68.options().brackets`,
`flag_building_libga68` → `a68.options().buildingLibga68`,
`warn_algol68_hidden_declarations` → `a68.options().hiddenDeclarations`.
Modes: `M_INT` / `MODE (INT)` → `a68.modes.INT`, `M_ROW_CHAR` →
`a68.modes.ROW_CHAR`. `MOID_T **` to a mode slot, e.g. `&M_INT` →
`Ref.of(() -> a68.modes.INT, v -> a68.modes.INT = v)`.

File-static variables become per-compilation state:
`a68.state(MyClass.State.class, MyClass.State::new)` with a nested
`static final class State { ... }`.

`#define` constants local to a C file become `static final` constants; local
function-like macros become private static methods.

## 5. Shared helpers (already ported)

- `Parser.newNode/newNodeInfo/newGenieInfo/someNode/newSymbolTable/newMoid/newPack/newTag(a68, ...)`,
  `Parser.makeSpecialMode(a68, m)`, `Parser.makeSub(a68, p, q, t)`,
  `Parser.whether(a68, p, A, B, Parser.not(C), ...)` (C `-C` → `Parser.not(C)`),
  `Parser.isOneOf(a68, p, A, B, ...)`, `Parser.newString(...)`,
  `Parser.isNewLexicalLevel`, `Parser.isUnitTerminator`, etc. — see `Parser.java`.
- `Parser.gccAssert(cond)`, `throw Parser.gccUnreachable()`.
- `Parser.PRIMAL_SCOPE`, `Parser.MOID_ERROR_WIDTH`.
- Keywords keep their existing signatures: `Keywords.findKeyword(a68, text)`
  for `a68_find_keyword (A68 (top_keyword), text)`,
  `Keywords.findKeywordFromAttribute(a68.topKeyword, att)`,
  `Keywords.addToken(a68, text)` for `a68_add_token (&A68 (top_token), text)`
  (returns a `Token`; `TEXT (...)` → `.text`), `Keywords.stropKeyword(a68, s)`.
- C string helpers: `CType.isUpper/isLower/isAlpha/isDigit/isSpace/isPrint`,
  `CType.toLower/toUpper`, `CType.strcmp`, `CType.strcasecmp`.
  `strcmp (a, b) == 0` → `a.equals(b)`.

## 6. Diagnostics

- `a68_error (p, fmt, ...)` → `a68.diagnostics.error(p, fmt, args...)` with
  the **identical** C format string (`%s %qs %d %u %c %e %< %>` supported).
- `a68_warning (p, OPT_Wfoo, fmt, ...)` → `a68.diagnostics.warning(p, Warn.FOO, fmt, ...)`;
  option `0` → `null`. Returns boolean like C. Add missing `Warn` values to your
  report (§8) and use `Warn.OTHER` meanwhile.
- `a68_inform`, `a68_fatal`, `a68_error_in_pragmat` → `inform`, `fatal`, `errorInPragmat`.
- `%e` arguments: `a68_attr_format_token a (X); ... &a` → pass `Fmt.attr(X)`;
  `a68_construct_format_token` → `Fmt.construct(x)`; `a68_symbol_format_token` →
  `Fmt.symbol(p)`; `a68_moid_format_token` → `Fmt.moid(a68, m)`;
  `a68_opmoid_format_token` → `Fmt.opmoid(a68, m)`; `a68_sort_format_token` →
  `Fmt.sort(s)`; `a68_line_format_token` → `Fmt.line(l, n)`.

## 7. Control flow

- `setjmp`/`longjmp` → a nested `static final class CrashExit extends RuntimeException`
  (with `serialVersionUID`), thrown at the longjmp, caught at the setjmp.
- `goto` → restructure minimally (labelled blocks/loops), comment `// C: goto label`.
- `switch` with fall-through → keep the fall-through semantics exactly.

## 8. Working area and report

> **Historical note.** This section describes the workflow of the parallel
> porting phase. The `work/` and `stubs/` directories were removed after all
> ported classes had been integrated into `src/main/java`; the section is kept
> only as a record of how the port was organized.

Do **not** modify files in `src/main/java` (others work in parallel). Write
your classes to `work/<yourname>/a68j/front/`. If you need classes owned by
someone else to compile, write minimal stubs with the signatures derived from
these rules to `stubs/<yourname>/a68j/front/` (never into `src`). Compile with:

```sh
cd /home/me/a68j
javac --release 21 -Xlint:all -d /tmp/out-<yourname> \
  $(find src/main/java work/<yourname> stubs/<yourname> -name '*.java')
```

Your code must compile cleanly this way. Finish with
`work/<yourname>/REPORT.md` listing: files ported (C lines → Java lines),
every external method you call that is not yet in `src` (exact Java
signature), fields you needed but were missing in shared types, new `Warn`
values, and anything you were unsure about.

## 9. Stage 2 addendum

Stage 1 is integrated: `src/main/java/a68j/front` now contains the complete
ports of the scanner, parser helpers, pragmat, brackets, top-down, bottom-up,
victal, extract, taxes, prelude, postulates, parser-modes, moids-to-string,
moids-equivalence, moids-sorting, moids-misc and imports. Read those classes
for the exact signatures before you call them; do not stub them.
Conventions established there:

- Deflexing constants: `MoidsMisc.NO_DEFLEXING` ... `SKIP_DEFLEXING` (int).
- `Taxes.addTag(..., int p)`: where C passes an attribute as priority, pass
  `X.ordinal()` (equals the C enum value).
- C default arguments become overloads.
- `MoidsToString.moidToString(A68, Moid, int width, Node)` for diagnostics.
- `src/.../MoidsDiagnostics.java` is a placeholder; the porter of
  a68-moids-diagnostics.cc replaces it (compile excluding the src copy).
- Subagents cannot write REPORT.md: return the report as your final reply.
