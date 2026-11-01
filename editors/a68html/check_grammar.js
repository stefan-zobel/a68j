#!/usr/bin/env node
// check_grammar.js - checks the TextMate grammar against the real scanner.
//
// For every source file there is a token dump of a68j (a68j.tools.TokenDump,
// the same format as ga68 with tools/ga68-token-dump.patch). Checked:
//
//  1. Every scanner token starts, in the grammar, at a place with a fitting
//     class: reserved word -> keyword/storage/constant.language,
//     identifier -> no keyword, bold word -> mode/operator/module,
//     number -> constant.numeric, string -> string, symbol -> not in a
//     comment/string/pragmat.
//  2. The other way round, every visible grammar token that is not a
//     comment or pragmat lies inside a scanner token; otherwise the grammar
//     takes a comment for code.
//
// Usage: node check_grammar.js DUMPDIR file.a68 ...
//   The dump of a/b/c.a68 is DUMPDIR/a_b_c.a68.tok.

'use strict';

const fs = require('fs');
const path = require('path');
const { tokenize } = require('./a68tokens');

const DUMP_RE = /^(.*?):(\d+):(\d+) (\S+) "((?:[^"\\]|\\.)*)"/;

function scopeAt(lineToks, col) {
    for (const t of lineToks) {
        if (col >= t.start && col < t.end) {
            return t;
        }
    }
    return null;
}

function has(t, prefixes) {
    return t.scopes.some((s) => prefixes.some((p) => s === p || s.startsWith(p + '.') ||
        s.startsWith(p) && s[p.length] === '.'));
}

const KW = ['keyword', 'storage', 'constant.language'];
const NOCODE = ['comment', 'meta.preprocessor.pragmat', 'string'];
const IDENT_OK = ['variable.other', 'support.function', 'support.constant', 'entity.name.function',
    'entity.name.label'];
const BOLD_OK = ['entity.name.type', 'entity.name.namespace', 'entity.name.function.operator',
    'support.function.operator', 'support.constant.formal-hole'];

/** Byte offset in a UTF-8 line -> index in the JS string. */
function byteToChar(line, byteOff) {
    let b = 0;
    let i = 0;
    while (i < line.length && b < byteOff) {
        const cp = line.codePointAt(i);
        b += cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
        i += cp > 0xffff ? 2 : 1;
    }
    return i;
}

/**
 * The ranges [line, from, to) of the lexeme at (ln, col) in the source (only
 * for check 2). Strings and numbers may span lines: the scanner skips spaces
 * and line ends in denotations.
 */
function lexemeRanges(lines, ln, col, attr, sym) {
    const rest = lines[ln].slice(col);
    let m;
    if (attr === 'GOTO_SYMBOL' && (m = /^go\s*to/.exec(rest))) {
        return [[ln, col, col + m[0].length]];
    }
    if (attr === 'ROW_CHAR_DENOTATION') {
        const out = [];
        let l = ln;
        let i = col + 1;
        while (l < lines.length) {
            const s = lines[l];
            while (i < s.length) {
                if (s[i] === '"') {
                    if (s[i + 1] === '"') {
                        i += 2;
                        continue;
                    }
                    out.push([l, l === ln ? col : 0, i + 1]);
                    return out;
                }
                i++;
            }
            out.push([l, l === ln ? col : 0, s.length]);
            l++;
            i = 0;
        }
        return out;
    }
    if (/^(INT|REAL|BITS)_DENOTATION$/.test(attr)) {
        m = /^(?:\d+[ \t]*r[ \t]*[0-9a-f]+|[\d \t]*\.?[\d \t]*(?:[eE][ \t]*[+-]?[ \t]*\d[\d \t]*)?)/.exec(rest);
        const len = Math.max(1, m[0].trimEnd().length);
        const out = [[ln, col, col + len]];
        let l = ln;
        let tail = lines[l].slice(col + len);
        while (/^\s*$/.test(tail) && l + 1 < lines.length && /^\s*\d/.test(lines[l + 1])) {
            l++;
            const mm = /^(\s*)([\d \t]*)/.exec(lines[l]);
            const a = mm[1].length;
            const b = a + mm[2].trimEnd().length;
            out.push([l, a, b]);
            tail = lines[l].slice(b);
        }
        return out;
    }
    if ((m = /^[A-Za-z][A-Za-z0-9_]*/.exec(rest))) {
        return [[ln, col, col + m[0].length]];
    }
    return [[ln, col, col + Math.max(1, sym.length)]];
}

