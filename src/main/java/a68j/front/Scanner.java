// Java port of ga68's a68-parser-scanner.cc: context-dependent Algol 68 tokeniser.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Context-dependent Algol 68 tokeniser, supporting the UPPER and SUPPER
 * stropping regimes.
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>A C {@code char *} pointing into a line is represented by a
 * {@link Cursor}: the line ({@code LINE_T *}), the text the pointer points into
 * and an index. As in C, the text stays valid when the line pointer has run off
 * the end of the line list.</li>
 * <li>The scratch buffer {@code scan_buf} is a {@code char[]} with explicit NUL
 * terminators, so that the buffer arithmetic of the original (including
 * {@code strlen (sym)} on stale buffer contents) behaves identically.</li>
 * <li>The source is read byte-wise (ISO-8859-1), like the C version.</li>
 * <li>{@code SCAN_ERROR} throws {@link Diagnostics.ScanError}; in C it exits the
 * compiler.</li>
 * </ul>
 * Quirks of the original are preserved on purpose and marked with "Sic".
 */
public final class Scanner {

    /** Standard prelude and postlude for particular programs, per stropping regime. */
    private static final String[] UPPER_PRELUDE_START = {"BEGIN", "      BEGIN"};
    private static final String[] UPPER_POSTLUDE = {"      END;", "      stop: SKIP", "END"};
    private static final String[] SUPPER_PRELUDE_START = {"begin", "     begin"};
    private static final String[] SUPPER_POSTLUDE = {"      end;", "      stop: skip", "end"};

    static final char NULL_CHAR = '\0';
    static final char STOP_CHAR = 127;
    static final char FORMFEED_CHAR = '\f';
    static final char CR_CHAR = '\r';
    static final char SINGLE_QUOTE_CHAR = '\'';
    static final char QUOTE_CHAR = '"';
    static final char APOSTROPHE_CHAR = '\'';
    static final char BACKSLASH_CHAR = '\\';
    static final char NEWLINE_CHAR = '\n';
    static final char EXPONENT_CHAR = 'e';
    static final char RADIX_CHAR = 'r';
    static final char POINT_CHAR = '.';

    /** a68.h */
    static final String MONADS = "%^&+-~!?";
    static final String NOMADS = "></=*";
    static final int BUFFER_SIZE = 1024;

    /** Result of {@link #lexicalAnalyser}. */
    public record Result(boolean emptyProgram) {}

    /**
     * A position in the internal source: the C pair {@code (LINE_T *, char *)}.
     * {@code text} is the string {@code s} points into; it may belong to a
     * line other than {@code l} (or {@code l} may be null) exactly when it does
     * so in C.
     */
    static final class Cursor {
        Line l;
        String t;
        int s;

        Cursor(Line l, String t, int s) {
            this.l = l;
            this.t = t;
            this.s = s;
        }

        Cursor copy() {
            return new Cursor(l, t, s);
        }

        void set(Cursor o) {
            l = o.l;
            t = o.t;
            s = o.s;
        }

        /** v[k] */
        char at(int k) {
            int i = s + k;
            return t != null && i >= 0 && i < t.length() ? t.charAt(i) : NULL_CHAR;
        }

        /** v[0] */
        char ch() {
            return at(0);
        }

        /** v = &(STRING (u)[0]), or NO_TEXT if u is null. */
        void startOf(Line u) {
            l = u;
            if (u != null) {
                t = u.string;
                s = 0;
            } else {
                t = null;
                s = 0;
            }
        }

        /** v == NO_TEXT */
        boolean noText() {
            return t == null;
        }

        /** strlen (v) */
        int strlen() {
            if (t == null) {
                return 0;
            }
            int end = t.indexOf(NULL_CHAR, s);
            return (end < 0 ? t.length() : end) - s;
        }
    }

    private final A68 a68;
    private final Options opts;
    private final ParserState ps;
    private final Diagnostics diag;

    /** The tokeniser's position (l, s in a68_lexical_analyser). */
    private final Cursor cur = new Cursor(null, null, 0);
    /** Start of the current token (start_l, start_c). */
    private Line startL;
    private int startC;
    /** Last node appended to the token list (root). */
    private Node root;
    /** Write index into scan_buf (sym in get_next_token). */
    private int sym;

    public Scanner(A68 a68) {
        this.a68 = a68;
        this.opts = a68.options();
        this.ps = a68.parserState;
        this.diag = a68.diagnostics;
    }

    private boolean upper() {
        return opts.stropping == Stropping.UPPER;
    }

    private boolean supper() {
        return opts.stropping == Stropping.SUPPER;
    }

    /** SCAN_ERROR (c, u, v, txt) with v given as an index into u's text (-1 = NO_TEXT). */
    private void scanError(boolean c, Line u, int v, String txt) {
        if (c) {
            throw diag.scanError(u, v, txt);
        }
    }

    private void scanError(boolean c, Cursor at, String txt) {
        if (c) {
            throw diag.scanError(at.l, at.s, txt);
        }
    }

    // ------------------------------------------------------------------
    // Scanner state for character look-ahead.
    // ------------------------------------------------------------------

    private String saveT;

    /** save_state */
    private void saveState(char ch) {
        a68.job.scanStateL = cur.l;
        saveT = cur.t;
        a68.job.scanStateS = cur.s;
        a68.job.scanStateC = ch;
    }

    /** restore_state; returns the saved character. */
    private char restoreState() {
        cur.l = a68.job.scanStateL;
        cur.t = saveT;
        cur.s = a68.job.scanStateS;
        return a68.job.scanStateC;
    }

    // ------------------------------------------------------------------
    // Reading the source file.
    // ------------------------------------------------------------------

    /** append_source_line; returns the new last line. */
    private Line appendSourceLine(String str, Line refL, String filename, int[] lineNum) {
        Line z = new Line(str, filename, lineNum[0]);
        z.maps = a68.lineTable;
        z.location = a68.lineTable.lineStart(lineNum[0]++, 80 /* max_column_hint */);
        z.next = null;
        z.previous = refL;
        if (a68.job.topLine == null) {
            a68.job.topLine = z;
        }
        if (refL != null) {
            refL.next = z;
        }
        return z;
    }

    /** append_environ */
    private Line appendEnviron(String[] str, Line refL, String name) {
        a68.lineTable.add(LineMaps.Reason.LC_ENTER, true /* sys_p */, name, 0 /* to_line */);
        for (String s : str) {
            int[] lineNum = {1};
            refL = appendSourceLine(s, refL, name, lineNum);
        }
        a68.lineTable.add(LineMaps.Reason.LC_LEAVE, false, null, 0);
        return refL;
    }

    /** unworthy: emit a diagnostic if CH is an unworthy character. */
    private void unworthy(Line u, int v, char ch) {
        // Sic: the test is inverted in the original.
        if (CType.isPrint(ch)) {
            throw diag.scanError(u, v, "*%s", "unworthy character");
        } else {
            throw diag.scanError(u, v, "*%s %c", "unworthy character", ch);
        }
    }

    /** Write a C string into scan_buf at offset 0, followed by NUL. */
    private void scanBufPut(int at, char c) {
        ensureScanBuf(at + 2);
        ps.scanBuf[at] = c;
    }

    private void ensureScanBuf(int size) {
        if (ps.scanBuf == null || ps.scanBuf.length < size) {
            char[] n = new char[Math.max(size, ps.scanBuf == null ? 0 : ps.scanBuf.length * 2)];
            if (ps.scanBuf != null) {
                System.arraycopy(ps.scanBuf, 0, n, 0, ps.scanBuf.length);
            }
            ps.scanBuf = n;
        }
    }

