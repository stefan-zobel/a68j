#!/bin/sh
# compact_access_test.sh NATIVE_EXTRA NATIVE_ROSETTA [JAR]: a test of the
# compact element access (Gen#compactAccess in
# backend/src/main/java/a68j/jvm/Gen.java). Runs tests/extra and the Rosetta
# programs with A68J_COMPACT_ACCESS=every, which accesses every element of
# a row by a call of the runtime instead of the inline fast paths, and
# compares them with the results of ga68 in the directories NATIVE_EXTRA and
# NATIVE_ROSETTA (as written by tools/run_native.py). The results must be
# the same as without the variable.
set -e
cd "$(dirname "$0")"
JAR=${3:-../backend/a68j-backend.jar}
export A68J_COMPACT_ACCESS=every
python3 run_jvm.py --jar "$JAR" --native "$1" ../tests/extra/*.a68
python3 run_jvm.py --jar "$JAR" --native "$2" ../tests/rosetta/*.a68
