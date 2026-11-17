// a68j runtime: the dynamic parts of a format text (-fformats).
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
 * -fformats: the procedures {@code ints} and {@code formats} of a value of
 * the mode FORMAT ({@code Gformat} of the module Genie_Formats) built from a
 * format text. The compiler makes each dynamic part of the format text (the
 * unit of a replicator {@code n(...)}, a parameter of {@code g(...)}, the
 * format of {@code f(...)}) a procedure without parameters, and the text it
 * passes to {@code gfmtd} names the part {@code @i} by its index i, from 1;
 * calling this procedure with i calls the procedure of that part. So a
 * dynamic part is elaborated each time the format uses it, in the
 * environment of the format text.
 */
public final class FormatParts extends Proc {
    private static final Object[] NO_ARGS = {};

    private final Proc[] parts;

    public FormatParts(Proc[] parts) {
        this.parts = parts;
    }

    @Override
    public Object call(Object[] args) {
        return parts[(Integer) args[0] - 1].call(NO_ARGS);
    }

    /**
     * A parameter of g given as a clause that yields a row, as in
     * g(c | 6 | 7): the text of the format needs the number of parameters,
     * so the row is one parameter and must have one element.
     */
    public static int single(Row r, String file, int line) {
        if (r.dims != 1 || r.n0 != 1) {
            throw Rt.abort(file, line, "a row of parameters of g must have one element; give them one by one");
        }
        return r.getInt(r.offset);
    }
}
