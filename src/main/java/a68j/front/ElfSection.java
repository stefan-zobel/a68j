// Reading a named section of an ELF object or shared library, replacing the
// parts of libiberty's simple_object (simple-object.c, simple-object-elf.c)
// that ga68's a68-imports.cc uses.
// Original simple_object: Copyright (C) 2010-2026 Free Software Foundation, Inc.,
// written by Ian Lance Taylor.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Minimal replacement for libiberty's {@code simple_object_read}, restricted
 * to little-endian ELF64 (x86-64): section header table plus section name
 * string table.
 *
 * <p>The API mirrors the three libiberty calls used by a68-imports.cc, with
 * the same error reporting conventions (an error message plus an errno value,
 * 0 if no errno is relevant), so that {@link Imports} can be ported literally:
 * <ul>
 * <li>{@code simple_object_start_read} → {@link #startRead}</li>
 * <li>{@code simple_object_find_section} → {@link #findSection}</li>
 * <li>{@code simple_object_release_read} → {@link #releaseRead}</li>
 * </ul>
 * The algorithms and error strings are those of simple-object.c and
 * simple-object-elf.c (simple_object_internal_read, simple_object_elf_match,
 * simple_object_elf_find_sections, find_one_section).
 *
 * <p>Differences from libiberty, all outside the scope of this port: ELF32,
 * big-endian ELF and the other object formats of simple_object (Mach-O, COFF,
 * PE, XCOFF) are not recognised; such files are treated like any other file
 * that is not an object file ("file not recognized"), so the caller pretends
 * no export data was found.
 *
 * <p>{@link Fd} emulates the POSIX file descriptor operations (open, lseek,
 * read, close, errno) on which both libiberty and a68-imports.cc rely.
 */
public final class ElfSection {

    // ------------------------------------------------------------------
    // POSIX file descriptor emulation.
    // ------------------------------------------------------------------

    /** lseek whence values. */
    public static final int SEEK_SET = 0;
    public static final int SEEK_CUR = 1;
    public static final int SEEK_END = 2;

    /** errno values used by the emulation (Linux numbering). */
    public static final int EIO = 5;
    public static final int EINVAL = 22;
    public static final int EISDIR = 21;

    /**
     * An open file, the equivalent of the {@code int fd} returned by
     * {@code open (filename, O_RDONLY | O_BINARY)}.
     *
     * <p>As with POSIX open, opening a readable directory succeeds; reading
     * from it then fails with EISDIR.
     */
    public static final class Fd {
        private final RandomAccessFile raf; // null for a directory
        private long position;
        /** The errno of the last failing operation. */
        public int errno;

        private Fd(RandomAccessFile raf) {
            this.raf = raf;
        }

        /** open (filename, O_RDONLY | O_BINARY); returns null where C returns -1. */
        public static Fd open(String filename) {
            Path path;
            try {
                path = Paths.get(filename);
            } catch (RuntimeException e) {
                return null;
            }
            if (Files.isDirectory(path)) {
                return Files.isReadable(path) ? new Fd(null) : null;
            }
            try {
                return new Fd(new RandomAccessFile(path.toFile(), "r"));
            } catch (FileNotFoundException | SecurityException e) {
                return null;
            }
        }

        /** lseek; returns the new offset, or -1 with {@link #errno} set. */
        public long lseek(long offset, int whence) {
            long base;
            switch (whence) {
                case SEEK_SET -> base = 0;
                case SEEK_CUR -> base = position;
                case SEEK_END -> {
                    try {
                        base = raf == null ? 0 : raf.length();
                    } catch (IOException e) {
                        errno = EIO;
                        return -1;
                    }
                }
                default -> {
                    errno = EINVAL;
                    return -1;
                }
            }
            long n = base + offset;
            if (n < 0) {
                errno = EINVAL;
                return -1;
            }
            position = n;
            return n;
        }

        /**
         * read (fd, buf + off, n): read up to N bytes at the current offset.
         * Returns the number of bytes read (0 at end of file), or -1 with
         * {@link #errno} set. Like read on a regular file, this only returns
         * fewer than N bytes at end of file.
         */
        public long read(byte[] buf, int off, long n) {
            if (raf == null) {
                errno = EISDIR;
                return -1;
            }
            int want = (int) Math.min(n, buf.length - off);
            int got = 0;
            try {
                raf.seek(position);
                while (got < want) {
                    int c = raf.read(buf, off + got, want - got);
                    if (c < 0) {
                        break;
                    }
                    got += c;
                }
            } catch (IOException e) {
                errno = EIO;
                return -1;
            }
            position += got;
            return got;
        }

        /** close (fd) */
        public void close() {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    // Ignored, as in C.
                }
            }
        }
    }

    /** strerror for the errno values produced by {@link Fd}. */
    public static String strerror(int errnum) {
        return switch (errnum) {
            case EIO -> "Input/output error";
            case EISDIR -> "Is a directory";
            case EINVAL -> "Invalid argument";
            default -> "Unknown error " + errnum;
        };
    }

    // ------------------------------------------------------------------
    // simple_object_read for ELF64 little-endian.
    // ------------------------------------------------------------------

    /** SIMPLE_OBJECT_MATCH_HEADER_LEN */
    private static final int SIMPLE_OBJECT_MATCH_HEADER_LEN = 16;

    private static final int EI_MAG0 = 0, EI_MAG1 = 1, EI_MAG2 = 2, EI_MAG3 = 3;
    private static final int EI_CLASS = 4, EI_DATA = 5, EI_VERSION = 6, EI_OSABI = 7;
    private static final int ELFMAG0 = 0x7f, ELFMAG1 = 'E', ELFMAG2 = 'L', ELFMAG3 = 'F';
    private static final int EV_CURRENT = 1;
    private static final int ELFCLASS64 = 2;
    private static final int ELFDATA2LSB = 1;
    private static final int SHN_LORESERVE = 0xff00;
    private static final int SHN_XINDEX = 0xffff;

    /** sizeof (Elf64_External_Ehdr) */
    private static final int EHDR64_SIZE = 64;
    /** sizeof (Elf64_External_Shdr) */
    private static final int SHDR64_SIZE = 64;

    // Field offsets in Elf64_External_Ehdr.
    private static final int E_MACHINE = 18, E_FLAGS = 48, E_SHOFF = 40, E_SHNUM = 60, E_SHSTRNDX = 62;
    // Field offsets in Elf64_External_Shdr.
    private static final int SH_NAME = 0, SH_OFFSET = 24, SH_SIZE = 32, SH_LINK = 40;

    // struct simple_object_read
    private final Fd descriptor;
    private final long offset;
    // struct simple_object_elf_read
    private final int eiData;
    private final int eiClass;
    private final int eiOsabi;
    private final int machine;
    private final int flags;
    private final long shoff;
    private final long shnum;
    private final long shstrndx;

    private ElfSection(Fd descriptor, long offset, int eiData, int eiClass, int eiOsabi, int machine,
                       int flags, long shoff, long shnum, long shstrndx) {
        this.descriptor = descriptor;
        this.offset = offset;
        this.eiData = eiData;
        this.eiClass = eiClass;
        this.eiOsabi = eiOsabi;
        this.machine = machine;
        this.flags = flags;
        this.shoff = shoff;
        this.shnum = shnum;
        this.shstrndx = shstrndx;
    }

    /** simple_object_internal_read */
    private static boolean internalRead(Fd descriptor, long offset, byte[] buffer, long size,
                                        String[] errmsg, int[] err) {
        if (descriptor.lseek(offset, SEEK_SET) < 0) {
            errmsg[0] = "lseek";
            err[0] = descriptor.errno;
            return false;
        }

        int at = 0;
        do {
            long got = descriptor.read(buffer, at, size);
            if (got == 0) {
                break;
            } else if (got > 0) {
                at += (int) got;
                size -= got;
            } else {
                errmsg[0] = "read";
                err[0] = descriptor.errno;
                return false;
            }
        } while (size > 0);

        if (size > 0) {
            errmsg[0] = "file too short";
            err[0] = 0;
            return false;
        }

        return true;
    }

    private static int u16(byte[] b, int at) {
        return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8;
    }

    private static long u32(byte[] b, int at) {
        return (b[at] & 0xffL) | (b[at + 1] & 0xffL) << 8 | (b[at + 2] & 0xffL) << 16 | (b[at + 3] & 0xffL) << 24;
    }

    private static long u64(byte[] b, int at) {
        return u32(b, at) | u32(b, at + 4) << 32;
    }

    /**
     * simple_object_start_read: try to read DESCRIPTOR at OFFSET as an object
     * file. Returns null and sets {@code errmsg[0]}/{@code err[0]} if the file
     * is not recognised. SEGMENT_NAME is unused for ELF.
     */
    public static ElfSection startRead(Fd descriptor, long offset, String segmentName,
                                       String[] errmsg, int[] err) {
        byte[] header = new byte[SIMPLE_OBJECT_MATCH_HEADER_LEN];

        if (!internalRead(descriptor, offset, header, SIMPLE_OBJECT_MATCH_HEADER_LEN, errmsg, err)) {
            return null;
        }

        // Only one format function: simple_object_elf_match.
        ElfSection data = elfMatch(header, descriptor, offset, segmentName, errmsg, err);
        if (data != null) {
            return data;
        }

        errmsg[0] = "file not recognized";
        err[0] = 0;
        return null;
    }

    /** simple_object_elf_match, restricted to ELFCLASS64 / ELFDATA2LSB. */
    private static ElfSection elfMatch(byte[] header, Fd descriptor, long offset, String segmentName,
                                       String[] errmsg, int[] err) {
        if ((header[EI_MAG0] & 0xff) != ELFMAG0
                || (header[EI_MAG1] & 0xff) != ELFMAG1
                || (header[EI_MAG2] & 0xff) != ELFMAG2
                || (header[EI_MAG3] & 0xff) != ELFMAG3
                || (header[EI_VERSION] & 0xff) != EV_CURRENT) {
            errmsg[0] = null;
            err[0] = 0;
            return null;
        }

        int eiData = header[EI_DATA] & 0xff;
        if (eiData != ELFDATA2LSB) {
            // libiberty also accepts ELFDATA2MSB; not supported here.
            errmsg[0] = "unknown ELF endianness";
            err[0] = 0;
            return null;
        }

        int eiClass = header[EI_CLASS] & 0xff;
        if (eiClass != ELFCLASS64) {
            // libiberty also accepts ELFCLASS32; not supported here.
            errmsg[0] = "unrecognized ELF size";
            err[0] = 0;
            return null;
        }

        byte[] ehdr = new byte[EHDR64_SIZE];
        if (!internalRead(descriptor, offset, ehdr, EHDR64_SIZE, errmsg, err)) {
            return null;
        }

        int eiOsabi = header[EI_OSABI] & 0xff;
        int machine = u16(ehdr, E_MACHINE);
        int flags = (int) u32(ehdr, E_FLAGS);
        long shoff = u64(ehdr, E_SHOFF);
        long shnum = u16(ehdr, E_SHNUM);
        long shstrndx = u16(ehdr, E_SHSTRNDX);

        if ((shnum == 0 || shstrndx == SHN_XINDEX) && shoff != 0) {
            byte[] shdr = new byte[SHDR64_SIZE];

            // Object file has more than 0xffff sections.

            if (!internalRead(descriptor, offset + shoff, shdr, SHDR64_SIZE, errmsg, err)) {
                return null;
            }

            if (shnum == 0) {
                // Sic: stored in an unsigned int in libiberty.
                shnum = u64(shdr, SH_SIZE) & 0xffffffffL;
            }

            if (shstrndx == SHN_XINDEX) {
                shstrndx = u32(shdr, SH_LINK);

                // Versions of the GNU binutils between 2.12 and 2.18 did not
                // handle objects with more than SHN_LORESERVE sections
                // correctly. All large section indexes were offset by 0x100.
                // There is more information at https://sourceware.org/PR5900 .
                // Fortunately these object files are easy to detect, as the
                // GNU binutils always put the section header string table
                // near the end of the list of sections. Thus if the section
                // header string table index is larger than the number of
                // sections, then we know we have to subtract 0x100 to get the
                // real section index.
                if (shstrndx >= shnum && shstrndx >= SHN_LORESERVE + 0x100) {
                    shstrndx -= 0x100;
                }
            }
        }

        if (shstrndx >= shnum) {
            errmsg[0] = "invalid ELF shstrndx >= shnum";
            err[0] = 0;
            return null;
        }

        if (shstrndx == 0) {
            errmsg[0] = "invalid ELF shstrndx == 0";
            err[0] = 0;
            return null;
        }

        return new ElfSection(descriptor, offset, eiData, eiClass, eiOsabi, machine, flags, shoff, shnum,
                shstrndx);
    }

    /** Callback of {@link #findSections} (C: int (*pfn) (void *, const char *, off_t, off_t)). */
    private interface SectionCallback {
        /** Return false to stop the iteration. */
        boolean section(String name, long offset, long length);
    }

    /**
     * simple_object_elf_find_sections: call PFN for each section (except
     * section 0). Returns null on success, an error message otherwise.
     */
    private String findSections(SectionCallback pfn, int[] err) {
        String[] errmsg = new String[1];
        int shdrSize = SHDR64_SIZE;

        // Read the section headers. We skip section 0, which is not a useful
        // section.

        long tableSize = shdrSize * (shnum - 1);
        if (tableSize > Integer.MAX_VALUE - 8) {
            // xmalloc would fail and abort in C.
            throw new OutOfMemoryError("ELF section header table too large");
        }
        byte[] shdrs = new byte[(int) tableSize];

        if (!internalRead(descriptor, offset + shoff + shdrSize, shdrs, tableSize, errmsg, err)) {
            return errmsg[0];
        }

        // Read the section names.

        int shstrhdr = (int) ((shstrndx - 1) * shdrSize);
        long nameSize = u64(shdrs, shstrhdr + SH_SIZE);
        long shstroff = u64(shdrs, shstrhdr + SH_OFFSET);
        if (nameSize < 0 || nameSize > Integer.MAX_VALUE - 8) {
            // xmalloc would fail and abort in C.
            throw new OutOfMemoryError("ELF section name table too large");
        }
        byte[] names = new byte[(int) nameSize];
        if (!internalRead(descriptor, offset + shstroff, names, nameSize, errmsg, err)) {
            return errmsg[0];
        }

        for (long i = 1; i < shnum; ++i) {
            int shdr = (int) ((i - 1) * shdrSize);
            long shName = u32(shdrs, shdr + SH_NAME);
            if (shName >= nameSize) {
                err[0] = 0;
                return "ELF section name out of range";
            }

            // Sic: in C the name is read up to the NUL terminator, possibly
            // beyond the end of the names buffer; here it stops at the end.
            int end = (int) shName;
            while (end < names.length && names[end] != 0) {
                end++;
            }
            String name = new String(names, (int) shName, end - (int) shName, StandardCharsets.ISO_8859_1);
            long sectionOffset = u64(shdrs, shdr + SH_OFFSET);
            long length = u64(shdrs, shdr + SH_SIZE);

            if (!pfn.section(name, sectionOffset, length)) {
                break;
            }
        }

        return null;
    }

    /**
     * simple_object_find_section: look for section NAME. Returns 1 and sets
     * {@code offset[0]}/{@code length[0]} if found. Returns 0 if not found
     * ({@code errmsg[0]} null) or on error ({@code errmsg[0]} set).
     */
    public static int findSection(ElfSection sobj, String name, long[] offset, long[] length,
                                  String[] errmsg, int[] err) {
        // struct find_one_section_data / find_one_section
        boolean[] found = {false};

        errmsg[0] = sobj.findSections((sname, soffset, slength) -> {
            if (!sname.equals(name)) {
                return true;
            }

            offset[0] = soffset;
            length[0] = slength;
            found[0] = true;

            // Stop iteration.
            return false;
        }, err);
        if (errmsg[0] != null) {
            return 0;
        }
        if (!found[0]) {
            return 0;
        }
        return 1;
    }

    /** simple_object_release_read. The descriptor is not closed, as in libiberty. */
    public static void releaseRead(ElfSection sobj) {
        // Nothing to free in Java.
    }

    // ------------------------------------------------------------------
    // Convenience entry point.
    // ------------------------------------------------------------------

    /**
     * Return the contents of section SECTION_NAME of the ELF64 file FILENAME,
     * or null if the file is not a recognised object file or has no such
     * section. I/O and format errors are reported as IOException.
     */
    public static byte[] readSection(String filename, String sectionName) throws IOException {
        Fd fd = Fd.open(filename);
        if (fd == null) {
            throw new IOException(filename + ": cannot open");
        }
        try {
            String[] errmsg = new String[1];
            int[] err = {0};
            ElfSection sobj = startRead(fd, 0, null, errmsg, err);
            if (sobj == null) {
                return null;
            }
            long[] off = new long[1];
            long[] len = new long[1];
            int found = findSection(sobj, sectionName, off, len, errmsg, err);
            releaseRead(sobj);
            if (found == 0) {
                if (errmsg[0] != null) {
                    throw new IOException(filename + ": " + errmsg[0]
                            + (err[0] != 0 ? ": " + strerror(err[0]) : ""));
                }
                return null;
            }
            if (len[0] < 0 || len[0] > Integer.MAX_VALUE - 8) {
                throw new IOException(filename + ": section too large");
            }
            byte[] buf = new byte[(int) len[0]];
            if (fd.lseek(off[0], SEEK_SET) < 0) {
                throw new IOException(filename + ": lseek: " + strerror(fd.errno));
            }
            long c = fd.read(buf, 0, len[0]);
            if (c < 0) {
                throw new IOException(filename + ": read: " + strerror(fd.errno));
            }
            if (c < len[0]) {
                throw new IOException(filename + ": short read");
            }
            return buf;
        } finally {
            fd.close();
        }
    }
}
