// The -W options of ga68 on the command line.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Sets the optional warnings ({@link Options#warnings}) from the -W options
 * of ga68 as GCC's option handling does (gcc/algol68/lang.opt, common.opt):
 *
 * <ul>
 * <li>{@code -Wscope}, {@code -Wvoiding}, {@code -Wextensions},
 * {@code -Wunused} and {@code -Wuninitialized} switch one warning on;
 * {@code -Wno-}<i>name</i> switches it off.</li>
 * <li>{@code -Whidden-declarations=none|prelude|all} sets the level of
 * the hidden-declarations warning; {@code -Whidden-declarations} is
 * {@code =prelude}, {@code -Wno-hidden-declarations} is {@code =none}.</li>
 * <li>{@code -Wall} switches on {@code -Whidden-declarations=prelude};
 * {@code -Wextra} switches on {@code -Wextensions}, {@code -Wvoiding} and
 * {@code -Wuninitialized}. A warning given by name keeps its setting,
 * whatever the order of the options.</li>
 * <li>{@code -Wframe-scope}, a warning of a68j that ga68 does not have, is
 * on by default; {@code -Wno-frame-scope} switches it off. {@code -Wall}
 * and {@code -Wextra} do not change it.</li>
 * </ul>
 *
 * Within each kind of option the last one wins. Without options all
 * warnings of ga68 are off, as in ga68.
 */
public final class WarningOptions {

    private WarningOptions() {
    }

    /**
     * Null if ARG (beginning with -W) is a valid -W option, otherwise the
     * message of the error (without the program name).
     */
    public static String check(String arg) {
        Options scratch = new Options();
        return apply(scratch, List.of(arg));
    }

    /**
     * Applies the -W options ARGS, in the order given, to OPTIONS. Returns
     * null, or the message of the error for the first option that is not
     * valid (without the program name).
     */
    public static String apply(Options options, List<String> args) {
        Map<Warn, Boolean> named = new EnumMap<>(Warn.class);
        Integer hidden = null;
        boolean all = false;
        boolean extra = false;
        for (String arg : args) {
            if (arg.equals("-W")) {
                // The old name of -Wextra (common.opt).
                extra = true;
                continue;
            }
            String name = arg.substring(2);
            if (name.startsWith("hidden-declarations=")) {
                String level = name.substring("hidden-declarations=".length());
                switch (level) {
                    case "none" -> hidden = 0;
                    case "prelude" -> hidden = 1;
                    case "all" -> hidden = 2;
                    default -> {
                        // The message of ga68, with its spelling of the option.
                        return "argument '" + level + "' to '-Whidden-declaration' not recognized";
                    }
                }
                continue;
            }
            boolean on = !name.startsWith("no-");
            if (!on) {
                name = name.substring(3);
            }
            switch (name) {
                case "all" -> all = on;
                case "extra" -> extra = on;
                case "scope" -> named.put(Warn.SCOPE, on);
                case "voiding" -> named.put(Warn.VOIDING, on);
                case "extensions" -> named.put(Warn.EXTENSIONS, on);
                case "unused" -> named.put(Warn.UNUSED, on);
                case "uninitialized" -> named.put(Warn.UNINITIALIZED, on);
                case "frame-scope" -> named.put(Warn.FRAME_SCOPE, on);
                case "hidden-declarations" -> hidden = on ? 1 : 0;
                default -> {
                    return "unrecognized command-line option '" + arg + "'";
                }
            }
        }
        set(options, Warn.SCOPE, named.getOrDefault(Warn.SCOPE, false));
        set(options, Warn.UNUSED, named.getOrDefault(Warn.UNUSED, false));
        set(options, Warn.EXTENSIONS, named.getOrDefault(Warn.EXTENSIONS, extra));
        set(options, Warn.VOIDING, named.getOrDefault(Warn.VOIDING, extra));
        set(options, Warn.UNINITIALIZED, named.getOrDefault(Warn.UNINITIALIZED, extra));
        set(options, Warn.FRAME_SCOPE, named.getOrDefault(Warn.FRAME_SCOPE, true));
        int level = hidden != null ? hidden : all ? 1 : 0;
        options.hiddenDeclarations = level;
        set(options, Warn.HIDDEN_DECLARATIONS, level > 0);
        return null;
    }

    private static void set(Options options, Warn w, boolean on) {
        if (on) {
            options.warnings.add(w);
        } else {
            options.warnings.remove(w);
        }
    }
}
