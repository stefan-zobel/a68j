#!/bin/sh
# Tests of the Java connection (tests/java): nest Java (-fnest-java) and the
# bundled modules Lists and Hashmaps. The modules tests/java/modules/X.a68
# are compiled first, into tests/java/lib/libX.jar. Every program X.a68 is
# compiled and run, and its output and exit status are compared with X.out.
# Options come from a first line "{ a68j-options: ... }", environment
# variables for the run from a second line "{ a68j-env: NAME=VALUE ... }".
# With an a68j-env line, the frames of the JDK and of a68j itself are left
# out of a Java stack trace in the output ("\tat java.base/...",
# "\tat a68j...", "\t... N more"), as their line numbers change with the
# JDK and with a68j; the frames of the program and of its Java classes stay.
# Usage: tools/java_tests.sh [JAVA].
cd "$(dirname "$0")/.."
JAVA=${1:-java}
JAR=backend/a68j-backend.jar
pass=0; fail=0
tmp=$(mktemp); trap 'rm -f "$tmp"; rm -rf tests/java/classes tests/java/lib' EXIT
# The Java classes of the user for the tests (nest-java-classpath.a68).
JAVAC=$(dirname "$JAVA")/javac; [ -x "$JAVAC" ] || JAVAC=javac
"$JAVAC" -d tests/java/classes tests/java/demo/*.java || exit 1
# The modules of the tests (nest-java-module*.a68).
mkdir -p tests/java/lib
for m in tests/java/modules/*.a68; do
  n=$(basename "$m" .a68)
  "$JAVA" -cp $JAR a68j.jvm.Compile -fnest-java -fjava-classpath=tests/java/classes "$m" \
    -d "tests/java/lib/lib$n.jar" 2>&1 | grep -v '^Picked up'
  [ -f "tests/java/lib/lib$n.jar" ] || { echo "cannot compile $m"; exit 1; }
done
# The keys of the large recursive modes of largemodes.a68 must stay short: the
# size of the class, not of the jar, whose compression would hide them.
"$JAVA" -cp $JAR a68j.jvm.Compile tests/java/modules/largemodes.a68 \
  -d tests/java/classes/largemodes 2>&1 | grep -v '^Picked up'
size=$(wc -c < tests/java/classes/largemodes/Largemodes.class) || exit 1
[ "$size" -le 65536 ] || { echo "Largemodes.class has $size bytes"; exit 1; }
for f in tests/java/*.a68; do
  b=${f%.a68}
  opts=$(sed -n '1s/^{ a68j-options: \(.*\) }$/\1/p' "$f")
  env=$(sed -n '2s/^{ a68j-env: \(.*\) }$/\1/p' "$f")
  env $env timeout 60 "$JAVA" -cp $JAR a68j.jvm.Compile $opts -run "$f" > "$tmp" 2>&1; st=$?
  if [ -n "$env" ]; then
    out=$(grep -v -e '^Picked up' -e '^	at java\.base/' -e '^	at a68j\.' -e '^	\.\.\. [0-9]* more$' "$tmp"; echo "exit $st")
  else
    out=$(grep -v '^Picked up' "$tmp"; echo "exit $st")
  fi
  if [ "$out" = "$(cat "$b.out")" ]; then pass=$((pass + 1)); else echo "FAIL $f"; echo "$out" | head -5; fail=$((fail + 1)); fi
done
echo "java tests: $pass passed, $fail failed"
[ $fail -eq 0 ]
