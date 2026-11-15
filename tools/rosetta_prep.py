#!/usr/bin/env python3
"""Prepare the ALGOL 68 solutions of Rosetta Code for ga68 and a68j.

Usage: rosetta_prep.py --data ROSETTACODEDATA --out tests/rosetta [--java JAVA] [--jar JAR]
       rosetta_prep.py --again [--java JAVA] [--jar JAR] FILE ...

ROSETTACODEDATA is a checkout of https://github.com/acmeism/RosettaCodeData
(only Task/*/ALGOL-68/*.alg is needed).  For every solution this writes

  OUT/orig/<name>.a68   the solution exactly as on Rosetta Code
  OUT/<name>.a68        the program prepared for ga68, in SUPPER stropping
                        (the GNU notation, the default of ga68):

    { dg-modules "genietransput" }
    { origin, licence }
    access Genie_Transput (
    <solution>
    )

The solutions are written for Algol 68 Genie, so the preparation makes
these changes, and only these:

  * the program runs inside an access clause of the module Genie_Transput
    (backend/lib/genietransput.a68, bundled with a68j), which provides
    print, newline, stand out, ...;
  * an apostrophe in a string denotation is written '' (in ga68 the
    apostrophe starts a string break);
  * pragmats are removed (PR precision=... PR and the like), except
    PR read "file" PR and PR include "file" PR, which become
    PR include "file" PR when OUT/include/<file> exists.  Programs that
    read a file that is not there are not written;
  * a program with formatted transput ($ ... $, FORMAT, printf ...) is
    given to the format rewrite tool (tools/formats/rewrite.a68, run with
    a68j: --java, default $JDK25/bin/java or java, and --jar, default
    backend/a68j-backend.jar).  It turns the format texts into calls of
    the module Genie_Formats (backend/lib/genieformats.a68), which the
    program then accesses, and the first line becomes
    { dg-modules "genietransput genieformats" }.  A program that the tool
    rejects (as Algol 68 Genie does) is kept as it is;
  * the program is converted from UPPER to SUPPER stropping by
    a68supper.py (begin, int, mode Node, max_sequence_length, { ... }).
    This comes last, so that the units inside format texts are converted
    as well.  The layout stays: line n of the solution is line n + 8 of
    the file.

--again prepares the given prepared files again, in place, from
orig/<name>.a68 next to them, with the task and URL of their headers (for
example after a change of the preparation, without RosettaCodeData).

OUT/index.tsv lists every solution with its task and what happened to it.
"""
import argparse, os, re, subprocess, sys, tempfile, urllib.parse
import a68supper

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

