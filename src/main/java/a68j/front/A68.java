// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Global state of the Algol 68 front-end (A68_T).
 *
 * <p>Unlike the C front-end, which uses a single global instance, the Java
 * port passes an instance of this class explicitly, so several compilations
 * can coexist in one JVM. GCC-specific members (global trees, global
 * declarations, module definition decls) are not ported.
 */
public final class A68 {
    public int newNodes;
    public int newModes;
    public int newPostulates;
    public int newNodeInfos;
    public int newGenieInfos;
    public int symbolTableCount;
    public int modeCount;
    public Keyword topKeyword;
    public final ModeCache modeCache = new ModeCache();
    /** Pre-defined modes, accessed as M_* in C. */
    public final Modes modes = new Modes();
    /** A68_JOB. */
    public final Job job = new Job();
    public Postulate postulates, topPostulate, topPostulateList;
    public Soid topSoidList;
    public Table standenv;
    public Token topToken;
    /** Search paths for included files (-I), searched in FIFO order. */
    public final List<String> includePaths = new ArrayList<>();
    /** Search paths for module exports data. */
    public final List<String> importPaths = new ArrayList<>();
    /**
     * a68j (JVM): the library jars (libX.jar) whose export data was read
     * (Imports#tryLibraryJar); a program accessing their modules needs them
     * on the class path.
     */
    public final List<String> libraryJars = new ArrayList<>();
    /** Association between module indicants and files (-fmodules-map). */
    public final Map<String, String> moduleFiles = new LinkedHashMap<>();
    {
        // a68-lang.cc: the TRANSPUT module lives in libga68.
        moduleFiles.put("TRANSPUT", "ga68");
    }
    public final ParserState parserState = new ParserState();
    /** GCC's global line_table. */
    public final LineMaps lineTable = new LineMaps();
    public final Diagnostics diagnostics = new Diagnostics(this);

    private final Map<Class<?>, Object> passState = new HashMap<>();

    /**
     * Per-compilation state of a pass, replacing file-static variables of the
     * C sources: {@code a68.state(BottomUp.State.class, BottomUp.State::new)}.
     */
    @SuppressWarnings("unchecked")
    public <T> T state(Class<T> key, Supplier<T> init) {
        return (T) passState.computeIfAbsent(key, k -> init.get());
    }

    /** Shorthand for {@code job.options}. */
    public Options options() {
        return job.options;
    }
}
