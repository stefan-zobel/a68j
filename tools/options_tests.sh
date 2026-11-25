#!/bin/sh
# Tests of the command-line options and the messages of the compiler:
# tests/options (the -W options of ga68, -fassert/-fno-assert, -fgenie with
# its widening between lengths, -fformats, an include path with a ~, the
# error for an unknown option and the message of an internal compiler
# error) and tests/scope (the warning -Wframe-scope).
# Every program X.a68 is compiled and run with the options of its first
# line "{ a68j-options: ... }" and the environment variables of a second
# line "{ a68j-env: NAME=VALUE ... }" if there is one, and the messages of
# the compiler, the output of the program and the exit status are compared
# with X.out.
# Usage: tools/options_tests.sh [JAVA].
cd "$(dirname "$0")/.."
JAVA=${1:-java}
JAR=backend/a68j-backend.jar
pass=0; fail=0
tab=$(printf '\t')
tmp=$(mktemp); trap 'rm -f "$tmp"' EXIT
for f in tests/options/*.a68 tests/scope/*.a68; do
  b=${f%.a68}
  opts=$(sed -n '1s/^{ a68j-options: \(.*\) }$/\1/p' "$f")
  vars=$(sed -n '2s/^{ a68j-env: \(.*\) }$/\1/p' "$f")
  env $vars timeout 60 "$JAVA" -cp $JAR a68j.jvm.Compile $opts -run "$f" > "$tmp" 2>&1; st=$?
  # An internal compiler error: the frames of the Java stack trace, the line
  # numbers of the innermost frame and the versions are left out.
  out=$(grep -v '^Picked up' "$tmp" | grep -v "^$tab\(at \|\.\.\. \)" \
        | sed -e 's/^\(  in .*\.java\):[0-9][0-9]*$/\1/' -e 's/^a68j [^ ]*, Java .*$/a68j VERSION, Java VERSION/'; echo "exit $st")
  if [ "$out" = "$(cat "$b.out")" ]; then pass=$((pass + 1)); else echo "FAIL $f"; echo "$out" | head -5; fail=$((fail + 1)); fi
done
echo "options tests: $pass passed, $fail failed"
[ $fail -eq 0 ]
