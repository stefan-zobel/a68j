// Java port of ga68's a68-parser-moids-check.cc: mode checker routines.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC and
// fixes by Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;
import static a68j.front.MoidsMisc.ALIAS_DEFLEXING;
import static a68j.front.MoidsMisc.A68_DEPREF;
import static a68j.front.MoidsMisc.A68_NO_DEPREF;
import static a68j.front.MoidsMisc.FORCE_DEFLEXING;
import static a68j.front.MoidsMisc.NO_DEFLEXING;
import static a68j.front.MoidsMisc.SAFE_DEFLEXING;
import static a68j.front.MoidsMisc.SKIP_DEFLEXING;

/**
 * Mode checker routines (a68-parser-moids-check.cc).
 *
 * <p>ALGOL 68 contexts are SOFT, WEAK, MEEK, FIRM and STRONG.
 * These contexts are increasing in strength:
 *
 * <pre>
 *   SOFT: Deproceduring
 *
 *   WEAK: Dereferencing to REF [] or REF STRUCT
 *
 *   MEEK: Deproceduring and dereferencing
 *
 *   FIRM: MEEK followed by uniting
 *
 *   STRONG: FIRM followed by rowing, widening or voiding
 * </pre>
 *
 * <p>Furthermore you will see in this file next switches:
 *
 * <pre>
 *   (1) FORCE_DEFLEXING allows assignment compatibility between FLEX and non FLEX
 *   rows. This can only be the case when there is no danger of altering bounds of a
 *   non FLEX row.
 *
 *   (2) ALIAS_DEFLEXING prohibits aliasing a FLEX row to a non FLEX row (vice versa
 *   is no problem) so that one cannot alter the bounds of a non FLEX row by
 *   aliasing it to a FLEX row. This is particularly the case when passing names as
 *   parameters to procedures:
 *
 *      PROC x = (REF STRING s) VOID: ..., PROC y = (REF [] CHAR c) VOID: ...;
 *
 *      x (LOC STRING);    # OK #
 *
 *      x (LOC [10] CHAR); # Not OK, suppose x changes bounds of s! #
 *
 *      y (LOC STRING);    # OK #
 *
 *      y (LOC [10] CHAR); # OK #
 *
 *   (3) SAFE_DEFLEXING sets FLEX row apart from non FLEX row. This holds for names,
 *   not for values, so common things are not rejected, for instance
 *
 *      STRING x = read string;
 *
 *      [] CHAR y = read string
 *
 *   (4) NO_DEFLEXING sets FLEX row apart from non FLEX row.
 * </pre>
 *
 * <p>In the RR grammar:
 *
 * <pre>
 *    SORT: strong; firm; weak; meek; soft.
 *    SORT MOID serial clause;
 *      strong void unit, go on token, SORT MOID serial clause;
 *      declaration, go on token, SORT MOID serial clause;
 *      SORT MOID unit
 * </pre>
 *
 * <p>And it is the SORT MOID sequence of metanotions, which shall evaluate the
 * same in the complete rule, that control the balancing! o_O
 *
 * <p>Also, it denotes how the SORT MOID of the serial clause gets "passed" to the
 * last unit in the serial clause.  Other units have SOID `strong void'.
 *
 * <p>It is used to pass down the required mode on whatever context.  Like,
 * PARTICULAR_PROGRAM evaluates in strong context and requires VOID.
 *
 * <p>The ATTRIBUTE in the soid is used to pass down the kind of construct that
 * introduces the context+required mode.  This is used in
 * a68_determine_unique_mode in order to know whether balancing shall be
 * performed or not.
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>{@code SOID_T} locals become fresh {@link Soid} objects; {@code SOID_T **}
 * soid lists are {@link Ref}{@code <Soid>}, {@code PACK_T **} are
 * {@link Ref}{@code <Pack>}, {@code int *} counters are {@code int[1]}.</li>
 * <li>An attribute argument {@code 0} (no attribute) is {@link Attribute#STOP},
 * whose ordinal is 0.</li>
 * <li>The macros {@code IS_FLEX}, {@code IS_ROW}, {@code IS_REF},
 * {@code IS_FLEXETY_ROW} and {@code A68_IF_MODE_IS_WELL} are private helpers.</li>
 * <li>{@code mode_check_specified_unit_list} and {@code mode_check_collateral}
 * are not static in C (although not declared in a68.h); they are public here.</li>
 * </ul>
 * Quirks of the original are preserved on purpose and marked with "Sic".
 */
public final class MoidsCheck {
    private MoidsCheck() {}

    // Macros of a68.h / a68-types.h used in this file.

    /** IS_FLEX (m). */
    private static boolean isFlex(Moid m) {
        return m.attribute == FLEX_SYMBOL;
    }

    /** IS_ROW (m). */
    private static boolean isRow(Moid m) {
        return m.attribute == ROW_SYMBOL;
    }

    /** IS_REF (m). */
    private static boolean isRef(Moid m) {
        return m.attribute == REF_SYMBOL;
    }

    /** IS_FLEXETY_ROW (m). */
    private static boolean isFlexetyRow(A68 a68, Moid m) {
        return (isFlex(m) || isRow(m) || m == a68.modes.STRING);
    }

    /** A68_IF_MODE_IS_WELL (n). */
    private static boolean ifModeIsWell(A68 a68, Moid n) {
        return !(n == a68.modes.ERROR || n == a68.modes.UNDEFINED);
    }

    // Forward declarations of some of the functions defined below: not needed in Java.

    /** Driver for mode checker (a68_mode_checker). */
    public static void modeChecker(A68 a68, Node p) {
        if (p.is(PACKET)) {
            p = p.sub;

            if (p.is(PARTICULAR_PROGRAM)) {
                a68.topSoidList = null;
                Soid x = new Soid(), y = new Soid();
                MoidsMisc.makeSoid(a68, x, STRONG, a68.modes.VOID, STOP);
                modeCheckEnclosed(a68, p.sub, x, y);
                p.type = y.type;
            } else if (p.is(PRELUDE_PACKET)) {
                modeCheckModuleDeclaration(a68, p.sub);
            }
        }
    }

    /** Mode check on bounds (mode_check_bounds). */
    private static void modeCheckBounds(A68 a68, Node p) {
        if (p == null) {
            return;
        } else if (p.is(UNIT)) {
            Soid x = new Soid(), y = new Soid();
            MoidsMisc.makeSoid(a68, x, STRONG, a68.modes.INT, STOP);
            modeCheckUnit(a68, p, x, y);
            if (!MoidsMisc.isCoercibleInContext(a68, y, x, SAFE_DEFLEXING)) {
                MoidsDiagnostics.cannotCoerce(a68, p, y.type, a68.modes.INT, MEEK, SAFE_DEFLEXING, UNIT);
            }
            modeCheckBounds(a68, p.next);
        } else {
            modeCheckBounds(a68, p.sub);
            modeCheckBounds(a68, p.next);
        }
    }

    /** Mode check declarer (mode_check_declarer). */
    private static void modeCheckDeclarer(A68 a68, Node p) {
        if (p == null) {
            return;
        } else if (p.is(BOUNDS)) {
            modeCheckBounds(a68, p.sub);
            modeCheckDeclarer(a68, p.next);
        } else {
            modeCheckDeclarer(a68, p.sub);
            modeCheckDeclarer(a68, p.next);
        }
    }

    /** Mode check identity declaration (mode_check_identity_declaration). */
    private static void modeCheckIdentityDeclaration(A68 a68, Node p) {
        if (p != null) {
            switch (p.attribute) {
            case DECLARER:
                modeCheckDeclarer(a68, p.sub);
                modeCheckIdentityDeclaration(a68, p.next);
                break;
            case DEFINING_IDENTIFIER: {
                Soid x = new Soid(), y = new Soid();
                MoidsMisc.makeSoid(a68, x, STRONG, p.type, STOP);
                modeCheckUnit(a68, p.next.next, x, y);
                if (!MoidsMisc.isCoercibleInContext(a68, y, x, SAFE_DEFLEXING)) {
                    MoidsDiagnostics.cannotCoerce(a68, p.next.next, y.type, x.type, STRONG, SAFE_DEFLEXING, UNIT);
                } else if (x.type != y.type) {
                    // Check for instance, REF INT i = LOC REF INT.
                    MoidsDiagnostics.semanticPitfall(a68, p.next.next, x.type, IDENTITY_DECLARATION, GENERATOR);
                }
                break;
            }
            default:
                modeCheckIdentityDeclaration(a68, p.sub);
                modeCheckIdentityDeclaration(a68, p.next);
                break;
            }
        }
    }

    /** Mode check variable declaration (mode_check_variable_declaration). */
    private static void modeCheckVariableDeclaration(A68 a68, Node p) {
        if (p != null) {
            switch (p.attribute) {
            case DECLARER:
                modeCheckDeclarer(a68, p.sub);
                modeCheckVariableDeclaration(a68, p.next);
                break;
            case DEFINING_IDENTIFIER:
                if (Parser.whether(a68, p, DEFINING_IDENTIFIER, ASSIGN_SYMBOL, UNIT)) {
                    Soid x = new Soid(), y = new Soid();
                    MoidsMisc.makeSoid(a68, x, STRONG, p.type.sub, STOP);
                    modeCheckUnit(a68, p.next.next, x, y);
                    if (!MoidsMisc.isCoercibleInContext(a68, y, x, FORCE_DEFLEXING)) {
                        MoidsDiagnostics.cannotCoerce(a68, p, y.type, x.type, STRONG, FORCE_DEFLEXING, UNIT);
                    } else if (x.type.sub != y.type) {
                        // Check for instance, REF INT i = LOC REF INT.
                        MoidsDiagnostics.semanticPitfall(a68, p.next.next, x.type, VARIABLE_DECLARATION, GENERATOR);
                    }
                }
                break;
            default:
                modeCheckVariableDeclaration(a68, p.sub);
                modeCheckVariableDeclaration(a68, p.next);
                break;
            }
        }
    }

