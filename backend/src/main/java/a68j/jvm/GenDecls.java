// a68j JVM backend: declarations (reference: a68-low-decls.cc).
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import a68j.front.Attribute;
import a68j.front.Moid;
import a68j.front.Node;
import a68j.front.Tag;

import java.util.ArrayList;
import java.util.List;

/** Declarations. Each declaration leaves nothing on the stack. */
final class GenDecls {
    private GenDecls() {}

    static void declaration(Gen g, Node p) {
        switch (p.attribute) {
            case DECLARATION_LIST -> {
                for (Node q = p.sub; q != null; q = q.next) {
                    if (q.attribute != Attribute.COMMA_SYMBOL) {
                        declaration(g, q);
                    }
                }
            }
            case MODE_DECLARATION, PRIORITY_DECLARATION -> { }
            case IDENTITY_DECLARATION -> identityDeclaration(g, p);
            case VARIABLE_DECLARATION -> variableDeclaration(g, p);
            case PROCEDURE_DECLARATION -> procedureDeclaration(g, p);
            case PROCEDURE_VARIABLE_DECLARATION -> procedureVariableDeclaration(g, p);
            case OPERATOR_DECLARATION, BRIEF_OPERATOR_DECLARATION -> operatorDeclaration(g, p);
            default -> throw new Gen.Unsupported(p, p.attribute.name());
        }
    }

    /** a68_lower_identity_declaration: DECLARER? DEFINING_IDENTIFIER = UNIT. */
    static void identityDeclaration(Gen g, Node p) {
        Node defId;
        if (p.sub.attribute == Attribute.IDENTITY_DECLARATION) {
            identityDeclaration(g, p.sub);
            defId = p.sub.next.next;
        } else if (p.sub.attribute == Attribute.PUBLIC_SYMBOL) {
            defId = p.sub.next.next;
        } else {
            defId = p.sub.next;
        }
        Node unit = defId.next.next;
        g.line(unit);
        g.unit(unit);
        GenUnits.copyIfValue(g, unit, unit.type);
        g.defineAndStore(defId.tag);
    }

    /** a68_lower_variable_declaration: [QUALIFIER] DECLARER DEFINING_IDENTIFIER [:= UNIT]. */
    static void variableDeclaration(Gen g, Node p) {
        Node declarer = null;
        Node defId;
        if (p.sub.attribute == Attribute.VARIABLE_DECLARATION) {
            declarer = variableDeclaration0(g, p.sub);
            defId = p.sub.next.next;
        } else {
            Node q = p.sub;
            if (q.attribute == Attribute.PUBLIC_SYMBOL) {
                q = q.next;
            }
            if (q.attribute == Attribute.QUALIFIER) {
                declarer = q.next;
                defId = q.next.next;
            } else {
                declarer = q;
                defId = q.next;
            }
        }
        declareVariable(g, declarer, defId);
    }

    private static Node variableDeclaration0(Gen g, Node p) {
        Node declarer;
        Node defId;
        if (p.sub.attribute == Attribute.VARIABLE_DECLARATION) {
            declarer = variableDeclaration0(g, p.sub);
            defId = p.sub.next.next;
        } else {
            Node q = p.sub;
            if (q.attribute == Attribute.PUBLIC_SYMBOL) {
                q = q.next;
            }
            if (q.attribute == Attribute.QUALIFIER) {
                q = q.next;
            }
            declarer = q;
            defId = q.next;
        }
        declareVariable(g, declarer, defId);
        return declarer;
    }

    private static void declareVariable(Gen g, Node declarer, Node defId) {
        g.line(defId);
        if (!cellAtEntry(defId.tag)) {
            // The name: a new location holding the initial value of the declarer.
            GenRows.generate(g, declarer, defId.type.sub, defId);
            g.store(defId.tag);
        }
        if (defId.next != null && defId.next.attribute == Attribute.ASSIGN_SYMBOL && g.direct.contains(defId.tag)) {
            g.unit(defId.next.next);
            g.store(defId.tag);
        } else if (defId.next != null && defId.next.attribute == Attribute.ASSIGN_SYMBOL) {
            g.load(defId.tag, defId);
            GenUnits.assignTo(g, defId.tag.type, defId.next.next, defId);
        }
    }

    /** PROC f = routine text: elaborated at the start of the serial clause (see {@link #prologue}). */
    static void procedureDeclaration(Gen g, Node p) {
    }

    /** [qualifier] PROC f := routine text: a variable of a procedure mode. */
    static void procedureVariableDeclaration(Gen g, Node p) {
        List<Node> defs = new ArrayList<>();
        definitions(p, defs);
        for (Node defId : defs) {
            g.line(defId);
            if (!cellAtEntry(defId.tag)) {
                GenRows.generate(g, null, defId.tag.type.sub, defId);
                g.store(defId.tag);
            }
            g.load(defId.tag, defId);
            GenUnits.assignTo(g, defId.tag.type, defId.next.next, defId);
        }
    }

