// a68j JVM backend: rows, structures, generators (reference: a68-low-multiples.cc,
// a68-low-structs.cc, a68-low-generator.cc). Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.Attribute;
import a68j.front.Moid;
import a68j.front.Node;
import a68j.front.Pack;

import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

/** Rows, structures and generators. */
final class GenRows {
    private GenRows() {}

    static final ClassDesc CD_GEN = ClassDesc.of("a68j.rt.Gen");

    // ------------------------------------------------------------------
    // Mode descriptions for a68j.rt.Gen.
    // ------------------------------------------------------------------

    /** The description of mode M for {@link a68j.rt.Gen} (see there). */
    static String desc(Gen g, Moid m) {
        StringBuilder sb = new StringBuilder();
        desc(g, m, sb, 0);
        return sb.toString();
    }

    private static void desc(Gen g, Moid m, StringBuilder sb, int depth) {
        m = JType.base(m);
        if (depth > 50) {
            // A recursive mode can only recurse through REF or PROC.
            sb.append('n');
            return;
        }
        var md = g.a68.modes;
        if (m == md.STRING) {
            sb.append('s');
            return;
        }
        if (m == md.CHAR) {
            sb.append('c');
            return;
        }
        switch (JType.of(g.a68, m)) {
            case VOID -> sb.append('v');
            case BOOL -> sb.append('b');
            case INT -> sb.append('i');
            case LONG -> sb.append('l');
            case FLOAT -> sb.append('f');
            case DOUBLE -> sb.append('d');
            case REF, PROC -> sb.append('n');
            case ROW -> {
                Moid r = m.attribute == Attribute.FLEX_SYMBOL ? JType.base(m.sub) : m;
                sb.append('R').append(r.dim).append(':');
                desc(g, r.sub, sb, depth + 1);
            }
            case STRUCT -> {
                if (!m.hasRows) {
                    sb.append('z');
                }
                int k = 0;
                for (Pack q = m.pack; q != null; q = q.next) {
                    k++;
                }
                sb.append('S').append(k).append(':');
                for (Pack q = m.pack; q != null; q = q.next) {
                    desc(g, q.type, sb, depth + 1);
                }
            }
            case UNION -> {
                if (!m.hasRows) {
                    sb.append('z');
                }
                Moid first = JType.base(m.pack.type);
                sb.append('U').append(JType.unionTag(g, first)).append(':');
                desc(g, first, sb, depth + 1);
            }
            default -> sb.append('n');
        }
    }

    /** Push the SKIP value of a non-primitive mode M. */
    static void pushSkipValue(Gen g, Moid m) {
        JType t = JType.of(g.a68, m);
        switch (t) {
            case ROW, STRUCT, UNION -> {
                g.cb.ldc(desc(g, m));
                g.cb.invokestatic(CD_GEN, "skip", MethodTypeDesc.of(CD_Object, CD_String));
                g.cb.checkcast(t.desc);
            }
            default -> g.cb.aconst_null();
        }
    }

    // ------------------------------------------------------------------
    // Generators.
    // ------------------------------------------------------------------

    /** Push a new name (Cell) of mode REF M, initialised as the declarer prescribes. */
    static void generate(Gen g, Node declarer, Moid m, Node at) {
        JType t = JType.of(g.a68, m);
        if (t.isPrimitive()) {
            // A location of a primitive mode holds its value unboxed.
            ClassDesc cell = typedCell(t);
            g.cb.new_(cell);
            g.cb.dup();
            GenUnits.pushSkip(g, m);
            g.cb.invokespecial(cell, "<init>", MethodTypeDesc.of(CD_void, t.desc));
            return;
        }
        g.cb.new_(Gen.CD_CELL);
        g.cb.dup();
        if (t == JType.ROW && generateRow1(g, declarer, desc(g, m))) {
            // Done: a one-dimensional row of a primitive mode.
        } else if (t == JType.ROW || t == JType.STRUCT || t == JType.UNION) {
            g.cb.ldc(desc(g, m));
            pushBounds(g, declarer);
            g.cb.invokestatic(CD_GEN, "gen", MethodTypeDesc.of(CD_Object, CD_String, CD_long.arrayType()));
        } else {
            GenUnits.pushSkip(g, m);
            g.box(m);
        }
        g.cb.invokespecial(Gen.CD_CELL, "<init>", MethodTypeDesc.of(CD_void, CD_Object));
    }

