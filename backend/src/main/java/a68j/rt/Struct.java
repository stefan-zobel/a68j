// a68j runtime: structured values. GPLv3+.
package a68j.rt;

/** A structured value; fields are boxed. */
public final class Struct {
    public final Object[] f;

    public Struct(Object[] f) {
        this.f = f;
    }

    /** Deep copy (value semantics). */
    public Struct copy() {
        Object[] g = new Object[f.length];
        for (int i = 0; i < f.length; i++) {
            g[i] = Rt.copyValue(f[i]);
        }
        return new Struct(g);
    }
}
