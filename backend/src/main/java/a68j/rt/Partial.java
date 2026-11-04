// a68j runtime: a procedure value made by partial parametrization.
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

/**
 * The yield of a call that leaves arguments out, {@code f (3, )}: the
 * procedure {@code f} together with the arguments given, which were
 * evaluated when the call was made (partial parametrization, C. H. Lindsey,
 * ALGOL Bulletin 39, AB39.3.1, 1976, as Algol 68 Genie implements it).
 * Calling it with the missing arguments calls {@code f} with all of them.
 *
 * <p>A partial parametrization of a partial parametrization is flattened:
 * the result refers to the original procedure, with the arguments of both
 * stages, so a call never goes through more than one {@code Partial}.
 */
public final class Partial extends Proc {
    /** The procedure parametrized; never a Partial. */
    private final Proc proc;
    /** The arguments of proc, by position; the missing positions are null. */
    private final Object[] given;
    /** The positions in given of the missing arguments, in order. */
    private final int[] missing;

    private Partial(Proc proc, Object[] given, int[] missing) {
        this.proc = proc;
        this.given = given;
        this.missing = missing;
    }

    /**
     * The partial parametrization of PROC with ARGS: one element per
     * parameter of PROC, holding the boxed argument, where PRESENT is set;
     * the other elements are ignored. PRESENT has bit i set if argument i is
     * given; the code generator rejects a partial call of a procedure with
     * more than 64 parameters.
     */
    public static Proc make(Proc proc, Object[] args, long present) {
        if (proc instanceof Partial p) {
            // Bit i of PRESENT refers to the i-th missing argument of p.
            Object[] given = p.given.clone();
            int[] missing = new int[p.missing.length - Long.bitCount(present)];
            int k = 0;
            for (int i = 0; i < p.missing.length; i++) {
                if ((present & (1L << i)) != 0) {
                    given[p.missing[i]] = args[i];
                } else {
                    missing[k++] = p.missing[i];
                }
            }
            return new Partial(p.proc, given, missing);
        }
        int[] missing = new int[args.length - Long.bitCount(present)];
        int k = 0;
        for (int i = 0; i < args.length; i++) {
            if ((present & (1L << i)) == 0) {
                missing[k++] = i;
            }
        }
        return new Partial(proc, args, missing);
    }

    @Override
    public Object call(Object[] args) {
        Object[] all = given.clone();
        for (int i = 0; i < missing.length; i++) {
            all[missing[i]] = args[i];
        }
        return proc.call(all);
    }
}
