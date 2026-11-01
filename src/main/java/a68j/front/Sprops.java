// Java port of ga68's a68-parser-sprops.cc: compute some static properties of values.
// Original: Copyright (C) 2026 Jose E. Marchesi.
// Written by Jose E. Marchesi. Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Access.*;
import static a68j.front.Attribute.*;
import static a68j.front.Kindo.*;

import java.util.Objects;

/**
 * This parser pass computes a set of static properties of certain parse tree
 * nodes and symtab entries representing source constructs. It is somewhat
 * inspired on the treatment of static properties described in
 *
 * <pre>
 *   An Optimized Translation Process and Its Application to ALGOL 68
 *   P. Branquart et al.
 * </pre>
 *
 * However, the compilation system described in Branquart et al translates
 * constructs to an IR which is considerably lower in abstraction compared to
 * GENERIC, and therefore our management of static properties is different.
 * The underlying ideas, however, are very similar.
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>{@code ORIGIN_T *} is {@link Origin}, {@code NO_ORIGIN} is {@code null};
 * {@code ACCESS_T} is {@link Access}.</li>
 * <li>{@code ORIGIN (p)} is {@code p.origin}, {@code ACCESS (p)} is
 * {@code p.access}; {@code KINDO (p)}, {@code BNO (p)}, {@code DEREFO (p)},
 * {@code GENO (p)} are {@code p.origin.kindo}, {@code p.origin.bno},
 * {@code p.origin.derefo}, {@code p.origin.geno} (likewise for tags).</li>
 * <li>{@code diago} (C: {@code location_t} from {@code a68_get_node_location})
 * is a {@link Location} from {@link Location#of(Node)}.</li>
 * </ul>
 */
public final class Sprops {
    private Sprops() {}

    /* The origin
       ──────────

       The "origin" is a static property of source constructs that yield values.
       Its purpose is to record the properties of the original value from which the
       construct is derived, as well as the transformations the value has been
       submitted to before appearing in the construct.  This static property is
       composed by several fields described below.

       kindo is the "kind of the origin".
       ─────

         It keeps track of the fact that a value is issued from

          ╭─────────╮
          │KINDO_IDE│ an identifier in a VAR_DECL,
          ╰─────────╯
          ╭─────────╮
          │KINDO_VAR│ a variable in a VAR_DECL,
          ╰─────────╯
          ╭─────────╮
          │KINDO_CST│ a constant in a VAR_DECL with TREE_CONSTANT=1,
          ╰─────────╯
          ╭─────────╮
          │KINDO_GEN│ a generator i.e. a malloc or alloca,
          ╰─────────╯
          ╭─────────╮
          │KINDO_NIL│ or another construct.
          ╰─────────╯

         This property remains invariant through the static elaboration of a number
         of actions such as slices, selection and dereferencings.

        bno is the "block number of the origin".
        ───

         Its meaning for a particular construct is to be interpreted along with its
         kindo.

         ● For KINDO_IDE and KINDO_VAR, bno indicates the depth number of the block
           where the identifier or the variable is declared.  This is not to be
           confused with the scope of the value ascribed to the identifier or
           stored in the variable.

         ● For KINDO_GEN, when it corresponds to a local generator, bno indicates
           the depth number of the block where the generator appears.

         ● For KINDO_CST, and for KINDO_GEN when it corresponds to a heap
           generator, bno is always zero.

         ● For KINDO_NIL, bno indicates the depth number of the construct issuing
           the value.

        derefo is the "flag dereferencing of the origin".
        ──────

         This flag indicates whether a dereferencing action has taken place
         starting from the construct in whcih kindo has been set up.  This
         information allows, in some cases, to statically detect the absence of
         side-effects, and subsequently to avoid some copies of values.

        geno is the "flag local generator of the origin".
        ────

         This flag indicates whether a local generator is involved in the
         construction of this value.

        diago contains the "diagnostics of the origin".
        ─────

         This property contains location information of the construct originating
         the value.  It is useful in order to emit good diagnostic messages in
         dynamic checks pointing to the source program construct giving rise to the
         value involved in the dynamic check.  */

