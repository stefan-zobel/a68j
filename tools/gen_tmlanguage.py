#!/usr/bin/env python3
"""Generates the TextMate grammar for Algol 68 in GNU SUPPER stropping.

Output: editors/vscode-algol68/syntaxes/algol68.tmLanguage.json

It started from https://github.com/jlguenego/vscode-algol68 (ISC license,
Jean-Louis Guenego, 2021). That grammar knows only UPPER stropping and a
good twenty keywords; this one is written anew and follows the rules of
the ga68 scanner (a68-parser-scanner.cc, here
src/main/java/a68j/front/Scanner.java and Keywords.java):

* Reserved words are in lower case (begin, if, int, ...). A word next to
  an underscore is never a reserved word (do_1, out_ are identifiers).
  \\b does that, since _ is a word character.
* Bold words (mode and operator indicants) begin with a capital letter:
  Int_list, Node, ABS, HAS. The language does not tell the two kinds
  apart lexically; the grammar follows the usual style: capitals only
  (two characters or more) = operator, otherwise mode. After
  mode/op/module/access the context decides.
* Comments: only { ... }, nestable; a "..." in a comment is skipped, so a
  } inside it does not end the comment. co, comment, # and note do not
  exist in SUPPER.
* Pragmats: only [[ ... ]] (e.g. [[ include "file.a68" ]]).
* Strings: "...", "" is a quote, GNU escapes 'n 'f 'r 't '' and
  '(u0041, U0001F600).
* Bits denotations 2r, 4r, 8r, 10r, 16r; real numbers 1.5, .5, 1e3, 1.e3.
* Format texts $...$: the ga68 scanner splits them (format letters,
  numbers, strings), but ga68 does not compile them. The grammar colors
  them as a region of their own, so that e.g. the d in $3d$ is no
  identifier.

Usage: python3 tools/gen_tmlanguage.py
"""

import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "editors", "vscode-algol68", "syntaxes", "algol68.tmLanguage.json")

S = ".algol68"  # suffix of all scope names

# --- Word lists ------------------------------------------------------------

# All reserved words of ga68 in SUPPER (Keywords.setUpTables without the
# entries that hold only in UPPER), grouped by role.
CONTROL = ["begin", "end", "if", "then", "elif", "else", "fi",
           "case", "in", "ouse", "out", "esac",
           "for", "from", "by", "to", "while", "do", "od",
           "goto", "go", "exit", "par", "assert"]
MODULE = ["module", "def", "postlude", "fed", "egg"]
DECL = ["mode", "op", "prio", "proc"]
MODIFIER = ["ref", "flex", "heap", "loc", "long", "short", "struct", "union"]
TYPES = ["int", "real", "bool", "char", "string", "void", "bits", "bytes",
         "compl", "sema", "file", "channel"]
CONSTANTS = ["true", "false", "nil", "skip", "empty"]
WORD_OPS = ["andth", "orel", "is", "isnt", "of", "at"]
# Without a group of their own: access, pub, nest (rules of their own) and word.
ALL_KEYWORDS = CONTROL + MODULE + DECL + MODIFIER + TYPES + CONSTANTS + WORD_OPS + \
    ["access", "pub", "nest", "word"]

# Operators of the standard prelude with a bold name (Prelude.java, standard.a68).
STD_OPS = ["ABS", "AND", "ARG", "BIN", "CLEAR", "CONJ", "DIVAB", "DOWN", "ELEM",
           "ELEMS", "ENTIER", "EQ", "FLOOR", "GE", "GT", "I", "IM", "LE", "LENG",
           "LEVEL", "LT", "LWB", "MINUSAB", "MOD", "MODAB", "NE", "NOT", "ODD", "OR",
           "OVER", "OVERAB", "PLUSAB", "PLUSTO", "RE", "REPR", "ROUND", "SET", "SHL",
           "SHORTEN", "SHR", "SIGN", "TEST", "TIMESAB", "UP", "UPB", "XOR"]

