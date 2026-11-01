// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import java.util.ArrayList;
import java.util.List;

/** A module interface (MOIF_T). */
public final class Moif {
    public int version;
    /** Name of the module as accessed at the source level (a bold word). */
    public String name;
    public String prelude;
    public String postlude;
    public final List<Moid> modes = new ArrayList<>();
    public final List<Extract> modules = new ArrayList<>();
    public final List<Extract> indicants = new ArrayList<>();
    public final List<Extract> identifiers = new ArrayList<>();
    public final List<Extract> prios = new ArrayList<>();
    public final List<Extract> operators = new ArrayList<>();
    public Moif next;
}
