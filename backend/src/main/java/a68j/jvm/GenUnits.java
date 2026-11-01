// a68j JVM backend: units (reference: a68-low-units.cc).
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.Attribute;
import a68j.front.Moid;
import a68j.front.Node;
import a68j.front.Tag;

import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

/** Units. Each leaves a value of JType.of(p.type) on the stack. */
final class GenUnits {
    private GenUnits() {}

    // ------------------------------------------------------------------
    // Helpers shared by the generators.
    // ------------------------------------------------------------------

    /** Copy a STRUCT, ROW or UNION value on the stack (value semantics, a68_low_dup). */
    static void copyIfValue(Gen g, Moid m) {
        JType t = JType.of(g.a68, m);
        if (t == JType.ROW || t == JType.STRUCT || t == JType.UNION) {
            g.cb.invokestatic(Gen.CD_RT, "copyValue", MethodTypeDesc.of(CD_Object, CD_Object));
            g.cb.checkcast(t.desc);
        }
    }

    /**
     * Whether the unit P yields a value that needs no copy when it is bound
     * to an identity or a parameter: the value of an identity or parameter
     * of a row, structure or union mode (Gen#valueConsts), or a slice or
     * field of one. No name refers to such a value and nothing changes it,
     * and the identity or parameter it goes to cannot change it either, so
     * sharing it cannot be observed.
     */
    static boolean ownedValue(Gen g, Node p) {
        Node s = strip(p);
        while (s != null) {
            switch (s.attribute) {
                case IDENTIFIER -> {
                    return s.tag != null && g.valueConsts.contains(s.tag);
                }
                case SLICE -> s = strip(s.sub);
                case SELECTION -> s = strip(s.sub.next);
                default -> {
                    return false;
                }
            }
        }
        return false;
    }

    /** copyIfValue for a value of mode M given by the unit P bound to an identity or parameter. */
    static void copyIfValue(Gen g, Node p, Moid m) {
        if (!ownedValue(g, p)) {
            copyIfValue(g, m);
        }
    }

    /** Stack: ref, boxed value. Stores the value into the name (NIL check included). */
    static void assignBoxed(Gen g, Moid refMode, Node at) {
        g.pushWhere(at);
        g.cb.invokestatic(Gen.CD_RT, "assign",
                MethodTypeDesc.of(CD_void, Gen.CD_REF, CD_Object, CD_String, CD_int));
    }

    /** Push the default (SKIP) value of mode M (a68_get_skip_tree). */
    static void pushSkip(Gen g, Moid m) {
        if (JType.base(m) == g.a68.modes.CHAR) {
            g.cb.ldc((int) ' ');
            return;
        }
        switch (JType.of(g.a68, m)) {
            case VOID -> { }
            case BOOL, INT -> g.cb.iconst_0();
            case LONG -> g.cb.lconst_0();
            case FLOAT -> g.cb.fconst_0();
            case DOUBLE -> g.cb.dconst_0();
            default -> GenRows.pushSkipValue(g, m);
        }
    }

    /** The innermost node below wrappers (UNIT, TERTIARY, SECONDARY, PRIMARY, ENCLOSED_CLAUSE...). */
    static Node strip(Node p) {
        while (p != null && p.sub != null && p.sub.next == null
                && (p.attribute == Attribute.UNIT || p.attribute == Attribute.TERTIARY
                    || p.attribute == Attribute.SECONDARY || p.attribute == Attribute.PRIMARY)) {
            p = p.sub;
        }
        return p;
    }

    /** collect_call_arguments: the UNITs below P. */
    static void collectArguments(Node p, List<Node> args) {
        for (; p != null; p = p.next) {
            if (p.attribute == Attribute.UNIT) {
                args.add(p);
            } else {
                collectArguments(p.sub, args);
            }
        }
    }

    /** Push an Object[] with the boxed (and copied) values of ARGS. */
    static void pushArgArray(Gen g, List<Node> args) {
        g.cb.ldc(args.size());
        g.cb.anewarray(CD_Object);
        for (int i = 0; i < args.size(); i++) {
            Node a = args.get(i);
            g.cb.dup();
            g.cb.ldc(i);
            g.unit(a);
            copyIfValue(g, a, a.type);
            g.box(a.type);
            g.cb.aastore();
        }
    }