    /* The access
       ──────────

       In the "low" pass parse tree nodes denoting units will be lowered to GCC
       GENERIC trees.  These trees compute the values yielded by the units.  For
       example, a DENOTATION node for an integral value may be lowered to an
       INTEGER_CST tree, a FORMULA node to a PLUS_EXPR tree, an applied IDENTIFIER
       to a VAR_DECL, and a DEREFERENCING node to an INDIRECT_REF tree.

       In principle, it would be expected for the resulting trees to directly
       compute the value denoted by the parse nodes originating them.  However, for
       reasons of efficiency, this is not always the case.

       Parse nodes lowering to trees that directly compute the value yielded by the
       unit are said to have "direct access".  Examples:

       ● Consider a denotation "10".  The parser builds a parse node DENOTATION
         with the value 10 for it, and the "low" pass lowers that parse node into a
         GENERIC tree consisting in a single INTEGER_CST tree node, with value 10.
         In this case, the tree indeed computes the value yielded by the DENOTATION
         node.

       ● An applied IDENTIFIER "maxint" with mode int that has been declared via an
         identity declaration, with some integral value ascribed to it.  The "low"
         pass lowers it into a VAR_DECL with TREE_TYPE int.  Again, this node has
         "direct access", because the VAR_DECL in a GENERIC r-value position
         computes the value ascribed to the IDENTIFIER.

       Parse nodes denoting units that yield names, lowering to VAR_DECL trees
       whose address is the value yielded by the unit are said to have "variable
       access".  Examples:

       ● Suppose that another applied IDENTIFIER "count" with mode int has been
         declared, this time via a variable declaration.  The value ascribed to the
         identifier is in this case a name with mode "ref int".  Such a name would
         generally be lowered to a tree of type *int, i.e. the address of the
         variable, but in this case the node will be lowered to a VAR_DECL with
         type int instead.  This is an optimization whose goal is to avoid
         unnecessary indirect addressing.  The tree represents the name and can be
         moved around as-is, but when it comes to access the actual value of the
         name, i.e. the address of the integral value referenced by the name, it
         becomes necessary to take the address of the VAR_DECL.

       Parse nodes lowering to trees that need to be indirected in order to compute
       the value yielded by the unit are said to have "indirect access".  Examples:

       ● Consider what happens when a DEREFERENCING node has a coercend with
         "variable access" which is of mode "ref int", or alternatively a coercend
         with "direct access" also of mode "ref int".  In the first case, the
         coercend will be lowered to a VAR_DECL with type int, as an optimization.
         In the second case, the coercend will be lowered to some tree with type
         *int.  It would make sense for the DEREFERENCING node to be of "direct
         class" and be lowered to an INDIRECT_REF tree taking as argument the
         address of the VAR_DECL in the first case, and just the value of the
         VAR_DECL in the second case.  However, as an optimization, the
         dereferencing of nodes with "direct class" or "variable class" doesn't
         require any run-time action (other than perhaps checking for nil) and the
         DEREFERENCING node is lowered to the coercend tree without any
         modification.  Only when the dereferenced value is actually used,
         indirection will be performed.

       ● The value yielded by the last unit in a serial clause doesn't need to be
         copied into the outer range if the yielded value is accessible in that
         range.  Instead, the unit gets lowered to a tree computing the address of
         the value, and is given indirect access.

       The different access classes are summarized below:

          ╭──────────╮
          │ACCESS_IND│ indirect access.
          ╰──────────╯
          ╭──────────╮
          │ACCESS_DIR│ direct access.
          ╰──────────╯
          ╭──────────╮
          │ACCESS_VAR│ variable access.
          ╰──────────╯


                        IND    DIR    VAR
                        ───    ───    ───
            node mode:  int    int    ref int
            tree type: *int    int    int


       The purpose of this static property is thus to guide the lowering pass, but
       there is certain level of circularity, as the calculation of the access is
       also influenced by the behavior of the lowering pass.

       It is important to remember that the access static property only makes sense
       for constructs that yield values, i.e. units.  Nodes that are not units are
       annotated with NO_ACCESS, which denotes no access.  */

    /* Handling of choice constructs
       ─────────────────────────────

       Choice constructs include:

       ● Serial clauses with completers.
       ● Conditional clauses.
       ● Conformity clauses.
       ● Case clauses.

       The characteristic of these constructs is that they need to perform
       balancing on a set of alternatives, like the values yielded by the then-part
       and the else-part of a conditional clause, in order to determine the static
       properties of the value yielded by the choice construct.

       Given a choice construct involving sub-values V1, V2, .. Vn, with their
       respective "a priori" static properties, the "a posteriori" static
       properties of the resulting value Vr are derived as follows:

       ● The mode of Vr (which is a static property, albeit not handled here) is
         determined from the balancing of the modes of V1, V2, ... Vn.  This has
         already been done by the parser at this stage and the parse tree nodes are
         annotated with the "a posteriori" mode of the choice construct.  Suitable
         coercions are also in the parse tree.

       ● If all sub-values have the same "a priori" origin, then that's the origin
         of Vr.  Otherwise, we act conservatively by setting KINDO (Vr) to
         KINDO_NIL, DEREFO to true iff any of the sub-values have DEREFO set, and
         GENO to true iff any of the sub-values have GENO set.


       ● If all sub-values have the same "a priori" access, then that's the access
         of Vr.  Otherwise, use direct access.

       These rules are implemented in the corresponding handlers for choice
       constructs, below.  */

    /**
     * make_origin: allocate a new origin and return it. The new origin is of
     * kind NIL, and its properties are derived from the given parse node P.
     */
    private static Origin makeOrigin(A68 a68, Node p) {
        // GCC-specific, omitted: ggc_alloc.
        Origin ori = new Origin();
        ori.kindo = KINDO_NIL;
        ori.diago = Location.of(p);
        ori.bno = p.symbolTable.level;
        ori.derefo = false;
        ori.geno = false;
        return ori;
    }

    /** dup_origin: allocate a copy of ORI and return it. */
    private static Origin dupOrigin(A68 a68, Origin ori) {
        // GCC-specific, omitted: ggc_alloc.
        Origin res = new Origin();
        // C: *res = *ori;
        res.kindo = ori.kindo;
        res.bno = ori.bno;
        res.derefo = ori.derefo;
        res.geno = ori.geno;
        res.diago = ori.diago;
        return res;
    }

    /** origin_equal_p: determine whether ORI1 and ORI2 reflect the same origin. */
    private static boolean originEqualP(A68 a68, Origin ori1, Origin ori2) {
        // Note: as in C, bno is not compared.  C compares the location_t values
        // of diago with ==; Location is a value record, hence Objects.equals.
        return (ori1 != null
                && ori2 != null
                && (ori1 == ori2
                        || (ori1.kindo == ori2.kindo
                                && ori1.derefo == ori2.derefo
                                && ori1.geno == ori2.geno
                                && Objects.equals(ori1.diago, ori2.diago))));
    }

