// Command-line tool: parse a program and dump the syntax tree.
// Licensed under the GNU GPL, version 3 or later.

package a68j.tools;

import a68j.front.A68;
import a68j.front.Diagnostics;
import a68j.front.Node;
import a68j.front.ParserDriver;
import a68j.front.Stropping;

import java.io.BufferedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Parse an Algol 68 program up to the end of syntax analysis and print the
 * syntax tree, one node per line, indented by depth:
 *
 * <pre>ATTRIBUTE "symbol" file:line:column [prio=N] [annotation=ATTR]</pre>
 *
 * The format is identical to ga68 with tools/ga68-token-dump.patch applied and
 * A68J_DUMP_SYNTAX set. Diagnostics go to stderr in GCC's plain format.
 *
 * <p>Usage: {@code SyntaxDump [-fstropping=upper|supper] [-std=algol68|gnu68] [-fbrackets] [-Idir] [-Ldir] file.a68}
 */
public final class SyntaxDump {

    public static void main(String[] args) {
        A68 a68 = new A68();
        String file = null;
        java.util.List<String> libDirs = new java.util.ArrayList<>();
        boolean semantic = false;
        for (String arg : args) {
            if (arg.startsWith("-W") && warningOption(a68, arg)) {
                continue;
            }
            if (arg.equals("-semantic")) {
                semantic = true;
            } else if (arg.equals("-fstropping=upper")) {
                a68.options().stropping = Stropping.UPPER;
            } else if (arg.equals("-fstropping=supper")) {
                a68.options().stropping = Stropping.SUPPER;
            } else if (arg.equals("-std=algol68")) {
                a68.options().strict = true;
            } else if (arg.equals("-std=gnu68")) {
                a68.options().strict = false;
            } else if (arg.equals("-fbrackets")) {
                a68.options().brackets = true;
            } else if (arg.equals("-fbuilding-libga68")) {
                a68.options().buildingLibga68 = true;
            } else if (arg.startsWith("-I")) {
                a68.includePaths.add(arg.substring(2));
            } else if (arg.startsWith("-L")) {
                libDirs.add(arg.substring(2));
            } else {
                file = arg;
            }
        }
        // The gcc spec passes %{I*} %{L*} to a681: all -I directories, then all -L.
        a68.importPaths.addAll(a68.includePaths);
        a68.importPaths.addAll(libDirs);
        if (file == null) {
            System.err.println("usage: SyntaxDump [options] file.a68");
            System.exit(2);
        }
        System.setErr(new PrintStream(System.err, true, StandardCharsets.ISO_8859_1));
        if (!a68.options().strict && a68.options().brackets) {
            System.err.println("a68j: fatal error: '-fbrackets' can only be used with UPPER stroping");
            System.exit(1);
        }
        PrintStream out = new PrintStream(new BufferedOutputStream(System.out), false, StandardCharsets.ISO_8859_1);
        boolean reached;
        try {
            if (semantic) {
                ParserDriver.parse(a68, file);
                reached = a68.job.errorCount == 0;
            } else {
                reached = ParserDriver.parseSyntax(a68, file);
            }
        } catch (Diagnostics.ScanError e) {
            printDiagnostics(a68);
            System.exit(1);
            return;
        } catch (UnsupportedOperationException e) {
            printDiagnostics(a68);
            System.err.println("a68j: " + e.getMessage());
            System.exit(3);
            return;
        }
        printDiagnostics(a68);
        if (reached) {
            if (semantic) {
                dumpSemantic(a68, out);
            } else {
                dump(out, a68.job.topNode, 0);
            }
            out.flush();
        }
        System.exit(reached && a68.job.errorCount == 0 ? 0 : 1);
    }

    private static void printDiagnostics(A68 a68) {
        for (Diagnostics.Diagnostic d : a68.diagnostics.messages()) {
            System.err.println(d);
        }
    }

    /** Same format as a68j_dump_tree in the ga68 patch. */
    static void dump(PrintStream f, Node p, int depth) {
        for (; p != null; p = p.next) {
            f.print("  ".repeat(depth));
            f.print(p.attribute.name());
            if (p.info != null) {
                f.print(' ');
                f.print(p.info.symbol == null ? "(null)" : TokenDump.escape(p.info.symbol));
                if (p.info.line != null && p.info.charInLine >= 0) {
                    f.print(" " + p.info.line.filename + ":" + p.info.line.number + ":" + (p.info.charInLine + 1));
                }
                if (p.info.priority != 0) {
                    f.print(" prio=" + p.info.priority);
                }
            }
            if (p.annotation != a68j.front.Attribute.STOP) {
                f.print(" annotation=" + p.annotation.name());
            }
            f.print('\n');
            dump(f, p.sub, depth + 1);
        }
    }

