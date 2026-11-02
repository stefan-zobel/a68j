"""method_size_test.py [--jar JAR] [--java JAVA]: methods near and above the
limit of 65 535 bytes of code that the JVM allows in one method
(docs/TODO-method-size.md).

Generates programs with thousands of copies of the statement
s := (s * 7 + k) MOD 1000003 (k = 0, 1, ...) and checks, without ga68 (the
expected values are computed here):

- in the main program without a loop, at a size that fits since the code
  that runs once is compact: the right result;
- in the main program and in a loop of it: the loop is made a method of
  its own after the first attempt (Gen#compileClass), the right result;
- too large in the main program, in a procedure and in a loop: a68j stops
  with status 1 and the error that names the kind of code, its lines and
  its size, and no Java stack trace.
"""
import argparse, os, re, subprocess, sys, tempfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def stmts(n, indent):
    return "".join(f"{indent}s := (s * 7 + {k}) MOD 1000003;\n" for k in range(n))


def expected(*runs):
    """The value of s after the statements: RUNS is a list of (copies, times)."""
    s = 0
    for n, times in runs:
        for _ in range(times):
            for k in range(n):
                s = (s * 7 + k) % 1000003
    return s


END = '  puts (whole (s, 0) + "\'n")\nend\n'

CASES = [
    # name, source, expected stdout or None, expected error pattern or None
    ("main-2500", "begin int s := 0;\n" + stmts(2500, "  ") + END, expected((2500, 1)), None),
    ("main-and-loop", "begin int s := 0;\n" + stmts(2500, "  ") + "  to 2 do\n" + stmts(2499, "    ")
     + "    skip\n  od;\n" + END, expected((2500, 1), (2499, 2)), None),
    ("main-too-large", "begin int s := 0;\n" + stmts(4000, "  ") + END, None,
     r"^m\.a68:1: error: the particular program \(lines 1 to 4003\) needs \d+ bytes of JVM code, "
     r"more than the 65535 that a method may have$"),
    ("proc-too-large", "begin int s := 0;\n  proc p = void: (\n" + stmts(4000, "    ") + "    skip);\n  p;\n" + END,
     None, r"^m\.a68:2:\d+: error: the routine text \(lines 2 to 4003\) needs \d+ bytes of JVM code"),
    ("loop-too-large", "begin int s := 0;\n  to 2 do\n" + stmts(4000, "    ") + "    skip\n  od;\n" + END,
     None, r"^m\.a68:2:\d+: error: the loop clause \(lines 2 to 4004\) needs \d+ bytes of JVM code"),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", default=os.path.join(ROOT, "backend", "a68j-backend.jar"))
    ap.add_argument("--java", default="java")
    a = ap.parse_args()
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    passed = failed = 0
    for name, src, out, err in CASES:
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "m.a68"), "w") as f:
                f.write(src)
            r = subprocess.run([a.java, "-jar", os.path.abspath(a.jar), "-run", "m.a68"], cwd=d, env=env,
                               capture_output=True, text=True, timeout=600)
        if out is not None:
            ok = r.returncode == 0 and r.stdout == f"{out}\n"
        else:
            lines = r.stderr.splitlines()
            ok = (r.returncode == 1 and bool(lines) and re.search(err, lines[0]) is not None
                  and "Exception" not in r.stderr and r.stdout == "")
        if ok:
            passed += 1
        else:
            failed += 1
            print(f"FAIL {name}: status {r.returncode}, stdout {r.stdout[:200]!r}, stderr {r.stderr[:400]!r}")
    print(f"method size tests: {passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
