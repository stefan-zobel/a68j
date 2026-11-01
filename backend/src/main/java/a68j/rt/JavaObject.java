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
     * status 134 as for other runtime errors. The position is that of the
     * Algol 68 code that called the method, found on the stack (see
     * Rt#abortAtCaller); for the bundled modules Lists and Hashmaps that of
     * the program that used the operator. Does not return; the result is
     * only there for {@code throw JavaObject.error(...)}.
     */
    public static RuntimeException error(String text) {
        throw Rt.abortAtCaller(text);
    }

    /** The name of the Java class of this object, for the messages. */
    protected String javaClassName() {
        return getClass().getName();
    }

    @Override
    public final Object get() {
        throw error("attempt to dereference a Java object (" + javaClassName() + ")");
    }

    @Override
    public final void set(Object v) {
        throw error("attempt to assign to a Java object (" + javaClassName() + ")");
    }
}