HEADER = """{{ dg-modules "genietransput" }}
{{ Rosetta Code, task "{task}", ALGOL 68 solution {file}.
  https://rosettacode.org/wiki/{url}#ALGOL_68
  The text of Rosetta Code is available under the GNU Free Documentation
  License 1.3 (see README.md).  The solution is unchanged in orig/{name}.a68;
  this copy was prepared for ga68 and converted to SUPPER stropping by
  tools/rosetta_prep.py. }}
ACCESS GENIE_TRANSPUT (
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


class FormatRewriter:
    """The format rewrite tool, compiled once with a68j."""

    # A prefilter: the tool decides, but only programs that contain one of
    # these can use formats.
    CANDIDATE = re.compile(r"\$|\bFORMAT\b|\b(?:printf|putf|readf|getf|writef)\b")

    def __init__(self, java, jar):
        self.java, self.jar, self.classes = java, jar, None

    def rewrite(self, prog):
        """(the program in UPPER stropping with its format texts rewritten,
        None), or (None, error)."""
        if not self.CANDIDATE.search(prog):
            return prog, None
        if self.classes is None:
            self.classes = tempfile.mkdtemp(prefix="rosetta-formats-")
            tool = os.path.join(HERE, "formats")
            c = subprocess.run([self.java, "-jar", self.jar, "-fstropping=upper", "-I", tool,
                                "-d", self.classes, os.path.join(tool, "rewrite-a68j.a68")], capture_output=True)
            if c.returncode != 0:
                sys.exit("cannot compile the format rewrite tool:\n" + c.stderr.decode("utf-8", "replace"))
        cp = os.pathsep.join([os.path.join(os.path.dirname(self.jar), "a68j-runtime.jar"), self.classes])
        p = subprocess.run([self.java, "-cp", cp, "RewriteA68j", "--upper"], input=prog.encode("latin-1"),
                           capture_output=True)
        if p.returncode != 0:
            return None, p.stderr.decode("utf-8", "replace").strip().splitlines()[0]
        out = p.stdout.decode("latin-1")
        if out != prog:
            out = out.replace('{ dg-modules "genietransput" }', '{ dg-modules "genietransput genieformats" }', 1)
        return out, None


def default_java():
    jdk = os.environ.get("JDK25")
    return os.path.join(jdk, "bin", "java") if jdk else "java"


def prepare_solution(text, name, task_title, fn, url, incdir, rewriter):
    """(the prepared program, "prepared"), or (None, the reason why the
    solution is not prepared)."""
    body, missing = prepare(text, incdir)
    if missing:
        return None, "missing include " + " ".join(missing)
    prog = HEADER.format(task=task_title, file=fn, url=url, name=name) + body.rstrip("\n") + FOOTER
    # A program that the tool rejects (as Algol 68 Genie would) is kept as
    # it is; ga68 does not compile it either way.
    rewritten, err = rewriter.rewrite(prog)
    if not err:
        prog = rewritten
    try:
        return a68supper.to_supper(prog), "prepared"
    except a68supper.ConvertError as e:
        return None, "not converted to SUPPER: " + str(e)


ORIGIN = re.compile(r'\{ Rosetta Code, task "(.*)", ALGOL 68 solution (\S+)\.\n'
                    r'  https://rosettacode.org/wiki/(\S+)#ALGOL_68\n')


def again(files, rewriter):
    """Prepares the solutions of the prepared FILES again from
    orig/<name>.a68, with the task and URL of their headers."""
    for f in files:
        name = os.path.basename(f)[:-4]
        m = ORIGIN.search(open(f, encoding="latin-1").read())
        if not m:
            print(f"{f}: no Rosetta Code header")
            continue
        text = open(os.path.join(os.path.dirname(f), "orig", name + ".a68"), "rb").read().decode("latin-1")
        incdir = os.path.join(os.path.dirname(f), "include")
        prog, status = prepare_solution(text, name, m.group(1), m.group(2), m.group(3), incdir, rewriter)
        if prog is None:
            print(f"{f}: {status}")
            continue
        if prog != open(f, encoding="latin-1", newline="").read():
            with open(f, "w", encoding="latin-1", newline="") as g:
                g.write(prog)
            print(f"{f}: changed")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data")
    ap.add_argument("--out")
    ap.add_argument("--java", default=default_java())
    ap.add_argument("--jar", default=os.path.join(ROOT, "backend", "a68j-backend.jar"))
    ap.add_argument("--again", action="store_true")
    ap.add_argument("files", nargs="*")
    a = ap.parse_args()
    rewriter = FormatRewriter(a.java, os.path.abspath(a.jar))
    if a.again:
        return again(a.files, rewriter)
    if not a.data or not a.out or a.files:
        ap.error("--data and --out are required")
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
            # No characters that are invalid in Windows file names
            # ("Sequence:-nth-..." becomes "sequence-nth-...").
            name = re.sub(r'[<>:"|?*\\]', "", fn[:-4])
            raw = open(os.path.join(sdir, fn), "rb").read()
            open(os.path.join(odir, name + ".a68"), "wb").write(raw)
            text = raw.decode("latin-1")
            url = task.replace("-", "_")
            meta = os.path.join(tdir, task, "00-META.yaml")
            if os.path.exists(meta):
                m = re.search(r"^from:\s*https?://rosettacode.org/wiki/(\S+)", open(meta).read(), re.M)
                if m:
                    url = m.group(1)
            url = urllib.parse.quote(urllib.parse.unquote(url), safe="/_()',.:!-*")
            task_title = urllib.parse.unquote(url).replace("_", " ").encode("ascii", "replace").decode()
            prog, status = prepare_solution(text, name, task_title, fn, url, incdir, rewriter)
            if prog is not None:
                with open(os.path.join(a.out, name + ".a68"), "w", encoding="latin-1", newline="") as f:
                    f.write(prog)
            index.append((name, task, status))
    with open(os.path.join(a.out, "index.tsv"), "w") as f:
        f.write("name\ttask\tstatus\n")
        for row in index:
            f.write("\t".join(row) + "\n")
    print(f"{len(index)} solutions, {sum(1 for r in index if r[2] == 'prepared')} prepared")


if __name__ == "__main__":
    sys.exit(main())
