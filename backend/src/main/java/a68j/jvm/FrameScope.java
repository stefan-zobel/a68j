// a68j JVM backend: the warning -Wframe-scope (docs/BACKEND.md section 3).
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import a68j.front.Attribute;
import a68j.front.Fmt;
import a68j.front.Moid;
import a68j.front.Node;
import a68j.front.Pack;
import a68j.front.Table;
import a68j.front.Tag;
import a68j.front.Warn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The warning -Wframe-scope: the escapes of procedure values that break the
 * scope rule of Algol 68 and that a68j, unlike most of those that -Wscope of
 * ga68 reports, does not make up for.
 *
 * <p>A frame holds the captured identifiers of one activation of a routine
 * (docs/BACKEND.md section 3), so a routine text that outlives its range
 * keeps the values it uses. But a range that is elaborated more than once
 * in one activation (the FOR identifier and the ranges in the WHILE and DO
 * parts of a loop, the ranges after the first label of a serial clause)
 * uses the same slots each time: a routine text made in one elaboration and
 * called after the next sees the values of the latest one. And a procedure
 * that jumps to a label (a procedured jump, or a routine text with a jump
 * out of it) cannot be called once the serial clause of the label has been
 * left: no handler catches the {@link a68j.rt.Jump} then.
 *
 * <p>A format text with dynamic parts (-fformats, GenFormats) is treated as
 * a routine text: its parts are methods that use the frame they were made
 * in.
 *
 * <p>Every value whose mode can hold a procedure gets a danger level: storing
 * it in a name whose scope (a symbol table level, as in a68j.front.Scope) is
 * at most that level is wrong. A repeater (a loop, or a serial clause with
 * labels) has a home level, the level of the range it does not elaborate
 * again; a routine text has as danger level the highest home level of the
 * repeaters of the identifiers it captures (in the frame that holds them),
 * and the level outside the serial clause of each label it jumps to. Danger
 * levels pass to identities and variables (iterated until nothing changes)
 * and through calls, unitings, displays, selections, slices and the values
 * of clauses. Assignations and the initial values of variables are checked.
 *
 * <p>Not seen: a procedure passed as an argument that the callee stores, a
 * parameter, the name of a variable of a loop that escapes and is
 * dereferenced later.
 */
final class FrameScope {

    /**
     * A repeater: LOOP is the loop clause, or LABEL the first label of a
     * serial clause. LEVEL is its home level.
     */
    private record Rep(int level, Node loop, Tag label) {}

    /**
     * Danger level LEVEL of a value, with its cause: the identifier CAPTURED
     * shared because of REP, or the label LABEL jumped to. ORIGIN is the node
     * of the value the warning is given at.
     */
    private record Danger(int level, Node origin, Tag captured, Rep rep, Tag label) {
        Danger at(Node p) {
            return new Danger(level, p, captured, rep, label);
        }
    }

    private final Gen g;
    private final Map<Table, Rep> repeat = new IdentityHashMap<>();
    private final Map<Node, Danger> texts = new IdentityHashMap<>();
    private final Map<Tag, Danger> tags = new IdentityHashMap<>();
    private final Map<Moid, Boolean> procModes = new IdentityHashMap<>();
    private final Set<Node> reported = new HashSet<>();
    private boolean changed;

    private FrameScope(Gen g) {
        this.g = g;
    }

    /** Gives the warnings for the program TOP (after Capture#analyse). */
    static void check(Gen g, Node top) {
        FrameScope f = new FrameScope(g);
        f.repeaters(top, null);
        f.captures(top, new ArrayDeque<>());
        for (int i = 0; i < 100; i++) {
            f.changed = false;
            f.flow(top, false);
            if (!f.changed) {
                break;
            }
        }
        f.flow(top, true);
    }

    private static Rep max(Rep a, Rep b) {
        return a == null || b != null && b.level > a.level ? b : a;
    }

    private static Danger max(Danger a, Danger b) {
        return a == null || b != null && b.level > a.level ? b : a;
    }

    // ------------------------------------------------------------------
    // Repeaters.
    // ------------------------------------------------------------------