    /* Denotations
       ───────────

       Denotations of any mode introduce their own constant origin, and always have
       direct access.  */

    /** sprops_for_denotation */
    private static void spropsForDenotation(A68 a68, Node p) {
        p.origin = makeOrigin(a68, p);
        p.origin.kindo = KINDO_CST;
        p.access = ACCESS_DIR;
    }

    /* Widening coercion
       ─────────────────

       Widening of integral and real values result into direct real and complex
       values.  The widening of bits and bytes result into direct multiple values.
       This is a kernel invariant operation, so the origin stays unchanged.  */

    /** sprops_for_widening */
    private static void spropsForWidening(A68 a68, Node p) {
        p.origin = p.sub.origin;
        p.access = ACCESS_DIR;
    }

    /* Voiding coercion
       ────────────────

       The origin of the voiding construct is the origin of the voided value.  The
       resulting voided voided value has acess NIL, denoting the absence of value.
       This is a kernel invariant operation, so the origin stays unchanged.  */

    /** sprops_for_voiding */
    private static void spropsForVoiding(A68 a68, Node p) {
        p.origin = p.sub.origin;
        p.access = ACCESS_NIL;
    }

    /* Dereferencing coercion
       ──────────────────────

       The origin of the dereferenced value is like the origin of the coercend but
       with DEREFO set.  We always use an indirect access for the coercee so we
       delay actual indirection for when (and if) the dereferenced value actually
       gets used.  */

    /** sprops_for_dereferencing */
    private static void spropsForDereferencing(A68 a68, Node p) {
        p.origin = dupOrigin(a68, p.sub.origin);
        p.origin.derefo = true;
        p.access = ACCESS_IND;
    }

    /* Deproceduring coercion
       ──────────────────────

       It is not possible to detemine the origin of the value yielded by the
       elaboration of the procedure, so we create a new one with KINDO_NIL.  The
       access of the yielded value is always direct.  */

    /** sprops_for_deproceduring */
    private static void spropsForDeproceduring(A68 a68, Node p) {
        p.origin = makeOrigin(a68, p);
        p.access = ACCESS_DIR;
    }

    /* Proceduring
       ───────────

       Procedured jumps result into a proc value.  The origin is new and is of
       KINDO_NIL.  The access for the resulting procedure is always direct.  */

    /** sprops_for_proceduring */
    private static void spropsForProceduring(A68 a68, Node p) {
        p.origin = makeOrigin(a68, p);
        p.access = ACCESS_DIR;
    }

    /* Rowing coercion
       ───────────────

       The origin of the coercee is the origin of the coercend.  The access of the
       resulting value is always direct: reals, complex or multiples of bools or
       chars.  This is a kernel invariant operation, so the origin stays
       unchanged. */

    /** sprops_for_rowing */
    private static void spropsForRowing(A68 a68, Node p) {
        p.origin = p.sub.origin;
        p.access = ACCESS_DIR;
    }

    /* Uniting coercion
       ────────────────

       The origin of the coercee is the origin of the coercend.  XXX this is a sort
       of multiple choices problem as well.  This is a kernel invariant operation,
       so the origin stays unchanged.  */

    /** sprops_for_uniting */
    private static void spropsForUniting(A68 a68, Node p) {
        p.origin = p.sub.origin;
        p.access = ACCESS_DIR; /* XXX */
    }

    /* Loop clauses
       ────────────

       The loop clause effectively acts like voiding, so it has its own origin.  We
       use NIL access, which denotes the absence of value.  */

    /** sprops_for_loop_clause */
    private static void spropsForLoopClause(A68 a68, Node p) {
        p.origin = makeOrigin(a68, p);
        p.access = ACCESS_NIL;
    }

    /* Enclosed clauses
       ────────────────

       The origin and access of the value of an enclosed clause is the origin and
       access of the value of its enclosed clause :-O  */

    /** sprops_for_enclosed_clause */
    private static void spropsForEnclosedClause(A68 a68, Node p) {
        Node enclosedClause = p.sub;
        p.origin = enclosedClause.origin;
        p.access = enclosedClause.access;
    }

    /* Access clauses
       ──────────────

       The origin/access of the access clause is the origin/access of the
       controlled clause.  */

    /** sprops_for_access_clause */
    private static void spropsForAccessClause(A68 a68, Node p) {
        p.origin = p.sub.next.origin;
        // Sic: ACCESS (p) is not set here, despite the comment above; it keeps
        // its previous value (ACCESS_DIR as set by a68_new_node).
    }

    /* Enquiry clauses
       ───────────────

       The origin of the enquiry clause is the origin of the unit yielded by the
       underlying serial clause, which is assured to not have choices.  The enquiry
       clause always yields a boolean value, which much have direct access.  */

    /** sprops_for_enquiry_clause */
    private static void spropsForEnquiryClause(A68 a68, Node p) {
        if (p.sub.is(UNIT)) {
            p.origin = p.sub.origin;
        } else if (p.sub.is(ENQUIRY_CLAUSE) || p.sub.is(INITIALISER_SERIES)) {
            p.origin = p.sub.next.next.origin;
        } else {
            throw Parser.gccUnreachable();
        }
        p.access = ACCESS_DIR;
    }

