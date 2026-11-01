#!/usr/bin/env node
// a68html - renders Algol 68 source (GNU, SUPPER stropping) as colored HTML or SVG.
//
// The colors come from the same TextMate grammar as in VS Code
// (../vscode-algol68/syntaxes/algol68.tmLanguage.json). In HTML every token
// gets a CSS class and the colors are given once in a <style> block. SVG is
// for places that show images but strip styles from HTML, such as Markdown
// files on GitHub: ![program](program.svg).
//
// Usage:
//   node a68html.js [options] file.a68                HTML on stdout
//   node a68html.js [options] -o out.html file.a68
//   node a68html.js [options] -d outdir a.a68 b.a68   one .html (or .svg) each
//   cat file.a68 | node a68html.js -                  from stdin
//
// Options:
//   --svg                     SVG instead of HTML (also when -o names a .svg file)
//   --theme light|dark|auto   color scheme (auto = follows the operating system; default)
//   --lines                   line numbers
//   --fragment                HTML: only <style> and <pre>, no <html>/<body> (for embedding)
//   --no-style                HTML: no <style> block (supply the CSS yourself, see --css)
//   --css                     print only the CSS
//   --title TEXT              page title (default: the file name)
//   --tab N                   tab width (default 8)
//   --font-size N             SVG: font size in pixels (default 14)

'use strict';

const fs = require('fs');
const path = require('path');
const { tokenize } = require('./a68tokens');

// Scope prefix -> CSS class. For each token the innermost scope that matches
// a prefix wins; of several prefixes the longest (as in TextMate themes).
const CLASSES = [
    ['comment.block.string', 'c'],
    ['comment', 'c'],
    ['keyword.other.todo', 'todo'],
    ['punctuation.definition.pragmat', 'p'],
    ['meta.preprocessor.pragmat', 'p'],
    ['keyword.control.directive', 'pk'],
    ['string', 's'],
    ['meta.format', 'f'],
    ['constant.other.format-item', 'fi'],
    ['constant.numeric.format', 'fi'],
    ['constant.character.escape', 'se'],
    ['invalid', 'err'],
    ['constant.numeric', 'n'],
    ['constant.language', 'cl'],
    ['keyword.control.brief', 'kb'],
    ['keyword.control', 'kc'],
    ['keyword.control.import', 'km'],
    ['keyword.other.module', 'km'],
    ['keyword.other.nest', 'km'],
    ['storage.modifier.pub', 'km'],
    ['storage.type.primitive', 't'],
    ['storage', 'kd'],
    ['keyword.operator.wordlike', 'kd'],
    ['keyword.operator', 'o'],
    ['entity.name.type', 'ty'],
    ['entity.name.namespace', 'ns'],
    ['support.constant.formal-hole', 'ns'],
    ['support.function.operator', 'sop'],
    ['entity.name.function.operator', 'op'],
    ['entity.name.function', 'fn'],
    ['support.function', 'sf'],
    ['support.constant', 'sc'],
    ['entity.name.label', 'lb'],
];

