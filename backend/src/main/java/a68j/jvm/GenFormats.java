// a68j JVM backend: format texts (-fformats).
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import static java.lang.constant.ConstantDescs.*;

import a68j.front.Attribute;
import a68j.front.MoidsCheck;
import a68j.front.Node;
import a68j.front.Tag;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

/**
 * Format texts, with -fformats (an a68j extension; ga68 has no format texts).
 * The mode FORMAT is the mode {@code Gformat} of the bundled module
 * Genie_Formats, whose procedures parse a format text at run time and do the
 * formatted transput. A format text is lowered to a call of that module:
 *
 * <pre>gfmt (text)                       no dynamic parts
 * gfmtd (text, ints, formats)        else</pre>
 *
 * The text is made from the syntax tree, in the syntax that
 * {@code gf_parse_list} of the module reads (backend/lib/genieformats.a68):
 * the frames, insertions and replicators of the format text without
 * blanks (the module expects an insertion to start where its replicator
 * does), with each dynamic part replaced by {@code @i}, its index from 1. A dynamic part is
 * the unit of a replicator {@code n(...)} or a parameter of {@code g(...)},
 * which yield INT (the procedure {@code ints}), or the unit of
 * {@code f(...)}, which yields FORMAT (the procedure {@code formats}); the
 * two have indices of their own. A unit that is a constant
 * ({@link Tables#constant}) goes into the text instead, so the module's
 * cache of parsed texts serves {@code g(-6, 2)} as well.
 *
 * <p>Each dynamic part is a method of its own without parameters
 * ({@link Gen.Routine#valueUnit}, made by {@link Capture}), and
 * {@link a68j.rt.FormatParts} calls the one of index i. So a dynamic part is
 * elaborated each time the format uses it, in the environment of the format
 * text, as in Algol 68 Genie.
 */
final class GenFormats {
    private GenFormats() {}

    private static final ClassDesc CD_FORMAT_PARTS = ClassDesc.of("a68j.rt.FormatParts");

    /**
     * The text of a format text and its dynamic parts: the nodes whose
     * methods yield INT and FORMAT, by index - 1. Each node is the clause or
     * unit that the method elaborates.
     */
    record Parts(String text, List<Node> ints, List<Node> formats) {
        boolean dynamic() {
            return !ints.isEmpty() || !formats.isEmpty();
        }
    }

    /** The parts of the format text P; Capture and the code generator use the same. */
    static Parts parts(Gen g, Node p) {
        List<Node> ints = new ArrayList<>();
        List<Node> formats = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        text(g, p.sub, b, ints, formats);
        return new Parts(b.toString(), ints, formats);
    }

    private static void text(Gen g, Node p, StringBuilder b, List<Node> ints, List<Node> formats) {
        for (; p != null; p = p.next) {
            switch (p.attribute) {
                case FORMAT_DELIMITER_SYMBOL -> { }
                case LITERAL -> {
                    // Two literals in a row: a blank, else they would be one
                    // literal with a quote in it.
                    if (!b.isEmpty() && b.charAt(b.length() - 1) == '"') {
                        b.append(' ');
                    }
                    String s = GenUnits.processBreaks(p.symbol());
                    b.append('"').append(s.replace("\"", "\"\"")).append('"');
                }
                case STATIC_REPLICATOR -> b.append(p.symbol());
                case FORMAT_OPEN_SYMBOL -> b.append('(');
                case FORMAT_CLOSE_SYMBOL -> b.append(')');
                case COMMA_SYMBOL -> b.append(',');
                // '/' is l.
                case FORMAT_ITEM_L -> b.append('l');
                case DYNAMIC_REPLICATOR -> {
                    b.append("n(");
                    param(g, clause(p.sub.next), b, ints);
                    b.append(')');
                }
                case GENERAL_PATTERN -> {
                    b.append(p.sub.symbol());
                    if (p.sub.next != null) {
                        b.append('(');
                        List<Node> ps = generalParameters(p.sub.next);
                        for (int i = 0; i < ps.size(); i++) {
                            b.append(i == 0 ? "" : ",");
                            param(g, ps.get(i), b, ints);
                        }
                        b.append(')');
                    }
                }
                case FORMAT_PATTERN -> {
                    formats.add(clause(p.sub.next));
                    b.append("f(@").append(formats.size()).append(')');
                }
                default -> {
                    if (p.sub == null && p.attribute.name().startsWith("FORMAT_ITEM_")) {
                        b.append(p.symbol());
                    } else {
                        text(g, p.sub, b, ints, formats);
                    }
                }
            }
        }
    }

