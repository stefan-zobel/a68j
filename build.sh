#!/bin/sh
# Build the Java port (requires JDK 21 or later).
# $JAVAC and $JAR, if set, name the javac and jar to use (backend/build.sh
# sets them to those of its JDK); otherwise javac and jar from the PATH.
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
"${JAVAC:-javac}" --release 21 -Xlint:all -d out $(find src/main/java -name '*.java')
"${JAR:-jar}" --create --file a68j.jar --main-class a68j.tools.TokenDump -C out .
echo "built a68j.jar"
