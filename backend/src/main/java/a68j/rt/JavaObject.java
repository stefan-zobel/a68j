// a68j runtime: Java objects as Algol 68 values (-fnest-java).
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
