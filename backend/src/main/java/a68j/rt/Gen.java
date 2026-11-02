// a68j runtime: generation of values from a mode description (as gen_mode in
// a68-low-generator.cc).
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
 * Builds fresh values (the initial value of a generator, SKIP values) from a
 * compact description of the mode, written by the code generator:
 *
 * <pre>
 *   i  int-like (0)        l  long (0)         f  float (0.0)   d  double (0.0)
 *   b  bool (false)        c  char (" ")       n  NIL / null proc
 *   s  string (empty)      v  void (null)
 *   R k e      row of k dimensions with element e: consumes 2k bounds
 *   S k e...   structure with k fields
 *   U m e      union whose SKIP is a value of mode number m described by e
 *   z e        a structure or union e without rows: a generator fills it
 *              with zeroes (ga68's fill_in_buffer memsets modes without
 *              rows), so its CHARs are NUL and a union is united with its
 *              first mode
 * </pre>
 *
 * <p>As in ga68, a generated row of INT, BOOL, CHAR, REAL or names is
 * filled with zeroes too (CHAR elements are NUL, not blank), while a
 * generated CHAR outside such a row is a blank (its SKIP value).
 *
 * <p>k and m are decimal numbers followed by ':'. Row bounds are taken
 * from {@code bounds} in the order of the description; the bounds of the
 * elements of a row are the same for every element. Missing bounds give
 * empty rows [1:0] (SKIP of a row mode).
 */
public final class Gen {
    private final String d;
    private final long[] bounds;
    private int pos;
    private int bpos;

    /** Generators leave united values uninitialised (mode -1), as ga68's fill_in_buffer. */
    private final boolean generator;
    /** Inside a part of a generated value that ga68 fills with zeroes ('z'). */
    private boolean zero;

    private Gen(String d, long[] bounds, boolean generator) {
        this.d = d;
        this.bounds = bounds;
        this.generator = generator;
    }

    public static Object gen(String desc, long[] bounds) {
        return new Gen(desc, bounds, true).value();
    }

    public static Object skip(String desc) {
        return new Gen(desc, new long[0], false).value();
    }

    private int num() {
        int start = pos;
        while (d.charAt(pos) != ':') {
            pos++;
        }
        return Integer.parseInt(d.substring(start, pos++));
    }

    private long bound(long dflt) {
        return bpos < bounds.length ? bounds[bpos++] : dflt;
    }

    private Object value() {
        char c = d.charAt(pos++);
        switch (c) {
            case 'i': return 0;
            case 'l': return 0L;
            case 'f': return 0.0f;
            case 'd': return 0.0;
            case 'b': return Boolean.FALSE;
            case 'c': return zero ? 0 : (int) ' ';
            case 'z': {
                boolean save = zero;
                zero = generator;
                Object v = value();
                zero = save;
                return v;
            }
            case 'n', 'v': return null;
            case 's': return Row.ofString("");
            case 'R': {
                int k = num();
                long[] lwb = new long[k];
                long[] upb = new long[k];
                for (int i = 0; i < k; i++) {
                    lwb[i] = bound(1);
                    upb[i] = bound(0);
                }
                int elemPos = pos;
                int elemBpos = bpos;
                char e = d.charAt(elemPos);
                // Rows of INT, CHAR, BITS, REAL, BOOL ... keep their elements
                // unboxed (Row#store); 'i' covers all int-like modes.
                boolean prim = "ilfdbc".indexOf(e) >= 0;
                Row r = Row.make(lwb, upb, prim ? (e == 'c' ? 'i' : e) : 'o');
                int n = (int) r.elems();
                // Build the first element even for an empty row, so that the
                // element's bounds are consumed.
                Object first = value();
                if (prim) {
                    // Zeroes, also for CHAR (ga68 memsets the elements).
                    return r;
                }
                if (n > 0) {
                    r.data[0] = first;
                }
                int endPos = pos;
                int endBpos = bpos;
                if (endPos == elemPos + 1 && "nv".indexOf(e) >= 0) {
                    // An immutable element: the same value everywhere.
                    java.util.Arrays.fill(r.data, first);
                    return r;
                }
                for (int i = 1; i < n; i++) {
                    pos = elemPos;
                    bpos = elemBpos;
                    r.data[i] = value();
                }
                pos = endPos;
                bpos = endBpos;
                return r;
            }
            case 'S': {
                int k = num();
                Object[] f = new Object[k];
                for (int i = 0; i < k; i++) {
                    f[i] = value();
                }
                return new Struct(f);
            }
            case 'U': {
                int m = num();
                Object v = value();
                return zero ? new Union(m, v) : generator ? new Union(-1, null) : new Union(m, v);
            }
            default:
                throw new IllegalStateException("bad mode description " + d + " at " + (pos - 1));
        }
    }
}
