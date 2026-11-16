// A rewrite of the source text before the scanner (a68j only, not in ga68).
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * a68j: a rewrite of the text of each source file before the scanner makes
 * its lines: the main file and each included file ({@link A68#sourceFilter}).
 * The options -fgenie and -fformats use it for the format rewrite tool
 * (a68j.jvm.GenieRewrite). ga68 has nothing like it; without a filter the
 * scanner reads the files as ga68 does.
 */
@FunctionalInterface
public interface SourceFilter {
    /**
     * The result of a rewrite: the text to scan, with the same lines as the
     * original; or, if ERROR is not null, the original text and an error at
     * its line ERRORLINE (from 1; 0 if the error has no line).
     */
    record Result(String text, int errorLine, String error) {}

    /** Rewrites TEXT, the content of a source file. */
    Result apply(String text);
}
