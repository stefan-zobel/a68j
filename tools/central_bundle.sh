#!/bin/sh
# A bundle of a release of a68j for Maven Central, uploaded at
# https://central.sonatype.com (Publish Component). Two steps:
#
#   sh tools/central_bundle.sh OUTDIR          stage the files in OUTDIR
#   sh tools/central_bundle.sh -k KEY OUTDIR   sign them, write the bundle
#
# Step 1 takes the release jars backend/dist/a68j-backend-x.y.z.jar and
# backend/dist/a68j-runtime-x.y.z.jar (sh backend/build.sh release, with
# $JDK28 for the multi-release jars) and stages two artifacts, in the layout
# of a Maven repository under OUTDIR/io/github/stefan-zobel:
#   a68j          the compiler (the backend jar, which contains the runtime)
#   a68j-runtime  the runtime alone
# each with its POM, a sources jar, a javadoc jar and the MD5 and SHA-1
# checksums of these four files. The sources come from the working tree; the
# script warns if they differ from the tag x.y.z.
#
# Step 2 signs exactly the staged files (detached, ASCII armored) with the
# GnuPG key KEY (its fingerprint or ID, blanks allowed) and writes
# OUTDIR/a68j-x.y.z-bundle.zip. Between the two steps the staged files can be
# inspected. $GPG names the gpg program (default: gpg); it has to be the one
# whose keyring holds KEY.
#
# JDK: $JDK25, else $JAVA_HOME (25 or newer; jar and javadoc). Needs md5sum
# and sha1sum (coreutils, also in Git Bash).
set -e
R=$(cd "$(dirname "$0")/.." && pwd)
G=io.github.stefan-zobel
GP=io/github/stefan-zobel
URL=https://github.com/stefan-zobel/a68j

KEY=
if [ "$1" = -k ]; then
  KEY=$(printf '%s' "$2" | tr -d ' ')
  if [ -z "$KEY" ]; then
    echo "central_bundle.sh: -k needs a key" >&2
    exit 2
  fi
  shift 2
