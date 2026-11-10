// Java port of ga68's a68-parser-top-down.cc: top-down parser for control structure.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Top-down parser for control structure (a68-parser-top-down.cc).
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>{@code longjmp (A68_PARSER (top_down_crash_exit), 1)} throws
 * {@link CrashExit}, caught in {@link #topDownParser} where C calls
 * setjmp.</li>
 * <li>Integer parameters holding attributes ({@code clause},
 * {@code expected} of top_down_diagnose) are {@link Attribute}s; C's 0 is
 * {@link Attribute#STOP}.</li>
 * <li>The function-static {@code buffer} of a68_phrase_to_text and the
 * scratch buffer {@code A68 (edit_line)} (not a field of {@link A68}) are
 * local buffers; both are overwritten before use in C.</li>
 * </ul>
 */
public final class TopDown {
    private TopDown() {}

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

    /**
     * A symbol of the program as part of the text of phraseToText, which
     * recover_from_error uses as a format string: a '%' is doubled. ga68
     * inserts the symbol unchanged, so that an operator such as %*:= makes
     * the format string invalid (docs/ga68-bug-reports.md, entry 23).
     * a68j does not reproduce this; the text is the same for every symbol
     * without a '%'.
     */
    private static String asFormatText(String symbol) {
        return symbol.replace("%", "%%");
    }

    // A few forward prototypes of functions defined below.
    // C: static NODE_T *top_down_loop (NODE_T *p);
    // C: static NODE_T *top_down_skip_unit (NODE_T *p);
    // C: static NODE_T *top_down_def (NODE_T *def_p);

    /**
     * Substitute brackets.
     *
     * <p>Traditional ALGOL 68 syntax allows ( .. ) to replace [ .. ] in bounds
     * and slices. This top-down pass substitutes [ .. ] occurrences into ( .. ).
     */
    public static void substituteBrackets(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            substituteBrackets(a68, p.sub);

            switch (p.attribute) {
                case SUB_SYMBOL:
                    p.attribute = OPEN_SYMBOL;
                    break;
                case BUS_SYMBOL:
                    p.attribute = CLOSE_SYMBOL;
                    break;
                default:
                    break;
            }
        }
    }

    /** #define MAX_TERMINALS 8 (local to a68_phrase_to_text). */
    private static final int MAX_TERMINALS = 8;

    /** Intelligible diagnostic from syntax tree branch. W may be null (NO_VAR). */
    public static String phraseToText(A68 a68, Node p, Ref<Node> w) {
        int count = 0;
        int line = 0;
        // C: static BUFFER buffer;
        StringBuilder buffer = new StringBuilder();
        // C: A68 (edit_line)
        String editLine;

        for (buffer.setLength(0); p != null && count < MAX_TERMINALS; p = p.next) {
            if (p.info.line.number == 0) {
                continue;
            }

            Attribute gatt = Parser.getGoodAttribute(a68, p);
            String z = Parser.attributeName(gatt);

            // Where to put the error message? Bob Uzgalis noted that actual
            // content of a diagnostic is not as important as accurately
            // indicating *were* the problem is!
            if (w != null) {
                if (count == 0 || w.get() == null) {
                    w.set(p);
                } else if (Parser.dontMarkHere(a68, w.get())) {
                    w.set(p);
                }
            }

            // Add initiation.
            if (count == 0) {
                if (w != null) {
                    bufcat(a68, buffer, "construct beginning with", BUFFER_SIZE);
                }
            } else if (count == 1) {
                bufcat(a68, buffer, " followed by", BUFFER_SIZE);
            } else if (count == 2) {
                bufcat(a68, buffer, " and then", BUFFER_SIZE);
            } else if (count >= 3) {
                bufcat(a68, buffer, " and", BUFFER_SIZE);
            }

            // Attribute or symbol.
            if (z != null && p.sub != null) {
                if (gatt == IDENTIFIER || gatt == OPERATOR || gatt == DENOTATION) {
                    String stropSymbol = asFormatText(Keywords.stropKeyword(a68, p.info.symbol));
                    // C: if (snprintf (...) < 0) gcc_unreachable ();
                    editLine = snprintf(a68, SNPRINTF_SIZE, " %%<%s%%>", stropSymbol);
                    bufcat(a68, buffer, editLine, BUFFER_SIZE);
                } else {
                    if (CType.strchr("aeio", z.isEmpty() ? '\0' : z.charAt(0))) {
                        bufcat(a68, buffer, " an", BUFFER_SIZE);
                    } else {
                        bufcat(a68, buffer, " a", BUFFER_SIZE);
                    }

                    // C: if (snprintf (...) < 0) gcc_unreachable ();
                    editLine = snprintf(a68, SNPRINTF_SIZE, " %s", z);
                    bufcat(a68, buffer, editLine, BUFFER_SIZE);
                }
            } else if (z != null && p.sub == null) {
                String stropSymbol = asFormatText(Keywords.stropKeyword(a68, p.info.symbol));
                // C: if (snprintf (...) < 0) gcc_unreachable ();
                editLine = snprintf(a68, SNPRINTF_SIZE, " %%<%s%%>", stropSymbol);
                bufcat(a68, buffer, editLine, BUFFER_SIZE);
            } else if (p.info.symbol != null) {
                String stropSymbol = asFormatText(Keywords.stropKeyword(a68, p.info.symbol));
                // C: if (snprintf (...) < 0) gcc_unreachable ();
                editLine = snprintf(a68, SNPRINTF_SIZE, " %%<%s%%>", stropSymbol);
                bufcat(a68, buffer, editLine, BUFFER_SIZE);
            }
            // Add "starting in line nn".
            if (z != null && line != p.info.line.number) {
                line = p.info.line.number;
                if (gatt == SERIAL_CLAUSE || gatt == ENQUIRY_CLAUSE || gatt == INITIALISER_SERIES) {
                    bufcat(a68, buffer, " starting", BUFFER_SIZE);
                }
                // C: if (snprintf (...) < 0) gcc_unreachable ();
                editLine = snprintf(a68, SNPRINTF_SIZE, " in line %d", line);
                bufcat(a68, buffer, editLine, BUFFER_SIZE);
            }
            count++;
        }

        if (p != null && count == MAX_TERMINALS) {
            bufcat(a68, buffer, " etcetera", BUFFER_SIZE);
        }
        return buffer.toString();
    }

    // Next is a top-down parser that branches out the basic blocks.
    // After this we can assign symbol tables to basic blocks.
    // This renders the two-level grammar LALR.

    /** Give diagnose from top-down parser. */
    private static void topDownDiagnose(A68 a68, Node start, Node p, Attribute clause, Attribute expected) {
        Node issue = (p != null ? p : start);
        String stropKeyword = Keywords.stropKeyword(a68, start.info.symbol);

        Fmt l = Fmt.line(start.info.line, issue);
        Fmt a1 = Fmt.attr(clause);

        // C: expected != 0
        if (expected != STOP) {

            Fmt a2 = Fmt.attr(expected);
            a68.diagnostics.error(issue, "%e expected in %e, near %qs %e",
                    a2, a1, stropKeyword, l);
        } else {
            a68.diagnostics.error(issue, "missing or unbalanced keyword in %e, near %qs %e",
                    a1, stropKeyword, l);
        }
    }

    /** Check for premature exhaustion of tokens. */
    private static void tokensExhausted(A68 a68, Node p, Node q) {
        if (p == null) {
            Fmt s = Fmt.symbol(q);
            a68.diagnostics.error(q, "check for missing or unmatched keyword in clause starting at %e",
                    s);
            throw new CrashExit();
        }
    }

    //
    // This part specifically branches out loop clauses.
    //

    /** Whether in cast or formula with loop clause. */
    private static int isLoopCastFormula(A68 a68, Node p) {
        // Accept declarers that can appear in such casts but not much more.
        if (p.is(VOID_SYMBOL)) {
            return 1;
        } else if (p.is(INT_SYMBOL)) {
            return 1;
        } else if (p.is(REF_SYMBOL)) {
            return 1;
        } else if (Parser.isOneOf(a68, p, OPERATOR, BOLD_TAG)) {
            return 1;
        } else if (Parser.whether(a68, p, UNION_SYMBOL, OPEN_SYMBOL)) {
            return 2;
        } else if (Parser.isOneOf(a68, p, OPEN_SYMBOL, SUB_SYMBOL)) {
            int k = 0;
            for (; p != null && (Parser.isOneOf(a68, p, OPEN_SYMBOL, SUB_SYMBOL)); p = p.next, k++) {
                ;
            }
            // Sic: the value of the && expression (0 or 1) is returned, not K.
            return (p != null && (Parser.whether(a68, p, UNION_SYMBOL, OPEN_SYMBOL) ? k : 0) != 0) ? 1 : 0;
        }
        return 0;
    }

    /** Skip a unit in a loop clause (FROM u BY u TO u). */
    private static Node topDownSkipLoopUnit(A68 a68, Node p) {
        // Unit may start with, or consist of, a loop.
        if (Parser.isLoopKeyword(a68, p)) {
            p = topDownLoop(a68, p);
        }

        // Skip rest of unit.
        while (p != null) {
            int k = isLoopCastFormula(a68, p);

            if (k != 0) {
                // operator-cast series ...
                while (p != null && k != 0) {
                    while (k != 0) {
                        p = p.next;
                        k--;
                    }
                    k = isLoopCastFormula(a68, p);
                }

                // ... may be followed by a loop clause.
                if (Parser.isLoopKeyword(a68, p)) {
                    p = topDownLoop(a68, p);
                }
            } else if (Parser.isLoopKeyword(a68, p) || p.is(OD_SYMBOL)) {
                // new loop or end-of-loop.
                return p;
            } else if (p.is(COLON_SYMBOL)) {
                p = p.next;
                // skip routine header: loop clause.
                if (p != null && Parser.isLoopKeyword(a68, p)) {
                    p = topDownLoop(a68, p);
                }
            } else if (Parser.isOneOf(a68, p, SEMI_SYMBOL, COMMA_SYMBOL) || p.is(EXIT_SYMBOL)) {
                // Statement separators.
                return p;
            } else {
                p = p.next;
            }
        }
        return null;
    }

    /** Skip a loop clause. */
    private static Node topDownSkipLoopSeries(A68 a68, Node p) {
        boolean siga;

        do {
            p = topDownSkipLoopUnit(a68, p);
            siga = (p != null && (Parser.isOneOf(a68, p, SEMI_SYMBOL, EXIT_SYMBOL,
                    COMMA_SYMBOL, COLON_SYMBOL)));
            if (siga) {
                p = p.next;
            }
        } while (!(p == null || !siga));

        return p;
    }

    /** Make branch of loop parts. */
    private static Node topDownLoop(A68 a68, Node p) {
        Node start = p, q = p;

        if (q.is(FOR_SYMBOL)) {
            q = q.next;
            tokensExhausted(a68, q, start);

            if (q.is(IDENTIFIER)) {
                q.attribute = DEFINING_IDENTIFIER;
            } else {
                topDownDiagnose(a68, start, q, LOOP_CLAUSE, IDENTIFIER);
                throw new CrashExit();
            }

            q = q.next;
            tokensExhausted(a68, q, start);

            if (Parser.isOneOf(a68, q, FROM_SYMBOL, BY_SYMBOL, TO_SYMBOL,
                    WHILE_SYMBOL)) {
                ;
            } else if (q.is(DO_SYMBOL)) {
                q.attribute = ALT_DO_SYMBOL;
            } else {
                topDownDiagnose(a68, start, q, LOOP_CLAUSE, STOP);
                throw new CrashExit();
            }
        }

        if (q.is(FROM_SYMBOL)) {
            start = q;
            q = topDownSkipLoopUnit(a68, q.next);
            tokensExhausted(a68, q, start);
            if (Parser.isOneOf(a68, q, BY_SYMBOL, TO_SYMBOL, WHILE_SYMBOL)) {
                ;
            } else if (q.is(DO_SYMBOL)) {
                q.attribute = ALT_DO_SYMBOL;
            } else {
                topDownDiagnose(a68, start, q, LOOP_CLAUSE, STOP);
                throw new CrashExit();
            }

            Parser.makeSub(a68, start, q.previous, FROM_SYMBOL);
        }

        if (q.is(BY_SYMBOL)) {
            start = q;
            q = topDownSkipLoopSeries(a68, q.next);
            tokensExhausted(a68, q, start);

            if (Parser.isOneOf(a68, q, TO_SYMBOL, WHILE_SYMBOL)) {
                ;
            } else if (q.is(DO_SYMBOL)) {
                q.attribute = ALT_DO_SYMBOL;
            } else {
                topDownDiagnose(a68, start, q, LOOP_CLAUSE, STOP);
                throw new CrashExit();
            }

            Parser.makeSub(a68, start, q.previous, BY_SYMBOL);
        }

        if (Parser.isOneOf(a68, q, TO_SYMBOL)) {
            start = q;
            q = topDownSkipLoopSeries(a68, q.next);
            tokensExhausted(a68, q, start);

            if (q.is(WHILE_SYMBOL)) {
                ;
            } else if (q.is(DO_SYMBOL)) {
                q.attribute = ALT_DO_SYMBOL;
            } else {
                topDownDiagnose(a68, start, q, LOOP_CLAUSE, STOP);
                throw new CrashExit();
            }

            Parser.makeSub(a68, start, q.previous, TO_SYMBOL);
        }

        if (q.is(WHILE_SYMBOL)) {
            start = q;
            q = topDownSkipLoopSeries(a68, q.next);
            tokensExhausted(a68, q, start);

            if (q.is(DO_SYMBOL)) {
                q.attribute = ALT_DO_SYMBOL;
            } else {
                topDownDiagnose(a68, start, q, LOOP_CLAUSE, DO_SYMBOL);
                throw new CrashExit();
            }

            Parser.makeSub(a68, start, q.previous, WHILE_SYMBOL);
        }

        if (Parser.isOneOf(a68, q, DO_SYMBOL, ALT_DO_SYMBOL)) {
            Attribute k = q.attribute;

            start = q;
            q = topDownSkipLoopSeries(a68, q.next);
            tokensExhausted(a68, q, start);

            if (!q.is(OD_SYMBOL)) {
                topDownDiagnose(a68, start, q, LOOP_CLAUSE, OD_SYMBOL);
                throw new CrashExit();
            }

            Parser.makeSub(a68, start, q, k);
        }

        Node save = start.next;
        Parser.makeSub(a68, p, start, LOOP_CLAUSE);
        return save;
    }

    /** Driver for making branches of loop parts. */
    private static void topDownLoops(A68 a68, Node p) {
        Node q = p;

        for (; q != null; q = q.next) {
            if (q.sub != null) {
                topDownLoops(a68, q.sub);
            }
        }

        q = p;
        while (q != null) {
            // C: a68_is_loop_keyword (q) != STOP (a bool compared with 0).
            if (Parser.isLoopKeyword(a68, q)) {
                q = topDownLoop(a68, q);
            } else {
                q = q.next;
            }
        }
    }

    //
    // Branch anything except parts of a loop.
    //

    /** Skip serial/enquiry clause (unit series). */
    private static Node topDownSeries(A68 a68, Node p) {
        boolean siga = true;
        while (siga) {
            siga = false;
            p = topDownSkipUnit(a68, p);
            if (p != null) {
                if (Parser.isOneOf(a68, p, SEMI_SYMBOL, EXIT_SYMBOL, COMMA_SYMBOL)) {
                    siga = true;
                    p = p.next;
                }
            }
        }
        return p;
    }

    /** Make branch of DEF .. POSTLUDE .. FED. */
    private static Node topDownDef(A68 a68, Node defP) {
        Node fedP = topDownSeries(a68, defP.next);

        if (fedP == null || !(fedP.is(FED_SYMBOL) || fedP.is(POSTLUDE_SYMBOL))) {
            topDownDiagnose(a68, defP, fedP, MODULE_TEXT, FED_SYMBOL);
            throw new CrashExit();
        }

        Parser.makeSub(a68, defP, fedP.previous, DEF_SYMBOL);

        if (fedP.is(POSTLUDE_SYMBOL)) {
            Node postludeP = topDownSeries(a68, fedP.next);

            if (postludeP == null || !postludeP.is(FED_SYMBOL)) {
                topDownDiagnose(a68, defP, fedP, MODULE_TEXT, FED_SYMBOL);
                throw new CrashExit();
            }

            Parser.makeSub(a68, fedP, postludeP.previous, POSTLUDE_SYMBOL);
        }

        return defP.next;
    }

    /**
     * Make branch of
     *
     * <pre>
     *   ACCESS REVELATIONS [DEF_SYMBOL]
     *   or
     *   ACCESS REVELATIONS ENCLOSED_CLAUSE.
     * </pre>
     */
    private static void topDownAccess(A68 a68, Node p) {
        for (Node q = p; q != null; q = q.next) {
            if (q.sub != null) {
                topDownAccess(a68, q.sub);
            }
        }

        for (Node q = p; q != null; q = q.next) {
            if (q.is(ACCESS_SYMBOL)) {
                Node endP = q.next;

                // Skip joined list of revelations and make branch until the
                // enclosed clause, DEF_SYMBOL or POSTLUDE_SYMBOL.
                while (Parser.isOneOf(a68, endP, BOLD_TAG, PUBLIC_SYMBOL, COMMA_SYMBOL)) {
                    endP = endP.next;
                }

                if (endP.is(DEF_SYMBOL)) {
                    endP = endP.next;
                    if (endP.is(POSTLUDE_SYMBOL)) {
                        endP = endP.next;
                    }
                    if (endP.is(FED_SYMBOL)) {
                        q.attribute = ALT_ACCESS_SYMBOL;
                        Parser.makeSub(a68, q, endP, ALT_ACCESS_SYMBOL);
                    }
                } else if (endP.is(ACCESS_SYMBOL)) {
                    topDownAccess(a68, endP);
                    Parser.makeSub(a68, q, endP, ACCESS_SYMBOL);
                } else {
                    Parser.makeSub(a68, q, endP, ACCESS_SYMBOL);
                }
            }
        }
    }

    /** Make branch of BEGIN .. END. */
    private static Node topDownBegin(A68 a68, Node beginP) {
        Node endP = topDownSeries(a68, beginP.next);

        if (endP == null || !endP.is(END_SYMBOL)) {
            topDownDiagnose(a68, beginP, endP, ENCLOSED_CLAUSE, END_SYMBOL);
            throw new CrashExit();
            // C: return NO_NODE; (unreachable)
        } else {
            Parser.makeSub(a68, beginP, endP, BEGIN_SYMBOL);
            return beginP.next;
        }
    }

    /** Make branch of ( .. ). */
    private static Node topDownOpen(A68 a68, Node openP) {
        Node thenBarP = topDownSeries(a68, openP.next), elifBarP;

        if (thenBarP != null && thenBarP.is(CLOSE_SYMBOL)) {
            Parser.makeSub(a68, openP, thenBarP, OPEN_SYMBOL);
            return openP.next;
        }

        if (thenBarP == null || !thenBarP.is(THEN_BAR_SYMBOL)) {
            topDownDiagnose(a68, openP, thenBarP, ENCLOSED_CLAUSE, STOP);
            throw new CrashExit();
        }

        Parser.makeSub(a68, openP, thenBarP.previous, OPEN_SYMBOL);
        elifBarP = topDownSeries(a68, thenBarP.next);
        if (elifBarP != null && elifBarP.is(CLOSE_SYMBOL)) {
            Parser.makeSub(a68, thenBarP, elifBarP.previous, THEN_BAR_SYMBOL);
            Parser.makeSub(a68, openP, elifBarP, OPEN_SYMBOL);
            return openP.next;
        }

        if (elifBarP != null && elifBarP.is(THEN_BAR_SYMBOL)) {
            Node closeP = topDownSeries(a68, elifBarP.next);

            if (closeP == null || !closeP.is(CLOSE_SYMBOL)) {
                topDownDiagnose(a68, openP, elifBarP, ENCLOSED_CLAUSE, CLOSE_SYMBOL);
                throw new CrashExit();
            }

            Parser.makeSub(a68, thenBarP, elifBarP.previous, THEN_BAR_SYMBOL);
            Parser.makeSub(a68, elifBarP, closeP.previous, THEN_BAR_SYMBOL);
            Parser.makeSub(a68, openP, closeP, OPEN_SYMBOL);
            return openP.next;
        }

        if (elifBarP != null && elifBarP.is(ELSE_BAR_SYMBOL)) {
            Node closeP = topDownOpen(a68, elifBarP);
            Parser.makeSub(a68, thenBarP, elifBarP.previous, THEN_BAR_SYMBOL);
            Parser.makeSub(a68, openP, elifBarP, OPEN_SYMBOL);
            return closeP;
        } else {
            topDownDiagnose(a68, openP, elifBarP, ENCLOSED_CLAUSE, CLOSE_SYMBOL);
            throw new CrashExit();
            // C: return NO_NODE; (unreachable)
        }
    }

    /** Make branch of [ .. ]. */
    private static Node topDownSub(A68 a68, Node subP) {
        Node busP = topDownSeries(a68, subP.next);

        if (busP != null && busP.is(BUS_SYMBOL)) {
            Parser.makeSub(a68, subP, busP, SUB_SYMBOL);
            return subP.next;
        } else {
            // C: clause 0
            topDownDiagnose(a68, subP, busP, STOP, BUS_SYMBOL);
            throw new CrashExit();
            // C: return NO_NODE; (unreachable)
        }
    }

    /** Make branch of IF .. THEN .. ELSE .. FI. */
    private static Node topDownIf(A68 a68, Node ifP) {
        Node thenP = topDownSeries(a68, ifP.next), elifP;

        if (thenP == null || !thenP.is(THEN_SYMBOL)) {
            topDownDiagnose(a68, ifP, thenP, CONDITIONAL_CLAUSE, THEN_SYMBOL);
            throw new CrashExit();
        }

        Parser.makeSub(a68, ifP, thenP.previous, IF_SYMBOL);

        elifP = topDownSeries(a68, thenP.next);
        if (elifP != null && elifP.is(FI_SYMBOL)) {
            Parser.makeSub(a68, thenP, elifP.previous, THEN_SYMBOL);
            Parser.makeSub(a68, ifP, elifP, IF_SYMBOL);
            return ifP.next;
        }

        if (elifP != null && elifP.is(ELSE_SYMBOL)) {
            Node fiP = topDownSeries(a68, elifP.next);

            if (fiP == null || !fiP.is(FI_SYMBOL)) {
                topDownDiagnose(a68, ifP, fiP, CONDITIONAL_CLAUSE, FI_SYMBOL);
                throw new CrashExit();
            } else {
                Parser.makeSub(a68, thenP, elifP.previous, THEN_SYMBOL);
                Parser.makeSub(a68, elifP, fiP.previous, ELSE_SYMBOL);
                Parser.makeSub(a68, ifP, fiP, IF_SYMBOL);
                return ifP.next;
            }
        }

        if (elifP != null && elifP.is(ELIF_SYMBOL)) {
            Node fiP = topDownIf(a68, elifP);

            Parser.makeSub(a68, thenP, elifP.previous, THEN_SYMBOL);
            Parser.makeSub(a68, ifP, elifP, IF_SYMBOL);
            return fiP;
        } else {
            topDownDiagnose(a68, ifP, elifP, CONDITIONAL_CLAUSE, FI_SYMBOL);
            throw new CrashExit();
            // C: return NO_NODE; (unreachable)
        }
    }

    /** Make branch of CASE .. IN .. OUT .. ESAC. */
    private static Node topDownCase(A68 a68, Node caseP) {
        Node inP = topDownSeries(a68, caseP.next), ouseP;

        if (inP == null || !inP.is(IN_SYMBOL)) {
            topDownDiagnose(a68, caseP, inP, ENCLOSED_CLAUSE, IN_SYMBOL);
            throw new CrashExit();
        }

        Parser.makeSub(a68, caseP, inP.previous, CASE_SYMBOL);

        ouseP = topDownSeries(a68, inP.next);
        if (ouseP != null && ouseP.is(ESAC_SYMBOL)) {
            Parser.makeSub(a68, inP, ouseP.previous, IN_SYMBOL);
            Parser.makeSub(a68, caseP, ouseP, CASE_SYMBOL);
            return caseP.next;
        }

        if (ouseP != null && ouseP.is(OUT_SYMBOL)) {
            Node esacP = topDownSeries(a68, ouseP.next);

            if (esacP == null || !esacP.is(ESAC_SYMBOL)) {
                topDownDiagnose(a68, caseP, esacP, ENCLOSED_CLAUSE, ESAC_SYMBOL);
                throw new CrashExit();
            } else {
                Parser.makeSub(a68, inP, ouseP.previous, IN_SYMBOL);
                Parser.makeSub(a68, ouseP, esacP.previous, OUT_SYMBOL);
                Parser.makeSub(a68, caseP, esacP, CASE_SYMBOL);
                return caseP.next;
            }
        }

        if (ouseP != null && ouseP.is(OUSE_SYMBOL)) {
            Node esacP = topDownCase(a68, ouseP);

            Parser.makeSub(a68, inP, ouseP.previous, IN_SYMBOL);
            Parser.makeSub(a68, caseP, ouseP, CASE_SYMBOL);
            return esacP;
        } else {
            topDownDiagnose(a68, caseP, ouseP, ENCLOSED_CLAUSE, ESAC_SYMBOL);
            throw new CrashExit();
            // C: return NO_NODE; (unreachable)
        }
    }

    /** Skip a unit. */
    private static Node topDownSkipUnit(A68 a68, Node p) {
        while (p != null && !Parser.isUnitTerminator(a68, p)) {
            if (p.is(BEGIN_SYMBOL)) {
                p = topDownBegin(a68, p);
            } else if (p.is(SUB_SYMBOL)) {
                p = topDownSub(a68, p);
            } else if (p.is(OPEN_SYMBOL)) {
                p = topDownOpen(a68, p);
            } else if (p.is(IF_SYMBOL)) {
                p = topDownIf(a68, p);
            } else if (p.is(CASE_SYMBOL)) {
                p = topDownCase(a68, p);
            } else {
                p = p.next;
            }
        }
        return p;
    }

    // C: static NODE_T *top_down_skip_format (NODE_T *);

    /** Make branch of ( .. ) in a format. */
    private static Node topDownFormatOpen(A68 a68, Node openP) {
        Node closeP = topDownSkipFormat(a68, openP.next);

        if (closeP != null && closeP.is(FORMAT_CLOSE_SYMBOL)) {
            Parser.makeSub(a68, openP, closeP, FORMAT_OPEN_SYMBOL);
            return openP.next;
        } else {
            // C: clause 0
            topDownDiagnose(a68, openP, closeP, STOP, FORMAT_CLOSE_SYMBOL);
            throw new CrashExit();
            // C: return NO_NODE; (unreachable)
        }
    }

    /** Skip a format text. */
    private static Node topDownSkipFormat(A68 a68, Node p) {
        while (p != null) {
            if (p.is(FORMAT_OPEN_SYMBOL)) {
                p = topDownFormatOpen(a68, p);
            } else if (Parser.isOneOf(a68, p, FORMAT_CLOSE_SYMBOL, FORMAT_DELIMITER_SYMBOL)) {
                return p;
            } else {
                p = p.next;
            }
        }
        return null;
    }

    /** Make branch of $ .. $. */
    private static void topDownFormats(A68 a68, Node p) {
        for (Node q = p; q != null; q = q.next) {
            if (q.sub != null) {
                topDownFormats(a68, q.sub);
            }
        }

        for (Node q = p; q != null; q = q.next) {
            if (q.is(FORMAT_DELIMITER_SYMBOL)) {
                Node f = q.next;

                while (f != null && !f.is(FORMAT_DELIMITER_SYMBOL)) {
                    if (f.is(FORMAT_OPEN_SYMBOL)) {
                        f = topDownFormatOpen(a68, f);
                    } else {
                        f = f.next;
                    }
                }

                if (f == null) {
                    topDownDiagnose(a68, p, f, FORMAT_TEXT, FORMAT_DELIMITER_SYMBOL);
                    throw new CrashExit();
                } else {
                    Parser.makeSub(a68, q, f, FORMAT_DELIMITER_SYMBOL);
                }
            }
        }
    }

    /** Skip prelude packet. */
    private static Node topDownPreludePacket(A68 a68, Node p) {
        while (p != null) {
            if (p.is(DEF_SYMBOL)) {
                p = topDownDef(a68, p);
            } else {
                p = p.next;
            }
        }

        return p;
    }

    /** Skip particular program. */
    private static Node topDownParticularProgram(A68 a68, Node p) {
        topDownSeries(a68, p);
        return p;
    }

    /** Make branches of phrases for the bottom-up parser. */
    public static void topDownParser(A68 a68, Node p) {
        if (p == null) {
            return;
        }

        // C: if (!setjmp (A68_PARSER (top_down_crash_exit)))
        try {
            if (p.is(MODULE_SYMBOL)) {
                topDownPreludePacket(a68, p);
            } else {
                topDownParticularProgram(a68, p);
            }

            topDownLoops(a68, p);
            topDownFormats(a68, p);
            topDownAccess(a68, p);
        } catch (CrashExit e) {
            // longjmp target: nothing to do.
        }
    }
}
