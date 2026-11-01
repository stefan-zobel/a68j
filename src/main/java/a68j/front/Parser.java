// Java port of ga68's a68-parser.cc: parser driver and utilities.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Mailloux-type parser driver and parser utilities (a68-parser.cc).
 * See the long comment at the top of a68-parser.cc for an overview of the
 * parser phases.
 */
public final class Parser {
    private Parser() {}

    /** a68.h */
    public static final int MOID_ERROR_WIDTH = 80;
    public static final int PRIMAL_SCOPE = 0;

    // ------------------------------------------------------------------
    // Assertions.
    // ------------------------------------------------------------------

    /** gcc_assert */
    public static void gccAssert(boolean cond) {
        if (!cond) {
            throw new IllegalStateException("gcc_assert failed");
        }
    }

    /** gcc_unreachable */
    public static IllegalStateException gccUnreachable() {
        return new IllegalStateException("gcc_unreachable");
    }

    // ------------------------------------------------------------------
    // Classifiers.
    // ------------------------------------------------------------------

    /** a68_is_ref_refety_flex */
    public static boolean isRefRefetyFlex(A68 a68, Moid m) {
        if (m.attribute == REF_SYMBOL && m.sub.attribute == FLEX_SYMBOL) {
            return true;
        } else if (m.attribute == REF_SYMBOL) {
            return isRefRefetyFlex(a68, m.sub);
        } else {
            return false;
        }
    }

    /** a68_count_operands: number of operands in an operator parameter list. */
    public static int countOperands(A68 a68, Node p) {
        if (p != null) {
            if (p.is(DECLARER)) {
                return countOperands(a68, p.next);
            } else if (p.is(COMMA_SYMBOL)) {
                return 1 + countOperands(a68, p.next);
            } else {
                return countOperands(a68, p.next) + countOperands(a68, p.sub);
            }
        }
        return 0;
    }

    /** a68_count_formal_bounds */
    public static int countFormalBounds(A68 a68, Node p) {
        if (p == null) {
            return 0;
        } else if (p.is(COMMA_SYMBOL)) {
            return 1;
        } else {
            return countFormalBounds(a68, p.next) + countFormalBounds(a68, p.sub);
        }
    }

    /** a68_count_pictures */
    public static void countPictures(A68 a68, Node p, int[] k) {
        for (; p != null; p = p.next) {
            if (p.is(PICTURE)) {
                k[0]++;
            }
            countPictures(a68, p.sub, k);
        }
    }

    /** a68_is_semicolon_less: whether token cannot follow semicolon or EXIT. */
    public static boolean isSemicolonLess(A68 a68, Node p) {
        return switch (p.attribute) {
            case BUS_SYMBOL, CLOSE_SYMBOL, END_SYMBOL, SEMI_SYMBOL, EXIT_SYMBOL, THEN_BAR_SYMBOL,
                    ELSE_BAR_SYMBOL, THEN_SYMBOL, ELIF_SYMBOL, ELSE_SYMBOL, FI_SYMBOL, IN_SYMBOL,
                    OUT_SYMBOL, OUSE_SYMBOL, ESAC_SYMBOL, OD_SYMBOL, FED_SYMBOL, POSTLUDE_SYMBOL -> true;
            default -> false;
        };
    }

    /** a68_is_formal_bounds */
    public static boolean isFormalBounds(A68 a68, Node p) {
        if (p == null) {
            return true;
        }
        return switch (p.attribute) {
            case OPEN_SYMBOL, CLOSE_SYMBOL, SUB_SYMBOL, BUS_SYMBOL, COMMA_SYMBOL, COLON_SYMBOL,
                    INT_DENOTATION, IDENTIFIER, OPERATOR ->
                    isFormalBounds(a68, p.sub) && isFormalBounds(a68, p.next);
            default -> false;
        };
    }

    /** a68_is_unit_terminator */
    public static boolean isUnitTerminator(A68 a68, Node p) {
        return switch (p.attribute) {
            case BUS_SYMBOL, CLOSE_SYMBOL, END_SYMBOL, SEMI_SYMBOL, EXIT_SYMBOL, COMMA_SYMBOL,
                    THEN_BAR_SYMBOL, ELSE_BAR_SYMBOL, THEN_SYMBOL, ELIF_SYMBOL, ELSE_SYMBOL, FI_SYMBOL,
                    IN_SYMBOL, OUT_SYMBOL, OUSE_SYMBOL, ESAC_SYMBOL, FED_SYMBOL, POSTLUDE_SYMBOL -> true;
            default -> false;
        };
    }

    /** a68_is_loop_keyword */
    public static boolean isLoopKeyword(A68 a68, Node p) {
        return switch (p.attribute) {
            case FOR_SYMBOL, FROM_SYMBOL, BY_SYMBOL, TO_SYMBOL, WHILE_SYMBOL, DO_SYMBOL -> true;
            default -> false;
        };
    }

