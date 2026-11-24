// a68j Java library: inverse hyperbolic functions and the gamma function
// for the module Math.
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
package a68j.jlib;

/**
 * The procedures of the Algol 68 module Math (backend/lib/math.a68) that
 * java.lang.Math does not have: the inverse hyperbolic functions and the
 * gamma function. Arguments outside the domain give NaN, poles give an
 * infinity, as in java.lang.Math; nothing is a runtime error. Only static
 * methods.
 */
public final class MathFunctions {
    private MathFunctions() {
    }

    /** Above this, x and x + 1/x are the same in double precision. */
    private static final double LARGE = 0x1p28;

    private static final double LN2 = 0.6931471805599453;

    /** arcsinh x = ln (x + sqrt (x^2 + 1)), odd. */
    public static double asinh(double x) {
        double a = Math.abs(x);
        double r;
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            return x;
        } else if (a > LARGE) {
            r = Math.log(a) + LN2;
        } else if (a > 2) {
            r = Math.log(2 * a + 1 / (a + Math.sqrt(a * a + 1)));
        } else {
            double a2 = a * a;
            r = Math.log1p(a + a2 / (1 + Math.sqrt(1 + a2)));
        }
        return Math.copySign(r, x);
    }

    /** arccosh x = ln (x + sqrt (x^2 - 1)), for x &gt;= 1, else NaN. */
    public static double acosh(double x) {
        if (Double.isNaN(x) || x < 1) {
            return Double.NaN;
        } else if (x == Double.POSITIVE_INFINITY) {
            return x;
        } else if (x > LARGE) {
            return Math.log(x) + LN2;
        } else if (x > 2) {
            return Math.log(2 * x - 1 / (x + Math.sqrt(x * x - 1)));
        } else {
            double t = x - 1;
            return Math.log1p(t + Math.sqrt(2 * t + t * t));
        }
    }

    /**
     * arctanh x = ln ((1 + x) / (1 - x)) / 2, odd; an infinity at 1 and -1,
     * NaN beyond.
     */
    public static double atanh(double x) {
        double a = Math.abs(x);
        double r;
        if (Double.isNaN(x) || a > 1) {
            return Double.NaN;
        } else if (a == 1) {
            r = Double.POSITIVE_INFINITY;
        } else if (a < 0.5) {
            r = 0.5 * Math.log1p(2 * a + 2 * a * a / (1 - a));
        } else {
            r = 0.5 * Math.log1p(2 * a / (1 - a));
        }
        return Math.copySign(r, x);
    }

    /** The Lanczos approximation with g = 7 and nine coefficients. */
    private static final double G = 7;
    private static final double[] LANCZOS = {
        0.99999999999980993, 676.5203681218851, -1259.1392167224028,
        771.32342877765313, -176.61502916214059, 12.507343278686905,
        -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7,
    };
    private static final double LN_SQRT_2PI = 0.9189385332046728;

    /** The Lanczos sum for x &gt;= 0.5, shifted: x stands for x - 1. */
    private static double lanczosSum(double x) {
        double a = LANCZOS[0];
        for (int i = 1; i < LANCZOS.length; i++) {
            a += LANCZOS[i] / (x + i);
        }
        return a;
    }

    /**
     * The gamma function: (x - 1)! for the positive integers, NaN at 0 and
     * the negative integers (an infinity at plus or minus 0, as C's tgamma),
     * an infinity where the value is too large for a double.
     */
    public static double gamma(double x) {
        if (Double.isNaN(x) || x == Double.NEGATIVE_INFINITY) {
            return Double.NaN;
        } else if (x == 0) {
            return 1 / x;
        } else if (x == Math.rint(x)) {
            if (x < 0) {
                return Double.NaN;
            } else if (x > 171) {
                return Double.POSITIVE_INFINITY;
            }
            double f = 1;
            for (int i = 2; i < (int) x; i++) {
                f *= i;
            }
            return f;
        } else if (x < 0.5) {
            // Reflection: gamma (x) gamma (1 - x) = pi / sin (pi x).
            return Math.PI / (sinPi(x) * gamma(1 - x));
        } else if (x > 171.7) {
            return Double.POSITIVE_INFINITY;
        }
        double y = x - 1;
        double t = y + G + 0.5;
        // t^(y + 0.5) in two halves, which do not overflow below 171.7.
        double h = Math.pow(t, (y + 0.5) / 2);
        return Math.sqrt(2 * Math.PI) * h * Math.exp(-t) * h * lanczosSum(y);
    }

    /**
     * ln |gamma (x)|, as C's lgamma: an infinity at 0 and the negative
     * integers; it does not overflow for large x.
     */
    public static double lgamma(double x) {
        if (Double.isNaN(x)) {
            return x;
        } else if (Double.isInfinite(x)) {
            return Double.POSITIVE_INFINITY;
        } else if (x <= 0 && x == Math.rint(x)) {
            return Double.POSITIVE_INFINITY;
        } else if (x == 1 || x == 2) {
            return 0;
        } else if (x < 0.5) {
            return Math.log(Math.PI / Math.abs(sinPi(x))) - lgamma(1 - x);
        }
        double y = x - 1;
        double t = y + G + 0.5;
        return LN_SQRT_2PI + (y + 0.5) * Math.log(t) - t + Math.log(lanczosSum(y));
    }

    /**
     * ln gamma (x) of Algol 68 Genie: the logarithm of gamma, so NaN where
     * gamma is negative.
     */
    public static double lnGamma(double x) {
        return Math.log(gamma(x));
    }

    /** sin (pi x), exact at the integers and halves. */
    private static double sinPi(double x) {
        double r = x - 2 * Math.floor(x / 2); // in [0, 2)
        if (r == 0 || r == 1) {
            return 0;
        } else if (r == 0.5) {
            return 1;
        } else if (r == 1.5) {
            return -1;
        }
        return Math.sin(Math.PI * r);
    }
}
