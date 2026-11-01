// Java port of ga68's a68-moids-sorting.cc: MOID sorting routines.
// Original: Copyright (C) 2026 James Bohl.
// Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Routines for ordering modes (a68-moids-sorting.cc).
 *
 * <p>Attribute differences use {@link Attribute#ordinal()}, which equals the
 * numeric value of the C enumerator. Symbol texts are interned token texts,
 * so the C pointer comparisons on TEXT are kept as reference comparisons.
 */
public final class MoidsSorting {
    private MoidsSorting() {}

    // Forward references: not needed in Java (mode_ordering, sort_union_pack).

    /**
     * packs_ordering with the default {@code compare_names = true}.
     */
    private static int packsOrdering(A68 a68, Pack a, Pack b) {
        return packsOrdering(a68, a, b, true);
    }

    /**
     * Returns a negative value if 'a' should be ordered after 'b'.
     * Returns a positive value if 'a' should be ordered before 'b'.
     * Returns zero if 'a' and 'b' are equivalent (packs_ordering).
     */
    private static int packsOrdering(A68 a68, Pack a, Pack b, boolean compareNames) {
        for (; a != null && b != null; a = a.next, b = b.next) {
            int order = modeOrdering(a68, a.type, b.type);
            if (order != 0) {
                return order;
            }
            if (compareNames) {
                if (a.text != b.text) {
                    if (a.text == null) {
                        return 1;
                    }
                    if (b.text == null) {
                        return -1;
                    }

                    int cmp = CType.strcmp(a.text, b.text);
                    if (cmp != 0) {
                        return -cmp;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Returns a negative value if 'a' should be ordered after 'b'.
     * Returns a positive value if 'a' should be ordered before 'b'.
     * Returns zero if 'a' and 'b' are equivalent (mode_ordering).
     */
    private static int modeOrdering(A68 a68, Moid a, Moid b) {
        if (a == b) {
            return 0;
        }
        int r = a.attribute.ordinal() - b.attribute.ordinal();
        if (r != 0) {
            return r;
        }
        r = a.dim - b.dim;
        if (r != 0) {
            return r;
        }
        if (a.attribute == STANDARD) {
            return CType.strcmp(a.node.info.symbol, b.node.info.symbol);
        } else if (a.equivalentMode == b || b.equivalentMode == a) {
            return 0;
        } else if (Postulates.isPostulatedPair(a68, a68.topPostulate, a, b) != null
                || Postulates.isPostulatedPair(a68, a68.topPostulate, b, a) != null) {
            return 0;
        } else if (a.attribute == INDICANT) {
            if (a.node == null) {
                return 1;
            }
            if (b.node == null) {
                return -1;
            }
            if (a.node == b.node) {
                return 0;
            }
            return CType.strcmp(a.node.info.symbol, b.node.info.symbol);
        } else if (a.attribute == REF_SYMBOL) {
            return modeOrdering(a68, a.sub, b.sub);
        } else if (a.attribute == ROW_SYMBOL) {
            return modeOrdering(a68, a.sub, b.sub);
        } else if (a.attribute == FLEX_SYMBOL) {
            return modeOrdering(a68, a.sub, b.sub);
        } else if (a.attribute == STRUCT_SYMBOL) {
            Postulate save = a68.topPostulate;
            Postulates.makePostulate(a68, Ref.of(() -> a68.topPostulate, v -> a68.topPostulate = v), a, b);
            r = packsOrdering(a68, a.pack, b.pack);
            Postulates.freePostulateList(a68, a68.topPostulate, save);
            a68.topPostulate = save;
            return r;
        } else if (a.attribute == UNION_SYMBOL) {
            a.pack = sortUnionPack(a68, a.pack);
            b.pack = sortUnionPack(a68, b.pack);
            return packsOrdering(a68, a.pack, b.pack, false);
        } else if (a.attribute == PROC_SYMBOL) {
            Postulate save = a68.topPostulate;
            Postulates.makePostulate(a68, Ref.of(() -> a68.topPostulate, v -> a68.topPostulate = v), a, b);
            r = modeOrdering(a68, a.sub, b.sub);
            if (r == 0) {
                r = packsOrdering(a68, a.pack, b.pack, false);
            }
            Postulates.freePostulateList(a68, a68.topPostulate, save);
            a68.topPostulate = save;
            return r;
        } else if (a.attribute == SERIES_MODE || a.attribute == STOWED_MODE) {
            return packsOrdering(a68, a.pack, b.pack, false);
        }
        return 0;
    }

    /** Add a moid to a sorted pack, maybe with a (field) name (add_mode_to_pack_sorted). */
    private static void addModeToPackSorted(A68 a68, Ref<Pack> p, Moid m, String text, Node node) {
        Pack z = Parser.newPack(a68);

        z.type = m;
        z.text = text;
        z.node = node;

        Pack next = p.get();
        Pack previous = null;
        while (next != null) {
            int order = modeOrdering(a68, m, next.type);
            if (order > 0) {
                break;
            }
            previous = next;
            next = next.next;
        }
        z.next = next;
        z.previous = previous;

        if (previous == null) {
            p.set(z);
        } else {
            previous.next = z;
        }

        if (next != null) {
            next.previous = z;
        }
    }

    /** Sort modes in a UNION pack (sort_union_pack). */
    private static Pack sortUnionPack(A68 a68, Pack u) {
        Ref.Box<Pack> z = Ref.box(null);
        for (Pack t = u; t != null; t = t.next) {
            addModeToPackSorted(a68, z, t.type, null, t.node);
        }
        return z.v;
    }

    /** Sort modes in UNION packs (a68_sort_union_packs). */
    public static void sortUnionPacks(A68 a68, Moid m) {
        for (; m != null; m = m.next) {
            if (m.attribute == UNION_SYMBOL) {
                m.pack = sortUnionPack(a68, m.pack);
            }
        }
    }
}
