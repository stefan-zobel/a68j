// Java port of ga68's a68-parser-taxes.cc: symbol table management.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * Symbol table handling, managing TAGS.
 *
 * <p>Porting notes: the {@code int} arguments of the C version that hold an
 * attribute (the {@code a} of a68_add_tag, the result of
 * a68_first_tag_global, the HEAP qualifiers) are {@link Attribute}s. The
 * priority argument {@code p} of a68_add_tag stays an {@code int}; where C
 * passes an attribute there (NORMAL_IDENTIFIER, ROUTINE_TEXT, STOP, ...) its
 * {@code ordinal()}, which equals the C enum value, is passed.
 */
public final class Taxes {
    private Taxes() {}

    // Forward declarations for several functions defined below: not needed
    // in Java (find_tag_local).

    /** a68_set_proc_level: Set level for procedures. */
    public static void setProcLevel(A68 a68, Node p, int n) {
        for (; p != null; p = p.next) {
            p.info.procedureLevel = n;
            if (p.is(ROUTINE_TEXT)) {
                setProcLevel(a68, p.sub, n + 1);
            } else {
                setProcLevel(a68, p.sub, n);
            }
        }
    }

    /** a68_set_nest: Set nests for diagnostics. */
    public static void setNest(A68 a68, Node p, Node s) {
        for (; p != null; p = p.next) {
            p.nest = s;
            if (p.is(PARTICULAR_PROGRAM)) {
                setNest(a68, p.sub, p);
            } else if (p.is(CLOSED_CLAUSE) && p.info.line.number != 0) {
                setNest(a68, p.sub, p);
            } else if (p.is(COLLATERAL_CLAUSE) && p.info.line.number != 0) {
                setNest(a68, p.sub, p);
            } else if (p.is(CONDITIONAL_CLAUSE) && p.info.line.number != 0) {
                setNest(a68, p.sub, p);
            } else if (p.is(CASE_CLAUSE) && p.info.line.number != 0) {
                setNest(a68, p.sub, p);
            } else if (p.is(CONFORMITY_CLAUSE) && p.info.line.number != 0) {
                setNest(a68, p.sub, p);
            } else if (p.is(LOOP_CLAUSE) && p.info.line.number != 0) {
                setNest(a68, p.sub, p);
            } else {
                setNest(a68, p.sub, s);
            }
        }
    }

    //
    // Routines that work with tags and symbol tables.
    //

