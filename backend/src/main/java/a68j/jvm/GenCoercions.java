// a68j JVM backend: coercions (reference: a68-low-coercions.cc).
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.Attribute;
import a68j.front.Moid;
import a68j.front.Node;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/** Coercions inserted by the coercion inserter (stage 2). */
final class GenCoercions {
    private GenCoercions() {}

    /** DEREFERENCING: REF M -> M. */
    static void dereferencing(Gen g, Node p) {
        a68j.front.Tag x = Direct.of(g, p.sub);
        if (x != null) {
            g.load(x, p);
            return;
        }
        Node sl = GenUnits.strip(p.sub);
        if (sl.attribute == Attribute.FORMULA && Direct.assigningOperator(g, sl) != null && Direct.of(g, sl.sub) != null) {
            // (x +:= e) for a direct variable x: its new value.
            Intrinsics.assigningOperatorDirect(g, sl);
            g.load(Direct.of(g, sl.sub), sl);
            return;
        }
        if (sl.attribute == Attribute.SLICE && JType.of(g.a68, JType.base(Gen.mode(sl.sub))) == JType.REF
                && GenRows.isElementAccess(g, sl)) {
            // a[i] for a name a: the element itself, without making its name.
            Hoist.Row h = GenRows.rowAndOffset(g, sl);
            GenRows.loadElem(g, p.type, h);
            return;
        }
        if (sl.attribute == Attribute.FORMULA && Intrinsics.isElementAssigning(g, sl)) {
            // (a[i] +:= 1): the new value of the element, without making its name.
            Intrinsics.assigningOperatorElement(g, sl, true);
            return;
        }
        if (sl.attribute == Attribute.SELECTION) {
            int idx = GenRows.nameFieldIndex(g, sl);
            if (idx >= 0) {
                // x OF p for a name p: the field itself, without making its name.
                GenRows.nameFieldStruct(g, sl);
                g.cb.getfield(Gen.CD_STRUCT, "f", Gen.CD_OBJECTS);
                g.cb.ldc(idx);
                g.cb.aaload();
                g.unbox(p.type);
                return;
            }
        }
        a68j.front.Tag own = g.ownCellTag(p.sub);
        if (own != null && JType.base(JType.base(own.type).sub) == JType.base(p.type)) {
            // A variable: the field of its location (Gen#ownCell).
            ClassDesc c = g.ownCell(own);
            g.loadOwnCell(own, c, p);
            JType t = JType.of(g.a68, p.type);
            if (t.isPrimitive()) {
                g.cb.getfield(c, "v", t.desc);
            } else {
                g.cb.getfield(c, "value", CD_Object);
                g.unbox(p.type);
            }
            return;
        }
        g.unit(p.sub);
        g.pushWhere(p);
        JType t = JType.of(g.a68, p.type);
        if (t.isPrimitive()) {
            g.cb.invokestatic(Gen.CD_RT, "deref" + GenRows.typeLetter(t), MethodTypeDesc.of(t.desc, Gen.CD_REF, CD_String, CD_int));
            return;
        }
        g.cb.invokestatic(Gen.CD_RT, "deref", MethodTypeDesc.of(Gen.CD_REF, Gen.CD_REF, CD_String, CD_int));
        g.cb.invokevirtual(Gen.CD_REF, "get", MethodTypeDesc.of(CD_Object));
        g.unbox(p.type);
    }

    /** DEPROCEDURING: PROC M -> M (call without arguments). */
    static void deproceduring(Gen g, Node p) {
        Node id = GenUnits.strip(p.sub);
        if (id.attribute == Attribute.IDENTIFIER && g.isIntrinsic(id.tag)) {
            // A standard (or module) procedure without parameters: call it directly.
            Intrinsics.call(g, id, java.util.List.of(), p);
            g.adjust(JType.base(id.tag.type).sub, p.type, p);
            return;
        }
        if (id.attribute == Attribute.IDENTIFIER && GenUnits.directCall(g, id.tag, java.util.List.of(), p.type, p)) {
            return;
        }
        g.unit(p.sub);
        g.cb.iconst_0();
        g.cb.anewarray(CD_Object);
        GenUnits.invokeProc(g, p.type, p);
    }

