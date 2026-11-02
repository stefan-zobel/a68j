// a68j runtime: the POSIX module (as libga68's ga68-posix.c).
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

import java.io.IOException;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

/**
 * The procedures and constants of the POSIX module. File descriptors 0, 1
 * and 2 are standard input, output and error; files opened by fopen and
 * fcreate get descriptors from 3 up.
 *
 * <p>A file is a {@link FileChannel}, because its open options correspond
 * to the flags of open(2): READ, WRITE and TRUNCATE_EXISTING never create a
 * file, and a channel opened for one direction refuses the other. So fopen
 * and fcreate behave as libga68's open(path, flags) and creat(path, mode):
 * fopen never creates a file, the default mode tries read-write, then
 * read-only, then write-only, and a descriptor of fcreate is write-only.
 *
 * <p>errno follows libga68 exactly. libga68 keeps its own variable, which
 * fopen, fcreate, fclose, fputs (and so puts) and lseek copy from the C errno
 * after the system call, whether it failed or not; fsize sets it only when
 * fstat fails. The C errno is set by every failing system call, including
 * the read of fgetc and the write of fputc, and is never cleared. So errno
 * keeps the number of the last error: a successful call does not reset it to
 * 0, and after a failing fgetc the error shows only at the next copying
 * call. {@link #sysErrno} plays the part of the C errno. The error numbers
 * and messages are those of Linux.
 *
 * <p>A string argument with a character that has no UTF-8 encoding (a
 * surrogate, or the EOF character) cannot be converted: fopen and fcreate
 * then fail with EFAULT, fputs and puts write nothing, return 0 and set
 * errno to EILSEQ, and getenv returns the empty string.
 *
 * <p>fsize and lseek also work on descriptors 0 to 2, as fstat and lseek
 * do: a redirected file has its size and position, a pipe or a terminal
 * size 0 and no position (ESPIPE). Standard input is buffered here, and
 * lseek on it accounts for the bytes read ahead. fcreate gives a new file
 * the permission bits of its mode less the umask, as creat.
 */
public final class Posix {
    private Posix() {}

    /** A POSIX procedure used as a value. */
    public static Proc procValue(String method) {
        return Std.reflectProc(Posix.class, method);
    }

    private static final int EIO = 5, EBADF = 9, EFAULT = 14, EINVAL = 22, EILSEQ = 84;

    /** The error numbers known here and their messages (strerror of glibc). */
    private static final Object[][] ERRORS = {
        {0, "Success"},
        {1, "Operation not permitted"},
        {2, "No such file or directory"},
        {EIO, "Input/output error"},
        {EBADF, "Bad file descriptor"},
        {13, "Permission denied"},
        {EFAULT, "Bad address"},
        {17, "File exists"},
        {20, "Not a directory"},
        {21, "Is a directory"},
        {EINVAL, "Invalid argument"},
        {24, "Too many open files"},
        {27, "File too large"},
        {28, "No space left on device"},
        {29, "Illegal seek"},
        {30, "Read-only file system"},
        {32, "Broken pipe"},
        {36, "File name too long"},
        {40, "Too many levels of symbolic links"},
        {EILSEQ, "Invalid or incomplete multibyte or wide character"},
    };

    /** errno as the program sees it (libga68's _libga68_errno). */
    private static int errno;
    /** The C errno: the error of the last failing system call. */
    private static int sysErrno;
    private static final Map<Integer, FileChannel> FILES = new HashMap<>();
    private static int nextFd = 3;
    /**
     * Standard input, read through a buffer of our own (not System.in), so
     * that lseek on descriptor 0 knows how many bytes were read ahead.
     */
    private static final FileInputStream STDIN = new FileInputStream(FileDescriptor.in);
    private static final byte[] INBUF = new byte[8192];
    private static int inPos, inLim;
    /** Channels of descriptors 0 to 2 for fsize and lseek (fstat and lseek of the descriptor). */
    private static final FileChannel[] STD = new FileChannel[3];
    private static final ByteBuffer ONE = ByteBuffer.allocate(1);

