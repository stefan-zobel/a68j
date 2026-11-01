// a68j JVM backend: row descriptors kept in JVM locals during a loop.
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.Attribute;
import a68j.front.Moid;
import a68j.front.Node;
import a68j.front.Tag;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Hoisted rows: for a row that a loop subscripts and cannot change, the
 * loop reads the descriptor once on entry into JVM locals instead of in
 * every element access (docs/optimizations.md, section 8).
 *
 * <p>Without this an access {@code a[i]} of a row variable is
 * {@code Cell.value}, a check that it is a {@code Row}, the fields
 * {@code lwb0}, {@code n0}, {@code stride0}, {@code offset} and
 * {@code store} and a check that the store is an {@code int[]}, each time;
 * C2 does not move these loads out of the loop.
 *
 * <p>Which rows: the subscripted primary of a one-dimensional element
 * access of a primitive mode (INT, REAL, BOOL, CHAR, BITS, ...) in the
 * loop clause (not in routine texts inside it) that is
 * <ul>
 * <li>a row variable whose name never escapes ({@link #analyse}: every
 *     applied occurrence is dereferenced, sliced, or the destination of a
 *     voided assignation or assigning operator) and that is not captured by
 *     a routine, so that only an assignation in the text of the loop can
 *     give it another row, and the loop has none; or
 * <li>an identity of a row mode (its value never changes);
 * </ul>
 * in both cases declared outside the loop and held in a JVM local of the
 * method. The fast path, {@code arr[off + (i - lo)]} after one bound
 * check, is for rows with stride 1 and a store that is an array of the
 * element type: nearly all of them, since identities and parameters copy a
 * slice (only a slice of an identity is shared, see
 * GenUnits#ownedValue). With a constant stride C2 removes its own range
 * check of the array and unrolls counted loops; with the stride in a local
 * it did neither (docs/optimizations.md, section 8). Any other row (a
 * column {@code mm[, 2]} of an identity, an Object[] store, a row of
 * fields) takes the old path in each access, which costs no more than
 * before. The elements themselves are not cached: they live in the shared
 * store and may change.
 *
 * <p>The variable is NIL on entry only if the loop runs before its
 * declaration (then it stays NIL during the whole loop, since the
 * declaration is outside it): the locals then say "no elements", and an
 * access takes the bound-error path, which dereferences the variable as
 * before and reports NIL. The only difference to the old code: the
 * subscript is evaluated before that error is reported.
 *
 * <p>The environment variable {@code A68J_HOIST_ROWS=off} turns it off
 * (for measurements).
 */
final class Hoist {
    private Hoist() {}

    static final boolean ON = !"off".equals(System.getenv("A68J_HOIST_ROWS"));

    /** The locals holding the descriptor of the row TAG during a loop. */
    record Row(Tag tag, JType elem, boolean variable, int row, int arr, int lo, int n, int off) {}

    private record Key(Tag tag) {}

    // ---- analysis -------------------------------------------------------

    /** The row variables of the program P whose names never escape (see the class comment). */
    static Set<Tag> analyse(Gen g, Node p) {
        Set<Tag> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Tag> escaped = Collections.newSetFromMap(new IdentityHashMap<>());
        walk(g, p, candidates, escaped);
        candidates.removeAll(escaped);
        candidates.removeIf(t -> {
            Gen.Routine owner = g.tableOwner.get(t.symbolTable);
            return owner == null || owner.frameSlots.containsKey(t);
        });
        if (System.getenv("A68J_DEBUG_HOIST") != null) {
            for (Tag t : candidates) {
                System.err.println("a68j: hoistable row variable " + t.node.symbol() + " at " + Gen.where(t.node));
            }
        }
        return candidates;
    }

    /** The element mode of a one-dimensional row mode M (FLEX allowed) of a primitive element, or null. */
    private static JType rowElem(Gen g, Moid m) {
        m = JType.base(m);
        if (m != null && m.attribute == Attribute.FLEX_SYMBOL) {
            m = JType.base(m.sub);
        }
        if (m == null || m.attribute != Attribute.ROW_SYMBOL || m.dim != 1 || m.sub == null) {
            return null;
        }
        JType t = JType.of(g.a68, JType.base(m.sub));
        return switch (t) {
            case BOOL, INT, LONG, FLOAT, DOUBLE -> t;
            default -> null;
        };
    }

    private static boolean candidate(Gen g, Tag t) {
        if (t == null || !t.variable || t.heap == Attribute.STATIC_SYMBOL) {
            return false;
        }
        Moid m = JType.base(t.type);
        return m != null && m.attribute == Attribute.REF_SYMBOL && rowElem(g, m.sub) != null
                && Gen.CD_CELL.equals(g.ownCell(t));
    }

    private static void walk(Gen g, Node p, Set<Tag> candidates, Set<Tag> escaped) {
        for (; p != null; p = p.next) {
            visit(g, p, candidates, escaped);
        }
    }

    private static void visit(Gen g, Node p, Set<Tag> candidates, Set<Tag> escaped) {
        switch (p.attribute) {
            case VARIABLE_DECLARATION -> {
                List<Node> defs = new ArrayList<>();
                GenDecls.definitions(p, defs);
                for (Node d : defs) {
                    if (candidate(g, d.tag)) {
                        candidates.add(d.tag);
                    }
                }
                walk(g, p.sub, candidates, escaped);
            }
            case DEREFERENCING -> {
                if (GenUnits.strip(p.sub).attribute != Attribute.IDENTIFIER) {
                    walk(g, p.sub, candidates, escaped);
                }
            }
            case SLICE -> {
                if (GenUnits.strip(p.sub).attribute == Attribute.IDENTIFIER) {
                    walk(g, p.sub.next, candidates, escaped);  // the indexer only
                } else {
                    walk(g, p.sub, candidates, escaped);
                }
            }
            case VOIDING -> {
                Node s = GenUnits.strip(p.sub);
                if ((s.attribute == Attribute.ASSIGNATION
                        || s.attribute == Attribute.FORMULA && Direct.assigningOperator(g, s) != null)
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

    /** Whether P contains an assignation to the variable T or an assigning operator applied to it. */
    private static boolean assigns(Gen g, Node p, Tag t) {
        for (Node q = p.sub; q != null; q = q.next) {
            if ((q.attribute == Attribute.ASSIGNATION
                    || q.attribute == Attribute.FORMULA && Direct.assigningOperator(g, q) != null)) {
                Node d = GenUnits.strip(q.sub);
                if (d.attribute == Attribute.IDENTIFIER && d.tag == t) {
                    return true;
                }
            }
            if (assigns(g, q, t)) {
                return true;
            }
        }
        return false;
    }

    /** The tag of the row subscripted by the element access P if it may be hoisted in some loop, else null. */
    private static Tag subscripted(Gen g, Node p) {
        if (p.attribute != Attribute.SLICE || !GenRows.isElementAccess(g, p)) {
            return null;
        }
        Node prim = GenUnits.strip(p.sub);
        if (prim.attribute != Attribute.IDENTIFIER || prim.tag == null) {
            return null;
        }
        Tag t = prim.tag;
        if (g.hoistable.contains(t)) {
            return t;
        }
        // An identity of a row mode, in a local of this method.
        if (!t.variable && JType.of(g.a68, t.type) == JType.ROW && rowElem(g, t.type) != null) {
            Gen.Routine owner = g.tableOwner.get(t.symbolTable);
            if (owner != null && !owner.frameSlots.containsKey(t)) {
                return t;
            }
        }
        return null;
    }

    private static void accesses(Gen g, Node p, List<Tag> out) {
        for (Node q = p.sub; q != null; q = q.next) {
            if (g.routineOf.containsKey(q)) {
                continue;
            }
            Tag t = subscripted(g, q);
            if (t != null && !out.contains(t)) {
                out.add(t);
            }
            accesses(g, q, out);
        }
    }

    // ---- code -----------------------------------------------------------

    /**
     * On entry of the loop clause LOOP: read the descriptors of the rows it
     * may hoist into new locals and register them; returns them for
     * {@link #exit}.
     */
    static List<Row> enter(Gen g, Node loop) {
        if (!ON) {
            return List.of();
        }
        List<Tag> tags = new ArrayList<>();
        accesses(g, loop, tags);
        if (tags.isEmpty()) {
            return List.of();
        }
        Set<Tag> defined = Collections.newSetFromMap(new IdentityHashMap<>());
        Outline.definitions(loop, defined);
        List<Row> out = new ArrayList<>();
        for (Tag t : tags) {
            if (defined.contains(t) || !g.localSlot.containsKey(t) || g.methodState.containsKey(new Key(t))) {
                continue;  // declared inside, not in a local, or hoisted by an enclosing loop already
            }
            boolean variable = t.variable;
            if (variable && assigns(g, loop, t)) {
                continue;
            }
            Moid rm = variable ? JType.base(t.type).sub : t.type;
            Row h = emit(g, t, rowElem(g, rm), variable, loop);
            g.methodState.put(new Key(t), h);
            out.add(h);
        }
        return out;
    }

    /** At the end of the loop clause: forget the rows of {@link #enter}. */
    static void exit(Gen g, List<Row> rows) {
        for (Row h : rows) {
            g.methodState.remove(new Key(h.tag));
        }
    }

    /** The hoisted row that the element access P subscripts, or null. */
    static Row of(Gen g, Node p) {
        if (!ON || g.methodState.isEmpty()) {
            return null;
        }
        Node prim = GenUnits.strip(p.sub);
        if (prim.attribute != Attribute.IDENTIFIER || prim.tag == null) {
            return null;
        }
        return (Row) g.methodState.get(new Key(prim.tag));
    }

    /**
     * The code on entry: the locals of the row TAG. ROW is the row (null if
     * the variable is NIL), ARR its store if that is an array of the element
     * type (else null), LO its lower bound, OFF its offset and N its number
     * of elements, but 0 unless the row has stride 1 and ARR is set: then an
     * access in the fast path is {@code arr[off + (i - lo)]} after one
     * comparison, and every other row takes the old path (see
     * {@link #offsets}).
     */
    private static Row emit(Gen g, Tag t, JType elem, boolean variable, Node at) {
        CodeBuilder cb = g.cb;
        ClassDesc arrDesc = elem.desc.arrayType();
        int row = cb.allocateLocal(TypeKind.REFERENCE);
        int arr = cb.allocateLocal(TypeKind.REFERENCE);
        int lo = cb.allocateLocal(TypeKind.INT);
        int n = cb.allocateLocal(TypeKind.INT);
        int off = cb.allocateLocal(TypeKind.INT);
        // Defaults: no elements, no array.
        cb.iconst_0();
        cb.istore(lo);
        cb.iconst_0();
        cb.istore(n);
        cb.iconst_0();
        cb.istore(off);
        cb.aconst_null();
        cb.checkcast(arrDesc);
        cb.astore(arr);
        Label done = cb.newLabel();
        g.load(t, at);
        if (variable) {
            Label nil = cb.newLabel();
            Label have = cb.newLabel();
            cb.dup();
            cb.ifnull(nil);
            cb.checkcast(Gen.CD_CELL);
            cb.getfield(Gen.CD_CELL, "value", CD_Object);
            cb.checkcast(Gen.CD_ROW);
            cb.goto_(have);
            cb.labelBinding(nil);
            cb.pop();
            cb.aconst_null();
            cb.checkcast(Gen.CD_ROW);
            cb.labelBinding(have);
        }
        cb.astore(row);
        cb.aload(row);
        cb.ifnull(done);
        cb.aload(row);
        cb.getfield(Gen.CD_ROW, "lwb0", CD_int);
        cb.istore(lo);
        cb.aload(row);
        cb.getfield(Gen.CD_ROW, "offset", CD_int);
        cb.istore(off);
        cb.aload(row);
        cb.getfield(Gen.CD_ROW, "store", CD_Object);
        cb.dup();
        Label notArr = cb.newLabel();
        cb.instanceOf(arrDesc);
        cb.ifeq(notArr);
        cb.checkcast(arrDesc);
        cb.astore(arr);
        cb.aload(row);
        cb.getfield(Gen.CD_ROW, "stride0", CD_int);
        cb.iconst_1();
        cb.if_icmpne(done);
        cb.aload(row);
        cb.getfield(Gen.CD_ROW, "n0", CD_int);
        cb.istore(n);
        cb.goto_(done);
        cb.labelBinding(notArr);
        cb.pop();
        cb.labelBinding(done);
        return new Row(t, elem, variable, row, arr, lo, n, off);
    }

    /**
     * Stack: -> Row, int: as GenRows#rowAndOffset for the element access P
     * of the hoisted row H: the subscript, checked against the bounds in the
     * locals. If that fails (a subscript out of bounds, or N is 0 because
     * the row has another stride, no typed store or is NIL) the row is
     * loaded as before and Row#index1 computes the index or reports the
     * error (or Rt.deref reports NIL).
     */
    static void offsets(Gen g, Node p, Row h) {
        CodeBuilder cb = g.cb;
        List<Node> subs = new ArrayList<>();
        GenRows.subscripts(p.sub.next, subs);
        g.unit(subs.get(0));
        int sub = cb.allocateLocal(TypeKind.INT);
        cb.istore(sub);
        int i = cb.allocateLocal(TypeKind.INT);
        Label bad = cb.newLabel();
        Label ok = cb.newLabel();
        cb.iload(sub);
        cb.iload(h.lo);
        cb.isub();
        cb.dup();
        cb.istore(i);
        cb.iflt(bad);
        cb.iload(i);
        cb.iload(h.n);
        cb.if_icmpge(bad);
        cb.iload(i);  // stride 1
        cb.goto_(ok);
        cb.labelBinding(bad);
        if (h.variable) {
            GenRows.loadRowOfName(g, p.sub, p);
        } else {
            g.unit(p.sub);
        }
        cb.iload(sub);
        g.pushWhere(p);
        cb.invokevirtual(Gen.CD_ROW, "index1", MethodTypeDesc.of(CD_int, CD_int, CD_String, CD_int));
        cb.labelBinding(ok);
        int off = cb.allocateLocal(TypeKind.INT);
        cb.istore(off);
        cb.aload(h.row);
        cb.iload(off);
    }

    /** Stack: Row, int -> the element, of the hoisted row H (see GenRows#loadElem). */
    static void loadElem(Gen g, Row h) {
        CodeBuilder cb = g.cb;
        JType t = h.elem;
        int idx = cb.allocateLocal(TypeKind.INT);
        cb.istore(idx);
        cb.pop();
        Label slow = cb.newLabel();
        Label end = cb.newLabel();
        cb.aload(h.arr);
        cb.ifnull(slow);
        cb.aload(h.arr);
        cb.iload(h.off);
        cb.iload(idx);
        cb.iadd();
        cb.arrayLoad(t.kind);
        cb.goto_(end);
        cb.labelBinding(slow);
        cb.aload(h.row);
        cb.iload(idx);
        cb.invokestatic(Gen.CD_RT, GenRows.elemName(t), MethodTypeDesc.of(t.desc, Gen.CD_ROW, CD_int));
        cb.labelBinding(end);
    }

    /** Stack: Row, int, value -> (empty): store the element of the hoisted row H (see GenRows#storeElem). */
    static void storeElem(Gen g, Row h) {
        CodeBuilder cb = g.cb;
        JType t = h.elem;
        int v = cb.allocateLocal(t.kind);
        int idx = cb.allocateLocal(TypeKind.INT);
        cb.storeLocal(t.kind, v);
        cb.istore(idx);
        cb.pop();
        Label slow = cb.newLabel();
        Label end = cb.newLabel();
        cb.aload(h.arr);
        cb.ifnull(slow);
        cb.aload(h.arr);
        cb.iload(h.off);
        cb.iload(idx);
        cb.iadd();
        cb.loadLocal(t.kind, v);
        cb.arrayStore(t.kind);
        cb.goto_(end);
        cb.labelBinding(slow);
        cb.aload(h.row);
        cb.iload(idx);
        cb.loadLocal(t.kind, v);
        cb.invokestatic(Gen.CD_RT, "set" + Character.toUpperCase(GenRows.elemName(t).charAt(0))
                + GenRows.elemName(t).substring(1), MethodTypeDesc.of(CD_void, Gen.CD_ROW, CD_int, t.desc));
        cb.labelBinding(end);
    }
}
