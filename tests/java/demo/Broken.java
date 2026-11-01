// A class whose static initializer throws, for tests/java/nest-java-init-error.a68 (compiled by tools/java_tests.sh).
package demo;

public final class Broken {
    private Broken() {}

    /** A public static field; reading it runs the static initializer, which throws. */
    public static final int LIMIT = Integer.parseInt(System.getProperty("demo.broken.limit", "none"));
}