    public static int stdin() { return 0; }
    public static int stdout() { return 1; }
    public static int stderr() { return 2; }
    public static int fileodefault() { return 0x99999999; }
    public static int fileordonly() { return 0x0; }
    public static int fileowronly() { return 0x1; }
    public static int fileordwr() { return 0x2; }
    public static int fileotrunc() { return 0x8; }
    public static int seekcur() { return 0; }
    public static int seekend() { return 1; }
    public static int seekset() { return 2; }

    public static int errno() { return errno; }

    /** argc: the program name counts as argument 1. */
    public static int argc() { return 1 + Rt.programArgs.size(); }

    public static Row argv(int n) {
        if (n < 1 || n > argc()) {
            return Row.ofString("");
        }
        return Row.ofString(n == 1 ? Rt.programName : Rt.programArgs.get(n - 2));
    }

    public static Row strerror(int e) {
        for (Object[] entry : ERRORS) {
            if ((int) entry[0] == e) {
                return Row.ofString((String) entry[1]);
            }
        }
        return Row.ofString("Unknown error " + e);
    }

    /**
     * The Java string of S, or null if S holds a character that has no UTF-8
     * encoding (a surrogate, or a value outside 0 .. 16r10ffff such as the
     * EOF character): libga68 converts strings with _libga68_u32_to_u8, which
     * fails with EILSEQ for these.
     */
    private static String utf8String(Row s) {
        StringBuilder sb = new StringBuilder();
        for (int c : s.codePoints()) {
            if (c < 0 || c > 0x10ffff || (c >= 0xd800 && c < 0xe000)) {
                return null;
            }
            sb.appendCodePoint(c);
        }
        return sb.toString();
    }

    /**
     * perror. A prefix that cannot be converted is left out (libga68 would
     * write from a null pointer).
     */
    public static void perror(Row s) {
        Rt.OUT.flush();
        String prefix = utf8String(s);
        System.err.print((prefix == null ? "" : prefix) + ": " + strerror(errno).asString() + "\n");
        System.err.flush();
    }

    /**
     * The error number of a failed operation. The JDK reports the error of a
     * system call on Linux with the text of strerror (as the reason of a
     * FileSystemException, or as the message of an IOException), so the
     * number is found by that text; a few exception classes give it directly.
     */
    private static int errnoOf(Exception e) {
        if (e instanceof NoSuchFileException) {
            return 2;
        }
        if (e instanceof AccessDeniedException) {
            return 13;
        }
        if (e instanceof FileAlreadyExistsException) {
            return 17;
        }
        if (e instanceof InvalidPathException) {
            return 2;
        }
        String text = e instanceof FileSystemException f ? f.getReason() : e.getMessage();
        if (text != null) {
            for (Object[] entry : ERRORS) {
                if (text.equals(entry[1]) || text.endsWith(": " + entry[1])) {
                    return (int) entry[0];
                }
            }
        }
        return EIO;
    }

    /** open(2) with the given options; errno is copied afterwards, as in libga68. */
    private static synchronized int open(String path, OpenOption... options) {
        return open(path, null, options);
    }

    /**
     * open(2) with the given options and, if PERMS is not null, with these
     * permission bits for a file that is created (the kernel applies the
     * umask, as for creat). On a file system without POSIX permissions the
     * file is created without them.
     */
    private static synchronized int open(String path, Set<PosixFilePermission> perms, OpenOption... options) {
        try {
            Path p = Path.of(path);
            Set<OpenOption> opts = Set.of(options);
            FileChannel ch;
            if (perms == null) {
                ch = FileChannel.open(p, opts);
            } else {
                FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
                try {
                    ch = FileChannel.open(p, opts, attr);
                } catch (UnsupportedOperationException e) {
                    ch = FileChannel.open(p, opts);
                }
            }
            FILES.put(nextFd, ch);
            return nextFd++;
        } catch (IOException | InvalidPathException | SecurityException e) {
            sysErrno = errnoOf(e);
            return -1;
        } finally {
            errno = sysErrno;
        }
    }