    /** The -W options of ga68 that the front end knows; returns false if ARG is not one of them. */
    static boolean warningOption(A68 a68, String arg) {
        var w = a68.options().warnings;
        switch (arg) {
            case "-Wvoiding" -> w.add(a68j.front.Warn.VOIDING);
            case "-Wextensions" -> w.add(a68j.front.Warn.EXTENSIONS);
            case "-Wscope" -> w.add(a68j.front.Warn.SCOPE);
            case "-Wuninitialized" -> w.add(a68j.front.Warn.UNINITIALIZED);
            case "-Wunused" -> w.add(a68j.front.Warn.UNUSED);
            case "-Whidden-declarations", "-Whidden-declarations=prelude" -> {
                w.add(a68j.front.Warn.HIDDEN_DECLARATIONS);
                a68.options().hiddenDeclarations = 1;
            }
            case "-Whidden-declarations=all" -> {
                w.add(a68j.front.Warn.HIDDEN_DECLARATIONS);
                a68.options().hiddenDeclarations = 2;
            }
            case "-Whidden-declarations=none" -> {
                w.remove(a68j.front.Warn.HIDDEN_DECLARATIONS);
                a68.options().hiddenDeclarations = 0;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void moidRef(StringBuilder f, String what, a68j.front.Moid m) {
        if (m != null) {
            f.append(' ').append(what).append("=#").append(m.number);
        }
    }

    private static String b(boolean x) {
        return x ? "1" : "0";
    }

    /** Same format as a68j_dump_semantic_tree in the ga68 patch. */
    static void dumpSemanticTree(StringBuilder f, Node p, int depth, java.util.List<a68j.front.Tag> tags) {
        for (; p != null; p = p.next) {
            f.append("  ".repeat(depth));
            f.append(p.attribute.name());
            if (p.info != null) {
                f.append(' ');
                f.append(p.info.symbol == null ? "(null)" : TokenDump.escape(p.info.symbol));
                if (p.info.line != null && p.info.charInLine >= 0) {
                    f.append(' ').append(p.info.line.filename).append(':').append(p.info.line.number)
                            .append(':').append(p.info.charInLine + 1);
                }
                if (p.info.priority != 0) {
                    f.append(" prio=").append(p.info.priority);
                }
                f.append(" plev=").append(p.info.procedureLevel);
            }
            if (p.annotation != a68j.front.Attribute.STOP) {
                f.append(" annotation=").append(p.annotation.name());
            }
            moidRef(f, "moid", p.type);
            if (p.tag != null) {
                f.append(" tax=#").append(p.tag.number);
                if (!tags.contains(p.tag)) {
                    tags.add(p.tag);
                }
            }
            if (p.symbolTable != null) {
                f.append(" table=").append(p.symbolTable.num).append('/').append(p.symbolTable.level);
            }
            if (p.nonLocal != null) {
                f.append(" nonlocal=").append(p.nonLocal.num);
            }
            if (p.status != 0) {
                f.append(" status=0x").append(Integer.toHexString(p.status));
            }
            if (p.dynamicStackAllocs) {
                f.append(" dsa");
            }
            if (p.publicized) {
                f.append(" public");
            }
            f.append('\n');
            dumpSemanticTree(f, p.sub, depth + 1, tags);
        }
    }

    /** Same format as a68j_dump_semantic in the ga68 patch. */
    static void dumpSemantic(A68 a68, PrintStream out) {
        StringBuilder f = new StringBuilder();
        java.util.List<a68j.front.Tag> tags = new java.util.ArrayList<>();
        dumpSemanticTree(f, a68.job.topNode, 0, tags);
        f.append("MODES\n");
        for (a68j.front.Moid m = a68.job.topMoid; m != null; m = m.next) {
            f.append('#').append(m.number).append(' ').append(m.attribute.name()).append(" dim=").append(m.dim);
            moidRef(f, "sub", m.sub);
            moidRef(f, "eq", m.equivalentMode);
            moidRef(f, "slice", m.slice);
            moidRef(f, "deflexed", m.deflexedMode);
            moidRef(f, "name", m.name);
            moidRef(f, "multiple", m.multipleMode);
            moidRef(f, "rowed", m.rowed);
            moidRef(f, "trim", m.trim);
            f.append(" flags=").append(b(m.hasRows)).append(b(m.hasRefs)).append(b(m.use))
                    .append(b(m.portable)).append(b(m.derivate));
            f.append(" pack=(");
            for (a68j.front.Pack z = m.pack; z != null; z = z.next) {
                f.append(z == m.pack ? "" : " ").append('#').append(z.type != null ? z.type.number : -1);
                // Only field names (see the ga68 patch).
                if (z.text != null && m.attribute == a68j.front.Attribute.STRUCT_SYMBOL) {
                    f.append(':').append(TokenDump.escape(z.text));
                }
            }
            f.append(") ");
            f.append(TokenDump.escape(a68j.front.MoidsToString.moidToString(a68, m, 1000, null)));
            f.append('\n');
        }
        f.append("TAGS\n");
        for (a68j.front.Tag t : tags) {
            f.append('#').append(t.number);
            moidRef(f, "moid", t.type);
            f.append(" prio=").append(t.priority).append(" heap=").append(t.heap.name())
                    .append(" scope=").append(t.scope).append(" youngest=").append(t.youngestEnviron)
                    .append(" status=0x").append(Integer.toHexString(t.status));
            f.append(" flags=").append(b(t.scopeAssigned)).append(b(t.use)).append(b(t.inProc))
                    .append(b(t.nestProc)).append(b(t.locAssigned)).append(b(t.portable)).append(b(t.variable))
                    .append(b(t.ascribedRoutineText)).append(b(t.isRecursive)).append(b(t.publicized));
            if (t.node != null) {
                f.append(" node=").append(t.node.number);
            }
            if (t.unit != null) {
                f.append(" unit=").append(t.unit.number);
            }
            if (t.symbolTable != null) {
                f.append(" table=").append(t.symbolTable.num);
            }
            if (t.value != null) {
                f.append(" value=").append(TokenDump.escape(t.value));
            }
            if (t.externSymbol != null) {
                f.append(" extern=").append(TokenDump.escape(t.externSymbol));
            }
            f.append('\n');
        }
        out.print(f);
    }

    private SyntaxDump() {}
}