// Colors after the VS Code themes "Light+" and "Dark+".
const LIGHT = {
    bg: '#ffffff', fg: '#000000', ln: '#237893',
    c: 'color:#008000', todo: 'color:#008000;font-weight:bold', p: 'color:#808080',
    pk: 'color:#af00db', s: 'color:#a31515', f: 'color:#a31515', fi: 'color:#811f3f;font-weight:bold', se: 'color:#ee0000',
    err: 'color:#cd3131;text-decoration:underline wavy', n: 'color:#098658',
    cl: 'color:#0000ff', kb: 'color:#af00db', kc: 'color:#af00db', km: 'color:#af00db',
    t: 'color:#0000ff', kd: 'color:#0000ff', o: 'color:#000000',
    ty: 'color:#267f99', ns: 'color:#267f99', sop: 'color:#795e26',
    op: 'color:#795e26', fn: 'color:#795e26', sf: 'color:#795e26',
    sc: 'color:#0070c1', lb: 'color:#811f3f',
};
const DARK = {
    bg: '#1e1e1e', fg: '#d4d4d4', ln: '#858585',
    c: 'color:#6a9955', todo: 'color:#6a9955;font-weight:bold', p: 'color:#9b9b9b',
    pk: 'color:#c586c0', s: 'color:#ce9178', f: 'color:#ce9178', fi: 'color:#d16969;font-weight:bold', se: 'color:#d7ba7d',
    err: 'color:#f44747;text-decoration:underline wavy', n: 'color:#b5cea8',
    cl: 'color:#569cd6', kb: 'color:#c586c0', kc: 'color:#c586c0', km: 'color:#c586c0',
    t: 'color:#569cd6', kd: 'color:#569cd6', o: 'color:#d4d4d4',
    ty: 'color:#4ec9b0', ns: 'color:#4ec9b0', sop: 'color:#dcdcaa',
    op: 'color:#dcdcaa', fn: 'color:#dcdcaa', sf: 'color:#dcdcaa',
    sc: 'color:#4fc1ff', lb: 'color:#d16969',
};

function classOf(scopes) {
    for (let i = scopes.length - 1; i >= 0; i--) {
        const s = scopes[i];
        let best = null;
        let bestLen = -1;
        for (const [prefix, cls] of CLASSES) {
            if ((s === prefix || s.startsWith(prefix + '.')) && prefix.length > bestLen) {
                best = cls;
                bestLen = prefix.length;
            }
        }
        if (best) {
            return best;
        }
    }
    return null;
}

function rules(theme, scope) {
    const out = [`${scope}{background:${theme.bg};color:${theme.fg}}`,
        `${scope} .ln{color:${theme.ln}}`];
    for (const [k, v] of Object.entries(theme)) {
        if (!['bg', 'fg', 'ln'].includes(k)) {
            out.push(`${scope} .${k}{${v}}`);
        }
    }
    return out.join('\n');
}

function css(themeName, tab) {
    const base = `pre.a68{font-family:Consolas,"DejaVu Sans Mono",Menlo,monospace;` +
        `font-size:14px;line-height:1.45;padding:12px 16px;margin:0;overflow-x:auto;` +
        `tab-size:${tab};-moz-tab-size:${tab};border-radius:6px}\n` +
        `pre.a68 .ln{display:inline-block;min-width:3.5em;padding-right:1em;` +
        `text-align:right;user-select:none;-webkit-user-select:none}\n` +
        `pre.a68 .c{font-style:italic}`;
    if (themeName === 'light') {
        return base + '\n' + rules(LIGHT, 'pre.a68');
    }
    if (themeName === 'dark') {
        return base + '\n' + rules(DARK, 'pre.a68');
    }
    return base + '\n' + rules(LIGHT, 'pre.a68') +
        '\n@media (prefers-color-scheme: dark){\n' + rules(DARK, 'pre.a68') + '\n}';
}

