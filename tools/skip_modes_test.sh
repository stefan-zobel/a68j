#!/bin/sh
# Modes without a SKIP value in ga68 (Gen#checkLowering): ga68 stops with
# "get skip tree: cannot compute SKIP for mode M" where its lowering needs
# a SKIP value of FILE, CHANNEL, BYTES or LONG BYTES. Every case below is
# a declaration in "begin ...; puts ("ok'n") end"; the expected result is
# what ga68 (GCC trunk of 31 Oct 2026) gives: "ok" or the mode in the
# message. The script checks that a68j gives the same.
# Usage: tools/skip_modes_test.sh [JAVA].
cd "$(dirname "$0")/.."
JAVA=${1:-java}
JAR=backend/a68j-backend.jar
pass=0; fail=0
dir=$(mktemp -d); trap 'rm -rf "$dir"' EXIT
while IFS='|' read -r expect decl; do
  [ -z "$decl" ] && continue
  printf 'begin\n    %s;\n    puts ("ok'"'"'n")\nend\n' "$decl" > "$dir/m.a68"
  out=$(cd "$dir" && timeout 60 "$JAVA" -jar "$OLDPWD/$JAR" -fstropping=supper -run m.a68 2>&1 | grep -v '^Picked up')
  case "$out" in
    ok) got=ok ;;
    *"cannot compute SKIP for mode "*) got=$(echo "$out" | sed -n 's/.*cannot compute SKIP for mode //p' | head -1) ;;
    *) got="other: $(echo "$out" | head -1)" ;;
  esac
  if [ "$got" = "$expect" ]; then pass=$((pass + 1)); else echo "FAIL $decl: expected $expect, got $got"; fail=$((fail + 1)); fi
done <<'EOF'
file|file f
channel|channel c
bytes|bytes b = bytes_pack ("ab")
long bytes|long bytes lb
ok|proc p = (file f) void: skip
ok|ref file rf = nil
ok|mode S = struct (int a, file f); ref S rs = nil
file|mode S = struct (int a, file f); S s
ok|[3] file r
ok|union (int, file) u
ok|union (file, int) u
ok|heap file hf
file|proc q = file: skip
ok|proc q = (file f) file: f
file|proc q = (file f) file: (f)
file|ref file rf = nil; file g = rf
ok|sema s
ok|compl z
ok|mode U = union (bytes, int); U u
EOF
echo "skip modes: $pass passed, $fail failed"
[ $fail -eq 0 ]
