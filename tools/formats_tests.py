#!/usr/bin/env python3
"""Tests of formatted transput: the rewrite tool (tools/formats) and the
module Genie_Formats (backend/lib/genieformats.a68), on a68j, ga68 and
Algol 68 Genie.

Usage: formats_tests.py [--java JAVA] [--jar JAR] [--no-java]
                        [--a68g A68G] [--ga68 XGCC --libdir LIBGA68]
                        [--record] [NAME ...]

Two kinds of tests, both run on every system given:

  a68j    (unless --no-java) with --jar (default backend/a68j-backend.jar)
          and --java (default $JDK25/bin/java, else java);
  a68g    --a68g A68G: Algol 68 Genie;
  ga68    --ga68 XGCC --libdir LIBGA68: ga68, with the modules
          Genie_Transput and Genie_Formats compiled from backend/lib.

Rewrite tests, tests/formats/rewrite/NAME.in: the program is given to the
tool on standard input, NAME.args (optional) holds the options of the tool
on one line.  The tool (rewrite-a68j.a68 on a68j and ga68, rewrite-a68g.a68
on Algol 68 Genie) must write NAME.expected to standard output and end with
exit status 0, with as many lines as the input; or, if NAME.err exists, end
with a non-zero exit status and write NAME.err as the first line of
standard error.  The output must be the same, byte for byte, on all
systems.

Program tests, tests/formats/NAME.a68: a program in UPPER stropping with
format texts, as written for Algol 68 Genie.  NAME.out is its output,
recorded with Algol 68 Genie (--record --a68g A68G); standard input is
NAME.in if it exists.  If NAME.err exists instead, the program must end
with a non-zero exit status and an error message that contains the text of
NAME.err.  The tool rewrites the program on each system, which then runs
it:

  a68j, ga68  the program rewritten with --ga68, with the module
              Genie_Formats; the output must be NAME.out, or differ only in
              blanks and in floating point numbers (int and real of ga68
              and a68j have 32 bits, those of Algol 68 Genie 64 bits), as
              in tools/partial_tests.py;
  a68g        the rewritten program together with an Algol 68 Genie
              version of Genie_Formats, made from its source (UPPER
              stropping, without the module frame) put in front of it;
              the output must be NAME.out exactly.  This
              compares Genie_Formats with the formats of Algol 68 Genie
              itself.

  --record   writes NAME.expected (or NAME.err) of the rewrite tests from
             the first system, and NAME.out of the program tests with
             Algol 68 Genie (needs --a68g).

Without NAME, all tests are run; NAME is the name of a rewrite test or of a
program test.  The exit status is 1 if a test fails.
"""
import argparse, glob, os, re, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
TOOL = os.path.join(HERE, "formats")
TESTS = os.path.join(ROOT, "tests", "formats")
REWRITE_TESTS = os.path.join(TESTS, "rewrite")
LIB = os.path.join(ROOT, "backend", "lib")
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "book", "tools"))
import run_jvm
import supper_to_upper

TIMEOUT = 120


def default_java():
    jdk = os.environ.get("JDK25")
    return os.path.join(jdk, "bin", "java") if jdk else "java"


class Result:
    def __init__(self, status, out, err):
        self.status, self.out, self.err = status, out, err

    def first_err_line(self):
        lines = self.err.decode("utf-8", "replace").splitlines()
        return lines[0] if lines else ""

    def text(self):
        return self.out.decode("utf-8", "replace")


def run(cmd, stdin=b"", cwd=None, env=None):
    p = subprocess.run(cmd, input=stdin, capture_output=True, cwd=cwd, env=env, timeout=TIMEOUT)
    return Result(p.returncode, p.stdout, p.stderr)


def fresh_dir(work):
    """A new empty directory for one run: programs may create files."""
    return tempfile.mkdtemp(prefix="run-", dir=work)


def a68g_library():
    """The module Genie_Formats as declarations for Algol 68 Genie: the body
    between def and fed, without pub, in UPPER stropping."""
    src = open(os.path.join(LIB, "genieformats.a68"), encoding="utf-8").read()
    body = src[src.index("\ndef\n") + 5:src.rindex("\nfed")]
    body = re.sub(r"\bpub\s+", "", body)
    # Algol 68 Genie has no exit procedure; a division by zero ends the
    # program with a non-zero exit status.
    assert "posix_exit (1)" in body
    body = body.replace("posix_exit (1)", "(int gf_zero := 0; print (1 OVER gf_zero))")
    return supper_to_upper.convert(body) + "\n"


