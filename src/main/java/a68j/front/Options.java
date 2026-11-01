// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import java.util.EnumSet;

/**
 * Front-end options (OPTIONS_T). Defaults follow a68_init_options_struct in
 * a68-lang.cc: SUPPER stropping, GNU extensions allowed, assertions and bounds
 * checking on, nil checking off.
 */
public final class Options {
    public Stropping stropping = Stropping.SUPPER;
    /** Whether [ .. ] and { .. } are equivalent to ( .. ). Only valid with UPPER stropping and -fstrict. */
    public boolean brackets = false;
    /** No Algol 68 extension allowed. */
    public boolean strict = false;
    public boolean assertions = true;
    public boolean boundsChecking = true;
    public boolean nilChecking = false;
    /** Enabled optional warnings (-W...); all off by default, as in a68_init_options_struct. */
    public final EnumSet<Warn> warnings = EnumSet.noneOf(Warn.class);
    /** Level for -Whidden-declarations: 0 = none, 1 = prelude, 2 = all (warn_algol68_hidden_declarations). */
    public int hiddenDeclarations = 0;
    /** Whether we are building libga68 itself (flag_building_libga68). */
    public boolean buildingLibga68 = false;
    /**
     * -fpar-threads (a68j extension): the units of a parallel clause run in
     * threads, and the SEMA operators LEVEL, UP and DOWN are available. Off
     * by default: then PAR is elaborated sequentially and SEMA is rejected,
     * as by ga68.
     */
    public boolean parThreads = false;
    /**
     * -fnest-java (a68j extension): formal holes may name a static Java
     * method or field, nest Java "java.lang.Math.cbrt" (ga68 knows only the
     * language C). See docs/java-modules.md.
     */
    public boolean nestJava = false;
}