    /**
     * Push a new name of mode REF M holding the SKIP value of M: a LOC
     * variable without rows, which ga68 makes a C variable initialised with
     * a68_get_skip_tree (a union is united with its first mode, a CHAR is a
     * blank), not by a generator (fill_in_buffer, see a68j.rt.Gen).
     */
    static void generateSkip(Gen g, Moid m) {
        JType t = JType.of(g.a68, m);
        if (t.isPrimitive()) {
            ClassDesc cell = typedCell(t);
            g.cb.new_(cell);
            g.cb.dup();
            GenUnits.pushSkip(g, m);
            g.cb.invokespecial(cell, "<init>", MethodTypeDesc.of(CD_void, t.desc));
            return;
        }
        g.cb.new_(Gen.CD_CELL);
        g.cb.dup();
        GenUnits.pushSkip(g, m);
        g.box(m);
        g.cb.invokespecial(Gen.CD_CELL, "<init>", MethodTypeDesc.of(CD_void, CD_Object));
    }

    /** The class of the unboxed locations of the primitive JVM type T (a68j.rt.IntCell, ...). */
    static ClassDesc typedCell(JType t) {
        return ClassDesc.of("a68j.rt." + switch (t) {
            case INT -> "IntCell";
            case LONG -> "LongCell";
            case FLOAT -> "FloatCell";
            case DOUBLE -> "DoubleCell";
            case BOOL -> "BoolCell";
            default -> throw new IllegalArgumentException(t.name());
        });
    }

    /** The suffix of the typed runtime helpers for the primitive JVM type T (Rt.derefI, Rt.elemI, ...). */
    static String typeLetter(JType t) {
        return switch (t) {
            case INT -> "I";
            case LONG -> "L";
            case FLOAT -> "F";
            case DOUBLE -> "D";
            case BOOL -> "Z";
            default -> throw new IllegalArgumentException(t.name());
        };
    }

    /**
     * The fast path of {@link #generate} for a one-dimensional row of INT,
     * REAL, BOOL, CHAR ... (description R1:x): push its new value with
     * Row.make1 instead of interpreting the description in a68j.rt.Gen.
     * Returns false (emitting nothing) for any other row.
     */
    private static boolean generateRow1(Gen g, Node declarer, String desc) {
        if (declarer == null || desc.length() != 4 || !desc.startsWith("R1:")
                || "ilfdbc".indexOf(desc.charAt(3)) < 0) {
            return false;
        }
        List<Node[]> bounds = new ArrayList<>();
        collectDeclarerBounds(declarer.sub, bounds, 0);
        if (bounds.size() != 1) {
            return false;
        }
        // As pushBounds: a declarer generated twice in one method (shared by
        // several declarations) elaborates its bounds once.
        Object key = List.of("bounds1", declarer);
        int[] slots = (int[]) g.methodState.get(key);
        if (slots == null) {
            Node[] b = bounds.get(0);
            if (b[0] == null) {
                g.cb.lconst_1();
            } else {
                g.unit(b[0]);
                g.cb.i2l();
            }
            g.unit(b[1]);
            g.cb.i2l();
            slots = new int[] {g.cb.allocateLocal(TypeKind.LONG), g.cb.allocateLocal(TypeKind.LONG)};
            g.cb.lstore(slots[1]);
            g.cb.lstore(slots[0]);
            g.methodState.put(key, slots);
        }
        g.cb.lload(slots[0]);
        g.cb.lload(slots[1]);
        char kind = desc.charAt(3);
        g.cb.ldc((int) (kind == 'c' ? 'i' : kind));
        g.cb.invokestatic(Gen.CD_ROW, "make1", MethodTypeDesc.of(Gen.CD_ROW, CD_long, CD_long, CD_char));
        return true;
    }

