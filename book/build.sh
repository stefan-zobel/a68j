#!/bin/sh
# build.sh [pdf|html|all]: builds the book into build/.
#
#   pdf   build/book.pdf (xelatex, with latexmk)
#   html  build/html/ (pandoc; one page per chapter)
#   all   both (the default)
#
# The listings of the example programs are extracted first
# (tools/run_examples.py --extract).  The outputs shown in the book are the
# recorded outputs examples/*/*.out; to check them against ga68 and a68j,
# run tools/run_examples.py (see its usage).
set -e
cd "$(dirname "$0")"
what=${1:-all}
python3 tools/run_examples.py --extract build/listings
# The version is that of a68j (../VERSION); the date is the day of the build.
version=$(cat ../VERSION)
date=$(LC_ALL=C date '+%-d %B %Y')
mkdir -p build
printf '\\newcommand{\\bookversion}{%s}\n\\newcommand{\\bookdate}{%s}\n' \
  "$version" "$date" > build/version.tex
if [ "$what" = pdf ] || [ "$what" = all ]; then
  latexmk -xelatex -outdir=build -interaction=nonstopmode -halt-on-error -quiet book.tex
  echo "build/book.pdf"
fi
if [ "$what" = html ] || [ "$what" = all ]; then
  rm -rf build/html
  pandoc book.tex -f latex -t chunkedhtml --split-level=1 --toc --number-sections -s \
    --lua-filter html/algol68.lua --css style.css \
    --metadata title="Algol 68 in Modern Terms" \
    --metadata date="Version $version, $date" -o build/html
  cp html/style.css build/html/
  echo "build/html/index.html"
fi
