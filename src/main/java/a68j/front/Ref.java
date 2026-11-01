// A reference to a variable, replacing C's T** out-parameters.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A reference to a variable of type T: the Java replacement for a C
 * {@code T **} parameter. Use {@link #box} for a local variable and
 * {@link #of} for a field, e.g. {@code Ref.of(() -> a68.job.topMoid, v -> a68.job.topMoid = v)}
 * for {@code &TOP_MOID (&A68_JOB)}.
 */
public interface Ref<T> {
    T get();

    void set(T value);

    /** A fresh variable with the given initial value. */
    static <T> Box<T> box(T initial) {
        return new Box<>(initial);
    }

    /** A reference to a field or other location, given by getter and setter. */
    static <T> Ref<T> of(Supplier<T> getter, Consumer<T> setter) {
        return new Ref<>() {
            @Override
            public T get() {
                return getter.get();
            }

            @Override
            public void set(T value) {
                setter.accept(value);
            }
        };
    }

    /** A simple mutable box. */
    final class Box<T> implements Ref<T> {
        public T v;

        public Box(T v) {
            this.v = v;
        }

        @Override
        public T get() {
            return v;
        }

        @Override
        public void set(T value) {
            v = value;
        }
    }
}
