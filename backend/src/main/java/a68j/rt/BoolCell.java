// a68j runtime: a location of mode boolean kept unboxed. GPLv3+.
package a68j.rt;

/**
 * A location created by a generator or a variable declaration of a
 * primitive mode, holding its value unboxed (see Rt#derefZ, Rt#assignZ).
 */
public final class BoolCell extends Ref {
    public boolean v;

    public BoolCell(boolean v) {
        this.v = v;
    }

    @Override
    public Object get() {
        return v;
    }

    @Override
    public void set(Object o) {
        v = (Boolean) o;
    }
}
