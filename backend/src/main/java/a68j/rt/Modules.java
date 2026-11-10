// a68j runtime: access to the public tags of compiled modules.
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

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A compiled module class has {@code static String pubKeys()} (the keys of
 * its public tags, one per line), {@code static Object pub$v(int i)} (the
 * value of the tag i in the current activation of the module, elaborating
 * the module on first use) and typed entries {@code pub$i} of procedures.
 * Code using a module tag reads its value through a call site
 * bootstrapped by {@link #bsmValue} and calls a module procedure through
 * one bootstrapped by {@link #bsmCall}. Neither keeps a value: a module
 * accessed again after its postlude is elaborated again with new storage.
 */
public final class Modules {
    private Modules() {}

    /** Index of each public key of the module class CLS (the order of pubKeys()). */
    private static final Map<String, Map<String, Integer>> INDEX = new ConcurrentHashMap<>();

    private static Map<String, Integer> index(Class<?> c) throws ReflectiveOperationException {
        Map<String, Integer> m = INDEX.get(c.getName());
        if (m == null) {
            String[] ks = ((String) c.getMethod("pubKeys").invoke(null)).split("\n");
            m = new HashMap<>();
            for (int i = 0; i < ks.length; i++) {
                m.putIfAbsent(ks[i], i);
            }
            INDEX.putIfAbsent(c.getName(), m);
        }
        return m;
    }

    /** The module class CLS, loaded by the loader of the class using it. */
    private static Class<?> moduleClass(MethodHandles.Lookup lookup, String cls) {
        try {
            // With -run that is the loader of the program, which sees the
            // library jars.
            return Class.forName(cls, true, lookup.lookupClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("a68j: cannot load module class " + cls, e);
        }
    }

    /** A handle () Object reading the value of the public tag KEY of the module class C. */
    private static MethodHandle value(MethodHandles.Lookup lookup, Class<?> c, String key)
            throws ReflectiveOperationException {
        Integer i = index(c).get(key);
        if (i == null) {
            throw new IllegalStateException("a68j: module " + c.getName() + " has no public " + key);
        }
        MethodHandle v = lookup.findStatic(c, "pub$v", MethodType.methodType(Object.class, int.class));
        return MethodHandles.insertArguments(v, 0, i);
    }

    /** A68J_DEBUG_LINK=1: report how each call site of a module tag is linked. */
    private static final boolean DEBUG = System.getenv("A68J_DEBUG_LINK") != null;

    private static final MethodHandle PROC_CALL;

    static {
        try {
            PROC_CALL = MethodHandles.publicLookup().findVirtual(Proc.class, "call",
                    MethodType.methodType(Object.class, Object[].class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Bootstrap of the call sites of module procedures and operators
     * (invokedynamic, see a68j.jvm.Intrinsics#callModuleTag). TYPE has the
     * parameters and the result unboxed. Links to the typed entry
     * {@code pub$i} of the module class if it has one of exactly this type,
     * otherwise to the boxed procedure value, read at every call: the
     * arguments are boxed into an Object[] and the result unboxed.
     */
    public static CallSite bsmCall(MethodHandles.Lookup lookup, String name, MethodType type, String cls, String key)
            throws ReflectiveOperationException {
        Class<?> c = moduleClass(lookup, cls);
        Integer i = index(c).get(key);
        if (i != null) {
            try {
                MethodHandle h = lookup.findStatic(c, "pub$" + i, type);
                if (DEBUG) {
                    System.err.println("a68j: " + cls + " " + key + ": typed entry pub$" + i);
                }
                return new ConstantCallSite(h);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // No typed entry: use the procedure value.
            }
        }
        if (DEBUG) {
            System.err.println("a68j: " + cls + " " + key + ": boxed procedure value");
        }
        MethodHandle proc = value(lookup, c, key).asType(MethodType.methodType(Proc.class));
        MethodHandle h = MethodHandles.collectArguments(PROC_CALL, 0, proc)
                .asCollector(Object[].class, type.parameterCount()).asType(type);
        return new ConstantCallSite(h);
    }

    /**
     * Bootstrap of the call sites that read the value of a module tag
     * (invokedynamic of type () Object, see a68j.jvm.Intrinsics#loadModuleTag).
     */
    public static CallSite bsmValue(MethodHandles.Lookup lookup, String name, MethodType type, String cls, String key)
            throws ReflectiveOperationException {
        return new ConstantCallSite(value(lookup, moduleClass(lookup, cls), key).asType(type));
    }
}
