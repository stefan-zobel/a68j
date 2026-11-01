#!/bin/sh
# PAR/SEMA tests (tests/par): every program is run with -fpar-threads and
# compared with X.out, and without the option and compared with X.ga68
# (the output and messages of ga68, which runs PAR sequentially and rejects
# SEMA). Usage: tools/par_tests.sh [JAVA [REPEAT]]: JAVA defaults to java,
# REPEAT (how often the threaded run is repeated) to 5.
cd "$(dirname "$0")/.."
JAVA=${1:-java}
REPEAT=${2:-5}
JAR=backend/a68j-backend.jar
pass=0; fail=0
tmp=$(mktemp); trap 'rm -f "$tmp"' EXIT
for f in tests/par/*.a68; do
  b=${f%.a68}
  i=0
  while [ $i -lt "$REPEAT" ]; do
    timeout 60 "$JAVA" -cp $JAR a68j.jvm.Compile -fpar-threads -run "$f" > "$tmp" 2>&1; st=$?
    out=$(grep -v '^Picked up' "$tmp"; echo "exit $st")
    if [ "$out" != "$(cat "$b.out")" ]; then
      echo "FAIL $f (-fpar-threads, run $i)"; echo "$out" | head -5; fail=$((fail + 1)); break
    fi
    i=$((i + 1))
  done
  [ $i -eq "$REPEAT" ] && pass=$((pass + 1))
  timeout 60 "$JAVA" -cp $JAR a68j.jvm.Compile -run "$f" > "$tmp" 2>&1; st=$?
  out=$(grep -v '^Picked up\|^compilation terminated' "$tmp"; echo "exit $st")
  if [ "$out" = "$(cat "$b.ga68")" ]; then pass=$((pass + 1)); else echo "FAIL $f (without option)"; echo "$out" | head -5; fail=$((fail + 1)); fi
done
echo "par tests: $pass passed, $fail failed"
[ $fail -eq 0 ]