    /** a68_is_declaration */
    public static boolean isDeclaration(A68 a68, Node p) {
        return switch (p.attribute) {
            case IDENTITY_DECLARATION, VARIABLE_DECLARATION, PROCEDURE_DECLARATION,
                    PROCEDURE_VARIABLE_DECLARATION, PRIORITY_DECLARATION, BRIEF_OPERATOR_DECLARATION,
                    OPERATOR_DECLARATION, MODE_DECLARATION -> true;
            default -> false;
        };
    }

    /** a68_yields_value */
    public static boolean yieldsValue(A68 a68, Node p) {
        return switch (p.attribute) {
            case ACCESS_CLAUSE, ASSERTION, UNIT, ROUTINE_TEXT, ASSIGNATION, TERTIARY, MONADIC_FORMULA,
                    FORMULA, SECONDARY, SLICE, SELECTION, PRIMARY, GENERATOR, CALL, CAST, AND_FUNCTION,
                    OR_FUNCTION, FORMAL_HOLE, IDENTITY_RELATION, EMPTY_SYMBOL, NIHIL, SKIP,
                    PARALLEL_CLAUSE, SERIAL_CLAUSE, CLOSED_CLAUSE, ENCLOSED_CLAUSE, LOOP_CLAUSE,
                    CONDITIONAL_CLAUSE, CASE_CLAUSE, CONFORMITY_CLAUSE, COLLATERAL_CLAUSE, DENOTATION,
                    IDENTIFIER, DEREFERENCING, DEPROCEDURING, PROCEDURING, WIDENING, UNITING, ROWING,
                    VOIDING, JUMP -> true;
            default -> false;
        };
    }

    /** a68_get_good_attribute */
    public static Attribute getGoodAttribute(A68 a68, Node p) {
        return switch (p.attribute) {
            case UNIT, TERTIARY, SECONDARY, PRIMARY -> getGoodAttribute(a68, p.sub);
            default -> p.attribute;
        };
    }

    /** a68_dont_mark_here: preferably don't put intelligible diagnostic here. */
    public static boolean dontMarkHere(A68 a68, Node p) {
        return switch (p.attribute) {
            case ALT_DO_SYMBOL, ALT_EQUALS_SYMBOL, ANDF_SYMBOL, ASSERT_SYMBOL, ASSIGN_SYMBOL,
                    ASSIGN_TO_SYMBOL, AT_SYMBOL, BEGIN_SYMBOL, BITS_SYMBOL, BOLD_COMMENT_SYMBOL,
                    BOLD_PRAGMAT_SYMBOL, BOLD_COMMENT_BEGIN_SYMBOL, BOLD_COMMENT_END_SYMBOL, BOOL_SYMBOL,
                    BUS_SYMBOL, BY_SYMBOL, BYTES_SYMBOL, CASE_SYMBOL, CHANNEL_SYMBOL, CHAR_SYMBOL,
                    CLOSE_SYMBOL, COLON_SYMBOL, COMMA_SYMBOL, COMPL_SYMBOL, DO_SYMBOL, ELIF_SYMBOL,
                    ELSE_BAR_SYMBOL, ELSE_SYMBOL, EMPTY_SYMBOL, END_SYMBOL, EQUALS_SYMBOL, ESAC_SYMBOL,
                    EXIT_SYMBOL, FALSE_SYMBOL, FILE_SYMBOL, FI_SYMBOL, FLEX_SYMBOL, FOR_SYMBOL,
                    FROM_SYMBOL, GO_SYMBOL, GOTO_SYMBOL, HEAP_SYMBOL, IF_SYMBOL, IN_SYMBOL, INT_SYMBOL,
                    ISNT_SYMBOL, IS_SYMBOL, LOC_SYMBOL, LONG_SYMBOL, MAIN_SYMBOL, MODE_SYMBOL,
                    NIL_SYMBOL, OD_SYMBOL, OF_SYMBOL, QUOTE_SYMBOL, OPEN_SYMBOL, OP_SYMBOL, ORF_SYMBOL,
                    OUSE_SYMBOL, OUT_SYMBOL, PAR_SYMBOL, POINT_SYMBOL, PRIO_SYMBOL, PROC_SYMBOL,
                    REAL_SYMBOL, REF_SYMBOL, ROWS_SYMBOL, ROW_SYMBOL, SEMA_SYMBOL, SEMI_SYMBOL,
                    SHORT_SYMBOL, SKIP_SYMBOL, STRING_SYMBOL, STRUCT_SYMBOL, STYLE_I_COMMENT_SYMBOL,
                    STYLE_II_COMMENT_SYMBOL, STYLE_I_PRAGMAT_SYMBOL, SUB_SYMBOL, THEN_BAR_SYMBOL,
                    THEN_SYMBOL, TO_SYMBOL, TRUE_SYMBOL, UNION_SYMBOL, VOID_SYMBOL, WHILE_SYMBOL,
                    SERIAL_CLAUSE, ENQUIRY_CLAUSE, INITIALISER_SERIES, DECLARATION_LIST, DEF_SYMBOL,
                    FED_SYMBOL, POSTLUDE_SYMBOL, ACCESS_SYMBOL -> true;
            default -> false;
        };
    }

