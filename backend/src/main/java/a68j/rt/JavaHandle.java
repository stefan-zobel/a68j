// a68j runtime: any Java object as an Algol 68 value (-fnest-java).
//
// Copyright (C) 2026 The a68j authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH GCC-exception-3.1
//
// This file is part of a68j. It is licensed under the GNU General Public
// License, version 3 or later. Under Section 7 of GPL version 3, you are
// granted additional permissions described in the GCC Runtime Library
// Exception, version 3.1, as published by the Free Software Foundation; see
// the files COPYING and COPYING.RUNTIME.
//
// Additional permission under GNU GPL version 3 section 7: for this file, a
// Compilation Process done with a68j, alone or with other GPL-compatible
// software, is an Eligible Compilation Process in the sense of the GCC
// Runtime Library Exception, version 3.1.
package a68j.rt;

/**
 * A Java object of any class as an Algol 68 value (nest Java,
 * docs/java-modules.md): the result of {@code nest Java "java.util.ArrayList::new"}
 * or of a Java method whose result is an object, where the mode is a name
 * (MODE ARRAYLIST = REF ARRAYLISTOBJECT). The classes of the Java modules
 * extend JavaObject themselves; an object of any other class is wrapped in
 * a JavaHandle on its way to Algol 68 and unwrapped on its way back
 * (JavaConv#objRef, JavaConv#javaObject), so a Java method never sees the
 * handle.
 *
 * <p>Two handles of the same object are the same name: IS compares the
 * objects (as == in Java), not the handles.
 */
public final class JavaHandle extends JavaObject {
    /** The Java object, never null (null is NIL). */
    public final Object value;

    public JavaHandle(Object value) {
        this.value = value;
    }

    @Override
    public boolean same(Ref o) {
        return o instanceof JavaHandle h && h.value == value;
    }

    @Override
    protected String javaClassName() {
        return value.getClass().getName();
    }
}