    /** Mode check routine text (mode_check_routine_text). */
    private static void modeCheckRoutineText(A68 a68, Node p, Soid y) {
        Soid w = new Soid();

        if (p.is(PARAMETER_PACK)) {
            modeCheckDeclarer(a68, p.sub);
            p = p.next;
        }

        modeCheckDeclarer(a68, p.sub);
        MoidsMisc.makeSoid(a68, w, STRONG, p.type, STOP);
        modeCheckUnit(a68, p.next.next, w, y);
        if (!MoidsMisc.isCoercibleInContext(a68, y, w, FORCE_DEFLEXING)) {
            MoidsDiagnostics.cannotCoerce(a68, p.next.next, y.type, w.type, STRONG, FORCE_DEFLEXING, UNIT);
        }
    }

    /** Mode check proc declaration (mode_check_proc_declaration). */
    private static void modeCheckProcDeclaration(A68 a68, Node p) {
        if (p == null) {
            return;
        } else if (p.is(ROUTINE_TEXT)) {
            Soid x = new Soid(), y = new Soid();
            // Sic: x is set up but never used.
            MoidsMisc.makeSoid(a68, x, STRONG, null, STOP);
            modeCheckRoutineText(a68, p.sub, y);
        } else {
            modeCheckProcDeclaration(a68, p.sub);
            modeCheckProcDeclaration(a68, p.next);
        }
    }

    /** Mode check brief op declaration (mode_check_brief_op_declaration). */
    private static void modeCheckBriefOpDeclaration(A68 a68, Node p) {
        if (p == null) {
            return;
        } else if (p.is(DEFINING_OPERATOR)) {
            Soid y = new Soid();

            if (p.type != p.next.next.type) {
                Soid y2 = new Soid(), x = new Soid();
                MoidsMisc.makeSoid(a68, y2, NO_SORT, p.next.next.type, STOP);
                MoidsMisc.makeSoid(a68, x, NO_SORT, p.type, STOP);
                MoidsDiagnostics.cannotCoerce(a68, p.next.next, y2.type, x.type, STRONG, SKIP_DEFLEXING, ROUTINE_TEXT);
            }
            modeCheckRoutineText(a68, p.next.next.sub, y);
        } else {
            modeCheckBriefOpDeclaration(a68, p.sub);
            modeCheckBriefOpDeclaration(a68, p.next);
        }
    }

    /** Mode check op declaration (mode_check_op_declaration). */
    private static void modeCheckOpDeclaration(A68 a68, Node p) {
        if (p == null) {
            return;
        } else if (p.is(DEFINING_OPERATOR)) {
            Soid y = new Soid(), x = new Soid();
            MoidsMisc.makeSoid(a68, x, STRONG, p.type, STOP);
            modeCheckUnit(a68, p.next.next, x, y);
            if (!MoidsMisc.isCoercibleInContext(a68, y, x, SAFE_DEFLEXING)) {
                MoidsDiagnostics.cannotCoerce(a68, p.next.next, y.type, x.type, STRONG, SAFE_DEFLEXING, UNIT);
            }
        } else {
            modeCheckOpDeclaration(a68, p.sub);
            modeCheckOpDeclaration(a68, p.next);
        }
    }

    /** Mode check declaration list (mode_check_declaration_list). */
    private static void modeCheckDeclarationList(A68 a68, Node p) {
        if (p != null) {
            switch (p.attribute) {
            case IDENTITY_DECLARATION:
                modeCheckIdentityDeclaration(a68, p.sub);
                break;
            case VARIABLE_DECLARATION:
                modeCheckVariableDeclaration(a68, p.sub);
                break;
            case MODE_DECLARATION:
                modeCheckDeclarer(a68, p.sub);
                break;
            case PROCEDURE_DECLARATION:
            case PROCEDURE_VARIABLE_DECLARATION:
                modeCheckProcDeclaration(a68, p.sub);
                break;
            case BRIEF_OPERATOR_DECLARATION:
                modeCheckBriefOpDeclaration(a68, p.sub);
                break;
            case OPERATOR_DECLARATION:
                modeCheckOpDeclaration(a68, p.sub);
                break;
            default:
                modeCheckDeclarationList(a68, p.sub);
                modeCheckDeclarationList(a68, p.next);
                break;
            }
        }
    }

    /** Mode check serial clause (mode_check_serial). */
    private static void modeCheckSerial(A68 a68, Ref<Soid> r, Node p, Soid x, boolean k) {
        if (p == null) {
            return;
        } else if (p.is(INITIALISER_SERIES)) {
            modeCheckSerial(a68, r, p.sub, x, false);
            modeCheckSerial(a68, r, p.next, x, k);
        } else if (p.is(DECLARATION_LIST)) {
            modeCheckDeclarationList(a68, p.sub);
        } else if (Parser.isOneOf(a68, p, LABEL, SEMI_SYMBOL, EXIT_SYMBOL)) {
            modeCheckSerial(a68, r, p.next, x, k);
        } else if (Parser.isOneOf(a68, p, SERIAL_CLAUSE, ENQUIRY_CLAUSE)) {
            if (p.next != null) {
                if (p.next.is(EXIT_SYMBOL) || p.next.is(END_SYMBOL) || p.next.is(CLOSE_SYMBOL)) {
                    modeCheckSerial(a68, r, p.sub, x, true);
                } else {
                    modeCheckSerial(a68, r, p.sub, x, false);
                }
                modeCheckSerial(a68, r, p.next, x, k);
            } else {
                modeCheckSerial(a68, r, p.sub, x, true);
            }
        } else if (p.is(LABELED_UNIT)) {
            modeCheckSerial(a68, r, p.sub, x, k);
        } else if (p.is(UNIT)) {
            Soid y = new Soid();

            if (k) {
                modeCheckUnit(a68, p, x, y);
            } else {
                Soid w = new Soid();
                MoidsMisc.makeSoid(a68, w, STRONG, a68.modes.VOID, STOP);
                modeCheckUnit(a68, p, w, y);
            }
            if (p.next != null) {
                modeCheckSerial(a68, r, p.next, x, k);
            } else {
                if (k) {
                    MoidsMisc.addToSoidList(a68, r, p, y);
                }
            }
        }
    }

    /** Mode check serial clause units (mode_check_serial_units). */
    private static void modeCheckSerialUnits(A68 a68, Node p, Soid x, Soid y, Attribute att) {
        // att is unused, as in C.
        Ref.Box<Soid> topSl = Ref.box(null);

        modeCheckSerial(a68, topSl, p.sub, x, true);
        if (MoidsMisc.isBalanced(a68, p, topSl.v, x.sort)) {
            Moid result = MoidsMisc.packSoidsInMoid(a68, topSl.v, SERIES_MODE);
            MoidsMisc.makeSoid(a68, y, x.sort, result, SERIAL_CLAUSE);
        } else {
            MoidsMisc.makeSoid(a68, y, x.sort, (x.type != null ? x.type : a68.modes.ERROR), STOP);
        }

        MoidsMisc.freeSoidList(a68, topSl.v);
    }

    /** Mode check unit list (mode_check_unit_list). */
    private static void modeCheckUnitList(A68 a68, Ref<Soid> r, Node p, Soid x) {
        modeCheckUnitList(a68, r, p, x, null);
    }

    /**
     * Mode check unit list (mode_check_unit_list), appending to the list R
     * from LAST on, the element of R appended last or null (see
     * MoidsMisc#addToSoidList(A68, Ref, Soid, Node, Soid)); returns the
     * element appended last. ga68 passes no LAST and walks R from its start
     * for every unit, which is quadratic in the number of units.
     */
    private static Soid modeCheckUnitList(A68 a68, Ref<Soid> r, Node p, Soid x, Soid last) {
        if (p == null) {
            return last;
        } else if (p.is(UNIT_LIST)) {
            last = modeCheckUnitList(a68, r, p.sub, x, last);
            return modeCheckUnitList(a68, r, p.next, x, last);
        } else if (p.is(COMMA_SYMBOL)) {
            return modeCheckUnitList(a68, r, p.next, x, last);
        } else if (p.is(UNIT)) {
            Soid y = new Soid();
            modeCheckUnit(a68, p, x, y);
            last = MoidsMisc.addToSoidList(a68, r, last, p, y);
            return modeCheckUnitList(a68, r, p.next, x, last);
        }
        return last;
    }

    /** Mode check struct display (mode_check_struct_display). */
    private static void modeCheckStructDisplay(A68 a68, Ref<Soid> r, Node p, Ref<Pack> fields) {
        modeCheckStructDisplay(a68, r, p, fields, null);
    }

    /**
     * Mode check struct display (mode_check_struct_display), appending to
     * the list R from LAST on and returning the element appended last, as
     * modeCheckUnitList does.
     */
    private static Soid modeCheckStructDisplay(A68 a68, Ref<Soid> r, Node p, Ref<Pack> fields, Soid last) {
        if (p == null) {
            return last;
        } else if (p.is(UNIT_LIST)) {
            last = modeCheckStructDisplay(a68, r, p.sub, fields, last);
            return modeCheckStructDisplay(a68, r, p.next, fields, last);
        } else if (p.is(COMMA_SYMBOL)) {
            return modeCheckStructDisplay(a68, r, p.next, fields, last);
        } else if (p.is(UNIT)) {
            Soid x = new Soid(), y = new Soid();

            if (fields.get() != null) {
                MoidsMisc.makeSoid(a68, x, STRONG, fields.get().type, STOP);
                fields.set(fields.get().next);
            } else {
                MoidsMisc.makeSoid(a68, x, STRONG, null, STOP);
            }
            modeCheckUnit(a68, p, x, y);
            last = MoidsMisc.addToSoidList(a68, r, last, p, y);
            return modeCheckStructDisplay(a68, r, p.next, fields, last);
        }
        return last;
    }

    /** Mode check get specified moids (mode_check_get_specified_moids). */
    private static void modeCheckGetSpecifiedMoids(A68 a68, Node p, Moid u) {
        for (; p != null; p = p.next) {
            if (Parser.isOneOf(a68, p, SPECIFIED_UNIT_LIST, SPECIFIED_UNIT)) {
                modeCheckGetSpecifiedMoids(a68, p.sub, u);
            } else if (p.is(SPECIFIER)) {
                Moid m = p.sub.next.type;
                ModeBuilder.addModeToPack(a68, Ref.of(() -> u.pack, v -> u.pack = v), m, null, m.node);
            }
        }
    }

