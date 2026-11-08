#!/bin/sh
# table_test.sh NATIVE_ROSETTA [JAR]: a test of the row displays of
# constants made tables (backend/src/main/java/a68j/jvm/Tables.java). Runs
# the ga68 test suite (tests/ga68), tests/extra, the Java and PAR tests and
# the Rosetta programs with A68J_TABLE_MIN=1, which makes a table of every
# display that can be one (with at least one constant element) instead of
# only those with at least Tables.MIN constants, and compares them with the
# results of ga68: in tests/ga68/expected and tests/extra/expected, and for
# the Rosetta programs in the directory NATIVE_ROSETTA (as written by
# tools/run_native.py). The results must be the same as without the
# variable. Then checks that a display of two dimensions whose rows differ
# in length is still the run-time error of Row#ofRows, which ga68 does not
# report (it is no table). Runs every part, also after failures; the exit
# status is 1 if a part failed.
cd "$(dirname "$0")"
JAR=${2:-../backend/a68j-backend.jar}
JAR=$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")
S=../tests/ga68/execute
export A68J_TABLE_MIN=1
st=0
python3 run_jvm.py --jar "$JAR" --native ../tests/ga68/expected "$S"/*.a68 "$S"/mcgt/*.a68 "$S"/modules/*.a68 || st=1
python3 run_jvm.py --jar "$JAR" --native ../tests/extra/expected ../tests/extra/*.a68 || st=1
sh java_tests.sh || st=1
sh par_tests.sh || st=1
python3 run_jvm.py --jar "$JAR" --native "$1" ../tests/rosetta/*.a68 || st=1
d=$(mktemp -d)
cat > "$d/m.a68" <<'EOF'
begin
   [,] int m = ((1, 2, 3), (4, 5));
   puts ("not reached " + whole (m[1, 1], 0) + "'n")
end
EOF
want="m.a68:2: runtime error: multiple bounds mismatch in row display"
got=$(cd "$d" && env -u JAVA_TOOL_OPTIONS java -jar "$JAR" m.a68 2>&1 \
      && env -u JAVA_TOOL_OPTIONS java -cp "$JAR:." M 2>&1)
if [ "$got" = "$want" ]; then
  echo "ragged display: pass"
else
  echo "ragged display: FAIL: $got"
  st=1
fi
rm -rf "$d"
exit $st