    /* Serial clauses
       ──────────────

       Serial clauses that have completers are choice constructs, in the sense they
       yield one of several units determined at run-time, and therefore it becomes
       necessary to do some "balancing" at compile-time.  See the comment "Handling
       of choice constructs" above for a description of the strategy we follow for
       the "origin" and "access" static properties in such constructs.  */

    /** sprops_for_serial_clause */
    private static void spropsForSerialClause(A68 a68, Node p) {
        Node lastUnit = null, completer = null;

        if (p.sub.is(UNIT)) {
            lastUnit = p.sub;
        } else if (p.sub.is(LABELED_UNIT)) {
            lastUnit = p.sub.sub.next;
        } else {
            Parser.gccAssert(p.sub.is(SERIAL_CLAUSE) || p.sub.is(INITIALISER_SERIES));

            if (p.sub.next.is(EXIT_SYMBOL)) {
                completer = p.sub;
            }

            Node q = p.sub.next.next;
            if (q.is(UNIT)) {
                lastUnit = q;
            } else {
                Parser.gccAssert(q.is(LABELED_UNIT));
                lastUnit = q.sub.next;
            }
        }

        Parser.gccAssert(lastUnit != null);
        Parser.gccAssert(lastUnit.origin != null);

        if (completer == null) {
            p.origin = lastUnit.origin;
            p.access = lastUnit.access;
        } else {
            /* Balance origin.  */
            if (originEqualP(a68, completer.origin, lastUnit.origin)) {
                p.origin = completer.origin;
            } else {
                boolean foundDerefo = lastUnit.origin.derefo | completer.origin.derefo;
                boolean foundGeno = lastUnit.origin.geno | completer.origin.geno;
                p.origin = makeOrigin(a68, p);
                p.origin.derefo = foundDerefo;
                p.origin.geno = foundGeno;
            }

            /* Balance access.  */
            if (completer.access == lastUnit.access) {
                p.access = completer.access;
            } else {
                p.access = ACCESS_DIR;
            }
        }
    }

    /* Parallel clauses
       ────────────────

       A parallel clause yields void, thus we create a fresh origin with KINDO_NIL.
       The access is NIL.  */

    /** sprops_for_parallel_clause */
    private static void spropsForParallelClause(A68 a68, Node p) {
        p.origin = makeOrigin(a68, p);
        p.access = ACCESS_NIL;
    }

    /* Closed clauses
       ──────────────

       The origin/access of a closed clause is the origin/access of its contained
       serial clause.  */

    /** sprops_for_closed_clause */
    private static void spropsForClosedClause(A68 a68, Node p) {
        p.origin = p.sub.next.origin;
        // Sic: ACCESS (p) is not set here, despite the comment above; it keeps
        // its previous value (ACCESS_DIR as set by a68_new_node).
    }

    /* Conditional clauses
       ───────────────────

       The if-part and else-part of the conditional clause makes it a choice
       construct in terms of static properties propagation.  See "Handling of
       choice constructs" above.  */

    /** sprops_for_conditional_clause */
    private static void spropsForConditionalClause(A68 a68, Node p) {
        Node thenPart = null, elsePart = null;

        /* IF or ELIF part,SUB is an enquiry clause.  */
        Node s = p.sub;

        /* THEN part, SUB is a serial clause.  */
        s = s.next;
        thenPart = s.sub.next;

        /* ELSE part. */
        s = s.next;
        if (s.is(CHOICE) || s.is(ELSE_PART)) {
            // Sic: CHOICE and ELSE_PART are (symbol, SERIAL_CLAUSE), so
            // NEXT (NEXT_SUB (s)) is NO_NODE and the else-part does not take part
            // in the balancing below.
            elsePart = s.sub.next.next;
        } else if (s.is(CLOSE_SYMBOL) || s.is(FI_SYMBOL)) {
            ;
        } else {
            /* ELIF part.  Recurse.  */
            spropsForConditionalClause(a68, s);
            elsePart = s;
        }

        if (elsePart == null) {
            p.origin = thenPart.origin;
            p.access = thenPart.access;
        } else {
            /* Balance origin.  */
            if (originEqualP(a68, thenPart.origin, elsePart.origin)) {
                p.origin = elsePart.origin;
            } else {
                boolean foundDerefo = thenPart.origin.derefo | elsePart.origin.derefo;
                boolean foundGeno = thenPart.origin.geno | elsePart.origin.geno;

                p.origin = makeOrigin(a68, p);
                p.origin.derefo = foundDerefo;
                p.origin.geno = foundGeno;
            }

            /* Balance access.  */
            if (thenPart.access == elsePart.access) {
                p.access = elsePart.access;
            } else {
                p.access = ACCESS_DIR;
            }
        }
    }

    /* Case clauses
       ────────────

       The existence of several alterantives in a case clause makes it a choice
       construct in terms of static properties propagation.  See "Handling of
       choice constructs" above.  */