    private static final OpenOption READ = StandardOpenOption.READ, WRITE = StandardOpenOption.WRITE,
            TRUNC = StandardOpenOption.TRUNCATE_EXISTING;

    /**
     * open(2) or creat(2) with a path that cannot be converted: libga68
     * passes a null pointer, and the call fails with EFAULT.
     */
    private static int badPath() {
        sysErrno = EFAULT;
        errno = sysErrno;
        return -1;
    }

    public static synchronized int fopen(Row path, int flags) {
        String p = utf8String(path);
        if (p == null) {
            return badPath();
        }
        if (flags == fileodefault()) {
            int fd = open(p, READ, WRITE);
            if (fd < 0) {
                fd = open(p, READ);
            }
            if (fd < 0) {
                fd = open(p, WRITE);
            }
            return fd;
        }
        // libga68 ORs O_WRONLY, O_RDWR and O_TRUNC by the bits of FLAGS.
        // O_RDONLY is 0; access mode 3 is opened read-write here.
        int access = flags & 0x3;
        boolean trunc = (flags & 0x8) != 0;
        if (access == 0x0) {
            if (trunc) {
                // Linux truncates with O_RDONLY | O_TRUNC, which needs write
                // permission; a channel opened for reading ignores
                // TRUNCATE_EXISTING, so the file is truncated first.
                int t = open(p, WRITE, TRUNC);
                if (t < 0) {
                    return -1;
                }
                closeQuietly(t);
            }
            return open(p, READ);
        }
        if (access == 0x1) {
            return trunc ? open(p, WRITE, TRUNC) : open(p, WRITE);
        }
        return trunc ? open(p, READ, WRITE, TRUNC) : open(p, READ, WRITE);
    }

    private static void closeQuietly(int fd) {
        try {
            FILES.remove(fd).close();
        } catch (IOException e) {
            // The descriptor was not used.
        }
    }

    /**
     * creat(2): create or truncate the file, write-only. A new file gets the
     * permission bits 0777 of MODE less the umask (the kernel applies it
     * when the file is created), and then the bits 07000 of MODE
     * (set-user-ID, set-group-ID, sticky), which creat applies as well, by
     * the "unix" attribute view where the platform has it. An existing file
     * keeps its permissions.
     */
    public static synchronized int fcreate(Row path, int mode) {
        String p = utf8String(path);
        if (p == null) {
            return badPath();
        }
        boolean existed;
        try {
            existed = Files.exists(Path.of(p));
        } catch (InvalidPathException | SecurityException e) {
            existed = true;
        }
        int fd = open(p, permissions(mode), WRITE, StandardOpenOption.CREATE, TRUNC);
        if (fd >= 0 && !existed && (mode & 07000) != 0) {
            try {
                Path q = Path.of(p);
                int now = (Integer) Files.getAttribute(q, "unix:mode") & 0777;
                Files.setAttribute(q, "unix:mode", now | (mode & 07000));
            } catch (IOException | RuntimeException e) {
                // No "unix" view, or not permitted: the file keeps the bits 0777.
            }
        }
        return fd;
    }

    /** The permissions of the bits 0777 of MODE. */
    private static Set<PosixFilePermission> permissions(int mode) {
        Set<PosixFilePermission> s = EnumSet.noneOf(PosixFilePermission.class);
        PosixFilePermission[] bits = {
            PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ,
            PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ,
            PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ,
        };
        for (int i = 0; i < bits.length; i++) {
            if ((mode & (1 << i)) != 0) {
                s.add(bits[i]);
            }
        }
        return s;
    }

    public static int fconnect(Row host, int port) {
        sysErrno = EINVAL;
        errno = EINVAL;
        return -1;
    }

