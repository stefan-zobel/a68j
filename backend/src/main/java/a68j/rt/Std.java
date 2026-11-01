// a68j runtime: the standard prelude (reference: a68-low-prelude.cc, libga68).
// Licensed under the GNU GPL, version 3 or later.
package a68j.rt;

/**
 * Standard environment operators and procedures that are not emitted
 * inline. Method names: the mangled Algol name (monadic operators with the
 * prefix "op"), then one code per operand mode (see
 * a68j.jvm.Intrinsics.code), e.g. {@code opabs_bi} is ABS on BITS.
 *
 * <p>Machine representation as in ga68 on x86_64: SHORT SHORT INT int8,
 * SHORT INT int16, INT int32, LONG and LONG LONG INT int64; SHORT SHORT
 * BITS 8, SHORT BITS 16, BITS 32, LONG (LONG) BITS 64 bits; CHAR is a
 * Unicode code point; REAL float, LONG REAL double, LONG LONG REAL (long
 * double in ga68) double here.
 */
public final class Std {
    private Std() {}

    /** A standard procedure used as a value: METHOD is the name of its method here. */
    public static Proc procValue(String method) {
        return reflectProc(Std.class, method);
    }

    /** A procedure calling the static method NAME of C (the only one of that name). */
    static Proc reflectProc(Class<?> c, String name) {
        java.lang.reflect.Method found = null;
        for (java.lang.reflect.Method m : c.getMethods()) {
            if (m.getName().equals(name)) {
                found = m;
            }
        }
        if (found == null) {
            throw new UnsupportedOperationException("a68j: no runtime method " + name);
        }
        java.lang.reflect.Method m = found;
        return new Proc() {
            @Override
            public Object call(Object[] args) {
                try {
                    return m.invoke(null, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    if (e.getCause() instanceof RuntimeException r) {
                        throw r;
                    }
                    throw new IllegalStateException(e.getCause());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    // ------------------------------------------------------------------
    // Random numbers: glibc's rand() (TYPE_3, seed 1), as libga68 uses it.
    // ------------------------------------------------------------------

    /**
     * The state of random_r (TYPE_3: degree 31, separation 3) as a ring of
     * 31 words: RAND[f] is r[i-31] and RAND[b] is r[i-3] for the next value
     * r[i] (glibc's fptr and rptr).
     */
    private static final int[] RAND = new int[31];
    private static int randF, randB;

    static {
        srand(1);
    }

    private static void srand(int seed) {
        int[] r = new int[344];
        r[0] = seed;
        for (int i = 1; i < 31; i++) {
            long hi = r[i - 1] / 127773;
            long lo = r[i - 1] % 127773;
            long word = 16807 * lo - 2836 * hi;
            if (word < 0) {
                word += 2147483647;
            }
            r[i] = (int) word;
        }
        for (int i = 31; i < 34; i++) {
            r[i] = r[i - 31];
        }
        for (int i = 34; i < 344; i++) {
            r[i] = r[i - 31] + r[i - 3];
        }
        // r[j] at RAND[j mod 31] for the last 31 values; the next is r[344].
        for (int j = 344 - 31; j < 344; j++) {
            RAND[j % 31] = r[j];
        }
        randF = 344 % 31;
        randB = (344 - 3) % 31;
    }

    /**
     * The next value of rand(): r[i] = r[i-31] + r[i-3], result r[i] >>> 1.
     * Not synchronized unless parallel clauses run in threads (-fpar-threads).
     */
    private static int rand() {
        if (Par.threads) {
            synchronized (RAND) {
                return rand0();
            }
        }
        return rand0();
    }

    private static int rand0() {
        int f = randF;
        int b = randB;
        int v = RAND[f] + RAND[b];
        RAND[f] = v;
        randF = f == 30 ? 0 : f + 1;
        randB = b == 30 ? 0 : b + 1;
        return v >>> 1;
    }

    public static float random() { return (float) rand() / (float) Integer.MAX_VALUE; }
    public static double longrandom() { return (double) rand() / (float) Integer.MAX_VALUE; }
    public static double longlongrandom() { return (double) rand() / (float) Integer.MAX_VALUE; }

    // ------------------------------------------------------------------
    // Environment enquiries.
    // ------------------------------------------------------------------

    public static int intlengths() { return 2; }
    public static int intshorths() { return 3; }
    public static int bitslengths() { return 2; }
    public static int bitsshorths() { return 3; }
    public static int reallengths() { return 3; }
    public static int realshorths() { return 1; }
    public static int maxint() { return Integer.MAX_VALUE; }
    public static int shortmaxint() { return Short.MAX_VALUE; }
    public static int shortshortmaxint() { return Byte.MAX_VALUE; }
    public static long longmaxint() { return Long.MAX_VALUE; }
    public static long longlongmaxint() { return Long.MAX_VALUE; }
    public static int minint() { return Integer.MIN_VALUE; }
    public static int shortminint() { return Short.MIN_VALUE; }
    public static int shortshortminint() { return Byte.MIN_VALUE; }
    public static long longminint() { return Long.MIN_VALUE; }
    public static long longlongminint() { return Long.MIN_VALUE; }
    public static int bitswidth() { return 32; }
    public static int shortbitswidth() { return 16; }
    public static int shortshortbitswidth() { return 8; }
    public static int longbitswidth() { return 64; }
    public static int longlongbitswidth() { return 64; }
    public static int wordbitswidth() { return 64; }
    public static int maxbits() { return -1; }
    public static int shortmaxbits() { return 0xffff; }
    public static int shortshortmaxbits() { return 0xff; }
    public static long longmaxbits() { return -1L; }
    public static long longlongmaxbits() { return -1L; }
    public static int maxabschar() { return 0x10FFFF; }
    public static int nullcharacter() { return 0; }
    public static int flip() { return 'T'; }
    public static int flop() { return 'F'; }
    public static int blank() { return ' '; }
    public static int errorchar() { return '*'; }
    public static float maxreal() { return Float.MAX_VALUE; }
    public static double longmaxreal() { return Double.MAX_VALUE; }
    public static double longlongmaxreal() { return Double.MAX_VALUE; }
    public static float minreal() { return -Float.MAX_VALUE; }
    public static double longminreal() { return -Double.MAX_VALUE; }
    public static double longlongminreal() { return -Double.MAX_VALUE; }
    public static float smallreal() { return Math.ulp(1.0f); }
    public static double longsmallreal() { return Math.ulp(1.0); }
    public static double longlongsmallreal() { return Math.ulp(1.0); }
    public static float pi() { return (float) Math.PI; }
    public static double longpi() { return Math.PI; }
    public static double longlongpi() { return Math.PI; }
    public static float infinity() { return Float.POSITIVE_INFINITY; }
    public static float minusinfinity() { return Float.NEGATIVE_INFINITY; }
    public static double longinfinity() { return Double.POSITIVE_INFINITY; }
    public static double longminusinfinity() { return Double.NEGATIVE_INFINITY; }

    // ------------------------------------------------------------------
    // Integers.
    // ------------------------------------------------------------------

    public static int opabs_i(int a) { return Math.abs(a); }
    public static int opabs_si(int a) { return (short) Math.abs(a); }
    public static int opabs_ssi(int a) { return (byte) Math.abs(a); }
    public static long opabs_li(long a) { return Math.abs(a); }
    public static long opabs_lli(long a) { return Math.abs(a); }
    public static int opsign_i(int a) { return Integer.signum(a); }
    public static int opsign_si(int a) { return Integer.signum(a); }
    public static int opsign_ssi(int a) { return Integer.signum(a); }
    public static int opsign_li(long a) { return Long.signum(a); }
    public static int opsign_lli(long a) { return Long.signum(a); }
    public static boolean opodd_i(int a) { return (a & 1) != 0; }
    public static boolean opodd_si(int a) { return (a & 1) != 0; }
    public static boolean opodd_ssi(int a) { return (a & 1) != 0; }
    public static boolean opodd_li(long a) { return (a & 1) != 0; }
    public static boolean opodd_lli(long a) { return (a & 1) != 0; }
    public static long opleng_i(int a) { return a; }
    public static int opleng_si(int a) { return a; }
    public static int opleng_ssi(int a) { return a; }
    public static long opleng_li(long a) { return a; }
    // SHORTEN saturates at the bounds of the shorter mode, as ga68.
    public static int opshorten_i(int a) { return Math.clamp(a, Short.MIN_VALUE, Short.MAX_VALUE); }
    public static int opshorten_si(int a) { return Math.clamp(a, Byte.MIN_VALUE, Byte.MAX_VALUE); }
    public static int opshorten_li(long a) { return Math.clamp(a, Integer.MIN_VALUE, Integer.MAX_VALUE); }
    public static long opshorten_lli(long a) { return a; }

    public static float $over_i_i(int a, int b) { return (float) a / (float) b; }
    public static double $over_li_li(long a, long b) { return (double) a / (double) b; }
    public static double $over_lli_lli(long a, long b) { return (double) a / (double) b; }

    /** a ** b on integers (b >= 0). */
    public static int $times$times_i_i(int a, int b) { return (int) ipow(a, b); }
    public static int $up_i_i(int a, int b) { return (int) ipow(a, b); }
    public static int $times$times_si_i(int a, int b) { return (short) ipow(a, b); }
    public static int $times$times_ssi_i(int a, int b) { return (byte) ipow(a, b); }
    public static long $times$times_li_i(long a, int b) { return ipow(a, b); }
    public static long $times$times_lli_i(long a, int b) { return ipow(a, b); }

    private static long ipow(long a, int b) {
        long r = 1;
        for (int i = 0; i < b; i++) {
            r *= a;
        }
        return r;
    }

    // ------------------------------------------------------------------
    // Reals.
    // ------------------------------------------------------------------

    public static float opabs_r(float a) { return Math.abs(a); }
    public static double opabs_lr(double a) { return Math.abs(a); }
    public static double opabs_llr(double a) { return Math.abs(a); }
    public static int opsign_r(float a) { return a == 0 ? 0 : a > 0 ? 1 : -1; }
    public static int opsign_lr(double a) { return a == 0 ? 0 : a > 0 ? 1 : -1; }
    public static int opsign_llr(double a) { return a == 0 ? 0 : a > 0 ? 1 : -1; }
    // ROUND rounds halfway cases away from zero (lround).
    private static long lround(double a) { return a < 0 ? -Math.round(-a) : Math.round(a); }
    public static int opround_r(float a) { return (int) lround(a); }
    public static long opround_lr(double a) { return lround(a); }
    public static long opround_llr(double a) { return lround(a); }
    public static long opentier_llr(double a) { return (long) Math.floor(a); }
    public static int opfloor_r(float a) { return (int) Math.floor(a); }
    public static long opfloor_lr(double a) { return (long) Math.floor(a); }
    public static long opfloor_llr(double a) { return (long) Math.floor(a); }
    public static int opentier_r(float a) { return (int) Math.floor(a); }
    public static long opentier_lr(double a) { return (long) Math.floor(a); }
    public static double opleng_r(float a) { return a; }
    public static double opleng_lr(double a) { return a; }
    public static float opshorten_lr(double a) { return (float) a; }
    public static double opshorten_llr(double a) { return a; }
    public static float $times$times_r_i(float a, int b) { return (float) Math.pow(a, b); }
    public static float $up_r_i(float a, int b) { return (float) Math.pow(a, b); }
    public static double $times$times_lr_i(double a, int b) { return Math.pow(a, b); }
    public static float $times$times_r_r(float a, float b) { return (float) Math.pow(a, b); }
    public static double $times$times_lr_lr(double a, double b) { return Math.pow(a, b); }

    public static float sqrt_r(float a) { return (float) Math.sqrt(a); }
    public static float exp_r(float a) { return (float) Math.exp(a); }
    public static float ln_r(float a) { return (float) Math.log(a); }
    public static float log_r(float a) { return (float) Math.log10(a); }
    public static float sin_r(float a) { return (float) Math.sin(a); }
    public static float cos_r(float a) { return (float) Math.cos(a); }
    public static float tan_r(float a) { return (float) Math.tan(a); }
    public static float arcsin_r(float a) { return (float) Math.asin(a); }
    public static float arccos_r(float a) { return (float) Math.acos(a); }
    public static float arctan_r(float a) { return (float) Math.atan(a); }
    public static double longsqrt_lr(double a) { return Math.sqrt(a); }
    public static double longexp_lr(double a) { return Math.exp(a); }
    public static double longln_lr(double a) { return Math.log(a); }
    public static double longlog_lr(double a) { return Math.log10(a); }
    public static double longsin_lr(double a) { return Math.sin(a); }
    public static double longcos_lr(double a) { return Math.cos(a); }
    public static double longtan_lr(double a) { return Math.tan(a); }
    public static double longarcsin_lr(double a) { return Math.asin(a); }
    public static double longarccos_lr(double a) { return Math.acos(a); }
    public static double longarctan_lr(double a) { return Math.atan(a); }
    public static double wordsqrt_lr(double a) { return Math.sqrt(a); }
    public static double wordexp_lr(double a) { return Math.exp(a); }
    public static double wordln_lr(double a) { return Math.log(a); }
    public static double wordlog_lr(double a) { return Math.log10(a); }
    public static double wordsin_lr(double a) { return Math.sin(a); }
    public static double wordcos_lr(double a) { return Math.cos(a); }
    public static double wordtan_lr(double a) { return Math.tan(a); }
    public static double wordarcsin_lr(double a) { return Math.asin(a); }
    public static double wordarccos_lr(double a) { return Math.acos(a); }
    public static double wordarctan_lr(double a) { return Math.atan(a); }
    public static double wordmaxreal() { return Double.MAX_VALUE; }
    public static double wordminreal() { return -Double.MAX_VALUE; }
    public static double wordsmallreal() { return Math.ulp(1.0); }
    public static double wordpi() { return Math.PI; }
    public static long wordminint() { return Long.MIN_VALUE; }
    public static double longlonginfinity() { return Double.POSITIVE_INFINITY; }
    public static double longlongminusinfinity() { return Double.NEGATIVE_INFINITY; }
    public static int compllengths() { return reallengths(); }
    public static int complshorths() { return realshorths(); }

    public static double $times$times_llr_i(double a, int b) { return Math.pow(a, b); }
    public static double $times$times_llr_llr(double a, double b) { return Math.pow(a, b); }
    public static double $up_lr_i(double a, int b) { return Math.pow(a, b); }
    public static double $up_llr_i(double a, int b) { return Math.pow(a, b); }
    public static float $up_r_r(float a, float b) { return (float) Math.pow(a, b); }
    public static double $up_lr_lr(double a, double b) { return Math.pow(a, b); }
    public static double $up_llr_llr(double a, double b) { return Math.pow(a, b); }
    public static long $up_li_i(long a, int b) { return ipow(a, b); }
    public static long $up_lli_i(long a, int b) { return ipow(a, b); }
    public static int $up_si_i(int a, int b) { return (short) ipow(a, b); }
    public static int $up_ssi_i(int a, int b) { return (byte) ipow(a, b); }

    // ------------------------------------------------------------------
    // Complex numbers (a68-low-complex.cc): STRUCT (REAL re, im) with
    // boxed Float (COMPL) or Double (LONG and LONG LONG COMPL) fields.
    // ------------------------------------------------------------------

    private static double re(Struct z) { return ((Number) z.f[0]).doubleValue(); }
    private static double im(Struct z) { return ((Number) z.f[1]).doubleValue(); }

    private static Struct compl(boolean f, double re, double im) {
        return f ? new Struct(new Object[] {(float) re, (float) im}) : new Struct(new Object[] {re, im});
    }

    public static Struct i_i_i(int a, int b) { return compl(true, a, b); }
    public static Struct i_r_r(float a, float b) { return compl(true, a, b); }
    public static Struct i_li_li(long a, long b) { return compl(false, a, b); }
    public static Struct i_lli_lli(long a, long b) { return compl(false, a, b); }
    public static Struct i_lr_lr(double a, double b) { return compl(false, a, b); }
    public static Struct i_llr_llr(double a, double b) { return compl(false, a, b); }
    public static Struct $plus$times_i_i(int a, int b) { return i_i_i(a, b); }
    public static Struct $plus$times_r_r(float a, float b) { return i_r_r(a, b); }
    public static Struct $plus$times_li_li(long a, long b) { return i_li_li(a, b); }
    public static Struct $plus$times_lli_lli(long a, long b) { return i_lli_lli(a, b); }
    public static Struct $plus$times_lr_lr(double a, double b) { return i_lr_lr(a, b); }
    public static Struct $plus$times_llr_llr(double a, double b) { return i_llr_llr(a, b); }

    public static Struct opleng_cx(Struct z) { return compl(false, re(z), im(z)); }
    public static Struct opleng_lcx(Struct z) { return compl(false, re(z), im(z)); }
    public static Struct opleng_llcx(Struct z) { return compl(false, re(z), im(z)); }
    public static Struct opshorten_cx(Struct z) { return z; }
    public static Struct opshorten_lcx(Struct z) { return compl(true, re(z), im(z)); }
    public static Struct opshorten_llcx(Struct z) { return compl(false, re(z), im(z)); }

    private static Struct cmul(boolean f, Struct a, Struct b) {
        return compl(f, re(a) * re(b) - im(a) * im(b), re(a) * im(b) + im(a) * re(b));
    }

    private static Struct cdiv(boolean f, Struct a, Struct b) {
        double d = re(b) * re(b) + im(b) * im(b);
        return compl(f, (re(a) * re(b) + im(a) * im(b)) / d, (im(a) * re(b) - re(a) * im(b)) / d);
    }

    private static Struct cpow(boolean f, Struct z, int n) {
        Struct r = compl(f, 1, 0);
        for (int i = 0; i < Math.abs(n); i++) {
            r = cmul(f, r, z);
        }
        return n < 0 ? cdiv(f, compl(f, 1, 0), r) : r;
    }

    public static float opre_cx(Struct z) { return (float) re(z); }
    public static float opim_cx(Struct z) { return (float) im(z); }
    public static float opabs_cx(Struct z) { return (float) Math.hypot(re(z), im(z)); }
    public static float oparg_cx(Struct z) { return (float) Math.atan2(im(z), re(z)); }
    public static Struct opconj_cx(Struct z) { return compl(true, re(z), -im(z)); }
    public static Struct op$minus_cx(Struct z) { return compl(true, -re(z), -im(z)); }
    public static Struct op$plus_cx(Struct z) { return z; }
    public static Struct $plus_cx_cx(Struct a, Struct b) { return compl(true, re(a) + re(b), im(a) + im(b)); }
    public static Struct $minus_cx_cx(Struct a, Struct b) { return compl(true, re(a) - re(b), im(a) - im(b)); }
    public static Struct $times_cx_cx(Struct a, Struct b) { return cmul(true, a, b); }
    public static Struct $over_cx_cx(Struct a, Struct b) { return cdiv(true, a, b); }
    public static boolean $eq_cx_cx(Struct a, Struct b) { return re(a) == re(b) && im(a) == im(b); }
    public static boolean eq_cx_cx(Struct a, Struct b) { return $eq_cx_cx(a, b); }
    public static boolean $over$eq_cx_cx(Struct a, Struct b) { return !$eq_cx_cx(a, b); }
    public static boolean ne_cx_cx(Struct a, Struct b) { return !$eq_cx_cx(a, b); }
    public static Struct $times$times_cx_i(Struct z, int n) { return cpow(true, z, n); }
    public static Struct $up_cx_i(Struct z, int n) { return cpow(true, z, n); }
    public static Struct up_cx_i(Struct z, int n) { return cpow(true, z, n); }
    public static double opre_lcx(Struct z) { return re(z); }
    public static double opim_lcx(Struct z) { return im(z); }
    public static double opabs_lcx(Struct z) { return Math.hypot(re(z), im(z)); }
    public static double oparg_lcx(Struct z) { return Math.atan2(im(z), re(z)); }
    public static Struct opconj_lcx(Struct z) { return compl(false, re(z), -im(z)); }
    public static Struct op$minus_lcx(Struct z) { return compl(false, -re(z), -im(z)); }
    public static Struct op$plus_lcx(Struct z) { return z; }
    public static Struct $plus_lcx_lcx(Struct a, Struct b) { return compl(false, re(a) + re(b), im(a) + im(b)); }
    public static Struct $minus_lcx_lcx(Struct a, Struct b) { return compl(false, re(a) - re(b), im(a) - im(b)); }
    public static Struct $times_lcx_lcx(Struct a, Struct b) { return cmul(false, a, b); }
    public static Struct $over_lcx_lcx(Struct a, Struct b) { return cdiv(false, a, b); }
    public static boolean $eq_lcx_lcx(Struct a, Struct b) { return re(a) == re(b) && im(a) == im(b); }
    public static boolean eq_lcx_lcx(Struct a, Struct b) { return $eq_lcx_lcx(a, b); }
    public static boolean $over$eq_lcx_lcx(Struct a, Struct b) { return !$eq_lcx_lcx(a, b); }
    public static boolean ne_lcx_lcx(Struct a, Struct b) { return !$eq_lcx_lcx(a, b); }
    public static Struct $times$times_lcx_i(Struct z, int n) { return cpow(false, z, n); }
    public static Struct $up_lcx_i(Struct z, int n) { return cpow(false, z, n); }
    public static Struct up_lcx_i(Struct z, int n) { return cpow(false, z, n); }
    public static double opre_llcx(Struct z) { return re(z); }
    public static double opim_llcx(Struct z) { return im(z); }
    public static double opabs_llcx(Struct z) { return Math.hypot(re(z), im(z)); }
    public static double oparg_llcx(Struct z) { return Math.atan2(im(z), re(z)); }
    public static Struct opconj_llcx(Struct z) { return compl(false, re(z), -im(z)); }
    public static Struct op$minus_llcx(Struct z) { return compl(false, -re(z), -im(z)); }
    public static Struct op$plus_llcx(Struct z) { return z; }
    public static Struct $plus_llcx_llcx(Struct a, Struct b) { return compl(false, re(a) + re(b), im(a) + im(b)); }
    public static Struct $minus_llcx_llcx(Struct a, Struct b) { return compl(false, re(a) - re(b), im(a) - im(b)); }
    public static Struct $times_llcx_llcx(Struct a, Struct b) { return cmul(false, a, b); }
    public static Struct $over_llcx_llcx(Struct a, Struct b) { return cdiv(false, a, b); }
    public static boolean $eq_llcx_llcx(Struct a, Struct b) { return re(a) == re(b) && im(a) == im(b); }
    public static boolean eq_llcx_llcx(Struct a, Struct b) { return $eq_llcx_llcx(a, b); }
    public static boolean $over$eq_llcx_llcx(Struct a, Struct b) { return !$eq_llcx_llcx(a, b); }
    public static boolean ne_llcx_llcx(Struct a, Struct b) { return !$eq_llcx_llcx(a, b); }
    public static Struct $times$times_llcx_i(Struct z, int n) { return cpow(false, z, n); }
    public static Struct $up_llcx_i(Struct z, int n) { return cpow(false, z, n); }
    public static Struct up_llcx_i(Struct z, int n) { return cpow(false, z, n); }

    public static double longlongsqrt_llr(double a) { return Math.sqrt(a); }
    public static double longlongexp_llr(double a) { return Math.exp(a); }
    public static double longlongln_llr(double a) { return Math.log(a); }
    public static double longlonglog_llr(double a) { return Math.log10(a); }
    public static double longlongsin_llr(double a) { return Math.sin(a); }
    public static double longlongcos_llr(double a) { return Math.cos(a); }
    public static double longlongtan_llr(double a) { return Math.tan(a); }
    public static double longlongarcsin_llr(double a) { return Math.asin(a); }
    public static double longlongarccos_llr(double a) { return Math.acos(a); }
    public static double longlongarctan_llr(double a) { return Math.atan(a); }

    // ------------------------------------------------------------------
    // Characters and booleans.
    // ------------------------------------------------------------------

    public static int opabs_c(int c) { return c; }
    public static int eofchar() { return -1; }
    public static int replacementchar() { return 0xfffd; }

    /** REPR: valid code points are U+0000..U+D7FF and U+E000..U+10FFFF. */
    public static int oprepr_i(int c, String file, int line) {
        if ((c >= 0 && c < 0xd800) || (c >= 0xe000 && c < 0x110000)) {
            return c;
        }
        if (c < 0) {
            throw Rt.abort(file, line, c + " is not a valid character point");
        }
        throw Rt.abort(file, line, "U+" + Integer.toHexString(c) + " is not a valid character point");
    }
    public static int opabs_b(boolean b) { return b ? 1 : 0; }

    // ------------------------------------------------------------------
    // Bits (a68-low-bits.cc).
    // ------------------------------------------------------------------

    public static int opbin_i(int a) { return Rt.strict ? 0 : a; }
    public static int opbin_si(int a) { return Rt.strict ? 0 : a & 0xffff; }
    public static int opbin_ssi(int a) { return Rt.strict ? 0 : a & 0xff; }
    public static long opbin_li(long a) { return Rt.strict ? 0 : a; }
    public static long opbin_lli(long a) { return Rt.strict ? 0 : a; }
    public static int opabs_bi(int a) { return Rt.strict && a < 0 ? 0 : a; }
    public static int opabs_sbi(int a) { return Rt.strict && (short) a < 0 ? 0 : (short) a; }
    public static int opabs_ssbi(int a) { return Rt.strict && (byte) a < 0 ? 0 : (byte) a; }
    public static long opabs_lbi(long a) { return Rt.strict && a < 0 ? 0 : a; }
    public static long opabs_llbi(long a) { return Rt.strict && a < 0 ? 0 : a; }
    public static int opnot_bi(int a) { return ~a; }
    public static int opnot_sbi(int a) { return ~a & 0xffff; }
    public static int opnot_ssbi(int a) { return ~a & 0xff; }
    public static long opnot_lbi(long a) { return ~a; }
    public static long opnot_llbi(long a) { return ~a; }
    private static int w(int a, int width) {
        return width == 32 ? a : a & ((1 << width) - 1);
    }

    private static int shl(int a, int n, int width) {
        if (n >= width || n <= -width) {
            return 0;
        }
        return n >= 0 ? w(a << n, width) : w(a, width) >>> -n;
    }

    private static long shl(long a, int n) {
        if (n >= 64 || n <= -64) {
            return 0;
        }
        return n >= 0 ? a << n : a >>> -n;
    }

    public static int shl_bi_i(int a, int n) { return shl(a, n, 32); }
    public static int shl_sbi_i(int a, int n) { return shl(a, n, 16); }
    public static int shl_ssbi_i(int a, int n) { return shl(a, n, 8); }
    public static long shl_lbi_i(long a, int n) { return shl(a, n); }
    public static long shl_llbi_i(long a, int n) { return shl(a, n); }
    public static int shr_bi_i(int a, int n) { return shl(a, -n, 32); }
    public static int shr_sbi_i(int a, int n) { return shl(a, -n, 16); }
    public static int shr_ssbi_i(int a, int n) { return shl(a, -n, 8); }
    public static long shr_lbi_i(long a, int n) { return shl(a, -n); }
    public static long shr_llbi_i(long a, int n) { return shl(a, -n); }
    public static int $up_bi_i(int a, int n) { return shl(a, n, 32); }
    public static int up_bi_i(int a, int n) { return shl(a, n, 32); }
    public static int up_sbi_i(int a, int n) { return shl(a, n, 16); }
    public static int up_ssbi_i(int a, int n) { return shl(a, n, 8); }
    public static long up_lbi_i(long a, int n) { return shl(a, n); }
    public static long up_llbi_i(long a, int n) { return shl(a, n); }
    public static int down_bi_i(int a, int n) { return shl(a, -n, 32); }
    public static int down_sbi_i(int a, int n) { return shl(a, -n, 16); }
    public static int down_ssbi_i(int a, int n) { return shl(a, -n, 8); }
    public static long down_lbi_i(long a, int n) { return shl(a, -n); }
    public static long down_llbi_i(long a, int n) { return shl(a, -n); }
    public static int op$not_bi(int a) { return ~a; }
    public static int op$not_sbi(int a) { return ~a & 0xffff; }
    public static int op$not_ssbi(int a) { return ~a & 0xff; }
    public static long op$not_lbi(long a) { return ~a; }
    public static long op$not_llbi(long a) { return ~a; }
    public static long opshorten_llbi(long a) { return a; }
    public static int $down_bi_i(int a, int n) { return shl(a, -n, 32); }

    /** Bit I (1 = most significant) of a bits value of the given width; checked as ELEM. */
    private static int bit(int i, int width) {
        if (i < 1 || i > width) {
            throw Rt.abort(Rt.curFile, Rt.curLine, "bound " + i + " out of range in ELEM");
        }
        return width - i;
    }

    /** Bit I of a bits value of the given width, checked as ELEM; errors at FILE:LINE. */
    private static int bit(int i, int width, String file, int line) {
        if (i < 1 || i > width) {
            throw Rt.abort(file, line, "bound " + i + " out of range in ELEM");
        }
        return width - i;
    }

    // With the position of the formula, for the error message (as ga68).
    public static boolean elem_i_bi(int i, int a, String f, int l) { return (a >>> bit(i, 32, f, l) & 1) != 0; }
    public static boolean elem_i_sbi(int i, int a, String f, int l) { return (a >>> bit(i, 16, f, l) & 1) != 0; }
    public static boolean elem_i_ssbi(int i, int a, String f, int l) { return (a >>> bit(i, 8, f, l) & 1) != 0; }
    public static boolean elem_i_lbi(int i, long a, String f, int l) { return (a >>> bit(i, 64, f, l) & 1) != 0; }
    public static boolean elem_i_llbi(int i, long a, String f, int l) { return (a >>> bit(i, 64, f, l) & 1) != 0; }

    public static boolean elem_i_bi(int i, int a) { return (a >>> bit(i, 32) & 1) != 0; }
    public static boolean elem_i_sbi(int i, int a) { return (a >>> bit(i, 16) & 1) != 0; }
    public static boolean elem_i_ssbi(int i, int a) { return (a >>> bit(i, 8) & 1) != 0; }
    public static boolean elem_i_lbi(int i, long a) { return (a >>> bit(i, 64) & 1) != 0; }
    public static boolean elem_i_llbi(int i, long a) { return (a >>> bit(i, 64) & 1) != 0; }
    private static boolean inRange(int i, int width) {
        return i >= 0 && i < width;
    }

    // SET, CLEAR and TEST number the bits from 0 (least significant) and
    // ignore positions out of range (no runtime error).
    public static int set_bi_i(int a, int i) { return inRange(i, 32) ? a | (1 << i) : a; }
    public static int set_sbi_i(int a, int i) { return inRange(i, 16) ? a | (1 << i) : a; }
    public static int set_ssbi_i(int a, int i) { return inRange(i, 8) ? a | (1 << i) : a; }
    public static long set_lbi_i(long a, int i) { return inRange(i, 64) ? a | (1L << i) : a; }
    public static long set_llbi_i(long a, int i) { return set_lbi_i(a, i); }
    public static int clear_bi_i(int a, int i) { return inRange(i, 32) ? a & ~(1 << i) : a; }
    public static int clear_sbi_i(int a, int i) { return inRange(i, 16) ? a & ~(1 << i) : a; }
    public static int clear_ssbi_i(int a, int i) { return inRange(i, 8) ? a & ~(1 << i) : a; }
    public static long clear_lbi_i(long a, int i) { return inRange(i, 64) ? a & ~(1L << i) : a; }
    public static long clear_llbi_i(long a, int i) { return clear_lbi_i(a, i); }
    public static boolean test_bi_i(int a, int i) { return inRange(i, 32) && (a >>> i & 1) != 0; }
    public static boolean test_sbi_i(int a, int i) { return inRange(i, 16) && (a >>> i & 1) != 0; }
    public static boolean test_ssbi_i(int a, int i) { return inRange(i, 8) && (a >>> i & 1) != 0; }
    public static boolean test_lbi_i(long a, int i) { return inRange(i, 64) && (a >>> i & 1) != 0; }
    public static boolean test_llbi_i(long a, int i) { return test_lbi_i(a, i); }

    public static long wordmaxbits() { return -1L; }
    public static long wordmaxint() { return Long.MAX_VALUE; }

    public static long opleng_bi(int a) { return a & 0xffffffffL; }
    public static int opleng_sbi(int a) { return a; }
    public static int opleng_ssbi(int a) { return a; }
    public static long opleng_lbi(long a) { return a; }
    public static int opshorten_bi(int a) { return a & 0xffff; }
    public static int opshorten_sbi(int a) { return a & 0xff; }
    public static int opshorten_lbi(long a) { return (int) a; }

    // ------------------------------------------------------------------
    // Rows (LWB, UPB, ELEMS on ROWS).
    // ------------------------------------------------------------------

    private static Row row(Object r) {
        return (Row) (r instanceof Union u ? u.value : r);
    }

    private static int dimCheck(Row r, int n) {
        if (n < 1 || n > r.dims) {
            throw Rt.abort(Rt.curFile, Rt.curLine, "invalid dimension " + n + "; shall be > 0 and <= " + r.dims);
        }
        return n - 1;
    }

    public static int oplwb_rows(Object r) { return (int) row(r).lwb[0]; }
    public static int opupb_rows(Object r) { return (int) row(r).upb[0]; }
    public static int opelems_rows(Object r) { return (int) row(r).elems(); }
    public static int lwb_i_rows(int n, Object r, String f, int l) { return (int) row(r).lwb[dimCheck(row(r), n, f, l)]; }
    public static int upb_i_rows(int n, Object r, String f, int l) { return (int) row(r).upb[dimCheck(row(r), n, f, l)]; }

    private static int dimCheck(Row r, int n, String file, int line) {
        if (n < 1 || n > r.dims) {
            throw Rt.abort(file, line, "invalid dimension " + n + "; shall be > 0 and <= " + r.dims);
        }
        return n - 1;
    }

    public static int lwb_i_rows(int n, Object r) { return (int) row(r).lwb[dimCheck(row(r), n)]; }
    public static int upb_i_rows(int n, Object r) { return (int) row(r).upb[dimCheck(row(r), n)]; }
    public static int elems_i_rows(int n, Object r) { return (int) row(r).size(dimCheck(row(r), n)); }

    // ------------------------------------------------------------------
    // Strings (a68-low-strings.cc).
    // ------------------------------------------------------------------

    private static int[] cps(Row s) {
        return s.codePoints();
    }

    private static Row ofCps(int[] c) {
        return Row.ofCodePoints(c);
    }

    public static Row $plus_s_s(Row a, Row b) {
        int[] x = cps(a);
        int[] y = cps(b);
        int[] z = java.util.Arrays.copyOf(x, x.length + y.length);
        System.arraycopy(y, 0, z, x.length, y.length);
        return ofCps(z);
    }

    /**
     * s +:= t for a STRING s (a68j optimisation, see Intrinsics#applyAssigning):
     * the value of a + b as {@link #$plus_s_s}, but a row with spare room
     * after its elements, like java.util.ArrayList, so that appending to the
     * same string again needs no copy (amortised O(length of b) instead of
     * O(length of a + b)). A row A that has the room ({@link Row#spare})
     * grows in place: B is written behind its last element, which no other
     * row can see (all of them end before it), and the room passes on to the
     * result. The elements of A itself are not changed, so the old value
     * stays what it was.
     */
    public static Row append_s_s(Row a, Row b) {
        int m = b.n0;
        int[] st = room(a, m);
        int at = (st == a.store ? a.offset : 0) + a.n0;
        if (b.store instanceof int[] y && b.stride0 == 1) {
            System.arraycopy(y, b.offset, st, at, m);
        } else {
            for (int i = 0; i < m; i++) {
                st[at + i] = b.getInt(b.offset + i * b.stride0);
            }
        }
        return appended(a, st, m);
    }

    /** s +:= c for a STRING s and a CHAR c, see {@link #append_s_s}. */
    public static Row append_s_c(Row a, int c) {
        int[] st = room(a, 1);
        st[(st == a.store ? a.offset : 0) + a.n0] = c;
        return appended(a, st, 1);
    }

    /**
     * The storage for A with M more elements: the store of A if A may grow in
     * place and has the room (A then loses the room), else a new larger
     * array holding the elements of A from index 0.
     */
    private static int[] room(Row a, int m) {
        int n = a.n0;
        if (a.spare && a.lwb0 == 1 && a.dims == 1 && a.stride0 == 1 && a.store instanceof int[] x
                && x.length - a.offset - n >= m) {
            a.spare = false;
            return x;
        }
        long want = (long) n + m;
        int[] st = new int[(int) Math.min(Integer.MAX_VALUE - 8, want + (want >> 1) + 4)];
        if (a.store instanceof int[] x && a.stride0 == 1) {
            System.arraycopy(x, a.offset, st, 0, n);
        } else {
            for (int i = 0; i < n; i++) {
                st[i] = a.getInt(a.offset + i * a.stride0);
            }
        }
        return st;
    }

    /** The row [1 : UPB a + m] over ST (see {@link #room}), with the spare room. */
    private static Row appended(Row a, int[] st, int m) {
        Row r = new Row(st, st == a.store ? a.offset : 0, new long[] {1}, new long[] {(long) a.n0 + m}, new long[] {1});
        r.spare = true;
        return r;
    }

    public static Row $plus_c_s(int a, Row b) { return $plus_s_s(ofCps(new int[] {a}), b); }
    public static Row $plus_s_c(Row a, int b) { return $plus_s_s(a, ofCps(new int[] {b})); }
    public static Row $plus_c_c(int a, int b) { return ofCps(new int[] {a, b}); }

    public static Row $times_s_i(Row a, int n) {
        int[] x = cps(a);
        int k = Math.max(0, n);
        int[] z = new int[x.length * k];
        for (int i = 0; i < k; i++) {
            System.arraycopy(x, 0, z, i * x.length, x.length);
        }
        return ofCps(z);
    }

    public static Row $times_i_s(int n, Row a) { return $times_s_i(a, n); }
    public static Row $times_c_i(int c, int n) { return $times_s_i(ofCps(new int[] {c}), n); }
    public static Row $times_i_c(int n, int c) { return $times_s_i(ofCps(new int[] {c}), n); }

    private static int cmp(Row a, Row b) {
        int[] x = cps(a);
        int[] y = cps(b);
        return java.util.Arrays.compare(x, y);
    }

    public static boolean $eq_s_s(Row a, Row b) { return cmp(a, b) == 0; }
    public static boolean $over$eq_s_s(Row a, Row b) { return cmp(a, b) != 0; }
    public static boolean $lt_s_s(Row a, Row b) { return cmp(a, b) < 0; }
    public static boolean $lt$eq_s_s(Row a, Row b) { return cmp(a, b) <= 0; }
    public static boolean $gt_s_s(Row a, Row b) { return cmp(a, b) > 0; }
    public static boolean $gt$eq_s_s(Row a, Row b) { return cmp(a, b) >= 0; }
    public static boolean eq_s_s(Row a, Row b) { return cmp(a, b) == 0; }
    public static boolean ne_s_s(Row a, Row b) { return cmp(a, b) != 0; }
    public static boolean lt_s_s(Row a, Row b) { return cmp(a, b) < 0; }
    public static boolean le_s_s(Row a, Row b) { return cmp(a, b) <= 0; }
    public static boolean gt_s_s(Row a, Row b) { return cmp(a, b) > 0; }
    public static boolean ge_s_s(Row a, Row b) { return cmp(a, b) >= 0; }

    /** PLUSTO (+=:): a +=: b means b := a + b. */
    public static Ref plusto_s_ref(Row a, Ref b) {
        b.set($plus_s_s(a, (Row) b.get()));
        return b;
    }

    public static Ref $plus$eq$colon_s_ref(Row a, Ref b) { return plusto_s_ref(a, b); }
    public static Ref $plus$eq$colon_c_ref(int a, Ref b) { return plusto_c_ref(a, b); }

    public static Ref plusto_c_ref(int a, Ref b) {
        b.set($plus_c_s(a, (Row) b.get()));
        return b;
    }

    /** char_in_string (standard.a68). */
    public static boolean charinstring_c_ref_s(int c, Ref i, Row s) {
        int[] x = cps(s);
        for (int k = 0; k < x.length; k++) {
            if (x[k] == c) {
                if (i != null) {
                    i.set((int) (s.lwb[0] + k));
                }
                return true;
            }
        }
        return false;
    }
}
