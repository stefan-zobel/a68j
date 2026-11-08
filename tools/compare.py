#!/usr/bin/env python3
"""Compare the token stream (--mode tokens) , the syntax tree (--mode syntax) or the tree with modes and tags after semantic analysis (--mode semantic) of the Java port with ga68 (patched with
tools/ga68-token-dump.patch) for every .a68 file below a directory.

Usage: compare.py --ga68 path/to/xgcc --jar a68j.jar --srcdir gcc/testsuite DIR...

Options for each test are taken from its dg-options line, like the ga68
testsuite does. A test passes when both tools produce the same token dump,
or when both reject the file with a scan error at the same location and with
the same message.
"""
import argparse, os, re, subprocess, sys

def options_for(path, srcdir):
    text = open(path, encoding="latin-1").read()
    m = re.search(r'dg-options "([^"]*)"', text)
    opts = m.group(1).split() if m else []
    keep = []
    for o in opts:
        o = o.replace("$srcdir", srcdir)
        # Only options that the scanner depends on; mutated (invalid) values
        # are dropped, so that both tools see the same valid options.
        if o in ("-fstropping=upper", "-fstropping=supper", "-std=algol68", "-std=gnu68",
                 "-fbrackets") or (o.startswith("-I") and "\x00" not in o):
            keep.append(o)
    return keep

def first_error(stderr):
    for line in stderr.splitlines():
        if ": error: " in line or ": fatal error: " in line:
            return line.strip()
    return None

def norm_err(e):
    # Normalise GCC's quoting of %<..%>.
    if not e:
        return e
    e = e.replace("‘", "'").replace("’", "'")
    # Errors without a location are prefixed with the program name.
    return re.sub(r"^(a681|a68j): ", "<program>: ", e)

DIAG = re.compile(r": (error|warning|fatal error|note): ")

def diag_lines(stderr):
    """All diagnostic lines, normalised (syntax mode compares all of them)."""
    return [norm_err(l.strip()) for l in stderr.splitlines() if DIAG.search(l)]

def run(cmd, env=None):
    p = subprocess.run(cmd, capture_output=True, env=env)
    return p.returncode, p.stdout.decode("latin-1"), p.stderr.decode("latin-1")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ga68", required=True)
    ap.add_argument("--jar", required=True)
    ap.add_argument("--srcdir", required=True)
    ap.add_argument("--show", type=int, default=5)
    ap.add_argument("--extra", default="", help="additional options for both tools, e.g. warnings")
    ap.add_argument("-j", "--jobs", type=int, default=os.cpu_count())
    ap.add_argument("--mode", choices=("tokens", "syntax", "semantic"), default="tokens")
    ap.add_argument("--libdir", help="directory with libga68 (.a68_exports), passed as -L")
    ap.add_argument("dirs", nargs="+")
    a = ap.parse_args()

    syntax = a.mode in ("syntax", "semantic")
    env = dict(os.environ, LC_ALL="C")
    env["A68J_DUMP_" + a.mode.upper()] = "1"
    lopts = ["-L" + a.libdir] if a.libdir else []
    jmain = ["java", "-cp", a.jar, "a68j.tools.SyntaxDump"] if syntax else ["java", "-jar", a.jar]
    if a.mode == "semantic":
        jmain.append("-semantic")
    jenv = dict(os.environ, JAVA_TOOL_OPTIONS="")
    bdir = os.path.dirname(os.path.abspath(a.ga68))
    files = []
    for d in a.dirs:
        if os.path.isfile(d):
            files.append(os.path.abspath(d))
        else:
            files += [os.path.abspath(os.path.join(r, f))
                      for r, _, fs in os.walk(d) for f in fs if f.endswith(".a68")]
    files.sort()
    ok = tokens = errors = 0
    fails = []
    def both(f):
        opts = options_for(f, a.srcdir) + a.extra.split()
        gc, gout, gerr = run([a.ga68, "-B" + bdir, "-fsyntax-only", "-fdiagnostics-column-unit=byte",
                              "-fdiagnostics-plain-output"] + lopts + opts + [f], env)
        jc, jout, jerr = run(jmain + lopts + opts + [f], jenv)
        return f, opts, gc, gout, gerr, jc, jout, jerr
    from concurrent.futures import ThreadPoolExecutor
    with ThreadPoolExecutor(a.jobs) as ex:
        results = list(ex.map(both, files))
    for f, opts, gc, gout, gerr, jc, jout, jerr in results:
        if syntax:
            if (gc == 0) == (jc == 0) and gout == jout and diag_lines(gerr) == diag_lines(jerr):
                ok += 1; tokens += gout.count("\n"); errors += gc != 0
            else:
                fails.append((f, opts, gc, jc, gout, jout, gerr, jerr))
        elif gc == 0 and jc == 0 and gout == jout:
            ok += 1; tokens += gout.count("\n")
        elif gc != 0 and jc != 0 and norm_err(first_error(gerr)) == norm_err(first_error(jerr)):
            ok += 1; errors += 1
        else:
            fails.append((f, opts, gc, jc, gout, jout, gerr, jerr))
    what = "dump lines" if syntax else "tokens"
    print(f"{ok}/{len(files)} files identical ({tokens} {what} compared, {errors} identical {'error outputs' if syntax else 'scan errors'})")
    for f, opts, gc, jc, gout, jout, gerr, jerr in fails[: a.show]:
        print("=" * 70)
        print("FAIL", f, " ".join(opts), "ga68 exit", gc, "java exit", jc)
        if syntax and diag_lines(gerr) != diag_lines(jerr):
            gd, jd = diag_lines(gerr), diag_lines(jerr)
            for x, y in zip(gd + [None] * len(jd), jd + [None] * len(gd)):
                if x != y:
                    print(f"  first differing diagnostic:\n    ga68: {x}\n    java: {y}")
                    break
        elif gc == 0 and jc == 0 or syntax:
            gl, jl = gout.splitlines(), jout.splitlines()
            for i, (x, y) in enumerate(zip(gl, jl)):
                if x != y:
                    print(f"  first difference at line {i + 1}:\n    ga68: {x}\n    java: {y}")
                    break
            else:
                print(f"  token counts differ: ga68 {len(gl)} java {len(jl)}")
        else:
            print("  ga68:", norm_err(first_error(gerr)) or gerr.strip()[:300])
            print("  java:", norm_err(first_error(jerr)) or jerr.strip()[:300])
    print(f"{len(fails)} failing")
    return 0 if not fails else 1

if __name__ == "__main__":
    sys.exit(main())
