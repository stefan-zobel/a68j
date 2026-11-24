#!/usr/bin/env python3
"""Fetch the include files that ALGOL 68 solutions of Rosetta Code read.

Usage: rosetta_includes.py [--out tests/rosetta/include] [--index tests/rosetta/index.tsv]
                           [--dry-run] [--only FILE ...]

The files are the ones that tests/rosetta/index.tsv lists as missing
("missing include primes.incl.a68 ...").  Rosetta Code has them as code
blocks on wiki pages:

  * a library X.incl.a68 on the page of its category ("Category:ALGOL
    68-X") or on the talk page of it;
  * the files of the prelude and the templates on "ALGOL 68/prelude", on
    pages "Template:..." that it includes, or on the page of the task
    whose solution uses them.

For each file the script looks at these pages and at the pages that the
search of the wiki finds for the file name, and takes the ALGOL 68 code
block that belongs to the file: the one under a heading or after a line
that names the file, or one whose text starts with the name.  Only reading
requests are made.  The files are written unchanged to OUT/<file>
(subdirectories such as prelude/ included), and OUT/SOURCES.tsv records
the page and revision of each.  Files that are not found are listed.

A file name that differs from another one only in case (primes.incl.A68)
is not written, since the two would be the same file on Windows; it is
listed as an alias.
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://rosettacode.org/w/api.php"
AGENT = "a68j-rosetta-includes"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

BLOCK = re.compile(r"<(syntaxhighlight|lang)\b([^>]*)>(.*?)</\1\s*>", re.S | re.I)
TRANSCLUDE = re.compile(r"\{\{\s*(Template:[^}|]+?)\s*\}\}")
LINK = re.compile(r"\[\[\s*([^]|#]+?)\s*(?:#[^]|]*)?(?:\|[^]]*)?\]\]")
FILE_NAME = re.compile(r"[\w/.\-()]+\.(?:a68|A68|alg)\b")

_cache = {}


def api(params):
    params = dict(params, format="json")
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": AGENT})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except OSError:
            if attempt == 2:
                raise
            time.sleep(2)


def page(title):
    """(title after redirects, revision id, wikitext), or None."""
    if title in _cache:
        return _cache[title]
    d = api({"action": "query", "prop": "revisions", "rvprop": "ids|content",
             "rvslots": "main", "redirects": 1, "titles": title})
    result = None
    for p in d.get("query", {}).get("pages", {}).values():
        if "revisions" in p:
            rev = p["revisions"][0]
            result = (p["title"], rev["revid"], rev["slots"]["main"]["*"])
    _cache[title] = result
    time.sleep(0.5)
    return result


def search(text):
    d = api({"action": "query", "list": "search", "srwhat": "text",
             "srsearch": '"%s"' % text, "srlimit": 20})
    time.sleep(0.5)
    return [h["title"] for h in d.get("query", {}).get("search", [])]


def is_algol(attrs):
    return "algol68" in attrs.lower().replace(" ", "").replace('"', "").replace("'", "") \
        or attrs.strip() == ""


def names_file(text, f):
    """Whether TEXT names the file F (with or without its directory)."""
    base = os.path.basename(f)
    return f in text or re.search(r"(?<![\w/.])" + re.escape(base) + r"(?![\w.])", text) is not None


def find_block(wikitext, f, library):
    """The code of F on a page: (code, None), or (None, a page to look at
    instead), or (None, None)."""
    blocks = [m for m in BLOCK.finditer(wikitext) if is_algol(m.group(2))]
    # A block whose first lines name the file (a library: "# primes.incl.a68: ...").
    for m in blocks:
        head = "\n".join(m.group(3).lstrip("\n").splitlines()[:3])
        if names_file(head, f) and (library or re.search(r"(?i)file", head) or head.lstrip().startswith("#")):
            return m.group(3), None
    # The text between the previous block and a block names the file: the
    # last of its lines (among the last four and its last heading) that
    # names a file names this one.  A line that names the file and links
    # to another page ("File: prelude/link.a68 ... available at
    # [[Queue/Definition#ALGOL 68|...]]") leads to that page.
    prev = 0
    link = None
    for m in blocks:
        before = wikitext[prev:m.start()]
        lines = [l for l in before.splitlines() if l.strip()]
        headings = [l for l in lines if re.match(r"^=+.*=+\s*$", l)]
        # A heading that names a file names the file of its section; lines
        # under it may name others ("Requires prelude/Bitmap.a68").
        if headings and FILE_NAME.search(headings[-1]):
            naming = headings[-1:]
        else:
            naming = [l for l in lines[-4:] if FILE_NAME.search(l)]
        if naming and names_file(naming[-1], f):
            return m.group(3), None
        for l in naming[:-1]:
            if names_file(l, f) and link is None:
                for k in LINK.finditer(l):
                    link = k.group(1)
                    break
        prev = m.end()
    if link:
        return None, link
    # A heading that names the file, followed by a transclusion or a link.
    for h in re.finditer(r"^=+\s*(.*?)\s*=+\s*$", wikitext, re.M):
        if names_file(h.group(1), f):
            rest = wikitext[h.end():]
            nxt = re.search(r"^=+", rest, re.M)
            section = rest[:nxt.start()] if nxt else rest
            t = TRANSCLUDE.search(section)
            if t:
                return None, t.group(1)
            for l in LINK.finditer(section):
                if names_file(l.group(1), f):
                    return None, l.group(1)
    return None, None


def preformatted(wikitext):
    """The code of a page that is preformatted text (every line starts with
    a blank, as on the pages Template:prelude/...), without the blank and
    without <noinclude> parts; None for another page."""
    text = re.sub(r"<noinclude>.*?</noinclude>", "", wikitext, flags=re.S)
    lines = text.splitlines()
    if not any(l.strip() for l in lines) or any(l.strip() and not l.startswith(" ") for l in lines):
        return None
    return "\n".join(l[1:] for l in lines)


# Files on a page that does not name them: the page, whose first ALGOL 68
# block is the file.
PAGES = {
    "uriParser.a68": "URL parser/URI parser ALGOL68",
}


def candidates(f):
    base = os.path.basename(f)
    m = re.match(r"(.+)\.incl\.a68$", base, re.I)
    out = []
    if m:
        lib = m.group(1).replace("lsystem", "l-system")
        out += ["Category:ALGOL 68-" + lib, "Category talk:ALGOL 68-" + lib, "Talk:ALGOL 68-" + lib]
    out += ["ALGOL 68/prelude", "Template:" + f, "ALGOL 68/" + f]
    return out


def locate(f):
    """(code, title, revid) of the file F, or None."""
    library = f.lower().endswith(".incl.a68")
    seen = set()
    queue = candidates(f)
    # Pages that a heading naming the file includes or links: their first
    # ALGOL 68 block is the file.
    direct = set()
    if f in PAGES:
        queue.insert(0, PAGES[f])
        direct.add(PAGES[f])
    searched = False
    while True:
        while queue:
            t = queue.pop(0)
            if t in seen:
                continue
            seen.add(t)
            p = page(t)
            if p is None:
                continue
            title, revid, text = p
            code, other = find_block(text, f, library)
            if code is None and t in direct:
                blocks = [m for m in BLOCK.finditer(text) if is_algol(m.group(2))]
                code = blocks[0].group(3) if blocks else preformatted(text)
            if code is not None:
                return code, title, revid
            if other and other not in seen:
                direct.add(other)
                queue.insert(0, other)
        if searched:
            return None
        searched = True
        queue = [t for t in search(f) if t not in seen]
        if not queue and "/" in f:
            queue = [t for t in search(os.path.basename(f)) if t not in seen]


def missing_files(index):
    files = []
    with open(index, encoding="utf-8") as fh:
        for line in fh:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3 and parts[2].startswith("missing include "):
                for f in parts[2][len("missing include "):].split():
                    if f not in files:
                        files.append(f)
    return files


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "tests", "rosetta", "include"))
    ap.add_argument("--index", default=os.path.join(ROOT, "tests", "rosetta", "index.tsv"))
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--only", nargs="+")
    a = ap.parse_args()
    files = a.only or missing_files(a.index)
    lower = {}
    aliases, found, notfound = [], [], []
    for f in sorted(files, key=lambda x: (x != x.lower(), x)):
        if f.lower() in lower and lower[f.lower()] != f:
            aliases.append((f, lower[f.lower()]))
            continue
        lower[f.lower()] = f
        r = locate(f)
        if r is None:
            notfound.append(f)
            print(f"{f}: not found", flush=True)
            continue
        code, title, revid = r
        print(f"{f}: {title} (revision {revid})", flush=True)
        found.append((f, title, revid, code))
    if not a.dry_run:
        for f, title, revid, code in found:
            path = os.path.join(a.out, f)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            text = code.lstrip("\n").rstrip() + "\n"
            with open(path, "w", encoding="utf-8", newline="\n") as fh:
                fh.write(text)
        if found:
            rows = {}
            sp = os.path.join(a.out, "SOURCES.tsv")
            if os.path.exists(sp):
                with open(sp, encoding="utf-8") as fh:
                    for line in fh.read().splitlines()[1:]:
                        rows[line.split("\t")[0]] = line
            for f, title, revid, code in found:
                rows[f] = f"{f}\t{title}\t{revid}"
            with open(sp, "w", encoding="utf-8", newline="\n") as fh:
                fh.write("file\tpage\trevision\n")
                for k in sorted(rows):
                    fh.write(rows[k] + "\n")
    print(f"{len(found)} found, {len(notfound)} not found, {len(aliases)} aliases")
    for f, g in aliases:
        print(f"  alias: {f} = {g}")
    for f in notfound:
        print(f"  not found: {f}")


if __name__ == "__main__":
    main()