    /** Records the repeater HOME (or a later one) for the ranges below P. */
    private void repeaters(Node p, Rep home) {
        for (; p != null; p = p.next) {
            Rep h = g.routineOf.containsKey(p) ? null : home; // a frame of its own
            note(p.symbolTable, h);
            if (p.attribute == Attribute.LOOP_CLAUSE && p.symbolTable != null) {
                Rep inner = max(h, new Rep(p.symbolTable.level, p, null));
                for (Node q = p.sub; q != null; q = q.next) {
                    boolean once = q.attribute == Attribute.FROM_PART || q.attribute == Attribute.BY_PART
                            || q.attribute == Attribute.TO_PART;
                    repeaters(q.sub, once ? h : inner);
                }
            } else if (p.attribute == Attribute.SERIAL_CLAUSE && p.symbolTable != null && Capture.hasLabels(p)) {
                List<Node> ph = new ArrayList<>();
                GenClauses.phrases(p, ph);
                Rep r = h;
                for (Node q : ph) {
                    if (r == h && q.attribute == Attribute.LABELED_UNIT) {
                        Node label = Gen.son(q, Attribute.LABEL);
                        Node id = label == null ? null : Gen.son(label, Attribute.DEFINING_IDENTIFIER);
                        r = max(h, new Rep(p.symbolTable.level, null, id == null ? null : id.tag));
                    }
                    note(q.symbolTable, r);
                    repeaters(q.sub, r);
                }
            } else {
                repeaters(p.sub, h);
            }
        }
    }

    /** REP elaborates the range T again, if T lies within its home. */
    private void note(Table t, Rep rep) {
        if (t != null && rep != null && rep.level < t.level) {
            repeat.merge(t, rep, FrameScope::max);
        }
    }

    // ------------------------------------------------------------------
    // Routine texts.
    // ------------------------------------------------------------------

    /**
     * The danger levels of the routine texts below P: what each captures
     * from ranges that are elaborated again, and the labels it jumps to.
     * AROUND holds the routine texts around P, innermost first.
     */
    private void captures(Node p, Deque<Node> around) {
        for (; p != null; p = p.next) {
            if (p.attribute == Attribute.JUMP) {
                Node id = Gen.son(p, Attribute.IDENTIFIER);
                if (id != null && id.tag != null) {
                    Tag l = id.tag;
                    contribute(around, l.symbolTable.level, new Danger(l.symbolTable.level - 1, null, null, null, l));
                }
                continue;
            }
            if ((p.attribute == Attribute.IDENTIFIER || p.attribute == Attribute.OPERATOR) && p.tag != null) {
                Rep r = repeat.get(p.tag.symbolTable);
                if (r != null && inFrame(p.tag)) {
                    contribute(around, p.tag.symbolTable.level, new Danger(r.level, null, p.tag, r, null));
                }
            }
            // A format text (-fformats) captures like a routine text: its
            // dynamic parts are methods called when the format is used.
            if (p.attribute == Attribute.ROUTINE_TEXT || p.attribute == Attribute.FORMAT_TEXT) {
                around.push(p);
                captures(p.sub, around);
                around.pop();
            } else {
                captures(p.sub, around);
            }
        }
    }

    /** D applies to each routine text of AROUND outside of which LEVEL lies. */
    private void contribute(Deque<Node> around, int level, Danger d) {
        for (Node t : around) {
            if (t.symbolTable == null || level > t.symbolTable.level) {
                return;
            }
            texts.merge(t, d, FrameScope::max);
        }
    }

    /** Whether TAG is held in a frame slot (a captured identifier). */
    private boolean inFrame(Tag t) {
        if (t.moif != null) {
            return false;
        }
        Gen.Routine owner = g.owner(t);
        return owner != null && owner.frameSlots.containsKey(t);
    }

    // ------------------------------------------------------------------
    // Danger levels of values.
    // ------------------------------------------------------------------

