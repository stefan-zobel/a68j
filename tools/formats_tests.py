#!/usr/bin/env python3
"""Tests of formatted transput: the rewrite tool (tools/formats) and the
module Genie_Formats (backend/lib/genieformats.a68), on a68j, ga68 and
Algol 68 Genie.

Usage: formats_tests.py [--java JAVA] [--jar JAR] [--no-java]
                        [--a68g A68G] [--ga68 XGCC --libdir LIBGA68]
                        [--record] [NAME ...]

Three kinds of tests.  The systems are:

  a68j          (unless --no-java) with --jar (default
                backend/a68j-backend.jar) and --java (default
                $JDK25/bin/java, else java);
  a68j -fgenie  the same a68j, compiling the original program with the
                option -fgenie (program tests and Genie tests only);
  a68g          --a68g A68G: Algol 68 Genie;
  ga68          --ga68 XGCC --libdir LIBGA68: ga68, with the modules
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
NAME.err.  If NAME.fixed exists, Genie_Formats deliberately differs from
Algol 68 Genie for this program (NAME.fixed says how): NAME.out or NAME.err
is written by hand, --record leaves it alone, and the test does not run on
Algol 68 Genie.  If NAME.native exists, the program needs the value error
handler of a file, which the Algol 68 Genie version of Genie_Formats
cannot call (see below): NAME.out is recorded with Algol 68 Genie, but the
test does not run with that version.  The tool rewrites the program on each system, which then
runs it:

  a68j, ga68  the program rewritten with --ga68, with the module
              Genie_Formats; the output must be NAME.out, or differ only in
              blanks and in floating point numbers (int and real of ga68
              and a68j have 32 bits, those of Algol 68 Genie 64 bits), as
              in tools/partial_tests.py;
  a68j -fgenie  the original program, compiled with -fgenie, which
              rewrites it in the compiler; the output is compared as on
              a68j;
  a68g        the rewritten program together with an Algol 68 Genie
              version of Genie_Formats, made from its source (UPPER
              stropping, without the module frame) put in front of it;
              the output must be NAME.out exactly.  This
              compares Genie_Formats with the formats of Algol 68 Genie
              itself.

Genie tests, tests/formats/genie/NAME.a68: a program written for Algol
68 Genie, run as it is with a68j -fgenie and with Algol 68 Genie, for
what -fgenie does besides formats: PR read of files in
tests/formats/genie/include, apostrophes, FILE, other pragmats, and the
lines in error messages.  NAME.out is its output, recorded with Algol 68
Genie, compared as in the program tests; standard input is NAME.in if it
exists.  If NAME.err exists instead, the test runs on a68j -fgenie only:
the program must end with a non-zero exit status, and the text of
NAME.err must appear in standard error.  The Rosetta Code solutions
rosetta-json and rosetta-json-pointer are Genie tests too; they read the
library json.incl.a68 next to them, as on Rosetta Code (a file NAME.incl.a68
is a library, not a test), and give the same output, byte for byte, on all
systems.

  --record   writes NAME.expected (or NAME.err) of the rewrite tests from
             the first system, and NAME.out of the program and Genie
             tests with Algol 68 Genie (needs --a68g), except for a
             program test with NAME.fixed.

Without NAME, all tests are run; NAME is the name of a rewrite test, a
program test or a Genie test.  The exit status is 1 if a test fails.
"""
import argparse, glob, os, re, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
TOOL = os.path.join(HERE, "formats")
TESTS = os.path.join(ROOT, "tests", "formats")
REWRITE_TESTS = os.path.join(TESTS, "rewrite")
GENIE_TESTS = os.path.join(TESTS, "genie")
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
    # Algol 68 Genie has no column of a file, so the alignment k does
    # nothing; its tests (with NAME.fixed) do not run on Algol 68 Genie.
    column = "proc gf_column = (ref File f, int c) void: while col of f + 1 < c do space (f) od;"
    assert column in body
    body = body.replace(column, "proc gf_column = (ref File f, int c) void: skip;")
    column_in = re.compile(r"proc gf_column_in = .*?do int d = gt_next_char \(f\); skip od;", re.S)
    assert column_in.search(body)
    body = column_in.sub("proc gf_column_in = (ref File f, int c) void: skip;", body)
    return supper_to_upper.convert(A68G_CHARS + body) + "\n"


# The procedures of Genie_Transput for reading one character at a time,
# built from the transput of Algol 68 Genie.  The characters given back by
# gt_unread or looked at by gt_peek_char are kept for one call of getf only
# (get does not see them), and the events are those of Algol 68 Genie,
# except that a value error ends the program: its handler cannot be called
# from a program.  Tests that need more have NAME.native.
A68G_CHARS = """
    string gt_back := "";
    bool gt_ended := false;
    proc gt_start_get = (ref File f) void:
       begin [1 : 0] int none;
             get (f, none);  { the read mood }
             gt_back := "";
             gt_ended := false
       end;
    proc gt_peek_char = (ref File f) int:
       if UPB gt_back >= 1 then ABS gt_back[1]
       elif eof (f) then -1
       elif eoln (f) then get (f, newline); gt_back := REPR 10; 10
       else char c; get (f, c); gt_back := c; ABS c
       fi;
    proc gt_next_char = (ref File f) int:
       begin int c = gt_peek_char (f);
             gt_ended := c < 0;
             (c >= 0 | gt_back := gt_back[2 :]);
             c
       end;
    proc gt_unread = (ref File f, string s) void: (gt_back := s + gt_back; gt_ended := false);
    proc gt_at_end = (ref File f) bool: gt_ended;
    proc gt_file_end = (ref File f) void: (string s; get (f, s));
    proc gt_value_error = (ref File f, string mode_name) void:
       gf_error ("error transputting " + mode_name + " value");
"""


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

    def run_program(self, name, path, data, stdin):
        r = self.rewrite(["--ga68"], data)
        if r.status != 0:
            return r
        prog = os.path.join(self.work, name + ".a68")
        open(prog, "wb").write(r.out)
        return run([self.java, "-jar", self.jar, "-fstropping=upper", "-run", prog], stdin, cwd=fresh_dir(self.work))


