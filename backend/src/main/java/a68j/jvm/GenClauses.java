// a68j JVM backend: clauses (reference: a68-low-clauses.cc).
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.Attribute;
import a68j.front.Moid;
import a68j.front.Node;

import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

/** Clauses. */
final class GenClauses {
    private GenClauses() {}

    /** CLOSED_CLAUSE: BEGIN/( SERIAL_CLAUSE END/). */
    static void closedClause(Gen g, Node p) {
        Node s = Gen.son(p, Attribute.SERIAL_CLAUSE);
        serial(g, s, p.type);
    }

    static void serialClause(Gen g, Node p) {
        serial(g, p, p.type);
    }

    /** Flatten the phrases of a serial clause (units, declarations, labeled units, EXIT) in order. */
    static void phrases(Node p, List<Node> out) {
        for (Node q = p.sub; q != null; q = q.next) {
            switch (q.attribute) {
                case SERIAL_CLAUSE, INITIALISER_SERIES, ENQUIRY_CLAUSE -> phrases(q, out);
                case SEMI_SYMBOL, COMMA_SYMBOL -> { }
                default -> out.add(q);
            }
        }
    }

    /**
     * Evaluate a serial clause, yielding a value of mode WANT.
     *
     * <p>Without labels the phrases are evaluated in order. With labels
     * (docs/BACKEND.md section 8) the whole clause is a region entered through a
     * switch on a state local (0 = start, k = k-th label) and protected by
     * a handler for {@link a68j.rt.Jump}: a jump to one of its labels in
     * this activation sets the state and re-enters the region. A completer
     * (EXIT) ends the clause with the value of the unit before it.
     */
    static void serial(Gen g, Node p, Moid want) {
        Gen.Routine pseudo = g.routineOf.get(p);
        if (pseudo != null && g.cur != pseudo) {
            // A serial clause with labels evaluated with a non-empty stack: a separate method.
            pseudo.want = want;
            g.routineFor(p);
            g.cb.aload(g.frameLocal);
            g.cb.iconst_0();
            g.cb.anewarray(CD_Object);
            g.cb.invokestatic(g.thisClass, pseudo.method, Gen.MT_ROUTINE);
            g.unbox(want);
            return;
        }
        List<Node> ph = new ArrayList<>();
        phrases(p, ph);
        int first = -1;
        for (int i = 0; i < ph.size(); i++) {
            if (ph.get(i).attribute == Attribute.LABELED_UNIT) {
                first = i;
                break;
            }
        }
        g.rangeWithoutLabels = first < 0;
        for (Node q : ph) {
            if (isDeclaration(q)) {
                GenDecls.prologue(g, q);
            }
        }
        g.rangeWithoutLabels = false;
        if (first < 0) {
            for (int i = 0; i < ph.size(); i++) {
                phrase(g, ph, i, want);
            }
            return;
        }
        var cb = g.cb;
        // State 0 is the start of the clause, state k the k-th label.
        List<Node> labeled = new ArrayList<>();
        for (int i = first; i < ph.size(); i++) {
            if (ph.get(i).attribute == Attribute.LABELED_UNIT) {
                labeled.add(ph.get(i));
            }
        }
        int state = cb.allocateLocal(java.lang.classfile.TypeKind.INT);
        JType jt = JType.of(g.a68, want);
        int result = jt == JType.VOID ? -1 : cb.allocateLocal(jt.kind);
        cb.iconst_0();
        cb.istore(state);
        Label restart = cb.newLabel();
        Label tryStart = cb.newLabel();
        Label tryEnd = cb.newLabel();
        Label handler = cb.newLabel();
        Label end = cb.newLabel();
        Label rethrow = cb.newLabel();
        List<Label> unitLabels = new ArrayList<>();
        for (int k = 0; k <= labeled.size(); k++) {
            unitLabels.add(cb.newLabel());
        }
        cb.labelBinding(restart);
        cb.labelBinding(tryStart);
        List<java.lang.classfile.instruction.SwitchCase> cases = new ArrayList<>();
        for (int k = 0; k <= labeled.size(); k++) {
            cases.add(java.lang.classfile.instruction.SwitchCase.of(k, unitLabels.get(k)));
        }
        cb.iload(state);
        cb.tableswitch(0, labeled.size(), unitLabels.get(0), cases);
        cb.labelBinding(unitLabels.get(0));
        int k = 1;
        for (int i = 0; i < ph.size(); i++) {
            Node q = ph.get(i);
            if (q.attribute == Attribute.LABELED_UNIT) {
                cb.labelBinding(unitLabels.get(k++));
            }
            if (q.attribute == Attribute.EXIT_SYMBOL) {
                continue;
            }
            boolean last = i == ph.size() - 1 || ph.get(i + 1).attribute == Attribute.EXIT_SYMBOL;
            if (q.attribute == Attribute.LABELED_UNIT) {
                q = Gen.son(q, Attribute.UNIT);
            }
            g.line(q);
            g.unit(q);
            if (isDeclaration(q)) {
                continue;
            }
            if (!last) {
                g.discard(Gen.mode(q));
            } else {
                g.adjust(Gen.mode(q), want, q);
                if (result >= 0) {
                    cb.storeLocal(jt.kind, result);
                }
                cb.goto_(end);
            }
        }
        cb.labelBinding(tryEnd);
        cb.exceptionCatch(tryStart, tryEnd, handler, ClassDesc.of("a68j.rt.Jump"));
        // Handler: stack [Jump].
        ClassDesc cdJump = ClassDesc.of("a68j.rt.Jump");
        cb.labelBinding(handler);
        cb.dup();
        cb.getfield(cdJump, "frame", Gen.CD_FRAME);
        cb.aload(g.frameLocal);
        cb.if_acmpne(rethrow);
        cb.dup();
        cb.getfield(cdJump, "label", CD_int);
        List<java.lang.classfile.instruction.SwitchCase> lcases = new ArrayList<>();
        List<Label> setters = new ArrayList<>();
        for (k = 0; k < labeled.size(); k++) {
            Label l = cb.newLabel();
            setters.add(l);
            for (a68j.front.Tag t : labelTags(labeled.get(k).sub, new ArrayList<>())) {
                lcases.add(java.lang.classfile.instruction.SwitchCase.of(g.labelNumber(t), l));
            }
        }
        lcases.sort((a, b) -> Integer.compare(a.caseValue(), b.caseValue()));
        cb.lookupswitch(rethrow, lcases);
        for (k = 0; k < labeled.size(); k++) {
            cb.labelBinding(setters.get(k));
            cb.pop();
            cb.ldc(k + 1);
            cb.istore(state);
            cb.goto_(restart);
        }
        cb.labelBinding(rethrow);
        cb.athrow();
        cb.labelBinding(end);
        if (result >= 0) {
            cb.loadLocal(jt.kind, result);
        }
    }

