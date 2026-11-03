# Algol 68 in Modern Terms

An introduction to Algol 68 (the language of the Revised Report) that
explains the concepts in the terminology programmers use today: nine
chapters, the answers to the exercises, and appendices on the standard
library, the syntax, the implementations, a glossary and modules. See
[outline.md](outline.md) for the plan, the terminology and the decisions,
and [notes.md](notes.md) for the implementation differences found while
the examples were checked.

## Layout

| Path | Content |
|---|---|
| `book.tex`, `preamble.tex` | the main file and the macros (listing style, `\example`, `\term`, `\rr`) |
| `chapters/` | one file per chapter |
| `examples/chNN/`, `examples/appE/` | the example programs; `NAME.out` is the recorded output shown in the book, `NAME.in` its input, `NAME.cfg` how it is run (implementations, options, modules, files; see `tools/run_examples.py`) |
| `examples/answers/` | programs that check the answers to the exercises |
| `html/` | the pandoc filter and the style sheet of the HTML version |
| `tools/run_examples.py` | runs the examples with ga68, a68j and Algol 68 Genie and checks or records their output |
| `tools/supper_to_upper.py` | converts an example to UPPER stropping for Algol 68 Genie |

## Building

```sh
./build.sh          # build/book.pdf and build/html/
./build.sh pdf      # only the PDF (xelatex, latexmk, DejaVu Sans Mono)
./build.sh html     # only the HTML (pandoc 3)
```

## Checking the examples

Every example is run with a68j (`backend/a68j-backend.jar`, built by the
top-level `build.sh`) and, if given, with ga68 and Algol 68 Genie; all
must give the recorded output. Examples of features that ga68 or a68j
lack (formats, `bytes`, semaphores in ga68) are run with the implementations
named in their `.cfg` file only. ga68 programs are linked with the static
libga68 (`libga68.a` in the `--libdir`), as the ga68 driver does by
default; the shared library does not export all operators of the prelude.

```sh
python3 book/tools/run_examples.py --ga68 …/gcc/xgcc --libdir …/libga68/.libs --a68g a68g
python3 book/tools/run_examples.py --record …   # after a deliberate change
```
