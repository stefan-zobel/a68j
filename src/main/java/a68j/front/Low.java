// Port of a68_demangle_symbol from a68-low.cc (the rest of a68-low.cc is GCC
// GENERIC lowering and is not part of the parser port).
// Original: Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/** Partial port of a68-low.cc: symbol mangling and demangling. */
public final class Low {
    private Low() {}

    /** File-static state: the counter of numbered symbols. */
    static final class State {
        int cnt;
    }

    /**
     * get_mangled_identifier_or_indicant. Returns the symbol (C: an
     * IDENTIFIER_NODE tree).
     */
    private static String getMangledIdentifierOrIndicant(A68 a68, String name, boolean indicant,
            String mname, boolean internal, boolean numbered) {
        String numberBuf = null;
        if (numbered) {
            State st = a68.state(State.class, State::new);
            numberBuf = Integer.toString(st.cnt++);
        }

        // Now fill-in the mangled symbol.
        StringBuilder mangledName = new StringBuilder();
        if (mname != null) {
            for (int i = 0; i < mname.length(); ++i) {
                // Module names are bold words. Make sure to emit them in upper-case.
                mangledName.append(CType.toUpper(mname.charAt(i)));
            }
            mangledName.append('_');
            if (internal) {
                mangledName.append('_');
            }
        }

        for (int i = 0; i < name.length(); ++i) {
            char p = name.charAt(i);
            if (Scanner.MONADS.indexOf(p) >= 0 || Scanner.NOMADS.indexOf(p) >= 0 || p == ':') {
                char c;
                switch (p) {
                    case '%': c = 'p'; break;
                    case '^': c = 'c'; break;
                    case '&': c = 'a'; break;
                    case '+': c = 'u'; break;
                    case '-': c = 'm'; break;
                    case '~': c = 't'; break;
                    case '!': c = 'b'; break;
                    case '?': c = 'q'; break;
                    case '>': c = 'g'; break;
                    case '<': c = 'l'; break;
                    case '/': c = 's'; break;
                    case '=': c = 'e'; break;
                    case ':': c = 'o'; break;
                    case '*': c = 'r'; break;
                    default:
                        // Should not happen.
                        throw Parser.gccUnreachable();
                }
                mangledName.append(c);
                mangledName.append('_');
            } else {
                if (indicant) {
                    mangledName.append(CType.toUpper(p));
                } else {
                    mangledName.append(CType.toLower(p));
                }
            }
        }

        if (numbered) {
            mangledName.append(numberBuf);
        }
        return mangledName.toString();
    }

    /** a68_get_mangled_identifier. */
    public static String getMangledIdentifier(A68 a68, String name, String mname,
            boolean internal, boolean numbered) {
        return getMangledIdentifierOrIndicant(a68, name, false /* indicant */, mname, internal, numbered);
    }

    /** a68_get_mangled_indicant. */
    public static String getMangledIndicant(A68 a68, String name, String mname,
            boolean internal, boolean numbered) {
        return getMangledIdentifierOrIndicant(a68, name, true /* indicant */, mname, internal, numbered);
    }

    /** a68_demangle_symbol with the default is_operator = false. */
    public static String demangleSymbol(A68 a68, String mname, String symbol) {
        return demangleSymbol(a68, mname, symbol, false);
    }

    /**
     * a68_demangle_symbol: Demangle a given SYMBOL.
     *
     * <p>This function does the reverse operation than
     * get_mangled_identifier_or_indicant.
     */
    public static String demangleSymbol(A68 a68, String mname, String symbol,
            boolean isOperator) {
        Parser.gccAssert(symbol.length() >= mname.length() + 1);
        // First get rid of the module name and underscore.
        symbol = symbol.substring(mname.length() + 1);
        // Now demangle the rest.
        int size = symbol.length() + 1;
        char[] demangled = new char[size + 1];
        int o = 0;
        for (int i = 0; i < size; ++i) {
            // C: symbol[i+1] may read the NUL terminator (i + 1 == size - 1)
            // or one past it (i + 1 == size); both are not '_'.
            char next = i + 1 < symbol.length() ? symbol.charAt(i + 1) : '\0';
            char cur = i < symbol.length() ? symbol.charAt(i) : '\0';
            if (next == '_') {
                switch (cur) {
                    case 'p': demangled[o++] = '%'; break;
                    case 'c': demangled[o++] = '^'; break;
                    case 'a': demangled[o++] = '&'; break;
                    case 'u': demangled[o++] = '+'; break;
                    case 'm': demangled[o++] = '-'; break;
                    case 't': demangled[o++] = '~'; break;
                    case 'b': demangled[o++] = '!'; break;
                    case 'q': demangled[o++] = '?'; break;
                    case 'g': demangled[o++] = '>'; break;
                    case 'l': demangled[o++] = '<'; break;
                    case 's': demangled[o++] = '/'; break;
                    case 'e': demangled[o++] = '='; break;
                    case 'o': demangled[o++] = ':'; break;
                    case 'r': demangled[o++] = '*'; break;
                    default:
                        // Invalid mangling.
                        // XXX this should be checked at import time in extract.
                        throw Parser.gccUnreachable();
                }
                i += 1;
            } else {
                demangled[o++] = cur;
            }
        }
        demangled[o] = '\0';

        if (isOperator) {
            // Remove trailing digits.
            for (int i = CType.strlen(demangled, 0) - 1; i > 0; --i) {
                if (CType.isDigit(demangled[i])) {
                    demangled[i] = '\0';
                } else {
                    break;
                }
            }
        }

        return CType.cString(demangled, 0);
    }
}
