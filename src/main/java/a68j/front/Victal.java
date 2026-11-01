// Java port of ga68's a68-parser-victal.cc: syntax check for formal, actual and virtual declarers.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Syntax check for formal, actual and virtual declarers
 * (a68-parser-victal.cc).
 *
 * <p>The C {@code int x} parameters hold one of the attributes
 * ACTUAL_DECLARER_MARK, FORMAL_DECLARER_MARK or VIRTUAL_DECLARER_MARK and are
 * therefore {@link Attribute}s here; {@code bool *z} is {@code boolean[1]}.
 */
public final class Victal {
    private Victal() {}

    // Forward declaration of victal_check_declarer: not needed in Java.

    /** victal_check_generator: check generator. */
    private static void victalCheckGenerator(A68 a68, Node p) {
        if (!victalCheckDeclarer(a68, p.next, ACTUAL_DECLARER_MARK)) {
            a68.diagnostics.error(p, "actual declarer expected");
        }
    }

    /** victal_check_formal_pack: check formal pack. */
    private static void victalCheckFormalPack(A68 a68, Node p, Attribute x, boolean[] z) {
        if (p != null) {
            if (p.is(FORMAL_DECLARERS)) {
                victalCheckFormalPack(a68, p.sub, x, z);
            } else if (Parser.isOneOf(a68, p, OPEN_SYMBOL, COMMA_SYMBOL)) {
                victalCheckFormalPack(a68, p.next, x, z);
            } else if (p.is(FORMAL_DECLARERS_LIST)) {
                victalCheckFormalPack(a68, p.next, x, z);
                victalCheckFormalPack(a68, p.sub, x, z);
            } else if (p.is(DECLARER)) {
                victalCheckFormalPack(a68, p.next, x, z);
                z[0] &= victalCheckDeclarer(a68, p.sub, x);
            }
        }
    }

    /** victal_check_operator_dec: check operator declaration. */
    private static void victalCheckOperatorDec(A68 a68, Node p) {
        if (p.next.is(FORMAL_DECLARERS)) {
            boolean[] z = {true};
            victalCheckFormalPack(a68, p.next, FORMAL_DECLARER_MARK, z);
            if (!z[0]) {
                a68.diagnostics.error(p, "formal declarers expected");
            }
            p = p.next;
        }
        if (!victalCheckDeclarer(a68, p.next, FORMAL_DECLARER_MARK)) {
            a68.diagnostics.error(p, "formal declarer expected");
        }
    }

    /** victal_check_mode_dec: check mode declaration. */
    private static void victalCheckModeDec(A68 a68, Node p) {
        if (p != null) {
            if (p.is(MODE_DECLARATION)) {
                victalCheckModeDec(a68, p.sub);
                victalCheckModeDec(a68, p.next);
            } else if (p.is(PUBLIC_SYMBOL)) {
                victalCheckModeDec(a68, p.next);
            } else if (Parser.isOneOf(a68, p, MODE_SYMBOL, DEFINING_INDICANT)
                    || Parser.isOneOf(a68, p, EQUALS_SYMBOL, COMMA_SYMBOL)) {
                victalCheckModeDec(a68, p.next);
            } else if (p.is(DECLARER)) {
                if (!victalCheckDeclarer(a68, p, ACTUAL_DECLARER_MARK)) {
                    a68.diagnostics.error(p, "actual declarer expected");
                }
            }
        }
    }

    /** victal_check_variable_dec: check variable declaration. */
    private static void victalCheckVariableDec(A68 a68, Node p) {
        if (p != null) {
            if (p.is(VARIABLE_DECLARATION)) {
                victalCheckVariableDec(a68, p.sub);
                victalCheckVariableDec(a68, p.next);
            } else {
                if (p.is(PUBLIC_SYMBOL)) {
                    p = p.next;
                }

                if (p.is(QUALIFIER)) {
                    p = p.next;
                }

                if (Parser.isOneOf(a68, p, DEFINING_IDENTIFIER, ASSIGN_SYMBOL)
                        || p.is(COMMA_SYMBOL)) {
                    victalCheckVariableDec(a68, p.next);
                } else if (p.is(UNIT)) {
                    victalChecker(a68, p.sub);
                } else if (p.is(DECLARER)) {
                    if (!victalCheckDeclarer(a68, p, ACTUAL_DECLARER_MARK)) {
                        a68.diagnostics.error(p, "actual declarer expected");
                    }
                    victalCheckVariableDec(a68, p.next);
                }
            }
        }
    }

