// Java port of ga68's a68-exports.cc.
// Original: Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emit the module interfaces of a prelude packet.
 *
 * <p>Instead of assembler directives for the {@code .a68_exports} section,
 * the export data is written to a byte buffer with the same layout (little
 * endian, 8-byte pointers as on x86_64). The buffer is what ga68 would put in
 * the section; a68j writes it to a {@code .m68} file, which
 * {@link Imports} reads ("a file containing nothing but Algol 68 export
 * data").
 */
public final class Exports {
    private Exports() {}

    /** The size of the target's pointer type. */
    private static final int PTR_SIZE = 8;

    /**
     * Create a new module interface, initially with no modes and no
     * extracts. MODULE_NAME is the name of the module as it is accessed at the
     * source level, which corresponds to a bold word.
     */
    public static Moif moifNew(A68 a68, String moduleName) {
        Moif moif = new Moif();
        moif.version = Imports.GA68_EXPORTS_VERSION;
        moif.name = moduleName;
        moif.prelude = null;
        moif.postlude = null;
        moif.next = null;
        return moif;
    }

    /** EXTRACT_T equality as used by vec::contains (pointer equality in C). */
    private static boolean contains(List<Extract> l, Extract e) {
        // Sic: in C the extract is freshly allocated, so contains never finds it.
        return false;
    }

    /** Add a new mode to a module interface. */
    private static void addMoidToMoif(A68 a68, Moif moif, Moid m) {
        if (!containsMoid(moif.modes, m)) {
            moif.modes.add(m);
        }
    }

    private static boolean containsMoid(List<Moid> l, Moid m) {
        for (Moid x : l) {
            if (x == m) {
                return true;
            }
        }
        return false;
    }

    /**
     * The symbol of the declaration of TAG (C: DECL_NAME (TAX_TREE_DECL
     * (tag))): the declarations in a public range are mangled with the module
     * name, operators numbered.
     */
    private static String declSymbol(A68 a68, Tag tag, String moduleName) {
        Node identifier = tag.node;
        boolean op = identifier.attribute == Attribute.DEFINING_OPERATOR
                || identifier.attribute == Attribute.OPERATOR;
        // a68_lower_operator and lower_operator_declaration: operators are
        // mangled as indicants.
        if (op) {
            return Low.getMangledIndicant(a68, identifier.symbol(), moduleName, false /* internal */, op);
        }
        return Low.getMangledIdentifier(a68, identifier.symbol(), moduleName, false /* internal */, op);
    }

    /** Add a new identifier extract to a module interface. */
    public static void addIdentifierToMoif(A68 a68, Moif moif, Tag tag, String tagSymbol) {
        Extract e = new Extract();

        e.kind = Imports.GA68_EXTRACT_IDEN;
        e.symbol = tagSymbol;
        e.mode = tag.type;
        e.priority = 0;
        e.variable = tag.variable;
        e.inProc = tag.inProc || tag.nestProc;

        if (!contains(moif.identifiers, e)) {
            addMoidToMoif(a68, moif, tag.type);
            moif.identifiers.add(e);
        }
    }

    /** Add a new mode indicant extract to a module interface. */
    private static void addIndicantToMoif(A68 a68, Moif moif, Tag tag) {
        Extract e = new Extract();
        // Mode tags are not associated with declarations, so we have to do the
        // mangling here.
        String tagSymbol = Low.getMangledIndicant(a68, tag.node.symbol(), moif.name, false, false);

        e.kind = Imports.GA68_EXTRACT_MODE;
        e.symbol = tagSymbol;
        e.mode = tag.type;
        e.priority = 0;
        e.variable = false;
        e.inProc = false;

        if (!contains(moif.indicants, e)) {
            addMoidToMoif(a68, moif, tag.type);
            moif.indicants.add(e);
        }
    }

    /** Add a new module extract to a module interface. */
    private static void addModuleToMoif(A68 a68, Moif moif, Tag tag) {
        Extract e = new Extract();
        String tagSymbol = tag.node.symbol();

        e.kind = Imports.GA68_EXTRACT_MODU;
        e.symbol = tagSymbol;
        e.mode = null;
        e.priority = 0;
        e.variable = false;
        e.inProc = false;

        if (!contains(moif.modules, e)) {
            moif.modules.add(e);
        }
    }

    /** Add a new priority extract to a module interface. */
    private static void addPrioToMoif(A68 a68, Moif moif, Tag tag) {
        Extract e = new Extract();
        // Priority tags are not associated with declarations, so we have to do
        // the mangling here.
        String tagSymbol = Low.getMangledIndicant(a68, tag.node.symbol(), moif.name, false, false);

        e.kind = Imports.GA68_EXTRACT_PRIO;
        e.symbol = tagSymbol;
        e.mode = null;
        e.priority = tag.priority;
        e.variable = false;
        e.inProc = false;

        if (!contains(moif.prios, e)) {
            moif.prios.add(e);
        }
    }

    /** Add a new operator extract to a module interface. */
    private static void addOperatorToMoif(A68 a68, Moif moif, Tag tag, String tagSymbol) {
        Extract e = new Extract();

        e.kind = Imports.GA68_EXTRACT_OPER;
        e.symbol = tagSymbol;
        e.mode = tag.type;
        e.priority = 0;
        e.variable = tag.variable;
        // There are no operatorvariable-declarations
        Parser.gccAssert(!e.variable);
        e.inProc = tag.inProc;

        if (!contains(moif.operators, e)) {
            addMoidToMoif(a68, moif, tag.type);
            moif.operators.add(e);
        }
    }

    // ------------------------------------------------------------------
    // The "assembler": a byte buffer with labels and deltas.
    // ------------------------------------------------------------------

    /** The export section being written (C: asm_out_file in the exports section). */
    private static final class Asm {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        /** ASM_LABEL (m): offset of the emitted mode, -1 while it is being emitted. */
        final Map<Moid, Integer> modeLabel = new IdentityHashMap<>();
        /** Deltas to modes whose label is not bound yet: {position, mode}. */
        final List<Object[]> fixups = new ArrayList<>();
        int moduleLabel;

        int pos() {
            return out.size();
        }

        /** dw2_asm_output_data (SIZE, VALUE): little endian. */
        void data(int size, long value) {
            for (int i = 0; i < size; i++) {
                out.write((int) (value >>> (8 * i)) & 0xff);
            }
        }

        /** dw2_asm_output_delta (PTR_SIZE, ASM_LABEL (M), module_label). */
        void modeDelta(Moid m) {
            Integer l = modeLabel.get(m);
            if (l == null || l < 0) {
                fixups.add(new Object[] {pos(), m});
                data(PTR_SIZE, 0);
            } else {
                data(PTR_SIZE, l - moduleLabel);
            }
        }

        byte[] finish() {
            byte[] b = out.toByteArray();
            for (Object[] f : fixups) {
                int at = (Integer) f[0];
                long v = modeLabel.get((Moid) f[1]) - moduleLabel;
                for (int i = 0; i < PTR_SIZE; i++) {
                    b[at + i] = (byte) (v >>> (8 * i));
                }
            }
            return b;
        }
    }

    /** Output a sized string. */
    private static void asmOutputString(Asm asm, String s) {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        asm.data(2, b.length + 1);
        asm.out.write(b, 0, b.length);
        asm.out.write(0);
    }

    private static boolean isIntegral(Modes md, Moid m) {
        return m == md.INT || m == md.LONG_INT || m == md.LONG_LONG_INT || m == md.SHORT_INT
                || m == md.SHORT_SHORT_INT || m == md.WORD_INT;
    }

    private static boolean isBits(Modes md, Moid m) {
        return m == md.BITS || m == md.LONG_BITS || m == md.LONG_LONG_BITS || m == md.SHORT_BITS
                || m == md.SHORT_SHORT_BITS || m == md.WORD_BITS;
    }

    private static boolean isBytes(Modes md, Moid m) {
        return m == md.BYTES || m == md.LONG_BYTES || m == md.WORD_BYTES;
    }

    private static boolean isComplex(Modes md, Moid m) {
        return m == md.COMPLEX || m == md.LONG_COMPLEX || m == md.LONG_LONG_COMPLEX || m == md.WORD_COMPLEX;
    }

    private static boolean isReal(Modes md, Moid m) {
        return m == md.REAL || m == md.LONG_REAL || m == md.LONG_LONG_REAL || m == md.WORD_REAL;
    }

    private static boolean is(Moid m, Attribute a) {
        return m.attribute == a;
    }

    /** Output a mode to the exports section if it hasn't been emitted already. */
    private static void asmOutputMode(A68 a68, Asm asm, Moid m) {
        Modes md = a68.modes;
        // Do nothing if the mode has been already emitted and therefore there
        // is already a label to access it.
        if (asm.modeLabel.containsKey(m)) {
            return;
        }

        // Mode indicants are not emitted in the mode table, but as mode
        // extracts in the extracts table. Still we have to emit the named mode.
        if (is(m, Attribute.INDICANT)) {
            m = m.node.type;
        }

        // Collection of modes.
        if (is(m, Attribute.SERIES_MODE) || is(m, Attribute.STOWED_MODE)) {
            for (Pack p = m.pack; p != null; p = p.next) {
                asmOutputMode(a68, asm, p.type);
            }
            return;
        }

        // Ok we got a mode to output.

        // First emit referred modes and sub-modes. Note how we have to create a
        // label for the mode and install it in order to avoid infinite
        // recursion in case of ref-induced recursive mode definitions.
        // Sic: for an indicant the label is installed on the named mode only.
        asm.modeLabel.put(m, -1);

        boolean isFlexetyRow = is(m, Attribute.FLEX_SYMBOL) || is(m, Attribute.ROW_SYMBOL) || m == md.STRING;
        if (is(m, Attribute.REF_SYMBOL) || is(m, Attribute.FLEX_SYMBOL)) {
            asmOutputMode(a68, asm, m.sub);
        } else if (m != md.STRING && isFlexetyRow) {
            asmOutputMode(a68, asm, m.sub);
        } else if (!isComplex(md, m) && (is(m, Attribute.STRUCT_SYMBOL) || is(m, Attribute.UNION_SYMBOL))) {
            for (Pack p = m.pack; p != null; p = p.next) {
                asmOutputMode(a68, asm, p.type);
            }
        } else if (is(m, Attribute.PROC_SYMBOL)) {
            asmOutputMode(a68, asm, m.sub);
            for (Pack p = m.pack; p != null; p = p.next) {
                asmOutputMode(a68, asm, p.type);
            }
        }

        // No recursion below this point pls.

        // Emit a label for this mode.
        asm.modeLabel.put(m, asm.pos());

        // Now emit assembly for the mode entry.
        if (m == md.VOID) {
            asm.data(1, Imports.GA68_MODE_VOID);
        } else if (m == md.CHAR) {
            asm.data(1, Imports.GA68_MODE_CHAR);
        } else if (m == md.BOOL) {
            asm.data(1, Imports.GA68_MODE_BOOL);
        } else if (m == md.STRING) {
            asm.data(1, Imports.GA68_MODE_STRING);
        } else if (isIntegral(md, m)) {
            asm.data(1, Imports.GA68_MODE_INT);
            asm.data(1, m.dim);
        } else if (isReal(md, m)) {
            asm.data(1, Imports.GA68_MODE_REAL);
            asm.data(1, m.dim);
        } else if (isBits(md, m)) {
            asm.data(1, Imports.GA68_MODE_BITS);
            asm.data(1, m.dim);
        } else if (isBytes(md, m)) {
            asm.data(1, Imports.GA68_MODE_BYTES);
            asm.data(1, m.dim);
        } else if (isComplex(md, m)) {
            // Complex is a struct of two reals of the right sizety.
            int dim = m.pack.type.dim;
            asm.data(1, Imports.GA68_MODE_CMPL);
            asm.data(1, dim);
        } else if (is(m, Attribute.REF_SYMBOL)) {
            asm.data(1, Imports.GA68_MODE_NAME);
            asm.modeDelta(m.sub);
        } else if (is(m, Attribute.FLEX_SYMBOL)) {
            asm.data(1, Imports.GA68_MODE_FLEX);
            asm.modeDelta(m.sub);
        } else if (is(m, Attribute.ROW_SYMBOL)) {
            asm.data(1, Imports.GA68_MODE_ROW);
            asm.data(1, m.dim);
            // XXX for now emit zeroes as triplets.
            for (int i = 0; i < m.dim; ++i) {
                asm.data(PTR_SIZE, 0);
                asm.data(PTR_SIZE, 0);
            }
            asm.modeDelta(m.sub);
        } else if (is(m, Attribute.STRUCT_SYMBOL)) {
            asm.data(1, Imports.GA68_MODE_STRUCT);
            asm.data(2, m.dim);
            for (Pack p = m.pack; p != null; p = p.next) {
                asm.modeDelta(p.type);
                asmOutputString(asm, p.text != null ? p.text : "");
            }
        } else if (is(m, Attribute.UNION_SYMBOL)) {
            asm.data(1, Imports.GA68_MODE_UNION);
            asm.data(2, ModeBuilder.countPackMembers(a68, m.pack));
            for (Pack p = m.pack; p != null; p = p.next) {
                asm.modeDelta(p.type);
            }
        } else if (is(m, Attribute.PROC_SYMBOL)) {
            asm.data(1, Imports.GA68_MODE_PROC);
            asm.modeDelta(m.sub);
            asm.data(1, m.dim);
            for (Pack p = m.pack; p != null; p = p.next) {
                asm.modeDelta(p.type);
                asmOutputString(asm, p.text != null ? p.text : "");
            }
        } else {
            asm.data(1, Imports.GA68_MODE_UNKNOWN);
        }
    }

    /** Output an extract for a given tag to the extracts section. */
    private static void asmOutputExtract(A68 a68, Asm asm, int kind, String symbol, Moid mode,
            int prio, boolean variable, boolean inProc) {
        int sizePos = asm.pos();
        asm.data(PTR_SIZE, 0); // extract size, patched below
        int begin = asm.pos();

        boolean encodeMdextra = false;
        switch (kind) {
            case Imports.GA68_EXTRACT_MODU:
                asm.data(1, Imports.GA68_EXTRACT_MODU);
                asmOutputString(asm, symbol);
                break;
            case Imports.GA68_EXTRACT_MODE:
                asm.data(1, Imports.GA68_EXTRACT_MODE);
                asmOutputString(asm, symbol);
                asm.modeDelta(mode);
                break;
            case Imports.GA68_EXTRACT_IDEN:
                asm.data(1, Imports.GA68_EXTRACT_IDEN);
                asmOutputString(asm, symbol);
                asm.modeDelta(mode);
                encodeMdextra = true;
                break;
            case Imports.GA68_EXTRACT_PRIO:
                asm.data(1, Imports.GA68_EXTRACT_PRIO);
                asmOutputString(asm, symbol);
                asm.data(1, prio);
                break;
            case Imports.GA68_EXTRACT_OPER:
                asm.data(1, Imports.GA68_EXTRACT_OPER);
                asmOutputString(asm, symbol);
                asm.modeDelta(mode);
                encodeMdextra = true;
                break;
            default:
                throw Parser.gccUnreachable();
        }

        if (encodeMdextra) {
            asm.data(PTR_SIZE, 2);
            asm.data(1, variable ? 1 : 0);
            asm.data(1, inProc ? 1 : 0);
        } else {
            asm.data(PTR_SIZE, 0);
        }

        patch(asm, sizePos, asm.pos() - begin);
    }

    /** Patch the 8-byte delta at AT, which precedes data written already. */
    private static void patch(Asm asm, int at, long v) {
        Asm tmp = new Asm();
        byte[] b = asm.out.toByteArray();
        for (int i = 0; i < PTR_SIZE; i++) {
            b[at + i] = (byte) (v >>> (8 * i));
        }
        asm.out.reset();
        asm.out.write(b, 0, b.length);
    }

    /** Output a module interface. */
    private static void asmOutputMoif(A68 a68, Asm asm, Moif moif) {
        asm.moduleLabel = asm.pos();

        asm.data(1, Imports.A68_EXPORT_MAGIC1);
        asm.data(1, Imports.A68_EXPORT_MAGIC2);
        asm.data(2, moif.version);
        asmOutputString(asm, moif.name);
        asmOutputString(asm, moif.prelude != null ? moif.prelude : "");
        asmOutputString(asm, moif.postlude != null ? moif.postlude : "");

        // Modes table.
        int modesSize = asm.pos();
        asm.data(PTR_SIZE, 0);
        int modesBegin = asm.pos();
        for (Moid m : moif.modes) {
            asmOutputMode(a68, asm, m);
        }
        patch(asm, modesSize, asm.pos() - modesBegin);

        // Extracts table.
        int extractsSize = asm.pos();
        asm.data(PTR_SIZE, 0);
        int extractsBegin = asm.pos();
        for (Extract e : moif.modules) {
            asmOutputExtract(a68, asm, Imports.GA68_EXTRACT_MODU, e.symbol, e.mode, e.priority, e.variable, e.inProc);
        }
        for (Extract e : moif.indicants) {
            asmOutputExtract(a68, asm, Imports.GA68_EXTRACT_MODE, e.symbol, e.mode, e.priority, e.variable, e.inProc);
        }
        for (Extract e : moif.identifiers) {
            asmOutputExtract(a68, asm, Imports.GA68_EXTRACT_IDEN, e.symbol, e.mode, e.priority, e.variable, e.inProc);
        }
        for (Extract e : moif.prios) {
            asmOutputExtract(a68, asm, Imports.GA68_EXTRACT_PRIO, e.symbol, e.mode, e.priority, e.variable, e.inProc);
        }
        for (Extract e : moif.operators) {
            asmOutputExtract(a68, asm, Imports.GA68_EXTRACT_OPER, e.symbol, e.mode, e.priority, e.variable, e.inProc);
        }
        patch(asm, extractsSize, asm.pos() - extractsBegin);
    }

    /** Add module exports for publicized module revelations. */
    private static void addPubRevelationsToMoif(A68 a68, Moif moif, Node p) {
        for (; p != null; p = p.next) {
            if (p.attribute == Attribute.PUBLIC_SYMBOL) {
                Parser.gccAssert(p.next.attribute == Attribute.MODULE_INDICANT);
                Tag tag = new Tag();
                tag.node = p.next;
                addModuleToMoif(a68, moif, tag);
                p = p.next;
            } else {
                addPubRevelationsToMoif(a68, moif, p.sub);
            }
        }
    }

    /**
     * The order in which ga68 lowers the declarations of the public range
     * TABLE, which determines the numbers of the operator symbols: the
     * defining and applied operators of the DEF part in textual order (the
     * declaration is made at the first of them).
     */
    private static void collectDefiningOperators(Node p, List<Node> out) {
        for (; p != null; p = p.next) {
            if (p.attribute == Attribute.DEFINING_OPERATOR || p.attribute == Attribute.OPERATOR) {
                out.add(p);
            }
            collectDefiningOperators(p.sub, out);
        }
    }

    /**
     * a68_do_exports: the export data for the module definitions in the parse
     * tree P (empty if there are none). Lowering mangles the symbols of the
     * public declarations; that is done here in the same order.
     */
    public static byte[] doExports(A68 a68, Node p) {
        Asm asm = new Asm();
        doExports(a68, p, asm);
        return asm.finish();
    }

    private static void doExports(A68 a68, Node p, Asm asm) {
        for (; p != null; p = p.next) {
            if (p.attribute == Attribute.DEFINING_MODULE_INDICANT) {
                String moduleId = Low.getMangledIndicant(a68, p.symbol(), null, false, false);
                Moif moif = moifNew(a68, moduleId);
                moif.prelude = moduleId + "__prelude";
                moif.postlude = moduleId + "__postlude";

                Node moduleText = p.next.next;
                Parser.gccAssert(moduleText.attribute == Attribute.MODULE_TEXT);

                // Get modules exports from the revelation part.
                if (moduleText.sub.attribute == Attribute.REVELATION_PART) {
                    Node revelationPart = moduleText.sub;
                    addPubRevelationsToMoif(a68, moif, revelationPart.sub);
                }

                Node defPart = moduleText.sub.attribute == Attribute.REVELATION_PART
                        ? moduleText.sub.next
                        : moduleText.sub;
                Parser.gccAssert(defPart.attribute == Attribute.DEF_PART);
                Table table = defPart.sub.symbolTable;
                Parser.gccAssert(table.publicRange);

                // The symbols of the declarations (mangled while lowering in C).
                Map<Tag, String> symbols = new IdentityHashMap<>();
                List<Node> ops = new ArrayList<>();
                collectDefiningOperators(defPart, ops);
                java.util.Set<Tag> declared = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
                for (Tag t = table.operators; t != null; t = t.next) {
                    declared.add(t);
                }
                for (Node op : ops) {
                    if (op.tag != null && declared.contains(op.tag) && !symbols.containsKey(op.tag)) {
                        symbols.put(op.tag, declSymbol(a68, op.tag, p.symbol()));
                    }
                }

                for (Tag t = table.indicants; t != null; t = t.next) {
                    if (t.publicized) {
                        addIndicantToMoif(a68, moif, t);
                    }
                }

                for (Tag t = table.identifiers; t != null; t = t.next) {
                    if (t.publicized) {
                        addIdentifierToMoif(a68, moif, t, declSymbol(a68, t, p.symbol()));
                    }
                }

                for (Tag t = table.priority; t != null; t = t.next) {
                    if (t.publicized) {
                        addPrioToMoif(a68, moif, t);
                    }
                }

                for (Tag t = table.operators; t != null; t = t.next) {
                    if (t.publicized) {
                        String s = symbols.get(t);
                        addOperatorToMoif(a68, moif, t, s != null ? s : declSymbol(a68, t, p.symbol()));
                    }
                }

                asmOutputMoif(a68, asm, moif);
            } else {
                doExports(a68, p.sub, asm);
            }
        }
    }
}
