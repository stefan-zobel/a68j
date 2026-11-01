#!/bin/sh
# perf_compile.sh FILE.a68: compile one Rosetta program to classes (for
# tools/perf_run.py), so that the timing does not include the compiler.
# Environment: JAR (default backend/a68j-backend.jar), OUT (directory; c.NAME/ per program),
# The programs use the module Genie_Transput, which comes with the jar.
# Usage: ls tests/rosetta/*.a68 | xargs -P 8 -n 1 sh tools/perf_compile.sh
export JAVA_TOOL_OPTIONS=
n=$(basename "$1" .a68); J=${JAR:-$(dirname "$0")/../backend/a68j-backend.jar}; O=${OUT:-/tmp/all}
mkdir -p "$O/c.$n"
opts=$(sed -n 's/^{ dg-options "\(.*\)" }$/\1/p' "$1" | head -1)
java -XX:TieredStopAtLevel=1 -jar $J $opts "$1" -d "$O/c.$n" > "$O/c.$n/compile.log" 2>&1
