// a68j Java library: files and directories for the module Files.
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
package a68j.jlib;

import a68j.rt.JavaObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The Java side of the Algol 68 module Files (backend/lib/files.a68): files
 * and directories on top of java.nio.file, with the procedures of Algol 68
 * Genie (get directory, file is directory, file mode, ...) and the everyday
 * operations (read and write a whole file, list, walk, delete, move, copy).
 * Only static methods, as they are called through formal holes (nest Java).
 *
 * <p>Paths are strings. A path that this class returns uses "/" on every
 * system (Windows accepts it too). Text is UTF-8; bytes that are not UTF-8
 * are read as U+FFFD.
 *
 * <p>The queries (exists, isDirectory, mode, directory, ...) yield false, 0
 * or nothing for a file that does not exist, as in Algol 68 Genie. Every
 * other failure is a runtime error at the position of the program that
 * called the procedure, with a message such as
 * {@code cannot read "data.txt": no such file or directory}.
 */
public final class Filesystem {
    private Filesystem() {}

    // The type bits of st_mode (POSIX).
    private static final int S_IFMT = 0170000;
    private static final int S_IFIFO = 0010000;
    private static final int S_IFCHR = 0020000;
    private static final int S_IFDIR = 0040000;
    private static final int S_IFBLK = 0060000;
    private static final int S_IFREG = 0100000;

