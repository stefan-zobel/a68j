#!/bin/sh
# compact_access_test.sh NATIVE_ROSETTA [JAR]: a test of the
# compact element access (Gen#compactAccess in
# backend/src/main/java/a68j/jvm/Gen.java). Runs tests/extra and the Rosetta
# programs with A68J_COMPACT_ACCESS=every, which accesses every element of
# a row by a call of the runtime instead of the inline fast paths, and
# compares them with the results of ga68 in tests/extra/expected and, for
# the Rosetta programs, in the directory NATIVE_ROSETTA (as written by
# tools/run_native.py). The results must be the same as without the
# variable. Runs both, also after failures; the exit status is 1 if one
# failed.
cd "$(dirname "$0")"
JAR=${2:-../backend/a68j-backend.jar}
export A68J_COMPACT_ACCESS=every
st=0
python3 run_jvm.py --jar "$JAR" --native ../tests/extra/expected ../tests/extra/*.a68 || st=1
python3 run_jvm.py --jar "$JAR" --native "$1" ../tests/rosetta/*.a68 || st=1
exit $st
