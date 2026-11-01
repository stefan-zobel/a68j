// MethodSizes: print the bytecode size of each method of the given class files.
// Usage: java tools/MethodSizes.java [-min N] file.class...
// HotSpot does not JIT-compile methods with more than 8000 bytes of bytecode
// (-XX:HugeMethodLimit, -XX:+DontCompileHugeMethods), so this shows which
// generated methods only ever run in the interpreter.

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.nio.file.Files;
import java.nio.file.Path;

public class MethodSizes {
    public static void main(String[] args) throws Exception {
        int min = 0;
        int i = 0;
        if (args.length > 1 && args[0].equals("-min")) {
            min = Integer.parseInt(args[1]);
            i = 2;
        }
        for (; i < args.length; i++) {
            ClassModel cm = ClassFile.of().parse(Files.readAllBytes(Path.of(args[i])));
            for (MethodModel m : cm.methods()) {
                var code = m.findAttribute(java.lang.classfile.Attributes.code());
                if (code.isEmpty()) {
                    continue;
                }
                int n = ((CodeAttribute) code.get()).codeLength();
                if (n >= min) {
                    System.out.println(n + " " + args[i] + " " + m.methodName().stringValue());
                }
            }
        }
    }
}
