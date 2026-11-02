// a68j runtime: a routine text together with its environment.
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
