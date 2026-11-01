// a68j JVM backend: formal holes naming Java methods and fields (-fnest-java).
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.Attribute;
import a68j.front.MoidsCheck;
import a68j.front.Moid;
import a68j.front.Node;
import a68j.front.Pack;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code nest Java "pkg.Class.member"} (an a68j extension, -fnest-java; see
 * docs/java-modules.md). The mode of the formal hole, given by its context,
 * says what is meant:
 *
 * <ul>
 * <li>a procedure mode: the public static method {@code member} of the class
 *     whose parameters and result fit the mode. The value is a closure over
 *     a bridge method {@code (Frame, Object[]) -> Object} that converts the
 *     arguments, calls the method with invokestatic and converts the result;
 * <li>another mode: the value of the public static field {@code member}.
 * </ul>
 *
 * The class is looked up when the program is compiled ({@link #loader}), so
 * a wrong name or a mismatch of the modes is a compile-time error.
 *
 * <p>Algol 68 and Java values: INT int (also long, double), LONG INT long,
 * REAL float (also double), LONG REAL double, BOOL boolean, CHAR int (code
 * point) or char, BITS int, STRING String, one-dimensional rows of INT,
 * LONG INT, REAL, LONG REAL, BOOL and STRING arrays (copied), any row also
 * a68j.rt.Row (as it is, not copied), and names
 * (REF modes) a subclass of a68j.rt.Ref: either the name itself (an output
 * parameter, an IntCell for REF INT ...) or a Java object
 * (a68j.rt.JavaObject).
 */
final class GenJava {
    private GenJava() {}

    /** The class loader used to find the classes of Java formal holes (set by Compile). */
    static ClassLoader loader = GenJava.class.getClassLoader();

    static final ClassDesc CD_CONV = ClassDesc.of("a68j.rt.JavaConv");

    static void formalHole(Gen g, Node p) {
        if (!g.a68.options().nestJava || !MoidsCheck.isJavaHole(p)) {
            throw new Gen.Unsupported(p, "formal hole");
        }
        String spec = denotation(p);
        int dot = spec.lastIndexOf('.');
        if (dot <= 0 || dot == spec.length() - 1) {
            throw new Gen.Fatal(p, "invalid Java formal hole '" + spec + "', expected 'package.Class.member'");
        }
        String className = spec.substring(0, dot);
        String member = spec.substring(dot + 1);
        Class<?> c;
        try {
            c = Class.forName(className, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new Gen.Fatal(p, "Java class '" + className + "' not found");
        }
        Moid m = JType.base(p.type);
        if (m.attribute == Attribute.PROC_SYMBOL) {
            Method target = method(g, p);
            String bridge = "j" + g.javaBridges.size();
            g.javaBridges.add(clb -> emitBridge(g, clb, bridge, target, m));
            g.cb.new_(Gen.CD_CLOSURE);
            g.cb.dup();
            g.cb.ldc(java.lang.constant.MethodHandleDesc.ofMethod(
                    java.lang.constant.DirectMethodHandleDesc.Kind.STATIC, g.thisClass, bridge, Gen.MT_ROUTINE));
            g.cb.aconst_null();
            g.cb.invokespecial(Gen.CD_CLOSURE, "<init>", MethodTypeDesc.of(CD_void, CD_MethodHandle, Gen.CD_FRAME));
        } else {
            Field f;
            try {
                f = c.getField(member);
            } catch (NoSuchFieldException e) {
                f = null;
            }
            if (f == null || !Modifier.isStatic(f.getModifiers()) || fromJava(g, f.getType(), f.getGenericType(), m) < 0) {
                throw new Gen.Fatal(p, "no public static Java field '" + spec + "' fits the mode "
                        + a68j.front.MoidsToString.moidToString(g.a68, p.type, 80, null).toLowerCase(java.util.Locale.ROOT));
            }
            g.cb.getstatic(desc(f.getDeclaringClass()), f.getName(), desc(f.getType()));
            convertFromJava(g, g.cb, f.getType(), f.getGenericType(), m);
        }
    }

    /**
     * The Java method of the formal hole P of a procedure mode (resolved once
     * per hole; a missing class or method is a compile-time error).
     */
    static Method method(Gen g, Node p) {
        Method cached = g.javaMethods.get(p);
        if (cached != null) {
            return cached;
        }
        String spec = denotation(p);
        int dot = spec.lastIndexOf('.');
        if (dot <= 0 || dot == spec.length() - 1) {
            throw new Gen.Fatal(p, "invalid Java formal hole '" + spec + "', expected 'package.Class.member'");
        }
        Class<?> c;
        try {
            c = Class.forName(spec.substring(0, dot), false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new Gen.Fatal(p, "Java class '" + spec.substring(0, dot) + "' not found");
        }
        Method target = findMethod(g, c, spec.substring(dot + 1), JType.base(p.type));
        if (target == null) {
            throw new Gen.Fatal(p, "no public static Java method '" + spec + "' fits the mode "
                    + a68j.front.MoidsToString.moidToString(g.a68, p.type, 80, null).toLowerCase(java.util.Locale.ROOT));
        }
        g.javaMethods.put(p, target);
        return target;
    }

    /**
     * A call of the procedure given by the Java formal hole HOLE (an
     * identifier or operator declared with it, see Gen#javaProcs) with the
     * units ARGS, yielding a value of mode RET: the arguments are evaluated
     * unboxed, converted and passed to the Java method with invokestatic;
     * no closure, no argument array, no boxing. The conversions are those of
     * the bridge method ({@link #emitBridge}), so the result is the same.
     */
    static void directCall(Gen g, Node hole, List<Node> args, Moid ret) {
        Method target = method(g, hole);
        Moid m = JType.base(hole.type);
        Class<?>[] ps = target.getParameterTypes();
        Type[] gps = target.getGenericParameterTypes();
        int i = 0;
        List<Runnable> after = new ArrayList<>();
        for (Pack z = m.pack; z != null; z = z.next, i++) {
            Node a = args.get(i);
            if (typedCallback(g, a, JType.base(z.type), ps[i], gps[i])) {
                continue;
            }
            g.unit(a);
            if (ps[i] == a68j.rt.Row.class) {
                // As in the bridge, where the argument array holds a copy.
                GenUnits.copyIfValue(g, a.type);
            }
            g.adjust(a.type, z.type, a);
            Runnable r = convertToJava(g, g.cb, JType.base(z.type), ps[i], gps[i]);
            if (r != null) {
                after.add(r);
            }
        }
        Class<?> owner = target.getDeclaringClass();
        g.cb.invokestatic(desc(owner), target.getName(), methodDesc(target), owner.isInterface());
        after.forEach(Runnable::run);
        Moid r = JType.base(m.sub);
        if (r == g.a68.modes.VOID) {
            Class<?> rt = target.getReturnType();
            if (rt == long.class || rt == double.class) {
                g.cb.pop2();
            } else if (rt != void.class) {
                g.cb.pop();
            }
        } else {
            convertFromJava(g, g.cb, target.getReturnType(), target.getGenericReturnType(), r);
        }
        g.adjust(m.sub, ret, hole);
    }

    /** The string of the formal hole P. */
    private static String denotation(Node p) {
        Node q = p.sub;
        while (q != null && q.attribute != Attribute.TERTIARY) {
            q = q.next;
        }
        while (q != null && q.attribute != Attribute.ROW_CHAR_DENOTATION) {
            q = q.sub;
        }
        return q == null ? "" : q.info.symbol;
    }

    /**
     * The public static method NAME of C whose parameters and result fit the
     * procedure mode M; of several the one with the lowest cost (see
     * {@link #toJava}), of those the first.
     */
    private static Method findMethod(Gen g, Class<?> c, String name, Moid m) {
        List<Moid> params = new ArrayList<>();
        for (Pack z = m.pack; z != null; z = z.next) {
            params.add(JType.base(z.type));
        }
        Method best = null;
        int bestCost = Integer.MAX_VALUE;
        for (Method x : c.getMethods()) {
            if (!x.getName().equals(name) || !Modifier.isStatic(x.getModifiers())
                    || x.getParameterCount() != params.size()) {
                continue;
            }
            int cost = fromJava(g, x.getReturnType(), x.getGenericReturnType(), JType.base(m.sub));
            Class<?>[] ps = x.getParameterTypes();
            Type[] gps = x.getGenericParameterTypes();
            for (int i = 0; cost >= 0 && i < ps.length; i++) {
                cost = add(cost, toJava(g, params.get(i), ps[i], gps[i], true));
            }
            if (cost >= 0 && cost < bestCost) {
                best = x;
                bestCost = cost;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Which Java types fit which modes.
    // ------------------------------------------------------------------

    /** Element mode of a one-dimensional row mode M, or null. */
    private static Moid rowElement(Moid m) {
        if (m.attribute == Attribute.FLEX_SYMBOL) {
            m = JType.base(m.sub);
        }
        return m.attribute == Attribute.ROW_SYMBOL && m.dim == 1 ? JType.base(m.sub) : null;
    }

    private static boolean isString(Gen g, Moid m) {
        var md = g.a68.modes;
        return m == JType.base(md.STRING) || m == md.ROW_CHAR || m == md.FLEX_ROW_CHAR;
    }

    /** Does not fit (a cost). */
    static final int NO = -1;

    /** Costs: same representation, widening, a copy, a Java object (Integer ...). */
    private static final int EXACT = 0, WIDEN = 1, COPY = 1, OBJECT = 3;

    private static int add(int a, int b) {
        return a < 0 || b < 0 ? NO : a + b;
    }

    /**
     * The cost of passing an Algol 68 value of mode M as a Java J, or NO if
     * it cannot be passed. PARAM: J is the parameter of a method called
     * from Algol 68 (not of a functional interface), so that a name of a row
     * can be passed as an array whose elements are stored back.
     */
    private static int toJava(Gen g, Moid m, Class<?> j, Type gt, boolean param) {
        if (m.attribute == Attribute.PROC_SYMBOL) {
            // An Algol 68 procedure as an object of a functional interface.
            Sam s = sam(j, gt);
            if (s == null || s.params.length != count(m)) {
                return NO;
            }
            int cost = COPY;
            int i = 0;
            for (Pack z = m.pack; z != null; z = z.next, i++) {
                cost = add(cost, fromJava(g, s.params[i], s.gparams[i], JType.base(z.type)));
            }
            Moid r = JType.base(m.sub);
            if (s.ret == void.class) {
                return cost;
            }
            return r == g.a68.modes.VOID ? NO : add(cost, toJava(g, r, s.ret, s.gret, false));
        }
        if (j == a68j.rt.Row.class) {
            // Any row, as it is (the Java side copies it if it keeps it).
            return isString(g, m) || m.attribute == Attribute.ROW_SYMBOL || m.attribute == Attribute.FLEX_SYMBOL
                    ? COPY : NO;
        }
        if (isString(g, m)) {
            return j == String.class ? EXACT : !j.isPrimitive() && j.isAssignableFrom(String.class) ? OBJECT : NO;
        }
        Moid e = rowElement(m);
        if (e != null) {
            return array(g, e, j);
        }
        if (m.attribute == Attribute.REF_SYMBOL) {
            Moid r = JType.base(m.sub);
            Moid re = isString(g, r) ? null : rowElement(r);
            if (param && j.isArray() && re != null) {
                // A name of a row: copied in and out.
                return add(COPY, array(g, re, j));
            }
            return a68j.rt.Ref.class.isAssignableFrom(j) || j.isAssignableFrom(a68j.rt.Ref.class) ? EXACT : NO;
        }
        if (!j.isPrimitive()) {
            return objectFits(g, j, m) ? OBJECT : NO;
        }
        if (m == g.a68.modes.CHAR) {
            return j == int.class || j == char.class ? EXACT : NO;
        }
        return switch (JType.of(g.a68, m)) {
            case INT -> j == int.class ? EXACT : j == long.class || j == double.class ? WIDEN : NO;
            case LONG -> j == long.class ? EXACT : j == double.class ? WIDEN : NO;
            case FLOAT -> j == float.class ? EXACT : j == double.class ? WIDEN : NO;
            case DOUBLE -> j == double.class ? EXACT : NO;
            case BOOL -> j == boolean.class ? EXACT : NO;
            default -> NO;
        };
    }

    /** The cost of a row of elements of mode E as the Java array J (both directions). */
    private static int array(Gen g, Moid e, Class<?> j) {
        if (!j.isArray()) {
            return NO;
        }
        Class<?> comp = j.getComponentType();
        if (comp == elementClass(g, e)) {
            return EXACT;
        }
        return objectFits(g, comp, e) ? OBJECT : NO;
    }

    /** The cost of a Java value of type J as an Algol 68 value of mode M, or NO. */
    private static int fromJava(Gen g, Class<?> j, Type gt, Moid m) {
        if (m == g.a68.modes.VOID) {
            return EXACT;
        }
        if (m.attribute == Attribute.PROC_SYMBOL) {
            // An object of a functional interface as an Algol 68 procedure.
            Sam s = sam(j, gt);
            if (s == null || s.params.length != count(m)) {
                return NO;
            }
            int cost = COPY;
            int i = 0;
            for (Pack z = m.pack; z != null; z = z.next, i++) {
                cost = add(cost, toJava(g, JType.base(z.type), s.params[i], s.gparams[i], false));
            }
            Moid r = JType.base(m.sub);
            if (r == g.a68.modes.VOID) {
                return cost;
            }
            return s.ret == void.class ? NO : add(cost, fromJava(g, s.ret, s.gret, r));
        }
        if (j == a68j.rt.Row.class) {
            return isString(g, m) || m.attribute == Attribute.ROW_SYMBOL || m.attribute == Attribute.FLEX_SYMBOL
                    ? COPY : NO;
        }
        if (isString(g, m)) {
            return j == String.class ? EXACT
                    : !j.isPrimitive() && (j.isAssignableFrom(String.class) || CharSequence.class.isAssignableFrom(j))
                            ? OBJECT : NO;
        }
        Moid e = rowElement(m);
        if (e != null) {
            return array(g, e, j);
        }
        if (m.attribute == Attribute.REF_SYMBOL) {
            return a68j.rt.Ref.class.isAssignableFrom(j) ? EXACT : j == Object.class ? OBJECT : NO;
        }
        if (!j.isPrimitive()) {
            return objectFits(g, j, m) ? OBJECT : NO;
        }
        if (m == g.a68.modes.CHAR) {
            return j == int.class || j == char.class ? EXACT : NO;
        }
        return switch (JType.of(g.a68, m)) {
            case INT -> j == int.class || j == short.class || j == byte.class ? EXACT : NO;
            case LONG -> j == long.class || j == int.class ? EXACT : NO;
            case FLOAT -> j == float.class || j == double.class ? EXACT : NO;
            case DOUBLE -> j == double.class || j == float.class ? EXACT : NO;
            case BOOL -> j == boolean.class ? EXACT : NO;
            default -> NO;
        };
    }

    /**
     * Whether the Java reference type J can hold the Java object for a value
     * of the plain mode M (or a string or name): Integer for INT, Long for
     * LONG INT, Float or Double for REAL, Double for LONG REAL, Boolean,
     * Character for CHAR, String, a68j.rt.Ref for a name; J may also be a
     * supertype such as Object, Number or Comparable.
     */
    private static boolean objectFits(Gen g, Class<?> j, Moid m) {
        if (j.isPrimitive()) {
            return false;
        }
        Class<?> box = boxClass(g, m);
        if (box == null) {
            return false;
        }
        if (m.attribute == Attribute.REF_SYMBOL) {
            return j.isAssignableFrom(box) || box.isAssignableFrom(j);
        }
        return j.isAssignableFrom(box) || JType.of(g.a68, m) == JType.FLOAT && j.isAssignableFrom(Double.class);
    }

    /** The class of the Java object for a value of mode M (see {@link #objectFits}), or null. */
    private static Class<?> boxClass(Gen g, Moid m) {
        if (isString(g, m)) {
            return String.class;
        }
        if (m == g.a68.modes.CHAR) {
            return Character.class;
        }
        if (m.attribute == Attribute.REF_SYMBOL) {
            return a68j.rt.Ref.class;
        }
        return switch (JType.of(g.a68, m)) {
            case INT -> Integer.class;
            case LONG -> Long.class;
            case FLOAT -> Float.class;
            case DOUBLE -> Double.class;
            case BOOL -> Boolean.class;
            default -> null;
        };
    }

    /** The element kind of mode E for JavaConv: i l f d b c s r (see JavaConv#toObject). */
    private static char kind(Gen g, Moid e) {
        if (isString(g, e)) {
            return 's';
        }
        if (e == g.a68.modes.CHAR) {
            return 'c';
        }
        if (e.attribute == Attribute.REF_SYMBOL) {
            return 'r';
        }
        return switch (JType.of(g.a68, e)) {
            case INT -> 'i';
            case LONG -> 'l';
            case FLOAT -> 'f';
            case DOUBLE -> 'd';
            case BOOL -> 'b';
            default -> throw new IllegalStateException();
        };
    }

    private static int count(Moid m) {
        int n = 0;
        for (Pack z = m.pack; z != null; z = z.next) {
            n++;
        }
        return n;
    }

    /**
     * The method M of a functional interface IFACE with the types of its
     * parameters and result: PARAMS and RET as far as the type arguments of
     * the generic type say (Float for the T of a Function&lt;Float, Float&gt;),
     * else erased; GPARAMS and GRET the generic types.
     */
    record Sam(Method m, Class<?> iface, Class<?>[] params, Type[] gparams, Class<?> ret, Type gret) {}

    /** The method of the functional interface J of the generic type GT (or null), see {@link Sam}. */
    static Sam sam(Class<?> j, Type gt) {
        Method s = sam(j);
        if (s == null) {
            return null;
        }
        Map<TypeVariable<?>, Type> env = new HashMap<>();
        bind(gt != null ? gt : j, env, 0);
        Class<?>[] ps = s.getParameterTypes();
        Type[] gps = s.getGenericParameterTypes();
        Class<?>[] eff = new Class<?>[ps.length];
        Type[] gs = new Type[ps.length];
        for (int i = 0; i < ps.length; i++) {
            gs[i] = resolve(gps[i], env, 0);
            eff[i] = narrow(ps[i], gs[i]);
        }
        Type gr = resolve(s.getGenericReturnType(), env, 0);
        return new Sam(s, j, eff, gs, narrow(s.getReturnType(), gr), gr);
    }

    /** Bind the type variables of the generic type T and of its superinterfaces to their arguments. */
    private static void bind(Type t, Map<TypeVariable<?>, Type> env, int depth) {
        Class<?> raw;
        if (depth > 20) {
            return;
        } else if (t instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
            raw = c;
            TypeVariable<?>[] vs = c.getTypeParameters();
            Type[] as = pt.getActualTypeArguments();
            for (int i = 0; i < vs.length && i < as.length; i++) {
                env.putIfAbsent(vs[i], as[i]);
            }
        } else if (t instanceof Class<?> c) {
            raw = c;
        } else {
            return;
        }
        for (Type s : raw.getGenericInterfaces()) {
            bind(s, env, depth + 1);
        }
    }

    /** The type T with its type variable or wildcard replaced by what it stands for (a bound if unknown). */
    private static Type resolve(Type t, Map<TypeVariable<?>, Type> env, int depth) {
        if (depth > 20) {
            return Object.class;
        } else if (t instanceof TypeVariable<?> v) {
            Type b = env.get(v);
            return resolve(b != null ? b : v.getBounds()[0], env, depth + 1);
        } else if (t instanceof WildcardType w) {
            Type[] lo = w.getLowerBounds();
            return resolve(lo.length > 0 ? lo[0] : w.getUpperBounds()[0], env, depth + 1);
        }
        return t;
    }

    /** The class of the type T if it is a subtype of the erased type ERASED (not for arrays), else ERASED. */
    private static Class<?> narrow(Class<?> erased, Type t) {
        Class<?> c = t instanceof Class<?> k ? k
                : t instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> k ? k : null;
        return c != null && !c.isArray() && !erased.isPrimitive() && erased.isAssignableFrom(c) ? c : erased;
    }

    /**
     * The method of the functional interface J (its single abstract method
     * that is not one of Object), or null if J is not a public functional
     * interface.
     */
    static Method sam(Class<?> j) {
        if (!j.isInterface() || !Modifier.isPublic(j.getModifiers())) {
            return null;
        }
        Method found = null;
        for (Method x : j.getMethods()) {
            if (!Modifier.isAbstract(x.getModifiers()) || isObjectMethod(x)) {
                continue;
            }
            if (found == null) {
                found = x;
            } else if (!found.getName().equals(x.getName())
                    || !java.util.Arrays.equals(found.getParameterTypes(), x.getParameterTypes())) {
                return null;
            }
        }
        return found;
    }

    private static boolean isObjectMethod(Method x) {
        try {
            return Modifier.isPublic(Object.class.getMethod(x.getName(), x.getParameterTypes()).getModifiers());
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** The Java component class for rows of E: int, long, float, double, boolean or String. */
    private static Class<?> elementClass(Gen g, Moid e) {
        if (isString(g, e)) {
            return String.class;
        }
        if (e == g.a68.modes.CHAR) {
            return null;
        }
        return switch (JType.of(g.a68, e)) {
            case INT -> int.class;
            case LONG -> long.class;
            case FLOAT -> float.class;
            case DOUBLE -> double.class;
            case BOOL -> boolean.class;
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Code.
    // ------------------------------------------------------------------

    /** The bridge method NAME (Frame, Object[]) -> Object calling TARGET for the procedure mode M. */
    private static void emitBridge(Gen g, java.lang.classfile.ClassBuilder clb, String name, Method target, Moid m) {
        clb.withMethodBody(name, Gen.MT_ROUTINE, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, code -> {
            CodeBuilder save = g.cb;
            g.cb = code;
            Class<?>[] ps = target.getParameterTypes();
            Type[] gps = target.getGenericParameterTypes();
            int i = 0;
            List<Runnable> after = new ArrayList<>();
            for (Pack z = m.pack; z != null; z = z.next, i++) {
                code.aload(1);
                code.ldc(i);
                code.aaload();
                g.unbox(z.type);
                if (ps[i] == a68j.rt.Row.class) {
                    // The Java method gets the row as it is: a copy, so that
                    // it cannot change a value shared by identities (the
                    // caller may pass one without a copy, GenUnits#ownedValue).
                    GenUnits.copyIfValue(g, z.type);
                }
                Runnable r = convertToJava(g, code, JType.base(z.type), ps[i], gps[i]);
                if (r != null) {
                    after.add(r);
                }
            }
            Class<?> owner = target.getDeclaringClass();
            code.invokestatic(desc(owner), target.getName(), methodDesc(target), owner.isInterface());
            after.forEach(Runnable::run);
            Moid r = JType.base(m.sub);
            if (r == g.a68.modes.VOID) {
                Class<?> rt = target.getReturnType();
                if (rt == long.class || rt == double.class) {
                    code.pop2();
                } else if (rt != void.class) {
                    code.pop();
                }
                code.aconst_null();
            } else {
                convertFromJava(g, code, target.getReturnType(), target.getGenericReturnType(), r);
                g.box(r);
            }
            code.areturn();
            g.cb = save;
        });
    }

    /**
     * Convert the Algol 68 value of mode M on the stack to the Java J (a
     * fit, see {@link #toJava}). The result is null or, for a name of a row
     * passed as an array, the code to run after the call: it stores the
     * elements of the array back into the row.
     */
    private static Runnable convertToJava(Gen g, CodeBuilder code, Moid m, Class<?> j, Type gt) {
        if (m.attribute == Attribute.PROC_SYMBOL) {
            toInterface(g, code, m, j, gt);
            return null;
        }
        if (j == a68j.rt.Row.class) {
            return null;
        }
        if (isString(g, m)) {
            code.invokestatic(CD_CONV, "string", MethodTypeDesc.of(CD_String, Gen.CD_ROW));
            return null;
        }
        Moid e = rowElement(m);
        if (e != null) {
            arrayToJava(g, code, e, j, Gen.CD_ROW);
            return null;
        }
        if (m.attribute == Attribute.REF_SYMBOL) {
            Moid r = JType.base(m.sub);
            if (j.isArray() && !isString(g, r) && rowElement(r) != null) {
                // A name of a row: an array with a copy of the elements,
                // stored back after the call.
                Moid re = rowElement(r);
                int name = code.allocateLocal(java.lang.classfile.TypeKind.REFERENCE);
                int array = code.allocateLocal(java.lang.classfile.TypeKind.REFERENCE);
                code.dup();
                code.astore(name);
                arrayToJava(g, code, re, j, Gen.CD_REF);
                code.dup();
                code.astore(array);
                return () -> {
                    code.aload(name);
                    code.aload(array);
                    if (j.getComponentType().isPrimitive()) {
                        code.invokestatic(CD_CONV, "back", MethodTypeDesc.of(CD_void, Gen.CD_REF, desc(j)));
                    } else {
                        code.ldc((int) kind(g, re));
                        code.invokestatic(CD_CONV, "back",
                                MethodTypeDesc.of(CD_void, Gen.CD_REF, CD_Object.arrayType(), CD_char));
                    }
                };
            }
            if (j != a68j.rt.Ref.class && j != Object.class) {
                code.checkcast(desc(j));
            }
            return null;
        }
        JType t = JType.of(g.a68, m);
        if (!j.isPrimitive()) {
            // A Java object: Integer, Long, Float or Double, Boolean, Character.
            ClassDesc box;
            if (m == g.a68.modes.CHAR) {
                code.i2c();
                box = CD_Character;
                code.invokestatic(box, "valueOf", MethodTypeDesc.of(box, CD_char));
            } else if (t == JType.FLOAT && j != Float.class) {
                code.f2d();
                box = CD_Double;
                code.invokestatic(box, "valueOf", MethodTypeDesc.of(box, CD_double));
            } else {
                box = t.box;
                code.invokestatic(box, "valueOf", MethodTypeDesc.of(box, t.desc));
            }
            return null;
        }
        if (t == JType.INT && j == long.class) {
            code.i2l();
        } else if (t == JType.INT && j == double.class) {
            code.i2d();
        } else if (t == JType.INT && j == char.class) {
            code.i2c();
        } else if (t == JType.LONG && j == double.class) {
            code.l2d();
        } else if (t == JType.FLOAT && j == double.class) {
            code.f2d();
        }
        return null;
    }

    /**
     * The row (FROM Gen.CD_ROW) or the name of a row (Gen.CD_REF) of
     * elements of mode E on the stack as the Java array J (a new array).
     */
    private static void arrayToJava(Gen g, CodeBuilder code, Moid e, Class<?> j, ClassDesc from) {
        Class<?> comp = j.getComponentType();
        if (comp == elementClass(g, e)) {
            String conv = switch (comp.getName()) {
                case "int" -> "ints";
                case "long" -> "longs";
                case "float" -> "floats";
                case "double" -> "doubles";
                case "boolean" -> "bools";
                default -> "strings";
            };
            if (from == Gen.CD_REF && conv.equals("strings")) {
                objectsToJava(g, code, e, j, from);
            } else {
                code.invokestatic(CD_CONV, conv, MethodTypeDesc.of(desc(j), from));
            }
            return;
        }
        objectsToJava(g, code, e, j, from);
    }

    private static void objectsToJava(Gen g, CodeBuilder code, Moid e, Class<?> j, ClassDesc from) {
        code.ldc(desc(j.getComponentType()));
        code.ldc((int) kind(g, e));
        code.invokestatic(CD_CONV, "objects", MethodTypeDesc.of(CD_Object.arrayType(), from, CD_Class, CD_char));
        if (j != Object[].class) {
            code.checkcast(desc(j));
        }
    }

    /**
     * Convert the Java value of type J on the stack to an Algol 68 value of
     * mode M (a fit, see {@link #fromJava}).
     */
    private static void convertFromJava(Gen g, CodeBuilder code, Class<?> j, Type gt, Moid m) {
        if (m.attribute == Attribute.PROC_SYMBOL) {
            fromInterface(g, code, j, gt, m);
            return;
        }
        if (j == a68j.rt.Row.class) {
            return;
        }
        if (isString(g, m)) {
            if (j == String.class) {
                code.invokestatic(CD_CONV, "string", MethodTypeDesc.of(Gen.CD_ROW, CD_String));
            } else {
                code.invokestatic(CD_CONV, "objString", MethodTypeDesc.of(Gen.CD_ROW, CD_Object));
            }
            return;
        }
        Moid e = rowElement(m);
        if (e != null) {
            Class<?> comp = j.getComponentType();
            if (comp != elementClass(g, e)) {
                code.ldc((int) kind(g, e));
                code.invokestatic(CD_CONV, "row", MethodTypeDesc.of(Gen.CD_ROW, CD_Object.arrayType(), CD_char));
            } else if (j == String[].class) {
                code.invokestatic(CD_CONV, "row", MethodTypeDesc.of(Gen.CD_ROW, CD_String.arrayType()));
            } else {
                code.invokestatic(CD_CONV, "row", MethodTypeDesc.of(Gen.CD_ROW, CD_Object));
            }
            return;
        }
        if (m.attribute == Attribute.REF_SYMBOL) {
            if (!a68j.rt.Ref.class.isAssignableFrom(j)) {
                code.invokestatic(CD_CONV, "objRef", MethodTypeDesc.of(Gen.CD_REF, CD_Object));
            }
            return;
        }
        JType t = JType.of(g.a68, m);
        if (!j.isPrimitive()) {
            String conv = m == g.a68.modes.CHAR ? "objChar" : switch (t) {
                case INT -> "objInt";
                case LONG -> "objLong";
                case FLOAT -> "objFloat";
                case DOUBLE -> "objDouble";
                default -> "objBool";
            };
            code.invokestatic(CD_CONV, conv, MethodTypeDesc.of(t.desc, CD_Object));
            return;
        }
        if (t == JType.LONG && j == int.class) {
            code.i2l();
        } else if (t == JType.FLOAT && j == double.class) {
            code.d2f();
        } else if (t == JType.DOUBLE && j == float.class) {
            code.f2d();
        }
    }

    // ------------------------------------------------------------------
    // Procedures and functional interfaces.
    // ------------------------------------------------------------------

    private static final ClassDesc CD_LMF = ClassDesc.of("java.lang.invoke.LambdaMetafactory");
    private static final DirectMethodHandleDesc BSM_LAMBDA = ConstantDescs.ofCallsiteBootstrap(
            CD_LMF, "metafactory", CD_CallSite, CD_MethodType, CD_MethodHandle, CD_MethodType);
    private static final MethodTypeDesc MT_FROM_JAVA = MethodTypeDesc.of(CD_Object, CD_Object, CD_Object.arrayType());

    /**
     * The Algol 68 procedure of mode M on the stack (an a68j.rt.Proc) as an
     * object of the functional interface J: LambdaMetafactory makes the
     * object, its method calls the adapter {@code jN(Proc, the parameters of
     * the method)}, which converts the arguments, calls the procedure and
     * converts its result.
     */
    private static void toInterface(Gen g, CodeBuilder code, Moid m, Class<?> j, Type gt) {
        Sam s = sam(j, gt);
        String name = "j" + g.javaBridges.size();
        g.javaBridges.add(clb -> emitToInterface(g, clb, name, s, m));
        MethodTypeDesc samType = methodDesc(s.m);
        MethodTypeDesc implType = samType.insertParameterTypes(0, Gen.CD_PROC);
        DirectMethodHandleDesc impl = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, g.thisClass,
                name, implType);
        code.invokedynamic(DynamicCallSiteDesc.of(BSM_LAMBDA, s.m.getName(),
                MethodTypeDesc.of(desc(j), Gen.CD_PROC), samType, impl, samType));
    }

    /**
     * The argument A of mode M (a procedure mode) for the Java parameter J
     * (generic type GT, a functional interface) without a procedure value,
     * if A is a routine text or a procedure declared with one whose frame is
     * at hand (as for GenUnits#directCall): the object of the interface
     * holds the frame, and its method calls the typed entry of the routine
     * (Gen#typedEntry) directly, without an argument array and boxing.
     * False (nothing emitted) otherwise.
     */
    private static boolean typedCallback(Gen g, Node a, Moid m, Class<?> j, Type gt) {
        if (m.attribute != Attribute.PROC_SYMBOL) {
            return false;
        }
        Node p = GenUnits.strip(a);
        Node text;
        Gen.Routine r;
        boolean literal = p.attribute == Attribute.ROUTINE_TEXT;
        if (literal) {
            text = p;
            r = g.routineOf.get(text);
            if (r == null || r.scheduled) {
                return false;
            }
        } else if (p.attribute == Attribute.IDENTIFIER && p.tag != null && g.staticProcs.get(p.tag) != null) {
            text = g.staticProcs.get(p.tag);
            r = g.routineOf.get(text);
            boolean encloses = false;
            for (Gen.Routine x = g.cur; r != null && x != null; x = x.parent) {
                if (x == r.parent) {
                    encloses = true;
                    break;
                }
            }
            if (!encloses) {
                return false;
            }
        } else {
            return false;
        }
        // The routine must take and yield the representations of the mode.
        List<a68j.front.Tag> params = GenUnits.parameters(text);
        if (params.size() != count(m)
                || JType.of(g.a68, text.type.sub) != JType.of(g.a68, m.sub)) {
            return false;
        }
        int k = 0;
        for (Pack z = m.pack; z != null; z = z.next, k++) {
            if (JType.of(g.a68, params.get(k).type) != JType.of(g.a68, z.type)) {
                return false;
            }
        }
        Sam s = sam(j, gt);
        if (literal) {
            g.typedTexts.add(text);
        }
        g.routineFor(text);
        MethodTypeDesc typed = g.typedEntry(r);
        if (typed == null) {
            throw new IllegalStateException("no typed entry: " + Gen.where(text));
        }
        if (literal) {
            g.cb.aload(g.frameLocal);
        } else {
            g.loadFrame(r.parent);
        }
        String name = "j" + g.javaBridges.size();
        String entry = r.method + "t";
        g.javaBridges.add(clb -> emitTypedCallback(g, clb, name, s, m, entry, typed));
        MethodTypeDesc samType = methodDesc(s.m);
        DirectMethodHandleDesc impl = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, g.thisClass,
                name, samType.insertParameterTypes(0, Gen.CD_FRAME));
        g.cb.invokedynamic(DynamicCallSiteDesc.of(BSM_LAMBDA, s.m.getName(),
                MethodTypeDesc.of(desc(j), Gen.CD_FRAME), samType, impl, samType));
        return true;
    }

    /** The method NAME (Frame, the parameters of S) of a typed callback calling ENTRY (see {@link #typedCallback}). */
    private static void emitTypedCallback(Gen g, java.lang.classfile.ClassBuilder clb, String name, Sam s, Moid m,
            String entry, MethodTypeDesc typed) {
        MethodTypeDesc type = methodDesc(s.m).insertParameterTypes(0, Gen.CD_FRAME);
        clb.withMethodBody(name, type, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_SYNTHETIC, code -> {
            CodeBuilder save = g.cb;
            g.cb = code;
            Class<?>[] ps = s.m.getParameterTypes();
            code.aload(0);
            int slot = 1;
            int i = 0;
            for (Pack z = m.pack; z != null; z = z.next, i++) {
                TypeKind k = TypeKind.from(desc(ps[i]));
                code.loadLocal(k, slot);
                slot += k.slotSize();
                if (s.params[i] != ps[i]) {
                    code.checkcast(desc(s.params[i]));
                }
                convertFromJava(g, code, s.params[i], s.gparams[i], JType.base(z.type));
            }
            code.invokestatic(g.thisClass, entry, typed);
            Class<?> rt = s.m.getReturnType();
            Moid r = JType.base(m.sub);
            if (rt == void.class) {
                JType t = JType.of(g.a68, r);
                if (t.kind.slotSize() == 2) {
                    code.pop2();
                } else if (t != JType.VOID) {
                    code.pop();
                }
                code.return_();
            } else {
                convertToJava(g, code, r, s.ret, s.gret);
                if (!rt.isPrimitive() && rt != Object.class) {
                    code.checkcast(desc(rt));
                }
                code.return_(TypeKind.from(desc(rt)));
            }
            g.cb = save;
        });
    }

    private static void emitToInterface(Gen g, java.lang.classfile.ClassBuilder clb, String name, Sam s, Moid m) {
        MethodTypeDesc type = methodDesc(s.m).insertParameterTypes(0, Gen.CD_PROC);
        clb.withMethodBody(name, type, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_SYNTHETIC, code -> {
            CodeBuilder save = g.cb;
            g.cb = code;
            Class<?>[] ps = s.m.getParameterTypes();
            code.aload(0);
            code.ldc(ps.length);
            code.anewarray(CD_Object);
            int slot = 1;
            int i = 0;
            for (Pack z = m.pack; z != null; z = z.next, i++) {
                TypeKind k = TypeKind.from(desc(ps[i]));
                code.dup();
                code.ldc(i);
                code.loadLocal(k, slot);
                slot += k.slotSize();
                if (s.params[i] != ps[i]) {
                    code.checkcast(desc(s.params[i]));
                }
                convertFromJava(g, code, s.params[i], s.gparams[i], JType.base(z.type));
                g.box(z.type);
                code.aastore();
            }
            code.invokevirtual(Gen.CD_PROC, "call", MethodTypeDesc.of(CD_Object, Gen.CD_OBJECTS));
            Class<?> rt = s.m.getReturnType();
            if (rt == void.class) {
                code.pop();
                code.return_();
            } else {
                Moid r = JType.base(m.sub);
                g.unbox(r);
                convertToJava(g, code, r, s.ret, s.gret);
                if (!rt.isPrimitive() && rt != Object.class) {
                    code.checkcast(desc(rt));
                }
                code.return_(TypeKind.from(desc(rt)));
            }
            g.cb = save;
        });
    }

    /**
     * The Java object of the functional interface J (generic type GT) on the
     * stack as an Algol 68 procedure of mode M: an a68j.rt.JavaFn whose
     * adapter {@code jN(Object target, Object[] args) -> Object} converts the
     * arguments, calls the method of the interface and converts its result.
     */
    private static void fromInterface(Gen g, CodeBuilder code, Class<?> j, Type gt, Moid m) {
        Sam s = sam(j, gt);
        String name = "j" + g.javaBridges.size();
        g.javaBridges.add(clb -> emitFromInterface(g, clb, name, s, m));
        code.ldc(MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, g.thisClass, name, MT_FROM_JAVA));
        code.invokestatic(CD_CONV, "proc", MethodTypeDesc.of(Gen.CD_PROC, CD_Object, CD_MethodHandle));
    }

    private static void emitFromInterface(Gen g, java.lang.classfile.ClassBuilder clb, String name, Sam s, Moid m) {
        clb.withMethodBody(name, MT_FROM_JAVA, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_SYNTHETIC,
                code -> {
            CodeBuilder save = g.cb;
            g.cb = code;
            Class<?>[] ps = s.m.getParameterTypes();
            code.aload(0);
            code.checkcast(desc(s.iface));
            int i = 0;
            for (Pack z = m.pack; z != null; z = z.next, i++) {
                code.aload(1);
                code.ldc(i);
                code.aaload();
                g.unbox(z.type);
                convertToJava(g, code, JType.base(z.type), s.params[i], s.gparams[i]);
                if (!ps[i].isPrimitive() && ps[i] != Object.class) {
                    code.checkcast(desc(ps[i]));
                }
            }
            code.invokeinterface(desc(s.iface), s.m.getName(), methodDesc(s.m));
            Moid r = JType.base(m.sub);
            Class<?> rt = s.m.getReturnType();
            if (r == g.a68.modes.VOID) {
                if (rt == long.class || rt == double.class) {
                    code.pop2();
                } else if (rt != void.class) {
                    code.pop();
                }
                code.aconst_null();
            } else {
                if (s.ret != rt) {
                    code.checkcast(desc(s.ret));
                }
                convertFromJava(g, code, s.ret, s.gret, r);
                g.box(r);
            }
            code.areturn();
            g.cb = save;
        });
    }

    static ClassDesc desc(Class<?> c) {
        return c.describeConstable().orElseThrow();
    }

    private static MethodTypeDesc methodDesc(Method x) {
        Class<?>[] ps = x.getParameterTypes();
        ClassDesc[] ds = new ClassDesc[ps.length];
        for (int i = 0; i < ps.length; i++) {
            ds[i] = desc(ps[i]);
        }
        return MethodTypeDesc.of(desc(x.getReturnType()), ds);
    }
}
