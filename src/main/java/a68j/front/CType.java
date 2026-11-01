// C character classification as in libiberty's safe-ctype.h.
// Licensed under the GNU GPL, version 3 or later.

package a68j.front;

/**
 * Locale-independent ASCII character classes, exactly as GCC's safe-ctype
 * (ISUPPER, ISLOWER, ...). Characters outside 0..127 belong to no class,
 * which matters because the scanner sees the source file byte by byte.
 *
 * <p>Also contains small helpers that reproduce C string functions on
 * NUL-terminated char arrays, so that the scanner's buffer arithmetic can be
 * ported literally.
 */
public final class CType {
    private CType() {}

    public static boolean isUpper(char c) { return c >= 'A' && c <= 'Z'; }
    public static boolean isLower(char c) { return c >= 'a' && c <= 'z'; }
    public static boolean isAlpha(char c) { return isUpper(c) || isLower(c); }
    public static boolean isDigit(char c) { return c >= '0' && c <= '9'; }
    public static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == 0x0b || c == '\f' || c == '\r';
    }
    public static boolean isPrint(char c) { return c >= 0x20 && c <= 0x7e; }
    public static boolean isCntrl(char c) { return c < 0x20 || c == 0x7f; }
    public static char toLower(char c) { return isUpper(c) ? (char) (c + ('a' - 'A')) : c; }
    public static char toUpper(char c) { return isLower(c) ? (char) (c - ('a' - 'A')) : c; }

    /**
     * {@code strchr (set, c) != NULL}. Note that, as in C, searching for the
     * NUL character always succeeds (it matches the terminator).
     */
    public static boolean strchr(String set, char c) {
        return c == '\0' || set.indexOf(c) >= 0;
    }

    /** strcmp on Java strings holding byte values (unsigned comparison). */
    public static int strcmp(String a, String b) {
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            int d = a.charAt(i) - b.charAt(i);
            if (d != 0) {
                return d;
            }
        }
        return a.length() - b.length();
    }

    /** strcasecmp (ASCII case folding). */
    public static int strcasecmp(String a, String b) {
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            int d = toLower(a.charAt(i)) - toLower(b.charAt(i));
            if (d != 0) {
                return d;
            }
        }
        return a.length() - b.length();
    }

    /** strlen of the NUL-terminated string starting at {@code buf[from]}. */
    public static int strlen(char[] buf, int from) {
        int i = from;
        while (i < buf.length && buf[i] != '\0') {
            i++;
        }
        return i - from;
    }

    /** The NUL-terminated string starting at {@code buf[from]}, as a Java string. */
    public static String cString(char[] buf, int from) {
        return new String(buf, from, strlen(buf, from));
    }
}