    /** The path of S, or null if S is not a valid path on this system. */
    private static Path pathOrNull(String s) {
        try {
            return Path.of(s);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /** The path of S; an invalid path is a runtime error of the operation WHAT. */
    private static Path path(String what, String s) {
        try {
            return Path.of(s);
        } catch (InvalidPathException e) {
            throw JavaObject.error("cannot " + what + " " + quote(s) + ": invalid path");
        }
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    /** P as a string with "/" as the separator. */
    private static String slashes(Path p) {
        String s = p.toString();
        return File.separatorChar == '/' ? s : s.replace(File.separatorChar, '/');
    }

    /** The runtime error for the exception E of the operation WHAT on S. */
    private static RuntimeException failure(String what, String s, IOException e) {
        return JavaObject.error("cannot " + what + " " + s + ": " + reason(e));
    }

    private static String reason(IOException e) {
        if (e instanceof NoSuchFileException) {
            return "no such file or directory";
        } else if (e instanceof AccessDeniedException) {
            return "permission denied";
        } else if (e instanceof FileAlreadyExistsException) {
            return "file exists";
        } else if (e instanceof DirectoryNotEmptyException) {
            return "directory not empty";
        } else if (e instanceof NotDirectoryException) {
            return "not a directory";
        } else if (e instanceof FileSystemException f && f.getReason() != null) {
            return f.getReason();
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getName();
    }

    // ---- The procedures of Algol 68 Genie ----

    /**
     * get directory: the names of the entries of the directory S, "." and
     * ".." first, then the others in the order of their names (Algol 68
     * Genie gives them in the order of readdir, which is not defined). If S
     * is not a directory that can be read, a runtime error with the text of
     * Algol 68 Genie, such as "file access error, no such file or
     * directory".
     */
    public static String[] directory(String s) {
        Path p = s.isEmpty() ? null : pathOrNull(s);
        if (p == null) {
            throw JavaObject.error("file access error, no such file or directory");
        }
        List<String> names;
        try {
            names = names(p);
        } catch (IOException e) {
            throw JavaObject.error("file access error, " + reason(e));
        }
        names.add(0, "..");
        names.add(0, ".");
        return names.toArray(new String[0]);
    }

    /** file is directory: whether S is a directory (following links). */
    public static boolean isDirectory(String s) {
        Path p = pathOrNull(s);
        return p != null && Files.isDirectory(p);
    }

    /** file is regular: whether S is a regular file (following links). */
    public static boolean isRegular(String s) {
        Path p = pathOrNull(s);
        return p != null && Files.isRegularFile(p);
    }

    /** file is link: whether S is a symbolic link. */
    public static boolean isLink(String s) {
        Path p = pathOrNull(s);
        return p != null && Files.isSymbolicLink(p);
    }

    /** file is block device. */
    public static boolean isBlockDevice(String s) {
        return (mode(s) & S_IFMT) == S_IFBLK;
    }

    /** file is char device. */
    public static boolean isCharDevice(String s) {
        return (mode(s) & S_IFMT) == S_IFCHR;
    }

    /** file is fifo. */
    public static boolean isFifo(String s) {
        return (mode(s) & S_IFMT) == S_IFIFO;
    }

    /**
     * file mode: st_mode of S (following links), 0 if it does not exist. On
     * a file system without the attribute unix:mode (Windows) the type bits
     * of a directory or regular file, and the permissions that Java sees for
     * the user, repeated for the group and the others, as the C library of
     * Windows does.
     */
    public static int mode(String s) {
        Path p = pathOrNull(s);
        if (p == null || !Files.exists(p)) {
            return 0;
        }
        try {
            return (Integer) Files.getAttribute(p, "unix:mode");
        } catch (UnsupportedOperationException | IllegalArgumentException | IOException e) {
            int type = Files.isDirectory(p) ? S_IFDIR : Files.isRegularFile(p) ? S_IFREG : 0;
            int rwx = (Files.isReadable(p) ? 4 : 0) | (Files.isWritable(p) ? 2 : 0)
                    | (Files.isExecutable(p) ? 1 : 0);
            return type | rwx << 6 | rwx << 3 | rwx;
        }
    }

    // ---- Queries ----

    /** Whether a file or directory S exists (following links). */
    public static boolean exists(String s) {
        Path p = pathOrNull(s);
        return p != null && Files.exists(p);
    }

    /** The size of the file S in bytes. */
    public static long size(String s) {
        try {
            return Files.size(path("get the size of", s));
        } catch (IOException e) {
            throw failure("get the size of", quote(s), e);
        }
    }

    /** The time of the last change of S, in seconds since 1970-01-01 UTC. */
    public static long time(String s) {
        try {
            return Files.getLastModifiedTime(path("get the time of", s)).to(TimeUnit.SECONDS);
        } catch (IOException e) {
            throw failure("get the time of", quote(s), e);
        }
    }

    /** The current directory, absolute. */
    public static String currentDirectory() {
        return slashes(Path.of("").toAbsolutePath());
    }

    /**
     * real path: S as an absolute path without "." and ".." and with every
     * symbolic link resolved, or "" if S does not exist (Algol 68 Genie).
     */
    public static String realPath(String s) {
        Path p = s.isEmpty() ? null : pathOrNull(s);
        if (p == null) {
            return "";
        }
        try {
            return slashes(p.toRealPath());
        } catch (IOException | SecurityException e) {
            return "";
        }
    }

    /** S as an absolute path without "." and "..". */
    public static String absolutePath(String s) {
        return slashes(path("make absolute", s).toAbsolutePath().normalize());
    }

    // ---- Reading and writing ----

    /** The text of the file S. */
    public static String readFile(String s) {
        Path p = path("read", s);
        if (Files.isDirectory(p)) {
            throw JavaObject.error("cannot read " + quote(s) + ": is a directory");
        }
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw failure("read", quote(s), e);
        }
    }

    /**
     * The lines of the file S, split at "\n", "\r\n" or "\r"; a line
     * end at the end of the file gives no empty line after it.
     */
    public static String[] readLines(String s) {
        String t = readFile(s);
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\n' || c == '\r') {
                lines.add(t.substring(start, i));
                if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        if (start < t.length()) {
            lines.add(t.substring(start));
        }
        return lines.toArray(new String[0]);
    }

    /** Creates or replaces the file S with the text T. */
    public static void writeFile(String s, String t) {
        write("write", s, t, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** Appends the text T to the file S, which is created if needed. */
    public static void appendFile(String s, String t) {
        write("append to", s, t, StandardOpenOption.APPEND);
    }

    /** Creates or replaces the file S with the LINES, each followed by "\n". */
    public static void writeLines(String s, String[] lines) {
        StringBuilder b = new StringBuilder();
        for (String l : lines) {
            b.append(l).append('\n');
        }
        write("write", s, b.toString(), StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void write(String what, String s, String t, StandardOpenOption mode) {
        Path p = path(what, s);
        if (Files.isDirectory(p)) {
            throw JavaObject.error("cannot " + what + " " + quote(s) + ": is a directory");
        }
        try {
            Files.write(p, t.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, mode);
        } catch (IOException e) {
            throw failure(what, quote(s), e);
        }
    }

    // ---- Directories ----

    /** The names in the directory P, in the order of the names. */
    private static List<String> names(Path p) throws IOException {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> d = Files.newDirectoryStream(p)) {
            for (Path q : d) {
                names.add(q.getFileName().toString());
            }
        }
        names.sort(null);
        return names;
    }

    /** The names in the directory S, without "." and "..", in their order. */
    public static String[] list(String s) {
        try {
            return names(path("list", s)).toArray(new String[0]);
        } catch (IOException e) {
            throw failure("list", quote(s), e);
        }
    }

    /**
     * Every file and directory below the directory S, as S + "/" + the path
     * below it: depth first, the entries of each directory in the order of
     * their names, a directory before its entries. Symbolic links to
     * directories are not followed.
     */
    public static String[] walk(String s) {
        Path p = path("walk", s);
        List<String> paths = new ArrayList<>();
        String prefix = s.endsWith("/") || s.endsWith(File.separator) ? s : s + "/";
        try {
            walk(p, prefix, paths);
        } catch (IOException e) {
            throw failure("walk", quote(s), e);
        }
        return paths.toArray(new String[0]);
    }

    private static void walk(Path dir, String prefix, List<String> paths) throws IOException {
        for (String n : names(dir)) {
            Path q = dir.resolve(n);
            paths.add(prefix + n);
            if (Files.isDirectory(q, LinkOption.NOFOLLOW_LINKS)) {
                walk(q, prefix + n + "/", paths);
            }
        }
    }

    /** Creates the directory S and its parents; nothing if it exists. */
    public static void makeDirectory(String s) {
        try {
            Files.createDirectories(path("make the directory", s));
        } catch (IOException e) {
            throw failure("make the directory", quote(s), e);
        }
    }

    /** A new empty directory in the directory for temporary files. */
    public static String makeTempDirectory(String prefix) {
        try {
            return slashes(Files.createTempDirectory(prefix).toAbsolutePath());
        } catch (IOException | IllegalArgumentException e) {
            String r = e instanceof IOException io ? reason(io) : "invalid prefix";
            throw JavaObject.error("cannot make a temporary directory " + quote(prefix) + ": " + r);
        }
    }

    // ---- Delete, move, copy ----

    /** Deletes the file or empty directory S. */
    public static void delete(String s) {
        try {
            Files.delete(path("delete", s));
        } catch (IOException e) {
            throw failure("delete", quote(s), e);
        }
    }

    /** Moves (renames) FROM to TO, replacing a file TO. */
    public static void move(String from, String to) {
        String what = "move " + quote(from) + " to";
        try {
            Files.move(path("move", from), path(what, to), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw failure(what, quote(to), e);
        }
    }

    /** Copies the file FROM to TO, replacing a file TO. */
    public static void copy(String from, String to) {
        String what = "copy " + quote(from) + " to";
        Path p = path("copy", from);
        if (Files.isDirectory(p)) {
            throw JavaObject.error("cannot copy " + quote(from) + ": is a directory");
        }
        try {
            Files.copy(p, path(what, to), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw failure(what, quote(to), e);
        }
    }
}
