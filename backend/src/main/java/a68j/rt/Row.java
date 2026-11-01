// a68j runtime: multiple values (rows), including STRING. GPLv3+.
package a68j.rt;

/**
 * A multiple value: a descriptor (bounds and strides per dimension, offset)
 * over an array of boxed elements. Slices and trims share the element array.
 * Element (i1, ..., in) is at {@code data[offset + sum((ik - lwb[k]) * stride[k])]}.
 */
public final class Row {
    /**
     * The element storage: an Object[] of boxed values, or for rows of
     * primitive modes made by generators, copies and string operations an
     * int[] (INT, CHAR, BITS, ...), long[], float[], double[] or boolean[]
     * holding the values unboxed.  Every element access goes through
     * {@link #get}/{@link #set} (or the typed variants), which handle both.
     */
    public final Object store;
    /** {@link #store} if it is an Object[], else null. */
    public final Object[] data;
    public final int dims;
    public final long[] lwb, upb, stride;
    public final int offset;
    /**
     * lwb[0], the number of indices and stride[0] of the first dimension as
     * ints (0 for a row without dimensions), for {@link #index1}: the bounds
     * arrays are never changed, and bounds are INTs.
     */
    public final int lwb0, n0, stride0;
    /**
     * Field path of a row made by selecting fields from a row of structures
     * (null otherwise): the element is field path[k] of ... of data[i].
     */
    public final int[] path;
    /**
     * Whether this row may grow in place (a68j optimisation of STRING +:=,
     * see {@link Std#append_s_s}): the elements of {@link #store} after its
     * last element are spare room that belongs to this row descriptor alone.
     * Only a row made by append_s_s has it, and it passes it on to the
     * longer row it makes; every other row sharing the store (slices, older
     * values) has it false and so never writes behind its end.
     */
    public boolean spare;

    public Row(Object store, int offset, long[] lwb, long[] upb, long[] stride) {
        this(store, offset, lwb, upb, stride, null);
    }

    public Row(Object store, int offset, long[] lwb, long[] upb, long[] stride, int[] path) {
        this.store = store;
        this.data = store instanceof Object[] o ? o : null;
        this.offset = offset;
        this.dims = lwb.length;
        this.lwb = lwb;
        this.upb = upb;
        this.stride = stride;
        this.path = path;
        this.lwb0 = dims > 0 ? (int) lwb[0] : 0;
        this.n0 = dims > 0 ? (int) Math.max(0, Math.min(Integer.MAX_VALUE, upb[0] - lwb[0] + 1)) : 0;
        this.stride0 = dims > 0 ? (int) stride[0] : 0;
    }

    /** The element at index IX of {@link #store}. */
    public Object get(int ix) {
        if (data == null) {
            return switch (store) {
                case int[] a -> a[ix];
                case long[] a -> a[ix];
                case float[] a -> a[ix];
                case double[] a -> a[ix];
                case boolean[] a -> a[ix];
                default -> throw new IllegalStateException();
            };
        }
        Object v = data[ix];
        if (path != null) {
            for (int f : path) {
                v = f == THROUGH_NAME ? ((Ref) v).get() : ((Struct) v).f[f];
            }
        }
        return v;
    }

    /**
     * A path entry: the element is the value referred to by the name stored
     * in data (a rowed name, {@link Rt#rowName}, aliases the variable).
     */
    public static final int THROUGH_NAME = -1;

    /** The object holding the last component of the path of the element at IX. */
    private Object holder(int ix) {
        Object st = data[ix];
        for (int k = 0; k < path.length - 1; k++) {
            int f = path[k];
            st = f == THROUGH_NAME ? ((Ref) st).get() : ((Struct) st).f[f];
        }
        return st;
    }

    /** Store the element at index IX of {@link #store}. */
    public void set(int ix, Object v) {
        if (path == null) {
            switch (store) {
                case Object[] a -> a[ix] = v;
                case int[] a -> a[ix] = (Integer) v;
                case long[] a -> a[ix] = (Long) v;
                case float[] a -> a[ix] = (Float) v;
                case double[] a -> a[ix] = (Double) v;
                case boolean[] a -> a[ix] = (Boolean) v;
                default -> throw new IllegalStateException();
            }
            return;
        }
        Object st = holder(ix);
        int f = path[path.length - 1];
        if (f == THROUGH_NAME) {
            ((Ref) st).set(v);
        } else {
            ((Struct) st).f[f] = v;
        }
    }