class A68jGenie:
    name = "a68j -fgenie"

    def __init__(self, java, jar, work):
        self.java, self.jar, self.work = java, jar, os.path.join(work, "a68j-genie")
        os.makedirs(self.work, exist_ok=True)

    def run_program(self, name, path, data, stdin):
        return run([self.java, "-jar", self.jar, "-fgenie", "-run", path], stdin, cwd=fresh_dir(self.work))


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

    def run_program(self, name, path, data, stdin):
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

    def run_program(self, name, path, data, stdin):
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

def program_test(name, systems, record, tests=TESTS):
    path = os.path.join(tests, name + ".a68")
    data = open(path, "rb").read()
    stdin = open(os.path.join(tests, name + ".in"), "rb").read() if os.path.exists(os.path.join(tests, name + ".in")) else b""
    out_file = os.path.join(tests, name + ".out")
    err_file = os.path.join(tests, name + ".err")
    fixed = os.path.exists(os.path.join(tests, name + ".fixed"))
    if fixed:
        # The alignment k does nothing in the Algol 68 Genie version of
        # the library, and the deliberate differences are those of Algol 68
        # Genie itself.
        systems = [s for s in systems if s.name != "a68g"]
    # With NAME.native the program needs a value error handler, which the
    # Algol 68 Genie version of the library cannot call: the output is
    # recorded with Algol 68 Genie, but not compared with that version.
    native = os.path.exists(os.path.join(tests, name + ".native"))
    runs = [s for s in systems if not (native and s.name == "a68g")]
    msgs = []
    if os.path.exists(err_file):
        # The program must end with an error that contains the text of
        # NAME.err; Algol 68 Genie writes its messages to standard output.
        want = open(err_file, encoding="utf-8").read().strip()
        for s in runs:
            r = s.run_program(name, path, data, stdin)
            text = r.err.decode("utf-8", "replace") + r.text()
            if r.status == 0 or want not in text:
                msgs.append(f"{s.name}: exit status {r.status}, expected an error {want!r}\n{text}")
        return msgs
    if record and not fixed:
        a68g = next(s for s in systems if s.name == "a68g")
        r = a68g.run_original(path, stdin)
        if r.status != 0:
            return [f"Algol 68 Genie: exit status {r.status}\n{r.err.decode('utf-8', 'replace')}{r.text()}"]
        open(out_file, "wb").write(r.out)
    if not os.path.exists(out_file):
        return [f"{name}.out is missing (record it with --record --a68g A68G)"]
    want = open(out_file, encoding="utf-8").read()
    for s in runs:
        r = s.run_program(name, path, data, stdin)
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


class Original:
    """Algol 68 Genie running the original program, not the rewritten one
    (Genie tests)."""

    def __init__(self, a68g):
        self.a68g, self.name = a68g, a68g.name

    def run_original(self, path, stdin):
        return self.a68g.run_original(path, stdin)

    def run_program(self, name, path, data, stdin):
        return self.a68g.run_original(path, stdin)


def genie_test(name, systems, record):
    """A Genie test: on a68j -fgenie and Algol 68 Genie, or, with NAME.err,
    on a68j -fgenie only."""
    if os.path.exists(os.path.join(GENIE_TESTS, name + ".err")):
        systems = [s for s in systems if s.name == "a68j -fgenie"]
    else:
        systems = [Original(s) if s.name == "a68g" else s
                   for s in systems if s.name in ("a68j -fgenie", "a68g")]
    if not systems:
        return []
    return program_test(name, systems, record, GENIE_TESTS)


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
        systems.append(A68jGenie(a.java, os.path.abspath(a.jar), work))
    if a.a68g:
        systems.append(A68g(a.a68g, work))
    if a.ga68:
        systems.append(Ga68(a.ga68, os.path.abspath(a.libdir), work))
    if not systems:
        ap.error("no system to run the tests on")

    rewrites = sorted(os.path.basename(p)[:-3] for p in glob.glob(os.path.join(REWRITE_TESTS, "*.in")))
    programs = sorted(os.path.basename(p)[:-4] for p in glob.glob(os.path.join(TESTS, "*.a68")))
    genies = sorted(os.path.basename(p)[:-4] for p in glob.glob(os.path.join(GENIE_TESTS, "*.a68"))
                    if not p.endswith(".incl.a68"))
    if a.names:
        rewrites = [n for n in rewrites if n in a.names]
        programs = [n for n in programs if n in a.names]
        genies = [n for n in genies if n in a.names]
    if a.record and (programs or genies) and not a.a68g:
        ap.error("--record of program tests needs --a68g")

    failed = 0
    # The rewrite tests need the tool, which a68j -fgenie does not run alone.
    rewriters = [s for s in systems if hasattr(s, "rewrite")]
    for kind, names, test, on in (("rewrite", rewrites, rewrite_test, rewriters),
                                  ("program", programs, program_test, systems),
                                  ("genie", genies, genie_test, systems)):
        for name in names:
            msgs = test(name, on, a.record)
            print(("FAIL " if msgs else "pass ") + f"{kind} {name}")
            for m in msgs:
                print("    " + "\n    ".join(m.splitlines()[:40]))
            failed += bool(msgs)
    total = len(rewrites) + len(programs) + len(genies)
    print(f"{total - failed} of {total} passed ({', '.join(s.name for s in systems)})")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
