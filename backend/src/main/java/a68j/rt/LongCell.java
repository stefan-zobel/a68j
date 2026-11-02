// a68j runtime: a location of mode long kept unboxed.
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
 * A location created by a generator or a variable declaration of a
 * primitive mode, holding its value unboxed (see Rt#derefL, Rt#assignL).
 */
public final class LongCell extends Ref {
    public long v;

    public LongCell(long v) {
        this.v = v;
    }

    @Override
    public Object get() {
        return v;
    }

    @Override
    public void set(Object o) {
        v = (Long) o;
    }
}
