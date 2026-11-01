// a68j JVM backend: variables kept in JVM locals (docs/BACKEND.md section 3).
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import a68j.front.Attribute;
import a68j.front.Moid;
import a68j.front.Node;
import a68j.front.Tag;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Direct variables: a LOC variable of a primitive mode (INT, REAL, BOOL,
 * CHAR, BITS, ...) whose name is never used as a value is kept unboxed in a
 * JVM local instead of a {@link a68j.rt.Cell}. Its name is "not used as a
 * value" when every applied occurrence of the identifier is
 * <ul>
 * <li>dereferenced (DEREFERENCING of the identifier),
 * <li>the destination of a voided assignation {@code x := e}, or
 * <li>the left operand of a standard assigning operator ({@code x +:= e},
 *     ...) that is voided or dereferenced (its new value used, as in
 *     {@code a[i +:= 1]}),
 * </ul>
 * and the variable is not captured by an inner routine (it has no frame
 * slot). The code generator then compiles exactly these patterns to
 * loads and stores of the local.
 */
final class Direct {
    private Direct() {}

    /** Find the direct variables of the program P (after {@link Capture#analyse}). */
    static Set<Tag> analyse(Gen g, Node p) {
        Set<Tag> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Tag> escaped = Collections.newSetFromMap(new IdentityHashMap<>());
        walk(g, p, candidates, escaped);
        candidates.removeAll(escaped);
        candidates.removeIf(t -> {
            Gen.Routine owner = g.tableOwner.get(t.symbolTable);
            return owner == null || owner.frameSlots.containsKey(t);
        });
        if (System.getenv("A68J_DEBUG_DIRECT") != null) {
            for (Tag t : candidates) {
                System.err.println("a68j: direct variable " + t.node.symbol() + " at " + Gen.where(t.node));
            }
        }
        return candidates;
    }

    /** Whether the variable TAG may be direct by its declaration. */
    private static boolean candidate(Gen g, Tag t) {
        if (t == null || t.heap == Attribute.HEAP_SYMBOL || t.heap == Attribute.STATIC_SYMBOL) {
            return false;
        }
        Moid m = JType.base(t.type);
        if (m == null || m.attribute != Attribute.REF_SYMBOL || !GenDecls.cellAtEntry(t)) {
            return false;
        }
        return switch (JType.of(g.a68, m.sub)) {
            case BOOL, INT, LONG, FLOAT, DOUBLE -> true;
            default -> false;
        };
    }

    private static void walk(Gen g, Node p, Set<Tag> candidates, Set<Tag> escaped) {
        for (; p != null; p = p.next) {
            visit(g, p, candidates, escaped);
        }
    }

    private static void visit(Gen g, Node p, Set<Tag> candidates, Set<Tag> escaped) {
        switch (p.attribute) {
            case VARIABLE_DECLARATION -> {
                java.util.List<Node> defs = new java.util.ArrayList<>();
                GenDecls.definitions(p, defs);
                for (Node d : defs) {
                    if (candidate(g, d.tag)) {
                        candidates.add(d.tag);
                    }
                }
                walk(g, p.sub, candidates, escaped);
            }
            case DEREFERENCING -> {
                Node s = GenUnits.strip(p.sub);
                if (s.attribute == Attribute.FORMULA && assigningOperator(g, s) != null
                        && GenUnits.strip(s.sub).attribute == Attribute.IDENTIFIER) {
                    // (x +:= e) used for its new value.
                    walk(g, s.sub.next.next, candidates, escaped);
                } else if (s.attribute != Attribute.IDENTIFIER) {
                    walk(g, p.sub, candidates, escaped);
                }
            }
            case VOIDING -> {
                Node s = GenUnits.strip(p.sub);
                if (s.attribute == Attribute.ASSIGNATION && GenUnits.strip(s.sub).attribute == Attribute.IDENTIFIER) {
                    walk(g, s.sub.next.next, candidates, escaped);
                } else if (s.attribute == Attribute.FORMULA && assigningOperator(g, s) != null
                        && GenUnits.strip(s.sub).attribute == Attribute.IDENTIFIER) {
                    walk(g, s.sub.next.next, candidates, escaped);
                } else {
                    walk(g, p.sub, candidates, escaped);
                }
            }
            case IDENTIFIER -> {
                if (p.tag != null) {
                    escaped.add(p.tag);
                }
                walk(g, p.sub, candidates, escaped);
            }
            default -> walk(g, p.sub, candidates, escaped);
        }
    }

    /**
     * The operator of FORMULA (without ":=", e.g. "+") if it is a standard
     * assigning operator REF M op M, otherwise null.
     */
    static String assigningOperator(Gen g, Node formula) {
        Node op = formula.sub.next;
        if (op == null || op.tag == null || !g.isIntrinsic(op.tag) || Intrinsics.isModuleTag(op.tag)) {
            return null;
        }
        Moid proc = JType.base(op.tag.type);
        if (proc == null || proc.pack == null || proc.pack.next == null
                || JType.of(g.a68, proc.pack.type) != JType.REF) {
            return null;
        }
        return Intrinsics.assigningName(Intrinsics.name(op.tag));
    }

    /** The direct variable denoted by P (an identifier, possibly wrapped), or null. */
    static Tag of(Gen g, Node p) {
        Node s = GenUnits.strip(p);
        return s.attribute == Attribute.IDENTIFIER && s.tag != null && g.direct.contains(s.tag) ? s.tag : null;
    }
}
