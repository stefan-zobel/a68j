// Java port of ga68's a68-parser-pragmat.cc: handling of compiler pragmats.
// Original: Copyright (C) 2025 Jose E. Marchesi.
// Written by Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Handling of compiler pragmats (a68-parser-pragmat.cc).
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>A C {@code const char *} pointing into the pragmat text
 * {@code NPRAGMAT (p)} is represented by an {@code int} index into that
 * string; a NULL pointer result is represented by {@code -1}. Reading past the
 * end of the string yields {@code '\0'}, as reading the terminator in C.</li>
 * <li>{@code parse_string_denotation} gets the string its pointer points into
 * as an additional parameter.</li>
 * </ul>
 */
public final class Pragmat {
    private Pragmat() {}

    /** *(P) for an index into S: the character, or NUL at/after the end. */
    private static char at(String s, int p) {
        return p >= 0 && p < s.length() ? s.charAt(p) : '\0';
    }

    /** strncmp (S + P, T, N) for an index P into S. */
    private static int strncmp(String s, int p, String t, int n) {
        for (int i = 0; i < n; i++) {
            char a = at(s, p + i);
            char b = i < t.length() ? t.charAt(i) : '\0';
            if (a != b) {
                return a - b;
            }
            if (a == '\0') {
                return 0;
            }
        }
        return 0;
    }

    // Utility macros useful for parsing pragmat contents.

    /** SKIP_WHITESPACES (P): returns the advanced P. */
    private static int skipWhitespaces(A68 a68, String s, int p) {
        while (CType.isSpace(at(s, p))) {
            p++;
        }
        return p;
    }

    /** PARSE_WORD (P, W): stores the word in W and returns the advanced P. */
    private static int parseWord(A68 a68, String s, int p, Ref<String> w) {
        StringBuilder sb = new StringBuilder();
        while (CType.isAlpha(at(s, p))) {
            sb.append(at(s, p++));
        }
        w.set(sb.toString());
        return p;
    }

    /**
     * Parse a string denotation and return its value in an allocated buffer in
     * *STR. It is up to the caller to dispose of the allocated memory.
     *
     * <p>This function returns a pointer to the character following the string
     * denotation in P, or P in case of parse error.
     *
     * <p>In case of parse error *STR is also set to NULL.
     *
     * <p>Java: P is an index into TEXT.
     */
    public static int parseStringDenotation(A68 a68, String text, int p, Ref<String> str) {
        int orig = p;
        // C: char *s = (char *) xmalloc (2 * strlen (p));
        StringBuilder s = new StringBuilder();

        // C: goto error
        error: {
            if (at(text, p++) != '\"') {
                break error;
            }
            while (at(text, p) != '\0' && at(text, p) != '\"') {
                if (at(text, p) == '\'' && at(text, p + 1) == '\"') {
                    s.append('\"');
                    p += 2;
                } else {
                    s.append(at(text, p));
                    p++;
                }
            }
            if (at(text, p) != '\"') {
                break error;
            }

            // Sic: in C the buffer S is never NUL-terminated, so the string
            // returned in *STR is followed by whatever the uninitialised
            // xmalloc'ed memory contains. Here it is just the collected
            // characters.
            str.set(s.toString());
            return p + 1;
        }
        // error:
        // C: free (s);
        str.set(null);
        return orig;
    }

