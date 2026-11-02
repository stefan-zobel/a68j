// a68j JVM backend: capture analysis (docs/BACKEND.md section 3).
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import a68j.front.Attribute;
import a68j.front.Node;
import a68j.front.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the routine texts, the routine that owns each symbol table, and the
 * identifiers that are used in a routine other than the one declaring them.
 * Those are stored in the declaring routine's frame.
 */
final class Capture {
    private Capture() {}

    private record Use(Tag tag, Gen.Routine in) {}

    static void analyse(Gen g, Node top, Gen.Routine program) {
        List<Use> uses = new ArrayList<>();
        walk(g, top, program, uses, true);
        for (Use u : uses) {
            Gen.Routine owner = g.tableOwner.get(u.tag.symbolTable);
            if (owner != null && owner != u.in && u.tag.moif == null && !owner.frameSlots.containsKey(u.tag)) {
                owner.frameSlots.put(u.tag, owner.frameSlots.size());
            }
        }
    }

    /**
     * Walk the tree. TOP says whether P is evaluated with an empty operand
     * stack (relative to its routine): a serial clause with labels needs that,
     * because its jump handler starts with an empty stack (docs/BACKEND.md section
     * 8). A serial clause with labels in another position is compiled as a
     * separate method without parameters (a pseudo-routine) and called there.
     */
    private static void walk(Gen g, Node p, Gen.Routine cur, List<Use> uses, boolean top) {
        for (; p != null; p = p.next) {
            Gen.Routine in = cur;
            if (!top && p.attribute == Attribute.SERIAL_CLAUSE && hasLabels(p)) {
                in = new Gen.Routine(g.routines.size(), p, cur, "r" + g.routines.size());
                g.routines.add(in);
                g.routineOf.put(p, in);
                if (p.symbolTable != null) {
                    g.tableOwner.put(p.symbolTable, in);
                }
            } else {
                if (p.symbolTable != null) {
                    g.tableOwner.putIfAbsent(p.symbolTable, cur);
                }
                if (p.attribute == Attribute.SERIAL_CLAUSE && hasLabels(p)) {
                    cur.ownsLabels = true;
                }
            }
            if (p.tag != null) {
                uses.add(new Use(p.tag, in));
            }
            if (p.attribute == Attribute.INDICANT) {
                declarerUses(p.tag, in, uses, new HashSet<>());
            }
            if (p.attribute == Attribute.PROCEDURE_DECLARATION || p.attribute == Attribute.BRIEF_OPERATOR_DECLARATION) {
                List<Node> defs = new ArrayList<>();
                GenDecls.definitions(p, defs);
                for (Node q : defs) {
                    Node text = q.next == null || q.next.next == null ? null : GenUnits.strip(q.next.next);
                    if (q.tag != null && text != null && text.attribute == Attribute.ROUTINE_TEXT) {
                        g.staticProcs.put(q.tag, text);
                    }
                }
            }
            if (p.attribute == Attribute.IDENTITY_DECLARATION) {
                List<Node> defs = new ArrayList<>();
                GenDecls.definitions(p, defs);
                for (Node q : defs) {
                    addValueConst(g, q.tag);
                }
            }
            if (p.attribute == Attribute.ROUTINE_TEXT) {
                for (a68j.front.Tag t : GenUnits.parameters(p)) {
                    addValueConst(g, t);
                }
            }
            if (g.a68.options().nestJava && (p.attribute == Attribute.IDENTITY_DECLARATION
                    || p.attribute == Attribute.PROCEDURE_DECLARATION || p.attribute == Attribute.OPERATOR_DECLARATION
                    || p.attribute == Attribute.BRIEF_OPERATOR_DECLARATION)) {
                // proc (int) int f = nest Java "...": calls of f go directly to Java.
                List<Node> defs = new ArrayList<>();
                GenDecls.definitions(p, defs);
                for (Node q : defs) {
                    Node hole = q.next == null || q.next.next == null ? null : GenUnits.strip(q.next.next);
                    if (q.tag != null && hole != null && hole.attribute == Attribute.FORMAL_HOLE
                            && a68j.front.MoidsCheck.isJavaHole(hole) && hole.type != null
                            && JType.base(hole.type).attribute == Attribute.PROC_SYMBOL) {
                        g.javaProcs.put(q.tag, hole);
                    }
                }
            }
            if (p.attribute == Attribute.PARALLEL_CLAUSE && g.a68.options().parThreads) {
                // -fpar-threads: each unit of the clause is a method without
                // parameters run in its own thread (GenClauses#parallelClause).
                // What it uses from the enclosing routines lives in their frames.
                List<Node> units = new ArrayList<>();
                GenUnits.collectArguments(p.sub.next.sub, units);
                for (Node u : units) {
                    Gen.Routine r = new Gen.Routine(g.routines.size(), u, in, "r" + g.routines.size());
                    r.parUnit = true;
                    g.routines.add(r);
                    g.routineOf.put(u, r);
                    if (u.tag != null) {
                        uses.add(new Use(u.tag, r));
                    }
                    walk(g, u.sub, r, uses, true);
                }
                continue;
            }
            if (p.attribute == Attribute.ROUTINE_TEXT) {
                Gen.Routine r = new Gen.Routine(g.routines.size(), p, cur, "r" + g.routines.size());
                g.routines.add(r);
                g.routineOf.put(p, r);
                walk(g, p.sub, r, uses, true);
            } else {
                walk(g, p.sub, in, uses, in != cur || keepsTop(p, top));
            }
        }
    }

