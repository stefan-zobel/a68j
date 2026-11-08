#!/usr/bin/env python3
"""Create mutated copies of Algol 68 sources at the token level, for
differential testing of the parser's error paths: words and symbols are
deleted, duplicated, swapped or replaced by other words of the same file, so
that most mutants still scan but no longer parse.

Usage: mutate_tokens.py SEED COUNT OUTDIR INPUT-FILES...
"""
import os, random, re, sys

seed, count, outdir, inputs = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3], sys.argv[4:]
rnd = random.Random(seed)
TOKEN = re.compile(rb"[A-Za-z_][A-Za-z0-9_]*|[0-9]+|:=:|:/=:|:=|[-+*/<>=~&|^%]+|[()\[\],;:@]")
EXTRA = [b";", b",", b"(", b")", b":=", b"=", b"begin", b"end", b"if", b"fi", b"then",
         b"BEGIN", b"END", b"IF", b"FI", b"THEN", b"do", b"od", b"DO", b"OD", b"|", b":", b"@"]
os.makedirs(outdir, exist_ok=True)
for n in range(count):
    src = open(rnd.choice(inputs), "rb").read()
    toks = [(m.start(), m.end()) for m in TOKEN.finditer(src)]
    # Skip the dg- comment header lines when choosing mutation points.
    body = [t for t in toks if not src[max(0, src.rfind(b"\n", 0, t[0])):t[0]].lstrip().startswith(b"{")]
    if not body:
        body = toks
    data = src
    for _ in range(rnd.randint(1, 3)):
        toks = [(m.start(), m.end()) for m in TOKEN.finditer(data)]
        cand = [t for t in toks if t[0] > len(data) // 20] or toks
        if not cand:
            break
        s, e = rnd.choice(cand)
        op = rnd.random()
        if op < 0.35:
            data = data[:s] + data[e:]
        elif op < 0.55:
            data = data[:e] + b" " + data[s:e] + data[e:]
        elif op < 0.8:
            s2, e2 = rnd.choice(toks)
            data = data[:s] + data[s2:e2] + data[e:]
        else:
            data = data[:s] + rnd.choice(EXTRA) + b" " + data[s:]
    with open(os.path.join(outdir, f"tok{n:04d}.a68"), "wb") as f:
        f.write(data)
