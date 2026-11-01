// Java port of ga68's a68-diagnostics.cc.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Diagnostics collector.
 *
 * <p>In C, errors and warnings are reported through GCC's diagnostic machinery
 * and a scan error terminates the compiler with {@code exit (FATAL_EXIT_CODE)}.
 * Here diagnostics are collected in a list and a scan error or fatal error
 * throws {@link ScanError}.
 *
 * <p>Messages use GCC's format directives so that the strings can be copied
 * literally from the C sources: {@code %s %qs %d %i %u %c %e %< %> %%}.
 * Quotes are rendered as in the C locale ({@code '...'}). For {@code %e} pass
 * a {@link Fmt} token.
 */
public final class Diagnostics {

    public enum Severity { ERROR, WARNING, FATAL, SCAN_ERROR, INFORM }

    public record Diagnostic(Severity severity, Location location, String message) {
        @Override
        public String toString() {
            String kind = switch (severity) {
                case WARNING -> "warning";
                case INFORM -> "note";
                case FATAL -> "fatal error";
                default -> "error";
            };
            String where = location == Location.UNKNOWN ? "a68j" : location.toString();
            return where + ": " + kind + ": " + message;
        }
    }

    /** Thrown by {@link #scanError} and {@link #fatal}; the equivalent of the C exit. */
    public static final class ScanError extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public final transient Diagnostic diagnostic;

        ScanError(Diagnostic diagnostic) {
            super(diagnostic.toString());
            this.diagnostic = diagnostic;
        }
    }

    private final A68 a68;
    private final List<Diagnostic> messages = new ArrayList<>();

    Diagnostics(A68 a68) {
        this.a68 = a68;
    }

    public List<Diagnostic> messages() {
        return Collections.unmodifiableList(messages);
    }

    private Diagnostic record(Severity sev, Location loc, String msg) {
        if (sev == Severity.WARNING) {
            a68.job.warningCount++;
        } else if (sev != Severity.INFORM) {
            a68.job.errorCount++;
        }
        Diagnostic d = new Diagnostic(sev, loc, msg);
        messages.add(d);
        return d;
    }

    /** a68_scan_error: report and abort scanning. {@code v} is an index into u's text, -1 for NO_TEXT. */
    public ScanError scanError(Line u, int v, String txt, Object... args) {
        Location loc = u == null ? Location.UNKNOWN : Location.of(u, v < 0 ? 0 : v);
        throw new ScanError(record(Severity.SCAN_ERROR, loc, format(txt, args)));
    }

    /** fatal_error (UNKNOWN_LOCATION, ...): report and abort. */
    public ScanError fatal(String txt, Object... args) {
        throw new ScanError(record(Severity.FATAL, Location.UNKNOWN, format(txt, args)));
    }

    /** a68_fatal: report at node P and abort. */
    public ScanError fatal(Node p, String txt, Object... args) {
        throw new ScanError(record(Severity.FATAL, Location.of(p), format(txt, args)));
    }

    /** a68_error */
    public void error(Node p, String txt, Object... args) {
        record(Severity.ERROR, Location.of(p), format(txt, args));
    }

    /** a68_error_in_pragmat: error at offset OFF within the pragmat attached to P. */
    public void errorInPragmat(Node p, int off, String txt, Object... args) {
        Location loc = Location.of(p.info.pragmatLine, p.info.pragmatCharInLine + off);
        record(Severity.ERROR, loc, format(txt, args));
        inform(p, "pragmat applies to this construct");
    }

    /**
     * a68_warning. {@code opt} null means the warning is always enabled
     * (option 0 in C). Returns whether the warning was emitted.
     */
    public boolean warning(Node p, Warn opt, String txt, Object... args) {
        if (opt != null && !a68.options().warnings.contains(opt)) {
            a68.job.warningCount++; // diagnostic() counts before GCC filters
            return false;
        }
        // GCC does not report warnings located in system headers; the prelude
        // and postlude are entered as system headers (append_environ).
        if (p != null && p.info != null && p.info.line != null && p.info.line.maps != null
                && p.info.line.maps.inSystemHeader(p.info.line.location)) {
            a68.job.warningCount++; // diagnostic() counts before GCC filters
            return false;
        }
        // GCC appends the option that controls the warning (-fdiagnostics-show-option).
        String msg = format(txt, args) + (opt != null ? " [" + opt.option + "]" : "");
        record(Severity.WARNING, Location.of(p), msg);
        return true;
    }

    /** a68_inform */
    public void inform(Node p, String txt, Object... args) {
        record(Severity.INFORM, Location.of(p), format(txt, args));
    }

    /** Render a GCC-style format string (C locale quoting). */
    public static String format(String fmt, Object... args) {
        StringBuilder sb = new StringBuilder();
        int ai = 0;
        for (int i = 0; i < fmt.length(); i++) {
            char c = fmt.charAt(i);
            if (c != '%') {
                sb.append(c);
                continue;
            }
            i++;
            if (i >= fmt.length()) {
                throw new IllegalArgumentException("bad format: " + fmt);
            }
            char d = fmt.charAt(i);
            boolean quoted = false;
            if (d == 'q') {
                quoted = true;
                d = fmt.charAt(++i);
            }
            // Length modifiers such as %ld, %lu.
            while (d == 'l' || d == 'h' || d == 'z') {
                d = fmt.charAt(++i);
            }
            String piece;
            switch (d) {
                case '%' -> piece = "%";
                case '<', '>' -> piece = "'";
                case 's', 'e' -> piece = String.valueOf(args[ai++]);
                case 'd', 'i', 'u' -> piece = String.valueOf(args[ai++]);
                case 'c' -> piece = String.valueOf((char) toInt(args[ai++]));
                default -> throw new IllegalArgumentException("unsupported directive %" + d + " in: " + fmt);
            }
            if (quoted) {
                sb.append('\'').append(piece).append('\'');
            } else {
                sb.append(piece);
            }
        }
        return sb.toString();
    }

    private static int toInt(Object o) {
        if (o instanceof Character ch) {
            return ch;
        }
        return ((Number) o).intValue();
    }
}
