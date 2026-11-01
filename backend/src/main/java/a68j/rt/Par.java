// a68j runtime: parallel clauses and semaphores (-fpar-threads). GPLv3+.
package a68j.rt;

import java.util.concurrent.ThreadFactory;

/**
 * The parallel clause PAR (u1, u2, ...) of a program compiled with
 * -fpar-threads (an a68j extension; ga68 elaborates PAR sequentially and
 * has no SEMA operators), and the SEMA operators LEVEL, UP and DOWN.
 *
 * <p>Each unit runs in a thread of its own. The clause ends when all units
 * have ended (RR 3.3.2.c). If a unit ends abnormally, by a jump out of the
 * clause or a runtime error, the other units are cancelled: their threads
 * are interrupted, so a DOWN that waits ends at once (a unit that only
 * computes runs to its end). The first such exception is then rethrown in
 * the thread that elaborates the clause, where the jump goes on.
 *
 * <p>{@link ParRun} starts and joins the threads: in the version for JDK 25
 * with plain threads, in the version for JDK 28 (META-INF/versions/28 of
 * the multi-release jar) with structured concurrency
 * (java.util.concurrent.StructuredTaskScope, JEP 543). Both behave the same.
 *
 * <p>The threads are platform threads with the large stack of the main
 * thread (Rt#start), as the stack of a virtual thread is limited by -Xss.
 * With -Da68j.par=virtual they are virtual threads.
 */
public final class Par {
    private Par() {}

    /**
     * Whether a parallel clause has been elaborated: the runtime must then be
     * thread safe. Not volatile (it is read by every random): it is set
     * before the first unit thread starts, which the threads therefore see.
     */
    public static boolean threads;

    static final ThreadFactory FACTORY = "virtual".equals(System.getProperty("a68j.par"))
            ? Thread.ofVirtual().name("par-", 0).factory()
            : Thread.ofPlatform().name("par-", 0).stackSize(Rt.STACK_SIZE).factory();

    private static final Object[] NO_ARGS = new Object[0];

    /** Thrown in a unit that waits in DOWN when the clause is cancelled; never seen by the program. */
    static final class Cancelled extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Cancelled() {
            super(null, null, false, false);
        }
    }

    /** Elaborate the units in parallel; see the class comment. */
    public static void run(Proc[] units) {
        threads = true;
        ParRun.run(units);
    }

    /** The number of units running in threads. */
    private static final java.util.concurrent.atomic.AtomicInteger ACTIVE = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Whether the current thread is that of a unit (set by {@link #unit}):
     * such a thread is interrupted only to cancel the unit, so an
     * interruption seen by a Java method that the unit called ends the unit
     * (Rt#javaException).
     */
    private static final ThreadLocal<Boolean> IN_UNIT = new ThreadLocal<>();

    /** Whether the current thread is that of a unit of a parallel clause. */
    static boolean inUnit() {
        return IN_UNIT.get() != null;
    }

    /** The body of the thread of a unit. */
    static void unit(Proc u) {
        ACTIVE.incrementAndGet();
        IN_UNIT.set(Boolean.TRUE);
        try {
            u.call(NO_ARGS);
        } catch (StackOverflowError e) {
            // As in Rt#start: the segmentation fault of a native program.
            Rt.OUT.flush();
            Runtime.getRuntime().halt(139);
        } finally {
            IN_UNIT.remove();
            ACTIVE.decrementAndGet();
        }
    }

    /** Rethrow the exception of a failed unit in the thread of the clause. */
    static void rethrow(Throwable e) {
        if (e instanceof RuntimeException r) {
            throw r;
        }
        if (e instanceof Error r) {
            throw r;
        }
        throw new IllegalStateException(e);
    }

    // ------------------------------------------------------------------
    // SEMA (RR 10.2.4): MODE SEMA = STRUCT (REF INT F). The REF INT is an
    // IntCell; its monitor guards its value.
    // ------------------------------------------------------------------

    /** OP LEVEL = (INT a) SEMA: a new semaphore with value A. */
    public static Struct level(int a) {
        return new Struct(new Object[] {new IntCell(a)});
    }

    private static IntCell cell(Struct s, String file, int line) {
        Object f = s.f[0];
        if (f instanceof IntCell c) {
            return c;
        }
        throw Rt.abort(file, line, "attempt to use an uninitialised semaphore");
    }

    /** OP LEVEL = (SEMA s) INT: the value of S. */
    public static int level(Struct s, String file, int line) {
        IntCell c = cell(s, file, line);
        synchronized (c) {
            return c.v;
        }
    }

    /** OP UP = (SEMA s) VOID: increment S and wake the units waiting in DOWN. */
    public static void up(Struct s, String file, int line) {
        IntCell c = cell(s, file, line);
        synchronized (c) {
            c.v++;
            c.notifyAll();
        }
    }

    /**
     * OP DOWN = (SEMA s) VOID: wait until S is positive, then decrement it.
     * A DOWN that can never succeed because no unit of a parallel clause
     * runs (so no one can UP the semaphore) ends the program with an error.
     * A deadlock of units that wait for each other is not detected.
     */
    public static void down(Struct s, String file, int line) {
        IntCell c = cell(s, file, line);
        synchronized (c) {
            while (c.v <= 0) {
                if (ACTIVE.get() == 0) {
                    throw Rt.abort(file, line, "deadlock: DOWN on a semaphore with value " + c.v
                            + " outside a parallel clause");
                }
                try {
                    c.wait();
                } catch (InterruptedException e) {
                    throw new Cancelled();
                }
            }
            c.v--;
        }
    }
}