    /** renumber_nodes: renumber nodes in subtree P, starting with n[0]. */
    public static void renumberNodes(A68 a68, Node p, int[] n) {
        for (; p != null; p = p.next) {
            p.number = n[0]++;
            renumberNodes(a68, p.sub, n);
        }
    }

    // ------------------------------------------------------------------
    // Constructors.
    // ------------------------------------------------------------------

    /** a68_new_node_info */
    public static NodeInfo newNodeInfo(A68 a68) {
        a68.newNodeInfos++;
        return new NodeInfo();
    }

    /** a68_new_genie_info */
    public static GInfo newGenieInfo(A68 a68) {
        a68.newGenieInfos++;
        return new GInfo();
    }

    /** a68_new_node: a new parse tree node with proper defaults. */
    public static Node newNode(A68 a68) {
        a68.newNodes++;
        Node z = new Node();
        z.access = Access.ACCESS_DIR;
        return z;
    }

    /** a68_some_node */
    public static Node someNode(A68 a68, String t) {
        Node z = newNode(a68);
        z.info = newNodeInfo(a68);
        z.genie = newGenieInfo(a68);
        z.info.symbol = t;
        return z;
    }

    /** a68_new_symbol_table */
    public static Table newSymbolTable(A68 a68, Table p) {
        Table z = new Table();
        z.num = a68.symbolTableCount;
        z.level = a68.symbolTableCount++;
        z.nest = a68.symbolTableCount;
        z.attribute = STOP;
        z.initialiseFrame = true;
        z.procOps = true;
        z.initialiseAnon = true;
        z.previous = p;
        z.outer = null;
        z.publicRange = false;
        return z;
    }

    /** a68_new_moid */
    public static Moid newMoid(A68 a68) {
        a68.newModes++;
        Moid z = new Moid();
        z.attribute = STOP;
        z.number = 0;
        z.dim = 0;
        z.use = false;
        z.hasRows = false;
        z.hasRefs = false;
        z.portable = true;
        z.derivate = false;
        return z;
    }

    /** a68_new_pack */
    public static Pack newPack(A68 a68) {
        return new Pack();
    }

    /** a68_new_tag */
    public static Tag newTag(A68 a68) {
        Tag z = new Tag();
        z.status = Status.NULL_MASK;
        z.scope = PRIMAL_SCOPE;
        z.scopeAssigned = false;
        z.priority = 0;
        z.use = false;
        z.inProc = false;
        z.nestProc = false;
        z.heap = STOP;
        z.youngestEnviron = PRIMAL_SCOPE;
        z.locAssigned = false;
        z.portable = true;
        z.variable = false;
        z.isRecursive = false;
        z.publicized = false;
        z.ascribedRoutineText = false;
        z.access = Access.ACCESS_DIR;
        z.number = ++a68.parserState.tagNumber;
        return z;
    }

    /** make_special_mode: make a special, internal mode with number M. */
    public static Moid makeSpecialMode(A68 a68, int m) {
        Moid n = newMoid(a68);
        n.attribute = STOP;
        n.number = m;
        return n;
    }

    // ------------------------------------------------------------------
    // Matching and tree surgery.
    // ------------------------------------------------------------------

    /** A negated attribute in a {@link #whether} pattern: C's {@code -ATTR}. */
    public record Not(Attribute attribute) {}

    /** {@code -a} in an a68_whether argument list. */
    public static Not not(Attribute a) {
        return new Not(a);
    }

    /**
     * a68_whether: whether attributes match in subsequent nodes. Pattern
     * elements are {@link Attribute}s (WILDCARD and KEYWORD have their special
     * meaning) or {@link #not} for a negated attribute. The terminating STOP of
     * the C version is omitted.
     */
    public static boolean whether(A68 a68, Node p, Object... pattern) {
        for (Object o : pattern) {
            if (o instanceof Not n) {
                if (p != null && n.attribute() != p.attribute) {
                    p = p.next;
                } else {
                    return false;
                }
                continue;
            }
            Attribute a = (Attribute) o;
            if (p != null && a == WILDCARD) {
                p = p.next;
            } else if (p != null && a == KEYWORD) {
                if (Keywords.findKeywordFromAttribute(a68.topKeyword, p.attribute) != null) {
                    p = p.next;
                } else {
                    return false;
                }
            } else if (p != null && a == p.attribute) {
                p = p.next;
            } else {
                return false;
            }
        }
        return true;
    }