    /** victal_check_identity_dec: check identity declaration. */
    private static void victalCheckIdentityDec(A68 a68, Node p) {
        if (p != null) {
            if (p.is(IDENTITY_DECLARATION)) {
                victalCheckIdentityDec(a68, p.sub);
                victalCheckIdentityDec(a68, p.next);
            } else if (Parser.isOneOf(a68, p, PUBLIC_SYMBOL, DEFINING_IDENTIFIER, EQUALS_SYMBOL, COMMA_SYMBOL)) {
                victalCheckIdentityDec(a68, p.next);
            } else if (p.is(UNIT)) {
                victalChecker(a68, p.sub);
            } else if (p.is(DECLARER)) {
                if (!victalCheckDeclarer(a68, p, FORMAL_DECLARER_MARK)) {
                    a68.diagnostics.error(p, "formal declarer expected");
                }
                victalCheckIdentityDec(a68, p.next);
            }
        }
    }

    /** victal_check_routine_pack: check routine pack. */
    private static void victalCheckRoutinePack(A68 a68, Node p, Attribute x, boolean[] z) {
        if (p != null) {
            if (p.is(PARAMETER_PACK)) {
                victalCheckRoutinePack(a68, p.sub, x, z);
            } else if (Parser.isOneOf(a68, p, OPEN_SYMBOL, COMMA_SYMBOL)) {
                victalCheckRoutinePack(a68, p.next, x, z);
            } else if (Parser.isOneOf(a68, p, PARAMETER_LIST, PARAMETER)) {
                victalCheckRoutinePack(a68, p.next, x, z);
                victalCheckRoutinePack(a68, p.sub, x, z);
            } else if (p.is(DECLARER)) {
                z[0] &= victalCheckDeclarer(a68, p.sub, x);
            }
        }
    }

    /** victal_check_routine_text: check routine text. */
    private static void victalCheckRoutineText(A68 a68, Node p) {
        if (p.is(PARAMETER_PACK)) {
            boolean[] z = {true};
            victalCheckRoutinePack(a68, p, FORMAL_DECLARER_MARK, z);
            if (!z[0]) {
                a68.diagnostics.error(p, "formal declarers expected");
            }
            p = p.next;
        }
        if (!victalCheckDeclarer(a68, p, FORMAL_DECLARER_MARK)) {
            a68.diagnostics.error(p, "formal declarer expected");
        }
        victalChecker(a68, p.next);
    }

    /** victal_check_structure_pack: check structure pack. */
    private static void victalCheckStructurePack(A68 a68, Node p, Attribute x, boolean[] z) {
        if (p != null) {
            if (p.is(STRUCTURE_PACK)) {
                victalCheckStructurePack(a68, p.sub, x, z);
            } else if (Parser.isOneOf(a68, p, OPEN_SYMBOL, COMMA_SYMBOL)) {
                victalCheckStructurePack(a68, p.next, x, z);
            } else if (Parser.isOneOf(a68, p, STRUCTURED_FIELD_LIST, STRUCTURED_FIELD)) {
                victalCheckStructurePack(a68, p.next, x, z);
                victalCheckStructurePack(a68, p.sub, x, z);
            } else if (p.is(DECLARER)) {
                z[0] &= victalCheckDeclarer(a68, p.sub, x);
            }
        }
    }

    /** victal_check_union_pack: check union pack. */
    private static void victalCheckUnionPack(A68 a68, Node p, Attribute x, boolean[] z) {
        if (p != null) {
            if (p.is(UNION_PACK)) {
                victalCheckUnionPack(a68, p.sub, x, z);
            } else if (Parser.isOneOf(a68, p, OPEN_SYMBOL, COMMA_SYMBOL, VOID_SYMBOL)) {
                victalCheckUnionPack(a68, p.next, x, z);
            } else if (p.is(UNION_DECLARER_LIST)) {
                victalCheckUnionPack(a68, p.next, x, z);
                victalCheckUnionPack(a68, p.sub, x, z);
            } else if (p.is(DECLARER)) {
                victalCheckUnionPack(a68, p.next, x, z);
                z[0] &= victalCheckDeclarer(a68, p.sub, FORMAL_DECLARER_MARK);
            }
        }
    }

