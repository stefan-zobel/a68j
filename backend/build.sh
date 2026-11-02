#!/bin/sh
# Build the JVM backend (requires JDK 25); builds the front end first.
set -e
cd "$(dirname "$0")"
# Use $JDK25 if set, else $JAVA_HOME if it is JDK 25 or newer, else the default path.
if [ -z "$JDK25" ]; then
  v=$("${JAVA_HOME:-/nonexistent}/bin/java" -XshowSettings:properties -version 2>&1 | sed -n 's/ *java.specification.version = //p')
  if [ -n "$v" ] && [ "$v" -ge 25 ] 2>/dev/null; then JDK25=$JAVA_HOME; else JDK25=/usr/lib/jvm/java-25-openjdk-amd64; fi
fi
JAVA_HOME=$JDK25
../build.sh
rm -rf out && mkdir -p out
"$JAVA_HOME/bin/javac" --release 25 -Xlint:all -cp ../a68j.jar -d out $(find src/main/java -name '*.java')
mkdir -p out/a68j/lib && cp lib/ga68.m68 out/a68j/lib/
# The Algol 68 part of the standard prelude (libga68's standard.a68), compiled by the backend itself.
"$JAVA_HOME/bin/java" -cp out:../a68j.jar a68j.jvm.Compile -fbuilding-libga68 -fclass=a68j.lib.Standard lib/standard.a68 -d out
# The bundled Algol 68 modules (Intrinsics.BUNDLED_MODULES): classes a68j.lib.X, export data a68j/lib/x.m68.
for m in lists hashmaps genietransput; do
  M=$(echo $m | cut -c1 | tr a-z A-Z)$(echo $m | cut -c2-)
  "$JAVA_HOME/bin/java" -cp out:../a68j.jar a68j.jvm.Compile -fstropping=supper -fnest-java -fclass=a68j.lib.$M lib/$m.a68 -d out
  mv out/$m.m68 out/a68j/lib/$m.m68
done
cd out && "$JAVA_HOME/bin/jar" xf ../../a68j.jar a68j && cd ..
# With JDK 28 ($JDK28, or the default path if it exists): the JDK 28 version
# of the runtime classes in src/main/java28 (PAR with structured concurrency,
# JEP 543) under META-INF/versions/28 of a multi-release jar. Without it the
# jar has only the JDK 25 version, which also runs on JDK 28.
JDK28=${JDK28:-/opt/jdk28/jdk-28}
rm -rf out28
if [ -x "$JDK28/bin/javac" ]; then
  mkdir -p out28
  "$JDK28/bin/javac" --release 28 -Xlint:all -cp out -d out28 $(find src/main/java28 -name '*.java')
  # The jar tool of JDK 25 cannot validate class files of JDK 28.
  "$JDK28/bin/jar" --create --file a68j-backend.jar --main-class a68j.jvm.Compile -C out . --release 28 -C out28 .
  echo "built backend/a68j-backend.jar (multi-release, with the JDK 28 classes)"
else
  "$JAVA_HOME/bin/jar" --create --file a68j-backend.jar --main-class a68j.jvm.Compile -C out .
  echo "built backend/a68j-backend.jar (without the JDK 28 classes: no \$JDK28)"
fi
# The runtime jar: only what a compiled program needs at run time (a68j.rt,
# a68j.jlib and the classes of a68j.lib: the standard prelude and the
# bundled modules), without the compiler and without the export data
# (*.m68), which only the compiler reads. A program compiled with -d runs
# with java -cp a68j-runtime.jar:<outdir> <Name>.
set --
for f in $(cd out && find a68j/rt a68j/jlib a68j/lib -name '*.class'); do
  set -- "$@" -C out "$f"
done
rm -f a68j-runtime.jar
if [ -d out28 ]; then
  "$JDK28/bin/jar" --create --file a68j-runtime.jar "$@" --release 28 -C out28 .
else
  "$JAVA_HOME/bin/jar" --create --file a68j-runtime.jar "$@"
fi
echo "built backend/a68j-runtime.jar"
# AOT cache (JDK 25, JEP 483/514/515): the classes of the compiler and of the
# runtime, loaded, linked and with method profiles from a training run of
# lib/aot-training.a68. Used with java -XX:AOTCache=backend/a68j-backend.aot;
# it saves about 0.25 s per program. It belongs to this jar and this JDK:
# after a rebuild of the jar or with another JDK the JVM ignores it.
# A68J_AOT=no skips it.
if [ "${A68J_AOT:-yes}" != no ]; then
  rm -f a68j-backend.aot
  "$JAVA_HOME/bin/java" -XX:AOTCacheOutput="$(pwd)/a68j-backend.aot" -cp "$(pwd)/a68j-backend.jar" \
      a68j.jvm.Compile -run lib/aot-training.a68 > /dev/null
  echo "built backend/a68j-backend.aot"
fi
