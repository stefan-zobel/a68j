// a68j runtime: a plain location (LOC/HEAP generator). GPLv3+.
package a68j.rt;

/** A location created by a generator or a variable declaration. */
public final class Cell extends Ref {
    public Object value;

    public Cell(Object value) {
        this.value = value;
    }

    @Override
    public Object get() {
        return value;
    }

    @Override
    public void set(Object v) {
        value = v;
    }
}
