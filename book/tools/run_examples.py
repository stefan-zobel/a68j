#!/usr/bin/env python3
"""Run the example programs of the book with a68j and ga68, and check or
record their output.

Usage:
  run_examples.py [--ga68 XGCC --libdir LIBGA68] [--a68g A68G] [--java JAVA]
                  [--record] [-j N] [FILE ...]
  run_examples.py --extract DIR

Without FILE, all programs in book/examples (*.a68) are run.

Every program is run with a68j (backend/a68j-backend.jar) and, if --ga68
and --libdir are given, compiled and run with ga68 as well.  The output of
a program is its standard output; for a program that is expected to fail
(see below) it is the error message.  The output of a68j and of ga68 must
be the same (for a run-time error only a68j's message is recorded: the
messages of the two run-time systems differ).  It is compared with the recorded output NAME.out next to the
program, or, with --record, written there.  The recorded outputs are part
of the book: the typeset text shows them, so the book can be built without
the compilers.

A program NAME.a68 may have these files next to it:
  NAME.in    standard input for the program;
  NAME.cfg   lines "key: value":
               run: IMPLS              the implementations to run the program
                                       with, of ga68, a68j and a68g (default
                                       "a68j ga68"); for a feature that
                                       ga68 and a68j lack, "a68g" alone
               a68j-options: OPTIONS   extra options for a68j (-fpar-threads)
               modules: NAMES          module packets NAME.a68 next to the
                                       program, compiled before it (for
                                       ga68 to objects, for a68j to jars)
               c-files: NAMES          C sources next to the program, compiled
                                       with cc and linked with it (ga68
                                       only, for formal holes "nest C")
               files: NAMES            run the program in an empty temporary
                                       directory into which these files
                                       (next to the program) are copied
                                       first; "files: -" for none.  For
                                       programs that create files.
               ga68: no                do not run with ga68 (with a reason
                                       in a "reason:" line)
               expect: compile-error   the program must not compile
               expect: runtime-error   the program must stop with an error
                                       (exit status not 0)

Algol 68 Genie (--a68g) runs the program converted to UPPER stropping by
supper_to_upper.py.  An implementation that is not available (no --ga68,
no --a68g) is left out; a program none of whose implementations is
available is reported as skipped.

Modules: a program that accesses Genie_Transput or Texts gets the bundled
module; for ga68 its source in backend/lib is compiled once.

--extract DIR writes the listing of each program to DIR (same relative
path).  The listing is the whole program, or, if the program contains
lines "{ begin excerpt }" and "{ end excerpt }", only the lines between
them (several excerpts are separated by an empty line), with the common
indentation removed.  A named excerpt, between "{ begin excerpt NAME }"
and "{ end excerpt }", is written to a listing of its own, NAME after the
name of the program (json.types.a68 for the excerpt "types" of
json.a68), which the text shows with the macro programpart (preamble.tex);
several excerpts with the same name are joined as above.
"""
import argparse, concurrent.futures, os, re, shutil, subprocess, sys, tempfile

BOOK = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(BOOK)
EXAMPLES = os.path.join(BOOK, "examples")
JAR = os.path.join(ROOT, "backend", "a68j-backend.jar")
BUNDLED = {"Genie_Transput": "genietransput", "Texts": "texts"}
TIMEOUT = 120


def all_examples():
    out = []
    for d, _, files in os.walk(EXAMPLES):
        for f in files:
            # A module packet (it begins with "module") is compiled with the
            # program that names it in "modules:", not run on its own.
            if f.endswith(".a68") and not re.match(r"\s*module\b", open(os.path.join(d, f), encoding="utf-8").read()):
                out.append(os.path.join(d, f))
    return sorted(out)


def config(path):
    cfg = {}
    p = path[:-4] + ".cfg"
    if os.path.exists(p):
        for line in open(p, encoding="utf-8"):
            if ":" in line and not line.lstrip().startswith("#"):
                k, v = line.split(":", 1)
                cfg[k.strip()] = v.strip()
    return cfg


def stdin_of(path):
    p = path[:-4] + ".in"
    return open(p, "rb").read() if os.path.exists(p) else b""


def rundir(path, cfg):
    """The directory to run the program in: its own directory, or for a
    program with a "files" line a new temporary directory with those
    files."""
    if "files" not in cfg:
        return os.path.dirname(path)
    d = tempfile.mkdtemp(prefix="book-run-")
    for name in cfg["files"].split():
        if name != "-":
            shutil.copy(os.path.join(os.path.dirname(path), name), d)
    return d


def clean(text, path):
    """The text without the directory of the program and without the line
    the JVM prints for JAVA_TOOL_OPTIONS."""
    lines = [l for l in text.splitlines(True) if not l.startswith("Picked up JAVA_TOOL_OPTIONS")]
    text = "".join(lines)
    return text.replace(os.path.dirname(path) + os.sep, "")