    public static synchronized int fclose(int fd) {
        FileChannel f = FILES.remove(fd);
        try {
            if (f == null) {
                if (fd >= 0 && fd <= 2) {
                    return 0;
                }
                sysErrno = EBADF;
                return -1;
            }
            f.close();
            return 0;
        } catch (IOException e) {
            sysErrno = errnoOf(e);
            return -1;
        } finally {
            errno = sysErrno;
        }
    }

    /**
     * The channel of descriptor FD, or null. For 0 to 2 it is a channel of
     * the standard stream (its size is that of fstat, 0 for a pipe or a
     * terminal; its position that of lseek, which fails for them); written
     * output is flushed first.
     */
    private static FileChannel channel(int fd) {
        if (fd < 0 || fd > 2) {
            return FILES.get(fd);
        }
        if (fd == 1) {
            Rt.OUT.flush();
        } else if (fd == 2) {
            Rt.OUT.flush();
            System.err.flush();
        }
        if (STD[fd] == null) {
            STD[fd] = switch (fd) {
                case 0 -> STDIN.getChannel();
                case 1 -> new FileOutputStream(FileDescriptor.out).getChannel();
                default -> new FileOutputStream(FileDescriptor.err).getChannel();
            };
        }
        return STD[fd];
    }

    /** fsize (fstat): the size of the file; errno is set only when the size is not known. */
    public static synchronized long fsize(int fd) {
        FileChannel f = channel(fd);
        if (f == null) {
            sysErrno = EBADF;
            errno = EBADF;
            return -1;
        }
        try {
            return f.size();
        } catch (IOException e) {
            sysErrno = errnoOf(e);
            errno = sysErrno;
            return -1;
        }
    }

    /**
     * lseek: WHENCE is seek_cur (0), seek_end (1) or seek_set (2) of the
     * POSIX module. As in Linux, a pipe or a terminal fails with ESPIPE
     * whatever the arguments, and a negative position with EINVAL. For
     * descriptor 0 the position is that of the next byte the program
     * reads, so the bytes read ahead into the buffer are subtracted, and
     * a successful seek drops them. A device that ignores the position,
     * such as /dev/null, accepts any offset and yields 0, as in Linux.
     */
    public static synchronized long lseek(int fd, long offset, int whence) {
        FileChannel f = channel(fd);
        try {
            if (f == null) {
                sysErrno = EBADF;
                return -1;
            }
            long raw = f.position();
            long cur = fd == 0 ? raw - (inLim - inPos) : raw;
            long pos = switch (whence) {
                case 0 -> cur + offset;
                case 1 -> f.size() + offset;
                case 2 -> offset;
                default -> -1;
            };
            if (pos < 0) {
                // A device that ignores the position (/dev/null, /dev/zero)
                // accepts any offset and yields 0; a file fails with EINVAL.
                f.position(raw + 1);
                if (f.position() != raw) {
                    f.position(raw);
                    sysErrno = EINVAL;
                    return -1;
                }
                pos = 0;
            }
            if (fd == 0) {
                inPos = inLim = 0;
            }
            f.position(pos);
            return f.position();
        } catch (IOException e) {
            sysErrno = errnoOf(e);
            return -1;
        } finally {
            errno = sysErrno;
        }
    }

    // ------------------------------------------------------------------
    // Characters and strings (UTF-8 on the descriptors).
    // ------------------------------------------------------------------

    /** read(2) of one byte: the byte, or -1 at end of file or on an error (which sets sysErrno). */
    private static synchronized int readByte(int fd) {
        try {
            if (fd == 0) {
                Rt.OUT.flush();
                if (inPos == inLim) {
                    int n = STDIN.read(INBUF);
                    if (n <= 0) {
                        return -1;
                    }
                    inPos = 0;
                    inLim = n;
                }
                return INBUF[inPos++] & 0xff;
            }
            FileChannel f = FILES.get(fd);
            if (f == null) {
                sysErrno = EBADF;
                return -1;
            }
            ONE.clear();
            return f.read(ONE) == 1 ? ONE.get(0) & 0xff : -1;
        } catch (NonReadableChannelException e) {
            sysErrno = EBADF;
            return -1;
        } catch (IOException e) {
            sysErrno = errnoOf(e);
            return -1;
        }
    }

