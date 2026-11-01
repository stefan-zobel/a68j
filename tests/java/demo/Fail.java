// Java methods that throw, for tests/java/nest-java-exception*.a68 (compiled by tools/java_tests.sh).
package demo;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class Fail {
    private Fail() {}

    /** Throws an exception with a cause. */
    public static int wrapped(String file) {
        throw new IllegalStateException("cannot load " + file, new java.io.FileNotFoundException(file));
    }

    /** A supplier that throws when it is called, not when it is made. */
    public static IntSupplier later(String text) {
        return () -> {
            throw new UnsupportedOperationException(text);
        };
    }

    /** Calls C with 1 .. N. */
    public static void each(int n, IntConsumer c) {
        for (int i = 1; i <= n; i++) {
            c.accept(i);
        }
    }

    /** Waits (in Thread.sleep) until the thread is interrupted; 0 if it never is. */
    public static int waitForInterrupt() throws InterruptedException {
        Thread.sleep(60_000);
        return 0;
    }
}