    /** Whether a value of mode M can hold a procedure (a name cannot). */
    private boolean holdsProc(Moid m) {
        m = JType.base(m);
        if (m == null) {
            return false;
        }
        Boolean known = procModes.get(m);
        if (known != null) {
            return known;
        }
        procModes.put(m, false); // recursion through a mode being decided
        boolean r = switch (m.attribute) {
            case PROC_SYMBOL -> true;
            case ROW_SYMBOL, FLEX_SYMBOL -> holdsProc(m.sub);
            case STRUCT_SYMBOL, UNION_SYMBOL, SERIES_MODE, STOWED_MODE -> {
                boolean any = false;
                for (Pack k = m.pack; k != null && !any; k = k.next) {
                    any = holdsProc(k.type);
                }
                yield any;
            }
            default -> false;
        };
        procModes.put(m, r);
        return r;
    }

    /** The danger level of the value of P, or null. */
    private Danger danger(Node p) {
        if (p == null || p.type != null && !holdsProc(p.type)) {
            return null;
        }
        switch (p.attribute) {
            case ROUTINE_TEXT, FORMAT_TEXT: {
                Danger d = texts.get(p);
                return d == null ? null : d.at(p);
            }
            case PROCEDURING: {
                Node j = p.sub;
                while (j != null && j.attribute != Attribute.JUMP) {
                    j = j.sub;
                }
                Node id = j == null ? null : Gen.son(j, Attribute.IDENTIFIER);
                if (id == null || id.tag == null) {
                    return null;
                }
                return new Danger(id.tag.symbolTable.level - 1, p, null, null, id.tag);
            }
            case IDENTIFIER, OPERATOR: {
                Danger d = p.tag == null ? null : tags.get(p.tag);
                return d == null ? null : d.at(p);
            }
            case DEREFERENCING: {
                Node v = variable(p.sub);
                Danger d = v == null ? null : tags.get(v.tag);
                return d == null ? null : d.at(v);
            }
            case DECLARATION_LIST, ASSIGNATION, GENERATOR, DENOTATION, NIHIL, SKIP, JUMP, FORMAL_HOLE,
                 LOOP_CLAUSE, VOIDING, IDENTITY_RELATION, AND_FUNCTION, OR_FUNCTION, ASSERTION:
                return null;
            default: {
                Danger d = null;
                for (Node q = p.sub; q != null; q = q.next) {
                    d = max(d, danger(q));
                }
                return d;
            }
        }
    }

    private static Node strip(Node p) {
        while (p != null && p.sub != null && (p.attribute == Attribute.UNIT || p.attribute == Attribute.TERTIARY
                || p.attribute == Attribute.SECONDARY || p.attribute == Attribute.PRIMARY)) {
            p = p.sub;
        }
        return p;
    }

    /** The identifier of the name P (an identifier, or a slice or selection of one), or null. */
    private static Node variable(Node p) {
        p = strip(p);
        if (p == null) {
            return null;
        }
        return switch (p.attribute) {
            case IDENTIFIER -> p.tag != null ? p : null;
            case SLICE -> variable(p.sub);
            case SELECTION -> p.sub == null ? null : variable(p.sub.next);
            default -> null;
        };
    }

    /** The scope (symbol table level) of the name P; 0 if not known. */
    private static int destLevel(Node p) {
        p = strip(p);
        if (p == null) {
            return 0;
        }
        switch (p.attribute) {
            case IDENTIFIER: {
                Tag t = p.tag;
                if (t == null) {
                    return 0;
                }
                if (t.priority == Attribute.PARAMETER_IDENTIFIER.ordinal()) {
                    return t.symbolTable.level - 1;
                }
                if (t.heap == Attribute.HEAP_SYMBOL) {
                    return 0;
                }
                if (!t.variable && t.scopeAssigned) {
                    return t.scope;
                }
                return t.symbolTable.level;
            }
            case SLICE:
                return destLevel(p.sub);
            case SELECTION:
                return p.sub == null ? 0 : destLevel(p.sub.next);
            case GENERATOR:
                if (p.sub != null && p.sub.attribute == Attribute.LOC_SYMBOL) {
                    return p.nonLocal != null ? p.nonLocal.level : p.symbolTable.level;
                }
                return 0;
            default:
                return 0;
        }
    }

