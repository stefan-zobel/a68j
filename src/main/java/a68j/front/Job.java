// Java port of ga68's a68-types.h.
// Original: Copyright (C) 2001-2023 J. Marcel van der Veer,
//           Copyright (C) 2025 Jose E. Marchesi.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * Global data of the source file being compiled (MODULE_T, accessed as
 * A68_JOB). Renamed to avoid a clash with {@code java.lang.Module}.
 */
public final class Job {
    public boolean treeListingSafe, crossReferenceSafe;
    public int errorCount, warningCount, sourceScan;
    public Line topLine;
    public Moid topMoid, standenvMoid;
    public Moif topMoif;
    public Node topNode;
    public final Options options = new Options();
    public String fileSourceName;

    /** Scanner look-ahead state (scan_state). */
    public Line scanStateL;
    public int scanStateS;
    public char scanStateC;
}