# Identifiers of the standard prelude. Underscores mean nothing in SUPPER
# (max_int = maxint); every _ here becomes optional in the regex.
# With an optional prefix long_/short_/word_ (also repeated: long_long_pi):
STD_CONST_L = ["pi", "max_int", "min_int", "max_real", "min_real", "small_real",
               "max_bits", "bits_width", "bytes_width", "int_width", "real_width",
               "exp_width", "infinity", "minus_infinity"]
STD_PROC_L = ["sqrt", "exp", "ln", "log", "sin", "cos", "tan", "arcsin", "arccos",
              "arctan", "random", "next_random", "bits_pack", "bytes_pack",
              "string_to_real"]
STD_CONST = ["blank", "flip", "flop", "null_character", "eof_char", "error_char",
             "replacement_char", "max_abs_char",
             "int_lengths", "int_shorths", "real_lengths", "real_shorths",
             "bits_lengths", "bits_shorths", "bytes_lengths", "bytes_shorths",
             "compl_lengths", "compl_shorths",
             # posix.a68
             "stdin", "stdout", "stderr", "file_o_default", "file_o_rdwr",
             "file_o_rdonly", "file_o_wronly", "file_o_trunc",
             "seek_cur", "seek_end", "seek_set"]
STD_PROC = ["print", "put", "get", "read", "whole", "fixed", "float",
            "char_in_string", "first_random", "rnd",
            # posix.a68
            "argc", "argv", "errno", "strerror", "fopen", "fcreate", "fconnect",
            "fclose", "fsize", "lseek", "getchar", "putchar", "fgetc", "fputc",
            "gets", "puts", "fgets", "fputs", "posix_exit", "perror", "getenv"]

BOLD = r"[A-Z][A-Za-z0-9_]*"          # bold word (mode/operator/module)
TAG = r"[a-z][a-z0-9_]*"              # identifier
OPCHARS = r"[-+*/%^&~<>=!?]"          # characters of symbolic operators


def words(ws):
    return r"\b(?:" + "|".join(sorted(ws, key=lambda w: (-len(w), w))) + r")\b"


def uscore(name):
    """max_int -> max_?int"""
    return name.replace("_", "_?")


def std_names(ws, prefixed):
    alts = "|".join(uscore(w) for w in sorted(ws, key=lambda w: (-len(w), w)))
    pre = r"(?:(?:long|short|word)_?)*" if prefixed else ""
    return r"\b" + pre + "(?:" + alts + r")\b"


# --- Grammar ---------------------------------------------------------------

NOT_KEYWORD = "(?!" + words(ALL_KEYWORDS) + ")"

repo = {}

repo["comment"] = {
    "comment": "{ ... } is the only comment in SUPPER; nestable, "
               "a \"...\" inside it is skipped (Scanner.pragment).",
    "name": "comment.block.algol68",
    "begin": r"\{",
    "end": r"\}",
    "beginCaptures": {"0": {"name": "punctuation.definition.comment.begin" + S}},
    "endCaptures": {"0": {"name": "punctuation.definition.comment.end" + S}},
    "patterns": [
        {"include": "#comment"},
        {"comment": "a string in a comment: a } inside it does not count",
         "begin": "\"", "end": "\"", "name": "comment.block.string" + S},
        {"match": r"\b(?:TODO|FIXME|XXX)\b", "name": "keyword.other.todo" + S},
    ],
}

repo["pragmat"] = {
    "comment": "[[ ... ]] is the only pragmat in SUPPER (GNU).",
    "name": "meta.preprocessor.pragmat.algol68",
    "begin": r"\[\[",
    "end": r"\]\]",
    "beginCaptures": {"0": {"name": "punctuation.definition.pragmat.begin" + S}},
    "endCaptures": {"0": {"name": "punctuation.definition.pragmat.end" + S}},
    "patterns": [
        {"match": r"\b(?:include|access|in)\b", "name": "keyword.control.directive" + S},
        {"begin": "\"", "end": "\"", "name": "string.quoted.double" + S},
        {"begin": "'", "end": "'", "name": "string.quoted.single" + S},
    ],
}