async function checkFile(file, dumpDir) {
    const errors = [];
    const dumpFile = path.join(dumpDir, file.replace(/\//g, '_') + '.tok');
    const text = fs.readFileSync(file, 'utf8');
    if (/dg-error/.test(text)) {
        // A deliberately erroneous program of the ga68 test suite.
        return { n: 0, errors: [], skipped: true };
    }
    const srcLines = text.split(/\r?\n/);
    const toks = await tokenize(text);
    const covered = srcLines.map(() => []);
    const dump = fs.readFileSync(dumpFile, 'utf8').split('\n');
    if (dump.some((d) => d.startsWith(file + ':') && / error: /.test(d))) {
        // The scanner already rejects the file; nothing to compare.
        return { n: 0, errors: [], skipped: true };
    }
    let n = 0;
    for (const d of dump) {
        const m = DUMP_RE.exec(d);
        if (!m || m[1] !== file) {
            continue;
        }
        n++;
        const ln = parseInt(m[2], 10) - 1;
        // The scanner counts columns in bytes (UTF-8), like ga68.
        const col = byteToChar(srcLines[ln] || '', parseInt(m[3], 10) - 1);
        const attr = m[4];
        const sym = m[5];
        const src = srcLines[ln] || '';
        const t = scopeAt(toks[ln] || [], col);
        const where = `${file}:${ln + 1}:${col + 1} ${attr} "${sym}"`;
        for (const [l, a, b] of lexemeRanges(srcLines, ln, col, attr, sym)) {
            covered[l].push([a, b]);
        }
        if (!t) {
            errors.push(`${where}: no grammar token`);
            continue;
        }
        const inner = t.scopes[t.scopes.length - 1];
        let ok;
        if (has(t, ['meta.format'])) {
            ok = true; // the scanner splits format texts by rules of their own
        } else if (attr === 'IDENTIFIER') {
            ok = has(t, IDENT_OK) && !has(t, KW);
        } else if (attr === 'BOLD_TAG') {
            ok = has(t, BOLD_OK);
        } else if (/^(INT|REAL|BITS)_DENOTATION$/.test(attr)) {
            ok = has(t, ['constant.numeric']);
        } else if (attr === 'ROW_CHAR_DENOTATION') {
            ok = has(t, ['punctuation.definition.string.begin']) && t.start === col;
        } else if (/^[a-z]/.test(src.slice(col))) {
            ok = has(t, KW);
        } else {
            ok = !has(t, NOCODE) && !has(t, ['invalid']);
        }
        if (!ok) {
            errors.push(`${where}: grammar says ${inner} ("${t.text}")`);
        }
    }
    // Check 2: code tokens of the grammar outside all scanner tokens.
    toks.forEach((lineToks, ln) => {
        for (const t of lineToks) {
            if (/^\s*$/.test(t.text) || has(t, ['comment', 'meta.preprocessor.pragmat', 'meta.format'])) {
                continue;
            }
            const first = t.start + (t.text.length - t.text.trimStart().length);
            if (!covered[ln].some(([a, b]) => first >= a && first < b)) {
                errors.push(`${file}:${ln + 1}:${first + 1}: grammar token "${t.text}" ` +
                    `(${t.scopes[t.scopes.length - 1]}) belongs to no scanner token`);
            }
        }
    });
    return { n, errors };
}

async function main() {
    const [dumpDir, ...files] = process.argv.slice(2);
    if (!dumpDir || files.length === 0) {
        process.stderr.write('Usage: node check_grammar.js DUMPDIR file.a68 ...\n');
        process.exit(2);
    }
    let total = 0;
    let bad = 0;
    let tokens = 0;
    const all = [];
    const skipped = [];
    for (const f of files) {
        const { n, errors, skipped: sk } = await checkFile(f, dumpDir);
        if (sk) {
            skipped.push(f);
            continue;
        }
        total++;
        tokens += n;
        if (n === 0) {
            all.push(`${f}: no token in the dump (scanner error?)`);
        }
        if (errors.length) {
            bad++;
            all.push(...errors);
        }
    }
    for (const e of all) {
        console.log(e);
    }
    if (skipped.length) {
        console.log(`skipped (the scanner reports errors): ${skipped.join(' ')}`);
    }
    console.log(`\n${total} files, ${tokens} scanner tokens, ${bad} files with differences, ` +
        `${all.length} messages`);
    process.exit(bad ? 1 : 0);
}

main();
