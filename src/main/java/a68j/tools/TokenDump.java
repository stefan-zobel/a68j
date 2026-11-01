// Command-line tool: run the scanner and dump the token list.
// Licensed under the GNU GPL, version 3 or later.

package a68j.tools;

import a68j.front.A68;
import a68j.front.Attribute;
import a68j.front.Diagnostics;
import a68j.front.Keywords;
import a68j.front.Node;
import a68j.front.Scanner;
import a68j.front.Stropping;

import java.io.BufferedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Tokenise an Algol 68 source file and print one line per token:
 *
 * <pre>file:line:column ATTRIBUTE "symbol" [pragmat=TYPE:"text"] [comment=TYPE:"text"]</pre>
 *
 * The format is identical to the output of ga68 with
 * tools/ga68-token-dump.patch applied and A68J_DUMP_TOKENS set, so that the
 * two can be compared with diff.
 *
 * <p>Usage: {@code TokenDump [-fstropping=upper|supper] [-std=algol68] [-Idir] file.a68}
 */
public final class TokenDump {

    public static void main(String[] args) {
        A68 a68 = new A68();
        String file = null;
        for (String arg : args) {
            if (arg.equals("-fstropping=upper")) {
                a68.options().stropping = Stropping.UPPER;
            } else if (arg.equals("-fstropping=supper")) {
                a68.options().stropping = Stropping.SUPPER;
            } else if (arg.equals("-std=algol68")) {
                a68.options().strict = true;
            } else if (arg.equals("-std=gnu68")) {
                a68.options().strict = false;
            } else if (arg.equals("-fbrackets")) {
                a68.options().brackets = true;
            } else if (arg.startsWith("-I")) {
                a68.includePaths.add(arg.substring(2));
            } else {
                file = arg;
            }
        }
        if (file == null) {
            System.err.println("usage: TokenDump [-fstropping=upper|supper] [-std=algol68] [-Idir] file.a68");
            System.exit(2);
        }

        // a68_post_options
        if (!a68.options().strict && a68.options().brackets) {
            System.err.println("a68j: fatal error: '-fbrackets' can only be used with UPPER stroping");
            System.exit(1);
        }

        PrintStream out = new PrintStream(new BufferedOutputStream(System.out), false, StandardCharsets.ISO_8859_1);
        // Diagnostics may contain raw source bytes; write them unchanged, as ga68 does.
        System.setErr(new PrintStream(System.err, true, StandardCharsets.ISO_8859_1));
        Keywords.setUpTables(a68);
        try {
            new Scanner(a68).lexicalAnalyser(file);
        } catch (Diagnostics.ScanError e) {
            out.flush();
            System.err.println(e.diagnostic);
            System.exit(1);
        }
        for (Node p = a68.job.topNode; p != null; p = p.next) {
            out.print(p.info.line.filename + ":" + p.info.line.number + ":" + (p.info.charInLine + 1) + " "
                    + p.attribute.name() + " ");
            if (p.info.symbol == null) {
                out.print("(null)");
            } else {
                out.print(escape(p.info.symbol));
            }
            if (p.info.pragmat != null) {
                out.print(" pragmat=" + p.info.pragmatType.name() + ":" + escape(p.info.pragmat));
            }
            if (p.info.comment != null) {
                out.print(" comment=" + p.info.commentType.name() + ":" + escape(p.info.comment));
            }
            out.print('\n');
        }
        out.flush();
        for (Diagnostics.Diagnostic d : a68.diagnostics.messages()) {
            System.err.println(d);
        }
    }

    /** Same escaping as a68j_dump_escaped in the ga68 patch. */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '"') {
                sb.append("\\\"");
            } else if (c < 32 || c >= 127) {
                sb.append(String.format("\\x%02x", (int) c & 0xff));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private TokenDump() {}

    static {
        // Make sure the attribute enum is loaded early (and matches the C enum).
        Attribute.of(0);
    }
}