repo["string"] = {
    "name": "string.quoted.double.algol68",
    "begin": "\"",
    "end": "\"(?!\")",
    "beginCaptures": {"0": {"name": "punctuation.definition.string.begin" + S}},
    "endCaptures": {"0": {"name": "punctuation.definition.string.end" + S}},
    "patterns": [
        {"match": "\"\"", "name": "constant.character.escape.quote" + S},
        {"match": r"'[nfrt']", "name": "constant.character.escape" + S},
        {"comment": "characters by their code: '(u00e4, U0001F600)",
         "match": r"'\(\s*[uU][0-9A-Fa-f]+(?:\s*,\s*[uU][0-9A-Fa-f]+)*\s*\)",
         "name": "constant.character.escape.unicode" + S},
        {"match": "'.?", "name": "invalid.illegal.escape" + S},
    ],
}

repo["format"] = {
    "comment": "format text $...$ (split by ga68, but not compiled)",
    "name": "meta.format.algol68",
    "begin": r"\$",
    "end": r"\$",
    "beginCaptures": {"0": {"name": "punctuation.definition.format.begin" + S}},
    "endCaptures": {"0": {"name": "punctuation.definition.format.end" + S}},
    "patterns": [
        {"include": "#comment"},
        {"include": "#string"},
        {"match": r"\d+", "name": "constant.numeric.format" + S},
        {"match": r"[A-Za-z]", "name": "constant.other.format-item" + S},
        {"match": r"[(),]", "name": "punctuation.format" + S},
    ],
}

# Spaces and tabs may stand in denotations ("1 000", "1 .5", "2 e - 4",
# "16r 44"); the scanner skips them (nextChar with allow_typo).
_T = r"[ \t]*"
_D = r"\d(?:" + _T + r"\d)*"
_E = _T + r"[eE]" + _T + r"[+-]?" + _T + _D


def _radix(base, digit):
    # After 16r ga68 allows no spaces between the digits in SUPPER
    # (otherwise "16r0 do" would be the literal 16r0d and an o).
    inner = digit + "+" if base == "16" else digit + "(?:" + _T + digit + ")*"
    return base + _T + "r" + _T + inner


repo["number"] = {
    "patterns": [
        {"comment": "bits denotation: radix 2, 4, 8, 10 (GNU) or 16",
         "match": r"\b(?:" + "|".join([_radix("2", "[01]"), _radix("4", "[0-3]"), _radix("8", "[0-7]"),
                                       _radix("10", "[0-9]"), _radix("16", "[0-9a-f]")]) +
                  r")(?![0-9A-Za-z_])",
         "name": "constant.numeric.bits" + S},
        {"comment": "a wrong digit for the radix (2r03, 16rfg) or a wrong radix",
         "match": r"\b\d+" + _T + "r" + _T + r"[0-9A-Za-z_]+", "name": "invalid.illegal.bits" + S},
        {"comment": "1.5, 1 .5, .5, 1e3, 1.e3, 1.5e-3 (also with spaces)",
         "match": r"(?<![\w.])(?:" +
                  _D + _T + r"\." + _T + _D + "(?:" + _E + ")?" + "|" +
                  _D + "(?:" + _T + r"\.)?" + _E + "|" +
                  r"\." + _T + _D + "(?:" + _E + ")?" + ")",
         "name": "constant.numeric.real" + S},
        {"match": r"\b" + _D + r"\b", "name": "constant.numeric.integer" + S},
    ]
}

