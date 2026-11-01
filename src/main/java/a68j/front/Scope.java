// Java port of ga68's a68-parser-scope.cc: static scope checker.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Original implementation by J. Marcel van der Veer, adapted for GCC by
// Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

/**
 * A static scope checker inspects the source. Note that ALGOL 68 also needs
 * dynamic scope checking.
 *
 * <p>Porting notes: the C struct TUPLE_T is a value type that is copied on
 * assignment; it is ported as the immutable class {@link Tuple}, so sharing
 * instances is equivalent to copying them. SCOPE_T is ported as
 * {@link ScopeT} (the name {@code Scope} is taken by this class). A
 * {@code SCOPE_T **} parameter is a {@code Ref<ScopeT>}; NO_VAR is
 * {@code null}. The C member {@code transient} is a Java keyword and is
 * called {@code transient_}.
 */
public final class Scope {
    private Scope() {}

    /** TUPLE_T. */
    static final class Tuple {
        final int level;
        final boolean transient_;

        Tuple(int level, boolean transient_) {
            this.level = level;
            this.transient_ = transient_;
        }
    }

    /** SCOPE_T. */
    static final class ScopeT {
        Node where;
        Tuple tuple;
        ScopeT next;
    }

    // constexpr TUPLE_T *NO_TUPLE = nullptr; constexpr SCOPE_T *NO_SCOPE = nullptr;
    // are null in Java. #define TUPLE(p) ((p)->tuple) is the field tuple.

    /** enum { NOT_TRANSIENT = 0, TRANSIENT }. */
    static final int NOT_TRANSIENT = 0;
    static final int TRANSIENT = 1;

    // Forward declarations of gather_scopes_for_youngest, scope_statement,
    // scope_enclosed_clause, scope_formula, scope_routine_text and
    // scope_access_clause: not needed in Java.

    //
    // Static scope checker.
    //

    /**
     * Scope_make_tuple.
     *
     * <p>The C version fills a function-static TUPLE_T and returns a copy of
     * it; returning a fresh immutable Tuple is equivalent.
     */
    private static Tuple scopeMakeTuple(A68 a68, int e, int t) {
        // C: static TUPLE_T z; LEVEL (&z) = e; TRANSIENT (&z) = t; (bool from int)
        Tuple z = new Tuple(e, t != 0);
        return z;
    }

    /** Link scope information into the list. */
    private static void scopeAdd(A68 a68, Ref<ScopeT> sl, Node p, Tuple tup) {
        if (sl != null) {
            ScopeT ns = new ScopeT();
            ns.where = p;
            ns.tuple = tup;
            ns.next = sl.get();
            sl.set(ns);
        }
    }

    /** Scope_check. */
    private static boolean scopeCheck(A68 a68, ScopeT top, int mask, int dest) {
        int errors = 0;

        // Transient names cannot be stored.
        if ((mask & TRANSIENT) != 0) {
            for (ScopeT s = top; s != null; s = s.next) {
                if (((s.tuple.transient_ ? 1 : 0) & TRANSIENT) != 0) {
                    a68.diagnostics.error(s.where, "attempt at storing a transient name");
                    s.where.status |= Status.SCOPE_ERROR_MASK;
                    errors++;
                }
            }
        }

        // Potential scope violations.
        for (ScopeT s = top; s != null; s = s.next) {
            if (dest < s.tuple.level && !((s.where.status & Status.SCOPE_ERROR_MASK) != 0)) {
                Moid ws = s.where.type;

                if (ws != null) {
                    if (ws.attribute == REF_SYMBOL
                            || ws.attribute == PROC_SYMBOL
                            || ws.attribute == FORMAT_SYMBOL
                            || ws.attribute == UNION_SYMBOL) {
                        a68.diagnostics.warning(s.where, Warn.SCOPE,
                                "%e %e is a potential scope violation",
                                Fmt.moid(a68, s.where.type), Fmt.attr(s.where.attribute));
                    }
                }
                s.where.status |= Status.SCOPE_ERROR_MASK;
                errors++;
            }
        }
        return (errors == 0);
    }

    /** Scope_check_multiple. */
    private static boolean scopeCheckMultiple(A68 a68, ScopeT top, int mask, ScopeT dest) {
        boolean noErr = true;

        // Note: as in C, the short-circuit && skips the checks for further
        // destinations once one check has failed.
        for (; dest != null; dest = dest.next) {
            noErr = noErr && scopeCheck(a68, top, mask, dest.tuple.level);
        }
        return noErr;
    }