    /** sprops_for_case_unit */
    private static void spropsForCaseUnit(A68 a68, Node p,
                                          boolean foundMismatch,
                                          Ref<Origin> postulatedOrigin,
                                          boolean[] foundDerefo, boolean[] foundGeno,
                                          Access[] postulatedAccess) {
        for (; p != null; p = p.next) {
            if (p.is(UNIT)) {
                foundDerefo[0] |= p.origin.derefo;
                foundGeno[0] |= p.origin.geno;

                // Sic: found_mismatch is passed by value, so a mismatch found in
                // a recursive call is not seen by the caller.
                if (postulatedOrigin.get() == null && !foundMismatch) {
                    postulatedOrigin.set(p.origin);
                } else if (!originEqualP(a68, p.origin, postulatedOrigin.get())) {
                    postulatedOrigin.set(null);
                    foundMismatch = true;
                }
            } else {
                spropsForCaseUnit(a68, p.sub,
                                  foundMismatch,
                                  postulatedOrigin,
                                  foundDerefo, foundGeno,
                                  postulatedAccess);
            }
        }
        // Sic: postulated_access is never assigned.
    }

    /** sprops_for_case_clause */
    private static void spropsForCaseClause(A68 a68, Node p) {
        Node outPart = null;

        /* CASE or OUSE.  */
        Node s = p.sub;

        /* IN.  */
        Node inParts = s = s.next;

        /* OUT.  */
        s = s.next;
        if (s.is(CHOICE) || s.is(OUT_PART)) {
            // Sic: CHOICE and OUT_PART are (symbol, SERIAL_CLAUSE), so
            // NEXT (NEXT_SUB (s)) is NO_NODE and no out-part is postulated.
            outPart = s.sub.next.next;
        } else if (s.is(CLOSE_SYMBOL) || s.is(ESAC_SYMBOL)) {
            ;
        } else {
            /* Recurse.  */
            spropsForCaseClause(a68, s);
            outPart = s;
        }

        /* We start by postulating the properties of the out-part, if it exists, then
           go through all the in-parts.  */

        Ref<Origin> postulatedOrigin = Ref.box(null);
        Access[] postulatedAccess = {NO_ACCESS};

        boolean[] foundDerefo = {false}, foundGeno = {false};

        if (outPart != null) {
            postulatedOrigin.set(outPart.origin);
        }

        // Sic: the walk starts at the in-parts but continues over all their
        // siblings (including the out-part and the closing symbol).
        spropsForCaseUnit(a68, inParts, false,
                          postulatedOrigin,
                          foundDerefo, foundGeno,
                          postulatedAccess);

        /* Balance origin.  */
        if (postulatedOrigin.get() != null) {
            p.origin = postulatedOrigin.get();
        } else {
            p.origin = makeOrigin(a68, p);
            p.origin.derefo = foundDerefo[0];
            p.origin.geno = foundGeno[0];
        }

        /* Balance access.  */
        // Sic: postulated_access is always NO_ACCESS here, so the access is
        // always ACCESS_DIR.
        if (postulatedAccess[0] != NO_ACCESS) {
            p.access = postulatedAccess[0];
        } else {
            p.access = ACCESS_DIR;
        }

    }

    /* Collateral clauses
       ──────────────────

       We distinguish between two cases when handling the static properties of
       collateral clauses:

       ● VOID-collateral-clauses yield void, and therefore they have origin of kind
         NIL and access NIL.

       ● Row- and struct-displays yield a multiple value, with origin constant and
         direct access.  */

    /** sprops_for_collateral_clause */
    private static void spropsForCollateralClause(A68 a68, Node p) {
        p.origin = makeOrigin(a68, p);

        if (p.type == a68.modes.VOID) {
            p.origin.kindo = KINDO_NIL;
            p.access = ACCESS_NIL;
        } else {
            p.origin.kindo = KINDO_CST;
            p.access = ACCESS_DIR;
        }
    }

    /* Conformity clauses
       ──────────────────

       The existence of several alterantives in a conformity clause makes it a
       choice construct in terms of static properties propagation.  See "Handling
       of choice constructs" above.  */

    /** sprops_for_unite_case_unit */
    private static void spropsForUniteCaseUnit(A68 a68, Node p,
                                               boolean foundMismatch,
                                               Ref<Origin> postulatedOrigin,
                                               boolean[] foundDerefo, boolean[] foundGeno,
                                               Access[] postulatedAccess) {
        for (; p != null; p = p.next) {
            if (p.is(SPECIFIER)) {
                Node specUnit = p.next.next;

                foundDerefo[0] |= specUnit.origin.derefo;
                foundGeno[0] |= specUnit.origin.geno;

                // Sic: found_mismatch is passed by value, so a mismatch found in
                // a recursive call is not seen by the caller.
                if (postulatedOrigin.get() == null && !foundMismatch) {
                    postulatedOrigin.set(specUnit.origin);
                } else if (!originEqualP(a68, specUnit.origin, postulatedOrigin.get())) {
                    postulatedOrigin.set(null);
                    foundMismatch = true;
                }

                p = p.next; /* Skip specifier.  */
                p = p.next; /* Skip unit.  */
                /* The unit is skipped in the for loop post-action.  */
                // Note: the two FORWARDs actually skip the specifier and the colon;
                // the unit is skipped by the loop post-action.
            } else {
                spropsForUniteCaseUnit(a68, p.sub,
                                       foundMismatch,
                                       postulatedOrigin,
                                       foundDerefo, foundGeno,
                                       postulatedAccess);
            }
        }
        // Sic: postulated_access is never assigned.
    }

