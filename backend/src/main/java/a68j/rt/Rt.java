// a68j runtime: support routines and runtime errors (as libga68's
// ga68-error.c).
//
// Copyright (C) 2026 The a68j authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH GCC-exception-3.1
//
// This file is part of a68j. It is licensed under the GNU General Public
// License, version 3 or later. Under Section 7 of GPL version 3, you are
// granted additional permissions described in the GCC Runtime Library
// Exception, version 3.1, as published by the Free Software Foundation; see
// the files COPYING and COPYING.RUNTIME.
//
// Additional permission under GNU GPL version 3 section 7: for this file, a
// Compilation Process done with a68j, alone or with other GPL-compatible
// software, is an Eligible Compilation Process in the sense of the GCC
// Runtime Library Exception, version 3.1.
package a68j.rt;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Runtime support. */
public final class Rt {
    private Rt() {}

    /** Standard output with UTF-8 encoding, flushed at exit. */
    public static final PrintStream OUT = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), false,
            StandardCharsets.UTF_8);

    /** Copy a boxed value for value semantics (structs, rows and united ones deep, everything else shared). */
    public static Object copyValue(Object v) {
        if (v instanceof Struct s) {
            return s.copy();
        }
        if (v instanceof Row r) {
            return r.copy();
        }
        if (v instanceof Union u && (u.value instanceof Struct || u.value instanceof Row)) {
            // A united row or structure is a value too (a68_low_dup).
            return new Union(u.mode, copyValue(u.value));
        }
        return v;
    }

    public static boolean isSame(Ref a, Ref b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.same(b);
    }

    /** _libga68_abort: print the message and terminate like abort(). */
    public static RuntimeException abort(String file, int line, String text) {
        OUT.flush();
        System.err.print(file + ":" + line + ": runtime error: " + text + "\n");
        System.err.flush();
        Runtime.getRuntime().halt(134);
        return new IllegalStateException();
    }

    // ------------------------------------------------------------------
    // Java exceptions (nest Java, docs/java-modules.md).
    // ------------------------------------------------------------------

    /**
     * A Java exception T thrown by a Java method that the program called at
     * FILE:LINE: a runtime error with that position and status 134, as for
     * the runtime errors of Algol 68 itself, for example
     * {@code x.a68:4: runtime error: Java exception
     * java.lang.NumberFormatException: For input string: "12x"}. The causes
     * of T follow (", caused by ..."). The generated code calls this in the
     * exception handler around the call and throws the result: T itself for
     * the exceptions that only pass through the Java method ({@link
     * #passesThrough}), a Par.Cancelled for an interruption of a cancelled
     * unit of a parallel clause; otherwise this does not return.
     */
    public static Throwable javaException(Throwable t, String file, int line) {
        Throwable pass = passesThrough(t);
        if (pass != null) {
            return pass;
        }
        throw abortJava(file, line, t);
    }

    /**
     * As {@link #javaException}, for a Java method called by a bridge or
     * adapter method, which is called through a procedure value and does not
     * know where from: the position is that of the innermost activation of
     * Algol 68 code below the method that calls this ({@link #algolFrame});
     * FILE:LINE (the formal hole) if there is none, no position if FILE is
     * null.
     */
    public static Throwable javaExceptionOfCaller(Throwable t, String file, int line) {
        Throwable pass = passesThrough(t);
        if (pass != null) {
            return pass;
        }
        StackWalker.StackFrame f = algolFrame(1);
        if (f != null) {
            throw abortJava(SOURCE.get(f.getDeclaringClass()), f.getLineNumber(), t);
        }
        throw abortJava(file, line, t);
    }

    /**
     * Whether the runtime error for a Java exception is followed by the Java
     * stack trace of the exception: if the environment variable
     * A68J_JAVA_TRACE is set (to any value), a debugging aid for the authors
     * of Java modules (docs/java-modules.md, "Java exceptions").
     */
    private static final boolean JAVA_TRACE = System.getenv("A68J_JAVA_TRACE") != null;

    /**
     * The runtime error for the Java exception T at FILE:LINE (no position
     * if FILE is null), followed by the Java stack trace of T if {@link
     * #JAVA_TRACE}; status 134. Does not return.
     */
    private static RuntimeException abortJava(String file, int line, Throwable t) {
        OUT.flush();
        String where = file == null ? programName : file + ":" + line;
        System.err.print(where + ": runtime error: " + javaMessage(t) + "\n");
        if (JAVA_TRACE) {
            t.printStackTrace(System.err);
        }
        System.err.flush();
        Runtime.getRuntime().halt(134);
        return new IllegalStateException();
    }

    /**
     * A runtime error that Java code called from Algol 68 reports itself
     * (JavaObject#error, the conversions of JavaConv): the position is that
     * of the innermost activation of Algol 68 code on the stack ({@link
     * #algolFrame}), none if there is none. Does not return.
     */
    public static RuntimeException abortAtCaller(String text) {
        StackWalker.StackFrame f = algolFrame(0);
        if (f != null) {
            throw abort(SOURCE.get(f.getDeclaringClass()), f.getLineNumber(), text);
        }
        throw abortWithoutPosition(text);
    }

    /** A runtime error without a position: "prog: runtime error: TEXT", status 134. */
    static RuntimeException abortWithoutPosition(String text) {
        OUT.flush();
        System.err.print(programName + ": runtime error: " + text + "\n");
        System.err.flush();
        Runtime.getRuntime().halt(134);
        return new IllegalStateException();
    }

    /**
     * What a Java method lets pass instead of a runtime error: a jump out of
     * an Algol 68 procedure that the method called back (Jump), the
     * cancellation of a unit of a parallel clause (Par.Cancelled; also an
     * InterruptedException or ClosedByInterruptException in a unit thread,
     * as its threads are interrupted only to cancel it, see Par), and the
     * errors of the virtual machine (a stack overflow ends the program with
     * status 139 as in Rt#start). Null for any other exception.
     */
    private static Throwable passesThrough(Throwable t) {
        if (t instanceof Jump || t instanceof Par.Cancelled || t instanceof VirtualMachineError) {
            return t;
        }
        if ((t instanceof InterruptedException || t instanceof java.nio.channels.ClosedByInterruptException)
                && Par.inUnit()) {
            return new Par.Cancelled();
        }
        return null;
    }

    /** The text of the runtime error for the Java exception T and its causes. */
    static String javaMessage(Throwable t) {
        StringBuilder b = new StringBuilder("Java exception ").append(t);
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        seen.add(t);
        for (Throwable c = t.getCause(); c != null && seen.add(c); c = c.getCause()) {
            b.append(", caused by ").append(c);
        }
        return b.toString();
    }

    /**
     * The source file of a class compiled from Algol 68 (the constant
     * A68_SOURCE that Gen gives every such class, as the file name appears
     * in the runtime errors); null for any other class.
     */
    private static final ClassValue<String> SOURCE = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> c) {
            try {
                java.lang.reflect.Field f = c.getField("A68_SOURCE");
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == String.class
                        && f.getDeclaringClass() == c) {
                    return (String) f.get(null);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // not a class compiled from Algol 68
            }
            return null;
        }
    };

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * The innermost frame of Algol 68 code (a class with A68_SOURCE, a line
     * number) on the stack, below the frames of Rt and JavaObject and SKIP
     * more frames (1 for the bridge or adapter method that called the Java
     * method, see #javaExceptionOfCaller); the frames of other Java code in
     * between are skipped too. The line is the last line number entry before
     * the call, the line of the unit or call.
     *
     * <p>The frames of the bundled modules (Lists, Hashmaps: classes in the
     * package a68j.lib) count as part of the runtime, as their source is not
     * the user's: a missing key in {@code k ELEM m} is reported at the
     * program's ELEM, not in hashmaps.a68. Only if there is no other frame of
     * Algol 68 code is a frame of a bundled module taken. Null if there is
     * none at all.
     */
    private static StackWalker.StackFrame algolFrame(int skip) {
        return WALKER.walk(s -> {
            StackWalker.StackFrame bundled = null;
            var it = s.dropWhile(f -> f.getDeclaringClass() == Rt.class || f.getDeclaringClass() == JavaObject.class)
                    .skip(skip)
                    .filter(f -> f.getLineNumber() > 0 && SOURCE.get(f.getDeclaringClass()) != null)
                    .iterator();
            while (it.hasNext()) {
                StackWalker.StackFrame f = it.next();
                if (!f.getDeclaringClass().getPackageName().equals("a68j.lib")) {
                    return f;
                }
                if (bundled == null) {
                    bundled = f;
                }
            }
            return bundled;
        });
    }

    public static RuntimeException assertFailure(String file, int line) {
        return abort(file, line, "ASSERT failure");
    }

    public static Ref deref(Ref r, String file, int line) {
        if (r == null) {
            throw abort(file, line, "attempt to dereference NIL");
        }
        return r;
    }

    // Dereferencing and assignation for names of primitive modes: unboxed
    // for the typed locations (IntCell, ...), otherwise through get/set.

    public static int derefI(Ref r, String file, int line) {
        if (r instanceof IntCell c) {
            return c.v;
        }
        return (Integer) deref(r, file, line).get();
    }

    public static void assignI(Ref r, int v, String file, int line) {
        if (r instanceof IntCell c) {
            c.v = v;
            return;
        }
        deref(r, file, line).set(v);
    }

    public static long derefL(Ref r, String file, int line) {
        if (r instanceof LongCell c) {
            return c.v;
        }
        return (Long) deref(r, file, line).get();
    }

    public static void assignL(Ref r, long v, String file, int line) {
        if (r instanceof LongCell c) {
            c.v = v;
            return;
        }
        deref(r, file, line).set(v);
    }

    public static float derefF(Ref r, String file, int line) {
        if (r instanceof FloatCell c) {
            return c.v;
        }
        return (Float) deref(r, file, line).get();
    }

    public static void assignF(Ref r, float v, String file, int line) {
        if (r instanceof FloatCell c) {
            c.v = v;
            return;
        }
        deref(r, file, line).set(v);
    }

    public static double derefD(Ref r, String file, int line) {
        if (r instanceof DoubleCell c) {
            return c.v;
        }
        return (Double) deref(r, file, line).get();
    }

    public static void assignD(Ref r, double v, String file, int line) {
        if (r instanceof DoubleCell c) {
            c.v = v;
            return;
        }
        deref(r, file, line).set(v);
    }

    public static boolean derefZ(Ref r, String file, int line) {
        if (r instanceof BoolCell c) {
            return c.v;
        }
        return (Boolean) deref(r, file, line).get();
    }

    public static void assignZ(Ref r, boolean v, String file, int line) {
        if (r instanceof BoolCell c) {
            c.v = v;
            return;
        }
        deref(r, file, line).set(v);
    }

    /**
     * Assignation: store V into the name R. A structure is stored into the
     * existing structure field by field, so that names of its fields stay
     * valid.
     */
    public static void assign(Ref r, Object v, String file, int line) {
        if (r == null) {
            throw abort(file, line, "attempt to dereference NIL");
        }
        if (v instanceof Struct n && r.get() instanceof Struct o && o != n && o.f.length == n.f.length) {
            System.arraycopy(n.f, 0, o.f, 0, n.f.length);
            return;
        }
        r.set(v);
    }

    /** Assignation of a row: a flexible name gets the new row, otherwise the elements are stored. */
    public static void assignRow(Ref r, Row v, boolean flex, String file, int line) {
        if (r == null) {
            throw abort(file, line, "attempt to dereference NIL");
        }
        if (flex || !(r.get() instanceof Row old)) {
            r.set(v);
            return;
        }
        v.storeInto(old, file, line);
    }

    /** The structure referred to by R (for a field selection through a name). */
    public static Struct derefStruct(Ref r, String file, int line) {
        if (r == null) {
            throw abort(file, line, "attempt to dereference NIL");
        }
        return (Struct) r.get();
    }

    /** Assignation to field FIELD of the structure S (as {@link #assign} through its name). */
    public static void assignField(Struct s, int field, Object v) {
        if (v instanceof Struct n && s.f[field] instanceof Struct o && o != n && o.f.length == n.f.length) {
            System.arraycopy(n.f, 0, o.f, 0, n.f.length);
            return;
        }
        s.f[field] = v;
    }

    /** Name of a field of the structure referred to by R. */
    public static Ref fieldRef(Ref r, int field, String file, int line) {
        if (r == null) {
            throw abort(file, line, "attempt to dereference NIL");
        }
        return new FieldRef((Struct) r.get(), field);
    }

    /** Element of a row value at the given data index (relative to the row's offset). */
    public static Object elem(Row row, int index) {
        return row.get(row.offset + index);
    }

    // Typed element access (unboxed for rows with primitive storage).
    public static int elemI(Row row, int index) { return row.getInt(row.offset + index); }
    public static long elemL(Row row, int index) { return row.getLong(row.offset + index); }
    public static float elemF(Row row, int index) { return row.getFloat(row.offset + index); }
    public static double elemD(Row row, int index) { return row.getDouble(row.offset + index); }
    public static boolean elemZ(Row row, int index) { return row.getBool(row.offset + index); }

    public static void setElemI(Row row, int index, int v) { row.setInt(row.offset + index, v); }
    public static void setElemL(Row row, int index, long v) { row.setLong(row.offset + index, v); }
    public static void setElemF(Row row, int index, float v) { row.setFloat(row.offset + index, v); }
    public static void setElemD(Row row, int index, double v) { row.setDouble(row.offset + index, v); }
    public static void setElemZ(Row row, int index, boolean v) { row.setBool(row.offset + index, v); }

    /** Name of the element of the row referred to by R at the given data index. */
    public static Ref elemRef(Row row, int index) {
        return row.ref(row.offset + index);
    }

    /** Uniting: a boxed value of the mode with number MODE into a united value. */
    public static Union unite(Object v, int mode) {
        if (v instanceof Union u) {
            return u;
        }
        return new Union(mode, v);
    }

    // Uniting a value of a primitive mode: the value boxed as Gen#box does
    // (Integer.valueOf ...), in one call instead of two (smaller code).

    public static Union unite(int v, int mode) {
        return new Union(mode, Integer.valueOf(v));
    }

    public static Union unite(long v, int mode) {
        return new Union(mode, Long.valueOf(v));
    }

    public static Union unite(float v, int mode) {
        return new Union(mode, Float.valueOf(v));
    }

    public static Union unite(double v, int mode) {
        return new Union(mode, Double.valueOf(v));
    }

    public static Union unite(boolean v, int mode) {
        return new Union(mode, Boolean.valueOf(v));
    }

    /** Rowing: a one-element row [1:1]. */
    public static Row rowOf1(Object v) {
        return Row.of1(1, 1, new Object[] {v});
    }

    /** Selection of a field from the row of structures referred to by R: a name of the row of fields. */
    public static Ref selectRowRef(Ref r, int field, String file, int line) {
        if (r == null) {
            throw abort(file, line, "attempt to dereference NIL");
        }
        return new Cell(((Row) r.get()).select(field));
    }

    /** A procedure that jumps to a label (proceduring of a jump). */
    public static Proc jumper(Frame frame, int label) {
        return new Proc() {
            @Override
            public Object call(Object[] args) {
                throw new Jump(frame, label);
            }
        };
    }

    /** Rowing of a name: a name of the rowed value; NIL yields NIL. */
    public static Ref rowName(Ref r, boolean moreDims) {
        if (r == null) {
            return null;
        }
        if (moreDims) {
            return new Cell(addDim((Row) r.get()));
        }
        // A row [1:1] whose element is the variable itself (a68_lower_rowing
        // makes a descriptor pointing to the referred value).
        return new Cell(new Row(new Object[] {r}, 0, new long[] {1}, new long[] {1}, new long[] {1},
                new int[] {Row.THROUGH_NAME}));
    }

    /** Rowing [] A -> [,] A: a new first dimension [1:1], sharing the elements. */
    public static Row addDim(Row r) {
        int k = r.dims;
        long[] lw = new long[k + 1], up = new long[k + 1], st = new long[k + 1];
        lw[0] = 1;
        up[0] = 1;
        st[0] = 0;
        System.arraycopy(r.lwb, 0, lw, 1, k);
        System.arraycopy(r.upb, 0, up, 1, k);
        System.arraycopy(r.stride, 0, st, 1, k);
        return new Row(r.store, r.offset, lw, up, st, r.path);
    }

    /** Widening of a BITS value of WIDTH bits to [] BOOL; element 1 is the most significant bit. */
    public static Row bitsToRow(long v, int width) {
        boolean[] d = new boolean[width];
        for (int i = 0; i < width; i++) {
            d[i] = ((v >>> (width - 1 - i)) & 1) != 0;
        }
        return new Row(d, 0, new long[] {1}, new long[] {width}, new long[] {1});
    }

    /**
     * Integer division traps as on x86-64 (idiv): a divisor 0, or the
     * quotient of the most negative number by -1, raise SIGFPE, which
     * terminates a ga68 program without any message. The JVM cannot take the
     * signal itself, so the program ends with the status a shell reports for
     * it (128 + SIGFPE).
     */
    public static void sigfpe() {
        OUT.flush();
        System.err.flush();
        Runtime.getRuntime().halt(128 + 8);
    }

    /** % (OVER) on INT. */
    public static int over(int a, int b) {
        if (b == 0 || (b == -1 && a == Integer.MIN_VALUE)) {
            sigfpe();
        }
        return a / b;
    }

    /** % (OVER) on LONG INT. */
    public static long over(long a, long b) {
        if (b == 0 || (b == -1 && a == Long.MIN_VALUE)) {
            sigfpe();
        }
        return a / b;
    }

    /** MODAB as in ga68: the remainder of C (sign of the left operand). */
    public static int rem(int a, int b) {
        if (b == 0 || (b == -1 && a == Integer.MIN_VALUE)) {
            sigfpe();
        }
        return a % b;
    }

    public static long rem(long a, long b) {
        if (b == 0 || (b == -1 && a == Long.MIN_VALUE)) {
            sigfpe();
        }
        return a % b;
    }

    /** MOD as in ga68: the result is non-negative. */
    public static int mod(int a, int b) {
        if (b == 0 || (b == -1 && a == Integer.MIN_VALUE)) {
            sigfpe();
        }
        int r = a % b;
        return r < 0 ? (b < 0 ? r - b : r + b) : r;
    }

    public static long mod(long a, long b) {
        if (b == 0 || (b == -1 && a == Long.MIN_VALUE)) {
            sigfpe();
        }
        long r = a % b;
        return r < 0 ? (b < 0 ? r - b : r + b) : r;
    }

    /** Whether the program was compiled with -std=algol68 (strict Algol 68). */
    public static boolean strict;

    /** Position of the current standard operation, for its runtime errors. */
    public static String curFile = "";
    public static int curLine;

    /** Program name and arguments (argc/argv of the POSIX module); argv[1] is the program. */
    public static String programName = "a68";
    public static java.util.List<String> programArgs = java.util.List.of();

    public static void setProgramName(String name, java.util.List<String> args) {
        programName = name;
        programArgs = java.util.List.copyOf(args);
    }

    /** Called by the main method of a compiled program with its command-line arguments. */
    public static void setProgramArgs(String name, String[] args) {
        setProgramName(name, java.util.Arrays.asList(args));
    }

    /**
     * The version of a68j that this runtime belongs to: the
     * Implementation-Version of the jar it was loaded from (build.sh writes
     * the file VERSION there), or null if it was not loaded from such a jar.
     */
    public static String version() {
        return Rt.class.getPackage().getImplementationVersion();
    }

    /**
     * Called first by the main method of a program and by the prelude of a
     * module (the first time) when the compiler that compiled CLASSNAME was
     * that of a68j COMPILEDBY: stop with a message if this runtime belongs
     * to another version. The generated code calls methods of the runtime
     * by name and type, so that with another runtime it could fail anywhere
     * or compute something else. A runtime without a version is not
     * checked.
     */
    public static void checkVersion(String compiledBy, String className) {
        String v = version();
        if (v != null && !v.equals(compiledBy)) {
            OUT.flush();
            System.err.print("a68j: error: " + className + " was compiled by a68j " + compiledBy
                    + ", but the runtime is that of a68j " + v
                    + "; compile it again or use the runtime of a68j " + compiledBy + "\n");
            System.err.flush();
            Runtime.getRuntime().halt(1);
        }
    }

    /** Stack size of the thread that runs the program. */
    static final long STACK_SIZE = 128L << 20;

    /**
     * Run the program (the class's run0) in a thread with a large stack.
     * A stack overflow ends the program like the segmentation fault of a
     * native ga68 program (status 139, no message); so does the use of an
     * undefined value, with a message ({@link #undefinedValue}).
     */
    public static void start(java.lang.invoke.MethodHandle run0) throws Throwable {
        ClassLoader loader = WALKER.getCallerClass().getClassLoader();
        Throwable[] failure = new Throwable[1];
        Thread t = new Thread(null, () -> {
            try {
                run0.invokeExact();
            } catch (StackOverflowError e) {
                OUT.flush();
                Runtime.getRuntime().halt(139);
            } catch (Throwable e) {
                failure[0] = e;
            }
        }, "main", STACK_SIZE);
        t.start();
        t.join();
        if (failure[0] != null) {
            undefinedValue(failure[0], loader);
            throw failure[0];
        }
    }

    /**
     * The generated code represents some values that have not been given
     * yet by null: that of a procedure variable that has not been assigned,
     * and that of an identifier whose identity declaration has not been
     * elaborated yet (but not a procedure declared with a routine text,
     * which exists from the start of its range, as in ga68). Using such a
     * value is undefined in the Report; the native ga68 program ends with a
     * segmentation fault. If the exception T (or one of its causes, for a failure in the
     * prelude of a module) is the NullPointerException of such a use in
     * Algol 68 code, the program ends with the runtime error
     * {@code FILE:LINE: runtime error: attempt to use an undefined value}
     * at the innermost activation of Algol 68 code ({@link #algolFrame(StackTraceElement[], ClassLoader)},
     * the classes resolved by LOADER, that of the program class) and, as
     * the native program, status 139. The Java stack trace follows if
     * A68J_JAVA_TRACE is set. Returns if T is no such exception.
     */
    private static void undefinedValue(Throwable t, ClassLoader loader) {
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable c = t; c != null && seen.add(c); c = c.getCause()) {
            if (c instanceof NullPointerException) {
                String where = algolFrame(c.getStackTrace(), loader);
                if (where == null) {
                    return;
                }
                OUT.flush();
                System.err.print(where + ": runtime error: attempt to use an undefined value\n");
                if (JAVA_TRACE) {
                    t.printStackTrace(System.err);
                }
                System.err.flush();
                Runtime.getRuntime().halt(139);
            }
        }
    }

    /**
     * FILE:LINE of the innermost frame of Algol 68 code in TRACE, the stack
     * trace of an exception, as {@link #algolFrame(int)} for the current
     * stack: frames of the bundled modules only if there is no other.
     * LOADER resolves the class names. Null if there is no such frame.
     */
    private static String algolFrame(StackTraceElement[] trace, ClassLoader loader) {
        String bundled = null;
        for (StackTraceElement e : trace) {
            if (e.getLineNumber() <= 0) {
                continue;
            }
            Class<?> c;
            try {
                c = Class.forName(e.getClassName(), false, loader);
            } catch (ClassNotFoundException | LinkageError x) {
                continue;
            }
            String file = SOURCE.get(c);
            if (file == null) {
                continue;
            }
            String at = file + ":" + e.getLineNumber();
            if (!c.getPackageName().equals("a68j.lib")) {
                return at;
            }
            if (bundled == null) {
                bundled = at;
            }
        }
        return bundled;
    }

    /** Normal end of the program. */
    public static void exit(int status) {
        OUT.flush();
        System.exit(status);
    }
}
