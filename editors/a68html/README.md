# a68html – Algol 68 as colored HTML or SVG

Renders Algol 68 source (GNU, SUPPER stropping) as colored HTML or SVG. The
colors come from the same TextMate grammar as in VS Code
(`../vscode-algol68/syntaxes/algol68.tmLanguage.json`), and `a68html` uses
the same libraries as VS Code to apply it (`vscode-textmate` and the
Oniguruma regex engine as WebAssembly). So the output looks token for token
like the editor.

## Setup

Node.js 18 or later, then once in this directory:

    npm install

## Usage

    node a68html.js example.a68 > example.html           HTML on stdout
    node a68html.js --lines -o example.html example.a68  with line numbers
    node a68html.js -o example.svg example.a68           SVG (from the file name)
    node a68html.js --svg -d img/ tests/rosetta/*.a68     one .svg per file
    type prog.a68 | node a68html.js -                    from stdin (Windows)

| Option | Effect |
|---|---|
| `--svg` | SVG instead of HTML (implied by `-o name.svg`) |
| `--theme light\|dark\|auto` | color scheme; `auto` (the default) follows the operating system |
| `--lines` | line numbers (in HTML not included when text is selected and copied) |
| `--fragment` | HTML: only `<style>` and `<pre>`, for embedding in a page of your own |
| `--no-style` | HTML: no `<style>`; generate the CSS once with `--css` |
| `--css` | print only the CSS |
| `--title TEXT` | page title (default: the file name) |
| `--tab N` | tab width (default 8) |
| `--font-size N` | SVG: font size in pixels (default 14) |

In HTML every token gets a short CSS class (`kc` control word, `ty` mode,
`op` operator, `s` string, `c` comment …); the colors are given once in the
`<style>` block, after the VS Code themes "Light+" and "Dark+". The mapping
from scopes to classes is at the top of `a68html.js`.

## SVG for GitHub

GitHub has no syntax highlighting for Algol 68, and it removes `style`
attributes and `<style>` elements from HTML in Markdown files. Images are
shown as they are, so a program rendered as SVG keeps its colors:

    node a68html.js --lines -o docs/img/primes.svg examples/primes.a68

```markdown
![primes.a68](docs/img/primes.svg)
```

How the SVG is made:

- Every line is one `<text>` element in a monospace font (Consolas, DejaVu
  Sans Mono, Menlo, Liberation Mono or the browser's `monospace`), the tokens
  are `<tspan>` elements with a class, and the colors are in a `<style>`
  element inside the SVG.
- The width of a line is fixed with `textLength` (columns × 0.6 × font
  size). Whatever monospace font the viewer has, the columns line up and the
  text fits the image.
- Tabs are expanded to spaces (`--tab`), trailing blanks are dropped.
- With `--theme auto` the SVG contains both color schemes and picks one with
  `@media (prefers-color-scheme: dark)`. In an image that follows the
  setting of the browser or operating system, not the theme chosen on
  GitHub. To follow the GitHub theme, render both schemes and let GitHub
  choose:

```html
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/img/primes-dark.svg">
  <img alt="primes.a68" src="docs/img/primes-light.svg">
</picture>
```

  (`--theme dark -o docs/img/primes-dark.svg` and
  `--theme light -o docs/img/primes-light.svg`.)
- The text in an image cannot be selected or copied. Link the source file
  next to the picture if readers should be able to copy it.

## As a library

```js
const { render, renderSvg } = require('./a68html');
const html = await render(text, { theme: 'auto', lines: true, fragment: true,
                                  style: true, tab: 8, title: 'x' });
const svg = await renderSvg(text, { theme: 'auto', lines: true, tab: 8,
                                    fontSize: 14, title: 'x' });
```

## Checking the grammar

`check_grammar.js` compares the grammar with the scanner of a68j (see
`docs/editor.md`). Most easily with

    sh tools/grammar_check.sh            (in the a68j main directory)