    /** sprops_for_conformity_clause */
    private static void spropsForConformityClause(A68 a68, Node p) {
        Node outPart = null;

        /* CASE or OUSE.  */
        Node s = p.sub;

        /* IN.  */
        Node inParts = s = s.next;

        /* OUT.  */
        s = s.next;
        if (s.is(CHOICE) || s.is(OUT_PART)) {
            // Sic: CHOICE and OUT_PART are (symbol, SERIAL_CLAUSE), so
            // NEXT (NEXT_SUB (s)) is NO_NODE and no out-part is postulated.
            outPart = s.sub.next.next;
        } else if (s.is(CLOSE_SYMBOL) || s.is(ESAC_SYMBOL)) {
            ;
        } else {
            /* Recurse.  */
            spropsForConformityClause(a68, s);
            outPart = s;
        }

        /* We start by postulating the properties of the out-part, if it exists, then
           go through all the in-parts.  */

        Ref<Origin> postulatedOrigin = Ref.box(null);
        Access[] postulatedAccess = {NO_ACCESS};

        boolean[] foundDerefo = {false}, foundGeno = {false};

        if (outPart != null) {
            postulatedOrigin.set(outPart.origin);
        }

        // Sic: the walk starts at the in-parts but continues over all their
        // siblings (including the out-part and the closing symbol).
        spropsForUniteCaseUnit(a68, inParts, false,
                               postulatedOrigin,
                               foundDerefo, foundGeno,
                               postulatedAccess);

        /* Balance origin.  */
        if (postulatedOrigin.get() != null) {
            p.origin = postulatedOrigin.get();
        } else {
            p.origin = makeOrigin(a68, p);
            p.origin.derefo = foundDerefo[0];
            p.origin.geno = foundGeno[0];
        }

        /* Balance access.  */
        // Sic: postulated_access is always NO_ACCESS here, so the access is
        // always ACCESS_DIR.
        if (postulatedAccess[0] != NO_ACCESS) {
            p.access = postulatedAccess[0];
        } else {
            p.access = ACCESS_DIR;
        }
    }

    /* Identity relations
       ──────────────────

       The identity relation yields a new boolean value with a fresh origin.  Its
       access is always direct.  */

    /** sprops_for_identity_relation */
    private static void spropsForIdentityRelation(A68 a68, Node p) {
        p.origin = makeOrigin(a68, p);
        p.access = ACCESS_DIR;
    }

    /* Assignations
       ────────────

       If according to previsions the assignation is immediately dereferenced, the
       dereferencing is combined with the assignation by the lowerer, and we use
       the static properties of the source instaed of those of the destination.  */

    /** sprops_for_assignation */
    private static void spropsForAssignation(A68 a68, Node p) {
        Node destination = p.sub;
        p.origin = destination.origin;
        /* XXX implement prevision.  */
        p.access = ACCESS_DIR;
    }

    /* Slices
       ──────

       The origin of a slice is the origin of the primary being sliced.  The access
       is alwyas direct.  This is a kernel invariant operation, so the origin stays
       unchanged.  */

    /** sprops_for_slice */
    private static void spropsForSlice(A68 a68, Node p) {
        Node primary = p.sub;
        p.origin = primary.origin;
        p.access = ACCESS_DIR;
    }

    /* Selections
       ──────────

       The origin of a selection is the origin of the secondary being selected.
       The access is always direct.  This is a kernel invariant operation, so the
       origin stays unchanged. */

    /** sprops_for_selection */
    private static void spropsForSelection(A68 a68, Node p) {
        Node secondary;

        if (p.sub.is(SELECTOR)) {
            secondary = p.sub.next;
        } else {
            Parser.gccAssert(p.sub.is(SECONDARY));
            secondary = p.sub;
        }
        p.origin = secondary.origin;
        p.access = ACCESS_DIR;
    }

    /* Logical functions
       ─────────────────

       The logical function constructs (or_function, and_function) introduce a new
       origin, which is of class KINDO_NIL.  The value yielded by these constructs
       is a boolean and its access is always direct.  */

    /** sprops_for_logical_function */
    private static void spropsForLogicalFunction(A68 a68, Node p) {
        p.origin = makeOrigin(a68, p);
        p.access = ACCESS_DIR;
    }

    /* Casts
       ─────

       Both origin and access of the coercee are the same origin and access o the
       coercend in the strong context introduced by the cast.  This is a kernel
       invariant operation, so the origin stays unchanged. */

    /** sprops_for_cast */
    private static void spropsForCast(A68 a68, Node p) {
        Node coercend = p.sub.next;
        p.origin = coercend.origin;
        p.access = coercend.access;
    }

    /* Calls
       ─────

       The value yielded by a call has a fresh origin of class NIL.  The access is
       always direct.  */

    /** sprops_for_call */
    private static void spropsForCall(A68 a68, Node p) {
        p.origin = makeOrigin(a68, p);
        p.access = ACCESS_DIR;
    }

    /* Generators
       ──────────

       Generators introduce a fresh origin of class GEN, and appropriate
       attributes.  The access is always IND.  */

    /** sprops_for_generator */
    private static void spropsForGenerator(A68 a68, Node p) {
        p.origin = makeOrigin(a68, p);
        p.origin.kindo = KINDO_GEN;

        if (p.sub.is(LOC_SYMBOL)) {
            p.origin.bno = p.symbolTable.level;
            p.origin.geno = true;
        } else {
            p.origin.bno = 0;
            p.origin.geno = false;
        }
        // Sic: ACCESS (p) is not set here, despite the comment above; it keeps
        // its previous value (ACCESS_DIR as set by a68_new_node).
    }

