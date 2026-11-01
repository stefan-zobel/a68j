// a68j JVM backend: code generator state and dispatch (docs/BACKEND.md section 5).
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.A68;
import a68j.front.Attribute;
import a68j.front.Moid;
import a68j.front.Node;
import a68j.front.Table;
import a68j.front.Tag;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Code generator: compiles the tree of a particular program (after semantic
 * analysis) into one class with a {@code main} method and one static method
 * per routine text.
 */
public final class Gen {
    public static final ClassDesc CD_REF = ClassDesc.of("a68j.rt.Ref");
    public static final ClassDesc CD_CELL = ClassDesc.of("a68j.rt.Cell");
    public static final ClassDesc CD_ROW = ClassDesc.of("a68j.rt.Row");
    public static final ClassDesc CD_STRUCT = ClassDesc.of("a68j.rt.Struct");
    public static final ClassDesc CD_UNION = ClassDesc.of("a68j.rt.Union");
    public static final ClassDesc CD_PROC = ClassDesc.of("a68j.rt.Proc");
    public static final ClassDesc CD_CLOSURE = ClassDesc.of("a68j.rt.Closure");
    public static final ClassDesc CD_FRAME = ClassDesc.of("a68j.rt.Frame");
    public static final ClassDesc CD_RT = ClassDesc.of("a68j.rt.Rt");
    public static final ClassDesc CD_STD = ClassDesc.of("a68j.rt.Std");
    public static final ClassDesc CD_POSIX = ClassDesc.of("a68j.rt.Posix");
    public static final ClassDesc CD_OBJECTS = CD_Object.arrayType();
    /** Type of every routine method: (Frame env, Object[] args) -> Object. */
    public static final MethodTypeDesc MT_ROUTINE = MethodTypeDesc.of(CD_Object, CD_FRAME, CD_OBJECTS);