    /**
     * PR access MODULE in "filename" PR
     *
     * <p>Java: PRAGMAT is an index into NPRAGMAT (p); returns an index into
     * it, or -1 for NULL.
     */
    public static int handleAccessInPragmat(A68 a68, Node p, int pragmat, int pos) {
        String text = p.info.pragmat;
        int beginning = pragmat;
        pragmat = skipWhitespaces(a68, text, pragmat);
        // Parse module name.
        Ref<String> moduleRef = Ref.box(null);
        pragmat = parseWord(a68, text, pragmat, moduleRef);
        String module = moduleRef.get();
        if (module.isEmpty()) {
            a68.diagnostics.errorInPragmat(p, pos + pragmat - beginning,
                    "invalid pragmat: expected module name after %<access%>");
            return -1;
        }
        pragmat = skipWhitespaces(a68, text, pragmat);
        // Parse "in "
        // Sic: only the first two characters of "in " are compared, but three
        // characters are skipped below.
        if (strncmp(text, pragmat, "in ", 2) != 0) {
            a68.diagnostics.errorInPragmat(p, pos + pragmat - beginning,
                    "invalid pragmat: expected %<in%> after module name");
            return -1;
        }
        pragmat += 3;
        pragmat = skipWhitespaces(a68, text, pragmat);
        // Parse a string denotation.
        Ref<String> filenameRef = Ref.box(null);
        pragmat = parseStringDenotation(a68, text, pragmat, filenameRef);
        String filename = filenameRef.get();
        if (filename == null) {
            int off = pos + pragmat - beginning;
            Ref<String> found = Ref.box(null);
            pragmat = parseWord(a68, text, pragmat, found);
            a68.diagnostics.errorInPragmat(p, off,
                    "in %<access%> pragmat, expected string, found %qs",
                    found.get());
            return -1;
        }

        // Normalize module indicant to upper-case.
        StringBuilder q = new StringBuilder(module.length());
        for (int i = 0; i < module.length(); ++i) {
            q.append(CType.toUpper(module.charAt(i)));
        }
        module = q.toString();

        // Add entry in the module files map.
        // C: const char **pmodule = A68_MODULE_FILES->get (module);
        boolean pmodule = a68.moduleFiles.containsKey(module);
        if (pmodule) {
            a68.diagnostics.errorInPragmat(p, pos + pragmat - beginning,
                    "module %qs cannot appear in multiple %<access%> pragmats",
                    module);
            return -1;
        }

        pragmat = skipWhitespaces(a68, text, pragmat);
        // Skip closing PR or PRAGMAT.
        if (p.info.pragmatType == STYLE_I_PRAGMAT_SYMBOL) {
            pragmat += 2;
        } else if (p.info.pragmatType == STYLE_I_PRAGMAT_BEGIN_SYMBOL) {
            pragmat += 2;
        } else {
            pragmat += 7;
        }

        a68.moduleFiles.put(module, filename);
        // C: free (filename);
        return pragmat;
    }

    /** Parse and action on a pragmat. */
    public static void handlePragmat(A68 a68, Node p) {
        String text = p.info.pragmat;
        if (text != null
                && (p.info.pragmatType == STYLE_I_PRAGMAT_SYMBOL
                    || p.info.pragmatType == STYLE_I_PRAGMAT_BEGIN_SYMBOL
                    || p.info.pragmatType == BOLD_PRAGMAT_SYMBOL)) {
            int pragmat = 0;
            // Process pragmat commands.
            do {
                pragmat = skipWhitespaces(a68, text, pragmat);
                // Skip PR or PRAGMAT.
                if (p.info.pragmatType == STYLE_I_PRAGMAT_SYMBOL) {
                    pragmat += 2;
                } else if (p.info.pragmatType == STYLE_I_PRAGMAT_BEGIN_SYMBOL) {
                    pragmat += 2;
                } else {
                    pragmat += 7;
                }
                pragmat = skipWhitespaces(a68, text, pragmat);
                // Get first word in pragmat and dispatch.
                pragmat = skipWhitespaces(a68, text, pragmat);
                StringBuilder wordBuf = new StringBuilder();
                // Sic: newlines are accepted as part of the word.
                while (CType.isAlpha(at(text, pragmat)) || at(text, pragmat) == '\n') {
                    wordBuf.append(at(text, pragmat++));
                }
                String word = wordBuf.toString();

                if (word.equals("access")) {
                    // C: pragmat - NPRAGMAT (p); NPRAGMAT (p) is index 0.
                    pragmat = handleAccessInPragmat(a68, p, pragmat, pragmat - 0);
                    if (pragmat == -1) {
                        break;
                    }
                } else if (word.equals("include")) {
                    // Include pragmats are handled in the scanner.
                    return;
                } else {
                    // C: pragmat - NPRAGMAT (p); NPRAGMAT (p) is index 0.
                    a68.diagnostics.errorInPragmat(p, pragmat - 0,
                            "unrecognized pragmat %qs", word);
                    break;
                }
            } while (at(text, pragmat) != '\0');
        }
    }

    /** Entry point: handle all pragmats in the given parse tree. */
    public static void handlePragmats(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            handlePragmats(a68, p.sub);
            handlePragmat(a68, p);
        }
    }
}