    /* Monadic formulas
       ────────────────

       The value yielded by a monadic formula has the same origin than the single
       operand.  The access is always direct.  */

    /** sprops_for_monadic_formula */
    private static void spropsForMonadicFormula(A68 a68, Node p) {
        p.origin = p.sub.next.origin;
        p.access = ACCESS_DIR;
    }

    /* Dyadic formulas
       ───────────────

       If both operands of the formula have the same origin, then that is the
       origin of the value yielded by the formula.  Otherwise a fresh origin is
       created with class NIL.  The value yielded by the formula always have direct
       access.  */

    /** sprops_for_formula */
    private static void spropsForFormula(A68 a68, Node p) {
        if (p.sub.is(MONADIC_FORMULA) && p.sub.next == null) {
            p.origin = p.sub.origin;
        } else {
            Node arg1 = p.sub;
            Node arg2 = p.sub.next.next;

            if (originEqualP(a68, arg1.origin, arg2.origin)) {
                p.origin = arg1.origin;
            } else {
                p.origin = makeOrigin(a68, p);
            }
        }
        p.access = ACCESS_DIR;
    }

    /* Identifiers
       ───────────

       Both the access and origin of the value yielded by an applied identifier is
       obtained from the symtab.  If the applied identifier appears before its
       declaration, the symtab will not have the properties installed; in that
       case, an origin is allocated in the symtab.  XXX we need to install a
       pointer to the access in the symtab. */

    /** sprops_for_applied_identifier */
    private static void spropsForAppliedIdentifier(A68 a68, Node p) {
        if (p.tag.origin == null) {
            p.tag.origin = makeOrigin(a68, p);
        }
        p.origin = p.tag.origin;
        p.access = p.tag.access;
    }

    /* Assertions
       ──────────

       Assertions introduce a fresh origin of class NIL.  The access is also
       NIL.  */

    /** sprops_for_assertion */
    private static void spropsForAssertion(A68 a68, Node p) {
        p.origin = makeOrigin(a68, p);
        p.access = ACCESS_NIL;
    }

    /**
     * sprops_for_decl: fill in the static properties for the symtab entry for
     * the identifier declared in P, which is a parse node for a declaration.
     * This function shall handle all node types for which a68_is_declaration
     * returns `true'.
     */
    private static void spropsForDecl(A68 a68, Node p) {
        /* Declarations do not yield values themselves, but we set the origin and
           access of their defining identifiers in their symtab entries.  */

        p.origin = null;
        p.access = NO_ACCESS;

        /* Get the defining identifier.  */
        Node definingIdentifier = null;

        switch (p.attribute) {
        case IDENTITY_DECLARATION:
            if (p.sub.is(IDENTITY_DECLARATION)) {
                definingIdentifier = p.sub.next.next;
            } else if (p.sub.is(PUBLIC_SYMBOL)) {
                definingIdentifier = p.sub.next.next;
            } else if (p.sub.is(DECLARER)) {
                definingIdentifier = p.sub.next;
            } else {
                throw Parser.gccUnreachable();
            }
            break;
        case PROCEDURE_DECLARATION:
            if (p.sub.is(PROCEDURE_DECLARATION)) {
                definingIdentifier = p.sub.next.next;
            } else if (p.sub.is(PUBLIC_SYMBOL)) {
                definingIdentifier = p.sub.next.next;
            } else if (p.sub.is(PROC_SYMBOL)) {
                definingIdentifier = p.sub.next;
            } else {
                throw Parser.gccUnreachable();
            }
            break;
        case BRIEF_OPERATOR_DECLARATION:
            if (p.sub.is(BRIEF_OPERATOR_DECLARATION)) {
                definingIdentifier = p.sub.next.next;
            } else if (p.sub.is(PUBLIC_SYMBOL)) {
                definingIdentifier = p.sub.next.next;
            } else {
                definingIdentifier = p.sub.next;
            }
            break;
        case OPERATOR_DECLARATION:
            if (p.sub.is(OPERATOR_DECLARATION)) {
                definingIdentifier = p.sub.next.next;
            } else if (p.sub.is(PUBLIC_SYMBOL)) {
                definingIdentifier = p.sub.next.next;
            } else {
                definingIdentifier = p.sub.next;
            }
            break;
        case VARIABLE_DECLARATION:
            if (p.sub.is(VARIABLE_DECLARATION)) {
                definingIdentifier = p.sub.next.next;
            } else {
                Node q = p.sub;

                if (q.is(PUBLIC_SYMBOL)) {
                    q = q.next;
                }

                if (q.is(QUALIFIER)) {
                    definingIdentifier = q.next.next;
                } else if (q.is(DECLARER)) {
                    definingIdentifier = q.next;
                } else {
                    throw Parser.gccUnreachable();
                }
            }
            break;
        case PROCEDURE_VARIABLE_DECLARATION:
            if (p.sub.is(PROCEDURE_VARIABLE_DECLARATION)) {
                definingIdentifier = p.sub.next.next;
            } else {
                Node q = p.sub;

                if (q.is(PUBLIC_SYMBOL)) {
                    q = q.next;
                }

                if (q.is(PROC_SYMBOL)) {
                    definingIdentifier = q.next;
                } else if (q.is(QUALIFIER)) {
                    definingIdentifier = q.next.next;
                } else {
                    throw Parser.gccUnreachable();
                }
            }
            break;
        default:
            break;
        }

        /* Set the static attributes in the symtab for the defining identifier.  */
        if (definingIdentifier != null) {
            switch (p.attribute) {
            case IDENTITY_DECLARATION:
            case PROCEDURE_DECLARATION: {
                Tag tax = definingIdentifier.tag;
                if (tax.origin == null) {
                    tax.origin = makeOrigin(a68, p);
                }
                tax.origin.kindo = KINDO_IDE;
                tax.origin.bno = definingIdentifier.symbolTable.level;
                tax.access = ACCESS_DIR;
                break;
            }
            case VARIABLE_DECLARATION:
            case PROCEDURE_VARIABLE_DECLARATION: {
                Tag tax = definingIdentifier.tag;
                if (tax.origin == null) {
                    tax.origin = makeOrigin(a68, p);
                }
                tax.origin.kindo = KINDO_VAR;
                tax.origin.bno = definingIdentifier.symbolTable.level;
                tax.access = ACCESS_VAR;
                break;
            }
            case MODE_DECLARATION:
            case PRIORITY_DECLARATION:
            case BRIEF_OPERATOR_DECLARATION:
            case OPERATOR_DECLARATION:
                /* These declarations do not ascribe values to identifiers so there
                   is nothing to do.  */
                break;
            default:
                throw Parser.gccUnreachable();
            }
        }
    }

