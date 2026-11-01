// a68j runtime: the threads of a parallel clause, JDK 28 version. GPLv3+.
package a68j.rt;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;

/**
 * Starts and joins the threads of the units of a parallel clause (see
 * {@link Par}) with structured concurrency (JEP 543, final in JDK 28): each
 * unit is a subtask of a StructuredTaskScope. The joiner
 * awaitAllSuccessfulOrThrow waits for all units; the first that fails
 * cancels the scope, which interrupts the others, and join rethrows its
 * exception (a jump or a runtime error). This class is the version for
 * JDK 28 and newer under META-INF/versions/28 of the multi-release jar.
 */
final class ParRun {
    private ParRun() {}

    static void run(Proc[] units) {
        try (var scope = StructuredTaskScope.open(Joiner.<Object, Throwable>awaitAllSuccessfulOrThrow(e -> e),
                cf -> cf.withThreadFactory(Par.FACTORY).withName("par"))) {
            for (Proc u : units) {
                scope.fork(() -> Par.unit(u));
            }
            scope.join();
        } catch (InterruptedException e) {
            // This clause is itself in a unit of a cancelled clause; closing
            // the scope has cancelled and joined the units.
            throw new Par.Cancelled();
        } catch (Throwable e) {
            Par.rethrow(e);
        }
    }
}
