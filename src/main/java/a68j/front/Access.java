// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/** Static access property of a node or tag (enum a68_access). See a68-parser-sprops.cc. */
public enum Access {
    NO_ACCESS, ACCESS_NIL, ACCESS_DIR, ACCESS_IND, ACCESS_VAR
}
