#!/usr/bin/env python3
"""Summarise the Rosetta Code test run in tests/rosetta/results.tsv.

Usage: rosetta_report.py --dir tests/rosetta --native DIR [--native DIR2]
                         --jvm DIR [--jvm DIR2] [--notes NOTES.tsv]

For every solution in DIR/index.tsv one line: name, task, ga68 (what
the native ga68 run did), jvm (the result of run_jvm.py) and a note.
Later --native/--jvm directories take precedence (e.g. a rerun with a
longer time limit).  NOTES.tsv (name<TAB>note) explains the known
differences.  Prints a summary.
"""
import argparse, collections, json, os, re, sys


def load(dirs, name):
    r = None
    for d in dirs:
        p = os.path.join(d, name + ".json")
        if os.path.exists(p):
            r = json.load(open(p))
    return r


def first_error(cerr):
    for line in cerr.splitlines():
        m = re.search(r"(?:error|internal compiler error): (.*)", line)
        if m:
            return m.group(0)[:120]
    return cerr.strip().splitlines()[0][:120] if cerr.strip() else "?"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", required=True)
    ap.add_argument("--native", action="append", required=True)
    ap.add_argument("--jvm", action="append", required=True)
    ap.add_argument("--notes")
    a = ap.parse_args()
    notes = {}
    if a.notes and os.path.exists(a.notes):
        for line in open(a.notes, encoding="utf-8"):
            if line.strip() and not line.startswith("#"):
                n, _, t = line.rstrip("\n").partition("\t")
                notes[n] = t
    rows, count = [], collections.Counter()
    for line in list(open(os.path.join(a.dir, "index.tsv")))[1:]:
        name, task, status = line.rstrip("\n").split("\t")
        if status != "prepared":
            ga68, jvm = status, "-"
        else:
            nat = load(a.native, name)
            if nat is None:
                ga68, jvm = "not run", "-"
            elif nat["compile"] != 0:
                ga68, jvm = "compile error: " + first_error(nat["cerr"]), "-"
            elif nat.get("nondeterministic"):
                ga68, jvm = "nondeterministic output", "-"
            elif nat.get("exit") == "timeout":
                ga68, jvm = "timeout", "-"
            else:
                ga68 = "exit %s" % nat["exit"]
                j = load(a.jvm, name)
                jvm = "not run" if j is None else j["status"] + (": " + j["reason"] if j["reason"] else "")
        key = "same as ga68" if jvm.startswith("pass") else "same as ga68 up to rounding" if jvm.startswith("approx") else (jvm.split(":")[0] if jvm != "-" else
              ("ga68 rejects it" if ga68.startswith(("compile error", "missing include")) else ga68))
        count[key] += 1
        # Paths of the machine the tests ran on are not of interest.
        ga68, jvm = (re.sub(r"/\S*/(tests/rosetta/)", r"\1", x) for x in (ga68, jvm))
        rows.append((name, task, ga68, jvm, notes.get(name, "")))
    with open(os.path.join(a.dir, "results.tsv"), "w", encoding="utf-8") as f:
        f.write("name\ttask\tga68\tjvm\tnote\n")
        for r in rows:
            f.write("\t".join(x.replace("\t", " ").replace("\n", " ") for x in r) + "\n")
    for k, v in count.most_common():
        print(f"{v:5d}  {k}")


if __name__ == "__main__":
    sys.exit(main())
