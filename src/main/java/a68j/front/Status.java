// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/** Status mask bits used in {@link Node#status} and {@link Tag#status} (STATUS_MASK_T). */
public final class Status {
    private Status() {}

    public static final int NULL_MASK = 0x00000000;
    /** Used by the static scope checker to avoid duplicated scope warnings. */
    public static final int SCOPE_ERROR_MASK = 0x00000200;
}