class A68j:
    name = "a68j"

    def __init__(self, java, jar, work):
        self.java, self.jar, self.work = java, jar, os.path.join(work, "a68j")
        self.runtime = os.path.join(os.path.dirname(jar), "a68j-runtime.jar")
        os.makedirs(self.work, exist_ok=True)
        c = subprocess.run([java, "-jar", jar, "-fstropping=upper", "-I", TOOL, "-d", self.work,
                            os.path.join(TOOL, "rewrite-a68j.a68")], capture_output=True)
        if c.returncode != 0:
            sys.exit("a68j cannot compile the tool:\n" + c.stderr.decode("utf-8", "replace"))

    def rewrite(self, args, stdin):
        cp = os.pathsep.join([self.runtime, self.work])
        return run([self.java, "-cp", cp, "RewriteA68j"] + args, stdin)

    def run_program(self, name, data, stdin):
        r = self.rewrite(["--ga68"], data)
        if r.status != 0:
            return r
        prog = os.path.join(self.work, name + ".a68")
        open(prog, "wb").write(r.out)
        return run([self.java, "-jar", self.jar, "-fstropping=upper", "-run", prog], stdin, cwd=fresh_dir(self.work))


class A68g:
    name = "a68g"

    def __init__(self, a68g, work):
        self.a68g, self.work = a68g, os.path.join(work, "a68g")
        os.makedirs(self.work, exist_ok=True)
        self.library = a68g_library().encode("utf-8")

    def rewrite(self, args, stdin):
        return run([self.a68g, "--quiet", "rewrite-a68g.a68", "--"] + args, stdin, cwd=TOOL)

    def run_original(self, path, stdin):
        return run([self.a68g, "--quiet", path], stdin, cwd=fresh_dir(self.work))

    def run_program(self, name, data, stdin):
        r = self.rewrite([], data)
        if r.status != 0:
            return r
        # The library and the program in one closed clause.
        prog = os.path.join(self.work, name + ".a68")
        open(prog, "wb").write(b"BEGIN\n" + self.library + b";\n(" + r.out.rstrip(b"\n") + b")\nEND\n")
        return run([self.a68g, "--quiet", prog], stdin, cwd=fresh_dir(self.work))


class Ga68:
    name = "ga68"

    def __init__(self, xgcc, libdir, work):
        self.env = dict(os.environ, LC_ALL="C", LD_LIBRARY_PATH=libdir)
        self.base = [xgcc, "-B" + os.path.dirname(os.path.abspath(xgcc)), "-L" + libdir]
        self.work = os.path.join(work, "ga68")
        os.makedirs(self.work, exist_ok=True)
        self.objs = []
        for m in ("genietransput", "genieformats"):
            o = os.path.join(self.work, m + ".o")
            self.compile(["-I" + self.work, "-c", os.path.join(LIB, m + ".a68"), "-o", o])
            self.objs.append(o)
        self.exe = os.path.join(self.work, "rewrite")
        self.compile(["-I" + self.work, "-I" + TOOL, "-fstropping=upper", os.path.join(TOOL, "rewrite-a68j.a68"),
                      self.objs[0], "-lga68", "-lm", "-o", self.exe])

    def compile(self, args):
        c = subprocess.run(self.base + args, capture_output=True, cwd=self.work)
        if c.returncode != 0:
            sys.exit("ga68 failed: " + " ".join(args) + "\n" + c.stderr.decode("utf-8", "replace"))

    def rewrite(self, args, stdin):
        return run([self.exe] + args, stdin, env=self.env)

    def run_program(self, name, data, stdin):
        r = self.rewrite(["--ga68"], data)
        if r.status != 0:
            return r
        prog = os.path.join(self.work, name + ".a68")
        exe = os.path.join(self.work, name)
        open(prog, "wb").write(r.out)
        c = subprocess.run(self.base + ["-I" + self.work, "-fstropping=upper", prog] + self.objs + ["-lga68", "-lm", "-o", exe],
                           capture_output=True, cwd=self.work)
        if c.returncode != 0:
            return Result(c.returncode, b"", c.stderr)
        return run([exe], stdin, cwd=fresh_dir(self.work), env=self.env)


# Rewrite tests.

def check_rewrite(name, res, data, sysname):
    """None if res is the expected result of rewrite test name, else a message."""
    base = os.path.join(REWRITE_TESTS, name)
    if os.path.exists(base + ".err"):
        want = open(base + ".err", encoding="utf-8").read().rstrip("\n")
        if res.status == 0:
            return f"{sysname}: exit status 0, expected an error"
        if res.first_err_line() != want:
            return f"{sysname}: error {res.first_err_line()!r}, expected {want!r}"
        return None
    if res.status != 0:
        return f"{sysname}: exit status {res.status}\n{res.err.decode('utf-8', 'replace')}"
    if not os.path.exists(base + ".expected"):
        return f"{sysname}: {name}.expected is missing"
    if res.out != open(base + ".expected", "rb").read():
        return f"{sysname}: output differs from {name}.expected"
    got, lines = res.out.count(b"\n"), data.count(b"\n")
    if got != lines:
        return f"{sysname}: {got} lines, the input has {lines}"
    return None