    /** victal_check_declarer: check declarer. */
    private static boolean victalCheckDeclarer(A68 a68, Node p, Attribute x) {
        if (p == null) {
            return false;
        } else if (p.is(DECLARER)) {
            return victalCheckDeclarer(a68, p.sub, x);
        } else if (Parser.isOneOf(a68, p, LONGETY, SHORTETY, FIXETY)) {
            return true;
        } else if (Parser.isOneOf(a68, p, VOID_SYMBOL, INDICANT, STANDARD)) {
            return true;
        } else if (p.is(REF_SYMBOL)) {
            return victalCheckDeclarer(a68, p.next, VIRTUAL_DECLARER_MARK);
        } else if (p.is(FLEX_SYMBOL)) {
            return victalCheckDeclarer(a68, p.next, x);
        } else if (p.is(BOUNDS)) {
            victalChecker(a68, p.sub);
            if (x == FORMAL_DECLARER_MARK) {
                a68.diagnostics.error(p, "formal bounds expected");
                victalCheckDeclarer(a68, p.next, x);
                return true;
            } else if (x == VIRTUAL_DECLARER_MARK) {
                a68.diagnostics.error(p, "virtual bounds expected");
                victalCheckDeclarer(a68, p.next, x);
                return true;
            } else {
                return victalCheckDeclarer(a68, p.next, x);
            }
        } else if (p.is(FORMAL_BOUNDS)) {
            victalChecker(a68, p.sub);
            if (x == ACTUAL_DECLARER_MARK) {
                a68.diagnostics.error(p, "actual bounds expected");
                victalCheckDeclarer(a68, p.next, x);
                return true;
            } else {
                return victalCheckDeclarer(a68, p.next, x);
            }
        } else if (p.is(STRUCT_SYMBOL)) {
            boolean[] z = {true};
            victalCheckStructurePack(a68, p.next, x, z);
            return z[0];
        } else if (p.is(UNION_SYMBOL)) {
            boolean[] z = {true};
            victalCheckUnionPack(a68, p.next, FORMAL_DECLARER_MARK, z);
            if (!z[0]) {
                a68.diagnostics.error(p, "formal declarer pack expected");
            }
            return true;
        } else if (p.is(PROC_SYMBOL)) {
            if (p.next.is(FORMAL_DECLARERS)) {
                boolean[] z = {true};
                victalCheckFormalPack(a68, p.next, FORMAL_DECLARER_MARK, z);
                if (!z[0]) {
                    a68.diagnostics.error(p, "formal declarer expected");
                }
                p = p.next;
            }
            if (!victalCheckDeclarer(a68, p.next, FORMAL_DECLARER_MARK)) {
                a68.diagnostics.error(p, "formal declarer expected");
            }
            return true;
        } else {
            return false;
        }
    }

    /** victal_check_cast: check cast. */
    private static void victalCheckCast(A68 a68, Node p) {
        if (!victalCheckDeclarer(a68, p, FORMAL_DECLARER_MARK)) {
            a68.diagnostics.error(p, "formal declarer expected");
            victalChecker(a68, p.next);
        }
    }

    /** a68_victal_checker: driver for checking VICTALITY of declarers. */
    public static void victalChecker(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(MODE_DECLARATION)) {
                victalCheckModeDec(a68, p.sub);
            } else if (p.is(VARIABLE_DECLARATION)) {
                victalCheckVariableDec(a68, p.sub);
            } else if (p.is(IDENTITY_DECLARATION)) {
                victalCheckIdentityDec(a68, p.sub);
            } else if (p.is(GENERATOR)) {
                victalCheckGenerator(a68, p.sub);
            } else if (p.is(ROUTINE_TEXT)) {
                victalCheckRoutineText(a68, p.sub);
            } else if (p.is(OPERATOR_PLAN)) {
                victalCheckOperatorDec(a68, p.sub);
            } else if (p.is(CAST)) {
                victalCheckCast(a68, p.sub);
            } else {
                victalChecker(a68, p.sub);
            }
        }
    }
}