    /** Evaluate phrase I of a serial clause without labels (EXIT is not possible there). */
    private static void phrase(Gen g, List<Node> ph, int i, Moid want) {
        Node q = ph.get(i);
        g.line(q);
        g.unit(q);
        if (i < ph.size() - 1) {
            if (!isDeclaration(q)) {
                g.discard(Gen.mode(q));
            }
        } else {
            g.adjust(isDeclaration(q) ? null : Gen.mode(q), want, q);
        }
    }

    /** The tags of the labels in a LABEL subtree (a unit can have several labels). */
    static List<a68j.front.Tag> labelTags(Node label, List<a68j.front.Tag> out) {
        for (Node q = label.sub; q != null; q = q.next) {
            if (q.attribute == Attribute.DEFINING_IDENTIFIER) {
                out.add(q.tag);
            } else if (q.attribute == Attribute.LABEL) {
                labelTags(q, out);
            }
        }
        return out;
    }

    static boolean isDeclaration(Node q) {
        return switch (q.attribute) {
            case DECLARATION_LIST, IDENTITY_DECLARATION, VARIABLE_DECLARATION, PROCEDURE_DECLARATION,
                 PROCEDURE_VARIABLE_DECLARATION, OPERATOR_DECLARATION, BRIEF_OPERATOR_DECLARATION,
                 MODE_DECLARATION, PRIORITY_DECLARATION -> true;
            default -> false;
        };
    }

