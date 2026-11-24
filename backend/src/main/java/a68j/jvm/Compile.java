// a68j: compile an Algol 68 program to a JVM class file.
// Licensed under the GNU GPL, version 3 or later.

package a68j.jvm;

import a68j.front.A68;
import a68j.front.Diagnostics;
import a68j.front.ParserDriver;
import a68j.front.Stropping;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code Compile [options] file.a68 [-d outdir | -d libX.jar]}: front end (stages 0-2) and
 * JVM backend. Writes {@code <outdir>/<Name>.class} (and for a module
 * packet {@code <outdir>/<base>.m68}); run it with
 * {@code java -cp a68j-backend.jar:<outdir> <Name>}. With an outdir ending in
 * {@code .jar} the class and the export data are added to that library jar
 * (the analogue of {@code libX.so}; a program accessing a module of
 * {@code X} finds {@code libX.jar} in the -I/-L directories, and needs it on
 * the class path, which -run does by itself); a program compiled into a jar
 * makes it an executable jar ({@link #programManifest}).
 *
 * <p>Options as ga68: -fstropping=upper|supper, -std=algol68|gnu68,
 * -fbrackets, -I DIR, -L DIR, -fmodules-map=..., -fmodules-map-file=FILE,
 * -fassert|-fno-assert (whether assertions are elaborated, by default yes),
 * the warnings -Wscope, -Wvoiding, -Wextensions, -Wunused, -Wuninitialized,
 * -Whidden-declarations[=none|prelude|all] with their -Wno- forms, -Wall
 * and -Wextra (see WarningOptions), --version. Any other argument that
 * begins with '-' is an error.
 * a68j only: -run (compile and run, the rest of the arguments go to the
 * program), -fclass=NAME, -d DIR|JAR, -fpar-threads (the units of a
 * parallel clause run in threads and the SEMA operators are available, see
 * docs/par-and-sema.md; -fno-par-threads is the default), -fnest-java
 * (formal holes nest Java "pkg.Class.member", see docs/java-modules.md) and
 * -fjava-classpath=PATH (jars and directories with the classes of such
 * formal holes, at compile time and for -run), -fno-loop-methods (compile
 * the loops of the particular program in place instead of as methods of
 * their own, see Outline; -floop-methods is the default), -fgenie (a
 * program written for Algol 68 Genie: UPPER stropping, each source file
 * prepared by the format rewrite tool, see GenieRewrite, the program put
 * into an access clause of Genie_Transput, Genie_Formats, Regex, Files, Times and Math, and the
 * format texts lowered as with -fformats) and -fformats (the front end parses format texts and the mode FORMAT, the
 * mode Gformat of the module Genie_Formats, and GenFormats lowers each
 * format text to a call of that module; in either stropping).
 *
 * <p>Exit status: 0, 1 for errors in the program and fatal errors (such as
 * an output file that cannot be written), 2 for a missing source file name,
 * 3 for a construct that a68j does not support yet, and 4 for an internal
 * compiler error ({@link #internalError}, as ICE_EXIT_CODE of GCC). With
 * -run, the exit status of the program.
 */
public final class Compile {
    private Compile() {}

    /**
     * The exports of libga68 (module "ga68": STANDARD, POSIX, TRANSPUT) are
     * bundled as the resource {@code /a68j/lib/ga68.m68} (the raw
     * {@code .a68_exports} section of libga68.so), and those of the bundled
     * Algol 68 modules (Intrinsics#BUNDLED_MODULES, e.g. Lists) as
     * {@code /a68j/lib/lists.m68} ...; they are copied once to a cache
     * directory in java.io.tmpdir, which is searched after all -I and -L
     * directories.
     *
     * <p>The cache directory is per user and per content
     * ({@code a68j-lib-<user>-<hash>}): java.io.tmpdir may be shared by
     * several users (/tmp on Linux), and a file created there by one user is
     * not readable by another (Files.createTempFile gives mode 0600). A file
     * whose content differs from the resource is replaced; if the directory
     * cannot be written (it belongs to another user, say), the files go to a
     * new private temporary directory instead.
     */
    private static String bundledLibDir() throws java.io.IOException {
        List<String> names = new ArrayList<>();
        names.add("ga68");
        for (String m : new java.util.TreeSet<>(Intrinsics.BUNDLED_MODULES)) {
            names.add(m.toLowerCase(java.util.Locale.ROOT));
        }
        List<byte[]> contents = new ArrayList<>();
        int h = 0;
        for (String n : names) {
            try (var in = Compile.class.getResourceAsStream("/a68j/lib/" + n + ".m68")) {
                if (in == null) {
                    if (n.equals("ga68")) {
                        return ".";
                    }
                    contents.add(null);
                    continue;
                }
                byte[] data = in.readAllBytes();
                contents.add(data);
                h = 31 * h + java.util.Arrays.hashCode(data);
            }
        }
        // A cache directory per user and content, so that concurrent compilers
        // of one user can share it.
        String user = System.getProperty("user.name", "").replaceAll("[^A-Za-z0-9._-]", "_");
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path dir = tmpDir.resolve("a68j-lib-" + user + "-" + Integer.toHexString(h));
        try {
            writeCache(dir, names, contents);
        } catch (java.io.IOException | SecurityException e) {
            dir = Files.createTempDirectory(tmpDir, "a68j-lib-");
            writeCache(dir, names, contents);
            dir.toFile().deleteOnExit();
            for (String n : names) {
                dir.resolve(n + ".m68").toFile().deleteOnExit();
            }
        }
        return dir.toString();
    }

    /** Writes each resource whose cached copy in DIR is missing or different. */
    private static void writeCache(Path dir, List<String> names, List<byte[]> contents)
            throws java.io.IOException {
        for (int i = 0; i < names.size(); i++) {
            byte[] data = contents.get(i);
            if (data == null) {
                continue;
            }
            Path f = dir.resolve(names.get(i) + ".m68");
            if (Files.isRegularFile(f) && Files.isReadable(f) && Files.size(f) == data.length
                    && java.util.Arrays.equals(Files.readAllBytes(f), data)) {
                continue;
            }
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, names.get(i), ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        }
    }

    public static void main(String[] args) throws Throwable {
        // The front end recurses like ga68 (e.g. once per declaration of a
        // series in reduce_right_to_left_constructs), which has the C stack of
        // 8 MB; the main thread of the JVM has 1 MB. The stack of this thread
        // is only reserved, its pages are used as needed.
        Throwable[] failure = new Throwable[1];
        Thread t = new Thread(null, () -> {
            A68 a68 = new A68();
            String[] file = new String[1];
            try {
                compile(args, a68, file);
            } catch (ProgramFailure e) {
                // -run: the program failed, not the compiler.
                failure[0] = e.getCause();
            } catch (OutOfMemoryError e) {
                System.out.flush();
                System.err.println("a68j: fatal error: out of memory (java -Xmx... gives the compiler more heap)");
                System.err.println("compilation terminated.");
                System.err.flush();
                Runtime.getRuntime().halt(1);
            } catch (Throwable e) {
                internalError(a68, file[0], e);
            }
        }, "a68j", COMPILER_STACK_SIZE);
        t.start();
        t.join();
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    /** The stack size of the compiler thread. */
    private static final long COMPILER_STACK_SIZE = 512L << 20;

    /** Where internal compiler errors are reported. */
    static final String BUG_REPORT_URL = "https://github.com/stefan-zobel/a68j/issues";

    /** The exit status of an internal compiler error (ICE_EXIT_CODE of GCC). */
    static final int ICE_EXIT_STATUS = 4;

    /**
     * Under -run, the Throwable of the program itself (its cause), which is
     * not an internal compiler error: main throws it on as before.
     */
    private static final class ProgramFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ProgramFailure(Throwable cause) {
            super(cause);
        }
    }

    /**
     * An internal compiler error, as GCC reports it (diagnostic.cc): the
     * exception E that the compiler did not expect, the innermost frame of
     * a68j code, where to report the error, the version, and the Java stack
     * trace; status 4. The diagnostics collected so far are printed first;
     * if there are errors among them, the compiler only says that it is
     * confused by them. FILE is the source file, or null if none was given
     * yet.
     */
    private static void internalError(A68 a68, String file, Throwable e) {
        String where = file != null ? file : "a68j";
        try {
            print(a68);
        } catch (Throwable ignored) {
            // The diagnostics are a help, not a must.
        }
        System.out.flush();
        PrintStream err = System.err;
        if (a68.job.errorCount != 0) {
            err.println(where + ": confused by earlier errors, bailing out");
        } else {
            err.println(where + ": internal compiler error: " + e);
            StackTraceElement frame = innermostFrame(e);
            if (frame != null) {
                err.println("  in " + frame.getClassName() + "." + frame.getMethodName()
                        + ", at " + frame.getFileName() + ":" + frame.getLineNumber());
            }
            err.println("Please submit a full bug report, with the program, its include files and");
            err.println("the command line, at <" + BUG_REPORT_URL + ">.");
            err.println((Gen.VERSION != null ? "a68j " + Gen.VERSION : "a68j (no version)")
                    + ", Java " + System.getProperty("java.version")
                    + " (" + System.getProperty("java.vendor") + ")");
            err.println("Java stack trace:");
            e.printStackTrace(err);
        }
        err.flush();
        System.exit(ICE_EXIT_STATUS);
    }

    /**
     * The innermost frame of a68j code in the stack trace of E, without the
     * assertions of the front end (Parser.gccAssert, gccUnreachable), or
     * null.
     */
    private static StackTraceElement innermostFrame(Throwable e) {
        for (StackTraceElement f : e.getStackTrace()) {
            if (f.getClassName().startsWith("a68j.")
                    && !(f.getClassName().equals("a68j.front.Parser")
                         && (f.getMethodName().equals("gccAssert") || f.getMethodName().equals("gccUnreachable")))) {
                return f;
            }
        }
        return null;
    }

    /**
     * A fatal error for the I/O exception E while the compiler writes its
     * output or its library cache: the file and the reason, status 1.
     */
    private static void ioError(String what, java.io.IOException e) {
        String reason;
        String path;
        if (e instanceof java.nio.file.FileSystemException fe) {
            path = fe.getFile();
            reason = fe.getReason() != null ? fe.getReason()
                    : fe instanceof java.nio.file.NoSuchFileException ? "no such file or directory"
                    : fe instanceof java.nio.file.AccessDeniedException ? "permission denied"
                    : fe instanceof java.nio.file.FileAlreadyExistsException ? "exists and is not a directory"
                    : e.getClass().getSimpleName();
        } else {
            path = null;
            reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
        System.out.flush();
        System.err.println("a68j: fatal error: " + what + (path != null ? " '" + path + "'" : "") + ": " + reason);
        System.err.println("compilation terminated.");
        System.exit(1);
    }

    /**
     * The test hook A68J_ICE=front|back: an internal compiler error before
     * the front end, or before the code generation, if PHASE is the value.
     */
    private static void testHook(String phase) {
        if (phase.equals(System.getenv("A68J_ICE"))) {
            throw new IllegalStateException("A68J_ICE=" + phase);
        }
    }

    private static void compile(String[] args, A68 a68, String[] fileOut) throws Throwable {
        String file = null;
        Path outDir = Path.of(".");
        List<String> libDirs = new ArrayList<>();
        boolean run = false;
        boolean loopMethods = true;
        boolean genie = false;
        boolean formats = false;
        boolean supperGiven = false;
        String className = null;
        List<String> progArgs = new ArrayList<>();
        List<String> javaClassPath = new ArrayList<>();
        List<String> warningOptions = new ArrayList<>();
        List<String> badOptions = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (file != null && run) {
                progArgs.add(args[i]);
                continue;
            }
            String arg = args[i];
            switch (arg) {
                case "-fstropping=upper" -> a68.options().stropping = Stropping.UPPER;
                case "-fstropping=supper" -> {
                    a68.options().stropping = Stropping.SUPPER;
                    supperGiven = true;
                }
                case "-fgenie" -> genie = true;
                case "-fformats" -> formats = true;
                case "-std=algol68" -> a68.options().strict = true;
                case "-std=gnu68" -> a68.options().strict = false;
                case "-fbrackets" -> a68.options().brackets = true;
                case "-fassert" -> a68.options().assertions = true;
                case "-fno-assert" -> a68.options().assertions = false;
                case "-fpar-threads" -> a68.options().parThreads = true;
                case "-fno-par-threads" -> a68.options().parThreads = false;
                case "-fnest-java" -> a68.options().nestJava = true;
                case "-fno-nest-java" -> a68.options().nestJava = false;
                case "-floop-methods" -> loopMethods = true;
                case "-fno-loop-methods" -> loopMethods = false;
                case "-d" -> outDir = Path.of(args[++i]);
                // As GCC: -I DIR and -L DIR as well as -IDIR and -LDIR.
                case "-I" -> a68.includePaths.add(args[++i]);
                case "-L" -> libDirs.add(args[++i]);
                case "-run" -> run = true;
                case "--version" -> {
                    System.out.println(Gen.VERSION != null ? "a68j " + Gen.VERSION
                            : "a68j (no version: not loaded from a jar built by build.sh)");
                    return;
                }
                case "-fbuilding-libga68" -> a68.options().buildingLibga68 = true;
                default -> {
                    if (arg.startsWith("-fmodules-map=")) {
                        moduleMap(a68, arg.substring("-fmodules-map=".length()), null);
                    } else if (arg.startsWith("-fmodules-map-file=")) {
                        String f = arg.substring("-fmodules-map-file=".length());
                        String text;
                        try {
                            text = Files.readString(Path.of(f), StandardCharsets.ISO_8859_1);
                        } catch (java.io.IOException e) {
                            System.err.println("a68j: fatal error: cannot open modules map file '" + f + "'");
                            System.exit(1);
                            return;
                        }
                        moduleMap(a68, text, f);
                    } else if (arg.startsWith("-fjava-classpath=")) {
                        for (String e : arg.substring("-fjava-classpath=".length()).split(java.io.File.pathSeparator)) {
                            if (!e.isEmpty()) {
                                javaClassPath.add(Path.of(e).toAbsolutePath().toString());
                            }
                        }
                    } else if (arg.startsWith("-fclass=")) {
                        className = arg.substring(8);
                    } else if (arg.startsWith("-I")) {
                        a68.includePaths.add(arg.substring(2));
                    } else if (arg.startsWith("-L")) {
                        libDirs.add(arg.substring(2));
                    } else if (arg.startsWith("-W")) {
                        String error = a68j.front.WarningOptions.check(arg);
                        if (error != null) {
                            badOptions.add(error);
                        } else {
                            warningOptions.add(arg);
                        }
                    } else if (arg.startsWith("-") && arg.length() > 1) {
                        badOptions.add("unrecognized command-line option '" + arg + "'");
                    } else {
                        file = arg;
                    }
                }
            }
        }
        a68j.front.WarningOptions.apply(a68.options(), warningOptions);
        if (genie) {
            if (supperGiven) {
                badOptions.add("'-fgenie' is incompatible with '-fstropping=supper'");
            }
            a68.options().stropping = Stropping.UPPER;
            a68.options().genie = true;
            // The front end parses the format texts and the back end lowers
            // them, as with -fformats; the tool leaves them alone and does
            // the rest of the rewrite (FILE, pragmats, ANDF, ...).
            // A68J_FORMATS=rewrite: the tool rewrites the format texts too
            // (the way of -fgenie until version 0.8.1), a fallback.
            boolean lower = !"rewrite".equals(System.getenv("A68J_FORMATS"));
            a68.options().formats = lower;
            a68.sourceFilter = GenieRewrite.load(true, true, lower);
            if (a68.sourceFilter == null) {
                badOptions.add("'-fgenie' is not available: the class a68j.lib.Genierewrite is missing");
            }
        } else if (formats && !"rewrite".equals(System.getenv("A68J_FORMATS"))) {
            // The front end parses the format texts, the back end lowers them
            // (GenFormats).
            a68.options().formats = true;
        } else if (formats) {
            // A68J_FORMATS=rewrite: the format rewrite tool instead (the way
            // of -fformats before format texts were lowered), a fallback.
            // -fgenie lowers the format texts itself, so -fformats with it
            // changes nothing.
            a68.sourceFilter = GenieRewrite.load(a68.options().stropping == Stropping.UPPER, false, false);
            if (a68.sourceFilter == null) {
                badOptions.add("'-fformats' is not available: the class a68j.lib.Genierewrite is missing");
            }
        }
        if (!badOptions.isEmpty()) {
            for (String m : badOptions) {
                System.err.println("a68j: error: " + m);
            }
            System.exit(1);
        }
        fileOut[0] = file;
        a68.importPaths.addAll(a68.includePaths);
        a68.importPaths.addAll(libDirs);
        try {
            a68.importPaths.add(bundledLibDir());
        } catch (java.io.IOException e) {
            ioError("cannot write the library cache", e);
        }
        if (file == null) {
            System.err.println("usage: Compile [options] file.a68 [-d outdir]\n       Compile [options] -run file.a68 [program arguments]");
            System.exit(2);
        }
        System.setErr(new PrintStream(System.err, true, StandardCharsets.ISO_8859_1));
        try {
            testHook("front");
            ParserDriver.parse(a68, file);
        } catch (Diagnostics.ScanError e) {
            print(a68);
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            print(a68);
            System.err.println("a68j: " + e.getMessage());
            System.exit(3);
        }
        print(a68);
        if (a68.job.errorCount != 0) {
            System.exit(1);
        }
        // A prelude packet (module definition): its export data goes to
        // <outdir>/<basename>.m68, and the class is named after the module.
        String moduleId = moduleName(a68.job.topNode);
        byte[] exports = a68j.front.Exports.doExports(a68, a68.job.topNode);
        String name = className != null ? className
                : moduleId != null ? Intrinsics.moduleClass(moduleId) : className(file);
        String base = Path.of(file).getFileName().toString().replaceFirst("\\.a68$", "");
        boolean toJar = outDir.toString().endsWith(".jar");
        if (exports.length > 0 && !run && !toJar) {
            try {
                Files.createDirectories(outDir);
                Files.write(outDir.resolve(base + ".m68"), exports);
            } catch (java.io.IOException e) {
                ioError("cannot write", e);
            }
        }
        try {
            // The classes of Java formal holes (nest Java) are looked up in
            // the library jars and the -fjava-classpath entries, then in the
            // compiler's own class path (a68j.jlib, the JDK).
            List<String> jars = runJars(a68);
            jars.addAll(javaClassPath);
            if (!jars.isEmpty()) {
                java.net.URL[] urls = new java.net.URL[jars.size()];
                for (int i = 0; i < urls.length; i++) {
                    urls[i] = Path.of(jars.get(i)).toUri().toURL();
                }
                GenJava.loader = new java.net.URLClassLoader(urls, Compile.class.getClassLoader());
            }
            // The class of the program (or module) and the classes of its procedure values.
            testHook("back");
            java.util.Map<String, byte[]> classes = Gen.compileClass(a68, name, file, loopMethods);
            print(a68); // the warnings of the back end (-Wframe-scope)
            if (run) {
                runClass(name, classes, progArgs, jars);
                return;
            }
            if (toJar) {
                // -d libX.jar: a library jar (the analogue of libX.so), see Imports#libraryJar.
                java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
                for (var c : classes.entrySet()) {
                    entries.put(c.getKey().replace('.', '/') + ".class", c.getValue());
                }
                if (exports.length > 0) {
                    entries.put(a68j.front.Imports.LIBRARY_JAR_EXPORTS + base + ".m68", exports);
                }
                if (moduleId == null) {
                    // A program: the jar becomes executable.
                    entries.put("META-INF/MANIFEST.MF", programManifest(name, outDir, jars));
                }
                try {
                    updateJar(outDir, entries);
                } catch (java.io.IOException e) {
                    ioError("cannot write", e);
                }
                return;
            }
            try {
                for (var c : classes.entrySet()) {
                    Path out = outDir.resolve(c.getKey().replace('.', '/') + ".class");
                    Files.createDirectories(out.getParent());
                    Files.write(out, c.getValue());
                }
            } catch (java.io.IOException e) {
                ioError("cannot write", e);
            }
        } catch (Gen.Unsupported e) {
            System.err.println(e.getMessage());
            System.exit(3);
        } catch (Gen.TooLarge e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (Gen.CompileError e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (Gen.Fatal e) {
            System.err.println(e.getMessage());
            System.err.println("compilation terminated.");
            System.exit(1);
        }
    }

    /**
     * The library jars for -run: those whose export data was read, then the
     * other {@code lib*.jar} of the -I/-L directories, for the modules that
     * the accessed modules access in turn (with ga68 all of them are linked),
     * then the -I/-L directories (module classes compiled with -c).
     */
    private static List<String> runJars(A68 a68) {
        List<String> jars = new ArrayList<>(a68.libraryJars);
        for (String dir : a68.importPaths) {
            Path d = Path.of(dir.isEmpty() ? "." : dir);
            if (!Files.isDirectory(d)) {
                continue;
            }
            try (var files = Files.list(d)) {
                files.map(Path::toAbsolutePath).map(Path::toString).sorted()
                        .filter(f -> { String b = Path.of(f).getFileName().toString(); return b.startsWith("lib") && b.endsWith(".jar"); })
                        .filter(f -> !jars.contains(f))
                        .forEach(jars::add);
            } catch (java.io.IOException e) {
                // An unreadable directory adds nothing.
            }
        }
        // Then the -I directories themselves, for module classes compiled with -c.
        for (String dir : a68.importPaths) {
            Path d = Path.of(dir.isEmpty() ? "." : dir).toAbsolutePath();
            if (Files.isDirectory(d) && !jars.contains(d.toString())) {
                jars.add(d.toString());
            }
        }
        return jars;
    }

    /**
     * The manifest of the jar JAR into which the program of class NAME is
     * compiled (-d prog.jar): an executable jar, run with
     * {@code java -jar prog.jar}. Main-Class is the program; Class-Path names
     * the runtime jar and the jars among JARS (the library jars of the
     * modules, see {@link #runJars}, and the jars of -fjava-classpath) by
     * their file names, so that they are found next to JAR. The runtime jar
     * is named both as a release copy of this version
     * ({@code a68j-runtime-<version>.jar}) and as build.sh names it
     * ({@code a68j-runtime.jar}); the JVM skips the entries it does not
     * find. Directories of JARS are not named.
     */
    private static byte[] programManifest(String name, Path jar, List<String> jars) throws java.io.IOException {
        java.util.Set<String> cp = new java.util.LinkedHashSet<>();
        if (Gen.VERSION != null) {
            cp.add("a68j-runtime-" + Gen.VERSION + ".jar");
        }
        cp.add("a68j-runtime.jar");
        String self = jar.getFileName().toString();
        for (String j : jars) {
            Path f = Path.of(j).getFileName();
            if (f != null && f.toString().endsWith(".jar") && !f.toString().equals(self) && !Files.isDirectory(Path.of(j))) {
                cp.add(f.toString());
            }
        }
        java.util.jar.Manifest mf = new java.util.jar.Manifest();
        java.util.jar.Attributes at = mf.getMainAttributes();
        at.put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        at.put(new java.util.jar.Attributes.Name("Created-By"), Gen.VERSION != null ? "a68j " + Gen.VERSION : "a68j");
        at.put(java.util.jar.Attributes.Name.MAIN_CLASS, name);
        StringBuilder path = new StringBuilder();
        for (String e : cp) {
            try {
                // An entry is a relative URL: a space becomes %20.
                path.append(path.isEmpty() ? "" : " ").append(new java.net.URI(null, null, e, null).getRawPath());
            } catch (java.net.URISyntaxException x) {
                throw new java.io.IOException(x);
            }
        }
        at.put(java.util.jar.Attributes.Name.CLASS_PATH, path.toString());
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        mf.write(bytes);
        return bytes.toByteArray();
    }

    /**
     * Add (or replace) ENTRIES in the jar JAR, creating it if needed: the
     * classes and export data of the module packets compiled into a library
     * jar one after the other, and the manifest if ENTRIES has one (a
     * program, see {@link #programManifest}).
     */
    private static void updateJar(Path jar, java.util.Map<String, byte[]> entries) throws java.io.IOException {
        java.util.Map<String, byte[]> all = new java.util.TreeMap<>();
        if (Files.exists(jar)) {
            try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
                for (var e = zip.entries(); e.hasMoreElements(); ) {
                    var z = e.nextElement();
                    if (!z.isDirectory()) {
                        try (var in = zip.getInputStream(z)) {
                            all.put(z.getName(), in.readAllBytes());
                        }
                    }
                }
            }
        }
        all.putAll(entries);
        all.putIfAbsent("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nCreated-By: a68j\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        if (jar.getParent() != null) {
            Files.createDirectories(jar.getParent());
        }
        Path tmp = Files.createTempFile(jar.toAbsolutePath().getParent(), "a68j", ".tmp");
        try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(tmp))) {
            // The manifest first, as the jar tool writes it.
            byte[] mf = all.remove("META-INF/MANIFEST.MF");
            out.putNextEntry(new java.util.zip.ZipEntry("META-INF/MANIFEST.MF"));
            out.write(mf);
            out.closeEntry();
            for (var e : all.entrySet()) {
                out.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        Files.move(tmp, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * -run: define the classes in memory (CLASSES by binary name, see
     * Gen#compileClass) and call the main of the class NAME with the program
     * arguments. The library jars JARS ({@link #runJars}) are added to the
     * class path. A Throwable of the program, or of loading its classes, is
     * thrown as a ProgramFailure, so that it is not taken for an internal
     * compiler error.
     */
    private static void runClass(String name, java.util.Map<String, byte[]> classes, List<String> progArgs,
                                 List<String> jars) throws Throwable {
        try {
            runClass0(name, classes, progArgs, jars);
        } catch (Throwable e) {
            throw new ProgramFailure(e);
        }
    }

    private static void runClass0(String name, java.util.Map<String, byte[]> classes, List<String> progArgs,
                                  List<String> jars) throws Throwable {
        ClassLoader parent = Compile.class.getClassLoader();
        if (!jars.isEmpty()) {
            java.net.URL[] urls = new java.net.URL[jars.size()];
            for (int i = 0; i < urls.length; i++) {
                urls[i] = Path.of(jars.get(i)).toUri().toURL();
            }
            parent = new java.net.URLClassLoader(urls, parent);
        }
        ClassLoader loader = new ClassLoader(parent) {
            @Override
            protected Class<?> findClass(String n) throws ClassNotFoundException {
                byte[] bytes = classes.get(n);
                if (bytes != null) {
                    return defineClass(n, bytes, 0, bytes.length);
                }
                throw new ClassNotFoundException(n);
            }
        };
        Class<?> c = loader.loadClass(name);
        try {
            c.getMethod("main", String[].class).invoke(null, (Object) progArgs.toArray(new String[0]));
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** -fmodules-map (a68-lang.cc). */
    private static void moduleMap(A68 a68, String map, String file) {
        a68j.front.Ref<String> errmsg = a68j.front.Ref.box(null);
        if (!a68j.front.Imports.processModuleMap(a68, map, errmsg)) {
            if (file == null) {
                System.err.println("a68j: error: invalid argument for '-fmodules-map': " + errmsg.get());
            } else {
                System.err.println("a68j: fatal error: " + file + ": " + errmsg.get());
            }
            System.exit(1);
        }
    }

    /** The mangled name of the module defined by the packet P, or null. */
    static String moduleName(a68j.front.Node p) {
        for (; p != null; p = p.next) {
            if (p.attribute == a68j.front.Attribute.DEFINING_MODULE_INDICANT) {
                return a68j.front.Low.getMangledIndicant(null, p.symbol(), null, false, false);
            }
            if (p.attribute != a68j.front.Attribute.PARTICULAR_PROGRAM) {
                String n = moduleName(p.sub);
                if (n != null) {
                    return n;
                }
            }
        }
        return null;
    }

    /** The number of diagnostics printed so far. */
    private static int printed;

    /** Prints the diagnostics not printed yet. */
    private static void print(A68 a68) {
        List<Diagnostics.Diagnostic> all = a68.diagnostics.messages();
        for (; printed < all.size(); printed++) {
            System.err.println(all.get(printed));
        }
    }

    /** A Java class name from the file name: letters and digits, capitalised. */
    static String className(String file) {
        String base = Path.of(file).getFileName().toString().replaceFirst("\\.a68$", "");
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (char c : base.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            } else {
                up = true;
            }
        }
        if (sb.isEmpty() || !Character.isJavaIdentifierStart(sb.charAt(0))) {
            sb.insert(0, 'P');
        }
        return sb.toString();
    }
}
