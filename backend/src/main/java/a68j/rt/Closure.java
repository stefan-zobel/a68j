// a68j runtime: a routine text together with its environment. GPLv3+.
package a68j.rt;

import java.lang.invoke.MethodHandle;

/** A procedure value made from a routine text: method (Frame, Object[]) -> Object and the frame. */
public final class Closure extends Proc {
    public final MethodHandle mh;
    public final Frame env;

    public Closure(MethodHandle mh, Frame env) {
        this.mh = mh;
        this.env = env;
    }

    @Override
    public Object call(Object[] args) {
        try {
            return (Object) mh.invokeExact(env, args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