    // Typed element access: unboxed when the storage is primitive.
    public int getInt(int ix) { return store instanceof int[] a ? a[ix] : (Integer) get(ix); }
    public long getLong(int ix) { return store instanceof long[] a ? a[ix] : (Long) get(ix); }
    public float getFloat(int ix) { return store instanceof float[] a ? a[ix] : (Float) get(ix); }
    public double getDouble(int ix) { return store instanceof double[] a ? a[ix] : (Double) get(ix); }
    public boolean getBool(int ix) { return store instanceof boolean[] a ? a[ix] : (Boolean) get(ix); }

    public void setInt(int ix, int v) {
        if (store instanceof int[] a) { a[ix] = v; } else { set(ix, v); }
    }
    public void setLong(int ix, long v) {
        if (store instanceof long[] a) { a[ix] = v; } else { set(ix, v); }
    }
    public void setFloat(int ix, float v) {
        if (store instanceof float[] a) { a[ix] = v; } else { set(ix, v); }
    }
    public void setDouble(int ix, double v) {
        if (store instanceof double[] a) { a[ix] = v; } else { set(ix, v); }
    }
    public void setBool(int ix, boolean v) {
        if (store instanceof boolean[] a) { a[ix] = v; } else { set(ix, v); }
    }

    /** The name of the element at index IX of {@link #store}. */
    public Ref ref(int ix) {
        if (path == null) {
            return new ElemRef(store, ix);
        }
        Object st = holder(ix);
        int f = path[path.length - 1];
        return f == THROUGH_NAME ? (Ref) st : new FieldRef((Struct) st, f);
    }

    /** The row of field FIELD of the elements of this row of structures (sharing the elements). */
    public Row select(int field) {
        int[] np;
        if (path == null) {
            np = new int[] {field};
        } else {
            np = java.util.Arrays.copyOf(path, path.length + 1);
            np[path.length] = field;
        }
        return new Row(store, offset, lwb, upb, stride, np);
    }

    /** A new one-dimensional row [lwb:upb] with the given storage (an array, not copied). */
    public static Row ofStore(long lwb, long upb, Object store) {
        return new Row(store, 0, new long[] {lwb}, new long[] {upb}, new long[] {1});
    }

    /** A new one-dimensional row [lwb:upb] of zeroes with storage of the given kind (see {@link #make(long[], long[], char)}). */
    public static Row make1(long lwb, long upb, char kind) {
        return new Row(storage(kind, (int) Math.max(0, upb - lwb + 1)), 0, new long[] {lwb}, new long[] {upb}, new long[] {1});
    }

    /** A new one-dimensional row [lwb:upb] filled with {@code init} (not copied). */
    public static Row of1(long lwb, long upb, Object[] elems) {
        return new Row(elems, 0, new long[] {lwb}, new long[] {upb}, new long[] {1});
    }

    /** A new row with the given bounds and compact storage (elements null). */
    public static Row make(long[] lwb, long[] upb) {
        return make(lwb, upb, 'o');
    }

    /**
     * A new row with the given bounds and compact storage of the given kind:
     * 'i' int[], 'l' long[], 'f' float[], 'd' double[], 'b' boolean[] (all
     * elements 0 or false), anything else Object[] (elements null).
     */
    public static Row make(long[] lwb, long[] upb, char kind) {
        int k = lwb.length;
        long[] st = new long[k];
        long s = 1;
        for (int i = k - 1; i >= 0; i--) {
            st[i] = s;
            s *= Math.max(0, upb[i] - lwb[i] + 1);
        }
        return new Row(storage(kind, (int) s), 0, lwb, upb, st);
    }

    static Object storage(char kind, int n) {
        return switch (kind) {
            case 'i' -> new int[n];
            case 'l' -> new long[n];
            case 'f' -> new float[n];
            case 'd' -> new double[n];
            case 'b' -> new boolean[n];
            default -> new Object[n];
        };
    }

    /** The kind of {@link #store} (see {@link #make(long[], long[], char)}); 'o' for elements reached by a path. */
    public char kind() {
        if (path != null) {
            return 'o';
        }
        return switch (store) {
            case int[] a -> 'i';
            case long[] a -> 'l';
            case float[] a -> 'f';
            case double[] a -> 'd';
            case boolean[] a -> 'b';
            default -> 'o';
        };
    }

    /**
     * The index (relative to {@link #offset}) of the element with subscript
     * SUB of a row of one dimension, checking the bounds. In int arithmetic,
     * so that the JIT recognises a subscript that is a loop counter (the
     * difference cannot wrap into the range: bounds and subscripts are INTs).
     */
    public int index1(int sub, String file, int line) {
        // Small (under the JIT's MaxInlineSize of 35 bytes) so that it is
        // inlined even in code compiled without a profile, such as the code
        // after the first hot loop of a long method compiled for OSR.
        int i = sub - lwb0;
        if (i < 0 || i >= n0) {
            throw boundError(0, sub, file, line);
        }
        return i * stride0;
    }

