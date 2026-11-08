#!/usr/bin/env python3
"""Tests of partial parametrization (tests/partial): calls that leave
arguments out, f (3, ), compared with Algol 68 Genie.

Usage: partial_tests.py [--java JAVA] [--jar JAR] [FILE ...]
       partial_tests.py --record --a68g A68G [FILE ...]

Without FILE, all programs tests/partial/*.a68 are run.  ga68 cannot serve
as the reference here (it crashes on partial parametrization, see
docs/ga68-bug-reports.md, report 19), so the expected output NAME.out was
recorded with Algol 68 Genie:

  --record --a68g A68G   runs every program with Algol 68 Genie (converted
                         to UPPER stropping by book/tools/supper_to_upper.py)
                         and writes its standard output to NAME.out.

Otherwise every program is compiled and run with a68j (-run), standard
input from NAME.in if it exists, and must end with exit status 0 and print
NAME.out:

  pass     the output is identical;
  approx   the output differs only in blanks and in floating point numbers
           within the tolerance of tools/run_jvm.py (approx_equal): a68j
           has the 32-bit int and real of ga68, Algol 68 Genie a 64-bit
           int (wider columns) and a 64-bit real, so the last digits and
           the layout of numbers differ;
  FAIL     anything else.

The exit status is 1 if a program fails.
"""
import argparse, os, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
TESTS = os.path.join(ROOT, "tests", "partial")
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "book", "tools"))
import run_jvm
import supper_to_upper

TIMEOUT = 120


def stdin_of(path):
    p = path[:-4] + ".in"
    return open(p, "rb").read() if os.path.exists(p) else b""


def run(cmd, path):
    p = subprocess.run(cmd, input=stdin_of(path), capture_output=True, cwd=TESTS,
                       timeout=TIMEOUT, env=dict(os.environ, LC_ALL="C"))
    return p.returncode, p.stdout.decode("utf-8", "replace"), p.stderr.decode("utf-8", "replace")


def record(path, a68g, work):
    upper = os.path.join(work, os.path.basename(path))
    open(upper, "w", encoding="utf-8").write(supper_to_upper.convert(open(path, encoding="utf-8").read()))
    rc, out, err = run([a68g, "--quiet", upper], path)
    if rc != 0:
        return False, f"Algol 68 Genie: exit status {rc}\n{err}"
    open(path[:-4] + ".out", "w", encoding="utf-8").write(out)
    return True, "recorded"


def check(path, java, jar):
    rc, out, err = run([java, "-jar", jar, "-run", path], path)
    if rc != 0:
        return False, f"exit status {rc}\n{err}{out}"
    want = open(path[:-4] + ".out", encoding="utf-8").read()
    if out == want:
        return True, "pass"
    dev = run_jvm.approx_equal(want, out)
    if dev is not None:
        dev = dev.replace("(native ", "(Algol 68 Genie ")
        return True, "approx" + (f" (largest deviation {dev})" if dev else " (blanks only)")
    return False, "output differs:\n--- expected\n" + want + "--- a68j\n" + out


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--java", default="java")
    ap.add_argument("--jar", default=os.path.join(ROOT, "backend", "a68j-backend.jar"))
    ap.add_argument("--record", action="store_true")
    ap.add_argument("--a68g")
    ap.add_argument("files", nargs="*")
    a = ap.parse_args()
    if a.record and not a.a68g:
        ap.error("--record needs --a68g")
    files = [os.path.abspath(f) for f in a.files] or sorted(
        os.path.join(TESTS, f) for f in os.listdir(TESTS) if f.endswith(".a68"))
    work = tempfile.mkdtemp(prefix="partial-")
    failed = 0
    for f in files:
        ok, msg = record(f, a.a68g, work) if a.record else check(f, a.java, a.jar)
        name = os.path.relpath(f, TESTS) if os.path.dirname(f) == TESTS else f
        if ok:
            tag, _, rest = msg.partition(" ")
            print(f"{tag:<9}{name}" + (" " + rest if rest else ""))
        else:
            failed += 1
            print(f"{'FAIL':<9}{name}")
            for line in msg.splitlines()[:20]:
                print("  " + line)
    print(f"{len(files) - failed} of {len(files)} programs ok")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