    /** An INT parameter or replicator U: its value if it is a constant, else @i. */
    private static void param(Gen g, Node u, StringBuilder b, List<Node> ints) {
        Object c = Tables.constant(g, u);
        if (c instanceof Integer i) {
            b.append(i);
        } else {
            ints.add(u);
            b.append('@').append(ints.size());
        }
    }

    /**
     * The clause of the ENCLOSED_CLAUSE E of n(...) or f(...); a closed
     * clause of one unit is that unit (so that a constant is found).
     */
    private static Node clause(Node e) {
        Node c = e.sub;
        Node u = single(c);
        return u != null ? u : c;
    }

    /** The unit of the closed clause C of one unit without declarations, or null. */
    private static Node single(Node c) {
        if (c.attribute != Attribute.CLOSED_CLAUSE) {
            return null;
        }
        Node serial = c.sub.next;
        if (serial == null || serial.attribute != Attribute.SERIAL_CLAUSE || serial.sub == null
                || serial.sub.next != null || serial.sub.attribute != Attribute.UNIT) {
            return null;
        }
        return serial.sub;
    }

    /**
     * The INT parameters of g(...), ENCLOSED_CLAUSE E of mode [] INT: the
     * units of a collateral clause, or the unit of a closed clause that is
     * rowed. The module needs their number, so another clause, such as
     * g(c | 6 | 7), is one parameter: its row must have one element
     * ({@link a68j.rt.FormatParts#single}).
     */
    private static List<Node> generalParameters(Node e) {
        List<Node> ps = new ArrayList<>();
        Node c = e.sub;
        if (c.attribute == Attribute.COLLATERAL_CLAUSE) {
            GenUnits.collectArguments(c.sub, ps);
            return ps;
        }
        Node u = single(c);
        Node s = u == null ? null : GenUnits.strip(u);
        ps.add(s != null && s.attribute == Attribute.ROWING ? s.sub : c);
        return ps;
    }

    /**
     * The method of the dynamic part R (Gen#emitRoutine): the value of its
     * unit as R.want, boxed. A parameter of g given as a row is its only
     * element.
     */
    static void valueUnit(Gen g, Gen.Routine r) {
        g.unit(r.node);
        a68j.front.Moid m = Gen.mode(r.node);
        if (r.want == g.a68.modes.INT && m.attribute == Attribute.ROW_SYMBOL) {
            g.pushWhere(r.node);
            g.cb.invokestatic(CD_FORMAT_PARTS, "single",
                    MethodTypeDesc.of(CD_int, Gen.CD_ROW, CD_String, CD_int));
        } else {
            g.adjust(m, r.want, r.node);
        }
        g.box(r.want);
    }

    /** FORMAT_TEXT P: the value of mode FORMAT. */
    static void formatText(Gen g, Node p) {
        Parts parts = parts(g, p);
        Tag gfmt = MoidsCheck.genieFormatsTag(p.symbolTable, parts.dynamic() ? "gfmtd" : "gfmt");
        if (gfmt == null) {
            throw new Gen.Unsupported(p, "format text without access to Genie_Formats");
        }
        var cb = g.cb;
        Gen.constant(cb, parts.text());
        cb.invokestatic(Gen.CD_ROW, "ofString", MethodTypeDesc.of(Gen.CD_ROW, CD_String));
        if (parts.dynamic()) {
            formatParts(g, parts.ints());
            formatParts(g, parts.formats());
        }
        Intrinsics.invokeModuleTag(g, gfmt);
    }

    /** new FormatParts (closures of the methods of NODES over the current frame). */
    private static void formatParts(Gen g, List<Node> nodes) {
        var cb = g.cb;
        cb.new_(CD_FORMAT_PARTS);
        cb.dup();
        Gen.constant(cb, nodes.size());
        cb.anewarray(Gen.CD_PROC);
        for (int i = 0; i < nodes.size(); i++) {
            Gen.Routine r = g.routineFor(nodes.get(i));
            cb.dup();
            Gen.constant(cb, i);
            cb.new_(Gen.CD_CLOSURE);
            cb.dup();
            Gen.constant(cb, g.handle(r));
            cb.aload(g.frameLocal);
            cb.invokespecial(Gen.CD_CLOSURE, "<init>",
                    MethodTypeDesc.of(CD_void, CD_MethodHandle, Gen.CD_FRAME));
            cb.aastore();
        }
        cb.invokespecial(CD_FORMAT_PARTS, "<init>", MethodTypeDesc.of(CD_void, Gen.CD_PROC.arrayType()));
    }
}
