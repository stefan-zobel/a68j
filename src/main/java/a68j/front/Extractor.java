// Java port of ga68's a68-parser-extract.cc: extract tags from phrases.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

import java.math.BigInteger;

/**
 * This is part of the bottom-up parser. Here is a set of routines that gather
 * definitions from phrases. This way we can apply tags before defining them.
 * These routines do not look very elegant as they have to scan through all kind
 * of symbols to find a pattern that they recognise.
 */
public final class Extractor {
    private Extractor() {}

    /** a68.h: MAX_PRIORITY. */
    private static final int MAX_PRIORITY = 9;

    /** Struct assignment {@code *INFO (q) = *INFO (p)}. */
    private static void copyNodeInfo(NodeInfo dst, NodeInfo src) {
        dst.procedureLevel = src.procedureLevel;
        dst.priority = src.priority;
        dst.charInLine = src.charInLine;
        dst.commentType = src.commentType;
        dst.comment = src.comment;
        dst.commentLine = src.commentLine;
        dst.commentCharInLine = src.commentCharInLine;
        dst.pragmatType = src.pragmatType;
        dst.pragmat = src.pragmat;
        dst.pragmatLine = src.pragmatLine;
        dst.pragmatCharInLine = src.pragmatCharInLine;
        dst.symbol = src.symbol;
        dst.line = src.line;
    }

    /** Struct assignment {@code *GINFO (q) = *GINFO (p)}. */
    private static void copyGenieInfo(GInfo dst, GInfo src) {
        dst.partialProc = src.partialProc;
        dst.partialLocale = src.partialLocale;
    }

    /** insert_alt_equals: Insert alt equals symbol. */
    private static void insertAltEquals(A68 a68, Node p) {
        Node q = Parser.newNode(a68);
        q.copyFrom(p);
        q.info = Parser.newNodeInfo(a68);
        copyNodeInfo(q.info, p.info);
        q.genie = Parser.newGenieInfo(a68);
        copyGenieInfo(q.genie, p.genie);
        q.attribute = ALT_EQUALS_SYMBOL;
        q.info.symbol = Keywords.addToken(a68, "=").text;
        p.next = q;
        q.previous = p;
        if (q.next != null) {
            q.next.previous = q;
        }
    }

    /** detect_redefined_keyword: Detect redefined keyword. */
    private static void detectRedefinedKeyword(A68 a68, Node p, Attribute construct) {
        if (p != null && Parser.whether(a68, p, KEYWORD, EQUALS_SYMBOL)) {
            Fmt a = Fmt.attr(construct);
            a68.diagnostics.error(p, "attempt to redefine keyword %s in %e",
                    p.info.symbol, a);
        }
    }

