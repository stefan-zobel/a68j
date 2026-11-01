"""Helpers for the multi-packet tests (testsuite/algol68/execute/modules):
which module packets a program needs and which extra options it has."""
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# The modules bundled with a68j-backend.jar that are written in plain
# Algol 68 without Java (backend/lib/<name>.a68, a subset of
# Intrinsics.BUNDLED_MODULES).  A program names them in { dg-modules } like
# any other packet: for ga68 the source in backend/lib is compiled, while
# a68j has them built in, so run_jvm.py does not compile them.
BUNDLED = ("genietransput",)


def module_source(path, m):
    """The source of the module packet m of the program path: the bundled
    source for a bundled module, otherwise m.a68 next to the program."""
    if m in BUNDLED:
        return os.path.join(ROOT, "backend", "lib", m + ".a68")
    return os.path.join(os.path.dirname(path), m + ".a68")


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
