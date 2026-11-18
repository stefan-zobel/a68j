// Java port of the driver function a68_parser in ga68's a68-parser.cc
// (the syntax-analysis part, stage 1 of the port).
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * The parser driver (a68_parser). Stage 1 of the port runs the phases up to
 * and including the victal checker, i.e. the end of syntax analysis.
 */
public final class ParserDriver {
    private ParserDriver() {}

    /**
     * Parse FILENAME up to the end of syntax analysis. Returns true if the
     * point after the victal checker was reached (where the C version with the
     * a68j patch dumps the syntax tree), false if an earlier phase reported
     * errors.
     */
    public static boolean parseSyntax(A68 a68, String filename) {
        Job job = a68.job;
        int[] renum = {0};

        // Initialisation.
        a68.topKeyword = null;
        a68.topToken = null;
        a68.parserState.errorTag = Parser.newTag(a68);
        job.topNode = null;
        job.topMoid = null;
        job.topMoif = null;
        job.topLine = null;
        job.standenvMoid = null;
        Keywords.setUpTables(a68);
        job.errorCount = job.warningCount = 0;

        // Tokeniser.
        if (job.errorCount == 0) {
            Scanner.Result r = new Scanner(a68).lexicalAnalyser(filename);
            if (r.emptyProgram()) {
                a68.diagnostics.error(null, "particular program or prelude packet not found in source file");
            }
            job.treeListingSafe = true;
            renum[0] = 0;
            Parser.renumberNodes(a68, job.topNode, renum);
        }

        // Final initialisations.
        if (job.errorCount == 0) {
            a68.standenv = null;
            Postulates.initPostulates(a68);
            a68.modeCount = 0;
            a68.modes.HIP = Parser.makeSpecialMode(a68, a68.modeCount++);
            a68.modes.UNDEFINED = Parser.makeSpecialMode(a68, a68.modeCount++);
            a68.modes.ERROR = Parser.makeSpecialMode(a68, a68.modeCount++);
            a68.modes.VACUUM = Parser.makeSpecialMode(a68, a68.modeCount++);
            a68.modes.C_STRING = Parser.makeSpecialMode(a68, a68.modeCount++);
            a68.modes.COLLITEM = Parser.makeSpecialMode(a68, a68.modeCount++);
        }

        // Handle pragmats.
        if (job.errorCount == 0) {
            Pragmat.handlePragmats(a68, job.topNode);
        }

        // Top-down parser.
        if (job.errorCount == 0) {
            Brackets.checkParenthesis(a68, job.topNode);
            if (job.errorCount == 0) {
                if (a68.options().brackets) {
                    TopDown.substituteBrackets(a68, job.topNode);
                }
                a68.symbolTableCount = 0;
                a68.standenv = Parser.newSymbolTable(a68, null);
                a68.standenv.level = 0;
                TopDown.topDownParser(a68, job.topNode);
            }
            renum[0] = 0;
            Parser.renumberNodes(a68, job.topNode, renum);
        }

        // Standard environment builder.
        if (job.errorCount == 0) {
            job.topNode.symbolTable = Parser.newSymbolTable(a68, a68.standenv);
            Prelude.makeStandardEnviron(a68);
            job.standenvMoid = job.topMoid;
        }

        // Bottom-up parser.
        if (job.errorCount == 0) {
            Taxes.preliminarySymbolTableSetup(a68, job.topNode);
            BottomUp.bottomUpParser(a68, job.topNode);
            renum[0] = 0;
            Parser.renumberNodes(a68, job.topNode, renum);
        }

        if (job.errorCount == 0) {
            BottomUp.bottomUpErrorCheck(a68, job.topNode);
            Victal.victalChecker(a68, job.topNode);
            // End of stage 1: here the patched ga68 dumps the tree (A68J_DUMP_SYNTAX).
            return true;
        }
        return false;
    }

    /** a68j: -fformats. The mode yielded by gfmt of the module interface M. */
    private static Moid formatMode(A68 a68, Moif m) {
        Moid gfmt = identifierMode(a68, m, "gfmt");
        return gfmt == null ? null : gfmt.sub;
    }

    /** a68j: the mode of the identifier NAME of the module interface M, or null. */
    private static Moid identifierMode(A68 a68, Moif m, String name) {
        for (Extract e : m.identifiers) {
            if (Low.demangleSymbol(a68, m.name, e.symbol).equals(name)) {
                return e.mode;
            }
        }
        return null;
    }

