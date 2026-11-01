# Syntax highlighting: VS Code grammar, HTML and SVG

This chapter describes the TextMate grammar for Algol 68 in GNU SUPPER
stropping, the VS Code extension around it, and the tool `a68html`, which
colors source text as HTML with the same grammar.

| File | Contents |
|---|---|
| `tools/gen_tmlanguage.py` | generates the grammar; change it **here** |
| `editors/vscode-algol68/syntaxes/algol68.tmLanguage.json` | the generated grammar |
| `editors/vscode-algol68/` | the VS Code extension (also as a `.vsix`) |
| `editors/vscode-algol68/examples/example.a68` | an example program with almost all language features |
| `editors/a68html/a68html.js` | source text → HTML (or SVG) |
| `editors/a68html/a68tokens.js` | the shared tokenizer (vscode-textmate + Oniguruma) |
| `editors/a68html/check_grammar.js` | compares grammar ↔ scanner |
| `tools/grammar_check.sh` | the comparison over all test programs |

## 1. Origin

The starting point was
[jlguenego/vscode-algol68](https://github.com/jlguenego/vscode-algol68)
(ISC license, 2021). Its grammar is very small: UPPER stropping, a good
twenty keywords (some twice), `#`, `CO` and `COMMENT` as comments, no
numbers, operators, pragmats, modules or bold words. None of it fitted
SUPPER, so the grammar is written anew. What remains of the original are
the structure and the name of the extension; the license names both
authors.

## 2. The rules of SUPPER as the scanner applies them

The grammar follows the scanner of ga68 (`a68-parser-scanner.cc`,
`a68-parser-keywords.cc`, here `front/Scanner.java` and
`front/Keywords.java`). What of it matters for highlighting I checked
with ga68:

- **Reserved words** are in lower case: `begin`, `int`, `if`, `andth`,
  `pub`, `nest` and so on; the list comes directly from
  `Keywords.setUpTables`. A word next to an underscore is **never**
  reserved: `do_1` and `out_` are identifiers. In the grammar `\b` takes
  care of that, because `_` is a word character.
- **Bold words** (mode and operator indicants) begin with a capital letter
  and may contain lower and upper case letters, digits and single
  underscores: `Node`, `Int_list`, `ABS`, `HAS`.
- **Underscores** mean nothing in identifiers: `max_int` and `maxint` are
  the same name. The names of the prelude are therefore matched with an
  optional `_` (`max_?int`), and the constants and mathematical functions
  may be preceded by `long_`, `short_` or `word_` (`long_long_pi`).
- **Comments** exist in SUPPER only as `{ … }`. They can be nested, and a
  `"…"` inside is skipped: `{ "}" }` is *one* comment. `co`, `comment`,
  `#` and `note` are no comments in SUPPER (ga68 reports a syntax error for
  `# x #`).
- **Pragmats** exist only as `[[ … ]]`, e.g. `[[ include "file.a68" ]]`.
  `pr` and `pragmat` do not exist in SUPPER.
- **Strings**: `"…"`, with `""` for a quote and the GNU escapes
  `'n 'f 'r 't ''` and `'(u00e4, U0001F600)`. Any other character after
  `'` is an error and is marked as one.
- **Numbers**: `42`, `1.5`, `.5`, `1e3`, `1.e3`, `1.5e-3`; bits
  denotations `2r…`, `4r…`, `8r…`, `10r…` (GNU) and `16r…`. **Spaces are
  allowed in denotations**: `1 000`, `1 234 567 . 9 e - 4` and
  `16r 444444` are *one* number each, and a long number may even continue
  over the end of the line. Only between the digits after `16r` SUPPER
  allows no spaces, otherwise `16r0 do` would be the literal `16r0d`
  followed by `o`.
- **Format texts** `$…$` are split by the scanner by rules of their own,
  but ga68 does not compile them. The grammar colors them as a region of
  their own, so that the `d` in `$3d$` does not appear as an identifier.
- **Characters that are not allowed** outside strings and comments (`×`,
  `¬`, `≠`, `\`, `` ` ``) are rejected by the scanner ("unworthy
  character"); the grammar marks them as errors, and so wrong digits
  (`2r03`, `16rfg`) and words like `foo_Bar`, which SUPPER splits into two
  tokens.

## 3. Mode or operator?

Bold words are either modes or operators, and the language does not tell
the two apart by their spelling. Only the parser knows which is meant. A
TextMate grammar always sees just one line and no declarations, so:

1. After `mode` comes a mode, after `op` an operator, after `module` and
   `access` a module name. This is always right.
2. Otherwise the grammar follows the usual style: **capitals only** (two
   characters or more, digits and `_` allowed) is an operator (`ABS`,
   `HAS`, `TOSTRING`), **everything else** a mode (`Node`, `Int_list`,
   `F`).

The 1131 Rosetta programs are written that way too: 471 mode
declarations, only 2 of them in capitals; no operator with lower case
letters. Colored wrongly are only an operator of a single letter (except
`I`), which appears as a mode, and a mode in capitals, which appears as an
operator. Operators of the prelude (`ABS`, `UPB`, `ODD`, `MOD` …) have a
class of their own (`support.function.operator`).

## 4. More highlighting from the context

- `proc name = …` and `name = (int n) int: …` (a routine text): the name
  as a function definition.
- `op NAME = …`, `op + = …`, `op +:= = …`: the name as an operator
  definition.
- `access [pub] A, [pub] B` and `module X = …`: module names.
- `nest C "…"` and `nest Java "…"` (formal holes): the language.
- Labels at the start of a line or after `;` (`done:`) and after
  `goto`/`go to`.
- Identifiers of the standard prelude (`puts`, `whole`, `fixed`, `sqrt`,
  `max_int`, `stdout` …) from `Prelude.java`, `standard.a68` and
  `posix.a68`.

One regex needed particular care: the recognition of a routine text looks
across declarers like `ref [] Int_list` up to the `:`. Without atomic
groups it ran into exponential backtracking on `SHORTEN SHORTEN SHORTEN …`;
Oniguruma then gives up, and the rest of the line stayed uncolored. With
`(?>…)` and possessive quantifiers (`*+`) it is linear.

## 5. Checked against the scanner

Because a68j follows the scanner of ga68 1:1 and `a68j.tools.TokenDump`
can print every token with its line, column and kind, the grammar can be
checked automatically. `check_grammar.js` demands agreement in both
directions:

1. Every scanner token starts, in the grammar, at a place with a fitting
   class: reserved word → `keyword`/`storage`/`constant.language`;
   identifier → never a keyword; bold word → mode, operator or module;
   denotation → `constant.numeric`; string → begins with `"`; symbol → not
   in a comment, string or pragmat.
2. Every visible grammar token outside comments and pragmats lies inside
   a scanner token. Otherwise the grammar would take a comment for code.

Result:

| Corpus | Files | Scanner tokens | Differences |
|---|---|---|---|
| Rosetta, extra tests, Java and PAR tests, `backend/lib` | 1157 | 356 440 | 0 |
| ga68 test suite (SUPPER) | 223 | 22 167 | 0 |

Skipped are files that the scanner already rejects (Rosetta solutions
with `×` or `¬`) and the deliberately erroneous programs of the ga68 test
suite (`dg-error`). The scanner counts columns in bytes (UTF-8), like
ga68; the checker converts them.

Usage (needs a built `a68j.jar`, Node.js and, once, `npm install` in
`editors/a68html`):

    sh tools/grammar_check.sh                     all test programs, about 1.5 minutes
    sh tools/grammar_check.sh prog.a68            single files

## 6. HTML with `a68html`

    cd editors/a68html && npm install
    node a68html.js --lines -o example.html ../vscode-algol68/examples/example.a68

Options and embedding: see `editors/a68html/README.md`. By default the
color scheme follows the operating system (light/dark); `--theme light`
or `--theme dark` fixes it.

**SVG** (added 1 Nov 2026): `node a68html.js --lines -o prog.svg prog.a68`
renders the program as an SVG image, for Markdown files on GitHub, which
has no highlighting for Algol 68 and strips styles from HTML. Every line is
a `<text>` element whose `textLength` is fixed, so the columns line up
whatever monospace font the viewer has. All 1124 programs in
`tests/rosetta` render to well-formed SVG (checked with `xmllint`) in 4.7 s.
Details, and how to follow the GitHub theme with `<picture>`:
`editors/a68html/README.md`.

## 7. Changing the grammar

1. Change `tools/gen_tmlanguage.py` (word lists at the top, rules below).
2. `python3 tools/gen_tmlanguage.py` writes the JSON file anew; on the way
   all regexes are compiled once, which shows unbalanced parentheses at
   once.
3. `sh tools/grammar_check.sh` must report 0 differences.
4. For VS Code: copy the folder `editors/vscode-algol68` to
   `%USERPROFILE%\.vscode\extensions\algol68-supper`, or package it anew
   with `npx @vscode/vsce package --allow-missing-repository` (raise the
   version in `package.json` first).
