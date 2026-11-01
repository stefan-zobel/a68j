// a68j runtime: the POSIX module (as libga68's ga68-posix.c). GPLv3+.
package a68j.rt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
 */
public final class Posix {
    private Posix() {}

    /** A POSIX procedure used as a value. */
    public static Proc procValue(String method) {
        return Std.reflectProc(Posix.class, method);
    }

    private static final int EIO = 5, EBADF = 9, EINVAL = 22;

    /** The error numbers known here and their messages (strerror of glibc). */
    private static final Object[][] ERRORS = {
        {0, "Success"},
        {1, "Operation not permitted"},
        {2, "No such file or directory"},
        {EIO, "Input/output error"},
        {EBADF, "Bad file descriptor"},
        {13, "Permission denied"},
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
    };

    /** errno as the program sees it (libga68's _libga68_errno). */
    private static int errno;
    /** The C errno: the error of the last failing system call. */
    private static int sysErrno;
    private static final Map<Integer, FileChannel> FILES = new HashMap<>();
    private static int nextFd = 3;
    private static final InputStream IN = System.in;
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

    public static void perror(Row s) {
        Rt.OUT.flush();
        System.err.print(s.asString() + ": " + strerror(errno).asString() + "\n");
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
        try {
            FileChannel ch = FileChannel.open(Path.of(path), options);
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

    public static synchronized int fopen(Row path, int flags) {
        String p = path.asString();
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
     * creat(2): create or truncate the file, write-only. The permission bits
     * MODE are not applied: the file gets the default permissions.
     */
    public static synchronized int fcreate(Row path, int mode) {
        return open(path.asString(), WRITE, StandardOpenOption.CREATE, TRUNC);
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

    /** fsize: the size of the file; errno is set only when the size is not known. */
    public static synchronized long fsize(int fd) {
        FileChannel f = FILES.get(fd);
        if (f == null) {
            if (fd >= 0 && fd <= 2) {
                return 0;
            }
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

    public static synchronized long lseek(int fd, long offset, int whence) {
        FileChannel f = FILES.get(fd);
        try {
            if (f == null) {
                sysErrno = EBADF;
                return -1;
            }
            long pos = switch (whence) {
                case 0 -> f.position() + offset;
                case 1 -> f.size() + offset;
                case 2 -> offset;
                default -> -1;
            };
            if (pos < 0) {
                sysErrno = EINVAL;
                return -1;
            }
            f.position(pos);
            return pos;
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
                return IN.read();
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

    /** fputs: the number of bytes written, or 0 on an error; errno is copied afterwards. */
    public static synchronized int fputs(int fd, Row s) {
        byte[] b = s.asString().getBytes(StandardCharsets.UTF_8);
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
        String n = name.asString();
        String v = n.isEmpty() ? null : System.getenv(n);
        return Row.ofString(v == null ? "" : v);
    }
}