    /**
     * Push a long[] with the bounds of the actual declarer (collect_declarer_bounds):
     * evaluated once per declarer node, as the language prescribes for
     * {@code [n +:= 1] REAL a, b}.
     */
    private static void pushBounds(Gen g, Node declarer) {
        if (declarer == null) {
            g.cb.iconst_0();
            g.cb.newarray(TypeKind.LONG);
            return;
        }
        Object key = List.of("bounds", declarer);
        Integer slot = (Integer) g.methodState.get(key);
        if (slot != null) {
            g.cb.aload(slot);
            return;
        }
        List<Node[]> bounds = new ArrayList<>();
        collectDeclarerBounds(declarer == null ? null : declarer.sub, bounds, 0);
        g.cb.ldc(2 * bounds.size());
        g.cb.newarray(TypeKind.LONG);
        int i = 0;
        for (Node[] b : bounds) {
            for (int j = 0; j < 2; j++) {
                g.cb.dup();
                g.cb.ldc(i++);
                if (b[j] == null) {
                    g.cb.lconst_1();
                } else {
                    g.unit(b[j]);
                    g.cb.i2l();
                }
                g.cb.lastore();
            }
        }
        if (!bounds.isEmpty()) {
            int s = g.cb.allocateLocal(TypeKind.REFERENCE);
            g.cb.dup();
            g.cb.astore(s);
            g.methodState.put(key, s);
        }
    }

    /** collect_declarer_bounds_1: pairs {lower or null, upper}. */
    private static void collectDeclarerBounds(Node p, List<Node[]> out, int depth) {
        for (; p != null; p = p.next) {
            if (p.attribute == Attribute.BOUNDS) {
                collectBounds(p.sub, out);
            } else if (p.attribute == Attribute.INDICANT && "STRING".equals(p.info.symbol)) {
                return;
            } else if (p.attribute == Attribute.INDICANT) {
                if (p.tag != null && p.tag.type != null && p.tag.type.hasRows && depth < 50) {
                    collectDeclarerBounds(p.tag.node.next.next, out, depth + 1);
                }
            } else if (p.attribute == Attribute.DECLARER && p.type != null
                    && (JType.base(p.type).attribute == Attribute.UNION_SYMBOL || !p.type.hasRows)) {
                return;
            } else {
                collectDeclarerBounds(p.sub, out, depth);
            }
        }
    }

    private static void collectBounds(Node p, List<Node[]> out) {
        for (; p != null; p = p.next) {
            if (p.attribute == Attribute.BOUNDS_LIST || p.attribute == Attribute.BOUND) {
                collectBounds(p.sub, out);
            } else if (p.attribute == Attribute.UNIT) {
                Node lower = null;
                if (p.next != null && p.next.attribute == Attribute.COLON_SYMBOL) {
                    lower = p;
                    p = p.next.next;
                }
                out.add(new Node[] {lower, p});
            }
        }
    }

    // ------------------------------------------------------------------
    // Collateral clauses (displays).
    // ------------------------------------------------------------------