    /** OP (...) M f = unit (a68_lower_operator_declaration); brief ones are hoisted like procedures. */
    static void operatorDeclaration(Gen g, Node p) {
        if (p.attribute == Attribute.BRIEF_OPERATOR_DECLARATION) {
            return;
        }
        List<Node> defs = new ArrayList<>();
        definitions(p, defs);
        for (Node defOp : defs) {
            Node unit = defOp.next.next;
            g.line(unit);
            g.unit(unit);
            g.adjust(Gen.mode(unit), defOp.tag.type, unit);
            g.store(defOp.tag);
        }
    }

    /** The defining identifiers and operators of a declaration or declaration list. */
    static void allDefinitions(Node d, List<Node> out) {
        if (d.attribute == Attribute.DECLARATION_LIST) {
            for (Node q = d.sub; q != null; q = q.next) {
                if (GenClauses.isDeclaration(q)) {
                    allDefinitions(q, out);
                }
            }
        } else if (GenClauses.isDeclaration(d) && d.attribute != Attribute.MODE_DECLARATION
                && d.attribute != Attribute.PRIORITY_DECLARATION) {
            definitions(d, out);
        }
    }

    /** Whether the defining identifier (operator) DEF is declared PUB. */
    static boolean isPublic(Node def) {
        return def.tag != null && def.tag.publicized;
    }

    // ------------------------------------------------------------------
    // Serial clause prologue.
    // ------------------------------------------------------------------

    /**
     * The defining identifiers (operators) of a declaration of one kind:
     * {@code K: K , defining, =, unit} or {@code [PUBLIC] [qualifier] [PROC|OP|declarer] defining ...}.
     */
    static void definitions(Node p, List<Node> out) {
        if (p.sub.attribute == p.attribute) {
            definitions(p.sub, out);
            out.add(p.sub.next.next);
            return;
        }
        for (Node q = p.sub; q != null; q = q.next) {
            if (q.attribute == Attribute.DEFINING_IDENTIFIER || q.attribute == Attribute.DEFINING_OPERATOR) {
                out.add(q);
                return;
            }
        }
    }

    /**
     * Whether the variable TAG (of mode REF M) gets its location when its
     * range is entered: LOC variables without rows are C variables of the
     * range in ga68, so they exist (with a SKIP value) before their
     * declaration; HEAP variables and those with rows are generated at the
     * declaration.
     */
    static boolean cellAtEntry(Tag t) {
        Moid m = JType.base(t.type);
        return t.heap != Attribute.HEAP_SYMBOL && m.sub != null && !JType.base(m.sub).hasRows;
    }

    /**
     * Whether the identity Q (a defining identifier of a row, structure or
     * union mode, whose SKIP value would be built at run time) can do
     * without its SKIP value at the start of its range: its range has no
     * labels (so no jump can skip the declaration), it lives in a JVM local
     * (no inner routine can see it), and it does not occur in the text
     * before its declaration. Then every use is preceded by the declaration.
     */
    private static boolean skipNotNeeded(Gen g, Node q) {
        JType t = JType.of(g.a68, q.tag.type);
        if (t != JType.ROW && t != JType.STRUCT && t != JType.UNION) {
            return false;
        }
        Gen.Routine owner = g.tableOwner.get(q.tag.symbolTable);
        return g.rangeWithoutLabels && owner != null && !owner.frameSlots.containsKey(q.tag)
                && !g.usedBeforeDeclaration(q);
    }

    /**
     * At the start of a serial clause, the identifiers it declares get their
     * initial values, as ga68 declares them for the whole range with a SKIP
     * value: identities SKIP, LOC variables without rows a fresh location,
     * other variables NIL; procedures and brief operators are elaborated
     * here, so that they can be called before their declaration.
     */
    static void prologue(Gen g, Node d) {
        switch (d.attribute) {
            case DECLARATION_LIST -> {
                for (Node q = d.sub; q != null; q = q.next) {
                    if (GenClauses.isDeclaration(q)) {
                        prologue(g, q);
                    }
                }
            }
            case IDENTITY_DECLARATION, OPERATOR_DECLARATION -> {
                List<Node> defs = new ArrayList<>();
                definitions(d, defs);
                for (Node q : defs) {
                    if (skipNotNeeded(g, q)) {
                        // Nobody can see the identifier before its
                        // declaration: no need to build a SKIP value.
                        g.cb.aconst_null();
                    } else {
                        GenUnits.pushSkip(g, q.tag.type);
                    }
                    g.store(q.tag);
                }
            }
            case VARIABLE_DECLARATION, PROCEDURE_VARIABLE_DECLARATION -> {
                List<Node> defs = new ArrayList<>();
                definitions(d, defs);
                for (Node q : defs) {
                    if (g.direct.contains(q.tag)) {
                        GenUnits.pushSkip(g, JType.base(q.tag.type).sub);
                    } else if (cellAtEntry(q.tag)) {
                        // ga68 does not run a generator for these (no
                        // fill_in_buffer): the C variable holds the SKIP value.
                        GenRows.generateSkip(g, JType.base(q.tag.type).sub);
                    } else {
                        g.cb.aconst_null();
                    }
                    g.store(q.tag);
                }
            }
            case PROCEDURE_DECLARATION, BRIEF_OPERATOR_DECLARATION -> {
                List<Node> defs = new ArrayList<>();
                definitions(d, defs);
                for (Node q : defs) {
                    g.unit(q.next.next);
                    g.store(q.tag);
                }
            }
            default -> { }
        }
    }
}
