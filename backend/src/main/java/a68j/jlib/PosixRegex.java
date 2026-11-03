// a68j Java library: regular expressions for the module Regex.
//
// Copyright (C) 2026 The a68j authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH GCC-exception-3.1
//
// This file is part of a68j. It is licensed under the GNU General Public
// License, version 3 or later. Under Section 7 of GPL version 3, you are
// granted additional permissions described in the GCC Runtime Library
// Exception, version 3.1, as published by the Free Software Foundation; see
// the files COPYING and COPYING.RUNTIME.
//
// Additional permission under GNU GPL version 3 section 7: for this file, a
// Compilation Process done with a68j, alone or with other GPL-compatible
// software, is an Eligible Compilation Process in the sense of the GCC
// Runtime Library Exception, version 3.1.
package a68j.jlib;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The Java side of the Algol 68 module Regex (backend/lib/regex.a68): the
 * regular expressions of Algol 68 Genie (grep in string, grep in
 * substring, sub in string) on top of java.util.regex. Only static
 * methods, as they are called through a formal hole (nest Java).
 *
 * <p>Algol 68 Genie passes the pattern to the POSIX regcomp with
 * REG_EXTENDED and REG_NEWLINE. A pattern is translated before it is
 * compiled, so that the POSIX forms that Java reads differently mean the
 * same (see {@link #translate}); the flags MULTILINE and UNIX_LINES give the
 * meaning of REG_NEWLINE ("^" and "$" also at a newline, "." and a
 * non-matching list such as "[^a]" not matching a newline). What stays
 * different, and is documented in docs/regex.md: Java takes the leftmost
 * match of the first alternative that fits, POSIX the leftmost longest
 * match; patterns that only Java accepts work instead of being invalid.
 *
 * <p>Positions are counted in characters (code points), as an Algol 68
 * string counts them, not in UTF-16 units.
 */
public final class PosixRegex {
    private PosixRegex() {}

    /** The results of {@link #grep} that are not a match. */
    static final long NO_MATCH = -1, INVALID = -2;

    /** The POSIX character classes and their Java equivalents (ASCII). */
    private static final Map<String, String> CLASSES = Map.ofEntries(
            Map.entry("alnum", "\\p{Alnum}"), Map.entry("alpha", "\\p{Alpha}"),
            Map.entry("blank", "\\p{Blank}"), Map.entry("cntrl", "\\p{Cntrl}"),
            Map.entry("digit", "\\p{Digit}"), Map.entry("graph", "\\p{Graph}"),
            Map.entry("lower", "\\p{Lower}"), Map.entry("print", "\\p{Print}"),
            Map.entry("punct", "\\p{Punct}"), Map.entry("space", "\\p{Space}"),
            Map.entry("upper", "\\p{Upper}"), Map.entry("xdigit", "\\p{XDigit}"));

    /**
     * The compiled patterns most recently used, by their POSIX text; null
     * for an invalid pattern. A program typically uses a few patterns many
     * times (in a loop over the lines of a file, say).
     */
    private static final int CACHE_SIZE = 64;
    private static final Map<String, Pattern> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Pattern> e) {
            return size() > CACHE_SIZE;
        }
    };

    /** The compiled pattern, or null if it is invalid (synchronized: PAR). */
    static synchronized Pattern compiled(String posix) {
        if (CACHE.containsKey(posix)) {
            return CACHE.get(posix);
        }
        Pattern p = compile(posix);
        CACHE.put(posix, p);
        return p;
    }

    private static Pattern compile(String posix) {
        Translation t = translate(posix);
        if (t == null) {
            return null;
        }
        try {
            Pattern p = Pattern.compile(t.java, Pattern.MULTILINE | Pattern.UNIX_LINES);
            // Java accepts a back-reference to a group that does not exist
            // (it never matches); POSIX rejects it.
            return t.maxBackReference > p.matcher("").groupCount() ? null : p;
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    /**
     * The first match of PATTERN in S: (start << 32) | end, in characters
     * from the start of S, with the end exclusive; NO_MATCH, or INVALID if
     * the pattern is invalid. With NOT_BOL "^" does not match at the start
     * of S (REG_NOTBOL, grep in substring): the matcher works on a region
     * after one extra character U+0000, with transparent bounds, so that
     * "^" (a look-behind, see {@link #translate}) sees a character that is
     * not a newline there, and "\b" one that is not a letter.
     */
    public static long grep(String pattern, String s, boolean notBol) {
        Pattern p = compiled(pattern);
        if (p == null) {
            return INVALID;
        }
        int shift = notBol ? 1 : 0;
        Matcher m;
        if (notBol) {
            m = p.matcher("\u0000" + s);
            m.region(1, s.length() + 1);
            m.useAnchoringBounds(false);
            m.useTransparentBounds(true);
        } else {
            m = p.matcher(s);
        }
        if (!m.find()) {
            return NO_MATCH;
        }
        int a = m.start() - shift, b = m.end() - shift;
        long start = s.codePointCount(0, a);
        long end = start + s.codePointCount(a, b);
        return start << 32 | end;
    }

    /** A pattern in Java syntax, with the highest back-reference \1 ... \9 in it. */
    record Translation(String java, int maxBackReference) {}

    /**
     * The Java form of a POSIX extended regular expression, or null if it
     * is invalid. Outside a bracket expression only these differ:
     * "^" becomes "(?<![^\n])", the start of S or a position after a
     * newline (in Java "^" does not match at the end of the input, so not
     * in an empty S, nor after a newline at its end); "\<" and "\>" (the
     * start and the end of a word, a GNU extension) become "\b(?=\w)" and
     * "\b(?<=\w)"; "{,n}" becomes "{0,n}".
     * Everything else is copied, so that the forms both know (and the
     * forms only Java knows, such as \d) keep their meaning.
     */
    static Translation translate(String p) {
        StringBuilder out = new StringBuilder(p.length() + 8);
        int maxRef = 0;
        int n = p.length();
        int i = 0;
        while (i < n) {
            char c = p.charAt(i);
            if (c == '\\') {
                if (i + 1 >= n) {
                    return null;
                }
                char d = p.charAt(i + 1);
                if (d == '<') {
                    out.append("\\b(?=\\w)");
                } else if (d == '>') {
                    out.append("\\b(?<=\\w)");
                } else {
                    if (d >= '1' && d <= '9') {
                        maxRef = Math.max(maxRef, d - '0');
                    }
                    out.append(c).append(d);
                }
                i += 2;
            } else if (c == '[') {
                i = bracket(p, i, out);
                if (i < 0) {
                    return null;
                }
            } else if (c == '^') {
                out.append("(?<![^\\n])");
                i++;
            } else if (c == '{' && i + 1 < n && p.charAt(i + 1) == ',') {
                out.append("{0");
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return new Translation(out.toString(), maxRef);
    }

    /**
     * Translates the bracket expression of P that starts at I to OUT; the
     * index after it, or -1 if it is invalid. In POSIX a backslash is an
     * ordinary character there, "]" is one if it comes first, and so are
     * "[" and "&"; Java needs a backslash before all of them. A character
     * class "[:alpha:]" becomes "\p{Alpha}"; an equivalence class "[=a=]"
     * and a collating symbol "[.a.]" of a single character become that
     * character, longer ones are invalid. A non-matching list gets "\n",
     * as with REG_NEWLINE it does not match a newline.
     */
    private static int bracket(String p, int i, StringBuilder out) {
        int n = p.length();
        int j = i + 1;
        out.append('[');
        boolean negated = j < n && p.charAt(j) == '^';
        if (negated) {
            out.append('^');
            j++;
        }
        boolean first = true;
        while (true) {
            if (j >= n) {
                return -1;
            }
            int c = p.codePointAt(j);
            if (c == ']' && !first) {
                break;
            }
            first = false;
            char kind = j + 1 < n ? p.charAt(j + 1) : 0;
            if (c == '[' && (kind == ':' || kind == '=' || kind == '.')) {
                int close = p.indexOf(new String(new char[] {kind, ']'}), j + 2);
                if (close < 0) {
                    return -1;
                }
                String name = p.substring(j + 2, close);
                if (kind == ':') {
                    String cls = CLASSES.get(name);
                    if (cls == null) {
                        return -1;
                    }
                    out.append(cls);
                } else {
                    if (name.isEmpty() || name.codePointCount(0, name.length()) != 1) {
                        return -1;
                    }
                    literal(name.codePointAt(0), out);
                }
                j = close + 2;
            } else {
                if (c == '-') {
                    // A range, or "-" itself at the start or the end: the same in Java.
                    out.append('-');
                } else {
                    literal(c, out);
                }
                j += Character.charCount(c);
            }
        }
        if (negated) {
            out.append("\\n");
        }
        out.append(']');
        return j + 1;
    }

    /** The character C as a member of a Java character class. */
    private static void literal(int c, StringBuilder out) {
        if (c == '\\' || c == '[' || c == ']' || c == '&' || c == '^' || c == '-') {
            out.append('\\');
        }
        out.appendCodePoint(c);
    }
}
