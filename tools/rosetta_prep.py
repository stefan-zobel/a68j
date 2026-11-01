#!/usr/bin/env python3
"""Prepare the ALGOL 68 solutions of Rosetta Code for ga68 and a68j.

Usage: rosetta_prep.py --data ROSETTACODEDATA --out tests/rosetta

ROSETTACODEDATA is a checkout of https://github.com/acmeism/RosettaCodeData
(only Task/*/ALGOL-68/*.alg is needed).  For every solution this writes

  OUT/orig/<name>.a68   the solution exactly as on Rosetta Code
  OUT/<name>.a68        the program prepared for ga68, in SUPPER stropping
                        (the GNU notation, the default of ga68):

    { dg-modules "rcprelude" }
    { origin, licence }
    access Rc_Prelude (
    <solution>
    )

The solutions are written for Algol 68 Genie, so the preparation makes
these changes, and only these:

  * the program runs inside an access clause of the module Rc_Prelude
    (rcprelude.a68), which provides print, newline, stand out, ...;
  * an apostrophe in a string denotation is written '' (in ga68 the
    apostrophe starts a string break);
  * pragmats are removed (PR precision=... PR and the like), except
    PR read "file" PR and PR include "file" PR, which become
    PR include "file" PR when OUT/include/<file> exists.  Programs that
    read a file that is not there are not written;
  * the program is converted from UPPER to SUPPER stropping by
    a68supper.py (begin, int, mode Node, max_sequence_length, { ... }).
    The layout stays: line n of the solution is line n + 8 of the file.

OUT/index.tsv lists every solution with its task and what happened to it.
"""
import argparse, os, re, sys, urllib.parse
import a68supper

HEADER = """{{ dg-modules "rcprelude" }}
{{ Rosetta Code, task "{task}", ALGOL 68 solution {file}.
  https://rosettacode.org/wiki/{url}#ALGOL_68
  The text of Rosetta Code is available under the GNU Free Documentation
  License 1.3 (see README.md).  The solution is unchanged in orig/{name}.a68;
  this copy was prepared for ga68 and converted to SUPPER stropping by
  tools/rosetta_prep.py. }}
ACCESS RC_PRELUDE (
"""
FOOTER = "\n)\n"


def bold_word(text, i):
    """The UPPER-stropped bold word starting at i, or ''."""
    if i > 0 and (text[i - 1].isalnum() or text[i - 1] == "_"):
        return ""
    m = re.match(r"[A-Z][A-Z0-9]*", text[i:])
    return m.group(0) if m else ""


def prepare(text, incdir):
    """Return (prepared text, list of missing include files)."""
    out, missing, i, n = [], [], 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            # String denotation; "" is a quote inside it.
            j = i + 1
            buf = ['"']
            while j < n:
                if text[j] == '"':
                    if j + 1 < n and text[j + 1] == '"':
                        buf.append('""'); j += 2; continue
                    break
                buf.append("''" if text[j] == "'" else text[j])
                j += 1
            buf.append('"')
            out.append("".join(buf)); i = j + 1
            continue
        if c == "#":
            j = text.find("#", i + 1)
            j = n if j < 0 else j + 1
            out.append(text[i:j]); i = j
            continue
        w = bold_word(text, i) if c.isupper() else ""
        if w in ("CO", "COMMENT"):
            m = re.compile(r"(?<![A-Za-z0-9_])" + w + r"(?![A-Z0-9])").search(text, i + len(w))
            j = n if not m else m.end()
            out.append(text[i:j]); i = j
            continue
        if w in ("PR", "PRAGMAT"):
            m = re.compile(r"(?<![A-Za-z0-9_])" + w + r"(?![A-Z0-9])").search(text, i + len(w))
            j = n if not m else m.end()
            body = text[i + len(w): m.start() if m else n]
            f = re.match(r'\s*(?:read|READ|include|INCLUDE)\s*"([^"]*)"\s*$', body)
            if f:
                if os.path.exists(os.path.join(incdir, f.group(1))):
                    out.append('PR include "include/%s" PR' % f.group(1))
                else:
                    missing.append(f.group(1))
            else:
                # Keep the line structure of the removed pragmat.
                out.append("\n" * text[i:j].count("\n") or " ")
            i = j
            continue
        if w:
            out.append(w); i += len(w)
            continue
        out.append(c); i += 1
    return "".join(out), missing


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    tdir = os.path.join(a.data, "Task")
    odir = os.path.join(a.out, "orig")
    incdir = os.path.join(a.out, "include")
    os.makedirs(odir, exist_ok=True)
    index = []
    for task in sorted(os.listdir(tdir)):
        sdir = os.path.join(tdir, task, "ALGOL-68")
        if not os.path.isdir(sdir):
            continue
        for fn in sorted(os.listdir(sdir)):
            if not fn.endswith(".alg"):
                continue
            name = fn[:-4]
            raw = open(os.path.join(sdir, fn), "rb").read()
            open(os.path.join(odir, name + ".a68"), "wb").write(raw)
            text = raw.decode("latin-1")
            body, missing = prepare(text, incdir)
            if missing:
                index.append((name, task, "missing include " + " ".join(missing)))
                continue
            url = task.replace("-", "_")
            meta = os.path.join(tdir, task, "00-META.yaml")
            if os.path.exists(meta):
                m = re.search(r"^from:\s*https?://rosettacode.org/wiki/(\S+)", open(meta).read(), re.M)
                if m:
                    url = m.group(1)
            url = urllib.parse.quote(urllib.parse.unquote(url), safe="/_()',.:!-*")
            task_title = urllib.parse.unquote(url).replace("_", " ").encode("ascii", "replace").decode()
            hdr = HEADER.format(task=task_title, file=fn, url=url, name=name)
            try:
                prog = a68supper.to_supper(hdr + body.rstrip("\n") + FOOTER)
            except a68supper.ConvertError as e:
                index.append((name, task, "not converted to SUPPER: " + str(e)))
                continue
            with open(os.path.join(a.out, name + ".a68"), "w", encoding="latin-1", newline="") as f:
                f.write(prog)
            index.append((name, task, "prepared"))
    with open(os.path.join(a.out, "index.tsv"), "w") as f:
        f.write("name\ttask\tstatus\n")
        for row in index:
            f.write("\t".join(row) + "\n")
    print(f"{len(index)} solutions, {sum(1 for r in index if r[2] == 'prepared')} prepared")


if __name__ == "__main__":
    sys.exit(main())
