// a68j runtime: activation records for captured identifiers. GPLv3+.
package a68j.rt;

/** Captured identifiers of one routine invocation, linked to the enclosing routine's frame. */
public final class Frame {
    public final Frame up;
    public final Object[] s;

    private static final Object[] NONE = new Object[0];

    public Frame(Frame up, int size) {
        this.up = up;
        this.s = size == 0 ? NONE : new Object[size];
    }
}