    /** PROCEDURING: a jump used as a PROC VOID: a procedure performing the jump. */
    static void proceduring(Gen g, Node p) {
        Node j = p.sub;
        while (j != null && j.attribute != Attribute.JUMP) {
            j = j.sub;
        }
        if (j == null) {
            throw new Gen.Unsupported(p, "proceduring");
        }
        a68j.front.Tag t = Gen.son(j, Attribute.IDENTIFIER).tag;
        g.loadFrame(g.tableOwner.get(t.symbolTable));
        g.cb.ldc(g.labelNumber(t));
        g.cb.invokestatic(Gen.CD_RT, "jumper", MethodTypeDesc.of(Gen.CD_PROC, Gen.CD_FRAME, CD_int));
    }

    /** UNITING: M -> UNION (...). */
    static void uniting(Gen g, Node p) {
        Node s = p.sub;
        g.unit(s);
        if (JType.of(g.a68, s.type) == JType.UNION) {
            return;
        }
        g.box(s.type);
        g.cb.ldc(JType.unionTag(g, s.type));
        g.cb.invokestatic(Gen.CD_RT, "unite", MethodTypeDesc.of(Gen.CD_UNION, CD_Object, CD_int));
    }

    /**
     * ROWING (a68_lower_rowing): A -> [] A gives a row [1:1]; [] A -> [,] A
     * adds a first dimension [1:1] sharing the elements. Rowing a name yields
     * a name of the rowed value (NIL stays NIL); the elements of a rowed row
     * are shared, a rowed non-row value is copied (the JVM cannot alias a
     * variable with a row element).
     */
    static void rowing(Gen g, Node p) {
        Node s = p.sub;
        g.unit(s);
        if (JType.of(g.a68, p.type) == JType.REF) {
            Moid tm = JType.base(JType.base(p.type).sub);
            if (tm.attribute == Attribute.FLEX_SYMBOL) {
                tm = JType.base(tm.sub);
            }
            g.cb.ldc(tm.dim >= 2 ? 1 : 0);
            g.cb.invokestatic(Gen.CD_RT, "rowName", MethodTypeDesc.of(Gen.CD_REF, Gen.CD_REF, CD_boolean));
            return;
        }
        Moid target = JType.base(p.type);
        if (target.attribute == Attribute.FLEX_SYMBOL) {
            target = JType.base(target.sub);
        }
        if (target.dim >= 2) {
            g.cb.invokestatic(Gen.CD_RT, "addDim", MethodTypeDesc.of(Gen.CD_ROW, Gen.CD_ROW));
            return;
        }
        g.box(s.type);
        g.cb.invokestatic(Gen.CD_RT, "rowOf1", MethodTypeDesc.of(Gen.CD_ROW, CD_Object));
    }

    /** WIDENING: INT -> REAL, REAL -> COMPL, BITS -> [] BOOL etc. (a68_lower_widening). */
    static void widening(Gen g, Node p) {
        Node s = p.sub;
        g.unit(s);
        JType f = JType.of(g.a68, s.type);
        JType t = JType.of(g.a68, p.type);
        var md = g.a68.modes;
        if (t == JType.ROW) {
            Moid b = JType.base(s.type);
            int width = b == md.SHORT_SHORT_BITS ? 8 : b == md.SHORT_BITS ? 16 : f == JType.LONG ? 64 : 32;
            if (f == JType.INT) {
                g.cb.i2l();
            }
            g.cb.ldc(width);
            g.cb.invokestatic(Gen.CD_RT, "bitsToRow", MethodTypeDesc.of(Gen.CD_ROW, CD_long, CD_int));
        } else if (t == JType.STRUCT) {
            // A complex number with imaginary part zero.
            int v = g.cb.allocateLocal(f.kind);
            g.cb.storeLocal(f.kind, v);
            g.cb.new_(Gen.CD_STRUCT);
            g.cb.dup();
            g.cb.iconst_2();
            g.cb.anewarray(CD_Object);
            g.cb.dup();
            g.cb.iconst_0();
            g.cb.loadLocal(f.kind, v);
            g.box(s.type);
            g.cb.aastore();
            g.cb.dup();
            g.cb.iconst_1();
            if (f == JType.FLOAT) {
                g.cb.fconst_0();
            } else {
                g.cb.dconst_0();
            }
            g.box(s.type);
            g.cb.aastore();
            g.cb.invokespecial(Gen.CD_STRUCT, "<init>", MethodTypeDesc.of(CD_void, Gen.CD_OBJECTS));
        } else if (f == JType.INT && t == JType.FLOAT) {
            g.cb.i2f();
        } else if (f == JType.INT && t == JType.DOUBLE) {
            g.cb.i2d();
        } else if (f == JType.LONG && t == JType.DOUBLE) {
            g.cb.l2d();
        } else if (f == JType.LONG && t == JType.FLOAT) {
            g.cb.l2f();
        } else if (f == JType.FLOAT && t == JType.DOUBLE) {
            g.cb.f2d();
        } else {
            throw new Gen.Unsupported(p, "widening " + f + " -> " + t);
        }
    }

