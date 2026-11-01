// a68j runtime: the threads of a parallel clause, JDK 25 version. GPLv3+.
package a68j.rt;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts and joins the threads of the units of a parallel clause (see
 * {@link Par}), with plain threads. The multi-release jar has a version for
 * JDK 28 under META-INF/versions/28 that uses structured concurrency
 * (src/main/java28/a68j/rt/ParRun.java); both behave the same.
 */
final class ParRun {
    private ParRun() {}

    static void run(Proc[] units) {
        AtomicReference<Throwable> first = new AtomicReference<>();
        Thread[] ts = new Thread[units.length];
        for (int i = 0; i < units.length; i++) {
            Proc u = units[i];
            ts[i] = Par.FACTORY.newThread(() -> {
                try {
                    Par.unit(u);
                } catch (Par.Cancelled e) {
                    // cancelled because another unit failed
                } catch (Throwable e) {
                    // The first failure cancels the other units.
                    if (first.compareAndSet(null, e)) {
                        for (Thread t : ts) {
                            if (t != Thread.currentThread()) {
                                t.interrupt();
                            }
                        }
                    }
                }
            });
        }
        for (Thread t : ts) {
            t.start();
        }
        boolean interrupted = false;
        for (Thread t : ts) {
            while (true) {
                try {
                    t.join();
                    break;
                } catch (InterruptedException e) {
                    // This clause is itself in a unit of a cancelled clause.
                    interrupted = true;
                    for (Thread u : ts) {
                        u.interrupt();
                    }
                }
            }
        }
        Throwable e = first.get();
        if (e != null) {
            Par.rethrow(e);
        }
        if (interrupted) {
            throw new Par.Cancelled();
        }
    }
}
