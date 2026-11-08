#!/usr/bin/env python3
"""Convert a program of the book (SUPPER stropping) to UPPER stropping for
Algol 68 Genie.

Usage: supper_to_upper.py FILE      (writes to standard output)

Only the subset of SUPPER that the book uses is handled:
  * reserved words (lower case) become upper case: begin -> BEGIN;
  * type names that the program declares (capitalized) become upper case:
    Node -> NODE;
  * operators (upper case) are copied;
  * brief comments { ... } (which nest) become COMMENT ... COMMENT;
  * the line "access Genie_Transput" (and the same for Texts) is dropped,
    since Algol 68 Genie has the transput of the Revised Report built in;
    an "access M" before a clause on the same line is dropped as well;
  * underscores in identifiers become blanks, since Algol 68 Genie spells
    the names of its library that way (bytes pack, max int);
  * strings and characters are copied unchanged.
"""
import re, sys

RESERVED = set("""access andth assert at begin bits bool by bytes case channel char
  compl def do egg elif else empty end esac exit false fed fi file flex
  for format from go goto heap if in int is isnt loc long mode module
  nest nil od of op orel ouse out par postlude prio proc pub real ref
  sema short skip string struct then to true union void while word""".split())
BUNDLED = ("Genie_Transput", "Texts")


def convert(src):
    src = re.sub(r"^\s*access\s+(" + "|".join(BUNDLED) + r")\s*\n", "", src, flags=re.M)
    src = re.sub(r"\baccess\s+(" + "|".join(BUNDLED) + r")\s+", "", src)
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if c == "{":
            depth, j = 0, i
            while j < n:
                if src[j] == "{":
                    depth += 1
                elif src[j] == "}":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            out.append("COMMENT " + src[i + 1:j].replace("{", "(").replace("}", ")").strip() + " COMMENT")
            i = j + 1
        elif c == '"':
            j = i + 1
            while j < n:
                if src[j] == '"':
                    if j + 1 < n and src[j + 1] == '"':
                        j += 2
                        continue
                    break
                j += 1
            out.append(src[i:j + 1])
            i = j + 1
        elif c.isalpha() or c == "_":
            m = re.match(r"[A-Za-z_][A-Za-z0-9_]*", src[i:])
            w = m.group(0)
            n_w = len(w)
            if w in RESERVED or w[0].isupper():
                w = w.upper()
            else:
                w = w.replace("_", " ")   # bytes_pack -> bytes pack
            out.append(w)
            i += n_w
        else:
            out.append(c)
            i += 1
    return "".join(out)


if __name__ == "__main__":
    sys.stdout.write(convert(open(sys.argv[1], encoding="utf-8").read()))