fi
if [ $# -ne 1 ] || [ -z "$1" ]; then
  echo "usage: sh tools/central_bundle.sh [-k KEY] OUTDIR" >&2
  exit 2
fi
mkdir -p "$1"
OUT=$(cd "$1" && pwd)

V=$(tr -d ' \r\n' < "$R/VERSION")
if ! printf '%s\n' "$V" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "central_bundle.sh: VERSION '$V' is not a release version x.y.z" >&2
  exit 1
fi

# The JDK.
JDK=${JDK25:-$JAVA_HOME}
v=$("$JDK/bin/java" -XshowSettings:properties -version 2>&1 | sed -n 's/ *java.specification.version = //p')
if ! [ "$v" -ge 25 ] 2>/dev/null; then
  echo "central_bundle.sh: no JDK 25 or newer (set \$JDK25 or \$JAVA_HOME; java.specification.version: ${v:-none})" >&2
  exit 1
fi

# ---------------------------------------------------------------- step 2
if [ -n "$KEY" ]; then
  GPG=${GPG:-gpg}
  for a in a68j a68j-runtime; do
    d="$OUT/$GP/$a/$V"
    for f in "$a-$V.jar" "$a-$V.pom" "$a-$V-sources.jar" "$a-$V-javadoc.jar"; do
      if [ ! -f "$d/$f" ]; then
        echo "central_bundle.sh: $d/$f is missing; run step 1 first" >&2
        exit 1
      fi
    done
  done
  for a in a68j a68j-runtime; do
    d="$OUT/$GP/$a/$V"
    for f in "$a-$V.jar" "$a-$V.pom" "$a-$V-sources.jar" "$a-$V-javadoc.jar"; do
      # Bare file names: a gpg for Windows does not understand MSYS paths.
      (cd "$d" && rm -f "$f.asc" &&
        "$GPG" --local-user "$KEY" --armor --detach-sign --output "$f.asc" "$f" &&
        "$GPG" --verify "$f.asc" "$f" 2> /dev/null) || {
        echo "central_bundle.sh: signing $d/$f failed" >&2
        exit 1
      }
      echo "signed $a/$V/$f"
    done
  done
  Z="$OUT/a68j-$V-bundle.zip"
  rm -f "$Z"
  "$JDK/bin/jar" --create --no-manifest --file "$Z" \
      -C "$OUT" "$GP/a68j/$V" -C "$OUT" "$GP/a68j-runtime/$V"
  echo "wrote $Z"
  exit 0
fi

# ---------------------------------------------------------------- step 1
JB="$R/backend/dist/a68j-backend-$V.jar"
JR="$R/backend/dist/a68j-runtime-$V.jar"
for j in "$JB" "$JR"; do
  if [ ! -f "$j" ]; then
    echo "central_bundle.sh: $j is missing (sh backend/build.sh release)" >&2
    exit 1
  fi
done

W="$OUT/work"
rm -rf "$W" "$OUT/$GP/a68j/$V" "$OUT/$GP/a68j-runtime/$V" "$OUT/a68j-$V-bundle.zip"
mkdir -p "$W"
trap 'rm -rf "$W"' EXIT

# The release jars: of this version, and multi-release (with the JDK 28 classes).
for j in "$JB" "$JR"; do
  rm -rf "$W/mf" && mkdir -p "$W/mf"
  (cd "$W/mf" && "$JDK/bin/jar" xf "$j" META-INF/MANIFEST.MF)
  m=$(tr -d '\r' < "$W/mf/META-INF/MANIFEST.MF")
  if ! printf '%s\n' "$m" | grep -qx "Implementation-Version: $V"; then
    echo "central_bundle.sh: $j is not of version $V" >&2
    exit 1
  fi
  if ! printf '%s\n' "$m" | grep -qx "Multi-Release: true"; then
    echo "central_bundle.sh: warning: $j has no JDK 28 classes (built without \$JDK28)" >&2
  fi
done

# The sources against the tag of the release.
if git -C "$R" rev-parse -q --verify "refs/tags/$V" > /dev/null; then
  if ! git -C "$R" diff --quiet "$V" -- src/main backend/src/main backend/lib COPYING COPYING.RUNTIME; then
    echo "central_bundle.sh: warning: the sources differ from the tag $V" >&2
  fi
else
  echo "central_bundle.sh: warning: no tag $V here; the sources are those of the working tree" >&2
fi

# The source trees. The Algol 68 sources of the classes a68j.lib.X (the
# standard prelude and the bundled modules) go to a68j/lib, the JDK 28
# sources to META-INF/versions/28, as in the jars.
SR="$W/src-runtime"
SC="$W/src-compiler"
mkdir -p "$SR/a68j/lib" "$SR/META-INF/versions/28" "$SC"
cp -r "$R/backend/src/main/java/a68j/rt" "$R/backend/src/main/java/a68j/jlib" "$SR/a68j/"
for f in "$R"/backend/lib/*.a68; do
  case "$f" in */aot-training.a68) ;; *) cp "$f" "$SR/a68j/lib/" ;; esac
done
cp -r "$R/backend/src/main/java28/." "$SR/META-INF/versions/28/"
cp "$R/COPYING" "$R/COPYING.RUNTIME" "$SR/META-INF/"
cp -r "$SR/." "$SC/"
cp -r "$R/src/main/java/." "$R/backend/src/main/java/." "$SC/"

# Javadoc (warnings in $W/javadoc-*.log, shown only if javadoc fails). The
# source directories are relative to $R: the paths in the argument file are
# not converted from MSYS to Windows paths as arguments are.
make_javadoc() { # NAME TITLE CLASSPATH SOURCE-DIR...
  n=$1 t=$2 cp=$3
  shift 3
  (cd "$R" && find "$@" -name '*.java') > "$W/javadoc-$n.files"
  if ! (cd "$R" && "$JDK/bin/javadoc" -quiet -Xdoclint:none -notimestamp --release 25 \
      -encoding UTF-8 -docencoding UTF-8 -charset UTF-8 \
      -windowtitle "$t" -doctitle "$t" -cp "$cp" -d "$W/javadoc-$n" \
      @"$W/javadoc-$n.files") > "$W/javadoc-$n.log" 2>&1; then
    cat "$W/javadoc-$n.log" >&2
    echo "central_bundle.sh: javadoc of $n failed" >&2
    exit 1
  fi
  mkdir -p "$W/javadoc-$n/META-INF"
  cp "$R/COPYING" "$R/COPYING.RUNTIME" "$W/javadoc-$n/META-INF/"
}
make_javadoc runtime "a68j runtime $V" "$JR" \
    backend/src/main/java/a68j/rt backend/src/main/java/a68j/jlib