    /** skip_module_text: Skip anything until a FED or ALT_ACCESS_SYMBOL is found. */
    private static Node skipModuleText(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(FED_SYMBOL) || p.is(ALT_ACCESS_SYMBOL)) {
                return p;
            }
        }

        return null;
    }

    /** skip_unit: Skip anything until a comma, semicolon or EXIT is found. */
    private static Node skipUnit(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(COMMA_SYMBOL)) {
                return p;
            } else if (p.is(SEMI_SYMBOL)) {
                return p;
            } else if (p.is(EXIT_SYMBOL)) {
                return p;
            }
        }
        return null;
    }

    /**
     * find_tag_definition: Attribute of entry in symbol table.
     *
     * <p>Returns the sum of the C enum values (ordinals) of INDICANT, OPERATOR
     * and MODULE_INDICANT for each kind of definition found, 0 if none. This is
     * kept an {@code int} because, as in C, the sum need not be an attribute.
     */
    private static int findTagDefinition(A68 a68, Table table, String name) {
        if (table != null) {
            int ret = 0;
            boolean found = false;
            for (Tag s = table.indicants; s != null && !found; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    ret += INDICANT.ordinal();
                    found = true;
                }
            }
            found = false;
            for (Tag s = table.operators; s != null && !found; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    ret += OPERATOR.ordinal();
                    found = true;
                }
            }
            found = false;
            for (Tag s = table.modules; s != null && !found; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    ret += MODULE_INDICANT.ordinal();
                    found = true;
                }
            }

            if (ret == 0) {
                return findTagDefinition(a68, table.previous, name);
            } else {
                return ret;
            }
        } else {
            return 0;
        }
    }

    /** Whether S is the language indication Java (-fnest-java): Java in SUPPER, JAVA in UPPER stropping. */
    public static boolean isJavaLanguage(String s) {
        return s.equals("Java") || s.equals("JAVA");
    }

    /**
     * a68_elaborate_bold_tags: Fill in whether bold tag is operator, indicant,
     * module indicant or language indicant.
     */
    public static void elaborateBoldTags(A68 a68, Node p) {
        for (Node q = p; q != null; q = q.next) {
            if (q.is(BOLD_TAG)) {
                if (q.previous != null
                        && q.previous.is(FORMAL_NEST_SYMBOL)) {
                    if (!q.info.symbol.equals("C")
                            && !(a68.options().nestJava && isJavaLanguage(q.info.symbol))) {
                        Fmt s = Fmt.symbol(q);
                        a68.diagnostics.error(q, "%e is not a valid language indication", s);
                    } else {
                        q.attribute = LANGUAGE_INDICANT;
                    }
                } else {
                    // C: switch (find_tag_definition (...)) with cases 0,
                    // INDICANT, OPERATOR and MODULE_INDICANT; sums of several
                    // kinds match no case and are left alone.
                    int def = findTagDefinition(a68, q.symbolTable, q.info.symbol);
                    if (def == 0) {
                        Fmt s = Fmt.symbol(q);
                        a68.diagnostics.error(q, "indicant %e has not been declared properly", s);
                    } else if (def == INDICANT.ordinal()) {
                        q.attribute = INDICANT;
                    } else if (def == OPERATOR.ordinal()) {
                        q.attribute = OPERATOR;
                    } else if (def == MODULE_INDICANT.ordinal()) {
                        q.attribute = MODULE_INDICANT;
                    }
                }
            }
        }
    }

    /** skip_pack_declarer: Skip declarer, or argument pack and declarer. */
    private static Node skipPackDeclarer(A68 a68, Node p) {
        // Skip PUB () REF [] REF FLEX [] [] ...
        while (p != null
                && (Parser.isOneOf(a68, p, SUB_SYMBOL, OPEN_SYMBOL, REF_SYMBOL,
                        FLEX_SYMBOL, SHORT_SYMBOL, LONG_SYMBOL))) {
            p = p.next;
        }

        // Skip STRUCT (), UNION () or PROC [()].
        if (p != null && (Parser.isOneOf(a68, p, STRUCT_SYMBOL, UNION_SYMBOL))) {
            return p.next;
        } else if (p != null && p.is(PROC_SYMBOL)) {
            return skipPackDeclarer(a68, p.next);
        } else {
            return p;
        }
    }

    /**
     * a68_extract_revelation: Extract the revelation associated with the
     * module MODULE.
     *
     * <p>The node Q is used for symbol table and diagnostic purposes
     *
     * <p>Publicized modules are recursively extracted as well. This call may
     * result in one or more errors.
     *
     * <p>If FILENAME is not NULL then the module exports are looked in
     * libFILENAME.so, FILENAME.o, etc. If it is NULL, the filename is derived
     * from the module name.
     *
     * <p>This function is visible externally because it is used to extract
     * revelations of modules distributed as part of libga68, in
     * a68-parser-prelude.cc
     */
    public static void extractRevelation(A68 a68, Node q, String module, String filename,
            Tag tag) {
        // Import the MOIF and install it in the tag.
        Moif moif = Imports.openPacket(a68, module, filename);
        if (moif == null) {
            a68.diagnostics.error(q, "cannot find module %qs", module);
            return;
        }

        if (tag != null) {
            tag.moif = moif;
        }

        // Store all the modes from the MOIF in the moid list.
        //
        // The front-end depends on being able to compare any two modes by
        // pointer value. For example, the parser mode equivalence and coercion
        // code relies on this. The lowerer also relies on this to make sure
        // the same lowered trees are used for the same modes.

        for (Moid m : moif.modes) {
            // Note that m == NO_MOID if the imported mode was already known to
            // the compiler and has been replaced.
            if (m != null) {
                Moid r = ModeBuilder.registerExtraMode(a68,
                        Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), m);
                if (r != m) {
                    System.out.printf("r: %s\n", MoidsToString.moidToString(a68, r, 80, null, false));
                    System.out.printf("m: %s\n", MoidsToString.moidToString(a68, m, 80, null, false));
                    throw Parser.gccUnreachable();
                }
            }
        }

        // Second thing to do is to extract the revelations of publicized
        // modules in this moif. This leads to recursive calls of this
        // function. Note that this should be done _after_ the modes get added
        // to the global list of modes so mode deduplication in a68_open_packet
        // in the recursive extract_revelation calls is properly done.

        for (Extract e : moif.modules) {
            extractRevelation(a68, q, e.symbol, filename, null);
        }

        // Store mode indicants from the MOIF in the symbol table,
        // and also in the moid list.
        for (Extract e : moif.indicants) {
            // Indicants stored in the indicants area of a symbol
            // table are expected to be INDICANT nodes originating in
            // parsing, with the following subtree:
            //
            // INDICANT - EQUALS_SYMBOL - DECLARER
            //
            // where MOID (DECLARER) is determined to be the mode
            // associated by INDICANT, or its "equivalent" mode.
            // Therefore we have to synthesize something like that
            // here, since the mode comes from a module interface and
            // we don't have a declarer tree for it..

            // INDICANT node.
            Node n = Parser.someNode(a68, Low.demangleSymbol(a68, moif.name,
                    e.symbol));
            n.type = e.mode;
            // EQUALS_SYMBOL node.
            n.next = Parser.someNode(a68, "=");
            n.next.attribute = EQUALS_SYMBOL;
            // DECLARER node.
            n.next.next = Parser.someNode(a68, "");
            n.info.line = q.info.line;
            // C: NCHAR_IN_LINE (n) = STRING (LINE (INFO (n))), the start of the line.
            n.info.charInLine = 0;
            n.next.next.type = e.mode;
            n.symbolTable = q.symbolTable;

            // Now add the INDICANT subtree to the symbol table of
            // this access clause lexical level.
            // C: the local is called tag, shadowing the parameter.
            Tag t = Taxes.addTag(a68, q.symbolTable, INDICANT, n, null, 0);
            Parser.gccAssert(t != null);

            // Finally add the mode indicant to the moids list. Note
            // that the mode in DECLARER is associated to the
            // INDICANT in the table at some point later, via
            // EQUIVALENT. Hence the NO_MOID in the call to
            // a68_add_mode.
            Moid effectiveMode = ModeBuilder.addMode(a68,
                    Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v), INDICANT,
                    0, n, null, null);
            Parser.gccAssert(effectiveMode != null);
        }

        // Store priorities from the MOIF.
        for (Extract e : moif.prios) {
            Node n
                    = Parser.someNode(a68, Low.demangleSymbol(a68, moif.name,
                            e.symbol));
            // XXX sensible location somehow?
            n.info.line = q.info.line;
            // C: NCHAR_IN_LINE (n) = STRING (LINE (INFO (n))), the start of the line.
            n.info.charInLine = 0;
            n.symbolTable = q.symbolTable;
            // C: the local is called tag, shadowing the parameter.
            Tag t = Taxes.addTag(a68, q.symbolTable, PRIO_SYMBOL, n, null,
                    e.priority);
            Parser.gccAssert(t != null);
            t.moif = moif; // XXX add to existing list of moifs.
        }

        // Store identifiers from the MOIF.
        for (Extract e : moif.identifiers) {
            Node n
                    = Parser.someNode(a68, Low.demangleSymbol(a68, moif.name,
                            e.symbol));
            // XXX sensible location somehow?
            n.info.line = q.info.line;
            // C: NCHAR_IN_LINE (n) = STRING (LINE (INFO (n))), the start of the line.
            n.info.charInLine = 0;
            n.symbolTable = q.symbolTable;

            // It is necessary to resolve equivalences because the dummy
            // declarer is not in the parse tree and therefore it is not handled
            // by the call to computed_derived_modes in a68_make_moid_list.
            ModeBuilder.resolveEquivalent(a68, Ref.of(() -> e.mode, v -> e.mode = v));

            n.type = e.mode;
            // C: the local is called tag, shadowing the parameter.
            Tag t = Taxes.addTag(a68, q.symbolTable, IDENTIFIER,
                    n, e.mode, NORMAL_IDENTIFIER.ordinal());
            Parser.gccAssert(t != null);

            t.externSymbol = e.symbol;
            t.variable = e.variable;
            t.inProc = e.inProc;
            t.heap = STATIC_SYMBOL;
            t.moif = moif;
        }

        // Store operators from the MOIF.
        for (Extract e : moif.operators) {
            Node n
                    = Parser.someNode(a68, Low.demangleSymbol(a68, moif.name,
                            e.symbol,
                            true /* operator */));
            n.type = e.mode;
            n.info.line = q.info.line;
            // C: NCHAR_IN_LINE (n) = STRING (LINE (INFO (n))), the start of the line.
            n.info.charInLine = 0;
            n.symbolTable = q.symbolTable;
            // C: the local is called tag, shadowing the parameter.
            Tag t = Taxes.addTag(a68, q.symbolTable, OP_SYMBOL,
                    n, e.mode, STOP.ordinal());
            Parser.gccAssert(t != null);
            t.variable = e.variable;
            t.inProc = e.inProc;
            t.heap = STATIC_SYMBOL;
            t.moif = moif;
            t.externSymbol = e.symbol;
        }
    }

    /** Overload for the C default argument {@code tag = NO_TAG}. */
    public static void extractRevelation(A68 a68, Table t, Line l, String module, String filename) {
        extractRevelation(a68, t, l, module, filename, null);
    }

    /** Overload for the C default argument {@code tag = NO_TAG}. */
    public static void extractRevelation(A68 a68, Node q, String module, String filename) {
        extractRevelation(a68, q, module, filename, null);
    }

    /**
     * a68_extract_revelation: This version of a68_extract_revelation gets a
     * symbol table and line info rather than a node. It is used to extract
     * revelations from standard modules distributed in the run-time library.
     * See a68-parser-prelude.cc
     */
    public static void extractRevelation(A68 a68, Table t, Line l,
            String module, String filename,
            Tag tag) {
        Node q = Parser.someNode(a68, "");
        q.symbolTable = t;
        q.info.line = l;
        extractRevelation(a68, q, module, filename, tag);
    }

    /**
     * a68_extract_indicants: Search [MODE|MODULE] A = .., B = .. and ACCESS A,
     * B, .. and store indicants.
     */
    public static void extractIndicants(A68 a68, Node p) {
        Node q = p;

        while (q != null) {
            if (q.is(ACCESS_SYMBOL) || q.is(ALT_ACCESS_SYMBOL)) {
                // An access clause implies the declaration of module indicants,
                // provided they are found in a suitable packet.
                do {
                    q = q.next;
                    if (q != null) {
                        Node boldTag = null;

                        if (q.is(BOLD_TAG)) {
                            boldTag = q;
                            q = q.next;
                        } else if (Parser.whether(a68, q, PUBLIC_SYMBOL, BOLD_TAG)) {
                            boldTag = q.next;
                            q = q.next;
                            q = q.next;
                        }

                        if (boldTag != null) {
                            Tag tag = Taxes.addTag(a68, boldTag.symbolTable, MODULE_SYMBOL, boldTag, null, STOP.ordinal());
                            Parser.gccAssert(tag != null);
                            extractRevelation(a68, boldTag, boldTag.info.symbol,
                                    null /* filename */, tag);
                        }
                    }
                } while (q != null && q.is(COMMA_SYMBOL));
            } else if (q.is(MODULE_SYMBOL)) {
                boolean siga = true;
                do {
                    q = q.next;
                    detectRedefinedKeyword(a68, q, MODULE_DECLARATION);
                    if (Parser.whether(a68, q, BOLD_TAG, EQUALS_SYMBOL)) {
                        q.attribute = DEFINING_MODULE_INDICANT;
                        q = q.next;
                        q.attribute = EQUALS_SYMBOL; /* XXX why not ALT_EQUALS_SYMBOL */
                        if (q.next != null && q.next.is(ACCESS_SYMBOL)) {
                            a68.diagnostics.error(q.next,
                                    "nested access clauses not allowed in module texts");
                            siga = false;
                        } else {
                            q = skipModuleText(a68, q.next);
                            if (q == null) {
                                siga = false;
                            } else {
                                q = q.next;
                            }
                        }
                    } else {
                        siga = false;
                    }
                } while (siga && q != null && q.is(COMMA_SYMBOL));
            } else if (q.is(MODE_SYMBOL) || Parser.whether(a68, q, PUBLIC_SYMBOL, MODE_SYMBOL)) {
                boolean siga = true;
                boolean isPublic = false;

                if (q.is(PUBLIC_SYMBOL)) {
                    isPublic = true;
                    q = q.next;
                }

                do {
                    q = q.next;
                    detectRedefinedKeyword(a68, q, MODE_DECLARATION);
                    if (Parser.whether(a68, q, BOLD_TAG, EQUALS_SYMBOL)) {
                        // Store in the symbol table, but also in the moid list.
                        // Position of definition (q) connects to this lexical
                        // level!
                        Tag t = Taxes.addTag(a68, p.symbolTable, INDICANT, q, null, STOP.ordinal());
                        if (t == null) {
                            throw Parser.gccUnreachable();
                        }
                        t.publicized = isPublic;
                        if (ModeBuilder.addMode(a68, Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v),
                                INDICANT, 0, q, null, null) == null) {
                            throw Parser.gccUnreachable();
                        }
                        q.attribute = DEFINING_INDICANT;
                        q.publicized = isPublic;
                        q = q.next;
                        q.attribute = ALT_EQUALS_SYMBOL;
                        q = skipPackDeclarer(a68, q.next);
                        q = q.next;
                    } else {
                        siga = false;
                    }
                } while (siga && q != null && q.is(COMMA_SYMBOL));
            } else {
                q = q.next;
            }
        }
    }

    /**
     * {@code atoi} as in glibc, i.e. {@code (int) strtol (s, NULL, 10)}.
     * {@code erange[0]} is set if strtol overflowed (errno = ERANGE).
     */
    private static int atoi(String s, boolean[] erange) {
        int i = 0;
        int n = s.length();
        while (i < n && CType.isSpace(s.charAt(i))) {
            i++;
        }
        boolean neg = false;
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            neg = s.charAt(i) == '-';
            i++;
        }
        int start = i;
        while (i < n && CType.isDigit(s.charAt(i))) {
            i++;
        }
        if (i == start) {
            return 0;
        }
        BigInteger v = new BigInteger(s.substring(start, i));
        if (neg) {
            v = v.negate();
        }
        long l;
        if (v.bitLength() > 63) {
            erange[0] = true;
            l = neg ? Long.MIN_VALUE : Long.MAX_VALUE;
        } else {
            l = v.longValue();
        }
        return (int) l;
    }

    /** GET_PRIORITY (q, k): returns k. */
    private static int getPriority(A68 a68, Node q) {
        int k;
        boolean[] errno = {false};
        k = atoi(q.info.symbol, errno);
        if (errno[0]) {
            a68.diagnostics.error(q, "invalid priority declaration");
            k = MAX_PRIORITY;
        } else if (k < 1 || k > MAX_PRIORITY) {
            a68.diagnostics.error(q, "invalid priority declaration");
            k = MAX_PRIORITY;
        }
        return k;
    }

    /** a68_extract_priorities: Search PRIO X = .., Y = .. and store priorities. */
    public static void extractPriorities(A68 a68, Node p) {
        Node q = p;
        while (q != null) {
            if (q.is(PRIO_SYMBOL) || Parser.whether(a68, q, PUBLIC_SYMBOL, PRIO_SYMBOL)) {
                boolean siga = true;
                boolean isPublic = false;

                if (q.is(PUBLIC_SYMBOL)) {
                    isPublic = true;
                    q = q.next;
                }

                do {
                    q = q.next;
                    detectRedefinedKeyword(a68, q, PRIORITY_DECLARATION);
                    // An operator tag like ++ or && gives strange errors so we
                    // catch it here.
                    if (Parser.whether(a68, q, OPERATOR, OPERATOR)) {
                        Node y = q;
                        a68.diagnostics.error(q, "invalid operator tag");
                        q.attribute = DEFINING_OPERATOR;
                        q.publicized = isPublic;
                        // Remove one superfluous operator, and hope it was only
                        // one.
                        q.next = q.next.next;
                        q.next.previous = q;
                        q = q.next;
                        q.attribute = ALT_EQUALS_SYMBOL;
                        q = q.next;
                        int k;
                        k = getPriority(a68, q);
                        q.attribute = PRIORITY;
                        if (Taxes.addTag(a68, p.symbolTable, PRIO_SYMBOL, y, null, k) == null) {
                            throw Parser.gccUnreachable();
                        }
                        q = q.next;
                    } else if (Parser.whether(a68, q, OPERATOR, EQUALS_SYMBOL, INT_DENOTATION)
                            || Parser.whether(a68, q, EQUALS_SYMBOL, EQUALS_SYMBOL, INT_DENOTATION)) {
                        Node y = q;
                        q.attribute = DEFINING_OPERATOR;
                        q.publicized = isPublic;
                        q = q.next;
                        q.attribute = ALT_EQUALS_SYMBOL;
                        q = q.next;
                        int k;
                        k = getPriority(a68, q);
                        q.attribute = PRIORITY;
                        if (Taxes.addTag(a68, p.symbolTable, PRIO_SYMBOL, y, null, k) == null) {
                            throw Parser.gccUnreachable();
                        }
                        q = q.next;
                    } else if (Parser.whether(a68, q, BOLD_TAG, IDENTIFIER)) {
                        siga = false;
                    } else if (Parser.whether(a68, q, BOLD_TAG, EQUALS_SYMBOL, INT_DENOTATION)) {
                        Node y = q;
                        q.attribute = DEFINING_OPERATOR;
                        q.publicized = isPublic;
                        q = q.next;
                        q.attribute = ALT_EQUALS_SYMBOL;
                        q = q.next;
                        int k;
                        k = getPriority(a68, q);
                        q.attribute = PRIORITY;
                        if (Taxes.addTag(a68, p.symbolTable, PRIO_SYMBOL, y, null, k) == null) {
                            throw Parser.gccUnreachable();
                        }
                        q = q.next;
                    } else if (Parser.whether(a68, q, BOLD_TAG, INT_DENOTATION)
                            || Parser.whether(a68, q, OPERATOR, INT_DENOTATION)
                            || Parser.whether(a68, q, EQUALS_SYMBOL, INT_DENOTATION)) {
                        // The scanner cannot separate operator and "=" sign so
                        // we do this here.
                        int len = q.info.symbol.length();
                        if (len > 1 && q.info.symbol.charAt(len - 1) == '=') {
                            Node y = q;
                            String sym = q.info.symbol.substring(0, len - 1);
                            q.info.symbol = Keywords.addToken(a68, sym).text;
                            if (len > 2 && q.info.symbol.charAt(len - 2) == ':' && q.info.symbol.charAt(len - 3) != '=') {
                                Fmt s = Fmt.symbol(q);
                                a68.diagnostics.error(q,
                                        "probably a missing symbol near invalid operator %e",
                                        s);
                            }
                            q.attribute = DEFINING_OPERATOR;
                            q.publicized = isPublic;
                            insertAltEquals(a68, q);
                            q = q.next.next;
                            int k;
                            k = getPriority(a68, q);
                            q.attribute = PRIORITY;
                            if (Taxes.addTag(a68, p.symbolTable, PRIO_SYMBOL, y, null, k) == null) {
                                throw Parser.gccUnreachable();
                            }
                            q = q.next;
                        } else {
                            siga = false;
                        }
                    } else {
                        siga = false;
                    }
                } while (siga && q != null && q.is(COMMA_SYMBOL));
            } else {
                q = q.next;
            }
        }
    }

    /** a68_extract_operators: Search OP [( .. ) ..] X = .., Y = .. and store operators. */
    public static void extractOperators(A68 a68, Node p) {
        Node q = p;

        while (q != null) {
            if (q.is(OP_SYMBOL) || Parser.whether(a68, q, PUBLIC_SYMBOL, OP_SYMBOL)) {
                boolean siga = true;
                boolean inProc = true;
                boolean isPublic = false;

                if (q.is(PUBLIC_SYMBOL)) {
                    isPublic = true;
                    q = q.next;
                }

                // Skip operator plan.
                if (q.next != null && q.next.is(OPEN_SYMBOL)) {
                    q = skipPackDeclarer(a68, q.next);
                    inProc = false;
                }
                // Sample operators.
                if (q != null) {
                    do {
                        q = q.next;
                        detectRedefinedKeyword(a68, q, OPERATOR_DECLARATION);
                        // Unacceptable operator tags like ++ or && could give
                        // strange errors.
                        if (Parser.whether(a68, q, OPERATOR, OPERATOR)) {
                            a68.diagnostics.error(q, "invalid operator tag");
                            q.attribute = DEFINING_OPERATOR;
                            q.publicized = isPublic;
                            Tag t = Taxes.addTag(a68, p.symbolTable, OP_SYMBOL, q, null, STOP.ordinal());
                            if (t == null) {
                                throw Parser.gccUnreachable();
                            }
                            t.inProc = inProc;
                            // Remove one superfluous operator, and hope it was
                            // only one.
                            q.next = q.next.next;
                            q.next.previous = q;
                            q = q.next;
                            q.attribute = ALT_EQUALS_SYMBOL;
                            q = skipUnit(a68, q);
                        } else if (Parser.whether(a68, q, OPERATOR, EQUALS_SYMBOL)
                                || Parser.whether(a68, q, EQUALS_SYMBOL, EQUALS_SYMBOL)) {
                            q.attribute = DEFINING_OPERATOR;
                            q.publicized = isPublic;
                            Tag t = Taxes.addTag(a68, p.symbolTable, OP_SYMBOL, q, null, STOP.ordinal());
                            if (t == null) {
                                throw Parser.gccUnreachable();
                            }
                            t.inProc = inProc;
                            q = q.next;
                            q.attribute = ALT_EQUALS_SYMBOL;
                            q = skipUnit(a68, q);
                        } else if (Parser.whether(a68, q, BOLD_TAG, IDENTIFIER)) {
                            siga = false;
                        } else if (Parser.whether(a68, q, BOLD_TAG, EQUALS_SYMBOL)) {
                            q.attribute = DEFINING_OPERATOR;
                            q.publicized = isPublic;
                            Tag t = Taxes.addTag(a68, p.symbolTable, OP_SYMBOL, q, null, STOP.ordinal());
                            if (t == null) {
                                throw Parser.gccUnreachable();
                            }
                            t.inProc = inProc;
                            q = q.next;
                            q.attribute = ALT_EQUALS_SYMBOL;
                            q = skipUnit(a68, q);
                        } else if (q != null && (Parser.isOneOf(a68, q, OPERATOR, BOLD_TAG, EQUALS_SYMBOL))) {
                            // The scanner cannot separate operator and "=" sign
                            // so we do this here.
                            int len = q.info.symbol.length();
                            if (len > 1 && q.info.symbol.charAt(len - 1) == '=') {
                                String sym = q.info.symbol.substring(0, len - 1);
                                q.info.symbol = Keywords.addToken(a68, sym).text;
                                if (len > 2 && q.info.symbol.charAt(len - 2) == ':'
                                        && q.info.symbol.charAt(len - 3) != '=') {
                                    Fmt s = Fmt.symbol(q);
                                    a68.diagnostics.error(q,
                                            "probably a missing symbol near invalid operator %e",
                                            s);
                                }
                                q.attribute = DEFINING_OPERATOR;
                                q.publicized = isPublic;
                                insertAltEquals(a68, q);
                                Tag t = Taxes.addTag(a68, p.symbolTable, OP_SYMBOL, q, null, STOP.ordinal());
                                if (t == null) {
                                    throw Parser.gccUnreachable();
                                }
                                t.inProc = inProc;
                                q = q.next;
                                q = skipUnit(a68, q);
                            } else {
                                siga = false;
                            }
                        } else {
                            siga = false;
                        }
                    } while (siga && q != null && q.is(COMMA_SYMBOL));
                }
            } else {
                q = q.next;
            }
        }
    }

    /** a68_extract_labels: Search and store labels. */
    public static void extractLabels(A68 a68, Node p, Attribute expect) {
        // Only handle candidate phrases as not to search indexers!.
        if (expect == SERIAL_CLAUSE || expect == ENQUIRY_CLAUSE || expect == SOME_CLAUSE) {
            for (Node q = p; q != null; q = q.next) {
                if (Parser.whether(a68, q, IDENTIFIER, COLON_SYMBOL)) {
                    Tag z = Taxes.addTag(a68, p.symbolTable, LABEL, q, null, LOCAL_LABEL.ordinal());
                    q.attribute = DEFINING_IDENTIFIER;
                    z.unit = null;
                }
            }
        }
    }

    /** extract_identities: Search MOID x = .., y = .. and store identifiers. */
    private static void extractIdentities(A68 a68, Node p) {
        Node q = p;

        while (q != null) {
            if (Parser.whether(a68, q, DECLARER, IDENTIFIER, EQUALS_SYMBOL)
                    || Parser.whether(a68, q, PUBLIC_SYMBOL, DECLARER, IDENTIFIER, EQUALS_SYMBOL)) {
                boolean siga = true;
                boolean isPublic = false;

                if (q.is(PUBLIC_SYMBOL)) {
                    isPublic = true;
                    q = q.next;
                }

                Node declarer = q;
                do {
                    q = q.next;
                    if (Parser.whether(a68, q, IDENTIFIER, EQUALS_SYMBOL)) {
                        Tag tag = Taxes.addTag(a68, p.symbolTable, IDENTIFIER, q, null, NORMAL_IDENTIFIER.ordinal());
                        if (tag == null) {
                            throw Parser.gccUnreachable();
                        }
                        q.attribute = DEFINING_IDENTIFIER;
                        q.publicized = isPublic;
                        if (declarer.sub.is(PROC_SYMBOL)) {
                            Node actualParam = q.next.next;
                            if (actualParam != null && actualParam.is(FORMAL_NEST_SYMBOL)) {
                                tag.nestProc = true;
                            }
                        }
                        q = q.next;
                        q.attribute = ALT_EQUALS_SYMBOL;
                        q = skipUnit(a68, q);
                    } else if (Parser.whether(a68, q, IDENTIFIER, ASSIGN_SYMBOL)) {
                        // Handle common error in ALGOL 68 programs.
                        a68.diagnostics.error(q, "mixed identity-declaration and variable-declaration");
                        if (Taxes.addTag(a68, p.symbolTable, IDENTIFIER, q, null, NORMAL_IDENTIFIER.ordinal()) == null) {
                            throw Parser.gccUnreachable();
                        }
                        q.attribute = DEFINING_IDENTIFIER;
                        q.publicized = isPublic;
                        q = q.next;
                        q.attribute = ALT_EQUALS_SYMBOL;
                        q = skipUnit(a68, q);
                    } else {
                        siga = false;
                    }
                } while (siga && q != null && q.is(COMMA_SYMBOL));
            } else {
                q = q.next;
            }
        }
    }

    /** extract_variables: Search MOID x [:= ..], y [:= ..] and store identifiers. */
    private static void extractVariables(A68 a68, Node p) {
        Node q = p;

        while (q != null) {
            if (Parser.whether(a68, q, HEAP_SYMBOL, DECLARER, IDENTIFIER)
                    || Parser.whether(a68, q, LOC_SYMBOL, DECLARER, IDENTIFIER)
                    || Parser.whether(a68, q, DECLARER, IDENTIFIER)
                    || Parser.whether(a68, q, PUBLIC_SYMBOL, HEAP_SYMBOL, DECLARER, IDENTIFIER)
                    || Parser.whether(a68, q, PUBLIC_SYMBOL, LOC_SYMBOL, DECLARER, IDENTIFIER)
                    || Parser.whether(a68, q, PUBLIC_SYMBOL, DECLARER, IDENTIFIER)) {
                boolean isPublic = q.is(PUBLIC_SYMBOL);
                while (!q.is(DECLARER)) {
                    q = q.next;
                }

                boolean siga = true;
                do {
                    q = q.next;
                    if (Parser.whether(a68, q, IDENTIFIER)) {
                        if (Parser.whether(a68, q, IDENTIFIER, EQUALS_SYMBOL)) {
                            // Handle common error in ALGOL 68 programs.
                            a68.diagnostics.error(q, "mixed identity-declaration and variable-declaration");
                            q.next.attribute = ASSIGN_SYMBOL;
                        }
                        Tag tag = Taxes.addTag(a68, p.symbolTable, IDENTIFIER, q, null, NORMAL_IDENTIFIER.ordinal());
                        if (tag == null) {
                            throw Parser.gccUnreachable();
                        }
                        tag.variable = true;
                        q.attribute = DEFINING_IDENTIFIER;
                        q.publicized = isPublic;
                        q = skipUnit(a68, q);
                    } else {
                        siga = false;
                    }
                } while (siga && q != null && q.is(COMMA_SYMBOL));
            } else {
                q = q.next;
            }
        }
    }

    /** extract_proc_identities: Search PROC x = .., y = .. and stores identifiers. */
    private static void extractProcIdentities(A68 a68, Node p) {
        Node q = p;

        while (q != null) {
            if (Parser.whether(a68, q, PROC_SYMBOL, IDENTIFIER, EQUALS_SYMBOL)
                    || Parser.whether(a68, q, PUBLIC_SYMBOL, PROC_SYMBOL, IDENTIFIER, EQUALS_SYMBOL)) {
                boolean siga = true;
                boolean isPublic = false;

                if (q.is(PUBLIC_SYMBOL)) {
                    isPublic = true;
                    q = q.next;
                }

                do {
                    q = q.next;
                    if (Parser.whether(a68, q, IDENTIFIER, EQUALS_SYMBOL)) {
                        Tag t = Taxes.addTag(a68, p.symbolTable, IDENTIFIER, q, null, NORMAL_IDENTIFIER.ordinal());
                        t.inProc = true;
                        q.attribute = DEFINING_IDENTIFIER;
                        q.publicized = isPublic;
                        q = q.next;
                        q.attribute = ALT_EQUALS_SYMBOL;
                        q = skipUnit(a68, q);
                    } else if (Parser.whether(a68, q, IDENTIFIER, ASSIGN_SYMBOL)) {
                        // Handle common error in ALGOL 68 programs.
                        a68.diagnostics.error(q, "mixed identity-declaration and variable-declaration");
                        if (Taxes.addTag(a68, p.symbolTable, IDENTIFIER, q, null, NORMAL_IDENTIFIER.ordinal()) == null) {
                            throw Parser.gccUnreachable();
                        }
                        q.attribute = DEFINING_IDENTIFIER;
                        q.publicized = isPublic;
                        q = q.next;
                        q.attribute = ALT_EQUALS_SYMBOL;
                        q = skipUnit(a68, q);
                    } else {
                        siga = false;
                    }
                } while (siga && q != null && q.is(COMMA_SYMBOL));
            } else {
                q = q.next;
            }
        }
    }

    /** extract_proc_variables: Search PROC x [:= ..], y [:= ..]; store identifiers. */
    private static void extractProcVariables(A68 a68, Node p) {
        Node q = p;

        while (q != null) {
            if (Parser.whether(a68, q, PROC_SYMBOL, IDENTIFIER)
                    || Parser.whether(a68, q, PUBLIC_SYMBOL, PROC_SYMBOL, IDENTIFIER)) {
                boolean siga = true;
                boolean isPublic = false;

                if (q.is(PUBLIC_SYMBOL)) {
                    isPublic = true;
                    q = q.next;
                }

                do {
                    q = q.next;
                    if (Parser.whether(a68, q, IDENTIFIER, ASSIGN_SYMBOL)) {
                        Tag tag = Taxes.addTag(a68, p.symbolTable, IDENTIFIER, q, null, NORMAL_IDENTIFIER.ordinal());
                        Parser.gccAssert(tag != null);
                        tag.variable = true;
                        q.attribute = DEFINING_IDENTIFIER;
                        q.publicized = isPublic;
                        q = q.next;
                        q = skipUnit(a68, q);
                    } else if (Parser.whether(a68, q, IDENTIFIER, EQUALS_SYMBOL)) {
                        // Handle common error in ALGOL 68 programs.
                        a68.diagnostics.error(q, "mixed identity-declaration and variable-declaration");
                        Tag tag = Taxes.addTag(a68, p.symbolTable, IDENTIFIER, q, null, NORMAL_IDENTIFIER.ordinal());
                        Parser.gccAssert(tag != null);
                        q.attribute = DEFINING_IDENTIFIER;
                        q.publicized = isPublic;
                        q = q.next;
                        q.attribute = ASSIGN_SYMBOL;
                        q = skipUnit(a68, q);
                    } else {
                        siga = false;
                    }
                } while (siga && q != null && q.is(COMMA_SYMBOL));
            } else {
                q = q.next;
            }
        }
    }

    /** a68_extract_declarations: Schedule gathering of definitions in a phrase. */
    public static void extractDeclarations(A68 a68, Node p) {
        // Get definitions so we know what is defined in this range.
        extractIdentities(a68, p);
        extractVariables(a68, p);
        extractProcIdentities(a68, p);
        extractProcVariables(a68, p);
        // By now we know whether "=" is an operator or not.
        for (Node q = p; q != null; q = q.next) {
            if (q.is(EQUALS_SYMBOL)) {
                q.attribute = OPERATOR;
            } else if (q.is(ALT_EQUALS_SYMBOL)) {
                q.attribute = EQUALS_SYMBOL;
            }
        }

        // Get qualifiers.
        for (Node q = p; q != null; q = q.next) {
            if (Parser.whether(a68, q, LOC_SYMBOL, DECLARER, DEFINING_IDENTIFIER)) {
                Parser.makeSub(a68, q, q, QUALIFIER);
            }
            if (Parser.whether(a68, q, HEAP_SYMBOL, DECLARER, DEFINING_IDENTIFIER)) {
                Parser.makeSub(a68, q, q, QUALIFIER);
            }
            if (Parser.whether(a68, q, LOC_SYMBOL, PROC_SYMBOL, DEFINING_IDENTIFIER)) {
                Parser.makeSub(a68, q, q, QUALIFIER);
            }
            if (Parser.whether(a68, q, HEAP_SYMBOL, PROC_SYMBOL, DEFINING_IDENTIFIER)) {
                Parser.makeSub(a68, q, q, QUALIFIER);
            }
        }

        // Give priorities to operators.
        for (Node q = p; q != null; q = q.next) {
            if (q.is(OPERATOR)) {
                if (Taxes.findTagGlobal(a68, q.symbolTable, OP_SYMBOL, q.info.symbol) != null) {
                    Tag s = Taxes.findTagGlobal(a68, q.symbolTable, PRIO_SYMBOL, q.info.symbol);

                    if (s != null) {
                        q.info.priority = s.priority;
                    } else {
                        q.info.priority = 0;
                    }
                } else {
                    Fmt s = Fmt.symbol(q);
                    a68.diagnostics.error(q, "indicant %e has not been declared properly", s);
                    q.info.priority = 1;
                }
            }
        }
    }
}