repo["module"] = {
    "patterns": [
        {"comment": "module Name = ...",
         "match": r"\b(module)\s+(" + BOLD + r")\b",
         "captures": {"1": {"name": "keyword.other.module" + S},
                      "2": {"name": "entity.name.namespace" + S}}},
        {"comment": "access [pub] A, [pub] B",
         "match": r"\b(access)\s+((?:pub\s+)?" + BOLD + r"(?:\s*,\s*(?:pub\s+)?" + BOLD + r")*)",
         "captures": {"1": {"name": "keyword.control.import" + S},
                      "2": {"patterns": [
                          {"match": r"\bpub\b", "name": "storage.modifier.pub" + S},
                          {"match": BOLD, "name": "entity.name.namespace" + S},
                          {"match": ",", "name": "punctuation.separator.comma" + S}]}}},
        {"match": r"\baccess\b", "name": "keyword.control.import" + S},
        {"match": r"\bpub\b", "name": "storage.modifier.pub" + S},
        {"comment": "formal hole: nest C \"name\", nest Java \"Class.method\"",
         "match": r"\b(nest)\s+(" + BOLD + r")\b",
         "captures": {"1": {"name": "keyword.other.nest" + S},
                      "2": {"name": "support.constant.formal-hole.language" + S}}},
        {"match": r"\bnest\b", "name": "keyword.other.nest" + S},
    ]
}

# Atomic groups and possessive quantifiers (*+): without them the regex runs
# into exponential backtracking on "SHORTEN SHORTEN SHORTEN ...", Oniguruma
# gives up and the rest of the line stays uncolored.
ROUTINE_RESULT = (r"(?>\s*\([^()]*+\))?(?>\s*+(?:(?:" + "|".join(TYPES + MODIFIER + ["proc"]) +
                  r")\b|\[[^\]]*+\]|[A-Z][A-Za-z0-9_]*+))*+\s*:(?!=)")

repo["declaration"] = {
    "patterns": [
        {"comment": "mode Name = ...",
         "match": r"\b(mode)\s+(" + BOLD + r")\b",
         "captures": {"1": {"name": "storage.type.mode" + S},
                      "2": {"name": "entity.name.type" + S}}},
        {"comment": "op NAME = ..., op + = ..., op +:= = ...",
         "match": r"\b(op)\s+(" + BOLD + "|" + OPCHARS + r"+(?::=|=:)?)(?=\s*:?=)",
         "captures": {"1": {"name": "storage.type.op" + S},
                      "2": {"name": "entity.name.function.operator" + S}}},
        {"comment": "proc name = ... and proc name := ...",
         "match": r"\b(proc)\s+(" + NOT_KEYWORD + TAG + r")(?=\s*:?=)",
         "captures": {"1": {"name": "storage.type.proc" + S},
                      "2": {"name": "entity.name.function" + S}}},
        {"comment": "name = (int a) int: ... (a routine text, e.g. after proc (int) int)",
         "match": r"\b(" + NOT_KEYWORD + TAG + r")(?=\s*=(?!=)\s*" + ROUTINE_RESULT + ")",
         "captures": {"1": {"name": "entity.name.function" + S}}},
    ]
}

repo["label"] = {
    "patterns": [
        {"comment": "a label at the start of a line or after ';': stop: skip",
         "match": r"(?:^|(?<=;))\s*(" + NOT_KEYWORD + TAG + r")\s*(:)(?![=/])",
         "captures": {"1": {"name": "entity.name.label" + S},
                      "2": {"name": "punctuation.separator.label" + S}}},
        {"match": r"\b(goto|go\s+to)\s+(" + NOT_KEYWORD + TAG + r")\b",
         "captures": {"1": {"name": "keyword.control.goto" + S},
                      "2": {"name": "entity.name.label" + S}}},
    ]
}

repo["keyword"] = {
    "patterns": [
        {"match": words(CONTROL), "name": "keyword.control" + S},
        {"match": words(MODULE), "name": "keyword.other.module" + S},
        {"match": words(DECL), "name": "storage.type.declaration" + S},
        {"match": words(TYPES), "name": "storage.type.primitive" + S},
        {"match": words(MODIFIER + ["word"]), "name": "storage.modifier" + S},
        {"match": words(CONSTANTS), "name": "constant.language" + S},
        {"match": words(WORD_OPS), "name": "keyword.operator.wordlike" + S},
    ]
}