make_javadoc compiler "a68j $V - an Algol 68 compiler for the JVM" "$JB" \
    src/main/java backend/src/main/java

# The POMs. LICENSES is the <license> elements of the artifact.
pom() { # ARTIFACT NAME DESCRIPTION LICENSES
  cat << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>$G</groupId>
  <artifactId>$1</artifactId>
  <version>$V</version>
  <packaging>jar</packaging>

  <name>$2</name>
  <description>$3</description>
  <url>$URL</url>

  <licenses>
$4
  </licenses>

  <developers>
    <developer>
      <name>The a68j authors</name>
      <url>$URL</url>
    </developer>
  </developers>

  <scm>
    <connection>scm:git:$URL.git</connection>
    <developerConnection>scm:git:ssh://git@github.com/stefan-zobel/a68j.git</developerConnection>
    <url>$URL</url>
    <tag>$V</tag>
  </scm>

  <issueManagement>
    <system>GitHub Issues</system>
    <url>$URL/issues</url>
  </issueManagement>
</project>
EOF
}
L_GPL='    <license>
      <name>GPL-3.0-or-later</name>
      <url>https://www.gnu.org/licenses/gpl-3.0.html</url>
      <distribution>repo</distribution>
      <comments>The compiler: packages a68j.front, a68j.jvm and a68j.tools.</comments>
    </license>'
L_RT='    <license>
      <name>GPL-3.0-or-later WITH GCC-exception-3.1</name>
      <url>https://www.gnu.org/licenses/gcc-exception-3.1.html</url>
      <distribution>repo</distribution>
      <comments>The runtime: packages a68j.rt, a68j.jlib and a68j.lib. With an additional permission under section 7 of the GPL: a compilation with a68j is an Eligible Compilation Process in the sense of the GCC Runtime Library Exception 3.1. The exact terms are in the header of each file and in META-INF/COPYING.RUNTIME of the sources jar.</comments>
    </license>'

# Stage one artifact: ARTIFACT JAR SOURCE-DIR JAVADOC-DIR NAME DESCRIPTION LICENSES
stage() {
  a=$1
  d="$OUT/$GP/$a/$V"
  mkdir -p "$d"
  cp "$2" "$d/$a-$V.jar"
  "$JDK/bin/jar" --create --file "$d/$a-$V-sources.jar" -C "$3" .
  "$JDK/bin/jar" --create --file "$d/$a-$V-javadoc.jar" -C "$4" .
  pom "$a" "$5" "$6" "$7" > "$d/$a-$V.pom"
  for f in "$a-$V.jar" "$a-$V.pom" "$a-$V-sources.jar" "$a-$V-javadoc.jar"; do
    printf '%s' "$(md5sum < "$d/$f" | cut -d' ' -f1)" > "$d/$f.md5"
    printf '%s' "$(sha1sum < "$d/$f" | cut -d' ' -f1)" > "$d/$f.sha1"
  done
  echo "staged $d"
}
stage a68j "$JB" "$SC" "$W/javadoc-compiler" \
  "a68j - an Algol 68 compiler for the JVM" \
  "a68j - an Algol 68 compiler for the JVM. It compiles GNU Algol 68, the dialect of ga68 (the Algol 68 front end of GCC), to JVM bytecode. This jar holds the compiler together with the runtime (the classes of a68j-runtime) and needs Java 25 or later. Run a program with: java -jar a68j-$V.jar -run prog.a68" \
  "$L_GPL
$L_RT"
stage a68j-runtime "$JR" "$SR" "$W/javadoc-runtime" \
  "a68j runtime - the runtime of a68j, an Algol 68 compiler for the JVM" \
  "The runtime of a68j, an Algol 68 compiler for the JVM: what a program compiled by a68j needs at run time (the standard prelude, the bundled modules and the run-time support), without the compiler. It has to be of the same version as the compiler that compiled the program, and needs Java 25 or later." \
  "$L_RT"

echo "inspect the files in $OUT/$GP, then sign them with: sh tools/central_bundle.sh -k KEY $1"
