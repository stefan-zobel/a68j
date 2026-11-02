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

And programs like the step of an interpreter: a procedure that is one case
clause with N branches of six element accesses each, called in a loop
(Gen#compactAccess):

- 20 branches: above 8000 bytes with the inline element access, below
  with compact access, which a68j chooses after the loops (there are none)
  did not help;
- 60 branches: above the JVM limit inline, valid compact;
- 200 branches: too large even compact, the error for the routine text.
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


def interp(n, calls):
    """The interpreter-like program with N branches and CALLS calls of its step."""
    branches = []
    for k in range(n):
        a, b, c, m = k % 16, (k * 5 + 3) % 16, (k * 11 + 7) % 16, k % 7 + 2
        branches.append(f"    (r[{a}] := (r[{b}] * {m} + r[{c}]) MOD 65521; if r[{a}] > r[{b}] then r[{c}] +:= 1 fi)")
    return ("begin [0:15] int r; for i from 0 to 15 do r[i] := i od;\n"
            "  proc step = (int code) void:\n    case code + 1 in\n" + ",\n".join(branches) + "\n    esac;\n"
            f"  for t to {calls} do step (t MOD {n}) od;\n"
            "  int s := 0; for i from 0 to 15 do s +:= r[i] od;\n" + END)


def interp_expected(n, calls):
    r = list(range(16))
    for t in range(1, calls + 1):
        k = t % n
        a, b, c, m = k % 16, (k * 5 + 3) % 16, (k * 11 + 7) % 16, k % 7 + 2
        r[a] = (r[b] * m + r[c]) % 65521
        if r[a] > r[b]:
            r[c] += 1
    return sum(r)


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
    ("interp-20", interp(20, 20000), interp_expected(20, 20000), None),
    ("interp-60", interp(60, 20000), interp_expected(60, 20000), None),
    ("interp-200", interp(200, 20000), None,
     r"^m\.a68:2:\d+: error: the routine text \(lines 2 to 204\) needs \d+ bytes of JVM code"),
]

# Methods that must be at most this large (bytes of code) in the class file
# of a case that compiles; the others are not checked.
MAX_SIZE = {"interp-20": 8000, "interp-60": 65535}


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
            # Compiled to M.class and run apart, so that the method sizes
            # can be checked.
            r = subprocess.run([a.java, "-jar", os.path.abspath(a.jar), "m.a68"], cwd=d, env=env,
                               capture_output=True, text=True, timeout=600)
            big = ""
            if r.returncode == 0 and name in MAX_SIZE:
                big = subprocess.run([a.java, os.path.join(ROOT, "tools", "MethodSizes.java"),
                                      "-min", str(MAX_SIZE[name] + 1), "M.class"], cwd=d, env=env,
                                     capture_output=True, text=True, timeout=600).stdout
            if r.returncode == 0:
                r = subprocess.run([a.java, "-cp", os.path.abspath(a.jar) + os.pathsep + ".", "M"], cwd=d, env=env,
                                   capture_output=True, text=True, timeout=600)
        if out is not None:
            ok = r.returncode == 0 and r.stdout == f"{out}\n" and big == ""
            if big:
                print(f"{name}: methods above {MAX_SIZE[name]} bytes: {big.strip()}")
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
