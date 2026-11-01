#!/usr/bin/env python3
"""Run Algol 68 test programs on the JVM backend and compare them with the
results of the native ga68 executables (recorded by run_native.py).

Usage: run_jvm.py --native DIR [--java JAVA] [--jar a68j-backend.jar]
                  [--out DIR] [-j N] [-v] [--timeout SEC] [--jit c1|tiered]
                  [--rel-eps E] [--abs-eps E] [--aot auto|none|FILE] [--modules-as-jars]
                  [--default-opts OPTS] FILES...

For each FILE with a native result DIR/<name>.json whose compilation
succeeded, compiles and runs FILE with `java -jar a68j-backend.jar -run`
(same -fstropping/-std/-fbrackets options as the native run) and compares:

  * standard output (exactly),
  * the exit status; a native abort (signal 6, status -6) corresponds to
    the JVM status 134,
  * the runtime error line (first line of stderr) when the native program
    failed with a runtime error.

Each program is classified as one of
  pass         same output and status
  approx       same status, output equal except for floating point numbers
               that agree within the tolerance (--rel-eps/--abs-eps); the
               reason gives the largest deviation.  Used for last-digit
               differences between the C library (ga68) and java.lang.Math
  unsupported  the backend reported a construct it does not support yet
  frontend     the a68j front end rejected the program (should not happen)
  crash        the JVM failed (verifier error, Java exception, ...)
  timeout      did not finish in time
  fail         ran but differs from the native result
and a summary with the most frequent unsupported constructs is printed.
Details per program go to OUT/<name>.json when --out is given.
"""
import argparse, collections, json, os, re, shutil, subprocess, sys, tempfile, time
import modtest
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)


DEFAULT_OPTS = []


def opts_for(path):
    """Options from a { dg-options } line, or --default-opts without one."""
    text = open(path, encoding="latin-1").read()
    m = re.search(r'dg-options "([^"]*)"', text)
    if not m:
        return list(DEFAULT_OPTS)
    keep = []
    for o in (m.group(1).split() if m else []):
        if o in ("-fstropping=upper", "-fstropping=supper", "-std=algol68", "-std=gnu68", "-fbrackets"):
            keep.append(o)
    return keep


# A number as written by the transput: optional sign, digits, optional
# fraction and exponent.  Only numbers with a fraction or an exponent are
# compared with a tolerance; integers must be identical.
NUM = re.compile(r"[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")
EPS = {"rel": 1e-4, "abs": 1e-6}


def approx_equal(a, b):
    """None if a and b differ in more than floating point numbers within the
    tolerance, otherwise the largest deviation found (as text)."""
    ta, tb = NUM.split(a), NUM.split(b)
    na, nb = NUM.findall(a), NUM.findall(b)
    if len(na) != len(nb):
        return None
    # The text between the numbers must be identical, except for blanks
    # (a number with one more digit before the point shifts the layout).
    if [t.replace(" ", "") for t in ta] != [t.replace(" ", "") for t in tb]:
        return None
    worst, wtxt = 0.0, ""
    for x, y in zip(na, nb):
        if x == y:
            continue
        if not any(c in x + y for c in ".eE"):
            return None
        fx, fy = float(x), float(y)
        d = abs(fx - fy)
        if d > EPS["abs"] + EPS["rel"] * max(abs(fx), abs(fy)):
            return None
        rel = d / max(abs(fx), abs(fy))
        if rel >= worst:
            worst, wtxt = rel, f"{y} (native {x})"
    return wtxt


