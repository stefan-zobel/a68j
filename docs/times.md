# The bundled module `Times`

The prelude of Algol 68 has no clock time, no calendar dates and no date
arithmetic. Algol 68 Genie has three procedures for the clock:
`local time`, `utc time` and `cpu time`. The module `Times` provides
them under the same names, and dates, date-times, durations and periods
with operators for their arithmetic, on top of `java.time`. It comes with
`a68j-backend.jar` and needs no option; as it needs Java, it runs with
a68j only.

```
access Times (
    Date d = date (2026, 1, 31);
    puts (date_text (d + MONTHS 1) + ", " + date_format (d, "EEEE, d MMMM uuuu") + "'n");
    puts (whole (date (2026, 12, 25) - date (2026, 11, 18), 0) + " days'n");
    Datetime start = datetime (2026, 3, 28, 22, 0, 0);
    Datetime arrival = start + HOURS 5 + MINUTES 30;
    puts (datetime_text (arrival) + ", weekday " + whole (WEEKDAY arrival, 0) + "'n");
    long int s = datetime_to_epoch (arrival, "Europe/Berlin");
    puts (datetime_text (datetime_from_epoch (s, "America/New_York")) + "'n");
    puts (duration_text (arrival - start) + "'n")
)
```

```sh
java -jar a68j-backend.jar -run prog.a68
2026-02-28, Saturday, 31 January 2026
37 days
2026-03-29T03:30:00, weekday 7
2026-03-28T21:30:00
PT5H30M
```

A `Datetime` has no time zone: 22:00 and five and a half hours is 03:30,
although in Berlin only four and a half hours pass that night, when the
clocks go forward. The zone comes in where a date-time meets the real
instant: the clock, and seconds since 1970.

## What it provides

### The procedures of Algol 68 Genie

| Name | Meaning |
|---|---|
| `local_time [] int` | year, month, day, hours, minutes, seconds, day of the week (1 for Sunday to 7 for Saturday) and 1 if daylight saving time is in effect, else 0, in the zone of the system; the lower bound is 1 |
| `utc_time [] int` | the same in UTC; the last element is always 0 |
| `cpu_time real` | the CPU time of the process in seconds |

### The modes

Four modes, each a Java object (java-modules.md, section 2), as `Bigint`
of `Bigints`: `mode Date = ref Date_object`. Their values never change:
every operator yields a new object, and an assigning operator such as
`+:=` puts the new object into the variable. `=` compares the values,
`is` the objects.

| Mode | Java | Meaning |
|---|---|---|
| `Date` | `LocalDate` | a date of the proleptic Gregorian calendar |
| `Datetime` | `LocalDateTime` | a date and a time of day to the nanosecond, without a time zone |
| `Duration` | `Duration` | an amount of time in seconds and nanoseconds, positive or negative |
| `Period` | `Period` | years, months and days of the calendar: a month is 28 to 31 days, a day 23 to 25 hours where the clocks change |

### Construction and the clock

| Name | Meaning |
|---|---|
| `date (int y, m, d) Date` | the date; an invalid one, such as `date (2026, 2, 30)`, is a runtime error |
| `datetime (int y, m, d, h, mi, s) Datetime` | the date and time; hours 0 to 23 |
| `at_time (Date d, int h, mi, s) Datetime` | `d` at that time |
| `today Date`, `now Datetime` | the system clock in the zone of the system |
| `now_in (string zone) Datetime` | the system clock in `zone` |
| `local_zone string` | the zone of the system, such as `Europe/Berlin` |
| `datetime_from_epoch (long int s, string zone) Datetime` | the date and time in `zone` at `s` seconds after 1970-01-01T00:00:00 UTC |
| `datetime_to_epoch (Datetime t, string zone) long int` | the seconds since 1970-01-01T00:00:00 UTC at the instant that is `t` in `zone` |
| `datetime_convert (Datetime t, string from, to) Datetime` | the date and time in `to` at the instant that is `t` in `from` |

### Operators

For a `Date` d, a `Datetime` t, a `Duration` u, a `Period` p and an `int` n:

| | |
|---|---|
| `YEAR`, `MONTH`, `DAY`, `WEEKDAY`, `YEARDAY` | of d or t; `WEEKDAY` is 1 for Monday to 7 for Sunday (ISO 8601) |
| `HOUR`, `MINUTE`, `SECOND`, `NANO` | of t |
| `TODATE t` | the date of t |
| `HOURS n`, `MINUTES n`, `SECONDS n` | a Duration; n may also be a `long int` |
| `DAYS n`, `WEEKS n`, `MONTHS n`, `YEARS n` | a Period |
| `d + n`, `n + d`, `d - n` | n days later or earlier |
| `d - d` | the days from the right to the left date, an `int` |
| `d + p`, `d - p`, `t + p`, `t - p` | the calendar date p later or earlier; a day that the month does not have becomes its last day (`date (2026, 1, 31) + MONTHS 1` is 2026-02-28) |
| `t + u`, `t - u` | u later or earlier |
| `t - t` | the Duration from the right to the left date-time |
| `u + u`, `u - u`, `u * n`, `n * u`, `u % n`, `u OVER n`, `- u`, `ABS u` | durations; `%` and `OVER` truncate toward zero, to the nanosecond |
| `p + p`, `p - p`, `- p` | periods, field by field: `YEARS 1 + MONTHS 2 - DAYS 3` is P1Y2M-3D |
| `=`, `/=`, `<`, `<=`, `>`, `>=`, `EQ`, `NE`, `LT`, `LE`, `GT`, `GE` | for two Dates, two Datetimes, two Durations |
| `p = p`, `p /= p`, `EQ`, `NE` | periods with the same fields: `MONTHS 12 = YEARS 1` is false, as Periods have no order |
| `+:=`, `-:=`, `PLUSAB`, `MINUSAB` | for a variable of mode `ref Date` with an `int` or a Period, `ref Datetime` with a Duration or a Period, `ref Duration` with a Duration; they yield the variable |

The operators have the priorities of the prelude, so
`start + HOURS 5 + MINUTES 30` adds both. The module declares no
priority, so a program can access it together with `Lists`, `Hashmaps`,
`Bigints`, `Texts`, `Files` and `Genie_Transput`.

### Text

| Name | Meaning |
|---|---|
| `date_text (Date) string` | ISO 8601: `2026-11-18` |
| `datetime_text (Datetime) string` | ISO 8601: `2026-11-18T14:05:09`, always with the seconds, with a fraction only if it is not 0 (`T07:30:00.25`) |
| `duration_text (Duration) string` | ISO 8601: `PT1H30M`, `PT-2S`, `PT8.571428571S`; days are written as hours (`PT8760H`) |
| `period_text (Period) string` | ISO 8601: `P1Y2M-3D` |
| `date_from_text (string) Date`, `datetime_from_text (string) Datetime` | ISO 8601 back; in a date-time the seconds and the fraction may be left out |
| `date_format (Date, string pattern) string`, `datetime_format (Datetime, string pattern) string` | the date in the form of `pattern` |
| `date_from_format (string s, pattern) Date`, `datetime_from_format (string s, pattern) Datetime` | `s` read in the form of `pattern` |

The patterns are those of `java.time.format.DateTimeFormatter`:
`yyyy` or `uuuu` the year, `MM` the month, `MMM` or `MMMM` its name,
`dd` the day, `EEE` or `EEEE` the name of the day of the week, `HH` the
hour from 0 to 23, `hh` from 1 to 12 with `a` for AM and PM, `mm`
minutes, `ss` seconds; text in apostrophes is copied, and in an Algol 68
string an apostrophe is written twice: `"dd.MM.yyyy ''at'' HH:mm"`. The
names are English. Reading is strict: `30.02.2026` with `dd.MM.yyyy` is
an error rather than the last day of February.

### Further procedures

| Name | Meaning |
|---|---|
| `months_between (Date a, b) int`, `years_between (Date a, b) int` | the whole months or years from `a` to `b`, negative if `b` is before `a` |
| `duration_seconds (Duration) long real` | the seconds with their fraction |
| `duration_millis (Duration) long int` | the milliseconds, truncated toward zero |

- In UPPER stropping the names have blanks (`date text`, `local time`)
  and the modes and operators are bold capitals (`DATE`, `WEEKDAY`); in
  SUPPER stropping the blanks are underscores.

## Time zones