repo["standard"] = {
    "patterns": [
        {"match": std_names(STD_CONST_L, True), "name": "support.constant" + S},
        {"match": std_names(STD_PROC_L, True), "name": "support.function" + S},
        {"match": std_names(STD_CONST, False), "name": "support.constant" + S},
        {"match": std_names(STD_PROC, False), "name": "support.function" + S},
    ]
}

repo["bold"] = {
    "patterns": [
        {"match": words(STD_OPS), "name": "support.function.operator" + S},
        {"comment": "capitals, digits and _ only (at least two characters): operator",
         "match": r"\b[A-Z][A-Z0-9_]*[A-Z0-9]\b", "name": "entity.name.function.operator" + S},
        {"comment": "otherwise: mode indicant (Int_list, Node, F)",
         "match": r"\b" + BOLD + r"\b", "name": "entity.name.type" + S},
    ]
}

repo["mixed"] = {
    "comment": "an identifier that runs into a bold word (foo_Bar, fooBar): in "
               "SUPPER two tokens and almost always a mistake",
    "match": r"\b[a-z][a-z0-9_]*[A-Z][A-Za-z0-9_]*\b",
    "name": "invalid.illegal.mixed-tag" + S,
}

repo["identifier"] = {
    "comment": "Ordinary identifiers stay without color, but are consumed as a whole, "
               "so that no part of them counts as a number or the like.",
    "match": r"\b" + TAG + r"\b",
    "name": "variable.other" + S,
}

repo["operator"] = {
    "patterns": [
        {"match": r":/=:|:=:", "name": "keyword.operator.identity" + S},
        {"comment": "+:=, -:=, *:=, /:=, %:=, %*:= and +=: (PLUSTO)",
         "match": OPCHARS + r"+(?::=|=:)", "name": "keyword.operator.assignment.compound" + S},
        {"match": r":=", "name": "keyword.operator.assignment" + S},
        {"match": r"=:", "name": "keyword.operator.assignment" + S},
        {"match": OPCHARS + "+", "name": "keyword.operator" + S},
        {"match": r"@", "name": "keyword.operator.at" + S},
    ]
}

repo["punctuation"] = {
    "patterns": [
        {"comment": "characters that ga68 rejects outside strings and comments "
                    "(unworthy character), e.g. ×, ¬, ≠, \\ and `",
         "match": r"[^\x00-\x7F]|[\\`]", "name": "invalid.illegal.unworthy" + S},
        {"match": r"\|:|\|", "name": "keyword.control.brief" + S},
        {"match": ";", "name": "punctuation.separator.semicolon" + S},
        {"match": ",", "name": "punctuation.separator.comma" + S},
        {"match": ":", "name": "punctuation.separator.colon" + S},
        {"match": r"[()]", "name": "punctuation.parenthesis" + S},
        {"match": r"[\[\]]", "name": "punctuation.brackets" + S},
        {"match": r"\.", "name": "punctuation.accessor" + S},
    ]
}

ORDER = ["comment", "pragmat", "string", "format", "number", "module", "mixed", "declaration", "label",
         "keyword", "standard", "bold", "identifier", "operator", "punctuation"]

grammar = {
    "$schema": "https://raw.githubusercontent.com/martinring/tmlanguage/master/tmlanguage.json",
    "name": "Algol 68 (SUPPER)",
    "scopeName": "source.algol68",
    "fileTypes": ["a68"],
    "comment": "Generated by a68j/tools/gen_tmlanguage.py - do not edit by hand. "
               "GNU Algol 68 (ga68) in SUPPER stropping.",
    "patterns": [{"include": "#" + n} for n in ORDER],
    "repository": repo,
}

# Compile all regexes once with Python; that catches unbalanced parentheses
# (Oniguruma is compatible in the constructs used here).
def check(node):
    if isinstance(node, dict):
        for k, v in node.items():
            if k in ("match", "begin", "end") and isinstance(v, str):
                re.compile(v)
            check(v)
    elif isinstance(node, list):
        for v in node:
            check(v)


check(grammar)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(grammar, f, indent=2, ensure_ascii=False)
    f.write("\n")
print("written:", os.path.relpath(OUT, ROOT))