    static void collateral(Gen g, Node p) {
        List<Node> units = new ArrayList<>();
        GenUnits.collectArguments(p.sub, units);
        Moid m = JType.base(p.type);
        JType t = JType.of(g.a68, m);
        var cb = g.cb;
        switch (t) {
            case VOID -> {
                for (Node u : units) {
                    g.unit(u);
                    g.discard(Gen.mode(u));
                }
            }
            case STRUCT -> {
                cb.new_(Gen.CD_STRUCT);
                cb.dup();
                pushElements(g, units);
                cb.invokespecial(Gen.CD_STRUCT, "<init>", MethodTypeDesc.of(CD_void, Gen.CD_OBJECTS));
            }
            case ROW -> {
                Moid r = m.attribute == Attribute.FLEX_SYMBOL ? JType.base(m.sub) : m;
                if (units.isEmpty()) {
                    pushSkipValue(g, m);
                } else if (r.dim == 1 && JType.of(g.a68, JType.base(r.sub)).isPrimitive()) {
                    // A display of INT, REAL, CHAR, ...: unboxed storage (Row#store).
                    JType et = JType.of(g.a68, JType.base(r.sub));
                    cb.lconst_1();
                    cb.ldc((long) units.size());
                    cb.ldc(units.size());
                    cb.newarray(et.kind);
                    for (int i = 0; i < units.size(); i++) {
                        Node u = units.get(i);
                        cb.dup();
                        cb.ldc(i);
                        g.unit(u);
                        g.adjust(Gen.mode(u), r.sub, u);
                        cb.arrayStore(et.kind);
                    }
                    cb.invokestatic(Gen.CD_ROW, "ofStore", MethodTypeDesc.of(Gen.CD_ROW, CD_long, CD_long, CD_Object));
                } else if (r.dim == 1) {
                    cb.lconst_1();
                    cb.ldc((long) units.size());
                    pushElements(g, units);
                    cb.invokestatic(Gen.CD_ROW, "of1", MethodTypeDesc.of(Gen.CD_ROW, CD_long, CD_long, Gen.CD_OBJECTS));
                } else {
                    pushElements(g, units);
                    g.pushWhere(p);
                    cb.invokestatic(Gen.CD_ROW, "ofRows", MethodTypeDesc.of(Gen.CD_ROW, Gen.CD_OBJECTS, CD_String, CD_int));
                }
            }
            default -> throw new Gen.Unsupported(p, "collateral clause of mode " + t);
        }
    }

    /** Push an Object[] with the boxed copies of the values of UNITS. */
    private static void pushElements(Gen g, List<Node> units) {
        g.cb.ldc(units.size());
        g.cb.anewarray(CD_Object);
        for (int i = 0; i < units.size(); i++) {
            Node u = units.get(i);
            g.cb.dup();
            g.cb.ldc(i);
            g.unit(u);
            Moid um = Gen.mode(u);
            GenUnits.copyIfValue(g, um);
            g.box(um);
            g.cb.aastore();
        }
    }

    // ------------------------------------------------------------------
    // Slices and selections.
    // ------------------------------------------------------------------

    /** SLICE: PRIMARY GENERIC_ARGUMENT (subscripts only). */
    static void slice(Gen g, Node p) {
        Node prim = p.sub;
        Node arg = prim.next;
        List<Node> subs = new ArrayList<>();
        boolean trimming = !subscripts(arg, subs);
        Moid pm = JType.base(Gen.mode(prim));
        boolean name = JType.of(g.a68, pm) == JType.REF;
        var cb = g.cb;
        if (!trimming && isElementAccess(g, p)) {
            Hoist.Row h = Hoist.of(g, p);
            if (h != null) {
                Hoist.offsets(g, p, h);
                if (name) {
                    cb.invokestatic(Gen.CD_RT, "elemRef", MethodTypeDesc.of(Gen.CD_REF, Gen.CD_ROW, CD_int));
                } else {
                    Hoist.loadElem(g, h);
                }
                return;
            }
        }
        if (name) {
            loadRowOfName(g, prim, p);
        } else {
            g.unit(prim);
        }
        if (trimming) {
            List<Node> items = new ArrayList<>();
            indexerItems(arg, items);
            int newDims = 0;
            for (Node q : items) {
                if (q.attribute == Attribute.TRIMMER) {
                    newDims++;
                }
            }
            int row = cb.allocateLocal(TypeKind.REFERENCE);
            cb.astore(row);
            cb.new_(CD_TRIM);
            cb.dup();
            cb.aload(row);
            cb.ldc(newDims);
            cb.invokespecial(CD_TRIM, "<init>", MethodTypeDesc.of(CD_void, Gen.CD_ROW, CD_int));
            for (Node q : items) {
                if (q.attribute == Attribute.UNIT) {
                    g.unit(q);
                    cb.i2l();
                    g.pushWhere(q);
                    cb.invokevirtual(CD_TRIM, "sub", MethodTypeDesc.of(CD_TRIM, CD_long, CD_String, CD_int));
                } else {
                    trimmer(g, q);
                }
            }
            if (name) {
                cb.invokevirtual(CD_TRIM, "ref", MethodTypeDesc.of(Gen.CD_REF));
            } else {
                cb.invokevirtual(CD_TRIM, "row", MethodTypeDesc.of(Gen.CD_ROW));
            }
            return;
        }
        if (!isElementAccess(g, p)) {
            throw new Gen.Unsupported(p, "slice with trimmers");
        }
        offsets(g, p);
        if (name) {
            cb.invokestatic(Gen.CD_RT, "elemRef", MethodTypeDesc.of(Gen.CD_REF, Gen.CD_ROW, CD_int));
        } else {
            loadElem(g, p.type);
        }
    }

