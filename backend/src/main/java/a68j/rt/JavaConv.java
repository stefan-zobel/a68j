// a68j runtime: value conversions for nest Java (-fnest-java). GPLv3+.
package a68j.rt;

/**
 * Conversions between Algol 68 values and Java values for the bridge
 * methods of Java formal holes (GenJava): STRING and rows of plain modes
 * are copied to and from Java strings and arrays.
 */
public final class JavaConv {
    private JavaConv() {}

    public static String string(Row r) {
        return r.asString();
    }

    public static Row string(String s) {
        return Row.ofString(s == null ? "" : s);
    }

    public static int[] ints(Row r) {
        int n = (int) r.size(0);
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = r.getInt(index(r, i));
        }
        return a;
    }

    public static long[] longs(Row r) {
        int n = (int) r.size(0);
        long[] a = new long[n];
        for (int i = 0; i < n; i++) {
            a[i] = r.getLong(index(r, i));
        }
        return a;
    }

    public static float[] floats(Row r) {
        int n = (int) r.size(0);
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = r.getFloat(index(r, i));
        }
        return a;
    }

    public static double[] doubles(Row r) {
        int n = (int) r.size(0);
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = r.getDouble(index(r, i));
        }
        return a;
    }

    public static boolean[] bools(Row r) {
        int n = (int) r.size(0);
        boolean[] a = new boolean[n];
        for (int i = 0; i < n; i++) {
            a[i] = r.getBool(index(r, i));
        }
        return a;
    }

    public static String[] strings(Row r) {
        int n = (int) r.size(0);
        String[] a = new String[n];
        for (int i = 0; i < n; i++) {
            a[i] = ((Row) r.get(index(r, i))).asString();
        }
        return a;
    }

    private static int index(Row r, int i) {
        return (int) (r.offset + i * r.stride[0]);
    }

    /** A row [1:n] holding a copy of the Java array A (int[], long[], float[], double[] or boolean[]). */
    public static Row row(Object a) {
        Object copy = switch (a) {
            case int[] x -> x.clone();
            case long[] x -> x.clone();
            case float[] x -> x.clone();
            case double[] x -> x.clone();
            case boolean[] x -> x.clone();
            default -> throw new IllegalArgumentException(String.valueOf(a));
        };
        return Row.ofStore(1, java.lang.reflect.Array.getLength(a), copy);
    }

    /** A row [1:n] of STRING from a Java String[]. */
    public static Row row(String[] a) {
        Object[] e = new Object[a.length];
        for (int i = 0; i < a.length; i++) {
            e[i] = string(a[i]);
        }
        return Row.of1(1, a.length, e);
    }

    /** The I-th element (from 0) of the one-dimensional row R of rows (e.g. [] STRING). */
    public static Row element(Row r, int i) {
        return (Row) r.get(index(r, i));
    }

    /** Compare two STRINGs by their characters (code points), as the Algol 68 relations do. */
    public static int compareStrings(Row a, Row b) {
        int[] x = a.codePoints();
        int[] y = b.codePoints();
        return java.util.Arrays.compare(x, y);
    }

    // ------------------------------------------------------------------
    // Java objects (Integer, Double, String ... as Object, Number, a type
    // variable of a generic method or interface) and Algol 68 values.
    // ------------------------------------------------------------------

    /** An INT from a Java object: a Number or a Character. */
    public static int objInt(Object o) {
        return switch (o) {
            case Number n -> n.intValue();
            case Character c -> c;
            case null -> throw nullValue("int");
            default -> throw wrongValue(o, "int");
        };
    }

    public static long objLong(Object o) {
        return switch (o) {
            case Number n -> n.longValue();
            case Character c -> c;
            case null -> throw nullValue("long int");
            default -> throw wrongValue(o, "long int");
        };
    }

    public static float objFloat(Object o) {
        return switch (o) {
            case Number n -> n.floatValue();
            case null -> throw nullValue("real");
            default -> throw wrongValue(o, "real");
        };
    }

    public static double objDouble(Object o) {
        return switch (o) {
            case Number n -> n.doubleValue();
            case null -> throw nullValue("long real");
            default -> throw wrongValue(o, "long real");
        };
    }

    public static boolean objBool(Object o) {
        return switch (o) {
            case Boolean b -> b;
            case null -> throw nullValue("bool");
            default -> throw wrongValue(o, "bool");
        };
    }

    /** A CHAR (code point) from a Character or an Integer. */
    public static int objChar(Object o) {
        return switch (o) {
            case Character c -> c;
            case Integer i -> i;
            case null -> throw nullValue("char");
            default -> throw wrongValue(o, "char");
        };
    }

    /** A STRING from a Java object: its toString(), "" for null. */
    public static Row objString(Object o) {
        return Row.ofString(o == null ? "" : o.toString());
    }

    /**
     * A name from a Java object: NIL for null, an a68j.rt.Ref as it is (a
     * name, or a Java object of a module), any other object in a JavaHandle.
     */
    public static Ref objRef(Object o) {
        if (o == null || o instanceof Ref) {
            return (Ref) o;
        }
        return new JavaHandle(o);
    }

    /** The Java object for the name R: the object of a JavaHandle, else R itself (null for NIL). */
    public static Object unwrap(Ref r) {
        return r instanceof JavaHandle h ? h.value : r;
    }

    /**
     * The Java object for the name R where Java wants a C ({@link #unwrap}):
     * null for NIL; a runtime error if the object is not a C.
     */
    public static Object javaObject(Ref r, Class<?> c) {
        if (r == null || c.isInstance(r) && Ref.class.isAssignableFrom(c)) {
            return r;
        }
        Object o = unwrap(r);
        if (c.isInstance(o)) {
            return o;
        }
        throw JavaObject.error((r instanceof JavaObject ? "Java object of " + o.getClass().getName() : "a name")
                + " where a " + c.getName() + " is expected");
    }

    /**
     * The object on which the Java method WHAT (Class::method) is called:
     * {@link #javaObject}, but NIL is a runtime error.
     */
    public static Object receiver(Ref r, Class<?> c, String what) {
        if (r == null) {
            throw JavaObject.error("Java method " + what + " called on NIL");
        }
        return javaObject(r, c);
    }

    /**
     * The object whose Java field WHAT (Class::field) is read or written:
     * {@link #javaObject}, but NIL is a runtime error.
     */
    public static Object fieldReceiver(Ref r, Class<?> c, String what) {
        if (r == null) {
            throw JavaObject.error("Java field " + what + " of NIL");
        }
        return javaObject(r, c);
    }

    private static RuntimeException nullValue(String mode) {
        return JavaObject.error("Java value null where a value of mode " + mode + " is expected");
    }

    private static RuntimeException wrongValue(Object o, String mode) {
        return JavaObject.error("Java value of " + o.getClass().getName()
                + " where a value of mode " + mode + " is expected");
    }

    /**
     * The Java object for the Algol 68 value V (boxed as in a68j: Integer,
     * Long, Float, Double, Boolean, Row, Ref) of an element of kind KIND
     * ('i' INT, 'l' LONG INT, 'f' REAL, 'd' LONG REAL, 'b' BOOL, 'c' CHAR,
     * 's' STRING, 'r' a name) for a Java array of component class COMP.
     */
    static Object toObject(Object v, char kind, Class<?> comp) {
        return switch (kind) {
            case 'f' -> comp == Float.class ? v : (Object) (double) (Float) v;
            case 'c' -> (char) (int) (Integer) v;
            case 's' -> ((Row) v).asString();
            case 'r' -> javaObject((Ref) v, comp);
            default -> v;
        };
    }

    /** The Algol 68 value (boxed as in a68j) of kind KIND for the Java object O. */
    static Object fromObject(Object o, char kind) {
        return switch (kind) {
            case 'i' -> objInt(o);
            case 'l' -> objLong(o);
            case 'f' -> objFloat(o);
            case 'd' -> objDouble(o);
            case 'b' -> objBool(o);
            case 'c' -> objChar(o);
            case 's' -> objString(o);
            case 'r' -> objRef(o);
            default -> o;
        };
    }

    /** A Java array of component class COMP holding the elements (of kind KIND) of the one-dimensional row R. */
    public static Object[] objects(Row r, Class<?> comp, char kind) {
        int n = (int) r.size(0);
        Object[] a = (Object[]) java.lang.reflect.Array.newInstance(comp, n);
        for (int i = 0; i < n; i++) {
            a[i] = toObject(r.get(index(r, i)), kind, comp);
        }
        return a;
    }

    /** A row [1:n] of elements of kind KIND from the Java array A. */
    public static Row row(Object[] a, char kind) {
        char store = switch (kind) {
            case 'i', 'c' -> 'i';
            case 'l', 'f', 'd', 'b' -> kind;
            default -> 'o';
        };
        Row r = Row.make1(1, a.length, store);
        for (int i = 0; i < a.length; i++) {
            r.set(i, fromObject(a[i], kind));
        }
        return r;
    }

    // ------------------------------------------------------------------
    // Names of rows (REF [] M): the Java method gets an array with a copy
    // of the elements, which are stored back into the row after the call.
    // ------------------------------------------------------------------

    public static int[] ints(Ref name) { return ints((Row) name.get()); }
    public static long[] longs(Ref name) { return longs((Row) name.get()); }
    public static float[] floats(Ref name) { return floats((Row) name.get()); }
    public static double[] doubles(Ref name) { return doubles((Row) name.get()); }
    public static boolean[] bools(Ref name) { return bools((Row) name.get()); }
    public static Object[] objects(Ref name, Class<?> comp, char kind) {
        return objects((Row) name.get(), comp, kind);
    }

    public static void back(Ref name, int[] a) {
        Row r = (Row) name.get();
        for (int i = 0; i < a.length; i++) {
            r.setInt(index(r, i), a[i]);
        }
    }

    public static void back(Ref name, long[] a) {
        Row r = (Row) name.get();
        for (int i = 0; i < a.length; i++) {
            r.setLong(index(r, i), a[i]);
        }
    }

    public static void back(Ref name, float[] a) {
        Row r = (Row) name.get();
        for (int i = 0; i < a.length; i++) {
            r.setFloat(index(r, i), a[i]);
        }
    }

    public static void back(Ref name, double[] a) {
        Row r = (Row) name.get();
        for (int i = 0; i < a.length; i++) {
            r.setDouble(index(r, i), a[i]);
        }
    }

    public static void back(Ref name, boolean[] a) {
        Row r = (Row) name.get();
        for (int i = 0; i < a.length; i++) {
            r.setBool(index(r, i), a[i]);
        }
    }

    public static void back(Ref name, Object[] a, char kind) {
        Row r = (Row) name.get();
        for (int i = 0; i < a.length; i++) {
            r.set(index(r, i), fromObject(a[i], kind));
        }
    }

    // ------------------------------------------------------------------
    // Procedures and functional interfaces.
    // ------------------------------------------------------------------

    /**
     * The Algol 68 procedure for the Java object TARGET of a functional
     * interface: ADAPTER (Object target, Object[] args) -> Object calls its
     * method (generated by GenJava).
     */
    public static Proc proc(Object target, java.lang.invoke.MethodHandle adapter) {
        if (target == null) {
            throw JavaObject.error("Java value null where a procedure is expected");
        }
        return new JavaFn(target, adapter);
    }
}
