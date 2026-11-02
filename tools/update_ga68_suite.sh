#!/bin/sh
# update_ga68_suite.sh GCC [XGCC LIBGA68]: brings the copy of the ga68 test
# suite in tests/ga68 up to date with a GCC checkout.
#
#   GCC      the GCC source directory (a checkout of
#            https://gcc.gnu.org/git/gcc.git or a mirror of it); its
#            gcc/testsuite/algol68/execute is copied to tests/ga68/execute,
#            replacing the old copy, and the commit of the checkout is
#            written to tests/ga68/GCC-COMMIT.
#   XGCC     optional: the ga68 driver of a GCC build (…/gcc/xgcc), and
#   LIBGA68  the directory of libga68 of that build
#            (…/x86_64-pc-linux-gnu/libga68/.libs).  With both, the results
#            of ga68 are recorded again (tools/run_native.py --reference):
#            for the test suite in tests/ga68/expected and for tests/extra
#            in tests/extra/expected.  The ga68 of the build should be made
#            from the same checkout.
#
# Only the directory "execute" is copied: the tests in
# gcc/testsuite/algol68/compile only compile, and check the error messages
# of GCC, which a68j does not use.  The copy is not changed; see
# tests/ga68/README.md for the origin and the license.  After an update,
# git diff tests/ga68 shows the new and changed tests and results.
set -e
if [ $# -ne 1 ] && [ $# -ne 3 ]; then
  echo "usage: $0 GCC [XGCC LIBGA68]" >&2
  exit 2
fi
gcc=$(cd "$1" && pwd)
src=$gcc/gcc/testsuite/algol68/execute
if [ ! -d "$src" ]; then
  echo "$0: $src does not exist" >&2
  exit 1
fi
cd "$(dirname "$0")/.."
root=$(pwd)

rm -rf tests/ga68/execute
mkdir -p tests/ga68
cp -R "$src" tests/ga68/execute
if commit=$(git -C "$gcc" log -1 --format='%H %cs' 2>/dev/null); then
  echo "$commit" > tests/ga68/GCC-COMMIT
else
  echo "unknown (not a git checkout)" > tests/ga68/GCC-COMMIT
fi
echo "tests/ga68/execute: $(find tests/ga68/execute -name '*.a68' | wc -l) files, GCC commit $(cat tests/ga68/GCC-COMMIT)"

[ $# -eq 3 ] || exit 0
xgcc=$(cd "$(dirname "$2")" && pwd)/$(basename "$2")
lib=$(cd "$3" && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# record DEST FILES...: the results of ga68 for FILES in DEST (only the
# .json files; run_native.py also leaves the objects of module packets).
record() {
  dest=$1; shift
  python3 tools/run_native.py --ga68 "$xgcc" --libdir "$lib" --reference \
    --out "$tmp/out" "$@"
  rm -rf "$dest"
  mkdir -p "$dest"
  cp "$tmp"/out/*.json "$dest"
  rm -rf "$tmp/out"
}
S=tests/ga68/execute
record tests/ga68/expected "$S"/*.a68 "$S"/mcgt/*.a68 "$S"/modules/*.a68
record tests/extra/expected tests/extra/*.a68
if grep -rl -e "$HOME/" -e "$root/" -e "$gcc/" tests/ga68/expected tests/extra/expected; then
  echo "$0: the files above still contain a directory of this machine" >&2
  exit 1
fi
