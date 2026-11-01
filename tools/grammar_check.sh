#!/bin/sh
# Checks the TextMate grammar (editors/vscode-algol68) against the scanner of
# a68j: makes a token dump (a68j.tools.TokenDump) of every file and compares
# it with editors/a68html/check_grammar.js.
#
# Usage: sh tools/grammar_check.sh [file.a68 ...]
#   without arguments: all programs under tests/ and backend/lib/
# Needs: a68j.jar built, node, and once
#   (cd editors/a68html && npm install)
set -e
cd "$(dirname "$0")/.."
python3 tools/gen_tmlanguage.py
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
    'java -cp a68j.jar a68j.tools.TokenDump -fstropping=supper "$1" > "$0/$(printf %s "$1" | tr / _).tok" 2>&1 || true' \
    "$DUMPS" < "$LIST"
node editors/a68html/check_grammar.js "$DUMPS" $(cat "$LIST")
rm -rf "$DUMPS"