A zone is a string: the name of a region of the IANA time zone database
(`Europe/Berlin`, `America/New_York`), `UTC` or `Z`, or a fixed offset
from UTC (`+05:30`, `-08:00`). Where a date-time does not exist in a
zone, because the clocks go forward, `datetime_to_epoch` moves it forward
by the length of the gap (02:30 on the day of the change in Berlin is
read as 03:30); where it exists twice, because the clocks go back, it
takes the earlier of the two instants. These are the rules of
`ZonedDateTime.of`.

`file_time` of the module `Files` gives seconds since 1970, so the time
of the last change of a file is

```
access Files, Times (
    Datetime t = datetime_from_epoch (file_time ("notes.txt"), local_zone);
    puts (datetime_format (t, "yyyy-MM-dd HH:mm") + "'n")
)
```

## Errors

A failure is a runtime error, status 134, at the position of the call in
the program:

```
prog.a68:5: runtime error: invalid date 2026-02-30
prog.a68:7: runtime error: invalid time 25:00:00
prog.a68:9: runtime error: cannot read "2026-13-01" as a date
prog.a68:11: runtime error: cannot read "30.02.2026" with the pattern "dd.MM.yyyy"
prog.a68:13: runtime error: invalid pattern "yyyy-bb": Unknown pattern letter: b
prog.a68:15: runtime error: unknown time zone "Mars/Base"
prog.a68:17: runtime error: attempt to use a NIL Date
```

`cannot format a date with the pattern "HH:mm"` is the error of a pattern
that asks a Date for a time of day. A value beyond the limits of
`java.time` (years beyond ±999 999 999, a Duration beyond 292 billion
years) is `date out of range`, `duration out of range` or
`period out of range`; `d - d`, `months_between` and `years_between`
beyond the limits of `int` are `too many days for an int` and so on.
A Duration divided by 0 is `division by zero`.

## Differences from Algol 68 Genie

- **`cpu_time`** is the CPU time of all threads of the Java virtual
  machine, including its just-in-time compiler and garbage collector, and
  with `-run` also that of compiling the program, so it starts above 0
  (about 2 seconds with `-run`). Differences, as in the Rosetta Code
  solutions, are what it is meant for. Where the operating system does
  not report it, it is the time since the program started.
- **`real`** has single precision in a68j (as in ga68), so `cpu_time`
  is exact to a millisecond only for its first two hours.

## `-fgenie`

With `-fgenie` the program is put into an access clause of
`Genie_Transput`, `Genie_Formats`, `Regex`, `Files` and `Times`
([genie-formats.md](genie-formats.md)), so a program written for
Algol 68 Genie can call `local time`, `utc time` and `cpu time` as it
is. A declaration of the program with the same name hides the one of the
module, whatever its kind: the Rosetta Code solution
`convert-seconds-to-compound-duration` declares its own `MODE DURATION`,
others `MODE YEAR` or `OP PERIOD`. The solutions `date-format`,
`system-time` and `palindrome-dates` run unchanged.

## Inside

`backend/lib/times.a68` declares each procedure as a formal hole of
`a68j.jlib`: the classes `DateValue`, `DatetimeValue`, `DurationValue`
and `PeriodValue`, one for each mode, hold the `java.time` value, and
`Times` has the procedures of Algol 68 Genie, the zones and the patterns.
The procedures are public formal holes that a program calls directly,
through the typed entry of the module ([java-modules.md](java-modules.md),
section 5), so it needs no `-fnest-java`; the operators call private
holes. The formatters of the patterns are kept in a small cache (the 64
used last).

## Tests

- `tests/java/times-basic.a68`: construction, fields, days, periods and
  month ends, durations, comparisons, assigning operators, ISO text and
  patterns both ways, seconds since 1970 in fixed zones across the
  changes of daylight saving time.
- `tests/java/times-now.a68`: the clock, checked for its ranges and the
  agreement of `today`, `now`, `local_time` and `utc_time`.
- `tests/java/times-error-1.a68` … `-5`: an invalid date, a text that does
  not fit its pattern, an invalid pattern, an unknown zone, `nil`.
- `tests/options/genie-times.a68`: `-fgenie` with `local time`,
  `utc time`, `cpu time`, and declarations that hide a mode, an operator
  and a procedure of `Times`.
