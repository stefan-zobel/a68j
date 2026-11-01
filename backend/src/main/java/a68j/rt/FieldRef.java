// a68j runtime: the name of a struct field. GPLv3+.
package a68j.rt;

/** Name of a field of a structured value held in a location. */
public final class FieldRef extends Ref {
    public final Struct struct;
    public final int field;

    public FieldRef(Struct struct, int field) {
        this.struct = struct;
        this.field = field;
    }

    @Override
    public Object get() {
        return struct.f[field];
    }

    @Override
    public void set(Object v) {
        struct.f[field] = v;
    }

    @Override
    public boolean same(Ref o) {
        return o instanceof FieldRef r && r.struct == struct && r.field == field;
    }
}