function esc(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

async function render(text, opts) {
    const lines = await tokenize(text);
    const width = String(lines.length).length;
    const body = lines.map((toks, i) => {
        let h = opts.lines ? `<span class="ln">${String(i + 1).padStart(width)}</span>` : '';
        for (const t of toks) {
            const cls = /^\s*$/.test(t.text) ? null : classOf(t.scopes);
            h += cls ? `<span class="${cls}">${esc(t.text)}</span>` : esc(t.text);
        }
        return h;
    }).join('\n');
    const pre = `<pre class="a68"><code>${body}\n</code></pre>`;
    const style = opts.style ? `<style>\n${css(opts.theme, opts.tab)}\n</style>\n` : '';
    if (opts.fragment) {
        return style + pre + '\n';
    }
    const bg = opts.theme === 'dark' ? DARK.bg : LIGHT.bg;
    return `<!DOCTYPE html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n` +
        `<meta name="viewport" content="width=device-width, initial-scale=1">\n` +
        `<title>${esc(opts.title)}</title>\n${style}` +
        `<style>body{margin:0;padding:16px;background:${bg}}` +
        (opts.theme === 'auto' ? `@media (prefers-color-scheme: dark){body{background:${DARK.bg}}}` : '') +
        `</style>\n</head>\n<body>\n${pre}\n</body>\n</html>\n`;
}

// ----------------------------------------------------------------------
// SVG.
// ----------------------------------------------------------------------

/** The SVG declarations for a theme entry such as 'color:#a31515;font-weight:bold'. */
function svgDecl(v) {
    return v.split(';').filter((d) => d && !d.startsWith('text-decoration'))
        .map((d) => d.replace(/^color:/, 'fill:')).join(';');
}

function svgRules(theme) {
    const out = [`.bg{fill:${theme.bg}}`, `text{fill:${theme.fg}}`, `.ln{fill:${theme.ln}}`];
    for (const [k, v] of Object.entries(theme)) {
        if (!['bg', 'fg', 'ln'].includes(k)) {
            out.push(`.${k}{${svgDecl(v)}}`);
        }
    }
    return out.join('');
}

/** The tokens of a line with tabs expanded to spaces, and the number of columns. */
function expandTabs(toks, tab) {
    let col = 0;
    const out = [];
    for (const t of toks) {
        let text = '';
        for (const ch of t.text) {
            if (ch === '\t') {
                const n = tab - (col % tab);
                text += ' '.repeat(n);
                col += n;
            } else {
                text += ch;
                col++;
            }
        }
        out.push({ text, scopes: t.scopes });
    }
    return { toks: out, cols: col };
}

/**
 * The program TEXT as an SVG image. Every line is a <text> element in a
 * monospace font. Its textLength is the number of columns times the
 * assumed character width, so that the columns line up and the text fits
 * the image whichever monospace font the viewer has.
 */
async function renderSvg(text, opts) {
    const lines = (await tokenize(text)).map((toks) => {
        const x = expandTabs(toks, opts.tab);
        // Trailing blanks take no room.
        while (x.toks.length > 0 && /^\s*$/.test(x.toks[x.toks.length - 1].text)) {
            x.toks.pop();
        }
        x.cols = x.toks.reduce((n, t) => n + Array.from(t.text).length, 0);
        if (x.toks.length > 0) {
            const last = x.toks[x.toks.length - 1];
            const trimmed = last.text.replace(/\s+$/, '');
            x.cols -= Array.from(last.text).length - Array.from(trimmed).length;
            last.text = trimmed;
        }
        return x;
    });
    const fs = opts.fontSize;
    const cw = fs * 0.6;
    const lh = Math.round(fs * 1.45 * 100) / 100;
    const padX = 16;
    const padY = 12;
    const digits = String(lines.length).length;
    const gutter = opts.lines ? (digits + 2) * cw : 0;
    const cols = Math.max(1, ...lines.map((l) => l.cols));
    const r1 = (v) => Math.round(v * 100) / 100;
    const width = Math.ceil(2 * padX + gutter + cols * cw);
    const height = Math.ceil(2 * padY + lines.length * lh);
    let style = `text{font-family:Consolas,"DejaVu Sans Mono",Menlo,"Liberation Mono",monospace;` +
        `font-size:${fs}px;white-space:pre}.c{font-style:italic}.ln{text-anchor:end}`;
    if (opts.theme === 'dark') {
        style += svgRules(DARK);
    } else {
        style += svgRules(LIGHT);
        if (opts.theme === 'auto') {
            style += `@media (prefers-color-scheme: dark){${svgRules(DARK)}}`;
        }
    }
    const out = [`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" ` +
        `viewBox="0 0 ${width} ${height}" role="img" aria-label="${esc(opts.title)}">`,
    `<title>${esc(opts.title)}</title>`,
    `<style>${style}</style>`,
    `<rect class="bg" width="${width}" height="${height}" rx="6"/>`];
    lines.forEach((l, i) => {
        const y = r1(padY + i * lh + fs * 1.05);
        if (opts.lines) {
            out.push(`<text class="ln" x="${r1(padX + gutter - 2 * cw)}" y="${y}">${i + 1}</text>`);
        }
        if (l.cols === 0) {
            return;
        }
        let h = '';
        for (const t of l.toks) {
            const cls = /^\s*$/.test(t.text) ? null : classOf(t.scopes);
            h += cls ? `<tspan class="${cls}">${esc(t.text)}</tspan>` : esc(t.text);
        }
        out.push(`<text x="${r1(padX + gutter)}" y="${y}" textLength="${r1(l.cols * cw)}" ` +
            `lengthAdjust="spacing" xml:space="preserve">${h}</text>`);
    });
    out.push('</svg>');
    return out.join('\n') + '\n';
}

// ----------------------------------------------------------------------
// Command line.
// ----------------------------------------------------------------------

function usage(msg) {
    if (msg) {
        process.stderr.write(`a68html: ${msg}\n`);
    }
    process.stderr.write('usage: a68html [--svg] [--theme light|dark|auto] [--lines] [--fragment] ' +
        '[--no-style] [--css] [--title T] [--tab N] [--font-size N] [-o file | -d dir] file.a68 ...\n');
    process.exit(2);
}

async function main(argv) {
    const opts = { theme: 'auto', lines: false, fragment: false, style: true, tab: 8, title: null,
        svg: false, fontSize: 14 };
    let out = null;
    let dir = null;
    let cssOnly = false;
    const files = [];
    for (let i = 0; i < argv.length; i++) {
        const a = argv[i];
        const next = () => (i + 1 < argv.length ? argv[++i] : usage(`${a} needs a value`));
        if (a === '--theme') {
            opts.theme = next();
            if (!['light', 'dark', 'auto'].includes(opts.theme)) {
                usage(`unknown color scheme ${opts.theme}`);
            }
        } else if (a === '--svg') {
            opts.svg = true;
        } else if (a === '--lines') {
            opts.lines = true;
        } else if (a === '--fragment') {
            opts.fragment = true;
        } else if (a === '--no-style') {
            opts.style = false;
        } else if (a === '--css') {
            cssOnly = true;
        } else if (a === '--title') {
            opts.title = next();
        } else if (a === '--tab') {
            opts.tab = parseInt(next(), 10) || 8;
        } else if (a === '--font-size') {
            opts.fontSize = parseFloat(next()) || 14;
        } else if (a === '-o') {
            out = next();
        } else if (a === '-d') {
            dir = next();
        } else if (a === '-h' || a === '--help') {
            usage();
        } else if (a.startsWith('--')) {
            usage(`unknown option ${a}`);
        } else {
            files.push(a);
        }
    }
    if (cssOnly) {
        process.stdout.write(css(opts.theme, opts.tab) + '\n');
        return;
    }
    if (files.length === 0) {
        usage('no input file');
    }
    if (files.length > 1 && !dir) {
        usage('several files need -d directory');
    }
    if (out && /\.svg$/i.test(out)) {
        opts.svg = true;
    }
    for (const f of files) {
        const text = f === '-' ? fs.readFileSync(0, 'utf8') : fs.readFileSync(f, 'utf8');
        const o = { ...opts, title: opts.title || (f === '-' ? 'Algol 68' : path.basename(f)) };
        const result = o.svg ? await renderSvg(text, o) : await render(text, o);
        if (dir) {
            fs.mkdirSync(dir, { recursive: true });
            const ext = o.svg ? '.svg' : '.html';
            fs.writeFileSync(path.join(dir, path.basename(f).replace(/\.a68$/, '') + ext), result);
        } else if (out) {
            fs.writeFileSync(out, result);
        } else {
            process.stdout.write(result);
        }
    }
}

module.exports = { render, renderSvg, css, classOf };

if (require.main === module) {
    main(process.argv.slice(2)).catch((e) => {
        process.stderr.write(`a68html: ${e.stack || e}\n`);
        process.exit(1);
    });
}
