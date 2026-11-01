// Java port of ga68's a68-parser-brackets.cc: recursive-descent parenthesis checker.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Recursive-descent parenthesis checker (a68-parser-brackets.cc).
 *
 * <p>After this checker, we know that at least brackets are matched. This
 * stabilises later parser phases.
 *
 * <p>Note that this checker operates on a linear list of nodes.
 *
 * <p>Error diagnostics are placed near offending lines.
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>{@code longjmp (A68_PARSER (top_down_crash_exit), 1)} throws
 * {@link CrashExit}, caught in {@link #checkParenthesis} where C calls
 * setjmp.</li>
 * <li>A C character buffer that is appended to is a {@link StringBuilder};
 * {@code A68 (edit_line)} (not a field of {@link A68}) is a local buffer, which
 * is equivalent since it is cleared before use.</li>
 * </ul>
 */
public final class Brackets {
    private Brackets() {}

    /** a68.h */
    private static final int BUFFER_SIZE = 1024;
    private static final int SNPRINTF_SIZE = BUFFER_SIZE - 1;

    /** longjmp (A68_PARSER (top_down_crash_exit), 1). */
    static final class CrashExit extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CrashExit() {
            super(null, null, false, false);
        }
    }

    /**
     * a68_bufcat (a68-parser.cc, not yet in {@link Parser}): safely append SRC
     * to DST; the result holds at most LEN - 1 characters.
     */
    private static void bufcat(A68 a68, StringBuilder dst, String src, int len) {
        if (src != null) {
            dst.append(src);
            if (dst.length() > len - 1) {
                dst.setLength(len - 1);
            }
        }
    }

    /** snprintf (buf, size, fmt, ...): at most SIZE - 1 characters. */
    private static String snprintf(A68 a68, int size, String fmt, Object... args) {
        String s = Diagnostics.format(fmt, args);
        return s.length() > size - 1 ? s.substring(0, size - 1) : s;
    }

    /** Intelligible diagnostics for the bracket checker. */
    private static void bracketCheckError(A68 a68, StringBuilder txt, int n, String bra, String ket,
                                          Ref<String> item) {
        String buf;

        if (n == 0) {
            return;
        }

        String stropKet = Keywords.stropKeyword(a68, ket);
        String stropBra = Keywords.stropKeyword(a68, bra);

        // C: BUFCLR (buf);
        // C: if (snprintf (...) < 0) gcc_unreachable ();
        buf = snprintf(a68, SNPRINTF_SIZE, "missing matching %%<%s%%>",
                (n > 0 ? stropKet : stropBra));

        item.set(n > 0 ? stropKet : stropBra);

        if (txt.length() > 0) {
            bufcat(a68, txt, " or ", BUFFER_SIZE);
        }
        bufcat(a68, txt, buf, BUFFER_SIZE);
    }

    /** Diagnose brackets in local branch of the tree. */
    private static String bracketCheckDiagnose(A68 a68, Node p, Ref<String> item) {
        int begins = 0, opens = 0, formatDelims = 0, formatOpens = 0;
        int subs = 0, ifs = 0, cases = 0, dos = 0;

        for (; p != null; p = p.next) {
            switch (p.attribute) {
                case BEGIN_SYMBOL:
                    begins++;
                    break;
                case END_SYMBOL:
                    begins--;
                    break;
                case OPEN_SYMBOL:
                    opens++;
                    break;
                case CLOSE_SYMBOL:
                    opens--;
                    break;
                case FORMAT_DELIMITER_SYMBOL:
                    if (formatDelims == 0) {
                        formatDelims = 1;
                    } else {
                        formatDelims = 0;
                    }
                    break;
                case FORMAT_OPEN_SYMBOL:
                    formatOpens++;
                    break;
                case FORMAT_CLOSE_SYMBOL:
                    formatOpens--;
                    break;
                case SUB_SYMBOL:
                    subs++;
                    break;
                case BUS_SYMBOL:
                    subs--;
                    break;
                case IF_SYMBOL:
                    ifs++;
                    break;
                case FI_SYMBOL:
                    ifs--;
                    break;
                case CASE_SYMBOL:
                    cases++;
                    break;
                case ESAC_SYMBOL:
                    cases--;
                    break;
                case DO_SYMBOL:
                    dos++;
                    break;
                case OD_SYMBOL:
                    dos--;
                    break;
                default:
                    break;
            }
        }

        // C: A68 (edit_line)[0] = '\0';
        StringBuilder editLine = new StringBuilder();
        bracketCheckError(a68, editLine, begins, "BEGIN", "END", item);
        bracketCheckError(a68, editLine, opens, "(", ")", item);
        bracketCheckError(a68, editLine, formatOpens, "(", ")", item);
        bracketCheckError(a68, editLine, formatDelims, "$", "$", item);
        bracketCheckError(a68, editLine, subs, "[", "]", item);
        bracketCheckError(a68, editLine, ifs, "IF", "FI", item);
        bracketCheckError(a68, editLine, cases, "CASE", "ESAC", item);
        bracketCheckError(a68, editLine, dos, "DO", "OD", item);
        return editLine.toString();
    }

    /** Driver for locally diagnosing non-matching tokens. */
    private static Node bracketCheckParse(A68 a68, Node top, Node p) {
        for (; p != null; p = p.next) {
            Attribute ket = STOP;
            Node q = null;
            boolean ignoreToken = false;

            switch (p.attribute) {
                case BEGIN_SYMBOL:
                    ket = END_SYMBOL;
                    q = bracketCheckParse(a68, top, p.next);
                    break;
                case OPEN_SYMBOL:
                    ket = CLOSE_SYMBOL;
                    q = bracketCheckParse(a68, top, p.next);
                    break;
                case FORMAT_OPEN_SYMBOL:
                    ket = FORMAT_CLOSE_SYMBOL;
                    q = bracketCheckParse(a68, top, p.next);
                    break;
                case SUB_SYMBOL:
                    ket = BUS_SYMBOL;
                    q = bracketCheckParse(a68, top, p.next);
                    break;
                case IF_SYMBOL:
                    ket = FI_SYMBOL;
                    q = bracketCheckParse(a68, top, p.next);
                    break;
                case CASE_SYMBOL:
                    ket = ESAC_SYMBOL;
                    q = bracketCheckParse(a68, top, p.next);
                    break;
                case DO_SYMBOL:
                    ket = OD_SYMBOL;
                    q = bracketCheckParse(a68, top, p.next);
                    break;
                case END_SYMBOL:
                case CLOSE_SYMBOL:
                case FORMAT_CLOSE_SYMBOL:
                case BUS_SYMBOL:
                case FI_SYMBOL:
                case ESAC_SYMBOL:
                case OD_SYMBOL:
                    return p;
                default:
                    ignoreToken = true;
            }

            if (ignoreToken) {
                ;
            } else if (q != null && q.is(ket)) {
                p = q;
            } else if (q == null) {
                Ref<String> item = Ref.box(null);
                String diag = bracketCheckDiagnose(a68, top, item);
                if (diag.length() == 0) {
                    a68.diagnostics.error(p, "incorrect nesting, check for missing or unmatched keyword");
                } else {
                    String fmt1 = diag;
                    String fmt2 = "incorrect nesting, check for " + fmt1;
                    a68.diagnostics.error(p, fmt2, item.get());
                    // C: free (item);
                }
                throw new CrashExit();
            } else {
                Ref<String> item = Ref.box(null);
                String diag = bracketCheckDiagnose(a68, top, item);
                Fmt a = Fmt.attr(q.attribute);

                if (diag.length() == 0) {
                    a68.diagnostics.error(q, "unexpected %e, check for missing or unmatched keyword",
                            a);
                } else {
                    String fmt1 = diag;
                    String fmt2 = "unexpected %e, check for " + fmt1;
                    a68.diagnostics.error(q, fmt2, a, item.get());
                    // C: free (item);
                }
                throw new CrashExit();
            }
        }
        return null;
    }

    /** Driver for globally diagnosing non-matching tokens. */
    public static void checkParenthesis(A68 a68, Node top) {
        // C: if (!setjmp (A68_PARSER (top_down_crash_exit)))
        try {
            if (bracketCheckParse(a68, top, top) != null) {
                a68.diagnostics.error(top, "incorrect nesting, check for missing or unmatched keyword");
            }
        } catch (CrashExit e) {
            // longjmp target: nothing to do.
        }
    }
}
