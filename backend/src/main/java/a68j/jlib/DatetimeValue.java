// a68j Java library: dates with a time of day for the module Times.
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * The Java side of the mode Datetime of the Algol 68 module Times
 * (backend/lib/times.a68): a date with a time of day to the nanosecond,
 * without a time zone, a java.time.LocalDateTime. Immutable; only static
 * methods, as they are called through formal holes (nest Java).
 */
public final class DatetimeValue extends JavaObject {
    final LocalDateTime v;

    private DatetimeValue(LocalDateTime v) {
        this.v = v;
    }

    @Override
    protected String javaClassName() {
        return "Datetime";
    }

    static DatetimeValue make(LocalDateTime v) {
        return new DatetimeValue(v);
    }

    /** The value of T; NIL is a runtime error. */
    static LocalDateTime val(DatetimeValue t) {
        if (t == null) {
            throw error("attempt to use a NIL Datetime");
        }
        return t.v;
    }

    // ------------------------------------------------------------------
    // Construction, the clock and time zones.
    // ------------------------------------------------------------------

    /** datetime (y, m, d, h, mi, s). */
    public static DatetimeValue of(int year, int month, int day, int hour, int minute, int second) {
        return make(Times.date(year, month, day).atTime(Times.time(hour, minute, second)));
    }

    /** now: the system clock in the zone of the system. */
    public static DatetimeValue now() {
        return make(LocalDateTime.now());
    }

    /** now_in (zone). */
    public static DatetimeValue nowIn(String zone) {
        return make(LocalDateTime.now(Times.zone(zone)));
    }

    /** datetime_from_epoch (s, zone): S seconds after 1970-01-01T00:00:00 UTC, in ZONE. */
    public static DatetimeValue fromEpoch(long seconds, String zone) {
        ZoneId z = Times.zone(zone);
        try {
            return make(LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), z));
        } catch (DateTimeException | ArithmeticException e) {
            throw error("date out of range");
        }
    }

    /** datetime_to_epoch (t, zone): the seconds since 1970-01-01T00:00:00 UTC of T in ZONE. */
    public static long toEpoch(DatetimeValue t, String zone) {
        LocalDateTime x = val(t);
        return x.atZone(Times.zone(zone)).toEpochSecond();
    }

    /** datetime_convert (t, from, to): the time in TO at the instant that is T in FROM. */
    public static DatetimeValue convert(DatetimeValue t, String from, String to) {
        LocalDateTime x = val(t);
        ZoneId f = Times.zone(from);
        ZoneId g = Times.zone(to);
        try {
            return make(x.atZone(f).withZoneSameInstant(g).toLocalDateTime());
        } catch (DateTimeException e) {
            throw error("date out of range");
        }
    }

    /** TODATE t. */
    public static DateValue toDate(DatetimeValue t) {
        return DateValue.make(val(t).toLocalDate());
    }

    // ------------------------------------------------------------------
    // Text.
    // ------------------------------------------------------------------

    /** datetime_text: ISO 8601 with the seconds, and the fraction if it is not 0. */
    public static String text(DatetimeValue t) {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(val(t));
    }

    /** datetime_from_text: ISO 8601; the seconds and the fraction may be left out. */
    public static DatetimeValue fromText(String s) {
        try {
            return make(LocalDateTime.parse(s));
        } catch (DateTimeParseException e) {
            throw error("cannot read " + Times.quote(s) + " as a date and time");
        }
    }

    /** datetime_format (t, pattern). */
    public static String format(DatetimeValue t, String pattern) {
        LocalDateTime x = val(t);
        DateTimeFormatter f = Times.formatter(pattern);
        try {
            return f.format(x);
        } catch (DateTimeException e) {
            throw error("cannot format a date and time with the pattern " + Times.quote(pattern));
        }
    }

    /** datetime_from_format (s, pattern). */
    public static DatetimeValue fromFormat(String s, String pattern) {
        DateTimeFormatter f = Times.formatter(pattern);
        try {
            return make(LocalDateTime.parse(s, f));
        } catch (DateTimeException e) {
            throw error("cannot read " + Times.quote(s) + " with the pattern " + Times.quote(pattern));
        }
    }

    // ------------------------------------------------------------------
    // Fields.
    // ------------------------------------------------------------------

    public static int year(DatetimeValue t) {
        return val(t).getYear();
    }

    public static int month(DatetimeValue t) {
        return val(t).getMonthValue();
    }

    public static int day(DatetimeValue t) {
        return val(t).getDayOfMonth();
    }

    /** WEEKDAY: 1 for Monday to 7 for Sunday (ISO 8601). */
    public static int weekday(DatetimeValue t) {
        return val(t).getDayOfWeek().getValue();
    }

    public static int yearday(DatetimeValue t) {
        return val(t).getDayOfYear();
    }

    public static int hour(DatetimeValue t) {
        return val(t).getHour();
    }

    public static int minute(DatetimeValue t) {
        return val(t).getMinute();
    }

    public static int second(DatetimeValue t) {
        return val(t).getSecond();
    }

    public static int nano(DatetimeValue t) {
        return val(t).getNano();
    }

    // ------------------------------------------------------------------
    // Arithmetic and comparison.
    // ------------------------------------------------------------------

    /** t + d, t - d (with D negated by the module). */
    public static DatetimeValue plusDuration(DatetimeValue t, DurationValue d) {
        LocalDateTime x = val(t);
        Duration y = DurationValue.val(d);
        try {
            return make(x.plus(y));
        } catch (DateTimeException | ArithmeticException e) {
            throw error("date out of range");
        }
    }

    /** t + p, t - p (with P negated by the module). */
    public static DatetimeValue plusPeriod(DatetimeValue t, PeriodValue p) {
        LocalDateTime x = val(t);
        java.time.Period y = PeriodValue.val(p);
        try {
            return make(x.plus(y));
        } catch (DateTimeException | ArithmeticException e) {
            throw error("date out of range");
        }
    }

    /** a - b: the duration from B to A. */
    public static DurationValue between(DatetimeValue a, DatetimeValue b) {
        LocalDateTime x = val(a);
        LocalDateTime y = val(b);
        try {
            return DurationValue.make(Duration.between(y, x));
        } catch (DateTimeException | ArithmeticException e) {
            throw error("duration out of range");
        }
    }

    /** Negative, zero or positive as A is before, equal to or after B. */
    public static int compare(DatetimeValue a, DatetimeValue b) {
        return Integer.signum(val(a).compareTo(val(b)));
    }
}