    /**
     * Evaluate the enquiry clause ENQ (a BOOL) and jump to FALSEL if it
     * yields FALSE. An enquiry consisting of a single comparison of integers
     * jumps without making the BOOL (Intrinsics#branchIfFalse).
     */
    static void branchIfFalse(Gen g, Node enq, Label falseL) {
        if (g.routineOf.get(enq) == null) {
            List<Node> ph = new ArrayList<>();
            phrases(enq, ph);
            if (ph.size() == 1 && !isDeclaration(ph.get(0)) && ph.get(0).attribute != Attribute.LABELED_UNIT
                    && Intrinsics.branchIfFalse(g, ph.get(0), falseL)) {
                return;
            }
        }
        serial(g, enq, g.a68.modes.BOOL);
        g.cb.ifeq(falseL);
    }

    /** IF enquiry THEN serial [ELSE serial | ELIF ...] FI, and the brief form. */
    static void conditionalClause(Gen g, Node p) {
        conditional(g, p.sub, p.type);
    }

    /**
     * IF_PART (OPEN_PART, ELIF_IF_PART, BRIEF_ELIF_IF_PART) holding the
     * enquiry clause, followed by THEN_PART (CHOICE) and optionally by
     * ELSE_PART (CHOICE) or ELIF_PART (BRIEF_ELIF_PART).
     */
    private static void conditional(Gen g, Node ifPart, Moid want) {
        Node enq = Gen.son(ifPart, Attribute.ENQUIRY_CLAUSE);
        Label elseL = g.cb.newLabel();
        Label end = g.cb.newLabel();
        branchIfFalse(g, enq, elseL);
        Node thenPart = ifPart.next;
        serial(g, Gen.son(thenPart, Attribute.SERIAL_CLAUSE), want);
        g.cb.goto_(end);
        g.cb.labelBinding(elseL);
        Node elsePart = thenPart.next;
        if (elsePart != null && (elsePart.attribute == Attribute.ELSE_PART || elsePart.attribute == Attribute.CHOICE)) {
            serial(g, Gen.son(elsePart, Attribute.SERIAL_CLAUSE), want);
        } else if (elsePart != null && (elsePart.attribute == Attribute.ELIF_PART || elsePart.attribute == Attribute.BRIEF_ELIF_PART)) {
            conditional(g, elsePart.sub, want);
        } else {
            GenUnits.pushSkip(g, want);
        }
        g.cb.labelBinding(end);
    }

    static void caseClause(Gen g, Node p) {
        caseParts(g, p.sub, p.type);
    }

    /**
     * CASE enquiry IN units [OUT serial | OUSE ...] ESAC (a68_lower_case_clause):
     * PART is the CASE_PART (OPEN_PART, OUSE_PART, ELSE_OPEN_PART); the
     * k-th unit is chosen when the enquiry yields k.
     */
    private static void caseParts(Gen g, Node part, Moid want) {
        var cb = g.cb;
        Node enq = Gen.son(part, Attribute.ENQUIRY_CLAUSE);
        serial(g, enq, g.a68.modes.INT);
        Node in = part.next;
        List<Node> units = new ArrayList<>();
        GenUnits.collectArguments(in.sub.next, units);
        Label out = cb.newLabel();
        Label end = cb.newLabel();
        if (units.isEmpty()) {
            cb.pop();
        } else {
            List<Label> ls = new ArrayList<>();
            List<java.lang.classfile.instruction.SwitchCase> cases = new ArrayList<>();
            for (int k = 0; k < units.size(); k++) {
                Label l = cb.newLabel();
                ls.add(l);
                cases.add(java.lang.classfile.instruction.SwitchCase.of(k + 1, l));
            }
            cb.tableswitch(1, units.size(), out, cases);
            for (int k = 0; k < units.size(); k++) {
                cb.labelBinding(ls.get(k));
                Node u = units.get(k);
                g.line(u);
                g.unit(u);
                g.adjust(Gen.mode(u), want, u);
                cb.goto_(end);
            }
        }
        cb.labelBinding(out);
        outPart(g, in.next, want, true);
        cb.labelBinding(end);
    }

