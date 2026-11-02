#!/bin/sh
# cut_test.sh NATIVE_SUITE NATIVE_EXTRA NATIVE_ROSETTA [JAR]: a test of the
# serial clauses cut into parts that are methods of their own
# (backend/src/main/java/a68j/jvm/Cut.java). Runs the ga68 test suite,
# tests/extra, the Java and PAR tests and the Rosetta programs with
# A68J_CUT=every, which cuts every serial clause before every phrase where
# it may and makes every serial clause a method of its own, and compares
# them with the results of ga68 in the directories NATIVE_SUITE,
# NATIVE_EXTRA and NATIVE_ROSETTA (as written by tools/run_native.py). The
# results must be the same as without the variable. The ga68 test suite is
# expected in ../../src/gcc/gcc/testsuite/algol68/execute, or in $GA68_SUITE.
set -e
cd "$(dirname "$0")"
JAR=${4:-../backend/a68j-backend.jar}
S=${GA68_SUITE:-../../src/gcc/gcc/testsuite/algol68/execute}
export A68J_CUT=every
python3 run_jvm.py --jar "$JAR" --native "$1" "$S"/*.a68 "$S"/mcgt/*.a68
python3 run_jvm.py --jar "$JAR" --native "$2" ../tests/extra/*.a68
sh java_tests.sh
sh par_tests.sh
python3 run_jvm.py --jar "$JAR" --native "$3" ../tests/rosetta/*.a68