    /** a68_is_one_of: whether one of a series of attributes matches a node (STOP omitted). */
    public static boolean isOneOf(A68 a68, Node p, Attribute... as) {
        if (p != null) {
            boolean match = false;
            for (Attribute a : as) {
                match = match | p.is(a);
            }
            return match;
        }
        return false;
    }

    /**
     * a68_make_sub: isolate nodes p-q making p a branch to p-q.
     *
     * <pre>
     * From x - p - a - b - c - q - y
     * To   x - t - y
     *          |
     *          p - a - b - c - q
     * </pre>
     */
    public static void makeSub(A68 a68, Node p, Node q, Attribute t) {
        Node z = newNode(a68);
        gccAssert(p != null && q != null);
        z.copyFrom(p);
        if (p.genie != null) {
            z.genie = newGenieInfo(a68);
        }
        z.previous = null;
        if (p == q) {
            z.next = null;
        } else {
            if (p.next != null) {
                p.next.previous = z;
            }
            p.next = q.next;
            if (p.next != null) {
                p.next.previous = p;
            }
            q.next = null;
        }
        p.sub = z;
        p.attribute = t;
    }

    /** find_level: find symbol table at level I. */
    static Table findLevel(A68 a68, Node n, int i) {
        if (n == null) {
            return null;
        }
        Table s = n.symbolTable;
        if (s != null && s.level == i) {
            return s;
        } else if ((s = findLevel(a68, n.sub, i)) != null) {
            return s;
        } else if ((s = findLevel(a68, n.next, i)) != null) {
            return s;
        } else {
            return null;
        }
    }

    /** a68_is_new_lexical_level: whether P is top of lexical level. */
    public static boolean isNewLexicalLevel(A68 a68, Node p) {
        switch (p.attribute) {
            case ALT_DO_PART, BRIEF_ELIF_PART, BRIEF_OUSE_PART, BRIEF_CONFORMITY_OUSE_PART, CHOICE,
                    CLOSED_CLAUSE, CONDITIONAL_CLAUSE, DO_PART, ELIF_PART, ELSE_PART, CASE_CLAUSE,
                    CASE_CHOICE_CLAUSE, CASE_IN_PART, CASE_OUSE_PART, OUT_PART, ROUTINE_TEXT,
                    SPECIFIED_UNIT, THEN_PART, CONFORMITY_CLAUSE, CONFORMITY_CHOICE, CONFORMITY_IN_PART,
                    CONFORMITY_OUSE_PART, WHILE_PART, DEF_PART, POSTLUDE_PART, ACCESS_CLAUSE -> {
                return true;
            }
            case MODULE_TEXT -> {
                // Module texts introduce an additional lexical level
                // encompassing all its parts only if endowed with revelations.
                return p.sub != null && p.sub.is(REVELATION_PART);
            }
            default -> {
                return false;
            }
        }
    }

    // ------------------------------------------------------------------
    // Strings and labels.
    // ------------------------------------------------------------------

    /** a68_new_string: concatenation of the arguments; null if the first is null. */
    public static String newString(String... parts) {
        if (parts.length == 0 || parts[0] == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            if (s == null) {
                break;
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /** tie_label_to_serial: tie label to the clause it is defined in. */
    static void tieLabelToSerial(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(SERIAL_CLAUSE)) {
                boolean validFollow;
                if (p.next == null) {
                    validFollow = true;
                } else if (p.next.is(CLOSE_SYMBOL)) {
                    validFollow = true;
                } else if (p.next.is(END_SYMBOL)) {
                    validFollow = true;
                } else if (p.next.is(OD_SYMBOL)) {
                    validFollow = true;
                } else {
                    validFollow = false;
                }
                if (validFollow) {
                    p.sub.symbolTable.jumpTo = null;
                }
            }
            tieLabelToSerial(a68, p.sub);
        }
    }

    /** tie_label */
    static void tieLabel(A68 a68, Node p, Node unit) {
        for (; p != null; p = p.next) {
            if (p.is(DEFINING_IDENTIFIER)) {
                p.tag.unit = unit;
            }
            tieLabel(a68, p.sub, unit);
        }
    }

    /** tie_label_to_unit */
    static void tieLabelToUnit(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(LABELED_UNIT)) {
                tieLabel(a68, p.sub.sub, p.sub.next);
            }
            tieLabelToUnit(a68, p.sub);
        }
    }

    /** a68_attribute_name */
    public static String attributeName(Attribute attr) {
        return attr.description;
    }
}
