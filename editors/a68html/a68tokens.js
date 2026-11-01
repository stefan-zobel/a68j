// a68tokens.js - splits Algol 68 source (GNU, SUPPER stropping) into tokens
// with the TextMate grammar in ../vscode-algol68/syntaxes.
//
// Uses the same libraries as VS Code (vscode-textmate and the Oniguruma
// regex engine as WebAssembly), so the result is colored exactly as in the
// editor.
//
//   const { tokenize } = require('./a68tokens');
//   const lines = await tokenize(text);
//   // lines[i] = [{ start, end, text, scopes: ['source.algol68', ...] }, ...]

'use strict';

const fs = require('fs');
const path = require('path');
const vsctm = require('vscode-textmate');
const onig = require('vscode-oniguruma');

const GRAMMAR_FILE = path.join(__dirname, '..', 'vscode-algol68', 'syntaxes',
    'algol68.tmLanguage.json');

let grammarPromise = null;

function loadGrammar(grammarFile = GRAMMAR_FILE) {
    if (grammarPromise) {
        return grammarPromise;
    }
    const wasm = fs.readFileSync(require.resolve('vscode-oniguruma/release/onig.wasm'));
    const onigLib = onig.loadWASM(wasm.buffer.slice(wasm.byteOffset, wasm.byteOffset + wasm.byteLength))
        .then(() => ({
            createOnigScanner: (patterns) => new onig.OnigScanner(patterns),
            createOnigString: (s) => new onig.OnigString(s),
        }));
    const registry = new vsctm.Registry({
        onigLib,
        loadGrammar: async (scopeName) => {
            if (scopeName !== 'source.algol68') {
                return null;
            }
            return vsctm.parseRawGrammar(fs.readFileSync(grammarFile, 'utf8'), grammarFile);
        },
    });
    grammarPromise = registry.loadGrammar('source.algol68');
    return grammarPromise;
}

/** Tokenize the text line by line; line ends belong to no token. */
async function tokenize(text) {
    const grammar = await loadGrammar();
    const lines = text.split(/\r?\n/);
    if (lines.length > 0 && lines[lines.length - 1] === '') {
        lines.pop();
    }
    let stack = vsctm.INITIAL;
    const out = [];
    for (const line of lines) {
        const r = grammar.tokenizeLine(line, stack);
        stack = r.ruleStack;
        out.push(r.tokens.map((t) => ({
            start: t.startIndex,
            end: Math.min(t.endIndex, line.length),
            text: line.slice(t.startIndex, Math.min(t.endIndex, line.length)),
            scopes: t.scopes,
        })).filter((t) => t.end > t.start));
    }
    return out;
}

module.exports = { tokenize, loadGrammar, GRAMMAR_FILE };
