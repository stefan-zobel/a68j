// a68j JVM backend: parts of serial clauses compiled as methods of their own.
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.Attribute;
import a68j.front.Moid;
import a68j.front.Node;
import a68j.front.Tag;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.CharacterRange;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parts of serial clauses compiled as methods of their own
 * (docs/TODO-method-size.md, step 3): the general remedy for a method that
 * is too large for the JIT (more than 8000 bytes) or for the JVM (more than
 * 65535), after its loops have become methods of their own (Outline) and
 * its element accesses compact (Gen#compactAccess).
 *
 * <p>What: a serial clause is cut before phrase k; the phrases from k on,
 * the rest of the clause, become a method {@code partN} that the clause
 * calls in their place and whose result is the value of the clause. A rest
 * that is still too large is cut again, so a long sequence of phrases
 * becomes a chain of methods. With k = 0 the whole clause is such a method:
 * for a branch of a conditional or case clause, or a closed clause, inside
 * a phrase that is too large itself (the step of an interpreter: one case
 * clause with many branches). Only rests, no pieces in the middle: what a
 * rest declares, only later phrases can see, and they are in the rest too.
 *
 * <p>How: as for a loop method (Outline), the part is compiled exactly as
 * before in the same routine ({@link Gen#cur}: frames, jumps and labels
 * work as in the enclosing method); the method gets the frame and the
 * values of the JVM locals the part uses, and the direct variables it
 * assigns in the one {@code long[]} holder, copied back when it ends,
 * normally or by an exception.
 *
 * <p>Where a clause may be cut ({@link #admissible}):
 * <ul>
 * <li>only a SERIAL_CLAUSE: the declarations of an enquiry clause are
 *     visible in the clauses that follow it (THEN, DO);
 * <li>no identifier declared in the rest occurs before it or outside the
 *     clause. Then the rest can give its declarations their initial
 *     values itself at its start (GenClauses#prologue), which is the same as
 *     at the start of the clause: nothing before it can see them;
 * <li>with labels: the whole clause (k = 0), or a rest after the last
 *     label. A jump from the rest back to a label leaves the method by an
 *     exception, as a jump out of a loop method does (GenClauses#jump), and
 *     the clause starts again at the label, in the method that has the
 *     labels; a jump into the middle of a method is not possible. A rest
 *     after a label has no declarations (none may follow a labeled unit),
 *     so the identifiers of the clause keep their values across such a
 *     jump, as before.
 * </ul>
 *
 * <p>Where the clauses are cut: {@link Gen#compileClass} measures. In the
 * attempts after the first, each phrase of the methods that are being
 * remedied is marked by a CharacterRange entry ({@link #begin}, {@link #end});
 * in the class file these give the exact size of each phrase in bytes
 * ({@link CodeAttribute#labelToBci}). {@link #plan} then cuts so that each
 * part stays below BUDGET bytes. The entries are debug information that
 * the JVM ignores; the last attempt is generated again without them. Only a
 * method above 65535 bytes cannot be measured (the class cannot be
 * written): it is first divided roughly, in proportion to the number of
 * nodes of its phrases, into parts that can be measured.
 *
 * <p>The environment variable {@code A68J_CUT=every} cuts every serial
 * clause wherever it may, before every phrase and as a whole, a test of the
 * mechanism (tools/cut_test.sh).
 */
final class Cut {
    private Cut() {}

    /** A68J_CUT=every: every serial clause cut before every phrase (a test, see the class comment). */
    static final boolean EVERY = "every".equals(System.getenv("A68J_CUT"));

    /** The most bytes of code a part should have; below Gen.HUGE_METHOD, as calls and parameters add a little. */
    static final int BUDGET = 7000;
    /** The same for the rough division of a method that cannot be measured: well below Gen.MAX_CODE. */
    static final int COARSE_BUDGET = 30000;
    /** What a call of a part costs in the caller, for the plan (the next attempt measures again). */
    static final int CALL = 32;

    /** A part of the serial clause CLAUSE, from phrase FROM on, to be emitted as method METHOD (see Gen#cutJobs). */
    record Job(Gen.Routine routine, Node clause, int from, Moid want, String method, MethodTypeDesc type,
               List<Tag> values, List<Tag> holders, Node root) {}

    /** A phrase measured: phrase INDEX of CLAUSE, in the phrase PARENT (null at the top of its method). */
    static final class Rec {
        final Node clause;
        final int index;
        final Rec parent;
        final Label start;
        final int id;
        int size;

        Rec(Node clause, int index, Rec parent, Label start, int id) {
            this.clause = clause;
            this.index = index;
            this.parent = parent;
            this.start = start;
            this.id = id;
        }
    }

    /** Key of the stack of the phrases being generated, in Gen#methodState. */
    private record Phrases() {}

    private static final Phrases PHRASES = new Phrases();

    // ---- measuring ----------------------------------------------------------

    /** Whether the phrases of the current method are measured (see the class comment). */
    private static boolean measured(Gen g) {
        return g.measure && g.methodRoot != null
                && (g.splitRoots.contains(g.methodRoot) || g.compactRoots.contains(g.methodRoot));
    }

    /** Start phrase I of the serial clause P; null if it is not measured. */
    static Rec begin(Gen g, Node p, int i) {
        if (!measured(g)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Deque<Rec> stack = (Deque<Rec>) g.methodState.computeIfAbsent(PHRASES, k -> new ArrayDeque<Rec>());
        List<Rec> list = g.recs.computeIfAbsent(g.methodName, k -> new ArrayList<>());
        Rec r = new Rec(p, i, stack.peek(), g.cb.newBoundLabel(), list.size());
        list.add(r);
        stack.push(r);
        return r;
    }

    /** End the phrase of R (from #begin): a CharacterRange entry from its start to here, numbered R.id. */
    static void end(Gen g, Rec r) {
        if (r == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Deque<Rec> stack = (Deque<Rec>) g.methodState.get(PHRASES);
        stack.pop();
        Label end = g.cb.newBoundLabel();
        g.cb.characterRange(r.start, end, r.id, r.id, 0);
    }

    // ---- where a clause may be cut --------------------------------------------

    /**
     * The points where the serial clause P (with the phrases PH) is cut in
     * this attempt: those chosen by Gen#compileClass, or with A68J_CUT=every
     * all admissible ones. Empty for every other kind of clause.
     */
    static NavigableSet<Integer> points(Gen g, Node p, List<Node> ph) {
        if (p.attribute != Attribute.SERIAL_CLAUSE) {
            return Collections.emptyNavigableSet();
        }
        if (EVERY) {
            return admissible(g, p, ph);
        }
        TreeSet<Integer> s = g.cuts.get(p);
        return s == null ? Collections.emptyNavigableSet() : s;
    }

    /**
     * The indices k of the phrases of the serial clause P (phrases PH)
     * before which it may be cut (see the class comment): the rest from
     * phrase k on (k = 0: the whole clause) can be a method of its own.
     */
    static NavigableSet<Integer> admissible(Gen g, Node p, List<Node> ph) {
        NavigableSet<Integer> r = g.admissible.get(p);
        if (r == null) {
            r = compute(g, p, ph);
            g.admissible.put(p, r);
        }
        return r;
    }

    private static NavigableSet<Integer> compute(Gen g, Node p, List<Node> ph) {
        TreeSet<Integer> r = new TreeSet<>();
        int n = ph.size();
        Integer hi = g.order.get(lastDescendant(p));
        if (p.attribute != Attribute.SERIAL_CLAUSE || n == 0 || hi == null) {
            return r;
        }
        int lastLabel = -1;
        for (int i = 0; i < n; i++) {
            if (ph.get(i).attribute == Attribute.LABELED_UNIT) {
                lastLabel = i;
            }
        }
        // From the end: the first and last use of the identifiers declared
        // in the phrases from k on, and whether there are declarations.
        int minFirst = Integer.MAX_VALUE;
        int maxLast = Integer.MIN_VALUE;
        boolean declarations = false;
        for (int k = n - 1; k >= 0; k--) {
            Node q = ph.get(k);
            List<Tag> defs = new ArrayList<>();
            definitions(q, defs);
            for (Tag t : defs) {
                Integer f = g.firstUse.get(t);
                Integer l = g.lastUse.get(t);
                if (f != null) {
                    minFirst = Math.min(minFirst, f);
                    maxLast = Math.max(maxLast, l);
                }
            }
            declarations |= GenClauses.isDeclaration(q);
            Integer lo = g.order.get(q);
            if (lo == null || minFirst < lo || maxLast > hi || q.attribute == Attribute.EXIT_SYMBOL) {
                continue;
            }
            if (k == 0) {
                // A serial clause with labels evaluated with a non-empty
                // stack is a method of its own already (GenClauses#serial).
                if (!g.routineOf.containsKey(p)) {
                    r.add(0);
                }
            } else if (lastLabel < 0 || k > lastLabel && !declarations) {
                // (A rest after a label has no declarations anyway: none may follow a labeled unit.)
                r.add(k);
            }
        }
        return r;
    }

    /** The tags declared in the sub-tree of Q (and by Q itself). */
    private static void definitions(Node q, List<Tag> out) {
        if (q.tag != null && (q.attribute == Attribute.DEFINING_IDENTIFIER
                || q.attribute == Attribute.DEFINING_OPERATOR || q.attribute == Attribute.DEFINING_INDICANT)) {
            out.add(q.tag);
        }
        for (Node s = q.sub; s != null; s = s.next) {
            definitions(s, out);
        }
    }

    /** The last node of the sub-tree of P in pre-order (Gen#order). */
    private static Node lastDescendant(Node p) {
        Node x = p;
        while (x.sub != null) {
            x = x.sub;
            while (x.next != null) {
                x = x.next;
            }
        }
        return x;
    }

    // ---- the call -------------------------------------------------------------

    /**
     * Compile the serial clause P as a call of a method of its own if it is
     * to be cut before its first phrase (#points); returns false (emitting
     * nothing) otherwise.
     */
    static boolean whole(Gen g, Node p, Moid want) {
        if (p.attribute != Attribute.SERIAL_CLAUSE || !EVERY && !g.cuts.containsKey(p)) {
            return false;
        }
        List<Node> ph = new ArrayList<>();
        GenClauses.phrases(p, ph);
        if (!points(g, p, ph).contains(0)) {
            return false;
        }
        Set<Tag> defined = Collections.newSetFromMap(new IdentityHashMap<>());
        Outline.definitions(p, defined);
        List<Tag> used = new ArrayList<>();
        Set<Tag> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Tag> assigned = Collections.newSetFromMap(new IdentityHashMap<>());
        if (!Outline.uses(g, p, defined, seen, used, assigned)) {
            return false;
        }
        return call(g, p, 0, want, ph.get(0), used, assigned);
    }

    /**
     * Compile the phrases of the serial clause P (phrases PH) from phrase K
     * on as a call of a method of its own, which yields the value of the
     * clause (mode WANT); returns false (emitting nothing) if it cannot be.
     */
    static boolean rest(Gen g, Node p, List<Node> ph, int k, Moid want) {
        Set<Tag> defined = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = k; i < ph.size(); i++) {
            Outline.definitions(ph.get(i), defined);
        }
        List<Tag> used = new ArrayList<>();
        Set<Tag> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Tag> assigned = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = k; i < ph.size(); i++) {
            if (!Outline.usesOne(g, ph.get(i), defined, seen, used, assigned)) {
                return false;
            }
        }
        return call(g, p, k, want, ph.get(k), used, assigned);
    }

    /**
     * The call of the part of P from phrase FROM on, whose method is made
     * from ROOT (its first phrase), with the tags USED as parameters and
     * those ASSIGNED in the holder (as Outline#loop does).
     */
    private static boolean call(Gen g, Node p, int from, Moid want, Node root, List<Tag> used, Set<Tag> assigned) {
        List<Tag> values = new ArrayList<>();
        List<Tag> holders = new ArrayList<>();
        List<ClassDesc> ps = new ArrayList<>();
        ps.add(Gen.CD_FRAME);
        int slots = 1;
        for (Tag t : used) {
            if (assigned.contains(t)) {
                continue;
            }
            values.add(t);
            ps.add(Outline.paramDesc(g, t));
            slots += Outline.localType(g, t).kind.slotSize();
        }
        for (Tag t : used) {
            if (assigned.contains(t)) {
                if (Outline.localType(g, t).kind == TypeKind.REFERENCE) {
                    return false;  // direct variables are primitive; be safe
                }
                holders.add(t);
            }
        }
        if (!holders.isEmpty()) {
            ps.add(CD_long.arrayType());
            slots++;
        }
        if (slots > Outline.MAX_SLOTS) {
            return false;
        }
        // The part inherits what the method it comes from is: code that
        // runs once, compact, with its loops as methods of their own.
        if (g.runsOnce()) {
            g.onceRoots.add(root);
        }
        if (g.methodRoot != null && g.compactRoots.contains(g.methodRoot)) {
            g.compactRoots.add(root);
        }
        if (g.methodRoot != null && g.splitRoots.contains(g.methodRoot)) {
            g.splitRoots.add(root);
        }
        String name = "part" + g.cutCount++;
        MethodTypeDesc type = MethodTypeDesc.of(JType.of(g.a68, want).desc, ps);
        g.cutJobs.add(new Job(g.cur, p, from, want, name, type, values, holders, root));
        g.partPhrases.put(root, new Object[] {p, from});
        Outline.call(g, name, type, values, holders);
        return true;
    }

    /** Emit the method of JOB (called from Gen#compile after the routines). */
    static void emit(Gen g, ClassBuilder clb, Job job) {
        clb.withMethodBody(job.method, job.type, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, code -> {
            CodeBuilder saveCb = g.cb;
            Gen.Routine saveCur = g.cur;
            int saveFrame = g.frameLocal;
            int saveDepth = g.loopDepth;
            int saveLabels = g.labelDepth;
            Node saveRoot = g.methodRoot;
            int saveBase = g.methodLoopBase;
            String saveName = g.methodName;
            Map<Tag, Integer> saveLocals = new IdentityHashMap<>(g.localSlot);
            Map<Object, Object> saveState = new HashMap<>(g.methodState);
            g.cb = code;
            g.cur = job.routine;
            g.frameLocal = 0;
            g.loopDepth = 0;
            g.labelDepth = 0;
            g.localSlot.clear();
            g.methodState.clear();
            int slot = 1;
            for (Tag t : job.values) {
                g.localSlot.put(t, slot);
                slot += Outline.localType(g, t).kind.slotSize();
            }
            int holder = job.holders.isEmpty() ? -1 : slot++;
            for (Tag t : job.holders) {
                g.localSlot.put(t, code.allocateLocal(Outline.localType(g, t).kind));
            }
            if (holder >= 0) {
                Outline.load(g, job.holders, holder);
            }
            Label start = code.newLabel();
            Label end = code.newLabel();
            code.labelBinding(start);
            g.methodRoot = job.root;
            g.methodLoopBase = 0;
            g.methodRoots.put(job.method, job.root);
            g.methodName = job.method;
            if (job.from == 0) {
                g.inWhole = job.clause;
                GenClauses.serial(g, job.clause, job.want);
            } else {
                List<Node> ph = new ArrayList<>();
                GenClauses.phrases(job.clause, ph);
                boolean withoutLabels = true;
                for (Node q : ph) {
                    withoutLabels &= q.attribute != Attribute.LABELED_UNIT;
                }
                NavigableSet<Integer> points = points(g, job.clause, ph);
                GenClauses.prologue(g, ph, job.from, points, withoutLabels);
                GenClauses.phrasesFrom(g, job.clause, ph, job.from, job.want, points);
            }
            code.labelBinding(end);
            if (holder >= 0) {
                Outline.store(g, job.holders, holder);
            }
            code.return_(JType.of(g.a68, job.want).kind);
            if (!job.holders.isEmpty()) {
                Label handler = code.newLabel();
                code.labelBinding(handler);
                Outline.store(g, job.holders, holder);
                code.athrow();
                code.exceptionCatchAll(start, end, handler);
            }
            g.cb = saveCb;
            g.cur = saveCur;
            g.frameLocal = saveFrame;
            g.loopDepth = saveDepth;
            g.labelDepth = saveLabels;
            g.methodRoot = saveRoot;
            g.methodLoopBase = saveBase;
            g.methodName = saveName;
            g.localSlot.clear();
            g.localSlot.putAll(saveLocals);
            g.methodState.clear();
            g.methodState.putAll(saveState);
        });
    }

    // ---- the plan ---------------------------------------------------------------

    /**
     * Choose cuts (into CUTS) for the method METHOD of the attempt G, which
     * has SIZE bytes of code: CODE is its code attribute with the
     * CharacterRange entries of its phrases (#end), or null if the method
     * was too large to be written (then the sizes of the phrases are taken
     * in proportion to their number of nodes, a rough division into parts
     * that the next attempt can measure). Returns whether a new cut was
     * chosen.
     *
     * <p>Bottom up, each serial clause whose phrases (what remains of them
     * after the cuts inside them) take more than the budget is cut into
     * rests of at most the budget, where it may be. If the method is still
     * larger than the budget (one phrase too large, such as a case clause
     * with many branches), the largest clauses inside it that fit into the
     * budget become methods as a whole, until it fits.
     */
    static boolean plan(Gen g, String method, CodeAttribute code, int size, Map<Node, TreeSet<Integer>> cuts) {
        List<Rec> rs = g.recs.get(method);
        if (rs == null || rs.isEmpty()) {
            return false;
        }
        int budget;
        if (code != null) {
            for (CodeElement e : code) {
                if (e instanceof CharacterRange cr && cr.characterRangeStart() < rs.size()) {
                    rs.get(cr.characterRangeStart()).size =
                            code.labelToBci(cr.endScope()) - code.labelToBci(cr.startScope());
                }
            }
            budget = BUDGET;
        } else {
            long total = 0;
            Map<Rec, Long> nodes = new IdentityHashMap<>();
            for (Rec r : rs) {
                long n = nodes(g, phrase(r));
                nodes.put(r, n);
                if (r.parent == null) {
                    total += n;
                }
            }
            for (Rec r : rs) {
                r.size = total == 0 ? 0 : (int) (size * nodes.get(r) / total);
            }
            budget = COARSE_BUDGET;
        }
        return new Planner(g, rs, budget, cuts).run(size);
    }

    /** The phrase node of R. */
    private static Node phrase(Rec r) {
        List<Node> ph = new ArrayList<>();
        GenClauses.phrases(r.clause, ph);
        return ph.get(r.index);
    }

    /** The number of nodes of the sub-tree of P, without those compiled as other methods. */
    private static long nodes(Gen g, Node p) {
        long n = 1;
        for (Node q = p.sub; q != null; q = q.next) {
            if (!g.routineOf.containsKey(q)) {
                n += nodes(g, q);
            }
        }
        return n;
    }

    /** The plan for one method (see #plan). */
    private static final class Planner {
        final Gen g;
        final int budget;
        final Map<Node, TreeSet<Integer>> cuts;
        /** The phrases of each clause in the method, in order. */
        final Map<Node, List<Rec>> byClause = new LinkedHashMap<>();
        /** The clauses directly inside each phrase. */
        final Map<Rec, List<Node>> children = new IdentityHashMap<>();
        final List<Node> tops = new ArrayList<>();
        /** The size each clause leaves in the method after its cuts. */
        final Map<Node, Long> resid = new IdentityHashMap<>();
        /** The first point of each clause cut in this plan. */
        final Map<Node, Integer> firstCut = new IdentityHashMap<>();
        boolean added;

        Planner(Gen g, List<Rec> rs, int budget, Map<Node, TreeSet<Integer>> cuts) {
            this.g = g;
            this.budget = budget;
            this.cuts = cuts;
            Map<Node, List<Rec>> m = new IdentityHashMap<>();
            for (Rec r : rs) {
                List<Rec> l = m.get(r.clause);
                if (l == null) {
                    l = new ArrayList<>();
                    m.put(r.clause, l);
                    // The clauses in the order of their first phrase.
                    byClause.put(r.clause, l);
                    if (r.parent == null) {
                        tops.add(r.clause);
                    } else {
                        children.computeIfAbsent(r.parent, k -> new ArrayList<>()).add(r.clause);
                    }
                }
                l.add(r);
            }
        }

        boolean run(int size) {
            long rest = size;
            for (Node c : tops) {
                rest -= full(c) - resid(c);
            }
            if (rest > budget) {
                whole(rest);
            }
            return added;
        }

        long full(Node c) {
            long s = 0;
            for (Rec r : byClause.get(c)) {
                s += r.size;
            }
            return s;
        }

        /** What the phrase R leaves in the method: its size less what the cuts inside it move out. */
        long left(Rec r) {
            long s = r.size;
            for (Node c : children.getOrDefault(r, List.of())) {
                s -= full(c) - resid(c);
            }
            return s;
        }

        long resid(Node c) {
            Long known = resid.get(c);
            if (known != null) {
                return known;
            }
            List<Rec> rs = byClause.get(c);
            long[] r = new long[rs.size()];
            long total = 0;
            for (int j = 0; j < rs.size(); j++) {
                r[j] = left(rs.get(j));
                total += r[j];
            }
            long result = total;
            if (total > budget) {
                List<Node> ph = new ArrayList<>();
                GenClauses.phrases(c, ph);
                NavigableSet<Integer> adm = admissible(g, c, ph);
                long seg = 0;
                long head = -1;
                for (int j = 0; j < rs.size(); j++) {
                    int k = rs.get(j).index;
                    if (j > 0 && seg > 0 && seg + r[j] > budget && adm.contains(k)) {
                        if (cuts.computeIfAbsent(c, x -> new TreeSet<>()).add(k)) {
                            added = true;
                        }
                        firstCut.merge(c, k, Math::min);
                        if (head < 0) {
                            head = seg;
                        }
                        seg = 0;
                    }
                    seg += r[j];
                }
                result = head < 0 ? total : head + CALL;
            }
            resid.put(c, result);
            return result;
        }

        /** Whether the phrase R moves out of the method with a rest cut in this plan. */
        boolean moved(Rec r) {
            for (; r != null; r = r.parent) {
                Integer k = firstCut.get(r.clause);
                if (k != null && r.index >= k) {
                    return true;
                }
            }
            return false;
        }

        /** Make whole clauses methods of their own, the largest first, until REST fits into the budget. */
        void whole(long rest) {
            List<Node> candidates = new ArrayList<>();
            for (Node c : byClause.keySet()) {
                Rec first = byClause.get(c).get(0);
                long r = resid(c);
                if (first.index == 0 && !moved(first) && r > CALL && r <= budget) {
                    List<Node> ph = new ArrayList<>();
                    GenClauses.phrases(c, ph);
                    if (admissible(g, c, ph).contains(0)) {
                        candidates.add(c);
                    }
                }
            }
            candidates.sort((a, b) -> Long.compare(resid(b), resid(a)));
            List<Node> chosen = new ArrayList<>();
            for (Node c : candidates) {
                if (rest <= budget) {
                    break;
                }
                boolean nested = false;
                for (Node d : chosen) {
                    nested |= inside(c, d) || inside(d, c);
                }
                if (nested) {
                    continue;
                }
                if (cuts.computeIfAbsent(c, x -> new TreeSet<>()).add(0)) {
                    added = true;
                }
                chosen.add(c);
                rest -= resid(c) - CALL;
            }
        }

        /** Whether the clause C is inside the clause D (in this method). */
        boolean inside(Node c, Node d) {
            for (Rec r = byClause.get(c).get(0).parent; r != null; r = r.parent) {
                if (r.clause == d) {
                    return true;
                }
            }
            return false;
        }
    }
}