    /** The runtime error for subscript SUB out of the bounds of dimension K. */
    private RuntimeException boundError(int k, long sub, String file, int line) {
        return Rt.abort(file, line, "bound " + sub + " out of range [" + lwb[k] + ":" + upb[k] + "]");
    }

    /** {@link #offsetOf} in int arithmetic: the part of the element index for subscript SUB in dimension K. */
    public int indexOf(int k, int sub, String file, int line) {
        if (sub < lwb[k] || sub > upb[k]) {
            throw boundError(k, sub, file, line);
        }
        return (int) ((sub - lwb[k]) * stride[k]);
    }

    /** Index into data of the element with subscript SUB in dimension K, checking the bounds. */
    public long offsetOf(int k, long sub, String file, int line) {
        if (sub < lwb[k] || sub > upb[k]) {
            throw Rt.abort(file, line, "bound " + sub + " out of range [" + lwb[k] + ":" + upb[k] + "]");
        }
        return (sub - lwb[k]) * stride[k];
    }

    /** A row of K+1 dimensions from rows of K dimensions with the same bounds (a row display). */
    public static Row ofRows(Object[] rows, String file, int line) {
        Row first = (Row) rows[0];
        int k = first.dims;
        long[] lwb = new long[k + 1];
        long[] upb = new long[k + 1];
        lwb[0] = 1;
        upb[0] = rows.length;
        for (int i = 0; i < k; i++) {
            lwb[i + 1] = first.lwb[i];
            upb[i + 1] = first.upb[i];
        }
        for (Object o : rows) {
            Row r = (Row) o;
            for (int i = 0; i < k; i++) {
                if (r.lwb[i] != first.lwb[i] || r.upb[i] != first.upb[i]) {
                    throw Rt.abort(file, line, "multiple bounds mismatch in row display");
                }
            }
        }
        char kind = first.kind();
        for (Object o : rows) {
            if (((Row) o).kind() != kind) {
                kind = 'o';
            }
        }
        Row res = make(lwb, upb, kind);
        int per = (int) first.elems();
        for (int j = 0; j < rows.length; j++) {
            Row r = (Row) rows[j];
            long[] subs = r.lwb.clone();
            for (int i = 0; i < per; i++) {
                res.set(j * per + i, Rt.copyValue(r.get(r.index(subs))));
                for (int q = k - 1; q >= 0; q--) {
                    if (++subs[q] <= r.upb[q]) {
                        break;
                    }
                    subs[q] = r.lwb[q];
                }
            }
        }
        return res;
    }

    /**
     * Builder for a slice with trimmers (lower_subscript_for_trimmers): the
     * indexer is processed dimension by dimension; the result shares the
     * elements of the sliced row.
     */
    public static final class Trim {
        public static final int LO = 1, HI = 2, AT = 4, SCRIPT = 8;
        private final Row src;
        private long off;
        private int dim, nd;
        private final long[] lw, up, st;

        public Trim(Row src, int newDims) {
            this.src = src;
            lw = new long[newDims];
            up = new long[newDims];
            st = new long[newDims];
        }

        /** A subscript in the current dimension. */
        public Trim sub(long i, String file, int line) {
            off += src.offsetOf(dim++, i, file, line);
            return this;
        }

        /** A trimmer: FLAGS says which of LO, HI and AT are given (SCRIPT: not an empty trimmer). */
        public Trim trim(int flags, long lo, long hi, long at, String file, int line) {
            long lb = src.lwb[dim], ub = src.upb[dim];
            if ((flags & LO) == 0) {
                lo = lb;
            }
            if ((flags & HI) == 0) {
                hi = ub;
            }
            if ((flags & LO) != 0 && lo < lb) {
                throw Rt.abort(file, line, "lower bound " + lo + " must be >= " + lb);
            }
            if ((flags & HI) != 0 && hi > ub) {
                throw Rt.abort(file, line, "upper bound " + hi + " must be <= " + ub);
            }
            off += (lo - lb) * src.stride[dim];
            if ((flags & SCRIPT) != 0) {
                long a = (flags & AT) != 0 ? at : 1;
                long d = lo - a;
                lo -= d;
                hi -= d;
            }
            lw[nd] = lo;
            up[nd] = hi;
            st[nd] = src.stride[dim];
            nd++;
            dim++;
            return this;
        }

        public Row row() {
            return new Row(src.store, (int) (src.offset + off), lw, up, st, src.path);
        }

        /** The name of the trimmed row (slicing a name). */
        public Ref ref() {
            return new Cell(row());
        }
    }

