// a68j JVM backend: list the standard-environment tags without a runtime method.
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import a68j.front.A68;
import a68j.front.Moid;
import a68j.front.Pack;
import a68j.front.ParserDriver;
import a68j.front.Tag;

import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Development aid: {@code StdCheck file.a68} parses a program and prints, for
 * every identifier and operator of the standard environment, the
 * {@code a68j.rt.Std} method the code generator would call when the
 * operation is not emitted inline, if that method does not exist.
 */
public final class StdCheck {
    private StdCheck() {}

    public static void main(String[] args) throws Exception {
        A68 a68 = new A68();
        ParserDriver.parse(a68, args[0]);
        Gen g = new Gen(a68, "X", args[0]);
        Class<?> std = Class.forName("a68j.rt.Std");
        TreeSet<String> have = new TreeSet<>();
        for (Method m : std.getMethods()) {
            have.add(m.getName() + sig(m.getParameterTypes()));
        }
        TreeSet<String> missing = new TreeSet<>();
        for (Tag t = a68.standenv.identifiers; t != null; t = t.next) {
            Moid m = JType.base(t.type);
            if (JType.of(a68, m) == JType.PROC) {
                check(g, missing, have, Intrinsics.method(g, "", Intrinsics.name(t), m), m, t);
            } else {
                String n = Intrinsics.mangle(Intrinsics.name(t));
                if (!have.contains(n + "()")) {
                    missing.add(n + "()   " + Intrinsics.key(g, t) + (t.unimplemented ? "   [ga68: unimplemented]" : ""));
                }
            }
        }
        for (Tag t = a68.standenv.operators; t != null; t = t.next) {
            Moid m = JType.base(t.type);
            int n = 0;
            for (Pack z = m.pack; z != null; z = z.next) {
                n++;
            }
            check(g, missing, have, Intrinsics.method(g, n == 1 ? "op" : "", Intrinsics.name(t), m), m, t);
        }
        missing.forEach(System.out::println);
    }

    private static void check(Gen g, TreeSet<String> missing, TreeSet<String> have, String name, Moid proc, Tag t) {
        List<String> ps = new ArrayList<>();
        for (Pack z = proc.pack; z != null; z = z.next) {
            ps.add(desc(JType.of(g.a68, z.type).desc));
        }
        String s = name + "(" + String.join(",", ps) + ")";
        if (!have.contains(s)) {
            missing.add(s + "   " + Intrinsics.key(g, t) + (t.unimplemented ? "   [ga68: unimplemented]" : ""));
        }
    }

    private static String desc(ClassDesc d) {
        String s = d.displayName();
        return s;
    }

    private static String sig(Class<?>[] ps) {
        List<String> l = new ArrayList<>();
        for (Class<?> c : ps) {
            l.add(c.getSimpleName());
        }
        return "(" + String.join(",", l) + ")";
    }
}
