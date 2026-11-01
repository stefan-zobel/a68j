// Java port of ga68's a68-parser-bottom-up.cc: hand-coded bottom-up parser for Algol 68.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Hand-coded bottom-up parser for Algol 68 (a68-parser-bottom-up.cc).
 *
 * <p>This is a Mailloux-type parser, in the sense that it scans a "phrase" for
 * definitions needed for parsing, and therefore allows for tags to be used
 * before they are defined, which gives some freedom in top-down programming.
 *
 * <pre>
 *    B. J. Mailloux. On the implementation of Algol 68.
 *    Thesis, Universiteit van Amsterdam (Mathematisch Centrum) [1968].
 * </pre>
 *
 * <p>Technically, Mailloux's approach renders the two-level grammar LALR.
 * This is the bottom-up parser that resolves the structure of the program.
 *
 * <h2>Thou who adventure here, please read this</h2>
 *
 * <h3>On parse trees, phrases and branches</h3>
 *
 * <p>The Algol 68 parser operates on parse trees in which sibling nodes are
 * linked together in "phrases" via NEXT and PREVIOUS pointers, and sub-trees
 * or "branches" start at SUB pointers. For example, in the parse tree
 *
 * <pre>
 *     a
 *     |
 *     b - c - d - e
 *         |
 *         f - g - h - i
 *                     |
 *                     j
 * </pre>
 *
 * <p>'a' is the root of the tree.
 * 'a', 'b - c - d - e', 'f - g - h -i' and 'j' are phrases.
 * 'a', 'c' and 'i' introduce branches to sub trees via their SUB pointer.
 *
 * <h3>From the scanner to the bottom-up parser</h3>
 *
 * <p>The lexical analyzer transforms the textual input programs into a single
 * phrase with all the tokens. For example, the program text
 *
 * <pre>
 *    begin if A then B fi;
 *          C
 *    end;
 *    D
 * </pre>
 *
 * <p>will be turned by the lexical analyzer into the following parse tree,
 * which in fact is a list conforming a single phrase:
 *
 * <pre>
 *   begin - if - A - then - B - fi - semi - C - end - semi - D
 * </pre>
 *
 * <p>This parse tree is then handed over to the top-down parser, which provides
 * some initial structure and turns the single phrase into an actual tree:
 *
 * <pre>
 *   begin - semi - D
 *     |
 *   begin - if - then - fi - end - semi - C - end
 *            |    |
 *            |   then - B
 *           if - A
 * </pre>
 *
 * <p>Note how branches have been created for the closed clause and for the
 * different parts of the conditional clause. The same would have happened for
 * loop parts and case parts as well, but it is important to note that the role
 * of the top-down parser doesn't handle the conditional, loop or case clauses
 * themselves: it just branches the parts of these clauses that introduce
 * ranges. Taking care of structuring the clauses is precisely the main role of
 * the bottom-up parser.
 *
 * <h3>The bottom-up parser</h3>
 *
 * <p>The bottom-up parser is given a parse tree like the one above, partially
 * structured in phrases by the top-down parser. The bottom-up parser then
 * proceeds from the root-node using two basic operations:
 *
 * <ul>
 * <li>reduce a phrase, implemented by 'reduce'.</li>
 * <li>reduce a sub-phrase starting at a branch, implemented by
 * 'reduce_branch'.</li>
 * </ul>
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>The C {@code setjmp/longjmp} on {@code A68_PARSER (bottom_up_crash_exit)}
 * is replaced by {@link CrashExit}, thrown at the longjmp and caught in
 * {@link #bottomUpParser}.</li>
 * <li>The variadic attribute list of {@code reduce} becomes Java varargs: the
 * result attribute is a separate parameter, the matched attributes are
 * {@link Attribute}s or {@link Parser#not} for C's negated {@code -ATTR}; the
 * terminating STOP is omitted. NO_NOTE and NO_TICK are {@code null}.</li>
 * <li>{@code bool siga} variables whose address is passed to {@code reduce}
 * are {@code boolean[1]}.</li>
 * </ul>
 */
public final class BottomUp {
    private BottomUp() {}

    // Bottom-up parser, reduces all constructs.

    /**
     * Maximum number of errors the bottom-up parser will try to recover from
     * and save diagnostics for.
     */
    static final int MAX_ERRORS = 5;

    /** a68.h: MAX_PRIORITY. */
    static final int MAX_PRIORITY = 9;

    /** longjmp (A68_PARSER (bottom_up_crash_exit), 1). */
    static final class CrashExit extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CrashExit() {
            super(null, null, false, false);
        }
    }

    /** A "noting" function passed to {@link #reduce}: C's {@code void (*a) (NODE_T *)}. */
    @FunctionalInterface
    interface Note {
        void apply(A68 a68, Node p);
    }

    // Forward declarations of some of the functions defined below: not needed
    // in Java.

    /** serial_or_collateral: whether a series is serial or collateral. */
    private static Attribute serialOrCollateral(A68 a68, Node p) {
        int semis = 0, commas = 0, exits = 0;
        for (Node q = p; q != null; q = q.next) {
            if (q.is(COMMA_SYMBOL)) {
                commas++;
            } else if (q.is(SEMI_SYMBOL)) {
                semis++;
            } else if (q.is(EXIT_SYMBOL)) {
                exits++;
            }
        }

        if (semis == 0 && exits == 0 && commas > 0) {
            return COLLATERAL_CLAUSE;
        } else if ((semis > 0 || exits > 0) && commas == 0) {
            return SERIAL_CLAUSE;
        } else if (semis == 0 && exits == 0 && commas == 0) {
            return SERIAL_CLAUSE;
        } else {
            // Heuristic guess to give intelligible error message.
            return (semis + exits >= commas) ? SERIAL_CLAUSE : COLLATERAL_CLAUSE;
        }
    }

    /** pad_node: insert a node with attribute "a" after "p". */
    private static void padNode(A68 a68, Node p, Attribute a) {
        // This is used to fill information that Algol 68 does not require to be
        // present. Filling in gives one format for such construct; this helps
        // later passes.
        Node z = Parser.newNode(a68);
        z.copyFrom(p);
        if (p.genie != null) {
            z.genie = Parser.newGenieInfo(a68);
        }
        z.previous = p;
        z.sub = null;
        z.attribute = a;
        z.type = null;
        if (z.next != null) {
            z.next.previous = z;
        }
        p.next = z;
    }

    /** a68_extension: diagnose extensions. */
    private static void extension(A68 a68, Node p) {
        a68.diagnostics.warning(p, Warn.EXTENSIONS, "ast node is an extension");
    }

    /** empty_clause: diagnose for clauses not yielding a value. */
    private static void emptyClause(A68 a68, Node p) {
        a68.diagnostics.error(p, "clause does not yield a value");
    }

    /** expected_module_text: diagnose for invalid module text. */
    private static void expectedModuleText(A68 a68, Node p) {
        a68.diagnostics.error(p, "expected module text in module definition");
    }

    /** strange_tokens: diagnose for missing symbol. */
    private static void strangeTokens(A68 a68, Node p) {
        Node q = ((p != null && p.next != null) ? p.next : p);
        a68.diagnostics.error(q, "possibly a missing or erroneous symbol nearby");
    }

    /** strange_separator: diagnose for strange separator. */
    private static void strangeSeparator(A68 a68, Node p) {
        Node q = ((p != null && p.next != null) ? p.next : p);
        a68.diagnostics.error(q, "possibly a missing or erroneous separator nearby");
    }

    /**
     * reduce: if match then reduce a sentence, the core bottom-up parser
     * routine. The reduction of a sequence of nodes stating at P:
     *
     * <pre>
     *    P - A - B - C - D - E - F
     * </pre>
     *
     * where the attributes passed to this function are Z P A B C, the result of
     * the reduction is:
     *
     * <pre>
     *    Z - D - E - F
     *    |
     *    P - A - B - C
     * </pre>
     *
     * <p>Note how the node pointed by P gets changed after a reduction gets
     * performed.
     *
     * <p>P is the AST node from where start matching.
     *
     * <p>ATTRS is a sequence of node attributes. The first of these attributes
     * is the kind of node resulting from the reduction (here: RESULT). The rest
     * of attributes are the matched in order starting at P, then a reduction is
     * performed. Two node attributes exist that convey special meaning when
     * passing to "reduce":
     *
     * <ul>
     * <li>STOP marks the end of the variable-length sequence of attributes to
     * match. (Omitted in Java.)</li>
     * <li>WILDCARD will match any non terminal, with the exception of a
     * keyword. It is used to recover from errors.</li>
     * </ul>
     *
     * <p>A is a "noting" function that is invoked and passed P right before
     * doing the reduction. If A is NO_NOTE then it is not used.
     *
     * <p>The boolean Z is set to "true" if the reduction has been performed, and
     * is left untouched otherwise. If Z is NO_TICK then it is not used.
     *
     * <p>It is common to invoke "reduce" in a row in order to try the reduction
     * of several alternatives, which are mutually exclusive. For example:
     *
     * <pre>
     *   reduce (p, NO_NOTE, NO_TICK, PARTICULAR_PROGRAM, LABEL, ENCLOSED_CLAUSE, STOP);
     *   reduce (p, NO_NOTE, NO_TICK, PARTICULAR_PROGRAM, ENCLOSED_CLAUSE, STOP);
     * </pre>
     *
     * <p>We know that at much only one of these reductions will succeed,
     * because if the first reduction succeeds, then P gets changed to LABEL
     * which cannot match ENCLOSED_CLAUSE.
     *
     * <p>Sometimes, however, "reduce" is invoked in a row in a way the second
     * reduction is intended to succeed if the first one succeeds. For example:
     *
     * <pre>
     *   reduce (p, NO_NOTE, NO_TICK, PARALLEL_CLAUSE, PAR_SYMBOL, COLLATERAL_CLAUSE, STOP);
     *   reduce (p, NO_NOTE, NO_TICK, ENCLOSED_CLAUSE, PARALLEL_CLAUSE, STOP);
     * </pre>
     *
     * <p>In this case if the first reduction succeeds, then the resulting
     * PARALLEL_CLAUSE will be itself reduced to an ENCLOSED_CLAUSE.
     *
     * <p>Another typical usage of "reduce" is to put it in a loop in order to
     * reduce matches of a left-recursive rule. This is where Z comes to play.
     * For example, consider the rules:
     *
     * <pre>
     *   label : defining identifier, colon symbol ;
     *           label, defining identifier, colon symbol.
     * </pre>
     *
     * <p>The second alternative is left-recursive, and along with the first rule
     * defines a sequence of one or more labels, each label consisting on a
     * defining identifier followed by a colon symbol. We could match these
     * rules using the following loop:
     *
     * <pre>
     *   while (siga)
     *     {
     *       siga = false;
     *       reduce (q, NO_NOTE, &amp;siga, LABEL, DEFINING_IDENTIFIER, COLON_SYMBOL, STOP);
     *       reduce (q, NO_NOTE, &amp;siga, LABEL, LABEL, DEFINING_IDENTIFIER, COLON_SYMBOL, STOP);
     *     }
     * </pre>
     *
     * <p>Note how, when presented with a sequence of labels like
     * {@code l1: l2: l3: ...}, the first call to "reduce" will succeed, turning
     * Q into a LABEL. Then the second call to "reduce" will also succeed,
     * reducing to another LABEL and setting SIGA to "true". In subsequent
     * iterations of the loop the first call will always fail, and the second
     * call will keep succeeding as more sequences of DEFINING_IDENTIFIER,
     * COLON_SYMBOL get matched.
     *
     * @param attrs {@link Attribute}s to match, or {@link Parser.Not} for a
     *              negated attribute (C: {@code -ATTR}).
     */
    private static void reduce(A68 a68, Node p, Note a, boolean[] z, Attribute result, Object... attrs) {
        Node head = p, tail = null;

        for (Object expect : attrs) {
            boolean keepMatching;

            if (p == null) {
                keepMatching = false;
            } else if (expect == WILDCARD) {
                // WILDCARD matches any non terminal, but no keyword.
                // Sic: a68_attribute_name never returns NO_TEXT (every
                // attribute has a name), so WILDCARD matches any node.
                keepMatching = (Parser.attributeName(p.attribute) != null);
            } else {
                if (expect == SKIP) {
                    // Stray "~" matches expected SKIP.
                    if (p.is(OPERATOR) && p.info.symbol.equals("~")) {
                        p.attribute = SKIP;
                    }
                }

                // C: expect >= 0
                if (expect instanceof Attribute e) {
                    keepMatching = (e == p.attribute);
                } else {
                    keepMatching = (((Parser.Not) expect).attribute() != p.attribute);
                }
            }

            if (keepMatching) {
                tail = p;
                p = p.next;
            } else {
                return;
            }
        }

        // Make reduction.
        if (a != null) {
            a.apply(a68, head);
        }

        Parser.makeSub(a68, head, tail, result);
        if (z != null) {
            z[0] = true;
        }
    }

    /** ignore_superfluous_semicolons: graciously ignore extra semicolons. */
    private static void ignoreSuperfluousSemicolons(A68 a68, Node p) {
        // This routine relaxes the parser a bit with respect to superfluous
        // semicolons, for instance "FI; OD". These provoke only a warning.
        for (; p != null; p = p.next) {
            ignoreSuperfluousSemicolons(a68, p.sub);

            if (p.next != null && p.next.is(SEMI_SYMBOL) && p.next.next == null) {
                a68.diagnostics.warning(p.next, null, "skipped superfluous %e", Fmt.attr(p.next.attribute));
                p.next = null;
            } else if (p.is(SEMI_SYMBOL) && Parser.isSemicolonLess(a68, p.next)) {
                a68.diagnostics.warning(p, null, "skipped superfluous %e", Fmt.attr(p.attribute));
                if (p.previous != null) {
                    p.previous.next = p.next;
                }
                p.next.previous = p.previous;
            }
        }
    }

    /** reduce_particular_program: reduce a particular program. */
    public static void reduceParticularProgram(A68 a68, Node p) {
        // A program is "label sequence; particular program".
        Extractor.extractLabels(a68, p, SERIAL_CLAUSE);

        Node q = p;
        // Parse the program itself.
        for (; q != null; q = q.next) {
            boolean[] siga = {true};

            if (q.sub != null) {
                reduceBranch(a68, q, SOME_CLAUSE);
            }

            while (siga[0]) {
                siga[0] = false;
                reduce(a68, q, null, siga, LABEL, DEFINING_IDENTIFIER, COLON_SYMBOL);
                reduce(a68, q, null, siga, LABEL, LABEL, DEFINING_IDENTIFIER, COLON_SYMBOL);
            }
        }
        // Determine the encompassing enclosed clause.
        for (q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, PARALLEL_CLAUSE, PAR_SYMBOL, COLLATERAL_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, PARALLEL_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, CLOSED_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, COLLATERAL_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, CONDITIONAL_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, CASE_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, CONFORMITY_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, LOOP_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, ACCESS_CLAUSE);
        }

        // Try reducing a particular program.
        q = p;
        reduce(a68, q, null, null, PARTICULAR_PROGRAM, LABEL, ENCLOSED_CLAUSE);
        reduce(a68, q, null, null, PARTICULAR_PROGRAM, ENCLOSED_CLAUSE);
    }

    /** reduce_prelude_packet: reduce a prelude packet. */
    public static void reducePreludePacket(A68 a68, Node p) {
        // Extract the module indicants.
        Extractor.extractIndicants(a68, p);

        // Reduce MODULE_TEXTs
        for (Node q = p; q != null; q = q.next) {
            if (q.is(ALT_ACCESS_SYMBOL)) {
                reduceBranch(a68, q, MODULE_TEXT);
            } else if (q.is(DEF_SYMBOL)) {
                for (Node v = q; v != null; v = v.next) {
                    if (v.is(DEF_SYMBOL)) {
                        reduceBranch(a68, v, DEF_PART);
                    } else if (v.is(POSTLUDE_SYMBOL)) {
                        reduceBranch(a68, v, POSTLUDE_PART);
                    }
                }
                reduceModuleTexts(a68, q);
            }
        }

        // Single module declaration.
        reduce(a68, p, null, null,
                MODULE_DECLARATION, MODULE_SYMBOL, DEFINING_MODULE_INDICANT, EQUALS_SYMBOL, MODULE_TEXT);
        reduce(a68, p, BottomUp::expectedModuleText, null,
                MODULE_DECLARATION, MODULE_SYMBOL, DEFINING_MODULE_INDICANT, EQUALS_SYMBOL,
                Parser.not(MODULE_TEXT));

        // C: #if 0
        //   /* Joined module declarations.  */
        //   for (NODE_T *q = p; q != NO_NODE; FORWARD (q))
        //     {
        //       bool siga;
        //       do
        //         {
        //           siga = false;
        //           reduce (q, NO_NOTE, &siga,
        //                   MODULE_DECLARATION, MODULE_DECLARATION,
        //                   COMMA_SYMBOL, DEFINING_MODULE_INDICANT, EQUALS_SYMBOL, MODULE_TEXT, STOP);
        //         }
        //       while (siga);
        //     }
        // #endif

        // Try reducing a prelude packet.
        reduce(a68, p, null, null, PRELUDE_PACKET, MODULE_DECLARATION);
    }

    /** a68_bottom_up_parser: driver for the bottom-up parser. */
    public static void bottomUpParser(A68 a68, Node p) {
        if (p != null) {
            // C: if (!setjmp (A68_PARSER (bottom_up_crash_exit)))
            try {
                Node q;
                int errorCount0 = a68.job.errorCount;

                ignoreSuperfluousSemicolons(a68, p);

                // A compilation unit is a packet, which can be either a
                // particular program or a prelude packet.
                if (p.is(MODULE_SYMBOL)) {
                    reducePreludePacket(a68, p);
                } else {
                    reduceParticularProgram(a68, p);
                }

                // Try reducing the packet.
                q = p;
                reduce(a68, q, null, null, PACKET, PARTICULAR_PROGRAM);
                reduce(a68, q, null, null, PACKET, PRELUDE_PACKET);
                if (p.sub == null || p.next != null) {
                    recoverFromError(a68, p, PACKET,
                            ((a68.job.errorCount - errorCount0) > MAX_ERRORS));
                }
            } catch (CrashExit e) {
                // longjmp (A68_PARSER (bottom_up_crash_exit), 1) lands here.
            }
        }
    }

    /**
     * reduce_branch: reduce the sub-phrase that starts one level down.
     *
     * <p>EXPECT is the expected construct for the sub-phrase, and it is used to
     * guide the parsing by determining what reductions to try. This can be
     * SOME_CLAUSE, which means either a serial or a collateral clause.
     *
     * <p>This is a recursive process. Let's suppose we have the parse tree below
     * and we call {@code reduce_branch (b, EXPECT)}:
     *
     * <pre>
     *    a - b - c - d - e
     *        |
     *        b - x - y - z
     *            |
     *            v - w
     * </pre>
     *
     * <p>The first thing this function does is to traverse the 'b' branch
     *
     * <pre>
     *    b - x - y - z
     * </pre>
     *
     * <p>looking for declarations of indicants, priorities and operators
     * (a68_extract_{indicants,priorities,operators}). Using this information,
     * it determines the nature of bold tags occurring in the branch, by turning
     * BOLD_TAG nodes into either INDICANT nodes or OPERATOR nodes
     * (a68_elaborate_bold_tags).
     *
     * <p>At this point it is possible to try reduce the declarers within the
     * 'b' subtree, so reduce_declarers is invoked on it. This function is
     * recursive and performs reductions of several kinds: sequences of 'long'
     * and 'short' symbols into sizeties, symbols like 'int' and 'real' into
     * indicants, declarers, declarer lists, etc.
     *
     * <p>Once that is done, the 'p' branch is traversed and reduce_branch is
     * invoked recursively in certain nodes that start a sub-branch:
     *
     * <pre>
     *   | Recursing in branch                              | with expect       |
     *   |--------------------------------------------------+-------------------|
     *   | FORMAT_DELIMITER_SYMBOL, FORMAT_OPEN_SYMBOL      | FORMAT_TEXT       |
     *   |--------------------------------------------------+-------------------|
     *   | OPEN_SYMBOL, IN_SYMBOL                           | COLLATERAL_CLAUSE |
     *   |--------------------------------------------------+-------------------|
     *   | IF_SYMBOL, ELIF_SYMBOL, CASE_SYMBOL, OUSE_SYMBOL | ENQUIRY_CLAUSE    |
     *   | WHILE_SYMBOL, ELSE_BAR_SYMBOL, DEF_SYMBOL        |                   |
     *   |--------------------------------------------------+-------------------|
     *   | BEGIN_SYMBOL, THEN_BAR_SYMBOL                    | SOME_CLAUSE       |
     *   |--------------------------------------------------+-------------------|
     *   | THEN_SYMBOL, ELSE_SYMBOL, OUT_SYMBOL             | SERIAL_CLAUSE     |
     *   | DO_SYMBOL, ALT_DO_SYMBOL, POSTLUDE_SYMBOL        |                   |
     *   |--------------------------------------------------+-------------------|
     *   | LOOP_CLAUSE, ACCESS_SYMBOL                       | ENCLOSED_CLAUSE   |
     *   |--------------------------------------------------+-------------------|
     *   | FOR_SYMBOL, FROM_SYMBOL, BY_SYMBOL, TO_SYMBOL    | UNIT              |
     * </pre>
     *
     * <p>Once all the sub-branches are reduced, the final step is to finally
     * reduce the 'b' branch, trying different possibilities: basic
     * declarations, units, bounds, declaration lists, clauses, module texts,
     * etc.
     *
     * <p>Note that reducing a branch requires knowing the kind of resulting
     * subtree. So if you find yourself wanting to call reduce_branch and don't
     * know what to pass in EXPECT, you are probably doing something wrong..
     * very likely you want to make reduce_branch aware of a new sort of
     * branch.
     */
    private static void reduceBranch(A68 a68, Node q, Attribute expect) {
        // If unsuccessful then the routine will at least copy the resulting
        // attribute as the parser can repair some faults. This gives less
        // spurious diagnostics.
        if (q != null && q.sub != null) {
            Node p = q.sub, u = null;
            int errorCount0 = a68.job.errorCount, errorCount02;
            boolean declarerPack = false, noError;

            switch (expect) {
                case STRUCTURE_PACK:
                case PARAMETER_PACK:
                case FORMAL_DECLARERS:
                case UNION_PACK:
                case SPECIFIER:
                    declarerPack = true;
                    break;
                default:
                    declarerPack = false;
            }

            // Sample all info needed to decide whether a bold tag is operator
            // or indicant. Find the meaning of bold tags and quit in case of
            // extra errors.
            Extractor.extractIndicants(a68, p);
            if (!declarerPack) {
                Extractor.extractPriorities(a68, p);
                Extractor.extractOperators(a68, p);
            }

            errorCount02 = a68.job.errorCount;
            Extractor.elaborateBoldTags(a68, p);
            if ((a68.job.errorCount - errorCount02) > 0) {
                throw new CrashExit();
            }

            // Now we can reduce declarers, knowing which bold tags are
            // indicants.
            reduceDeclarers(a68, p, expect);
            // Parse the phrase, as appropriate.
            if (declarerPack == false) {
                errorCount02 = a68.job.errorCount;
                Extractor.extractDeclarations(a68, p);
                if ((a68.job.errorCount - errorCount02) > 0) {
                    throw new CrashExit();
                }
                Extractor.extractLabels(a68, p, expect);
                for (u = p; u != null; u = u.next) {
                    if (u.sub != null) {
                        if (u.is(FORMAT_DELIMITER_SYMBOL)) {
                            reduceBranch(a68, u, FORMAT_TEXT);
                        } else if (u.is(FORMAT_OPEN_SYMBOL)) {
                            reduceBranch(a68, u, FORMAT_TEXT);
                        } else if (u.is(OPEN_SYMBOL)) {
                            if (u.next != null && u.next.is(THEN_BAR_SYMBOL)) {
                                reduceBranch(a68, u, ENQUIRY_CLAUSE);
                            } else if (u.previous != null && u.previous.is(PAR_SYMBOL)) {
                                reduceBranch(a68, u, COLLATERAL_CLAUSE);
                            }
                        } else if (Parser.isOneOf(a68, u, IF_SYMBOL, ELIF_SYMBOL, CASE_SYMBOL,
                                OUSE_SYMBOL, WHILE_SYMBOL,
                                ELSE_BAR_SYMBOL, DEF_PART)) {
                            reduceBranch(a68, u, ENQUIRY_CLAUSE);
                        } else if (u.is(BEGIN_SYMBOL)) {
                            reduceBranch(a68, u, SOME_CLAUSE);
                        } else if (Parser.isOneOf(a68, u, THEN_SYMBOL, ELSE_SYMBOL, OUT_SYMBOL,
                                DO_SYMBOL, ALT_DO_SYMBOL,
                                POSTLUDE_PART)) {
                            reduceBranch(a68, u, SERIAL_CLAUSE);
                        } else if (u.is(IN_SYMBOL)) {
                            reduceBranch(a68, u, COLLATERAL_CLAUSE);
                        } else if (u.is(THEN_BAR_SYMBOL)) {
                            reduceBranch(a68, u, SOME_CLAUSE);
                        } else if (u.is(ACCESS_SYMBOL)) {
                            reduceBranch(a68, u, ENCLOSED_CLAUSE);
                        } else if (u.is(DEF_SYMBOL)) {
                            reduceBranch(a68, u, DEF_PART);
                        } else if (u.is(POSTLUDE_SYMBOL)) {
                            reduceBranch(a68, u, POSTLUDE_PART);
                        } else if (u.is(LOOP_CLAUSE)) {
                            reduceBranch(a68, u, ENCLOSED_CLAUSE);
                        } else if (Parser.isOneOf(a68, u, FOR_SYMBOL, FROM_SYMBOL, BY_SYMBOL, TO_SYMBOL)) {
                            reduceBranch(a68, u, UNIT);
                        }
                    }
                }

                reducePrimaryParts(a68, p, expect);
                if (expect != ENCLOSED_CLAUSE) {
                    reducePrimaries(a68, p, expect);
                    if (expect == FORMAT_TEXT) {
                        reduceFormatTexts(a68, p);
                    } else {
                        reduceSecondaries(a68, p);
                        reduceFormulae(a68, p);
                        reduceTertiaries(a68, p);
                    }
                }

                reduceRightToLeftConstructs(a68, p);
                // Reduce units and declarations.
                reduceBasicDeclarations(a68, p);
                reduceFormalHoles(a68, p);
                reduceUnits(a68, p);
                reduceErroneousUnits(a68, p);
                if (expect != UNIT) {
                    if (expect == GENERIC_ARGUMENT) {
                        reduceGenericArguments(a68, p);
                    } else if (expect == BOUNDS) {
                        reduceBounds(a68, p);
                    } else {
                        reduceDeclarationLists(a68, p);
                        if (expect != DECLARATION_LIST) {
                            for (u = p; u != null; u = u.next) {
                                reduce(a68, u, null, null, LABELED_UNIT, LABEL, UNIT);
                                reduce(a68, u, null, null, SPECIFIED_UNIT, SPECIFIER,
                                        COLON_SYMBOL, UNIT);
                            }
                            if (expect == SOME_CLAUSE) {
                                expect = serialOrCollateral(a68, p);
                            }
                            if (expect == SERIAL_CLAUSE) {
                                reduceSerialClauses(a68, p);
                            } else if (expect == ENQUIRY_CLAUSE) {
                                reduceEnquiryClauses(a68, p);
                            } else if (expect == COLLATERAL_CLAUSE) {
                                reduceCollateralClauses(a68, p);
                            } else if (expect == ARGUMENT) {
                                reduceArguments(a68, p);
                            }
                        }
                    }
                }
                reduceEnclosedClauses(a68, p, expect);
                if (expect == DEF_PART || expect == POSTLUDE_PART) {
                    reduceModuleTextParts(a68, p);
                }
                if (expect == MODULE_TEXT) {
                    reduceModuleTexts(a68, p);
                }
            }

            // Do something if parsing failed.
            if (p.sub == null || p.next != null) {
                recoverFromError(a68, p, expect,
                        ((a68.job.errorCount - errorCount0) > MAX_ERRORS));
                noError = false;
            } else {
                noError = true;
            }
            q.attribute = p.attribute;
            if (noError) {
                q.sub = p.sub;
            }
        }
    }

    /** reduce_declarers: driver for reducing declarers. */
    private static void reduceDeclarers(A68 a68, Node p, Attribute expect) {
        Node q;
        boolean[] siga = new boolean[1]; // Must be in this scope.

        // Reduce lengtheties.
        for (q = p; q != null; q = q.next) {
            siga[0] = true;
            reduce(a68, q, null, null, FIXETY, WORD_SYMBOL);
            reduce(a68, q, null, null, LONGETY, LONG_SYMBOL);
            reduce(a68, q, null, null, SHORTETY, SHORT_SYMBOL);
            while (siga[0]) {
                siga[0] = false;
                reduce(a68, q, null, siga, LONGETY, LONGETY, LONG_SYMBOL);
                reduce(a68, q, null, siga, SHORTETY, SHORTETY, SHORT_SYMBOL);
            }
        }

        // Reduce indicants.
        for (q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, INDICANT, INT_SYMBOL);
            reduce(a68, q, null, null, INDICANT, REAL_SYMBOL);
            reduce(a68, q, null, null, INDICANT, BITS_SYMBOL);
            reduce(a68, q, null, null, INDICANT, BYTES_SYMBOL);
            reduce(a68, q, null, null, INDICANT, COMPL_SYMBOL);
            reduce(a68, q, null, null, INDICANT, BOOL_SYMBOL);
            reduce(a68, q, null, null, INDICANT, CHAR_SYMBOL);
            reduce(a68, q, null, null, INDICANT, FORMAT_SYMBOL);
            reduce(a68, q, null, null, INDICANT, STRING_SYMBOL);
            reduce(a68, q, null, null, INDICANT, FILE_SYMBOL);
            reduce(a68, q, null, null, INDICANT, CHANNEL_SYMBOL);
            reduce(a68, q, null, null, INDICANT, SEMA_SYMBOL);
        }

        // Reduce standard stuff.
        for (q = p; q != null; q = q.next) {
            if (Parser.whether(a68, q, LONGETY, INDICANT)) {
                Attribute a;

                if (q.next.sub == null) {
                    a68.diagnostics.error(q.next, "appropriate declarer expected");
                    reduce(a68, q, null, null, DECLARER, LONGETY, INDICANT);
                } else {
                    a = q.next.sub.attribute;

                    if (a == INT_SYMBOL || a == REAL_SYMBOL || a == BITS_SYMBOL
                            || a == BYTES_SYMBOL || a == COMPL_SYMBOL) {
                        reduce(a68, q, null, null, DECLARER, LONGETY, INDICANT);
                    } else {
                        a68.diagnostics.error(q.next, "appropriate declarer expected");
                        reduce(a68, q, null, null, DECLARER, LONGETY, INDICANT);
                    }
                }
            } else if (Parser.whether(a68, q, SHORTETY, INDICANT)) {
                Attribute a;

                if (q.next.sub == null) {
                    a68.diagnostics.error(q.next, "appropriate declarer expected");
                    reduce(a68, q, null, null, DECLARER, SHORTETY, INDICANT);
                } else {
                    a = q.next.sub.attribute;
                    if (a == INT_SYMBOL || a == REAL_SYMBOL || a == BITS_SYMBOL
                            || a == BYTES_SYMBOL || a == COMPL_SYMBOL) {
                        reduce(a68, q, null, null, DECLARER, SHORTETY, INDICANT);
                    } else {
                        a68.diagnostics.error(q.next, "appropriate declarer expected");
                        reduce(a68, q, null, null, DECLARER, SHORTETY, INDICANT);
                    }
                }
            } else if (Parser.whether(a68, q, FIXETY, INDICANT)) {
                Attribute a;

                if (q.next.sub == null) {
                    a68.diagnostics.error(q.next, "appropriate declarer expected");
                    reduce(a68, q, null, null, DECLARER, FIXETY, INDICANT);
                } else {
                    a = q.next.sub.attribute;
                    if (a == INT_SYMBOL || a == REAL_SYMBOL || a == BITS_SYMBOL
                            || a == BYTES_SYMBOL || a == COMPL_SYMBOL) {
                        reduce(a68, q, null, null, DECLARER, FIXETY, INDICANT);
                    } else {
                        a68.diagnostics.error(q.next, "appropriate declarer expected");
                        reduce(a68, q, null, null, DECLARER, FIXETY, INDICANT);
                    }
                }
            }
        }

        for (q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, DECLARER, INDICANT);
        }

        // Reduce declarer lists.
        for (q = p; q != null; q = q.next) {
            if (q.next != null && q.next.sub != null) {
                if (q.is(STRUCT_SYMBOL)) {
                    reduceBranch(a68, q.next, STRUCTURE_PACK);
                    reduce(a68, q, null, null, DECLARER, STRUCT_SYMBOL, STRUCTURE_PACK);
                } else if (q.is(UNION_SYMBOL)) {
                    reduceBranch(a68, q.next, UNION_PACK);
                    reduce(a68, q, null, null, DECLARER, UNION_SYMBOL, UNION_PACK);
                } else if (q.is(PROC_SYMBOL)) {
                    if (Parser.whether(a68, q, PROC_SYMBOL, OPEN_SYMBOL)) {
                        if (!Parser.isFormalBounds(a68, q.next.sub)) {
                            reduceBranch(a68, q.next, FORMAL_DECLARERS);
                        }
                    }
                } else if (q.is(OP_SYMBOL)) {
                    if (Parser.whether(a68, q, OP_SYMBOL, OPEN_SYMBOL)) {
                        if (!Parser.isFormalBounds(a68, q.next.sub)) {
                            reduceBranch(a68, q.next, FORMAL_DECLARERS);
                        }
                    }
                }
            }
        }

        // Reduce row, proc or op declarers.
        siga[0] = true;
        while (siga[0]) {
            siga[0] = false;

            for (q = p; q != null; q = q.next) {
                // FLEX DECL.
                if (Parser.whether(a68, q, FLEX_SYMBOL, DECLARER)) {
                    reduce(a68, q, null, siga, DECLARER, FLEX_SYMBOL, DECLARER);
                }

                // FLEX [] DECL.
                if (Parser.whether(a68, q, FLEX_SYMBOL, SUB_SYMBOL, DECLARER) && q.next.sub != null) {
                    reduceBranch(a68, q.next, BOUNDS);
                    reduce(a68, q, null, siga, DECLARER, FLEX_SYMBOL, BOUNDS, DECLARER);
                    reduce(a68, q, null, siga, DECLARER, FLEX_SYMBOL, FORMAL_BOUNDS, DECLARER);
                }

                // FLEX () DECL.
                if (Parser.whether(a68, q, FLEX_SYMBOL, OPEN_SYMBOL, DECLARER) && q.next.sub != null) {
                    if (!Parser.whether(a68, q, FLEX_SYMBOL, OPEN_SYMBOL, DECLARER, COLON_SYMBOL)) {
                        reduceBranch(a68, q.next, BOUNDS);
                        reduce(a68, q, null, siga, DECLARER, FLEX_SYMBOL, BOUNDS, DECLARER);
                        reduce(a68, q, null, siga, DECLARER, FLEX_SYMBOL, FORMAL_BOUNDS, DECLARER);
                    }
                }

                // [] DECL.
                if (Parser.whether(a68, q, SUB_SYMBOL, DECLARER) && q.sub != null) {
                    reduceBranch(a68, q, BOUNDS);
                    reduce(a68, q, null, siga, DECLARER, BOUNDS, DECLARER);
                    reduce(a68, q, null, siga, DECLARER, FORMAL_BOUNDS, DECLARER);
                }

                // () DECL.
                if (Parser.whether(a68, q, OPEN_SYMBOL, DECLARER) && q.sub != null) {
                    if (Parser.whether(a68, q, OPEN_SYMBOL, DECLARER, COLON_SYMBOL)) {
                        // Catch e.g. (INT i) () INT:.
                        if (Parser.isFormalBounds(a68, q.sub)) {
                            reduceBranch(a68, q, BOUNDS);
                            reduce(a68, q, null, siga, DECLARER, BOUNDS, DECLARER);
                            reduce(a68, q, null, siga, DECLARER, FORMAL_BOUNDS, DECLARER);
                        }
                    } else {
                        reduceBranch(a68, q, BOUNDS);
                        reduce(a68, q, null, siga, DECLARER, BOUNDS, DECLARER);
                        reduce(a68, q, null, siga, DECLARER, FORMAL_BOUNDS, DECLARER);
                    }
                }
            }

            // PROC DECL, PROC () DECL, OP () DECL.
            for (q = p; q != null; q = q.next) {
                Attribute a = q.attribute;
                if (a == REF_SYMBOL) {
                    reduce(a68, q, null, siga, DECLARER, REF_SYMBOL, DECLARER);
                } else if (a == PROC_SYMBOL) {
                    reduce(a68, q, null, siga, DECLARER, PROC_SYMBOL, DECLARER);
                    reduce(a68, q, null, siga, DECLARER, PROC_SYMBOL, FORMAL_DECLARERS, DECLARER);
                    reduce(a68, q, null, siga, DECLARER, PROC_SYMBOL, VOID_SYMBOL);
                    reduce(a68, q, null, siga, DECLARER, PROC_SYMBOL, FORMAL_DECLARERS, VOID_SYMBOL);
                } else if (a == OP_SYMBOL) {
                    reduce(a68, q, null, siga, OPERATOR_PLAN, OP_SYMBOL, FORMAL_DECLARERS, DECLARER);
                    reduce(a68, q, null, siga, OPERATOR_PLAN, OP_SYMBOL, FORMAL_DECLARERS, VOID_SYMBOL);
                }
            }
        }

        // Reduce packs etcetera.
        if (expect == STRUCTURE_PACK) {
            for (q = p; q != null; q = q.next) {
                siga[0] = true;
                while (siga[0]) {
                    siga[0] = false;
                    reduce(a68, q, null, siga, STRUCTURED_FIELD, DECLARER, IDENTIFIER);
                    reduce(a68, q, null, siga, STRUCTURED_FIELD, STRUCTURED_FIELD, COMMA_SYMBOL, IDENTIFIER);
                }
            }

            for (q = p; q != null; q = q.next) {
                siga[0] = true;
                while (siga[0]) {
                    siga[0] = false;
                    reduce(a68, q, null, siga, STRUCTURED_FIELD_LIST, STRUCTURED_FIELD);
                    reduce(a68, q, null, siga, STRUCTURED_FIELD_LIST, STRUCTURED_FIELD_LIST,
                            COMMA_SYMBOL, STRUCTURED_FIELD);
                    reduce(a68, q, BottomUp::strangeSeparator, siga, STRUCTURED_FIELD_LIST, STRUCTURED_FIELD_LIST,
                            STRUCTURED_FIELD);
                    reduce(a68, q, BottomUp::strangeSeparator, siga, STRUCTURED_FIELD_LIST, STRUCTURED_FIELD_LIST,
                            SEMI_SYMBOL, STRUCTURED_FIELD);
                }
            }
            q = p;
            reduce(a68, q, null, null, STRUCTURE_PACK, OPEN_SYMBOL, STRUCTURED_FIELD_LIST,
                    CLOSE_SYMBOL);
        } else if (expect == PARAMETER_PACK) {
            for (q = p; q != null; q = q.next) {
                siga[0] = true;
                while (siga[0]) {
                    siga[0] = false;
                    reduce(a68, q, null, siga, PARAMETER, DECLARER, IDENTIFIER);
                    reduce(a68, q, null, siga, PARAMETER, PARAMETER, COMMA_SYMBOL, IDENTIFIER);
                }
            }

            for (q = p; q != null; q = q.next) {
                siga[0] = true;
                while (siga[0]) {
                    siga[0] = false;
                    reduce(a68, q, null, siga, PARAMETER_LIST, PARAMETER);
                    reduce(a68, q, null, siga, PARAMETER_LIST, PARAMETER_LIST, COMMA_SYMBOL, PARAMETER);
                }
            }
            q = p;
            reduce(a68, q, null, null, PARAMETER_PACK, OPEN_SYMBOL, PARAMETER_LIST,
                    CLOSE_SYMBOL);
        } else if (expect == FORMAL_DECLARERS) {
            for (q = p; q != null; q = q.next) {
                siga[0] = true;
                while (siga[0]) {
                    siga[0] = false;
                    reduce(a68, q, null, siga, FORMAL_DECLARERS_LIST, DECLARER);
                    reduce(a68, q, null, siga, FORMAL_DECLARERS_LIST, FORMAL_DECLARERS_LIST,
                            COMMA_SYMBOL, DECLARER);
                    reduce(a68, q, BottomUp::strangeSeparator, siga, FORMAL_DECLARERS_LIST, FORMAL_DECLARERS_LIST,
                            SEMI_SYMBOL, DECLARER);
                    reduce(a68, q, BottomUp::strangeSeparator, siga, FORMAL_DECLARERS_LIST, FORMAL_DECLARERS_LIST,
                            DECLARER);
                }
            }
            q = p;
            reduce(a68, q, null, null, FORMAL_DECLARERS, OPEN_SYMBOL, FORMAL_DECLARERS_LIST,
                    CLOSE_SYMBOL);
        } else if (expect == UNION_PACK) {
            for (q = p; q != null; q = q.next) {
                siga[0] = true;
                while (siga[0]) {
                    siga[0] = false;
                    reduce(a68, q, null, siga, UNION_DECLARER_LIST, DECLARER);
                    reduce(a68, q, null, siga, UNION_DECLARER_LIST, VOID_SYMBOL);
                    reduce(a68, q, null, siga, UNION_DECLARER_LIST, UNION_DECLARER_LIST,
                            COMMA_SYMBOL, DECLARER);
                    reduce(a68, q, null, siga, UNION_DECLARER_LIST, UNION_DECLARER_LIST,
                            COMMA_SYMBOL, VOID_SYMBOL);
                    reduce(a68, q, BottomUp::strangeSeparator, siga, UNION_DECLARER_LIST, UNION_DECLARER_LIST,
                            SEMI_SYMBOL, DECLARER);
                    reduce(a68, q, BottomUp::strangeSeparator, siga, UNION_DECLARER_LIST, UNION_DECLARER_LIST,
                            SEMI_SYMBOL, VOID_SYMBOL);
                    reduce(a68, q, BottomUp::strangeSeparator, siga, UNION_DECLARER_LIST, UNION_DECLARER_LIST,
                            DECLARER);
                    reduce(a68, q, BottomUp::strangeSeparator, siga, UNION_DECLARER_LIST, UNION_DECLARER_LIST,
                            VOID_SYMBOL);
                }
            }
            q = p;
            reduce(a68, q, null, null, UNION_PACK, OPEN_SYMBOL, UNION_DECLARER_LIST,
                    CLOSE_SYMBOL);
        } else if (expect == SPECIFIER) {
            reduce(a68, p, null, null, SPECIFIER, OPEN_SYMBOL, DECLARER, IDENTIFIER, CLOSE_SYMBOL);
            reduce(a68, p, null, null, SPECIFIER, OPEN_SYMBOL, DECLARER, CLOSE_SYMBOL);
            reduce(a68, p, null, null, SPECIFIER, OPEN_SYMBOL, VOID_SYMBOL, CLOSE_SYMBOL);
        } else {
            for (q = p; q != null; q = q.next) {
                if (Parser.whether(a68, q, OPEN_SYMBOL, COLON_SYMBOL)
                        && !(expect == GENERIC_ARGUMENT || expect == BOUNDS)) {
                    if (Parser.isOneOf(a68, p, IN_SYMBOL, THEN_BAR_SYMBOL)) {
                        reduceBranch(a68, q, SPECIFIER);
                    }
                }
                if (Parser.whether(a68, q, OPEN_SYMBOL, DECLARER, COLON_SYMBOL)) {
                    reduceBranch(a68, q, PARAMETER_PACK);
                }
                if (Parser.whether(a68, q, OPEN_SYMBOL, VOID_SYMBOL, COLON_SYMBOL)) {
                    reduceBranch(a68, q, PARAMETER_PACK);
                }
            }
        }
    }

    /** reduce_right_to_left_constructs: handle cases that need reducing from right-to-left. */
    private static void reduceRightToLeftConstructs(A68 a68, Node p) {
        // Here are cases that need reducing from right-to-left whereas many
        // things can be reduced left-to-right. Assignations are a notable
        // example; one could discuss whether it would not be more natural to
        // write 1 =: k instead of k := 1. (jemarch: MARY did just that.) The
        // latter is said to be more natural, or it could be just computing
        // history. Meanwhile we use this routine.

        if (p != null) {
            reduceRightToLeftConstructs(a68, p.next);
            // Assignations.
            if (p.is(TERTIARY)) {
                reduce(a68, p, null, null, ASSIGNATION, TERTIARY, ASSIGN_SYMBOL, TERTIARY);
                reduce(a68, p, null, null, ASSIGNATION, TERTIARY, ASSIGN_SYMBOL, IDENTITY_RELATION);
                reduce(a68, p, null, null, ASSIGNATION, TERTIARY, ASSIGN_SYMBOL, AND_FUNCTION);
                reduce(a68, p, null, null, ASSIGNATION, TERTIARY, ASSIGN_SYMBOL, OR_FUNCTION);
                reduce(a68, p, null, null, ASSIGNATION, TERTIARY, ASSIGN_SYMBOL, ROUTINE_TEXT);
                reduce(a68, p, null, null, ASSIGNATION, TERTIARY, ASSIGN_SYMBOL, JUMP);
                reduce(a68, p, null, null, ASSIGNATION, TERTIARY, ASSIGN_SYMBOL, SKIP);
                reduce(a68, p, null, null, ASSIGNATION, TERTIARY, ASSIGN_SYMBOL, ASSIGNATION);
            }

            // Routine texts with parameter pack.
            else if (p.is(PARAMETER_PACK)) {
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, DECLARER, COLON_SYMBOL, ASSIGNATION);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, DECLARER, COLON_SYMBOL, IDENTITY_RELATION);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, DECLARER, COLON_SYMBOL, AND_FUNCTION);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, DECLARER, COLON_SYMBOL, OR_FUNCTION);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, DECLARER, COLON_SYMBOL, JUMP);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, DECLARER, COLON_SYMBOL, SKIP);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, DECLARER, COLON_SYMBOL, TERTIARY);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, DECLARER, COLON_SYMBOL, ROUTINE_TEXT);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, VOID_SYMBOL, COLON_SYMBOL, ASSIGNATION);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, VOID_SYMBOL, COLON_SYMBOL, IDENTITY_RELATION);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, VOID_SYMBOL, COLON_SYMBOL, AND_FUNCTION);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, VOID_SYMBOL, COLON_SYMBOL, OR_FUNCTION);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, VOID_SYMBOL, COLON_SYMBOL, JUMP);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, VOID_SYMBOL, COLON_SYMBOL, SKIP);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, VOID_SYMBOL, COLON_SYMBOL, TERTIARY);
                reduce(a68, p, null, null,
                        ROUTINE_TEXT, PARAMETER_PACK, VOID_SYMBOL, COLON_SYMBOL, ROUTINE_TEXT);
            }
            // Routine texts without parameter pack.
            else if (p.is(DECLARER)) {
                if (!(p.previous != null && p.previous.is(PARAMETER_PACK))) {
                    reduce(a68, p, null, null, ROUTINE_TEXT, DECLARER, COLON_SYMBOL, ASSIGNATION);
                    reduce(a68, p, null, null, ROUTINE_TEXT, DECLARER, COLON_SYMBOL, IDENTITY_RELATION);
                    reduce(a68, p, null, null, ROUTINE_TEXT, DECLARER, COLON_SYMBOL, AND_FUNCTION);
                    reduce(a68, p, null, null, ROUTINE_TEXT, DECLARER, COLON_SYMBOL, OR_FUNCTION);
                    reduce(a68, p, null, null, ROUTINE_TEXT, DECLARER, COLON_SYMBOL, JUMP);
                    reduce(a68, p, null, null, ROUTINE_TEXT, DECLARER, COLON_SYMBOL, SKIP);
                    reduce(a68, p, null, null, ROUTINE_TEXT, DECLARER, COLON_SYMBOL, TERTIARY);
                    reduce(a68, p, null, null, ROUTINE_TEXT, DECLARER, COLON_SYMBOL, ROUTINE_TEXT);
                }
            } else if (p.is(VOID_SYMBOL)) {
                if (!(p.previous != null && p.previous.is(PARAMETER_PACK))) {
                    reduce(a68, p, null, null, ROUTINE_TEXT, VOID_SYMBOL, COLON_SYMBOL, ASSIGNATION);
                    reduce(a68, p, null, null, ROUTINE_TEXT, VOID_SYMBOL, COLON_SYMBOL, IDENTITY_RELATION);
                    reduce(a68, p, null, null, ROUTINE_TEXT, VOID_SYMBOL, COLON_SYMBOL, AND_FUNCTION);
                    reduce(a68, p, null, null, ROUTINE_TEXT, VOID_SYMBOL, COLON_SYMBOL, OR_FUNCTION);
                    reduce(a68, p, null, null, ROUTINE_TEXT, VOID_SYMBOL, COLON_SYMBOL, JUMP);
                    reduce(a68, p, null, null, ROUTINE_TEXT, VOID_SYMBOL, COLON_SYMBOL, SKIP);
                    reduce(a68, p, null, null, ROUTINE_TEXT, VOID_SYMBOL, COLON_SYMBOL, TERTIARY);
                    reduce(a68, p, null, null, ROUTINE_TEXT, VOID_SYMBOL, COLON_SYMBOL, ROUTINE_TEXT);
                }
            }
        }
    }

    /** reduce_primary_parts: reduce primary elements. */
    private static void reducePrimaryParts(A68 a68, Node p, Attribute expect) {
        for (Node q = p; q != null; q = q.next) {
            if (Parser.whether(a68, q, IDENTIFIER, OF_SYMBOL)
                    || Parser.whether(a68, q, IDENTIFIER, QUOTE_SYMBOL)) {
                q.attribute = FIELD_IDENTIFIER;
            }

            reduce(a68, q, null, null, NIHIL, NIL_SYMBOL);
            reduce(a68, q, null, null, SKIP, SKIP_SYMBOL);
            reduce(a68, q, null, null, SELECTOR, FIELD_IDENTIFIER, OF_SYMBOL);
            reduce(a68, q, null, null, SELECTOR, FIELD_IDENTIFIER, QUOTE_SYMBOL);
            // JUMPs without GOTO are resolved later.
            reduce(a68, q, null, null, JUMP, GOTO_SYMBOL, IDENTIFIER);
            reduce(a68, q, null, null, DENOTATION, LONGETY, INT_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, LONGETY, REAL_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, LONGETY, BITS_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, SHORTETY, INT_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, SHORTETY, REAL_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, SHORTETY, BITS_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, FIXETY, INT_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, FIXETY, REAL_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, FIXETY, BITS_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, INT_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, REAL_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, BITS_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, ROW_CHAR_DENOTATION);
            reduce(a68, q, null, null, DENOTATION, TRUE_SYMBOL);
            reduce(a68, q, null, null, DENOTATION, FALSE_SYMBOL);
            reduce(a68, q, null, null, DENOTATION, EMPTY_SYMBOL);
            if (expect == SERIAL_CLAUSE || expect == ENQUIRY_CLAUSE || expect == SOME_CLAUSE) {
                boolean[] siga = {true};
                while (siga[0]) {
                    siga[0] = false;
                    reduce(a68, q, null, siga, LABEL, DEFINING_IDENTIFIER, COLON_SYMBOL);
                    reduce(a68, q, null, siga, LABEL, LABEL, DEFINING_IDENTIFIER, COLON_SYMBOL);
                }
            }
        }

        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, PARALLEL_CLAUSE, PAR_SYMBOL, COLLATERAL_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, ACCESS_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, PARALLEL_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, CLOSED_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, COLLATERAL_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, CONDITIONAL_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, CASE_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, CONFORMITY_CLAUSE);
            reduce(a68, q, null, null, ENCLOSED_CLAUSE, LOOP_CLAUSE);
        }
    }

    /** reduce_primaries: reduce primaries completely. */
    private static void reducePrimaries(A68 a68, Node p, Attribute expect) {
        Node q = p;
        while (q != null) {
            boolean fwd = true;
            boolean[] siga = new boolean[1];
            // Primaries excepts call and slice.
            reduce(a68, q, null, null, PRIMARY, IDENTIFIER);
            reduce(a68, q, null, null, PRIMARY, DENOTATION);
            reduce(a68, q, null, null, CAST, DECLARER, ENCLOSED_CLAUSE);
            reduce(a68, q, null, null, CAST, VOID_SYMBOL, ENCLOSED_CLAUSE);
            reduce(a68, q, null, null, ASSERTION, ASSERT_SYMBOL, ENCLOSED_CLAUSE);
            reduce(a68, q, null, null, PRIMARY, CAST);
            reduce(a68, q, null, null, PRIMARY, ENCLOSED_CLAUSE);
            reduce(a68, q, null, null, PRIMARY, FORMAT_TEXT);
            // Call and slice.
            siga[0] = true;
            while (siga[0]) {
                Node x = q.next;

                siga[0] = false;
                if (q.is(PRIMARY) && x != null) {
                    if (x.is(OPEN_SYMBOL)) {
                        reduceBranch(a68, q.next, GENERIC_ARGUMENT);
                        reduce(a68, q, null, siga, SPECIFICATION, PRIMARY, GENERIC_ARGUMENT);
                        reduce(a68, q, null, siga, PRIMARY, SPECIFICATION);
                    } else if (x.is(SUB_SYMBOL)) {
                        reduceBranch(a68, q.next, GENERIC_ARGUMENT);
                        reduce(a68, q, null, siga, SPECIFICATION, PRIMARY, GENERIC_ARGUMENT);
                        reduce(a68, q, null, siga, PRIMARY, SPECIFICATION);
                    }
                }
            }

            // Now that call and slice are known, reduce remaining ( .. ).
            if (q.is(OPEN_SYMBOL) && q.sub != null) {
                reduceBranch(a68, q, SOME_CLAUSE);
                reduce(a68, q, null, null, ENCLOSED_CLAUSE, CLOSED_CLAUSE);
                reduce(a68, q, null, null, ENCLOSED_CLAUSE, COLLATERAL_CLAUSE);
                reduce(a68, q, null, null, ENCLOSED_CLAUSE, CONDITIONAL_CLAUSE);
                reduce(a68, q, null, null, ENCLOSED_CLAUSE, CASE_CLAUSE);
                reduce(a68, q, null, null, ENCLOSED_CLAUSE, CONFORMITY_CLAUSE);
                if (q.previous != null) {
                    q = q.previous;
                    fwd = false;
                }
            }

            // Format text items.
            if (expect == FORMAT_TEXT) {
                Node r;

                for (r = p; r != null; r = r.next) {
                    reduce(a68, r, null, null, DYNAMIC_REPLICATOR, FORMAT_ITEM_N, ENCLOSED_CLAUSE);
                    reduce(a68, r, null, null, GENERAL_PATTERN, FORMAT_ITEM_G, ENCLOSED_CLAUSE);
                    reduce(a68, r, null, null, GENERAL_PATTERN, FORMAT_ITEM_H, ENCLOSED_CLAUSE);
                    reduce(a68, r, null, null, FORMAT_PATTERN, FORMAT_ITEM_F, ENCLOSED_CLAUSE);
                }
            }
            if (fwd) {
                q = q.next;
            }
        }
    }

    /** ambiguous_patterns: enforce that ambiguous patterns are separated by commas. */
    private static void ambiguousPatterns(A68 a68, Node p) {
        // Example: printf (($+d.2d +d.2d$, 1, 2)) can produce either
        // "+1.00 +2.00" or "+1+002.00". A comma must be supplied to resolve the
        // ambiguity.
        //
        // The obvious thing would be to weave this into the syntax, letting the
        // BU parser sort it out. But the C-style patterns do not suffer from
        // Algol 68 pattern ambiguity, so by solving it this way we maximise
        // freedom in writing the patterns as we want without introducing two
        // "kinds" of patterns, and so we have shorter routines for implementing
        // formatted transput. This is a pragmatic system.
        Node q, lastPat = null;

        for (q = p; q != null; q = q.next) {
            switch (q.attribute) {
                // These are the potentially ambiguous patterns.
                case INTEGRAL_PATTERN:
                case REAL_PATTERN:
                case COMPLEX_PATTERN:
                case BITS_PATTERN:
                    if (lastPat != null) {
                        a68.diagnostics.error(q, "%e and %e must be separated by a comma-symbol",
                                Fmt.attr(lastPat.attribute), Fmt.attr(q.attribute));
                    }
                    lastPat = q;
                    break;
                case COMMA_SYMBOL:
                    lastPat = null;
                    break;
                default:
                    break;
            }
        }
    }

    /** reduce_c_pattern: reduce C format texts completely. */
    private static void reduceCPattern(A68 a68, Node p, Attribute pr, Attribute let) {
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_POINT, REPLICATOR, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, REPLICATOR, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, REPLICATOR, FORMAT_ITEM_POINT, REPLICATOR, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_PLUS, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_PLUS, FORMAT_ITEM_POINT, REPLICATOR, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_PLUS, REPLICATOR, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_PLUS, REPLICATOR, FORMAT_ITEM_POINT, REPLICATOR, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_MINUS, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_MINUS, FORMAT_ITEM_POINT, REPLICATOR, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_MINUS, REPLICATOR, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_MINUS, REPLICATOR, FORMAT_ITEM_POINT, REPLICATOR, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_MINUS, FORMAT_ITEM_PLUS, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_MINUS, FORMAT_ITEM_PLUS, FORMAT_ITEM_POINT, REPLICATOR,
                    let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_MINUS, FORMAT_ITEM_PLUS, REPLICATOR, let);
            reduce(a68, q, null, null, pr,
                    FORMAT_ITEM_ESCAPE, FORMAT_ITEM_MINUS, FORMAT_ITEM_PLUS, REPLICATOR, FORMAT_ITEM_POINT,
                    REPLICATOR, let);
        }
    }

    /** reduce_format_texts: reduce format texts completely. */
    private static void reduceFormatTexts(A68 a68, Node p) {
        // Replicators.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, REPLICATOR, STATIC_REPLICATOR);
            reduce(a68, q, null, null, REPLICATOR, DYNAMIC_REPLICATOR);
        }

        // "OTHER" patterns.
        reduceCPattern(a68, p, BITS_C_PATTERN, FORMAT_ITEM_B);
        reduceCPattern(a68, p, BITS_C_PATTERN, FORMAT_ITEM_O);
        reduceCPattern(a68, p, BITS_C_PATTERN, FORMAT_ITEM_X);
        reduceCPattern(a68, p, CHAR_C_PATTERN, FORMAT_ITEM_C);
        reduceCPattern(a68, p, FIXED_C_PATTERN, FORMAT_ITEM_F);
        reduceCPattern(a68, p, FLOAT_C_PATTERN, FORMAT_ITEM_E);
        reduceCPattern(a68, p, GENERAL_C_PATTERN, FORMAT_ITEM_G);
        reduceCPattern(a68, p, INTEGRAL_C_PATTERN, FORMAT_ITEM_D);
        reduceCPattern(a68, p, INTEGRAL_C_PATTERN, FORMAT_ITEM_I);
        reduceCPattern(a68, p, STRING_C_PATTERN, FORMAT_ITEM_S);
        // Radix frames.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, RADIX_FRAME, REPLICATOR, FORMAT_ITEM_R);
        }

        // Insertions.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, INSERTION, FORMAT_ITEM_X);
            reduce(a68, q, null, null, INSERTION, FORMAT_ITEM_Y);
            reduce(a68, q, null, null, INSERTION, FORMAT_ITEM_L);
            reduce(a68, q, null, null, INSERTION, FORMAT_ITEM_P);
            reduce(a68, q, null, null, INSERTION, FORMAT_ITEM_Q);
            reduce(a68, q, null, null, INSERTION, FORMAT_ITEM_K);
            reduce(a68, q, null, null, INSERTION, LITERAL);
        }
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, INSERTION, REPLICATOR, INSERTION);
        }
        for (Node q = p; q != null; q = q.next) {
            boolean[] siga = {true};
            while (siga[0]) {
                siga[0] = false;
                reduce(a68, q, null, siga, INSERTION, INSERTION, INSERTION);
            }
        }

        // Replicated suppressible frames.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, FORMAT_A_FRAME, REPLICATOR, FORMAT_ITEM_S, FORMAT_ITEM_A);
            reduce(a68, q, null, null, FORMAT_Z_FRAME, REPLICATOR, FORMAT_ITEM_S, FORMAT_ITEM_Z);
            reduce(a68, q, null, null, FORMAT_D_FRAME, REPLICATOR, FORMAT_ITEM_S, FORMAT_ITEM_D);
        }

        // Suppressible frames.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, FORMAT_A_FRAME, FORMAT_ITEM_S, FORMAT_ITEM_A);
            reduce(a68, q, null, null, FORMAT_Z_FRAME, FORMAT_ITEM_S, FORMAT_ITEM_Z);
            reduce(a68, q, null, null, FORMAT_D_FRAME, FORMAT_ITEM_S, FORMAT_ITEM_D);
            reduce(a68, q, null, null, FORMAT_E_FRAME, FORMAT_ITEM_S, FORMAT_ITEM_E);
            reduce(a68, q, null, null, FORMAT_POINT_FRAME, FORMAT_ITEM_S, FORMAT_ITEM_POINT);
            reduce(a68, q, null, null, FORMAT_I_FRAME, FORMAT_ITEM_S, FORMAT_ITEM_I);
        }

        // Replicated frames.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, FORMAT_A_FRAME, REPLICATOR, FORMAT_ITEM_A);
            reduce(a68, q, null, null, FORMAT_Z_FRAME, REPLICATOR, FORMAT_ITEM_Z);
            reduce(a68, q, null, null, FORMAT_D_FRAME, REPLICATOR, FORMAT_ITEM_D);
        }

        // Frames.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, FORMAT_A_FRAME, FORMAT_ITEM_A);
            reduce(a68, q, null, null, FORMAT_Z_FRAME, FORMAT_ITEM_Z);
            reduce(a68, q, null, null, FORMAT_D_FRAME, FORMAT_ITEM_D);
            reduce(a68, q, null, null, FORMAT_E_FRAME, FORMAT_ITEM_E);
            reduce(a68, q, null, null, FORMAT_POINT_FRAME, FORMAT_ITEM_POINT);
            reduce(a68, q, null, null, FORMAT_I_FRAME, FORMAT_ITEM_I);
        }

        // Frames with an insertion.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, FORMAT_A_FRAME, INSERTION, FORMAT_A_FRAME);
            reduce(a68, q, null, null, FORMAT_Z_FRAME, INSERTION, FORMAT_Z_FRAME);
            reduce(a68, q, null, null, FORMAT_D_FRAME, INSERTION, FORMAT_D_FRAME);
            reduce(a68, q, null, null, FORMAT_E_FRAME, INSERTION, FORMAT_E_FRAME);
            reduce(a68, q, null, null, FORMAT_POINT_FRAME, INSERTION, FORMAT_POINT_FRAME);
            reduce(a68, q, null, null, FORMAT_I_FRAME, INSERTION, FORMAT_I_FRAME);
        }

        // String patterns.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, STRING_PATTERN, REPLICATOR, FORMAT_A_FRAME);
        }

        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, STRING_PATTERN, FORMAT_A_FRAME);
        }

        for (Node q = p; q != null; q = q.next) {
            boolean[] siga = {true};
            while (siga[0]) {
                siga[0] = false;
                reduce(a68, q, null, siga, STRING_PATTERN, STRING_PATTERN, STRING_PATTERN);
                reduce(a68, q, null, siga, STRING_PATTERN, STRING_PATTERN, INSERTION, STRING_PATTERN);
            }
        }

        // Integral moulds.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, INTEGRAL_MOULD, FORMAT_Z_FRAME);
            reduce(a68, q, null, null, INTEGRAL_MOULD, FORMAT_D_FRAME);
        }
        for (Node q = p; q != null; q = q.next) {
            boolean[] siga = {true};
            while (siga[0]) {
                siga[0] = false;
                reduce(a68, q, null, siga, INTEGRAL_MOULD, INTEGRAL_MOULD, INTEGRAL_MOULD);
                reduce(a68, q, null, siga, INTEGRAL_MOULD, INTEGRAL_MOULD, INSERTION);
            }
        }

        // Sign moulds.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, SIGN_MOULD, INTEGRAL_MOULD, FORMAT_ITEM_PLUS);
            reduce(a68, q, null, null, SIGN_MOULD, INTEGRAL_MOULD, FORMAT_ITEM_MINUS);
        }

        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, SIGN_MOULD, FORMAT_ITEM_PLUS);
            reduce(a68, q, null, null, SIGN_MOULD, FORMAT_ITEM_MINUS);
        }

        // Exponent frames.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, EXPONENT_FRAME, FORMAT_E_FRAME, SIGN_MOULD, INTEGRAL_MOULD);
            reduce(a68, q, null, null, EXPONENT_FRAME, FORMAT_E_FRAME, INTEGRAL_MOULD);
        }

        // Real patterns.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null,
                    REAL_PATTERN, SIGN_MOULD, INTEGRAL_MOULD, FORMAT_POINT_FRAME, INTEGRAL_MOULD, EXPONENT_FRAME);
            reduce(a68, q, null, null,
                    REAL_PATTERN, SIGN_MOULD, INTEGRAL_MOULD, FORMAT_POINT_FRAME, INTEGRAL_MOULD);
            reduce(a68, q, null, null,
                    REAL_PATTERN, SIGN_MOULD, INTEGRAL_MOULD, FORMAT_POINT_FRAME, EXPONENT_FRAME);
            reduce(a68, q, null, null,
                    REAL_PATTERN, SIGN_MOULD, INTEGRAL_MOULD, FORMAT_POINT_FRAME);
        }

        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null,
                    REAL_PATTERN, SIGN_MOULD, FORMAT_POINT_FRAME, INTEGRAL_MOULD, EXPONENT_FRAME);
            reduce(a68, q, null, null, REAL_PATTERN, SIGN_MOULD, FORMAT_POINT_FRAME, INTEGRAL_MOULD);
            reduce(a68, q, null, null, REAL_PATTERN, SIGN_MOULD, FORMAT_POINT_FRAME, EXPONENT_FRAME);
            reduce(a68, q, null, null, REAL_PATTERN, SIGN_MOULD, FORMAT_POINT_FRAME);
        }

        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null,
                    REAL_PATTERN, INTEGRAL_MOULD, FORMAT_POINT_FRAME, INTEGRAL_MOULD, EXPONENT_FRAME);
            reduce(a68, q, null, null,
                    REAL_PATTERN, INTEGRAL_MOULD, FORMAT_POINT_FRAME, INTEGRAL_MOULD);
            reduce(a68, q, null, null,
                    REAL_PATTERN, INTEGRAL_MOULD, FORMAT_POINT_FRAME, EXPONENT_FRAME);
            reduce(a68, q, null, null,
                    REAL_PATTERN, INTEGRAL_MOULD, FORMAT_POINT_FRAME);
            reduce(a68, q, null, null,
                    REAL_PATTERN, FORMAT_POINT_FRAME, INTEGRAL_MOULD, EXPONENT_FRAME);
            reduce(a68, q, null, null,
                    REAL_PATTERN, FORMAT_POINT_FRAME, INTEGRAL_MOULD);
        }

        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, REAL_PATTERN, SIGN_MOULD, INTEGRAL_MOULD, EXPONENT_FRAME);
            reduce(a68, q, null, null, REAL_PATTERN, INTEGRAL_MOULD, EXPONENT_FRAME);
        }

        // Complex patterns.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, COMPLEX_PATTERN, REAL_PATTERN, FORMAT_I_FRAME, REAL_PATTERN);
        }

        // Bits patterns.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, BITS_PATTERN, RADIX_FRAME, INTEGRAL_MOULD);
        }

        // Integral patterns.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, INTEGRAL_PATTERN, SIGN_MOULD, INTEGRAL_MOULD);
            reduce(a68, q, null, null, INTEGRAL_PATTERN, INTEGRAL_MOULD);
        }

        // Patterns.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, BOOLEAN_PATTERN, FORMAT_ITEM_B, COLLECTION);
            reduce(a68, q, null, null, CHOICE_PATTERN, FORMAT_ITEM_C, COLLECTION);
        }

        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, BOOLEAN_PATTERN, FORMAT_ITEM_B);
            reduce(a68, q, null, null, GENERAL_PATTERN, FORMAT_ITEM_G);
            reduce(a68, q, null, null, GENERAL_PATTERN, FORMAT_ITEM_H);
        }

        ambiguousPatterns(a68, p);
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, BottomUp::extension, null, A68_PATTERN, BITS_C_PATTERN);
            reduce(a68, q, BottomUp::extension, null, A68_PATTERN, CHAR_C_PATTERN);
            reduce(a68, q, BottomUp::extension, null, A68_PATTERN, FIXED_C_PATTERN);
            reduce(a68, q, BottomUp::extension, null, A68_PATTERN, FLOAT_C_PATTERN);
            reduce(a68, q, BottomUp::extension, null, A68_PATTERN, GENERAL_C_PATTERN);
            reduce(a68, q, BottomUp::extension, null, A68_PATTERN, INTEGRAL_C_PATTERN);
            reduce(a68, q, BottomUp::extension, null, A68_PATTERN, STRING_C_PATTERN);
            reduce(a68, q, null, null, A68_PATTERN, BITS_PATTERN);
            reduce(a68, q, null, null, A68_PATTERN, BOOLEAN_PATTERN);
            reduce(a68, q, null, null, A68_PATTERN, CHOICE_PATTERN);
            reduce(a68, q, null, null, A68_PATTERN, COMPLEX_PATTERN);
            reduce(a68, q, null, null, A68_PATTERN, FORMAT_PATTERN);
            reduce(a68, q, null, null, A68_PATTERN, GENERAL_PATTERN);
            reduce(a68, q, null, null, A68_PATTERN, INTEGRAL_PATTERN);
            reduce(a68, q, null, null, A68_PATTERN, REAL_PATTERN);
            reduce(a68, q, null, null, A68_PATTERN, STRING_PATTERN);
        }

        // Pictures.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, PICTURE, INSERTION);
            reduce(a68, q, null, null, PICTURE, A68_PATTERN);
            reduce(a68, q, null, null, PICTURE, COLLECTION);
            reduce(a68, q, null, null, PICTURE, REPLICATOR, COLLECTION);
        }

        // Picture lists.
        for (Node q = p; q != null; q = q.next) {
            if (q.is(PICTURE)) {
                boolean[] siga = {true};
                reduce(a68, q, null, null, PICTURE_LIST, PICTURE);
                while (siga[0]) {
                    siga[0] = false;
                    reduce(a68, q, null, siga, PICTURE_LIST, PICTURE_LIST, COMMA_SYMBOL, PICTURE);
                    // We filtered ambiguous patterns, so commas may be omitted
                    reduce(a68, q, null, siga, PICTURE_LIST, PICTURE_LIST, PICTURE);
                }
            }
        }
    }

    /** reduce_secondaries: reduce secondaries completely. */
    private static void reduceSecondaries(A68 a68, Node p) {
        Node q;
        boolean[] siga = new boolean[1];

        for (q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, SECONDARY, PRIMARY);
            reduce(a68, q, null, null, GENERATOR, LOC_SYMBOL, DECLARER);
            reduce(a68, q, null, null, GENERATOR, HEAP_SYMBOL, DECLARER);
            reduce(a68, q, null, null, SECONDARY, GENERATOR);
        }
        siga[0] = true;
        while (siga[0]) {
            siga[0] = false;
            for (q = p; q.next != null; q = q.next) {
                ;
            }
            for (; q != null; q = q.previous) {
                reduce(a68, q, null, siga, SELECTION, SELECTOR, SECONDARY);
                reduce(a68, q, null, siga, SECONDARY, SELECTION);
            }
        }
    }

    /**
     * operator_with_priority: whether Q is an operator with priority K.
     * (C returns int used as a truth value.)
     */
    private static boolean operatorWithPriority(A68 a68, Node q, int k) {
        return q.next != null
                && q.next.attribute == OPERATOR && q.next.info.priority == k;
    }

    /** reduce_formulae: reduce formulae. */
    private static void reduceFormulae(A68 a68, Node p) {
        Node q = p;

        while (q != null) {
            if (Parser.isOneOf(a68, q, OPERATOR, SECONDARY)) {
                // C: reduce_dyadic (q, STOP), STOP being 0.
                q = reduceDyadic(a68, q, STOP.ordinal());
            } else {
                q = q.next;
            }
        }

        // Reduce the expression.
        for (int prio = MAX_PRIORITY; prio >= 0; prio--) {
            for (q = p; q != null; q = q.next) {
                if (operatorWithPriority(a68, q, prio)) {
                    boolean[] siga = {false};
                    Node op = q.next;
                    if (q.is(SECONDARY)) {
                        reduce(a68, q, null, siga, FORMULA, SECONDARY, OPERATOR, SECONDARY);
                        reduce(a68, q, null, siga, FORMULA, SECONDARY, OPERATOR, MONADIC_FORMULA);
                        reduce(a68, q, null, siga, FORMULA, SECONDARY, OPERATOR, FORMULA);
                    } else if (q.is(MONADIC_FORMULA)) {
                        reduce(a68, q, null, siga, FORMULA, MONADIC_FORMULA, OPERATOR, SECONDARY);
                        reduce(a68, q, null, siga, FORMULA, MONADIC_FORMULA, OPERATOR, MONADIC_FORMULA);
                        reduce(a68, q, null, siga, FORMULA, MONADIC_FORMULA, OPERATOR, FORMULA);
                    }
                    if (prio == 0 && siga[0]) {
                        a68.diagnostics.error(op, "%e has no priority declaration", Fmt.symbol(op));
                    }
                    siga[0] = true;
                    while (siga[0]) {
                        Node op2 = q.next;
                        siga[0] = false;
                        if (operatorWithPriority(a68, q, prio)) {
                            reduce(a68, q, null, siga, FORMULA, FORMULA, OPERATOR, SECONDARY);
                        }
                        if (operatorWithPriority(a68, q, prio)) {
                            reduce(a68, q, null, siga, FORMULA, FORMULA, OPERATOR, MONADIC_FORMULA);
                        }
                        if (operatorWithPriority(a68, q, prio)) {
                            reduce(a68, q, null, siga, FORMULA, FORMULA, OPERATOR, FORMULA);
                        }
                        if (prio == 0 && siga[0]) {
                            a68.diagnostics.error(op2, "%e has no priority declaration", Fmt.symbol(op2));
                        }
                    }
                }
            }
        }
    }

    /** reduce_dyadic: reduce dyadic expressions. */
    private static Node reduceDyadic(A68 a68, Node p, int u) {
        // We work inside out - higher priority expressions get reduced first.
        if (u > MAX_PRIORITY) {
            if (p == null) {
                return null;
            } else if (p.is(OPERATOR)) {
                // Reduce monadic formulas.
                Node q = p;
                boolean siga;
                do {
                    q.info.priority = 10;
                    siga = ((q.next != null) && (q.next.is(OPERATOR)));
                    if (siga) {
                        q = q.next;
                    }
                } while (siga);
                reduce(a68, q, null, null, MONADIC_FORMULA, OPERATOR, SECONDARY);
                while (q != p) {
                    q = q.previous;
                    reduce(a68, q, null, null, MONADIC_FORMULA, OPERATOR, MONADIC_FORMULA);
                }
            }
            p = p.next;
        } else {
            p = reduceDyadic(a68, p, u + 1);
            while (p != null && p.is(OPERATOR) && p.info.priority == u) {
                p = p.next;
                p = reduceDyadic(a68, p, u + 1);
            }
        }
        return p;
    }

    /** reduce_tertiaries: reduce tertiaries completely. */
    private static void reduceTertiaries(A68 a68, Node p) {
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, TERTIARY, NIHIL);
            reduce(a68, q, null, null, FORMULA, MONADIC_FORMULA);
            reduce(a68, q, null, null, TERTIARY, FORMULA);
            reduce(a68, q, null, null, TERTIARY, SECONDARY);
        }
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, IDENTITY_RELATION, TERTIARY, IS_SYMBOL, TERTIARY);
            reduce(a68, q, null, null, IDENTITY_RELATION, TERTIARY, ISNT_SYMBOL, TERTIARY);
        }
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, AND_FUNCTION, TERTIARY, ANDF_SYMBOL, TERTIARY);
            reduce(a68, q, null, null, OR_FUNCTION, TERTIARY, ORF_SYMBOL, TERTIARY);
        }
    }

    /** reduce_formal_holes: reduce formal holes. */
    private static void reduceFormalHoles(A68 a68, Node p) {
        for (Node q = p; q != null; q = q.next) {
            boolean[] ahole = {false};
            reduce(a68, q, null, ahole, FORMAL_HOLE,
                    FORMAL_NEST_SYMBOL, TERTIARY);
            reduce(a68, q, null, ahole, FORMAL_HOLE,
                    FORMAL_NEST_SYMBOL, LANGUAGE_INDICANT, TERTIARY);

            if (ahole[0]) {
                // Check that the tertiary is a row of chars denotation.
                for (Node s = q.sub; s != null; s = s.next) {
                    if (s.is(TERTIARY)
                            && !(s.sub.is(SECONDARY)
                                    && s.sub.sub.is(PRIMARY)
                                    && s.sub.sub.sub.is(DENOTATION)
                                    && s.sub.sub.sub.sub.is(ROW_CHAR_DENOTATION))) {
                        a68.diagnostics.error(s, "expected %<row char%> denotation");
                    }
                }
            }
        }
    }

    /** reduce_units: reduce units. */
    private static void reduceUnits(A68 a68, Node p) {
        // Stray ~ is a SKIP.
        for (Node q = p; q != null; q = q.next) {
            if (q.is(OPERATOR) && q.info.symbol.equals("~")) {
                q.attribute = SKIP;
            }
        }

        // Reduce units.
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, UNIT, ASSIGNATION);
            reduce(a68, q, null, null, UNIT, IDENTITY_RELATION);
            reduce(a68, q, BottomUp::extension, null, UNIT, AND_FUNCTION);
            reduce(a68, q, BottomUp::extension, null, UNIT, OR_FUNCTION);
            reduce(a68, q, null, null, UNIT, ROUTINE_TEXT);
            reduce(a68, q, null, null, UNIT, JUMP);
            reduce(a68, q, null, null, UNIT, SKIP);
            reduce(a68, q, null, null, UNIT, TERTIARY);
            reduce(a68, q, null, null, UNIT, ASSERTION);
            reduce(a68, q, null, null, UNIT, FORMAL_HOLE);
        }
    }

    /** reduce_generic_arguments: reduce_generic arguments. */
    private static void reduceGenericArguments(A68 a68, Node p) {
        Node q;
        boolean[] siga = new boolean[1]; // In this scope.

        for (q = p; q != null; q = q.next) {
            if (q.is(UNIT)) {
                reduce(a68, q, null, null, TRIMMER, UNIT, COLON_SYMBOL, UNIT, AT_SYMBOL, UNIT);
                reduce(a68, q, null, null, TRIMMER, UNIT, COLON_SYMBOL, UNIT);
                reduce(a68, q, null, null, TRIMMER, UNIT, COLON_SYMBOL, AT_SYMBOL, UNIT);
                reduce(a68, q, null, null, TRIMMER, UNIT, COLON_SYMBOL);
            } else if (q.is(COLON_SYMBOL)) {
                reduce(a68, q, null, null, TRIMMER, COLON_SYMBOL, UNIT, AT_SYMBOL, UNIT);
                reduce(a68, q, null, null, TRIMMER, COLON_SYMBOL, UNIT);
                reduce(a68, q, null, null, TRIMMER, COLON_SYMBOL, AT_SYMBOL, UNIT);
                reduce(a68, q, null, null, TRIMMER, COLON_SYMBOL);
            }
        }

        for (q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, TRIMMER, UNIT, AT_SYMBOL, UNIT);
        }
        for (q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, TRIMMER, AT_SYMBOL, UNIT);
        }
        for (q = p; q != null && q.next != null; q = q.next) {
            if (q.is(COMMA_SYMBOL)) {
                if (!(q.next.attribute == UNIT || q.next.attribute == TRIMMER)) {
                    padNode(a68, q, TRIMMER);
                }
            } else {
                if (q.next.is(COMMA_SYMBOL)) {
                    if (!q.is(UNIT) && !q.is(TRIMMER)) {
                        padNode(a68, q, TRIMMER);
                    }
                }
            }
        }

        q = p.next;
        if (q == null) {
            throw Parser.gccUnreachable();
        }
        reduce(a68, q, null, null, GENERIC_ARGUMENT_LIST, UNIT);
        reduce(a68, q, null, null, GENERIC_ARGUMENT_LIST, TRIMMER);
        do {
            siga[0] = false;
            reduce(a68, q, null, siga, GENERIC_ARGUMENT_LIST, GENERIC_ARGUMENT_LIST, COMMA_SYMBOL, UNIT);
            reduce(a68, q, null, siga, GENERIC_ARGUMENT_LIST, GENERIC_ARGUMENT_LIST, COMMA_SYMBOL, TRIMMER);
            reduce(a68, q, BottomUp::strangeSeparator, siga, GENERIC_ARGUMENT_LIST, GENERIC_ARGUMENT_LIST, UNIT);
            reduce(a68, q, BottomUp::strangeSeparator, siga, GENERIC_ARGUMENT_LIST, GENERIC_ARGUMENT_LIST, TRIMMER);
        } while (siga[0]);
    }

    /** reduce_bounds: reduce bounds. */
    private static void reduceBounds(A68 a68, Node p) {
        Node q;
        boolean[] siga = new boolean[1];

        for (q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, BOUND, UNIT, COLON_SYMBOL, UNIT);
            reduce(a68, q, null, null, BOUND, UNIT);
        }
        q = p.next;
        reduce(a68, q, null, null, BOUNDS_LIST, BOUND);
        reduce(a68, q, null, null, FORMAL_BOUNDS_LIST, COMMA_SYMBOL);
        reduce(a68, q, null, null, ALT_FORMAL_BOUNDS_LIST, COLON_SYMBOL);
        do {
            siga[0] = false;
            reduce(a68, q, null, siga, BOUNDS_LIST, BOUNDS_LIST, COMMA_SYMBOL, BOUND);
            reduce(a68, q, null, siga, FORMAL_BOUNDS_LIST, FORMAL_BOUNDS_LIST, COMMA_SYMBOL);
            reduce(a68, q, null, siga, ALT_FORMAL_BOUNDS_LIST, FORMAL_BOUNDS_LIST, COLON_SYMBOL);
            reduce(a68, q, null, siga, FORMAL_BOUNDS_LIST, ALT_FORMAL_BOUNDS_LIST, COMMA_SYMBOL);
            reduce(a68, q, BottomUp::strangeSeparator, siga, BOUNDS_LIST, BOUNDS_LIST, BOUND);
        } while (siga[0]);
    }

    /** reduce_arguments: reduce argument packs. */
    private static void reduceArguments(A68 a68, Node p) {
        if (p.next != null) {
            Node q = p.next;
            boolean[] siga = new boolean[1];
            reduce(a68, q, null, null, ARGUMENT_LIST, UNIT);
            do {
                siga[0] = false;
                reduce(a68, q, null, siga, ARGUMENT_LIST, ARGUMENT_LIST, COMMA_SYMBOL, UNIT);
                reduce(a68, q, BottomUp::strangeSeparator, siga, ARGUMENT_LIST, ARGUMENT_LIST, UNIT);
            } while (siga[0]);
        }
    }

    /** reduce_basic_declarations: reduce declarations. */
    private static void reduceBasicDeclarations(A68 a68, Node p) {
        for (Node q = p; q != null; q = q.next) {
            // Publicized declarations.
            reduce(a68, q, null, null,
                    PRIORITY_DECLARATION, PUBLIC_SYMBOL,
                    PRIO_SYMBOL, DEFINING_OPERATOR, EQUALS_SYMBOL, PRIORITY);
            reduce(a68, q, null, null,
                    MODE_DECLARATION, PUBLIC_SYMBOL,
                    MODE_SYMBOL, DEFINING_INDICANT, EQUALS_SYMBOL, DECLARER);
            reduce(a68, q, null, null,
                    MODE_DECLARATION, PUBLIC_SYMBOL,
                    MODE_SYMBOL, DEFINING_INDICANT, EQUALS_SYMBOL, VOID_SYMBOL);
            reduce(a68, q, null, null,
                    PROCEDURE_DECLARATION, PUBLIC_SYMBOL,
                    PROC_SYMBOL, DEFINING_IDENTIFIER, EQUALS_SYMBOL, ROUTINE_TEXT);
            reduce(a68, q, null, null,
                    PROCEDURE_VARIABLE_DECLARATION, PUBLIC_SYMBOL,
                    PROC_SYMBOL, DEFINING_IDENTIFIER, ASSIGN_SYMBOL, ROUTINE_TEXT);
            reduce(a68, q, null, null,
                    PROCEDURE_VARIABLE_DECLARATION, PUBLIC_SYMBOL,
                    QUALIFIER, PROC_SYMBOL, DEFINING_IDENTIFIER, ASSIGN_SYMBOL,
                    ROUTINE_TEXT);
            reduce(a68, q, null, null,
                    BRIEF_OPERATOR_DECLARATION, PUBLIC_SYMBOL,
                    OP_SYMBOL, DEFINING_OPERATOR, EQUALS_SYMBOL, ROUTINE_TEXT);

            reduce(a68, q, null, null,
                    PRIORITY_DECLARATION, PRIO_SYMBOL, DEFINING_OPERATOR, EQUALS_SYMBOL, PRIORITY);
            reduce(a68, q, null, null,
                    MODE_DECLARATION, MODE_SYMBOL, DEFINING_INDICANT, EQUALS_SYMBOL, DECLARER);
            reduce(a68, q, null, null,
                    MODE_DECLARATION, MODE_SYMBOL, DEFINING_INDICANT, EQUALS_SYMBOL, VOID_SYMBOL);
            reduce(a68, q, null, null,
                    PROCEDURE_DECLARATION, PROC_SYMBOL, DEFINING_IDENTIFIER, EQUALS_SYMBOL, ROUTINE_TEXT);
            reduce(a68, q, null, null,
                    PROCEDURE_VARIABLE_DECLARATION, PROC_SYMBOL, DEFINING_IDENTIFIER, ASSIGN_SYMBOL, ROUTINE_TEXT);
            reduce(a68, q, null, null,
                    PROCEDURE_VARIABLE_DECLARATION, QUALIFIER, PROC_SYMBOL, DEFINING_IDENTIFIER, ASSIGN_SYMBOL,
                    ROUTINE_TEXT);
            reduce(a68, q, null, null,
                    BRIEF_OPERATOR_DECLARATION, OP_SYMBOL, DEFINING_OPERATOR, EQUALS_SYMBOL, ROUTINE_TEXT);
            // C: #if 0
            //   /* XXX for local module definitions */
            //   reduce (q, NO_NOTE, NO_TICK,
            //           MODULE_DECLARATION, MODULE_SYMBOL, DEFINING_INDICANT, EQUALS_SYMBOL, MODULE_TEXT, STOP);
            // #endif
            // Errors.
            reduce(a68, q, BottomUp::strangeTokens, null,
                    PRIORITY_DECLARATION, PRIO_SYMBOL, Parser.not(DEFINING_OPERATOR), Parser.not(EQUALS_SYMBOL),
                    Parser.not(PRIORITY));
            reduce(a68, q, BottomUp::strangeTokens, null,
                    MODE_DECLARATION, MODE_SYMBOL, DEFINING_INDICANT, EQUALS_SYMBOL, Parser.not(DECLARER));
            reduce(a68, q, BottomUp::strangeTokens, null,
                    PROCEDURE_DECLARATION, PROC_SYMBOL, DEFINING_IDENTIFIER, EQUALS_SYMBOL,
                    Parser.not(ROUTINE_TEXT));
            reduce(a68, q, BottomUp::strangeTokens, null,
                    PROCEDURE_VARIABLE_DECLARATION, PROC_SYMBOL, DEFINING_IDENTIFIER, ASSIGN_SYMBOL,
                    Parser.not(ROUTINE_TEXT));
            reduce(a68, q, BottomUp::strangeTokens, null,
                    PROCEDURE_VARIABLE_DECLARATION, QUALIFIER, PROC_SYMBOL, DEFINING_IDENTIFIER, ASSIGN_SYMBOL,
                    Parser.not(ROUTINE_TEXT));
            reduce(a68, q, BottomUp::strangeTokens, null,
                    BRIEF_OPERATOR_DECLARATION, OP_SYMBOL, DEFINING_OPERATOR, EQUALS_SYMBOL,
                    Parser.not(ROUTINE_TEXT));
            // C: #if 0
            //   /* XXX for local module definitions */
            //   reduce (q, strange_tokens, NO_TICK,
            //           MODULE_DECLARATION, MODULE_SYMBOL, DEFINING_INDICANT, EQUALS_SYMBOL, -MODULE_TEXT, STOP);
            // #endif
            // Errors. WILDCARD catches TERTIARY which catches IDENTIFIER.
            reduce(a68, q, BottomUp::strangeTokens, null, PROCEDURE_DECLARATION, PROC_SYMBOL, WILDCARD, ROUTINE_TEXT);
        }
        for (Node q = p; q != null; q = q.next) {
            boolean[] siga = new boolean[1];
            do {
                siga[0] = false;
                reduce(a68, q, null, siga,
                        PRIORITY_DECLARATION, PRIORITY_DECLARATION, COMMA_SYMBOL, DEFINING_OPERATOR,
                        EQUALS_SYMBOL, PRIORITY);
                reduce(a68, q, null, siga,
                        MODE_DECLARATION, MODE_DECLARATION, COMMA_SYMBOL, DEFINING_INDICANT, EQUALS_SYMBOL,
                        DECLARER);
                reduce(a68, q, null, siga,
                        MODE_DECLARATION, MODE_DECLARATION, COMMA_SYMBOL, DEFINING_INDICANT, EQUALS_SYMBOL,
                        VOID_SYMBOL);
                reduce(a68, q, null, siga,
                        PROCEDURE_DECLARATION, PROCEDURE_DECLARATION, COMMA_SYMBOL, DEFINING_IDENTIFIER,
                        EQUALS_SYMBOL, ROUTINE_TEXT);
                reduce(a68, q, null, siga,
                        PROCEDURE_VARIABLE_DECLARATION, PROCEDURE_VARIABLE_DECLARATION, COMMA_SYMBOL,
                        DEFINING_IDENTIFIER, ASSIGN_SYMBOL, ROUTINE_TEXT);
                reduce(a68, q, null, siga,
                        BRIEF_OPERATOR_DECLARATION, BRIEF_OPERATOR_DECLARATION, COMMA_SYMBOL,
                        DEFINING_OPERATOR, EQUALS_SYMBOL, ROUTINE_TEXT);
                // C: #if 0
                //   /* XXX for local module definitions */
                //   reduce (q, NO_NOTE, &siga,
                //           MODULE_DECLARATION, MODULE_DECLARATION, COMMA_SYMBOL, DEFINING_INDICANT, EQUALS_SYMBOL,
                //           MODULE_TEXT, STOP);
                // #endif
                // Errors. WILDCARD catches TERTIARY which catches IDENTIFIER.
                reduce(a68, q, BottomUp::strangeTokens, siga,
                        PROCEDURE_DECLARATION, PROCEDURE_DECLARATION, COMMA_SYMBOL, WILDCARD, ROUTINE_TEXT);
            } while (siga[0]);
        }
    }

    /** reduce_declaration_lists: reduce declaration lists. */
    private static void reduceDeclarationLists(A68 a68, Node p) {
        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null,
                    IDENTITY_DECLARATION, PUBLIC_SYMBOL,
                    DECLARER, DEFINING_IDENTIFIER, EQUALS_SYMBOL, UNIT);
            reduce(a68, q, null, null,
                    VARIABLE_DECLARATION, PUBLIC_SYMBOL,
                    QUALIFIER, DECLARER, DEFINING_IDENTIFIER, ASSIGN_SYMBOL, UNIT);
            reduce(a68, q, null, null,
                    VARIABLE_DECLARATION, PUBLIC_SYMBOL,
                    QUALIFIER, DECLARER, DEFINING_IDENTIFIER);
            reduce(a68, q, null, null,
                    VARIABLE_DECLARATION, PUBLIC_SYMBOL,
                    DECLARER, DEFINING_IDENTIFIER, ASSIGN_SYMBOL, UNIT);
            reduce(a68, q, null, null,
                    VARIABLE_DECLARATION, PUBLIC_SYMBOL,
                    DECLARER, DEFINING_IDENTIFIER);

            reduce(a68, q, null, null,
                    IDENTITY_DECLARATION, DECLARER, DEFINING_IDENTIFIER, EQUALS_SYMBOL, UNIT);
            reduce(a68, q, null, null,
                    VARIABLE_DECLARATION, QUALIFIER, DECLARER, DEFINING_IDENTIFIER, ASSIGN_SYMBOL, UNIT);
            reduce(a68, q, null, null,
                    VARIABLE_DECLARATION, QUALIFIER, DECLARER, DEFINING_IDENTIFIER);
            reduce(a68, q, null, null,
                    VARIABLE_DECLARATION, DECLARER, DEFINING_IDENTIFIER, ASSIGN_SYMBOL, UNIT);
            reduce(a68, q, null, null,
                    VARIABLE_DECLARATION, DECLARER, DEFINING_IDENTIFIER);
        }

        for (Node q = p; q != null; q = q.next) {
            boolean[] siga = new boolean[1];
            do {
                siga[0] = false;
                reduce(a68, q, null, siga,
                        IDENTITY_DECLARATION, IDENTITY_DECLARATION, COMMA_SYMBOL, DEFINING_IDENTIFIER,
                        EQUALS_SYMBOL, UNIT);
                reduce(a68, q, null, siga,
                        VARIABLE_DECLARATION, VARIABLE_DECLARATION, COMMA_SYMBOL, DEFINING_IDENTIFIER,
                        ASSIGN_SYMBOL, UNIT);
                if (!Parser.whether(a68, q, VARIABLE_DECLARATION, COMMA_SYMBOL, DEFINING_IDENTIFIER,
                        ASSIGN_SYMBOL, UNIT)) {
                    reduce(a68, q, null, siga,
                            VARIABLE_DECLARATION, VARIABLE_DECLARATION, COMMA_SYMBOL, DEFINING_IDENTIFIER);
                }
            } while (siga[0]);
        }

        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null,
                    OPERATOR_DECLARATION, PUBLIC_SYMBOL,
                    OPERATOR_PLAN, DEFINING_OPERATOR, EQUALS_SYMBOL, UNIT);
            reduce(a68, q, null, null,
                    OPERATOR_DECLARATION, OPERATOR_PLAN, DEFINING_OPERATOR, EQUALS_SYMBOL, UNIT);
        }

        for (Node q = p; q != null; q = q.next) {
            boolean[] siga = new boolean[1];
            do {
                siga[0] = false;
                reduce(a68, q, null, siga,
                        OPERATOR_DECLARATION, OPERATOR_DECLARATION, COMMA_SYMBOL, DEFINING_OPERATOR,
                        EQUALS_SYMBOL, UNIT);
            } while (siga[0]);
        }

        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, DECLARATION_LIST, MODE_DECLARATION);
            reduce(a68, q, null, null, DECLARATION_LIST, PRIORITY_DECLARATION);
            reduce(a68, q, null, null, DECLARATION_LIST, BRIEF_OPERATOR_DECLARATION);
            reduce(a68, q, null, null, DECLARATION_LIST, OPERATOR_DECLARATION);
            reduce(a68, q, null, null, DECLARATION_LIST, IDENTITY_DECLARATION);
            reduce(a68, q, null, null, DECLARATION_LIST, PROCEDURE_DECLARATION);
            reduce(a68, q, null, null, DECLARATION_LIST, PROCEDURE_VARIABLE_DECLARATION);
            reduce(a68, q, null, null, DECLARATION_LIST, VARIABLE_DECLARATION);
            // C: #if 0
            //   /* XXX for local module definitions */
            //   reduce (q, NO_NOTE, NO_TICK, DECLARATION_LIST, MODULE_DECLARATION, STOP);
            // #endif
        }

        for (Node q = p; q != null; q = q.next) {
            boolean[] siga = new boolean[1];
            do {
                siga[0] = false;
                reduce(a68, q, null, siga,
                        DECLARATION_LIST, DECLARATION_LIST, COMMA_SYMBOL, DECLARATION_LIST);
            } while (siga[0]);
        }
    }

    /** reduce_module_texts: reduce module text. */
    private static void reduceModuleTexts(A68 a68, Node p) {
        if (p.is(ALT_ACCESS_SYMBOL)) {
            reduce(a68, p, null, null,
                    REVELATION, ALT_ACCESS_SYMBOL, PUBLIC_SYMBOL, MODULE_INDICANT);
            reduce(a68, p, null, null,
                    REVELATION, ALT_ACCESS_SYMBOL, MODULE_INDICANT);
            boolean[] siga = new boolean[1];
            do {
                siga[0] = false;
                reduce(a68, p, null, siga,
                        REVELATION, REVELATION, COMMA_SYMBOL, PUBLIC_SYMBOL, MODULE_INDICANT);
                reduce(a68, p, null, siga,
                        REVELATION, REVELATION, COMMA_SYMBOL, MODULE_INDICANT);
            } while (siga[0]);
            reduce(a68, p, null, null, REVELATION_PART, REVELATION);
        }

        for (Node q = p; q != null; q = q.next) {
            reduce(a68, q, null, null, MODULE_TEXT, REVELATION_PART, DEF_PART, POSTLUDE_PART, FED_SYMBOL);
            reduce(a68, q, null, null, MODULE_TEXT, REVELATION_PART, DEF_PART, FED_SYMBOL);
            reduce(a68, q, null, null, MODULE_TEXT, DEF_PART, POSTLUDE_PART, FED_SYMBOL);
            reduce(a68, q, null, null, MODULE_TEXT, DEF_PART, FED_SYMBOL);
        }
    }

    /**
     * reduce_module_text_parts: reduce def-parts and postlude-parts.
     * Note that revelations are reduced in reduce_module_texts.
     */
    private static void reduceModuleTextParts(A68 a68, Node p) {
        if (p.is(DEF_SYMBOL)) {
            reduceEnquiryClauses(a68, p);
            reduce(a68, p, null, null, DEF_PART, DEF_SYMBOL, ENQUIRY_CLAUSE);
        } else if (p.is(POSTLUDE_SYMBOL)) {
            reduceSerialClauses(a68, p);
            reduce(a68, p, null, null, POSTLUDE_PART, POSTLUDE_SYMBOL, SERIAL_CLAUSE);
        }
    }

    /** reduce_serial_clauses: reduce serial clauses. */
    private static void reduceSerialClauses(A68 a68, Node p) {
        if (p.next != null) {
            Node q = p.next, u;
            boolean[] siga = new boolean[1];
            boolean labelSeen;
            // Check wrong exits.
            for (u = q; u != null; u = u.next) {
                if (u.is(EXIT_SYMBOL)) {
                    if (u.next == null || !u.next.is(LABELED_UNIT)) {
                        a68.diagnostics.error(u, "%e must be followed by a labeled unit", Fmt.symbol(u));
                    }
                }
            }

            // Check wrong jumps and declarations.
            for (u = q, labelSeen = false; u != null; u = u.next) {
                if (u.is(LABELED_UNIT)) {
                    labelSeen = true;
                } else if (u.is(DECLARATION_LIST)) {
                    if (labelSeen) {
                        a68.diagnostics.error(u, "declaration cannot follow a labeled unit");
                    }
                }
            }

            // Reduce serial clauses.
            reduce(a68, q, null, null, SERIAL_CLAUSE, LABELED_UNIT);
            reduce(a68, q, null, null, SERIAL_CLAUSE, UNIT);
            reduce(a68, q, null, null, INITIALISER_SERIES, DECLARATION_LIST);
            do {
                siga[0] = false;
                if (q.is(SERIAL_CLAUSE)) {
                    reduce(a68, q, null, siga,
                            SERIAL_CLAUSE, SERIAL_CLAUSE, SEMI_SYMBOL, UNIT);
                    reduce(a68, q, null, siga,
                            SERIAL_CLAUSE, SERIAL_CLAUSE, EXIT_SYMBOL, LABELED_UNIT);
                    reduce(a68, q, null, siga,
                            SERIAL_CLAUSE, SERIAL_CLAUSE, SEMI_SYMBOL, LABELED_UNIT);
                    reduce(a68, q, null, siga,
                            INITIALISER_SERIES, SERIAL_CLAUSE, SEMI_SYMBOL, DECLARATION_LIST);
                    // Errors
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SERIAL_CLAUSE, SERIAL_CLAUSE, COMMA_SYMBOL, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SERIAL_CLAUSE, SERIAL_CLAUSE, COMMA_SYMBOL, LABELED_UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            INITIALISER_SERIES, SERIAL_CLAUSE, COMMA_SYMBOL, DECLARATION_LIST);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SERIAL_CLAUSE, SERIAL_CLAUSE, COLON_SYMBOL, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SERIAL_CLAUSE, SERIAL_CLAUSE, COLON_SYMBOL, LABELED_UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            INITIALISER_SERIES, SERIAL_CLAUSE, COLON_SYMBOL, DECLARATION_LIST);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SERIAL_CLAUSE, SERIAL_CLAUSE, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SERIAL_CLAUSE, SERIAL_CLAUSE, LABELED_UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            INITIALISER_SERIES, SERIAL_CLAUSE, DECLARATION_LIST);
                } else if (q.is(INITIALISER_SERIES)) {
                    reduce(a68, q, null, siga,
                            SERIAL_CLAUSE, INITIALISER_SERIES, SEMI_SYMBOL, UNIT);
                    reduce(a68, q, null, siga,
                            SERIAL_CLAUSE, INITIALISER_SERIES, SEMI_SYMBOL, LABELED_UNIT);
                    reduce(a68, q, null, siga,
                            INITIALISER_SERIES, INITIALISER_SERIES, SEMI_SYMBOL, DECLARATION_LIST);
                    // Errors
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SERIAL_CLAUSE, INITIALISER_SERIES, COMMA_SYMBOL, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SERIAL_CLAUSE, INITIALISER_SERIES, COMMA_SYMBOL, LABELED_UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            INITIALISER_SERIES, INITIALISER_SERIES, COMMA_SYMBOL, DECLARATION_LIST);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SERIAL_CLAUSE, INITIALISER_SERIES, COLON_SYMBOL, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SERIAL_CLAUSE, INITIALISER_SERIES, COLON_SYMBOL, LABELED_UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            INITIALISER_SERIES, INITIALISER_SERIES, COLON_SYMBOL, DECLARATION_LIST);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SERIAL_CLAUSE, INITIALISER_SERIES, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SERIAL_CLAUSE, INITIALISER_SERIES, LABELED_UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            INITIALISER_SERIES, INITIALISER_SERIES, DECLARATION_LIST);
                }
            } while (siga[0]);
        }
    }

    /** reduce_enquiry_clauses: reduce enquiry clauses. */
    private static void reduceEnquiryClauses(A68 a68, Node p) {
        if (p.next != null) {
            Node q = p.next;
            boolean[] siga = new boolean[1];
            reduce(a68, q, null, null, ENQUIRY_CLAUSE, UNIT);
            reduce(a68, q, null, null, INITIALISER_SERIES, DECLARATION_LIST);
            do {
                siga[0] = false;
                if (q.is(ENQUIRY_CLAUSE)) {
                    reduce(a68, q, null, siga,
                            ENQUIRY_CLAUSE, ENQUIRY_CLAUSE, SEMI_SYMBOL, UNIT);
                    reduce(a68, q, null, siga,
                            INITIALISER_SERIES, ENQUIRY_CLAUSE, SEMI_SYMBOL, DECLARATION_LIST);
                    // Errors
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            ENQUIRY_CLAUSE, ENQUIRY_CLAUSE, COMMA_SYMBOL, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            INITIALISER_SERIES, ENQUIRY_CLAUSE, COMMA_SYMBOL, DECLARATION_LIST);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            ENQUIRY_CLAUSE, ENQUIRY_CLAUSE, COLON_SYMBOL, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            INITIALISER_SERIES, ENQUIRY_CLAUSE, COLON_SYMBOL, DECLARATION_LIST);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            ENQUIRY_CLAUSE, ENQUIRY_CLAUSE, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            INITIALISER_SERIES, ENQUIRY_CLAUSE, DECLARATION_LIST);
                } else if (q.is(INITIALISER_SERIES)) {
                    reduce(a68, q, null, siga,
                            ENQUIRY_CLAUSE, INITIALISER_SERIES, SEMI_SYMBOL, UNIT);
                    reduce(a68, q, null, siga,
                            INITIALISER_SERIES, INITIALISER_SERIES, SEMI_SYMBOL, DECLARATION_LIST);
                    // Errors
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            ENQUIRY_CLAUSE, INITIALISER_SERIES, COMMA_SYMBOL, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            INITIALISER_SERIES, INITIALISER_SERIES, COMMA_SYMBOL, DECLARATION_LIST);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            ENQUIRY_CLAUSE, INITIALISER_SERIES, COLON_SYMBOL, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            INITIALISER_SERIES, INITIALISER_SERIES, COLON_SYMBOL, DECLARATION_LIST);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            ENQUIRY_CLAUSE, INITIALISER_SERIES, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            INITIALISER_SERIES, INITIALISER_SERIES, DECLARATION_LIST);
                }
            } while (siga[0]);
        }
    }

    /** reduce_collateral_clauses: reduce collateral clauses. */
    private static void reduceCollateralClauses(A68 a68, Node p) {
        if (p.next != null) {
            Node q = p.next;
            if (q.is(UNIT)) {
                boolean[] siga = new boolean[1];
                reduce(a68, q, null, null, UNIT_LIST, UNIT);
                do {
                    siga[0] = false;
                    reduce(a68, q, null, siga, UNIT_LIST, UNIT_LIST, COMMA_SYMBOL, UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga, UNIT_LIST, UNIT_LIST, UNIT);
                } while (siga[0]);
            } else if (q.is(SPECIFIED_UNIT)) {
                boolean[] siga = new boolean[1];
                reduce(a68, q, null, null, SPECIFIED_UNIT_LIST, SPECIFIED_UNIT);
                do {
                    siga[0] = false;
                    reduce(a68, q, null, siga,
                            SPECIFIED_UNIT_LIST, SPECIFIED_UNIT_LIST, COMMA_SYMBOL, SPECIFIED_UNIT);
                    reduce(a68, q, BottomUp::strangeSeparator, siga,
                            SPECIFIED_UNIT_LIST, SPECIFIED_UNIT_LIST, SPECIFIED_UNIT);
                } while (siga[0]);
            }
        }
    }

    /** reduce_enclosed_clauses: reduces enclosed clauses. */
    private static void reduceEnclosedClauses(A68 a68, Node q, Attribute expect) {
        Node p = q;

        if (p.sub == null) {
            if (p.is(FOR_SYMBOL)) {
                reduce(a68, p, null, null, FOR_PART, FOR_SYMBOL, DEFINING_IDENTIFIER);
            } else if (p.is(OPEN_SYMBOL)) {
                if (expect == ENQUIRY_CLAUSE) {
                    reduce(a68, p, null, null, OPEN_PART, OPEN_SYMBOL, ENQUIRY_CLAUSE);
                } else if (expect == ARGUMENT) {
                    reduce(a68, p, null, null, ARGUMENT, OPEN_SYMBOL, CLOSE_SYMBOL);
                    reduce(a68, p, null, null, ARGUMENT, OPEN_SYMBOL, ARGUMENT_LIST, CLOSE_SYMBOL);
                    reduce(a68, p, BottomUp::emptyClause, null, ARGUMENT, OPEN_SYMBOL, INITIALISER_SERIES, CLOSE_SYMBOL);
                } else if (expect == GENERIC_ARGUMENT) {
                    if (Parser.whether(a68, p, OPEN_SYMBOL, CLOSE_SYMBOL)) {
                        padNode(a68, p, TRIMMER);
                        reduce(a68, p, null, null, GENERIC_ARGUMENT, OPEN_SYMBOL, TRIMMER, CLOSE_SYMBOL);
                    }
                    reduce(a68, p, null, null,
                            GENERIC_ARGUMENT, OPEN_SYMBOL, GENERIC_ARGUMENT_LIST, CLOSE_SYMBOL);
                } else if (expect == BOUNDS) {
                    reduce(a68, p, null, null, FORMAL_BOUNDS, OPEN_SYMBOL, CLOSE_SYMBOL);
                    reduce(a68, p, null, null, BOUNDS, OPEN_SYMBOL, BOUNDS_LIST, CLOSE_SYMBOL);
                    reduce(a68, p, null, null, FORMAL_BOUNDS, OPEN_SYMBOL, FORMAL_BOUNDS_LIST, CLOSE_SYMBOL);
                    reduce(a68, p, null, null,
                            FORMAL_BOUNDS, OPEN_SYMBOL, ALT_FORMAL_BOUNDS_LIST, CLOSE_SYMBOL);
                } else {
                    reduce(a68, p, null, null, CLOSED_CLAUSE, OPEN_SYMBOL, SERIAL_CLAUSE, CLOSE_SYMBOL);
                    reduce(a68, p, null, null, COLLATERAL_CLAUSE, OPEN_SYMBOL, UNIT_LIST, CLOSE_SYMBOL);
                    reduce(a68, p, null, null, COLLATERAL_CLAUSE, OPEN_SYMBOL, CLOSE_SYMBOL);
                    reduce(a68, p, BottomUp::emptyClause, null,
                            CLOSED_CLAUSE, OPEN_SYMBOL, INITIALISER_SERIES, CLOSE_SYMBOL);
                }
            } else if (p.is(SUB_SYMBOL)) {
                if (expect == GENERIC_ARGUMENT) {
                    if (Parser.whether(a68, p, SUB_SYMBOL, BUS_SYMBOL)) {
                        padNode(a68, p, TRIMMER);
                        reduce(a68, p, null, null, GENERIC_ARGUMENT, SUB_SYMBOL, TRIMMER, BUS_SYMBOL);
                    }
                    reduce(a68, p, null, null,
                            GENERIC_ARGUMENT, SUB_SYMBOL, GENERIC_ARGUMENT_LIST, BUS_SYMBOL);
                } else if (expect == BOUNDS) {
                    reduce(a68, p, null, null, FORMAL_BOUNDS, SUB_SYMBOL, BUS_SYMBOL);
                    reduce(a68, p, null, null, BOUNDS, SUB_SYMBOL, BOUNDS_LIST, BUS_SYMBOL);
                    reduce(a68, p, null, null, FORMAL_BOUNDS, SUB_SYMBOL, FORMAL_BOUNDS_LIST, BUS_SYMBOL);
                    reduce(a68, p, null, null, FORMAL_BOUNDS, SUB_SYMBOL, ALT_FORMAL_BOUNDS_LIST, BUS_SYMBOL);
                }
            } else if (p.is(BEGIN_SYMBOL)) {
                reduce(a68, p, null, null, COLLATERAL_CLAUSE, BEGIN_SYMBOL, UNIT_LIST, END_SYMBOL);
                reduce(a68, p, null, null, COLLATERAL_CLAUSE, BEGIN_SYMBOL, END_SYMBOL);
                reduce(a68, p, null, null, CLOSED_CLAUSE, BEGIN_SYMBOL, SERIAL_CLAUSE, END_SYMBOL);
                reduce(a68, p, BottomUp::emptyClause, null, CLOSED_CLAUSE, BEGIN_SYMBOL, INITIALISER_SERIES, END_SYMBOL);
            } else if (p.is(FORMAT_DELIMITER_SYMBOL)) {
                reduce(a68, p, null, null,
                        FORMAT_TEXT, FORMAT_DELIMITER_SYMBOL, PICTURE_LIST, FORMAT_DELIMITER_SYMBOL);
                reduce(a68, p, null, null,
                        FORMAT_TEXT, FORMAT_DELIMITER_SYMBOL, FORMAT_DELIMITER_SYMBOL);
            } else if (p.is(FORMAT_OPEN_SYMBOL)) {
                reduce(a68, p, null, null,
                        COLLECTION, FORMAT_OPEN_SYMBOL, PICTURE_LIST, FORMAT_CLOSE_SYMBOL);
            } else if (p.is(ACCESS_SYMBOL)) {
                Node s;
                for (s = p; s != null; s = s.next) {
                    if (s.sub != null) {
                        // XXX why
                        if (s.sub.is(OPEN_SYMBOL)) {
                            reduceBranch(a68, s, SOME_CLAUSE);
                        } else {
                            reduceBranch(a68, s, ENCLOSED_CLAUSE);
                        }
                    }
                    reduce(a68, s, null, null, PARALLEL_CLAUSE, PAR_SYMBOL, COLLATERAL_CLAUSE);
                    reduce(a68, s, null, null, ENCLOSED_CLAUSE, PARALLEL_CLAUSE);
                    reduce(a68, s, null, null, ENCLOSED_CLAUSE, CLOSED_CLAUSE);
                    reduce(a68, s, null, null, ENCLOSED_CLAUSE, COLLATERAL_CLAUSE);
                    reduce(a68, s, null, null, ENCLOSED_CLAUSE, CONDITIONAL_CLAUSE);
                    reduce(a68, s, null, null, ENCLOSED_CLAUSE, CASE_CLAUSE);
                    reduce(a68, s, null, null, ENCLOSED_CLAUSE, CONFORMITY_CLAUSE);
                    reduce(a68, s, null, null, ENCLOSED_CLAUSE, LOOP_CLAUSE);
                    reduce(a68, s, null, null, ENCLOSED_CLAUSE, ACCESS_CLAUSE);
                }

                // Reduce revelations.

                reduce(a68, p, null, null,
                        REVELATION, ACCESS_SYMBOL, MODULE_INDICANT);

                boolean[] siga = new boolean[1];
                do {
                    siga[0] = false;
                    reduce(a68, p, null, siga,
                            REVELATION, REVELATION, COMMA_SYMBOL, MODULE_INDICANT);
                } while (siga[0]);
                reduce(a68, p, null, null,
                        ACCESS_CLAUSE, REVELATION, ENCLOSED_CLAUSE);
            } else if (p.is(IF_SYMBOL)) {
                reduce(a68, p, null, null, IF_PART, IF_SYMBOL, ENQUIRY_CLAUSE);
                reduce(a68, p, BottomUp::emptyClause, null, IF_PART, IF_SYMBOL, INITIALISER_SERIES);
            } else if (p.is(THEN_SYMBOL)) {
                reduce(a68, p, null, null, THEN_PART, THEN_SYMBOL, SERIAL_CLAUSE);
                reduce(a68, p, BottomUp::emptyClause, null, THEN_PART, THEN_SYMBOL, INITIALISER_SERIES);
            } else if (p.is(ELSE_SYMBOL)) {
                reduce(a68, p, null, null, ELSE_PART, ELSE_SYMBOL, SERIAL_CLAUSE);
                reduce(a68, p, BottomUp::emptyClause, null, ELSE_PART, ELSE_SYMBOL, INITIALISER_SERIES);
            } else if (p.is(ELIF_SYMBOL)) {
                reduce(a68, p, null, null, ELIF_IF_PART, ELIF_SYMBOL, ENQUIRY_CLAUSE);
            } else if (p.is(CASE_SYMBOL)) {
                reduce(a68, p, null, null, CASE_PART, CASE_SYMBOL, ENQUIRY_CLAUSE);
                reduce(a68, p, BottomUp::emptyClause, null, CASE_PART, CASE_SYMBOL, INITIALISER_SERIES);
            } else if (p.is(IN_SYMBOL)) {
                reduce(a68, p, null, null, CASE_IN_PART, IN_SYMBOL, UNIT_LIST);
                reduce(a68, p, null, null, CONFORMITY_IN_PART, IN_SYMBOL, SPECIFIED_UNIT_LIST);
            } else if (p.is(OUT_SYMBOL)) {
                reduce(a68, p, null, null, OUT_PART, OUT_SYMBOL, SERIAL_CLAUSE);
                reduce(a68, p, BottomUp::emptyClause, null, OUT_PART, OUT_SYMBOL, INITIALISER_SERIES);
            } else if (p.is(OUSE_SYMBOL)) {
                reduce(a68, p, null, null, OUSE_PART, OUSE_SYMBOL, ENQUIRY_CLAUSE);
            } else if (p.is(THEN_BAR_SYMBOL)) {
                reduce(a68, p, null, null, CHOICE, THEN_BAR_SYMBOL, SERIAL_CLAUSE);
                reduce(a68, p, null, null, CASE_CHOICE_CLAUSE, THEN_BAR_SYMBOL, UNIT_LIST);
                reduce(a68, p, null, null, CONFORMITY_CHOICE, THEN_BAR_SYMBOL, SPECIFIED_UNIT_LIST);
                reduce(a68, p, null, null, CONFORMITY_CHOICE, THEN_BAR_SYMBOL, SPECIFIED_UNIT);
                reduce(a68, p, BottomUp::emptyClause, null, CHOICE, THEN_BAR_SYMBOL, INITIALISER_SERIES);
            } else if (p.is(ELSE_BAR_SYMBOL)) {
                reduce(a68, p, null, null, ELSE_OPEN_PART, ELSE_BAR_SYMBOL, ENQUIRY_CLAUSE);
                reduce(a68, p, BottomUp::emptyClause, null, ELSE_OPEN_PART, ELSE_BAR_SYMBOL, INITIALISER_SERIES);
            } else if (p.is(FROM_SYMBOL)) {
                reduce(a68, p, null, null, FROM_PART, FROM_SYMBOL, UNIT);
            } else if (p.is(BY_SYMBOL)) {
                reduce(a68, p, null, null, BY_PART, BY_SYMBOL, UNIT);
            } else if (p.is(TO_SYMBOL)) {
                reduce(a68, p, null, null, TO_PART, TO_SYMBOL, UNIT);
            } else if (p.is(WHILE_SYMBOL)) {
                reduce(a68, p, null, null, WHILE_PART, WHILE_SYMBOL, ENQUIRY_CLAUSE);
                reduce(a68, p, BottomUp::emptyClause, null, WHILE_PART, WHILE_SYMBOL, INITIALISER_SERIES);
            } else if (p.is(DO_SYMBOL)) {
                reduce(a68, p, null, null, DO_PART, DO_SYMBOL, SERIAL_CLAUSE, OD_SYMBOL);
            } else if (p.is(ALT_DO_SYMBOL)) {
                reduce(a68, p, null, null, ALT_DO_PART, ALT_DO_SYMBOL, SERIAL_CLAUSE, OD_SYMBOL);
            }
        }
        p = q;
        if (p.sub != null) {
            if (p.is(OPEN_PART)) {
                reduce(a68, p, null, null, CONDITIONAL_CLAUSE, OPEN_PART, CHOICE, CHOICE, CLOSE_SYMBOL);
                reduce(a68, p, null, null, CONDITIONAL_CLAUSE, OPEN_PART, CHOICE, CLOSE_SYMBOL);
                reduce(a68, p, null, null, CONDITIONAL_CLAUSE, OPEN_PART, CHOICE, BRIEF_ELIF_PART);
                reduce(a68, p, null, null, CASE_CLAUSE, OPEN_PART, CASE_CHOICE_CLAUSE, CHOICE, CLOSE_SYMBOL);
                reduce(a68, p, null, null, CASE_CLAUSE, OPEN_PART, CASE_CHOICE_CLAUSE, CLOSE_SYMBOL);
                reduce(a68, p, null, null, CASE_CLAUSE, OPEN_PART, CASE_CHOICE_CLAUSE, BRIEF_OUSE_PART);
                reduce(a68, p, null, null,
                        CONFORMITY_CLAUSE, OPEN_PART, CONFORMITY_CHOICE, CHOICE, CLOSE_SYMBOL);
                reduce(a68, p, null, null,
                        CONFORMITY_CLAUSE, OPEN_PART, CONFORMITY_CHOICE, CLOSE_SYMBOL);
                reduce(a68, p, null, null,
                        CONFORMITY_CLAUSE, OPEN_PART, CONFORMITY_CHOICE, BRIEF_CONFORMITY_OUSE_PART);
            } else if (p.is(ELSE_OPEN_PART)) {
                reduce(a68, p, null, null, BRIEF_ELIF_PART, ELSE_OPEN_PART, CHOICE, CHOICE, CLOSE_SYMBOL);
                reduce(a68, p, null, null, BRIEF_ELIF_PART, ELSE_OPEN_PART, CHOICE, CLOSE_SYMBOL);
                reduce(a68, p, null, null, BRIEF_ELIF_PART, ELSE_OPEN_PART, CHOICE, BRIEF_ELIF_PART);
                reduce(a68, p, null, null,
                        BRIEF_OUSE_PART, ELSE_OPEN_PART, CASE_CHOICE_CLAUSE, CHOICE, CLOSE_SYMBOL);
                reduce(a68, p, null, null, BRIEF_OUSE_PART, ELSE_OPEN_PART, CASE_CHOICE_CLAUSE, CLOSE_SYMBOL);
                reduce(a68, p, null, null,
                        BRIEF_OUSE_PART, ELSE_OPEN_PART, CASE_CHOICE_CLAUSE, BRIEF_OUSE_PART);
                reduce(a68, p, null, null,
                        BRIEF_CONFORMITY_OUSE_PART, ELSE_OPEN_PART, CONFORMITY_CHOICE, CHOICE, CLOSE_SYMBOL);
                reduce(a68, p, null, null,
                        BRIEF_CONFORMITY_OUSE_PART, ELSE_OPEN_PART, CONFORMITY_CHOICE, CLOSE_SYMBOL);
                reduce(a68, p, null, null,
                        BRIEF_CONFORMITY_OUSE_PART, ELSE_OPEN_PART, CONFORMITY_CHOICE, BRIEF_CONFORMITY_OUSE_PART);
            } else if (p.is(IF_PART)) {
                reduce(a68, p, null, null, CONDITIONAL_CLAUSE, IF_PART, THEN_PART, ELSE_PART, FI_SYMBOL);
                reduce(a68, p, null, null, CONDITIONAL_CLAUSE, IF_PART, THEN_PART, ELIF_PART);
                reduce(a68, p, null, null, CONDITIONAL_CLAUSE, IF_PART, THEN_PART, FI_SYMBOL);
            } else if (p.is(ELIF_IF_PART)) {
                reduce(a68, p, null, null, ELIF_PART, ELIF_IF_PART, THEN_PART, ELSE_PART, FI_SYMBOL);
                reduce(a68, p, null, null, ELIF_PART, ELIF_IF_PART, THEN_PART, FI_SYMBOL);
                reduce(a68, p, null, null, ELIF_PART, ELIF_IF_PART, THEN_PART, ELIF_PART);
            } else if (p.is(CASE_PART)) {
                reduce(a68, p, null, null, CASE_CLAUSE, CASE_PART, CASE_IN_PART, OUT_PART, ESAC_SYMBOL);
                reduce(a68, p, null, null, CASE_CLAUSE, CASE_PART, CASE_IN_PART, ESAC_SYMBOL);
                reduce(a68, p, null, null, CASE_CLAUSE, CASE_PART, CASE_IN_PART, CASE_OUSE_PART);
                reduce(a68, p, null, null,
                        CONFORMITY_CLAUSE, CASE_PART, CONFORMITY_IN_PART, OUT_PART, ESAC_SYMBOL);
                reduce(a68, p, null, null,
                        CONFORMITY_CLAUSE, CASE_PART, CONFORMITY_IN_PART, ESAC_SYMBOL);
                reduce(a68, p, null, null,
                        CONFORMITY_CLAUSE, CASE_PART, CONFORMITY_IN_PART, CONFORMITY_OUSE_PART);
            } else if (p.is(OUSE_PART)) {
                reduce(a68, p, null, null, CASE_OUSE_PART, OUSE_PART, CASE_IN_PART, OUT_PART, ESAC_SYMBOL);
                reduce(a68, p, null, null, CASE_OUSE_PART, OUSE_PART, CASE_IN_PART, ESAC_SYMBOL);
                reduce(a68, p, null, null, CASE_OUSE_PART, OUSE_PART, CASE_IN_PART, CASE_OUSE_PART);
                reduce(a68, p, null, null,
                        CONFORMITY_OUSE_PART, OUSE_PART, CONFORMITY_IN_PART, OUT_PART, ESAC_SYMBOL);
                reduce(a68, p, null, null,
                        CONFORMITY_OUSE_PART, OUSE_PART, CONFORMITY_IN_PART, ESAC_SYMBOL);
                reduce(a68, p, null, null,
                        CONFORMITY_OUSE_PART, OUSE_PART, CONFORMITY_IN_PART, CONFORMITY_OUSE_PART);
            } else if (p.is(FOR_PART)) {
                reduce(a68, p, null, null,
                        LOOP_CLAUSE, FOR_PART, FROM_PART, BY_PART, TO_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, FROM_PART, BY_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, FROM_PART, TO_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, FROM_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, BY_PART, TO_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, BY_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, TO_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, FROM_PART, BY_PART, TO_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, FROM_PART, BY_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, FROM_PART, TO_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, FROM_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, BY_PART, TO_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, BY_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, TO_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FOR_PART, ALT_DO_PART);
            } else if (p.is(FROM_PART)) {
                reduce(a68, p, null, null, LOOP_CLAUSE, FROM_PART, BY_PART, TO_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FROM_PART, BY_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FROM_PART, TO_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FROM_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FROM_PART, BY_PART, TO_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FROM_PART, BY_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FROM_PART, TO_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, FROM_PART, ALT_DO_PART);
            } else if (p.is(BY_PART)) {
                reduce(a68, p, null, null, LOOP_CLAUSE, BY_PART, TO_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, BY_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, BY_PART, TO_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, BY_PART, ALT_DO_PART);
            } else if (p.is(TO_PART)) {
                reduce(a68, p, null, null, LOOP_CLAUSE, TO_PART, WHILE_PART, ALT_DO_PART);
                reduce(a68, p, null, null, LOOP_CLAUSE, TO_PART, ALT_DO_PART);
            } else if (p.is(WHILE_PART)) {
                reduce(a68, p, null, null, LOOP_CLAUSE, WHILE_PART, ALT_DO_PART);
            } else if (p.is(DO_PART)) {
                reduce(a68, p, null, null, LOOP_CLAUSE, DO_PART);
            }
        }
    }

    /** recover_from_error: substitute reduction when a phrase could not be parsed. */
    private static void recoverFromError(A68 a68, Node p, Attribute expect, boolean suppress) {
        // This routine does not do fancy things as that might introduce more
        // errors.
        Node q = p;
        if (p == null) {
            return;
        }

        if (expect == SOME_CLAUSE) {
            expect = serialOrCollateral(a68, p);
        }

        if (!suppress) {
            // Give an error message.
            Ref.Box<Node> w = Ref.box(p);
            String seq = TopDown.phraseToText(a68, p, w);
            if (seq.length() == 0) {
                if (a68.job.errorCount == 0) {
                    a68.diagnostics.error(w.v, "expected %e", Fmt.attr(expect));
                }
            } else {
                String fmt = seq;
                fmt += " is an invalid %e";
                a68.diagnostics.error(w.v, fmt, Fmt.attr(expect));
            }

            if (a68.job.errorCount >= MAX_ERRORS) {
                throw new CrashExit();
            }
        }

        // Try to prevent spurious diagnostics by guessing what was expected.
        while (q.next != null) {
            q = q.next;
        }

        if (Parser.isOneOf(a68, p, BEGIN_SYMBOL, OPEN_SYMBOL)) {
            if (expect == ARGUMENT || expect == COLLATERAL_CLAUSE
                    || expect == PARAMETER_PACK || expect == STRUCTURE_PACK
                    || expect == UNION_PACK) {
                Parser.makeSub(a68, p, q, expect);
            } else if (expect == ENQUIRY_CLAUSE) {
                Parser.makeSub(a68, p, q, OPEN_PART);
            } else if (expect == FORMAL_DECLARERS) {
                Parser.makeSub(a68, p, q, FORMAL_DECLARERS);
            } else {
                Parser.makeSub(a68, p, q, CLOSED_CLAUSE);
            }
        } else if (p.is(FORMAT_DELIMITER_SYMBOL) && expect == FORMAT_TEXT) {
            Parser.makeSub(a68, p, q, FORMAT_TEXT);
        } else if (Parser.isOneOf(a68, p, THEN_BAR_SYMBOL, CHOICE)) {
            Parser.makeSub(a68, p, q, CHOICE);
        } else if (Parser.isOneOf(a68, p, IF_SYMBOL, IF_PART)) {
            Parser.makeSub(a68, p, q, IF_PART);
        } else if (Parser.isOneOf(a68, p, THEN_SYMBOL, THEN_PART)) {
            Parser.makeSub(a68, p, q, THEN_PART);
        } else if (Parser.isOneOf(a68, p, ELSE_SYMBOL, ELSE_PART)) {
            Parser.makeSub(a68, p, q, ELSE_PART);
        } else if (Parser.isOneOf(a68, p, ELIF_SYMBOL, ELIF_IF_PART)) {
            Parser.makeSub(a68, p, q, ELIF_IF_PART);
        } else if (Parser.isOneOf(a68, p, CASE_SYMBOL, CASE_PART)) {
            Parser.makeSub(a68, p, q, CASE_PART);
        } else if (Parser.isOneOf(a68, p, OUT_SYMBOL, OUT_PART)) {
            Parser.makeSub(a68, p, q, OUT_PART);
        } else if (Parser.isOneOf(a68, p, OUSE_SYMBOL, OUSE_PART)) {
            Parser.makeSub(a68, p, q, OUSE_PART);
        } else if (Parser.isOneOf(a68, p, FOR_SYMBOL, FOR_PART)) {
            Parser.makeSub(a68, p, q, FOR_PART);
        } else if (Parser.isOneOf(a68, p, FROM_SYMBOL, FROM_PART)) {
            Parser.makeSub(a68, p, q, FROM_PART);
        } else if (Parser.isOneOf(a68, p, BY_SYMBOL, BY_PART)) {
            Parser.makeSub(a68, p, q, BY_PART);
        } else if (Parser.isOneOf(a68, p, TO_SYMBOL, TO_PART)) {
            Parser.makeSub(a68, p, q, TO_PART);
        } else if (Parser.isOneOf(a68, p, WHILE_SYMBOL, WHILE_PART)) {
            Parser.makeSub(a68, p, q, WHILE_PART);
        } else if (Parser.isOneOf(a68, p, DO_SYMBOL, DO_PART)) {
            Parser.makeSub(a68, p, q, DO_PART);
        } else if (Parser.isOneOf(a68, p, ALT_DO_SYMBOL, ALT_DO_PART)) {
            Parser.makeSub(a68, p, q, ALT_DO_PART);
        } else if (Parser.attributeName(expect) != null) {
            Parser.makeSub(a68, p, q, expect);
        }
    }

    /** reduce_erroneous_units: heuristic aid in pinpointing errors. */
    private static void reduceErroneousUnits(A68 a68, Node p) {
        // Constructs are reduced to units in an attempt to limit spurious
        // diagnostics.
        for (Node q = p; q != null; q = q.next) {
            // Some implementations allow selection from a tertiary, when there
            // is no risk of ambiguity. GCC follows RR, so some extra attention
            // here to guide an unsuspecting user.
            if (Parser.whether(a68, q, SELECTOR, Parser.not(SECONDARY))) {
                a68.diagnostics.error(q.next, "expected %e", Fmt.attr(SECONDARY));
                reduce(a68, q, null, null, UNIT, SELECTOR, WILDCARD);
            }

            // Attention for identity relations that require tertiaries.
            if (Parser.whether(a68, q, Parser.not(TERTIARY), IS_SYMBOL, TERTIARY)
                    || Parser.whether(a68, q, TERTIARY, IS_SYMBOL, Parser.not(TERTIARY))
                    || Parser.whether(a68, q, Parser.not(TERTIARY), IS_SYMBOL, Parser.not(TERTIARY))) {
                a68.diagnostics.error(q.next, "expected %e", Fmt.attr(TERTIARY));
                reduce(a68, q, null, null, UNIT, WILDCARD, IS_SYMBOL, WILDCARD);
            } else if (Parser.whether(a68, q, Parser.not(TERTIARY), ISNT_SYMBOL, TERTIARY)
                    || Parser.whether(a68, q, TERTIARY, ISNT_SYMBOL, Parser.not(TERTIARY))
                    || Parser.whether(a68, q, Parser.not(TERTIARY), ISNT_SYMBOL, Parser.not(TERTIARY))) {
                a68.diagnostics.error(q.next, "expected %e", Fmt.attr(TERTIARY));
                reduce(a68, q, null, null, UNIT, WILDCARD, ISNT_SYMBOL, WILDCARD);
            }
        }
    }

    // ------------------------------------------------------------------
    // A posteriori checks of the syntax tree built by the BU parser.
    // ------------------------------------------------------------------

    /** a68_bottom_up_error_check: driver for a posteriori error checking. */
    public static void bottomUpErrorCheck(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(BOOLEAN_PATTERN)) {
                int[] k = {0};
                Parser.countPictures(a68, p.sub, k);
                if (!(k[0] == 0 || k[0] == 2)) {
                    a68.diagnostics.error(p, "incorrect number of pictures for %e", Fmt.attr(p.attribute));
                }
            } else if (Parser.isOneOf(a68, p,
                    DEFINING_INDICANT, DEFINING_IDENTIFIER, DEFINING_OPERATOR)) {
                if (p.publicized && !p.symbolTable.publicRange) {
                    a68.diagnostics.error(p,
                            "%<pub%>licized declaration not in a module definition");
                }
            } else {
                bottomUpErrorCheck(a68, p.sub);
            }
        }
    }

    // ------------------------------------------------------------------
    // Next part rearranges and checks the tree after the symbol tables are
    // finished.
    // ------------------------------------------------------------------

    /** a68_rearrange_goto_less_jumps: transfer IDENTIFIER to JUMP where appropriate. */
    public static void rearrangeGotoLessJumps(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(UNIT)) {
                Node q = p.sub;
                if (q.is(TERTIARY)) {
                    Node tertiary = q;
                    q = q.sub;
                    if (q != null && q.is(SECONDARY)) {
                        q = q.sub;
                        if (q != null && q.is(PRIMARY)) {
                            q = q.sub;
                            if (q != null && q.is(IDENTIFIER)) {
                                if (Taxes.isIdentifierOrLabelGlobal(a68, q.symbolTable, q.info.symbol) == LABEL) {
                                    tertiary.attribute = JUMP;
                                    tertiary.sub = q;
                                }
                            }
                        }
                    }
                }
            } else if (p.is(TERTIARY)) {
                Node q = p.sub;
                if (q != null && q.is(SECONDARY)) {
                    Node secondary = q;
                    q = q.sub;
                    if (q != null && q.is(PRIMARY)) {
                        q = q.sub;
                        if (q != null && q.is(IDENTIFIER)) {
                            if (Taxes.isIdentifierOrLabelGlobal(a68, q.symbolTable, q.info.symbol) == LABEL) {
                                secondary.attribute = JUMP;
                                secondary.sub = q;
                            }
                        }
                    }
                }
            } else if (p.is(SECONDARY)) {
                Node q = p.sub;
                if (q != null && q.is(PRIMARY)) {
                    Node primary = q;
                    q = q.sub;
                    if (q != null && q.is(IDENTIFIER)) {
                        if (Taxes.isIdentifierOrLabelGlobal(a68, q.symbolTable, q.info.symbol) == LABEL) {
                            primary.attribute = JUMP;
                            primary.sub = q;
                        }
                    }
                }
            } else if (p.is(PRIMARY)) {
                Node q = p.sub;
                if (q != null && q.is(IDENTIFIER)) {
                    if (Taxes.isIdentifierOrLabelGlobal(a68, q.symbolTable, q.info.symbol) == LABEL) {
                        Parser.makeSub(a68, q, q, JUMP);
                    }
                }
            }
            rearrangeGotoLessJumps(a68, p.sub);
        }
    }
}
