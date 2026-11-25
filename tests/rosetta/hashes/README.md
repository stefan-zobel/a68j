# 32-bit words, bytes and SHA-256 for Rosetta Code

`hashes.incl.a68` holds what the hash functions of Rosetta Code have in
common: arithmetic on 32-bit words, the bytes of a string, words from
bytes and back, the padding of a message, hexadecimal output, SHA-256 and
HMAC. It is written in plain Algol 68 in the notation of Algol 68 Genie,
for the ALGOL 68 solutions on Rosetta Code, and runs with Algol 68 Genie
and with a68j (`-fgenie`) with the same results.

It builds on `bits.incl.a68` of Rosetta Code
([Category:ALGOL 68-bits](https://rosettacode.org/wiki/Category:ALGOL_68-bits)),
which provides `AND`, `OR`, `XOR`, `NOT`, `SHL` and `SHR` on `INT`. It
is in `tests/rosetta/include`, with the other include files of Rosetta
Code (`tools/rosetta_includes.py`). A program here reads both:

```
PR read "../include/bits.incl.a68" PR
PR read "hashes.incl.a68" PR
```

On Rosetta Code, where both files are next to the program, the first
line is `PR read "bits.incl.a68" PR`.

```sh
sh tools/hashes_tests.sh [JAVA]              # run with a68j -fgenie, compare with NAME.out
sh tools/hashes_tests.sh --record [A68G]     # record NAME.out with Algol 68 Genie
```

## Contents

| File | |
|---|---|
| `hashes.incl.a68` | the library |
| `sha-256.a68` | the task "SHA-256": the digest of "Rosetta code" |
| `sha-1.a68` | the task "SHA-1": SHA-1 implemented with the words of the library, the digest of "Rosetta Code" and of the test vectors of FIPS 180-4 |
| `md5-implementation.a68` | the task "MD5/Implementation": MD5 implemented with the words of the library, checked with the test suite of RFC 1321 |
| `test-words.a68` | words, rotations, bytes in both orders, UTF-8, padding |
| `test-sha256.a68` | SHA-256 with the test vectors of FIPS 180-4 and the task "SHA-256", messages of 55, 56, 63, 64, 119 and 1000 bytes (the limits of the padding), and HMAC-SHA-256 with the test cases 1, 2, 3 and 6 of RFC 4231 |
| `NAME.out` | the output and exit status of `NAME.a68`, recorded with Algol 68 Genie 3.13.4 |

The tests check every value themselves (`ok` or `WRONG`); the expected
digests were computed with Python's `hashlib` and `hmac`.

## Words

A word is an `INT` that holds 32 bits. After `+` and `SHL` a program masks
it with `AND word mask`:

```
w[t] := (w[t - 16] + s0 + w[t - 7] + s1) AND word mask
```

This works with both widths of `INT`. In Algol 68 Genie, `INT` has 64
bits: the sum of a few words cannot overflow, and the mask
(`TOINT 16rffffffff` = 4294967295) cuts it to 32 bits, so a word is 0 to
2^32 - 1. In a68j, as in ga68, `INT` has 32 bits and an addition wraps
around modulo 2^32; the mask is -1 and changes nothing, and a word with
the highest bit set is negative but has the same bits. `SHR` of
`bits.incl.a68` shifts the bits of a negative `INT` as those of a
positive one, so the results are the same in both.

| | |
|---|---|
| `word mask` | the 32 bits as an `INT` |
| `x ROTL n`, `x ROTR n` | rotations of a word, for 0 < n < 32 |
| `big endian word (b, i)`, `little endian word (b, i)` | the word of the bytes `b[i]` to `b[i + 3]` (SHA-1, SHA-256: big endian; MD4, MD5, RIPEMD-160: little endian) |
| `big endian bytes (w)`, `little endian bytes (w)` | the 4 bytes of a word |
| `padded (message, big endian)` | the message with the byte 128, zeros up to 56 modulo 64 and its length in bits as 8 bytes, as MD4, MD5, SHA-1, SHA-256 and RIPEMD-160 pad it |

## Bytes

A row of bytes is a `[] INT` of values 0 to 255. `bytes (s)` gives the
UTF-8 bytes of a string. In Algol 68 Genie a `CHAR` is a byte, and a
string from a source in UTF-8 holds its bytes already; in a68j a `CHAR` is
a Unicode code point, and `bytes` encodes it. The procedure tells the two
apart by `max abs char` (255 in Algol 68 Genie, 1114111 in a68j), so
`bytes ("aä€")` is `61 c3 a4 e2 82 ac` in both.

| | |
|---|---|
| `bytes (s)` | the UTF-8 bytes of a string |
| `join (a, b)` | the bytes of `a` followed by those of `b` |
| `hex (b)` | the bytes in lower case hexadecimal digits |

The bytes of a file, unchanged, come from `read bytes` of the module
`Files` of a68j ([docs/files.md](../../../docs/files.md)), which
`-fgenie` makes available: `hex (sha256 (read bytes ("title.png")))`.
Algol 68 Genie has no such procedure, and reading a file with `get`
there loses the byte 255.

## Hash functions

| | |
|---|---|
| `sha256 (message)` | SHA-256 (FIPS 180-4): 32 bytes |
| `hmac (hash, block size, key, message)` | HMAC (RFC 2104) with any hash procedure `PROC ([] INT) [] INT` whose blocks have `block size` bytes (64 for MD5, SHA-1 and SHA-256) |

SHA-256 is here because several tasks use it: "SHA-256",
"SHA-256 Merkle tree", "Bitcoin/address validation",
"Bitcoin/public point to address" and "Elliptic Curve Digital Signature
Algorithm". The tasks "MD4", "MD5/Implementation", "SHA-1" and
"RIPEMD-160" ask for the implementation of their hash itself; their
solutions use the words and the padding of the library, and the task
"Time-based one-time password algorithm" can use `hmac` with SHA-1.

Example, the task "SHA-256":

```
print ((hex (sha256 (bytes ("Rosetta code"))), newline))
```