    /**
     * A call of the procedure or operator T with the arguments ARGS, if T is
     * a procedure or brief operator declared with a routine text in this
     * compilation (Gen#staticProcs): its method is invoked directly with the
     * frame the routine text would have captured. False (nothing emitted)
     * otherwise.
     */
    static boolean directCall(Gen g, Tag t, List<Node> args, Moid ret) {
        Node hole = t == null ? null : g.javaProcs.get(t);
        if (hole != null) {
            GenJava.directCall(g, hole, args, ret);
            return true;
        }
        Node text = t == null ? null : g.staticProcs.get(t);
        Gen.Routine r = text == null ? null : g.routineOf.get(text);
        if (r == null) {
            return false;
        }
        boolean encloses = false;
        for (Gen.Routine x = g.cur; x != null; x = x.parent) {
            if (x == r.parent) {
                encloses = true;
                break;
            }
        }
        if (!encloses) {
            return false;
        }
        g.routineFor(text);
        MethodTypeDesc typed = g.typedEntry(r);
        g.loadFrame(r.parent);
        if (typed == null) {
            pushArgArray(g, args);
            g.cb.invokestatic(g.thisClass, r.method, Gen.MT_ROUTINE);
            g.unbox(ret);
            return true;
        }
        List<Tag> params = parameters(text);
        for (int i = 0; i < args.size(); i++) {
            Node a = args.get(i);
            g.unit(a);
            copyIfValue(g, a, a.type);
            g.adjust(a.type, params.get(i).type, a);
        }
        g.cb.invokestatic(g.thisClass, r.method + "t", typed);
        g.adjust(text.type.sub, ret, text);
        return true;
    }

    /** Stack: Proc, Object[] args. Calls it and unboxes the result of mode RET. */
    static void invokeProc(Gen g, Moid ret, Node at) {
        g.cb.invokevirtual(Gen.CD_PROC, "call", MethodTypeDesc.of(CD_Object, Gen.CD_OBJECTS));
        g.unbox(ret);
    }

    // ------------------------------------------------------------------
    // Units.
    // ------------------------------------------------------------------

    /**
     * DENOTATION: [LONGETY | SHORTETY | FIXETY] plain denotation, TRUE, FALSE,
     * EMPTY. The value is pushed in the representation of the DENOTATION's
     * mode (the plain denotation below a LONGETY has the unlengthened mode).
     */
    static void denotation(Gen g, Node p) {
        Node d = p;
        if (p.attribute == Attribute.DENOTATION) {
            d = p.sub;
            while (d.next != null) {
                d = d.next;
            }
        }
        Moid m = p.type != null ? p.type : d.type;
        String s = d.info.symbol;
        JType t = JType.of(g.a68, m);
        switch (d.attribute) {
            case TRUE_SYMBOL -> g.cb.iconst_1();
            case FALSE_SYMBOL -> g.cb.iconst_0();
            case EMPTY_SYMBOL -> { }
            case INT_DENOTATION -> {
                long v = Long.parseUnsignedLong(s.replace(" ", ""));
                pushIntegral(g, t, v);
            }
            case BITS_DENOTATION -> {
                String x = s.replace(" ", "");
                int r = x.toLowerCase().indexOf('r');
                long v = Long.parseUnsignedLong(x.substring(r + 1), Integer.parseInt(x.substring(0, r)));
                pushIntegral(g, t, v);
            }
            case REAL_DENOTATION -> {
                String x = s.replace(" ", "").replace('\\', 'e').replace('E', 'e');
                if (x.startsWith(".")) {
                    x = "0" + x;
                }
                if (t == JType.FLOAT) {
                    // As C: the denotation is converted to float directly.
                    g.cb.ldc(Float.parseFloat(x));
                } else {
                    g.cb.ldc(Double.parseDouble(x));
                }
            }
            case ROW_CHAR_DENOTATION -> {
                s = processBreaks(s);
                if (t == JType.INT) {
                    g.cb.ldc(s.isEmpty() ? 0 : s.codePointAt(0));
                } else {
                    g.cb.ldc(s);
                    g.cb.invokestatic(Gen.CD_ROW, "ofString", MethodTypeDesc.of(Gen.CD_ROW, CD_String));
                }
            }
            default -> throw new Gen.Unsupported(p, "denotation " + d.attribute);
        }
    }