    /**
     * Whether the SLICE P selects one element: it has only subscripts, as many
     * as the row has dimensions.
     */
    static boolean isElementAccess(Gen g, Node p) {
        Node prim = p.sub;
        List<Node> subs = new ArrayList<>();
        if (!subscripts(prim.next, subs)) {
            return false;
        }
        Moid pm = JType.base(Gen.mode(prim));
        Moid rowMode = JType.of(g.a68, pm) == JType.REF ? JType.base(pm.sub) : pm;
        if (rowMode.attribute == Attribute.FLEX_SYMBOL) {
            rowMode = JType.base(rowMode.sub);
        }
        return subs.size() == rowMode.dim;
    }

    /**
     * For an element access P (see {@link #isElementAccess}) of a row or of
     * the name of a row: push the row and the index of the element relative
     * to the row's offset, checking the bounds. The sliced name is
     * dereferenced (and checked for NIL) as in {@link #slice}.
     */
    static Hoist.Row rowAndOffset(Gen g, Node p) {
        Hoist.Row h = Hoist.of(g, p);
        if (h != null) {
            Hoist.offsets(g, p, h);
            return h;
        }
        Node prim = p.sub;
        boolean name = JType.of(g.a68, JType.base(Gen.mode(prim))) == JType.REF;
        if (name) {
            loadRowOfName(g, prim, p);
        } else {
            g.unit(prim);
        }
        offsets(g, p);
        return null;
    }

    /** Push the row referred to by the name PRIM (checked for NIL). */
    static void loadRowOfName(Gen g, Node prim, Node p) {
        a68j.front.Tag x = g.ownCellTag(prim);
        if (x != null && g.ownCell(x).equals(Gen.CD_CELL)) {
            // A row variable: the field of its Cell.
            g.loadOwnCell(x, Gen.CD_CELL, p);
            g.cb.getfield(Gen.CD_CELL, "value", CD_Object);
        } else {
            g.unit(prim);
            g.pushWhere(p);
            g.cb.invokestatic(Gen.CD_RT, "deref", MethodTypeDesc.of(Gen.CD_REF, Gen.CD_REF, CD_String, CD_int));
            g.cb.invokevirtual(Gen.CD_REF, "get", MethodTypeDesc.of(CD_Object));
        }
        g.cb.checkcast(Gen.CD_ROW);
    }

