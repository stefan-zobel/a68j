// a68j JVM backend: loops compiled as methods of their own.
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.Attribute;
import a68j.front.Node;
import a68j.front.Tag;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loop methods: a loop clause compiled as a private static method
 * {@code loopN} of its own and called where it stood (docs/optimizations.md,
 * section 7).
 *
 * <p>Why: HotSpot does not compile a method with more than 8000 bytes of
 * bytecode at all (-XX:HugeMethodLimit, -XX:+DontCompileHugeMethods), so a
 * long main program or procedure with its hot loops inside would run in
 * the interpreter for ever. And the particular program is one method,
 * {@code program()}, that runs once: HotSpot compiles its loops by
 * on-stack replacement (OSR), each time the whole method, with no profile
 * for the code after the hot loop yet (docs/optimizations.md, section 3).
 *
 * <p>Which loops:
 * <ul>
 * <li>every loop of the particular program, nested ones too: an inner
 *     loop becomes a method that the method of the outer loop calls. A hot
 *     inner loop then gets an ordinary compilation with a full profile
 *     instead of an OSR compilation entered in the middle of the outer
 *     loop, which C2 handles worse (proper-divisors-2: 27 % faster than
 *     with only the outermost loops as methods, docs/optimizations.md,
 *     section 7);
 * <li>the outermost loops of a method that turned out to be larger than
 *     8000 bytes: {@link Gen#compileClass} measures the generated methods
 *     and generates the class again with such methods in
 *     {@link Gen#splitRoots}. For a loop method that is too large these are
 *     the loops directly inside its loop.
 * </ul>
 * The environment variable {@code A68J_LOOP_METHODS} selects another
 * choice for the first rule, for experiments: {@code none} (also turns the
 * second rule off), {@code outer} (only the loops of the particular program
 * that are not inside another loop of it) or {@code all} (the default); {@code every} makes every
 * loop of every method a method of its own (a test of the mechanism, see
 * tools/loop_methods_test.sh). The option
 * -fno-loop-methods turns both rules off.
 *
 * <p>How: the loop is compiled exactly as before, in the same routine
 * ({@link Gen#cur} is unchanged, so frames, jumps and labels work as in the
 * enclosing method); only the JVM locals differ. The method gets as
 * parameters
 * <ul>
 * <li>the frame of the enclosing method ({@link Gen#frameLocal}), and
 * <li>the value of each JVM local the loop uses: every identifier (or
 *     operator, ...) declared outside the loop that has a local
 *     ({@link Gen#localSlot}). Identities and the names of variables never
 *     change, so a copy is enough.
 * </ul>
 * The exception are direct variables ({@link Direct}), whose value is kept
 * in a local and which the loop may assign. The values of those that the
 * loop assigns are passed together in one {@code long[]} (the holder; a
 * float or double travels as its raw bits): the method copies them into
 * locals of its own on entry and back into the holder when it ends,
 * normally or by an exception (a jump to a label outside the loop, see
 * GenClauses#jump, or a runtime error), and the caller copies them back
 * into its locals in both cases too. One array rather than one per
 * variable: the holder stays live during the whole loop, and with one
 * array per variable these references took registers the loop needed
 * (docs/optimizations.md, section 7).
 */
final class Outline {
    private Outline() {}

    /** A loop to be emitted as method METHOD of type TYPE. */
    record Job(Gen.Routine routine, Node loop, String method, MethodTypeDesc type, List<Tag> values, List<Tag> holders) {}

    /** At most this many parameter slots (the JVM allows 255). */
    static final int MAX_SLOTS = 200;

    static final String MODE = mode();

    /** A68J_DEBUG_LOOPS: report the methods that were too large (Gen#compileClass). */
    static final boolean DEBUG = System.getenv("A68J_DEBUG_LOOPS") != null;

    private static String mode() {
        String m = System.getenv("A68J_LOOP_METHODS");
        return m == null || m.isEmpty() ? "all" : m;
    }

    /**
     * Compile the loop clause P as a call of a method of its own if it is
     * one of the loops this is done for (see the class comment); returns
     * false (emitting nothing) otherwise.
     */
    static boolean loop(Gen g, Node p) {
        if (!g.loopMethods || MODE.equals("none")) {
            return false;
        }
        // A loop of the particular program (see above), or an outermost
        // loop of a method that was too large to be compiled by the JIT.
        boolean program = g.cur.node == g.a68.job.topNode && (MODE.equals("all") || g.loopDepth == 0);
        boolean split = (MODE.equals("every") || g.splitRoots.contains(g.methodRoot)) && g.loopDepth == g.methodLoopBase;
        if (!program && !split) {
            return false;
        }
        // The tags the loop uses that have a JVM local in this method.
        Set<Tag> defined = Collections.newSetFromMap(new IdentityHashMap<>());
        definitions(p, defined);
        List<Tag> used = new ArrayList<>();
        Set<Tag> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Tag> assigned = Collections.newSetFromMap(new IdentityHashMap<>());
        if (!uses(g, p, defined, seen, used, assigned)) {
            return false;
        }
        List<Tag> values = new ArrayList<>();
        List<Tag> holders = new ArrayList<>();
        List<ClassDesc> ps = new ArrayList<>();
        ps.add(Gen.CD_FRAME);
        int slots = 1;
        for (Tag t : used) {
            if (assigned.contains(t)) {
                continue;
            }
            JType jt = localType(g, t);
            values.add(t);
            ps.add(paramDesc(g, t));
            slots += jt.kind.slotSize();
        }
        for (Tag t : used) {
            if (assigned.contains(t)) {
                if (localType(g, t).kind == TypeKind.REFERENCE) {
                    return false;  // direct variables are primitive; be safe
                }
                holders.add(t);
            }
        }
        if (!holders.isEmpty()) {
            ps.add(CD_long.arrayType());
            slots++;
        }
        if (slots > MAX_SLOTS) {
            return false;
        }
        String name = "loop" + g.loopCount++;
        MethodTypeDesc type = MethodTypeDesc.of(CD_void, ps);
        g.loopJobs.add(new Job(g.cur, p, name, type, values, holders));
        call(g, name, type, values, holders);
        return true;
    }

    /**
     * The declared type of the parameter for the value of TAG. For an own
     * variable (Gen#ownCell) it is the exact, final class of its location,
     * not Ref: C2 trusts the types of a method's parameters, so the
     * checkcast in each access of the variable folds away. In the main
     * program C2 knew that already from the type flow from `new Cell`
     * on; with Ref the loop method paid a type check and a reload of the
     * location in every access (docs/optimizations.md, section 7).
     */
    static ClassDesc paramDesc(Gen g, Tag t) {
        ClassDesc c = g.ownCell(t);
        return c != null ? c : localType(g, t).desc;
    }

    /** The type of the JVM local of TAG (for a direct variable: the value, not the name). */
    static JType localType(Gen g, Tag t) {
        return g.direct.contains(t) ? JType.of(g.a68, JType.base(t.type).sub) : JType.of(g.a68, t.type);
    }

    /** The call: holder filled, the method invoked, the values copied back (also on an exception). */
    static void call(Gen g, String name, MethodTypeDesc type, List<Tag> values, List<Tag> holders) {
        CodeBuilder cb = g.cb;
        int holder = -1;
        if (!holders.isEmpty()) {
            Gen.constant(cb, holders.size());
            cb.newarray(TypeKind.LONG);
            holder = cb.allocateLocal(TypeKind.REFERENCE);
            cb.astore(holder);
            store(g, holders, holder);
        }
        cb.aload(g.frameLocal);
        for (Tag t : values) {
            JType jt = localType(g, t);
            cb.loadLocal(jt.kind, g.localSlot.get(t));
            ClassDesc c = paramDesc(g, t);
            if (!c.equals(jt.desc)) {
                cb.checkcast(c);  // the local is declared as Ref; null passes
            }
        }
        if (holder >= 0) {
            cb.aload(holder);
        }
        if (holders.isEmpty()) {
            cb.invokestatic(g.thisClass, name, type);
            return;
        }
        Label start = cb.newLabel();
        Label end = cb.newLabel();
        Label handler = cb.newLabel();
        Label done = cb.newLabel();
        cb.labelBinding(start);
        cb.invokestatic(g.thisClass, name, type);
        cb.labelBinding(end);
        load(g, holders, holder);
        cb.goto_(done);
        cb.labelBinding(handler);
        load(g, holders, holder);
        cb.athrow();
        cb.exceptionCatchAll(start, end, handler);
        cb.labelBinding(done);
    }

    /** Copy the elements of the holder in local HOLDER into the locals of HOLDERS. */
    static void load(Gen g, List<Tag> holders, int holder) {
        CodeBuilder cb = g.cb;
        for (int i = 0; i < holders.size(); i++) {
            Tag t = holders.get(i);
            TypeKind k = localType(g, t).kind;
            cb.aload(holder);
            Gen.constant(cb, i);
            cb.laload();
            fromLong(cb, k);
            cb.storeLocal(k, g.localSlot.get(t));
        }
    }

    /** Copy the locals of HOLDERS into the holder in local HOLDER. */
    static void store(Gen g, List<Tag> holders, int holder) {
        CodeBuilder cb = g.cb;
        for (int i = 0; i < holders.size(); i++) {
            Tag t = holders.get(i);
            TypeKind k = localType(g, t).kind;
            cb.aload(holder);
            Gen.constant(cb, i);
            cb.loadLocal(k, g.localSlot.get(t));
            toLong(cb, k);
            cb.lastore();
        }
    }

    private static final ClassDesc CD_FLOAT_BOX = ClassDesc.of("java.lang.Float");
    private static final ClassDesc CD_DOUBLE_BOX = ClassDesc.of("java.lang.Double");

    /** Convert the value of kind K on the stack to a long (raw bits for float and double). */
    private static void toLong(CodeBuilder cb, TypeKind k) {
        switch (k) {
            case LONG -> { }
            case FLOAT -> {
                cb.invokestatic(CD_FLOAT_BOX, "floatToRawIntBits", MethodTypeDesc.of(CD_int, CD_float));
                cb.i2l();
            }
            case DOUBLE -> cb.invokestatic(CD_DOUBLE_BOX, "doubleToRawLongBits", MethodTypeDesc.of(CD_long, CD_double));
            default -> cb.i2l();  // int, boolean, char, byte, short
        }
    }

    /** The inverse of {@link #toLong}. */
    private static void fromLong(CodeBuilder cb, TypeKind k) {
        switch (k) {
            case LONG -> { }
            case FLOAT -> {
                cb.l2i();
                cb.invokestatic(CD_FLOAT_BOX, "intBitsToFloat", MethodTypeDesc.of(CD_float, CD_int));
            }
            case DOUBLE -> cb.invokestatic(CD_DOUBLE_BOX, "longBitsToDouble", MethodTypeDesc.of(CD_double, CD_long));
            default -> cb.l2i();
        }
    }

    /** Emit the method of JOB (called from Gen#compile after the routines). */
    static void emit(Gen g, ClassBuilder clb, Job job) {
        clb.withMethodBody(job.method, job.type, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, code -> {
            CodeBuilder saveCb = g.cb;
            Gen.Routine saveCur = g.cur;
            int saveFrame = g.frameLocal;
            int saveDepth = g.loopDepth;
            Map<Tag, Integer> saveLocals = new IdentityHashMap<>(g.localSlot);
            Map<Object, Object> saveState = new HashMap<>(g.methodState);
            g.cb = code;
            g.cur = job.routine;
            g.frameLocal = 0;
            g.loopDepth = 0;
            g.localSlot.clear();
            g.methodState.clear();
            int slot = 1;
            for (Tag t : job.values) {
                g.localSlot.put(t, slot);
                slot += localType(g, t).kind.slotSize();
            }
            int holder = job.holders.isEmpty() ? -1 : slot++;
            for (Tag t : job.holders) {
                g.localSlot.put(t, code.allocateLocal(localType(g, t).kind));
            }
            if (holder >= 0) {
                load(g, job.holders, holder);
            }
            Label start = code.newLabel();
            Label end = code.newLabel();
            code.labelBinding(start);
            Node saveRoot = g.methodRoot;
            int saveBase = g.methodLoopBase;
            g.methodRoot = job.loop;
            g.methodLoopBase = 1;
            g.methodRoots.put(job.method, job.loop);
            String saveName = g.methodName;
            g.methodName = job.method;
            GenClauses.loopInline(g, job.loop);
            g.methodRoot = saveRoot;
            g.methodLoopBase = saveBase;
            g.methodName = saveName;
            code.labelBinding(end);
            if (holder >= 0) {
                store(g, job.holders, holder);
            }
            code.return_();
            if (!job.holders.isEmpty()) {
                Label handler = code.newLabel();
                code.labelBinding(handler);
                store(g, job.holders, holder);
                code.athrow();
                code.exceptionCatchAll(start, end, handler);
            }
            g.cb = saveCb;
            g.cur = saveCur;
            g.frameLocal = saveFrame;
            g.loopDepth = saveDepth;
            g.localSlot.clear();
            g.localSlot.putAll(saveLocals);
            g.methodState.clear();
            g.methodState.putAll(saveState);
        });
    }

    /** The tags declared inside P (identifiers, operators, labels, the FOR identifier). */
    static void definitions(Node p, Set<Tag> out) {
        for (Node q = p.sub; q != null; q = q.next) {
            if (q.tag != null && (q.attribute == Attribute.DEFINING_IDENTIFIER
                    || q.attribute == Attribute.DEFINING_OPERATOR || q.attribute == Attribute.DEFINING_INDICANT)) {
                out.add(q.tag);
            }
            definitions(q, out);
        }
    }

    /**
     * Collect the tags used in P (not in routine texts and other parts
     * compiled as methods of their own) that are declared outside P and
     * have a JVM local here, in order of first use; and the direct
     * variables among them that P assigns. Returns false if the loop cannot
     * be outlined: it uses a direct variable that has no local yet (one
     * declared after the loop, used before its declaration).
     */
    static boolean uses(Gen g, Node p, Set<Tag> defined, Set<Tag> seen, List<Tag> used, Set<Tag> assigned) {
        for (Node q = p.sub; q != null; q = q.next) {
            if (!usesOne(g, q, defined, seen, used, assigned)) {
                return false;
            }
        }
        return true;
    }

    /** {@link #uses} for the node Q and its sub-tree. */
    static boolean usesOne(Gen g, Node q, Set<Tag> defined, Set<Tag> seen, List<Tag> used, Set<Tag> assigned) {
        if (g.routineOf.containsKey(q)) {
            return true;
        }
        Tag t = q.tag;
        if (t != null && !defined.contains(t)) {
            if (g.localSlot.containsKey(t)) {
                if (seen.add(t)) {
                    used.add(t);
                }
                if (g.direct.contains(t) && q.attribute == Attribute.IDENTIFIER) {
                    // Not below a DEREFERENCING (see below): an assignment.
                    assigned.add(t);
                }
            } else if (g.direct.contains(t)) {
                return false;
            }
        }
        if (q.attribute == Attribute.DEREFERENCING && dereferencedIdentifier(q) != null) {
            // The value of a variable: for a direct one, a read of its local.
            Node id = dereferencedIdentifier(q);
            Tag x = id.tag;
            if (x != null && !defined.contains(x)) {
                if (g.localSlot.containsKey(x)) {
                    if (seen.add(x)) {
                        used.add(x);
                    }
                } else if (g.direct.contains(x)) {
                    return false;
                }
            }
            return true;
        }
        return uses(g, q, defined, seen, used, assigned);
    }

    /** The identifier that the DEREFERENCING P dereferences directly, or null. */
    private static Node dereferencedIdentifier(Node p) {
        Node s = GenUnits.strip(p.sub);
        return s != null && s.attribute == Attribute.IDENTIFIER ? s : null;
    }
}
