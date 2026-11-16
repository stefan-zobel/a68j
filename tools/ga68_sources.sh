#!/bin/sh
# The Algol 68 sources that ga68 users need, as an asset of a release:
#
#   sh tools/ga68_sources.sh OUTDIR
#
# writes OUTDIR/a68j-ga68-sources-x.y.z.zip (x.y.z from VERSION) with the
# directory a68j-ga68-sources-x.y.z/ holding the bundled modules that ga68
# compiles (Genie_Transput, Genie_Formats, Texts), the format rewrite tool
# with its prologue for ga68 and a68j, a README.txt with the commands, and
# COPYING and COPYING.RUNTIME. The files are those of the working tree.
#
# JDK: $JDK25, else $JAVA_HOME (its jar tool writes the zip, since Git Bash
# has no zip program).
set -e
R=$(cd "$(dirname "$0")/.." && pwd)
if [ $# -ne 1 ] || [ -z "$1" ]; then
  echo "usage: sh tools/ga68_sources.sh OUTDIR" >&2
  exit 2
fi
mkdir -p "$1"
OUT=$(cd "$1" && pwd)

V=$(tr -d ' \r\n' < "$R/VERSION")
if ! printf '%s\n' "$V" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "ga68_sources.sh: VERSION '$V' is not a release version x.y.z" >&2
  exit 1
fi
JDK=${JDK25:-$JAVA_HOME}
if [ ! -x "$JDK/bin/jar" ] && [ ! -x "$JDK/bin/jar.exe" ]; then
  echo "ga68_sources.sh: no jar tool (set \$JDK25 or \$JAVA_HOME)" >&2
  exit 1
fi

N=a68j-ga68-sources-$V
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
D="$W/$N"
mkdir -p "$D"
cp "$R/backend/lib/genietransput.a68" "$R/backend/lib/genieformats.a68" \
   "$R/backend/lib/texts.a68" "$D/"
cp "$R/tools/formats/rewrite.a68" "$R/tools/formats/rewrite-a68j.a68" "$D/"
cp "$R/COPYING" "$R/COPYING.RUNTIME" "$D/"

cat > "$D/README.txt" <<EOF
a68j $V: the Algol 68 sources for ga68

These files come with a68j (https://github.com/stefan-zobel/a68j). With
them ga68, the Algol 68 front end of GCC, compiles programs that use the
transput of Algol 68 Genie (print, read, put, get, files) and its formatted
output (format texts, printf, putf). a68j has the modules built in, and its
options -fformats and -fgenie run the rewrite tool inside the compiler.

  genietransput.a68   the module Genie_Transput: print, read, put, get,
                      files, strings as files, events
  genieformats.a68    the module Genie_Formats: formatted output; it
                      accesses Genie_Transput
  texts.a68           the module Texts: string functions
  rewrite.a68         the format rewrite tool, which turns the format texts
  rewrite-a68j.a68    of a program into calls of Genie_Formats (its body and
                      its prologue for ga68 and a68j, UPPER stropping)

Compile the modules and the tool once:

  ga68 -c genietransput.a68
  ga68 -I. -c genieformats.a68
  ga68 -I. -c texts.a68
  ga68 -I. -fstropping=upper rewrite-a68j.a68 genietransput.o -o rewrite

A program that accesses Genie_Transput:

  ga68 -I. prog.a68 genietransput.o -o prog

A program with format texts: the tool rewrites it first, keeping its
lines, and adds Genie_Formats to its access list.

  ./rewrite < prog.a68 > prog-r.a68
  ga68 -I. prog-r.a68 genieformats.o genietransput.o -o prog

A program written for Algol 68 Genie (UPPER stropping, such as a Rosetta
Code solution): with --ga68 the tool also puts it into an access clause of
the modules and adapts it for ga68.

  ./rewrite --ga68 < prog.a68 > prog-r.a68
  ga68 -I. -fstropping=upper prog-r.a68 genieformats.o genietransput.o -o prog

The modules are described in docs/genie-transput.md, docs/genie-formats.md
and docs/texts.md of the repository, and in chapters 1 and 9 of the book
"Algol 68 in Modern Terms".

License: the GNU General Public License, version 3 or later (COPYING). The
modules are part of the runtime of a68j and have, in addition, the GCC
Runtime Library Exception 3.1 with the permission that their headers state
(COPYING.RUNTIME).
EOF

rm -f "$OUT/$N.zip"
(cd "$W" && "$JDK/bin/jar" --create --no-manifest --file "$OUT/$N.zip" "$N")
echo "$OUT/$N.zip"