    /**
     * a68_string_process_breaks: expand the string breaks 'n 't 'r 'f '' and
     * '(u..., U...), then decode the result as UTF-8 like ga68 does
     * (a68_u8_to_u32): the source text is read byte by byte (ISO 8859-1), so a
     * non-ASCII character of the source is a sequence of UTF-8 bytes here,
     * and a character point break is appended in UTF-8.
     */
    static String processBreaks(String str) {
        java.io.ByteArrayOutputStream sb = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < str.length();) {
            char c = str.charAt(i);
            if (c != '\'' || i + 1 >= str.length()) {
                sb.write(c);
                i++;
                continue;
            }
            char e = str.charAt(i + 1);
            i += 2;
            switch (e) {
                case 'n' -> sb.write('\n');
                case 't' -> sb.write('\t');
                case 'r' -> sb.write('\r');
                case 'f' -> sb.write('\f');
                case '(' -> {
                    while (i < str.length()) {
                        char d = str.charAt(i);
                        if (d == ')') {
                            i++;
                            break;
                        }
                        if (d == ',' || Character.isWhitespace(d)) {
                            i++;
                            continue;
                        }
                        i++; // u or U
                        int j = i;
                        while (j < str.length() && Character.digit(str.charAt(j), 16) >= 0) {
                            j++;
                        }
                        sb.writeBytes(new String(Character.toChars(Integer.parseInt(str.substring(i, j), 16)))
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        i = j;
                    }
                }
                default -> sb.write(e);
            }
        }
        return new String(sb.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void pushIntegral(Gen g, JType t, long v) {
        if (t == JType.LONG) {
            g.cb.ldc(v);
        } else {
            g.cb.ldc((int) v);
        }
    }

    static void skip(Gen g, Node p) {
        pushSkip(g, p.type);
    }

    static void nihil(Gen g, Node p) {
        // NIL is not voided: in a VOID context it yields nothing.
        if (JType.of(g.a68, p.type) != JType.VOID) {
            g.cb.aconst_null();
        }
    }

    static void identifier(Gen g, Node p) {
        Tag t = p.tag;
        if (g.isIntrinsic(t)) {
            Intrinsics.identifier(g, p);
        } else {
            g.load(t, p);
        }
        g.adjust(t.type, p.type, p);
    }

    /** Dyadic formula: operand OPERATOR operand. */
    static void formula(Gen g, Node p) {
        Node lhs = p.sub;
        Node op = lhs.next;
        if (op == null) {
            // A formula consisting of a monadic formula or operand only.
            g.unit(lhs);
            g.adjust(Gen.mode(lhs), p.type, p);
            return;
        }
        Node rhs = op.next;
        if (g.isIntrinsic(op.tag)) {
            Intrinsics.dyadic(g, op, lhs, rhs, p);
        } else if (directCall(g, op.tag, List.of(lhs, rhs), op.tag.type.sub)) {
            // done
        } else {
            g.load(op.tag, op);
            pushArgArray(g, List.of(lhs, rhs));
            invokeProc(g, op.tag.type.sub, p);
        }
        g.adjust(op.tag.type.sub, p.type, p);
    }

    /** Monadic formula: OPERATOR operand. */
    static void monadicFormula(Gen g, Node p) {
        Node op = p.sub;
        Node arg = op.next;
        if (g.isIntrinsic(op.tag)) {
            Intrinsics.monadic(g, op, arg, p);
        } else if (directCall(g, op.tag, List.of(arg), op.tag.type.sub)) {
            // done
        } else {
            g.load(op.tag, op);
            pushArgArray(g, List.of(arg));
            invokeProc(g, op.tag.type.sub, p);
        }
        g.adjust(op.tag.type.sub, p.type, p);
    }

    /** destination := source; yields the destination name. */
    static void assignation(Gen g, Node p) {
        Node dst = p.sub;
        Node src = dst.next.next;
        g.line(p);
        g.unit(dst);
        g.cb.dup();
        assignTo(g, Gen.mode(dst), src, p);
    }

    /**
     * Stack: name of mode REFMODE. Evaluates SRC and stores a copy of its
     * value into the name (a68_low_assignation): a row is stored element by
     * element unless the name is flexible.
     */
    static void assignTo(Gen g, Moid refMode, Node src, Node at) {
        g.unit(src);
        Moid sm = Gen.mode(src);
        copyIfValue(g, sm);
        if (JType.of(g.a68, sm) == JType.ROW) {
            Moid x = JType.base(JType.base(refMode).sub);
            boolean flex = x == g.a68.modes.STRING || (x != null && x.attribute == Attribute.FLEX_SYMBOL);
            g.cb.ldc(flex ? 1 : 0);
            g.pushWhere(at);
            g.cb.invokestatic(Gen.CD_RT, "assignRow",
                    MethodTypeDesc.of(CD_void, Gen.CD_REF, Gen.CD_ROW, CD_boolean, CD_String, CD_int));
            return;
        }
        JType t = JType.of(g.a68, sm);
        if (t.isPrimitive() && JType.of(g.a68, JType.base(refMode).sub) == t) {
            g.pushWhere(at);
            g.cb.invokestatic(Gen.CD_RT, "assign" + GenRows.typeLetter(t),
                    MethodTypeDesc.of(CD_void, Gen.CD_REF, t.desc, CD_String, CD_int));
            return;
        }
        g.box(sm);
        assignBoxed(g, refMode, at);
    }

    static void identityRelation(Gen g, Node p) {
        Node lhs = p.sub;
        Node rel = lhs.next;
        Node rhs = rel.next;
        g.unit(lhs);
        g.unit(rhs);
        g.cb.invokestatic(Gen.CD_RT, "isSame", MethodTypeDesc.of(CD_boolean, Gen.CD_REF, Gen.CD_REF));
        if (rel.attribute == Attribute.ISNT_SYMBOL) {
            g.cb.iconst_1();
            g.cb.ixor();
        }
    }

    static void logicFunction(Gen g, Node p) {
        Node lhs = p.sub;
        Node rhs = lhs.next.next;
        java.lang.classfile.Label end = g.cb.newLabel();
        g.unit(lhs);
        g.cb.dup();
        if (p.attribute == Attribute.AND_FUNCTION) {
            g.cb.ifeq(end);
        } else {
            g.cb.ifne(end);
        }
        g.cb.pop();
        g.unit(rhs);
        g.cb.labelBinding(end);
    }

    static void slice(Gen g, Node p) {
        GenRows.slice(g, p);
    }

    static void selection(Gen g, Node p) {
        GenRows.selection(g, p);
    }

    /** CAST: DECLARER ENCLOSED_CLAUSE. */
    static void cast(Gen g, Node p) {
        Node e = p.sub.next;
        g.unit(e);
        g.adjust(e.type, p.type, p);
    }

    /** CALL: PRIMARY GENERIC_ARGUMENT. */
    static void call(Gen g, Node p) {
        Node prim = p.sub;
        List<Node> args = new ArrayList<>();
        collectArguments(prim.next, args);
        Moid procMode = prim.type;
        int params = 0;
        for (a68j.front.Pack z = JType.base(procMode).pack; z != null; z = z.next) {
            params++;
        }
        if (args.size() < params) {
            // A call with empty arguments (f (3, )) is a partial parametrisation
            // (an Algol 68 Genie extension); ga68 does not lower it either.
            throw new Gen.Unsupported(p, "partial parametrisation");
        }
        Node id = strip(prim);
        g.line(p);
        if (id.attribute == Attribute.IDENTIFIER && g.isIntrinsic(id.tag)) {
            Intrinsics.call(g, id, args, p);
        } else if (id.attribute == Attribute.IDENTIFIER && directCall(g, id.tag, args, procMode.sub)) {
            // done
        } else {
            g.unit(prim);
            pushArgArray(g, args);
            invokeProc(g, procMode.sub, p);
        }
        g.adjust(procMode.sub, p.type, p);
    }

    static void generator(Gen g, Node p) {
        // GENERATOR: LOC_SYMBOL/HEAP_SYMBOL/NEW_SYMBOL DECLARER.
        Node declarer = p.sub.next;
        GenRows.generate(g, declarer, p.type.sub, p);
    }

    /** ROUTINE_TEXT: a closure over the current frame. */
    static void routineText(Gen g, Node p) {
        Gen.Routine r = g.routineFor(p);
        g.cb.new_(Gen.CD_CLOSURE);
        g.cb.dup();
        g.cb.ldc(g.handle(r));
        g.cb.aload(g.frameLocal);
        g.cb.invokespecial(Gen.CD_CLOSURE, "<init>",
                MethodTypeDesc.of(CD_void, CD_MethodHandle, Gen.CD_FRAME));
    }

    /** The parameters of the routine text RT, in order. */
    static List<Tag> parameters(Node rt) {
        List<Node> params = new ArrayList<>();
        Node pack = Gen.son(rt, Attribute.PARAMETER_PACK);
        if (pack != null) {
            collectParameters(pack.sub, params);
        }
        List<Tag> ts = new ArrayList<>();
        for (Node q : params) {
            ts.add(q.tag);
        }
        return ts;
    }

    /**
     * Body of a routine method: bind the parameters, evaluate the unit. The
     * boxed entry (Frame, Object[]) leaves the result boxed; the TYPED entry
     * (Gen#typedEntry) takes the parameters in locals 1... and leaves the
     * result unboxed.
     */
    static void routineBody(Gen g, Gen.Routine r, boolean typed) {
        Node rt = r.node;
        List<Tag> params = parameters(rt);
        int slot = 1;
        for (int i = 0; i < params.size(); i++) {
            Tag t = params.get(i);
            if (typed) {
                JType jt = JType.of(g.a68, t.type);
                g.cb.loadLocal(jt.kind, slot);
                slot += jt.kind.slotSize();
            } else {
                g.cb.aload(1);
                g.cb.ldc(i);
                g.cb.aaload();
                g.unbox(t.type);
            }
            g.defineAndStore(t);
        }
        Node body = rt.sub;
        while (body.next != null) {
            body = body.next;
        }
        Moid ret = rt.type.sub;
        g.unit(body);
        g.adjust(body.type, ret, body);
        if (!typed) {
            g.box(ret);
        }
    }

    private static void collectParameters(Node p, List<Node> out) {
        for (; p != null; p = p.next) {
            if (p.attribute == Attribute.IDENTIFIER) {
                out.add(p);
            } else if (p.attribute != Attribute.DECLARER) {
                collectParameters(p.sub, out);
            }
        }
    }
}