    /**
     * The uses in IN through the mode indicant T: the bounds of the actual
     * declarer of its mode declaration (MODE M = [p:q] INT) are evaluated
     * where M is used (GenRows#collectDeclarerBounds), so a routine using M
     * uses p and q, declared perhaps in another routine.  The declarers of
     * the indicants in that declarer count too; each indicant once
     * (VISITING).  Routine texts in the declarer are not followed: their
     * uses are recorded where they are.
     */
    private static void declarerUses(Tag t, Gen.Routine in, List<Use> uses, Set<Tag> visiting) {
        if (t == null || t.node == null || t.type == null || !t.type.hasRows || !visiting.add(t)
                || t.node.next == null || t.node.next.next == null) {
            return;
        }
        tagsIn(t.node.next.next, in, uses, visiting);
    }

    private static void tagsIn(Node p, Gen.Routine in, List<Use> uses, Set<Tag> visiting) {
        if (p.attribute == Attribute.ROUTINE_TEXT) {
            return;
        }
        if (p.attribute == Attribute.INDICANT) {
            declarerUses(p.tag, in, uses, visiting);
        } else if (p.tag != null) {
            uses.add(new Use(p.tag, in));
        }
        for (Node q = p.sub; q != null; q = q.next) {
            tagsIn(q, in, uses, visiting);
        }
    }

    /** Whether the sons of P are evaluated at the stack depth of P (if P is). */
    private static boolean keepsTop(Node p, boolean top) {
        if (!top) {
            return false;
        }
        return switch (p.attribute) {
            case PACKET, PARTICULAR_PROGRAM, PRELUDE_PACKET, MODULE_DECLARATION, MODULE_TEXT, DEF_PART, POSTLUDE_PART,
                 ENCLOSED_CLAUSE, CLOSED_CLAUSE, SERIAL_CLAUSE, ENQUIRY_CLAUSE, INITIALISER_SERIES, DECLARATION_LIST,
                 LABELED_UNIT, LABEL, UNIT, TERTIARY, SECONDARY, PRIMARY,
                 CONDITIONAL_CLAUSE, IF_PART, THEN_PART, ELSE_PART, ELIF_PART, ELIF_IF_PART, OPEN_PART, CHOICE,
                 BRIEF_ELIF_PART, ELSE_OPEN_PART,
                 CASE_CLAUSE, CONFORMITY_CLAUSE, CASE_PART, CASE_IN_PART, OUT_PART, CASE_OUSE_PART, OUSE_PART,
                 CASE_CHOICE_CLAUSE, BRIEF_OUSE_PART, CONFORMITY_IN_PART, CONFORMITY_OUSE_PART, CONFORMITY_CHOICE,
                 BRIEF_CONFORMITY_OUSE_PART, SPECIFIED_UNIT_LIST, SPECIFIED_UNIT, UNIT_LIST,
                 LOOP_CLAUSE, FOR_PART, FROM_PART, BY_PART, TO_PART, WHILE_PART, DO_PART, ALT_DO_PART,
                 IDENTITY_DECLARATION, PROCEDURE_DECLARATION, OPERATOR_DECLARATION, BRIEF_OPERATOR_DECLARATION,
                 VOIDING, DEREFERENCING, DEPROCEDURING, UNITING, ROWING, WIDENING, CAST -> true;
            default -> false;
        };
    }

    /** Whether the serial clause P has labeled units among its phrases. */
    private static void addValueConst(Gen g, a68j.front.Tag t) {
        if (t == null) {
            return;
        }
        JType jt = JType.of(g.a68, t.type);
        if (jt == JType.ROW || jt == JType.STRUCT || jt == JType.UNION) {
            g.valueConsts.add(t);
        }
    }

    static boolean hasLabels(Node p) {
        List<Node> ph = new ArrayList<>();
        GenClauses.phrases(p, ph);
        for (Node q : ph) {
            if (q.attribute == Attribute.LABELED_UNIT) {
                return true;
            }
        }
        return false;
    }
}
