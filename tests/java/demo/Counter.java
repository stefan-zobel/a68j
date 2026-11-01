// A Java class of the user for tests/java/nest-java-fields.a68 (compiled by tools/java_tests.sh):
// public fields of all kinds, read and written through "Class::field".
package demo;
public class Counter {
    public static int created;
    public static final String KIND = "counter";
    public static double scale = 1.5;
    public final String name;
    public int count;
    public long total;
    public Counter next;
    public int size = 7;
    public int size() { return 42; }
    public Counter(String name) { this.name = name; created++; }
    public void add(int n) { count++; total += n; }
}
