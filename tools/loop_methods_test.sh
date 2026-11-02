#!/bin/sh
# loop_methods_test.sh NATIVE_ROSETTA [JAR]: a test of loop
# methods (backend/src/main/java/a68j/jvm/Outline.java). Runs tests/extra
# and the Rosetta programs with A68J_LOOP_METHODS=every, which makes every
# loop of every method a method of its own, and compares them with the
# results of ga68 in tests/extra/expected and, for the Rosetta programs, in
# the directory NATIVE_ROSETTA (as written by tools/run_native.py). The
# results must be the same as without the variable. Runs both, also after
# failures; the exit status is 1 if one failed.
cd "$(dirname "$0")"
JAR=${2:-../backend/a68j-backend.jar}
export A68J_LOOP_METHODS=every
st=0
python3 run_jvm.py --jar "$JAR" --native ../tests/extra/expected ../tests/extra/*.a68 || st=1
python3 run_jvm.py --jar "$JAR" --native "$1" ../tests/rosetta/*.a68 || st=1
exit $st