def result(path, cfg, rc, out, err):
    """(ok, output): the output shown in the book, and whether the run
    behaved as the configuration expects."""
    expect = cfg.get("expect", "")
    if expect == "compile-error" or expect == "runtime-error":
        return rc != 0, clean(out, path) + clean(err, path)
    if rc != 0:
        return False, clean(out, path) + clean(err, path) + f"[exit status {rc}]\n"
    return True, clean(out, path)


def run_a68j(path, cfg, java):
    inc = []
    if cfg.get("modules"):
        lib = tempfile.mkdtemp(prefix="book-lib-")
        for m in cfg["modules"].split():
            src = os.path.join(os.path.dirname(path), m + ".a68")
            c = subprocess.run([java, "-jar", JAR, src, "-d", os.path.join(lib, "lib" + m + ".jar")],
                               capture_output=True, env=dict(os.environ, LC_ALL="C"))
            if c.returncode != 0:
                return False, clean(c.stderr.decode("utf-8", "replace"), path)
        inc = ["-I", lib]
    cmd = [java, "-jar", JAR] + cfg.get("a68j-options", "").split() + inc + ["-run", path]
    p = subprocess.run(cmd, input=stdin_of(path), capture_output=True, cwd=rundir(path, cfg),
                       timeout=TIMEOUT, env=dict(os.environ, LC_ALL="C"))
    return result(path, cfg, p.returncode, p.stdout.decode("utf-8", "replace"), p.stderr.decode("utf-8", "replace"))


class Ga68:
    def __init__(self, xgcc, libdir, work):
        self.xgcc, self.libdir, self.work = xgcc, libdir, work
        self.base = [xgcc, "-B" + os.path.dirname(xgcc), "-L" + libdir, "-fstropping=supper",
                     "-fno-diagnostics-show-caret", "-fdiagnostics-color=never"]
        self.env = dict(os.environ, LC_ALL="C", LD_LIBRARY_PATH=libdir)
        self.objects = {}

    def module(self, name):
        if name not in self.objects:
            src = os.path.join(ROOT, "backend", "lib", BUNDLED[name] + ".a68")
            obj = os.path.join(self.work, BUNDLED[name] + ".o")
            p = subprocess.run(self.base + ["-I" + self.work, "-c", src, "-o", obj], capture_output=True, env=self.env)
            if p.returncode != 0:
                sys.exit(f"cannot compile {src} with ga68:\n" + p.stderr.decode())
            self.objects[name] = obj
        return self.objects[name]

    def run(self, path, cfg):
        text = open(path, encoding="utf-8").read()
        objs = [self.module(m) for m in BUNDLED if re.search(r"\baccess\b[^;]*\b" + m + r"\b", text)]
        mdir = os.path.join(self.work, os.path.relpath(path, EXAMPLES).replace(os.sep, "_")[:-4] + ".mod")
        os.makedirs(mdir, exist_ok=True)
        for m in cfg.get("modules", "").split():
            o = os.path.join(mdir, m + ".o")
            c = subprocess.run(self.base + ["-I" + self.work, "-I" + mdir, "-c", m + ".a68", "-o", o],
                               capture_output=True, cwd=os.path.dirname(path), env=self.env)
            if c.returncode != 0:
                return result(path, cfg, c.returncode, "", c.stderr.decode("utf-8", "replace"))
            objs.append(o)
        for c_src in cfg.get("c-files", "").split():
            o = os.path.join(mdir, c_src[:-2] + ".o")
            c = subprocess.run(["cc", "-c", c_src, "-o", o], capture_output=True,
                               cwd=os.path.dirname(path), env=self.env)
            if c.returncode != 0:
                return result(path, cfg, c.returncode, "", c.stderr.decode("utf-8", "replace"))
            objs.append(o)
        exe = os.path.join(self.work, os.path.relpath(path, EXAMPLES).replace(os.sep, "_")[:-4])
        c = subprocess.run(self.base + ["-I" + self.work, "-I" + mdir, os.path.basename(path)] + objs + [os.path.join(self.libdir, "libga68.a"), "-lm", "-o", exe],
                           capture_output=True, cwd=os.path.dirname(path), env=self.env)
        if c.returncode != 0:
            return result(path, cfg, c.returncode, "", c.stderr.decode("utf-8", "replace"))
        if cfg.get("expect") == "compile-error":
            return False, "compiled, but a compile error was expected\n"
        p = subprocess.run([exe], input=stdin_of(path), capture_output=True, cwd=rundir(path, cfg),
                           timeout=TIMEOUT, env=self.env)
        return result(path, cfg, p.returncode, p.stdout.decode("utf-8", "replace"), p.stderr.decode("utf-8", "replace"))


def run_a68g(path, cfg, a68g, work):
    import supper_to_upper
    conv = os.path.join(work, os.path.relpath(path, EXAMPLES).replace(os.sep, "_"))
    open(conv, "w", encoding="utf-8").write(supper_to_upper.convert(open(path, encoding="utf-8").read()))
    p = subprocess.run([a68g, "--quiet", conv], input=stdin_of(path), capture_output=True, cwd=rundir(path, cfg),
                       timeout=TIMEOUT, env=dict(os.environ, LC_ALL="C"))
    return result(path, cfg, p.returncode, p.stdout.decode("utf-8", "replace"), p.stderr.decode("utf-8", "replace"))


