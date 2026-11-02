// a68j JVM backend: the standard environment and imported modules
// (docs/BACKEND.md section 4; reference: a68-low-prelude.cc and friends).
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.Attribute;
import a68j.front.Moid;
import a68j.front.MoidsToString;
import a68j.front.Node;
import a68j.front.Pack;
import a68j.front.Tag;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Code for tags of the standard environment and of imported modules. Simple
 * operators on the basic modes are emitted inline; everything else calls a
 * static method of {@code a68j.rt.Std} (standard prelude) or
 * {@code a68j.rt.Posix} (POSIX module), found by name and JVM signature.
 */
final class Intrinsics {
    private Intrinsics() {}

    /** Name of a standard-environment tag (operator symbols as in the prelude, e.g. "ABS", "+"). */
    static String name(Tag t) {
        return t.node != null && t.node.info != null ? t.node.info.symbol : "?";
    }

    /** Lookup key: name and mode, e.g. "+ proc (int, int) int". */
    static String key(Gen g, Tag t) {
        return name(t) + " " + MoidsToString.moidToString(g.a68, t.type, 1000, null).toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // Tags of compiled modules (standard.a68).
    // ------------------------------------------------------------------

    static final ClassDesc CD_MODULES = ClassDesc.of("a68j.rt.Modules");

    /** Whether T is a tag of a module compiled to a class (all imported modules but POSIX). */
    static boolean isModuleTag(Tag t) {
        return t.moif != null && !"POSIX".equals(t.moif.name);
    }

    /**
     * The modules written in Algol 68 that are bundled with a68j-backend.jar
     * (backend/lib/*.a68, compiled by build.sh to classes a68j.lib.X and
     * export data /a68j/lib/x.m68, see Compile#bundledLibDir).
     */
    static final java.util.Set<String> BUNDLED_MODULES = java.util.Set.of("LISTS", "HASHMAPS", "GENIETRANSPUT");

    /** The class of the module with the given name. */
    static String moduleClass(String module) {
        String n = module.substring(0, 1).toUpperCase(Locale.ROOT) + module.substring(1).toLowerCase(Locale.ROOT);
        return switch (module) {
            case "STANDARD", "TRANSPUT" -> "a68j.lib." + n;
            default -> BUNDLED_MODULES.contains(module) ? "a68j.lib." + n : n;
        };
    }

    /**
     * The class whose prelude and postlude are called when the module NAME is
     * accessed, or null for the modules of libga68 (their preludes have no
     * observable effect on the JVM).
     */
    static String ludeClass(String module) {
        return switch (module) {
            case "STANDARD", "TRANSPUT", "POSIX" -> null;
            default -> moduleClass(module);
        };
    }

    /** Key of a public tag: mangled name and a structural description of its mode. */
    static String moduleKey(Gen g, Tag t) {
        return mangle(name(t)) + " " + modeKey(g, t.type, 0);
    }

    private static String modeKey(Gen g, Moid m, int depth) {
        m = JType.base(m);
        if (m == null) {
            return "void";
        }
        if (depth > 12) {
            return "?";
        }
        if (m.attribute == a68j.front.Attribute.FLEX_SYMBOL) {
            m = JType.base(m.sub);
        }
        String c = code(g, m);
        switch (c) {
            case "row" -> {
                return "row" + m.dim + "(" + modeKey(g, m.sub, depth + 1) + ")";
            }
            case "ref" -> {
                return "ref(" + modeKey(g, m.sub, depth + 1) + ")";
            }
            case "struct" -> {
                List<String> l = new ArrayList<>();
                // With the field names: modes that differ only in them are
                // different modes (e.g. the modes of the module Lists).
                for (Pack z = m.pack; z != null; z = z.next) {
                    l.add(modeKey(g, z.type, depth + 1) + (z.text != null ? " " + z.text : ""));
                }
                return "struct(" + String.join(",", l) + ")";
            }
            case "union" -> {
                List<String> l = new ArrayList<>();
                for (Pack z = m.pack; z != null; z = z.next) {
                    l.add(modeKey(g, z.type, depth + 1));
                }
                java.util.Collections.sort(l);
                return "union(" + String.join(",", l) + ")";
            }
            case "proc" -> {
                List<String> l = new ArrayList<>();
                for (Pack z = m.pack; z != null; z = z.next) {
                    l.add(modeKey(g, z.type, depth + 1));
                }
                return "proc(" + String.join(",", l) + ")" + modeKey(g, m.sub, depth + 1);
            }
            case "x" -> {
                return JType.of(g.a68, m) == JType.VOID ? "void" : "x" + m.number;
            }
            default -> {
                return c;
            }
        }
    }

    /** Push the (boxed) value of the module tag T. */
    static void loadModuleTag(Gen g, Tag t) {
        var bsm = java.lang.constant.ConstantDescs.ofConstantBootstrap(CD_MODULES, "bsm", CD_Object, CD_String, CD_String);
        Gen.constant(g.cb, java.lang.constant.DynamicConstantDesc.ofNamed(bsm, "m", CD_Object,
                moduleClass(t.moif.name), moduleKey(g, t)));
    }

    /**
     * Call of a module procedure or operator T with the units ARGS. The
     * arguments are passed unboxed to an invokedynamic call site whose
     * bootstrap ({@code Modules.bsmCall}) links it to the typed entry
     * {@code pub$i} of the module class (Gen#emitTypedPubEntries), or, if
     * the module has none for T (declared neither with a routine text nor
     * with a Java formal hole, or compiled by an older a68j), to the boxed
     * procedure value. Either way the
     * module is elaborated before the first call, as with the value.
     */
    static void callModuleTag(Gen g, Tag t, List<Node> args, Node p) {
        Moid proc = JType.base(t.type);
        List<ClassDesc> params = new ArrayList<>();
        int i = 0;
        for (Pack z = proc.pack; z != null; z = z.next, i++) {
            Node a = args.get(i);
            g.unit(a);
            g.adjust(Gen.mode(a), z.type, a);
            if (Gen.mode(a) == z.type) {
                GenUnits.copyIfValue(g, a, z.type);
            } else {
                GenUnits.copyIfValue(g, z.type);
            }
            params.add(JType.of(g.a68, z.type).desc);
        }
        var bsm = java.lang.constant.MethodHandleDesc.ofMethod(
                java.lang.constant.DirectMethodHandleDesc.Kind.STATIC, CD_MODULES, "bsmCall",
                MethodTypeDesc.of(CD_CallSite, CD_MethodHandles_Lookup, CD_String, CD_MethodType, CD_String, CD_String));
        g.cb.invokedynamic(java.lang.constant.DynamicCallSiteDesc.of(bsm, "call",
                MethodTypeDesc.of(JType.of(g.a68, proc.sub).desc, params),
                moduleClass(t.moif.name), moduleKey(g, t)));
    }

    // ------------------------------------------------------------------
    // Identifiers (constants and procedures used as values).
    // ------------------------------------------------------------------

    static void identifier(Gen g, Node p) {
        Tag t = p.tag;
        JType jt = JType.of(g.a68, t.type);
        if (isModuleTag(t)) {
            loadModuleTag(g, t);
            g.unbox(t.type);
            return;
        }
        if (jt == JType.PROC) {
            // A standard procedure used as a value: a Proc calling its method.
            Moid proc = JType.base(t.type);
            Gen.constant(g.cb, owner(t) == Gen.CD_POSIX ? mangle(name(t)) : method(g, "", name(t), proc));
            g.cb.invokestatic(owner(t), "procValue", MethodTypeDesc.of(Gen.CD_PROC, CD_String));
            return;
        }
        // Constants: Std.<mangled name>() returning the JVM type.
        g.cb.invokestatic(owner(t), mangle(name(t)), MethodTypeDesc.of(jt.desc));
    }

    // ------------------------------------------------------------------
    // Calls of standard procedures.
    // ------------------------------------------------------------------

    static void call(Gen g, Node id, List<Node> args, Node p) {
        Tag t = id.tag;
        if (isModuleTag(t)) {
            callModuleTag(g, t, args, p);
            return;
        }
        Moid proc = JType.base(t.type);
        List<ClassDesc> params = new ArrayList<>();
        int i = 0;
        for (Pack z = proc.pack; z != null; z = z.next, i++) {
            Node a = args.get(i);
            g.unit(a);
            g.adjust(a.type, z.type, a);
            GenUnits.copyIfValue(g, z.type);
            params.add(JType.of(g.a68, z.type).desc);
        }
        JType ret = JType.of(g.a68, proc.sub);
        if (needsWhere(name(t))) {
            g.pushWhere(p);
            params.add(CD_String);
            params.add(CD_int);
        }
        g.cb.invokestatic(owner(t), owner(t) == Gen.CD_POSIX ? mangle(name(t)) : method(g, "", name(t), proc),
                MethodTypeDesc.of(ret.desc, params));
    }

    private static boolean needsWhere(String name) {
        return false;
    }

    static ClassDesc owner(Tag t) {
        if (t.moif != null && "POSIX".equals(t.moif.name)) {
            return Gen.CD_POSIX;
        }
        return Gen.CD_STD;
    }

    /**
     * Short code of an operand mode for the Java method names of standard
     * operators, so that e.g. ABS on INT, BITS and CHAR (all int) differ:
     * i si ssi li lli (integers), bi sbi ssbi lbi llbi (bits), r lr llr
     * (reals), c (char), b (bool), s (strings and rows of char), row (other
     * rows), ref, struct, union, proc.
     */
    static String code(Gen g, Moid m) {
        m = JType.base(m);
        var md = g.a68.modes;
        if (m == md.INT) return "i";
        if (m == md.SHORT_INT) return "si";
        if (m == md.SHORT_SHORT_INT) return "ssi";
        if (m == md.LONG_INT) return "li";
        if (m == md.LONG_LONG_INT) return "lli";
        if (m == md.WORD_INT) return "li";
        if (m == md.BITS) return "bi";
        if (m == md.SHORT_BITS) return "sbi";
        if (m == md.SHORT_SHORT_BITS) return "ssbi";
        if (m == md.LONG_BITS || m == md.WORD_BITS) return "lbi";
        if (m == md.LONG_LONG_BITS) return "llbi";
        if (m == md.REAL) return "r";
        if (m == md.LONG_REAL || m == md.WORD_REAL) return "lr";
        if (m == md.LONG_LONG_REAL) return "llr";
        if (m == md.CHAR) return "c";
        if (m == md.BOOL) return "b";
        if (m == md.STRING || m == md.ROW_CHAR || m == md.FLEX_ROW_CHAR) return "s";
        if (m == md.FILE) return "file";
        if (m == md.COMPL || m == md.COMPLEX) return "cx";
        if (m == md.LONG_COMPL || m == md.LONG_COMPLEX || m == md.WORD_COMPLEX) return "lcx";
        if (m == md.LONG_LONG_COMPL || m == md.LONG_LONG_COMPLEX) return "llcx";
        if (m == md.ROWS) return "rows";
        return switch (JType.of(g.a68, m)) {
            case ROW -> "row";
            case REF -> "ref";
            case STRUCT -> "struct";
            case UNION -> "union";
            case PROC -> "proc";
            default -> "x";
        };
    }

    /** Java method name of a standard operator or procedure: mangled name and operand codes. */
    static String method(Gen g, String prefix, String n, Moid proc) {
        StringBuilder sb = new StringBuilder(prefix).append(mangle(n));
        for (Pack z = JType.base(proc).pack; z != null; z = z.next) {
            sb.append('_').append(code(g, z.type));
        }
        return sb.toString();
    }

    /** Java method name for an Algol name or operator symbol. */
    static String mangle(String n) {
        StringBuilder sb = new StringBuilder();
        for (char c : n.toCharArray()) {
            if (c == '_' || c == ' ') {
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append('$').append(switch (c) {
                    case '+' -> "plus";
                    case '-' -> "minus";
                    case '*' -> "times";
                    case '/' -> "over";
                    case '%' -> "pct";
                    case '=' -> "eq";
                    case '<' -> "lt";
                    case '>' -> "gt";
                    case ':' -> "colon";
                    case '~' -> "not";
                    case '^' -> "up";
                    case '&' -> "and";
                    case '|' -> "bar";
                    default -> Integer.toString(c);
                });
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Operators.
    // ------------------------------------------------------------------

    static void dyadic(Gen g, Node op, Node lhs, Node rhs, Node p) {
        Tag t = op.tag;
        if (isModuleTag(t)) {
            callModuleTag(g, t, List.of(lhs, rhs), p);
            return;
        }
        Moid proc = JType.base(t.type);
        Moid lm = proc.pack.type;
        Moid rm = proc.pack.next.type;
        String n = name(t);
        JType lt = JType.of(g.a68, lm);
        JType rt = JType.of(g.a68, rm);
        Node ur = GenUnits.strip(rhs);
        if ((n.equals("LWB") || n.equals("UPB")) && ur.attribute == Attribute.UNITING
                && JType.of(g.a68, ur.sub.type) == JType.ROW && lt == JType.INT
                && JType.of(g.a68, proc.sub) == JType.INT) {
            // k LWB a, k UPB a of a row: without uniting it to ROWS (a Union per use).
            g.unit(lhs);
            g.adjust(lhs.type, lm, lhs);
            g.unit(ur.sub);
            g.pushWhere(p);
            g.cb.invokestatic(Gen.CD_STD, n.equals("LWB") ? "lwb_i_rows" : "upb_i_rows",
                    MethodTypeDesc.of(CD_int, CD_int, CD_Object, CD_String, CD_int));
            return;
        }
        // Assigning operators: REF M op M.
        String an = assigningName(n);
        if (lt == JType.REF && an != null) {
            assigningOperator(g, an, lm, lhs, rhs, rm, p);
            return;
        }
        g.unit(lhs);
        g.adjust(lhs.type, lm, lhs);
        if (lt == rt && JType.base(lm) == JType.base(rm) && (lt == JType.INT || lt == JType.LONG) && !isBits(g, lm)) {
            Division d = switch (n) {
                case "%", "OVER" -> Division.OVER;
                case "%*", "MOD" -> Division.MOD;
                default -> null;
            };
            long c = d == null ? 0 : positiveDenotation(g, rhs, rm);
            if (c > 0) {
                constantDivision(g, d, lt, c);
                if (lt == JType.INT) {
                    narrow(g, proc.sub);
                }
                return;
            }
        }
        g.unit(rhs);
        g.adjust(rhs.type, rm, rhs);
        if (lt == rt && JType.base(lm) == JType.base(rm) && (JType.base(lm) != g.a68.modes.CHAR || isComparison(n))
                && inlineDyadic(g, n, lt, lm, proc.sub)) {
            if (lt == JType.INT && (n.equals("+") || n.equals("-") || n.equals("*"))) {
                narrow(g, proc.sub);
            }
            return;
        }
        String mn = method(g, "", n, proc);
        if (WHERE_METHODS.contains(mn)) {
            // These can fail; the message gives the position of the formula.
            g.pushWhere(p);
            g.cb.invokestatic(owner(t), mn,
                    MethodTypeDesc.of(JType.of(g.a68, proc.sub).desc, lt.desc, rt.desc, CD_String, CD_int));
            return;
        }
        g.cb.invokestatic(owner(t), mn, MethodTypeDesc.of(JType.of(g.a68, proc.sub).desc, lt.desc, rt.desc));
    }

    /** Standard dyadic operators whose Std method takes the position (file, line) for its error message. */
    private static final java.util.Set<String> WHERE_METHODS = java.util.Set.of("lwb_i_rows", "upb_i_rows",
            "elem_i_bi", "elem_i_sbi", "elem_i_ssbi", "elem_i_lbi", "elem_i_llbi");

    /** The operator applied by the assigning operator N ("+" for "+:=" and PLUSAB), or null. */
    static String assigningName(String n) {
        return switch (n) {
            case "PLUSAB" -> "+";
            case "MINUSAB" -> "-";
            case "TIMESAB" -> "*";
            case "DIVAB" -> "/";
            case "OVERAB" -> "%";
            case "MODAB" -> "%*";
            default -> n.endsWith(":=") && !n.equals(":=") ? n.substring(0, n.length() - 2) : null;
        };
    }

    /** Voided x op:= y for a direct variable x (see {@link Direct}). */
    static void assigningOperatorDirect(Gen g, Node formula) {
        Node lhs = formula.sub;
        Node op = lhs.next;
        Node rhs = op.next;
        Tag x = Direct.of(g, lhs);
        Moid proc = JType.base(op.tag.type);
        Moid m = JType.base(proc.pack.type).sub;
        Moid rm = proc.pack.next.type;
        g.load(x, lhs);
        applyAssigning(g, assigningName(name(op.tag)), m, rhs, rm);
        g.store(x);
    }

    /**
     * Whether the voided FORMULA x op:= y can be compiled by
     * {@link #assigningOperatorElement}: x selects an element of a row name
     * of a primitive mode and op is a standard assigning operator.
     */
    static boolean isElementAssigning(Gen g, Node formula) {
        if (Direct.assigningOperator(g, formula) == null) {
            return false;
        }
        Node dst = GenUnits.strip(formula.sub);
        Moid m = JType.base(JType.base(formula.sub.next.tag.type).pack.type).sub;
        return dst.attribute == Attribute.SLICE && JType.of(g.a68, JType.base(Gen.mode(dst.sub))) == JType.REF
                && GenRows.isElementAccess(g, dst) && JType.of(g.a68, m).isPrimitive();
    }

    /** a[i] op:= y, voided (see {@link #isElementAssigning}): no name of the element is made. */
    static void assigningOperatorElement(Gen g, Node formula) {
        assigningOperatorElement(g, formula, false);
    }

    /**
     * a[i] op:= y (see {@link #isElementAssigning}) without making a name of
     * the element; with KEEP the new value of the element is left on the
     * stack (the formula dereferenced).
     */
    static void assigningOperatorElement(Gen g, Node formula, boolean keep) {
        Node lhs = formula.sub;
        Node op = lhs.next;
        Node rhs = op.next;
        Moid proc = JType.base(op.tag.type);
        Moid m = JType.base(proc.pack.type).sub;
        Moid rm = proc.pack.next.type;
        CodeBuilder cb = g.cb;
        Hoist.Row h = GenRows.rowAndOffset(g, GenUnits.strip(lhs));
        int off = cb.allocateLocal(TypeKind.INT);
        cb.istore(off);
        int row = cb.allocateLocal(TypeKind.REFERENCE);
        cb.astore(row);
        cb.aload(row);
        cb.iload(off);
        cb.aload(row);
        cb.iload(off);
        GenRows.loadElem(g, m, h);
        applyAssigning(g, assigningName(name(op.tag)), m, rhs, rm);
        if (keep) {
            TypeKind k = JType.of(g.a68, m).kind;
            int v = cb.allocateLocal(k);
            cb.storeLocal(k, v);
            cb.loadLocal(k, v);
            GenRows.storeElem(g, m, h);
            cb.loadLocal(k, v);
            return;
        }
        GenRows.storeElem(g, m, h);
    }

    /**
     * Stack: value of mode M; push RHS (of mode RM) and apply the operator N
     * of an assigning operator. OVERAB and MODAB with a positive integral
     * denotation as divisor are compiled by {@link #constantDivision}.
     */
    private static void applyAssigning(Gen g, String n, Moid m, Node rhs, Moid rm) {
        JType mt = JType.of(g.a68, m);
        if ((n.equals("%") || n.equals("%*")) && mt == JType.of(g.a68, rm) && JType.base(m) == JType.base(rm)
                && (mt == JType.INT || mt == JType.LONG) && !isBits(g, m)) {
            long c = positiveDenotation(g, rhs, rm);
            if (c > 0) {
                constantDivision(g, n.equals("%") ? Division.OVER : Division.REM, mt, c);
                if (mt == JType.INT) {
                    narrow(g, m);
                }
                return;
            }
        }
        g.unit(rhs);
        g.adjust(rhs.type, rm, rhs);
        applyAssigning(g, n, m, rm);
    }

    /** Stack: value of mode M, value of mode RM; apply the operator N (of an assigning operator). */
    private static void applyAssigning(Gen g, String n, Moid m, Moid rm) {
        JType mt = JType.of(g.a68, m);
        JType rt = JType.of(g.a68, rm);
        if (n.equals("%*") && mt == rt && JType.base(m) == JType.base(rm) && (mt == JType.INT || mt == JType.LONG)
                && !isBits(g, m)) {
            // As ga68 (a68_lower_modab3, TRUNC_MOD_EXPR): MODAB is the C remainder,
            // negative for a negative left operand, unlike MOD.
            g.cb.invokestatic(Gen.CD_RT, "rem", MethodTypeDesc.of(mt.desc, mt.desc, mt.desc));
            return;
        }
        if (n.equals("+") && mt == JType.ROW && code(g, m).equals("s") && (code(g, rm).equals("s") || code(g, rm).equals("c"))) {
            // STRING +:= STRING or CHAR: append with spare room (Std.append_s_s),
            // amortised constant time per appended character.
            g.cb.invokestatic(Gen.CD_STD, "append_s_" + code(g, rm), MethodTypeDesc.of(mt.desc, mt.desc, rt.desc));
            return;
        }
        if (mt == rt && JType.base(m) == JType.base(rm) && (JType.base(m) != g.a68.modes.CHAR || isComparison(n)) && inlineDyadic(g, n, mt, m, m)) {
            if (mt == JType.INT) {
                narrow(g, m);
            }
        } else {
            g.cb.invokestatic(Gen.CD_STD, mangle(n) + "_" + code(g, m) + "_" + code(g, rm),
                    MethodTypeDesc.of(mt.desc, mt.desc, rt.desc));
        }
    }

    /** x op:= y: dereference x, apply op, store, yield x. */
    private static void assigningOperator(Gen g, String n, Moid refMode, Node lhs, Node rhs, Moid rm, Node p) {
        Moid m = JType.base(refMode).sub;
        JType mt = JType.of(g.a68, m);
        JType rt = JType.of(g.a68, rm);
        CodeBuilder cb = g.cb;
        Tag own = g.ownCellTag(lhs);
        if (mt.isPrimitive() && own != null && JType.base(JType.base(own.type).sub) == JType.base(m)) {
            // x op:= y for a variable x: the field of its location (Gen#ownCell).
            ClassDesc c = g.ownCell(own);
            g.loadOwnCell(own, c, p);
            cb.dup();
            cb.dup();
            cb.getfield(c, "v", mt.desc);
            applyAssigning(g, n, m, rhs, rm);
            cb.putfield(c, "v", mt.desc);
            return;
        }
        g.unit(lhs);
        cb.dup();
        cb.dup();
        g.pushWhere(p);
        if (mt.isPrimitive()) {
            // Unboxed for the typed locations (Rt.derefI, Rt.assignI, ...).
            String x = GenRows.typeLetter(mt);
            cb.invokestatic(Gen.CD_RT, "deref" + x, MethodTypeDesc.of(mt.desc, Gen.CD_REF, CD_String, CD_int));
            applyAssigning(g, n, m, rhs, rm);
            g.pushWhere(p);
            cb.invokestatic(Gen.CD_RT, "assign" + x, MethodTypeDesc.of(CD_void, Gen.CD_REF, mt.desc, CD_String, CD_int));
            return;
        }
        cb.invokestatic(Gen.CD_RT, "deref", MethodTypeDesc.of(Gen.CD_REF, Gen.CD_REF, CD_String, CD_int));
        cb.invokevirtual(Gen.CD_REF, "get", MethodTypeDesc.of(CD_Object));
        g.unbox(m);
        applyAssigning(g, n, m, rhs, rm);
        g.box(m);
        GenUnits.assignBoxed(g, refMode, p);
    }

    /** The integral divisions: OVER (%), MOD (%*) and the C remainder of MODAB (%*:=). */
    enum Division { OVER, MOD, REM }

    /**
     * The value of the unit P if it is an integral denotation (of mode M,
     * without coercion) whose value is greater than 0 and fits the JVM type
     * of M; otherwise 0.
     */
    static long positiveDenotation(Gen g, Node p, Moid m) {
        Node u = GenUnits.strip(p);
        if (u == null) {
            return 0;
        }
        Node d = u;
        if (u.attribute == Attribute.DENOTATION) {
            d = u.sub;
            while (d != null && d.next != null) {
                d = d.next;
            }
        }
        Moid dm = u.type != null ? u.type : d == null ? null : d.type;
        if (d == null || d.attribute != Attribute.INT_DENOTATION || d.info == null || dm == null
                || JType.base(dm) != JType.base(m)) {
            return 0;
        }
        long v;
        try {
            v = Long.parseUnsignedLong(d.info.symbol.replace(" ", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
        JType t = JType.of(g.a68, m);
        if (v <= 0 || (t == JType.INT && v > Integer.MAX_VALUE) || (t != JType.INT && t != JType.LONG)) {
            return 0;
        }
        return v;
    }

    /**
     * Stack: a (int or long, T) -> a OVER c, a MOD c or the remainder of
     * MODAB, for a constant c greater than 0. The divisor is neither 0 nor
     * -1, so none of the checks of {@link #intDivision} (and Rt.over,
     * Rt.mod, Rt.rem) is needed, and the code is a third of the size: about
     * 12 bytes instead of 50 to 70 for MOD on INT (see
     * docs/TODO-method-size.md). The results are those of Rt.over, Rt.mod
     * and Rt.rem with b = c: the JVM's idiv/ldiv truncate and irem/lrem have
     * the sign of a, as in C; MOD adds c to a negative remainder.
     */
    static void constantDivision(Gen g, Division d, JType t, long c) {
        CodeBuilder cb = g.cb;
        boolean l = t == JType.LONG;
        GenUnits.pushIntegral(g, t, c);
        if (d == Division.OVER) {
            if (l) {
                cb.ldiv();
            } else {
                cb.idiv();
            }
            return;
        }
        if (l) {
            cb.lrem();
        } else {
            cb.irem();
        }
        if (d == Division.REM) {
            return;
        }
        Label end = cb.newLabel();
        if (l) {
            cb.dup2();
            cb.lconst_0();
            cb.lcmp();
        } else {
            cb.dup();
        }
        cb.ifge(end);
        GenUnits.pushIntegral(g, t, c);
        if (l) {
            cb.ladd();
        } else {
            cb.iadd();
        }
        cb.labelBinding(end);
    }

    /**
     * Stack: int a, int b -> a OVER b (or a MOD b): idiv/irem inline, so
     * that no call is left for the JIT to inline (see GenRows#offsets); a
     * divisor 0 or -1 goes to Rt.over/Rt.mod, which end the program as the
     * SIGFPE of ga68 (0, and the most negative value by -1) or compute it.
     */
    private static void intDivision(CodeBuilder cb, boolean mod) {
        int a = cb.allocateLocal(TypeKind.INT);
        int b = cb.allocateLocal(TypeKind.INT);
        cb.istore(b);
        cb.istore(a);
        Label slow = cb.newLabel();
        Label end = cb.newLabel();
        cb.iload(b);
        cb.ifeq(slow);
        cb.iload(b);
        cb.iconst_m1();
        cb.if_icmpeq(slow);
        cb.iload(a);
        cb.iload(b);
        if (!mod) {
            cb.idiv();
        } else {
            // MOD is never negative: r < 0 gives r + |b|.
            cb.irem();
            cb.dup();
            cb.ifge(end);
            cb.iload(b);
            Label pos = cb.newLabel();
            cb.ifge(pos);
            cb.iload(b);
            cb.isub();
            cb.goto_(end);
            cb.labelBinding(pos);
            cb.iload(b);
            cb.iadd();
        }
        cb.goto_(end);
        cb.labelBinding(slow);
        cb.iload(a);
        cb.iload(b);
        cb.invokestatic(Gen.CD_RT, mod ? "mod" : "over", MethodTypeDesc.of(CD_int, CD_int, CD_int));
        cb.labelBinding(end);
    }

    /** Inline code for operators on equal basic operand types; false if not handled. */
    private static boolean inlineDyadic(Gen g, String n, JType t, Moid operand, Moid result) {
        CodeBuilder cb = g.cb;
        boolean bits = isBits(g, operand);
        switch (t) {
            case INT -> {
                switch (n) {
                    case "+" -> cb.iadd();
                    case "-" -> cb.isub();
                    case "*" -> cb.imul();
                    case "%", "OVER" -> intDivision(cb, false);
                    case "%*", "MOD" -> intDivision(cb, true);
                    case "AND", "&" -> { if (!bits) { return false; } cb.iand(); }
                    case "OR" -> { if (!bits) { return false; } cb.ior(); }
                    case "XOR" -> { if (!bits) { return false; } cb.ixor(); }
                    case "=", "EQ" -> compare(cb, t, "eq", bits);
                    case "/=", "NE" -> compare(cb, t, "ne", bits);
                    case "<", "LT" -> compare(cb, t, "lt", bits);
                    case "<=", "LE" -> compare(cb, t, "le", bits);
                    case ">", "GT" -> compare(cb, t, "gt", bits);
                    case ">=", "GE" -> compare(cb, t, "ge", bits);
                    default -> { return false; }
                }
                narrow(g, result);
                return true;
            }
            case LONG -> {
                switch (n) {
                    case "+" -> cb.ladd();
                    case "-" -> cb.lsub();
                    case "*" -> cb.lmul();
                    case "%", "OVER" -> cb.invokestatic(Gen.CD_RT, "over", MethodTypeDesc.of(CD_long, CD_long, CD_long));
                    case "%*", "MOD" -> cb.invokestatic(Gen.CD_RT, "mod", MethodTypeDesc.of(CD_long, CD_long, CD_long));
                    case "AND", "&" -> { if (!bits) { return false; } cb.land(); }
                    case "OR" -> { if (!bits) { return false; } cb.lor(); }
                    case "XOR" -> { if (!bits) { return false; } cb.lxor(); }
                    case "=", "EQ" -> compare(cb, t, "eq", bits);
                    case "/=", "NE" -> compare(cb, t, "ne", bits);
                    case "<", "LT" -> compare(cb, t, "lt", bits);
                    case "<=", "LE" -> compare(cb, t, "le", bits);
                    case ">", "GT" -> compare(cb, t, "gt", bits);
                    case ">=", "GE" -> compare(cb, t, "ge", bits);
                    default -> { return false; }
                }
                return true;
            }
            case FLOAT, DOUBLE -> {
                boolean f = t == JType.FLOAT;
                switch (n) {
                    case "+" -> { if (f) { cb.fadd(); } else { cb.dadd(); } }
                    case "-" -> { if (f) { cb.fsub(); } else { cb.dsub(); } }
                    case "*" -> { if (f) { cb.fmul(); } else { cb.dmul(); } }
                    case "/" -> { if (f) { cb.fdiv(); } else { cb.ddiv(); } }
                    case "=", "EQ" -> compare(cb, t, "eq", false);
                    case "/=", "NE" -> compare(cb, t, "ne", false);
                    case "<", "LT" -> compare(cb, t, "lt", false);
                    case "<=", "LE" -> compare(cb, t, "le", false);
                    case ">", "GT" -> compare(cb, t, "gt", false);
                    case ">=", "GE" -> compare(cb, t, "ge", false);
                    default -> { return false; }
                }
                return true;
            }
            case BOOL -> {
                switch (n) {
                    case "AND", "&" -> cb.iand();
                    case "OR" -> cb.ior();
                    case "XOR" -> cb.ixor();
                    case "=", "EQ" -> compare(cb, JType.INT, "eq", false);
                    case "/=", "NE" -> compare(cb, JType.INT, "ne", false);
                    default -> { return false; }
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    static boolean isComparison(String n) {
        return switch (n) {
            case "=", "EQ", "/=", "NE", "<", "LT", "<=", "LE", ">", "GT", ">=", "GE" -> true;
            default -> false;
        };
    }

    static boolean isBits(Gen g, Moid m) {
        m = JType.base(m);
        var md = g.a68.modes;
        return m == md.BITS || m == md.SHORT_BITS || m == md.SHORT_SHORT_BITS || m == md.LONG_BITS
                || m == md.LONG_LONG_BITS || m == md.WORD_BITS;
    }

    /** Truncate an int result to the width of SHORT/SHORT SHORT modes. */
    static void narrow(Gen g, Moid m) {
        m = JType.base(m);
        var md = g.a68.modes;
        if (m == md.SHORT_SHORT_INT) {
            g.cb.i2b();
        } else if (m == md.SHORT_INT) {
            g.cb.i2s();
        } else if (m == md.SHORT_SHORT_BITS) {
            Gen.constant(g.cb, 0xff);
            g.cb.iand();
        } else if (m == md.SHORT_BITS) {
            Gen.constant(g.cb, 0xffff);
            g.cb.iand();
        }
    }

    /**
     * If the unit Q is a comparison of two INT or LONG INT values with a
     * standard operator (=, /=, <, <=, >, >=, not on BITS), evaluate its
     * operands and jump to FALSEL when it yields FALSE, without making a BOOL
     * value; otherwise emit nothing and return false.
     */
    static boolean branchIfFalse(Gen g, Node q, Label falseL) {
        Node f = GenUnits.strip(q);
        if (f.attribute != Attribute.FORMULA || f.sub == null || f.sub.next == null) {
            return false;
        }
        Node lhs = f.sub;
        Node op = lhs.next;
        Node rhs = op.next;
        Tag t = op.tag;
        if (t == null || !g.isIntrinsic(t) || isModuleTag(t) || !isComparison(name(t))) {
            return false;
        }
        Moid proc = JType.base(t.type);
        Moid lm = proc.pack.type;
        Moid rm = proc.pack.next.type;
        JType lt = JType.of(g.a68, lm);
        if (lt != JType.of(g.a68, rm) || JType.base(lm) != JType.base(rm) || (lt != JType.INT && lt != JType.LONG)
                || isBits(g, lm) || JType.of(g.a68, proc.sub) != JType.BOOL || JType.of(g.a68, f.type) != JType.BOOL) {
            return false;
        }
        g.line(q);
        g.unit(lhs);
        g.adjust(lhs.type, lm, lhs);
        g.unit(rhs);
        g.adjust(rhs.type, rm, rhs);
        CodeBuilder cb = g.cb;
        String rel = relation(name(t));
        if (lt == JType.INT) {
            switch (rel) {
                case "eq" -> cb.if_icmpne(falseL);
                case "ne" -> cb.if_icmpeq(falseL);
                case "lt" -> cb.if_icmpge(falseL);
                case "le" -> cb.if_icmpgt(falseL);
                case "gt" -> cb.if_icmple(falseL);
                default -> cb.if_icmplt(falseL);
            }
        } else {
            cb.lcmp();
            switch (rel) {
                case "eq" -> cb.ifne(falseL);
                case "ne" -> cb.ifeq(falseL);
                case "lt" -> cb.ifge(falseL);
                case "le" -> cb.ifgt(falseL);
                case "gt" -> cb.ifle(falseL);
                default -> cb.iflt(falseL);
            }
        }
        return true;
    }

    private static String relation(String n) {
        return switch (n) {
            case "=", "EQ" -> "eq";
            case "/=", "NE" -> "ne";
            case "<", "LT" -> "lt";
            case "<=", "LE" -> "le";
            case ">", "GT" -> "gt";
            default -> "ge";
        };
    }

    /** Compare the two operands on the stack, leaving a boolean. */
    private static void compare(CodeBuilder cb, JType t, String rel, boolean unsigned) {
        if (t == JType.INT && !unsigned) {
            // if_icmp<rel> directly, without Integer.compare.
            Label yes = cb.newLabel();
            Label end = cb.newLabel();
            switch (rel) {
                case "eq" -> cb.if_icmpeq(yes);
                case "ne" -> cb.if_icmpne(yes);
                case "lt" -> cb.if_icmplt(yes);
                case "le" -> cb.if_icmple(yes);
                case "gt" -> cb.if_icmpgt(yes);
                default -> cb.if_icmpge(yes);
            }
            cb.iconst_0();
            cb.goto_(end);
            cb.labelBinding(yes);
            cb.iconst_1();
            cb.labelBinding(end);
            return;
        }
        switch (t) {
            case INT -> {
                if (unsigned) {
                    cb.invokestatic(CD_Integer, "compareUnsigned", MethodTypeDesc.of(CD_int, CD_int, CD_int));
                } else {
                    cb.invokestatic(CD_Integer, "compare", MethodTypeDesc.of(CD_int, CD_int, CD_int));
                }
            }
            case LONG -> {
                if (unsigned) {
                    cb.invokestatic(CD_Long, "compareUnsigned", MethodTypeDesc.of(CD_int, CD_long, CD_long));
                } else {
                    cb.lcmp();
                }
            }
            case FLOAT -> cb.fcmpg();
            case DOUBLE -> cb.dcmpg();
            default -> throw new IllegalStateException();
        }
        Label yes = cb.newLabel();
        Label end = cb.newLabel();
        switch (rel) {
            case "eq" -> cb.ifeq(yes);
            case "ne" -> cb.ifne(yes);
            case "lt" -> cb.iflt(yes);
            case "le" -> cb.ifle(yes);
            case "gt" -> cb.ifgt(yes);
            default -> cb.ifge(yes);
        }
        cb.iconst_0();
        cb.goto_(end);
        cb.labelBinding(yes);
        cb.iconst_1();
        cb.labelBinding(end);
    }

    static void monadic(Gen g, Node op, Node arg, Node p) {
        Tag t = op.tag;
        if (isModuleTag(t)) {
            callModuleTag(g, t, List.of(arg), p);
            return;
        }
        Moid proc = JType.base(t.type);
        Moid am = proc.pack.type;
        JType at = JType.of(g.a68, am);
        String n = name(t);
        CodeBuilder cb = g.cb;
        Node ua = GenUnits.strip(arg);
        if ((n.equals("LWB") || n.equals("UPB")) && ua.attribute == Attribute.UNITING
                && JType.of(g.a68, ua.sub.type) == JType.ROW && JType.of(g.a68, proc.sub) == JType.INT) {
            // LWB a, UPB a of a row: its first bound, without uniting it to ROWS.
            g.unit(ua.sub);
            cb.getfield(Gen.CD_ROW, n.equals("LWB") ? "lwb" : "upb", CD_long.arrayType());
            cb.iconst_0();
            cb.laload();
            cb.l2i();
            return;
        }
        g.unit(arg);
        g.adjust(arg.type, am, arg);
        if (n.equals("+") && at.isPrimitive()) {
            return;
        }
        if (n.equals("-")) {
            switch (at) {
                case INT -> { cb.ineg(); narrow(g, am); return; }
                case LONG -> { cb.lneg(); return; }
                case FLOAT -> { cb.fneg(); return; }
                case DOUBLE -> { cb.dneg(); return; }
                default -> { }
            }
        }
        if ((n.equals("NOT") || n.equals("~")) && at == JType.BOOL) {
            cb.iconst_1();
            cb.ixor();
            return;
        }
        if (n.equals("REPR")) {
            // Checked: the operand must be a valid code point (a68_char_repr).
            g.pushWhere(p);
            cb.invokestatic(owner(t), method(g, "op", n, proc),
                    MethodTypeDesc.of(JType.of(g.a68, proc.sub).desc, at.desc, CD_String, CD_int));
            return;
        }
        Moid sema = JType.base(g.a68.modes.SEMA);
        if (JType.base(am) == sema || JType.base(proc.sub) == sema) {
            // LEVEL, UP and DOWN (only with -fpar-threads, see Gen#checkLowering).
            ClassDesc rt = JType.of(g.a68, proc.sub).desc;
            if (JType.base(am) == sema) {
                g.pushWhere(p);
                cb.invokestatic(GenClauses.CD_PAR, n.toLowerCase(Locale.ROOT),
                        MethodTypeDesc.of(rt, at.desc, CD_String, CD_int));
            } else {
                cb.invokestatic(GenClauses.CD_PAR, "level", MethodTypeDesc.of(rt, at.desc));
            }
            return;
        }
        cb.invokestatic(owner(t), method(g, "op", n, proc),
                MethodTypeDesc.of(JType.of(g.a68, proc.sub).desc, at.desc));
    }
}