    /** Thrown for constructs the backend does not support yet. */
    public static final class Unsupported extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Unsupported(Node p, String what) {
            super(where(p) + ": a68j backend: " + what + " not supported yet");
        }
    }

    /**
     * A fatal error of ga68's lowering pass (fatal_error in a68-low*.cc):
     * the message is complete, as GCC prints it.
     */
    public static final class Fatal extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Fatal(Node p, String msg) {
            super(a68j.front.Location.of(p) + ": fatal error: " + msg);
        }
    }

    /**
     * As a68_lower_unimplemented: a use of a standard identifier or operator
     * that ga68 declares without a lowering routine (Tag#unimplemented) is
     * a fatal error. Checked in tree order before the code is generated.
     */
    private void checkImplemented(Node p) {
        for (; p != null; p = p.next) {
            if ((p.attribute == Attribute.IDENTIFIER || p.attribute == Attribute.OPERATOR) && p.tag != null
                    && p.tag.unimplemented && !(a68.options().parThreads && isSemaOperator(p.tag))) {
                throw new Fatal(p, "no lowering routine installed for construct.  jemarch has been lazy");
            }
            checkImplemented(p.sub);
        }
    }

    /** Whether T is one of the SEMA operators LEVEL, UP and DOWN (available with -fpar-threads). */
    private boolean isSemaOperator(a68j.front.Tag t) {
        Moid m = t.type;
        if (m == null || m.attribute != Attribute.PROC_SYMBOL) {
            return false;
        }
        if (m.sub == a68.modes.SEMA) {
            return true;
        }
        for (a68j.front.Pack z = m.pack; z != null; z = z.next) {
            if (z.type == a68.modes.SEMA) {
                return true;
            }
        }
        return false;
    }

    /** A routine text, or the particular program (node == top). */
    public static final class Routine {
        public final int id;
        public final Node node;
        public final Routine parent;
        public final String method;
        /** Frame slots of the captured identifiers declared in this routine. */
        public final Map<Tag, Integer> frameSlots = new IdentityHashMap<>();
        boolean scheduled;
        /** For a pseudo-routine (a serial clause with labels, see Capture): the mode of its value. */
        public Moid want;
        /** Whether labels are declared in this routine itself (not in a pseudo-routine). */
        boolean ownsLabels;
        /** For a unit of a parallel clause with -fpar-threads: a method run in its own thread. */
        boolean parUnit;

        /**
         * Whether an invocation of this routine makes its own frame. A routine
         * text without captured identifiers and labels does not: it uses the
         * frame it was called with (its environment), see {@link Gen#loadFrame}.
         */
        boolean hasFrame() {
            return node.attribute != Attribute.ROUTINE_TEXT || !frameSlots.isEmpty() || ownsLabels;
        }

        Routine(int id, Node node, Routine parent, String method) {
            this.id = id;
            this.node = node;
            this.parent = parent;
            this.method = method;
        }

        int depthFrom(Routine inner) {
            int d = 0;
            for (Routine r = inner; r != this; r = r.parent) {
                if (r == null) {
                    throw new IllegalStateException("routine not enclosing");
                }
                d++;
            }
            return d;
        }
    }

    public final A68 a68;
    public final ClassDesc thisClass;
    public final String sourceFile;

    // Capture analysis results.
    final Map<Table, Routine> tableOwner = new IdentityHashMap<>();
    final Map<Node, Routine> routineOf = new IdentityHashMap<>();
    /**
     * The routine texts of the procedures and brief operators declared in
     * this compilation (PROC f = ..., OP X = (...) ...: ...). They are
     * elaborated when their range is entered and cannot change, so a call of
     * such an identifier or operator invokes the routine's method directly.
     */
    final Map<Tag, Node> staticProcs = new IdentityHashMap<>();

    /**
     * Routine texts passed directly to a Java method as an object of a
     * functional interface (GenJava#typedCallback): they get a typed entry.
     */
    final java.util.Set<Node> typedTexts = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Identifiers (and operators) declared with a Java formal hole of a
     * procedure mode ({@code proc (int) int f = nest Java "..."}): tag ->
     * FORMAL_HOLE. They cannot change either, so a call of one invokes the
     * Java method directly (GenJava#directCall).
     */
    final Map<Tag, Node> javaProcs = new IdentityHashMap<>();
    /**
     * Identities and parameters of a row, structure or union mode: they hold
     * values that no name refers to (they got a copy, or a value that is not
     * reachable from a name either) and that nothing can change. A value
     * taken from them (GenUnits#ownedValue) needs no copy when it goes into
     * another such holder.
     */
    final java.util.Set<Tag> valueConsts = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /** The Java method of each Java formal hole of a procedure mode, once resolved. */
    final Map<Node, java.lang.reflect.Method> javaMethods = new IdentityHashMap<>();
    final Deque<Routine> work = new ArrayDeque<>();
    /** Loops to be emitted as methods of their own (see {@link Outline}). */
    final Deque<Outline.Job> loopJobs = new ArrayDeque<>();
    /** Number of loop methods so far (their names are loop0, loop1, ...). */
    int loopCount;
    /** Whether loops may be compiled as methods of their own (option -fno-loop-methods turns it off). */
    public boolean loopMethods = true;
    final List<Routine> routines = new ArrayList<>();

    // Per-method state.
    public CodeBuilder cb;
    public Routine cur;
    /** JVM local slot of the current frame. */
    public int frameLocal;
    /** Number of loop clauses around the code being generated, in the current method. */
    int loopDepth;
    /**
     * The node the current method was made from: the routine's node, or
     * for a loop method (Outline) its loop clause, whose own loopDepth is
     * then methodLoopBase = 1.
     */
    Node methodRoot;
    int methodLoopBase;
    /** The node each method was made from, by method name (for {@link #compileClass}). */
    final Map<String, Node> methodRoots = new HashMap<>();
    /**
     * Methods (by the node they are made from, see methodRoot) whose
     * outermost loops become methods of their own because the method was
     * too large for the JIT in an earlier attempt (see {@link #compileClass}).
     */
    final java.util.Set<Node> splitRoots = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    final Map<Tag, Integer> localSlot = new IdentityHashMap<>();
    /** Free-form per-method state for the generator classes (labels etc.). */
    public final Map<Object, Object> methodState = new HashMap<>();

    public Gen(A68 a68, String className, String sourceFile) {
        this.a68 = a68;
        this.thisClass = ClassDesc.of(className);
        this.sourceFile = sourceFile;
    }

    // ------------------------------------------------------------------
    // Entry point.
    // ------------------------------------------------------------------

    /** Variables kept unboxed in JVM locals (see {@link Direct}). */
    java.util.Set<Tag> direct = java.util.Set.of();
    /** Row variables whose descriptor a loop may keep in locals ({@link Hoist#analyse}). */
    java.util.Set<Tag> hoistable = java.util.Set.of();

    /** The DEF part of a module packet (MODULE_DECLARATION), or null for a particular program. */
    Node moduleDef;
    /** The public tags of a module, in order. */
    final List<Tag> pubTags = new ArrayList<>();

    /**
     * HotSpot compiles no method with more bytecode than this
     * (-XX:HugeMethodLimit=8000 with the default -XX:+DontCompileHugeMethods):
     * such a method always runs in the interpreter.
     */
    static final int HUGE_METHOD = 8000;

    /**
     * Compile the program (or module) and return the class file, making
     * sure where possible that no method is too large for the JIT
     * (HUGE_METHOD): the class is generated, the size of each method is
     * measured, and if one is too large, the class is generated again with
     * the outermost loops of that method as methods of their own (Outline).
     * Repeated while that helps (a loop method can be too large itself), at
     * most a few times. Nothing is estimated: the sizes are those of the
     * generated bytecode. LOOP_METHODS false (-fno-loop-methods) turns all
     * loop methods off.
     */
    public static byte[] compileClass(A68 a68, String className, String sourceFile, boolean loopMethods) {
        java.util.Set<Node> split = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (int round = 0; ; round++) {
            Gen g = new Gen(a68, className, sourceFile);
            g.loopMethods = loopMethods;
            g.splitRoots.addAll(split);
            byte[] bytes = g.compile();
            if (!loopMethods || round == 4) {
                return bytes;
            }
            boolean more = false;
            for (java.lang.classfile.MethodModel m : ClassFile.of().parse(bytes).methods()) {
                var code = m.findAttribute(java.lang.classfile.Attributes.code());
                int size = code.isEmpty() ? 0 : code.get().codeLength();
                Node root = g.methodRoots.get(m.methodName().stringValue());
                if (size > HUGE_METHOD && root != null && split.add(root)) {
                    more = true;
                    if (Outline.DEBUG) {
                        System.err.println("a68j: method " + m.methodName().stringValue() + " has " + size
                                + " bytes, compiling its loops as methods of their own");
                    }
                }
            }
            if (!more) {
                return bytes;
            }
        }
    }

    /** Compile the program (or module); returns the class file. */
    public byte[] compile() {
        Node top = a68.job.topNode;
        checkImplemented(top);
        Routine program = new Routine(0, top, null, "program");
        routines.add(program);
        routineOf.put(top, program);
        moduleDef = findModuleDef(top);
        moduleText = findModuleText(top);
        Capture.analyse(this, top, program);
        if (moduleDef != null) {
            // Every identifier and operator declared in the DEF part lives in
            // the module's frame, which survives the elaboration of the module.
            List<Node> ph = new ArrayList<>();
            GenClauses.phrases(son(moduleDef, Attribute.ENQUIRY_CLAUSE), ph);
            for (Node q : ph) {
                List<Node> defs = new ArrayList<>();
                GenDecls.allDefinitions(q, defs);
                for (Node d : defs) {
                    if (!program.frameSlots.containsKey(d.tag)) {
                        program.frameSlots.put(d.tag, program.frameSlots.size());
                    }
                    if (GenDecls.isPublic(d)) {
                        pubTags.add(d.tag);
                    }
                }
            }
        }
        direct = Direct.analyse(this, top);
        hoistable = Hoist.analyse(this, top);
        numberNodes(top);
        program.scheduled = true;
        work.add(program);
        ClassHierarchyResolver resolver = ClassHierarchyResolver.defaultResolver()
                .orElse(ClassHierarchyResolver.ofClassLoading(Gen.class.getClassLoader()));
        return ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(resolver)).build(thisClass, clb -> {
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            clb.with(java.lang.classfile.attribute.SourceFileAttribute.of(new java.io.File(sourceFile).getName()));
            if (moduleDef != null) {
                emitModuleAccess(clb);
            }
            // main runs the program (run0) in a thread with a large stack, as
            // deep recursion is common in Algol 68 programs (see Rt.start).
            clb.withMethodBody("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                        code.ldc(java.lang.constant.MethodHandleDesc.ofMethod(
                                java.lang.constant.DirectMethodHandleDesc.Kind.STATIC, thisClass, "run0",
                                MethodTypeDesc.of(CD_void)));
                        code.invokestatic(CD_RT, "start", MethodTypeDesc.of(CD_void, CD_MethodHandle));
                        code.return_();
                    });
            clb.withMethodBody("run0", MethodTypeDesc.of(CD_void),
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, code -> {
                        if (a68.options().strict) {
                            code.iconst_1();
                            code.putstatic(CD_RT, "strict", CD_boolean);
                        }
                        code.aconst_null();
                        code.iconst_0();
                        code.anewarray(CD_Object);
                        code.invokestatic(thisClass, "program", MT_ROUTINE);
                        code.pop();
                        code.iconst_0();
                        code.invokestatic(CD_RT, "exit", MethodTypeDesc.of(CD_void, CD_int));
                        code.return_();
                    });
            while (!work.isEmpty() || !loopJobs.isEmpty()) {
                if (!work.isEmpty()) {
                    emitRoutine(clb, work.poll());
                } else {
                    Outline.emit(this, clb, loopJobs.poll());
                }
            }
            for (int i = 0; i < javaBridges.size(); i++) {
                javaBridges.get(i).accept(clb);
            }
        });
    }

    /** Position of every node in a pre-order walk of the tree (textual order). */
    final Map<Node, Integer> order = new IdentityHashMap<>();
    /** The position of the first applied occurrence of each tag. */
    final Map<Tag, Integer> firstUse = new IdentityHashMap<>();

    private void numberNodes(Node top) {
        java.util.ArrayDeque<Node> stack = new java.util.ArrayDeque<>();
        stack.push(top);
        int n = 0;
        while (!stack.isEmpty()) {
            Node p = stack.pop();
            for (; p != null; p = p.next) {
                order.put(p, n);
                if (p.tag != null && p.attribute != Attribute.DEFINING_IDENTIFIER
                        && p.attribute != Attribute.DEFINING_OPERATOR) {
                    firstUse.merge(p.tag, n, Math::min);
                }
                n++;
                if (p.sub != null) {
                    // Pre-order: the sub-tree before the following siblings.
                    if (p.next != null) {
                        stack.push(p.next);
                    }
                    stack.push(p.sub);
                    break;
                }
            }
        }
    }

    /**
     * Whether the identifier defined at DEF (a defining identifier) occurs
     * before its declaration in the text.
     */
    boolean usedBeforeDeclaration(Node def) {
        Integer u = firstUse.get(def.tag);
        Integer d = order.get(def);
        return u != null && (d == null || u < d);
    }

    /** Whether the current serial clause has no labels (set by GenClauses#serial for GenDecls#prologue). */
    boolean rangeWithoutLabels;

    /** The bridge methods of Java formal holes (GenJava), emitted after the routines. */
    final List<java.util.function.Consumer<ClassBuilder>> javaBridges = new ArrayList<>();

    /** The MODULE_TEXT of a module packet, or null. */
    Node moduleText;

    private static Node findModuleText(Node p) {
        for (; p != null; p = p.next) {
            if (p.attribute == Attribute.MODULE_TEXT) {
                return p;
            }
            if (p.attribute == Attribute.MODULE_DECLARATION || p.attribute == Attribute.PACKET
                    || p.attribute == Attribute.PRELUDE_PACKET) {
                Node d = findModuleText(p.sub);
                if (d != null) {
                    return d;
                }
            }
        }
        return null;
    }

    private static Node findModuleDef(Node p) {
        for (; p != null; p = p.next) {
            if (p.attribute == Attribute.DEF_PART) {
                return p;
            }
            if (p.attribute == Attribute.MODULE_DECLARATION || p.attribute == Attribute.PACKET
                    || p.attribute == Attribute.PRELUDE_PACKET || p.attribute == Attribute.MODULE_TEXT) {
                Node d = findModuleDef(p.sub);
                if (d != null) {
                    return d;
                }
            }
        }
        return null;
    }

    /**
     * The access methods of a module class: {@code pubKeys()} gives the keys
     * of the public tags (see {@link Intrinsics#moduleKey}) separated by
     * newlines, {@code pub()} elaborates the module once and gives their
     * values (boxed) in the same order.
     */
    private void emitModuleAccess(ClassBuilder clb) {
        clb.withField("frame", CD_FRAME, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC);
        StringBuilder keys = new StringBuilder();
        for (Tag t : pubTags) {
            keys.append(Intrinsics.moduleKey(this, t)).append('\n');
        }
        String k = keys.toString();
        clb.withMethodBody("pubKeys", MethodTypeDesc.of(CD_String), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
            code.ldc(k);
            code.areturn();
        });
        Routine program = routines.get(0);
        // lower_module_text: the prelude elaborates the module the first
        // time it is called (sentinel 1), after the preludes of the modules
        // of its revelation part.
        clb.withField("sentinel", CD_long, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC);
        Node revelation = moduleDef.sub != null && moduleText != null
                && moduleText.sub.attribute == Attribute.REVELATION_PART ? moduleText.sub : null;
        clb.withMethodBody("prelude", MethodTypeDesc.of(CD_void),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_SYNCHRONIZED, code -> {
                    CodeBuilder save = cb;
                    cb = code;
                    java.lang.classfile.Label skip = code.newLabel();
                    code.getstatic(thisClass, "sentinel", CD_long);
                    code.lconst_1();
                    code.ladd();
                    code.dup2();
                    code.putstatic(thisClass, "sentinel", CD_long);
                    code.lconst_1();
                    code.lcmp();
                    code.ifne(skip);
                    if (revelation != null) {
                        GenClauses.revelationLudes(this, revelation, true);
                    }
                    code.aconst_null();
                    code.iconst_0();
                    code.anewarray(CD_Object);
                    code.invokestatic(thisClass, "program", MT_ROUTINE);
                    code.pop();
                    code.labelBinding(skip);
                    code.return_();
                    cb = save;
                });
        // Sic: the postlude increments the sentinel too ("Decrease sentinel"),
        // and runs only when it is 0, i.e. never; so only the increment is kept.
        clb.withMethodBody("postlude", MethodTypeDesc.of(CD_void),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_SYNCHRONIZED, code -> {
                    code.getstatic(thisClass, "sentinel", CD_long);
                    code.lconst_1();
                    code.ladd();
                    code.putstatic(thisClass, "sentinel", CD_long);
                    code.return_();
                });
        emitTypedPubEntries(clb, program);
        clb.withMethodBody("pub", MethodTypeDesc.of(CD_OBJECTS),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_SYNCHRONIZED, code -> {
                    java.lang.classfile.Label done = code.newLabel();
                    code.getstatic(thisClass, "frame", CD_FRAME);
                    code.ifnonnull(done);
                    code.invokestatic(thisClass, "prelude", MethodTypeDesc.of(CD_void));
                    code.labelBinding(done);
                    code.ldc(pubTags.size());
                    code.anewarray(CD_Object);
                    for (int i = 0; i < pubTags.size(); i++) {
                        code.dup();
                        code.ldc(i);
                        code.getstatic(thisClass, "frame", CD_FRAME);
                        code.getfield(CD_FRAME, "s", CD_OBJECTS);
                        code.ldc(program.frameSlots.get(pubTags.get(i)));
                        code.aaload();
                        code.aastore();
                    }
                    code.areturn();
                });
    }

    /**
     * The typed entries of the public procedures and operators of a module
     * that are declared with a routine text ({@code pub proc f = (int n)
     * int: ...}, {@code pub op +:= = ...}): {@code public static pub$i(
     * parameters unboxed) -> result unboxed}, i the index of the tag in
     * pubKeys(). Code using the module calls them through invokedynamic
     * (Intrinsics#callModuleTag, Modules#bsmCall) instead of the boxed
     * value. The entry elaborates the module if that has not happened yet
     * (as pub() does) and calls the routine's typed method with the module's
     * frame, the frame the routine text captured.
     */
    private void emitTypedPubEntries(ClassBuilder clb, Routine program) {
        for (int i = 0; i < pubTags.size(); i++) {
            Node text = staticProcs.get(pubTags.get(i));
            Routine r = text == null ? null : routineOf.get(text);
            if (r == null || r.parent != program || !program.hasFrame()) {
                continue;
            }
            routineFor(text);
            MethodTypeDesc typed = typedEntry(r);
            if (typed == null) {
                continue;
            }
            MethodTypeDesc entry = typed.dropParameterTypes(0, 1);
            clb.withMethodBody("pub$" + i, entry, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                java.lang.classfile.Label ready = code.newLabel();
                code.getstatic(thisClass, "frame", CD_FRAME);
                code.ifnonnull(ready);
                code.invokestatic(thisClass, "pub", MethodTypeDesc.of(CD_OBJECTS));
                code.pop();
                code.labelBinding(ready);
                code.getstatic(thisClass, "frame", CD_FRAME);
                int slot = 0;
                for (int k = 0; k < entry.parameterCount(); k++) {
                    TypeKind kind = TypeKind.from(entry.parameterType(k));
                    code.loadLocal(kind, slot);
                    slot += kind.slotSize();
                }
                code.invokestatic(thisClass, r.method + "t", typed);
                code.return_(TypeKind.from(entry.returnType()));
            });
        }
    }

    /** Register a routine text for compilation and return it. */
    public Routine routineFor(Node routineText) {
        Routine r = routineOf.get(routineText);
        if (r == null) {
            throw new IllegalStateException("routine text not analysed: " + where(routineText));
        }
        if (!r.scheduled) {
            r.scheduled = true;
            work.add(r);
        }
        return r;
    }

    /**
     * The descriptor of the typed entry of routine R (a routine text of a
     * procedure or brief operator called directly, see GenUnits#directCall):
     * (Frame, parameters unboxed) -> result unboxed. Null for other routines.
     */
    MethodTypeDesc typedEntry(Routine r) {
        if (r.node.attribute != Attribute.ROUTINE_TEXT
                || !typedTexts.contains(r.node) && !staticProcs.containsValue(r.node)) {
            return null;
        }
        java.util.List<ClassDesc> ps = new java.util.ArrayList<>();
        ps.add(CD_FRAME);
        for (Tag t : GenUnits.parameters(r.node)) {
            ps.add(JType.of(a68, t.type).desc);
        }
        return MethodTypeDesc.of(JType.of(a68, r.node.type.sub).desc, ps);
    }

    private void emitRoutine(ClassBuilder clb, Routine r) {
        MethodTypeDesc typed = typedEntry(r);
        if (typed != null) {
            // The boxed entry (used by closures) unboxes the arguments and
            // calls the typed one.
            clb.withMethodBody(r.method, MT_ROUTINE, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, code -> {
                code.aload(0);
                int i = 0;
                for (Tag t : GenUnits.parameters(r.node)) {
                    code.aload(1);
                    code.ldc(i++);
                    code.aaload();
                    CodeBuilder save = cb;
                    cb = code;
                    unbox(t.type);
                    cb = save;
                }
                code.invokestatic(thisClass, r.method + "t", typed);
                CodeBuilder save = cb;
                cb = code;
                box(r.node.type.sub);
                cb = save;
                code.areturn();
            });
        }
        clb.withMethodBody(typed != null ? r.method + "t" : r.method, typed != null ? typed : MT_ROUTINE,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, code -> {
            CodeBuilder saveCb = cb;
            Routine saveCur = cur;
            int saveFrame = frameLocal;
            int saveDepth = loopDepth;
            Map<Tag, Integer> saveLocals = new IdentityHashMap<>(localSlot);
            Map<Object, Object> saveState = new HashMap<>(methodState);
            cb = code;
            cur = r;
            loopDepth = 0;
            Node saveRoot = methodRoot;
            int saveBase = methodLoopBase;
            methodRoot = r.node;
            methodLoopBase = 0;
            methodRoots.put(typed != null ? r.method + "t" : r.method, r.node);
            localSlot.clear();
            methodState.clear();
            if (r.hasFrame()) {
                // new Frame(env, size)
                code.new_(CD_FRAME);
                code.dup();
                code.aload(0);
                code.ldc(r.frameSlots.size());
                code.invokespecial(CD_FRAME, "<init>", MethodTypeDesc.of(CD_void, CD_FRAME, CD_int));
                frameLocal = code.allocateLocal(TypeKind.REFERENCE);
                code.astore(frameLocal);
            } else {
                frameLocal = 0;
            }
            if (r.node == a68.job.topNode && moduleDef != null) {
                GenClauses.serial(this, son(moduleDef, Attribute.ENQUIRY_CLAUSE), a68.modes.VOID);
                code.aload(frameLocal);
                code.putstatic(thisClass, "frame", CD_FRAME);
                code.aconst_null();
            } else if (r.node == a68.job.topNode) {
                unit(r.node);
                code.aconst_null();
            } else if (r.parUnit) {
                unit(r.node);
                discard(mode(r.node));
                code.aconst_null();
            } else if (r.node.attribute == Attribute.SERIAL_CLAUSE) {
                GenClauses.serial(this, r.node, r.want);
                box(r.want);
            } else if (typed != null) {
                GenUnits.routineBody(this, r, true);
                code.return_(JType.of(a68, r.node.type.sub).kind);
            } else {
                GenUnits.routineBody(this, r, false);
            }
            if (typed == null) {
                code.areturn();
            }
            cb = saveCb;
            cur = saveCur;
            frameLocal = saveFrame;
            loopDepth = saveDepth;
            methodRoot = saveRoot;
            methodLoopBase = saveBase;
            localSlot.clear();
            localSlot.putAll(saveLocals);
            methodState.clear();
            methodState.putAll(saveState);
        });
    }

    // ------------------------------------------------------------------
    // Dispatch (cf. a68_lower_tree).
    // ------------------------------------------------------------------

    /** Evaluate P, leaving a value of JType.of(P.type) on the stack (nothing for VOID). */
    public void unit(Node p) {
        switch (p.attribute) {
            // Wrappers with a single relevant son.
            case PACKET, PARTICULAR_PROGRAM -> {
                // The particular program: its enclosed clause, voided.
                Node s = p.sub;
                while (s.attribute != Attribute.ENCLOSED_CLAUSE) {
                    s = s.attribute == Attribute.PARTICULAR_PROGRAM ? s.sub : s.next;
                }
                unit(s);
                discard(s.type);
            }
            case ENCLOSED_CLAUSE, UNIT, TERTIARY, SECONDARY, PRIMARY -> {
                Node s = p.sub;
                unit(s);
                if (p.type != null) {
                    adjust(mode(s), p.type, p);
                }
            }
            case CLOSED_CLAUSE -> GenClauses.closedClause(this, p);
            case SERIAL_CLAUSE -> GenClauses.serialClause(this, p);
            case CONDITIONAL_CLAUSE -> GenClauses.conditionalClause(this, p);
            case CASE_CLAUSE -> GenClauses.caseClause(this, p);
            case CONFORMITY_CLAUSE -> GenClauses.conformityClause(this, p);
            case LOOP_CLAUSE -> GenClauses.loopClause(this, p);
            case COLLATERAL_CLAUSE -> GenClauses.collateralClause(this, p);
            case PARALLEL_CLAUSE -> GenClauses.parallelClause(this, p);
            case ACCESS_CLAUSE -> GenClauses.accessClause(this, p);
            case JUMP -> GenClauses.jump(this, p);
            case ASSERTION -> GenClauses.assertion(this, p);
            case DENOTATION, INT_DENOTATION, REAL_DENOTATION, BITS_DENOTATION, ROW_CHAR_DENOTATION, TRUE_SYMBOL, FALSE_SYMBOL,
                 LONGETY, SHORTETY, EMPTY_SYMBOL -> GenUnits.denotation(this, p);
            case SKIP -> GenUnits.skip(this, p);
            case NIHIL -> GenUnits.nihil(this, p);
            case IDENTIFIER -> GenUnits.identifier(this, p);
            case FORMULA -> GenUnits.formula(this, p);
            case MONADIC_FORMULA -> GenUnits.monadicFormula(this, p);
            case ASSIGNATION -> GenUnits.assignation(this, p);
            case IDENTITY_RELATION -> GenUnits.identityRelation(this, p);
            case AND_FUNCTION, OR_FUNCTION -> GenUnits.logicFunction(this, p);
            case SLICE -> GenUnits.slice(this, p);
            case SELECTION -> GenUnits.selection(this, p);
            case CAST -> GenUnits.cast(this, p);
            case CALL -> GenUnits.call(this, p);
            case GENERATOR -> GenUnits.generator(this, p);
            case ROUTINE_TEXT -> GenUnits.routineText(this, p);
            case FORMAL_HOLE -> GenJava.formalHole(this, p);
            case DEREFERENCING -> GenCoercions.dereferencing(this, p);
            case DEPROCEDURING -> GenCoercions.deproceduring(this, p);
            case PROCEDURING -> GenCoercions.proceduring(this, p);
            case UNITING -> GenCoercions.uniting(this, p);
            case ROWING -> GenCoercions.rowing(this, p);
            case WIDENING -> GenCoercions.widening(this, p);
            case VOIDING -> GenCoercions.voiding(this, p);
            case LABELED_UNIT -> GenClauses.labeledUnit(this, p);
            case DECLARATION_LIST, IDENTITY_DECLARATION, VARIABLE_DECLARATION, PROCEDURE_DECLARATION,
                 PROCEDURE_VARIABLE_DECLARATION, OPERATOR_DECLARATION, BRIEF_OPERATOR_DECLARATION,
                 MODE_DECLARATION, PRIORITY_DECLARATION -> GenDecls.declaration(this, p);
            default -> throw new Unsupported(p, p.attribute.name());
        }
    }

    /**
     * Conversion between the JVM type a son leaves and the one its father must
     * leave. Wrapper nodes normally have the same mode as their son; when they
     * do not (VOID father) the value is discarded.
     */
    public void adjust(Moid from, Moid to, Node p) {
        JType f = JType.of(a68, from);
        JType t = JType.of(a68, to);
        if (f == t) {
            return;
        }
        if (t == JType.VOID) {
            discard(from);
            return;
        }
        if (f == JType.VOID) {
            throw new Unsupported(p, "value of VOID mode where " + t + " is required");
        }
        if (t == JType.OBJECT && !f.isPrimitive()) {
            return;
        }
        if (!f.isPrimitive() && t.isPrimitive()) {
            unbox(to);
            return;
        }
        if (f.isPrimitive() && !t.isPrimitive()) {
            box(from);
            return;
        }
        if (!f.isPrimitive() && !t.isPrimitive()) {
            cb.checkcast(t.desc);
            return;
        }
        throw new Unsupported(p, "conversion " + f + " -> " + t);
    }

    /** Pop a value of mode M. */
    public void discard(Moid m) {
        JType t = JType.of(a68, m);
        switch (t.kind.slotSize()) {
            case 0 -> { }
            case 1 -> cb.pop();
            default -> cb.pop2();
        }
    }

    /** Box the value of mode M on the stack. */
    public void box(Moid m) {
        JType t = JType.of(a68, m);
        if (t.isPrimitive()) {
            cb.invokestatic(t.box, "valueOf", MethodTypeDesc.of(t.box, t.desc));
        } else if (t == JType.VOID) {
            cb.aconst_null();
        }
    }

    /** Unbox an Object on the stack into the representation of mode M. */
    public void unbox(Moid m) {
        JType t = JType.of(a68, m);
        switch (t) {
            case VOID -> cb.pop();
            case BOOL -> {
                cb.checkcast(CD_Boolean);
                cb.invokevirtual(CD_Boolean, "booleanValue", MethodTypeDesc.of(CD_boolean));
            }
            case INT -> {
                cb.checkcast(CD_Integer);
                cb.invokevirtual(CD_Integer, "intValue", MethodTypeDesc.of(CD_int));
            }
            case LONG -> {
                cb.checkcast(CD_Long);
                cb.invokevirtual(CD_Long, "longValue", MethodTypeDesc.of(CD_long));
            }
            case FLOAT -> {
                cb.checkcast(CD_Float);
                cb.invokevirtual(CD_Float, "floatValue", MethodTypeDesc.of(CD_float));
            }
            case DOUBLE -> {
                cb.checkcast(CD_Double);
                cb.invokevirtual(CD_Double, "doubleValue", MethodTypeDesc.of(CD_double));
            }
            case OBJECT -> { }
            default -> cb.checkcast(t.desc);
        }
    }

    // ------------------------------------------------------------------
    // Identifier storage (docs/BACKEND.md section 3).
    // ------------------------------------------------------------------

    /** Whether TAG is defined by the standard environment or an imported module. */
    public boolean isIntrinsic(Tag t) {
        return t.moif != null || tableOwner.get(t.symbolTable) == null;
    }

    /** Declare TAG in the current routine; the value to store is on the stack (JType of tag mode). */
    public void defineAndStore(Tag t) {
        Integer fs = cur.frameSlots.get(t);
        if (fs == null && !localSlot.containsKey(t)) {
            localSlot.put(t, cb.allocateLocal(JType.of(a68, t.type).kind));
        }
        store(t);
    }

    /** Store the value on the stack into TAG's storage. */
    public void store(Tag t) {
        Routine owner = tableOwner.get(t.symbolTable);
        Integer fs = owner == null ? null : owner.frameSlots.get(t);
        JType jt = JType.of(a68, t.type);
        if (fs != null) {
            box(t.type);
            int tmp = cb.allocateLocal(TypeKind.REFERENCE);
            cb.astore(tmp);
            loadFrame(owner);
            cb.getfield(CD_FRAME, "s", CD_OBJECTS);
            cb.ldc(fs);
            cb.aload(tmp);
            cb.aastore();
            return;
        }
        if (direct.contains(t)) {
            jt = JType.of(a68, JType.base(t.type).sub);
        }
        Integer slot = localSlot.get(t);
        if (slot == null) {
            slot = cb.allocateLocal(jt.kind);
            localSlot.put(t, slot);
        }
        cb.storeLocal(jt.kind, slot);
    }

    /** Load TAG's value (JType of tag mode). */
    public void load(Tag t, Node at) {
        Routine owner = tableOwner.get(t.symbolTable);
        Integer fs = owner == null ? null : owner.frameSlots.get(t);
        JType jt = JType.of(a68, t.type);
        if (fs != null) {
            loadFrame(owner);
            cb.getfield(CD_FRAME, "s", CD_OBJECTS);
            cb.ldc(fs);
            cb.aaload();
            unbox(t.type);
            return;
        }
        if (direct.contains(t)) {
            jt = JType.of(a68, JType.base(t.type).sub);
        }
        Integer slot = localSlot.get(t);
        if (slot == null) {
            throw new Unsupported(at, "use of identifier before its declaration was compiled");
        }
        cb.loadLocal(jt.kind, slot);
    }

    /**
     * The class of the location of X if X is a variable declared in this
     * compilation unit and not a direct one: its name is the Cell, or for a
     * primitive mode the typed cell (IntCell, ...), made by its generator
     * (GenRows#generate, #generateSkip), so that code can use the field of
     * that class instead of calling Rt.deref/Ref.get or Rt.derefI/assignI.
     * Null otherwise.
     */
    ClassDesc ownCell(Tag x) {
        if (x == null || !x.variable || direct.contains(x) || isIntrinsic(x) || Intrinsics.isModuleTag(x)
                || tableOwner.get(x.symbolTable) == null) {
            return null;
        }
        Moid m = JType.base(x.type);
        if (JType.of(a68, m) != JType.REF || m.sub == null) {
            return null;
        }
        JType t = JType.of(a68, JType.base(m.sub));
        return t.isPrimitive() ? GenRows.typedCell(t) : CD_CELL;
    }

    /** The tag of the unit P if it is an applied identifier of an own variable (see {@link #ownCell}), else null. */
    Tag ownCellTag(Node p) {
        Node id = GenUnits.strip(p);
        return id != null && id.attribute == Attribute.IDENTIFIER && ownCell(id.tag) != null ? id.tag : null;
    }

    /**
     * Push the location of the own variable X cast to its class C (see
     * {@link #ownCell}). Before its declaration is elaborated the variable
     * is NIL (GenDecls#prologue): Rt.deref reports that as usual.
     */
    void loadOwnCell(Tag x, ClassDesc c, Node at) {
        load(x, at);
        Label ok = cb.newLabel();
        cb.dup();
        cb.ifnonnull(ok);
        pushWhere(at);
        cb.invokestatic(CD_RT, "deref", MethodTypeDesc.of(CD_REF, CD_REF, CD_String, CD_int));
        cb.labelBinding(ok);
        cb.checkcast(c);
    }

    /**
     * Push the frame of routine OWNER, which encloses the current routine.
     * frameLocal holds the frame of the current routine, or its environment
     * if it makes no frame of its own (Routine#hasFrame); one step up the
     * chain of frames is needed for each routine from the current one up to
     * (not including) OWNER that has a frame. For an OWNER without a frame
     * this is the frame its invocations use.
     */
    public void loadFrame(Routine owner) {
        cb.aload(frameLocal);
        owner.depthFrom(cur);
        for (Routine x = cur; x != owner; x = x.parent) {
            if (x.hasFrame()) {
                cb.getfield(CD_FRAME, "up", CD_FRAME);
            }
        }
    }

    private final Map<Tag, Integer> labelNumbers = new IdentityHashMap<>();

    /** Program-wide number of a label. */
    public int labelNumber(Tag label) {
        return labelNumbers.computeIfAbsent(label, k -> labelNumbers.size());
    }

    /** MethodHandle constant for a routine method. */
    public DirectMethodHandleDesc handle(Routine r) {
        return MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, thisClass, r.method, MT_ROUTINE);
    }

    // ------------------------------------------------------------------
    // Helpers.
    // ------------------------------------------------------------------

    /** Source position for messages. */
    public static String where(Node p) {
        if (p == null || p.info == null || p.info.line == null) {
            return "a68j";
        }
        return p.info.line.filename + ":" + p.info.line.number;
    }

    /** Emit a line number entry for P. */
    public void line(Node p) {
        if (p != null && p.info != null && p.info.line != null && p.info.line.filename.equals(sourceFile)) {
            cb.lineNumber(p.info.line.number);
        }
    }

    /** Push file name and line of P (arguments of the Rt error routines). */
    public void pushWhere(Node p) {
        String file = p.info != null && p.info.line != null ? p.info.line.filename : "";
        int line = p.info != null && p.info.line != null ? p.info.line.number : 0;
        cb.ldc(file);
        cb.ldc(line);
    }

    /**
     * The mode of the value P leaves: its own mode, or for a wrapper without
     * one (or with a SERIES_MODE) the mode of its son.
     */
    public static Moid mode(Node p) {
        while (p != null) {
            if (p.type != null && p.type.attribute != Attribute.SERIES_MODE && p.type.attribute != Attribute.STOWED_MODE) {
                return p.type;
            }
            switch (p.attribute) {
                case ENCLOSED_CLAUSE, UNIT, TERTIARY, SECONDARY, PRIMARY, LABELED_UNIT -> p = p.attribute == Attribute.LABELED_UNIT ? p.sub.next : p.sub;
                default -> {
                    return p.type;
                }
            }
        }
        return null;
    }

    /** First son of P with attribute A, or null. */
    public static Node son(Node p, Attribute a) {
        for (Node q = p.sub; q != null; q = q.next) {
            if (q.attribute == a) {
                return q;
            }
        }
        return null;
    }
}