    /**
     * sprops_for_unit: fill in the static properties for P, which is a parse
     * node for a construct yielding some value. This function shall handle all
     * node types for which a68_yields_value returns `true'.
     */
    private static void spropsForUnit(A68 a68, Node p) {
        switch (p.attribute) {
        case UNIT:
        case PRIMARY:
        case SECONDARY:
        case TERTIARY:
            p.origin = p.sub.origin;
            // Sic: ACCESS (p) is not copied from SUB (p); it keeps the value it
            // had (ACCESS_DIR from a68_new_node).
            break;
        case FORMAL_HOLE:
        case JUMP:
        case SKIP:
        case NIHIL:
        case EMPTY_SYMBOL:
        case ROUTINE_TEXT:
            p.origin = makeOrigin(a68, p);
            p.access = ACCESS_DIR;
            break;
        case DENOTATION:
            spropsForDenotation(a68, p);
            break;
        case IDENTIFIER:
            spropsForAppliedIdentifier(a68, p);
            break;
        case MONADIC_FORMULA:
            spropsForMonadicFormula(a68, p);
            break;
        case FORMULA:
            spropsForFormula(a68, p);
            break;
        case GENERATOR:
            spropsForGenerator(a68, p);
            break;
        case SELECTION:
            spropsForSelection(a68, p);
            break;
        case DEREFERENCING:
            spropsForDereferencing(a68, p);
            break;
        case DEPROCEDURING:
            spropsForDeproceduring(a68, p);
            break;
        case PROCEDURING:
            spropsForProceduring(a68, p);
            break;
        case SLICE:
            spropsForSlice(a68, p);
            break;
        case WIDENING:
            spropsForWidening(a68, p);
            break;
        case UNITING:
            spropsForUniting(a68, p);
            break;
        case ROWING:
            spropsForRowing(a68, p);
            break;
        case VOIDING:
            spropsForVoiding(a68, p);
            break;
        case ASSIGNATION:
            spropsForAssignation(a68, p);
            break;
        case IDENTITY_RELATION:
            spropsForIdentityRelation(a68, p);
            break;
        case ACCESS_CLAUSE:
            spropsForAccessClause(a68, p);
            break;
        case ENQUIRY_CLAUSE:
            spropsForEnquiryClause(a68, p);
            break;
        case LOOP_CLAUSE:
            spropsForLoopClause(a68, p);
            break;
        case ENCLOSED_CLAUSE:
            spropsForEnclosedClause(a68, p);
            break;
        case SERIAL_CLAUSE:
            spropsForSerialClause(a68, p);
            break;
        case CLOSED_CLAUSE:
            spropsForClosedClause(a68, p);
            break;
        case PARALLEL_CLAUSE:
            spropsForParallelClause(a68, p);
            break;
        case CONDITIONAL_CLAUSE:
            spropsForConditionalClause(a68, p);
            break;
        case CONFORMITY_CLAUSE:
            spropsForConformityClause(a68, p);
            break;
        case CASE_CLAUSE:
            spropsForCaseClause(a68, p);
            break;
        case COLLATERAL_CLAUSE:
            spropsForCollateralClause(a68, p);
            break;
        case CALL:
            spropsForCall(a68, p);
            break;
        case AND_FUNCTION:
        case OR_FUNCTION:
            spropsForLogicalFunction(a68, p);
            break;
        case ASSERTION:
            spropsForAssertion(a68, p);
            break;
        case CAST:
            spropsForCast(a68, p);
            break;
        default:
            throw Parser.gccUnreachable();
        }

        /* Sanity check.  */
        Parser.gccAssert(p.access != NO_ACCESS);
        Parser.gccAssert(p.origin != null);
    }

    /** a68_sprops: entry point for the sprops parser pass. */
    public static void sprops(A68 a68, Node p) {
        for (; p != null; p = p.next) {
            sprops(a68, p.sub);

            if (Parser.yieldsValue(a68, p)) {
                spropsForUnit(a68, p);
            } else if (Parser.isDeclaration(a68, p)) {
                spropsForDecl(a68, p);
            } else {
                p.origin = null;
                p.access = NO_ACCESS;
            }
        }
    }
}
