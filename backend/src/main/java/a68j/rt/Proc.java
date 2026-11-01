// a68j runtime: procedure values. GPLv3+.
package a68j.rt;

/** A procedure value. Arguments and result are boxed; VOID results are null. */
public abstract class Proc {
    public abstract Object call(Object[] args);
}