    /** The OUT part of a case or conformity clause: OUT serial, ESAC, or a nested OUSE. */
    private static void outPart(Gen g, Node s, Moid want, boolean intCase) {
        if (s == null || s.attribute == Attribute.ESAC_SYMBOL || s.attribute == Attribute.CLOSE_SYMBOL) {
            GenUnits.pushSkip(g, want);
        } else if (s.attribute == Attribute.OUT_PART || s.attribute == Attribute.CHOICE) {
            serial(g, Gen.son(s, Attribute.SERIAL_CLAUSE), want);
        } else if (intCase) {
            caseParts(g, s.sub, want);
        } else {
            conformityParts(g, s.sub, want);
        }
    }

    static void conformityClause(Gen g, Node p) {
        conformityParts(g, p.sub, p.type);
    }

    /**
     * CASE enquiry IN (spec) unit, ... [OUT serial | OUSE ...] ESAC
     * (a68_lower_conformity_clause). The united value carries the number of
     * the mode of its value; a specifier of a united mode accepts every
     * member of that mode and gets the united value itself.
     */
    private static void conformityParts(Gen g, Node part, Moid want) {
        var cb = g.cb;
        Moid enqMode = part.sub.type;
        Node enq = Gen.son(part, Attribute.ENQUIRY_CLAUSE);
        serial(g, enq, enqMode);
        int u = cb.allocateLocal(java.lang.classfile.TypeKind.REFERENCE);
        cb.astore(u);
        Node in = part.next;
        List<Node[]> specs = new ArrayList<>();
        specifiedUnits(in.sub.next, specs);
        Label end = cb.newLabel();
        for (Node[] su : specs) {
            Node spec = su[0];
            Node unit = su[1];
            Moid sm = JType.base(spec.sub.next.type);
            Node id = spec.sub.next.next;
            List<Integer> numbers = new ArrayList<>();
            if (sm.attribute == Attribute.UNION_SYMBOL) {
                for (a68j.front.Pack q = sm.pack; q != null; q = q.next) {
                    numbers.add(JType.unionTag(g, q.type));
                }
            } else {
                numbers.add(JType.unionTag(g, sm));
            }
            Label take = cb.newLabel();
            Label next = cb.newLabel();
            for (int n : numbers) {
                cb.aload(u);
                cb.getfield(Gen.CD_UNION, "mode", CD_int);
                cb.ldc(n);
                cb.if_icmpeq(take);
            }
            cb.goto_(next);
            cb.labelBinding(take);
            if (id != null && id.attribute == Attribute.IDENTIFIER && id.tag != null) {
                cb.aload(u);
                if (sm.attribute != Attribute.UNION_SYMBOL) {
                    cb.getfield(Gen.CD_UNION, "value", CD_Object);
                    g.unbox(id.tag.type);
                }
                g.defineAndStore(id.tag);
            }
            g.line(unit);
            g.unit(unit);
            g.adjust(Gen.mode(unit), want, unit);
            cb.goto_(end);
            cb.labelBinding(next);
        }
        outPart(g, in.next, want, false);
        cb.labelBinding(end);
    }

    /** Pairs {SPECIFIER, UNIT} below P, in order. */
    private static void specifiedUnits(Node p, List<Node[]> out) {
        for (; p != null; p = p.next) {
            if (p.attribute == Attribute.SPECIFIER) {
                out.add(new Node[] {p, p.next.next});
                p = p.next.next;
            } else if (p.sub != null && p.attribute != Attribute.UNIT) {
                specifiedUnits(p.sub, out);
            }
        }
    }

