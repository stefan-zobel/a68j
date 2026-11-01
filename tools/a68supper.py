#!/usr/bin/env python3
"""Convert an UPPER-stropped Algol 68 program to SUPPER stropping, the
default notation of ga68.

Usage: a68supper.py [-o OUT] FILE      (writes to standard output without -o)

The conversion follows the lexical rules of the ga68 scanner
(gcc/algol68/a68-parser-scanner.cc) and the style of the ga68 preludes
(libga68/standard.a68.in, libga68/posix.a68):

  * reserved words are written in lower case: BEGIN -> begin, INT -> int;
  * mode indicants declared in the program and module indicants are
    capitalised: MODE NODE = ... -> mode Node = ...,
    ACCESS RC_PRELUDE -> access Rc_Prelude;
  * all other bold words (operators and priorities) stay in upper case:
    ABS, UPB, ELEM, PLUSAB, a user-defined OP DIVISORSUM;
  * a tag whose taggles are separated by blanks gets underscores instead,
    since SUPPER does not allow blanks inside tags:
    max sequence length -> max_sequence_length;
  * a tag that is a reserved word in SUPPER (out, in, file, to, ...) gets a
    trailing underscore: in ga68 a taggle next to an underscore is never
    taken as a reserved word, and the underscore is not part of the tag,
    so the program still refers to the same identifier;
  * comments CO ... CO, COMMENT ... COMMENT, # ... # and NOTE ... ETON
    become brief comments { ... } (SUPPER has only those); a brace in the
    text of such a comment becomes a parenthesis unless the braces are
    balanced (brief comments nest);
  * a blank is inserted where two converted symbols would otherwise run
    together (UPPER allows INTx, SUPPER needs int x).

String denotations, character and format texts are copied unchanged, and
so is the layout: the converted program has the same lines as the original.
Pragmats are not converted (the Rosetta Code preparation removes them);
the converter reports them as an error.
"""
import argparse, re, sys

# Bold words that are reserved words in ga68 (a68-parser-keywords.cc, GNU68).
KEYWORDS = set("""
ANDTH OREL WORD ACCESS DEF POSTLUDE FED NEST MODULE EGG PUB BY OP AT PRIO
END GO TO THEN TRUE PROC FOR GOTO WHILE COMPL FROM DO CASE LOC CHAR REF NIL
FI FILE PAR ASSERT OUSE IN LONG EMPTY MODE IF OD OF STRUCT SKIP SHORT IS ESAC
CHANNEL REAL STRING BOOL ISNT FALSE UNION OUT BEGIN FLEX VOID BITS ELSE EXIT
HEAP INT BYTES SEMA ELIF
""".split())
# Reserved words of UPPER stropping only; in SUPPER they are ordinary tags.
COMMENT_WORDS = {"CO": "CO", "COMMENT": "COMMENT", "NOTE": "ETON"}
PRAGMAT_WORDS = {"PR", "PRAGMAT", "ORDER"}
SUPPER_RESERVED = {k.lower() for k in KEYWORDS}

BOLD = re.compile(r"[A-Z]+(?:_[A-Z]+)*")
# A tag: lower-case letters and digits; in UPPER stropping blanks (and one
# underscore at a time) may separate the taggles.
TAGGLE = re.compile(r"[a-z0-9]+")
GAP = re.compile(r"(?:[ \t\f\r\n]|_)+")


class ConvertError(Exception):
    pass


def lex(text):
    """Split UPPER-stropped text into (kind, text) tokens.  Kinds: ws, comment,
    brief, string, format, number, tag, bold, other."""
    toks, i, n = [], 0, len(text)
    line = lambda: text.count("\n", 0, i) + 1
    while i < n:
        c = text[i]
        if c in " \t\f\r\n":
            j = i
            while j < n and text[j] in " \t\f\r\n":
                j += 1
            toks.append(("ws", text[i:j])); i = j
        elif c == "{":
            depth, j = 0, i
            while j < n:
                if text[j] == "{":
                    depth += 1
                elif text[j] == "}":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            if j >= n:
                raise ConvertError(f"line {line()}: unterminated {{ comment")
            toks.append(("brief", text[i:j + 1])); i = j + 1
        elif c == '"':
            j = i + 1
            while j < n:
                if text[j] == '"':
                    if j + 1 < n and text[j + 1] == '"':
                        j += 2; continue
                    break
                j += 1
            toks.append(("string", text[i:j + 1])); i = j + 1
        elif c == "#":
            j = text.find("#", i + 1)
            if j < 0:
                raise ConvertError(f"line {line()}: unterminated # comment")
            toks.append(("comment", text[i + 1:j])); i = j + 1
        elif c == "$":
            # A format text: copied unchanged (string denotations inside it
            # may contain a dollar).
            j = i + 1
            while j < n and text[j] != "$":
                if text[j] == '"':
                    j += 1
                    while j < n and not (text[j] == '"' and text[j + 1:j + 2] != '"'):
                        j += 2 if text[j] == '"' else 1
                j += 1
            toks.append(("format", text[i:j + 1])); i = j + 1
        elif "A" <= c <= "Z":
            w = BOLD.match(text, i).group(0)
            if w in COMMENT_WORDS:
                # As in ga68 (skip_comment), the comment ends at the first
                # closing word that is not followed by another capital
                # letter, even inside a longer word (TACO ends CO).
                end = COMMENT_WORDS[w]
                m = re.compile(end + r"(?![A-Z])").search(text, i + len(w))
                if not m:
                    raise ConvertError(f"line {line()}: unterminated {w} comment")
                toks.append(("comment", text[i + len(w):m.start()])); i = m.end()
            elif w in PRAGMAT_WORDS:
                raise ConvertError(f"line {line()}: pragmat {w} is not converted")
            else:
                toks.append(("bold", w)); i += len(w)
        elif "0" <= c <= "9" or (c == "." and "0" <= text[i + 1:i + 2] <= "9" and text[i + 1:i + 2]):
            # Blanks may appear inside denotations, as inside tags.
            m = re.compile(r"\d+(?:[ \t]+\d+)*[ \t]*r(?:[ \t]*[0-9a-f])+").match(text, i)  # radix
            if not m:
                m = re.compile(r"(?:\d+(?:[ \t]+\d+)*)?(?:\.\d+(?:[ \t]+\d+)*)?"
                               r"(?:[eE\\][ \t]*[+-]?[ \t]*\d+)?").match(text, i)
            toks.append(("number", m.group(0))); i = m.end()
        elif "a" <= c <= "z":
            parts, j = [], i
            while True:
                m = TAGGLE.match(text, j)
                parts.append(m.group(0)); j = m.end()
                # ga68 skips blanks inside a tag, so a letter or a digit
                # after a blank continues it ("x 1" is x1).
                g = GAP.match(text, j)
                if g and TAGGLE.match(text, g.end()):
                    j = g.end()
                    continue
                break
            toks.append(("tag", (parts, text[i:j]))); i = j
        else:
            toks.append(("other", c)); i += 1
    return toks


