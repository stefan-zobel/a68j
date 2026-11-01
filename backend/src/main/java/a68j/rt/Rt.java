// a68j runtime: support routines and runtime errors (as libga68's ga68-error.c). GPLv3+.
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

    /** Stack size of the thread that runs the program. */
    static final long STACK_SIZE = 128L << 20;

    /**
     * Run the program (the class's run0) in a thread with a large stack.
     * A stack overflow ends the program like the segmentation fault of a
     * native ga68 program (status 139, no message).
     */
    public static void start(java.lang.invoke.MethodHandle run0) throws Throwable {
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
            throw failure[0];
        }
    }

    /** Normal end of the program. */
    public static void exit(int status) {
        OUT.flush();
        System.exit(status);
    }
}
