// a68j Java library: durations for the module Times.
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
package a68j.jlib;

import a68j.rt.JavaObject;

import java.time.Duration;

/**
 * The Java side of the mode Duration of the Algol 68 module Times
 * (backend/lib/times.a68): an amount of time in seconds and nanoseconds, a
 * java.time.Duration. Immutable; only static methods, as they are called
 * through formal holes (nest Java).
 */
public final class DurationValue extends JavaObject {
    final Duration v;

    private DurationValue(Duration v) {
        this.v = v;
    }

    @Override
    protected String javaClassName() {
        return "Duration";
    }

    static DurationValue make(Duration v) {
        return new DurationValue(v);
    }

    /** The value of D; NIL is a runtime error. */
    static Duration val(DurationValue d) {
        if (d == null) {
            throw error("attempt to use a NIL Duration");
        }
        return d.v;
    }

    private static RuntimeException range() {
        return error("duration out of range");
    }

    // ------------------------------------------------------------------
    // Construction and text.
    // ------------------------------------------------------------------

    /** HOURS n. */
    public static DurationValue hours(long n) {
        try {
            return make(Duration.ofHours(n));
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    /** MINUTES n. */
    public static DurationValue minutes(long n) {
        try {
            return make(Duration.ofMinutes(n));
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    /** SECONDS n. */
    public static DurationValue seconds(long n) {
        return make(Duration.ofSeconds(n));
    }

    /** duration_text: ISO 8601, PT1H30M. */
    public static String text(DurationValue d) {
        return val(d).toString();
    }

    /** duration_seconds: the seconds with their fraction. */
    public static double toSeconds(DurationValue d) {
        Duration x = val(d);
        return x.getSeconds() + x.getNano() / 1e9;
    }

    /** duration_millis: the milliseconds, truncated toward zero. */
    public static long toMillis(DurationValue d) {
        Duration x = val(d);
        try {
            return x.toMillis();
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    // ------------------------------------------------------------------
    // Arithmetic and comparison.
    // ------------------------------------------------------------------

    public static DurationValue add(DurationValue a, DurationValue b) {
        Duration x = val(a);
        Duration y = val(b);
        try {
            return make(x.plus(y));
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    public static DurationValue subtract(DurationValue a, DurationValue b) {
        Duration x = val(a);
        Duration y = val(b);
        try {
            return make(x.minus(y));
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    public static DurationValue multiply(DurationValue a, long n) {
        Duration x = val(a);
        try {
            return make(x.multipliedBy(n));
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    /** a % n: truncated toward zero, to the nanosecond. */
    public static DurationValue divide(DurationValue a, long n) {
        Duration x = val(a);
        if (n == 0) {
            throw error("division by zero");
        }
        try {
            return make(x.dividedBy(n));
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    public static DurationValue negate(DurationValue a) {
        Duration x = val(a);
        try {
            return make(x.negated());
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    public static DurationValue abs(DurationValue a) {
        Duration x = val(a);
        try {
            return make(x.abs());
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    /** Negative, zero or positive as A is shorter than, equal to or longer than B. */
    public static int compare(DurationValue a, DurationValue b) {
        return Integer.signum(val(a).compareTo(val(b)));
    }
}
