import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

public final class Events {
    private Events() {}

    // An interface with one abstract method: an Algol 68 function
    // proc (string, int) void fits it.
    public interface Handler {
        void handle(String event, int value);
    }

    private static final List<Handler> handlers = new ArrayList<>();

    // Keeps the handler, to call it later.
    public static void register(Handler h) {
        handlers.add(h);
    }

    // Calls every handler registered so far.
    public static void fire(String event, int value) {
        for (Handler h : handlers) {
            h.handle(event, value);
        }
    }

    // Newton's method: f and its derivative df are Algol 68 functions.
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