def rewrite_test(name, systems, record):
    base = os.path.join(REWRITE_TESTS, name)
    data = open(base + ".in", "rb").read()
    args = open(base + ".args").read().split() if os.path.exists(base + ".args") else []
    results = [(s.name, s.rewrite(args, data)) for s in systems]
    if record:
        for ext in (".expected", ".err"):
            if os.path.exists(base + ext):
                os.remove(base + ext)
        first = results[0][1]
        if first.status == 0:
            open(base + ".expected", "wb").write(first.out)
        else:
            open(base + ".err", "w", encoding="utf-8", newline="\n").write(first.first_err_line() + "\n")
    msgs = [m for m in (check_rewrite(name, r, data, n) for n, r in results) if m]
    first = results[0][1]
    for n, r in results[1:]:
        # After an error only the message counts: Algol 68 Genie ends the
        # tool with a run-time error of its own.
        if (r.status == 0) != (first.status == 0) or (r.status == 0 and r.out != first.out):
            msgs.append(f"{n}: output differs from {results[0][0]}")
    return msgs


# Program tests.

def program_test(name, systems, record):
    path = os.path.join(TESTS, name + ".a68")
    data = open(path, "rb").read()
    stdin = open(os.path.join(TESTS, name + ".in"), "rb").read() if os.path.exists(os.path.join(TESTS, name + ".in")) else b""
    out_file = os.path.join(TESTS, name + ".out")
    err_file = os.path.join(TESTS, name + ".err")
    msgs = []
    if os.path.exists(err_file):
        # The program must end with an error that contains the text of
        # NAME.err; Algol 68 Genie writes its messages to standard output.
        want = open(err_file, encoding="utf-8").read().strip()
        for s in systems:
            r = s.run_program(name, data, stdin)
            text = r.err.decode("utf-8", "replace") + r.text()
            if r.status == 0 or want not in text:
                msgs.append(f"{s.name}: exit status {r.status}, expected an error {want!r}\n{text}")
        return msgs
    if record:
        a68g = next(s for s in systems if s.name == "a68g")
        r = a68g.run_original(path, stdin)
        if r.status != 0:
            return [f"Algol 68 Genie: exit status {r.status}\n{r.err.decode('utf-8', 'replace')}{r.text()}"]
        open(out_file, "wb").write(r.out)
    if not os.path.exists(out_file):
        return [f"{name}.out is missing (record it with --record --a68g A68G)"]
    want = open(out_file, encoding="utf-8").read()
    for s in systems:
        r = s.run_program(name, data, stdin)
        got = r.text()
        if r.status != 0:
            msgs.append(f"{s.name}: exit status {r.status}\n{r.err.decode('utf-8', 'replace')}{got}")
        elif got == want:
            continue
        elif s.name == "a68g":
            msgs.append(f"{s.name}: output differs:\n--- expected\n{want}--- {s.name}\n{got}")
        else:
            dev = run_jvm.approx_equal(want, got)
            if dev is None:
                msgs.append(f"{s.name}: output differs:\n--- expected\n{want}--- {s.name}\n{got}")
    return msgs


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--java", default=default_java())
    ap.add_argument("--jar", default=os.path.join(ROOT, "backend", "a68j-backend.jar"))
    ap.add_argument("--no-java", action="store_true")
    ap.add_argument("--a68g")
    ap.add_argument("--ga68")
    ap.add_argument("--libdir")
    ap.add_argument("--record", action="store_true")
    ap.add_argument("names", nargs="*")
    a = ap.parse_args()
    if a.ga68 and not a.libdir:
        ap.error("--ga68 needs --libdir")

    work = tempfile.mkdtemp(prefix="a68-formats-")
    systems = []
    if not a.no_java:
        systems.append(A68j(a.java, os.path.abspath(a.jar), work))
    if a.a68g:
        systems.append(A68g(a.a68g, work))
    if a.ga68:
        systems.append(Ga68(a.ga68, os.path.abspath(a.libdir), work))
    if not systems:
        ap.error("no system to run the tests on")

    rewrites = sorted(os.path.basename(p)[:-3] for p in glob.glob(os.path.join(REWRITE_TESTS, "*.in")))
    programs = sorted(os.path.basename(p)[:-4] for p in glob.glob(os.path.join(TESTS, "*.a68")))
    if a.names:
        rewrites = [n for n in rewrites if n in a.names]
        programs = [n for n in programs if n in a.names]
    if a.record and programs and not a.a68g:
        ap.error("--record of program tests needs --a68g")

    failed = 0
    for kind, names, test in (("rewrite", rewrites, rewrite_test), ("program", programs, program_test)):
        for name in names:
            msgs = test(name, systems, a.record)
            print(("FAIL " if msgs else "pass ") + f"{kind} {name}")
            for m in msgs:
                print("    " + "\n    ".join(m.splitlines()[:40]))
            failed += bool(msgs)
    total = len(rewrites) + len(programs)
    print(f"{total - failed} of {total} passed ({', '.join(s.name for s in systems)})")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
