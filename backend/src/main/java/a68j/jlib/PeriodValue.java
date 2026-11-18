// a68j Java library: periods of the calendar for the module Times.
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

import java.time.Period;

/**
 * The Java side of the mode Period of the Algol 68 module Times
 * (backend/lib/times.a68): years, months and days of the calendar, a
 * java.time.Period. Unlike a Duration, its length depends on the date it is
 * added to: a month is 28 to 31 days. Immutable; only static methods, as
 * they are called through formal holes (nest Java).
 */
public final class PeriodValue extends JavaObject {
    final Period v;

    private PeriodValue(Period v) {
        this.v = v;
    }

    @Override
    protected String javaClassName() {
        return "Period";
    }

    static PeriodValue make(Period v) {
        return new PeriodValue(v);
    }

    /** The value of P; NIL is a runtime error. */
    static Period val(PeriodValue p) {
        if (p == null) {
            throw error("attempt to use a NIL Period");
        }
        return p.v;
    }

    private static RuntimeException range() {
        return error("period out of range");
    }

    /** DAYS n. */
    public static PeriodValue days(int n) {
        return make(Period.ofDays(n));
    }

    /** WEEKS n. */
    public static PeriodValue weeks(int n) {
        try {
            return make(Period.ofWeeks(n));
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    /** MONTHS n. */
    public static PeriodValue months(int n) {
        return make(Period.ofMonths(n));
    }

    /** YEARS n. */
    public static PeriodValue years(int n) {
        return make(Period.ofYears(n));
    }

    /** period_text: ISO 8601, P1Y2M3D. */
    public static String text(PeriodValue p) {
        return val(p).toString();
    }

    public static PeriodValue add(PeriodValue a, PeriodValue b) {
        Period x = val(a);
        Period y = val(b);
        try {
            return make(x.plus(y));
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    public static PeriodValue subtract(PeriodValue a, PeriodValue b) {
        Period x = val(a);
        Period y = val(b);
        try {
            return make(x.minus(y));
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    public static PeriodValue negate(PeriodValue a) {
        Period x = val(a);
        try {
            return make(x.negated());
        } catch (ArithmeticException e) {
            throw range();
        }
    }

    /** a = b: the same years, months and days (MONTHS 12 is not YEARS 1). */
    public static boolean equal(PeriodValue a, PeriodValue b) {
        return val(a).equals(val(b));
    }
}
