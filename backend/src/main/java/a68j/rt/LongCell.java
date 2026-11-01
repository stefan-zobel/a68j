// a68j runtime: a location of mode long kept unboxed. GPLv3+.
package a68j.rt;

/**
 * A location created by a generator or a variable declaration of a
 * primitive mode, holding its value unboxed (see Rt#derefL, Rt#assignL).
 */
public final class LongCell extends Ref {
    public long v;

    public LongCell(long v) {
        this.v = v;
    }

    @Override
    public Object get() {
        return v;
    }

    @Override
    public void set(Object o) {
        v = (Long) o;
    }
}
