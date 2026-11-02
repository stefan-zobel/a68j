#!/bin/sh
# cut_test.sh NATIVE_ROSETTA [JAR]: a test of the serial clauses cut into
# parts that are methods of their own
# (backend/src/main/java/a68j/jvm/Cut.java). Runs the ga68 test suite
# (tests/ga68), tests/extra, the Java and PAR tests and the Rosetta programs
# with A68J_CUT=every, which cuts every serial clause before every phrase
# where it may and makes every serial clause a method of its own, and
# compares them with the results of ga68: in tests/ga68/expected and
# tests/extra/expected, and for the Rosetta programs in the directory
# NATIVE_ROSETTA (as written by tools/run_native.py). The results must be
# the same as without the variable. Runs every part, also after failures;
# the exit status is 1 if a part failed.
cd "$(dirname "$0")"
JAR=${2:-../backend/a68j-backend.jar}
S=../tests/ga68/execute
export A68J_CUT=every
st=0
python3 run_jvm.py --jar "$JAR" --native ../tests/ga68/expected "$S"/*.a68 "$S"/mcgt/*.a68 "$S"/modules/*.a68 || st=1
python3 run_jvm.py --jar "$JAR" --native ../tests/extra/expected ../tests/extra/*.a68 || st=1
sh java_tests.sh || st=1
sh par_tests.sh || st=1
python3 run_jvm.py --jar "$JAR" --native "$1" ../tests/rosetta/*.a68 || st=1
exit $st
