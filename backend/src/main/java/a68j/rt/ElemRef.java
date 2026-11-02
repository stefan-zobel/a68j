// a68j runtime: the name of a row element.
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

/** Name of an element of a row: the element storage of the row and an index into it. */
public final class ElemRef extends Ref {
    /** An Object[] or a primitive array (see {@link Row#store}). */
    public final Object data;
    public final int index;

    public ElemRef(Object data, int index) {
        this.data = data;
        this.index = index;
    }

    @Override
    public Object get() {
        return switch (data) {
            case Object[] a -> a[index];
            case int[] a -> a[index];
            case long[] a -> a[index];
            case float[] a -> a[index];
            case double[] a -> a[index];
            case boolean[] a -> a[index];
            default -> throw new IllegalStateException();
        };
    }

    @Override
    public void set(Object v) {
        switch (data) {
            case Object[] a -> a[index] = v;
            case int[] a -> a[index] = (Integer) v;
            case long[] a -> a[index] = (Long) v;
            case float[] a -> a[index] = (Float) v;
            case double[] a -> a[index] = (Double) v;
            case boolean[] a -> a[index] = (Boolean) v;
            default -> throw new IllegalStateException();
        }
    }

    @Override
    public boolean same(Ref o) {
        return o instanceof ElemRef e && e.data == data && e.index == index;
    }
}
