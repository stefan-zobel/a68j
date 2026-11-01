#!/bin/sh
# loop_methods_test.sh NATIVE_EXTRA NATIVE_ROSETTA [JAR]: a test of loop
# methods (backend/src/main/java/a68j/jvm/Outline.java). Runs tests/extra
# and the Rosetta programs with A68J_LOOP_METHODS=every, which makes every
# loop of every method a method of its own, and compares them with the
# results of ga68 in the directories NATIVE_EXTRA and NATIVE_ROSETTA (as
# written by tools/run_native.py). The results must be the same as
# without the variable.
set -e
cd "$(dirname "$0")"
JAR=${3:-../backend/a68j-backend.jar}
export A68J_LOOP_METHODS=every
python3 run_jvm.py --jar "$JAR" --native "$1" ../tests/extra/*.a68
python3 run_jvm.py --jar "$JAR" --native "$2" ../tests/rosetta/*.a68
