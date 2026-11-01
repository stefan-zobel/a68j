// Java port of ga68's a68-parser-modes.cc: mode table management.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Mode collection, equivalencing and derived modes (a68-parser-modes.cc).
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>{@code MOID_T **} and {@code PACK_T **} parameters are {@link Ref}s; the
 * mode and pack lists are linked lists exactly as in C, and modes are compared
 * by reference.</li>
 * <li>Symbol texts ({@code NSYMBOL}, {@code TEXT}) are interned token texts,
 * so the C pointer comparisons on them are kept as reference comparisons
 * ({@code ==}) on {@link String}s.</li>
 * </ul>
 * Quirks of the original are preserved on purpose and marked with "Sic".
 */
public final class ModeBuilder {
    private ModeBuilder() {}

    /** Deflexing strategy NO_DEFLEXING (a68.h). */
    private static final int NO_DEFLEXING = 1;

    /*
     * Mode collection, equivalencing and derived modes.
     */

    // Few forward references: not needed in Java (get_mode_from_declarer).

    /*
     * Mode service routines.
     */

    /** Count bounds in declarer in tree (count_bounds). */
    private static int countBounds(A68 a68, Node p) {
        if (p == null) {
            return 0;
        } else {
            if (p.is(BOUND)) {
                return 1 + countBounds(a68, p.next);
            } else {
                return countBounds(a68, p.next) + countBounds(a68, p.sub);
            }
        }
    }

    /**
     * Count number of SHORTs or LONGs (count_sizety).
     *
     * <p>Non-cummulative sizes use fixed codes: WORD - 100
     */
    private static int countSizety(A68 a68, Node p) {
        if (p == null) {
            return 0;
        } else if (p.is(LONGETY)) {
            return countSizety(a68, p.sub) + countSizety(a68, p.next);
        } else if (p.is(SHORTETY)) {
            return countSizety(a68, p.sub) + countSizety(a68, p.next);
        } else if (p.is(FIXETY)) {
            return countSizety(a68, p.sub);
        } else if (p.is(LONG_SYMBOL)) {
            return 1;
        } else if (p.is(SHORT_SYMBOL)) {
            return -1;
        } else if (p.is(WORD_SYMBOL)) {
            return 100;
        } else {
            return 0;
        }
    }

    /** Count moids in a pack (a68_count_pack_members). */
    public static int countPackMembers(A68 a68, Pack u) {
        int k = 0;

        for (; u != null; u = u.next) {
            k++;
        }
        return k;
    }

    /** Replace a mode by its equivalent mode (a68_resolve_equivalent). */
    public static void resolveEquivalent(A68 a68, Ref<Moid> m) {
        while (m.get() != null
                && m.get().equivalentMode != null
                && m.get() != m.get().equivalentMode) {
            m.set(m.get().equivalentMode);
        }
    }

