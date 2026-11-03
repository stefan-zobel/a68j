-- Pandoc filter for the HTML version of the book: prints the reserved
-- words of Algol 68 in bold and comments in italics, as the LaTeX
-- listings style does (preamble.tex), and marks program output and shell
-- sessions with a class for the style sheet.

local reserved = {}
for w in ([[access andth assert at begin bits bool by bytes case channel char
  compl def do egg elif else empty end esac exit false fed fi file flex
  for format from go goto heap if in int is isnt loc long mode module
  nest nil od of op orel ouse out par postlude prio proc pub real ref
  sema short skip string struct then to true union void while word]]):gmatch("%S+") do
  reserved[w] = true
end

local function esc(s)
  return (s:gsub("&", "&amp;"):gsub("<", "&lt;"):gsub(">", "&gt;"))
end

-- The text of a program fragment as HTML.
local function algol(src)
  local out, i, n = {}, 1, #src
  while i <= n do
    local c = src:sub(i, i)
    if c == "{" then                      -- comment, possibly nested
      local depth, j = 0, i
      repeat
        local d = src:sub(j, j)
        if d == "{" then depth = depth + 1 elseif d == "}" then depth = depth - 1 end
        j = j + 1
      until depth == 0 or j > n
      out[#out + 1] = '<span class="comment">' .. esc(src:sub(i, j - 1)) .. '</span>'
      i = j
    elseif c == '"' then                  -- string, "" inside is a quote
      local j = i + 1
      while j <= n do
        if src:sub(j, j) == '"' then
          if src:sub(j + 1, j + 1) == '"' then j = j + 2 else break end
        else j = j + 1 end
      end
      out[#out + 1] = esc(src:sub(i, j))
      i = j + 1
    elseif c:match("[%a_]") then
      local w = src:match("^[%w_]+", i)
      out[#out + 1] = reserved[w] and ("<b>" .. w .. "</b>") or esc(w)
      i = i + #w
    else
      out[#out + 1] = esc(c)
      i = i + 1
    end
  end
  return table.concat(out)
end

function CodeBlock(el)
  local style = el.attributes["style"]
  -- A listing in another language (the C file of Appendix F) is shown
  -- as it is.
  if style == "output" or style == "shell" or el.attributes["language"] == "C" then
    return pandoc.RawBlock("html", '<pre class="' .. (style or "other") .. '"><code>' .. esc(el.text) .. "</code></pre>")
  end
  return pandoc.RawBlock("html", '<pre class="algol68"><code>' .. algol(el.text) .. "</code></pre>")
end

function Code(el)
  return pandoc.RawInline("html", "<code>" .. algol(el.text) .. "</code>")
end

-- Appendices: the LaTeX source numbers them A, B, ... after \appendix,
-- which pandoc ignores. Their chapter labels begin with "app-"; they are
-- given the letters here and left out of the section numbering.
local appendix = 0
-- Letter of each appendix, by its label, for the references to it.
local appendix_letter = {}
function Header(el)
  if el.level == 1 and el.identifier:match("^app%-") then
    appendix = appendix + 1
    local letter = string.char(64 + appendix)
    appendix_letter[el.identifier] = letter
    el.classes:insert("unnumbered")
    table.insert(el.content, 1, pandoc.Str("Appendix " .. letter .. ": "))
    return el
  end
end

-- A reference to an appendix shows its letter, not the chapter number
-- that pandoc counts on through the appendices.
function Pandoc(doc)
  return doc:walk {
    Link = function(el)
      local letter = appendix_letter[el.attributes["reference"] or ""]
      if letter and el.attributes["reference-type"] == "ref" then
        el.content = {pandoc.Str(letter)}
        return el
      end
    end
  }
end

-- The license, which the PDF gives on the title page (\publishers in
-- book.tex, which pandoc does not read), under the title of the HTML book.
function Meta(m)
  local url = "https://creativecommons.org/publicdomain/zero/1.0/"
  m["abstract-title"] = "License"
  m.abstract = pandoc.MetaBlocks {
    pandoc.Para {
      pandoc.Str("This book is dedicated to the public domain under the "),
      pandoc.Link("Creative Commons CC0 1.0 Universal Public Domain Dedication", url),
      pandoc.Str(".")
    }
  }
  return m
end

-- Bibliography. Pandoc reads the thebibliography environment as a Div
-- without the keys of the \bibitem entries and leaves \cite without
-- citeproc empty; the keys are therefore taken from chapters/biblio.tex,
-- the entries are numbered in their order, given a heading of their own
-- (and so a page of their own), and each \cite becomes "[n]" linked to
-- its entry.
local bibkeys, bibnum = {}, {}
do
  local f = io.open("chapters/biblio.tex")
  if f then
    for k in f:read("a"):gmatch("\\bibitem{([^}]*)}") do
      table.insert(bibkeys, k)
      bibnum[k] = #bibkeys
    end
    f:close()
  end
end

function Cite(el)
  local out = pandoc.List {pandoc.Str("[")}
  for i, c in ipairs(el.citations) do
    if i > 1 then out:insert(pandoc.Str(", ")) end
    local n = bibnum[c.id]
    if n then
      out:insert(pandoc.Link(tostring(n), "#bib-" .. c.id))
    else
      out:insert(pandoc.Str("?"))
    end
  end
  out:insert(pandoc.Str("]"))
  return out
end

function Div(el)
  if el.classes:includes("thebibliography") then
    local blocks = pandoc.List {
      pandoc.Header(1, "Bibliography", pandoc.Attr("bibliography", {"unnumbered"}))
    }
    -- The first paragraph is the widest label ("99") of the environment.
    for i = 2, #el.content do
      local n = i - 1
      local entry = pandoc.List {pandoc.Str("[" .. n .. "]"), pandoc.Space()}
      entry:extend(el.content[i].content)
      blocks:insert(pandoc.Div({pandoc.Para(entry)},
                               pandoc.Attr("bib-" .. (bibkeys[n] or n))))
    end
    return blocks
  end
end
