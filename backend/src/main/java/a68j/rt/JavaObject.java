// a68j runtime: Java objects as Algol 68 values (-fnest-java). GPLv3+.
package a68j.rt;

/**
 * A Java object in an Algol 68 program (nest Java, docs/java-modules.md).
 * Its Algol 68 mode is a name, for example MODE INTLIST = REF INTLISTOBJECT:
 * it can be passed around, compared with IS, ISNT and NIL, and given to
 * the procedures of its module, but not dereferenced or assigned to. A
 * Java class whose objects an Algol 68 program sees extends this class.
 */
public abstract class JavaObject extends Ref {
    /**
     * A runtime error in a Java method called from Algol 68: the message and
     * status 134 as for other runtime errors, but without a position (the
     * method does not know where it was called from).
     */
    public static RuntimeException error(String text) {
        Rt.OUT.flush();
        System.err.print(Rt.programName + ": runtime error: " + text + "\n");
        System.err.flush();
        Runtime.getRuntime().halt(134);
        return new IllegalStateException();
    }

    @Override
    public final Object get() {
        throw error("attempt to dereference a Java object (" + getClass().getName() + ")");
    }

    @Override
    public final void set(Object v) {
        throw error("attempt to assign to a Java object (" + getClass().getName() + ")");
    }
}
