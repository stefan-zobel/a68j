#!/bin/sh
# Tests of the graph library tests/rosetta/graphs/graphs.incl.a68 and of the
# Rosetta Code solutions that use it (tests/rosetta/graphs/README.md). The
# programs are written for Algol 68 Genie; every program X.a68 is run with
# a68j -fgenie, and its output and exit status are compared with X.out,
# recorded with Algol 68 Genie 3.13.4.
# Usage: tools/graphs_tests.sh [JAVA]
#        tools/graphs_tests.sh --record [A68G]   (records X.out with A68G)
cd "$(dirname "$0")/.."
DIR=tests/rosetta/graphs
if [ "$1" = "--record" ]; then
  A68G=${2:-a68g}
  for f in $DIR/*.a68; do
    case $f in *.incl.a68) continue;; esac
    b=${f%.a68}
    (cd $DIR && "$A68G" "$(basename "$f")"; echo "exit $?") > "$b.out" 2>&1
    echo "recorded $b.out"
  done
  exit 0
fi
JAVA=${1:-java}
JAR=backend/a68j-backend.jar
pass=0; fail=0
tmp=$(mktemp); trap 'rm -f "$tmp"' EXIT
for f in $DIR/*.a68; do
  case $f in *.incl.a68) continue;; esac
  b=${f%.a68}
  timeout 120 "$JAVA" -cp $JAR a68j.jvm.Compile -fgenie -run "$f" > "$tmp" 2>&1; st=$?
  out=$(grep -v '^Picked up' "$tmp"; echo "exit $st")
  if [ "$out" = "$(cat "$b.out")" ]; then pass=$((pass + 1)); else echo "FAIL $f"; echo "$out" | head -5; fail=$((fail + 1)); fi
done
echo "graphs tests: $pass passed, $fail failed"
[ $fail -eq 0 ]
