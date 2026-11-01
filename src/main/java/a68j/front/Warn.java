// Optional warnings of the Algol 68 front-end.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * Optional warnings, corresponding to GCC's {@code OPT_W...} codes passed to
 * a68_warning. A warning issued with option 0 in C is issued with
 * {@code null} in Java and is always emitted.
 */
public enum Warn {
    /** OPT_Wextensions */
    EXTENSIONS("-Wextensions"),
    /** OPT_Wscope */
    SCOPE("-Wscope"),
    /** OPT_Whidden_declarations_ */
    HIDDEN_DECLARATIONS("-Whidden-declarations="),
    /** OPT_Wunused */
    UNUSED("-Wunused"),
    /** OPT_Wvoiding */
    VOIDING("-Wvoiding"),
    /** OPT_Wuninitialized */
    UNINITIALIZED("-Wuninitialized"),
    /** Similar codes not listed here can be added as needed. */
    OTHER("-W");

    public final String option;

    Warn(String option) {
        this.option = option;
    }
}