    /** Stack: Row -> Row, int: the index of the element selected by the subscripts of P (relative to the row's offset). */
    private static void offsets(Gen g, Node p) {
        var cb = g.cb;
        List<Node> subs = new ArrayList<>();
        subscripts(p.sub.next, subs);
        int row = cb.allocateLocal(TypeKind.REFERENCE);
        cb.astore(row);
        if (subs.size() == 1) {
            // Row#index1, inlined: the JIT need not inline anything, which
            // matters in code compiled without a profile (after the first
            // hot loop of a long method compiled for OSR).
            g.unit(subs.get(0));
            int sub = cb.allocateLocal(TypeKind.INT);
            cb.istore(sub);
            int i = cb.allocateLocal(TypeKind.INT);
            Label bad = cb.newLabel();
            Label ok = cb.newLabel();
            cb.iload(sub);
            cb.aload(row);
            cb.getfield(Gen.CD_ROW, "lwb0", CD_int);
            cb.isub();
            cb.dup();
            cb.istore(i);
            cb.iflt(bad);
            cb.iload(i);
            cb.aload(row);
            cb.getfield(Gen.CD_ROW, "n0", CD_int);
            cb.if_icmpge(bad);
            cb.iload(i);
            cb.aload(row);
            cb.getfield(Gen.CD_ROW, "stride0", CD_int);
            cb.imul();
            cb.goto_(ok);
            cb.labelBinding(bad);
            cb.aload(row);
            cb.iload(sub);
            g.pushWhere(p);
            cb.invokevirtual(Gen.CD_ROW, "index1", MethodTypeDesc.of(CD_int, CD_int, CD_String, CD_int));
            cb.labelBinding(ok);
        } else {
            for (int k = 0; k < subs.size(); k++) {
                cb.aload(row);
                cb.ldc(k);
                g.unit(subs.get(k));
                g.pushWhere(p);
                cb.invokevirtual(Gen.CD_ROW, "indexOf", MethodTypeDesc.of(CD_int, CD_int, CD_int, CD_String, CD_int));
                if (k > 0) {
                    cb.iadd();
                }
            }
        }
        int off = cb.allocateLocal(TypeKind.INT);
        cb.istore(off);
        cb.aload(row);
        cb.iload(off);
    }

    /** The name of the Rt method reading an element of type T (elemI ...), or null. */
    static String elemName(JType t) {
        return switch (t) {
            case INT -> "elemI";
            case LONG -> "elemL";
            case FLOAT -> "elemF";
            case DOUBLE -> "elemD";
            case BOOL -> "elemZ";
            default -> null;
        };
    }

    /** {@link #loadElem(Gen, Moid)}, or for a hoisted row H (not null) {@link Hoist#loadElem}. */
    static void loadElem(Gen g, Moid m, Hoist.Row h) {
        if (h != null) {
            Hoist.loadElem(g, h);
        } else {
            loadElem(g, m);
        }
    }

    /** {@link #storeElem(Gen, Moid)}, or for a hoisted row H (not null) {@link Hoist#storeElem}. */
    static boolean storeElem(Gen g, Moid m, Hoist.Row h) {
        if (h != null) {
            Hoist.storeElem(g, h);
            return true;
        }
        return storeElem(g, m);
    }

    /** Stack: Row, int -> the element (of mode M), unboxed. */
    static void loadElem(Gen g, Moid m) {
        JType t = JType.of(g.a68, m);
        String n = switch (t) {
            case INT -> "elemI";
            case LONG -> "elemL";
            case FLOAT -> "elemF";
            case DOUBLE -> "elemD";
            case BOOL -> "elemZ";
            default -> null;
        };
        if (n != null) {
            // Inlined fast path for compact storage (see storeElem), else Rt.elemI ...
            var cb = g.cb;
            int idx = cb.allocateLocal(TypeKind.INT);
            int row = cb.allocateLocal(TypeKind.REFERENCE);
            cb.istore(idx);
            cb.astore(row);
            Label slow = cb.newLabel();
            Label end = cb.newLabel();
            ClassDesc arr = t.desc.arrayType();
            cb.aload(row);
            cb.getfield(Gen.CD_ROW, "store", CD_Object);
            cb.dup();
            cb.instanceOf(arr);
            cb.ifeq(slow);
            cb.checkcast(arr);
            cb.aload(row);
            cb.getfield(Gen.CD_ROW, "offset", CD_int);
            cb.iload(idx);
            cb.iadd();
            cb.arrayLoad(t.kind);
            cb.goto_(end);
            cb.labelBinding(slow);
            cb.pop();
            cb.aload(row);
            cb.iload(idx);
            cb.invokestatic(Gen.CD_RT, n, MethodTypeDesc.of(t.desc, Gen.CD_ROW, CD_int));
            cb.labelBinding(end);
        } else {
            g.cb.invokestatic(Gen.CD_RT, "elem", MethodTypeDesc.of(CD_Object, Gen.CD_ROW, CD_int));
            g.unbox(m);
        }
    }

