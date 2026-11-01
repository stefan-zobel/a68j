// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * A node in the syntax tree (NODE_T), produced by the scanner as a linear list
 * of tokens and later expanded by the Mailloux parser.
 *
 * <pre>
 *   TOP &lt;-&gt; N &lt;-&gt; N &lt;-&gt; ...      &lt; is previous, &gt; is next
 *           |                     | is sub
 *           N &lt;-&gt; N &lt;-&gt; ...
 * </pre>
 *
 * <p>GCC-specific members (cdecl: a GENERIC tree) are not ported; a JVM
 * backend will attach its own data.
 */
public final class Node {
    public GInfo genie;
    public int number;
    public Attribute attribute = Attribute.STOP;
    public Attribute annotation = Attribute.STOP;
    /** Mode of the entity denoted by the node (MOID). */
    public Moid type;
    public NodeInfo info;
    public Node next, previous, sub;
    public Node sequence, nest;
    public Pack pack;
    public int status;
    public Table symbolTable;
    public Table nonLocal;
    public Tag tag;
    public boolean dynamicStackAllocs;
    public boolean publicized;
    public boolean negated;
    public Origin origin;
    public Access access = Access.ACCESS_DIR;

    /** a68_new_node. */
    public Node() {}

    /** Struct assignment {@code *this = *p}: shallow copy of all fields. */
    public void copyFrom(Node p) {
        genie = p.genie;
        number = p.number;
        attribute = p.attribute;
        annotation = p.annotation;
        type = p.type;
        info = p.info;
        next = p.next;
        previous = p.previous;
        sub = p.sub;
        sequence = p.sequence;
        nest = p.nest;
        pack = p.pack;
        status = p.status;
        symbolTable = p.symbolTable;
        nonLocal = p.nonLocal;
        tag = p.tag;
        dynamicStackAllocs = p.dynamicStackAllocs;
        publicized = p.publicized;
        negated = p.negated;
        origin = p.origin;
        access = p.access;
    }

    /** IS (p, s). */
    public boolean is(Attribute a) {
        return attribute == a;
    }

    /** a68_is_one_of: true if the node is non-null and has one of the attributes. */
    public static boolean isOneOf(Node p, Attribute... as) {
        if (p == null) {
            return false;
        }
        for (Attribute a : as) {
            if (p.attribute == a) {
                return true;
            }
        }
        return false;
    }

    /** NSYMBOL (p). */
    public String symbol() {
        return info == null ? null : info.symbol;
    }

    @Override
    public String toString() {
        return attribute + (symbol() != null ? " \"" + symbol() + "\"" : "");
    }
}
