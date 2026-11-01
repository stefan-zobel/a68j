#!/bin/sh
# Build the Java port (requires JDK 21 or later).
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac --release 21 -Xlint:all -d out $(find src/main/java -name '*.java')
jar --create --file a68j.jar --main-class a68j.tools.TokenDump -C out .
echo "built a68j.jar"
