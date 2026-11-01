// Java port of ga68's a68-moids-to-string.cc: pretty-print a MOID.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * A pretty printer for moids (a68-moids-to-string.cc).
 *
 * <p>For example "PROC (REF STRUCT (REF SELF, UNION (INT, VOID))) REF SELF"
 * for a procedure yielding a pointer to an object of its own mode.
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>The text is built in a {@link StringBuilder} standing for a C buffer of
 * BUFFER_SIZE chars, appended to with the semantics of a68_bufcat, i.e. the
 * text is silently truncated to BUFFER_SIZE - 1 characters.</li>
 * <li>The width {@code w} is a C {@code size_t}. It can wrap around below
 * zero, after which every width test succeeds. To reproduce this it is kept
 * in a {@code long} (64 bits, like size_t on the reference platform) and
 * compared unsigned; see {@link #ge}.</li>
 * <li>The C version returns one of MAX_MTS static buffers used round-robin;
 * the Java version returns an immutable copy of the text, so the reuse of the
 * buffers is not observable.</li>
 * </ul>
 */
public final class MoidsToString {
    private MoidsToString() {}

    /** a68.h */
    private static final int BUFFER_SIZE = 1024;
    private static final int SMALL_BUFFER_SIZE = 128;
    /** #define MAX_MTS in a68_moid_to_string. */
    private static final int MAX_MTS = 8;

    /** File-static variables of a68-moids-to-string.cc. */
    static final class State {
        /** static int mts_buff_ptr in a68_moid_to_string. */
        int mtsBuffPtr = 0;
    }

    /** {@code a >= b} for C size_t operands. */
    private static boolean ge(long a, long b) {
        return Long.compareUnsigned(a, b) >= 0;
    }

    /**
     * a68_bufcat (a68-parser.cc): safely append SRC to the NUL-terminated
     * buffer DST of LEN chars. Not available in Parser.java, so ported here
     * on the StringBuilder that represents the buffer.
     */
    private static void bufcat(StringBuilder dst, String src, int len) {
        if (src != null) {
            int d = 0;
            int s = 0;
            int n = len;
            // Find end of dst and left-adjust; do not go past end
            for (; n-- != 0 && d < dst.length(); d++) {
                ;
            }
            int dlen = d;
            n = len - dlen;
            if (n > 0) {
                dst.setLength(d);
                while (s < src.length()) {
                    if (n != 1) {
                        dst.append(src.charAt(s));
                        d++;
                        n--;
                    }
                    s++;
                }
                // d[0] = '\0'
                dst.setLength(d);
            }
            // Better sure than sorry
            if (dst.length() > len - 1) {
                dst.setLength(len - 1);
            }
        }
    }

    /** Add string to MOID text (add_to_moid_text). */
    private static void addToMoidText(A68 a68, StringBuilder dst, String str, long[] w) {
        bufcat(dst, str, BUFFER_SIZE);
        w[0] -= str.length();
    }

    /** Find a tag, searching symbol tables towards the root (find_indicant_global). */
    private static Tag findIndicantGlobal(A68 a68, Table table, Moid mode) {
        if (table != null) {
            for (Tag s = table.indicants; s != null; s = s.next) {
                if (s.type == mode) {
                    return s;
                }
            }
            return findIndicantGlobal(a68, table.previous, mode);
        } else {
            return null;
        }
    }

    /** Pack to string (pack_to_string). */
    private static void packToString(A68 a68, StringBuilder b, Pack p, long[] w, boolean text, Node idf,
            boolean indicantValue) {
        for (; p != null; p = p.next) {
            moidToString2(a68, b, p.type, w, idf, indicantValue);
            if (text) {
                if (p.text != null) {
                    addToMoidText(a68, b, " ", w);
                    addToMoidText(a68, b, p.text, w);
                }
            }
            if (p != null && p.next != null) {
                addToMoidText(a68, b, ", ", w);
            }
        }
    }

    /** Moid to string 2 (moid_to_string_2). */
    private static void moidToString2(A68 a68, StringBuilder b, Moid n, long[] w, Node idf,
            boolean indicantValue) {
        boolean supperStropping = (a68.options().stropping == Stropping.SUPPER);
        String stropSelf = supperStropping ? "self" : "SELF";
        String stropHip = supperStropping ? "hip" : "HIP";
        String stropCompl = supperStropping ? "compl" : "COMPL";
        String stropLongCompl = supperStropping ? "long compl" : "LONG COMPL";
        String stropLongLongCompl = supperStropping ? "long long compl" : "LONG LONG COMPL";
        String stropWordCompl = supperStropping ? "word compl" : "WORD COMPL";
        String stropString = supperStropping ? "string" : "STRING";
        String stropCollitem = supperStropping ? "collitem" : "COLLITEM";
        // Sic: these are not format strings, so the text contains "%%" literally.
        String stropSimplin = supperStropping ? "%%<simplin%%>" : "%%<SIMPLIN%%>";
        String stropSimplout = supperStropping ? "%%<simplout%%>" : "%%<SIMPLOUT%%>";
        String stropRows = supperStropping ? "%%<rows%%>" : "%%<ROWS%%>";
        String stropVacuum = supperStropping ? "%%<vacuum%%>" : "%%<VACUUM%%>";
        String stropLong = supperStropping ? "long" : "LONG";
        String stropShort = supperStropping ? "short" : "SHORT";
        String stropWord = supperStropping ? "word" : "WORD";
        String stropRef = supperStropping ? "ref" : "REF";
        String stropFlex = supperStropping ? "flex" : "FLEX";
        String stropStruct = supperStropping ? "struct" : "STRUCT";
        String stropUnion = supperStropping ? "union" : "UNION";
        String stropProc = supperStropping ? "proc" : "PROC";

        if (n == null) {
            // Oops. Should not happen.
            addToMoidText(a68, b, "null", w);
            return;
        }

        // Reference to self through REF or PROC.
        if (Postulates.isPostulated(a68, a68.postulates, n) != null) {
            addToMoidText(a68, b, stropSelf, w);
            return;
        }

        // If declared by a mode-declaration, present the indicant.
        if (idf != null && !(n.attribute == STANDARD)) {
            Tag indy = findIndicantGlobal(a68, idf.symbolTable, n);

            if (indy != null) {
                addToMoidText(a68, b, indy.node.info.symbol, w);
                if (!indicantValue) {
                    return;
                } else {
                    addToMoidText(a68, b, " = ", w);
                }
            }
        }

        // Write the standard modes.
        if (n == a68.modes.HIP) {
            addToMoidText(a68, b, stropHip, w);
        } else if (n == a68.modes.ERROR) {
            addToMoidText(a68, b, "ERROR", w);
        } else if (n == a68.modes.UNDEFINED) {
            addToMoidText(a68, b, "unresolved mode", w);
        } else if (n == a68.modes.C_STRING) {
            addToMoidText(a68, b, "C-STRING", w);
        } else if (n == a68.modes.COMPLEX) {
            addToMoidText(a68, b, stropCompl, w);
        } else if (n == a68.modes.LONG_COMPLEX) {
            addToMoidText(a68, b, stropLongCompl, w);
        } else if (n == a68.modes.LONG_LONG_COMPLEX) {
            addToMoidText(a68, b, stropLongLongCompl, w);
        } else if (n == a68.modes.WORD_COMPLEX) {
            addToMoidText(a68, b, stropWordCompl, w);
        } else if (n == a68.modes.STRING) {
            addToMoidText(a68, b, stropString, w);
        } else if (n == a68.modes.COLLITEM) {
            addToMoidText(a68, b, stropCollitem, w);
        } else if (n.attribute == IN_TYPE_MODE) {
            addToMoidText(a68, b, stropSimplin, w);
        } else if (n.attribute == OUT_TYPE_MODE) {
            addToMoidText(a68, b, stropSimplout, w);
        } else if (n.attribute == ROWS_SYMBOL) {
            addToMoidText(a68, b, stropRows, w);
        } else if (n == a68.modes.VACUUM) {
            addToMoidText(a68, b, stropVacuum, w);
        } else if (n.attribute == VOID_SYMBOL || n.attribute == STANDARD || n.attribute == INDICANT) {
            if (n.dim == 100 /* special DIM value for word */) {
                if (ge(w[0], "WORD ".length() + n.node.info.symbol.length())) {
                    addToMoidText(a68, b, stropWord, w);
                    addToMoidText(a68, b, " ", w);
                    String stropSymbol = Keywords.stropKeyword(a68, n.node.info.symbol);
                    addToMoidText(a68, b, stropSymbol, w);
                } else {
                    addToMoidText(a68, b, "..", w);
                }
            } else if (n.dim > 0) {
                long k = n.dim;

                if (ge(w[0], k * "LONG ".length() + n.node.info.symbol.length())) {
                    while (k-- != 0) {
                        addToMoidText(a68, b, stropLong, w);
                        addToMoidText(a68, b, " ", w);
                    }

                    String stropSymbol = Keywords.stropKeyword(a68, n.node.info.symbol);
                    addToMoidText(a68, b, stropSymbol, w);
                } else {
                    addToMoidText(a68, b, "..", w);
                }
            } else if (n.dim < 0) {
                long k = -n.dim;

                if (ge(w[0], k * "SHORT ".length() + n.node.info.symbol.length())) {
                    while (k-- != 0) {
                        addToMoidText(a68, b, stropShort, w);
                        addToMoidText(a68, b, " ", w);
                    }

                    String stropSymbol = Keywords.stropKeyword(a68, n.node.info.symbol);
                    addToMoidText(a68, b, stropSymbol, w);
                } else {
                    addToMoidText(a68, b, "..", w);
                }
            } else if (n.dim == 0) {
                String stropSymbol = Keywords.stropKeyword(a68, n.node.info.symbol);
                addToMoidText(a68, b, stropSymbol, w);
            }

            //  Write compxounded modes.
        } else if (n.attribute == REF_SYMBOL) {
            if (ge(w[0], "REF ..".length())) {
                addToMoidText(a68, b, stropRef, w);
                addToMoidText(a68, b, " ", w);
                moidToString2(a68, b, n.sub, w, idf, indicantValue);
            } else {
                addToMoidText(a68, b, stropRef, w);
                addToMoidText(a68, b, " ..", w);
            }
        } else if (n.attribute == FLEX_SYMBOL) {
            if (ge(w[0], "FLEX ..".length())) {
                addToMoidText(a68, b, stropFlex, w);
                addToMoidText(a68, b, " ", w);
                moidToString2(a68, b, n.sub, w, idf, indicantValue);
            } else {
                addToMoidText(a68, b, stropFlex, w);
                addToMoidText(a68, b, " ..", w);
            }
        } else if (n.attribute == ROW_SYMBOL) {
            long j = "[] ..".length() + (long) (n.dim - 1) * ",".length();

            if (ge(w[0], j)) {
                long k = n.dim - 1;
                addToMoidText(a68, b, "[", w);
                // C: while (k-- > 0) with k a size_t.
                while (k-- != 0) {
                    addToMoidText(a68, b, ",", w);
                }
                addToMoidText(a68, b, "] ", w);
                moidToString2(a68, b, n.sub, w, idf, indicantValue);
            } else if (n.dim == 1) {
                addToMoidText(a68, b, "[] ..", w);
            } else {
                long k = n.dim;
                addToMoidText(a68, b, "[", w);
                while (k-- != 0) {
                    addToMoidText(a68, b, ",", w);
                }
                addToMoidText(a68, b, "] ..", w);
            }
        } else if (n.attribute == STRUCT_SYMBOL) {
            long j = ("STRUCT ()".length() + (long) (n.dim - 1)
                    * ".., ".length() + "..".length());

            if (ge(w[0], j)) {
                Postulate save = a68.postulates;
                Postulates.makePostulate(a68, Ref.of(() -> a68.postulates, v -> a68.postulates = v), n, null);
                addToMoidText(a68, b, stropStruct, w);
                addToMoidText(a68, b, " (", w);
                packToString(a68, b, n.pack, w, true, idf, indicantValue);
                addToMoidText(a68, b, ")", w);
                Postulates.freePostulateList(a68, a68.postulates, save);
                a68.postulates = save;
            } else {
                long k = n.dim;
                addToMoidText(a68, b, stropStruct, w);
                addToMoidText(a68, b, " (", w);
                // C: while (k-- > 0) with k a size_t.
                while (k-- != 0) {
                    addToMoidText(a68, b, ",", w);
                }
                addToMoidText(a68, b, ")", w);
            }
        } else if (n.attribute == UNION_SYMBOL) {
            long j = ("UNION ()".length() + (long) (n.dim - 1)
                    * ".., ".length() + "..".length());

            if (ge(w[0], j)) {
                Postulate save = a68.postulates;
                Postulates.makePostulate(a68, Ref.of(() -> a68.postulates, v -> a68.postulates = v), n, null);
                addToMoidText(a68, b, stropUnion, w);
                addToMoidText(a68, b, " (", w);
                packToString(a68, b, n.pack, w, false, idf, indicantValue);
                addToMoidText(a68, b, ")", w);
                Postulates.freePostulateList(a68, a68.postulates, save);
                a68.postulates = save;
            } else {
                long k = n.dim;
                addToMoidText(a68, b, stropUnion, w);
                addToMoidText(a68, b, " (", w);
                // C: while (k-- > 0) with k a size_t.
                while (k-- != 0) {
                    addToMoidText(a68, b, ",", w);
                }
                addToMoidText(a68, b, ")", w);
            }
        } else if (n.attribute == PROC_SYMBOL && n.dim == 0) {
            if (ge(w[0], "PROC ..".length())) {
                addToMoidText(a68, b, stropProc, w);
                addToMoidText(a68, b, " ", w);
                moidToString2(a68, b, n.sub, w, idf, indicantValue);
            } else {
                addToMoidText(a68, b, stropProc, w);
                addToMoidText(a68, b, " ..", w);
            }
        } else if (n.attribute == PROC_SYMBOL && n.dim > 0) {
            long j = ("PROC () ..".length() + (long) (n.dim - 1)
                    * ".., ".length() + "..".length());

            if (ge(w[0], j)) {
                Postulate save = a68.postulates;
                Postulates.makePostulate(a68, Ref.of(() -> a68.postulates, v -> a68.postulates = v), n, null);
                addToMoidText(a68, b, stropProc, w);
                addToMoidText(a68, b, " (", w);
                packToString(a68, b, n.pack, w, false, idf, indicantValue);
                addToMoidText(a68, b, ") ", w);
                moidToString2(a68, b, n.sub, w, idf, indicantValue);
                Postulates.freePostulateList(a68, a68.postulates, save);
                a68.postulates = save;
            } else {
                long k = n.dim;

                addToMoidText(a68, b, stropProc, w);
                addToMoidText(a68, b, " (", w);
                // C: while (k-- > 0) with k a size_t.
                while (k-- != 0) {
                    addToMoidText(a68, b, ",", w);
                }
                addToMoidText(a68, b, ") ..", w);
            }
        } else if (n.attribute == SERIES_MODE || n.attribute == STOWED_MODE) {
            long j = ("()".length() + (long) (n.dim - 1)
                    * ".., ".length() + "..".length());

            if (ge(w[0], j)) {
                addToMoidText(a68, b, "(", w);
                packToString(a68, b, n.pack, w, false, idf, indicantValue);
                addToMoidText(a68, b, ")", w);
            } else {
                long k = n.dim;

                addToMoidText(a68, b, "(", w);
                // C: while (k-- > 0) with k a size_t.
                while (k-- != 0) {
                    addToMoidText(a68, b, ",", w);
                }
                addToMoidText(a68, b, ")", w);
            }
        } else {
            // C: snprintf (str, SMALL_BUFFER_SIZE, "\\%d", ATTRIBUTE (n)), which
            // only fails (gcc_unreachable) on an encoding error and cannot
            // truncate an int here.
            String str = "\\" + n.attribute.ordinal();
            if (str.length() > SMALL_BUFFER_SIZE - 1) {
                str = str.substring(0, SMALL_BUFFER_SIZE - 1);
            }
            addToMoidText(a68, b, str, w);
        }
    }

    /**
     * Pretty-formatted mode N; W is a measure of width (a68_moid_to_string),
     * with the default {@code indicant_value = false}.
     */
    public static String moidToString(A68 a68, Moid n, int w, Node idf) {
        return moidToString(a68, n, w, idf, false);
    }

    /** Pretty-formatted mode N; W is a measure of width (a68_moid_to_string). */
    public static String moidToString(A68 a68, Moid n, int w, Node idf, boolean indicantValue) {
        State st = a68.state(State.class, State::new);
        // We use a static buffer of MAX_MTS strings. This value 8 should be safe.
        // No more than MAX_MTS calls can be pending in for instance printf.  Instead
        // we could allocate each string on the heap but that leaks memory.
        StringBuilder a = new StringBuilder();
        st.mtsBuffPtr++;
        if (st.mtsBuffPtr >= MAX_MTS) {
            st.mtsBuffPtr = 0;
        }
        // a[0] = '\0'
        // C: W is a size_t; an int argument is converted with sign extension.
        long[] ww = {w};
        if (ge(ww[0], BUFFER_SIZE)) {
            ww[0] = BUFFER_SIZE - 1;
        }
        a68.postulates = null;
        if (n != null) {
            moidToString2(a68, a, n, ww, idf, indicantValue);
        } else {
            bufcat(a, "null", BUFFER_SIZE);
        }
        return a.toString();
    }
}
