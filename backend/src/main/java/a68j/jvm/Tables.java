// a68j JVM backend: row displays of constants as tables. Licensed under the
// GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.Attribute;
import a68j.front.Moid;
import a68j.front.Node;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Row displays of constants, such as a table of primes or a matrix of
 * reals, as tables (docs/optimizations.md, section 12).
 *
 * <p>GenRows#collateral stores each element of a row display with code of
 * its own, about 8 bytes for an INT and 11 for a STRING. A display of a few
 * thousand elements so makes a method too large for the JVM (65535 bytes),
 * and one of about a thousand a method that HotSpot does not compile
 * (8000 bytes, Gen#compileClass), and neither can be cut into parts (Cut
 * cuts serial clauses, not units). As a table the display needs a few
 * bytes of code whatever its size: the values are written as text into a
 * string constant of the class, the class initializer decodes the text
 * once into a static field of class a68j.rt.Table, and each evaluation of
 * the display makes a new row of copies of the values (Table#row). The
 * elements that are not constants are evaluated in their order and stored
 * into the copy before the row is made; the constants have no effects, so
 * the order in which they are elaborated cannot be observed.
 *
 * <p>A display becomes a table if
 * <ul>
 * <li>its elements are of mode INT, CHAR, BITS, BOOL, REAL or one of their
 *     lengths, or STRING (a row of rows of characters);</li>
 * <li>for a display of N dimensions, its elements are displays nested N - 1
 *     deep with the same number of elements at each depth (otherwise
 *     Row#ofRows reports the bounds that do not match at run time);</li>
 * <li>at least {@link #MIN} of its elements are constants: denotations,
 *     their widenings and the standard monadic - and + applied to them.</li>
 * </ul>
 * The environment variable {@code A68J_TABLE_MIN} sets another minimum, so
 * that the test suites can run with every display of two or more elements
 * made a table ({@code A68J_TABLE_MIN=1}, tools/table_test.sh).
 */
final class Tables {
    private Tables() {}

    static final ClassDesc CD_TABLE = ClassDesc.of("a68j.rt.Table");
    private static final ClassDesc CD_StringBuilder = ClassDesc.of("java.lang.StringBuilder");

    /**
     * The least number of constant elements of a display made a table.
     * Below it the code of the display is small (for INT about 8 bytes an
     * element, about 1000 bytes at this minimum) and, in a procedure called
     * often, was faster than the table; from 128 INTs on both took the
     * same time (docs/optimizations.md, section 12).
     */
    static final int MIN = minimum();

    private static int minimum() {
        String v = System.getenv("A68J_TABLE_MIN");
        if (v != null && !v.isEmpty()) {
            try {
                return Math.max(1, Integer.parseInt(v));
            } catch (NumberFormatException e) {
                // ignored: the default
            }
        }
        return 128;
    }

    /**
     * A string constant holds at most 65535 bytes of modified UTF-8, at
     * most 3 for a char: the text of a table goes into constants of at most
     * this many chars.
     */
    private static final int CHUNK = 16384;

    /**
     * The row display P of row mode R (not flexible, with at least one
     * element) as a table, if it can be one (see the class comment): leaves
     * the new row on the stack and returns true; otherwise generates
     * nothing and returns false.
     */
    static boolean display(Gen g, Node p, Moid r) {
        Moid em = r.sub;
        char kind = kind(g, em);
        if (kind == 0) {
            return false;
        }
        long[] n = new long[r.dim];
        List<Node> leaves = new ArrayList<>();
        if (!shape(p, 0, n, leaves)) {
            return false;
        }
        JType et = JType.of(g.a68, em);
        Object[] values = new Object[leaves.size()];
        int constants = 0;
        for (int i = 0; i < values.length; i++) {
            Node u = leaves.get(i);
            if (JType.of(g.a68, Gen.mode(u)) == et) {
                values[i] = constant(g, u);
                if (values[i] != null && !fits(kind, values[i])) {
                    values[i] = null;
                }
            }
            if (values[i] != null) {
                constants++;
            }
        }
        if (constants < MIN) {
            return false;
        }
        String field = field(g, text(kind, n, values));
        var cb = g.cb;
        cb.getstatic(g.thisClass, field, CD_TABLE);
        if (constants == values.length) {
            cb.invokevirtual(CD_TABLE, "row", MethodTypeDesc.of(Gen.CD_ROW));
            return true;
        }
        cb.dup();
        cb.invokevirtual(CD_TABLE, "store", MethodTypeDesc.of(CD_Object));
        cb.checkcast(storeDesc(kind));
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                continue;
            }
            Node u = leaves.get(i);
            cb.dup();
            Gen.constant(cb, i);
            g.unit(u);
            if (kind == 's') {
                // As GenRows#pushElements.
                Moid um = Gen.mode(u);
                GenUnits.copyValueOf(g, u, um);
                g.box(um);
                cb.aastore();
            } else {
                g.adjust(Gen.mode(u), em, u);
                cb.arrayStore(et.kind);
            }
        }
        cb.invokevirtual(CD_TABLE, "row", MethodTypeDesc.of(Gen.CD_ROW, CD_Object));
        return true;
    }

    /**
     * The kind of storage of a table with elements of mode EM (see
     * a68j.rt.Table), or 0 if a display of such elements cannot be one.
     */
    private static char kind(Gen g, Moid em) {
        if (isString(g, em)) {
            return 's';
        }
        return switch (JType.of(g.a68, em)) {
            case INT -> 'i';
            case LONG -> 'l';
            case FLOAT -> 'f';
            case DOUBLE -> 'd';
            case BOOL -> 'b';
            default -> 0;
        };
    }

    /** Whether M is STRING or [] CHAR (a row of one dimension of characters). */
    private static boolean isString(Gen g, Moid m) {
        Moid x = JType.base(m);
        if (x == null) {
            return false;
        }
        if (x == JType.base(g.a68.modes.STRING)) {
            return true;
        }
        if (x.attribute == Attribute.FLEX_SYMBOL) {
            x = JType.base(x.sub);
        }
        return x != null && x.attribute == Attribute.ROW_SYMBOL && x.dim == 1
                && JType.base(x.sub) == JType.base(g.a68.modes.CHAR);
    }

    /**
     * Collects the elements of the display P at depth LEVEL into LEAVES in
     * row-major order, the number of elements at each depth in N; false if
     * the elements at a depth above the last are not all displays or their
     * numbers differ.
     */
    private static boolean shape(Node p, int level, long[] n, List<Node> leaves) {
        List<Node> units = new ArrayList<>();
        GenUnits.collectArguments(p.sub, units);
        if (units.isEmpty() || (n[level] != 0 && n[level] != units.size())) {
            return false;
        }
        n[level] = units.size();
        for (Node u : units) {
            if (level == n.length - 1) {
                leaves.add(u);
            } else {
                Node c = core(u);
                if (c.attribute != Attribute.COLLATERAL_CLAUSE || !shape(c, level + 1, n, leaves)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** P below the wrappers with a single son (see {@link #isWrapper}). */
    private static Node core(Node p) {
        while (p.sub != null && p.sub.next == null && isWrapper(p)) {
            p = p.sub;
        }
        return p;
    }

    /**
     * Whether P passes the value of its only son on, adjusted to its mode
     * (Gen#unit; GenUnits#formula for a FORMULA that is a monadic formula
     * or an operand only).
     */
    private static boolean isWrapper(Node p) {
        return switch (p.attribute) {
            case UNIT, TERTIARY, SECONDARY, PRIMARY, ENCLOSED_CLAUSE, FORMULA -> true;
            default -> false;
        };
    }

    /**
     * The value of the unit U if it is a constant, in the representation of
     * its mode as GenUnits#denotationValue gives it, else null: a
     * denotation, a widening of a constant, or the standard monadic - or +
     * of a constant of a mode the JVM computes the same way (Intrinsics#monadic).
     * The value is the one the code for U would compute.
     */
    static Object constant(Gen g, Node u) {
        Node s = u;
        while (s.sub != null && s.sub.next == null && isWrapper(s)) {
            // Gen#unit: the wrapper adjusts the value of its son to its mode.
            if (s.type != null && JType.of(g.a68, Gen.mode(s.sub)) != JType.of(g.a68, s.type)) {
                return null;
            }
            s = s.sub;
        }
        switch (s.attribute) {
            case DENOTATION, INT_DENOTATION, REAL_DENOTATION, BITS_DENOTATION, ROW_CHAR_DENOTATION,
                 TRUE_SYMBOL, FALSE_SYMBOL -> {
                return GenUnits.denotationValue(g, s);
            }
            case WIDENING -> {
                return widened(constant(g, s.sub), JType.of(g.a68, s.sub.type), JType.of(g.a68, s.type));
            }
            case MONADIC_FORMULA -> {
                return monadic(g, s);
            }
            default -> {
                return null;
            }
        }
    }

    /** The constant V of JVM type F widened to T as GenCoercions#widening does it, or null. */
    private static Object widened(Object v, JType f, JType t) {
        if (v == null) {
            return null;
        }
        if (f == JType.INT && t == JType.FLOAT) {
            return (float) (Integer) v;
        } else if (f == JType.INT && t == JType.DOUBLE) {
            return (double) (Integer) v;
        } else if (f == JType.LONG && t == JType.DOUBLE) {
            return (double) (Long) v;
        } else if (f == JType.LONG && t == JType.FLOAT) {
            return (float) (Long) v;
        } else if (f == JType.FLOAT && t == JType.DOUBLE) {
            return (double) (Float) v;
        }
        return null;
    }

    /** The monadic formula P (GenUnits#monadicFormula) as a constant, or null. */
    private static Object monadic(Gen g, Node p) {
        Node op = p.sub;
        Node arg = op.next;
        if (op.tag == null || !g.isIntrinsic(op.tag) || Intrinsics.isModuleTag(op.tag)) {
            return null;
        }
        String n = Intrinsics.name(op.tag);
        if (!n.equals("-") && !n.equals("+")) {
            return null;
        }
        Moid proc = JType.base(op.tag.type);
        Moid am = proc.pack.type;
        JType at = JType.of(g.a68, am);
        // The adjustments of Intrinsics#monadic and GenUnits#monadicFormula change nothing.
        if (JType.of(g.a68, arg.type) != at || JType.of(g.a68, proc.sub) != at
                || (p.type != null && JType.of(g.a68, p.type) != at)) {
            return null;
        }
        Object v = constant(g, arg);
        if (v == null) {
            return null;
        }
        if (n.equals("+")) {
            return at.isPrimitive() ? v : null;
        }
        return switch (at) {
            // Intrinsics#narrow follows ineg for the shorter INTs and BITS.
            case INT -> JType.base(am) == JType.base(g.a68.modes.INT) ? -(Integer) v : null;
            case LONG -> -(Long) v;
            case FLOAT -> -(Float) v;
            case DOUBLE -> -(Double) v;
            default -> null;
        };
    }

    /** Whether the constant V is of the representation of a table of kind KIND. */
    private static boolean fits(char kind, Object v) {
        return switch (kind) {
            case 'i' -> v instanceof Integer;
            case 'l' -> v instanceof Long;
            case 'f' -> v instanceof Float;
            case 'd' -> v instanceof Double;
            case 'b' -> v instanceof Boolean;
            case 's' -> v instanceof String;
            default -> false;
        };
    }

    /**
     * The text of a table (see a68j.rt.Table) of kind KIND with N elements
     * at each depth and the given values (null: not a constant, a
     * placeholder that the code of the display replaces).
     */
    static String text(char kind, long[] n, Object[] values) {
        StringBuilder sb = new StringBuilder();
        sb.append(kind).append(' ').append(n.length).append(' ');
        for (long k : n) {
            sb.append(k).append(' ');
        }
        for (Object v : values) {
            switch (kind) {
                case 'b' -> sb.append(v != null && (Boolean) v ? '1' : '0');
                case 's' -> {
                    String s = v == null ? "" : (String) v;
                    sb.append(s.length()).append(':').append(s);
                }
                case 'f' -> sb.append(v == null ? "0" : Float.toString((Float) v)).append(' ');
                case 'd' -> sb.append(v == null ? "0" : Double.toString((Double) v)).append(' ');
                default -> sb.append(v == null ? "0" : v.toString()).append(' ');
            }
        }
        return sb.toString();
    }

    /** The type of the storage of a table of kind KIND. */
    private static ClassDesc storeDesc(char kind) {
        return switch (kind) {
            case 'i' -> CD_int.arrayType();
            case 'l' -> CD_long.arrayType();
            case 'f' -> CD_float.arrayType();
            case 'd' -> CD_double.arrayType();
            case 'b' -> CD_boolean.arrayType();
            default -> CD_Object.arrayType();
        };
    }

    /** The name of the static field of the table with text TEXT (one field for equal texts). */
    private static String field(Gen g, String text) {
        return g.tables.computeIfAbsent(text, t -> "table" + g.tables.size());
    }

    /**
     * The static fields of the tables of the class and the class
     * initializer that decodes them (after all methods have been
     * generated, see Gen#compile).
     */
    static void emit(Gen g, ClassBuilder clb) {
        if (g.tables.isEmpty()) {
            return;
        }
        for (String f : g.tables.values()) {
            clb.withField(f, CD_TABLE, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
        }
        clb.withMethodBody(CLASS_INIT_NAME, MTD_void, ClassFile.ACC_STATIC, code -> {
            for (Map.Entry<String, String> e : g.tables.entrySet()) {
                String text = e.getKey();
                if (text.length() <= CHUNK) {
                    Gen.constant(code, text);
                } else {
                    code.new_(CD_StringBuilder);
                    code.dup();
                    Gen.constant(code, text.length());
                    code.invokespecial(CD_StringBuilder, INIT_NAME, MethodTypeDesc.of(CD_void, CD_int));
                    for (int from = 0; from < text.length(); from += CHUNK) {
                        Gen.constant(code, text.substring(from, Math.min(from + CHUNK, text.length())));
                        code.invokevirtual(CD_StringBuilder, "append",
                                MethodTypeDesc.of(CD_StringBuilder, CD_String));
                    }
                    code.invokevirtual(CD_StringBuilder, "toString", MethodTypeDesc.of(CD_String));
                }
                code.invokestatic(CD_TABLE, "decode", MethodTypeDesc.of(CD_TABLE, CD_String));
                code.putstatic(g.thisClass, e.getValue(), CD_TABLE);
            }
            code.return_();
        });
    }
}
