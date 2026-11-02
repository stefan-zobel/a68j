// a68j runtime: the values of a row display of constants. GPLv3+.
package a68j.rt;

/**
 * The values of a row display whose elements are constants, such as
 * {@code [] int primes = (2, 3, 5, 7, ...)} or a matrix of reals
 * (a68j.jvm.Tables, docs/optimizations.md section 12). The compiler writes
 * the values as text into a string constant of the class; the class
 * initializer decodes it once with {@link #decode} into a static field, and
 * each evaluation of the display makes a new row of copies of the values
 * with {@link #row()}, or with {@link #store()} and {@link #row(Object)}
 * when some elements of the display are not constants and are stored into
 * the copy before the row is made. So the code for the display does not
 * grow with the number of its elements.
 *
 * <p>The text is {@code KIND DIMS N1 ... Nd VALUES}, the parts separated by
 * a blank:
 * <ul>
 * <li>KIND: the storage of the elements, as {@link Row#make(long[], long[], char)}:
 *     {@code i} int[] (INT, CHAR, BITS), {@code l} long[], {@code f}
 *     float[], {@code d} double[], {@code b} boolean[], and {@code s} for
 *     STRING elements (an Object[] of rows of code points);</li>
 * <li>DIMS, N1 ... Nd: the number of dimensions and the number of elements
 *     in each (the bounds are 1 : Nk, as for a row display);</li>
 * <li>VALUES: the elements in row-major order. Numbers in decimal, each
 *     followed by a blank, a float or double as {@link Float#toString} or
 *     {@link Double#toString} writes it (the conversion back gives the same
 *     value); booleans as the characters 0 and 1 without blanks; strings
 *     as the number of their UTF-16 chars, a colon and the chars.</li>
 * </ul>
 */
public final class Table {
    /** The kind of storage (see the class comment). */
    private final char kind;
    /** The upper bounds, one per dimension (the lower bounds are 1). */
    private final long[] upb;
    /**
     * The values: an int[], long[], float[], double[] or boolean[] in the
     * form of the storage of a row, or for kind {@code s} an int[][] of
     * the code points of each string.
     */
    private final Object values;

    private Table(char kind, long[] upb, Object values) {
        this.kind = kind;
        this.upb = upb;
        this.values = values;
    }

    /** The table of the text TEXT (see the class comment). */
    public static Table decode(String text) {
        Reader r = new Reader(text);
        char kind = text.charAt(0);
        r.pos = 2;
        int dims = (int) r.number();
        long[] upb = new long[dims];
        int n = 1;
        for (int k = 0; k < dims; k++) {
            upb[k] = r.number();
            n = Math.multiplyExact(n, (int) upb[k]);
        }
        Object values = switch (kind) {
            case 'i' -> {
                int[] a = new int[n];
                for (int i = 0; i < n; i++) {
                    a[i] = (int) r.number();
                }
                yield a;
            }
            case 'l' -> {
                long[] a = new long[n];
                for (int i = 0; i < n; i++) {
                    a[i] = r.number();
                }
                yield a;
            }
            case 'f' -> {
                float[] a = new float[n];
                for (int i = 0; i < n; i++) {
                    a[i] = Float.parseFloat(r.word());
                }
                yield a;
            }
            case 'd' -> {
                double[] a = new double[n];
                for (int i = 0; i < n; i++) {
                    a[i] = Double.parseDouble(r.word());
                }
                yield a;
            }
            case 'b' -> {
                boolean[] a = new boolean[n];
                for (int i = 0; i < n; i++) {
                    a[i] = text.charAt(r.pos++) == '1';
                }
                yield a;
            }
            case 's' -> {
                int[][] a = new int[n][];
                for (int i = 0; i < n; i++) {
                    int c = text.indexOf(':', r.pos);
                    int len = Integer.parseInt(text, r.pos, c, 10);
                    a[i] = text.substring(c + 1, c + 1 + len).codePoints().toArray();
                    r.pos = c + 1 + len;
                }
                yield a;
            }
            default -> throw new IllegalArgumentException("table of kind " + kind);
        };
        if (r.pos != text.length()) {
            throw new IllegalArgumentException("table text not used up");
        }
        return new Table(kind, upb, values);
    }

    /**
     * A new storage with copies of the values: an array of the same type,
     * or for strings a new Object[] of new rows (as Row#ofString makes them).
     */
    public Object store() {
        return switch (values) {
            case int[] a -> a.clone();
            case long[] a -> a.clone();
            case float[] a -> a.clone();
            case double[] a -> a.clone();
            case boolean[] a -> a.clone();
            case int[][] a -> {
                Object[] s = new Object[a.length];
                for (int i = 0; i < a.length; i++) {
                    s[i] = Row.ofCodePoints(a[i].clone());
                }
                yield s;
            }
            default -> throw new IllegalStateException();
        };
    }

    /**
     * The row [1 : N1, ..., 1 : Nd] with the storage STORE (made by
     * {@link #store}, not copied), laid out as Row#make lays out a new row:
     * the same row as GenRows#collateral makes with Row#ofStore, Row#of1
     * or Row#ofRows.
     */
    public Row row(Object store) {
        int dims = upb.length;
        long[] lwb = new long[dims];
        long[] st = new long[dims];
        long s = 1;
        for (int k = dims - 1; k >= 0; k--) {
            lwb[k] = 1;
            st[k] = s;
            s *= upb[k];
        }
        return new Row(store, 0, lwb, upb.clone(), st);
    }

    /** A new row of copies of the values. */
    public Row row() {
        return row(store());
    }

    /** The kind of storage (see the class comment), for tests. */
    public char kind() {
        return kind;
    }

    /** Reads the numbers and words of a table text. */
    private static final class Reader {
        final String text;
        int pos;

        Reader(String text) {
            this.text = text;
        }

        /** The word at pos, up to the next blank; pos after the blank. */
        String word() {
            int e = text.indexOf(' ', pos);
            String w = text.substring(pos, e);
            pos = e + 1;
            return w;
        }

        /** The decimal number at pos, up to the next blank; pos after the blank. */
        long number() {
            int e = text.indexOf(' ', pos);
            long v = Long.parseLong(text, pos, e, 10);
            pos = e + 1;
            return v;
        }
    }
}
