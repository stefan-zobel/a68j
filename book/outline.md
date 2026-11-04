# Algol 68 in Modern Terms: outline

Working outline for an introduction to Algol 68 at the level and scope of a
two-semester course (a first course: chapters 1 to 5; a second course:
chapters 6 to 9). The book explains the language of the Revised Report in
the terminology programmers use today. The terms of the Report are given
once, where a concept is introduced, so that a reader can still read the
Report and the older literature; after that the modern term is used.

The chapter order follows the classic order of Algol 68 textbooks. The
text and all examples are new.

## Conventions

- Programs are written in SUPPER stropping, the default of ga68:
  reserved words in lower case (`begin`, `int`, `ref`), names of types
  that the program declares capitalized (`Node`), operators in upper case
  (`ABS`, `UPB`, a user-defined `MAX`). In the typeset text reserved words
  are printed in bold, as in the Algol tradition.
- Every example is a complete program or a fragment of one that is
  compiled and run with ga68 and with a68j when the book is built; the
  output shown is the output of that run. A fragment that cannot be run
  with either (formats, for example) is marked as such and is checked with
  Algol 68 Genie instead.
- Input and output use the bundled module `Genie_Transput` (`print`,
  `read`, `put`, `get`, files), since the standard prelude of ga68 has no
  transput of the Revised Report. Chapter 1 shows the one line that every
  program needs for it.
- Each chapter ends with exercises (answers at the end of the book) and
  programming problems (no answers).
- Where the implementations differ (the size of `int` and `real`, missing
  features), a short note says so; Appendix C collects these notes.

## Chapters

1. **Introduction**
   - What a program is; compiled languages; where Algol 68 comes from and
     what it influenced (C, Pascal, ...).
   - A first program; compiling and running it with ga68 and a68j.
   - How keywords are marked (stropping), and the SUPPER convention.

2. **Values, types and variables**
   - The primitive types `int`, `real`, `bool`, `char`; literals.
   - Constants: binding a name to a value (`int n = 10`).
   - Variables: a variable is a name bound to a *reference* to a memory
     cell (`int x := 0` is short for `ref int x = loc int := 0`).
   - Arithmetic, precedence, parentheses; comparison and logical operators.
   - The first implicit conversions: reading a variable (auto-dereference)
     and `int` to `real`.
   - Assignment and compound assignment (`+:=`); assignment is an
     expression.
   - Explicit conversions (`ROUND`, `ENTIER`, `ABS`, `REPR`).
   - Comments and layout; simple input and output.

3. **Expressions and control flow**
   - Everything is an expression: blocks, `if` and `case` yield values.
   - Statement sequences; discarding a value.
   - Blocks and lexical scope; declarations and uses; shadowing.
   - Unordered evaluation (comma-separated lists whose order is
     unspecified).
   - Loops: the one general loop (`for ... from ... by ... to ... while
     ... do ... od`) and its special cases.
   - `if` and `case` expressions; the common type of the branches.
   - Labels and `goto`; why they are rarely needed.
   - Structure of a whole program.

4. **Arrays and records**
   - Arrays with arbitrary lower bounds; multi-dimensional arrays;
     indexing, slicing, bounds (`LWB`, `UPB`); array literals.
   - Records (`struct`); field access (`x of p`); record literals.
   - Arrays of records, records of arrays; field access through arrays.
   - Type declarations (`mode`); type aliases and structural equivalence.
   - A single value where an array is expected.

5. **Functions and operators**
   - Function types; function literals (lambdas); declaring functions.
   - Parameter passing: everything is passed by value; passing a
     reference to get the effect of an in-out parameter.
   - Recursion.
   - Calling a parameterless function without parentheses.
   - Defining and overloading operators; declaring precedence; how an
     operator is resolved.
   - Programming with functions and operators.

6. **More built-in types and the standard library**
   - Complex numbers (`compl`).
   - Bit sets (`bits`) and byte strings (`bytes`).
   - Precision variants (`long`, `short`).
   - Implementation limits (`max_int`, `small_real`, ...); mathematical
     functions; extending the standard operators.

