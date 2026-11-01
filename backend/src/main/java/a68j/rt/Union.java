// a68j runtime: united values. GPLv3+.
package a68j.rt;

/** A united value: the number of the mode of the value, and the boxed value. */
public final class Union {
    public final int mode;
    public final Object value;

    public Union(int mode, Object value) {
        this.mode = mode;
        this.value = value;
    }
}
