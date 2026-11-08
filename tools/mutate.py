#!/usr/bin/env python3
"""Create mutated copies of Algol 68 sources for differential testing of
error paths: random insertions, deletions, case flips and truncations.

Usage: mutate.py SEED COUNT OUTDIR INPUT-FILES...
"""
import os, random, sys

seed, count, outdir, inputs = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3], sys.argv[4:]
rnd = random.Random(seed)
alphabet = list(b'"{}_=:/\\.er01279aZ\'[]#$@|<>*+-~ \t\n') + [0x80, 0xc3, 0x00, 0x0c, 0x7f]
os.makedirs(outdir, exist_ok=True)
for n in range(count):
    src = open(rnd.choice(inputs), "rb").read()
    data = bytearray(src)
    for _ in range(rnd.randint(1, 4)):
        if not data:
            break
        i = rnd.randrange(len(data))
        op = rnd.random()
        if op < 0.4:
            data.insert(i, rnd.choice(alphabet))
        elif op < 0.7:
            del data[i]
        elif op < 0.85:
            j = min(len(data), i + rnd.randint(1, 12))
            data[i:j] = bytes(data[i:j]).swapcase()
        else:
            del data[i:]
    with open(os.path.join(outdir, f"mut{n:04d}.a68"), "wb") as f:
        f.write(bytes(data))
