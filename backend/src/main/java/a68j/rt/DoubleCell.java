// a68j runtime: a location of mode double kept unboxed. GPLv3+.
package a68j.rt;

/**
 * A location created by a generator or a variable declaration of a
 * primitive mode, holding its value unboxed (see Rt#derefD, Rt#assignD).
 */
public final class DoubleCell extends Ref {
    public double v;

    public DoubleCell(double v) {
        this.v = v;
    }

    @Override
    public Object get() {
        return v;
    }

    @Override
    public void set(Object o) {
        v = (Double) o;
    }
}
