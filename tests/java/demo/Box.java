// A generic Java class and a subclass for tests/java/nest-java-fields.a68 (compiled by
// tools/java_tests.sh): a field of a type variable, and a field inherited from a superclass.
package demo;
public class Box<T> {
    public T value;
    public static class StringBox extends Box<String> {
        public int reads;
    }
}
