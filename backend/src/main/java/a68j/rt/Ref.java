// a68j runtime: names (REF modes). Licensed under the GNU GPL, version 3 or later.
package a68j.rt;

/** A name: a reference to a location holding a boxed value. */
public abstract class Ref {
    public abstract Object get();

    public abstract void set(Object v);

    /** Whether this and {@code o} denote the same location (identity relation). */
    public boolean same(Ref o) {
        return this == o;
    }
}
