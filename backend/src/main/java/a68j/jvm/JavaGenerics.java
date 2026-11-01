package a68j.jvm;

import a68j.front.Node;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Type arguments in a Java formal hole (an a68j extension, section 1b of
 * docs/java-modules.md), written as in Java:
 *
 * <pre>
 * proc (Stringlist, int) string get = nest Java "java.util.ArrayList&lt;String&gt;::get";
 * proc (Map, string) Stringlist find = nest Java "java.util.HashMap&lt;String, java.util.List&lt;String&gt;&gt;::get";
 * </pre>
 *
 * The type variables of the class (and of its superclasses and
 * superinterfaces) then stand for the type arguments in the parameters and
 * the result of the member (GenJava.Target#bound): {@code E} of the
 * {@code get} of {@code ArrayList<String>} is {@code String}. So the modes
 * are checked against {@code String} when the program is compiled, and the
 * result is checked when the program runs, as javac does with a
 * {@code checkcast} (JavaConv#checked).
 *
 * <p>A type is a class name, optionally with type arguments, followed by any
 * number of {@code []}. A class of {@code java.lang} may be given by its
 * simple name ({@code String}, {@code Integer}), any other class by its full
 * name, a nested class with dots ({@code java.util.Map.Entry}). A type
 * argument is a type or, inside another type argument, a wildcard
 * ({@code ?}, {@code ? extends T}, {@code ? super T}): the class of the
 * formal hole stands for an object, which has no wildcard type, as in Java
 * {@code new ArrayList<?>()} is no expression. A primitive type is a type
 * argument only as the component of an array ({@code int[]}). The number of type arguments must be that of the type
 * parameters of the class, and each must be within their bounds (as far as
 * the erasures say). A raw type argument ({@code List} without arguments)
 * is allowed, as in Java.
 *
 * <p>The types made here implement the interfaces of java.lang.reflect, so
 * that they go wherever the generic types of reflection go (GenJava#sam,
 * GenJava#resolve).
 */
final class JavaGenerics {

    private JavaGenerics() {}

    /** A parameterized type {@code RAW<ARGS>} (OWNER: the declaring class of RAW or null). */
    record PType(Class<?> raw, Type[] args, Type owner) implements ParameterizedType {
        @Override public Type[] getActualTypeArguments() { return args.clone(); }
        @Override public Type getRawType() { return raw; }
        @Override public Type getOwnerType() { return owner; }
        @Override public String getTypeName() {
            return raw.getTypeName() + Arrays.stream(args).map(Type::getTypeName).collect(Collectors.joining(", ", "<", ">"));
        }
        @Override public String toString() { return getTypeName(); }
    }

    /** A wildcard: {@code ?} (UPPER Object, no LOWER), {@code ? extends T} or {@code ? super T}. */
    record Wild(Type[] upper, Type[] lower) implements WildcardType {
        @Override public Type[] getUpperBounds() { return upper.clone(); }
        @Override public Type[] getLowerBounds() { return lower.clone(); }
        @Override public String getTypeName() {
            return lower.length > 0 ? "? super " + lower[0].getTypeName()
                    : upper[0] == Object.class ? "?" : "? extends " + upper[0].getTypeName();
        }
        @Override public String toString() { return getTypeName(); }
    }

    /** An array of a parameterized type or of a type variable. */
    record GArray(Type component) implements GenericArrayType {
        @Override public Type getGenericComponentType() { return component; }
        @Override public String getTypeName() { return component.getTypeName() + "[]"; }
        @Override public String toString() { return getTypeName(); }
    }

    /**
     * The type TEXT of the class of the formal hole P (the part before
     * {@code ::}): the class itself if it has no type arguments, else a
     * {@link PType}. A wrong type is a compile-time error.
     */
    static Type classType(Node p, String text) {
        int lt = text.indexOf('<');
        if (lt < 0) {
            return GenJava.javaClass(p, text.strip());
        }
        Parser q = new Parser(p, text);
        Type t = q.type(true);
        q.skip();
        if (q.at < text.length() || !(t instanceof PType)) {
            throw q.invalid();
        }
        return t;
    }

    /** The class of the type T (a class, the raw type of a parameterized type), or null. */
    static Class<?> raw(Type t) {
        return t instanceof Class<?> c ? c
                : t instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c ? c : null;
    }

    /**
     * The type T with the type variables that ENV binds replaced by what
     * they stand for, also inside type arguments, wildcards and arrays
     * ({@code Consumer<? super E>} becomes {@code Consumer<? super String>}
     * for E bound to String). Unbound type variables stay as they are.
     */
    static Type subst(Type t, Map<TypeVariable<?>, Type> env) {
        return subst(t, env, 0);
    }

    private static Type subst(Type t, Map<TypeVariable<?>, Type> env, int depth) {
        if (depth > 20) {
            return Object.class;
        } else if (t instanceof TypeVariable<?> v) {
            Type b = env.get(v);
            return b == null ? v : subst(b, env, depth + 1);
        } else if (t instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
            Type[] as = pt.getActualTypeArguments();
            for (int i = 0; i < as.length; i++) {
                as[i] = subst(as[i], env, depth + 1);
            }
            return new PType(c, as, pt.getOwnerType());
        } else if (t instanceof WildcardType w) {
            return new Wild(substAll(w.getUpperBounds(), env, depth), substAll(w.getLowerBounds(), env, depth));
        } else if (t instanceof GenericArrayType ga) {
            Type c = subst(ga.getGenericComponentType(), env, depth + 1);
            return c instanceof Class<?> k ? k.arrayType() : new GArray(c);
        }
        return t;
    }

    private static Type[] substAll(Type[] ts, Map<TypeVariable<?>, Type> env, int depth) {
        Type[] r = new Type[ts.length];
        for (int i = 0; i < ts.length; i++) {
            r[i] = subst(ts[i], env, depth + 1);
        }
        return r;
    }

    /** A recursive descent parser of the type TEXT of the formal hole P. */
    private static final class Parser {
        final Node p;
        final String text;
        int at;

        Parser(Node p, String text) {
            this.p = p;
            this.text = text;
        }

        Gen.Fatal invalid() {
            return new Gen.Fatal(p, "invalid type arguments in the Java formal hole '" + text + "'");
        }

        void skip() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }

        boolean accept(char c) {
            skip();
            if (at < text.length() && text.charAt(at) == c) {
                at++;
                return true;
            }
            return false;
        }

        /** A name: Java identifiers separated by dots. */
        String name() {
            skip();
            int start = at;
            while (at < text.length() && (Character.isJavaIdentifierPart(text.charAt(at)) || text.charAt(at) == '.')) {
                at++;
            }
            String n = text.substring(start, at);
            if (n.isEmpty() || !Character.isJavaIdentifierStart(n.charAt(0)) || n.endsWith(".") || n.contains("..")) {
                throw invalid();
            }
            return n;
        }

        /**
         * type: name [ "<" argument { "," argument } ">" ] { "[" "]" }. OUTER:
         * the class of the formal hole, whose arguments are no wildcards.
         */
        Type type(boolean outer) {
            String n = name();
            Type t = primitive(n);
            if (t == null) {
                Class<?> c = lookup(n);
                if (accept('<')) {
                    List<Type> as = new ArrayList<>();
                    do {
                        as.add(argument(outer));
                    } while (accept(','));
                    if (!accept('>')) {
                        throw invalid();
                    }
                    t = parameterized(c, as.toArray(new Type[0]));
                } else {
                    t = c;
                }
            }
            boolean array = false;
            while (accept('[')) {
                if (!accept(']')) {
                    throw invalid();
                }
                t = t instanceof Class<?> k ? k.arrayType() : new GArray(t);
                array = true;
            }
            if (!array && t instanceof Class<?> k && k.isPrimitive()) {
                throw new Gen.Fatal(p, "the primitive type " + n + " is no type argument (in the Java formal hole '"
                        + text + "'), use its class (Integer for int ...)");
            }
            return t;
        }

        /** argument: type | "?" [ ( "extends" | "super" ) type ] (not for OUTER, see {@link #type}). */
        Type argument(boolean outer) {
            if (!accept('?')) {
                return type(false);
            }
            if (outer) {
                throw new Gen.Fatal(p, "a wildcard is no type argument of the class of the Java formal hole '" + text
                        + "', which stands for an object (only inside another type argument)");
            }
            skip();
            if (text.startsWith("extends", at) && !identifierAt(at + 7)) {
                at += 7;
                return new Wild(new Type[] {type(false)}, new Type[0]);
            }
            if (text.startsWith("super", at) && !identifierAt(at + 5)) {
                at += 5;
                return new Wild(new Type[] {Object.class}, new Type[] {type(false)});
            }
            return new Wild(new Type[] {Object.class}, new Type[0]);
        }

        private boolean identifierAt(int i) {
            return i < text.length() && Character.isJavaIdentifierPart(text.charAt(i));
        }

        /** C with the type arguments AS: their number and their bounds are checked. */
        Type parameterized(Class<?> c, Type[] as) {
            TypeVariable<?>[] vs = c.getTypeParameters();
            if (vs.length != as.length) {
                throw new Gen.Fatal(p, "the Java class " + c.getTypeName() + " has " + vs.length
                        + " type parameter" + (vs.length == 1 ? "" : "s") + ", not " + as.length
                        + " (in the Java formal hole '" + text + "')");
            }
            for (int i = 0; i < vs.length; i++) {
                Class<?> a = raw(as[i]);
                if (a == null) {
                    // A wildcard, an array of a parameterized type: not checked.
                    continue;
                }
                for (Type b : vs[i].getBounds()) {
                    Class<?> e = raw(b);
                    if (e != null && !e.isAssignableFrom(a)) {
                        throw new Gen.Fatal(p, "the type argument " + as[i].getTypeName() + " is not within the bound "
                                + b.getTypeName() + " of " + vs[i].getName() + " of the Java class " + c.getTypeName()
                                + " (in the Java formal hole '" + text + "')");
                    }
                }
            }
            return new PType(c, as, c.getDeclaringClass());
        }

        /** The class N: a class of java.lang by its simple name, else by its full name. */
        Class<?> lookup(String n) {
            if (n.indexOf('.') < 0) {
                try {
                    return Class.forName("java.lang." + n, false, GenJava.loader);
                } catch (ClassNotFoundException | LinkageError e) {
                    throw new Gen.Fatal(p, "Java class '" + n + "' not found (a class outside java.lang needs its"
                            + " full name, in the Java formal hole '" + text + "')");
                }
            }
            return GenJava.javaClass(p, n);
        }

        static Class<?> primitive(String n) {
            return switch (n) {
                case "boolean" -> boolean.class;
                case "byte" -> byte.class;
                case "char" -> char.class;
                case "short" -> short.class;
                case "int" -> int.class;
                case "long" -> long.class;
                case "float" -> float.class;
                case "double" -> double.class;
                default -> null;
            };
        }
    }
}