    /**
     * a68j: -fgenie, -fformats. Records the union U of the items of the
     * procedure PROC ([] U) VOID called NAME of the module interface M
     * (print, printf, read, readf), and yields it (null if there is none).
     */
    private static Moid itemUnion(A68 a68, Moif m, String name) {
        Moid p = m == null ? null : identifierMode(a68, m, name);
        if (p != null && p.attribute == Attribute.PROC_SYMBOL && p.pack != null && p.pack.next == null
                && p.pack.type.attribute == Attribute.ROW_SYMBOL && p.pack.type.sub.attribute == Attribute.UNION_SYMBOL) {
            a68.itemUnions.add(p.pack.type.sub);
            return p.pack.type.sub;
        }
        return null;
    }

    /**
     * The complete a68_parser: syntax analysis ({@link #parseSyntax}) followed
     * by the semantic passes (symbol tables, modes, mode checker, coercions,
     * application checker, scope checker, serial DSA, static properties).
     */
    public static void parse(A68 a68, String filename) {
        Job job = a68.job;
        int[] renum = {0};
        boolean afterVictal = parseSyntax(a68, filename);
        if (afterVictal) {
            if (job.errorCount == 0) {
                Taxes.finaliseSymbolTableSetup(a68, job.topNode, 1);
                job.topNode.symbolTable.nest = a68.symbolTableCount = 3;
                Taxes.resetSymbolTableNestCount(a68, job.topNode);
                Taxes.fillSymbolTableOuter(a68, job.topNode, job.topNode.symbolTable);
                Taxes.setNest(a68, job.topNode, null);
                Taxes.setProcLevel(a68, job.topNode, 1);
            }
            renum[0] = 0;
            Parser.renumberNodes(a68, job.topNode, renum);
        }

        // Mode table builder.
        if (job.errorCount == 0) {
            ModeBuilder.makeMoidList(a68, job);
            // a68j: -fformats. FORMAT is the mode Gformat of Genie_Formats,
            // the mode yielded by its procedure gfmt.
            if (a68.options().formats && a68.genieFormats != null) {
                a68.modes.FORMAT = formatMode(a68, a68.genieFormats);
            }
            // a68j: -fgenie, -fformats. The unions of the items of print and
            // printf, whose members a structure or a row of rows may be made
            // of (MoidsMisc#straightensTo).
            itemUnion(a68, a68.genieTransput, "print");
            itemUnion(a68, a68.genieFormats, "printf");
            itemUnion(a68, a68.genieTransput, "read");
            a68.readfUnion = itemUnion(a68, a68.genieFormats, "readf");
        }
        job.crossReferenceSafe = true;

        // Symbol table builder.
        if (job.errorCount == 0) {
            Taxes.collectTaxes(a68, job.topNode);
        }

        // Post parser.
        if (job.errorCount == 0) {
            BottomUp.rearrangeGotoLessJumps(a68, job.topNode);
        }

        // Mode checker.
        if (job.errorCount == 0) {
            MoidsCheck.modeChecker(a68, job.topNode);
        }

        // Coercion inserter.
        if (job.errorCount == 0) {
            MoidsCoerce.coercionInserter(a68, job.topNode);
            renum[0] = 0;
            Parser.renumberNodes(a68, job.topNode, renum);
        }

        // Application checker.
        if (job.errorCount == 0) {
            Taxes.markMoids(a68, job.topNode);
            Taxes.markAuxilliary(a68, job.topNode);
            Taxes.jumpsFromProcs(a68, job.topNode);
            Taxes.warnForUnusedTags(a68, job.topNode);
        }

        // Static scope checker.
        if (job.errorCount == 0) {
            Parser.tieLabelToSerial(a68, job.topNode);
            Parser.tieLabelToUnit(a68, job.topNode);
            Taxes.bindRoutineTagsToTree(a68, job.topNode);
            Scope.scopeChecker(a68, job.topNode);
        }

        // Serial dynamic stack allocation checker.
        if (job.errorCount == 0) {
            SerialDsa.serialDsa(a68, job.topNode);
        }

        // Static properties.
        if (job.errorCount == 0) {
            Sprops.sprops(a68, job.topNode);
        }

        // Finalise syntax tree.
        if (job.errorCount == 0) {
            int[] num = {0};
            Parser.renumberNodes(a68, job.topNode, num);
            job.topNode.symbolTable.nest = a68.symbolTableCount = 3;
            Taxes.resetSymbolTableNestCount(a68, job.topNode);
        }
    }
}