    /** Check_identifier_usage. */
    private static void checkIdentifierUsage(A68 a68, Tag t, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(IDENTIFIER) && p.tag == t && t.type.attribute != PROC_SYMBOL) {
                a68.diagnostics.warning(p, Warn.UNINITIALIZED,
                        "identifier %e might be used uninitialised", Fmt.symbol(p));
            }
            checkIdentifierUsage(a68, t, p.sub);
        }
    }

    /** Scope_find_youngest_outside. */
    private static Tuple scopeFindYoungestOutside(A68 a68, ScopeT s, int treshold) {
        Tuple z = scopeMakeTuple(a68, Parser.PRIMAL_SCOPE, NOT_TRANSIENT);

        for (; s != null; s = s.next) {
            if (s.tuple.level > z.level && s.tuple.level <= treshold) {
                z = s.tuple;
            }
        }
        return z;
    }

    /** Scope_find_youngest. */
    private static Tuple scopeFindYoungest(A68 a68, ScopeT s) {
        return scopeFindYoungestOutside(a68, s, Integer.MAX_VALUE);
    }

    //
    // Routines for determining scope of ROUTINE TEXT or FORMAT TEXT.
    //

    /** Get_declarer_elements. */
    private static void getDeclarerElements(A68 a68, Node p, Ref<ScopeT> r, boolean noRef) {
        if (p != null) {
            if (p.is(BOUNDS)) {
                gatherScopesForYoungest(a68, p.sub, r);
            } else if (p.is(INDICANT)) {
                if (p.type != null && p.tag != null && p.type.hasRows && noRef) {
                    scopeAdd(a68, r, p, scopeMakeTuple(a68, p.tag.symbolTable.level, NOT_TRANSIENT));
                }
            } else if (p.is(REF_SYMBOL)) {
                getDeclarerElements(a68, p.next, r, false);
            } else if (Parser.isOneOf(a68, p, PROC_SYMBOL, UNION_SYMBOL)) {
                // Empty.
            } else {
                getDeclarerElements(a68, p.sub, r, noRef);
                getDeclarerElements(a68, p.next, r, noRef);
            }
        }
    }

    /** Gather_scopes_for_youngest. */
    private static void gatherScopesForYoungest(A68 a68, Node p, Ref<ScopeT> s) {
        for (; p != null; p = p.next) {
            if ((Parser.isOneOf(a68, p, ROUTINE_TEXT, FORMAT_TEXT))
                    && (p.tag.youngestEnviron == Parser.PRIMAL_SCOPE)) {
                Ref.Box<ScopeT> t = Ref.box(null);
                Tuple tup;

                gatherScopesForYoungest(a68, p.sub, t);
                tup = scopeFindYoungestOutside(a68, t.v, p.symbolTable.level);
                p.tag.youngestEnviron = tup.level;
                // Direct link into list iso "gather_scopes_for_youngest (SUB (p),
                // s);".
                if (t.v != null) {
                    ScopeT u = t.v;
                    while (u.next != null) {
                        u = u.next;
                    }
                    u.next = s.get();
                    s.set(t.v);
                }
            } else if (Parser.isOneOf(a68, p, IDENTIFIER, OPERATOR)) {
                if (p.tag != null && p.tag.symbolTable.level != Parser.PRIMAL_SCOPE) {
                    scopeAdd(a68, s, p, scopeMakeTuple(a68, p.tag.symbolTable.level, NOT_TRANSIENT));
                }
            } else if (p.is(DECLARER)) {
                getDeclarerElements(a68, p, s, true);
            } else {
                gatherScopesForYoungest(a68, p.sub, s);
            }
        }
    }

    /** Get_youngest_environs. */
    private static void getYoungestEnvirons(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (Parser.isOneOf(a68, p, ROUTINE_TEXT, FORMAT_TEXT)) {
                Ref.Box<ScopeT> s = Ref.box(null);
                Tuple tup;
                gatherScopesForYoungest(a68, p.sub, s);
                tup = scopeFindYoungestOutside(a68, s.v, p.symbolTable.level);
                p.tag.youngestEnviron = tup.level;
            } else {
                getYoungestEnvirons(a68, p.sub);
            }
        }
    }

    /** Bind_scope_to_tag. */
    private static void bindScopeToTag(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(DEFINING_IDENTIFIER) && p.type == a68.modes.FORMAT) {
                if (p.next.next.is(FORMAT_TEXT)) {
                    p.tag.scope = p.next.next.tag.youngestEnviron;
                    p.tag.scopeAssigned = true;
                }
                return;
            } else if (p.is(DEFINING_IDENTIFIER)) {
                if (p.next.next.is(ROUTINE_TEXT)) {
                    p.tag.scope = p.next.next.tag.youngestEnviron;
                    p.tag.scopeAssigned = true;
                }
                return;
            } else {
                bindScopeToTag(a68, p.sub);
            }
        }
    }

    /** Bind_scope_to_tags. */
    private static void bindScopeToTags(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (Parser.isOneOf(a68, p, PROCEDURE_DECLARATION, IDENTITY_DECLARATION)) {
                bindScopeToTag(a68, p.sub);
            } else {
                bindScopeToTags(a68, p.sub);
            }
        }
    }

    /** Scope_bounds. */
    private static void scopeBounds(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(UNIT)) {
                scopeStatement(a68, p, null);
            } else {
                scopeBounds(a68, p.sub);
            }
        }
    }

    /** Scope_declarer. */
    private static void scopeDeclarer(A68 a68, Node p) {
        if (p != null) {
            if (p.is(BOUNDS)) {
                scopeBounds(a68, p.sub);
            } else if (p.is(INDICANT)) {
                // Empty.
            } else if (p.is(REF_SYMBOL)) {
                scopeDeclarer(a68, p.next);
            } else if (Parser.isOneOf(a68, p, PROC_SYMBOL, UNION_SYMBOL)) {
                // Empty.
            } else {
                scopeDeclarer(a68, p.sub);
                scopeDeclarer(a68, p.next);
            }
        }
    }

    /** Scope_identity_declaration. */
    private static void scopeIdentityDeclaration(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            scopeIdentityDeclaration(a68, p.sub);

            if (p.is(DEFINING_IDENTIFIER)) {
                Node unit = p.next.next;
                Ref.Box<ScopeT> s = Ref.box(null);
                Tuple tup;
                int z = Parser.PRIMAL_SCOPE;

                if (p.tag.type.attribute != PROC_SYMBOL) {
                    checkIdentifierUsage(a68, p.tag, unit);
                }
                scopeStatement(a68, unit, s);
                scopeCheck(a68, s.v, TRANSIENT, p.symbolTable.level);
                tup = scopeFindYoungest(a68, s.v);
                z = tup.level;
                if (z < p.symbolTable.level) {
                    p.tag.scope = z;
                    p.tag.scopeAssigned = true;
                }
                return;
            }
        }
    }

    /** Scope_variable_declaration. */
    private static void scopeVariableDeclaration(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            scopeVariableDeclaration(a68, p.sub);
            if (p.is(DECLARER)) {
                scopeDeclarer(a68, p.sub);
            } else if (p.is(DEFINING_IDENTIFIER)) {
                if (Parser.whether(a68, p, DEFINING_IDENTIFIER, ASSIGN_SYMBOL, UNIT)) {
                    Node unit = p.next.next;
                    Ref.Box<ScopeT> s = Ref.box(null);
                    checkIdentifierUsage(a68, p.tag, unit);
                    scopeStatement(a68, unit, s);
                    scopeCheck(a68, s.v, TRANSIENT, p.symbolTable.level);
                    return;
                }
            }
        }
    }

    /** Scope_procedure_declaration. */
    private static void scopeProcedureDeclaration(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            scopeProcedureDeclaration(a68, p.sub);

            if (Parser.isOneOf(a68, p, DEFINING_IDENTIFIER, DEFINING_OPERATOR)) {
                Node unit = p.next.next;
                Ref.Box<ScopeT> s = Ref.box(null);

                scopeStatement(a68, unit, s);
                scopeCheck(a68, s.v, NOT_TRANSIENT, p.symbolTable.level);
                return;
            }
        }
    }

    /** Scope_declaration_list. */
    private static void scopeDeclarationList(A68 a68, Node p) {
        if (p != null) {
            if (p.is(IDENTITY_DECLARATION)) {
                scopeIdentityDeclaration(a68, p.sub);
            } else if (p.is(VARIABLE_DECLARATION)) {
                scopeVariableDeclaration(a68, p.sub);
            } else if (p.is(MODE_DECLARATION)) {
                scopeDeclarer(a68, p.sub);
            } else if (p.is(PRIORITY_DECLARATION)) {
                // Empty.
            } else if (p.is(PROCEDURE_DECLARATION)) {
                scopeProcedureDeclaration(a68, p.sub);
            } else if (p.is(PROCEDURE_VARIABLE_DECLARATION)) {
                scopeProcedureDeclaration(a68, p.sub);
            } else if (Parser.isOneOf(a68, p, BRIEF_OPERATOR_DECLARATION, OPERATOR_DECLARATION)) {
                scopeProcedureDeclaration(a68, p.sub);
            } else {
                scopeDeclarationList(a68, p.sub);
                scopeDeclarationList(a68, p.next);
            }
        }
    }

    /** Scope_arguments. */
    private static void scopeArguments(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(UNIT)) {
                Ref.Box<ScopeT> s = Ref.box(null);
                scopeStatement(a68, p, s);
                scopeCheck(a68, s.v, TRANSIENT, p.symbolTable.level);
            } else {
                scopeArguments(a68, p.sub);
            }
        }
    }

    /** Is_coercion. */
    private static boolean isCoercion(A68 a68, Node p) {
        if (p != null) {
            switch (p.attribute) {
                case DEPROCEDURING:
                case DEREFERENCING:
                case UNITING:
                case ROWING:
                case WIDENING:
                case VOIDING:
                case PROCEDURING:
                    return true;
                default:
                    return false;
            }
        } else {
            return false;
        }
    }

    /** Scope_coercion. */
    private static void scopeCoercion(A68 a68, Node p, Ref<ScopeT> s) {
        if (isCoercion(a68, p)) {
            if (p.is(VOIDING)) {
                scopeCoercion(a68, p.sub, null);
            } else if (p.is(DEREFERENCING)) {
                // Leave this to the dynamic scope checker.
                scopeCoercion(a68, p.sub, null);
            } else if (p.is(DEPROCEDURING)) {
                scopeCoercion(a68, p.sub, null);
            } else if (p.is(ROWING)) {
                Ref.Box<ScopeT> z = Ref.box(null);

                scopeCoercion(a68, p.sub, z);
                scopeCheck(a68, z.v, TRANSIENT, p.symbolTable.level);
                if (p.sub.type.attribute == REF_SYMBOL && p.sub.type.sub.attribute == FLEX_SYMBOL) {
                    scopeAdd(a68, s, p, scopeMakeTuple(a68, p.symbolTable.level, TRANSIENT));
                } else {
                    scopeAdd(a68, s, p, scopeMakeTuple(a68, p.symbolTable.level, NOT_TRANSIENT));
                }
            } else if (p.is(PROCEDURING)) {
                // Can only be a JUMP.
                Node q = p.sub.sub;
                if (q.is(GOTO_SYMBOL)) {
                    q = q.next;
                }

                scopeAdd(a68, s, q, scopeMakeTuple(a68, q.tag.symbolTable.level, NOT_TRANSIENT));
            } else if (p.is(UNITING)) {
                Ref.Box<ScopeT> z = Ref.box(null);

                scopeCoercion(a68, p.sub, z);
                if (z.v != null) {
                    scopeCheck(a68, z.v, TRANSIENT, p.symbolTable.level);
                    scopeAdd(a68, s, p, scopeFindYoungest(a68, z.v));
                }
            } else {
                scopeCoercion(a68, p.sub, s);
            }
        } else {
            scopeStatement(a68, p, s);
        }
    }

    /** Scope_format_text. */
    private static void scopeFormatText(A68 a68, Node p, Ref<ScopeT> s) {
        for (; p != null; p = p.next) {
            if (p.is(FORMAT_PATTERN)) {
                scopeEnclosedClause(a68, p.sub.next.sub, s);
            } else if (p.is(FORMAT_ITEM_G) && p.next != null) {
                scopeEnclosedClause(a68, p.next.sub, s);
            } else if (p.is(DYNAMIC_REPLICATOR)) {
                scopeEnclosedClause(a68, p.sub.next.sub, s);
            } else {
                scopeFormatText(a68, p.sub, s);
            }
        }
    }

    /** Scope_operand. */
    private static void scopeOperand(A68 a68, Node p, Ref<ScopeT> s) {
        if (p.is(MONADIC_FORMULA)) {
            scopeOperand(a68, p.sub.next, s);
        } else if (p.is(FORMULA)) {
            scopeFormula(a68, p, s);
        } else if (p.is(SECONDARY)) {
            scopeStatement(a68, p.sub, s);
        }
    }

    /** Scope_formula. */
    private static void scopeFormula(A68 a68, Node p, Ref<ScopeT> s) {
        Node q = p.sub;
        Ref.Box<ScopeT> s2 = Ref.box(null);

        scopeOperand(a68, q, s2);
        scopeCheck(a68, s2.v, TRANSIENT, p.symbolTable.level);
        if (q.next != null) {
            Ref.Box<ScopeT> s3 = Ref.box(null);
            scopeOperand(a68, q.next.next, s3);
            scopeCheck(a68, s3.v, TRANSIENT, p.symbolTable.level);
        }
        // C: (void) s; -- the parameter s is unused.
    }

    /** Scope_routine_text. */
    private static void scopeRoutineText(A68 a68, Node p, Ref<ScopeT> s) {
        Node q = p.sub;
        Node routine = (q.is(PARAMETER_PACK) ? q.next : q);
        Ref.Box<ScopeT> x = Ref.box(null);

        scopeStatement(a68, routine.next.next, x);
        scopeCheck(a68, x.v, TRANSIENT, p.symbolTable.level);
        Tuple routineTuple = scopeMakeTuple(a68, p.tag.youngestEnviron, NOT_TRANSIENT);
        scopeAdd(a68, s, p, routineTuple);
    }

    /** Scope_statement. */
    private static void scopeStatement(A68 a68, Node p, Ref<ScopeT> s) {
        if (isCoercion(a68, p)) {
            scopeCoercion(a68, p, s);
        } else if (Parser.isOneOf(a68, p, PRIMARY, SECONDARY, TERTIARY, UNIT)) {
            scopeStatement(a68, p.sub, s);
        } else if (Parser.isOneOf(a68, p, NIHIL)) {
            scopeAdd(a68, s, p, scopeMakeTuple(a68, Parser.PRIMAL_SCOPE, NOT_TRANSIENT));
        } else if (p.is(DENOTATION)) {
            // Empty.
        } else if (p.is(IDENTIFIER)) {
            if (p.type.attribute == REF_SYMBOL) {
                if (p.tag.priority == PARAMETER_IDENTIFIER.ordinal()) {
                    scopeAdd(a68, s, p, scopeMakeTuple(a68, p.tag.symbolTable.level - 1, NOT_TRANSIENT));
                } else {
                    if (p.tag.heap == HEAP_SYMBOL) {
                        scopeAdd(a68, s, p, scopeMakeTuple(a68, Parser.PRIMAL_SCOPE, NOT_TRANSIENT));
                    } else if (p.tag.scopeAssigned) {
                        scopeAdd(a68, s, p, scopeMakeTuple(a68, p.tag.scope, NOT_TRANSIENT));
                    } else {
                        scopeAdd(a68, s, p, scopeMakeTuple(a68, p.tag.symbolTable.level, NOT_TRANSIENT));
                    }
                }
            } else if (p.type.attribute == PROC_SYMBOL && p.tag.scopeAssigned == true) {
                scopeAdd(a68, s, p, scopeMakeTuple(a68, p.tag.scope, NOT_TRANSIENT));
            } else if (p.type == a68.modes.FORMAT && p.tag.scopeAssigned == true) {
                scopeAdd(a68, s, p, scopeMakeTuple(a68, p.tag.scope, NOT_TRANSIENT));
            }
        } else if (p.is(ENCLOSED_CLAUSE)) {
            scopeEnclosedClause(a68, p.sub, s);
        } else if (p.is(CALL)) {
            Ref.Box<ScopeT> x = Ref.box(null);

            scopeStatement(a68, p.sub, x);
            scopeCheck(a68, x.v, NOT_TRANSIENT, p.symbolTable.level);
            scopeArguments(a68, p.sub.next);
        } else if (p.is(SLICE)) {
            Ref.Box<ScopeT> x = Ref.box(null);
            Moid m = p.sub.type;

            if (m.attribute == REF_SYMBOL) {
                if (p.sub.attribute == PRIMARY && p.sub.sub.attribute == SLICE) {
                    scopeStatement(a68, p.sub, s);
                } else {
                    scopeStatement(a68, p.sub, x);
                    scopeCheck(a68, x.v, NOT_TRANSIENT, p.symbolTable.level);
                }
                if (m.sub.attribute == FLEX_SYMBOL) {
                    scopeAdd(a68, s, p.sub, scopeMakeTuple(a68, p.symbolTable.level, TRANSIENT));
                }
                scopeBounds(a68, p.sub.next.sub);
            }
            if (p.type.attribute == REF_SYMBOL) {
                scopeAdd(a68, s, p, scopeFindYoungest(a68, x.v));
            }
        } else if (p.is(FORMAT_TEXT)) {
            Ref.Box<ScopeT> x = Ref.box(null);
            scopeFormatText(a68, p.sub, x);
            scopeAdd(a68, s, p, scopeFindYoungest(a68, x.v));
        } else if (p.is(CAST)) {
            Ref.Box<ScopeT> x = Ref.box(null);
            scopeEnclosedClause(a68, p.sub.next.sub, x);
            scopeCheck(a68, x.v, NOT_TRANSIENT, p.symbolTable.level);
            scopeAdd(a68, s, p, scopeFindYoungest(a68, x.v));
        } else if (p.is(SELECTION)) {
            Ref.Box<ScopeT> ns = Ref.box(null);
            scopeStatement(a68, p.sub.next, ns);
            scopeCheck(a68, ns.v, NOT_TRANSIENT, p.symbolTable.level);
            if (Parser.isRefRefetyFlex(a68, p.sub.next.type)) {
                scopeAdd(a68, s, p, scopeMakeTuple(a68, p.symbolTable.level, TRANSIENT));
            }
            scopeAdd(a68, s, p, scopeFindYoungest(a68, ns.v));
        } else if (p.is(GENERATOR)) {
            if (p.sub.is(LOC_SYMBOL)) {
                if (p.nonLocal != null) {
                    scopeAdd(a68, s, p, scopeMakeTuple(a68, p.nonLocal.level, NOT_TRANSIENT));
                } else {
                    scopeAdd(a68, s, p, scopeMakeTuple(a68, p.symbolTable.level, NOT_TRANSIENT));
                }
            } else {
                scopeAdd(a68, s, p, scopeMakeTuple(a68, Parser.PRIMAL_SCOPE, NOT_TRANSIENT));
            }
            scopeDeclarer(a68, p.sub.next.sub);
        } else if (p.is(FORMULA)) {
            scopeFormula(a68, p, s);
        } else if (p.is(ASSIGNATION)) {
            Node unit = p.sub.next.next;
            Ref.Box<ScopeT> ns = Ref.box(null);
            Ref.Box<ScopeT> nd = Ref.box(null);
            Tuple tup;
            scopeStatement(a68, p.sub.sub, nd);
            scopeStatement(a68, unit, ns);
            scopeCheckMultiple(a68, ns.v, TRANSIENT, nd.v);
            tup = scopeFindYoungest(a68, nd.v);
            scopeAdd(a68, s, p, scopeMakeTuple(a68, tup.level, NOT_TRANSIENT));
        } else if (p.is(ROUTINE_TEXT)) {
            scopeRoutineText(a68, p, s);
        } else if (Parser.isOneOf(a68, p, IDENTITY_RELATION, AND_FUNCTION, OR_FUNCTION)) {
            Ref.Box<ScopeT> n = Ref.box(null);
            scopeStatement(a68, p.sub, n);
            scopeStatement(a68, p.sub.next.next, n);
            scopeCheck(a68, n.v, NOT_TRANSIENT, p.symbolTable.level);
        } else if (p.is(ASSERTION)) {
            Ref.Box<ScopeT> n = Ref.box(null);
            scopeEnclosedClause(a68, p.sub.next.sub, n);
            scopeCheck(a68, n.v, NOT_TRANSIENT, p.symbolTable.level);
        } else if (Parser.isOneOf(a68, p, JUMP, SKIP)) {
            // Empty.
        }
    }

    /** Scope_statement_list. */
    private static void scopeStatementList(A68 a68, Node p, Ref<ScopeT> s) {
        for (; p != null; p = p.next) {
            if (p.is(UNIT)) {
                scopeStatement(a68, p, s);
            } else {
                scopeStatementList(a68, p.sub, s);
            }
        }
    }

    /** Scope_serial_clause. */
    private static void scopeSerialClause(A68 a68, Node p, Ref<ScopeT> s, boolean terminator) {
        if (p != null) {
            if (p.is(INITIALISER_SERIES)) {
                scopeSerialClause(a68, p.sub, s, false);
                scopeSerialClause(a68, p.next, s, terminator);
            } else if (p.is(DECLARATION_LIST)) {
                scopeDeclarationList(a68, p.sub);
            } else if (Parser.isOneOf(a68, p, LABEL, SEMI_SYMBOL, EXIT_SYMBOL)) {
                scopeSerialClause(a68, p.next, s, terminator);
            } else if (Parser.isOneOf(a68, p, SERIAL_CLAUSE, ENQUIRY_CLAUSE)) {
                if (p.next != null) {
                    Attribute j = p.next.attribute;
                    if (j == EXIT_SYMBOL || j == END_SYMBOL || j == CLOSE_SYMBOL) {
                        scopeSerialClause(a68, p.sub, s, true);
                    } else {
                        scopeSerialClause(a68, p.sub, s, false);
                    }
                } else {
                    scopeSerialClause(a68, p.sub, s, true);
                }
                scopeSerialClause(a68, p.next, s, terminator);
            } else if (p.is(LABELED_UNIT)) {
                scopeSerialClause(a68, p.sub, s, terminator);
            } else if (p.is(UNIT)) {
                if (terminator) {
                    scopeStatement(a68, p, s);
                } else {
                    scopeStatement(a68, p, null);
                }
            }
        }
    }

    /** Scope_closed_clause. */
    private static void scopeClosedClause(A68 a68, Node p, Ref<ScopeT> s) {
        if (p != null) {
            if (p.is(SERIAL_CLAUSE)) {
                scopeSerialClause(a68, p, s, true);
            } else if (Parser.isOneOf(a68, p, OPEN_SYMBOL, BEGIN_SYMBOL)) {
                scopeClosedClause(a68, p.next, s);
            }
        }
    }

    /** Scope_collateral_clause. */
    private static void scopeCollateralClause(A68 a68, Node p, Ref<ScopeT> s) {
        if (p != null) {
            if (!(Parser.whether(a68, p, BEGIN_SYMBOL, END_SYMBOL)
                    || Parser.whether(a68, p, OPEN_SYMBOL, CLOSE_SYMBOL))) {
                scopeStatementList(a68, p, s);
            }
        }
    }

    /** Scope_conditional_clause. */
    private static void scopeConditionalClause(A68 a68, Node p, Ref<ScopeT> s) {
        scopeSerialClause(a68, p.sub.next, null, true);
        p = p.next;
        scopeSerialClause(a68, p.sub.next, s, true);
        if ((p = p.next) != null) {
            if (Parser.isOneOf(a68, p, ELSE_PART, CHOICE)) {
                scopeSerialClause(a68, p.sub.next, s, true);
            } else if (Parser.isOneOf(a68, p, ELIF_PART, BRIEF_ELIF_PART)) {
                scopeConditionalClause(a68, p.sub, s);
            }
        }
    }

    /** Scope_case_clause. */
    private static void scopeCaseClause(A68 a68, Node p, Ref<ScopeT> s) {
        Ref.Box<ScopeT> n = Ref.box(null);
        scopeSerialClause(a68, p.sub.next, n, true);
        scopeCheck(a68, n.v, NOT_TRANSIENT, p.symbolTable.level);
        p = p.next;
        scopeStatementList(a68, p.sub.next, s);
        if ((p = p.next) != null) {
            if (Parser.isOneOf(a68, p, OUT_PART, CHOICE)) {
                scopeSerialClause(a68, p.sub.next, s, true);
            } else if (Parser.isOneOf(a68, p, CASE_OUSE_PART, BRIEF_OUSE_PART)) {
                scopeCaseClause(a68, p.sub, s);
            } else if (Parser.isOneOf(a68, p, CONFORMITY_OUSE_PART, BRIEF_CONFORMITY_OUSE_PART)) {
                scopeCaseClause(a68, p.sub, s);
            }
        }
    }

    /** Scope_loop_clause. */
    private static void scopeLoopClause(A68 a68, Node p) {
        if (p != null) {
            if (p.is(FOR_PART)) {
                scopeLoopClause(a68, p.next);
            } else if (Parser.isOneOf(a68, p, FROM_PART, BY_PART, TO_PART)) {
                scopeStatement(a68, p.sub.next, null);
                scopeLoopClause(a68, p.next);
            } else if (p.is(WHILE_PART)) {
                scopeSerialClause(a68, p.sub.next, null, true);
                scopeLoopClause(a68, p.next);
            } else if (Parser.isOneOf(a68, p, DO_PART, ALT_DO_PART)) {
                Node doP = p.sub.next;

                if (doP.is(SERIAL_CLAUSE)) {
                    scopeSerialClause(a68, doP, null, true);
                }
            }
        }
    }

    /** Scope and access-clause. */
    private static void scopeAccessClause(A68 a68, Node p, Ref<ScopeT> s) {
        for (; p != null; p = p.next) {
            if (p.is(ENCLOSED_CLAUSE)) {
                scopeEnclosedClause(a68, p.sub, s);
            }
        }
    }

    /** Scope_enclosed_clause. */
    private static void scopeEnclosedClause(A68 a68, Node p, Ref<ScopeT> s) {
        if (p.is(ENCLOSED_CLAUSE)) {
            scopeEnclosedClause(a68, p.sub, s);
        } else if (p.is(CLOSED_CLAUSE)) {
            scopeClosedClause(a68, p.sub, s);
        } else if (Parser.isOneOf(a68, p, COLLATERAL_CLAUSE, PARALLEL_CLAUSE)) {
            scopeCollateralClause(a68, p.sub, s);
        } else if (p.is(CONDITIONAL_CLAUSE)) {
            scopeConditionalClause(a68, p.sub, s);
        } else if (Parser.isOneOf(a68, p, CASE_CLAUSE, CONFORMITY_CLAUSE)) {
            scopeCaseClause(a68, p.sub, s);
        } else if (p.is(LOOP_CLAUSE)) {
            scopeLoopClause(a68, p.sub);
        } else if (p.is(ACCESS_CLAUSE)) {
            scopeAccessClause(a68, p.sub, s);
        }
    }

    /** Whether a symbol table contains no (anonymous) definition. */
    private static boolean emptyTable(A68 a68, Table t) {
        if (t.identifiers == null) {
            return (t.operators == null && t.indicants == null);
        } else if (t.identifiers.priority == LOOP_IDENTIFIER.ordinal() && t.identifiers.next == null) {
            return (t.operators == null && t.indicants == null);
        } else if (t.identifiers.priority == SPECIFIER_IDENTIFIER.ordinal() && t.identifiers.next == null) {
            return (t.operators == null && t.indicants == null);
        } else {
            return false;
        }
    }

    /** Indicate non-local environs. */
    private static void getNonLocalEnvirons(A68 a68, Node p, int max) {
        for (; p != null; p = p.next) {
            if (p.is(ROUTINE_TEXT)) {
                getNonLocalEnvirons(a68, p.sub, p.sub.symbolTable.level);
            } else if (p.is(FORMAT_TEXT)) {
                getNonLocalEnvirons(a68, p.sub, p.sub.symbolTable.level);
            } else {
                getNonLocalEnvirons(a68, p.sub, max);
                p.nonLocal = null;
                if (p.symbolTable != null) {
                    Table q = p.symbolTable;
                    while (q != null && emptyTable(a68, q)
                            && q.previous != null && q.previous.level >= max) {
                        p.nonLocal = q.previous;
                        q = q.previous;
                    }
                }
            }
        }
    }

    /** Scope a module text. */
    private static void scopeModuleText(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(DEF_PART) || p.is(POSTLUDE_PART)) {
                Node clause = p.sub.next;
                Parser.gccAssert(clause.is(ENQUIRY_CLAUSE) || clause.is(SERIAL_CLAUSE));
                scopeSerialClause(a68, clause, null, true /* terminator */);
            }
        }
    }

    /** Scope a module declaration. */
    private static void scopeModuleDeclaration(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            if (p.is(MODULE_TEXT)) {
                scopeModuleText(a68, p.sub);
            } else {
                scopeModuleDeclaration(a68, p.sub);
            }
        }
    }

    /** Scope a particular program. */
    private static void scopeParticularProgram(A68 a68, Node p) {
        scopeEnclosedClause(a68, p.sub.sub, null);
    }

    /** Scope a prelude packet. */
    private static void scopePreludePacket(A68 a68, Node p) {
        Parser.gccAssert(p.sub.is(MODULE_DECLARATION));
        scopeModuleDeclaration(a68, p.sub);
    }

    /** a68_scope_checker: the static scope checker. */
    public static void scopeChecker(A68 a68, Node p) {
        // Establish scopes of routine texts and format texts.
        getYoungestEnvirons(a68, p);
        // Find non-local environs.
        getNonLocalEnvirons(a68, p, Parser.PRIMAL_SCOPE);
        // PROC and FORMAT identities can now be assigned a scope.
        bindScopeToTags(a68, p);

        // Now check everything else.
        Parser.gccAssert(p.is(PACKET));
        if (p.sub.is(PARTICULAR_PROGRAM)) {
            scopeParticularProgram(a68, p.sub);
        } else if (p.sub.is(PRELUDE_PACKET)) {
            scopePreludePacket(a68, p.sub);
        } else {
            throw Parser.gccUnreachable();
        }
    }
}