    /**
     * Assignation of a (copied) row to a non-flexible name holding row DST:
     * the bounds must agree, the elements are stored into DST's elements.
     */
    public void storeInto(Row dst, String file, int line) {
        for (int k = 0; k < dims; k++) {
            if (dst.lwb[k] != lwb[k] || dst.upb[k] != upb[k]) {
                throw Rt.abort(file, line, "multiple bounds mismatch in assignation: dim " + (k + 1) + ": ["
                        + dst.lwb[k] + ":" + dst.upb[k] + "] /= [" + lwb[k] + ":" + upb[k] + "]");
            }
        }
        long n = elems();
        if (n == 0) {
            return;
        }
        if (dims == 1 && stride[0] == 1 && dst.stride[0] == 1 && path == null && dst.path == null
                && kind() != 'o' && kind() == dst.kind()) {
            // Contiguous rows of the same primitive storage.
            System.arraycopy(store, offset, dst.store, dst.offset, (int) n);
            return;
        }
        long[] subs = lwb.clone();
        for (long i = 0; i < n; i++) {
            dst.set(dst.index(subs), get(index(subs)));
            for (int k = dims - 1; k >= 0; k--) {
                if (++subs[k] <= upb[k]) {
                    break;
                }
                subs[k] = lwb[k];
            }
        }
    }

    public long size(int k) {
        return Math.max(0, upb[k] - lwb[k] + 1);
    }

    /** Total number of elements. */
    public long elems() {
        long n = 1;
        for (int k = 0; k < dims; k++) {
            n *= size(k);
        }
        return n;
    }

    /** Index into {@link #data} of the element with the given subscripts (unchecked). */
    public int index(long[] subs) {
        long ix = offset;
        for (int k = 0; k < dims; k++) {
            ix += (subs[k] - lwb[k]) * stride[k];
        }
        return (int) ix;
    }

    /** Deep copy with fresh, compact element storage (value semantics). */
    public Row copy() {
        int n = (int) elems();
        char kind = kind();
        long[] st = new long[dims];
        long s = 1;
        for (int k = dims - 1; k >= 0; k--) {
            st[k] = s;
            s *= size(k);
        }
        Object d;
        if (kind != 'o' && dims == 1 && stride[0] == 1) {
            // A contiguous row of a primitive mode: copy the storage.
            d = storage(kind, n);
            if (n > 0) {
                System.arraycopy(store, offset, d, 0, n);
            }
            return new Row(d, 0, lwb.clone(), upb.clone(), st);
        }
        if (dims == 1) {
            // One dimension (also elements reached by a path): walk the
            // elements directly instead of through subscript arrays.
            Object[] o = kind == 'o' ? new Object[n] : null;
            Row res = new Row(o != null ? o : storage(kind, n), 0, lwb.clone(), upb.clone(), st);
            int ix = offset;
            for (int i = 0; i < n; i++, ix += stride0) {
                Object v = Rt.copyValue(get(ix));
                if (o != null) {
                    o[i] = v;
                } else {
                    res.set(i, v);
                }
            }
            return res;
        }
        d = storage(kind, n);
        Row res = new Row(d, 0, lwb.clone(), upb.clone(), st);
        long[] subs = lwb.clone();
        for (int i = 0; i < n; i++) {
            res.set(i, Rt.copyValue(get(index(subs))));
            for (int k = dims - 1; k >= 0; k--) {
                if (++subs[k] <= upb[k]) {
                    break;
                }
                subs[k] = lwb[k];
            }
        }
        return res;
    }

    /** Java string of a row of CHAR (code points). */
    public String asString() {
        StringBuilder sb = new StringBuilder();
        if (dims == 1) {
            for (long i = lwb[0]; i <= upb[0]; i++) {
                sb.appendCodePoint(getInt((int) (offset + (i - lwb[0]) * stride[0])));
            }
        }
        return sb.toString();
    }

    /** The code points of a one-dimensional row of CHAR. */
    public int[] codePoints() {
        int n = (int) size(0);
        if (store instanceof int[] a && stride[0] == 1) {
            return java.util.Arrays.copyOfRange(a, offset, offset + n);
        }
        int[] c = new int[n];
        for (int i = 0; i < n; i++) {
            c[i] = getInt((int) (offset + i * stride[0]));
        }
        return c;
    }

    /** A STRING (FLEX [1:n] CHAR) from a Java string. */
    public static Row ofString(String s) {
        return ofCodePoints(s.codePoints().toArray());
    }

    /** A STRING with the given code points (the array is not copied). */
    public static Row ofCodePoints(int[] cps) {
        return new Row(cps, 0, new long[] {1}, new long[] {cps.length}, new long[] {1});
    }
}