7. **Advanced types**
   - Resizable arrays (`flex`) and `string`; references into a resizable
     array.
   - Tagged unions and pattern matching on the type (`case ... in ...
     esac`).
   - Orthogonality: any type can be combined with any other; references
     to references (pointers); explicit type annotations (casts);
     reference equality (`is`, `isnt`).
   - All implicit conversions, and where each is allowed: the five
     conversion contexts.
   - Lifetimes: stack and heap allocation; the rule that a reference may
     not outlive what it refers to; lifetimes of functions.
   - Recursive types (lists, trees); which recursive types are allowed;
     when two types are the same.
   - Programming examples: resizable arrays, linked structures, unions.

8. **Concurrency**
   - The problems: mutual exclusion and message passing.
   - Concurrent execution (`par`) and semaphores (`sema`, `UP`, `DOWN`).
   - Producer and consumer; the dining philosophers.
   - (Real threads need a68j with `-fpar-threads`; ga68 executes `par`
     sequentially and has no semaphores.)

9. **Input and output**
   - Files, channels and file handles.
   - Unformatted I/O: `put`, `get`, `print`, `read`; how structured
     values are flattened for I/O; layout procedures (`newline`, ...).
   - Reading from and writing to strings.
   - Querying and changing files; error and end-of-file callbacks.
   - Formatted I/O (format texts) - see the open question below.
   - Binary I/O.

Appendices:

- A. The standard library, by topic.
- B. The syntax in a modern notation (EBNF), instead of a syntax chart.
- C. Implementation notes: ga68, a68j, Algol 68 Genie.
- D. Glossary: the terms of the Report and their modern equivalents (the
  table below, extended).
- E. Modules (a GNU extension): `module`, `pub`, `access`.
- F. Calling C (a GNU extension): formal holes `nest C`, the mapping of
  types, strings and functions as parameters.
- G. Partial parametrization (Lindsey's proposal, AB 39.3.1; Algol 68
  Genie and a68j).
- Answers to the exercises; index.

## Terminology

The table maps the terms of the Revised Report to the terms the book uses.
"Report" means the Revised Report on the Algorithmic Language Algol 68.