    /** [FOR i] [FROM a] [BY b] [TO c] [WHILE e] DO s OD (INT counters only). */
    static void loopClause(Gen g, Node p) {
        if (Outline.loop(g, p)) {
            return;
        }
        loopInline(g, p);
    }

    /** A loop clause compiled in the current method. */
    static void loopInline(Gen g, Node p) {
        g.loopDepth++;
        List<Hoist.Row> hoisted = Hoist.enter(g, p);
        try {
            loop(g, p);
        } finally {
            Hoist.exit(g, hoisted);
            g.loopDepth--;
        }
    }

    private static void loop(Gen g, Node p) {
        Node forPart = Gen.son(p, Attribute.FOR_PART);
        Node fromPart = Gen.son(p, Attribute.FROM_PART);
        Node byPart = Gen.son(p, Attribute.BY_PART);
        Node toPart = Gen.son(p, Attribute.TO_PART);
        Node whilePart = Gen.son(p, Attribute.WHILE_PART);
        Node doPart = Gen.son(p, Attribute.DO_PART);
        if (doPart == null) {
            doPart = Gen.son(p, Attribute.ALT_DO_PART);
        }
        var cb = g.cb;
        if (forPart == null && fromPart == null && byPart == null && toPart == null) {
            // [WHILE ...] DO ... OD: no iterator (as a68_lower_loop_clause).
            Label top = cb.newLabel();
            Label end = cb.newLabel();
            cb.labelBinding(top);
            if (whilePart != null) {
                branchIfFalse(g, Gen.son(whilePart, Attribute.ENQUIRY_CLAUSE), end);
            }
            Node body = Gen.son(doPart, Attribute.SERIAL_CLAUSE);
            if (body != null) {
                serial(g, body, g.a68.modes.VOID);
            }
            cb.goto_(top);
            cb.labelBinding(end);
            return;
        }
        if (toPart != null && (byPart == null || isOne(byPart.sub.next))) {
            countedLoop(g, forPart, fromPart, byPart, toPart, whilePart, doPart);
            return;
        }
        int i = cb.allocateLocal(java.lang.classfile.TypeKind.INT);
        int by = cb.allocateLocal(java.lang.classfile.TypeKind.INT);
        int to = cb.allocateLocal(java.lang.classfile.TypeKind.INT);
        if (fromPart != null) {
            g.unit(fromPart.sub.next);
        } else {
            cb.iconst_1();
        }
        cb.istore(i);
        if (byPart != null) {
            g.unit(byPart.sub.next);
        } else {
            cb.iconst_1();
        }
        cb.istore(by);
        if (toPart != null) {
            g.unit(toPart.sub.next);
            cb.istore(to);
        }
        Label top = cb.newLabel();
        Label end = cb.newLabel();
        cb.labelBinding(top);
        if (toPart != null) {
            // by > 0 ? i > to : i < to  -> exit
            Label neg = cb.newLabel();
            Label body = cb.newLabel();
            cb.iload(by);
            cb.iflt(neg);
            cb.iload(i);
            cb.iload(to);
            cb.if_icmpgt(end);
            cb.goto_(body);
            cb.labelBinding(neg);
            cb.iload(i);
            cb.iload(to);
            cb.if_icmplt(end);
            cb.labelBinding(body);
        }
        if (forPart != null) {
            Node id = Gen.son(forPart, Attribute.DEFINING_IDENTIFIER);
            cb.iload(i);
            g.defineAndStore(id.tag);
        }
        if (whilePart != null) {
            branchIfFalse(g, Gen.son(whilePart, Attribute.ENQUIRY_CLAUSE), end);
        }
        Node body = Gen.son(doPart, Attribute.SERIAL_CLAUSE);
        if (body != null) {
            serial(g, body, g.a68.modes.VOID);
        }
        // i +:= by; the loop ends when the counter would overflow (as ga68).
        int next = cb.allocateLocal(java.lang.classfile.TypeKind.INT);
        cb.iload(i);
        cb.iload(by);
        cb.iadd();
        cb.istore(next);
        cb.iload(i);
        cb.iload(next);
        cb.ixor();
        cb.iload(by);
        cb.iload(next);
        cb.ixor();
        cb.iand();
        cb.iflt(end);
        cb.iload(next);
        cb.istore(i);
        cb.goto_(top);
        cb.labelBinding(end);
    }