    /** a68_first_tag_global: Find a tag, searching symbol tables towards the root. */
    public static Attribute firstTagGlobal(A68 a68, Table table, String name) {
        if (table != null) {
            for (Tag s = table.identifiers; s != null; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    return IDENTIFIER;
                }
            }
            for (Tag s = table.indicants; s != null; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    return INDICANT;
                }
            }
            for (Tag s = table.labels; s != null; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    return LABEL;
                }
            }
            for (Tag s = table.operators; s != null; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    return OP_SYMBOL;
                }
            }
            for (Tag s = table.modules; s != null; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    return MODULE_SYMBOL;
                }
            }
            for (Tag s = table.priority; s != null; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    return PRIO_SYMBOL;
                }
            }
            return firstTagGlobal(a68, table.previous, name);
        } else {
            return STOP;
        }
    }

    /**
     * ACCEPT (u, v) of is_mappable_routine. In C the macro returns true from
     * the enclosing function; here the caller does.
     */
    private static boolean accept(String u, String v) {
        if (u.length() >= v.length()) {
            if (u.substring(u.length() - v.length()).equals(v)) {
                return true;
            }
        }
        return false;
    }

    /** is_mappable_routine: Whether routine can be "lengthety-mapped". */
    private static boolean isMappableRoutine(A68 a68, String z) {
        // Math routines.
        if (accept(z, "arccos")) {
            return true;
        }
        if (accept(z, "arccot")) {
            return true;
        }
        if (accept(z, "arcsin")) {
            return true;
        }
        if (accept(z, "arctan")) {
            return true;
        }
        if (accept(z, "cos")) {
            return true;
        }
        if (accept(z, "exp")) {
            return true;
        }
        if (accept(z, "ln")) {
            return true;
        }
        if (accept(z, "log")) {
            return true;
        }
        if (accept(z, "pi")) {
            return true;
        }
        if (accept(z, "sin")) {
            return true;
        }
        if (accept(z, "sqrt")) {
            return true;
        }
        if (accept(z, "tan")) {
            return true;
        }
        // Random generator.
        if (accept(z, "nextrandom")) {
            return true;
        }
        if (accept(z, "random")) {
            return true;
        }
        // BITS.
        if (accept(z, "bitspack")) {
            return true;
        }
        // Enquiries.
        if (accept(z, "maxint")) {
            return true;
        }
        if (accept(z, "intwidth")) {
            return true;
        }
        if (accept(z, "maxreal")) {
            return true;
        }
        if (accept(z, "realwidth")) {
            return true;
        }
        if (accept(z, "expwidth")) {
            return true;
        }
        if (accept(z, "maxbits")) {
            return true;
        }
        if (accept(z, "bitswidth")) {
            return true;
        }
        if (accept(z, "byteswidth")) {
            return true;
        }
        if (accept(z, "smallreal")) {
            return true;
        }
        return false;
    }

    /** CAR (u, v) of bind_lengthety_identifier: strncmp (u, v, strlen (v)) == 0. */
    private static boolean car(String u, String v) {
        return u.startsWith(v);
    }

    /** bind_lengthety_identifier: Map "short sqrt" onto "sqrt" etcetera. */
    private static Tag bindLengthetyIdentifier(A68 a68, String u) {
        // We can only map routines blessed by "is_mappable_routine", so there
        // is no "short print" or "long char in string".
        if (car(u, "short")) {
            do {
                u = u.substring("short".length());
                String v = Keywords.addToken(a68, u).text;
                Tag w = findTagLocal(a68, a68.standenv, IDENTIFIER, v);
                if (w != null && isMappableRoutine(a68, v)) {
                    return w;
                }
            } while (car(u, "short"));
        } else if (car(u, "long")) {
            do {
                u = u.substring("long".length());
                String v = Keywords.addToken(a68, u).text;
                Tag w = findTagLocal(a68, a68.standenv, IDENTIFIER, v);
                if (w != null && isMappableRoutine(a68, v)) {
                    return w;
                }
            } while (car(u, "long"));
        }

        return null;
    }

    /** bind_identifier_tag_to_symbol_table: Bind identifier tags to the symbol table. */
    private static void bindIdentifierTagToSymbolTable(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            bindIdentifierTagToSymbolTable(a68, p.sub);

            if (Parser.isOneOf(a68, p, IDENTIFIER, DEFINING_IDENTIFIER)) {
                Attribute att = firstTagGlobal(a68, p.symbolTable, p.info.symbol);

                if (att == STOP) {
                    Tag z = bindLengthetyIdentifier(a68, p.info.symbol);

                    if (z != null) {
                        p.type = z.type;
                    }
                    p.tag = z;
                } else {
                    Tag z = findTagGlobal(a68, p.symbolTable, att, p.info.symbol);

                    if (att == IDENTIFIER && z != null) {
                        p.type = z.type;
                    } else if (att == LABEL && z != null) {
                        ;
                    } else if ((z = bindLengthetyIdentifier(a68, p.info.symbol)) != null) {
                        p.type = z.type;
                    } else {
                        a68.diagnostics.error(p, "tag %qs has not been declared properly",
                                p.info.symbol);
                        z = addTag(a68, p.symbolTable, IDENTIFIER, p, a68.modes.ERROR, NORMAL_IDENTIFIER.ordinal());
                        p.type = a68.modes.ERROR;
                    }
                    p.tag = z;
                    if (p.is(DEFINING_IDENTIFIER)) {
                        z.node = p;
                    }
                }
            }
        }
    }

    /**
     * declarer_contains_indicant: Tell whether the given tree refers to the
     * applied indicant INDICANT in an actual declarer.
     */
    private static boolean declarerContainsIndicant(A68 a68, Node p, Node indicant) {
        for (Node q = p; q != null; q = q.next) {
            if (q.is(DECLARER)
                    && q.sub.is(INDICANT)
                    && ((q.sub.tag != null && q.sub.tag.isRecursive)
                        || q.sub.info.symbol.equals(indicant.info.symbol))) {
                return true;
            }

            if (declarerContainsIndicant(a68, q.sub, indicant)) {
                return true;
            }
        }

        return false;
    }

    /** bind_indicant_tag_to_symbol_table: Bind indicant tags to the symbol table. */
    private static void bindIndicantTagToSymbolTable(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            bindIndicantTagToSymbolTable(a68, p.sub);

            if (Parser.isOneOf(a68, p, INDICANT, DEFINING_INDICANT)) {
                Tag z = findTagGlobal(a68, p.symbolTable, INDICANT, p.info.symbol);

                if (z != null) {
                    p.type = z.type;
                    p.tag = z;
                    if (p.is(DEFINING_INDICANT)) {
                        z.node = p;
                        z.isRecursive = declarerContainsIndicant(a68, p.next.next, p);
                    }
                }
            }
        }
    }

    /** tax_specifiers: Enter specifier identifiers in the symbol table. */
    private static void taxSpecifiers(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            taxSpecifiers(a68, p.sub);

            if (p.sub != null && p.is(SPECIFIER)) {
                taxSpecifierList(a68, p.sub);
            }
        }
    }

    /** tax_specifier_list: Enter specifier identifiers in the symbol table. */
    private static void taxSpecifierList(A68 a68, Node p) {
        if (p != null) {
            if (p.is(OPEN_SYMBOL)) {
                taxSpecifierList(a68, p.next);
            } else if (Parser.isOneOf(a68, p, CLOSE_SYMBOL, VOID_SYMBOL)) {
                ;
            } else if (p.is(IDENTIFIER)) {
                Tag z = addTag(a68, p.symbolTable, IDENTIFIER, p, null, SPECIFIER_IDENTIFIER.ordinal());
                z.heap = LOC_SYMBOL;
            } else if (p.is(DECLARER)) {
                taxSpecifiers(a68, p.sub);
                taxSpecifierList(a68, p.next);
                // last identifier entry is identifier with this declarer.
                if (p.symbolTable.identifiers != null
                        && p.symbolTable.identifiers.priority == SPECIFIER_IDENTIFIER.ordinal()) {
                    p.symbolTable.identifiers.type = p.type;
                }
            }
        }
    }

    /** tax_parameters: Enter parameter identifiers in the symbol table. */
    private static void taxParameters(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.sub != null) {
                taxParameters(a68, p.sub);
                if (p.is(PARAMETER_PACK)) {
                    taxParameterList(a68, p.sub);
                }
            }
        }
    }

    /** tax_parameter_list: Enter parameter identifiers in the symbol table. */
    private static void taxParameterList(A68 a68, Node p) {
        if (p != null) {
            if (Parser.isOneOf(a68, p, OPEN_SYMBOL, COMMA_SYMBOL)) {
                taxParameterList(a68, p.next);
            } else if (p.is(CLOSE_SYMBOL)) {
                ;
            } else if (Parser.isOneOf(a68, p, PARAMETER_LIST, PARAMETER)) {
                taxParameterList(a68, p.next);
                taxParameterList(a68, p.sub);
            } else if (p.is(IDENTIFIER)) {
                // parameters are always local.
                addTag(a68, p.symbolTable, IDENTIFIER, p, null, PARAMETER_IDENTIFIER.ordinal()).heap = LOC_SYMBOL;
            } else if (p.is(DECLARER)) {
                taxParameterList(a68, p.next);
                // last identifier entries are identifiers with this declarer.
                for (Tag s = p.symbolTable.identifiers; s != null && s.type == null; s = s.next) {
                    s.type = p.type;
                }
                taxParameters(a68, p.sub);
            }
        }
    }

    /** tax_for_identifiers: Enter FOR identifiers in the symbol table. */
    private static void taxForIdentifiers(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            taxForIdentifiers(a68, p.sub);

            if (p.is(FOR_SYMBOL)) {
                // Sic: FORWARD (p) advances the loop variable; if it becomes
                // NO_NODE the loop increment dereferences it (a crash in C, a
                // NullPointerException here).
                if ((p = p.next) != null) {
                    addTag(a68, p.symbolTable, IDENTIFIER, p, a68.modes.INT, LOOP_IDENTIFIER.ordinal());
                }
            }
        }
    }

    /** tax_routine_texts: Enter routine texts in the symbol table. */
    private static void taxRoutineTexts(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            taxRoutineTexts(a68, p.sub);

            if (p.is(ROUTINE_TEXT)) {
                Tag z = addTag(a68, p.symbolTable, ANONYMOUS, p, p.type, ROUTINE_TEXT.ordinal());
                p.tag = z;
                z.heap = LOC_SYMBOL;
                z.use = true;
            }
        }
    }

    /** tax_format_texts: Enter format texts in the symbol table. */
    private static void taxFormatTexts(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            taxFormatTexts(a68, p.sub);

            if (p.is(FORMAT_TEXT)) {
                Tag z = addTag(a68, p.symbolTable, ANONYMOUS, p, a68.modes.FORMAT, FORMAT_TEXT.ordinal());
                p.tag = z;
                z.use = true;
            } else if (p.is(FORMAT_DELIMITER_SYMBOL) && p.next != null) {
                Tag z = addTag(a68, p.symbolTable, ANONYMOUS, p, a68.modes.FORMAT, FORMAT_IDENTIFIER.ordinal());
                p.tag = z;
                z.use = true;
            }
        }
    }

    /** tax_pictures: Enter FORMAT pictures in the symbol table. */
    private static void taxPictures(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            taxPictures(a68, p.sub);

            if (p.is(PICTURE)) {
                p.tag = addTag(a68, p.symbolTable, ANONYMOUS, p, a68.modes.COLLITEM, FORMAT_IDENTIFIER.ordinal());
            }
        }
    }

    /** tax_generators: Enter generators in the symbol table. */
    private static void taxGenerators(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            taxGenerators(a68, p.sub);

            if (p.is(GENERATOR)) {
                if (p.sub.is(LOC_SYMBOL)) {
                    Tag z = addTag(a68, p.symbolTable, ANONYMOUS, p, p.sub.type.sub, GENERATOR.ordinal());
                    z.heap = LOC_SYMBOL;
                    z.use = true;
                    p.tag = z;
                }
            }
        }
    }

    /** find_firmly_related_op: Find a firmly related operator for operands. */
    private static Tag findFirmlyRelatedOp(A68 a68, Table c, String n, Moid l, Moid r, Tag self) {
        if (c != null) {
            Tag s = c.operators;

            for (; s != null; s = s.next) {
                // C compares the symbol pointers, relying on interned tokens.
                if (s != self && s.node.info.symbol == n) {
                    Pack t = s.type.pack;
                    if (t != null && MoidsMisc.isFirm(a68, t.type, l)) {
                        // catch monadic operator.
                        if ((t = t.next) == null) {
                            if (r == null) {
                                return s;
                            }
                        } else {
                            // catch dyadic operator.
                            if (r != null && MoidsMisc.isFirm(a68, t.type, r)) {
                                return s;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** test_firmly_related_ops_local: Check for firmly related operators in this range. */
    private static void testFirmlyRelatedOpsLocal(A68 a68, Node p, Tag s) {
        if (s != null) {
            Pack u = s.type.pack;

            if (u != null) {
                Moid l = u.type;
                Moid r = (u.next != null ? u.next.type : null);
                Tag t = findFirmlyRelatedOp(a68, s.symbolTable, s.node.info.symbol, l, r, s);

                if (t != null) {
                    Fmt m1 = Fmt.moid(a68, s.type);
                    Fmt m2 = Fmt.moid(a68, t.type);
                    a68.diagnostics.error(p, "%e %qs is firmly related to %e %qs",
                            m1, s.node.info.symbol, m2,
                            t.node.info.symbol);
                } else {
                    // Warn for hidden firmly related operators defined in outer
                    // ranges, if requested.
                    for (Table prev = s.symbolTable.previous;
                            prev != null;
                            prev = prev.previous) {
                        // C: this local is also called t, shadowing the outer t.
                        Tag t2 = findFirmlyRelatedOp(a68, prev, s.node.info.symbol, l, r,
                                null /* self */);
                        if (t2 != null) {
                            if (t2.symbolTable == a68.standenv
                                    && a68.options().hiddenDeclarations > 0) {
                                if (a68.diagnostics.warning(p, Warn.HIDDEN_DECLARATIONS,
                                        "%qs hides a firmly related operator in a larger reach",
                                        s.node.info.symbol)) {
                                    Fmt m = Fmt.moid(a68, t2.type);
                                    a68.diagnostics.inform(null,
                                            "operator %e %qs defined in the standard prelude",
                                            m, t2.node.info.symbol);
                                }
                            } else if (a68.options().hiddenDeclarations > 1) {
                                if (a68.diagnostics.warning(p, Warn.HIDDEN_DECLARATIONS,
                                        "%qs hides a firmly related operator in a larger reach",
                                        s.node.info.symbol)) {
                                    Fmt s1 = Fmt.symbol(s.node);
                                    a68.diagnostics.inform(t2.node,
                                            "previous hidden declaration of %e declared here",
                                            s1);
                                }
                            }

                            // Report only one level of hiding or it gets messy.
                            break;
                        }
                    }
                }
            }
            if (s.next != null) {
                testFirmlyRelatedOpsLocal(a68, (p == null ? null : s.next.node), s.next);
            }
        }
    }

    /** test_firmly_related_ops: Find firmly related operators in this program. */
    private static void testFirmlyRelatedOps(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.sub != null && Parser.isNewLexicalLevel(a68, p)) {
                Tag oops = p.sub.symbolTable.operators;

                if (oops != null) {
                    testFirmlyRelatedOpsLocal(a68, oops.node, oops);
                }
            }
            testFirmlyRelatedOps(a68, p.sub);
        }
    }

    /** a68_collect_taxes: Driver for the processing of TAXes. */
    public static void collectTaxes(A68 a68, Node p) {
        taxTags(a68, p);
        taxSpecifiers(a68, p);
        taxParameters(a68, p);
        taxForIdentifiers(a68, p);
        taxRoutineTexts(a68, p);
        taxPictures(a68, p);
        taxFormatTexts(a68, p);
        taxGenerators(a68, p);
        bindIdentifierTagToSymbolTable(a68, p);
        bindIndicantTagToSymbolTable(a68, p);
        testFirmlyRelatedOps(a68, p);
        testFirmlyRelatedOpsLocal(a68, null, a68.standenv.operators);
    }

    /**
     * attr_descr: Get a description text for attribute passed as the second
     * argument to already_declared and already-declared_hidden. To be used in
     * diagnostics.
     */
    private static String attrDescr(A68 a68, Attribute a) {
        return (a == OP_SYMBOL ? "operator"
                : a == PRIO_SYMBOL ? "priority for operator"
                : a == INDICANT ? "mode indicant"
                : a == LABEL ? "label"
                : a == IDENTIFIER ? "identifier"
                : "tag");
    }

    /** already_declared: Whether tag has already been declared in this range. */
    private static void alreadyDeclared(A68 a68, Node n, Attribute a) {
        if (findTagLocal(a68, n.symbolTable, a, n.info.symbol) != null) {
            a68.diagnostics.error(n, "multiple declaration of %s %qs",
                    attrDescr(a68, a), n.info.symbol);
        }
    }

    /** already_declared_hidden: Whether tag has already been declared in this range. */
    private static void alreadyDeclaredHidden(A68 a68, Node n, Attribute a) {
        if (findTagLocal(a68, n.symbolTable, a, n.info.symbol) != null) {
            a68.diagnostics.error(n, "multiple declaration of %s %qs",
                    attrDescr(a68, a), n.info.symbol);
        }

        Tag s = findTagGlobal(a68, n.symbolTable.previous, a, n.info.symbol);

        if (s != null
                && ((s.symbolTable == a68.standenv && a68.options().hiddenDeclarations > 0)
                    || (s.symbolTable != a68.standenv && a68.options().hiddenDeclarations > 1))) {
            if (a68.diagnostics.warning(n, Warn.HIDDEN_DECLARATIONS,
                    "%qs hides a declaration with larger reach",
                    n.info.symbol)) {
                if (s.symbolTable == a68.standenv) {
                    Fmt m = Fmt.moid(a68, s.type);
                    a68.diagnostics.inform(null,
                            "%e %qs defined in the standard prelude",
                            m, s.node.info.symbol);
                } else {
                    a68.diagnostics.inform(s.node,
                            "previous hidden declaration of %qs declared here",
                            n.info.symbol);
                }
            }
        }
    }

    /** INSERT_TAG (l, n) of a68_add_tag. */
    private static void insertTag(Ref<Tag> l, Tag n) {
        n.next = l.get();
        l.set(n);
    }

    /** a68_add_tag: Add tag to local symbol table. */
    public static Tag addTag(A68 a68, Table s, Attribute a, Node n, Moid m, int p) {
        if (s != null) {
            Tag z = Parser.newTag(a68);

            z.symbolTable = s;
            z.priority = p;
            z.type = m;
            z.node = n;
            // TAX(n) = z;.
            switch (a) {
                case IDENTIFIER:
                    alreadyDeclaredHidden(a68, n, IDENTIFIER);
                    alreadyDeclaredHidden(a68, n, LABEL);
                    insertTag(Ref.of(() -> s.identifiers, v -> s.identifiers = v), z);
                    break;
                case INDICANT:
                    alreadyDeclaredHidden(a68, n, INDICANT);
                    alreadyDeclared(a68, n, OP_SYMBOL);
                    alreadyDeclared(a68, n, PRIO_SYMBOL);
                    insertTag(Ref.of(() -> s.indicants, v -> s.indicants = v), z);
                    break;
                case LABEL:
                    alreadyDeclaredHidden(a68, n, LABEL);
                    alreadyDeclaredHidden(a68, n, IDENTIFIER);
                    insertTag(Ref.of(() -> s.labels, v -> s.labels = v), z);
                    break;
                case OP_SYMBOL:
                    alreadyDeclared(a68, n, INDICANT);
                    insertTag(Ref.of(() -> s.operators, v -> s.operators = v), z);
                    break;
                case MODULE_SYMBOL:
                    alreadyDeclared(a68, n, INDICANT);
                    insertTag(Ref.of(() -> s.modules, v -> s.modules = v), z);
                    break;
                case PRIO_SYMBOL:
                    alreadyDeclared(a68, n, PRIO_SYMBOL);
                    alreadyDeclared(a68, n, INDICANT);
                    insertTag(Ref.of(() -> s.priority, v -> s.priority = v), z);
                    break;
                case ANONYMOUS:
                    insertTag(Ref.of(() -> s.anonymous, v -> s.anonymous = v), z);
                    break;
                default:
                    throw Parser.gccUnreachable();
            }
            return z;
        } else {
            return null;
        }
    }

    /** a68_find_tag_global: Find a tag, searching symbol tables towards the root. */
    public static Tag findTagGlobal(A68 a68, Table table, Attribute a, String name) {
        if (table != null) {
            Tag s = null;
            switch (a) {
                case IDENTIFIER:
                    s = table.identifiers;
                    break;
                case INDICANT:
                    s = table.indicants;
                    break;
                case LABEL:
                    s = table.labels;
                    break;
                case OP_SYMBOL:
                    s = table.operators;
                    break;
                case MODULE_SYMBOL:
                    s = table.modules;
                    break;
                case PRIO_SYMBOL:
                    s = table.priority;
                    break;
                default:
                    throw Parser.gccUnreachable();
            }

            for (; s != null; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    return s;
                }
            }
            return findTagGlobal(a68, table.previous, a, name);
        } else {
            return null;
        }
    }

    /**
     * a68_is_identifier_or_label_global: Whether identifier or label global.
     * Returns IDENTIFIER, LABEL or STOP (C: 0).
     */
    public static Attribute isIdentifierOrLabelGlobal(A68 a68, Table table, String name) {
        if (table != null) {
            for (Tag s = table.identifiers; s != null; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    return IDENTIFIER;
                }
            }
            for (Tag s = table.labels; s != null; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    return LABEL;
                }
            }
            return isIdentifierOrLabelGlobal(a68, table.previous, name);
        } else {
            return STOP;
        }
    }

    /** find_tag_local: Find a tag, searching only local symbol table. */
    private static Tag findTagLocal(A68 a68, Table table, Attribute a, String name) {
        if (table != null) {
            Tag s = null;

            if (a == OP_SYMBOL) {
                s = table.operators;
            } else if (a == MODULE_SYMBOL) {
                s = table.modules;
            } else if (a == PRIO_SYMBOL) {
                s = table.priority;
            } else if (a == IDENTIFIER) {
                s = table.identifiers;
            } else if (a == INDICANT) {
                s = table.indicants;
            } else if (a == LABEL) {
                s = table.labels;
            } else {
                throw Parser.gccUnreachable();
            }

            for (; s != null; s = s.next) {
                if (s.node.info.symbol == name || s.node.info.symbol.equals(name)) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * tab_qualifier: Whether context specifies HEAP or LOC for an identifier.
     *
     * <p>The boolean *E is set to true if an explicit qualifier was found,
     * false otherwise.
     */
    private static Attribute tabQualifier(A68 a68, Node p, boolean[] e) {
        e[0] = false;
        if (p != null) {
            if (Parser.isOneOf(a68, p, UNIT, ASSIGNATION, TERTIARY, SECONDARY, GENERATOR)) {
                return tabQualifier(a68, p.sub, e);
            } else if (Parser.isOneOf(a68, p, LOC_SYMBOL, HEAP_SYMBOL)) {
                e[0] = true;
                return p.attribute == LOC_SYMBOL ? LOC_SYMBOL : HEAP_SYMBOL;
            } else {
                return LOC_SYMBOL;
            }
        } else {
            return LOC_SYMBOL;
        }
    }

    /**
     * tax_identity_dec: Enter identity declarations in the symbol table.
     *
     * <p>E is true if HEAP or LOC got specified explicitly in the identity
     * declaration's formal parameter.
     */
    private static void taxIdentityDec(A68 a68, Node p, Ref<Moid> m) {
        if (p != null) {
            if (p.is(IDENTITY_DECLARATION)) {
                taxIdentityDec(a68, p.sub, m);
                taxIdentityDec(a68, p.next, m);
            } else if (p.is(PUBLIC_SYMBOL)) {
                taxIdentityDec(a68, p.next, m);
            } else if (p.is(DECLARER)) {
                taxTags(a68, p.sub);
                m.set(p.type);
                taxIdentityDec(a68, p.next, m);
            } else if (p.is(COMMA_SYMBOL)) {
                taxIdentityDec(a68, p.next, m);
            } else if (p.is(DEFINING_IDENTIFIER)) {
                Tag entry = findTagLocal(a68, p.symbolTable, IDENTIFIER, p.info.symbol);
                Parser.gccAssert(entry != null);

                p.type = m.get();
                entry.heap = LOC_SYMBOL;
                p.tag = entry;
                entry.type = m.get();
                entry.publicized = p.publicized;
                boolean[] e = new boolean[1];
                if (m.get().attribute == REF_SYMBOL) {
                    entry.heap = tabQualifier(a68, p.next.next, e);
                    if (e[0] && entry.heap == LOC_SYMBOL && entry.publicized) {
                        a68.diagnostics.warning(p, null, "value of local generator will be out of scope");
                    }
                }
                taxIdentityDec(a68, p.next.next, m);
            } else {
                taxTags(a68, p);
            }
        }
    }

    /**
     * tax_variable_dec: Enter variable declarations in the symbol table.
     *
     * <p>E is true if an explicit sample generator was specified in the
     * variable declaration.
     */
    private static void taxVariableDec(A68 a68, Node p, Attribute[] q, Ref<Moid> m, boolean e) {
        if (p != null) {
            if (p.is(VARIABLE_DECLARATION)) {
                taxVariableDec(a68, p.sub, q, m, e);
                taxVariableDec(a68, p.next, q, m, e);
            } else if (p.is(PUBLIC_SYMBOL)) {
                taxVariableDec(a68, p.next, q, m, e);
            } else if (p.is(DECLARER)) {
                taxTags(a68, p.sub);
                m.set(p.type);
                taxVariableDec(a68, p.next, q, m, e);
            } else if (p.is(QUALIFIER)) {
                q[0] = p.sub.attribute;
                taxVariableDec(a68, p.next, q, m, true);
            } else if (p.is(COMMA_SYMBOL)) {
                taxVariableDec(a68, p.next, q, m, e);
            } else if (p.is(DEFINING_IDENTIFIER)) {
                Tag entry = findTagLocal(a68, p.symbolTable, IDENTIFIER, p.info.symbol);

                p.type = m.get();
                p.tag = entry;
                entry.publicized = p.publicized;

                if (p.publicized && e && q[0] == LOC_SYMBOL) {
                    a68.diagnostics.error(p, "publicized variable should not be allocated on the stack");
                }

                if (p.publicized) {
                    entry.heap = STATIC_SYMBOL;
                } else {
                    entry.heap = q[0];
                }

                if (entry.heap == LOC_SYMBOL) {
                    Tag z = addTag(a68, p.symbolTable, ANONYMOUS, p, m.get().sub, GENERATOR.ordinal());
                    z.heap = LOC_SYMBOL;
                    z.use = true;
                    entry.body = z;
                } else {
                    entry.body = null;
                }
                entry.type = m.get();
                taxVariableDec(a68, p.next, q, m, e);
            } else {
                taxTags(a68, p);
            }
        }
    }

    /**
     * tax_proc_variable_dec: Enter procedure variable declarations in the
     * symbol table.
     *
     * <p>E is true if an explicit sample generator was specified in the
     * procedure variable declaration.
     */
    private static void taxProcVariableDec(A68 a68, Node p, Attribute[] q, boolean e) {
        if (p != null) {
            if (p.is(PROCEDURE_VARIABLE_DECLARATION)) {
                taxProcVariableDec(a68, p.sub, q, e);
                taxProcVariableDec(a68, p.next, q, e);
            } else if (p.is(QUALIFIER)) {
                q[0] = p.sub.attribute;
                taxProcVariableDec(a68, p.next, q, true);
            } else if (Parser.isOneOf(a68, p, PUBLIC_SYMBOL, PROC_SYMBOL, COMMA_SYMBOL)) {
                taxProcVariableDec(a68, p.next, q, e);
            } else if (p.is(DEFINING_IDENTIFIER)) {
                Tag entry = findTagLocal(a68, p.symbolTable, IDENTIFIER, p.info.symbol);

                p.tag = entry;
                entry.type = p.type;
                entry.publicized = p.publicized;

                if (p.publicized && e && q[0] == LOC_SYMBOL) {
                    a68.diagnostics.error(p, "publicized variable should not be allocated on the stack");
                }

                if (p.publicized) {
                    entry.heap = STATIC_SYMBOL;
                } else {
                    entry.heap = q[0];
                }

                if (entry.heap == LOC_SYMBOL) {
                    Tag z = addTag(a68, p.symbolTable, ANONYMOUS, p, p.type.sub, GENERATOR.ordinal());
                    z.heap = LOC_SYMBOL;
                    z.use = true;
                    entry.body = z;
                } else {
                    entry.body = null;
                }
                taxProcVariableDec(a68, p.next, q, e);
            } else {
                taxTags(a68, p);
            }
        }
    }

    /** tax_proc_dec: Enter procedure declarations in the symbol table. */
    private static void taxProcDec(A68 a68, Node p) {
        if (p != null) {
            if (p.is(PROCEDURE_DECLARATION)) {
                taxProcDec(a68, p.sub);
                taxProcDec(a68, p.next);
            } else if (Parser.isOneOf(a68, p, PUBLIC_SYMBOL, PROC_SYMBOL, COMMA_SYMBOL)) {
                taxProcDec(a68, p.next);
            } else if (p.is(DEFINING_IDENTIFIER)) {
                Tag entry = findTagLocal(a68, p.symbolTable, IDENTIFIER, p.info.symbol);

                Moid m = p.next.next.type;
                p.type = m;
                p.tag = entry;
                entry.heap = LOC_SYMBOL;
                entry.type = m;
                entry.publicized = p.publicized;
                taxProcDec(a68, p.next);
            } else {
                taxTags(a68, p);
            }
        }
    }

    /** check_operator_dec: Check validity of operator declaration. */
    private static void checkOperatorDec(A68 a68, Node p, Moid u) {
        int k = 0;

        if (u == null) {
            Node pack = p.next.next.sub.sub; /* Where the parameter pack
                                                is. */
            if (p.next.next.attribute != ROUTINE_TEXT) {
                pack = pack.sub;
            }
            k = 1 + Parser.countOperands(a68, pack);
        } else {
            k = ModeBuilder.countPackMembers(a68, u.pack);
        }

        if (k < 1 || k > 2) {
            Fmt s = Fmt.symbol(p);
            a68.diagnostics.error(p, "incorrect number of operands for %e", s);
            k = 0;
        }

        // C: NSYMBOL (p)[0], which is the NUL terminator for an empty symbol.
        char first = p.info.symbol.isEmpty() ? '\0' : p.info.symbol.charAt(0);
        if (k == 1 && CType.strchr(Scanner.NOMADS, first)) {
            Fmt s = Fmt.symbol(p);
            a68.diagnostics.error(p, "monadic %e cannot start with a character from %qs",
                    s, Scanner.NOMADS);
        } else if (k == 2 && findTagGlobal(a68, p.symbolTable, PRIO_SYMBOL, p.info.symbol) == null) {
            Fmt s = Fmt.symbol(p);
            a68.diagnostics.error(p, "dyadic %e has no priority declaration", s);
        }
    }

    /** tax_op_dec: Enter operator declarations in the symbol table. */
    private static void taxOpDec(A68 a68, Node p, Ref<Moid> m) {
        if (p != null) {
            if (p.is(OPERATOR_DECLARATION)) {
                taxOpDec(a68, p.sub, m);
                taxOpDec(a68, p.next, m);
            } else if (p.is(OPERATOR_PLAN)) {
                taxTags(a68, p.sub);
                m.set(p.type);
                taxOpDec(a68, p.next, m);
            } else if (p.is(OP_SYMBOL)) {
                taxOpDec(a68, p.next, m);
            } else if (Parser.isOneOf(a68, p, PUBLIC_SYMBOL, COMMA_SYMBOL)) {
                taxOpDec(a68, p.next, m);
            } else if (p.is(DEFINING_OPERATOR)) {
                Tag entry = p.symbolTable.operators;
                checkOperatorDec(a68, p, m.get());
                while (entry != null && entry.node != p) {
                    entry = entry.next;
                }
                p.type = m.get();
                p.tag = entry;
                entry.heap = LOC_SYMBOL;
                entry.type = m.get();
                entry.publicized = p.publicized;
                taxOpDec(a68, p.next, m);
            } else {
                taxTags(a68, p);
            }
        }
    }

    /** tax_module_dec: Enter module declarations in the symbol table. */
    private static void taxModuleDec(A68 a68, Node p) {
        if (p != null) {
            if (p.is(MODULE_DECLARATION)) {
                taxModuleDec(a68, p.sub);
                taxModuleDec(a68, p.next);
            } else if (p.is(MODULE_SYMBOL)) {
                taxModuleDec(a68, p.next);
            } else if (p.is(COMMA_SYMBOL)) {
                taxModuleDec(a68, p.next);
            } else {
                taxTags(a68, p);
            }
        }
    }

    /** tax_brief_op_dec: Enter brief operator declarations in the symbol table. */
    private static void taxBriefOpDec(A68 a68, Node p) {
        if (p != null) {
            if (p.is(BRIEF_OPERATOR_DECLARATION)) {
                taxBriefOpDec(a68, p.sub);
                taxBriefOpDec(a68, p.next);
            } else if (Parser.isOneOf(a68, p, PUBLIC_SYMBOL, OP_SYMBOL, COMMA_SYMBOL)) {
                taxBriefOpDec(a68, p.next);
            } else if (p.is(DEFINING_OPERATOR)) {
                Tag entry = p.symbolTable.operators;
                Moid m = p.next.next.type;
                checkOperatorDec(a68, p, null);
                while (entry != null && entry.node != p) {
                    entry = entry.next;
                }
                p.type = m;
                p.tag = entry;
                entry.heap = LOC_SYMBOL;
                entry.type = m;
                entry.publicized = p.publicized;
                taxBriefOpDec(a68, p.next);
            } else {
                taxTags(a68, p);
            }
        }
    }

    /** tax_prio_dec: Enter priority declarations in the symbol table. */
    private static void taxPrioDec(A68 a68, Node p) {
        if (p != null) {
            if (p.is(PRIORITY_DECLARATION)) {
                taxPrioDec(a68, p.sub);
                taxPrioDec(a68, p.next);
            } else if (Parser.isOneOf(a68, p, PUBLIC_SYMBOL, PRIO_SYMBOL, COMMA_SYMBOL)) {
                taxPrioDec(a68, p.next);
            } else if (p.is(DEFINING_OPERATOR)) {
                Tag entry = p.symbolTable.priority;
                while (entry != null && entry.node != p) {
                    entry = entry.next;
                }
                p.type = null;
                p.tag = entry;
                entry.heap = LOC_SYMBOL;
                entry.publicized = p.publicized;
                taxPrioDec(a68, p.next);
            } else {
                taxTags(a68, p);
            }
        }
    }

    /** tax_tags: Enter TAXes in the symbol table. */
    private static void taxTags(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            Attribute[] heap = {LOC_SYMBOL};
            Ref.Box<Moid> m = Ref.box(null);

            if (p.is(IDENTITY_DECLARATION)) {
                taxIdentityDec(a68, p, m);
            } else if (p.is(VARIABLE_DECLARATION)) {
                taxVariableDec(a68, p, heap, m, false);
            } else if (p.is(PROCEDURE_DECLARATION)) {
                taxProcDec(a68, p);
            } else if (p.is(PROCEDURE_VARIABLE_DECLARATION)) {
                taxProcVariableDec(a68, p, heap, false);
            } else if (p.is(OPERATOR_DECLARATION)) {
                taxOpDec(a68, p, m);
            } else if (p.is(BRIEF_OPERATOR_DECLARATION)) {
                taxBriefOpDec(a68, p);
            } else if (p.is(PRIORITY_DECLARATION)) {
                taxPrioDec(a68, p);
            } else if (p.is(MODULE_DECLARATION)) {
                taxModuleDec(a68, p);
            } else {
                taxTags(a68, p.sub);
            }
        }
    }

    /** a68_reset_symbol_table_nest_count: Reset symbol table nest count. */
    public static void resetSymbolTableNestCount(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.sub != null && Parser.isNewLexicalLevel(a68, p)) {
                p.sub.symbolTable.nest = a68.symbolTableCount++;
            }
            resetSymbolTableNestCount(a68, p.sub);
        }
    }

    /** a68_bind_routine_tags_to_tree: Bind routines in symbol table to the tree. */
    public static void bindRoutineTagsToTree(A68 a68, Node p) {
        // By inserting coercions etc. some may have shifted.
        for (; p != null; p = p.next) {
            if (p.is(ROUTINE_TEXT) && p.tag != null) {
                p.tag.node = p;
            }
            bindRoutineTagsToTree(a68, p.sub);
        }
    }

    /** bind_format_tags_to_tree: Bind formats in symbol table to tree. */
    private static void bindFormatTagsToTree(A68 a68, Node p) {
        // By inserting coercions etc. some may have shifted.
        for (; p != null; p = p.next) {
            if (p.is(FORMAT_TEXT) && p.tag != null) {
                p.tag.node = p;
            } else if (p.is(FORMAT_DELIMITER_SYMBOL) && p.next != null && p.tag != null) {
                p.tag.node = p;
            }

            bindFormatTagsToTree(a68, p.sub);
        }
    }

    /** a68_fill_symbol_table_outer: Fill outer level of symbol table. */
    public static void fillSymbolTableOuter(A68 a68, Node p, Table s) {
        for (; p != null; p = p.next) {
            if (p.symbolTable != null) {
                p.symbolTable.outer = s;
            }

            if (p.sub != null && p.is(ROUTINE_TEXT)) {
                fillSymbolTableOuter(a68, p.sub, p.sub.symbolTable);
            } else if (p.sub != null && p.is(FORMAT_TEXT)) {
                fillSymbolTableOuter(a68, p.sub, p.sub.symbolTable);
            } else if (p.sub != null && p.is(MODULE_TEXT)) {
                fillSymbolTableOuter(a68, p.sub, p.sub.symbolTable);
            } else {
                fillSymbolTableOuter(a68, p.sub, s);
            }
        }
    }

    /** flood_with_symbol_table_restricted: Flood branch in tree with local symbol table S. */
    private static void floodWithSymbolTableRestricted(A68 a68, Node p, Table s) {
        for (; p != null; p = p.next) {
            p.symbolTable = s;
            if (p.attribute != ROUTINE_TEXT && p.attribute != SPECIFIED_UNIT) {
                if (Parser.isNewLexicalLevel(a68, p)) {
                    p.sub.symbolTable.previous = s;
                } else {
                    floodWithSymbolTableRestricted(a68, p.sub, s);
                }
            }
        }
    }

    /** a68_finalise_symbol_table_setup: Final structure of symbol table after parsing. */
    public static void finaliseSymbolTableSetup(A68 a68, Node p, int l) {
        Table s = p.symbolTable;
        Node q = p;

        while (q != null) {
            // routine texts are ranges.
            if (q.is(ROUTINE_TEXT)) {
                floodWithSymbolTableRestricted(a68, q.sub, Parser.newSymbolTable(a68, s));
            } else if (q.is(SPECIFIED_UNIT)) {
                // specifiers are ranges.
                floodWithSymbolTableRestricted(a68, q.sub, Parser.newSymbolTable(a68, s));
            }

            // level count and recursion.
            if (q.sub != null) {
                if (Parser.isNewLexicalLevel(a68, q)) {
                    q.sub.symbolTable.level = l + 1;
                    Parser.gccAssert(q.sub.symbolTable != s);
                    q.sub.symbolTable.previous = s;
                    finaliseSymbolTableSetup(a68, q.sub, l + 1);
                    if (q.is(WHILE_PART)) {
                        // This was a bug that went unnoticed for 15 years!.
                        Table s2 = q.sub.symbolTable;
                        if ((q = q.next) == null) {
                            return;
                        }
                        if (q.is(ALT_DO_PART)) {
                            q.sub.symbolTable.previous = s2;
                            q.sub.symbolTable.level = l + 2;
                            finaliseSymbolTableSetup(a68, q.sub, l + 2);
                        }
                    }
                    if (q.is(DEF_PART)) {
                        Table s2 = q.sub.symbolTable;
                        if ((q = q.next) == null) {
                            return;
                        }
                        if (q.is(POSTLUDE_PART)) {
                            q.sub.symbolTable.previous = s2;
                            q.sub.symbolTable.level = l + 2;
                            finaliseSymbolTableSetup(a68, q.sub, l + 2);
                        }
                    }
                } else {
                    q.sub.symbolTable = s;
                    finaliseSymbolTableSetup(a68, q.sub, l);
                }
            }
            q.symbolTable = s;

            if (q.is(FOR_SYMBOL)) {
                q = q.next;
            }
            q = q.next;
        }

        // FOR identifiers are in the DO ... OD range.
        for (q = p; q != null; q = q.next) {
            if (q.is(FOR_SYMBOL)) {
                q.next.symbolTable = q.next.sequence.symbolTable;
            }
        }
    }

    /** a68_preliminary_symbol_table_setup: First structure of symbol table for parsing. */
    public static void preliminarySymbolTableSetup(A68 a68, Node p) {
        Table s = p.symbolTable;
        boolean notAForRange = false;

        // Let the tree point to the current symbol table.
        for (Node q = p; q != null; q = q.next) {
            q.symbolTable = s;
        }

        // insert new tables when required.
        // Sic: where FORWARD (q) inside the body yields NO_NODE, the loop
        // increment dereferences it (a crash in C, a NullPointerException
        // here); likewise IS (q, IF_SYMBOL) below after q became NO_NODE.
        for (Node q = p; q != null && !notAForRange; q = q.next) {
            if (q.sub != null) {
                // BEGIN ... END, CODE ... EDOC, DO ... OD, $ ... $, { ... } are
                // ranges.
                if (Parser.isOneOf(a68, q, BEGIN_SYMBOL, DO_SYMBOL, ALT_DO_SYMBOL,
                        FORMAT_DELIMITER_SYMBOL)) {
                    q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                    preliminarySymbolTableSetup(a68, q.sub);
                } else if (q.is(OPEN_SYMBOL)) {
                    // ( ... ) is a range.
                    if (Parser.whether(a68, q, OPEN_SYMBOL, THEN_BAR_SYMBOL)) {
                        q.sub.symbolTable = s;
                        preliminarySymbolTableSetup(a68, q.sub);
                        q = q.next;
                        q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                        preliminarySymbolTableSetup(a68, q.sub);
                        if ((q = q.next) == null) {
                            notAForRange = true;
                        } else {
                            if (q.is(THEN_BAR_SYMBOL)) {
                                q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                                preliminarySymbolTableSetup(a68, q.sub);
                            }
                            if (q.is(OPEN_SYMBOL)) {
                                q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                                preliminarySymbolTableSetup(a68, q.sub);
                            }
                        }
                    } else {
                        // Don't worry about STRUCT (...), UNION (...), PROC
                        // (...) yet.
                        q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                        preliminarySymbolTableSetup(a68, q.sub);
                    }
                } else if (q.is(IF_SYMBOL)) {
                    // IF ... THEN ... ELSE ... FI are ranges.
                    if (Parser.whether(a68, q, IF_SYMBOL, THEN_SYMBOL)) {
                        q.sub.symbolTable = s;
                        preliminarySymbolTableSetup(a68, q.sub);
                        q = q.next;
                        q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                        preliminarySymbolTableSetup(a68, q.sub);
                        if ((q = q.next) == null) {
                            notAForRange = true;
                        } else if (q.is(ELSE_SYMBOL)) {
                            q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                            preliminarySymbolTableSetup(a68, q.sub);
                        }
                        if (q.is(IF_SYMBOL)) {
                            q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                            preliminarySymbolTableSetup(a68, q.sub);
                        }
                    } else {
                        q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                        preliminarySymbolTableSetup(a68, q.sub);
                    }
                } else if (q.is(CASE_SYMBOL)) {
                    // CASE ... IN ... OUT ... ESAC are ranges.
                    if (Parser.whether(a68, q, CASE_SYMBOL, IN_SYMBOL)) {
                        q.sub.symbolTable = s;
                        preliminarySymbolTableSetup(a68, q.sub);
                        q = q.next;
                        q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                        preliminarySymbolTableSetup(a68, q.sub);
                        if ((q = q.next) == null) {
                            notAForRange = true;
                        } else {
                            if (q.is(OUT_SYMBOL)) {
                                q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                                preliminarySymbolTableSetup(a68, q.sub);
                            }
                            if (q.is(CASE_SYMBOL)) {
                                q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                                preliminarySymbolTableSetup(a68, q.sub);
                            }
                        }
                    } else {
                        q.sub.symbolTable = Parser.newSymbolTable(a68, s);
                        preliminarySymbolTableSetup(a68, q.sub);
                    }
                } else if (q.is(WHILE_SYMBOL)) {
                    // WHILE ... DO ... OD are ranges.
                    Table u = Parser.newSymbolTable(a68, s);
                    q.sub.symbolTable = u;
                    preliminarySymbolTableSetup(a68, q.sub);
                    if ((q = q.next) == null) {
                        notAForRange = true;
                    } else if (q.is(ALT_DO_SYMBOL)) {
                        q.sub.symbolTable = Parser.newSymbolTable(a68, u);
                        preliminarySymbolTableSetup(a68, q.sub);
                    }
                } else if (q.is(ALT_ACCESS_SYMBOL)) {
                    // ACCESS ... DEF ...POSTLUDE ...FED are ranges.
                    Table u = Parser.newSymbolTable(a68, s);
                    q.sub.symbolTable = u;
                    preliminarySymbolTableSetup(a68, q.sub);
                    if (q.next == null) {
                        notAForRange = true;
                    } else if ((q = q.next).is(DEF_SYMBOL)) {
                        Table v = Parser.newSymbolTable(a68, u);
                        q.sub.symbolTable = v;
                        preliminarySymbolTableSetup(a68, q.sub);
                    }
                } else if (q.is(DEF_SYMBOL)) {
                    // DEF ... POSTLUDE ... FED are ranges.
                    Table u = Parser.newSymbolTable(a68, s);
                    u.publicRange = true;
                    q.sub.symbolTable = u;
                    preliminarySymbolTableSetup(a68, q.sub);
                    if (q.next == null) {
                        notAForRange = true;
                    } else if ((q = q.next).is(POSTLUDE_SYMBOL)) {
                        Table v = Parser.newSymbolTable(a68, u);
                        q.sub.symbolTable = v;
                        preliminarySymbolTableSetup(a68, q.sub);
                    }
                } else if (q.is(ACCESS_SYMBOL)) {
                    // ACCESS ... CONTROLLED_CLAUSE  are ranges.
                    Table u = Parser.newSymbolTable(a68, s);
                    q.sub.symbolTable = u;
                    preliminarySymbolTableSetup(a68, q.sub);
                    if (q.next == null) {
                        notAForRange = true;
                    }
                } else {
                    q.sub.symbolTable = s;
                    preliminarySymbolTableSetup(a68, q.sub);
                }
            }
        }
        // FOR identifiers will go to the DO ... OD range.
        if (!notAForRange) {
            for (Node q = p; q != null; q = q.next) {
                if (q.is(FOR_SYMBOL)) {
                    Node r = q;
                    q.next.symbolTable = null;
                    for (; r != null && q.next.symbolTable == null; r = r.next) {
                        if ((Parser.isOneOf(a68, r, WHILE_SYMBOL, ALT_DO_SYMBOL))
                                && (q.next != null && r.sub != null)) {
                            q.next.symbolTable = r.sub.symbolTable;
                            q.next.sequence = r.sub;
                        }
                    }
                }
            }
        }
    }

    /** mark_mode: Mark a mode as in use. */
    private static void markMode(A68 a68, Moid m) {
        if (m != null && m.use == false) {
            Pack p = m.pack;
            m.use = true;
            for (; p != null; p = p.next) {
                markMode(a68, p.type);
                markMode(a68, m.sub);
                markMode(a68, m.slice);
            }
        }
    }

    /** a68_mark_moids: Traverse tree and mark modes as used. */
    public static void markMoids(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            markMoids(a68, p.sub);
            if (p.type != null) {
                markMode(a68, p.type);
            }
        }
    }

    /** a68_mark_auxilliary: Mark various tags as used. */
    public static void markAuxilliary(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.sub != null) {
                // You get no warnings on unused PROC parameters. That is ok
                // since A68 has some parameters that you may not use at all -
                // think of PROC (REF FILE) BOOL event routines in transput.
                markAuxilliary(a68, p.sub);
            } else if (p.is(OPERATOR)) {
                Tag z;

                if (p.tag != null) {
                    p.tag.use = true;
                }

                if ((z = findTagGlobal(a68, p.symbolTable, PRIO_SYMBOL, p.info.symbol)) != null) {
                    z.use = true;
                }
            } else if (p.is(INDICANT)) {
                Tag z = findTagGlobal(a68, p.symbolTable, INDICANT, p.info.symbol);

                if (z != null) {
                    p.tag = z;
                    z.use = true;
                }
            } else if (p.is(IDENTIFIER)) {
                if (p.tag != null) {
                    p.tag.use = true;
                }
            }
        }
    }

    /** unused: Check a single tag. */
    private static void unused(A68 a68, Tag s) {
        for (; s != null; s = s.next) {
            if (s.node.info.line.number > 0 && !s.use) {
                a68.diagnostics.warning(s.node, Warn.UNUSED, "tag %qs is not used", s.node.info.symbol);
            }
        }
    }

    /** a68_warn_for_unused_tags: Driver for traversing tree and warn for unused tags. */
    public static void warnForUnusedTags(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.sub != null) {
                if (Parser.isNewLexicalLevel(a68, p)) {
                    unused(a68, p.sub.symbolTable.modules);
                    unused(a68, p.sub.symbolTable.operators);
                    unused(a68, p.sub.symbolTable.priority);
                    unused(a68, p.sub.symbolTable.identifiers);
                    unused(a68, p.sub.symbolTable.labels);
                    unused(a68, p.sub.symbolTable.indicants);
                }
            }
            warnForUnusedTags(a68, p.sub);
        }
    }

    /** a68_jumps_from_procs: Mark jumps and procedured jumps. */
    public static void jumpsFromProcs(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(PROCEDURING)) {
                Node u = p.sub.sub;

                if (u.is(GOTO_SYMBOL)) {
                    u = u.next;
                }
                u.tag.use = true;
            } else if (p.is(JUMP)) {
                Node u = p.sub;

                if (u.is(GOTO_SYMBOL)) {
                    u = u.next;
                }
                if ((u.tag == null) && (u.type == null)
                        && (findTagGlobal(a68, u.symbolTable, LABEL, u.info.symbol) == null)) {
                    addTag(a68, u.symbolTable, LABEL, u, null, LOCAL_LABEL.ordinal());
                    a68.diagnostics.error(u, "tag %qs has not been declared properly", u.info.symbol);
                } else {
                    u.tag.use = true;
                }
            } else {
                jumpsFromProcs(a68, p.sub);
            }
        }
    }
}
