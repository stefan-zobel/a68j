// Java code that calls Algol 68 procedures, for examples/java-callbacks.a68.
// Compile and run:
//   javac -d classes examples/Events.java
//   java -jar a68j-backend.jar -fnest-java -fjava-classpath=classes -run examples/java-callbacks.a68

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

public final class Events {
    private Events() {}

    /**
     * An interface of our own; with one abstract method, an Algol 68 procedure fits it.
     * In Algol 68: proc (string, int) void
     */
    public interface Handler {
        void handle(String event, int value);
    }

    private static final List<Handler> handlers = new ArrayList<>();

    /**
     * Keeps the handler; it is called later, after register has returned.
     * In Algol 68: proc (proc (string, int) void) void register = nest Java "Events.register"
     */
    public static void register(Handler h) {
        handlers.add(h);
    }

    /**
     * Sends an event to every handler registered so far.
     * In Algol 68: proc (string, int) void fire = nest Java "Events.fire"
     */
    public static void fire(String event, int value) {
        for (Handler h : handlers) {
            h.handle(event, value);
        }
    }

    /**
     * Newton's method: f and its derivative df are Algol 68 procedures.
     * In Algol 68: proc (proc (long real) long real, proc (long real) long real, long real) long real
     *    newton = nest Java "Events.newton"
     */
    public static double newton(DoubleUnaryOperator f, DoubleUnaryOperator df, double x) {
        for (int i = 0; i < 50; i++) {
            double step = f.applyAsDouble(x) / df.applyAsDouble(x);
            x -= step;
            if (Math.abs(step) < 1e-15) {
                break;
            }
        }
        return x;
    }
}