def classify(nat, rc, out, err):
    """Return (status, reason)."""
    if rc == "timeout":
        return "timeout", ""
    m = re.search(r"a68j backend: (.*) not supported yet", err)
    if rc == 3 and m:
        return "unsupported", m.group(1)
    if rc == 1 and re.search(r": (error|fatal error): ", err):
        return "frontend", err.strip().splitlines()[0]
    if re.search(r"^(Exception in thread|Error: |java\.lang\.)", err, re.M) or "at java.base/" in err:
        lines = [l for l in err.splitlines() if l.strip()]
        first = next((l for l in lines if "Exception" in l or "Error" in l), lines[0] if lines else "")
        first = re.sub(r"\s+", " ", first)[:160]
        return "crash", first
    nexit = nat.get("exit")
    # A native death by signal N (status -N) corresponds to the JVM status 128 + N
    # (abort: 134, SIGFPE: 136).
    want_rc = 128 - nexit if isinstance(nexit, int) and nexit < 0 else nexit
    reasons = []
    if rc != want_rc:
        reasons.append(f"exit {rc} (native {nexit})")
    approx = None
    if out != nat.get("stdout", ""):
        approx = approx_equal(nat.get("stdout", ""), out)
        if approx is None:
            reasons.append("stdout differs")
    if nexit == -6:
        nl = (nat.get("stderr", "").splitlines() or [""])[0]
        jl = (err.splitlines() or [""])[0]
        if nl != jl:
            reasons.append(f"runtime error {jl!r} (native {nl!r})")
    if reasons:
        return "fail", "; ".join(reasons)
    if approx is not None:
        return "approx", "largest deviation " + approx
    return "pass", ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--java", default=os.environ.get("JDK25", "/usr/lib/jvm/java-25-openjdk-amd64") + "/bin/java")
    ap.add_argument("--jar", default=os.path.join(ROOT, "backend", "a68j-backend.jar"))
    ap.add_argument("--native", required=True,
                    help="directory with the results of run_native.py")
    ap.add_argument("--out")
    ap.add_argument("--timeout", type=float, default=30)
    ap.add_argument("-j", type=int, default=os.cpu_count())
    ap.add_argument("-v", action="store_true", help="print every program that does not pass")
    ap.add_argument("files", nargs="+")
    ap.add_argument("--jit", choices=("c1", "tiered"), default="c1",
                    help="c1: only the client compiler (fast start, the default for the test suites); "
                         "tiered: the full JIT (C2), for long-running programs and time comparisons")
    ap.add_argument("--rel-eps", type=float, default=EPS["rel"],
                    help="relative tolerance for floating point numbers in the output")
    ap.add_argument("--abs-eps", type=float, default=EPS["abs"],
                    help="absolute tolerance (for numbers close to 0)")
    ap.add_argument("--aot", default="auto",
                    help="AOT cache for the JVM (-XX:AOTCache): a file, 'none', or 'auto' (the default): "
                         "the .aot file next to the jar (backend/build.sh makes a68j-backend.aot) if it exists")
    ap.add_argument("--modules-as-jars", action="store_true",
                    help="compile each module packet X of a multi-packet test into a library jar libX.jar "
                         "(-d libX.jar) instead of X.m68 and a class directory")
    ap.add_argument("--default-opts", default="",
                    help="options for programs without a dg-options line, e.g. -fstropping=upper")
    a = ap.parse_args()
    DEFAULT_OPTS[:] = a.default_opts.split()
    EPS["rel"], EPS["abs"] = a.rel_eps, a.abs_eps
    a.jar = os.path.abspath(a.jar)
    aot = os.path.splitext(a.jar)[0] + ".aot" if a.aot == "auto" else (None if a.aot == "none" else a.aot)
    aot_opts = ["-XX:AOTCache=" + os.path.abspath(aot)] if aot and os.path.exists(aot) else []
    if a.out:
        os.makedirs(a.out, exist_ok=True)
    env = dict(os.environ, LC_ALL="C")
    env.pop("JAVA_TOOL_OPTIONS", None)

    def one(f):
        f = os.path.abspath(f)
        name = os.path.splitext(os.path.basename(f))[0]
        npath = os.path.join(a.native, name + ".json")
        if not os.path.exists(npath):
            return None
        nat = json.load(open(npath))
        if nat.get("compile") != 0 or nat.get("exit") == "timeout" or nat.get("nondeterministic"):
            return None
        jvm = [a.java, "-XX:+UseSerialGC"] + (["-XX:TieredStopAtLevel=1"] if a.jit == "c1" else []) + aot_opts
        cmd = jvm + ["-jar", a.jar] + opts_for(f) + ["-run", f]
        # Module packets ({ dg-modules }): compiled to classes and .m68 export
        # data in a temporary directory, which goes on the class path.  The
        # bundled modules (modtest.BUNDLED) come with the jar.
        mods, mdir = [m for m in modtest.modules_for(f) if m not in modtest.BUNDLED], None
        if mods:
            mdir = tempfile.mkdtemp(prefix="a68j-mod-")
            for m in mods:
                src = modtest.module_source(f, m)
                dest = os.path.join(mdir, "lib" + m + ".jar") if a.modules_as_jars else mdir
                c = subprocess.run(jvm + ["-jar", a.jar, "-I" + mdir] + opts_for(src) + [src, "-d", dest],
                                   capture_output=True, env=env, timeout=a.timeout)
                if c.returncode != 0:
                    err = c.stderr.decode("latin-1")
                    status, reason = classify(nat, c.returncode, "", err)
                    shutil.rmtree(mdir, ignore_errors=True)
                    r = {"file": f, "status": status if status != "fail" else "crash", "reason": m + ": " + reason, "exit": c.returncode, "stdout": "", "stderr": err}
                    if a.out:
                        json.dump(r, open(os.path.join(a.out, name + ".json"), "w"))
                    return r
            if a.modules_as_jars:
                # The library jars are found through -I and put on the class path by -run.
                cmd = jvm + ["-jar", a.jar, "-I" + mdir] + modtest.module_map_opts(f) + opts_for(f) + ["-run", f]
            else:
                cmd = jvm + ["-cp", a.jar + os.pathsep + mdir, "a68j.jvm.Compile", "-I" + mdir] + modtest.module_map_opts(f) + opts_for(f) + ["-run", f]
        t0 = time.monotonic()
        try:
            p = subprocess.run(cmd, capture_output=True, env=env, timeout=a.timeout,
                               stdin=subprocess.DEVNULL, cwd=os.path.dirname(os.path.abspath(f)))
            rc, out, err = p.returncode, p.stdout.decode("latin-1"), p.stderr.decode("latin-1")
        except subprocess.TimeoutExpired:
            rc, out, err = "timeout", "", ""
        if mdir:
            shutil.rmtree(mdir, ignore_errors=True)
        status, reason = classify(nat, rc, out, err)
        r = {"file": f, "status": status, "reason": reason, "exit": rc, "stdout": out, "stderr": err,
             "time": round(time.monotonic() - t0, 3)}
        if a.out:
            json.dump(r, open(os.path.join(a.out, name + ".json"), "w"))
        return r

    # Module packets are compiled for the programs that use them, not run.
    a.files = [f for f in a.files if not modtest.is_packet(f)]
    with ThreadPoolExecutor(a.j) as ex:
        rs = [r for r in ex.map(one, a.files) if r is not None]
    counts = collections.Counter(r["status"] for r in rs)
    order = ["pass", "approx", "fail", "unsupported", "crash", "timeout", "frontend"]
    print(f"{len(rs)} programs: " + ", ".join(f"{counts[k]} {k}" for k in order if counts[k]))
    unsup = collections.Counter(r["reason"] for r in rs if r["status"] == "unsupported")
    if unsup:
        print("most frequent unsupported constructs:")
        for what, n in unsup.most_common(25):
            print(f"  {n:4d}  {what}")
    crashes = collections.Counter(r["reason"] for r in rs if r["status"] == "crash")
    if crashes:
        print("crashes:")
        for what, n in crashes.most_common(15):
            print(f"  {n:4d}  {what}")
    if a.v:
        for r in sorted(rs, key=lambda r: r["file"]):
            if r["status"] != "pass":
                print(f"{r['status']:12s} {os.path.basename(r['file'])}: {r['reason']}")
    return 0 if counts["pass"] + counts["approx"] == len(rs) else 1


if __name__ == "__main__":
    sys.exit(main())
