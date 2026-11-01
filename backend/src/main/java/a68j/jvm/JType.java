// a68j JVM backend: JVM representation of modes (docs/BACKEND.md section 1).
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.A68;
import a68j.front.Attribute;
import a68j.front.Modes;
import a68j.front.Moid;

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;

/** The JVM representation of a mode. */
public enum JType {
    VOID(CD_void, TypeKind.VOID, null),
    BOOL(CD_boolean, TypeKind.BOOLEAN, CD_Boolean),
    INT(CD_int, TypeKind.INT, CD_Integer),
    LONG(CD_long, TypeKind.LONG, CD_Long),
    FLOAT(CD_float, TypeKind.FLOAT, CD_Float),
    DOUBLE(CD_double, TypeKind.DOUBLE, CD_Double),
    REF(Gen.CD_REF, TypeKind.REFERENCE, null),
    ROW(Gen.CD_ROW, TypeKind.REFERENCE, null),
    STRUCT(Gen.CD_STRUCT, TypeKind.REFERENCE, null),
    UNION(Gen.CD_UNION, TypeKind.REFERENCE, null),
    PROC(Gen.CD_PROC, TypeKind.REFERENCE, null),
    OBJECT(CD_Object, TypeKind.REFERENCE, null);

    /** Descriptor of the unboxed form. */
    public final ClassDesc desc;
    public final TypeKind kind;
    /** Box class for primitives, null for reference types. */
    public final ClassDesc box;

    JType(ClassDesc desc, TypeKind kind, ClassDesc box) {
        this.desc = desc;
        this.kind = kind;
        this.box = box;
    }

    public boolean isPrimitive() {
        return box != null;
    }

    /** Follow mode indicants (MODE A = ...) to the mode they stand for. */
    public static Moid base(Moid m) {
        int guard = 0;
        while (m != null && m.equivalentMode != null && m.equivalentMode != m && guard++ < 1000) {
            m = m.equivalentMode;
        }
        return m;
    }

    /**
     * The number a united value carries for a value of mode M: the number of
     * the (deflexed) mode, so that STRING and [] CHAR agree
     * (a68_united_mode_index compares with SAFE_DEFLEXING).
     */
    public static int unionTag(Gen g, Moid m) {
        m = base(m);
        if (m != null && m.attribute == Attribute.FLEX_SYMBOL) {
            m = base(m.sub);
        }
        if (m == null) {
            return 0;
        }
        // Mode numbers differ between compilations, so a value united in a
        // module would not conform in a program using it: the number is a
        // hash of the structure of the mode instead (non-negative; -1 marks
        // the uninitialised union of a generator).
        String key = structuralKey(g, m, new java.util.ArrayList<>());
        if (System.getenv("A68J_DEBUG_UNION") != null) {
            System.err.println("union tag " + (key.hashCode() & 0x7fffffff) + " " + key);
        }
        return key.hashCode() & 0x7fffffff;
    }

    /** A description of the structure of M, the same in every compilation. */
    static String structuralKey(Gen g, Moid m, java.util.List<Moid> path) {
        m = base(m);
        if (m == null) {
            return "void";
        }
        for (int i = 0; i < path.size(); i++) {
            if (path.get(i) == m) {
                return "^" + (path.size() - i);
            }
        }
        if (m == g.a68.modes.VOID) {
            return "void";
        }
        String c = Intrinsics.code(g, m);
        if (!c.equals("row") && !c.equals("ref") && !c.equals("struct") && !c.equals("union")
                && !c.equals("proc") && !c.equals("x")) {
            return c;
        }
        path.add(m);
        StringBuilder sb = new StringBuilder();
        switch (m.attribute) {
            case FLEX_SYMBOL -> sb.append("flex(").append(structuralKey(g, m.sub, path)).append(')');
            case ROW_SYMBOL -> sb.append("row").append(m.dim).append('(').append(structuralKey(g, m.sub, path)).append(')');
            case REF_SYMBOL -> sb.append("ref(").append(structuralKey(g, m.sub, path)).append(')');
            case STRUCT_SYMBOL -> {
                sb.append("struct(");
                for (a68j.front.Pack z = m.pack; z != null; z = z.next) {
                    sb.append(structuralKey(g, z.type, path)).append(' ').append(z.text).append(',');
                }
                sb.append(')');
            }
            case UNION_SYMBOL -> {
                java.util.List<String> l = new java.util.ArrayList<>();
                for (a68j.front.Pack z = m.pack; z != null; z = z.next) {
                    l.add(structuralKey(g, z.type, path));
                }
                java.util.Collections.sort(l);
                sb.append("union(").append(String.join(",", l)).append(')');
            }
            case PROC_SYMBOL -> {
                sb.append("proc(");
                for (a68j.front.Pack z = m.pack; z != null; z = z.next) {
                    sb.append(structuralKey(g, z.type, path)).append(',');
                }
                sb.append(')').append(structuralKey(g, m.sub, path));
            }
            // Independent of the stropping regime the mode was written in.
            default -> sb.append(a68j.front.MoidsToString.moidToString(g.a68, m, 1 << 20, null)
                    .toLowerCase(java.util.Locale.ROOT).replace("_", "").replace(" ", ""));
        }
        path.remove(path.size() - 1);
        return sb.toString();
    }

    /** JVM representation of mode M. */
    public static JType of(A68 a68, Moid m) {
        m = base(m);
        if (m == null) {
            return VOID;
        }
        Modes md = a68.modes;
        if (m == md.VOID) {
            return VOID;
        }
        if (m == md.BOOL) {
            return BOOL;
        }
        if (m == md.CHAR || m == md.INT || m == md.SHORT_INT || m == md.SHORT_SHORT_INT
                || m == md.BITS || m == md.SHORT_BITS || m == md.SHORT_SHORT_BITS
                || m == md.FILE || m == md.CHANNEL) {
            return INT;
        }
        if (m == md.LONG_INT || m == md.LONG_LONG_INT || m == md.LONG_BITS || m == md.LONG_LONG_BITS
                || m == md.WORD_INT || m == md.WORD_BITS) {
            return LONG;
        }
        if (m == md.REAL) {
            return FLOAT;
        }
        if (m == md.LONG_REAL || m == md.LONG_LONG_REAL || m == md.WORD_REAL) {
            return DOUBLE;
        }
        Attribute a = m.attribute;
        if (a == Attribute.REF_SYMBOL) {
            return REF;
        }
        if (a == Attribute.ROW_SYMBOL || a == Attribute.FLEX_SYMBOL) {
            return ROW;
        }
        if (a == Attribute.STRUCT_SYMBOL) {
            return STRUCT;
        }
        if (a == Attribute.UNION_SYMBOL) {
            return UNION;
        }
        if (a == Attribute.PROC_SYMBOL) {
            return PROC;
        }
        return OBJECT;
    }
}