    /** Whether the unit P is the denotation 1 (of mode INT). */
    private static boolean isOne(Node p) {
        Node d = GenUnits.strip(p);
        if (d.attribute == Attribute.DENOTATION && d.sub != null && d.sub.next == null) {
            d = d.sub;
        }
        return d.attribute == Attribute.INT_DENOTATION && d.info != null && "1".equals(d.info.symbol);
    }

    /**
     * A loop with TO and the step 1 ([FOR i] [FROM a] [BY 1] TO b [WHILE c]
     * DO ... OD): the same as the general loop, which ends when i > b or when
     * i +:= 1 would overflow, in a shape the JIT treats as a counted loop:
     * <pre>
     *   i := a; to := b; if i > to: end
     *   top: [WHILE] body; if i >= to: end; i := i + 1; goto top
     * </pre>
     * After the body, i >= to says that the next value would be beyond to,
     * which includes the overflow case (i = max int >= to).
     */
    private static void countedLoop(Gen g, Node forPart, Node fromPart, Node byPart, Node toPart,
            Node whilePart, Node doPart) {
        var cb = g.cb;
        int i = cb.allocateLocal(java.lang.classfile.TypeKind.INT);
        int to = cb.allocateLocal(java.lang.classfile.TypeKind.INT);
        if (fromPart != null) {
            g.unit(fromPart.sub.next);
        } else {
            cb.iconst_1();
        }
        cb.istore(i);
        if (byPart != null) {
            // Evaluated as in the general loop (a denotation: no effect).
            g.unit(byPart.sub.next);
            cb.pop();
        }
        g.unit(toPart.sub.next);
        cb.istore(to);
        Label top = cb.newLabel();
        Label end = cb.newLabel();
        cb.iload(i);
        cb.iload(to);
        cb.if_icmpgt(end);
        cb.labelBinding(top);
        if (forPart != null) {
            Node id = Gen.son(forPart, Attribute.DEFINING_IDENTIFIER);
            cb.iload(i);
            g.defineAndStore(id.tag);
        }
        if (whilePart != null) {
            branchIfFalse(g, Gen.son(whilePart, Attribute.ENQUIRY_CLAUSE), end);
        }
        Node body = Gen.son(doPart, Attribute.SERIAL_CLAUSE);
        if (body != null) {
            serial(g, body, g.a68.modes.VOID);
        }
        cb.iload(i);
        cb.iload(to);
        cb.if_icmpge(end);
        cb.iinc(i, 1);
        cb.goto_(top);
        cb.labelBinding(end);
    }

    static void collateralClause(Gen g, Node p) {
        GenRows.collateral(g, p);
    }

    static final ClassDesc CD_PAR = ClassDesc.of("a68j.rt.Par");

    /**
     * PARALLEL_CLAUSE: PAR COLLATERAL_CLAUSE. As a68_lower_parallel_clause
     * ("for now treat like a VOID collateral clause"): the units are
     * elaborated one after the other, in this thread.
     *
     * <p>With -fpar-threads (an a68j extension) each unit is a method of its
     * own (see Capture) and {@link a68j.rt.Par#run} elaborates them in
     * parallel threads, each as a closure over the current frame. The clause
     * ends when all units have ended; a jump out of a unit or a runtime
     * error stops the others and continues in this thread.
     */
    static void parallelClause(Gen g, Node p) {
        Node c = p.sub.next;
        if (!g.a68.options().parThreads) {
            g.unit(c);
            g.discard(Gen.mode(c));
            return;
        }
        List<Node> units = new ArrayList<>();
        GenUnits.collectArguments(c.sub, units);
        var cb = g.cb;
        cb.ldc(units.size());
        cb.anewarray(Gen.CD_PROC);
        for (int i = 0; i < units.size(); i++) {
            Gen.Routine r = g.routineFor(units.get(i));
            cb.dup();
            cb.ldc(i);
            cb.new_(Gen.CD_CLOSURE);
            cb.dup();
            cb.ldc(g.handle(r));
            cb.aload(g.frameLocal);
            cb.invokespecial(Gen.CD_CLOSURE, "<init>",
                    MethodTypeDesc.of(CD_void, CD_MethodHandle, Gen.CD_FRAME));
            cb.aastore();
        }
        cb.invokestatic(CD_PAR, "run", MethodTypeDesc.of(CD_void, Gen.CD_PROC.arrayType()));
    }