    /** VOIDING: evaluate and discard. */
    static void voiding(Gen g, Node p) {
        Node d = GenUnits.strip(p.sub);
        if (d.attribute == Attribute.ASSIGNATION && Direct.of(g, d.sub) != null) {
            // x := e for a direct variable x.
            g.line(d);
            Node src = d.sub.next.next;
            g.unit(src);
            g.store(Direct.of(g, d.sub));
            return;
        }
        if (d.attribute == Attribute.ASSIGNATION) {
            // a[i] := e for a row of a primitive mode: store the element
            // directly (same order: the element is selected, then e is
            // evaluated).
            Node dst = GenUnits.strip(d.sub);
            Moid em = JType.base(JType.base(Gen.mode(d.sub)).sub);
            if (dst.attribute == Attribute.SLICE && JType.of(g.a68, JType.base(Gen.mode(dst.sub))) == JType.REF
                    && GenRows.isElementAccess(g, dst) && JType.of(g.a68, em).isPrimitive()) {
                g.line(d);
                Hoist.Row h = GenRows.rowAndOffset(g, dst);
                Node src = d.sub.next.next;
                g.unit(src);
                g.adjust(Gen.mode(src), em, d);
                GenRows.storeElem(g, em, h);
                return;
            }
            // x := e for a variable x of a primitive mode: the field of its location.
            a68j.front.Tag own = g.ownCellTag(d.sub);
            if (own != null && JType.of(g.a68, em).isPrimitive()) {
                g.line(d);
                ClassDesc c = g.ownCell(own);
                g.loadOwnCell(own, c, d);
                Node src = d.sub.next.next;
                g.unit(src);
                g.adjust(Gen.mode(src), em, d);
                g.cb.putfield(c, "v", JType.of(g.a68, em).desc);
                return;
            }
            // x OF p := e for a name p (a field that is not a row: rows are
            // assigned element by element, see GenUnits.assignTo).
            if (dst.attribute == Attribute.SELECTION && GenRows.nameFieldIndex(g, dst) >= 0
                    && JType.of(g.a68, em) != JType.ROW) {
                g.line(d);
                GenRows.nameFieldStruct(g, dst);
                g.cb.ldc(GenRows.nameFieldIndex(g, dst));
                Node src = d.sub.next.next;
                g.unit(src);
                Moid sm = Gen.mode(src);
                GenUnits.copyIfValue(g, sm);
                g.box(sm);
                g.cb.invokestatic(Gen.CD_RT, "assignField", MethodTypeDesc.of(CD_void, Gen.CD_STRUCT, CD_int, CD_Object));
                return;
            }
        }
        if (d.attribute == Attribute.FORMULA && Direct.assigningOperator(g, d) != null && Direct.of(g, d.sub) != null) {
            Intrinsics.assigningOperatorDirect(g, d);
            return;
        }
        if (d.attribute == Attribute.FORMULA && Intrinsics.isElementAssigning(g, d)) {
            g.line(d);
            Intrinsics.assigningOperatorElement(g, d);
            return;
        }
        Node s = p.sub;
        g.unit(s);
        g.discard(s.type);
    }
}
