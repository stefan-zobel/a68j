"""Helpers for the multi-packet tests (testsuite/algol68/execute/modules):
which module packets a program needs and which extra options it has."""
import os, re


def modules_for(path):
    """The module packet names of the { dg-modules "a b" } mark, or []."""
    text = open(path, encoding="latin-1").read()
    m = re.search(r'dg-modules\s+(?:"([^"]*)"|\{([^}]*)\}|(\S+))\s*\}', text)
    if not m:
        return []
    return (m.group(1) or m.group(2) or m.group(3)).split()


def module_map_opts(path):
    """-fmodules-map / -fmodules-map-file options of the program, with
    $srcdir expanded to the testsuite directory."""
    text = open(path, encoding="latin-1").read()
    m = re.search(r'dg-options\s+(?:"([^"]*)"|\{([^}]*)\})', text)
    if not m:
        return []
    srcdir = os.path.abspath(os.path.join(os.path.dirname(path), "..", "..", ".."))
    out = []
    for o in (m.group(1) or m.group(2)).split():
        if o.startswith("-fmodules-map"):
            out.append(o.replace("$srcdir", srcdir))
    return out


def is_packet(path):
    """Whether the file is a module packet (module X = ... fed), not a program."""
    text = open(path, encoding="latin-1").read()
    text = re.sub(r"\{[^}]*\}|#[^#]*#", " ", text)
    return re.match(r"\s*(module|MODULE)\b", text) is not None
