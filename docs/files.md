# The bundled module `Files`

The transput of Algol 68 opens, reads and writes files, but it has no test
whether a file exists, no directory listing, no way to read or write a
whole file as a string, and no delete, move, copy or creation of
directories. The module `Files` provides them on top of `java.nio.file`,
together with the file procedures of Algol 68 Genie under their names. It
comes with `a68j-backend.jar` and needs no option; as it needs Java, it
runs with a68j only.

```
access Files (
    make_directory ("demo");
    write_file ("demo/greeting.txt", "hello'nworld'n");
    write_lines ("demo/numbers.txt", ("1", "2", "3"));
    [] string lines = read_lines ("demo/greeting.txt");
    puts (whole (UPB lines, 0) + " lines, " + lines[2] + "'n");
    [] string names = list_directory ("demo");
    for i to UPB names do
        string p = "demo/" + names[i];
        puts (names[i] + " " + whole (file_size (p), 0) + "'n");
        delete_file (p)
    od;
    delete_file ("demo")
)
```

```sh
java -jar a68j-backend.jar -run prog.a68
2 lines, world
greeting.txt 12
numbers.txt 6
```

## What it provides

### The procedures of Algol 68 Genie

| Name | Meaning |
|---|---|
| `get_directory (string dir) [] string` | the names in `dir`: `.` and `..` first, then the others sorted; no names if `dir` is not a directory that can be read |
| `file_is_directory (string path) bool` | whether `path` is a directory |
| `file_is_regular (string path) bool` | whether `path` is a regular file |
| `file_is_link (string path) bool` | whether `path` is a symbolic link |
| `file_is_block_device`, `file_is_char_device`, `file_is_fifo` | the same for the special files of POSIX |
| `file_mode (string path) bits` | `st_mode` of POSIX: the type bits (`8r170000`) and the permissions |

They do not fail. For a path that does not exist, the `file_is_` procedures
yield `false` and `file_mode` yields 0. Except for `file_is_link`, they
follow symbolic links, as `stat` does.

### Further procedures

| Name | Meaning |
|---|---|
| `file_exists (string path) bool` | whether a file or directory exists |
| `file_size (string path) long int` | the size in bytes |
| `file_time (string path) long int` | the time of the last change, in seconds since 1970-01-01 UTC |
| `read_file (string path) string` | the whole file |
| `read_lines (string path) [] string` | its lines, split at `"'n"`, `"'r'n"` or `"'r"`, without the line ends; a line end at the end of the file gives no empty line after it, and an empty file has no lines |
| `write_file (string path, text) void` | creates the file or replaces its contents |
| `append_file (string path, text) void` | appends to the file, which is created if needed |
| `write_lines (string path, [] string lines) void` | creates the file or replaces its contents, each line followed by `"'n"` |
| `list_directory (string dir) [] string` | the names in `dir`, sorted, without `.` and `..` |
| `walk_directory (string dir) [] string` | every file and directory below `dir`, as paths that begin with `dir` and `/`: depth first, the entries of each directory sorted, a directory before its entries; symbolic links to directories are not followed |
| `make_directory (string dir) void` | creates `dir` and its parents; nothing if it exists |
| `make_temp_directory (string prefix) string` | creates a new empty directory in the directory for temporary files (`java.io.tmpdir`) and yields its absolute path |
| `delete_file (string path) void` | deletes a file or an empty directory |
| `move_file (string from, to) void` | moves or renames `from`, replacing a file `to` |
| `copy_file (string from, to) void` | copies the file `from`, replacing a file `to` |
| `current_directory string` | the current directory, absolute |
| `absolute_path (string path) string` | `path` as an absolute path, without `.` and `..` |

- The rows have the lower bound 1.
- Names are sorted by their characters (`B.txt` before `a.txt`).
- To delete a directory with its contents, delete the paths of
  `walk_directory` from the last to the first, then the directory:

  ```
  [] string all = walk_directory (d);
  for i from UPB all by -1 to 1 do delete_file (all[i]) od;
  delete_file (d)
  ```

- In UPPER stropping the names have blanks (`get directory`,
  `read file`); in SUPPER stropping the blanks are underscores.

## Errors

Apart from the procedures of Algol 68 Genie and `file_exists`, a failure
is a runtime error, status 134, at the position of the call in the
program:

```
prog.a68:5: runtime error: cannot read "data.txt": no such file or directory
prog.a68:9: runtime error: cannot delete "out": directory not empty
prog.a68:12: runtime error: cannot list "notes.txt": not a directory
prog.a68:14: runtime error: cannot copy "out": is a directory
```

The reasons are `no such file or directory`, `permission denied`,
`file exists`, `directory not empty`, `not a directory`, `is a directory`
and `invalid path`. Any other reason is the one that the operating system
gives. A program that must not stop tests first with `file_exists`,
`file_is_directory` or `file_is_regular`.

## Paths and text

- A path is a string, absolute or relative to the current directory. A
  program cannot change the current directory (the JVM has no `chdir`).
- The paths that the module yields (`walk_directory`,
  `make_temp_directory`, `current_directory`, `absolute_path`) use `/` on
  every system, so that a program prints the same on Windows and
  elsewhere. Windows accepts `/` in the paths it is given as well.
- Text is UTF-8: a character of Algol 68 (a code point) is one to four
  bytes in the file. Bytes that are not UTF-8 are read as U+FFFD.
  `file_size` counts bytes, so `"a" + REPR 228` is 3 bytes.
- No line end is added or changed: `write_file` writes the text as it is,
  and `read_file` yields the bytes of the file, CR included.

## Differences from Algol 68 Genie

- **Order.** Algol 68 Genie gives the names of `get directory` in the
  order of `readdir`, which depends on the file system; a68j sorts them,
  after `.` and `..`.
- **Windows.** Without the POSIX attributes, `file_mode` has the type bits
  of a directory or regular file and the permissions that Java sees for
  the user (read, write, execute), repeated for the group and the others,
  as the C library of Windows gives them. Symbolic links on Windows need
  the right to create them.

## `-fgenie`

With `-fgenie` the program is put into an access clause of
`Genie_Transput`, `Genie_Formats`, `Regex`, `Files` and `Times`
([genie-formats.md](genie-formats.md)), so a program written for
Algol 68 Genie can call `get directory` and `file is directory` as it is.
A declaration of the program with the same name hides the one of the
module: the Rosetta Code solution `check-that-file-exists` declares its
own `file exists`. The solutions `walk-a-directory-recursively`,
`walk-a-directory-non-recursively` and `empty-directory` run unchanged.

## Inside

`backend/lib/files.a68` declares each procedure as a public formal hole of
the class `a68j.jlib.Filesystem`, which works on `java.nio.file.Path` and
`Files`. A program calls such a hole directly, through the typed entry of
the module ([java-modules.md](java-modules.md), section 5), so it needs no
`-fnest-java`. The Java method turns an `IOException` into the message
above with `JavaObject.error`. `file_mode` reads the attribute
`unix:mode` where the file system has it.

## Tests

- `tests/java/files-basic.a68`: reading, writing and appending, lines
  with `"'r'n"` and `"'r"`, sizes and times, characters beyond ASCII,
  listing and walking, moving, copying and deleting, all in a temporary
  directory.
- `tests/java/files-genie.a68`: the procedures of Algol 68 Genie on a
  directory, a file and a missing path.
- `tests/java/files-error-1.a68` … `-4`: a missing file, a directory that
  is not empty, a file listed as a directory, a directory copied.
- `tests/options/genie-files.a68`: `-fgenie` with `get directory`,
  `file is directory`, `grep in string` and a declaration that hides one
  of `Files`.
