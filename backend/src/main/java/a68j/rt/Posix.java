// a68j runtime: the POSIX module (as libga68's ga68-posix.c). GPLv3+.
package a68j.rt;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The procedures and constants of the POSIX module. File descriptors 0, 1
 * and 2 are standard input, output and error; files opened by fopen and
 * fcreate get descriptors from 3 up. errno is set as libga68 does, with the
 * Linux error numbers.
 */
public final class Posix {
    private Posix() {}

    /** A POSIX procedure used as a value. */
    public static Proc procValue(String method) {
        return Std.reflectProc(Posix.class, method);
    }

    private static final int ENOENT = 2, EBADF = 9, EACCES = 13, EEXIST = 17, EISDIR = 21, EINVAL = 22;
    private static int errno;
    private static final Map<Integer, RandomAccessFile> FILES = new HashMap<>();
    private static int nextFd = 3;
    private static final InputStream IN = System.in;

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
        return Row.ofString(switch (e) {
            case 0 -> "Success";
            case ENOENT -> "No such file or directory";
            case EBADF -> "Bad file descriptor";
            case EACCES -> "Permission denied";
            case EEXIST -> "File exists";
            case EISDIR -> "Is a directory";
            case EINVAL -> "Invalid argument";
            default -> "Unknown error " + e;
        });
    }

    public static void perror(Row s) {
        Rt.OUT.flush();
        System.err.print(s.asString() + ": " + strerror(errno).asString() + "\n");
        System.err.flush();
    }

    private static synchronized int open(String path, String mode, boolean trunc) {
        java.io.File f = new java.io.File(path);
        if (!f.exists() && mode.equals("r")) {
            errno = ENOENT;
            return -1;
        }
        if (f.isDirectory() && !mode.equals("r")) {
            errno = EISDIR;
            return -1;
        }
        try {
            RandomAccessFile raf = new RandomAccessFile(f, mode);
            if (trunc) {
                raf.setLength(0);
            }
            errno = 0;
            FILES.put(nextFd, raf);
            return nextFd++;
        } catch (IOException e) {
            errno = f.exists() ? EACCES : ENOENT;
            return -1;
        }
    }

    public static synchronized int fopen(Row path, int flags) {
        String p = path.asString();
        if (flags == fileodefault()) {
            int fd = open(p, "rw", false);
            if (fd < 0) {
                fd = open(p, "r", false);
            }
            return fd;
        }
        boolean write = (flags & 0x3) != 0;
        if (write && !new java.io.File(p).exists()) {
            errno = ENOENT;
            return -1;
        }
        return open(p, write ? "rw" : "r", (flags & 0x8) != 0);
    }

    public static synchronized int fcreate(Row path, int mode) {
        return open(path.asString(), "rw", true);
    }

    public static int fconnect(Row host, int port) {
        errno = EINVAL;
        return -1;
    }

    public static synchronized int fclose(int fd) {
        RandomAccessFile f = FILES.remove(fd);
        if (f == null) {
            errno = fd >= 0 && fd <= 2 ? 0 : EBADF;
            return fd >= 0 && fd <= 2 ? 0 : -1;
        }
        try {
            f.close();
            errno = 0;
            return 0;
        } catch (IOException e) {
            errno = EBADF;
            return -1;
        }
    }

    public static synchronized long fsize(int fd) {
        RandomAccessFile f = FILES.get(fd);
        if (f == null) {
            if (fd >= 0 && fd <= 2) {
                return 0;
            }
            errno = EBADF;
            return -1;
        }
        try {
            return f.length();
        } catch (IOException e) {
            errno = EBADF;
            return -1;
        }
    }

    public static synchronized long lseek(int fd, long offset, int whence) {
        RandomAccessFile f = FILES.get(fd);
        if (f == null) {
            errno = EBADF;
            return -1;
        }
        try {
            long pos = switch (whence) {
                case 0 -> f.getFilePointer() + offset;
                case 1 -> f.length() + offset;
                default -> offset;
            };
            if (pos < 0) {
                errno = EINVAL;
                return -1;
            }
            f.seek(pos);
            errno = 0;
            return pos;
        } catch (IOException e) {
            errno = EBADF;
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // Characters and strings (UTF-8 on the descriptors).
    // ------------------------------------------------------------------

    private static synchronized int readByte(int fd) {
        try {
            if (fd == 0) {
                Rt.OUT.flush();
                return IN.read();
            }
            RandomAccessFile f = FILES.get(fd);
            return f == null ? -1 : f.read();
        } catch (IOException e) {
            return -1;
        }
    }

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
                    RandomAccessFile f = FILES.get(fd);
                    if (f == null) {
                        errno = EBADF;
                        return false;
                    }
                    f.write(b);
                }
            }
            errno = 0;
            return true;
        } catch (IOException e) {
            errno = EBADF;
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

    public static int fputc(int fd, int c) {
        if (c < 0 || c > 0x10ffff || (c >= 0xd800 && c < 0xe000)) {
            return -1;
        }
        return write(fd, new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8)) ? c : -1;
    }

    public static int putchar(int c) { return fputc(1, c); }

    public static int fputs(int fd, Row s) {
        byte[] b = s.asString().getBytes(StandardCharsets.UTF_8);
        return write(fd, b) ? b.length : 0;
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