    /** read_source_file: read FILENAME and make the internal copy. */
    private boolean readSourceFile(String filename) {
        Path path = Paths.get(filename);
        if (!Files.exists(path)) {
            throw diag.fatal("could not open source file " + filename);
        }
        if (Files.isDirectory(path)) {
            throw diag.fatal("specified file " + filename + " is a directory");
        }
        a68.job.fileSourceName = filename;
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw diag.fatal("could not open source file " + filename);
        }
        String buffer = new String(bytes, StandardCharsets.ISO_8859_1);
        int sourceFileSize = buffer.length();

        // scan_buf is an auxiliary buffer known to be big enough to hold any
        // string contained in the source file.
        ps.maxScanBufLength = sourceFileSize + 1 + 1024; // +1024 for the environment.
        ps.scanBuf = new char[ps.maxScanBufLength];

        Line refL = null;
        // Prelude.
        refL = appendEnviron(upper() ? UPPER_PRELUDE_START : SUPPER_PRELUDE_START, refL, "prelude");

        // Link all lines into the list. As in C, each line is assembled in
        // scan_buf first (this determines the stale buffer contents later on).
        int[] lineNum = {1};
        a68.lineTable.add(LineMaps.Reason.LC_ENTER, false /* sys_p */, filename, 0 /* to_line */);
        int k = 0;
        char[] sb = ps.scanBuf;
        while (k < sourceFileSize) {
            int l = 0;
            sb[0] = NULL_CHAR;
            while (k < sourceFileSize && buffer.charAt(k) != NEWLINE_CHAR) {
                if (k < sourceFileSize - 1 && buffer.charAt(k) == CR_CHAR
                        && buffer.charAt(k + 1) == NEWLINE_CHAR) {
                    k++;
                } else {
                    sb[l++] = buffer.charAt(k++);
                    sb[l] = NULL_CHAR;
                }
            }
            sb[l++] = NEWLINE_CHAR;
            sb[l] = NULL_CHAR;
            if (k < sourceFileSize) {
                k++;
            }
            String text = CType.cString(sb, 0);
            refL = appendSourceLine(text, refL, filename, lineNum);
            scanError(l != text.length(), null, -1, "invalid characters in source file");
        }

        // Extra line before adding postlude.
        a68.lineTable.lineStart(lineNum[0], 80 /* max_column_hint */);

        // Postlude.
        refL = appendEnviron(upper() ? UPPER_POSTLUDE : SUPPER_POSTLUDE, refL, "postlude");

        // Include files.
        includeFiles(a68.job.topLine);