    // ------------------------------------------------------------------
    // Identities, variables and the checks.
    // ------------------------------------------------------------------

    /**
     * Passes danger levels to identities and variables below P; with
     * REPORT, gives the warnings for the assignations and the initial
     * values of variables instead.
     */
    private void flow(Node p, boolean report) {
        for (; p != null; p = p.next) {
            switch (p.attribute) {
                case IDENTITY_DECLARATION, PROCEDURE_DECLARATION, OPERATOR_DECLARATION, BRIEF_OPERATOR_DECLARATION,
                     VARIABLE_DECLARATION, PROCEDURE_VARIABLE_DECLARATION -> {
                    boolean var = p.attribute == Attribute.VARIABLE_DECLARATION
                            || p.attribute == Attribute.PROCEDURE_VARIABLE_DECLARATION;
                    List<Node> defs = new ArrayList<>();
                    GenDecls.definitions(p, defs);
                    for (Node q : defs) {
                        if (q.tag == null || q.next == null || q.next.next == null
                                || var && q.next.attribute != Attribute.ASSIGN_SYMBOL) {
                            continue;
                        }
                        Danger d = danger(q.next.next);
                        int dest = !var ? q.tag.symbolTable.level
                                : q.tag.heap == Attribute.HEAP_SYMBOL ? 0 : q.tag.symbolTable.level;
                        if (report) {
                            check(dest, d);
                        } else {
                            raise(q.tag, dest, d);
                        }
                    }
                }
                case ASSIGNATION -> {
                    Node dest = p.sub;
                    Danger d = dest == null || dest.next == null ? null : danger(dest.next.next);
                    if (report) {
                        check(destLevel(dest), d);
                    } else {
                        Node v = variable(dest);
                        if (v != null) {
                            raise(v.tag, destLevel(dest), d);
                        }
                    }
                }
                default -> { }
            }
            flow(p.sub, report);
        }
    }

    /**
     * The value of danger D is stored in T, a name of scope DEST. Unless
     * that is an escape itself (which is reported), T holds the danger on.
     */
    private void raise(Tag t, int dest, Danger d) {
        if (d == null || dest <= d.level) {
            return;
        }
        Danger old = tags.get(t);
        if (old == null || d.level > old.level) {
            tags.put(t, d);
            changed = true;
        }
    }

    /** Warns if a value of danger D is stored in a name of scope DEST. */
    private void check(int dest, Danger d) {
        if (d == null || d.level < 0 || dest > d.level || d.origin == null || !reported.add(d.origin)) {
            return;
        }
        String what = d.origin.attribute == Attribute.ROUTINE_TEXT ? "routine text"
                : d.origin.attribute == Attribute.FORMAT_TEXT ? "format text"
                : d.origin.attribute == Attribute.PROCEDURING ? null
                : "value of '" + Fmt.symbol(d.origin) + "'";
        var diag = g.a68.diagnostics;
        if (d.captured != null) {
            String shared = d.rep.loop != null
                    ? "all iterations of the loop, and may outlive its iteration"
                    : "all elaborations after a jump back to '" + name(d.rep.label) + "', and may outlive its elaboration";
            if (diag.warning(d.origin, Warn.FRAME_SCOPE, "%s captures %qs, which is shared by %s",
                    what, name(d.captured), shared) && d.captured.node != null) {
                diag.inform(d.captured.node, "%qs is declared here", name(d.captured));
            }
        } else if (d.label != null) {
            String l = name(d.label);
            String msg = what == null
                    ? "procedure of this jump to %qs may be called after the serial clause of %qs has been left"
                    : what + " jumps to %qs and may be called after the serial clause of %qs has been left";
            if (diag.warning(d.origin, Warn.FRAME_SCOPE, msg, l, l) && d.label.node != null) {
                diag.inform(d.label.node, "%qs is declared here", l);
            }
        }
    }

    private static String name(Tag t) {
        return t == null || t.node == null ? "?" : String.valueOf(Fmt.symbol(t.node));
    }
}
