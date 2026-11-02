// a68j runtime: the name of a struct field.
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

/** Name of a field of a structured value held in a location. */
public final class FieldRef extends Ref {
    public final Struct struct;
    public final int field;

    public FieldRef(Struct struct, int field) {
        this.struct = struct;
        this.field = field;
    }

    @Override
    public Object get() {
        return struct.f[field];
    }

    @Override
    public void set(Object v) {
        struct.f[field] = v;
    }

    @Override
    public boolean same(Ref o) {
        return o instanceof FieldRef r && r.struct == struct && r.field == field;
    }
}
