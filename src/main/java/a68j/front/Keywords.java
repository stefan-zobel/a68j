// Java port of ga68's a68-parser-keywords.cc.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Keyword and token tables.
 *
 * <p>Both are unbalanced binary search trees, exactly as in C. The order of
 * insertion matters: when the same text is inserted twice (for example "{"),
 * {@link #findKeyword} returns the entry inserted first, as in the original.
 */
public final class Keywords {
    private Keywords() {}

    private static boolean caseInsensitive(A68 a68) {
        return a68.options().stropping == Stropping.SUPPER;
    }

    /**
     * a68_strop_keyword: apply stropping to a keyword (given in upper case)
     * and return its written form.
     */
    public static String stropKeyword(A68 a68, String keyword) {
        if (a68.options().stropping == Stropping.SUPPER) {
            StringBuilder sb = new StringBuilder(keyword.length());
            for (int i = 0; i < keyword.length(); i++) {
                sb.append(CType.toLower(keyword.charAt(i)));
            }
            return sb.toString();
        }
        return keyword;
    }

    /** a68_add_token: intern {@code t} in the token tree and return its node. */
    public static Token addToken(A68 a68, String t) {
        if (a68.topToken == null) {
            return a68.topToken = new Token(t);
        }
        Token p = a68.topToken;
        while (true) {
            int k = CType.strcmp(t, p.text);
            if (k < 0) {
                if (p.less == null) {
                    return p.less = new Token(t);
                }
                p = p.less;
            } else if (k > 0) {
                if (p.more == null) {
                    return p.more = new Token(t);
                }
                p = p.more;
            } else {
                return p;
            }
        }
    }

    /** a68_find_keyword: find a keyword from its text. */
    public static Keyword findKeyword(A68 a68, String t) {
        Keyword p = a68.topKeyword;
        while (p != null) {
            int k = caseInsensitive(a68) ? CType.strcasecmp(t, p.text) : CType.strcmp(t, p.text);
            if (k < 0) {
                p = p.less;
            } else if (k > 0) {
                p = p.more;
            } else {
                return p;
            }
        }
        return null;
    }

    /** a68_find_keyword_from_attribute. */
    public static Keyword findKeywordFromAttribute(Keyword p, Attribute a) {
        if (p == null) {
            return null;
        } else if (a == p.attribute) {
            return p;
        }
        Keyword z = findKeywordFromAttribute(p.less, a);
        if (z != null) {
            return z;
        }
        return findKeywordFromAttribute(p.more, a);
    }

    /** add_keyword: equal keys go to the right (MORE), as in C. */
    private static void addKeyword(A68 a68, Attribute a, String t) {
        Keyword z = new Keyword(a, t);
        if (a68.topKeyword == null) {
            a68.topKeyword = z;
            return;
        }
        Keyword p = a68.topKeyword;
        while (true) {
            int k = caseInsensitive(a68) ? CType.strcasecmp(t, p.text) : CType.strcmp(t, p.text);
            if (k < 0) {
                if (p.less == null) {
                    p.less = z;
                    return;
                }
                p = p.less;
            } else {
                if (p.more == null) {
                    p.more = z;
                    return;
                }
                p = p.more;
            }
        }
    }

    /** a68_set_up_tables: make tables of keywords and non-terminals. */
    public static void setUpTables(A68 a68) {
        Options o = a68.options();
        // Entries are randomised to balance the tree.
        if (!o.strict) {
            // Symbols from GNU extensions.
            addKeyword(a68, ANDF_SYMBOL, "ANDTH");
            addKeyword(a68, ORF_SYMBOL, "OREL");
            addKeyword(a68, BRIEF_COMMENT_BEGIN_SYMBOL, "{");
            addKeyword(a68, BRIEF_COMMENT_END_SYMBOL, "}");
            addKeyword(a68, STYLE_I_PRAGMAT_BEGIN_SYMBOL, "[[");
            addKeyword(a68, STYLE_I_PRAGMAT_END_SYMBOL, "]]");
            addKeyword(a68, QUOTE_SYMBOL, "'");
            addKeyword(a68, WORD_SYMBOL, "WORD");

            if (o.stropping != Stropping.SUPPER) {
                addKeyword(a68, BOLD_COMMENT_BEGIN_SYMBOL, "NOTE");
                addKeyword(a68, BOLD_COMMENT_END_SYMBOL, "ETON");
                addKeyword(a68, BOLD_PRAGMAT_BEGIN_SYMBOL, "ORDER");
                addKeyword(a68, BOLD_PRAGMAT_END_SYMBOL, "REDRO");
            }

            // Symbols from MR.
            addKeyword(a68, ACCESS_SYMBOL, "ACCESS");
            addKeyword(a68, DEF_SYMBOL, "DEF");
            addKeyword(a68, POSTLUDE_SYMBOL, "POSTLUDE");
            addKeyword(a68, FED_SYMBOL, "FED");
            addKeyword(a68, FORMAL_NEST_SYMBOL, "NEST");
            addKeyword(a68, MODULE_SYMBOL, "MODULE");
            addKeyword(a68, EGG_SYMBOL, "EGG");
            addKeyword(a68, PUBLIC_SYMBOL, "PUB");
        }

        if (o.stropping != Stropping.SUPPER) {
            // The following representations do not work well with stropping
            // regimes in which reserved words live in the same namespace than
            // tags/identifiers. The alternative "brief" representations for
            // these symbols shall be used instead.
            addKeyword(a68, STYLE_I_COMMENT_SYMBOL, "CO");
            addKeyword(a68, BOLD_COMMENT_SYMBOL, "COMMENT");
            addKeyword(a68, STYLE_II_COMMENT_SYMBOL, "#");
            addKeyword(a68, BOLD_PRAGMAT_SYMBOL, "PRAGMAT");
            addKeyword(a68, STYLE_I_PRAGMAT_SYMBOL, "PR");
        }

        addKeyword(a68, POINT_SYMBOL, ".");
        addKeyword(a68, COLON_SYMBOL, ":");
        addKeyword(a68, THEN_BAR_SYMBOL, "|");
        addKeyword(a68, SUB_SYMBOL, "[");
        addKeyword(a68, BY_SYMBOL, "BY");
        addKeyword(a68, OP_SYMBOL, "OP");
        addKeyword(a68, COMMA_SYMBOL, ",");
        addKeyword(a68, AT_SYMBOL, "AT");
        addKeyword(a68, PRIO_SYMBOL, "PRIO");
        addKeyword(a68, END_SYMBOL, "END");
        addKeyword(a68, GO_SYMBOL, "GO");
        addKeyword(a68, TO_SYMBOL, "TO");
        addKeyword(a68, ELSE_BAR_SYMBOL, "|:");
        addKeyword(a68, THEN_SYMBOL, "THEN");
        addKeyword(a68, TRUE_SYMBOL, "TRUE");
        addKeyword(a68, PROC_SYMBOL, "PROC");
        addKeyword(a68, FOR_SYMBOL, "FOR");
        addKeyword(a68, GOTO_SYMBOL, "GOTO");
        addKeyword(a68, WHILE_SYMBOL, "WHILE");
        addKeyword(a68, IS_SYMBOL, ":=:");
        addKeyword(a68, ASSIGN_TO_SYMBOL, "=:");
        addKeyword(a68, COMPL_SYMBOL, "COMPL");
        addKeyword(a68, FROM_SYMBOL, "FROM");
        addKeyword(a68, DO_SYMBOL, "DO");
        addKeyword(a68, CASE_SYMBOL, "CASE");
        addKeyword(a68, LOC_SYMBOL, "LOC");
        addKeyword(a68, CHAR_SYMBOL, "CHAR");
        addKeyword(a68, ISNT_SYMBOL, ":/=:");
        addKeyword(a68, REF_SYMBOL, "REF");
        addKeyword(a68, NIL_SYMBOL, "NIL");
        addKeyword(a68, ASSIGN_SYMBOL, ":=");
        addKeyword(a68, FI_SYMBOL, "FI");
        addKeyword(a68, FILE_SYMBOL, "FILE");
        addKeyword(a68, PAR_SYMBOL, "PAR");
        addKeyword(a68, ASSERT_SYMBOL, "ASSERT");
        addKeyword(a68, OUSE_SYMBOL, "OUSE");
        addKeyword(a68, IN_SYMBOL, "IN");
        addKeyword(a68, LONG_SYMBOL, "LONG");
        addKeyword(a68, SEMI_SYMBOL, ";");
        addKeyword(a68, EMPTY_SYMBOL, "EMPTY");
        addKeyword(a68, MODE_SYMBOL, "MODE");
        addKeyword(a68, IF_SYMBOL, "IF");
        addKeyword(a68, OD_SYMBOL, "OD");
        addKeyword(a68, OF_SYMBOL, "OF");
        addKeyword(a68, STRUCT_SYMBOL, "STRUCT");
        addKeyword(a68, BUS_SYMBOL, "]");
        addKeyword(a68, SKIP_SYMBOL, "SKIP");
        addKeyword(a68, SHORT_SYMBOL, "SHORT");
        addKeyword(a68, IS_SYMBOL, "IS");
        addKeyword(a68, ESAC_SYMBOL, "ESAC");
        addKeyword(a68, CHANNEL_SYMBOL, "CHANNEL");
        addKeyword(a68, REAL_SYMBOL, "REAL");
        addKeyword(a68, STRING_SYMBOL, "STRING");
        addKeyword(a68, BOOL_SYMBOL, "BOOL");
        addKeyword(a68, ISNT_SYMBOL, "ISNT");
        addKeyword(a68, FALSE_SYMBOL, "FALSE");
        addKeyword(a68, UNION_SYMBOL, "UNION");
        addKeyword(a68, OUT_SYMBOL, "OUT");
        // Sic: the C original maps "{" to BRIEF_COMMENT_END_SYMBOL here. With
        // GNU extensions enabled the earlier "{" entry wins in lookups.
        addKeyword(a68, BRIEF_COMMENT_END_SYMBOL, "{");
        addKeyword(a68, OPEN_SYMBOL, "(");
        addKeyword(a68, BEGIN_SYMBOL, "BEGIN");
        addKeyword(a68, FLEX_SYMBOL, "FLEX");
        addKeyword(a68, VOID_SYMBOL, "VOID");
        addKeyword(a68, BITS_SYMBOL, "BITS");
        addKeyword(a68, ELSE_SYMBOL, "ELSE");
        addKeyword(a68, EXIT_SYMBOL, "EXIT");
        addKeyword(a68, HEAP_SYMBOL, "HEAP");
        addKeyword(a68, INT_SYMBOL, "INT");
        addKeyword(a68, BYTES_SYMBOL, "BYTES");
        addKeyword(a68, SEMA_SYMBOL, "SEMA");
        addKeyword(a68, CLOSE_SYMBOL, ")");
        addKeyword(a68, AT_SYMBOL, "@");
        addKeyword(a68, ELIF_SYMBOL, "ELIF");
    }
}
