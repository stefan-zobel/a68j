#!/bin/sh
# Build hsdis-amd64.so (the HotSpot disassembler plugin) with Capstone,
# so that -XX:+PrintAssembly prints real x86-64 instructions.
#
# Why not the distribution package: Ubuntu's libhsdis0-fcml only exports
# the old entry point decode_instructions; JDK 9 and later look for
# decode_instructions_virtual and silently fall back to hex dumps.
#
# Usage:  sh tools/build_hsdis.sh [OUTDIR] [JDK_TAG]
#   OUTDIR   default $HOME/hsdis
#   JDK_TAG  OpenJDK git tag for the plugin sources, default jdk-25+36
#
# Then run the JVM with the plugin on the library path, for example:
#   LD_LIBRARY_PATH=$HOME/hsdis java -XX:+UnlockDiagnosticVMOptions \
#     -XX:CompileCommand=print,a68prog::loop0 -cp ... a68prog
# The same plugin also works with JDK 28.
set -e
OUT=${1:-$HOME/hsdis}
TAG=${2:-jdk-25+36}
TAGURL=$(printf '%s' "$TAG" | sed 's/+/%2B/')
JDK=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")

sudo apt-get install -y libcapstone-dev >/dev/null
mkdir -p "$OUT/src"
for f in capstone/hsdis-capstone.c hsdis.h; do
  curl -sSfL -o "$OUT/src/$(basename $f)" \
    "https://raw.githubusercontent.com/openjdk/jdk/$TAGURL/src/utils/hsdis/$f"
done
# The JDK build passes the Capstone architecture and mode as macros.
gcc -O2 -shared -fPIC -I"$OUT/src" -I"$JDK/include" -I"$JDK/include/linux" \
  -DCAPSTONE_ARCH=CS_ARCH_X86 -DCAPSTONE_MODE=CS_MODE_64 \
  $(pkg-config --cflags capstone) \
  -o "$OUT/hsdis-amd64.so" "$OUT/src/hsdis-capstone.c" -lcapstone
nm -D "$OUT/hsdis-amd64.so" | grep -q decode_instructions_virtual
echo "built $OUT/hsdis-amd64.so"
