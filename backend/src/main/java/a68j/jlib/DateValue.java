// a68j Java library: calendar dates for the module Times.
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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * The Java side of the mode Date of the Algol 68 module Times
 * (backend/lib/times.a68): a date without a time of day and without a time
 * zone, a java.time.LocalDate. Immutable; only static methods, as they are
 * called through formal holes (nest Java). Errors are runtime errors at the
 * position of the program that called the procedure or operator.
 */
public final class DateValue extends JavaObject {
    final LocalDate v;

    private DateValue(LocalDate v) {
        this.v = v;
    }

    @Override
    protected String javaClassName() {
        return "Date";
    }

    static DateValue make(LocalDate v) {
        return new DateValue(v);
    }

    /** The value of D; NIL is a runtime error. */
    static LocalDate val(DateValue d) {
        if (d == null) {
            throw error("attempt to use a NIL Date");
        }
        return d.v;
    }

    // ------------------------------------------------------------------
    // Construction and text.
    // ------------------------------------------------------------------

    /** date (y, m, d). */
    public static DateValue of(int year, int month, int day) {
        return make(Times.date(year, month, day));
    }

    /** today: the date of the system clock in the zone of the system. */
    public static DateValue today() {
        return make(LocalDate.now());
    }

    /** date_text: ISO 8601, 2026-11-18. */
    public static String text(DateValue d) {
        return val(d).toString();
    }

    /** date_from_text: ISO 8601. */
    public static DateValue fromText(String s) {
        try {
            return make(LocalDate.parse(s));
        } catch (DateTimeParseException e) {
            throw error("cannot read " + Times.quote(s) + " as a date");
        }
    }

    /** date_format (d, pattern). */
    public static String format(DateValue d, String pattern) {
        LocalDate x = val(d);
        DateTimeFormatter f = Times.formatter(pattern);
        try {
            return f.format(x);
        } catch (DateTimeException e) {
            throw error("cannot format a date with the pattern " + Times.quote(pattern));
        }
    }

    /** date_from_format (s, pattern). */
    public static DateValue fromFormat(String s, String pattern) {
        DateTimeFormatter f = Times.formatter(pattern);
        try {
            return make(LocalDate.parse(s, f));
        } catch (DateTimeException e) {
            throw error("cannot read " + Times.quote(s) + " with the pattern " + Times.quote(pattern));
        }
    }

    // ------------------------------------------------------------------
    // Fields.
    // ------------------------------------------------------------------

    public static int year(DateValue d) {
        return val(d).getYear();
    }

    public static int month(DateValue d) {
        return val(d).getMonthValue();
    }

    public static int day(DateValue d) {
        return val(d).getDayOfMonth();
    }

    /** WEEKDAY: 1 for Monday to 7 for Sunday (ISO 8601). */
    public static int weekday(DateValue d) {
        return val(d).getDayOfWeek().getValue();
    }

    public static int yearday(DateValue d) {
        return val(d).getDayOfYear();
    }

    // ------------------------------------------------------------------
    // Arithmetic and comparison.
    // ------------------------------------------------------------------

    /** d + n, d - n: N days later. */
    public static DateValue plusDays(DateValue d, long n) {
        LocalDate x = val(d);
        try {
            return make(x.plusDays(n));
        } catch (DateTimeException | ArithmeticException e) {
            throw error("date out of range");
        }
    }

    /** d + p, d - p (with P negated by the module). */
    public static DateValue plusPeriod(DateValue d, PeriodValue p) {
        LocalDate x = val(d);
        java.time.Period y = PeriodValue.val(p);
        try {
            return make(x.plus(y));
        } catch (DateTimeException | ArithmeticException e) {
            throw error("date out of range");
        }
    }

    /** a - b: the days from B to A. */
    public static int daysBetween(DateValue a, DateValue b) {
        return Times.toInt(ChronoUnit.DAYS.between(val(b), val(a)), "days");
    }

    /** months_between (a, b): the whole months from A to B. */
    public static int monthsBetween(DateValue a, DateValue b) {
        return Times.toInt(ChronoUnit.MONTHS.between(val(a), val(b)), "months");
    }

    /** years_between (a, b): the whole years from A to B. */
    public static int yearsBetween(DateValue a, DateValue b) {
        return Times.toInt(ChronoUnit.YEARS.between(val(a), val(b)), "years");
    }

    /** Negative, zero or positive as A is before, equal to or after B. */
    public static int compare(DateValue a, DateValue b) {
        return Integer.signum(val(a).compareTo(val(b)));
    }

    /** at_time (d, h, mi, s). */
    public static DatetimeValue atTime(DateValue d, int hour, int minute, int second) {
        LocalDate x = val(d);
        return DatetimeValue.make(x.atTime(Times.time(hour, minute, second)));
    }
}
