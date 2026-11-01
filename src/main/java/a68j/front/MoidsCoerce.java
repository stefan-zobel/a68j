// Java port of ga68's a68-parser-moids-coerce.cc: mode coercion driver.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Mode coercion driver (a68-parser-moids-coerce.cc): inserts the coercions
 * determined by the mode checker into the syntax tree.
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>{@code SOID_T} locals on the C stack are fresh {@link Soid} objects.</li>
 * <li>{@code PACK_T **} parameters are {@link Ref}{@code <Pack>}.</li>
 * <li>The C forward declarations of coerce_unit, coerce_formula,
 * coerce_operand and coerce_enclosed are not needed in Java.</li>
 * </ul>
 */
public final class MoidsCoerce {
    private MoidsCoerce() {}

    /** A68_INSERT_COERCIONS (n, p, q): a68_make_strong ((n), (p), MOID (q)). */
    private static void insertCoercions(A68 a68, Node n, Moid p, Soid q) {
        MoidsMisc.makeStrong(a68, n, p, q.type);
    }

    /** Coerce bounds (coerce_bounds). */
    private static void coerceBounds(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(UNIT)) {
                Soid q = new Soid();
                MoidsMisc.makeSoid(a68, q, MEEK, a68.modes.INT, STOP);
                coerceUnit(a68, p, q);
            } else {
                coerceBounds(a68, p.sub);
            }
        }
    }

    /** Coerce declarer (coerce_declarer). */
    private static void coerceDeclarer(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(BOUNDS)) {
                coerceBounds(a68, p.sub);
            } else {
                coerceDeclarer(a68, p.sub);
            }
        }
    }

    /** Coerce identity declaration (coerce_identity_declaration). */
    private static void coerceIdentityDeclaration(A68 a68, Node p) {
        if (p != null) {
            switch (p.attribute) {
                case DECLARER:
                    coerceDeclarer(a68, p.sub);
                    coerceIdentityDeclaration(a68, p.next);
                    break;
                case DEFINING_IDENTIFIER: {
                    Soid q = new Soid();
                    MoidsMisc.makeSoid(a68, q, STRONG, p.type, STOP);
                    coerceUnit(a68, p.next.next, q);
                    break;
                }
                default:
                    coerceIdentityDeclaration(a68, p.sub);
                    coerceIdentityDeclaration(a68, p.next);
                    break;
            }
        }
    }

    /** Coerce variable declaration (coerce_variable_declaration). */
    @SuppressWarnings("fallthrough")
    private static void coerceVariableDeclaration(A68 a68, Node p) {
        if (p != null) {
            switch (p.attribute) {
                case DECLARER:
                    coerceDeclarer(a68, p.sub);
                    coerceVariableDeclaration(a68, p.next);
                    break;
                case DEFINING_IDENTIFIER:
                    if (Parser.whether(a68, p, DEFINING_IDENTIFIER, ASSIGN_SYMBOL, UNIT)) {
                        Soid q = new Soid();
                        MoidsMisc.makeSoid(a68, q, STRONG, p.type.sub, STOP);
                        coerceUnit(a68, p.next.next, q);
                        break;
                    }
                    // Fallthrough.
                default:
                    coerceVariableDeclaration(a68, p.sub);
                    coerceVariableDeclaration(a68, p.next);
                    break;
            }
        }
    }

    /** Coerce routine text (coerce_routine_text). */
    private static void coerceRoutineText(A68 a68, Node p) {
        if (p.is(PARAMETER_PACK)) {
            p = p.next;
        }
        Soid w = new Soid();
        MoidsMisc.makeSoid(a68, w, STRONG, p.type, STOP);
        coerceUnit(a68, p.next.next, w);
    }

    /** Coerce proc declaration (coerce_proc_declaration). */
    private static void coerceProcDeclaration(A68 a68, Node p) {
        if (p == null) {
            return;
        } else if (p.is(ROUTINE_TEXT)) {
            coerceRoutineText(a68, p.sub);
        } else {
            coerceProcDeclaration(a68, p.sub);
            coerceProcDeclaration(a68, p.next);
        }
    }

    /** Coerce_op_declaration (coerce_op_declaration). */
    private static void coerceOpDeclaration(A68 a68, Node p) {
        if (p == null) {
            return;
        } else if (p.is(DEFINING_OPERATOR)) {
            Soid q = new Soid();
            MoidsMisc.makeSoid(a68, q, STRONG, p.type, STOP);
            coerceUnit(a68, p.next.next, q);
        } else {
            coerceOpDeclaration(a68, p.sub);
            coerceOpDeclaration(a68, p.next);
        }
    }

    /** Coerce brief op declaration (coerce_brief_op_declaration). */
    private static void coerceBriefOpDeclaration(A68 a68, Node p) {
        if (p == null) {
            return;
        } else if (p.is(DEFINING_OPERATOR)) {
            coerceRoutineText(a68, p.next.next.sub);
        } else {
            coerceBriefOpDeclaration(a68, p.sub);
            coerceBriefOpDeclaration(a68, p.next);
        }
    }

    /** Coerce declaration list (coerce_declaration_list). */
    private static void coerceDeclarationList(A68 a68, Node p) {
        if (p != null) {
            switch (p.attribute) {
                case IDENTITY_DECLARATION:
                    coerceIdentityDeclaration(a68, p.sub);
                    break;
                case VARIABLE_DECLARATION:
                    coerceVariableDeclaration(a68, p.sub);
                    break;
                case MODE_DECLARATION:
                    coerceDeclarer(a68, p.sub);
                    break;
                case PROCEDURE_DECLARATION:
                case PROCEDURE_VARIABLE_DECLARATION:
                    coerceProcDeclaration(a68, p.sub);
                    break;
                case BRIEF_OPERATOR_DECLARATION:
                    coerceBriefOpDeclaration(a68, p.sub);
                    break;
                case OPERATOR_DECLARATION:
                    coerceOpDeclaration(a68, p.sub);
                    break;
                default:
                    coerceDeclarationList(a68, p.sub);
                    coerceDeclarationList(a68, p.next);
                    break;
            }
        }
    }

    /** Coerce serial (coerce_serial). */
    private static void coerceSerial(A68 a68, Node p, Soid q, boolean k) {
        if (p == null) {
            return;
        } else if (p.is(INITIALISER_SERIES)) {
            coerceSerial(a68, p.sub, q, false);
            coerceSerial(a68, p.next, q, k);
        } else if (p.is(DECLARATION_LIST)) {
            coerceDeclarationList(a68, p.sub);
        } else if (Parser.isOneOf(a68, p, LABEL, SEMI_SYMBOL, EXIT_SYMBOL)) {
            coerceSerial(a68, p.next, q, k);
        } else if (Parser.isOneOf(a68, p, SERIAL_CLAUSE, ENQUIRY_CLAUSE)) {
            Node z = p.next;
            if (z != null) {
                if (z.is(EXIT_SYMBOL) || z.is(END_SYMBOL) || z.is(CLOSE_SYMBOL)) {
                    coerceSerial(a68, p.sub, q, true);
                } else {
                    coerceSerial(a68, p.sub, q, false);
                }
            } else {
                coerceSerial(a68, p.sub, q, true);
            }
            coerceSerial(a68, p.next, q, k);
        } else if (p.is(LABELED_UNIT)) {
            coerceSerial(a68, p.sub, q, k);
        } else if (p.is(UNIT)) {
            if (k) {
                coerceUnit(a68, p, q);
            } else {
                Soid strongvoid = new Soid();
                MoidsMisc.makeSoid(a68, strongvoid, STRONG, a68.modes.VOID, STOP);
                coerceUnit(a68, p, strongvoid);
            }
        }
    }

    /** Coerce closed (coerce_closed). */
    private static void coerceClosed(A68 a68, Node p, Soid q) {
        if (p.is(SERIAL_CLAUSE)) {
            coerceSerial(a68, p, q, true);
        } else if (Parser.isOneOf(a68, p, OPEN_SYMBOL, BEGIN_SYMBOL)) {
            coerceClosed(a68, p.next, q);
        }
    }

    /** Coerce access clause (coerce_access). */
    private static void coerceAccess(A68 a68, Node p, Soid q) {
        for (; p != null; p = p.next) {
            if (p.is(ENCLOSED_CLAUSE)) {
                coerceEnclosed(a68, p, q);
            }
        }
    }

    /** Coerce conditional (coerce_conditional). */
    private static void coerceConditional(A68 a68, Node p, Soid q) {
        Soid w = new Soid();
        MoidsMisc.makeSoid(a68, w, MEEK, a68.modes.BOOL, STOP);
        coerceSerial(a68, p.sub.next, w, true);
        p = p.next;
        coerceSerial(a68, p.sub.next, q, true);
        if ((p = p.next) != null) {
            if (Parser.isOneOf(a68, p, ELSE_PART, CHOICE)) {
                coerceSerial(a68, p.sub.next, q, true);
            } else if (Parser.isOneOf(a68, p, ELIF_PART, BRIEF_ELIF_PART)) {
                coerceConditional(a68, p.sub, q);
            }
        }
    }

    /** Coerce unit list (coerce_unit_list). */
    private static void coerceUnitList(A68 a68, Node p, Soid q) {
        if (p == null) {
            return;
        } else if (p.is(UNIT_LIST)) {
            coerceUnitList(a68, p.sub, q);
            coerceUnitList(a68, p.next, q);
        } else if (Parser.isOneOf(a68, p, OPEN_SYMBOL, BEGIN_SYMBOL, COMMA_SYMBOL)) {
            coerceUnitList(a68, p.next, q);
        } else if (p.is(UNIT)) {
            coerceUnit(a68, p, q);
            coerceUnitList(a68, p.next, q);
        }
    }

    /** Coerce int case (coerce_int_case). */
    private static void coerceIntCase(A68 a68, Node p, Soid q) {
        Soid w = new Soid();
        MoidsMisc.makeSoid(a68, w, MEEK, a68.modes.INT, STOP);
        coerceSerial(a68, p.sub.next, w, true);
        p = p.next;
        coerceUnitList(a68, p.sub.next, q);
        if ((p = p.next) != null) {
            if (Parser.isOneOf(a68, p, OUT_PART, CHOICE)) {
                coerceSerial(a68, p.sub.next, q, true);
            } else if (Parser.isOneOf(a68, p, CASE_OUSE_PART, BRIEF_OUSE_PART)) {
                coerceIntCase(a68, p.sub, q);
            }
        }
    }

    /** Coerce spec unit list (coerce_spec_unit_list). */
    private static void coerceSpecUnitList(A68 a68, Node p, Soid q) {
        for (; p != null; p = p.next) {
            if (Parser.isOneOf(a68, p, SPECIFIED_UNIT_LIST, SPECIFIED_UNIT)) {
                coerceSpecUnitList(a68, p.sub, q);
            } else if (p.is(UNIT)) {
                coerceUnit(a68, p, q);
            }
        }
    }

    /** Coerce united case (coerce_united_case). */
    private static void coerceUnitedCase(A68 a68, Node p, Soid q) {
        Soid w = new Soid();
        MoidsMisc.makeSoid(a68, w, MEEK, p.sub.type, STOP);
        coerceSerial(a68, p.sub.next, w, true);
        p = p.next;
        coerceSpecUnitList(a68, p.sub.next, q);
        if ((p = p.next) != null) {
            if (Parser.isOneOf(a68, p, OUT_PART, CHOICE)) {
                coerceSerial(a68, p.sub.next, q, true);
            } else if (Parser.isOneOf(a68, p, CONFORMITY_OUSE_PART, BRIEF_CONFORMITY_OUSE_PART)) {
                coerceUnitedCase(a68, p.sub, q);
            }
        }
    }

    /** Coerce loop (coerce_loop). */
    private static void coerceLoop(A68 a68, Node p) {
        if (p.is(FOR_PART)) {
            coerceLoop(a68, p.next);
        } else if (Parser.isOneOf(a68, p, FROM_PART, BY_PART, TO_PART)) {
            Soid w = new Soid();
            MoidsMisc.makeSoid(a68, w, MEEK, a68.modes.INT, STOP);
            coerceUnit(a68, p.sub.next, w);
            coerceLoop(a68, p.next);
        } else if (p.is(WHILE_PART)) {
            Soid w = new Soid();
            MoidsMisc.makeSoid(a68, w, MEEK, a68.modes.BOOL, STOP);
            coerceSerial(a68, p.sub.next, w, true);
            coerceLoop(a68, p.next);
        } else if (Parser.isOneOf(a68, p, DO_PART, ALT_DO_PART)) {
            Soid w = new Soid();
            Node doP = p.sub.next;
            MoidsMisc.makeSoid(a68, w, STRONG, a68.modes.VOID, STOP);
            coerceSerial(a68, doP, w, true);
        }
    }

    /** Coerce struct display (coerce_struct_display). */
    private static void coerceStructDisplay(A68 a68, Ref<Pack> r, Node p) {
        if (p == null) {
            return;
        } else if (p.is(UNIT_LIST)) {
            coerceStructDisplay(a68, r, p.sub);
            coerceStructDisplay(a68, r, p.next);
        } else if (Parser.isOneOf(a68, p, OPEN_SYMBOL, BEGIN_SYMBOL, COMMA_SYMBOL)) {
            coerceStructDisplay(a68, r, p.next);
        } else if (p.is(UNIT)) {
            Soid s = new Soid();
            MoidsMisc.makeSoid(a68, s, STRONG, r.get().type, STOP);
            coerceUnit(a68, p, s);
            r.set(r.get().next);
            coerceStructDisplay(a68, r, p.next);
        }
    }

    /** Coerce collateral (coerce_collateral). */
    private static void coerceCollateral(A68 a68, Node p, Soid q) {
        if (!(Parser.whether(a68, p, BEGIN_SYMBOL, END_SYMBOL)
                || Parser.whether(a68, p, OPEN_SYMBOL, CLOSE_SYMBOL))) {
            if (q.type.attribute == STRUCT_SYMBOL) {
                Ref.Box<Pack> t = Ref.box(q.type.pack);
                coerceStructDisplay(a68, t, p);
            } else if (q.type.attribute == FLEX_SYMBOL) {
                Soid w = new Soid();
                MoidsMisc.makeSoid(a68, w, STRONG, q.type.sub.slice, STOP);
                coerceUnitList(a68, p, w);
            } else if (q.type.attribute == ROW_SYMBOL) {
                Soid w = new Soid();
                MoidsMisc.makeSoid(a68, w, STRONG, q.type.slice, STOP);
                coerceUnitList(a68, p, w);
            } else {
                // if (MOID (q) != M_VOID).
                coerceUnitList(a68, p, q);
            }
        }
    }

    /** Coerce_enclosed (coerce_enclosed). */
    private static void coerceEnclosed(A68 a68, Node p, Soid q) {
        if (p.is(ENCLOSED_CLAUSE)) {
            coerceEnclosed(a68, p.sub, q);
        } else if (p.is(CLOSED_CLAUSE)) {
            coerceClosed(a68, p.sub, q);
        } else if (p.is(COLLATERAL_CLAUSE)) {
            coerceCollateral(a68, p.sub, q);
        } else if (p.is(ACCESS_CLAUSE)) {
            coerceAccess(a68, p.sub, q);
        } else if (p.is(PARALLEL_CLAUSE)) {
            coerceCollateral(a68, p.sub.next.sub, q);
        } else if (p.is(CONDITIONAL_CLAUSE)) {
            coerceConditional(a68, p.sub, q);
        } else if (p.is(CASE_CLAUSE)) {
            coerceIntCase(a68, p.sub, q);
        } else if (p.is(CONFORMITY_CLAUSE)) {
            coerceUnitedCase(a68, p.sub, q);
        } else if (p.is(LOOP_CLAUSE)) {
            coerceLoop(a68, p.sub);
        }

        p.type = MoidsMisc.deprefRows(a68, p.type, q.type);
    }

    /** Get monad moid (get_monad_moid). */
    private static Moid getMonadMoid(A68 a68, Node p) {
        if (p.tag != null && p.tag != a68.parserState.errorTag) {
            p.type = p.tag.type;
            return p.type.pack.type;
        } else {
            return a68.modes.ERROR;
        }
    }

    /** Coerce monad oper (coerce_monad_oper). */
    private static void coerceMonadOper(A68 a68, Node p, Soid q) {
        if (p != null) {
            Soid z = new Soid();
            MoidsMisc.makeSoid(a68, z, FIRM, p.tag.type.pack.type, STOP);
            insertCoercions(a68, p.next, q.type, z);
        }
    }

    /** Coerce monad formula (coerce_monad_formula). */
    private static void coerceMonadFormula(A68 a68, Node p) {
        Soid e = new Soid();
        MoidsMisc.makeSoid(a68, e, STRONG, getMonadMoid(a68, p), STOP);
        coerceOperand(a68, p.next, e);
        coerceMonadOper(a68, p, e);
    }

    /** Coerce operand (coerce_operand). */
    private static void coerceOperand(A68 a68, Node p, Soid q) {
        if (p.is(MONADIC_FORMULA)) {
            coerceMonadFormula(a68, p.sub);
            if (p.type != q.type) {
                Parser.makeSub(a68, p, p, FORMULA);
                insertCoercions(a68, p, p.type, q);
                Parser.makeSub(a68, p, p, TERTIARY);
            }
            p.type = MoidsMisc.deprefRows(a68, p.type, q.type);
        } else if (p.is(FORMULA)) {
            coerceFormula(a68, p.sub, q);
            insertCoercions(a68, p, p.type, q);
            p.type = MoidsMisc.deprefRows(a68, p.type, q.type);
        } else if (p.is(SECONDARY)) {
            coerceUnit(a68, p.sub, q);
            p.type = p.sub.type;
        }
    }

    /** Coerce formula (coerce_formula). Q is unused. */
    private static void coerceFormula(A68 a68, Node p, Soid q) {
        if (p.is(MONADIC_FORMULA) && p.next == null) {
            coerceMonadFormula(a68, p.sub);
        } else {
            if (p.next.tag != null && p.next.tag != a68.parserState.errorTag) {
                Soid s = new Soid();
                Node op = p.next, nq = p.next.next;
                Moid w = op.type;
                Moid u = w.pack.type, v = w.pack.next.type;
                MoidsMisc.makeSoid(a68, s, STRONG, u, STOP);
                coerceOperand(a68, p, s);
                MoidsMisc.makeSoid(a68, s, STRONG, v, STOP);
                coerceOperand(a68, nq, s);
            }
        }
    }

    /** Coerce assignation (coerce_assignation). */
    private static void coerceAssignation(A68 a68, Node p) {
        Soid w = new Soid();
        MoidsMisc.makeSoid(a68, w, SOFT, p.type, STOP);
        coerceUnit(a68, p.sub, w);
        MoidsMisc.makeSoid(a68, w, STRONG, p.type.sub, STOP);
        coerceUnit(a68, p.next.next, w);
    }

    /** Coerce relation (coerce_relation). */
    private static void coerceRelation(A68 a68, Node p) {
        Soid w = new Soid();
        MoidsMisc.makeSoid(a68, w, STRONG, p.type, STOP);
        coerceUnit(a68, p.sub, w);
        MoidsMisc.makeSoid(a68, w, STRONG, p.next.next.type, STOP);
        coerceUnit(a68, p.next.next.sub, w);
    }

    /** Coerce bool function (coerce_bool_function). */
    private static void coerceBoolFunction(A68 a68, Node p) {
        Soid w = new Soid();
        MoidsMisc.makeSoid(a68, w, STRONG, a68.modes.BOOL, STOP);
        coerceUnit(a68, p.sub, w);
        coerceUnit(a68, p.next.next.sub, w);
    }

    /** Coerce assertion (coerce_assertion). */
    private static void coerceAssertion(A68 a68, Node p) {
        Soid w = new Soid();
        MoidsMisc.makeSoid(a68, w, MEEK, a68.modes.BOOL, STOP);
        coerceEnclosed(a68, p.next.sub, w);
    }

    /** Coerce selection (coerce_selection). */
    private static void coerceSelection(A68 a68, Node p) {
        Soid w = new Soid();
        MoidsMisc.makeSoid(a68, w, STRONG, p.next.type, STOP);
        coerceUnit(a68, p.next.sub, w);
    }

    /** Coerce cast (coerce_cast). */
    private static void coerceCast(A68 a68, Node p) {
        coerceDeclarer(a68, p);
        Soid w = new Soid();
        MoidsMisc.makeSoid(a68, w, STRONG, p.type, STOP);
        coerceEnclosed(a68, p.next, w);
    }

    /** Coerce argument list (coerce_argument_list). */
    private static void coerceArgumentList(A68 a68, Ref<Pack> r, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(ARGUMENT_LIST)) {
                coerceArgumentList(a68, r, p.sub);
            } else if (p.is(UNIT)) {
                Soid s = new Soid();
                MoidsMisc.makeSoid(a68, s, STRONG, r.get().type, STOP);
                coerceUnit(a68, p, s);
                r.set(r.get().next);
            } else if (p.is(TRIMMER)) {
                r.set(r.get().next);
            }
        }
    }

    /** Coerce call (coerce_call). */
    private static void coerceCall(A68 a68, Node p) {
        Moid proc = p.type;
        Soid w = new Soid();
        MoidsMisc.makeSoid(a68, w, MEEK, proc, STOP);
        coerceUnit(a68, p.sub, w);
        p = p.next;
        Ref.Box<Pack> t = Ref.box(proc.pack);
        coerceArgumentList(a68, t, p.sub);
    }

    /** Coerce meek int (coerce_meek_int). */
    private static void coerceMeekInt(A68 a68, Node p) {
        Soid x = new Soid();
        MoidsMisc.makeSoid(a68, x, MEEK, a68.modes.INT, STOP);
        coerceUnit(a68, p, x);
    }

    /** Coerce trimmer (coerce_trimmer). */
    private static void coerceTrimmer(A68 a68, Node p) {
        if (p != null) {
            if (p.is(UNIT)) {
                coerceMeekInt(a68, p);
                coerceTrimmer(a68, p.next);
            } else {
                coerceTrimmer(a68, p.next);
            }
        }
    }

    /** Coerce indexer (coerce_indexer). */
    private static void coerceIndexer(A68 a68, Node p) {
        if (p != null) {
            if (p.is(TRIMMER)) {
                coerceTrimmer(a68, p.sub);
            } else if (p.is(UNIT)) {
                coerceMeekInt(a68, p);
            } else {
                coerceIndexer(a68, p.sub);
                coerceIndexer(a68, p.next);
            }
        }
    }

    /** Coerce_slice (coerce_slice). */
    private static void coerceSlice(A68 a68, Node p) {
        Soid w = new Soid();
        Moid row = p.type;
        MoidsMisc.makeSoid(a68, w, STRONG, row, STOP);
        coerceUnit(a68, p.sub, w);
        coerceIndexer(a68, p.next.sub);
    }

    /** Coerce format text (coerce_format_text). */
    private static void coerceFormatText(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            coerceFormatText(a68, p.sub);
            if (p.is(FORMAT_PATTERN)) {
                Soid x = new Soid();
                MoidsMisc.makeSoid(a68, x, STRONG, a68.modes.FORMAT, STOP);
                coerceEnclosed(a68, p.sub.next.sub, x);
            } else if (p.is(GENERAL_PATTERN) && p.sub.next != null) {
                Soid x = new Soid();
                MoidsMisc.makeSoid(a68, x, STRONG, a68.modes.ROW_INT, STOP);
                coerceEnclosed(a68, p.sub.next.sub, x);
            } else if (p.is(DYNAMIC_REPLICATOR)) {
                Soid x = new Soid();
                MoidsMisc.makeSoid(a68, x, STRONG, a68.modes.INT, STOP);
                coerceEnclosed(a68, p.sub.next.sub, x);
            }
        }
    }

    /** Coerce unit (coerce_unit). */
    private static void coerceUnit(A68 a68, Node p, Soid q) {
        if (p == null) {
            return;
        } else if (Parser.isOneOf(a68, p, UNIT, TERTIARY, SECONDARY, PRIMARY)) {
            coerceUnit(a68, p.sub, q);
            p.type = p.sub.type;
            // Ex primary.
        } else if (p.is(CALL)) {
            coerceCall(a68, p.sub);
            insertCoercions(a68, p, p.type, q);
        } else if (p.is(SLICE)) {
            coerceSlice(a68, p.sub);
            insertCoercions(a68, p, p.type, q);
        } else if (p.is(CAST)) {
            coerceCast(a68, p.sub);
            insertCoercions(a68, p, p.type, q);
        } else if (Parser.isOneOf(a68, p, DENOTATION, IDENTIFIER)) {
            insertCoercions(a68, p, p.type, q);
        } else if (p.is(FORMAT_TEXT)) {
            coerceFormatText(a68, p.sub);
            insertCoercions(a68, p, p.type, q);
        } else if (p.is(ENCLOSED_CLAUSE)) {
            coerceEnclosed(a68, p, q);
            // Ex secondary.
        } else if (p.is(SELECTION)) {
            coerceSelection(a68, p.sub);
            insertCoercions(a68, p, p.type, q);
        } else if (p.is(GENERATOR)) {
            coerceDeclarer(a68, p.sub);
            insertCoercions(a68, p, p.type, q);
            // Ex tertiary.
        } else if (p.is(NIHIL)) {
            if (q.type.attribute != REF_SYMBOL && q.type != a68.modes.VOID) {
                a68.diagnostics.error(p, "context does not require a name");
            }
            p.type = MoidsMisc.deprefRows(a68, p.type, q.type);
        } else if (p.is(FORMULA)) {
            coerceFormula(a68, p.sub, q);
            insertCoercions(a68, p, p.type, q);
        } else if (p.is(JUMP)) {
            if (q.type == a68.modes.PROC_VOID) {
                Parser.makeSub(a68, p, p, PROCEDURING);
            }
            p.type = MoidsMisc.deprefRows(a68, p.type, q.type);
        } else if (p.is(SKIP)) {
            p.type = MoidsMisc.deprefRows(a68, p.type, q.type);
        } else if (p.is(ASSIGNATION)) {
            coerceAssignation(a68, p.sub);
            insertCoercions(a68, p, p.type, q);
            p.type = MoidsMisc.deprefRows(a68, p.type, q.type);
        } else if (p.is(IDENTITY_RELATION)) {
            coerceRelation(a68, p.sub);
            insertCoercions(a68, p, p.type, q);
        } else if (p.is(ROUTINE_TEXT)) {
            coerceRoutineText(a68, p.sub);
            insertCoercions(a68, p, p.type, q);
        } else if (Parser.isOneOf(a68, p, AND_FUNCTION, OR_FUNCTION)) {
            coerceBoolFunction(a68, p.sub);
            insertCoercions(a68, p, p.type, q);
        } else if (p.is(ASSERTION)) {
            coerceAssertion(a68, p.sub);
            insertCoercions(a68, p, p.type, q);
        }
    }

    /** Coerce module text (coerce_module_text). */
    private static void coerceModuleText(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(DEF_PART) || p.is(POSTLUDE_PART)) {
                Soid w = new Soid();
                MoidsMisc.makeSoid(a68, w, STRONG, a68.modes.VOID, STOP);
                coerceSerial(a68, p.sub.next, w, true);
            }
        }
    }

    /** Coerce module declaration (coerce_module_declaration). */
    private static void coerceModuleDeclaration(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(MODULE_TEXT)) {
                coerceModuleText(a68, p.sub);
            } else {
                coerceModuleDeclaration(a68, p.sub);
            }
        }
    }

    /** Driver for coercion insertions (a68_coercion_inserter). */
    public static void coercionInserter(A68 a68, Node p) {
        if (p.is(PACKET)) {
            p = p.sub;
            if (p.is(PARTICULAR_PROGRAM)) {
                Soid q = new Soid();
                MoidsMisc.makeSoid(a68, q, STRONG, a68.modes.VOID, STOP);
                coerceEnclosed(a68, p.sub, q);
            } else if (p.is(PRELUDE_PACKET)) {
                coerceModuleDeclaration(a68, p.sub);
            }
        }
    }
}
