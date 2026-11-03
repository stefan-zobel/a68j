"""Helpers for the multi-packet tests (tests/ga68/execute/modules) and the
options of the { dg-options } mark: which module packets a program needs
and which extra options (module maps, include directories) it has."""
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# The modules bundled with a68j-backend.jar that are written in plain
# Algol 68 without Java (backend/lib/<name>.a68, a subset of
# Intrinsics.BUNDLED_MODULES).  A program names them in { dg-modules } like
# any other packet: for ga68 the source in backend/lib is compiled, while
# a68j has them built in, so run_jvm.py does not compile them.
BUNDLED = ("genietransput", "texts")


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


def dg_options(path):
    """The options of the { dg-options "..." } mark of the program, or []."""
    text = open(path, encoding="latin-1").read()
    m = re.search(r'dg-options\s+(?:"([^"]*)"|\{([^}]*)\})', text)
    return (m.group(1) or m.group(2)).split() if m else []


def expand_srcdir(o, path):
    """The option o with $srcdir/algol68/execute expanded to the directory
    "execute" the program path is in (gcc/testsuite/algol68/execute in GCC,
    tests/ga68/execute in a68j)."""
    execute = os.path.dirname(os.path.abspath(path))
    while os.path.basename(execute) != "execute" and os.path.dirname(execute) != execute:
        execute = os.path.dirname(execute)
    return o.replace("$srcdir/algol68/execute", execute)


def module_map_opts(path):
    """-fmodules-map / -fmodules-map-file options of the program, with
    $srcdir expanded (expand_srcdir)."""
    return [expand_srcdir(o, path) for o in dg_options(path) if o.startswith("-fmodules-map")]


def include_opts(path):
    """The -I options of the program (directories searched by the include
    pragmat, PR include "file" PR): $srcdir expanded (expand_srcdir), and a
    relative directory made absolute from the directory of the program, as
    the runners do not run the compilers there."""
    out = []
    for o in dg_options(path):
        if o.startswith("-I") and len(o) > 2:
            d = expand_srcdir(o[2:], path)
            out.append("-I" + os.path.join(os.path.dirname(os.path.abspath(path)), d))
    return out


def is_packet(path):
    """Whether the file is a module packet (module X = ... fed), not a program."""
    text = open(path, encoding="latin-1").read()
    text = re.sub(r"\{[^}]*\}|#[^#]*#", " ", text)
    return re.match(r"\s*(module|MODULE)\b", text) is not None