    /**
     * Mode check specified unit list (mode_check_specified_unit_list).
     * Not static in C.
     */
    public static void modeCheckSpecifiedUnitList(A68 a68, Ref<Soid> r, Node p, Soid x, Moid u) {
        for (; p != null; p = p.next) {
            if (Parser.isOneOf(a68, p, SPECIFIED_UNIT_LIST, SPECIFIED_UNIT)) {
                modeCheckSpecifiedUnitList(a68, r, p.sub, x, u);
            } else if (p.is(SPECIFIER)) {
                Moid m = p.sub.next.type;
                if (u != null && !MoidsMisc.isUnitable(a68, m, u, SAFE_DEFLEXING)) {
                    Fmt m1 = Fmt.moid(a68, m);
                    Fmt m2 = Fmt.moid(a68, u);
                    a68.diagnostics.error(p, "%e is neither component nor subset of %e", m1, m2);
                }

            } else if (p.is(UNIT)) {
                Soid y = new Soid();
                modeCheckUnit(a68, p, x, y);
                MoidsMisc.addToSoidList(a68, r, p, y);
            }
        }
    }

    /** Mode check united case parts (mode_check_united_case_parts). */
    private static void modeCheckUnitedCaseParts(A68 a68, Ref<Soid> ry, Node p, Soid x) {
        Soid enqExpct = new Soid(), enqYield = new Soid();
        Moid u = null, v = null, w = null;
        // Check the CASE part and deduce the united mode.
        MoidsMisc.makeSoid(a68, enqExpct, MEEK, null, STOP);
        modeCheckSerialUnits(a68, p.sub.next, enqExpct, enqYield, ENQUIRY_CLAUSE);
        // Deduce the united mode from the enquiry clause.
        // This requires balancing.
        u = enqYield.type;
        Ref.Box<Moid> uRef = Ref.box(u);
        MoidsMisc.absorbSeriesPack(a68, uRef);
        u = uRef.v;
        u.dim = ModeBuilder.countPackMembers(a68, u.pack);
        if (u.dim == 1) {
            u = u.pack.type;
        } else {
            Moid united, balanced;
            united = MoidsMisc.makeUnitedMode(a68, u);
            balanced = MoidsMisc.getBalancedModeOrNoMode(a68, united,
                    STRONG, A68_NO_DEPREF,
                    SAFE_DEFLEXING);
            if (balanced != null) {
                u = balanced;
            }
        }
        u = MoidsMisc.deprefCompletely(a68, u);
        // Also deduce the united mode from the specifiers.
        v = Parser.newMoid(a68);
        v.attribute = SERIES_MODE;
        modeCheckGetSpecifiedMoids(a68, p.next.sub.next, v);
        v = MoidsMisc.makeUnitedMode(a68, v);
        // Determine a resulting union.
        if (u == a68.modes.HIP) {
            w = v;
        } else {
            if (u.attribute == UNION_SYMBOL) {
                boolean[] uv = new boolean[1], vu = new boolean[1], some = new boolean[1];
                MoidsMisc.investigateFirmRelations(a68, u.pack, v.pack, uv, some);
                MoidsMisc.investigateFirmRelations(a68, v.pack, u.pack, vu, some);
                if (uv[0] && vu[0]) {
                    // Every component has a specifier.
                    w = u;
                } else if (!uv[0] && !vu[0]) {
                    // Hmmmm ... let the coercer sort it out.
                    w = u;
                } else {
                    // This is all the balancing we allow here for the moment. Firmly
                    // related subsets are not valid so we absorb them. If this
                    // doesn't solve it then we get a coercion-error later.
                    w = MoidsMisc.absorbRelatedSubsets(a68, u);
                }
            } else {
                Fmt m = Fmt.moid(a68, u);
                a68.diagnostics.error(p.sub.next, "%e is not a united mode", m);
                return;
            }
        }
        p.sub.type = w;
        p = p.next;
        // Check the IN part.
        modeCheckSpecifiedUnitList(a68, ry, p.sub.next, x, w);
        // OUSE, OUT, ESAC.
        if ((p = p.next) != null) {
            if (Parser.isOneOf(a68, p, OUT_PART, CHOICE)) {
                modeCheckSerial(a68, ry, p.sub.next, x, true);
            } else if (Parser.isOneOf(a68, p, CONFORMITY_OUSE_PART, BRIEF_CONFORMITY_OUSE_PART)) {
                modeCheckUnitedCaseParts(a68, ry, p.sub, x);
            }
        }
    }

    /** Mode check united case (mode_check_united_case). */
    private static void modeCheckUnitedCase(A68 a68, Node p, Soid x, Soid y) {
        Ref.Box<Soid> topSl = Ref.box(null);

        modeCheckUnitedCaseParts(a68, topSl, p, x);
        if (!MoidsMisc.isBalanced(a68, p, topSl.v, x.sort)) {
            if (x.type != null) {
                MoidsMisc.makeSoid(a68, y, x.sort, x.type, CONFORMITY_CLAUSE);
            } else {
                MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
            }
        } else {
            Moid z = MoidsMisc.packSoidsInMoid(a68, topSl.v, SERIES_MODE);
            MoidsMisc.makeSoid(a68, y, x.sort, z, CONFORMITY_CLAUSE);
        }
        MoidsMisc.freeSoidList(a68, topSl.v);
    }

    /** Mode check unit list 2 (mode_check_unit_list_2). */
    private static void modeCheckUnitList2(A68 a68, Node p, Soid x, Soid y) {
        Ref.Box<Soid> topSl = Ref.box(null);

        if (x.type != null) {
            if (isFlex(x.type)) {
                Soid y2 = new Soid();
                MoidsMisc.makeSoid(a68, y2, x.sort, x.type.sub.slice, STOP);
                modeCheckUnitList(a68, topSl, p.sub, y2);
            } else if (isRow(x.type)) {
                Soid y2 = new Soid();
                MoidsMisc.makeSoid(a68, y2, x.sort, x.type.slice, STOP);
                modeCheckUnitList(a68, topSl, p.sub, y2);
            } else if (x.type.attribute == STRUCT_SYMBOL) {
                Ref.Box<Pack> y2 = Ref.box(x.type.pack);
                modeCheckStructDisplay(a68, topSl, p.sub, y2);
            } else {
                modeCheckUnitList(a68, topSl, p.sub, x);
            }
        } else {
            modeCheckUnitList(a68, topSl, p.sub, x);
        }

        MoidsMisc.makeSoid(a68, y, STRONG, MoidsMisc.packSoidsInMoid(a68, topSl.v, STOWED_MODE), STOP);
        MoidsMisc.freeSoidList(a68, topSl.v);
    }

    /** Mode check access (mode_check_access). */
    private static void modeCheckAccess(A68 a68, Node p, Soid x, Soid y) {
        for (Node q = p; q != null; q = q.next) {
            if (q.is(ENCLOSED_CLAUSE)) {
                modeCheckEnclosed(a68, q, x, y);
                p.type = y.type;
            }
        }
    }

    /** Mode check closed (mode_check_closed). */
    private static void modeCheckClosed(A68 a68, Node p, Soid x, Soid y) {
        if (p == null) {
            return;
        } else if (p.is(SERIAL_CLAUSE)) {
            modeCheckSerialUnits(a68, p, x, y, SERIAL_CLAUSE);
        } else if (Parser.isOneOf(a68, p, OPEN_SYMBOL, BEGIN_SYMBOL)) {
            modeCheckClosed(a68, p.next, x, y);
        }
        p.type = y.type;
    }

    /**
     * Mode check collateral (mode_check_collateral).
     * Not static in C.
     */
    public static void modeCheckCollateral(A68 a68, Node p, Soid x, Soid y) {
        if (p == null) {
            return;
        } else if (Parser.whether(a68, p, BEGIN_SYMBOL, END_SYMBOL)
                || Parser.whether(a68, p, OPEN_SYMBOL, CLOSE_SYMBOL)) {
            if (x.sort == STRONG) {
                if (x.type == null) {
                    a68.diagnostics.error(p, "vacuum cannot have row elements (use a %qs generator)",
                            Keywords.stropKeyword(a68, "REF MODE"));
                } else if (isFlexetyRow(a68, x.type)) {
                    MoidsMisc.makeSoid(a68, y, STRONG, a68.modes.VACUUM, STOP);
                } else {
                    // The syntax only allows vacuums in strong contexts with rowed
                    // modes.  See rule 33d.
                    Fmt m = Fmt.moid(a68, x.type);
                    a68.diagnostics.error(p, "a vacuum is not a valid %e", m);
                    MoidsMisc.makeSoid(a68, y, STRONG, a68.modes.ERROR, STOP);
                }
            } else {
                MoidsMisc.makeSoid(a68, y, STRONG, a68.modes.UNDEFINED, STOP);
            }
        } else {
            if (p.is(UNIT_LIST)) {
                modeCheckUnitList2(a68, p, x, y);
            } else if (Parser.isOneOf(a68, p, OPEN_SYMBOL, BEGIN_SYMBOL)) {
                modeCheckCollateral(a68, p.next, x, y);
            }
            p.type = y.type;
        }
    }

    /** Mode check conditional 2 (mode_check_conditional_2). */
    private static void modeCheckConditional2(A68 a68, Ref<Soid> ry, Node p, Soid x) {
        Soid enqExpct = new Soid(), enqYield = new Soid();

        MoidsMisc.makeSoid(a68, enqExpct, MEEK, a68.modes.BOOL, STOP);
        modeCheckSerialUnits(a68, p.sub.next, enqExpct, enqYield, ENQUIRY_CLAUSE);
        if (!MoidsMisc.isCoercibleInContext(a68, enqYield, enqExpct, SAFE_DEFLEXING)) {
            MoidsDiagnostics.cannotCoerce(a68, p, enqYield.type, enqExpct.type, MEEK, SAFE_DEFLEXING, ENQUIRY_CLAUSE);
        }
        p = p.next;
        modeCheckSerial(a68, ry, p.sub.next, x, true);
        if ((p = p.next) != null) {
            if (Parser.isOneOf(a68, p, ELSE_PART, CHOICE)) {
                modeCheckSerial(a68, ry, p.sub.next, x, true);
            } else if (Parser.isOneOf(a68, p, ELIF_PART, BRIEF_ELIF_PART)) {
                modeCheckConditional2(a68, ry, p.sub, x);
            }
        }
    }

