# The bundled module `Regex`

Algol 68 Genie has three procedures for regular expressions:
`grep in string`, `grep in substring` and `sub in string`. The module
`Regex` provides them under the same names, on top of
`java.util.regex`. It comes with `a68j-backend.jar` and needs no option;
as it needs Java, it runs with a68j only (`Texts`, docs/texts.md, is the
portable part).

```
access Regex (
    int first, last;
    if grep_in_string ("[0-9]+", "abc 123 def", first, last) = 0
    then puts (whole (first, 0) + " " + whole (last, 0) + "'n")
    fi;
    string s := "2026-11-03";
    sub_in_string ("-", "/", s);
    puts (s + "'n")
)
```

```sh
java -jar a68j-backend.jar -run prog.a68
5 7
2026/11-03
```

## What it provides

The procedures of Algol 68 Genie, with the same modes and results
(measured with Algol 68 Genie 3.1.2):

| Name | Meaning |
|---|---|
| `grep_in_string (pattern, s, ref int first, last) int` | 0 if `pattern` matches in `s`, with the indices of the first and the last character of the match in `first` and `last`; 1 if not, with `first` and `last` unchanged; 2 if the pattern is invalid |
| `grep_in_substring (pattern, s, ref int first, last) int` | the same, but `^` does not match at the start of `s` (it still matches after a newline; `$` matches at the end of `s`) |
| `sub_in_string (pattern, replacement, ref string s) int` | replaces the first match in `s` by `replacement`, taken literally (no `\1`, `$0` or `&`); 0, 1 or 2 as above |

- The indices are those of `s`: in `"xyz"[@3]` the `y` is at 4.
- An empty match gives `last = first - 1`: `x*` in `abc` gives 1 and 0,
  `$` gives 4 and 3.
- `first` and `last` may be `nil`.
- After a replacement `s` has the lower bound 1, as in Algol 68 Genie.
- In UPPER stropping the names have blanks (`grep in string`); in SUPPER
  stropping the blanks are underscores. `tools/a68supper.py` converts
  them.

## The patterns

Algol 68 Genie passes the pattern to the POSIX `regcomp` with
`REG_EXTENDED` and `REG_NEWLINE`: POSIX extended regular expressions, in
which `^` and `$` also match after and before a newline, and `.` and a
non-matching list such as `[^a]` do not match a newline. The module
translates a pattern before Java compiles it
(`a68j.jlib.PosixRegex.translate`), so that these forms mean the same as
in Algol 68 Genie:

| POSIX | Java |
|---|---|
| `^` | `(?<![^\n])`: Java's `^` does not match at the end of the input, so not in an empty string (`^$` on `""`) |
| `[:alpha:]` and the other eleven classes in a bracket expression | `\p{Alpha}` ...; Java does not know `[:alpha:]` and does not reject it either: `[[:alpha:]]+` would find `a` in `ab:c`, not `ab` |
| `[=a=]`, `[.a.]` (one character) | `a`; with more than one character the pattern is invalid (2) |
| `\`, `[`, `&`, a leading `]` in a bracket expression | escaped: in POSIX they are ordinary characters there (`[\d]` is a backslash or `d`) |
| `[^...]` | `[^...\n]` (`REG_NEWLINE`) |
| `\<`, `\>` (GNU) | `\b(?=\w)`, `\b(?<=\w)` |
| `{,n}` (GNU) | `{0,n}` |
| `\1` ... `\9` without such a group | invalid (2); Java would accept it and never match |

The flags `MULTILINE` and `UNIX_LINES` give the rest of `REG_NEWLINE`
(`$` before a newline, `.` not matching one, and only `\n` is a line
terminator).

## Differences from Algol 68 Genie

Measured with `tests/java/regex-a68g.a68`, 74 patterns that were also run
with Algol 68 Genie 3.1.2:

- **Which match.** POSIX takes the leftmost longest match; Java takes the
  leftmost match of the first alternative that fits. `a|ab` in `xabc`
  gives 2 to 3 in Algol 68 Genie and 2 to 2 here. Put the longer
  alternative first (`ab|a`) where it matters.
- **Java syntax.** Patterns that only Java accepts work instead of
  matching literally or being invalid: `\d` is a digit (Algol 68 Genie
  reads it as `d`), and so are lazy quantifiers (`a*?`), possessive ones
  (`a*+`) and look-arounds. For that reason a quantifier directly after a
  quantifier means something else: `a**` is valid in Algol 68 Genie and
  invalid here (2).
- **Back-references.** `(a)(b)\2` finds `abb` here; Algol 68 Genie finds
  no match (back-references are not part of POSIX extended regular
  expressions, glibc offers them as an extension). `(a)\1` matches in both.
- **Characters beyond ASCII.** Algol 68 Genie counts the bytes of UTF-8,
  a68j counts characters: in a string denotation of `x` and an e with an
  acute accent (two bytes in UTF-8), Algol 68 Genie finds the accented
  letter at 2 to 3; ga68 and a68j read the denotation as two characters,
  and a68j finds it at 2 to 2.
  Characters beyond U+FFFF (two UTF-16 units in Java) are one character
  too (`tests/java/regex-unicode.a68`).
- **Classes** are ASCII, as in the C locale (`\p{Alpha}` without
  `UNICODE_CHARACTER_CLASS`).

## Rosetta Code

Ten of the Algol 68 Genie solutions on Rosetta Code (`tests/rosetta/orig`)
use these procedures. With `access Genie_Transput, Regex` one of them,
`strip-comments-from-a-string`, runs with a68j and prints the same as
Algol 68 Genie 3.1.2. The others need more: HTTP (`http content`, `tcp
request`: `http`, `web-scraping`, `base64-encode-data`,
`rosetta-code-rank-languages-by-popularity-2`), formats
(`regular-expressions-1`), the file system (`get directory`: the two
`walk-a-directory` solutions, java-open-points.md B2), or library files
that Rosetta Code does not include (`abc-words-1`,
`rosetta-code-rank-languages-by-popularity-1`). The Rosetta Code tests
compare a68j with ga68, which has no `Regex`, so `tools/rosetta_prep.py`
does not add the module.

## Inside

`backend/lib/regex.a68` declares one formal hole,
`a68j.jlib.PosixRegex.grep`, which yields the first match as
`(start << 32) | end` in characters (or -1, -2); the module turns that
into the indices of `s`. `sub_in_string` builds the new string in
Algol 68 from the match, so the replacement is literal without
`Matcher.quoteReplacement`. The compiled patterns are kept in a small
cache (the 64 used last), so a pattern in a loop is compiled once.
`grep_in_substring` runs the matcher on a region after one extra character
U+0000 with transparent bounds: `^`, a look-behind after the translation,
sees a character that is not a newline there, and `\b` one that is not a
letter, so `\<a` still matches at the start of `s`, as in Algol 68 Genie.

## Tests

- `tests/java/regex-a68g.a68`: the 74 patterns, with the output of a68j;
  the lines where Algol 68 Genie differs are listed in the program.
- `tests/java/regex-unicode.a68`: characters beyond U+FFFF, a negative
  lower bound, `nil` positions, `^` after a newline in
  `grep_in_substring`.
