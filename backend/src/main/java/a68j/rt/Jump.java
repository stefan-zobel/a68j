// a68j runtime: a jump to a label of a serial clause. GPLv3+.
package a68j.rt;

/**
 * Thrown by a jump (GOTO label); caught by the serial clause that declares
 * the label in the activation (frame) that owns it. Carries no stack trace.
 */
public final class Jump extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Frame of the routine activation in which the label is declared. */
    public final transient Frame frame;
    /** Number of the label within the program. */
    public final int label;

    public Jump(Frame frame, int label) {
        super(null, null, false, false);
        this.frame = frame;
        this.label = label;
    }
}
