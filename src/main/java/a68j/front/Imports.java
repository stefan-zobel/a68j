// Java port of ga68's a68-imports.cc: importing Algol 68 module interfaces.
// Original: Copyright (C) 2025 Jose E. Marchesi,
//           Copyright (C) 2010-2026 Free Software Foundation, Inc.
// Written by Jose E. Marchesi.
//
// The following utility functions have been adapted from the Go front-end:
//
//   a68_open_packet
//   a68_try_packet_in_directory
//   a68_try_suffixes
//   a68_find_export_data
//   a68_find_object_export_data
//   a68_read_export_data
//
// Ported to Java as a close 1:1 translation.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import static a68j.front.Attribute.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Importing Algol 68 module interfaces (a68-imports.cc): finding the exports
 * data of a module in the file system, and decoding it into MOIFs.
 *
 * <h2>Porting notes</h2>
 * <ul>
 * <li>File descriptors are represented by {@link ElfSection.Fd}; libiberty's
 * simple_object is replaced by {@link ElfSection} (ELF64 little-endian only).</li>
 * <li>A C {@code char *} buffer plus its {@code size_t} size is a
 * {@code byte[]}; the size out-parameters ({@code size_t *psize},
 * {@code size_t *plen}) are therefore dropped.</li>
 * <li>Positions in the exports data ({@code size_t pos}) are {@code long},
 * because they are combined with the 64-bit offsets read from the data;
 * unsigned C comparisons use {@link Long#compareUnsigned}.</li>
 * <li>The decoding macros DINT8, DUINT8, ... {@code goto decode_error}; their
 * Java versions throw {@link DecodeError}, which is caught at the
 * {@code decode_error} label of the decoding function.</li>
 * <li>{@code encoded_modes_map_t} (a GCC {@code hash_map}) is emulated by
 * {@link EncodedModesMap}, including GCC's open addressing, so that the
 * iteration order (which determines the order in which moids are created and
 * stored in the MOIF) is the same as in ga68.</li>
 * <li>Reading archives (a68-imports-archive.cc) is not ported.</li>
 * </ul>
 */
public final class Imports {
    private Imports() {}

    // ------------------------------------------------------------------
    // Definitions from a68.h.
    // ------------------------------------------------------------------

    /** Magic number for the exports data. */
    public static final int A68_EXPORT_MAGIC_LEN = 2;

    public static final int A68_EXPORT_MAGIC1 = 0x0a;
    public static final int A68_EXPORT_MAGIC2 = 0x68;

    /** The segment name we pass to simple_object_start_read to find Algol 68 export data. */
    public static final String A68_EXPORT_SEGMENT_NAME = "__GNU_A68";

    /** The section name we use when reading and writing export data. */
    public static final String A68_EXPORT_SECTION_NAME = ".a68_exports";

    // ga68 export format definitions. See ga68-exports.pk.

    public static final int GA68_EXPORTS_VERSION = 1;

    public static final int GA68_MODE_UNKNOWN = 0;
    public static final int GA68_MODE_VOID = 1;
    public static final int GA68_MODE_INT = 2;
    public static final int GA68_MODE_REAL = 3;
    public static final int GA68_MODE_BITS = 4;
    public static final int GA68_MODE_BYTES = 5;
    public static final int GA68_MODE_CHAR = 6;
    public static final int GA68_MODE_BOOL = 7;
    public static final int GA68_MODE_CMPL = 8;
    public static final int GA68_MODE_ROW = 9;
    public static final int GA68_MODE_STRUCT = 10;
    public static final int GA68_MODE_UNION = 11;
    public static final int GA68_MODE_NAME = 12;
    public static final int GA68_MODE_PROC = 13;
    public static final int GA68_MODE_STRING = 14;
    public static final int GA68_MODE_FLEX = 15;

    public static final int GA68_EXTRACT_MODU = 0;
    public static final int GA68_EXTRACT_IDEN = 1;
    public static final int GA68_EXTRACT_MODE = 2;
    public static final int GA68_EXTRACT_PRIO = 3;
    public static final int GA68_EXTRACT_OPER = 4;

    /** The character at index I of S, or NUL past its end (as a C string). */
    private static char at(String s, int i) {
        return i < s.length() ? s.charAt(i) : '\0';
    }

    // ------------------------------------------------------------------
    // Module maps.
    // ------------------------------------------------------------------

    // A few macros to aid parsing of module map strings below.

    /** SKIP_WHITESPACES (P) */
    private static void skipWhitespaces(A68 a68, String s, int[] p) {
        while (CType.isSpace(at(s, p[0]))) {
            p[0]++;
        }
    }

    /** PARSE_BASENAME (P, W): returns W. */
    private static String parseBasename(A68 a68, String s, int[] p) {
        StringBuilder w = new StringBuilder();
        while (at(s, p[0]) != '=' && !CType.isSpace(at(s, p[0])) && at(s, p[0]) != '\0') {
            w.append(at(s, p[0]++));
        }
        return w.toString();
    }

    /** PARSE_INDICANT (P, W): returns W. */
    private static String parseIndicant(A68 a68, String s, int[] p) {
        StringBuilder w = new StringBuilder();
        if (CType.isAlpha(at(s, p[0]))) {
            w.append(at(s, p[0]++));
            while (CType.isAlpha(at(s, p[0])) || CType.isDigit(at(s, p[0])) || at(s, p[0]) == '_') {
                if (at(s, p[0]) == '_') {
                    p[0]++;
                } else {
                    w.append(at(s, p[0]++));
                }
            }
        }
        return w.toString();
    }

    /**
     * Parse module map information in MAP and add entries to A68_MODULE_FILES
     * accordingly. Existing entries in the map are overridden without warning.
     *
     * <p>If MAP is not a valid module map specification then this function
     * returns `false' and sets *ERRMSG to some explanatory message. Otherwise it
     * returns `true' and sets *ERRMSG to NULL.
     */
    public static boolean processModuleMap(A68 a68, String map, Ref<String> errmsg) {
        int[] p = {0};

        while (at(map, p[0]) != '\0') {
            String filename;
            skipWhitespaces(a68, map, p);
            filename = parseBasename(a68, map, p);

            if (at(map, p[0]) != '=') {
                errmsg.set("expected = after filename");
                return false; // C: goto error
            }
            p[0]++;

            // Parse one or more joined module indicants.
            while (at(map, p[0]) != ':' && at(map, p[0]) != '\0') {
                String module;
                skipWhitespaces(a68, map, p);
                module = parseIndicant(a68, map, p);
                if (at(module, 0) == '\0') {
                    errmsg.set("expected module indicant");
                    return false; // C: goto error
                }

                skipWhitespaces(a68, map, p);
                if (at(map, p[0]) != ',' && at(map, p[0]) != ':' && at(map, p[0]) != '\0') {
                    errmsg.set("expected comma or end of string after module indicant");
                    return false; // C: goto error
                }

                StringBuilder q = new StringBuilder();
                for (int i = 0; i < module.length(); ++i) {
                    q.append(CType.toUpper(module.charAt(i)));
                }
                module = q.toString();
                a68.moduleFiles.put(module, filename);

                if (at(map, p[0]) == ',') {
                    p[0]++;
                }
            }

            skipWhitespaces(a68, map, p);
            if (at(map, p[0]) != ':' && at(map, p[0]) != '\0') {
                errmsg.set("expected semicolon or end of string");
                return false; // C: goto error
            }

            if (at(map, p[0]) == ':') {
                p[0]++;
            }
        }

        errmsg.set(null);
        return true;
    }

    // ------------------------------------------------------------------
    // Reading export data from files.
    // ------------------------------------------------------------------

    /**
     * Read exports from an object file.
     *
     * <p>FD is a file descriptor open for reading.
     *
     * <p>OFFSET is the offset within the file where the object file starts;
     * this will be 0 except when reading an archive.
     *
     * <p>On success this returns NULL and sets *PBUF to a buffer holding the
     * export data.
     *
     * <p>If the data is not found this returns NULL and sets *PBUF to NULL.
     *
     * <p>If some error occurs, this returns an error message and sets *PERR to
     * an errno value or 0 if there is no relevant errno.
     */
    private static String readExportData(A68 a68, ElfSection.Fd fd, long offset, Ref<byte[]> pbuf,
                                         int[] perr) {
        ElfSection sobj;
        String[] errmsg = new String[1];
        long[] secOffset = new long[1];
        long[] secLength = new long[1];
        int found;
        byte[] buf;
        long c;

        pbuf.set(null);

        sobj = ElfSection.startRead(fd, offset, A68_EXPORT_SEGMENT_NAME, errmsg, perr);
        if (sobj == null) {
            // If we get an error here, just pretend that we didn't find any
            // export data. This is the right thing to do if the error is that
            // the file was not recognized as an object file. This will ignore
            // file I/O errors, but it's not too big a deal because we will wind
            // up giving some other error later.
            return null;
        }

        found = ElfSection.findSection(sobj, A68_EXPORT_SECTION_NAME, secOffset, secLength, errmsg, perr);
        ElfSection.releaseRead(sobj);
        if (found == 0) {
            return errmsg[0];
        }

        if (fd.lseek(offset + secOffset[0], ElfSection.SEEK_SET) < 0) {
            perr[0] = fd.errno;
            return "lseek failed while reading export data";
        }

        // Sic: XNEWVEC never returns NULL in C (xmalloc aborts the compiler
        // on failure), so the check below is dead code there. In Java a size
        // that does not fit in an array takes this path.
        if (secLength[0] < 0 || secLength[0] > Integer.MAX_VALUE - 8) {
            buf = null;
        } else {
            buf = new byte[(int) secLength[0]];
        }
        if (buf == null) {
            perr[0] = 0;
            return "memory allocation failed while reading export data";
        }

        c = fd.read(buf, 0, secLength[0]);
        if (c < 0) {
            perr[0] = fd.errno;
            return "read failed while reading export data";
        }

        if (c < secLength[0]) {
            return "short read while reading export data";
        }

        pbuf.set(buf);
        return null;
    }

    /** Look for export data in an object file. */
    public static byte[] findObjectExportData(A68 a68, String filename, ElfSection.Fd fd, long offset) {
        Ref<byte[]> buf = Ref.box(null);
        // Sic: uninitialized in C, and not set by a68_read_export_data in the
        // "short read" case.
        int[] err = {0};

        String errmsg = readExportData(a68, fd, offset, buf, err);
        if (errmsg != null) {
            if (err[0] == 0) {
                a68.diagnostics.error(null, "%s: %s", filename, errmsg);
            } else {
                a68.diagnostics.error(null, "%s: %s: %s", filename, errmsg, ElfSection.strerror(err[0]));
            }
            return null;
        }

        return buf.get();
    }

    /**
     * a68_file_size (a68-parser-scanner.cc): get the size of a file given a
     * file descriptor FD. In case the size of the file cannot be determined
     * then this function returns -1.
     *
     * <p>Ported here because Scanner.java reads files through java.nio and has
     * no file-descriptor based equivalent.
     */
    private static long fileSize(A68 a68, ElfSection.Fd fd) {
        long fsize;
        long off, save;

        save = fd.lseek(0, ElfSection.SEEK_CUR);
        if (save == -1) {
            return -1;
        }

        off = fd.lseek(0, ElfSection.SEEK_END);
        if (off == -1) {
            return -1;
        }
        fsize = off;

        off = fd.lseek(save, ElfSection.SEEK_SET);
        if (off == -1) {
            return -1;
        }

        return fsize;
    }

    /**
     * a68_file_read (a68-parser-scanner.cc): read bytes from file into buffer
     * given a file descriptor. EINTR cannot happen in Java, so the restart
     * logic (MAX_RESTART) is omitted.
     */
    private static long fileRead(A68 a68, ElfSection.Fd fd, byte[] buf, long n) {
        long toDo = n;
        int z = 0;
        while (toDo > 0) {
            long bytesRead;

            bytesRead = fd.read(buf, z, toDo);
            if (bytesRead < 0) {
                // read error.
                return -1;
            } else if (bytesRead == 0) {
                // EOF_CHAR
                break;
            }
            toDo -= bytesRead;
            z += (int) bytesRead;
        }

        // return >= 0
        return n - toDo;
    }

    /**
     * a68_is_archive_magic (a68-imports-archive.cc): return true if bytes,
     * which are from the start of the file, are an archive magic number.
     * Ported here only to keep the control flow of a68_find_export_data.
     */
    private static boolean isArchiveMagic(A68 a68, byte[] bytes) {
        String s = new String(bytes, 0, 8, java.nio.charset.StandardCharsets.ISO_8859_1);
        return s.equals("!<arch>\n") || s.equals("!<thin>\n") || s.equals("<bigaf>\n");
    }

    /** Look for export data in the file descriptor FD. */
    private static byte[] findExportData(A68 a68, String filename, ElfSection.Fd fd) {
        // See if we can read this as an object file.
        byte[] exports = findObjectExportData(a68, filename, fd, 0);
        if (exports != null) {
            return exports;
        }

        if (fd.lseek(0, ElfSection.SEEK_SET) < 0) {
            a68.diagnostics.error(null, "lseek %qs failed", filename);
            return null;
        }

        byte[] buf = new byte[A68_EXPORT_MAGIC_LEN];
        long c = fd.read(buf, 0, A68_EXPORT_MAGIC_LEN);
        if (c < A68_EXPORT_MAGIC_LEN) {
            return null;
        }

        if (fd.lseek(0, ElfSection.SEEK_SET) < 0) {
            a68.diagnostics.error(null, "lseek %qs failed", filename);
            return null;
        }

        // Check for a file containing nothing but Algol 68 export data.
        if (buf[0] == 0x0a && buf[1] == 0x68) {
            // read whole file.

            byte[] wbuf;
            long len, nread;

            len = fileSize(a68, fd);
            if (len == -1) {
                a68.diagnostics.error(null, "%<a68_file_size%> failed for %qs", filename);
                return null;
            }

            // Sic: XNEWVEC never returns NULL in C (xmalloc aborts instead).
            wbuf = len > Integer.MAX_VALUE - 8 ? null : new byte[(int) len];
            if (wbuf == null) {
                a68.diagnostics.error(null, "memory allocation failed while reading export data");
                return null;
            }

            nread = fileRead(a68, fd, wbuf, len);
            if (nread < 0) {
                a68.diagnostics.error(null, "read failed while reading export data");
                return null;
            }

            if (nread < len) {
                a68.diagnostics.error(null, "short read while reading export data");
                return null;
            }

            return wbuf;
        }

        // See if we can read this as an archive.
        {
            byte[] abuf = new byte[8];

            if (fd.lseek(0, ElfSection.SEEK_SET) < 0) {
                a68.diagnostics.error(null, "lseek %qs failed", filename);
                return null;
            }

            c = fd.read(abuf, 0, 8);
            if (c < 8) {
                a68.diagnostics.error(null, "read %qs failed", filename);
                return null;
            }

            if (isArchiveMagic(a68, abuf)) {
                // not ported: C returns
                //   a68_find_archive_export_data (filename.c_str (), fd, psize);
                // Reading .a archives (a68-imports-archive.cc) is out of the
                // scope of this port. Returning NULL here makes the caller
                // report that the file contains no export data.
                return null;
            }
        }

        return null;
    }

    /** libiberty lbasename (Unix): the part of FILENAME after the last '/'. */
    private static int lbasenamePos(String filename) {
        return filename.lastIndexOf('/') + 1;
    }

    /**
     * Given *PFILENAME, where *PFILENAME does not exist, try various suffixes.
     * If we find one, set *FILENAME to the one we found. Return the open file
     * descriptor (null where C returns -1).
     */
    private static ElfSection.Fd trySuffixes(A68 a68, Ref<String> pfilename) {
        String filename = pfilename.get() + ".m68";
        ElfSection.Fd fd = ElfSection.Fd.open(filename);
        if (fd != null) {
            pfilename.set(filename);
            return fd;
        }

        int basenamePos = lbasenamePos(pfilename.get());
        String basename = pfilename.get().substring(basenamePos);

        // C: #if TARGET_MACHO, Macos uses .dylib as extension for libraries;
        // #elif TARGET_PECOFF, Windows uses .dll, maybe prefixed with lib.
        // Only the branch for the x86_64-linux target is ported:
        // Other supported systems use .so.
        filename = pfilename.get().substring(0, basenamePos) + "lib" + basename + ".so";
        fd = ElfSection.Fd.open(filename);
        if (fd != null) {
            pfilename.set(filename);
            return fd;
        }

        filename = pfilename.get().substring(0, basenamePos) + "lib" + basename + ".a";
        fd = ElfSection.Fd.open(filename);
        if (fd != null) {
            pfilename.set(filename);
            return fd;
        }

        filename = pfilename.get() + ".o";
        fd = ElfSection.Fd.open(filename);
        if (fd != null) {
            pfilename.set(filename);
            return fd;
        }

        return null;
    }

    /**
     * Prefix of the entries of a library jar that hold export data, one per
     * compiled module packet (see {@link #tryLibraryJar}).
     */
    public static final String LIBRARY_JAR_EXPORTS = "a68j/exports/";

    /**
     * a68j (JVM): the analogue of libFILENAME.so for the JVM is a library
     * jar libFILENAME.jar holding the classes of the modules and, in the
     * entries a68j/exports/*.m68, their export data. As the linker
     * concatenates the .a68_exports sections of the objects of a shared
     * library, the export data of the jar is the concatenation of these
     * entries (in the order of their names). The path of the jar, or null
     * if there is none; tried after FILENAME.m68 and before libFILENAME.so.
     */
    private static java.nio.file.Path libraryJar(String filename) {
        int basenamePos = lbasenamePos(filename);
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(
                    filename.substring(0, basenamePos) + "lib" + filename.substring(basenamePos) + ".jar");
            return java.nio.file.Files.isRegularFile(path) ? path : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The export data of the library jar JAR (see {@link #libraryJar}), or null after an error. */
    private static byte[] readLibraryJar(A68 a68, java.nio.file.Path jar) {
        java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
            List<String> names = new ArrayList<>();
            for (var e = zip.entries(); e.hasMoreElements(); ) {
                String n = e.nextElement().getName();
                if (n.startsWith(LIBRARY_JAR_EXPORTS) && n.endsWith(".m68")) {
                    names.add(n);
                }
            }
            java.util.Collections.sort(names);
            for (String n : names) {
                try (var in = zip.getInputStream(zip.getEntry(n))) {
                    in.transferTo(data);
                }
            }
        } catch (java.io.IOException e) {
            a68.diagnostics.error(null, "cannot read library jar %qs", jar.toString());
            return null;
        }
        if (data.size() == 0) {
            // As for an object file without export data: stop looking.
            a68.diagnostics.error(null, "file %qs exists but does not contain any export data", jar.toString());
            return null;
        }
        String abs = jar.toAbsolutePath().toString();
        if (!a68.libraryJars.contains(abs)) {
            a68.libraryJars.add(abs);
        }
        return data.toByteArray();
    }

    /** Try to find the export data for FILENAME. */
    private static byte[] tryPacketInDirectory(A68 a68, String filename) {
        // a68j (JVM): FILENAME.m68 first, then the library jar libFILENAME.jar,
        // then the native forms (libFILENAME.so, libFILENAME.a, FILENAME.o).
        java.nio.file.Path jar = libraryJar(filename);
        if (jar != null && !java.nio.file.Files.exists(java.nio.file.Paths.get(filename + ".m68"))) {
            return readLibraryJar(a68, jar);
        }
        Ref<String> foundFilename = Ref.box(filename);
        ElfSection.Fd fd = trySuffixes(a68, foundFilename);
        if (fd == null) {
            return null;
        }

        // The export data may not be in this file.
        byte[] exports = findExportData(a68, foundFilename.get(), fd);
        if (exports != null) {
            // Sic: the C code does not close FD here (descriptor leak). It is
            // closed in Java, which has no observable effect.
            fd.close();
            return exports;
        }

        fd.close();

        a68.diagnostics.error(null, "file %qs exists but does not contain any export data",
                foundFilename.get());

        return null;
    }

    /** IS_ABSOLUTE_PATH (Unix) */
    private static boolean isAbsolutePath(String f) {
        return at(f, 0) == '/';
    }

    /** IS_DIR_SEPARATOR (Unix) */
    private static boolean isDirSeparator(char c) {
        return c == '/';
    }

    /**
     * Find import data in FILENAME.
     *
     * <p>This searches the file system for FILENAME, reads exports information
     * from it, and returns a buffer with the exports data. If the file is not
     * found, this function returns NULL.
     *
     * <p>When FILENAME is not an absolute path and does not start with ./ or
     * ../, we use the search path provided by -I and -L options.
     *
     * <p>When FILENAME does start with ./ or ../, we use RELATIVE_IMPORT_PATH
     * as a prefix.
     *
     * <p>When FILENAME does not exist, we try modifying FILENAME to find the
     * file. We use the first of these which exists:
     * <ul>
     * <li>We append ".m68".</li>
     * <li>a68j (JVM): we turn the base of FILENAME into libFILENAME.jar, a
     * library jar ({@link #libraryJar}).</li>
     * <li>We turn the base of FILENAME info libFILENAME.so.</li>
     * <li>We turn the base of FILENAME into libFILENAME.a.</li>
     * <li>We append ".o".</li>
     * </ul>
     *
     * <p>When using a search path, we apply each of these transformations at
     * each entry on the search path before moving on to the next entry. If the
     * file exists, but does not contain Algol 68 export data, we stop; we do not
     * keep looking for another file with the same name later in the search
     * path.
     */
    private static byte[] getPacketExports(A68 a68, String filename, String relativeImportPath) {
        byte[] exports;

        boolean isLocal;
        if (isAbsolutePath(filename)) {
            isLocal = true;
        } else if (at(filename, 0) == '.'
                && (at(filename, 1) == '\0' || isDirSeparator(at(filename, 1)))) {
            isLocal = true;
        } else if (at(filename, 0) == '.'
                && at(filename, 1) == '.'
                && (at(filename, 2) == '\0' || isDirSeparator(at(filename, 2)))) {
            isLocal = true;
        } else {
            isLocal = false;
        }

        String fn = filename;
        if (isLocal && !isAbsolutePath(filename)
                && !relativeImportPath.isEmpty()) {
            if (fn.equals(".")) {
                // A special case.
                fn = relativeImportPath;
            } else if (at(fn, 0) == '.' && at(fn, 1) == '.'
                    && (at(fn, 2) == '\0' || isDirSeparator(at(fn, 2)))) {
                // We are going to join relative_import_path and fn, and it will
                // look like DIR/../PATH. BUt DIR does not necessarily exist in
                // this case, and if it doesn't the use of .. will fail although
                // it shouldn't.
                int index;
                for (index = relativeImportPath.length() - 1;
                     index > 0 && !isDirSeparator(relativeImportPath.charAt(index));
                     index--) {
                    // Empty.
                }
                if (index > 0) {
                    fn = relativeImportPath.substring(0, index) + fn.substring(2);
                } else {
                    fn = relativeImportPath + '/' + fn;
                }
            } else {
                fn = relativeImportPath + '/' + fn;
            }
            isLocal = false;
        }

        if (!isLocal) {
            for (String path : a68.importPaths) {
                if (!path.isEmpty() && path.charAt(path.length() - 1) != '/') {
                    path += '/';
                }
                path += fn;
                exports = tryPacketInDirectory(a68, path);
                if (exports != null) {
                    return exports;
                }
            }
        }

        return tryPacketInDirectory(a68, fn);
    }

    // ------------------------------------------------------------------
    // Decoding of exports data.
    // ------------------------------------------------------------------

    /** The size of the target's pointer type, in bytes (x86-64). */
    private static final int PTR_SIZE = 8;

    /** BYTES_BIG_ENDIAN (x86-64). */
    private static final boolean BYTES_BIG_ENDIAN = false;

    /** The {@code goto decode_error} of the decoding macros below. */
    private static final class DecodeError extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DecodeError() {
            super(null, null, false, false);
        }
    }

    // Collection of decoding helper macros, to be exclusively used in the
    // a68_decode_* functions below. POS is the position (C: pos, a local
    // variable of the calling function) and SIZE the size of DATA.

    /** DINT8 (V): returns V. */
    private static int dint8(A68 a68, byte[] data, long size, long[] pos) {
        if (pos[0] + 1 > size) {
            throw new DecodeError(); // C: goto decode_error
        }
        return data[(int) pos[0]++];
    }

    /** DUINT8 (V): returns V. */
    private static int duint8(A68 a68, byte[] data, long size, long[] pos) {
        if (pos[0] + 1 > size) {
            throw new DecodeError(); // C: goto decode_error
        }
        return data[(int) pos[0]++] & 0xff;
    }

    /** DUINT16 (V): returns V. */
    private static int duint16(A68 a68, byte[] data, long size, long[] pos) {
        if (pos[0] + 2 > size) {
            throw new DecodeError(); // C: goto decode_error
        }
        int p = (int) pos[0];
        int v;
        if (BYTES_BIG_ENDIAN) {
            v = ((data[p] & 0xff) << 8) | (data[p + 1] & 0xff);
        } else {
            v = ((data[p + 1] & 0xff) << 8) | (data[p] & 0xff);
        }
        pos[0] += 2;
        return v;
    }

    /**
     * DOFFSET (V): returns V.
     *
     * <p>Sic: in C each byte is a {@code uint8_t} promoted to {@code int}, so
     * the shift is a 32-bit shift: a byte at index 3 with its top bit set is
     * sign-extended into the upper half of V, and shifts by 32 or more are
     * undefined. ga68 (x86-64) computes them with a 32-bit {@code shl %cl},
     * i.e. with the shift count taken modulo 32, followed by a sign extension
     * ({@code cltq}); Java's {@code int} shift has exactly these semantics.
     * For the small offsets and sizes found in practice the result is the
     * plain little-endian value.
     */
    private static long doffset(A68 a68, byte[] data, long size, long[] pos) {
        if (pos[0] + PTR_SIZE > size) {
            throw new DecodeError(); // C: goto decode_error
        }
        long v = 0;
        int p = (int) pos[0];
        if (BYTES_BIG_ENDIAN) {
            for (int i = 0; i < PTR_SIZE; i++) {
                v = v | (long) ((data[p + i] & 0xff) << ((PTR_SIZE - i - 1) * 8));
            }
        } else {
            for (int i = 0; i < PTR_SIZE; i++) {
                v = v | (long) ((data[p + i] & 0xff) << (i * 8));
            }
        }
        pos[0] += PTR_SIZE;
        return v;
    }

    /**
     * DSTR (V): returns V, NULL for an empty string.
     *
     * <p>The encoded length includes the terminating NUL, which the C code
     * copies along with the string. The Java string ends at the first NUL.
     * Sic: if there is no NUL within the encoded length, C reads past the end
     * of the copied string; here the string just ends there.
     */
    private static String dstr(A68 a68, byte[] data, long size, long[] pos) {
        int len;
        String str = null;
        len = duint16(a68, data, size, pos);
        if (pos[0] + len > size) {
            throw new DecodeError(); // C: goto decode_error
        }
        if (len > 0) {
            int p = (int) pos[0];
            int n = 0;
            while (n < len && data[p + n] != 0) {
                n++;
            }
            str = new String(data, p, n, java.nio.charset.StandardCharsets.ISO_8859_1);
            pos[0] += len;
        }
        return str;
    }

    /**
     * ggc_strdup. Sic: ggc_strdup (NULL) crashes in C (strlen (NULL)); this
     * happens for an extract with an empty (zero length) name.
     */
    private static String ggcStrdup(String s) {
        if (s == null) {
            throw new NullPointerException("ggc_strdup (NULL)");
        }
        return s;
    }

    // Types to denote encoded modes.

    /** struct encoded_triplet */
    private static final class EncodedTriplet {
        long lb;
        long ub;
    }

    /** struct encoded_field */
    private static final class EncodedField {
        long modeOffset;
        String name;
    }

    /** struct encoded_arg */
    private static final class EncodedArg {
        long argModeOffset;
        String argName;
    }

    /**
     * struct encoded_mode. The members of the C union {@code data} are
     * separate fields here; name.sub_offset and flex.sub_offset share storage
     * in C.
     */
    private static final class EncodedMode {
        Moid moid;
        long offset;
        /** uint8_t */
        int kind;
        /** int8_t */
        int sizety;
        // data.name
        long nameSubOffset;
        // data.flex
        long flexSubOffset;
        // data.row
        int rowNdims;
        EncodedTriplet[] rowTriplets;
        long rowSubOffset;
        // data.sct
        int sctNfields;
        EncodedField[] sctFields;
        // data.union_
        int unionNmodes;
        long[] unionModes;
        // data.proc
        long procRetModeOffset;
        int procNargs;
        EncodedArg[] procArgs;
    }

    /** Free the memory used by an encoded mode. */
    private static void encodedModeFree(A68 a68, EncodedMode em) {
        switch (em.kind) {
            case GA68_MODE_ROW:
                em.rowTriplets = null;
                break;
            case GA68_MODE_STRUCT:
                // Note that the field names are installed in moids in
                // encoded_mode_to_moid, so we should not free them.
                em.sctFields = null;
                break;
            case GA68_MODE_UNION:
                em.unionModes = null;
                break;
            case GA68_MODE_PROC:
                // Sic: the argument names were installed in the packs of the
                // moids by complete_encoded_mode, so freeing them leaves the
                // TEXT of those packs dangling in C (use after free). Java
                // strings stay valid: only the references held here are
                // dropped.
                for (int i = 0; i < em.procNargs; i++) {
                    em.procArgs[i].argName = null;
                }
                em.procArgs = null;
                break;
            default:
                break;
        }
        // free (em): garbage collected.
    }

    // A collection of encoded modes indexed by offsets.

    private static final long NO_OFFSET = -1L;

    /**
     * encoded_modes_map_t: {@code hash_map<int_hash<uint64_t,NO_OFFSET>,
     * struct encoded_mode *>}.
     *
     * <p>An emulation of GCC's hash_table (hash-table.h, hash-table.cc): open
     * addressing with double hashing over a prime-sized table, the same
     * expansion policy, and iteration in slot order. The hash of a key is the
     * key truncated to 32 bits (int_hash_base::hash). Entries are never
     * removed. The iteration order of the C code depends on all this.
     */
    private static final class EncodedModesMap implements Iterable<EncodedModesMap.Entry> {
        /** The primes of prime_tab in hash-table.cc. */
        private static final long[] PRIMES = {
            7L, 13L, 31L, 61L, 127L, 251L, 509L, 1021L, 2039L, 4093L, 8191L, 16381L, 32749L, 65521L,
            131071L, 262139L, 524287L, 1048573L, 2097143L, 4194301L, 8388593L, 16777213L, 33554393L,
            67108859L, 134217689L, 268435399L, 536870909L, 1073741789L, 2147483647L, 0xfffffffbL
        };

        /** An element of the map, as seen when iterating (C: auto entry). */
        record Entry(long first, EncodedMode second) {}

        private long[] keys;
        private EncodedMode[] values;
        private int sizePrimeIndex;
        private int size;
        private long nElements;
        private long nDeleted;

        /** hash_map (size_t n) */
        EncodedModesMap(A68 a68, long n) {
            sizePrimeIndex = higherPrimeIndex(n);
            size = (int) PRIMES[sizePrimeIndex];
            allocEntries(size);
            nElements = 0;
            nDeleted = 0;
        }

        private void allocEntries(int n) {
            keys = new long[n];
            java.util.Arrays.fill(keys, NO_OFFSET);
            values = new EncodedMode[n];
        }

        /** hash_table_higher_prime_index */
        private static int higherPrimeIndex(long n) {
            int low = 0;
            int high = PRIMES.length;

            while (low != high) {
                int mid = low + (high - low) / 2;
                if (n > PRIMES[mid]) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }

            // If we've run out of primes, abort.
            Parser.gccAssert(n <= PRIMES[low]);

            return low;
        }

        /** int_hash_base::hash: hashval_t (unsigned 32 bits). */
        private static long hash(long key) {
            return key & 0xffffffffL;
        }

        /** hash_table_mod1 */
        private static long mod1(long hash, int index) {
            return hash % PRIMES[index];
        }

        /** hash_table_mod2 */
        private static long mod2(long hash, int index) {
            return 1 + hash % (PRIMES[index] - 2);
        }

        private boolean isEmpty(int slot) {
            return keys[slot] == NO_OFFSET;
        }

        /** hash_table::too_empty_p */
        private boolean tooEmptyP(long elts) {
            return elts * 8 < size && size > 32;
        }

        /** hash_table::find_empty_slot_for_expand */
        private int findEmptySlotForExpand(long hash) {
            long index = mod1(hash, sizePrimeIndex);
            if (isEmpty((int) index)) {
                return (int) index;
            }

            long hash2 = mod2(hash, sizePrimeIndex);
            for (;;) {
                index += hash2;
                if (index >= size) {
                    index -= size;
                }

                if (isEmpty((int) index)) {
                    return (int) index;
                }
            }
        }

        /** hash_table::expand */
        private void expand() {
            long[] okeys = keys;
            EncodedMode[] ovalues = values;
            int oindex = sizePrimeIndex;
            int osize = size;
            long elts = nElements - nDeleted;

            // Resize only when table after removal of unused elements is
            // either too full or too empty.
            int nindex;
            int nsize;
            if (elts * 2 > osize || tooEmptyP(elts)) {
                nindex = higherPrimeIndex(elts * 2);
                nsize = (int) PRIMES[nindex];
            } else {
                nindex = oindex;
                nsize = osize;
            }

            allocEntries(nsize);
            size = nsize;
            sizePrimeIndex = nindex;
            nElements -= nDeleted;
            nDeleted = 0;

            for (int p = 0; p < osize; p++) {
                if (okeys[p] != NO_OFFSET) {
                    int q = findEmptySlotForExpand(hash(okeys[p]));
                    keys[q] = okeys[p];
                    values[q] = ovalues[p];
                }
            }
        }

        /** hash_table::find_slot_with_hash (..., INSERT) */
        private int findSlotWithHash(long key, long hash) {
            if ((long) size * 3 <= nElements * 4) {
                expand();
            }

            long index = mod1(hash, sizePrimeIndex);
            long hash2 = mod2(hash, sizePrimeIndex);
            if (isEmpty((int) index)) {
                nElements++;
                return (int) index;
            } else if (keys[(int) index] == key) {
                return (int) index;
            }

            for (;;) {
                index += hash2;
                if (index >= size) {
                    index -= size;
                }

                if (isEmpty((int) index)) {
                    nElements++;
                    return (int) index;
                } else if (keys[(int) index] == key) {
                    return (int) index;
                }
            }
        }

        /** hash_map::put */
        void put(long k, EncodedMode v) {
            int e = findSlotWithHash(k, hash(k));
            boolean ins = isEmpty(e);
            if (ins) {
                keys[e] = k;
            }
            values[e] = v;
        }

        /** hash_map::get (C returns a pointer to the value, NULL if absent). */
        EncodedMode get(long k) {
            long hash = hash(k);
            long index = mod1(hash, sizePrimeIndex);
            if (isEmpty((int) index) || keys[(int) index] == k) {
                return isEmpty((int) index) ? null : values[(int) index];
            }
            long hash2 = mod2(hash, sizePrimeIndex);
            for (;;) {
                index += hash2;
                if (index >= size) {
                    index -= size;
                }
                if (isEmpty((int) index) || keys[(int) index] == k) {
                    return isEmpty((int) index) ? null : values[(int) index];
                }
            }
        }

        /** Iteration in slot order, as hash_map::iterator. */
        @Override
        public Iterator<Entry> iterator() {
            return new Iterator<>() {
                private int slot = slide(0);

                private int slide(int s) {
                    while (s < size && isEmpty(s)) {
                        s++;
                    }
                    return s;
                }

                @Override
                public boolean hasNext() {
                    return slot < size;
                }

                @Override
                public Entry next() {
                    if (slot >= size) {
                        throw new NoSuchElementException();
                    }
                    Entry e = new Entry(keys[slot], values[slot]);
                    slot = slide(slot + 1);
                    return e;
                }
            };
        }
    }

    /**
     * {@code *(encoded_modes.get (offset))}. Sic: the C code dereferences the
     * result without checking it, so an offset that does not denote a mode
     * entry crashes ga68; here it throws NullPointerException.
     */
    private static EncodedMode getEncodedMode(EncodedModesMap encodedModes, long offset) {
        EncodedMode em = encodedModes.get(offset);
        if (em == null) {
            throw new NullPointerException("no encoded mode at offset " + Long.toUnsignedString(offset));
        }
        return em;
    }

    /** Complete a encoded mode. */
    private static Moid completeEncodedMode(A68 a68, EncodedModesMap encodedModes, long offset) {
        EncodedMode em = getEncodedMode(encodedModes, offset);
        Moid sub;
        Ref.Box<Pack> pack = Ref.box(null);

        if (em.moid != null) {
            return em.moid;
        }

        switch (em.kind) {
            case GA68_MODE_VOID:
                em.moid = a68.modes.VOID;
                break;
            case GA68_MODE_CHAR:
                em.moid = a68.modes.CHAR;
                break;
            case GA68_MODE_BOOL:
                em.moid = a68.modes.BOOL;
                break;
            case GA68_MODE_STRING:
                em.moid = a68.modes.FLEX_ROW_CHAR;
                break;
            case GA68_MODE_INT:
                switch (em.sizety) {
                    case 0: em.moid = a68.modes.INT; break;
                    case 1: em.moid = a68.modes.LONG_INT; break;
                    case 2: em.moid = a68.modes.LONG_LONG_INT; break;
                    case -1: em.moid = a68.modes.SHORT_INT; break;
                    case -2: em.moid = a68.modes.SHORT_SHORT_INT; break;
                    case 100: em.moid = a68.modes.WORD_INT; break;
                    default:
                        throw Parser.gccUnreachable();
                }
                break;
            case GA68_MODE_BITS:
                switch (em.sizety) {
                    case 0: em.moid = a68.modes.BITS; break;
                    case 1: em.moid = a68.modes.LONG_BITS; break;
                    case 2: em.moid = a68.modes.LONG_LONG_BITS; break;
                    case -1: em.moid = a68.modes.SHORT_BITS; break;
                    case -2: em.moid = a68.modes.SHORT_SHORT_BITS; break;
                    case 100: em.moid = a68.modes.WORD_BITS; break;
                    default:
                        throw Parser.gccUnreachable();
                }
                break;
            case GA68_MODE_BYTES:
                switch (em.sizety) {
                    case 0: em.moid = a68.modes.BYTES; break;
                    case 1: em.moid = a68.modes.LONG_BYTES; break;
                    case 100: em.moid = a68.modes.WORD_BYTES; break;
                    default:
                        throw Parser.gccUnreachable();
                }
                break;
            case GA68_MODE_REAL:
                switch (em.sizety) {
                    case 0: em.moid = a68.modes.REAL; break;
                    case 1: em.moid = a68.modes.LONG_REAL; break;
                    case 2: em.moid = a68.modes.LONG_LONG_REAL; break;
                    case 100: em.moid = a68.modes.WORD_REAL; break;
                    default:
                        throw Parser.gccUnreachable();
                }
                break;
            case GA68_MODE_CMPL:
                switch (em.sizety) {
                    case 0: em.moid = a68.modes.COMPLEX; break;
                    case 1: em.moid = a68.modes.LONG_COMPLEX; break;
                    case 2: em.moid = a68.modes.LONG_LONG_COMPLEX; break;
                    case 100: em.moid = a68.modes.WORD_COMPLEX; break;
                    default:
                        throw Parser.gccUnreachable();
                }
                break;
            case GA68_MODE_NAME:
            case GA68_MODE_FLEX:
                // For recursive declarations.
                em.moid = ModeBuilder.createMode(a68, em.kind == GA68_MODE_NAME ? REF_SYMBOL : FLEX_SYMBOL,
                        0, null, a68.modes.ERROR, null);
                sub = completeEncodedMode(a68, encodedModes,
                        em.kind == GA68_MODE_NAME
                                ? em.nameSubOffset : em.flexSubOffset);
                if (sub == null) {
                    // Free em->moid
                    // Sic: em->moid is left set to the incomplete mode.
                    return null;
                }
                em.moid.sub = sub;
                break;
            case GA68_MODE_ROW:
                // XXX how to convey actual bounds.
                // For recursive declarations.
                em.moid = ModeBuilder.createMode(a68, ROW_SYMBOL, 0, null, a68.modes.ERROR, null);
                sub = completeEncodedMode(a68, encodedModes, em.rowSubOffset);
                if (sub == null) {
                    // Free em->moid
                    // Sic: em->moid is left set to the incomplete mode.
                    return null;
                }
                em.moid.sub = sub;
                em.moid.dim = em.rowNdims;
                break;
            case GA68_MODE_STRUCT:
                // For recursive declarations.
                em.moid = ModeBuilder.createMode(a68, STRUCT_SYMBOL, 0, null, null, null);
                pack.set(null);
                for (int i = 0; i < em.sctNfields; i++) {
                    // Note we have to do this from last field to first field,
                    // because a68_add_mode_to_pack prepends to the list.
                    int index = (em.sctNfields - 1 - i) & 0xffff;
                    String fieldName = em.sctFields[index].name;
                    Moid fieldMoid = completeEncodedMode(a68, encodedModes,
                            em.sctFields[index].modeOffset);
                    if (fieldMoid == null) {
                        // XXX free em->moid
                        em.moid = null;
                        return null;
                    }
                    ModeBuilder.addModeToPack(a68, pack, fieldMoid, fieldName, null);
                }
                em.moid.dim = ModeBuilder.countPackMembers(a68, pack.get());
                em.moid.pack = pack.get();
                break;
            case GA68_MODE_UNION:
                // For recursive declarations.
                em.moid = ModeBuilder.createMode(a68, UNION_SYMBOL, 0, null, null, null);
                pack.set(null);
                for (int i = 0; i < em.unionNmodes; i++) {
                    // The order of union alternatives is irrelevant.
                    int index = i;
                    Moid unitedMoid = completeEncodedMode(a68, encodedModes,
                            em.unionModes[index]);
                    if (unitedMoid == null) {
                        // XXX free em->moid
                        em.moid = null;
                        return null;
                    }
                    ModeBuilder.addModeToPack(a68, pack, unitedMoid, null, null);
                }
                em.moid.dim = ModeBuilder.countPackMembers(a68, pack.get());
                em.moid.pack = pack.get();
                break;
            case GA68_MODE_PROC:
                // For recursive declarations.
                em.moid = ModeBuilder.createMode(a68, PROC_SYMBOL, 0, null, null, null);
                pack.set(null);
                for (int i = 0; i < em.procNargs; i++) {
                    // Note we have to do this from last argument mode to first
                    // argument mode, because a68_add_mode_to_pack prepends to
                    // the list.
                    int index = (em.procNargs - 1 - i) & 0xffff;
                    String argName = em.procArgs[index].argName;
                    Moid argMoid = completeEncodedMode(a68, encodedModes,
                            em.procArgs[index].argModeOffset);
                    if (argMoid == null) {
                        // XXX free em->moid
                        em.moid = null;
                        return null;
                    }
                    ModeBuilder.addModeToPack(a68, pack, argMoid, argName, null);
                }
                em.moid.sub = completeEncodedMode(a68, encodedModes,
                        em.procRetModeOffset);
                if (em.moid.sub == null) {
                    // Free em->moid
                    em.moid = null;
                    return null;
                }
                em.moid.dim = ModeBuilder.countPackMembers(a68, pack.get());
                em.moid.pack = pack.get();
                break;
            default:
                throw Parser.gccUnreachable();
        }

        return em.moid;
    }

    /** printf's %s of a possibly NULL string (glibc prints "(null)"). */
    private static String cs(String s) {
        return s == null ? "(null)" : s;
    }

    /** Dump the contents of an encoded_mode, for debugging purposes. */
    @SuppressWarnings("unused")
    private static void dumpEncodedMode(A68 a68, EncodedMode em) {
        System.out.printf("[%s] kind: %d", Long.toUnsignedString(em.offset), em.kind);
        switch (em.kind) {
            case GA68_MODE_VOID:
                System.out.printf(" void\n");
                break;
            case GA68_MODE_CHAR:
                System.out.printf(" char\n");
                break;
            case GA68_MODE_BOOL:
                System.out.printf(" bool\n");
                break;
            case GA68_MODE_STRING:
                System.out.printf(" string\n");
                break;
            case GA68_MODE_NAME:
                System.out.printf(" name\n");
                System.out.printf("  sub: %s\n", Long.toUnsignedString(em.nameSubOffset));
                break;
            case GA68_MODE_STRUCT:
                System.out.printf(" struct\n");
                System.out.printf("  nfields: %d\n", em.sctNfields);
                for (int i = 0; i < em.sctNfields; i++) {
                    System.out.printf("  %s : [%s]\n",
                            cs(em.sctFields[i].name), Long.toUnsignedString(em.sctFields[i].modeOffset));
                }
                break;
            case GA68_MODE_FLEX:
                System.out.printf(" flex\n");
                // C prints em->data.name.sub_offset, which shares storage
                // with em->data.flex.sub_offset.
                System.out.printf("  sub: %s\n", Long.toUnsignedString(em.flexSubOffset));
                break;
            case GA68_MODE_UNION:
                System.out.printf(" union\n");
                System.out.printf("  nmodes: %d\n", em.unionNmodes);
                System.out.printf(" ");
                for (int i = 0; i < em.unionNmodes; i++) {
                    System.out.printf(" [%s]", Long.toUnsignedString(em.unionModes[i]));
                }
                System.out.printf("\n");
                break;
            case GA68_MODE_PROC:
                System.out.printf(" proc\n");
                System.out.printf("  retmode: [%s]\n", Long.toUnsignedString(em.procRetModeOffset));
                System.out.printf("  nargs: %d\n", em.procNargs);
                for (int i = 0; i < em.procNargs; i++) {
                    System.out.printf("  %s : [%s]\n",
                            cs(em.procArgs[i].argName),
                            Long.toUnsignedString(em.procArgs[i].argModeOffset));
                }
                break;
            case GA68_MODE_ROW:
                System.out.printf(" row\n");
                System.out.printf("  ndims: %d\n", em.rowNdims);
                for (int i = 0; i < em.rowNdims; i++) {
                    System.out.printf("    lb: %s\n", Long.toUnsignedString(em.rowTriplets[i].lb));
                    System.out.printf("    ub: %s\n", Long.toUnsignedString(em.rowTriplets[i].ub));
                }
                System.out.printf("  sub: [%s]\n", Long.toUnsignedString(em.rowSubOffset));
                break;
            default:
                break;
        }
    }

    /** Substitute any reference to mode M in T to R. */
    private static void replaceSubmode(A68 a68, Moid t, Moid m, Moid r) {
        if (t.sub == m) {
            t.sub = r;
        }

        for (Pack p = t.pack; p != null; p = p.next) {
            if (p.type == m) {
                p.type = r;
            }
        }
    }

    /**
     * Substitute mode M with mode R in all modes in MODES_LIST.
     * The entry for M in MODES_LIST is set to NO_MOID.
     */
    private static void replaceEquivalentMode(A68 a68, Moif moif, Moid m, Moid r) {
        // Handle the mode to be replaced.
        List<Moid> modeList = moif.modes;
        for (int i = 0; i < modeList.size(); ++i) {
            if (modeList.get(i) == m) {
                modeList.set(i, null);
            } else if (modeList.get(i) != null) {
                replaceSubmode(a68, modeList.get(i), m, r);
            }
        }

        // Update extracts to reflect the replacement.
        for (Extract e : moif.indicants) {
            if (e.mode == m) {
                e.mode = r;
            }
        }

        for (Extract e : moif.identifiers) {
            if (e.mode == m) {
                e.mode = r;
            }
        }

        for (Extract e : moif.operators) {
            if (e.mode == m) {
                e.mode = r;
            }
        }
    }

    /** Decode a modes table at DATA + POS. */
    private static boolean decodeModes(A68 a68, Moif moif, EncodedModesMap encodedModes,
                                       byte[] data, long posIn, long moifPos,
                                       long[] ppos, Ref<String> errstr) {
        long size = data.length;
        long[] pos = {posIn};
        boolean siga;
        int kind;
        long modeTableSize, modeTableEnd;

        try {
            // Get the size of the modes table.
            modeTableSize = doffset(a68, data, size, pos);
            modeTableEnd = pos[0] + modeTableSize;

            // Decode all the mode entries and fill in encoded_modes.
            while (Long.compareUnsigned(pos[0], modeTableEnd) < 0) {
                int sizety;
                int ndims, nmodes, nargs;
                int nfields;
                long modeOffset = pos[0] - moifPos;
                long sub, retModeOffset;
                EncodedMode encodedMode;

                kind = duint8(a68, data, size, pos);
                encodedMode = new EncodedMode();
                encodedMode.moid = null;
                encodedMode.offset = modeOffset;
                encodedMode.kind = kind;
                encodedMode.sizety = 0;
                switch (kind) {
                    case GA68_MODE_VOID:
                    case GA68_MODE_CHAR:
                    case GA68_MODE_BOOL:
                    case GA68_MODE_STRING:
                        break;
                    case GA68_MODE_INT:
                    case GA68_MODE_REAL:
                    case GA68_MODE_BITS:
                    case GA68_MODE_BYTES:
                    case GA68_MODE_CMPL:
                        sizety = dint8(a68, data, size, pos);
                        encodedMode.sizety = sizety;
                        break;
                    case GA68_MODE_NAME:
                        sub = doffset(a68, data, size, pos);
                        encodedMode.nameSubOffset = sub;
                        break;
                    case GA68_MODE_FLEX:
                        sub = doffset(a68, data, size, pos);
                        encodedMode.flexSubOffset = sub;
                        break;
                    case GA68_MODE_ROW:
                        ndims = duint8(a68, data, size, pos);
                        encodedMode.rowTriplets = new EncodedTriplet[ndims];
                        for (int i = 0; i < ndims; i++) {
                            long lb, ub;
                            lb = doffset(a68, data, size, pos);
                            ub = doffset(a68, data, size, pos);
                            encodedMode.rowTriplets[i] = new EncodedTriplet();
                            encodedMode.rowTriplets[i].lb = lb;
                            encodedMode.rowTriplets[i].ub = ub;
                        }
                        sub = doffset(a68, data, size, pos);
                        encodedMode.rowNdims = ndims;
                        encodedMode.rowSubOffset = sub;
                        break;
                    case GA68_MODE_UNION:
                        // Sic: nmodes is an uint8_t in C, so the 16-bit value
                        // is truncated.
                        nmodes = duint16(a68, data, size, pos) & 0xff;
                        encodedMode.unionNmodes = nmodes;
                        encodedMode.unionModes = new long[nmodes];
                        for (int i = 0; i < nmodes; i++) {
                            long mode;
                            mode = doffset(a68, data, size, pos);
                            encodedMode.unionModes[i] = mode;
                        }
                        break;
                    case GA68_MODE_PROC:
                        retModeOffset = doffset(a68, data, size, pos);
                        nargs = duint8(a68, data, size, pos);
                        encodedMode.procRetModeOffset = retModeOffset;
                        encodedMode.procNargs = nargs;
                        encodedMode.procArgs = new EncodedArg[nargs];
                        for (int i = 0; i < nargs; i++) {
                            long argModeOffset;
                            String argName;
                            argModeOffset = doffset(a68, data, size, pos);
                            argName = dstr(a68, data, size, pos);
                            encodedMode.procArgs[i] = new EncodedArg();
                            encodedMode.procArgs[i].argModeOffset = argModeOffset;
                            encodedMode.procArgs[i].argName = argName;
                        }
                        break;
                    case GA68_MODE_STRUCT:
                        nfields = duint16(a68, data, size, pos);
                        encodedMode.sctNfields = nfields;
                        encodedMode.sctFields = new EncodedField[nfields];
                        for (int i = 0; i < nfields; i++) {
                            long fieldModeOffset;
                            String fieldName;
                            fieldModeOffset = doffset(a68, data, size, pos);
                            fieldName = dstr(a68, data, size, pos);
                            encodedMode.sctFields[i] = new EncodedField();
                            encodedMode.sctFields[i].modeOffset = fieldModeOffset;
                            encodedMode.sctFields[i].name = fieldName;
                        }
                        break;
                    case GA68_MODE_UNKNOWN:
                    default:
                        errstr.set("invalid kind in mode");
                        throw new DecodeError(); // C: goto decode_error
                }

                encodedModes.put(modeOffset, encodedMode);
            }

            // Sanity check.
            if (pos[0] != modeTableEnd) {
                errstr.set("invalid mode table size");
                throw new DecodeError(); // C: goto decode_error
            }

            // Complete all encoded modes.
            // This operation must conform a transitive closure.
            siga = true;
            while (siga) {
                siga = false;
                for (EncodedModesMap.Entry entry : encodedModes) {
                    long offset = entry.first();
                    EncodedMode em = entry.second();

                    if (em.moid == null
                            && completeEncodedMode(a68, encodedModes, offset) != null) {
                        siga = true;
                    }
                }
            }

            // At this point all the encoded modes are complete and they are all
            // associated with moids. Put them in the moif.
            for (EncodedModesMap.Entry entry : encodedModes) {
                EncodedMode em = entry.second();
                Parser.gccAssert(em.moid != null);
                moif.modes.add(em.moid);
            }

            errstr.set(null);
            ppos[0] = pos[0];
            return true;
        } catch (DecodeError e) {
            // C: decode_error:
        }
        if (errstr.get() == null) {
            errstr.set("error decoding mode");
        }
        return false;
    }

    /** Decode an extracts table at DATA + POS. */
    private static boolean decodeExtracts(A68 a68, Moif moif, EncodedModesMap encodedModes,
                                          byte[] data, long posIn,
                                          long[] ppos, Ref<String> errstr) {
        long size = data.length;
        long[] pos = {posIn};
        long extractsTableSize, extractsTableEnd;

        try {
            // Get the size of the extracts table.
            extractsTableSize = doffset(a68, data, size, pos);
            extractsTableEnd = pos[0] + extractsTableSize;

            // Decode all the extracts entries, adding them to MOIF as we go.
            while (Long.compareUnsigned(pos[0], extractsTableEnd) < 0) {
                int marker, prio, variable, inProc;
                long extractSize, extractEnd, modeOffset;
                long mdextraSize;
                String name;

                Extract e = new Extract();

                extractSize = doffset(a68, data, size, pos);
                extractEnd = pos[0] + extractSize;

                marker = duint8(a68, data, size, pos);
                switch (marker) {
                    case GA68_EXTRACT_MODU:
                        name = dstr(a68, data, size, pos);
                        mdextraSize = doffset(a68, data, size, pos);
                        if (mdextraSize != 0) {
                            errstr.set("non-empty mdextra in module extract");
                            throw new DecodeError(); // C: goto decode_error
                        }
                        e.kind = GA68_EXTRACT_MODU;
                        e.symbol = ggcStrdup(name);
                        e.mode = null;
                        e.priority = 0;
                        e.variable = false;
                        e.inProc = false;
                        moif.modules.add(e);
                        break;
                    case GA68_EXTRACT_IDEN:
                        name = dstr(a68, data, size, pos);
                        modeOffset = doffset(a68, data, size, pos);
                        mdextraSize = doffset(a68, data, size, pos);
                        if (mdextraSize != 2) {
                            errstr.set("mdextra size should be 2 in iden extract");
                            throw new DecodeError(); // C: goto decode_error
                        }
                        variable = duint8(a68, data, size, pos);
                        inProc = duint8(a68, data, size, pos);
                        e.kind = GA68_EXTRACT_IDEN;
                        e.symbol = ggcStrdup(name);
                        e.mode = getEncodedMode(encodedModes, modeOffset).moid;
                        e.priority = 0;
                        e.variable = variable != 0;
                        e.inProc = inProc != 0;
                        moif.identifiers.add(e);
                        break;
                    case GA68_EXTRACT_MODE:
                        name = dstr(a68, data, size, pos);
                        modeOffset = doffset(a68, data, size, pos);
                        mdextraSize = doffset(a68, data, size, pos);
                        if (mdextraSize != 0) {
                            errstr.set("non-empty mdextra in indicant extract");
                            throw new DecodeError(); // C: goto decode_error
                        }
                        e.kind = GA68_EXTRACT_MODE;
                        e.symbol = ggcStrdup(name);
                        e.mode = getEncodedMode(encodedModes, modeOffset).moid;
                        e.priority = 0;
                        e.variable = false;
                        e.inProc = false;
                        moif.indicants.add(e);
                        break;
                    case GA68_EXTRACT_PRIO:
                        name = dstr(a68, data, size, pos);
                        prio = duint8(a68, data, size, pos);
                        mdextraSize = doffset(a68, data, size, pos);
                        if (mdextraSize != 0) {
                            errstr.set("non-empty mdextra in prio extract");
                            throw new DecodeError(); // C: goto decode_error
                        }
                        e.kind = GA68_EXTRACT_PRIO;
                        e.symbol = ggcStrdup(name);
                        e.mode = null;
                        e.priority = prio;
                        e.variable = false;
                        e.inProc = false;
                        moif.prios.add(e);
                        break;
                    case GA68_EXTRACT_OPER:
                        name = dstr(a68, data, size, pos);
                        modeOffset = doffset(a68, data, size, pos);
                        mdextraSize = doffset(a68, data, size, pos);
                        if (mdextraSize != 2) {
                            errstr.set("mdextra size should be 2 in oper extract");
                            throw new DecodeError(); // C: goto decode_error
                        }
                        variable = duint8(a68, data, size, pos);
                        inProc = duint8(a68, data, size, pos);
                        e.kind = GA68_EXTRACT_OPER;
                        e.symbol = ggcStrdup(name);
                        e.mode = getEncodedMode(encodedModes, modeOffset).moid;
                        e.priority = 0;
                        e.variable = variable != 0;
                        e.inProc = inProc != 0;
                        moif.operators.add(e);
                        break;
                    default:
                        errstr.set("invalid marker in extract");
                        throw new DecodeError(); // C: goto decode_error
                }

                // Sanity check.
                if (pos[0] != extractEnd) {
                    errstr.set("invalid extract size");
                    throw new DecodeError(); // C: goto decode_error
                }
            }

            // Sanity check.
            if (pos[0] != extractsTableEnd) {
                errstr.set("invalid extracts table size");
                throw new DecodeError(); // C: goto decode_error
            }

            errstr.set(null);
            ppos[0] = pos[0];
            return true;
        } catch (DecodeError e) {
            // C: decode_error:
        }
        if (errstr.get() == null) {
            errstr.set("error decoding extract");
        }
        return false;
    }

    /**
     * Decode the given exports data into moifs, add them to the TOP_MOIF
     * list, and return true. If there is a decoding error then put an
     * explicative message in *ERRSTR and return false.
     */
    private static boolean decodeMoifs(A68 a68, byte[] data, Ref<String> errstr) {
        long size = data.length;
        Moif moifList = null;
        long[] pos = {0};

        int magic1, magic2;
        int version;
        String name, prelude, postlude;

        try {
            while (pos[0] < size) {
                Moif moif = Exports.moifNew(a68, null /* name */);
                moif.next = moifList;
                moifList = moif;

                long moifPos = pos[0];
                magic1 = duint8(a68, data, size, pos);
                magic2 = duint8(a68, data, size, pos);
                if (magic1 != A68_EXPORT_MAGIC1 || magic2 != A68_EXPORT_MAGIC2) {
                    errstr.set("invalid magic number");
                    throw new DecodeError(); // C: goto decode_error
                }

                version = duint16(a68, data, size, pos);
                if (version != 1) {
                    errstr.set("invalid a68 exports version");
                    throw new DecodeError(); // C: goto decode_error
                }

                name = dstr(a68, data, size, pos);
                prelude = dstr(a68, data, size, pos);
                postlude = dstr(a68, data, size, pos);
                moif.name = name;
                moif.prelude = prelude;
                moif.postlude = postlude;

                EncodedModesMap encodedModes = new EncodedModesMap(a68, 16);

                // Decode the modes table.
                // This installs the resulting moids in MOIF.
                if (!decodeModes(a68, moif, encodedModes, data, pos[0], moifPos, pos, errstr)) {
                    throw new DecodeError(); // C: goto decode_error
                }

                // Decode the extracts table.
                // This installs the resulting tags in MOIF.
                if (!decodeExtracts(a68, moif, encodedModes, data, pos[0], pos, errstr)) {
                    throw new DecodeError(); // C: goto decode_error
                }

                // We don't need the encoded modes anymore.
                for (EncodedModesMap.Entry entry : encodedModes) {
                    EncodedMode em = entry.second();
                    encodedModeFree(a68, em);
                }
            }
        } catch (DecodeError e) {
            // C: decode_error:
            if (errstr.get() == null) {
                errstr.set("premature end of data");
            }
            return false;
        }

        // Add the moifs in moif_list to the global list of moifs.
        // XXX error and fail on duplicates?
        {
            Moif end = a68.job.topMoif;
            if (end == null) {
                a68.job.topMoif = moifList;
            } else {
                while (end.next != null) {
                    end = end.next;
                }
                end.next = moifList;
            }
        }

        return true;
    }

    /** a68_open_packet (module) with the default argument FILENAME = NULL. */
    public static Moif openPacket(A68 a68, String module) {
        return openPacket(a68, module, null);
    }

    /**
     * Get a moif with the exports for module named MODULE. If no exports can
     * be found then return NULL.
     *
     * <p>If BASENAME is not NULL then it specifies the basefile of the file to
     * open for the module exports: BASENAME.o, libBASENAME.so, etc. If BASENAME
     * is NULL then the filename is derived from the module name.
     */
    public static Moif openPacket(A68 a68, String module, String basename) {
        // We may have a suitable moif already decoded for the requested
        // module. If so, use it.

        // Sic: a moif with a NULL name (empty name in the exports data) makes
        // strcmp crash in C; here equals throws NullPointerException.
        Moif moif = a68.job.topMoif;
        while (moif != null) {
            if (moif.name.equals(module)) {
                break;
            }
            moif = moif.next;
        }

        // If we didn't find already decoded exports for the requested module,
        // look in the modules location maps to see if there is an entry for
        // MODULE. If there is one, use the specified filename. Otherwise
        // canonicalize the module name to a file name.

        if (moif == null) {
            String filename;
            if (basename != null) {
                filename = basename;
            } else {
                String pfilename = a68.moduleFiles.get(module);
                if (pfilename == null) {
                    // Turn the module indicant in MODULE to lower-case.
                    StringBuilder f = new StringBuilder();
                    int i = 0;
                    for (; i < module.length(); i++) {
                        f.append(CType.toLower(module.charAt(i)));
                    }
                    filename = f.toString();
                } else {
                    filename = pfilename;
                }
            }

            // Try to read exports data in a buffer.
            byte[] exportsData;
            exportsData = getPacketExports(a68, filename, ".");
            if (exportsData == null) {
                return null;
            }

            // Got some data. Decode it into a list of moif.
            Ref<String> errstr = Ref.box(null);
            if (!decodeMoifs(a68, exportsData, errstr)) {
                a68.diagnostics.error(null, "%s", errstr.get());
                return null;
            }

            // The androids we are looking for are likely to be now in the
            // global list.
            moif = a68.job.topMoif;
            while (moif != null && !moif.name.equals(module)) {
                moif = moif.next;
            }
        }

        // If we got a moif, we need to make sure that it doesn't introduce new
        // modes that are equivalent to any mode in the compiler's mode list,
        // nor modes that are equivalent to other modes introduced in this same
        // moif. If it does, we replace the mode everywhere in the moif.

        if (moif != null) {
            List<Moid> knownMoids = new ArrayList<>();

            // As in C, the iteration sees the entries that
            // a68_replace_equivalent_mode sets to NO_MOID.
            for (Moid m : moif.modes) {
                Moid r = ModeBuilder.searchEquivalentMode(a68, m);
                if (r == null) {
                    for (int i = 0; i < knownMoids.size(); ++i) {
                        if (MoidsEquivalence.proveMoidEquivalence(a68, m, knownMoids.get(i))) {
                            r = knownMoids.get(i);
                            break;
                        }
                    }
                }

                if (r == null) {
                    knownMoids.add(m);
                } else if (r != m) {
                    replaceEquivalentMode(a68, moif, m, r);
                }
            }
        }

        return moif;
    }
}
