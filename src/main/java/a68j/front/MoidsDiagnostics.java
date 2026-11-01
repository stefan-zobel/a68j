// Java port of ga68's a68-moids-diagnostics.cc: MOID diagnostics routines.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * MOID diagnostics routines (a68-moids-diagnostics.cc).
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>The function-static {@code BUFFER txt} of a68_mode_error_text is
 * per-compilation state ({@link State}). Its C semantics (writes through
 * {@code snprintf} with a size limit, writes at the start of the buffer that
 * discard what was there) are reproduced by {@link #snprintf}.</li>
 * <li>The {@code snprintf} format strings are kept verbatim and rendered with
 * {@link String#format}, which treats {@code %%} and {@code %s} like C.
 * The resulting text contains {@code %<} and {@code %>} and is used as
 * (part of) a diagnostic format string, as in C.</li>
 * <li>Sorts ({@code int context}, {@code int c}) and attributes
 * ({@code int att}, {@code int u}) are {@link Attribute}s; deflexing stays
 * {@code int} ({@link MoidsMisc#NO_DEFLEXING} ...).</li>
 * </ul>
 */
public final class MoidsDiagnostics {
    private MoidsDiagnostics() {}

    /** BUFFER_SIZE (a68.h). */
    private static final int BUFFER_SIZE = 1024;
    /** SNPRINTF_SIZE (a68.h): ((size_t) (BUFFER_SIZE - 1)). */
    private static final int SNPRINTF_SIZE = BUFFER_SIZE - 1;

    /** Per-compilation state: the static buffer of a68_mode_error_text. */
    static final class State {
        /** static BUFFER txt; */
        final StringBuilder txt = new StringBuilder();
    }

    private static State state(A68 a68) {
        return a68.state(State.class, State::new);
    }

    /**
     * snprintf (TXT + AT, SIZE, FMT, ARGS...): write the formatted text at
     * offset AT of the NUL-terminated buffer TXT, writing at most SIZE - 1
     * characters followed by the terminating NUL, which ends the string.
     * {@code TAIL (txt)} is {@code at = txt.length ()}, plain {@code txt} is
     * {@code at = 0}. Never fails, so the C {@code < 0} checks
     * ({@code gcc_unreachable}) are omitted.
     */
    private static void snprintf(StringBuilder txt, int at, int size, String fmt, Object... args) {
        String s = String.format(fmt, args);
        if (size <= 0) {
            return;
        }
        if (s.length() > size - 1) {
            s = s.substring(0, size - 1);
        }
        txt.setLength(at);
        txt.append(s);
    }

    /** Give accurate error message (a68_mode_error_text). */
    public static String modeErrorText(A68 a68, Node n, Moid p, Moid q, Attribute context, int deflex, int depth) {
        // #define TAIL(z) (&(z)[strlen (z)]): offset txt.length ().
        // #define ACTUAL_SNPRINTF_SIZE ((SNPRINTF_SIZE - len))
        StringBuilder txt = state(a68).txt;
        int len;
        if (depth == 1) {
            txt.setLength(0);
        }
        if (p.attribute == SERIES_MODE) {
            len = txt.length();
            Pack u = p.pack;

            int N = 0;
            if (u == null) {
                // Sic: writes at the start of the buffer, discarding its contents.
                snprintf(txt, 0, SNPRINTF_SIZE - len, "empty mode-list");
                N++;
            } else {
                for (; u != null; u = u.next) {
                    if (u.type != null) {
                        if (u.type.attribute == SERIES_MODE) {
                            modeErrorText(a68, n, u.type, q, context, deflex, depth + 1);
                        } else if (!MoidsMisc.isCoercible(a68, u.type, q, context, deflex)) {
                            len = txt.length();
                            if (len > BUFFER_SIZE / 2) {
                                snprintf(txt, txt.length(), SNPRINTF_SIZE - len, " etcetera");
                                N++;
                            } else {
                                if (len > 0) {
                                    snprintf(txt, txt.length(), SNPRINTF_SIZE - len, " and ");
                                    N++;
                                    len = txt.length();
                                }
                                snprintf(txt, txt.length(), SNPRINTF_SIZE - len, "%%<%s%%>",
                                        MoidsToString.moidToString(a68, u.type, Parser.MOID_ERROR_WIDTH, n));
                                N++;
                            }
                        }
                    }
                }
            }
            if (depth == 1) {
                len = txt.length();
                if (N == 0) {
                    snprintf(txt, txt.length(), SNPRINTF_SIZE - len, "mode");
                    len = txt.length();
                }
                snprintf(txt, txt.length(), SNPRINTF_SIZE - len, " cannot be coerced to %%<%s%%>",
                        MoidsToString.moidToString(a68, q, Parser.MOID_ERROR_WIDTH, n));
            }
        } else if (p.attribute == STOWED_MODE && q.attribute == FLEX_SYMBOL) {
            Pack u = p.pack;
            len = txt.length();
            if (u == null) {
                // Sic: writes at the start of the buffer, discarding its contents.
                snprintf(txt, 0, SNPRINTF_SIZE - len, "empty mode-list");
            } else {
                for (; u != null; u = u.next) {
                    if (!MoidsMisc.isCoercible(a68, u.type, q.sub.slice, context, deflex)) {
                        len = txt.length();
                        if (len > BUFFER_SIZE / 2) {
                            snprintf(txt, txt.length(), SNPRINTF_SIZE - len, " etcetera");
                        } else {
                            if (len > 0) {
                                snprintf(txt, txt.length(), SNPRINTF_SIZE - len, " and ");
                                len = txt.length();
                            }
                            // Sic: unlike the other branches, the mode is not quoted.
                            snprintf(txt, txt.length(), SNPRINTF_SIZE - len, "%s",
                                    MoidsToString.moidToString(a68, u.type, Parser.MOID_ERROR_WIDTH, n));
                        }
                    }
                }
                len = txt.length();
                snprintf(txt, txt.length(), SNPRINTF_SIZE - len, " cannot be coerced to %%<%s%%>",
                        MoidsToString.moidToString(a68, q.sub.slice, Parser.MOID_ERROR_WIDTH, n));
            }
        } else if (p.attribute == STOWED_MODE && q.attribute == ROW_SYMBOL) {
            Pack u = p.pack;
            len = txt.length();
            if (u == null) {
                // Sic: writes at the start of the buffer, discarding its contents.
                snprintf(txt, 0, SNPRINTF_SIZE - len, "empty mode-list");
            } else {
                for (; u != null; u = u.next) {
                    if (!MoidsMisc.isCoercible(a68, u.type, q.slice, context, deflex)) {
                        len = txt.length();
                        if (len > BUFFER_SIZE / 2) {
                            snprintf(txt, txt.length(), SNPRINTF_SIZE - len, " etcetera");
                        } else {
                            if (len > 0) {
                                snprintf(txt, txt.length(), SNPRINTF_SIZE - len, " and ");
                                len = txt.length();
                            }
                            snprintf(txt, txt.length(), SNPRINTF_SIZE - len, "%%<%s%%>",
                                    MoidsToString.moidToString(a68, u.type, Parser.MOID_ERROR_WIDTH, n));
                        }
                    }
                }
                len = txt.length();
                // Sic: unlike the other branches, the target mode is not quoted.
                snprintf(txt, txt.length(), SNPRINTF_SIZE - len, " cannot be coerced to %s",
                        MoidsToString.moidToString(a68, q.slice, Parser.MOID_ERROR_WIDTH, n));
            }
        } else if (p.attribute == STOWED_MODE && (q.attribute == PROC_SYMBOL || q.attribute == STRUCT_SYMBOL)) {
            Pack u = p.pack, v = q.pack;
            len = txt.length();
            if (u == null) {
                // Sic: writes at the start of the buffer, discarding its contents.
                snprintf(txt, 0, SNPRINTF_SIZE - len, "empty mode-list");
            } else {
                for (; u != null && v != null; u = u.next, v = v.next) {
                    if (!MoidsMisc.isCoercible(a68, u.type, v.type, context, deflex)) {
                        len = txt.length();
                        if (len > BUFFER_SIZE / 2) {
                            snprintf(txt, txt.length(), SNPRINTF_SIZE - len, " etcetera");
                        } else {
                            if (len > 0) {
                                snprintf(txt, txt.length(), SNPRINTF_SIZE - len, " and ");
                                len = txt.length();
                            }
                            snprintf(txt, txt.length(), SNPRINTF_SIZE - len, "%%<%s%%> cannot be coerced to %%<%s%%>",
                                    MoidsToString.moidToString(a68, u.type, Parser.MOID_ERROR_WIDTH, n),
                                    MoidsToString.moidToString(a68, v.type, Parser.MOID_ERROR_WIDTH, n));
                        }
                    }
                }
            }
        }
        return txt.toString();
        // #undef TAIL
        // #undef ACTUAL_SNPRINTF_SIZE
    }

    /** Cannot coerce error (a68_cannot_coerce). */
    public static void cannotCoerce(A68 a68, Node p, Moid from, Moid to, Attribute context, int deflex, Attribute att) {
        String txt = modeErrorText(a68, p, from, to, context, deflex, 1);

        Fmt from1 = Fmt.moid(a68, from);
        Fmt to1 = Fmt.moid(a68, to);
        Fmt att1 = Fmt.attr(att);
        Fmt context1 = Fmt.sort(context);

        if (att == STOP) {
            if (txt.length() == 0) {
                a68.diagnostics.error(p, "%e cannot be coerced to %e in %e context", from1, to1, context1);
            } else {
                String fmt = txt;
                a68.diagnostics.error(p, fmt + " in %e context", context1);
            }
        } else {
            if (txt.length() == 0) {
                a68.diagnostics.error(p, "%e cannot be coerced to %e in %e-%e", from1, to1, context1, att1);
            } else {
                String fmt = txt;
                a68.diagnostics.error(p, fmt + " in %e-%e", context1, att1);
            }
        }
    }

    /** Give a warning when a value is silently discarded (a68_warn_for_voiding). */
    public static void warnForVoiding(A68 a68, Node p, Soid x, Soid y, Attribute c) {
        // (void) c;

        if (x.cast == false) {
            if (x.type == a68.modes.VOID
                    && y.type != a68.modes.ERROR
                    && !(y.type == a68.modes.VOID || !MoidsMisc.isNonproc(a68, y.type))) {
                Fmt m1 = Fmt.moid(a68, y.type);
                Fmt c1 = Fmt.construct(p);

                a68.diagnostics.warning(p, Warn.VOIDING, "value of %e %e will be voided",
                        m1, c1);
            }
        }
    }

    /** Warn for things that are likely unintended (a68_semantic_pitfall). */
    public static void semanticPitfall(A68 a68, Node p, Moid m, Attribute c, Attribute u) {
        // semantic_pitfall: warn for things that are likely unintended, for instance
        //                   REF INT i := LOC INT := 0, which should probably be
        //                   REF INT i = LOC INT := 0.
        if (p.is(u)) {
            Fmt m1 = Fmt.moid(a68, p.type);
            Fmt m2 = Fmt.moid(a68, m);
            Fmt u1 = Fmt.construct(u);
            Fmt c1 = Fmt.construct(c);

            a68.diagnostics.warning(p, null, "possibly unintended %e %e in %e %e",
                    m1, u1, m2, c1);
        } else if (Parser.isOneOf(a68, p, UNIT, TERTIARY, SECONDARY, PRIMARY)) {
            semanticPitfall(a68, p.sub, m, c, u);
        }
    }
}
