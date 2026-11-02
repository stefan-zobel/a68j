// a68j runtime: the threads of a parallel clause, JDK 25 version.
//
// Copyright (C) 2026 The a68j authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH GCC-exception-3.1
//
// This file is part of a68j. It is licensed under the GNU General Public
// License, version 3 or later. Under Section 7 of GPL version 3, you are
// granted additional permissions described in the GCC Runtime Library
// Exception, version 3.1, as published by the Free Software Foundation; see
// the files COPYING and COPYING.RUNTIME.
//
// Additional permission under GNU GPL version 3 section 7: for this file, a
// Compilation Process done with a68j, alone or with other GPL-compatible
// software, is an Eligible Compilation Process in the sense of the GCC
// Runtime Library Exception, version 3.1.
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