    /**
     * Stack: Row, int, value of the primitive mode M -> (empty): store the
     * element. False (and nothing emitted) if M is not primitive.
     */
    static boolean storeElem(Gen g, Moid m) {
        JType t = JType.of(g.a68, m);
        String n = switch (t) {
            case INT -> "setElemI";
            case LONG -> "setElemL";
            case FLOAT -> "setElemF";
            case DOUBLE -> "setElemD";
            case BOOL -> "setElemZ";
            default -> null;
        };
        if (n == null) {
            return false;
        }
        // Inlined: a store into the compact storage (Row#store an int[] ...
        // of the element type) without a call, which the JIT may not inline
        // in code compiled without a profile; otherwise Rt.setElemI ...
        var cb = g.cb;
        int v = cb.allocateLocal(t.kind);
        int idx = cb.allocateLocal(TypeKind.INT);
        int row = cb.allocateLocal(TypeKind.REFERENCE);
        cb.storeLocal(t.kind, v);
        cb.istore(idx);
        cb.astore(row);
        Label slow = cb.newLabel();
        Label end = cb.newLabel();
        ClassDesc arr = t.desc.arrayType();
        cb.aload(row);
        cb.getfield(Gen.CD_ROW, "store", CD_Object);
        cb.dup();
        cb.instanceOf(arr);
        cb.ifeq(slow);
        cb.checkcast(arr);
        cb.aload(row);
        cb.getfield(Gen.CD_ROW, "offset", CD_int);
        cb.iload(idx);
        cb.iadd();
        cb.loadLocal(t.kind, v);
        cb.arrayStore(t.kind);
        cb.goto_(end);
        cb.labelBinding(slow);
        cb.pop();
        cb.aload(row);
        cb.iload(idx);
        cb.loadLocal(t.kind, v);
        cb.invokestatic(Gen.CD_RT, n, MethodTypeDesc.of(CD_void, Gen.CD_ROW, CD_int, t.desc));
        cb.labelBinding(end);
        return true;
    }

    static final ClassDesc CD_TRIM = ClassDesc.of("a68j.rt.Row$Trim");

    /** The subscripts (UNIT) and trimmers (TRIMMER) of an indexer, in order. */
    private static void indexerItems(Node p, List<Node> out) {
        for (Node q = p.sub; q != null; q = q.next) {
            switch (q.attribute) {
                case UNIT, TRIMMER -> out.add(q);
                case GENERIC_ARGUMENT_LIST, GENERIC_ARGUMENT -> indexerItems(q, out);
                default -> { }
            }
        }
    }

    /** Stack: Trim. TRIMMER: [UNIT] COLON [UNIT] [AT UNIT], [UNIT] AT UNIT, or empty. */
    private static void trimmer(Gen g, Node p) {
        var cb = g.cb;
        Node lo = null, hi = null, at = null;
        boolean colon = false;
        for (Node q = p.sub; q != null; q = q.next) {
            switch (q.attribute) {
                case COLON_SYMBOL -> colon = true;
                case AT_SYMBOL -> {
                    at = q.next;
                    q = q.next;
                }
                case UNIT -> {
                    if (!colon) {
                        lo = q;
                    } else {
                        hi = q;
                    }
                }
                default -> { }
            }
        }
        int flags = (p.sub != null ? 8 : 0) | (lo != null ? 1 : 0) | (hi != null ? 2 : 0) | (at != null ? 4 : 0);
        cb.ldc(flags);
        for (Node u : new Node[] {lo, hi, at}) {
            if (u == null) {
                cb.lconst_0();
            } else {
                g.unit(u);
                cb.i2l();
            }
        }
        g.pushWhere(p);
        cb.invokevirtual(CD_TRIM, "trim", MethodTypeDesc.of(CD_TRIM, CD_int, CD_long, CD_long, CD_long, CD_String, CD_int));
    }