    /** write(2) of all of B; false on an error (which sets sysErrno). */
    private static synchronized boolean write(int fd, byte[] b) {
        try {
            switch (fd) {
                case 1 -> Rt.OUT.write(b);
                case 2 -> {
                    Rt.OUT.flush();
                    System.err.write(b);
                    System.err.flush();
                }
                default -> {
                    FileChannel f = FILES.get(fd);
                    if (f == null) {
                        sysErrno = EBADF;
                        return false;
                    }
                    ByteBuffer buf = ByteBuffer.wrap(b);
                    while (buf.hasRemaining()) {
                        f.write(buf);
                    }
                }
            }
            return true;
        } catch (NonWritableChannelException e) {
            sysErrno = EBADF;
            return false;
        } catch (IOException e) {
            sysErrno = errnoOf(e);
            return false;
        }
    }

    /** fgetc: one UTF-8 encoded character; EOF gives -1, a bad sequence U+FFFD. */
    public static int fgetc(int fd) {
        int c = readByte(fd);
        if (c < 0) {
            return -1;
        }
        int more = c < 128 ? 0 : c < 224 ? 1 : c < 240 ? 2 : 3;
        byte[] u = new byte[more + 1];
        u[0] = (byte) c;
        for (int i = 0; i < more; i++) {
            int d = readByte(fd);
            if (d < 0) {
                return -1;
            }
            u[i + 1] = (byte) d;
        }
        String s = new String(u, StandardCharsets.UTF_8);
        int cp = s.codePointAt(0);
        return s.length() != Character.charCount(cp) ? 0xfffd : cp;
    }

    public static int getchar() { return fgetc(0); }

    /** fputc: errno is not copied (as in libga68). */
    public static int fputc(int fd, int c) {
        if (c < 0 || c > 0x10ffff || (c >= 0xd800 && c < 0xe000)) {
            return -1;
        }
        return write(fd, new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8)) ? c : -1;
    }

    public static int putchar(int c) { return fputc(1, c); }

    /**
     * fputs: the number of bytes written, or 0 on an error; errno is copied
     * afterwards. A string with a character that has no UTF-8 encoding (such
     * as the EOF character) writes nothing, returns 0 and sets errno to
     * EILSEQ. (libga68 then calls write with a null pointer and an
     * uninitialized length, so its result there is undefined.)
     */
    public static synchronized int fputs(int fd, Row s) {
        String str = utf8String(s);
        if (str == null) {
            sysErrno = EILSEQ;
            errno = sysErrno;
            return 0;
        }
        byte[] b = str.getBytes(StandardCharsets.UTF_8);
        boolean ok = write(fd, b);
        errno = sysErrno;
        return ok ? b.length : 0;
    }

    /** puts: write a string to standard output. */
    public static void puts(Row s) {
        fputs(1, s);
    }

    /** fgets: NCHARS characters (or up to EOF), or a line including its newline if NCHARS <= 0. */
    public static Ref fgets(int fd, int nchars) {
        StringBuilder sb = new StringBuilder();
        if (nchars > 0) {
            while (sb.codePointCount(0, sb.length()) < nchars) {
                int c = fgetc(fd);
                if (c == -1) {
                    break;
                }
                sb.appendCodePoint(c);
            }
        } else {
            while (true) {
                int c = fgetc(fd);
                if (c == -1) {
                    break;
                }
                sb.appendCodePoint(c);
                if (c == '\n') {
                    break;
                }
            }
        }
        return new Cell(Row.ofString(sb.toString()));
    }

    public static Ref gets(int nchars) { return fgets(0, nchars); }

    public static void posixexit(int status) {
        Rt.exit(status);
    }

    public static Row getenv(Row name) {
        String n = utf8String(name);
        String v = n == null || n.isEmpty() ? null : System.getenv(n);
        return Row.ofString(v == null ? "" : v);
    }
}