    /** Mode check conditional (mode_check_conditional). */
    private static void modeCheckConditional(A68 a68, Node p, Soid x, Soid y) {
        Ref.Box<Soid> topSl = Ref.box(null);
        modeCheckConditional2(a68, topSl, p, x);
        if (!MoidsMisc.isBalanced(a68, p, topSl.v, x.sort)) {
            if (x.type != null) {
                MoidsMisc.makeSoid(a68, y, x.sort, x.type, CONDITIONAL_CLAUSE);
            } else {
                MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
            }
        } else {
            Moid z = MoidsMisc.packSoidsInMoid(a68, topSl.v, SERIES_MODE);
            MoidsMisc.makeSoid(a68, y, x.sort, z, CONDITIONAL_CLAUSE);
        }
        MoidsMisc.freeSoidList(a68, topSl.v);
    }

    /** Mode check int case 2 (mode_check_int_case_2). */
    private static void modeCheckIntCase2(A68 a68, Ref<Soid> ry, Node p, Soid x) {
        Soid enqExpct = new Soid(), enqYield = new Soid();
        MoidsMisc.makeSoid(a68, enqExpct, MEEK, a68.modes.INT, STOP);
        modeCheckSerialUnits(a68, p.sub.next, enqExpct, enqYield, ENQUIRY_CLAUSE);
        if (!MoidsMisc.isCoercibleInContext(a68, enqYield, enqExpct, SAFE_DEFLEXING)) {
            MoidsDiagnostics.cannotCoerce(a68, p, enqYield.type, enqExpct.type, MEEK, SAFE_DEFLEXING, ENQUIRY_CLAUSE);
        }
        p = p.next;
        modeCheckUnitList(a68, ry, p.sub.next, x);
        if ((p = p.next) != null) {
            if (Parser.isOneOf(a68, p, OUT_PART, CHOICE)) {
                modeCheckSerial(a68, ry, p.sub.next, x, true);
            } else if (Parser.isOneOf(a68, p, CASE_OUSE_PART, BRIEF_OUSE_PART)) {
                modeCheckIntCase2(a68, ry, p.sub, x);
            }
        }
    }

    /** Mode check int case (mode_check_int_case). */
    private static void modeCheckIntCase(A68 a68, Node p, Soid x, Soid y) {
        Ref.Box<Soid> topSl = Ref.box(null);
        modeCheckIntCase2(a68, topSl, p, x);
        if (!MoidsMisc.isBalanced(a68, p, topSl.v, x.sort)) {
            if (x.type != null) {
                MoidsMisc.makeSoid(a68, y, x.sort, x.type, CASE_CLAUSE);
            } else {
                MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
            }
        } else {
            Moid z = MoidsMisc.packSoidsInMoid(a68, topSl.v, SERIES_MODE);
            MoidsMisc.makeSoid(a68, y, x.sort, z, CASE_CLAUSE);
        }
        MoidsMisc.freeSoidList(a68, topSl.v);
    }

    /** Mode check loop 2 (mode_check_loop_2). */
    private static void modeCheckLoop2(A68 a68, Node p, Soid y) {
        if (p == null) {
            return;
        } else if (p.is(FOR_PART)) {
            modeCheckLoop2(a68, p.next, y);
        } else if (Parser.isOneOf(a68, p, FROM_PART, BY_PART, TO_PART)) {
            Soid ix = new Soid(), iy = new Soid();
            MoidsMisc.makeSoid(a68, ix, STRONG, a68.modes.INT, STOP);
            modeCheckUnit(a68, p.sub.next, ix, iy);
            if (!MoidsMisc.isCoercibleInContext(a68, iy, ix, SAFE_DEFLEXING)) {
                MoidsDiagnostics.cannotCoerce(a68, p.sub.next, iy.type, a68.modes.INT, MEEK, SAFE_DEFLEXING, ENQUIRY_CLAUSE);
            }
            modeCheckLoop2(a68, p.next, y);
        } else if (p.is(WHILE_PART)) {
            Soid enqExpct = new Soid(), enqYield = new Soid();
            MoidsMisc.makeSoid(a68, enqExpct, MEEK, a68.modes.BOOL, STOP);
            modeCheckSerialUnits(a68, p.sub.next, enqExpct, enqYield, ENQUIRY_CLAUSE);
            if (!MoidsMisc.isCoercibleInContext(a68, enqYield, enqExpct, SAFE_DEFLEXING)) {
                MoidsDiagnostics.cannotCoerce(a68, p, enqYield.type, enqExpct.type, MEEK, SAFE_DEFLEXING, ENQUIRY_CLAUSE);
            }
            modeCheckLoop2(a68, p.next, y);
        } else if (Parser.isOneOf(a68, p, DO_PART, ALT_DO_PART)) {
            Ref.Box<Soid> z = Ref.box(null);
            Node doP = p.sub.next;
            Soid ix = new Soid();
            MoidsMisc.makeSoid(a68, ix, STRONG, a68.modes.VOID, STOP);
            if (doP.is(SERIAL_CLAUSE)) {
                modeCheckSerial(a68, z, doP, ix, true);
            }
            MoidsMisc.freeSoidList(a68, z.v);
        }
    }

    /** Mode check loop (mode_check_loop). */
    private static void modeCheckLoop(A68 a68, Node p, Soid y) {
        Soid z = null;
        // Sic: z is NO_SOID; mode_check_loop_2 only passes it along.
        modeCheckLoop2(a68, p, z);
        MoidsMisc.makeSoid(a68, y, STRONG, a68.modes.VOID, STOP);
    }

    /** Mode check enclosed (mode_check_enclosed). */
    private static void modeCheckEnclosed(A68 a68, Node p, Soid x, Soid y) {
        if (p == null) {
            return;
        } else if (p.is(ENCLOSED_CLAUSE)) {
            modeCheckEnclosed(a68, p.sub, x, y);
        } else if (p.is(CLOSED_CLAUSE)) {
            modeCheckClosed(a68, p.sub, x, y);
        } else if (p.is(ACCESS_CLAUSE)) {
            modeCheckAccess(a68, p.sub, x, y);
        } else if (p.is(PARALLEL_CLAUSE)) {
            modeCheckCollateral(a68, p.sub.next.sub, x, y);
            MoidsMisc.makeSoid(a68, y, STRONG, a68.modes.VOID, STOP);
            p.sub.next.type = a68.modes.VOID;
        } else if (p.is(COLLATERAL_CLAUSE)) {
            modeCheckCollateral(a68, p.sub, x, y);
        } else if (p.is(CONDITIONAL_CLAUSE)) {
            modeCheckConditional(a68, p.sub, x, y);
        } else if (p.is(CASE_CLAUSE)) {
            modeCheckIntCase(a68, p.sub, x, y);
        } else if (p.is(CONFORMITY_CLAUSE)) {
            modeCheckUnitedCase(a68, p.sub, x, y);
        } else if (p.is(LOOP_CLAUSE)) {
            modeCheckLoop(a68, p.sub, y);
        }

        p.type = y.type;
    }

