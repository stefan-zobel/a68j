// Java port of ga68's a68-parser-serial-dsa.cc: check dynamic stack usage in serial clauses.
// Original: Copyright (C) 2025 Jose E. Marchesi.
// Written by Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * This file implements a phase that determines what serial clauses contain
 * phrases whose elaboration may involve dynamic stack allocation. It
 * annotates the SERIAL_CLAUSE parse nodes by setting the DYNAMIC_STACK_ALLOCS
 * flag ({@link Node#dynamicStackAllocs}).
 *
 * <p>Porting notes: the {@code bool *dsa} out-parameter is a
 * {@code boolean[1]}. The debugging traces (compiled only with
 * SERIAL_DSA_DEBUG defined, which it is not) are omitted.
 */
public final class SerialDsa {
    private SerialDsa() {}

    // Uncomment the following line for debugging traces.
    // #define SERIAL_DSA_DEBUG
    // (Not ported: the SERIAL_DSA_DEBUG traces are omitted.)

    /** serial_dsa_check_serial_clause. */
    private static void serialDsaCheckSerialClause(A68 a68, Node p, boolean[] dsa) {
        for (; p != null; p = p.next) {
            if (p.is(GENERATOR)) {
                // LOC generators always result in dynamic stack allocation regardless
                // of the mode of the allocated value.
                if (p.sub.is(LOC_SYMBOL)) {
                    // SERIAL_DSA_DEBUG trace omitted ("serial_dsa: %s:%d: loc generator implies DSA\n").
                    dsa[0] = true;
                    return;
                }
            } else if (p.is(DEFINING_IDENTIFIER)) {
                // Variable declarations of values with sample loc generators will
                // result in dynamic stack allocation.
                //
                // Note that label declarations do no have a mode, so we have to
                // check for MOID (p).
                if (p.type != null && p.type.attribute == REF_SYMBOL) {
                    boolean heap = p.tag.heap == HEAP_SYMBOL;
                    if (p.type.sub.hasRows && !heap) {
                        // SERIAL_DSA_DEBUG trace omitted
                        // ("serial_dsa: %s:%d: defining identifier %s implies DSA\n").
                        dsa[0] = true;
                        return;
                    }
                }
            } else {
                // Inner serial clauses will take care of their own. Code in routine
                // texts will not impact the stack of the containing serial
                // clause.
                if (!p.is(SERIAL_CLAUSE) && !p.is(ROUTINE_TEXT)) {
                    serialDsaCheckSerialClause(a68, p.sub, dsa);
                }
            }
        }
    }

    /** a68_serial_dsa. */
    public static void serialDsa(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            serialDsa(a68, p.sub);
            if (p.is(SERIAL_CLAUSE)) {
                boolean[] dsa = {false};
                serialDsaCheckSerialClause(a68, p.sub, dsa);
                p.dynamicStackAllocs = dsa[0];
                // SERIAL_DSA_DEBUG trace omitted
                // ("serial_dsa: %s:%d: marking serial clause %p as DSA\n").
            }
        }
    }
}
