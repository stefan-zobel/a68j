// a68j runtime: a location of mode float kept unboxed. GPLv3+.
package a68j.rt;

/**
 * A location created by a generator or a variable declaration of a
 * primitive mode, holding its value unboxed (see Rt#derefF, Rt#assignF).
 */
public final class FloatCell extends Ref {
    public float v;

    public FloatCell(float v) {
        this.v = v;
    }

    @Override
    public Object get() {
        return v;
    }

    @Override
    public void set(Object o) {
        v = (Float) o;
    }
}