def mode_indicants(toks):
    """The bold words declared as mode indicants: the first bold word after
    MODE and every bold word after a comma at the same bracket depth, when
    followed by '='."""
    sig = [t for t in toks if t[0] not in ("ws", "comment", "brief")]
    modes = set()
    for k, (kind, txt) in enumerate(sig):
        # Module indicants (ACCESS A, B; MODULE A = ...) are capitalised too.
        if kind == "bold" and txt in ("ACCESS", "MODULE"):
            for kind2, txt2 in sig[k + 1:]:
                if kind2 == "bold" and txt2 not in KEYWORDS:
                    modes.add(txt2)
                elif not (kind2 == "other" and txt2 == ","):
                    break
        if not (kind == "bold" and txt == "MODE"):
            continue
        depth, expect = 0, True
        for kind2, txt2 in sig[k + 1:]:
            if expect and kind2 == "bold":
                modes.add(txt2); expect = False
                continue
            expect = False
            if kind2 == "other" and txt2 in "([":
                depth += 1
            elif kind2 == "other" and txt2 in ")]":
                depth -= 1
                if depth < 0:
                    break
            elif kind2 == "other" and txt2 == ";" and depth == 0:
                break
            elif kind2 == "other" and txt2 == "," and depth == 0:
                expect = True
            elif kind2 == "bold" and txt2 in ("BEGIN", "END", "IF", "FI", "DO", "OD", "CASE", "ESAC") and depth == 0:
                break
    return modes


def brief(body):
    """A brief comment with the text BODY.  ga68 skips string denotations
    inside a brief comment: a quote must be closed on the same line, and the
    string may contain only ASCII characters; braces inside strings do not
    count.  If BODY does not meet this, its quotes become apostrophes."""
    def strings_ok(b):
        for ln in b.split("\n"):
            parts = ln.split('"')
            if len(parts) % 2 == 0:
                return False
            if any(not p.isascii() for p in parts[1::2]):
                return False
        return True
    if not strings_ok(body):
        body = body.replace('"', "'")
    outside = "".join(p for k, ln in enumerate(body.split("\n")) for j, p in enumerate(ln.split('"')) if j % 2 == 0)
    opened = 0
    for ch in outside:
        opened += (ch == "{") - (ch == "}")
        if opened < 0:
            break
    if opened != 0:
        body = body.replace("{", "(").replace("}", ")")
    return "{" + body + "}"


def to_supper(text):
    toks = lex(text)
    modes = mode_indicants(toks)
    out = []

    def emit(s):
        word = lambda ch: ch != "" and (ch.isascii() and ch.isalnum() or ch == "_")
        if out and s and word(s[0]) and word(out[-1][-1:]):
            out.append(" ")
        out.append(s)

    for kind, txt in toks:
        if kind == "bold":
            if txt in KEYWORDS:
                emit(txt.lower())
            elif txt in modes:
                emit("_".join(p.capitalize() for p in txt.split("_")))
            else:
                emit(txt)
        elif kind == "tag":
            parts, src = txt
            name = "_".join(parts)
            if len(parts) == 1 and name in SUPPER_RESERVED:
                name += "_"
            emit(name)
            # Keep the line structure when a tag was split over lines.
            if "\n" in src:
                out.append("\n" * src.count("\n"))
        elif kind == "comment":
            emit(brief(txt))
        elif kind == "other" and txt in "[]" and out and out[-1] == txt:
            # [[ and ]] are pragmat brackets in SUPPER: a[b[i]] needs a blank.
            out.append(" "); out.append(txt)
        else:
            emit(txt)
    return "".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("-o")
    ap.add_argument("file")
    a = ap.parse_args()
    text = open(a.file, encoding="latin-1", newline="").read()
    try:
        res = to_supper(text)
    except ConvertError as e:
        print(f"{a.file}: {e}", file=sys.stderr)
        return 1
    if a.o:
        open(a.o, "w", encoding="latin-1", newline="").write(res)
    else:
        sys.stdout.write(res)
    return 0


if __name__ == "__main__":
    sys.exit(main())
