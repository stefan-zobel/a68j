#!/bin/sh
# Checks a TextMate grammar against the scanner of a68j: makes a token dump
# (a68j.tools.TokenDump) of every file and compares it with
# editors/a68html/check_grammar.js.
#
# Usage: sh tools/grammar_check.sh [file.a68 ...]
#   without arguments: all programs under tests/ and backend/lib/
# Environment:
#   GRAMMAR    the grammar file (default: the SUPPER grammar of
#              editors/vscode-algol68; the one for UPPER and SUPPER is
#              editors/textmate-algol68/syntaxes/algol68.tmLanguage.json)
#   STROPPING  supper (the default) or upper, for the scanner
#   JOBS       the number of parallel token dumps (default 8)
# Needs: a68j.jar built, node, and once
#   (cd editors/a68html && npm install)
set -e
cd "$(dirname "$0")/.."
python3 tools/gen_tmlanguage.py
python3 tools/gen_tmlanguage_upper.py
if [ -n "$GRAMMAR" ]; then
    A68_GRAMMAR=$(cd "$(dirname "$GRAMMAR")" && pwd)/$(basename "$GRAMMAR")
    export A68_GRAMMAR
fi
STROPPING=${STROPPING:-supper}
DUMPS=$(mktemp -d)
LIST=$DUMPS/files.txt
if [ $# -gt 0 ]; then
    printf '%s\n' "$@" > "$LIST"
else
    ls tests/rosetta/*.a68 tests/extra/*.a68 tests/java/*.a68 tests/par/*.a68 backend/lib/*.a68 > "$LIST"
fi
# In parallel, one JVM per file. File names go as arguments, not through
# the shell (Rosetta names contain '$').
JAVA_TOOL_OPTIONS= xargs -P "${JOBS:-8}" -d '\n' -n 1 sh -c \
    'java -cp a68j.jar a68j.tools.TokenDump -fstropping='"$STROPPING"' "$1" > "$0/$(printf %s "$1" | tr / _).tok" 2>&1 || true' \
    "$DUMPS" < "$LIST"
# The list goes as @file: on Windows the names of all files do not fit on a
# command line. There, node needs a Windows path (cygpath).
L=$LIST
if command -v cygpath > /dev/null 2>&1; then L=$(cygpath -m "$LIST"); fi
node editors/a68html/check_grammar.js "$DUMPS" "@$L"
rm -rf "$DUMPS"