    /** Search table for operator (search_table_for_operator). */
    private static Tag searchTableForOperator(A68 a68, Tag t, String n, Moid x, Moid y) {
        if (MoidsMisc.isModeIsntWell(a68, x)) {
            return a68.parserState.errorTag;
        } else if (y != null && MoidsMisc.isModeIsntWell(a68, y)) {
            return a68.parserState.errorTag;
        }

        for (; t != null; t = t.next) {
            if (t.node.info.symbol == n || t.node.info.symbol.equals(n)) {
                Pack p = t.type.pack;
                if (MoidsMisc.isCoercible(a68, x, p.type, FIRM, ALIAS_DEFLEXING)) {
                    p = p.next;
                    if (p == null && y == null) {
                        // Matched in case of a monadic.
                        return t;
                    } else if (p != null && y != null
                            && MoidsMisc.isCoercible(a68, y, p.type, FIRM, ALIAS_DEFLEXING)) {
                        // Matched in case of a dyadic.
                        return t;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Search chain of symbol tables and return matching operator "x n y" or
     * "n x" (search_table_chain_for_operator).
     */
    private static Tag searchTableChainForOperator(A68 a68, Table s, String n, Moid x, Moid y) {
        if (MoidsMisc.isModeIsntWell(a68, x)) {
            return a68.parserState.errorTag;
        } else if (y != null && MoidsMisc.isModeIsntWell(a68, y)) {
            return a68.parserState.errorTag;
        }

        while (s != null) {
            Tag z = searchTableForOperator(a68, s.operators, n, x, y);
            if (z != null) {
                return z;
            }
            s = s.previous;
        }
        return null;
    }

    /** Return a matching operator "x n y" (find_operator). */
    private static Tag findOperator(A68 a68, Table s, String n, Moid x, Moid y) {
        // Coercions to operand modes are FIRM.
        Moid u, v;
        Tag z;
        // (A) Catch exceptions first.
        if (x == null && y == null) {
            return null;
        } else if (MoidsMisc.isModeIsntWell(a68, x)) {
            return a68.parserState.errorTag;
        } else if (y != null && MoidsMisc.isModeIsntWell(a68, y)) {
            return a68.parserState.errorTag;
        }

        // (B) MONADs.
        if (x != null && y == null) {
            z = searchTableChainForOperator(a68, s, n, x, null);
            if (z != null) {
                return z;
            } else {
                // (B.2) A little trick to allow - (0, 1) or ABS (1, long pi).
                if (MoidsMisc.isCoercible(a68, x, a68.modes.COMPLEX, STRONG, SAFE_DEFLEXING)) {
                    z = searchTableForOperator(a68, a68.standenv.operators, n, a68.modes.COMPLEX, null);
                    if (z != null) {
                        return z;
                    }
                }
                if (MoidsMisc.isCoercible(a68, x, a68.modes.LONG_COMPLEX, STRONG, SAFE_DEFLEXING)) {
                    z = searchTableForOperator(a68, a68.standenv.operators, n, a68.modes.LONG_COMPLEX, null);
                    if (z != null) {
                        return z;
                    }
                }
                if (MoidsMisc.isCoercible(a68, x, a68.modes.LONG_LONG_COMPLEX, STRONG, SAFE_DEFLEXING)) {
                    // Sic: the result of this search is not returned; it is
                    // dropped (or overwritten below) and NO_TAG returned.
                    z = searchTableForOperator(a68, a68.standenv.operators, n, a68.modes.LONG_LONG_COMPLEX, null);
                }
                if (MoidsMisc.isCoercible(a68, x, a68.modes.WORD_COMPLEX, STRONG, SAFE_DEFLEXING)) {
                    z = searchTableForOperator(a68, a68.standenv.operators, n,
                            a68.modes.WORD_COMPLEX, null);
                    if (z != null) {
                        return z;
                    }
                }
            }
            return null;
        }
        // (C) DYADs.
        z = searchTableChainForOperator(a68, s, n, x, y);
        if (z != null) {
            return z;
        }

        // The checks below apply to operators covered by a) and b) in 10.2.3.5 but
        // not to the pow operator, which is covered by g)
        if (n.equals("**") || n.equals("^")) {
            return z;
        }

        // (C.2) Vector and matrix "strong coercions" in standard environ.
        u = MoidsMisc.deprefCompletely(a68, x).deflex();
        v = MoidsMisc.deprefCompletely(a68, y).deflex();
        if ((u == a68.modes.ROW_REAL || u == a68.modes.ROW_ROW_REAL)
                || (v == a68.modes.ROW_REAL || v == a68.modes.ROW_ROW_REAL)
                || (u == a68.modes.ROW_COMPLEX || u == a68.modes.ROW_ROW_COMPLEX)
                || (v == a68.modes.ROW_COMPLEX || v == a68.modes.ROW_ROW_COMPLEX)) {
            if (u == a68.modes.INT) {
                z = searchTableForOperator(a68, a68.standenv.operators, n, a68.modes.REAL, y);
                if (z != null) {
                    return z;
                }
                z = searchTableForOperator(a68, a68.standenv.operators, n, a68.modes.COMPLEX, y);
                if (z != null) {
                    return z;
                }
            } else if (v == a68.modes.INT) {
                z = searchTableForOperator(a68, a68.standenv.operators, n, x, a68.modes.REAL);
                if (z != null) {
                    return z;
                }
                z = searchTableForOperator(a68, a68.standenv.operators, n, x, a68.modes.COMPLEX);
                if (z != null) {
                    return z;
                }
            } else if (u == a68.modes.REAL) {
                z = searchTableForOperator(a68, a68.standenv.operators, n, a68.modes.COMPLEX, y);
                if (z != null) {
                    return z;
                }
            } else if (v == a68.modes.REAL) {
                z = searchTableForOperator(a68, a68.standenv.operators, n, x, a68.modes.COMPLEX);
                if (z != null) {
                    return z;
                }
            }
        }

        // (C.3) Look in standenv for an appropriate cross-term.
        u = MoidsMisc.makeSeriesFromMoids(a68, x, y);
        u = MoidsMisc.makeUnitedMode(a68, u);
        v = MoidsMisc.getBalancedMode(a68, u, STRONG, A68_NO_DEPREF, SAFE_DEFLEXING);
        z = searchTableForOperator(a68, a68.standenv.operators, n, v, v);
        if (z != null) {
            return z;
        }
        if (MoidsMisc.isCoercibleSeries(a68, u, a68.modes.REAL, STRONG, SAFE_DEFLEXING)) {
            z = searchTableForOperator(a68, a68.standenv.operators, n, a68.modes.REAL, a68.modes.REAL);
            if (z != null) {
                return z;
            }
        }
        if (MoidsMisc.isCoercibleSeries(a68, u, a68.modes.LONG_REAL, STRONG, SAFE_DEFLEXING)) {
            z = searchTableForOperator(a68, a68.standenv.operators, n, a68.modes.LONG_REAL, a68.modes.LONG_REAL);
            if (z != null) {
                return z;
            }
        }
        if (MoidsMisc.isCoercibleSeries(a68, u, a68.modes.LONG_LONG_REAL, STRONG, SAFE_DEFLEXING)) {
            z = searchTableForOperator(a68, a68.standenv.operators, n, a68.modes.LONG_LONG_REAL, a68.modes.LONG_LONG_REAL);
            if (z != null) {
                return z;
            }
        }
        if (MoidsMisc.isCoercibleSeries(a68, u, a68.modes.COMPLEX, STRONG, SAFE_DEFLEXING)) {
            z = searchTableForOperator(a68, a68.standenv.operators, n, a68.modes.COMPLEX, a68.modes.COMPLEX);
            if (z != null) {
                return z;
            }
        }
        if (MoidsMisc.isCoercibleSeries(a68, u, a68.modes.LONG_COMPLEX, STRONG, SAFE_DEFLEXING)) {
            z = searchTableForOperator(a68, a68.standenv.operators, n, a68.modes.LONG_COMPLEX, a68.modes.LONG_COMPLEX);
            if (z != null) {
                return z;
            }
        }
        if (MoidsMisc.isCoercibleSeries(a68, u, a68.modes.LONG_LONG_COMPLEX, STRONG, SAFE_DEFLEXING)) {
            z = searchTableForOperator(a68, a68.standenv.operators, n, a68.modes.LONG_LONG_COMPLEX, a68.modes.LONG_LONG_COMPLEX);
            if (z != null) {
                return z;
            }
        }
        if (MoidsMisc.isCoercibleSeries(a68, u, a68.modes.WORD_COMPLEX, STRONG, SAFE_DEFLEXING)) {
            z = searchTableForOperator(a68, a68.standenv.operators, n,
                    a68.modes.WORD_COMPLEX, a68.modes.WORD_COMPLEX);
            if (z != null) {
                return z;
            }
        }

        // (C.4) Now allow for depreffing for REF REAL +:= INT and alike.
        v = MoidsMisc.getBalancedMode(a68, u, STRONG, A68_DEPREF, SAFE_DEFLEXING);
        z = searchTableForOperator(a68, a68.standenv.operators, n, v, v);
        if (z != null) {
            return z;
        }

        return null;
    }

    /** Mode check monadic operator (mode_check_monadic_operator). */
    private static void modeCheckMonadicOperator(A68 a68, Node p, Soid x, Soid y) {
        if (p != null) {
            Tag t;
            Moid u = MoidsMisc.determineUniqueMode(a68, y, SAFE_DEFLEXING);
            if (MoidsMisc.isModeIsntWell(a68, u)) {
                MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
            } else if (u == a68.modes.HIP) {
                Fmt m = Fmt.moid(a68, u);
                a68.diagnostics.error(p.next, "%e construct is an invalid operand", m);
                MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
            } else {
                // C: *(NSYMBOL (p)), which is the NUL terminator for an empty symbol.
                char first = p.info.symbol.isEmpty() ? '\0' : p.info.symbol.charAt(0);
                if (CType.strchr(Scanner.NOMADS, first)) {
                    t = null;
                    Fmt s = Fmt.symbol(p);
                    a68.diagnostics.error(p, "monadic %e cannot start with a character from %qs",
                            s, Scanner.NOMADS);
                    MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
                } else {
                    t = findOperator(a68, p.symbolTable, p.info.symbol, u, null);
                    if (t == null) {
                        Fmt s = Fmt.symbol(p);
                        Fmt o = Fmt.opmoid(a68, u);
                        a68.diagnostics.error(p, "monadic operator %e %e has not been declared",
                                s, o);
                        MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
                    }
                }
                if (t != null) {
                    p.type = t.type;
                }
                p.tag = t;
                if (t != null && t != a68.parserState.errorTag) {
                    p.type = t.type;
                    MoidsMisc.makeSoid(a68, y, x.sort, t.type.sub, STOP);
                } else {
                    p.type = a68.modes.ERROR;
                    MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
                }
            }
        }
    }

    /** Mode check monadic formula (mode_check_monadic_formula). */
    private static void modeCheckMonadicFormula(A68 a68, Node p, Soid x, Soid y) {
        Soid e = new Soid();
        MoidsMisc.makeSoid(a68, e, FIRM, null, STOP);
        modeCheckFormula(a68, p.next, e, y);
        modeCheckMonadicOperator(a68, p, e, y);
        MoidsMisc.makeSoid(a68, y, x.sort, y.type, STOP);
    }

    /** Mode check formula (mode_check_formula). */
    private static void modeCheckFormula(A68 a68, Node p, Soid x, Soid y) {
        // Sic: in C, ls (and rs below) stay uninitialised if the node is none of
        // the cases handled; here they are fresh soids.
        Soid ls = new Soid();
        if (p.is(MONADIC_FORMULA)) {
            modeCheckMonadicFormula(a68, p.sub, x, ls);
        } else if (p.is(FORMULA)) {
            modeCheckFormula(a68, p.sub, x, ls);
        } else if (p.is(SECONDARY)) {
            Soid e = new Soid();
            MoidsMisc.makeSoid(a68, e, FIRM, null, STOP);
            modeCheckUnit(a68, p.sub, e, ls);
        }
        Moid u = MoidsMisc.determineUniqueMode(a68, ls, SAFE_DEFLEXING);
        p.type = u;
        Soid rs = new Soid();
        if (p.next == null) {
            MoidsMisc.makeSoid(a68, y, x.sort, u, STOP);
        } else {
            Node q = p.next.next;
            if (q.is(MONADIC_FORMULA)) {
                modeCheckMonadicFormula(a68, p.next.next.sub, x, rs);
            } else if (q.is(FORMULA)) {
                modeCheckFormula(a68, p.next.next.sub, x, rs);
            } else if (q.is(SECONDARY)) {
                Soid e = new Soid();
                MoidsMisc.makeSoid(a68, e, FIRM, null, STOP);
                modeCheckUnit(a68, q.sub, e, rs);
            }
            Moid v = MoidsMisc.determineUniqueMode(a68, rs, SAFE_DEFLEXING);
            q.type = v;
            if (MoidsMisc.isModeIsntWell(a68, u) || MoidsMisc.isModeIsntWell(a68, v)) {
                MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
            } else if (u == a68.modes.HIP) {
                Fmt m = Fmt.moid(a68, u);
                a68.diagnostics.error(p, "%e construct is an invalid operand", m);
                MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
            } else if (v == a68.modes.HIP) {
                // Sic: the mode printed is u, not v.
                Fmt m = Fmt.moid(a68, u);
                a68.diagnostics.error(q, "%e construct is an invalid operand", m);
                MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
            } else {
                Tag op = findOperator(a68, p.next.symbolTable, p.next.info.symbol, u, v);
                if (op == null) {
                    Fmt s = Fmt.symbol(p.next);
                    Fmt o1 = Fmt.opmoid(a68, u);
                    Fmt o2 = Fmt.opmoid(a68, v);
                    a68.diagnostics.error(p.next, "dyadic operator %e %e %e has not been declared",
                            o1, s, o2);
                    MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
                }
                if (op != null) {
                    p.next.type = op.type;
                }
                p.next.tag = op;
                if (op != null && op != a68.parserState.errorTag) {
                    MoidsMisc.makeSoid(a68, y, x.sort, op.type.sub, STOP);
                } else {
                    MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
                }
            }
        }
    }

    /** Mode check assignation (mode_check_assignation). */
    private static void modeCheckAssignation(A68 a68, Node p, Soid x, Soid y) {
        // Get destination mode.
        Soid name = new Soid(), tmp = new Soid(), value = new Soid();
        MoidsMisc.makeSoid(a68, name, SOFT, null, STOP);
        modeCheckUnit(a68, p.sub, name, tmp);
        // SOFT coercion.
        Moid ori = MoidsMisc.determineUniqueMode(a68, tmp, SAFE_DEFLEXING);
        Moid nameMoid = MoidsMisc.deprocCompletely(a68, ori);
        if (nameMoid.attribute != REF_SYMBOL) {
            if (ifModeIsWell(a68, nameMoid)) {
                Fmt m = Fmt.moid(a68, ori);
                Fmt a = Fmt.attr(p.sub.attribute);
                a68.diagnostics.error(p, "%e %e does not yield a name", m, a);
            }
            MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
            return;
        }
        p.type = nameMoid;
        // Get source mode.
        MoidsMisc.makeSoid(a68, name, STRONG, nameMoid.sub, STOP);
        modeCheckUnit(a68, p.next.next, name, value);
        if (!MoidsMisc.isCoercibleInContext(a68, value, name, FORCE_DEFLEXING)) {
            MoidsDiagnostics.cannotCoerce(a68, p, value.type, name.type, STRONG, FORCE_DEFLEXING, UNIT);
            MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
        } else {
            MoidsMisc.makeSoid(a68, y, x.sort, nameMoid, STOP);
        }
    }

    /** Mode check identity relation (mode_check_identity_relation). */
    private static void modeCheckIdentityRelation(A68 a68, Node p, Soid x, Soid y) {
        Node ln = p, rn = p.next.next;
        Soid e = new Soid(), l = new Soid(), r = new Soid();
        MoidsMisc.makeSoid(a68, e, SOFT, null, STOP);
        modeCheckUnit(a68, ln.sub, e, l);
        modeCheckUnit(a68, rn.sub, e, r);
        // SOFT coercion.
        Moid oril = MoidsMisc.determineUniqueMode(a68, l, SAFE_DEFLEXING);
        Moid orir = MoidsMisc.determineUniqueMode(a68, r, SAFE_DEFLEXING);
        Moid lhs = MoidsMisc.deprocCompletely(a68, oril);
        Moid rhs = MoidsMisc.deprocCompletely(a68, orir);
        if (ifModeIsWell(a68, lhs) && lhs != a68.modes.HIP && lhs.attribute != REF_SYMBOL) {
            Fmt m = Fmt.moid(a68, oril);
            Fmt a = Fmt.attr(ln.sub.attribute);
            a68.diagnostics.error(ln, "%e %e does not yield a name", m, a);
            lhs = a68.modes.ERROR;
        }
        if (ifModeIsWell(a68, rhs) && rhs != a68.modes.HIP && rhs.attribute != REF_SYMBOL) {
            Fmt m = Fmt.moid(a68, orir);
            Fmt a = Fmt.attr(rn.sub.attribute);
            a68.diagnostics.error(rn, "%e %e does not yield a name", m, a);
            rhs = a68.modes.ERROR;
        }
        if (lhs == a68.modes.HIP && rhs == a68.modes.HIP) {
            a68.diagnostics.error(p, "construct has no unique mode");
        }

        if (MoidsMisc.isCoercible(a68, lhs, rhs, STRONG, SAFE_DEFLEXING)) {
            lhs = rhs;
        } else if (MoidsMisc.isCoercible(a68, rhs, lhs, STRONG, SAFE_DEFLEXING)) {
            rhs = lhs;
        } else {
            MoidsDiagnostics.cannotCoerce(a68, p.next, rhs, lhs, SOFT, SKIP_DEFLEXING, TERTIARY);
            lhs = rhs = a68.modes.ERROR;
        }
        ln.type = lhs;
        rn.type = rhs;
        MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.BOOL, STOP);
    }

    /** Mode check bool functions ANDF and ORF (mode_check_bool_function). */
    private static void modeCheckBoolFunction(A68 a68, Node p, Soid x, Soid y) {
        Soid e = new Soid(), l = new Soid(), r = new Soid();
        Node ln = p, rn = p.next.next;
        MoidsMisc.makeSoid(a68, e, STRONG, a68.modes.BOOL, STOP);
        modeCheckUnit(a68, ln.sub, e, l);
        if (!MoidsMisc.isCoercibleInContext(a68, l, e, SAFE_DEFLEXING)) {
            MoidsDiagnostics.cannotCoerce(a68, ln, l.type, e.type, MEEK, SAFE_DEFLEXING, TERTIARY);
        }
        modeCheckUnit(a68, rn.sub, e, r);
        if (!MoidsMisc.isCoercibleInContext(a68, r, e, SAFE_DEFLEXING)) {
            MoidsDiagnostics.cannotCoerce(a68, rn, r.type, e.type, MEEK, SAFE_DEFLEXING, TERTIARY);
        }
        ln.type = a68.modes.BOOL;
        rn.type = a68.modes.BOOL;
        MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.BOOL, STOP);
    }

    /** Mode check cast (mode_check_cast). */
    private static void modeCheckCast(A68 a68, Node p, Soid x, Soid y) {
        Soid w = new Soid();
        modeCheckDeclarer(a68, p);
        MoidsMisc.makeSoid(a68, w, STRONG, p.type, STOP);
        w.cast = true;
        modeCheckEnclosed(a68, p.next.sub, w, y);
        if (!MoidsMisc.isCoercibleInContext(a68, y, w, SAFE_DEFLEXING)) {
            MoidsDiagnostics.cannotCoerce(a68, p.next, y.type, w.type, STRONG, SAFE_DEFLEXING, ENCLOSED_CLAUSE);
        }
        MoidsMisc.makeSoid(a68, y, x.sort, p.type, STOP);
    }

    /** Mode check assertion (mode_check_assertion). */
    private static void modeCheckAssertion(A68 a68, Node p) {
        Soid w = new Soid(), y = new Soid();
        MoidsMisc.makeSoid(a68, w, STRONG, a68.modes.BOOL, STOP);
        modeCheckEnclosed(a68, p.next.sub, w, y);
        y.sort = w.sort;
        if (!MoidsMisc.isCoercibleInContext(a68, y, w, NO_DEFLEXING)) {
            MoidsDiagnostics.cannotCoerce(a68, p.next, y.type, w.type, MEEK, NO_DEFLEXING, ENCLOSED_CLAUSE);
        }
    }

    /** Mode check argument list (mode_check_argument_list). */
    private static void modeCheckArgumentList(A68 a68, Ref<Soid> r, Node p, Ref<Pack> x, Ref<Pack> v, Ref<Pack> w) {
        for (; p != null; p = p.next) {
            if (p.is(GENERIC_ARGUMENT_LIST)) {
                p.attribute = ARGUMENT_LIST;
            }

            if (p.is(ARGUMENT_LIST)) {
                modeCheckArgumentList(a68, r, p.sub, x, v, w);
            } else if (p.is(UNIT)) {
                Soid y = new Soid(), z = new Soid();
                if (x.get() != null) {
                    MoidsMisc.makeSoid(a68, z, STRONG, x.get().type, STOP);
                    ModeBuilder.addModeToPackEnd(a68, v, x.get().type, null, p);
                    x.set(x.get().next);
                } else {
                    MoidsMisc.makeSoid(a68, z, STRONG, null, STOP);
                }
                modeCheckUnit(a68, p, z, y);
                MoidsMisc.addToSoidList(a68, r, p, y);
            } else if (p.is(TRIMMER)) {
                Soid z = new Soid();
                if (p.sub != null) {
                    Fmt a = Fmt.attr(ARGUMENT);
                    a68.diagnostics.error(p, "syntax error detected in %e", a);
                    MoidsMisc.makeSoid(a68, z, STRONG, a68.modes.ERROR, STOP);
                    ModeBuilder.addModeToPackEnd(a68, v, a68.modes.VOID, null, p);
                    // Sic: *x is not checked against NO_PACK here.
                    ModeBuilder.addModeToPackEnd(a68, w, x.get().type, null, p);
                    x.set(x.get().next);
                } else if (x.get() != null) {
                    MoidsMisc.makeSoid(a68, z, STRONG, x.get().type, STOP);
                    ModeBuilder.addModeToPackEnd(a68, v, a68.modes.VOID, null, p);
                    ModeBuilder.addModeToPackEnd(a68, w, x.get().type, null, p);
                    x.set(x.get().next);
                } else {
                    MoidsMisc.makeSoid(a68, z, STRONG, null, STOP);
                }
                MoidsMisc.addToSoidList(a68, r, p, z);
            } else if (p.is(SUB_SYMBOL) && !a68.options().brackets) {
                Fmt a = Fmt.attr(CALL);
                a68.diagnostics.error(p, "syntax error detected in %e", a);
            }
        }
    }

    /** Mode check argument list 2 (mode_check_argument_list_2). */
    private static void modeCheckArgumentList2(A68 a68, Node p, Pack x, Soid y, Ref<Pack> v, Ref<Pack> w) {
        Ref.Box<Soid> topSl = Ref.box(null);
        modeCheckArgumentList(a68, topSl, p.sub, Ref.box(x), v, w);
        MoidsMisc.makeSoid(a68, y, STRONG, MoidsMisc.packSoidsInMoid(a68, topSl.v, STOWED_MODE), STOP);
        MoidsMisc.freeSoidList(a68, topSl.v);
    }

    /** Mode check meek int (mode_check_meek_int). */
    private static void modeCheckMeekInt(A68 a68, Node p) {
        Soid x = new Soid(), y = new Soid();
        MoidsMisc.makeSoid(a68, x, MEEK, a68.modes.INT, STOP);
        modeCheckUnit(a68, p, x, y);
        if (!MoidsMisc.isCoercibleInContext(a68, y, x, SAFE_DEFLEXING)) {
            MoidsDiagnostics.cannotCoerce(a68, p, y.type, x.type, MEEK, SAFE_DEFLEXING, STOP);
        }
    }

    /** Mode check trimmer (mode_check_trimmer). */
    private static void modeCheckTrimmer(A68 a68, Node p) {
        if (p == null) {
            return;
        } else if (p.is(TRIMMER)) {
            modeCheckTrimmer(a68, p.sub);
        } else if (p.is(UNIT)) {
            modeCheckMeekInt(a68, p);
            modeCheckTrimmer(a68, p.next);
        } else {
            modeCheckTrimmer(a68, p.next);
        }
    }

    /** Mode check indexer (mode_check_indexer). */
    private static void modeCheckIndexer(A68 a68, Node p, int[] subs, int[] trims) {
        if (p == null) {
            return;
        } else if (p.is(TRIMMER)) {
            trims[0]++;
            modeCheckTrimmer(a68, p.sub);
        } else if (p.is(UNIT)) {
            subs[0]++;
            modeCheckMeekInt(a68, p);
        } else {
            modeCheckIndexer(a68, p.sub, subs, trims);
            modeCheckIndexer(a68, p.next, subs, trims);
        }
    }

    /** Mode check call (mode_check_call). */
    private static void modeCheckCall(A68 a68, Node p, Moid n, Soid x, Soid y) {
        p.type = n;
        // "partial_locale" is the mode of the locale.
        p.genie.partialLocale = Parser.newMoid(a68);
        p.genie.partialLocale.attribute = PROC_SYMBOL;
        p.genie.partialLocale.pack = null;
        p.genie.partialLocale.sub = n.sub;
        // "partial_proc" is the mode of the resulting proc.
        p.genie.partialProc = Parser.newMoid(a68);
        p.genie.partialProc.attribute = PROC_SYMBOL;
        p.genie.partialProc.pack = null;
        p.genie.partialProc.sub = n.sub;
        // Check arguments and construct modes.
        Soid d = new Soid();
        Moid partialLocale = p.genie.partialLocale;
        Moid partialProc = p.genie.partialProc;
        modeCheckArgumentList2(a68, p.next, n.pack, d, Ref.of(() -> partialLocale.pack, v -> partialLocale.pack = v),
                Ref.of(() -> partialProc.pack, v -> partialProc.pack = v));
        p.genie.partialProc.dim = ModeBuilder.countPackMembers(a68, p.genie.partialProc.pack);
        p.genie.partialLocale.dim = ModeBuilder.countPackMembers(a68, p.genie.partialLocale.pack);
        p.genie.partialProc = ModeBuilder.registerExtraMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), p.genie.partialProc);
        p.genie.partialLocale = ModeBuilder.registerExtraMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), p.genie.partialLocale);
        if (d.type.dim != n.dim) {
            Fmt m = Fmt.moid(a68, n);
            a68.diagnostics.error(p, "incorrect number of arguments for %e", m);
            MoidsMisc.makeSoid(a68, y, x.sort, n.sub, STOP);
            // a68_make_soid (y, SORT (x), M_ERROR, 0);.
        } else {
            if (!MoidsMisc.isCoercible(a68, d.type, n, STRONG, ALIAS_DEFLEXING)) {
                MoidsDiagnostics.cannotCoerce(a68, p, d.type, n, STRONG, ALIAS_DEFLEXING, ARGUMENT);
            }
            if (p.genie.partialProc.dim == 0) {
                MoidsMisc.makeSoid(a68, y, x.sort, n.sub, STOP);
            } else {
                Fmt c = Fmt.construct(p.next);
                a68.diagnostics.warning(p.next, Warn.EXTENSIONS, "%e is an extension", c);
                MoidsMisc.makeSoid(a68, y, x.sort, p.genie.partialProc, STOP);
            }
        }
    }

    /** Mode check slice (mode_check_slice). */
    private static void modeCheckSlice(A68 a68, Node p, Moid ori, Soid x, Soid y) {
        Moid m = MoidsMisc.deprefCompletely(a68, ori), n = ori;
        // WEAK coercion.
        while ((isRef(n) && !MoidsMisc.isRefRow(a68, n)) || (n.attribute == PROC_SYMBOL && n.pack == null)) {
            n = MoidsMisc.deprefOnce(a68, n);
        }

        if (n == null || !(n.deflex().slice != null || MoidsMisc.isRefRow(a68, n))) {
            if (ifModeIsWell(a68, n)) {
                Fmt m1 = Fmt.moid(a68, n);
                Fmt a = Fmt.attr(p.sub.attribute);
                a68.diagnostics.error(p, "%e %e does not yield a row or procedure", m1, a);
            }
            MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
            // Sic: no return; the code below carries on with n.
        }

        p.type = n;
        int dim = 0;
        int[] subs = {0}, trims = {0};
        modeCheckIndexer(a68, p.next.sub, subs, trims);
        boolean isRef;
        if ((isRef = MoidsMisc.isRefRow(a68, n))) {
            dim = n.sub.deflex().dim;
        } else {
            dim = n.deflex().dim;
        }

        if ((subs[0] + trims[0]) != dim) {
            Fmt m1 = Fmt.moid(a68, n);
            a68.diagnostics.error(p, "incorrect number of indexers for %e", m1);
            MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
        } else {
            if (subs[0] > 0 && trims[0] == 0) {
                p.next.annotation = SLICE;
                m = n;
            } else {
                p.next.annotation = TRIMMER;
                m = n;
            }
            while (subs[0] > 0) {
                if (isRef) {
                    m = m.name;
                } else {
                    if (isFlex(m)) {
                        m = m.sub;
                    }
                    m = m.slice;
                }
                Parser.gccAssert(m != null);
                subs[0]--;
            }
            // A trim cannot be but deflexed.
            if (p.next.annotation == TRIMMER && m.trim != null) {
                Parser.gccAssert(m.trim != null);
                MoidsMisc.makeSoid(a68, y, x.sort, m.trim, STOP);
            } else {
                MoidsMisc.makeSoid(a68, y, x.sort, m, STOP);
            }
        }
    }

    /** Mode check specification (mode_check_specification). */
    private static Attribute modeCheckSpecification(A68 a68, Node p, Soid x, Soid y) {
        Soid w = new Soid(), d = new Soid();
        MoidsMisc.makeSoid(a68, w, WEAK, null, STOP);
        modeCheckUnit(a68, p.sub, w, d);
        Moid ori = MoidsMisc.determineUniqueMode(a68, d, SAFE_DEFLEXING);
        Moid m = MoidsMisc.deprefCompletely(a68, ori);
        if (m.attribute == PROC_SYMBOL) {
            // Assume CALL.
            modeCheckCall(a68, p, m, x, y);
            return CALL;
        } else if (isRow(m) || isFlex(m)) {
            // Assume SLICE.
            modeCheckSlice(a68, p, ori, x, y);
            return SLICE;
        } else {
            if (m != a68.modes.ERROR) {
                Fmt m1 = Fmt.moid(a68, m);
                a68.diagnostics.error(p, "%e construct must yield a routine or a row value", m1);
            }
            MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
            return PRIMARY;
        }
    }

    /** Mode check selection (mode_check_selection). */
    private static void modeCheckSelection(A68 a68, Node p, Soid x, Soid y) {
        boolean deflex = false;
        Node secondary = p.next.sub;
        Soid w = new Soid(), d = new Soid();
        MoidsMisc.makeSoid(a68, w, WEAK, null, STOP);
        modeCheckUnit(a68, secondary, w, d);
        Moid n, ori;
        n = ori = MoidsMisc.determineUniqueMode(a68, d, SAFE_DEFLEXING);
        Pack t = null, t2 = null;
        boolean coerce = true;
        while (coerce) {
            if (n.attribute == STRUCT_SYMBOL) {
                coerce = false;
                t = n.pack;
            } else if (isRef(n) && (isRow(n.sub) || isFlex(n.sub)) && n.multipleMode != null) {
                coerce = false;
                deflex = true;
                t = n.multipleMode.pack;
            } else if ((isRow(n) || isFlex(n)) && n.multipleMode != null) {
                coerce = false;
                deflex = true;
                t = n.multipleMode.pack;
            } else if (isRef(n) && MoidsMisc.isNameStruct(a68, n)) {
                coerce = false;
                t = n.name.pack;
            } else if (MoidsMisc.isDeprefable(a68, n)) {
                coerce = true;
                n = n.sub;
                t = null;
            } else {
                coerce = false;
                t = null;
            }
        }
        if (t == null) {
            if (ifModeIsWell(a68, d.type)) {
                Fmt m = Fmt.moid(a68, ori);
                Fmt a = Fmt.attr(secondary.attribute);
                a68.diagnostics.error(secondary, "%e %e does not yield a structured value", m, a);
            }
            MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
            return;
        }

        p.next.type = n;
        String fs = p.sub.info.symbol;
        Moid str = n;
        while (isRef(str)) {
            str = str.sub;
        }
        if (isFlex(str)) {
            str = str.sub;
        }
        if (isRow(str)) {
            str = str.sub;
        }
        t2 = str.pack;
        while (t != null && t2 != null) {
            if (t.text == fs || t.text.equals(fs)) {
                Moid ret = t.type;
                if (deflex && ret.trim != null) {
                    ret = ret.trim;
                }
                MoidsMisc.makeSoid(a68, y, x.sort, ret, STOP);
                p.type = ret;
                p.sub.pack = t2;
                return;
            }
            t = t.next;
            t2 = t2.next;
        }
        // Sic: d is set up but not used afterwards.
        MoidsMisc.makeSoid(a68, d, NO_SORT, n, STOP);
        Fmt m = Fmt.moid(a68, str);
        a68.diagnostics.error(p, "%e has no field %qs", m, fs);
        MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.ERROR, STOP);
    }

    /** Mode check format text (mode_check_format_text). */
    private static void modeCheckFormatText(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            modeCheckFormatText(a68, p.sub);
            if (p.is(FORMAT_PATTERN)) {
                Soid x = new Soid(), y = new Soid();
                MoidsMisc.makeSoid(a68, x, STRONG, a68.modes.FORMAT, STOP);
                modeCheckEnclosed(a68, p.sub.next.sub, x, y);
                if (!MoidsMisc.isCoercibleInContext(a68, y, x, SAFE_DEFLEXING)) {
                    MoidsDiagnostics.cannotCoerce(a68, p, y.type, x.type, STRONG, SAFE_DEFLEXING, ENCLOSED_CLAUSE);
                }
            } else if (p.is(GENERAL_PATTERN) && p.sub.next != null) {
                Soid x = new Soid(), y = new Soid();
                MoidsMisc.makeSoid(a68, x, STRONG, a68.modes.ROW_INT, STOP);
                modeCheckEnclosed(a68, p.sub.next.sub, x, y);
                if (!MoidsMisc.isCoercibleInContext(a68, y, x, SAFE_DEFLEXING)) {
                    MoidsDiagnostics.cannotCoerce(a68, p, y.type, x.type, STRONG, SAFE_DEFLEXING, ENCLOSED_CLAUSE);
                }
            } else if (p.is(DYNAMIC_REPLICATOR)) {
                Soid x = new Soid(), y = new Soid();
                MoidsMisc.makeSoid(a68, x, STRONG, a68.modes.INT, STOP);
                modeCheckEnclosed(a68, p.sub.next.sub, x, y);
                if (!MoidsMisc.isCoercibleInContext(a68, y, x, SAFE_DEFLEXING)) {
                    MoidsDiagnostics.cannotCoerce(a68, p, y.type, x.type, STRONG, SAFE_DEFLEXING, ENCLOSED_CLAUSE);
                }
            }
        }
    }

    /** Mode check unit (mode_check_unit). */
    private static void modeCheckUnit(A68 a68, Node p, Soid x, Soid y) {
        if (p == null) {
            return;
        } else if (Parser.isOneOf(a68, p, UNIT, TERTIARY, SECONDARY, PRIMARY)) {
            modeCheckUnit(a68, p.sub, x, y);
        // Ex primary.
        } else if (p.is(SPECIFICATION)) {
            p.attribute = modeCheckSpecification(a68, p.sub, x, y);
            MoidsDiagnostics.warnForVoiding(a68, p, x, y, p.attribute);
        } else if (p.is(CAST)) {
            modeCheckCast(a68, p.sub, x, y);
            MoidsDiagnostics.warnForVoiding(a68, p, x, y, CAST);
        } else if (p.is(DENOTATION)) {
            MoidsMisc.makeSoid(a68, y, x.sort, p.sub.type, STOP);
            MoidsDiagnostics.warnForVoiding(a68, p, x, y, DENOTATION);
        } else if (p.is(IDENTIFIER)) {
            if ((p.tag == null) && (p.type == null)) {
                Attribute att = Taxes.firstTagGlobal(a68, p.symbolTable, p.info.symbol);
                if (att == STOP) {
                    Taxes.addTag(a68, p.symbolTable, IDENTIFIER, p, a68.modes.ERROR, NORMAL_IDENTIFIER.ordinal());
                    a68.diagnostics.error(p, "tag %qs has not been declared properly", p.info.symbol);
                    p.type = a68.modes.ERROR;
                } else {
                    Tag z = Taxes.findTagGlobal(a68, p.symbolTable, att, p.info.symbol);
                    if (att == IDENTIFIER && z != null) {
                        p.type = z.type;
                    } else {
                        Taxes.addTag(a68, p.symbolTable, IDENTIFIER, p, a68.modes.ERROR, NORMAL_IDENTIFIER.ordinal());
                        a68.diagnostics.error(p, "tag %qs has not been declared properly", p.info.symbol);
                        p.type = a68.modes.ERROR;
                    }
                }
            }
            MoidsMisc.makeSoid(a68, y, x.sort, p.type, STOP);
            MoidsDiagnostics.warnForVoiding(a68, p, x, y, IDENTIFIER);
        } else if (p.is(ENCLOSED_CLAUSE)) {
            modeCheckEnclosed(a68, p.sub, x, y);
        } else if (p.is(FORMAT_TEXT)) {
            modeCheckFormatText(a68, p);
            MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.FORMAT, STOP);
            MoidsDiagnostics.warnForVoiding(a68, p, x, y, FORMAT_TEXT);
            // Ex secondary.
        } else if (p.is(GENERATOR)) {
            modeCheckDeclarer(a68, p.sub);
            MoidsMisc.makeSoid(a68, y, x.sort, p.sub.type, STOP);
            MoidsDiagnostics.warnForVoiding(a68, p, x, y, GENERATOR);
        } else if (p.is(SELECTION)) {
            modeCheckSelection(a68, p.sub, x, y);
            MoidsDiagnostics.warnForVoiding(a68, p, x, y, SELECTION);
            // Ex tertiary.
        } else if (p.is(NIHIL)) {
            MoidsMisc.makeSoid(a68, y, STRONG, a68.modes.HIP, STOP);
        } else if (p.is(FORMULA)) {
            modeCheckFormula(a68, p, x, y);
            if (!isRef(y.type)) {
                MoidsDiagnostics.warnForVoiding(a68, p, x, y, FORMULA);
            }
        } else if (Parser.isOneOf(a68, p, JUMP, SKIP)) {
            if (x.sort != STRONG) {
                Fmt c = Fmt.construct(p);
                Fmt s = Fmt.sort(x.sort);
                a68.diagnostics.warning(p, null, "%e should not be in %e context", c, s);
            }
            // a68_make_soid (y, STRONG, M_HIP, 0);
            MoidsMisc.makeSoid(a68, y, x.sort, a68.modes.HIP, STOP);
        } else if (p.is(ASSIGNATION)) {
            modeCheckAssignation(a68, p.sub, x, y);
        } else if (p.is(IDENTITY_RELATION)) {
            modeCheckIdentityRelation(a68, p.sub, x, y);
            MoidsDiagnostics.warnForVoiding(a68, p, x, y, IDENTITY_RELATION);
        } else if (p.is(ROUTINE_TEXT)) {
            modeCheckRoutineText(a68, p.sub, y);
            MoidsMisc.makeSoid(a68, y, x.sort, p.type, STOP);
            MoidsDiagnostics.warnForVoiding(a68, p, x, y, ROUTINE_TEXT);
        } else if (p.is(ASSERTION)) {
            modeCheckAssertion(a68, p.sub);
            MoidsMisc.makeSoid(a68, y, STRONG, a68.modes.VOID, STOP);
        } else if (p.is(AND_FUNCTION)) {
            modeCheckBoolFunction(a68, p.sub, x, y);
            MoidsDiagnostics.warnForVoiding(a68, p, x, y, AND_FUNCTION);
        } else if (p.is(OR_FUNCTION)) {
            modeCheckBoolFunction(a68, p.sub, x, y);
            MoidsDiagnostics.warnForVoiding(a68, p, x, y, OR_FUNCTION);
        } else if (p.is(FORMAL_HOLE)) {
            Node tertiary = null;

            for (Node q = p.sub; q != null; q = q.next) {
                if (q.is(TERTIARY)) {
                    tertiary = q;
                    break;
                }
            }

            Node str = tertiary;
            while (str != null && !str.is(ROW_CHAR_DENOTATION)) {
                str = str.sub;
            }
            Parser.gccAssert(str.is(ROW_CHAR_DENOTATION));

            if (x.sort == STRONG && isJavaHole(p)) {
                // a68j: nest Java "..." (-fnest-java). The mode is checked
                // against the Java method or field by the backend.
                if (!MoidsMisc.isJavaMode(a68, x.type)) {
                    Fmt m = Fmt.moid(a68, x.type);
                    a68.diagnostics.error(p, "formal hole cannot be of mode %e", m);
                    MoidsMisc.makeSoid(a68, y, STRONG, a68.modes.ERROR, STOP);
                } else {
                    Soid z = new Soid();
                    modeCheckUnit(a68, tertiary, x, z);
                    MoidsMisc.makeSoid(a68, y, x.sort, x.type, STOP);
                }
            } else if (x.sort != STRONG) {
                // A formal hole should appear in a strong context, and its mode is
                // the goal mode of the context.
                a68.diagnostics.error(p, "formal hole should be in a strong context");
                MoidsMisc.makeSoid(a68, y, STRONG, a68.modes.ERROR, STOP);
            } else if (!MoidsMisc.isCMode(a68, x.type)) {
                // Additionally, the mode of the formal hole should be amenable to be
                // somehow "translated" to C semantics.
                Fmt m = Fmt.moid(a68, x.type);
                a68.diagnostics.error(p, "formal hole cannot be of mode %e", m);
                MoidsMisc.makeSoid(a68, y, STRONG, a68.modes.ERROR, STOP);
            } else if (!str.info.symbol.isEmpty() && str.info.symbol.charAt(0) == '&' && !isRef(x.type)) {
                // A C formal whole whose string starts with & requires
                // a ref mode.
                a68.diagnostics.error(p, "formal hole should be a name (ref to a mode)");
                MoidsMisc.makeSoid(a68, y, STRONG, a68.modes.ERROR, STOP);
            } else {
                Soid z = new Soid();
                modeCheckUnit(a68, tertiary, x, z);
                MoidsMisc.makeSoid(a68, y, x.sort, x.type, STOP);
                MoidsDiagnostics.warnForVoiding(a68, p, x, y, FORMAL_HOLE);
            }
        }

        p.type = y.type;
    }

    /** Whether the formal hole P names the language Java (a68j, -fnest-java). */
    public static boolean isJavaHole(Node p) {
        for (Node q = p.sub; q != null; q = q.next) {
            if (q.is(LANGUAGE_INDICANT)) {
                return Extractor.isJavaLanguage(q.info.symbol);
            }
        }
        return false;
    }

    /** Mode check a module text (mode_check_module_text). */
    private static void modeCheckModuleText(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(DEF_PART) || p.is(POSTLUDE_PART)) {
                // XXX unde def is an enquiry clause
                Ref.Box<Soid> z = Ref.box(null);
                Soid ix = new Soid();
                MoidsMisc.makeSoid(a68, ix, STRONG, a68.modes.VOID, STOP);
                modeCheckSerial(a68, z, p.sub.next, ix, true);
                MoidsMisc.freeSoidList(a68, z.v);
            }
        }
    }

    /** Mode check a module declaration (mode_check_module_declaration). */
    private static void modeCheckModuleDeclaration(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(MODULE_TEXT)) {
                modeCheckModuleText(a68, p.sub);
            } else {
                modeCheckModuleDeclaration(a68, p.sub);
            }
        }
    }
}
