// Java methods with functional interfaces for tests/java/nest-java-fn.a68 (compiled by tools/java_tests.sh).
package demo;

import java.util.function.*;

public final class Fns {
    private Fns() {}

    /** A functional interface of the user (not from java.util.function). */
    public interface Visitor {
        void visit(int depth, String name);
    }

    public static int apply(IntUnaryOperator f, int x) { return f.applyAsInt(x); }

    /** Simpson's rule with n intervals (n even). */
    public static double integrate(DoubleUnaryOperator f, double a, double b, int n) {
        double h = (b - a) / n, s = f.applyAsDouble(a) + f.applyAsDouble(b);
        for (int i = 1; i < n; i++) {
            s += (i % 2 == 0 ? 2 : 4) * f.applyAsDouble(a + i * h);
        }
        return s * h / 3;
    }

    public static IntUnaryOperator adder(int k) { return x -> x + k; }

    public static IntUnaryOperator compose(IntUnaryOperator f, IntUnaryOperator g) { return f.andThen(g); }

    public static <T> T twice(UnaryOperator<T> f, T x) { return f.apply(f.apply(x)); }

    public static void each(int n, IntConsumer c) {
        for (int i = 1; i <= n; i++) {
            c.accept(i);
        }
    }

    public static int count(Predicate<String> p, String[] words) {
        int n = 0;
        for (String w : words) {
            if (p.test(w)) {
                n++;
            }
        }
        return n;
    }

    public static void walk(Visitor v) {
        v.visit(0, "root");
        v.visit(1, "left");
        v.visit(2, "leaf");
        v.visit(1, "right");
    }

    public static Supplier<String> constant(String s) { return () -> s; }

    public static BiFunction<Long, Double, String> describe() { return (n, x) -> n + " and " + x; }

    public static long fold(long[] a, long z, LongBinaryOperator f) {
        for (long x : a) {
            z = f.applyAsLong(z, x);
        }
        return z;
    }

    public static boolean any(boolean[] a, Function<Boolean, Boolean> f) {
        for (boolean x : a) {
            if (f.apply(x)) {
                return true;
            }
        }
        return false;
    }

    public static String chars(String s, IntFunction<Character> f) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            b.append(f.apply(s.charAt(i)));
        }
        return b.toString();
    }

    public static float half(Function<Float, Float> f) { return f.apply(1.0f); }
}