        a68.lineTable.add(LineMaps.Reason.LC_LEAVE, false, null, 0);
        return true;
    }

    // ------------------------------------------------------------------
    // Character level.
    // ------------------------------------------------------------------

    /**
     * next_char: get the next character from the internal copy of the source.
     * If allowTypo, typographical display features are skipped. If
     * allowOneUnder, a single underscore is skipped (and foundUnder[0] set).
     */
    private char nextChar(Cursor c, boolean allowTypo, boolean allowOneUnder, boolean[] foundUnder) {
        // Empty source.
        if (c.l == null) {
            return STOP_CHAR;
        }
        if (c.ch() == NEWLINE_CHAR || c.ch() == NULL_CHAR) {
            // Go to new line.
            c.l = c.l.next;
            if (c.l == null) {
                return STOP_CHAR;
            }
            c.t = c.l.string;
            c.s = 0;
        } else {
            c.s++;
        }
        // Skip backslash at the end of lines.
        if (c.ch() == BACKSLASH_CHAR && c.at(1) == NEWLINE_CHAR) {
            c.s++;
            return nextChar(c, allowTypo, allowOneUnder, foundUnder);
        }
        // Deliver next char.
        char ch = c.ch();
        if ((allowTypo && (CType.isSpace(ch) || ch == FORMFEED_CHAR)) || (allowOneUnder && ch == '_')) {
            if (ch == '_' && foundUnder != null) {
                foundUnder[0] = true;
            }
            ch = nextChar(c, allowTypo, false, null);
        }
        return ch;
    }

    private char nextChar(boolean allowTypo) {
        return nextChar(cur, allowTypo, false, null);
    }

    /** get_good_char: find the first character that can start a valid symbol. */
    private char getGoodChar(char c) {
        while (c != STOP_CHAR && (CType.isSpace(c) || c == NULL_CHAR)) {
            c = nextChar(false);
        }
        return c;
    }

    /** streq: case-insensitive comparison of u against v, up to the end of either. */
    static int streq(Cursor u, String v) {
        int diff = 0;
        int i = 0;
        while (diff == 0 && u.at(i) != NULL_CHAR && i < v.length()) {
            diff = CType.toLower(u.at(i)) - CType.toLower(v.charAt(i));
            i++;
        }
        return diff;
    }

    /** strneq: case-insensitive comparison for at most n chars. */
    static int strneq(Cursor u, String v, int n) {
        int diff = 0;
        int pos = 0;
        while (diff == 0 && u.at(pos) != NULL_CHAR && pos < v.length() && pos < n) {
            diff = CType.toLower(u.at(pos)) - CType.toLower(v.charAt(pos));
            pos++;
        }
        return diff;
    }

    /** is_bold: whether the text at u is bold tag v, for the current stropping regime. */
    private boolean isBold(Cursor u, String v) {
        int len = v.length();
        if (upper()) {
            // UPPER stropping: strncmp (u, v, len) == 0 && !ISUPPER (u[len]).
            for (int i = 0; i < len; i++) {
                if (u.at(i) != v.charAt(i)) {
                    return false;
                }
            }
            return !CType.isUpper(u.at(len));
        } else {
            // SUPPER stropping.
            return u.strlen() >= len
                    && CType.isLower(u.at(0))
                    && strneq(u, v, len) == 0
                    && !CType.isAlpha(u.at(len))
                    && !CType.isDigit(u.at(len));
        }
    }

    // ------------------------------------------------------------------
    // Skipping strings, comments and pragmats (used when looking for INCLUDE).
    // ------------------------------------------------------------------

    /** skip_string: returns true if the end of the string denotation is found. */
    private boolean skipString(Cursor top) {
        Cursor v = top.copy();
        v.s++;
        while (v.l != null) {
            while (v.ch() != NULL_CHAR) {
                if (v.ch() == QUOTE_CHAR && v.at(1) != QUOTE_CHAR) {
                    top.set(v);
                    top.s++;
                    return true;
                } else if (v.ch() == QUOTE_CHAR && v.at(1) == QUOTE_CHAR) {
                    v.s += 2;
                } else {
                    v.s++;
                }
            }
            v.startOf(v.l.next);
        }
        return false;
    }

    /** skip_comment: returns true if the end of the comment is found. */
    private boolean skipComment(Cursor top, Attribute delim) {
        Cursor v = top.copy();
        int nestingLevel = 1;
        v.s++;
        while (v.l != null) {
            while (v.ch() != NULL_CHAR) {
                Cursor c = v.copy();
                if (v.ch() == QUOTE_CHAR && skipString(c)
                        && (delim == BOLD_COMMENT_BEGIN_SYMBOL || delim == BRIEF_COMMENT_BEGIN_SYMBOL)) {
                    v.set(c);
                } else if (isBold(v, "COMMENT") && delim == BOLD_COMMENT_SYMBOL) {
                    top.set(v);
                    top.s++;
                    return true;
                } else if (isBold(v, "CO") && delim == STYLE_I_COMMENT_SYMBOL) {
                    top.set(v);
                    top.s++;
                    return true;
                } else if (v.ch() == '#' && delim == STYLE_II_COMMENT_SYMBOL) {
                    top.set(v);
                    top.s++;
                    return true;
                } else if (isBold(v, "ETON") && delim == BOLD_COMMENT_BEGIN_SYMBOL) {
                    nestingLevel -= 1;
                    if (nestingLevel == 0) {
                        top.set(v);
                        top.s++;
                        return true;
                    }
                } else if (v.ch() == '}' && delim == BRIEF_COMMENT_BEGIN_SYMBOL) {
                    nestingLevel -= 1;
                    if (nestingLevel == 0) {
                        top.set(v);
                        top.s++;
                        return true;
                    }
                } else {
                    if ((isBold(v, "NOTE") && delim == BOLD_COMMENT_BEGIN_SYMBOL)
                            || (v.ch() == '{' && delim == BRIEF_COMMENT_BEGIN_SYMBOL)) {
                        nestingLevel += 1;
                    }
                    v.s++;
                }
            }
            v.startOf(v.l.next);
        }
        return false;
    }

    /** skip_pragmat: returns true if the end of the pragmat is found. */
    private boolean skipPragmat(Cursor top, Attribute delim, boolean whitespace) {
        Cursor v = top.copy();
        while (v.l != null) {
            while (v.ch() != NULL_CHAR) {
                if (isBold(v, "PRAGMAT") && delim == BOLD_PRAGMAT_SYMBOL) {
                    top.set(v);
                    top.s++;
                    return true;
                } else if (isBold(v, "REDRO") && delim == BOLD_PRAGMAT_BEGIN_SYMBOL) {
                    top.set(v);
                    top.s++;
                    return true;
                } else if (isBold(v, "PR") && delim == STYLE_I_PRAGMAT_SYMBOL) {
                    top.set(v);
                    top.s++;
                    return true;
                } else if (v.ch() == ']' && v.at(1) == ']' && delim == STYLE_I_PRAGMAT_BEGIN_SYMBOL) {
                    top.set(v);
                    top.s++;
                    return true;
                } else {
                    if (whitespace && !CType.isSpace(v.ch()) && v.ch() != NEWLINE_CHAR) {
                        scanError(true, v, "error in pragment");
                    } else if (upper() && CType.isUpper(v.ch())) {
                        // Skip a bold word as you may trigger on REPR, for instance.
                        while (CType.isUpper(v.ch())) {
                            v.s++;
                        }
                    } else if (supper() && CType.isLower(v.ch())) {
                        // Skip a tag as you may trigger on expr, for instance.
                        while (CType.isLower(v.ch()) || CType.isDigit(v.ch()) || v.ch() == '_') {
                            v.s++;
                        }
                    } else {
                        v.s++;
                    }
                }
            }
            v.startOf(v.l.next);
        }
        return false;
    }

    /** get_pragmat_item: position of the next token within a pragmat; false if none. */
    private boolean getPragmatItem(Cursor top) {
        Cursor v = top.copy();
        while (v.l != null) {
            while (v.ch() != NULL_CHAR) {
                if (!CType.isSpace(v.ch()) && v.ch() != NEWLINE_CHAR) {
                    top.set(v);
                    return true;
                } else {
                    v.s++;
                }
            }
            v.startOf(v.l.next);
        }
        return false;
    }

    /**
     * next_preprocessor_item: scan for the next pragmat. Returns true and
     * leaves {@code top} at its first item if that item is INCLUDE; returns
     * false at the end of the source. {@code delim[0]} receives the pragmat
     * delimiter.
     */
    private boolean nextPreprocessorItem(Cursor top, Attribute[] delim) {
        Cursor v = top.copy();
        delim[0] = STOP;
        while (v.l != null) {
            while (v.ch() != NULL_CHAR) {
                Line startLine = v.l;
                int startPos = v.s;

                if (v.ch() == QUOTE_CHAR) {
                    // Skip string denotation.
                    scanError(!skipString(v), startLine, startPos, "unterminated string");
                } else if (Keywords.findKeyword(a68, "COMMENT") != null && isBold(v, "COMMENT")) {
                    scanError(!skipComment(v, BOLD_COMMENT_SYMBOL), startLine, startPos, "unterminated comment");
                } else if (Keywords.findKeyword(a68, "CO") != null && isBold(v, "CO")) {
                    scanError(!skipComment(v, STYLE_I_COMMENT_SYMBOL), startLine, startPos, "unterminated comment");
                } else if (Keywords.findKeyword(a68, "#") != null && v.ch() == '#') {
                    scanError(!skipComment(v, STYLE_II_COMMENT_SYMBOL), startLine, startPos, "unterminated comment");
                } else if (Keywords.findKeyword(a68, "NOTE") != null && isBold(v, "NOTE")) {
                    scanError(!skipComment(v, BOLD_COMMENT_BEGIN_SYMBOL), startLine, startPos, "unterminated comment");
                } else if (Keywords.findKeyword(a68, "{") != null && v.ch() == '{') {
                    scanError(!skipComment(v, BRIEF_COMMENT_BEGIN_SYMBOL), startLine, startPos, "unterminated comment");
                } else if ((Keywords.findKeyword(a68, "PRAGMAT") != null && isBold(v, "PRAGMAT"))
                        || (Keywords.findKeyword(a68, "PR") != null && isBold(v, "PR"))
                        || (Keywords.findKeyword(a68, "ORDER") != null && isBold(v, "ORDER"))
                        || (Keywords.findKeyword(a68, "[[") != null && v.ch() == '[' && v.at(1) == '[')) {
                    // We caught a PRAGMAT.
                    if (isBold(v, "PRAGMAT")) {
                        delim[0] = BOLD_PRAGMAT_SYMBOL;
                        v.s += "PRAGMAT".length();
                    } else if (isBold(v, "PR")) {
                        delim[0] = STYLE_I_PRAGMAT_SYMBOL;
                        v.s += "PR".length();
                    } else if (v.ch() == '[' && v.at(1) == '[' && !opts.strict && supper()) {
                        delim[0] = STYLE_I_PRAGMAT_BEGIN_SYMBOL;
                        v.s += "[[".length();
                    }
                    // Sic: an ORDER pragmat sets no delimiter.
                    boolean found = getPragmatItem(v);
                    scanError(!found, startLine, startPos, "unterminated pragmat");
                    if (streq(v, "INCLUDE") == 0) {
                        // Item "INCLUDE" includes a file.
                        top.set(v);
                        return true;
                    } else {
                        // Unrecognised item - probably options handled later by the tokeniser.
                        scanError(!skipPragmat(v, delim[0], false), startLine, startPos, "unterminated pragmat");
                    }
                } else if (upper() && CType.isUpper(v.ch())) {
                    // Skip a bold word as you may trigger on REPR, for instance.
                    while (CType.isUpper(v.ch())) {
                        v.s++;
                    }
                } else if (supper() && CType.isLower(v.ch())) {
                    // Skip a tag as you may trigger on expr, for instance.
                    while (CType.isLower(v.ch()) || CType.isDigit(v.ch()) || v.ch() == '_') {
                        v.s++;
                    }
                } else {
                    v.s++;
                }
            }
            v.startOf(v.l.next);
        }
        top.set(v);
        return false;
    }

    // ------------------------------------------------------------------
    // Include files.
    // ------------------------------------------------------------------

    /** libiberty ldirname: directory part, without trailing slash; "" if none. */
    private static String ldirname(String filename) {
        int i = filename.lastIndexOf('/');
        return i < 0 ? "" : filename.substring(0, i);
    }

    /** libiberty lbasename */
    private static String lbasename(String filename) {
        int i = filename.lastIndexOf('/');
        return i < 0 ? filename : filename.substring(i + 1);
    }

    /** a68_relpath: concatenate P1/P2/FN and canonicalise it (lrealpath). */
    private static String relpath(String p1, String p2, String fn) {
        String q = p1 + "/" + p2 + "/" + fn;
        // Home directory shortcut ~ is a shell extension.
        if (q.indexOf('~') >= 0) {
            return null;
        }
        try {
            return Paths.get(q).toRealPath().toString();
        } catch (IOException | RuntimeException e) {
            return q; // lrealpath returns a copy of the input on failure.
        }
    }

    /** file_read_p */
    private static boolean fileReadP(String filename) {
        return filename != null && Files.isReadable(Paths.get(filename));
    }

    /** find_include_file */
    private String findIncludeFile(String curfile, String filename) {
        String fnbdir = ldirname(filename);
        String incfile = lbasename(filename);
        // isAbsolute instead of a test for a leading '/', for Windows paths.
        if (!Paths.get(filename).isAbsolute()) {
            String sourcedir = ldirname(curfile);
            if (sourcedir.isEmpty() && fnbdir.isEmpty()) {
                sourcedir = ".";
            }
            String tmpfpath = relpath(sourcedir, fnbdir, incfile);
            if (fileReadP(tmpfpath)) {
                return tmpfpath;
            }
            for (String includeDir : a68.includePaths) {
                tmpfpath = relpath(includeDir, fnbdir, incfile);
                if (tmpfpath == null || !Paths.get(tmpfpath).isAbsolute()) {
                    tmpfpath = relpath(sourcedir, fnbdir, incfile);
                }
                if (fileReadP(tmpfpath)) {
                    return tmpfpath;
                }
            }
            return null;
        } else {
            return fileReadP(filename) ? filename : null;
        }
    }

    /**
     * include_files: handle the INCLUDE pragmat. Syntax: PR include "filename" PR.
     * The file gets inserted before the line containing the pragmat, which
     * preserves line numbers. A file is included at most once.
     */
    private void includeFiles(Line top) {
        boolean makePass = true;
        while (makePass) {
            Cursor v = new Cursor(top, top.string, 0);
            makePass = false;
            while (v.l != null) {
                Attribute[] prLim = new Attribute[1];
                boolean item = nextPreprocessorItem(v, prLim);
                Line startL = v.l;
                int startC = v.s;
                // Search for PR include "filename" PR.
                if (item && streq(v, "INCLUDE") == 0) {
                    // Skip to filename.
                    while (CType.isAlpha(v.ch())) {
                        v.s++;
                    }
                    while (CType.isSpace(v.ch())) {
                        v.s++;
                    }
                    // Scan quoted filename.
                    scanError(v.ch() != QUOTE_CHAR && v.ch() != '\'', startL, startC, "incorrect filename");
                    char delim = v.ch();
                    v.s++;
                    StringBuilder fnb = new StringBuilder();
                    // Scan Algol 68 string (note: "" denotes a ").
                    do {
                        scanError(v.ch() == NEWLINE_CHAR || v.ch() == NULL_CHAR, startL, startC, "incorrect filename");
                        scanError(fnb.length() == BUFFER_SIZE - 1, startL, startC, "incorrect filename");
                        if (v.ch() == delim) {
                            while (v.ch() == delim && v.at(1) == delim) {
                                scanError(fnb.length() == BUFFER_SIZE - 1, startL, startC, "incorrect filename");
                                fnb.append(delim);
                                v.s += 2;
                            }
                        } else if (CType.isPrint(v.ch())) {
                            fnb.append(v.ch());
                            v.s++;
                        } else {
                            scanError(true, startL, startC, "incorrect filename");
                        }
                    } while (v.ch() != delim);

                    // Insist that the pragmat is closed properly.
                    v.s++;
                    scanError(!skipPragmat(v, prLim[0], true), startL, startC, "unterminated pragmat");
                    scanError(fnb.length() == 0, startL, startC, "incorrect filename");

                    String sourcefile = v.l.filename != null ? v.l.filename : ".";
                    String fn = findIncludeFile(sourcefile, fnb.toString());
                    scanError(fn == null, startL, startC, "included file not found");

                    // Ignore the file when included more than once.
                    boolean already = false;
                    for (Line t = top; t != null; t = t.next) {
                        if (t.filename.equals(fn)) {
                            already = true;
                            break;
                        }
                    }
                    if (already) {
                        continue;
                    }

                    // Access the file.
                    byte[] bytes = null;
                    try {
                        bytes = Files.readAllBytes(Paths.get(fn));
                    } catch (IOException e) {
                        scanError(true, startL, startC, "error opening included file");
                    }
                    String fbuf = new String(bytes, StandardCharsets.ISO_8859_1);
                    int fsize = fbuf.length();

                    // Buffer still usable?
                    if (fsize > ps.maxScanBufLength) {
                        ps.maxScanBufLength = fsize;
                        ps.scanBuf = new char[8 + ps.maxScanBufLength];
                    }
                    char[] sb = ps.scanBuf;

                    a68.lineTable.add(LineMaps.Reason.LC_ENTER, false /* sys_p */, fn, 0 /* to_line */);

                    // Link all lines into the list.
                    int[] linum = {1};
                    Line s = v.l;
                    Line t = s.previous;
                    int k = 0;
                    if (fsize == 0) {
                        // If file is empty, insert single empty line.
                        sb[0] = NEWLINE_CHAR;
                        sb[1] = NULL_CHAR;
                        t = appendSourceLine("\n", t, fn, linum);
                    } else {
                        while (k < fsize) {
                            int n = 0;
                            sb[0] = NULL_CHAR;
                            while (k < fsize && fbuf.charAt(k) != NEWLINE_CHAR) {
                                char fc = fbuf.charAt(k);
                                // In C, char is signed: bytes >= 128 are not control characters.
                                scanError((CType.isCntrl(fc) && !CType.isSpace(fc)) || fc == STOP_CHAR,
                                        startL, startC, "invalid characters in included file");
                                sb[n++] = fbuf.charAt(k++);
                                sb[n] = NULL_CHAR;
                            }
                            sb[n++] = NEWLINE_CHAR;
                            sb[n] = NULL_CHAR;
                            if (k < fsize) {
                                k++;
                            }
                            t = appendSourceLine(CType.cString(sb, 0), t, fn, linum);
                        }
                    }

                    // Conclude and go find another include directive, if any.
                    a68.lineTable.add(LineMaps.Reason.LC_LEAVE, false, null, 0);
                    t.next = s;
                    s.previous = t;
                    makePass = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Pragments (pragmats and comments).
    // ------------------------------------------------------------------

    /** pragment: handle a pragmat or comment; returns its text including the delimiters. */
    private String pragment(Attribute type) {
        String termS = null;
        String begS = null;
        char c = cur.ch();
        Line startLine = cur.l;
        int startPos = cur.s;
        boolean pragmat = false;

        // Set terminator to look for.
        if (upper()) {
            if (type == STYLE_I_COMMENT_SYMBOL) {
                termS = "CO";
            } else if (type == STYLE_II_COMMENT_SYMBOL) {
                termS = "#";
            } else if (type == BOLD_COMMENT_SYMBOL) {
                termS = "COMMENT";
            } else if (type == BOLD_COMMENT_BEGIN_SYMBOL) {
                begS = "NOTE";
                termS = "ETON";
            } else if (type == BRIEF_COMMENT_BEGIN_SYMBOL) {
                begS = "{";
                termS = "}";
            } else if (type == STYLE_I_PRAGMAT_SYMBOL) {
                termS = "PR";
                pragmat = true;
            } else if (type == BOLD_PRAGMAT_SYMBOL) {
                termS = "PRAGMAT";
                pragmat = true;
            }
        } else {
            // SUPPER stropping.
            if (type == STYLE_I_COMMENT_SYMBOL) {
                termS = "co";
            } else if (type == STYLE_II_COMMENT_SYMBOL) {
                termS = "#";
            } else if (type == BOLD_COMMENT_SYMBOL) {
                termS = "comment";
            } else if (type == BOLD_COMMENT_BEGIN_SYMBOL) {
                begS = "note";
                termS = "eton";
            } else if (type == BRIEF_COMMENT_BEGIN_SYMBOL) {
                begS = "{";
                termS = "}";
            } else if (type == STYLE_I_PRAGMAT_SYMBOL) {
                termS = "pr";
                pragmat = true;
            } else if (type == STYLE_I_PRAGMAT_BEGIN_SYMBOL) {
                termS = "]]";
                pragmat = true;
            } else if (type == BOLD_PRAGMAT_SYMBOL) {
                termS = "pragmat";
                pragmat = true;
            }
        }
        if (termS == null) {
            // In C this would dereference a NULL pointer.
            throw new IllegalStateException("no terminator for pragment " + type);
        }

        int begSLength = begS != null ? begS.length() : 0;
        int termSLength = termS.length();

        // Scan for terminator.
        boolean nestableComment = begS != null;
        int nestingLevel = 1;
        char[] sb = ps.scanBuf;
        int charsInBuf = 0;
        sb[charsInBuf] = NULL_CHAR;
        boolean stop = false;
        while (!stop) {
            scanError(c == STOP_CHAR, startLine, startPos, "unterminated pragment");

            // A ".." or '..' delimited string in a PRAGMAT, or a ".." in a nestable comment.
            if ((pragmat && (c == QUOTE_CHAR || c == '\'')) || (nestableComment && c == QUOTE_CHAR)) {
                char delim = c;
                boolean eos = false;
                sb = addOneChar(charsInBuf++, c);
                c = nextChar(false);
                while (!eos) {
                    scanError(c == NEWLINE_CHAR || c == NULL_CHAR, startLine, startPos,
                            "string within pragment exceeds end of line");
                    if (c == delim) {
                        sb = addOneChar(charsInBuf++, delim);
                        saveState(c);
                        c = nextChar(false);
                        if (c == delim) {
                            c = nextChar(false);
                        } else {
                            c = restoreState();
                            eos = true;
                        }
                    } else if (CType.isPrint(c)) {
                        sb = addOneChar(charsInBuf++, c);
                        c = nextChar(false);
                    } else {
                        unworthy(startLine, startPos, c);
                    }
                }
            } else if (c == NEWLINE_CHAR || c == NULL_CHAR) {
                sb = addOneChar(charsInBuf++, NEWLINE_CHAR);
            } else if (CType.isPrint(c) || CType.isSpace(c)) {
                sb = addOneChar(charsInBuf++, c);
            }

            boolean skipTerminatorCheck = false;
            if (nestableComment && charsInBuf >= begSLength) {
                // If we find another instance of the nestable begin mark, bump the
                // nesting level and continue scanning.
                if (CType.cString(sb, charsInBuf - begSLength).equals(begS)) {
                    nestingLevel += 1;
                    skipTerminatorCheck = true;
                }
            }

            if (!skipTerminatorCheck && charsInBuf >= termSLength) {
                // Check whether we encountered the terminator. Mind nesting if necessary.
                if (CType.cString(sb, charsInBuf - termSLength).equals(termS)) {
                    if (nestableComment) {
                        nestingLevel -= 1;
                        stop = (nestingLevel == 0);
                    } else {
                        stop = true;
                    }
                }
            }
            c = nextChar(false);
        }

        sb[charsInBuf - termSLength] = NULL_CHAR;
        return termS + CType.cString(sb, 0) + termS;
    }

    /** ADD_ONE_CHAR, growing the buffer if needed; returns the (possibly new) buffer. */
    private char[] addOneChar(int at, char ch) {
        scanBufPut(at, ch);
        ps.scanBuf[at + 1] = NULL_CHAR;
        return ps.scanBuf;
    }

    // ------------------------------------------------------------------
    // Look-ahead helpers for denotations.
    // ------------------------------------------------------------------

    /** is_exp_char: whether input shows an exponent character. Works for both regimes. */
    private boolean isExpChar(char ch) {
        boolean ret = false;
        saveState(ch);
        if (CType.strchr("eE", ch)) {
            ch = nextChar(true);
            ret = CType.strchr("+-0123456789", ch);
        }
        restoreState();
        return ret;
    }

    /** is_radix_char */
    private boolean isRadixChar(char ch) {
        boolean ret = false;
        saveState(ch);
        if (ch == RADIX_CHAR) {
            ch = nextChar(true);
            ret = CType.strchr("0123456789abcdef", ch);
        }
        restoreState();
        return ret;
    }

    /** is_decimal_point */
    private boolean isDecimalPoint(char ch) {
        boolean ret = false;
        saveState(ch);
        if (ch == POINT_CHAR) {
            ch = nextChar(true);
            if (CType.strchr("eE", ch)) {
                ch = nextChar(true);
                ret = CType.strchr("+-0123456789", ch);
            } else {
                ret = CType.strchr("0123456789", ch);
            }
        }
        restoreState();
        return ret;
    }

    /** get_format_item: attribute for a format item. */
    private static Attribute getFormatItem(char ch) {
        return switch (CType.toLower(ch)) {
            case 'a' -> FORMAT_ITEM_A;
            case 'b' -> FORMAT_ITEM_B;
            case 'c' -> FORMAT_ITEM_C;
            case 'd' -> FORMAT_ITEM_D;
            case 'e' -> FORMAT_ITEM_E;
            case 'f' -> FORMAT_ITEM_F;
            case 'g' -> FORMAT_ITEM_G;
            case 'h' -> FORMAT_ITEM_H;
            case 'i' -> FORMAT_ITEM_I;
            case 'j' -> FORMAT_ITEM_J;
            case 'k' -> FORMAT_ITEM_K;
            case 'l', '/' -> FORMAT_ITEM_L;
            case 'm' -> FORMAT_ITEM_M;
            case 'n' -> FORMAT_ITEM_N;
            case 'o' -> FORMAT_ITEM_O;
            case 'p' -> FORMAT_ITEM_P;
            case 'q' -> FORMAT_ITEM_Q;
            case 'r' -> FORMAT_ITEM_R;
            case 's' -> FORMAT_ITEM_S;
            case 't' -> FORMAT_ITEM_T;
            case 'u' -> FORMAT_ITEM_U;
            case 'v' -> FORMAT_ITEM_V;
            case 'w' -> FORMAT_ITEM_W;
            case 'x' -> FORMAT_ITEM_X;
            case 'y' -> FORMAT_ITEM_Y;
            case 'z' -> FORMAT_ITEM_Z;
            case '+' -> FORMAT_ITEM_PLUS;
            case '-' -> FORMAT_ITEM_MINUS;
            case POINT_CHAR -> FORMAT_ITEM_POINT;
            case '%' -> FORMAT_ITEM_ESCAPE;
            default -> STOP;
        };
    }

    // ------------------------------------------------------------------
    // Tokens.
    // ------------------------------------------------------------------

    /** (sym++)[0] = c */
    private void put(char c) {
        scanBufPut(sym++, c);
    }

    /** sym[0] = '\0' */
    private void terminate() {
        scanBufPut(sym, NULL_CHAR);
    }

    /** SCAN_DIGITS */
    private char scanDigits(char c) {
        while (CType.isDigit(c)) {
            put(c);
            c = nextChar(true);
        }
        return c;
    }

    /** SCAN_EXPONENT_PART */
    private char scanExponentPart(char c) {
        put(EXPONENT_CHAR);
        c = nextChar(true);
        if (c == '+' || c == '-') {
            put(c);
            c = nextChar(true);
        }
        scanError(!CType.isDigit(c), startL, startC, "invalid exponent digit");
        return scanDigits(c);
    }

    /**
     * get_next_token: get the next token. Its text is left in scan_buf and its
     * kind in {@code att[0]} (left unchanged at end of file).
     *
     * <p>Recognised tokens: FORMAT_ITEM_* and STATIC_REPLICATOR in formats,
     * BOLD_TAG, IDENTIFIER, IDENTIFIER_WITH_UNDERSCORES, REAL_DENOTATION,
     * POINT_SYMBOL, BITS_DENOTATION, INT_DENOTATION, ROW_CHAR_DENOTATION,
     * LITERAL, STOP (single and compound punctuation), EQUALS_SYMBOL and
     * OPERATOR.
     */
    private void getNextToken(boolean inFormat, Attribute[] att) {
        char c = cur.ch();
        sym = 0;
        terminate();
        c = getGoodChar(c);
        startL = cur.l;
        startC = cur.s;
        if (c == STOP_CHAR) {
            // We are at EOF.
            put(STOP_CHAR);
            terminate();
            return;
        }

        if (inFormat) {
            // In a format.
            String formatItems = "/%\\+-.abcdefghijklmnopqrstuvwxyz";
            if (CType.strchr(formatItems, c)) {
                // General format items.
                put(c);
                terminate();
                att[0] = getFormatItem(c);
                nextChar(false);
                return;
            }
            if (CType.isDigit(c)) {
                // INT denotation for static replicator.
                c = scanDigits(c);
                terminate();
                att[0] = STATIC_REPLICATOR;
                return;
            }
        }

        if (CType.isUpper(c)) {
            // Bold taggles are enabled only in gnu68.
            boolean allowOneUnder = !opts.strict;
            if (upper()) {
                // In UPPER stropping a bold tag is an upper case word.
                while (CType.isUpper(c)) {
                    put(c);
                    c = nextChar(cur, false, allowOneUnder, null);
                }
            } else {
                // In SUPPER stropping a bold tag is a capitalized word that may
                // contain letters and digits.
                while (CType.isAlpha(c) || CType.isDigit(c)) {
                    put(c);
                    c = nextChar(cur, false, allowOneUnder, null);
                }
            }
            terminate();
            att[0] = BOLD_TAG;
        } else if (CType.isLower(c)) {
            // In both regimes a tag is a lower case word which may contain
            // letters and digits. In SUPPER stropping, however, it is not
            // allowed to have blanks separating the taggles within tags.
            boolean[] foundUnder = {false};
            boolean allowTypo = !supper();
            // Lower case word - identifier.
            while (CType.isLower(c) || CType.isDigit(c)) {
                put(c);
                c = nextChar(cur, allowTypo, true, foundUnder);
            }
            terminate();
            att[0] = foundUnder[0] ? IDENTIFIER_WITH_UNDERSCORES : IDENTIFIER;
        } else if (c == POINT_CHAR) {
            // Begins with a point symbol - point, L REAL denotation.
            if (isDecimalPoint(c)) {
                put('0');
                put(POINT_CHAR);
                c = nextChar(true);
                c = scanDigits(c);
                if (isExpChar(c)) {
                    c = scanExponentPart(c);
                }
                terminate();
                att[0] = REAL_DENOTATION;
            } else {
                c = nextChar(true);
                put(POINT_CHAR);
                terminate();
                att[0] = POINT_SYMBOL;
            }
        } else if (!opts.strict && c == SINGLE_QUOTE_CHAR) {
            c = nextChar(true);
            put(SINGLE_QUOTE_CHAR);
            terminate();
            att[0] = QUOTE_SYMBOL;
        } else if (CType.isDigit(c)) {
            // Something that begins with a digit: L INT denotation, L REAL denotation.
            c = scanDigits(c);
            if (isDecimalPoint(c)) {
                c = nextChar(true);
                if (isExpChar(c)) {
                    put(POINT_CHAR);
                    put('0');
                    c = scanExponentPart(c);
                    att[0] = REAL_DENOTATION;
                } else {
                    put(POINT_CHAR);
                    c = scanDigits(c);
                    if (isExpChar(c)) {
                        c = scanExponentPart(c);
                    }
                    att[0] = REAL_DENOTATION;
                }
            } else if (isExpChar(c)) {
                c = scanExponentPart(c);
                att[0] = REAL_DENOTATION;
            } else if (isRadixChar(c)) {
                // Parse the radix, which is expressed in base 10.
                put(c);
                terminate();
                String digits = CType.cString(ps.scanBuf, 0);
                long radix;
                try {
                    radix = Long.parseLong(digits.substring(0, digits.length() - 1));
                } catch (NumberFormatException e) {
                    radix = -1; // In C: strtoul overflow, an internal error.
                }
                // Validate radix.
                if (radix != 2 && radix != 4 && radix != 8 && (opts.strict || radix != 10) && radix != 16) {
                    scanError(true, startL, cur.s, "invalid radix in %<bits%> denotation");
                }
                // Get the rest of the bits literal. In SUPPER stropping,
                // typographical display features are not allowed in bits
                // denotations with radix 16, to avoid reading "16r0 do" as 16r0d.
                c = nextChar(true);
                while ((radix == 2 && (c == '0' || c == '1'))
                        || (radix == 4 && (c >= '0' && c <= '3'))
                        || (radix == 8 && (c >= '0' && c <= '7'))
                        || (!opts.strict && radix == 10 && CType.isDigit(c))
                        || (radix == 16 && (CType.isDigit(c) || CType.strchr("abcdef", c)))) {
                    put(c);
                    c = nextChar(cur, !supper() || radix != 16, false, null);
                }
                att[0] = BITS_DENOTATION;
            } else {
                att[0] = INT_DENOTATION;
            }
            terminate();
        } else if (c == QUOTE_CHAR) {
            scanStringDenotation(inFormat, att);
        } else if (c == '[' && !opts.strict && supper()) {
            // Either a single [ or [[ for a pragmat.
            put(c);
            c = nextChar(false);
            if (c == '[') {
                put(c);
                nextChar(false);
            }
            terminate();
            att[0] = STOP;
        } else if (c == ']' && !opts.strict && supper()) {
            // Either a single ] or ]] for a pragmat.
            put(c);
            c = nextChar(false);
            if (c == ']') {
                put(c);
                nextChar(false);
            }
            terminate();
            att[0] = STOP;
        } else if (CType.strchr("#$(){}[],;@", c)) {
            // Single character symbols.
            put(c);
            nextChar(false);
            terminate();
            att[0] = STOP;
        } else if (c == '|') {
            // Bar.
            put(c);
            c = nextChar(false);
            if (c == ':') {
                put(c);
                nextChar(false);
            }
            terminate();
            att[0] = STOP;
        } else if (c == ':') {
            // Colon, semicolon, IS, ISNT.
            put(c);
            c = nextChar(false);
            if (c == '=') {
                put(c);
                if ((c = nextChar(false)) == ':') {
                    put(c);
                    c = nextChar(false);
                }
            } else if (c == '/') {
                put(c);
                if ((c = nextChar(false)) == '=') {
                    put(c);
                    if ((c = nextChar(false)) == ':') {
                        put(c);
                        c = nextChar(false);
                    }
                }
            } else if (c == ':') {
                put(c);
                if ((c = nextChar(false)) == '=') {
                    put(c);
                }
            }
            terminate();
            att[0] = STOP;
        } else if (c == '=') {
            // Operator starting with "=".
            int scanned = sym;
            put(c);
            c = nextChar(false);
            if (CType.strchr(NOMADS, c)) {
                put(c);
                c = nextChar(false);
            }
            if (c == '=') {
                put(c);
                if (nextChar(false) == ':') {
                    put(':');
                    c = nextChar(false);
                    // Sic: strlen (sym), not strlen (scanned): this measures
                    // stale buffer contents beyond the token.
                    if (CType.strlen(ps.scanBuf, sym) < 4 && c == '=') {
                        put('=');
                        nextChar(false);
                    }
                }
            } else if (c == ':') {
                put(c);
                terminate();
                if (nextChar(false) == '=') {
                    put('=');
                    nextChar(false);
                } else {
                    String s = CType.cString(ps.scanBuf, scanned);
                    scanError(!(s.equals("=:") || s.equals("==:")), startL, startC, "invalid operator tag");
                }
            }
            terminate();
            att[0] = CType.cString(ps.scanBuf, scanned).equals("=") ? EQUALS_SYMBOL : OPERATOR;
        } else if (CType.strchr(MONADS, c) || CType.strchr(NOMADS, c)) {
            // Operator.
            int scanned = sym;
            put(c);
            c = nextChar(false);
            if (CType.strchr(NOMADS, c)) {
                put(c);
                c = nextChar(false);
            }
            if (c == '=') {
                put(c);
                if (nextChar(false) == ':') {
                    put(':');
                    c = nextChar(false);
                    if (CType.strlen(ps.scanBuf, scanned) < 4 && c == '=') {
                        put('=');
                        nextChar(false);
                    }
                }
            } else if (c == ':') {
                put(c);
                terminate();
                if (nextChar(false) == '=') {
                    put('=');
                    terminate();
                    nextChar(false);
                } else {
                    scanError(!CType.cString(ps.scanBuf, scanned + 1).equals("=:"), startL, startC,
                            "invalid operator tag");
                }
            }
            terminate();
            att[0] = OPERATOR;
        } else {
            // Afuuus ... strange characters!
            unworthy(startL, startC, c);
        }
    }

    /** The STRING denotation branch of get_next_token. */
    private void scanStringDenotation(boolean inFormat, Attribute[] att) {
        char c;
        boolean stop = false;
        while (!stop) {
            c = nextChar(false);
            while (c != QUOTE_CHAR && c != STOP_CHAR) {
                if (c == APOSTROPHE_CHAR) {
                    put(c);
                    c = nextChar(false);
                    switch (c) {
                        case APOSTROPHE_CHAR, 'n', 'f', 'r', 't' -> put(c);
                        case '(' -> {
                            // Sic: num_code_points is never incremented in the original.
                            int numCodePoints = 0;
                            put(c);
                            // Process code points.
                            while (true) {
                                // Skip white spaces.
                                do {
                                    c = nextChar(false);
                                } while (CType.isSpace(c));
                                // See if we are done.
                                if (c == ')') {
                                    scanError(numCodePoints == 0, startL, cur.s,
                                            "expected at least one character point in string break");
                                    put(c);
                                    break;
                                } else if (c == 'u' || c == 'U') {
                                    put(c);
                                    // Process a code point.
                                    char u = c;
                                    int numdigits = (u == 'u' ? 4 : 8);
                                    int startpos = cur.s;
                                    int i = 0;
                                    do {
                                        c = nextChar(false);
                                        if (!(CType.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                                            scanError(true, startL, startpos, u == 'u'
                                                    ? "expected four hex digits in string break character point"
                                                    : "expected eight hex digits in string break character point");
                                        }
                                        put(c);
                                        i += 1;
                                    } while (i < numdigits);
                                    // Skip white spaces.
                                    do {
                                        c = nextChar(false);
                                    } while (CType.isSpace(c));
                                    // Comma or end of list.
                                    if (c == ')') {
                                        put(c);
                                        break;
                                    }
                                    scanError(c != ',', startL, cur.s, "expected %<,%> or %<)%> in string break");
                                } else {
                                    scanError(true, startL, cur.s, "unterminated list of character codes");
                                }
                            }
                        }
                        default -> scanError(true, startL, cur.s, "invalid string break sequence");
                    }
                } else {
                    scanError(c == NEWLINE_CHAR || c == NULL_CHAR, startL, startC, "string exceeds end of line");
                    put(c);
                }
                c = nextChar(false);
            }
            scanError(cur.l == null, startL, startC, "unterminated string");
            c = nextChar(false);
            if (c == QUOTE_CHAR) {
                put(QUOTE_CHAR);
            } else {
                stop = true;
            }
        }
        terminate();
        att[0] = inFormat ? LITERAL : ROW_CHAR_DENOTATION;
    }

    /** open_nested_clause */
    private static boolean openNestedClause(Attribute att) {
        return switch (att) {
            case OPEN_SYMBOL, BEGIN_SYMBOL, PAR_SYMBOL, IF_SYMBOL, CASE_SYMBOL, FOR_SYMBOL, FROM_SYMBOL,
                    BY_SYMBOL, TO_SYMBOL, WHILE_SYMBOL, DO_SYMBOL, SUB_SYMBOL -> true;
            default -> false;
        };
    }

    /** close_nested_clause */
    private static boolean closeNestedClause(Attribute att) {
        return switch (att) {
            case CLOSE_SYMBOL, END_SYMBOL, FI_SYMBOL, ESAC_SYMBOL, OD_SYMBOL, BUS_SYMBOL -> true;
            default -> false;
        };
    }

    /** make_lower_case / make_upper_case on the NUL-terminated scan_buf. */
    private void changeCase(boolean toUpper) {
        char[] sb = ps.scanBuf;
        for (int i = 0; i < sb.length && sb[i] != NULL_CHAR; i++) {
            sb[i] = toUpper ? CType.toUpper(sb[i]) : CType.toLower(sb[i]);
        }
    }

    /** tokenise_source: construct a linear list of tokens. */
    private void tokeniseSource(int level, boolean inFormat) {
        String pragmatLpr = null;
        Attribute pragmatLprt = STOP;
        Line pragmatLprl = null;
        int pragmatLprc = -1;

        String commentLpr = null;
        Attribute commentLprt = STOP;
        Line commentLprl = null;
        int commentLprc = -1;

        while (!ps.stopScanner) {
            Attribute[] attH = {STOP};
            getNextToken(inFormat, attH);
            Attribute att = attH[0];
            char[] sb = ps.scanBuf;

            if (sb[0] == STOP_CHAR) {
                ps.stopScanner = true;
            } else if (CType.strlen(sb, 0) > 0 || att == ROW_CHAR_DENOTATION || att == LITERAL) {
                Keyword kw;
                String c = null;
                boolean makeNode = true;
                String trailing = null;

                if (upper()) {
                    // In UPPER stropping all symbols in R9.4.1 are expressed as
                    // bold tags like "BEGIN", or symbols like "@".
                    if (att != IDENTIFIER && att != IDENTIFIER_WITH_UNDERSCORES) {
                        kw = Keywords.findKeyword(a68, CType.cString(sb, 0));
                    } else {
                        kw = null;
                    }
                } else {
                    // In SUPPER stropping all symbols in R9.4.1 are expressed as
                    // tags like "begin", or symbols like "@".

                    // Normalize bold tags to all upper-case letters.
                    if (att == BOLD_TAG) {
                        changeCase(true);
                    }
                    // If any of the taggles of the scanned identifier were
                    // adjacent to an underscore, that inhibits interpreting it
                    // as a keyword.
                    if (att != BOLD_TAG && att != IDENTIFIER_WITH_UNDERSCORES) {
                        kw = Keywords.findKeyword(a68, CType.cString(sb, 0));
                    } else {
                        kw = null;
                    }
                }

                // Beyond this point it is irrelevant whether an identifier had
                // taggles adjacent to an underscore.
                if (att == IDENTIFIER_WITH_UNDERSCORES) {
                    att = IDENTIFIER;
                }

                if (kw == null || att == ROW_CHAR_DENOTATION) {
                    if (att == IDENTIFIER) {
                        changeCase(false);
                    }
                    if (att != ROW_CHAR_DENOTATION && att != LITERAL) {
                        int len = CType.strlen(sb, 0);
                        while (len >= 1 && sb[len - 1] == '_') {
                            trailing = "_";
                            sb[len - 1] = NULL_CHAR;
                            len--;
                        }
                    }
                    c = Keywords.addToken(a68, CType.cString(sb, 0)).text;
                } else {
                    if (kw.attribute == TO_SYMBOL) {
                        // Merge GO and TO to GOTO.
                        if (root != null && root.is(GO_SYMBOL)) {
                            root.attribute = GOTO_SYMBOL;
                            root.info.symbol = Keywords.findKeyword(a68, "GOTO").text;
                            makeNode = false;
                        } else {
                            att = kw.attribute;
                            c = kw.text;
                        }
                    } else {
                        if (upper()) {
                            if (att == STOP || att == BOLD_TAG) {
                                att = kw.attribute;
                            }
                        } else {
                            if (att == STOP || att == IDENTIFIER) {
                                att = kw.attribute;
                            }
                        }
                        c = kw.text;
                        // Handle pragments.
                        if (att == STYLE_II_COMMENT_SYMBOL
                                || att == STYLE_I_COMMENT_SYMBOL
                                || att == BOLD_COMMENT_SYMBOL
                                || att == BOLD_COMMENT_BEGIN_SYMBOL
                                || att == BRIEF_COMMENT_BEGIN_SYMBOL) {
                            String nlpr = pragment(kw.attribute);
                            if (commentLpr == null || commentLpr.isEmpty()) {
                                commentLpr = nlpr;
                            } else {
                                // Sic: "n\n" in the original.
                                commentLpr = commentLpr + "n\n" + nlpr;
                            }
                            commentLprt = att;
                            commentLprl = startL;
                            commentLprc = startC;
                            makeNode = false;
                        } else if (att == STYLE_I_PRAGMAT_SYMBOL
                                || att == BOLD_PRAGMAT_SYMBOL
                                || att == STYLE_I_PRAGMAT_BEGIN_SYMBOL) {
                            String nlpr = pragment(kw.attribute);
                            if (pragmatLpr == null || pragmatLpr.isEmpty()) {
                                pragmatLpr = nlpr;
                            } else {
                                pragmatLpr = pragmatLpr + " " + nlpr;
                            }
                            pragmatLprt = att;
                            pragmatLprl = startL;
                            pragmatLprc = startC;
                            if (!ps.stopScanner) {
                                makeNode = false;
                            }
                        }
                    }
                }

                // Add token to the tree.
                if (makeNode) {
                    Node q = new Node();
                    a68.newNodes++;
                    q.info = new NodeInfo();
                    a68.newNodeInfos++;
                    switch (att) {
                        case ASSIGN_SYMBOL, END_SYMBOL, ESAC_SYMBOL, OD_SYMBOL, OF_SYMBOL, QUOTE_SYMBOL,
                                FI_SYMBOL, CLOSE_SYMBOL, BUS_SYMBOL, COLON_SYMBOL, COMMA_SYMBOL, SEMI_SYMBOL ->
                                q.genie = null;
                        default -> {
                            q.genie = new GInfo();
                            a68.newGenieInfos++;
                        }
                    }
                    q.status = Status.NULL_MASK;
                    q.info.line = startL;
                    q.info.charInLine = startC;
                    q.info.priority = 0;
                    q.info.procedureLevel = 0;
                    q.attribute = att;
                    q.info.symbol = c;
                    q.previous = root;
                    q.sub = q.next = null;
                    q.symbolTable = null;
                    q.type = null;
                    q.tag = null;
                    if (pragmatLpr != null) {
                        q.info.pragmat = pragmatLpr;
                        q.info.pragmatType = pragmatLprt;
                        q.info.pragmatLine = pragmatLprl;
                        q.info.pragmatCharInLine = pragmatLprc;
                        pragmatLpr = null;
                        pragmatLprt = STOP;
                    }
                    if (commentLpr != null) {
                        q.info.comment = commentLpr;
                        q.info.commentType = commentLprt;
                        q.info.commentLine = commentLprl;
                        q.info.commentCharInLine = commentLprc;
                        commentLpr = null;
                        commentLprt = STOP;
                    }
                    if (root != null) {
                        root.next = q;
                    }
                    if (a68.job.topNode == null) {
                        a68.job.topNode = q;
                    }
                    root = q;
                    if (trailing != null) {
                        diag.warning(q, null, "ignoring trailing character %qs in %e", trailing, Fmt.attr(att));
                    }
                }

                // Redirection in tokenising formats. The scanner is a
                // recursive-descent type as to know when it scans a format text
                // and when not.
                if (inFormat && att == FORMAT_DELIMITER_SYMBOL) {
                    return;
                } else if (!inFormat && att == FORMAT_DELIMITER_SYMBOL) {
                    tokeniseSource(level + 1, true);
                } else if (inFormat && openNestedClause(att)) {
                    Node z = root.previous;
                    if (z != null && Node.isOneOf(z, FORMAT_ITEM_N, FORMAT_ITEM_G, FORMAT_ITEM_H, FORMAT_ITEM_F)) {
                        tokeniseSource(level, false);
                    } else if (att == OPEN_SYMBOL) {
                        root.attribute = FORMAT_OPEN_SYMBOL;
                    } else if (opts.brackets && att == SUB_SYMBOL) {
                        root.attribute = FORMAT_OPEN_SYMBOL;
                    }
                } else if (!inFormat && level > 0 && openNestedClause(att)) {
                    tokeniseSource(level + 1, false);
                } else if (!inFormat && level > 0 && closeNestedClause(att)) {
                    return;
                } else if (inFormat && att == CLOSE_SYMBOL) {
                    root.attribute = FORMAT_CLOSE_SYMBOL;
                } else if (opts.brackets && inFormat && att == BUS_SYMBOL) {
                    root.attribute = FORMAT_CLOSE_SYMBOL;
                }
            }
        }
    }

    /**
     * a68_lexical_analyser: tokenise the source file and build the initial
     * syntax tree (a linear list of tokens starting at {@code a68.job.topNode}).
     */
    public Result lexicalAnalyser(String filename) {
        // Read the source file into lines.
        readSourceFile(filename);

        // Start tokenising.
        ps.stopScanner = false;
        Line l = a68.job.topLine;
        cur.l = l;
        cur.t = l != null ? l.string : null;
        cur.s = 0;
        root = null;
        tokeniseSource(0, false);

        // Determine whether the actual file contents resulted in some token.
        boolean emptyProgram = true;
        for (Node p = a68.job.topNode; p != null; p = p.next) {
            String fn = p.info.line.filename;
            if (!fn.equals("prelude") && !fn.equals("postlude")) {
                emptyProgram = false;
                break;
            }
        }

        // If the source is a prelude packet then remove the prelude and
        // postlude nodes from the token stream. These nodes are distinguished
        // by location.
        Node p = a68.job.topNode;
        for (; p != null; p = p.next) {
            if (!p.info.line.filename.equals("prelude")) {
                break;
            }
        }
        if (p != null && p.is(MODULE_SYMBOL)) {
            p = a68.job.topNode;
            while (p != null) {
                String fn = p.info.line.filename;
                if (fn.equals("prelude") || fn.equals("postlude")) {
                    if (p.previous != null) {
                        p.previous.next = p.next;
                    } else {
                        a68.job.topNode = p.next;
                    }
                    if (p.next != null) {
                        p.next.previous = p.previous;
                    }
                }
                p = p.next;
            }
        }

        ps.scanBuf = null;
        ps.maxScanBufLength = 0;
        return new Result(emptyProgram);
    }
}
