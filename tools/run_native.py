#!/usr/bin/env python3
"""Compile and run Algol 68 test programs with ga68 and record the results
(exit status and output), as the reference for the JVM backend.

Usage: run_native.py --ga68 xgcc --libdir LIBGA68 --out DIR [-j N]
                     [--timeout SEC] [--runs N] [--stack KIB|unlimited]
                     [--default-opts OPTS] FILES...
Writes DIR/<name>.json with compile status, exit code, stdout, stderr and
run time.  Programs read an empty standard input.  --runs N runs each
program N times and marks it "nondeterministic" if the results differ;
--default-opts applies to files without a { dg-options } line.
"""
import argparse, json, os, re, shutil, subprocess, sys, tempfile, time
import modtest
from concurrent.futures import ThreadPoolExecutor

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

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ga68", required=True)
    ap.add_argument("--libdir", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("-j", type=int, default=os.cpu_count())
    ap.add_argument("--timeout", type=float, default=20)
    ap.add_argument("--stack", default="",
                    help="stack size limit for the programs in KiB, or 'unlimited' (ga68 allocates LOC rows on the stack)")
    ap.add_argument("--runs", type=int, default=1,
                    help="run each program N times; differing results mark it nondeterministic")
    ap.add_argument("files", nargs="+")
    ap.add_argument("--sequential-runs", action="store_true",
                    help="compile all programs first (in parallel, -j), then run them one after the other, "
                         "so that the times are not distorted by parallel processes")
    ap.add_argument("--cflags", default="",
                    help="extra options for ga68, e.g. -O2 (for time comparisons, with --sequential-runs)")
    ap.add_argument("--default-opts", default="",
                    help="options for programs without a dg-options line, e.g. -fstropping=upper")
    a = ap.parse_args()
    preexec = None
    if a.stack:
        import resource
        lim = resource.RLIM_INFINITY if a.stack == "unlimited" else int(a.stack) * 1024
        preexec = lambda: resource.setrlimit(resource.RLIMIT_STACK, (lim, lim))
    DEFAULT_OPTS[:] = a.default_opts.split()
    os.makedirs(a.out, exist_ok=True)
    bdir = os.path.dirname(os.path.abspath(a.ga68))
    env = dict(os.environ, LC_ALL="C", LD_LIBRARY_PATH=a.libdir)

    def one(f):
        f = os.path.abspath(f)
        name = os.path.splitext(os.path.basename(f))[0]
        exe = os.path.join(a.out, name + ".exe")
        # Module packets ({ dg-modules }): compiled to objects in OUT/<name>.mod.
        mods, objs, merr = modtest.modules_for(f), [], ""
        mdir = os.path.join(a.out, name + ".mod")
        if mods:
            os.makedirs(mdir, exist_ok=True)
        for m in mods:
            o = os.path.join(mdir, m + ".o")
            msrc = modtest.module_source(f, m)
            mc = subprocess.run([a.ga68, "-B" + bdir, "-L" + a.libdir, "-I" + mdir] + a.cflags.split() + opts_for(msrc) + ["-c", msrc, "-o", o],
                                capture_output=True, env=env)
            merr += mc.stderr.decode("latin-1")
            objs.append(o)
        extra = (["-I" + mdir] + modtest.module_map_opts(f)) if mods else []
        c = subprocess.run([a.ga68, "-B" + bdir, "-L" + a.libdir] + a.cflags.split() + opts_for(f) + extra + [f] + objs + ["-lga68", "-lm", "-o", exe],
                           capture_output=True, env=env)
        r = {"file": f, "compile": c.returncode, "cerr": merr + c.stderr.decode("latin-1")}
        if c.returncode == 0 and a.sequential_runs:
            return r
        return execute(r, exe, name)

    def execute(r, exe, name):
        if r["compile"] == 0:
            seen = []
            for _ in range(a.runs):
                # Every run in a new empty directory: programs may create
                # files, and the JVM run gets the same (run_jvm.py).
                wd = tempfile.mkdtemp(prefix="a68-run-")
                try:
                    t0 = time.monotonic()
                    p = subprocess.run([exe], capture_output=True, env=env, timeout=a.timeout, cwd=wd,
                                       stdin=subprocess.DEVNULL, preexec_fn=preexec)
                    res = dict(exit=p.returncode, stdout=p.stdout.decode("latin-1"), stderr=p.stderr.decode("latin-1"))
                    r["time"] = round(time.monotonic() - t0, 3)
                except subprocess.TimeoutExpired:
                    res = dict(exit="timeout", stdout="", stderr="")
                finally:
                    shutil.rmtree(wd, ignore_errors=True)
                seen.append(res)
                if res["exit"] == "timeout":
                    break
            r.update(seen[0])
            if any(x != seen[0] for x in seen[1:]):
                r["nondeterministic"] = True
            os.remove(exe)
        json.dump(r, open(os.path.join(a.out, name + ".json"), "w"))
        return r

    # Module packets are compiled for the programs that use them, not run.
    a.files = [f for f in a.files if not modtest.is_packet(f)]
    with ThreadPoolExecutor(a.j) as ex:
        rs = list(ex.map(one, a.files))
    if a.sequential_runs:
        def base(r):
            return os.path.splitext(os.path.basename(r["file"]))[0]
        rs = [execute(r, os.path.join(a.out, base(r) + ".exe"), base(r)) if r["compile"] == 0 else r for r in rs]
    ok = sum(1 for r in rs if r["compile"] == 0 and r.get("exit") == 0)
    print(f"{len(rs)} programs, {ok} compiled and exited with status 0")

if __name__ == "__main__":
    sys.exit(main())
