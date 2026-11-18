// a68j Java library: clock, time zones and patterns for the module Times.
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
import a68j.rt.Rt;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The procedures of the Algol 68 module Times (backend/lib/times.a68) that
 * belong to no single mode: those of Algol 68 Genie (local time, utc time,
 * cpu time, wall time, sleep), the zone of the system, and the helpers of DateValue,
 * DatetimeValue, DurationValue and PeriodValue for checked construction,
 * time zones and patterns. Only static methods.
 */
public final class Times {
    private Times() {
    }

    // ------------------------------------------------------------------
    // The procedures of Algol 68 Genie.
    // ------------------------------------------------------------------

    /**
     * local_time: year, month, day, hours, minutes, seconds, day of the week
     * (1 for Sunday to 7 for Saturday) and 1 if daylight saving time is in
     * effect, else 0, as Algol 68 Genie gives them from localtime.
     */
    public static int[] localTime() {
        return fields(ZonedDateTime.now());
    }

    /** utc_time: as local_time, in UTC (daylight saving time is never in effect). */
    public static int[] utcTime() {
        return fields(ZonedDateTime.now(ZoneOffset.UTC));
    }

    private static int[] fields(ZonedDateTime t) {
        boolean dst = t.getZone().getRules().isDaylightSavings(t.toInstant());
        return new int[] {
            t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
            t.getHour(), t.getMinute(), t.getSecond(),
            t.getDayOfWeek().getValue() % 7 + 1, dst ? 1 : 0
        };
    }

    /**
     * cpu_time (also clock and seconds): the CPU time of the program in
     * seconds, as Algol 68 Genie counts it from the start of the program.
     * That is the CPU time of the thread that runs the program, which starts
     * with the program ({@link Rt#start}), so that neither the start of the
     * JVM nor the compilation with -run counts; without that thread, or
     * where the system does not measure the time of a thread, the CPU time
     * of the process; where the system does not report that either, the
     * seconds since the program started.
     */
    public static double cpuTime() {
        Thread t = Rt.programThread();
        if (t != null) {
            ThreadMXBean mx = ManagementFactory.getThreadMXBean();
            if (mx.isThreadCpuTimeSupported()) {
                long n = mx.getThreadCpuTime(t.threadId());
                if (n >= 0) {
                    return n / 1e9;
                }
            }
        }
        Duration d = ProcessHandle.current().info().totalCpuDuration().orElse(null);
        if (d != null) {
            return d.getSeconds() + d.getNano() / 1e9;
        }
        return wallTime();
    }

    /**
     * wall_time (also wall_clock and wall_seconds): the seconds since the
     * program started.
     */
    public static double wallTime() {
        return (System.nanoTime() - Rt.startNanos()) / 1e9;
    }

    /**
     * sleep: waits ABS S seconds and gives 0, as Algol 68 Genie does when
     * the time has passed.
     */
    public static int sleep(double s) {
        double a = Math.abs(s);
        if (!(a < Long.MAX_VALUE / 1e9)) {
            throw JavaObject.error("invalid argument for sleep");
        }
        long end = System.nanoTime() + (long) (a * 1e9);
        for (long left = end - System.nanoTime(); left > 0; left = end - System.nanoTime()) {
            try {
                Thread.sleep(Duration.ofNanos(left));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return 0;
    }

    /** local_zone: the time zone of the system, such as Europe/Berlin. */
    public static String localZone() {
        return ZoneId.systemDefault().getId();
    }

    // ------------------------------------------------------------------
    // Helpers of the value classes.
    // ------------------------------------------------------------------

    /** The date Y-M-D; an invalid one is a runtime error. */
    static LocalDate date(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            throw JavaObject.error("invalid date " + String.format("%04d-%02d-%02d", year, month, day));
        }
    }

    /** The time of day H:MI:S; an invalid one is a runtime error. */
    static LocalTime time(int hour, int minute, int second) {
        try {
            return LocalTime.of(hour, minute, second);
        } catch (DateTimeException e) {
            throw JavaObject.error("invalid time " + String.format("%02d:%02d:%02d", hour, minute, second));
        }
    }

    /** The time zone named S: a region (Europe/Berlin), UTC or an offset (+01:00). */
    static ZoneId zone(String s) {
        try {
            return ZoneId.of(s);
        } catch (DateTimeException e) {
            throw JavaObject.error("unknown time zone " + quote(s));
        }
    }

    /** N as an int; beyond the limits a runtime error that names the UNIT. */
    static int toInt(long n, String unit) {
        if (n != (int) n) {
            throw JavaObject.error("too many " + unit + " for an int");
        }
        return (int) n;
    }

    static String quote(String s) {
        return "\"" + s + "\"";
    }

    /**
     * The formatters most recently used, by their pattern. They resolve
     * strictly, so that 30.02.2026 is an error rather than the last day of
     * February, with the era defaulted, so that the pattern letter y works
     * as well as u.
     */
    private static final int CACHE_SIZE = 64;
    private static final Map<String, DateTimeFormatter> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, DateTimeFormatter> e) {
            return size() > CACHE_SIZE;
        }
    };

    /** The formatter of PATTERN; an invalid pattern is a runtime error (synchronized: PAR). */
    static synchronized DateTimeFormatter formatter(String pattern) {
        DateTimeFormatter f = CACHE.get(pattern);
        if (f == null) {
            try {
                f = new DateTimeFormatterBuilder()
                        .appendPattern(pattern)
                        .parseDefaulting(ChronoField.ERA, 1)
                        .toFormatter(Locale.US)
                        .withResolverStyle(ResolverStyle.STRICT);
            } catch (IllegalArgumentException e) {
                throw JavaObject.error("invalid pattern " + quote(pattern) + ": " + e.getMessage());
            }
            CACHE.put(pattern, f);
        }
        return f;
    }
}
