// A Java class of the user for tests/java/nest-java-classpath.a68 (compiled by tools/java_tests.sh).
package demo;
public final class Util {
    public static String greet(String name) { return "Hallo, " + name + "!"; }
    public static long fib(int n) { long a = 0, b = 1; for (int i = 0; i < n; i++) { long t = a + b; a = b; b = t; } return a; }
}