    /** Collect the subscripts of an indexer; false if it contains trimmers. */
    static boolean subscripts(Node p, List<Node> out) {
        for (Node q = p.sub; q != null; q = q.next) {
            switch (q.attribute) {
                case UNIT -> out.add(q);
                case GENERIC_ARGUMENT_LIST -> {
                    if (!subscripts(q, out)) {
                        return false;
                    }
                }
                case TRIMMER -> {
                    return false;
                }
                default -> { }
            }
        }
        return true;
    }

    /**
     * The field index if the SELECTION P selects a field of a structure
     * through a name (x OF p, p a REF STRUCT), else -1.
     */
    static int nameFieldIndex(Gen g, Node p) {
        Node sel = p.sub;
        Node sec = sel.next;
        Moid sm = JType.base(Gen.mode(sec));
        if (JType.of(g.a68, sm) != JType.REF) {
            return -1;
        }
        Moid st = JType.base(sm.sub);
        if (st.attribute != Attribute.STRUCT_SYMBOL) {
            return -1;
        }
        String field = Gen.son(sel, Attribute.FIELD_IDENTIFIER).info.symbol;
        int idx = 0;
        for (Pack q = st.pack; q != null; q = q.next, idx++) {
            if (field.equals(q.text)) {
                return idx;
            }
        }
        return -1;
    }

    /**
     * For a selection P with {@link #nameFieldIndex} >= 0: push the structure
     * the name refers to (checking for NIL, as {@code Rt.fieldRef}).
     */
    static void nameFieldStruct(Gen g, Node p) {
        g.unit(p.sub.next);
        g.pushWhere(p);
        g.cb.invokestatic(Gen.CD_RT, "derefStruct", MethodTypeDesc.of(Gen.CD_STRUCT, Gen.CD_REF, CD_String, CD_int));
    }

    /** SELECTION: SELECTOR (FIELD_IDENTIFIER OF) SECONDARY. */
    static void selection(Gen g, Node p) {
        Node sel = p.sub;
        Node sec = sel.next;
        String field = Gen.son(sel, Attribute.FIELD_IDENTIFIER).info.symbol;
        Moid sm = JType.base(Gen.mode(sec));
        boolean name = JType.of(g.a68, sm) == JType.REF;
        Moid st = name ? JType.base(sm.sub) : sm;
        if (st.attribute != Attribute.STRUCT_SYMBOL) {
            // Selection from a row of structures: the row of the fields.
            Moid r = st.attribute == Attribute.FLEX_SYMBOL ? JType.base(st.sub) : st;
            Moid es = JType.base(r.sub);
            int idx = 0;
            Pack q = es.pack;
            while (q != null && !field.equals(q.text)) {
                q = q.next;
                idx++;
            }
            g.unit(sec);
            g.cb.ldc(idx);
            if (name) {
                g.pushWhere(p);
                g.cb.invokestatic(Gen.CD_RT, "selectRowRef", MethodTypeDesc.of(Gen.CD_REF, Gen.CD_REF, CD_int, CD_String, CD_int));
            } else {
                g.cb.invokevirtual(Gen.CD_ROW, "select", MethodTypeDesc.of(Gen.CD_ROW, CD_int));
            }
            return;
        }
        int idx = 0;
        Pack q = st.pack;
        while (q != null && !field.equals(q.text)) {
            q = q.next;
            idx++;
        }
        if (q == null) {
            throw new IllegalStateException("field " + field + " not found");
        }
        g.unit(sec);
        if (name) {
            g.cb.ldc(idx);
            g.pushWhere(p);
            g.cb.invokestatic(Gen.CD_RT, "fieldRef", MethodTypeDesc.of(Gen.CD_REF, Gen.CD_REF, CD_int, CD_String, CD_int));
        } else {
            g.cb.getfield(Gen.CD_STRUCT, "f", Gen.CD_OBJECTS);
            g.cb.ldc(idx);
            g.cb.aaload();
            g.unbox(p.type);
        }
    }
}