| Report term | Term in the book | Remarks |
|---|---|---|
| mode | type | |
| plain mode | primitive type | `int`, `real`, `bool`, `char` |
| denotation | literal | `42`, `3.5`, `"a"`, `true` |
| mode indicant | type name | `int`, or `Node` after `mode Node = ...` |
| identifier, tag | name, identifier | |
| identity declaration | constant declaration, binding | `int n = 10`; like `let` / `final` |
| variable declaration | variable declaration | shorthand, see *name* |
| name | reference, memory location | the value of a variable *is* a reference |
| `ref int` | reference to `int` | a mutable cell; compare `int&`, `&mut i32` |
| generator (`loc`, `heap`) | allocation (stack, heap) | `heap int` is like `new int` |
| object, value | value | |
| assignation | assignment | an expression with a result |
| unit | expression | |
| formula | operator expression | |
| monadic / dyadic operator | unary / binary operator | |
| priority | precedence | 1 to 9 for binary operators |
| coercion | implicit conversion | |
| dereferencing | auto-dereference (reading a variable) | |
| deproceduring | implicit call of a parameterless function | |
| widening | widening | `int` to `real` to `compl`; `bits` to `[] bool`, `bytes` to `[] char` |
| rowing | wrapping a value in a one-element array | |
| uniting | injection into a union | |
| voiding | discarding a value | |
| syntactic position, sort | conversion context | strong, firm, meek, weak, soft: how many implicit conversions are allowed |
| a priori / a posteriori mode | type before / after conversion | |
| balancing | common type of the branches | as for `c ? a : b` |
| serial clause | statement sequence | its value is the value of the last expression |
| closed clause | block | `begin ... end` or `( ... )` |
| collateral clause | unordered list | evaluation order unspecified |
| collateral elaboration | unordered evaluation | |
| parallel clause | concurrent block | `par (...)` |
| conditional clause | `if` expression | |
| case clause | `case` expression on an integer | like `switch` |
| conformity clause | type switch, pattern match on a union | |
| loop clause | loop | |
| range | scope (lexical block) | |
| reach | a block without the blocks nested in it | |
| defining / applied occurrence | declaration / use | |
| elaboration | evaluation, execution | |
| multiple value, row | array | |
| row display | array literal | |
| bounds, descriptor | bounds, array header | |
| slice, trimmer | indexing, slice (range) | `a[2:5]` |
| flexible name (`flex`) | resizable array variable | |
| deflexing | removing `flex` from a type | the type of the value of a resizable array |
| transient name | reference into a resizable array | may not be stored, since resizing invalidates it |
| structure | record, struct | |
| field selector, selection | field name, field access | `x of p` is `p.x` |
| structure display | record literal | |
| mode declaration | type declaration, type alias | structural, not nominal |
| equivalent modes | the same type | structural equivalence |
| well-formed mode | admissible recursive type | every cycle must pass through `ref` or `proc`, and through `struct` or a `proc` with parameters |
| united mode | union, sum type | tagged; the tag is checked at run time |
| procedure | function | |
| routine text | function literal, lambda | |
| formal / actual parameter | parameter / argument | |
| `proc` mode | function type | |
| operator declaration | operator definition, overloading | |
| priority declaration | precedence declaration | |
| identification of operators | overload resolution | by the operand types |
| `nil` | null reference | |
| `skip` | an unspecified value of any type | |
| `void`, `empty` | void, unit type | |
| scope (of a value) | lifetime | |
| scope rule | lifetime rule | a reference may not outlive its target |
| cast | type annotation | allows implicit conversions only; does not convert `real` to `int` |
| identity relation (`is`, `:=:`) | reference equality | |
| actual / formal / virtual declarer | type with bounds (variables, type declarations) / without bounds (parameters, constants) / after `ref` | |
| environment enquiry | implementation limit | `max_int`, `max_real`, `int_width` |
| standard prelude, standard environment | standard library | |
| transput | input and output, I/O | |
| book | file (on disk) | |
| channel | device, kind of file | |
| file | file handle, stream | |
| straightening | flattening (for I/O) | |
| event routine | callback, error handler | `on_logical_file_end` |
| format text | format string | |
| pragmat | compiler directive | |
| stropping | keyword marking | |
| jump, label | `goto`, label | |
| hip (`skip`, `nil`, jump) | expressions that fit any expected type | `nil` only reference types |
| `sema` | semaphore | |

## Decisions

- The subject is Algol 68 as defined by the Revised Report and taught in
  the late 1970s. Comparisons with current languages are welcome; modern
  additions are side remarks, not the subject.
- GNU extensions of ga68 (`andth`, `orel`, modules, ...) may be mentioned
  where useful. Extensions of a68j (Java interoperation, real threads) get
  no more than a short note.
- Differences between ga68, a68j and Algol 68 Genie are mentioned briefly
  where they help the reader, for example to understand Rosetta Code
  programs, or where they concern sizes (`int`, `real`). Differences found
  while testing are recorded in `notes.md`; the book discusses one openly
  only if an implementation contradicts the Revised Report.
- Sources: McGettrick, "ALGOL 68: A First and Second Course" (1978), is
  the model for scope, order and many exercises and problems (named in the
  preface). The book was checked against Lindsey and van der Meulen,
  "Informal Introduction to ALGOL 68" (1977) for errors and gaps; no text
  is taken from either. The Revised Report decides every question.
- Title page: version from `../VERSION`, date of the build (both written
  by `build.sh` to `build/version.tex`), license CC0 1.0.
- Formatted transput and `bytes` are described as part of the language;
  their examples are checked with Algol 68 Genie (`run: a68g`).
- `par` and `sema`: examples checked with Algol 68 Genie and with a68j
  (`-fpar-threads`); the text notes that ga68 elaborates `par` like a
  collateral clause and has no semaphores yet.
- Scope: the level and coverage of McGettrick's course, without its
  material for first-time programmers and without the Report's
  terminology for its own sake. Where a rule has consequences that
  programmers run into, the book shows them, in "Pitfall" boxes where
  they are traps that the compilers do not report. Every chapter ends
  with exercises (answers at the end of the book) and programming
  problems (no answers).
- Formatted transput is limited to what Algol 68 Genie supports; the
  chapter lists what Genie lacks.