    /** Reset moid (reset_moid_tree). */
    private static void resetMoidTree(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            p.type = null;
            resetMoidTree(a68, p.sub);
        }
    }

    /** Renumber moids (a68_renumber_moids). */
    public static void renumberMoids(A68 a68, Moid p, int n) {
        if (p != null) {
            p.number = n;
            renumberMoids(a68, p.next, n + 1);
        }
    }

    /**
     * See whether a mode equivalent to the mode M exists in the global mode
     * table, and return it. Return NO_MOID if no equivalent mode is found
     * (a68_search_equivalent_mode).
     */
    public static Moid searchEquivalentMode(A68 a68, Moid m) {
        for (Moid head = a68.job.topMoid; head != null; head = head.next) {
            if (MoidsEquivalence.proveMoidEquivalence(a68, head, m)) {
                return head;
            }
        }

        return null;
    }

    /** Register mode in the global mode table, if mode is unique (a68_register_extra_mode). */
    public static Moid registerExtraMode(A68 a68, Ref<Moid> z, Moid u) {
        // If we already know this mode, return the existing entry; otherwise link it
        // in.
        for (Moid head = a68.job.topMoid; head != null; head = head.next) {
            if (MoidsEquivalence.proveMoidEquivalence(a68, head, u)) {
                return head;
            }
        }

        // Link to chain and exit.
        u.number = a68.modeCount++;
        u.next = z.get();
        z.set(u);
        return u;
    }

    /** Create a new mode (a68_create_mode). */
    public static Moid createMode(A68 a68, Attribute att, int dim, Node node, Moid sub, Pack pack) {
        Moid newMode = Parser.newMoid(a68);

        if (sub == null) {
            if (att == REF_SYMBOL
                    || att == FLEX_SYMBOL
                    || att == ROW_SYMBOL) {
                throw Parser.gccUnreachable();
            }
        }

        newMode.use = false;
        newMode.attribute = att;
        newMode.dim = dim;
        newMode.node = node;
        newMode.hasRows = (att == ROW_SYMBOL);
        newMode.hasRefs = (att == REF_SYMBOL);
        newMode.sub = sub;
        newMode.pack = pack;
        newMode.next = null;
        newMode.equivalentMode = null;
        newMode.slice = null;
        newMode.deflexedMode = null;
        newMode.name = null;
        newMode.multipleMode = null;
        newMode.rowed = null;

        return newMode;
    }

    /** Create a new mode and add it to chain Z (a68_add_mode). */
    public static Moid addMode(A68 a68, Ref<Moid> z, Attribute att, int dim, Node node, Moid sub, Pack pack) {
        Moid newMode = createMode(a68, att, dim, node, sub, pack);
        return registerExtraMode(a68, z, newMode);
    }

    /** Contract a UNION (a68_contract_union). */
    public static void contractUnion(A68 a68, Moid u) {
        for (Pack s = u.pack; s != null; s = s.next) {
            Pack t = s;

            while (t != null) {
                if (t.next != null && t.next.type == s.type) {
                    // Sic: self-assignment in the original.
                    t.type = t.type;
                    t.next = t.next.next;
                } else {
                    t = t.next;
                }
            }
        }
    }

    /** Absorb UNION pack (a68_absorb_union_pack). */
    public static Pack absorbUnionPack(A68 a68, Pack u) {
        Ref.Box<Pack> z;
        boolean siga;

        do {
            z = Ref.box(null);
            siga = false;
            for (Pack t = u; t != null; t = t.next) {
                if (t.type.attribute == UNION_SYMBOL) {
                    siga = true;
                    for (Pack s = t.type.pack; s != null; s = s.next) {
                        addModeToPack(a68, z, s.type, null, s.node);
                    }
                } else {
                    addModeToPack(a68, z, t.type, null, t.node);
                }
            }
            u = z.v;
        } while (siga);
        return z.v;
    }

    /** Add row and its slices to chain, recursively (add_row). */
    private static Moid addRow(A68 a68, Ref<Moid> p, int dim, Moid sub, Node n, boolean derivate) {
        Moid q = addMode(a68, p, ROW_SYMBOL, dim, n, sub, null);

        q.derivate |= derivate;
        if (dim > 1) {
            q.slice = addRow(a68, Ref.of(() -> q.next, v -> q.next = v), dim - 1, sub, n, derivate);
        } else {
            q.slice = sub;
        }
        return q;
    }

    /** Add a moid to a pack, maybe with a (field) name (a68_add_mode_to_pack). */
    public static void addModeToPack(A68 a68, Ref<Pack> p, Moid m, String text, Node node) {
        Pack z = Parser.newPack(a68);

        z.type = m;
        z.text = text;
        z.node = node;
        z.next = p.get();
        z.previous = null;
        if (z.next != null) {
            z.next.previous = z;
        }

        // Link in chain.
        p.set(z);
    }

    /** Add a moid to a pack, maybe with a (field) name (a68_add_mode_to_pack_end). */
    public static void addModeToPackEnd(A68 a68, Ref<Pack> p, Moid m, String text, Node node) {
        Pack z = Parser.newPack(a68);

        z.type = m;
        z.text = text;
        z.node = node;
        z.next = null;
        // Sic: dead code in the original, NEXT (z) was just set to NO_PACK.
        if (z.next != null) {
            z.next.previous = z;
        }

        // Link in chain.
        while (p.get() != null) {
            Pack last = p.get();
            p = Ref.of(() -> last.next, v -> last.next = v);
        }
        // Sic: *p is NO_PACK here, so PREVIOUS (z) is always NO_PACK.
        z.previous = p.get();
        p.set(z);
    }

    /** Absorb UNION members (absorb_unions). */
    private static void absorbUnions(A68 a68, Moid m) {
        // UNION (A, UNION (B, C)) = UNION (A, B, C) or
        // UNION (A, UNION (A, B)) = UNION (A, B).
        for (; m != null; m = m.next) {
            if (m.attribute == UNION_SYMBOL) {
                m.pack = absorbUnionPack(a68, m.pack);
            }
        }
    }

    /** Contract UNIONs (contract_unions). */
    private static void contractUnions(A68 a68, Moid m) {
        // UNION (A, B, A) -> UNION (A, B).
        for (; m != null; m = m.next) {
            if (m.attribute == UNION_SYMBOL && m.equivalentMode == null) {
                contractUnion(a68, m);
            }
        }
    }

    /*
     * Routines to collect MOIDs from the program text.
     */

    /** Search standard mode in standard environ (search_standard_mode). */
    private static Moid searchStandardMode(A68 a68, int sizety, Node indicant) {
        // Search standard mode.
        for (Moid p = a68.job.topMoid; p != null; p = p.next) {
            // NSYMBOLs are interned token texts: pointer comparison as in C.
            if (p.attribute == STANDARD
                    && p.dim == sizety
                    && p.node.info.symbol == indicant.info.symbol) {
                return p;
            }
        }

        if (sizety >= 100) {
            // Modes with sizety > 100 are non-cummulative.
            return null;
        }

        if (sizety < 0) {
            return searchStandardMode(a68, sizety + 1, indicant);
        } else if (sizety > 0) {
            return searchStandardMode(a68, sizety - 1, indicant);
        } else {
            return null;
        }
    }

    /** Collect mode from STRUCT field (get_mode_from_struct_field). */
    private static void getModeFromStructField(A68 a68, Node p, Ref<Pack> u) {
        if (p != null) {
            if (p.is(IDENTIFIER)) {
                p.attribute = FIELD_IDENTIFIER;
                addModeToPack(a68, u, null, p.info.symbol, p);
            } else if (p.is(DECLARER)) {
                Moid newOne = getModeFromDeclarer(a68, p);

                getModeFromStructField(a68, p.next, u);
                for (Pack t = u.get(); t != null && t.type == null; t = t.next) {
                    t.type = newOne;
                    t.node.type = newOne;
                }
            } else {
                getModeFromStructField(a68, p.next, u);
                getModeFromStructField(a68, p.sub, u);
            }
        }
    }

    /** Collect MODE from formal pack (get_mode_from_formal_pack). */
    private static void getModeFromFormalPack(A68 a68, Node p, Ref<Pack> u) {
        if (p != null) {
            if (p.is(DECLARER)) {
                getModeFromFormalPack(a68, p.next, u);
                Moid z = getModeFromDeclarer(a68, p);
                addModeToPack(a68, u, z, null, p);
            } else {
                getModeFromFormalPack(a68, p.next, u);
                getModeFromFormalPack(a68, p.sub, u);
            }
        }
    }

    /** Collect MODE or VOID from formal UNION pack (get_mode_from_union_pack). */
    private static void getModeFromUnionPack(A68 a68, Node p, Ref<Pack> u) {
        if (p != null) {
            if (p.is(DECLARER) || p.is(VOID_SYMBOL)) {
                getModeFromUnionPack(a68, p.next, u);
                Moid z = getModeFromDeclarer(a68, p);
                addModeToPack(a68, u, z, null, p);
            } else {
                getModeFromUnionPack(a68, p.next, u);
                getModeFromUnionPack(a68, p.sub, u);
            }
        }
    }

    /** Collect mode from PROC, OP pack (get_mode_from_routine_pack). */
    private static void getModeFromRoutinePack(A68 a68, Node p, Ref<Pack> u) {
        if (p != null) {
            if (p.is(IDENTIFIER)) {
                addModeToPack(a68, u, null, null, p);
            } else if (p.is(DECLARER)) {
                Moid z = getModeFromDeclarer(a68, p);

                for (Pack t = u.get(); t != null && t.type == null; t = t.next) {
                    t.type = z;
                    t.node.type = z;
                }
                addModeToPack(a68, u, z, null, p);
            } else {
                getModeFromRoutinePack(a68, p.next, u);
                getModeFromRoutinePack(a68, p.sub, u);
            }
        }
    }

    /** Collect MODE from DECLARER (get_mode_from_declarer). */
    private static Moid getModeFromDeclarer(A68 a68, Node p) {
        if (p == null) {
            return null;
        } else {
            if (p.is(DECLARER)) {
                if (p.type != null) {
                    return p.type;
                } else {
                    return p.type = getModeFromDeclarer(a68, p.sub);
                }
            } else {
                if (p.is(VOID_SYMBOL)) {
                    p.type = a68.modes.VOID;
                    return p.type;
                } else if (p.is(LONGETY)) {
                    if (Parser.whether(a68, p, LONGETY, INDICANT)) {
                        int k = countSizety(a68, p.sub);
                        p.type = searchStandardMode(a68, k, p.next);
                        return p.type;
                    } else {
                        return null;
                    }
                } else if (p.is(SHORTETY)) {
                    if (Parser.whether(a68, p, SHORTETY, INDICANT)) {
                        int k = countSizety(a68, p.sub);
                        p.type = searchStandardMode(a68, k, p.next);
                        return p.type;
                    } else {
                        return null;
                    }
                } else if (p.is(FIXETY)) {
                    if (Parser.whether(a68, p, FIXETY, INDICANT)) {
                        int k = countSizety(a68, p.sub);
                        p.type = searchStandardMode(a68, k, p.next);
                        return p.type;
                    } else {
                        return null;
                    }
                } else if (p.is(INDICANT)) {
                    Moid q = searchStandardMode(a68, 0, p);
                    if (q != null) {
                        p.type = q;
                    } else {
                        // Position of definition tells indicants apart.
                        Tag y = Taxes.findTagGlobal(a68, p.symbolTable, INDICANT, p.info.symbol);
                        if (y == null) {
                            a68.diagnostics.error(p, "tag %qs has not been declared properly", p.info.symbol);
                        } else {
                            p.type = addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                                    INDICANT, 0, y.node, null, null);
                        }
                    }
                    return p.type;
                } else if (p.is(REF_SYMBOL)) {
                    Moid newOne = getModeFromDeclarer(a68, p.next);
                    p.type = addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                            REF_SYMBOL, 0, p, newOne, null);
                    return p.type;
                } else if (p.is(FLEX_SYMBOL)) {
                    Moid newOne = getModeFromDeclarer(a68, p.next);
                    p.type = addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                            FLEX_SYMBOL, 0, p, newOne, null);
                    p.type.slice = newOne.slice;
                    return p.type;
                } else if (p.is(FORMAL_BOUNDS)) {
                    Moid newOne = getModeFromDeclarer(a68, p.next);
                    p.type = addRow(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                            1 + Parser.countFormalBounds(a68, p.sub), newOne, p, false);
                    return p.type;
                } else if (p.is(BOUNDS)) {
                    Moid newOne = getModeFromDeclarer(a68, p.next);
                    p.type = addRow(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                            countBounds(a68, p.sub), newOne, p, false);
                    return p.type;
                } else if (p.is(STRUCT_SYMBOL)) {
                    Ref.Box<Pack> u = Ref.box(null);
                    getModeFromStructField(a68, p.next, u);
                    p.type = addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                            STRUCT_SYMBOL, countPackMembers(a68, u.v), p, null, u.v);
                    return p.type;
                } else if (p.is(UNION_SYMBOL)) {
                    Ref.Box<Pack> u = Ref.box(null);
                    getModeFromUnionPack(a68, p.next, u);
                    p.type = addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                            UNION_SYMBOL, countPackMembers(a68, u.v), p, null, u.v);
                    return p.type;
                } else if (p.is(PROC_SYMBOL)) {
                    Node save = p;
                    Ref.Box<Pack> u = Ref.box(null);
                    if (p.next.is(FORMAL_DECLARERS)) {
                        getModeFromFormalPack(a68, p.next.sub, u);
                        p = p.next;
                    }
                    Moid newOne = getModeFromDeclarer(a68, p.next);
                    p.type =
                            addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                                    PROC_SYMBOL, countPackMembers(a68, u.v), save, newOne, u.v);
                    save.type = p.type;
                    return p.type;
                } else {
                    return null;
                }
            }
        }
    }

    /** Collect MODEs from a routine-text header (get_mode_from_routine_text). */
    private static Moid getModeFromRoutineText(A68 a68, Node p) {
        Ref.Box<Pack> u = Ref.box(null);
        Node q = p;

        if (p.is(PARAMETER_PACK)) {
            getModeFromRoutinePack(a68, p.sub, u);
            p = p.next;
        }
        Moid n = getModeFromDeclarer(a68, p);
        return addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                PROC_SYMBOL, countPackMembers(a68, u.v), q, n, u.v);
    }

    /** Collect modes from operator-plan (get_mode_from_operator). */
    private static Moid getModeFromOperator(A68 a68, Node p) {
        Ref.Box<Pack> u = Ref.box(null);
        Node save = p;

        if (p.next.is(FORMAL_DECLARERS)) {
            getModeFromFormalPack(a68, p.next.sub, u);
            p = p.next;
        }
        Moid newOne = getModeFromDeclarer(a68, p.next);
        p.type = addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                PROC_SYMBOL, countPackMembers(a68, u.v), save, newOne, u.v);
        return p.type;
    }

    /** Collect mode from denotation (get_mode_from_denotation). */
    private static void getModeFromDenotation(A68 a68, Node p, int sizety) {
        if (p != null) {
            if (p.is(ROW_CHAR_DENOTATION)) {
                String s = p.info.symbol;
                int len = s.length();

                if (len == 1
                        || (len == 2 && s.charAt(0) == '\'')
                        || (len == 8 && s.charAt(0) == '\'' && s.charAt(1) == '(' && s.charAt(2) == 'u')
                        || (len == 12 && s.charAt(0) == '\'' && s.charAt(1) == '(' && s.charAt(2) == 'U')) {
                    p.type = a68.modes.CHAR;
                } else {
                    p.type = a68.modes.ROW_CHAR;
                }
            } else if (p.is(TRUE_SYMBOL) || p.is(FALSE_SYMBOL)) {
                p.type = a68.modes.BOOL;
            } else if (p.is(INT_DENOTATION)) {
                if (sizety == -2) {
                    p.type = a68.modes.SHORT_SHORT_INT;
                } else if (sizety == -1) {
                    p.type = a68.modes.SHORT_INT;
                } else if (sizety == 0) {
                    p.type = a68.modes.INT;
                } else if (sizety == 1) {
                    p.type = a68.modes.LONG_INT;
                } else if (sizety == 2) {
                    p.type = a68.modes.LONG_LONG_INT;
                } else if (sizety == 100) {
                    p.type = a68.modes.WORD_INT;
                } else {
                    p.type = (sizety > 0 ? a68.modes.LONG_LONG_INT : a68.modes.INT);
                }
            } else if (p.is(REAL_DENOTATION)) {
                if (sizety == 0) {
                    p.type = a68.modes.REAL;
                } else if (sizety == 1) {
                    p.type = a68.modes.LONG_REAL;
                } else if (sizety == 2) {
                    p.type = a68.modes.LONG_LONG_REAL;
                } else if (sizety == 100) {
                    p.type = a68.modes.WORD_REAL;
                } else {
                    p.type = (sizety > 0 ? a68.modes.LONG_LONG_REAL : a68.modes.REAL);
                }
            } else if (p.is(BITS_DENOTATION)) {
                if (sizety == -2) {
                    p.type = a68.modes.SHORT_SHORT_BITS;
                } else if (sizety == -1) {
                    p.type = a68.modes.SHORT_BITS;
                } else if (sizety == 0) {
                    p.type = a68.modes.BITS;
                } else if (sizety == 1) {
                    p.type = a68.modes.LONG_BITS;
                } else if (sizety == 2) {
                    p.type = a68.modes.LONG_LONG_BITS;
                } else if (sizety == 100) {
                    p.type = a68.modes.WORD_BITS;
                } else {
                    p.type = (sizety > 0 ? a68.modes.LONG_LONG_BITS : a68.modes.BITS);
                }
            } else if (p.is(LONGETY) || p.is(SHORTETY) || p.is(FIXETY)) {
                getModeFromDenotation(a68, p.next, countSizety(a68, p.sub));
                p.type = p.next.type;
            } else if (p.is(EMPTY_SYMBOL)) {
                p.type = a68.modes.VOID;
            }
        }
    }

    /** Collect modes from the syntax tree (get_modes_from_tree). */
    private static void getModesFromTree(A68 a68, Node p, Attribute attribute) {
        for (Node q = p; q != null; q = q.next) {
            if (q.is(VOID_SYMBOL)) {
                q.type = a68.modes.VOID;
            } else if (q.is(DECLARER)) {
                if (attribute == VARIABLE_DECLARATION) {
                    Moid newOne = getModeFromDeclarer(a68, q);
                    q.type = addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                            REF_SYMBOL, 0, null, newOne, null);
                } else {
                    q.type = getModeFromDeclarer(a68, q);
                }
            } else if (q.is(ROUTINE_TEXT)) {
                q.type = getModeFromRoutineText(a68, q.sub);
            } else if (q.is(OPERATOR_PLAN)) {
                q.type = getModeFromOperator(a68, q.sub);
            } else if (Parser.isOneOf(a68, q, LOC_SYMBOL, HEAP_SYMBOL)) {
                if (attribute == GENERATOR) {
                    Moid newOne = getModeFromDeclarer(a68, q.next);
                    q.next.type = newOne;
                    q.type = addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                            REF_SYMBOL, 0, null, newOne, null);
                }
            } else {
                if (attribute == DENOTATION) {
                    getModeFromDenotation(a68, q, 0);
                }
            }
        }

        if (attribute != DENOTATION) {
            for (Node q = p; q != null; q = q.next) {
                if (q.sub != null) {
                    getModesFromTree(a68, q.sub, q.attribute);
                }
            }
        }
    }

    /** Collect modes from proc variables (get_mode_from_proc_variables). */
    private static void getModeFromProcVariables(A68 a68, Node p) {
        if (p != null) {
            if (p.is(PROCEDURE_VARIABLE_DECLARATION)) {
                getModeFromProcVariables(a68, p.sub);
                getModeFromProcVariables(a68, p.next);
            } else if (p.is(PUBLIC_SYMBOL) || p.is(QUALIFIER) || p.is(PROC_SYMBOL) || p.is(COMMA_SYMBOL)) {
                getModeFromProcVariables(a68, p.next);
            } else if (p.is(DEFINING_IDENTIFIER)) {
                Moid newOne = p.next.next.type;
                p.type = addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                        REF_SYMBOL, 0, p, newOne, null);
            }
        }
    }

    /** Collect modes from proc variable declarations (get_mode_from_proc_var_declarations_tree). */
    private static void getModeFromProcVarDeclarationsTree(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            getModeFromProcVarDeclarationsTree(a68, p.sub);

            if (p.is(PROCEDURE_VARIABLE_DECLARATION)) {
                getModeFromProcVariables(a68, p);
            }
        }
    }

    /*
     * Various routines to test modes.
     */

    // Whether a mode declaration refers to self or relates to void.
    // This uses Lindsey's ying-yang algorithm.

    private static final int WELL = 0;
    private static final int NO_YIN = 1;
    private static final int NO_YANG = 2;
    private static final int UNWELL = 3;

    /** is_well_formed */
    private static int isWellFormed(A68 a68, Moid def, Moid z, boolean yin, boolean yang, int video) {
        if (z == null) {
            return UNWELL;
        } else if (yin && yang) {
            return z == a68.modes.VOID ? video : WELL;
        } else if (z == a68.modes.VOID) {
            return video;
        } else if (z.attribute == STANDARD) {
            return WELL;
        } else if (z.attribute == INDICANT) {
            if (def == null) {
                // Check an applied indicant for relation to VOID.
                while (z != null) {
                    z = z.equivalentMode;
                }
                // Sic: z is always NO_MOID here, so this always yields WELL.
                if (z == a68.modes.VOID) {
                    return video;
                } else {
                    return WELL;
                }
            } else {
                if (z == def || z.use) {
                    if (!yin) {
                        return NO_YIN;
                    } else if (!yang) {
                        return NO_YANG;
                    } else {
                        return WELL;
                    }
                } else {
                    z.use = true;
                    int wwf = isWellFormed(a68, def, z.equivalentMode, yin, yang, video);
                    z.use = false;
                    return wwf;
                }
            }
        } else if (z.attribute == REF_SYMBOL) {
            return isWellFormed(a68, def, z.sub, true, yang, UNWELL);
        } else if (z.attribute == PROC_SYMBOL) {
            return z.pack != null ? WELL : isWellFormed(a68, def, z.sub, true, yang, WELL);
        } else if (z.attribute == ROW_SYMBOL) {
            return isWellFormed(a68, def, z.sub, yin, yang, UNWELL);
        } else if (z.attribute == FLEX_SYMBOL) {
            return isWellFormed(a68, def, z.sub, yin, yang, UNWELL);
        } else if (z.attribute == STRUCT_SYMBOL) {
            for (Pack s = z.pack; s != null; s = s.next) {
                int wwf = isWellFormed(a68, def, s.type, yin, true, UNWELL);
                if (wwf != WELL) {
                    return wwf;
                }
            }
            return WELL;
        } else if (z.attribute == UNION_SYMBOL) {
            for (Pack s = z.pack; s != null; s = s.next) {
                int wwf = isWellFormed(a68, def, s.type, yin, yang, WELL);
                if (wwf != WELL) {
                    return wwf;
                }
            }
            return WELL;
        } else {
            return UNWELL;
        }
    }

    /** Replace a mode by its equivalent mode (walk chain) (resolve_eq_members). */
    private static void resolveEqMembers(A68 a68, Moid q) {
        resolveEquivalent(a68, Ref.of(() -> q.sub, v -> q.sub = v));
        resolveEquivalent(a68, Ref.of(() -> q.deflexedMode, v -> q.deflexedMode = v));
        resolveEquivalent(a68, Ref.of(() -> q.multipleMode, v -> q.multipleMode = v));
        resolveEquivalent(a68, Ref.of(() -> q.name, v -> q.name = v));
        resolveEquivalent(a68, Ref.of(() -> q.slice, v -> q.slice = v));
        resolveEquivalent(a68, Ref.of(() -> q.trim, v -> q.trim = v));
        resolveEquivalent(a68, Ref.of(() -> q.rowed, v -> q.rowed = v));
        for (Pack p = q.pack; p != null; p = p.next) {
            Pack pp = p;
            resolveEquivalent(a68, Ref.of(() -> pp.type, v -> pp.type = v));
        }
    }

    /** Track equivalent tags (resolve_eq_tags). */
    private static void resolveEqTags(A68 a68, Tag z) {
        for (; z != null; z = z.next) {
            if (z.type != null) {
                Tag zz = z;
                resolveEquivalent(a68, Ref.of(() -> zz.type, v -> zz.type = v));
            }
        }
    }

    /** Bind modes in syntax tree (bind_modes). */
    private static void bindModes(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            Node pp = p;
            resolveEquivalent(a68, Ref.of(() -> pp.type, v -> pp.type = v));

            if (p.sub != null && Parser.isNewLexicalLevel(a68, p)) {
                Table s = p.sub.symbolTable;
                for (Tag z = s.indicants; z != null; z = z.next) {
                    if (z.node != null) {
                        Node nn = z.node.next.next;
                        resolveEquivalent(a68, Ref.of(() -> nn.type, v -> nn.type = v));
                        z.type = z.node.next.next.type;
                        z.node.type = z.type;
                    }
                }
            }
            bindModes(a68, p.sub);
        }
    }

    // Routines for calculating subordinates for selections, for instance selection
    // from REF STRUCT (A) yields REF A fields and selection from [] STRUCT (A)
    // yields [] A fields.

    /**
     * Make name pack (make_name_pack).
     * Given a pack with modes: M1, M2, ...
     * Build a pack with modes: REF M1, REF M2, ...
     */
    private static void makeNamePack(A68 a68, Pack src, Ref<Pack> dst, Ref<Moid> p) {
        if (src != null) {
            makeNamePack(a68, src.next, dst, p);
            Moid z = addMode(a68, p, REF_SYMBOL, 0, null, src.type, null);
            addModeToPack(a68, dst, z, src.text, src.node);
        }
    }

    /**
     * Make flex multiple row pack (make_flex_multiple_row_pack).
     * Given a pack with modes: M1, M2, ...
     * Build a pack with modes: []M1, []M2, ...
     */
    private static void makeFlexMultipleRowPack(A68 a68, Pack src, Ref<Pack> dst, Ref<Moid> p, int dim) {
        if (src != null) {
            makeFlexMultipleRowPack(a68, src.next, dst, p, dim);
            Moid z = addRow(a68, p, dim, src.type, null, false);
            z = addMode(a68, p, FLEX_SYMBOL, 0, null, z, null);
            addModeToPack(a68, dst, z, src.text, src.node);
        }
    }

    /** Make name struct (make_name_struct). */
    private static Moid makeNameStruct(A68 a68, Moid m, Ref<Moid> p) {
        Ref.Box<Pack> u = Ref.box(null);
        makeNamePack(a68, m.pack, u, p);
        return addMode(a68, p, STRUCT_SYMBOL, m.dim, null, null, u.v);
    }

    /** Make name row (make_name_row). */
    private static Moid makeNameRow(A68 a68, Moid m, Ref<Moid> p) {
        if (m.slice != null) {
            return addMode(a68, p, REF_SYMBOL, 0, null, m.slice, null);
        } else if (m.sub != null) {
            return addMode(a68, p, REF_SYMBOL, 0, null, m.sub, null);
        } else {
            // weird, FLEX INT or so ...
            return null;
        }
    }

    /** Make multiple row pack (make_multiple_row_pack). */
    private static void makeMultipleRowPack(A68 a68, Pack src, Ref<Pack> dst, Ref<Moid> p, int dim) {
        if (src != null) {
            makeMultipleRowPack(a68, src.next, dst, p, dim);
            addModeToPack(a68, dst, addRow(a68, p, dim, src.type, null, false),
                    src.text, src.node);
        }
    }

    /** Make flex multiple struct (make_flex_multiple_struct). */
    private static Moid makeFlexMultipleStruct(A68 a68, Moid m, Ref<Moid> p, int dim) {
        Ref.Box<Pack> u = Ref.box(null);
        makeFlexMultipleRowPack(a68, m.pack, u, p, dim);
        return addMode(a68, p, STRUCT_SYMBOL, m.dim, null, null, u.v);
    }

    /** Make multiple struct (make_multiple_struct). */
    private static Moid makeMultipleStruct(A68 a68, Moid m, Ref<Moid> p, int dim) {
        Ref.Box<Pack> u = Ref.box(null);
        makeMultipleRowPack(a68, m.pack, u, p, dim);
        return addMode(a68, p, STRUCT_SYMBOL, m.dim, null, null, u.v);
    }

    /** Whether mode has row (is_mode_has_row). */
    private static boolean isModeHasRow(A68 a68, Moid m) {
        if (m.attribute == STRUCT_SYMBOL || m.attribute == UNION_SYMBOL) {
            boolean k = false;

            for (Pack p = m.pack; p != null && k == false; p = p.next) {
                p.type.hasRows = isModeHasRow(a68, p.type);
                k |= (p.type.hasRows);
            }
            return k;
        } else {
            return (m.hasRows || m.attribute == ROW_SYMBOL || m.attribute == FLEX_SYMBOL);
        }
    }

    /** Whether mode has ref (is_mode_has_refs). */
    private static boolean isModeHasRefs(A68 a68, Moid m) {
        if (m.attribute == ROW_SYMBOL || m.attribute == FLEX_SYMBOL) {
            m.hasRefs = isModeHasRefs(a68, m.sub);
            return m.hasRefs;
        } else if (m.attribute == STRUCT_SYMBOL || m.attribute == UNION_SYMBOL) {
            boolean hasRefs = false;
            for (Pack p = m.pack; p != null; p = p.next) {
                p.type.hasRefs = isModeHasRefs(a68, p.type);
                hasRefs |= p.type.hasRefs;
            }
            return hasRefs;
        } else {
            return m.hasRefs;
        }
    }

    /** Compute derived modes (compute_derived_modes). */
    private static void computeDerivedModes(A68 a68, Job mod) {
        Moid z;
        int len = 0, nlen = 1;
        // &TOP_MOID (mod)
        Ref<Moid> topMoid = Ref.of(() -> mod.topMoid, v -> mod.topMoid = v);

        // UNION things.
        absorbUnions(a68, mod.topMoid);
        contractUnions(a68, mod.topMoid);
        // The for-statement below prevents an endless loop.
        for (int k = 1; k <= 10 && len != nlen; k++) {
            // Make deflexed modes.
            for (z = mod.topMoid; z != null; z = z.next) {
                if (z.sub != null) {
                    if ((z.attribute == REF_SYMBOL && z.sub.attribute == FLEX_SYMBOL)
                            && z.sub.sub.deflexedMode != null) {
                        z.deflexedMode = addMode(a68, topMoid, REF_SYMBOL, 0, z.node,
                                z.sub.sub.deflexedMode, null);
                    } else if (z.attribute == REF_SYMBOL && z.sub.deflexedMode != null) {
                        z.deflexedMode = addMode(a68, topMoid, REF_SYMBOL, 0, z.node,
                                z.sub.deflexedMode, null);
                    } else if (z.attribute == ROW_SYMBOL && z.sub.deflexedMode != null) {
                        z.deflexedMode = addMode(a68, topMoid, ROW_SYMBOL, z.dim, z.node,
                                z.sub.deflexedMode, null);
                    } else if (z.attribute == FLEX_SYMBOL && z.sub.deflexedMode != null) {
                        z.deflexedMode = z.sub.deflexedMode;
                    } else if (z.attribute == FLEX_SYMBOL) {
                        z.deflexedMode = z.sub;
                    } else {
                        z.deflexedMode = z;
                    }
                }
            }

            // Derived modes for stowed modes.
            for (z = mod.topMoid; z != null; z = z.next) {
                if (z.name == null && z.attribute == REF_SYMBOL) {
                    if (z.sub.attribute == STRUCT_SYMBOL) {
                        z.name = makeNameStruct(a68, z.sub, topMoid);
                    } else if (z.sub.attribute == ROW_SYMBOL) {
                        z.name = makeNameRow(a68, z.sub, topMoid);
                    } else if (z.sub.attribute == FLEX_SYMBOL && z.sub.sub != null) {
                        z.name = makeNameRow(a68, z.sub.sub, topMoid);
                    }
                }

                if (z.multipleMode != null) {
                    ;
                } else if (z.attribute == REF_SYMBOL) {
                    if (z.sub.multipleMode != null) {
                        z.multipleMode = makeNameStruct(a68, z.sub.multipleMode, topMoid);
                    }
                } else if (z.attribute == ROW_SYMBOL) {
                    if (z.sub.attribute == STRUCT_SYMBOL) {
                        z.multipleMode = makeMultipleStruct(a68, z.sub, topMoid, z.dim);
                    }
                }
            }

            for (z = mod.topMoid; z != null; z = z.next) {
                if (z.trim == null && z.attribute == FLEX_SYMBOL) {
                    z.trim = z.sub;
                }
                if (z.trim == null && (z.attribute == REF_SYMBOL && z.sub.attribute == FLEX_SYMBOL)) {
                    z.trim = addMode(a68, topMoid, REF_SYMBOL, 0, z.node, z.sub.sub, null);
                }
            }

            // Fill out stuff for rows, f.i. inverse relations.
            for (z = mod.topMoid; z != null; z = z.next) {
                if (z.attribute == ROW_SYMBOL && z.dim > 0 && z.sub != null && !z.derivate) {
                    addRow(a68, topMoid, z.dim + 1, z.sub, z.node, true);
                } else if (z.attribute == REF_SYMBOL && z.sub.attribute == ROW_SYMBOL && !z.sub.derivate) {
                    Moid x = addRow(a68, topMoid, z.sub.dim + 1, z.sub.sub, z.sub.node, true);
                    Moid y = addMode(a68, topMoid, REF_SYMBOL, 0, z.node, x, null);
                    y.name = z;
                }
            }

            for (z = mod.topMoid; z != null; z = z.next) {
                if (z.attribute == ROW_SYMBOL && z.slice != null) {
                    z.slice.rowed = z;
                }
                if (z.attribute == REF_SYMBOL) {
                    Moid y = z.sub;
                    if (y.slice != null && y.slice.attribute == ROW_SYMBOL && z.name != null) {
                        z.name.rowed = z;
                    }
                }
            }

            bindModes(a68, mod.topNode);
            for (z = mod.topMoid; z != null; z = z.next) {
                if (z.attribute == INDICANT && z.node != null) {
                    z.equivalentMode = z.node.type;
                }
            }
            for (z = mod.topMoid; z != null; z = z.next) {
                resolveEqMembers(a68, z);
            }
            resolveEqTags(a68, a68.standenv.indicants);
            resolveEqTags(a68, a68.standenv.identifiers);
            resolveEqTags(a68, a68.standenv.operators);
            resolveEquivalent(a68, Ref.of(() -> a68.modes.STRING, v -> a68.modes.STRING = v));
            resolveEquivalent(a68, Ref.of(() -> a68.modes.COMPLEX, v -> a68.modes.COMPLEX = v));
            resolveEquivalent(a68, Ref.of(() -> a68.modes.LONG_COMPLEX, v -> a68.modes.LONG_COMPLEX = v));
            resolveEquivalent(a68, Ref.of(() -> a68.modes.LONG_LONG_COMPLEX, v -> a68.modes.LONG_LONG_COMPLEX = v));
            resolveEquivalent(a68, Ref.of(() -> a68.modes.WORD_COMPLEX, v -> a68.modes.WORD_COMPLEX = v));
            resolveEquivalent(a68, Ref.of(() -> a68.modes.SEMA, v -> a68.modes.SEMA = v));
            // UNION members could be resolved.
            absorbUnions(a68, mod.topMoid);
            contractUnions(a68, mod.topMoid);
            // FLEX INDICANT could be resolved.
            for (z = mod.topMoid; z != null; z = z.next) {
                if (z.attribute == FLEX_SYMBOL && z.sub != null) {
                    if (z.sub.sub != null && z.sub.sub.attribute == STRUCT_SYMBOL) {
                        z.multipleMode = makeFlexMultipleStruct(a68, z.sub.sub, topMoid, z.sub.dim);
                    }
                }
            }
            // See what new known modes we have generated by resolving..
            for (z = mod.topMoid; z != a68.job.standenvMoid; z = z.next) {
                Moid v;

                for (v = z.next; v != null; v = v.next) {
                    if (MoidsEquivalence.proveMoidEquivalence(a68, z, v)) {
                        z.equivalentMode = v;
                        v.equivalentMode = null;
                    }
                }
            }

            // Count the modes to check self consistency.
            len = nlen;
            for (nlen = 0, z = mod.topMoid; z != null; z = z.next) {
                nlen++;
            }
        }

        Parser.gccAssert(a68.modes.STRING == a68.modes.FLEX_ROW_CHAR);

        // Find out what modes contain rows, and refs.
        for (z = mod.topMoid; z != null; z = z.next) {
            z.hasRows = isModeHasRow(a68, z);
            z.hasRefs = isModeHasRefs(a68, z);
        }

        // Check flexible modes.
        for (z = mod.topMoid; z != null; z = z.next) {
            if (z.attribute == FLEX_SYMBOL && !(z.sub.attribute == ROW_SYMBOL)) {
                Fmt m = Fmt.moid(a68, z);
                a68.diagnostics.error(z.node, "%e does not specify a well formed mode", m);
            }
        }

        // Check on fields in structured modes f.i. STRUCT (REAL x, INT n, REAL x) is
        // wrong.
        for (z = mod.topMoid; z != null; z = z.next) {
            if (z.attribute == STRUCT_SYMBOL && z.equivalentMode == null) {
                Pack s = z.pack;

                for (; s != null; s = s.next) {
                    boolean x = true;

                    for (Pack t = s.next; t != null && x; t = t.next) {
                        // TEXTs are interned token texts: pointer comparison as in C.
                        if (s.text == t.text) {
                            Fmt zs = Fmt.symbol(z.node);
                            a68.diagnostics.error(z.node, "multiple declaration of field %e", zs);
                            while (s.next != null && s.next.text == t.text) {
                                s = s.next;
                            }
                            x = false;
                        }
                    }
                }
            }
        }

        // Various union test.
        for (z = mod.topMoid; z != null; z = z.next) {
            if (z.attribute == UNION_SYMBOL && z.equivalentMode == null) {
                Pack s = z.pack;
                // Discard unions with one member.
                if (countPackMembers(a68, s) == 1) {
                    Fmt m = Fmt.moid(a68, z);
                    a68.diagnostics.error(z.node, "%e must have at least two components", m);
                }
                // Discard incestuous unions with firmly related modes.
                for (; s != null; s = s.next) {
                    Pack t;

                    for (t = s.next; t != null; t = t.next) {
                        if (t.type != s.type) {
                            if (MoidsMisc.isFirm(a68, s.type, t.type)) {
                                Fmt m = Fmt.moid(a68, z);
                                a68.diagnostics.error(z.node, "%e has firmly related components", m);
                            }
                        }
                    }
                }

                // Discard incestuous unions with firmly related subsets.
                for (s = z.pack; s != null; s = s.next) {
                    Moid n = MoidsMisc.deprefCompletely(a68, s.type);

                    if (n.attribute == UNION_SYMBOL && MoidsMisc.isSubset(a68, n, z, NO_DEFLEXING)) {
                        Fmt m1 = Fmt.moid(a68, z);
                        Fmt m2 = Fmt.moid(a68, n);
                        a68.diagnostics.error(z.node, "%e has firmly related subset %e", m1, m2);
                    }
                }
            }
        }

        // Wrap up and exit.
        Postulates.freePostulateList(a68, a68.topPostulate, null);
        a68.topPostulate = null;
    }

    /** Make list of all modes in the program (a68_make_moid_list). */
    public static void makeMoidList(A68 a68, Job mod) {
        boolean cont = true;

        // Collect modes from the syntax tree.
        resetMoidTree(a68, mod.topNode);
        getModesFromTree(a68, mod.topNode, STOP);
        getModeFromProcVarDeclarationsTree(a68, mod.topNode);

        // Connect indicants to their declarers.
        for (Moid z = mod.topMoid; z != null; z = z.next) {
            if (z.attribute == INDICANT) {
                Node u = z.node;
                Parser.gccAssert(u.next != null);
                Parser.gccAssert(u.next.next != null);
                Parser.gccAssert(u.next.next.type != null);
                z.equivalentMode = u.next.next.type;
            }
        }

        // Checks on wrong declarations.
        for (Moid z = mod.topMoid; z != null; z = z.next) {
            z.use = false;
        }

        for (Moid z = mod.topMoid; z != null; z = z.next) {
            if (z.attribute == INDICANT && z.equivalentMode != null) {
                int wwf = isWellFormed(a68, z, z.equivalentMode, false, false, WELL);
                if (wwf != WELL) {
                    Fmt m = Fmt.moid(a68, z);
                    a68.diagnostics.error(z.node, "%e does not specify a well formed mode", m);
                    if (wwf == NO_YIN) {
                        a68.diagnostics.inform(z.node, "mode %e is infinite", m);
                    } else if (wwf == NO_YANG) {
                        a68.diagnostics.inform(z.node, "mode %e is strongly coercible to itself", m);
                    }
                    cont = false;
                }
            }
        }

        for (Moid z = mod.topMoid; cont && z != null; z = z.next) {
            if (z.attribute == INDICANT && z.equivalentMode != null) {
                ;
            } else if (z.node != null) {
                int wwf = isWellFormed(a68, null, z, false, false, WELL);
                if (wwf != WELL) {
                    Fmt m = Fmt.moid(a68, z);
                    a68.diagnostics.error(z.node, "%e does not specify a well formed mode", m);
                    if (wwf == NO_YIN) {
                        a68.diagnostics.inform(z.node, "mode %e is infinite", m);
                    } else if (wwf == NO_YANG) {
                        a68.diagnostics.inform(z.node, "mode %e is strongly coercible to itself", m);
                    }
                }
            }
        }

        for (Moid z = mod.topMoid; z != null; z = z.next) {
            if (z.use) {
                throw Parser.gccUnreachable();
            }
        }

        if (mod.errorCount != 0) {
            return;
        }

        computeDerivedModes(a68, mod);
        Postulates.initPostulates(a68);
        MoidsSorting.sortUnionPacks(a68, mod.topMoid);
    }
}