EXCERPT = re.compile(r"\{ (begin|end) excerpt(?: ([A-Za-z0-9_-]+))? \}$")


def extract(path):
    """The listing of a program and its named excerpts: (listing, {name: text})."""
    lines = open(path, encoding="utf-8").read().splitlines()

    def join(parts):
        out = []
        for part in parts:
            ind = min((len(l) - len(l.lstrip()) for l in part if l.strip()), default=0)
            if out:
                out.append("")
            out += [l[ind:] for l in part]
        return "\n".join(out) + "\n"

    plain, named, cur, name = [], {}, None, None
    for l in lines:
        m = EXCERPT.match(l.strip())
        if m and m.group(1) == "begin":
            cur, name = [], m.group(2)
        elif m:
            if cur is not None:
                (named.setdefault(name, []) if name else plain).append(cur)
            cur = None
        elif cur is not None:
            cur.append(l)
    if plain:
        listing = join(plain)
    else:   # the whole program, without the marks of named excerpts
        listing = "\n".join(l for l in lines if not EXCERPT.match(l.strip())).rstrip("\n") + "\n"
    return listing, {k: join(v) for k, v in named.items()}


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--ga68")
    ap.add_argument("--libdir")
    ap.add_argument("--java", default="java")
    ap.add_argument("--a68g")
    ap.add_argument("--record", action="store_true")
    ap.add_argument("--extract", metavar="DIR")
    ap.add_argument("-j", type=int, default=os.cpu_count() or 1)
    ap.add_argument("files", nargs="*")
    a = ap.parse_args()
    files = [os.path.abspath(f) for f in a.files] or all_examples()

    if a.extract:
        if not a.files:   # the listings of module packets too
            files = sorted(os.path.join(d, f) for d, _, fs in os.walk(EXAMPLES) for f in fs if f.endswith(".a68"))
        for f in files:
            dst = os.path.join(a.extract, os.path.relpath(f, EXAMPLES))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            listing, named = extract(f)
            open(dst, "w", encoding="utf-8").write(listing)
            for k, text in named.items():
                open(dst[:-4] + "." + k + ".a68", "w", encoding="utf-8").write(text)
        return 0

    if bool(a.ga68) != bool(a.libdir):
        ap.error("--ga68 and --libdir go together")
    work = tempfile.mkdtemp(prefix="book-")
    ga68 = Ga68(os.path.abspath(a.ga68), os.path.abspath(a.libdir), work) if a.ga68 else None
    if ga68:
        for m in BUNDLED:  # compiled once, before the parallel runs
            ga68.module(m)

    def one(f):
        cfg = config(f)
        impls = cfg.get("run", "a68j ga68").split()
        if cfg.get("ga68") == "no" and "ga68" in impls:
            impls.remove("ga68")
        runners = {"a68j": lambda: run_a68j(f, cfg, a.java),
                   "ga68": (lambda: ga68.run(f, cfg)) if ga68 else None,
                   "a68g": (lambda: run_a68g(f, cfg, a.a68g, work)) if a.a68g else None}
        msgs, outs = [], []
        for impl in impls:
            if runners[impl] is None:
                continue
            ok, out = runners[impl]()
            if not ok:
                msgs.append(impl + ": " + out)
            else:
                outs.append((impl, out))
        if not msgs and not outs:
            return f, None
        if not msgs and cfg.get("expect") != "runtime-error":
            for impl, out in outs[1:]:
                if out != outs[0][1]:
                    msgs.append(f"{outs[0][0]} and {impl} differ:\n--- {outs[0][0]}\n" + outs[0][1] + f"--- {impl}\n" + out)
        out_j = outs[0][1] if outs else ""
        rec = f[:-4] + ".out"
        if not msgs:
            if a.record:
                open(rec, "w", encoding="utf-8").write(out_j)
            elif not os.path.exists(rec):
                msgs.append("no recorded output (run with --record)")
            elif open(rec, encoding="utf-8").read() != out_j:
                msgs.append("output differs from " + os.path.basename(rec) + ":\n" + out_j)
        return f, msgs

    failed = skipped = 0
    with concurrent.futures.ThreadPoolExecutor(a.j) as ex:
        for f, msgs in ex.map(one, files):
            name = os.path.relpath(f, EXAMPLES)
            if msgs is None:
                skipped += 1
                print(f"skip {name} (no implementation available)")
            elif msgs:
                failed += 1
                print(f"FAIL {name}\n  " + "\n  ".join(m.rstrip().replace("\n", "\n  ") for m in msgs))
            else:
                print(f"ok   {name}")
    print(f"{len(files) - failed - skipped} of {len(files)} examples ok, {skipped} skipped"
          + ("" if ga68 else ", without ga68") + ("" if a.a68g else ", without a68g"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