    /**
     * ACCESS_CLAUSE: REVELATION ENCLOSED_CLAUSE (a68_lower_access_clause):
     * call the preludes of the accessed modules, elaborate the clause, call
     * their postludes.
     */
    static void accessClause(Gen g, Node p) {
        Node controlled = p.sub.next;
        // Sic: a68_lower_revelation_ludes walks the siblings of the
        // revelation too, so the modules of ACCESS clauses nested in the
        // controlled clause are also "preluded" here.
        revelationLudes(g, p.sub, true);
        g.unit(controlled);
        g.adjust(Gen.mode(controlled), p.type, p);
        revelationLudes(g, p.sub, false);
    }

    /** Calls of the preludes or postludes of all the module indicants in P and its siblings. */
    static void revelationLudes(Gen g, Node p, boolean prelude) {
        for (; p != null; p = p.next) {
            if (p.attribute == Attribute.MODULE_INDICANT) {
                a68j.front.Tag tag = a68j.front.Taxes.findTagGlobal(g.a68, p.symbolTable,
                        Attribute.MODULE_SYMBOL, p.symbol());
                String cls = tag == null || tag.moif == null ? null : Intrinsics.ludeClass(tag.moif.name);
                if (cls != null) {
                    g.cb.invokestatic(java.lang.constant.ClassDesc.of(cls), prelude ? "prelude" : "postlude",
                            java.lang.constant.MethodTypeDesc.of(CD_void));
                }
            } else {
                revelationLudes(g, p.sub, prelude);
            }
        }
    }

    /** JUMP: [GOTO] label identifier; throws a Jump to the label's activation. */
    static void jump(Gen g, Node p) {
        Node id = Gen.son(p, Attribute.IDENTIFIER);
        a68j.front.Tag t = id.tag;
        Gen.Routine owner = g.tableOwner.get(t.symbolTable);
        ClassDesc cdJump = ClassDesc.of("a68j.rt.Jump");
        g.cb.new_(cdJump);
        g.cb.dup();
        g.loadFrame(owner);
        g.cb.ldc(g.labelNumber(t));
        g.cb.invokespecial(cdJump, "<init>", MethodTypeDesc.of(CD_void, Gen.CD_FRAME, CD_int));
        g.cb.athrow();
        // Unreachable; keep the stack shape of the jump's mode for the verifier.
        GenUnits.pushSkip(g, p.type);
    }

    /** LABELED_UNIT: LABEL UNIT (the label is bound by the enclosing serial clause). */
    static void labeledUnit(Gen g, Node p) {
        Node u = p.sub.next;
        g.unit(u);
        g.adjust(u.type, p.type, p);
    }

    /** ASSERT (enclosed clause). */
    static void assertion(Gen g, Node p) {
        Node e = p.sub.next;
        Label ok = g.cb.newLabel();
        g.unit(e);
        g.cb.ifne(ok);
        g.pushWhere(p);
        g.cb.invokestatic(Gen.CD_RT, "assertFailure", MethodTypeDesc.of(ClassDesc.of("java.lang.RuntimeException"), CD_String, CD_int));
        g.cb.athrow();
        g.cb.labelBinding(ok);
    }
}
