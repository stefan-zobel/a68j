// a68j runtime: activation records for captured identifiers.
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

/** Captured identifiers of one routine invocation, linked to the enclosing routine's frame. */
public final class Frame {
    public final Frame up;
    public final Object[] s;

    private static final Object[] NONE = new Object[0];

    public Frame(Frame up, int size) {
        this.up = up;
        this.s = size == 0 ? NONE : new Object[size];
    }
}
