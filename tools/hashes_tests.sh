#!/bin/sh
# Tests of tests/rosetta/hashes/hashes.incl.a68 (tests/rosetta/hashes/README.md).
# The programs are written for Algol 68 Genie and read bits.incl.a68 of
# Rosetta Code, which is not in the repository: --fetch copies it from
# Rosetta Code into tests/rosetta/hashes (ignored by git). Every program
# X.a68 is run with a68j -fgenie, and its output and exit status are
# compared with X.out, recorded with Algol 68 Genie 3.13.4.
# Usage: tools/hashes_tests.sh [JAVA]
#        tools/hashes_tests.sh --record [A68G]   (records X.out with A68G)
#        tools/hashes_tests.sh --fetch           (fetches bits.incl.a68)
cd "$(dirname "$0")/.."
DIR=tests/rosetta/hashes
BITS=$DIR/bits.incl.a68
if [ "$1" = "--fetch" ]; then
  curl -sSfL "https://rosettacode.org/wiki/Category:ALGOL_68-bits?action=raw" |
    sed -n '/<syntaxhighlight lang=algol68>/,/<\/syntaxhighlight>/p' | sed '1d;$d' > "$BITS.tmp" &&
    [ -s "$BITS.tmp" ] && mv "$BITS.tmp" "$BITS" && echo "fetched $BITS" && exit 0
  rm -f "$BITS.tmp"; echo "cannot fetch bits.incl.a68"; exit 1
fi
[ -f "$BITS" ] || { echo "$BITS is missing: run tools/hashes_tests.sh --fetch"; exit 1; }
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
echo "hashes tests: $pass passed, $fail failed"
[ $fail -eq 0 ]
