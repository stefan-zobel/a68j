# Java from Algol 68: `nest Java` and the modules `Lists` and `Hashmaps`

As of 31 Oct 2026. An extension of a68j over ga68. Without the option
`-fnest-java` a68j behaves exactly like ga68. The modules `Lists` and
`Hashmaps` can be used without the option, because they come with the
jar.

## 1. Formal holes: `nest Java "Class.member"`

For foreign code ga68 knows the *formal holes* of the Revised Report, but
only for C:

```
proc real myrandom = nest C "_libga68_random";
```

With `-fnest-java` a68j allows the same notation for Java. As in ga68,
the Algol 68 text gives the mode. The compiler looks up the fitting
public static method or field by reflection **at compile time**:

```
begin
   proc (real) real cbrt = nest Java "java.lang.Math.cbrt";
   proc (int) string tobin = nest Java "java.lang.Integer.toBinaryString";
   proc ([] int) string show = nest Java "java.util.Arrays.toString";
   long real pi = nest Java "java.lang.Math.PI";
   puts (fixed (cbrt (27.0), 0, 3) + " " + tobin (10) + " " + show ((3, 1, 4)) + "'n")
end
```

```sh
java -jar backend/a68j-backend.jar -fnest-java -run prog.a68
```

- **Procedure mode:** the static method whose parameters and result fit
  the mode is looked up; with overloading, the cheapest fitting one
  (section 1a, "Which method").
- **Other mode:** the static field is read, such as `Math.PI`.
- **Errors:** a wrong class name or modes that do not fit are a
  **compile-time error**, e.g.
  `fatal error: no public static Java method 'java.lang.Math.nosuch' fits the mode proc (real) real`.
- **Without `-fnest-java`:** the message of ga68,
  `error: Java is not a valid language indication`.
- **Classes of your own:** `-fjava-classpath=PATH` (jars and directories,
  separated by `:`, on Windows `;`). The compiler also looks in the
  library jars (`libX.jar`) and in its own class path, that is, in the JDK
  and in `a68j.jlib`. With `-run` the entries also go on the class path of
  the program. A program compiled separately gets them with `-cp`:

```sh
java -jar backend/a68j-backend.jar -fnest-java -fjava-classpath=classes prog.a68 -d out
java -cp backend/a68j-backend.jar:classes:out Prog
```

### How the modes map

| Algol 68 | Java |
|---|---|
| `int` | `int` (as a parameter also `long`, `double`) |
| `long int` | `long` (as a parameter also `double`) |
| `real` (32 bits in ga68) | `float` or `double` |
| `long real` | `double` |
| `bool` | `boolean` |
| `char` | `int` (code point) or `char` |
| `bits` | `int` |
| `string` | `java.lang.String` (converted) **or** `a68j.rt.Row` (unchanged) |
| `[] int`, `[] long int`, `[] real`, `[] long real`, `[] bool` | `int[]`, `long[]`, `float[]`, `double[]`, `boolean[]` (copied) |
| `[] string` | `String[]` (copied) |
| any row | also `a68j.rt.Row` (unchanged, not copied) |
| `ref M` (a name) | `a68j.rt.Ref` or a subclass: an output parameter (an `IntCell` for `ref int`) or a Java object (`a68j.rt.JavaObject`) |
| `void` (result) | any result, which is discarded |

Section 1a adds procedures (functional interfaces), names of rows (Java
arrays, stored back) and Java objects for plain values.

### How it is compiled

For every formal hole of a procedure a68j generates a **bridge method**
`(Frame, Object[]) → Object` in the class of the program. It unpacks the
arguments, converts them, calls the Java method with `invokestatic` and
converts the result back. The value of the formal hole is a closure over
this method, that is, an ordinary procedure value: it can be passed on,
stored in variables and so on (`backend/.../jvm/GenJava.java`, runtime
helpers in `a68j.rt.JavaConv`). A call of the identifier itself does not
go through the closure but calls the Java method directly
(`docs/optimizations.md`, section 2).

## 1a. Procedures and functional interfaces, names of rows, Java objects

**Algol 68 procedures as Java objects.** A parameter of a procedure mode
fits a Java parameter whose type is a *functional interface* (a public
interface with one abstract method, such as `java.util.Comparator`,
`java.util.function.IntUnaryOperator` or an interface of your own). The
Algol 68 procedure becomes an object of that interface. Calling its method
calls the procedure:

```
proc (proc (long real) long real, long real, long real, int) long real integrate
   = nest Java "demo.Fns.integrate";
proc (ref [] string, proc (string, string) int) void sort = nest Java "java.util.Arrays.sort";

print (integrate (long_sin, long 0, long_pi, 100));
[5] string names := ("Wirth", "van Wijngaarden", "Lindsey", "Koster", "Mailloux");
sort (names, (string a, b) int: UPB a - UPB b)
```

**Java objects as Algol 68 procedures.** The other way round, a Java result
(or field) whose type is a functional interface is an Algol 68 procedure:

```
proc (int) proc (int) int adder = nest Java "demo.Fns.adder";   { returns an IntUnaryOperator }
proc (int) int add10 = adder (10);
print (add10 (5))                                                { 15 }
```

Both nest: a procedure from Java can be passed back to Java, and the
parameters and results of the interface method may themselves be
procedures.

**Generic types.** The modes are matched against the types the generic
signature gives. `Function<Float, Float>` takes and yields `Float`, so
`proc (real) real` fits it, and `Comparator<? super String>` compares
`String`s. Type variables are bound to the type arguments of the interface
and its superinterfaces, and a wildcard stands for its bound. What the
signature does not say becomes the bound of the type variable, usually
`Object`: the `T` of `<T> T twice (UnaryOperator<T> f, T x)` is `Object`.
The code itself uses the erased signature (as javac does), with casts and
boxing.

**Names of rows as arrays.** A name of a one-dimensional row (`ref [] int`,
`ref [] string` …) fits a Java array parameter. The method gets a new array
with the elements, and after the call they are stored back into the row.
This makes in-place methods of the JDK usable, with slices too:

```
proc (ref [] int) void sort = nest Java "java.util.Arrays.sort";
proc (ref [] int, proc (int) int) void set_all = nest Java "java.util.Arrays.setAll";
[1:8] int a := (5, 3, 8, 1, 9, 2, 7, 4);
sort (a[3:6]);                          { 5 3 1 2 8 9 7 4 }
set_all (a, (int i) int: i * i)         { the index is that of Java, from 0 }
```

**Java objects for plain values.** Where Java wants an object (`Object`,
`Number`, `Integer`, a type variable …), a value of a plain mode is passed
as `Integer` (INT, BITS), `Long` (LONG INT), `Float` or `Double` (REAL: `Float`
if `Float` is asked for, else `Double`), `Double` (LONG REAL), `Boolean`,
`Character` (CHAR) or `String` (STRING); a name as itself. A Java object as an
Algol 68 value is converted back: any `Number` for the numeric modes,
`Character` or `Integer` for CHAR, `toString()` for STRING (`""` for `null`).
A `null` or an object of the wrong class where a plain value is expected is
a runtime error.

**Which method.** If several overloads fit, the one with the lowest cost is
taken: the same representation costs nothing, widening (`int` → `long`)
and copying (rows, names of rows, procedures) a little, Java objects more.
So `Arrays.sort(int[])` is preferred to `Arrays.sort(Object[])` for a
`ref [] int`.

**Control flow.** A jump out of the procedure (`goto` to a label outside)
and runtime errors pass through the Java method as they would through an
Algol 68 procedure, and the error message gives the position in the Algol 68
source. The Java method should not catch `Throwable` in between. Java may
call the procedure from other threads (`Arrays.parallelSetAll`). That is
safe as far as PAR clauses are safe: the procedure should not assign to
variables it shares with other threads without a semaphore.

**Speed.** If the argument is a routine text, or a procedure declared with
one whose environment is at hand (the same cases as for direct calls,
section 2 of `docs/optimizations.md`), the object of the interface holds
the frame. Its method calls the typed entry of the routine directly,
without an argument array and without boxing: about 3 ns per call (20
million calls of `(long real x) long real: x * x` from Simpson's rule in
0.06 s). Any other procedure value is called through `Proc.call` with an
`Object[]`, about 22 ns per call.

**How it is compiled** (`backend/.../jvm/GenJava.java`):

- An Algol 68 procedure for a functional interface: `invokedynamic` with
  `LambdaMetafactory.metafactory`, capturing either the frame (typed path)
  or the `a68j.rt.Proc`. The implementation is a generated static method
  `jN (Frame or Proc, the parameters of the interface method)` that
  converts the arguments, calls the routine and converts the result.
- A Java object as a procedure: an `a68j.rt.JavaFn` (a subclass of
  `a68j.rt.Proc`) holding the object and a generated adapter
  `jN (Object target, Object[] args) -> Object` that calls the interface
  method with `invokeinterface`.
- A name of a row: `JavaConv.ints(Ref)` … before the call and
  `JavaConv.back(Ref, int[])` … after it.
- Java objects: `Integer.valueOf` … in one direction, `JavaConv.objInt(Object)`
  … in the other.

Tests: `tests/java/nest-java-fn.a68` (all of the above with
`tests/java/demo/Fns.java` and the JDK), `nest-java-fn-jump.a68` (a jump
out of a comparator through `Arrays.sort`, a runtime error in a callback),
`nest-java-fn-error.a68` (a procedure that does not fit `Predicate<String>`).

## 2. Java objects in Algol 68

Algol 68 has no mode for "any Java object". a68j takes a **name that
refers to nothing** for it:

```
mode Intlist = ref Intlist_object;
mode Intlist_object = struct (int intlist_java_object);
```

At run time such a value is a Java object whose class extends
`a68j.rt.JavaObject` (a subclass of `a68j.rt.Ref`). So what one knows from
names works:

- **What works:** passing it on, storing it, comparing it with
  `is`/`isnt`, assigning `nil` and comparing with `nil`.
- **What is a runtime error:** dereferencing (`Intlist_object o = l`) and
  assigning, with the message
  `runtime error: attempt to dereference a Java object (a68j.jlib.IntList)`.

The structure with the field `intlist_java_object` is never made. It only
makes the mode unique: two modes that differ only in the name of a field
are different modes in Algol 68, and operators can be overloaded for them
separately.

## 3. The module `Lists`

Lists that grow like `java.util.ArrayList`. An append costs O(1) on
average; a FLEX row is copied entirely each time it grows. The values are
kept **unboxed** in a Java array of the element type.

| Mode | Elements | Java class |
|---|---|---|
| `Intlist` | `int` | `a68j.jlib.IntList` (`int[]`) |
| `Longintlist` | `long int` | `LongIntList` (`long[]`) |
| `Reallist` | `real` | `RealList` (`float[]`) |
| `Longreallist` | `long real` | `LongRealList` (`double[]`) |
| `Boollist` | `bool` | `BoolList` (`boolean[]`) |
| `Stringlist` | `string` | `StringList` (copies of the strings) |

Operations, for every list `l` of the mode `M` with elements of the mode
`E`. The operators exist for all six modes:

| | |
|---|---|
| `new_m`, e.g. `new_intlist` | a new empty list |
| `new_m_with_capacity (n)` | the same with room for n elements |
| `m_of (r)`, e.g. `intlist_of ((1, 2, 3))` | a new list with the elements of the row r |
| `l +:= x`, `l +:= r` | append an element, or all elements of a row; yields l |
| `UPB l`, `LWB l` | the number of elements, 1 |
| `i ELEM l` | the i-th element (bound check) |
| `i AT l` | the **name** of the i-th element: `i AT l := x`, `i AT l +:= 1` |
| `POP l` | remove the last element and yield it |
| `CLEAR l` | remove all elements; yields l |
| `SORT l` | sort ascending (FALSE before TRUE); yields l |
| `SIZE l` | the number of elements (as for the maps) |
| `l INDEX x` | the index of the first element equal to x, else 0 |
| `l HAS x` | whether an element is equal to x |
| `l REMOVE i` | remove the i-th element and yield it; the following ones move up |
| `TOROW l` | the elements as a row `[1 : UPB l]` (a copy) |

`AT`, `INDEX`, `HAS` and `REMOVE` have priority 7. For a `Stringlist`,
`+:=`, `INDEX` and `HAS` also take a `char`, see "A character is not a
string" in section 4.

An example (`tests/java/lists-basic.a68`):

```
access Lists (
   Intlist l = new_intlist;
   for i to 20 do l +:= i * i od;
   5 AT l := -1;
   6 AT l +:= 1000;
   puts ("POP: " + whole (POP l, 0) + ", UPB " + whole (UPB l, 0) + "'n");
   [] int r = TOROW SORT l;
   puts ("l INDEX 49: " + whole (l INDEX 49, 0) + "'n")
)
```

**Strings:** a `Stringlist` stores copies. `s +:= w; w[1] := "b"` does
not change the element in the list; likewise `ELEM`, `POP` and `TOROW`
yield copies, as the value semantics of Algol 68 demand. Strings are
sorted by character codes, like the comparison operators for STRING do.

**Errors:** an index outside `[1 : UPB l]`, `POP` on an empty list or an
operation on `nil` ends the program with
`runtime error: index 2 out of range [1:1] of Intlist` (status 134). The
line number is missing, because the Java method does not know where it
was called from.

### Speed

| | time (with the start of the JVM) |
|---|---|
| append to a `flex [] int` 100 000 times | 8.7 s |
| `string +:= "x"` 100 000 times | 6.0 s (since 31 Oct 2026 with spare room: 0.39 s, `docs/optimizations.md`) |
| **append to an `Intlist` 1 000 000 times and read everything** | **0.46 s** |
| plus 100 000 strings into a `Stringlist` | 0.65 s |

Since 31 Oct 2026 the calls go directly to the Java method without boxing
(`docs/optimizations.md`, section 2): `lists-speed.a68` takes 0.22 s
instead of 0.32 s without the start of the JVM; the same with
`java.util.ArrayList` takes 0.13 s with the start of the JVM.

### Where the module comes from

All six modes come from **one template**: `tools/gen_lists.py` generates
`backend/lib/lists.a68` and the Java classes
`backend/src/main/java/a68j/jlib/*List.java`. `backend/build.sh` compiles
the module with `-fnest-java` into the class `a68j.lib.Lists` and puts its
export data into the jar as `a68j/lib/lists.m68`. The compiler finds them
like those of libga68, so `access Lists` works without `-I`
(`Intrinsics.BUNDLED_MODULES`, `Compile.bundledLibDir`).

A new element mode is one line in `KINDS` in `tools/gen_lists.py`.

## 4. The module `Hashmaps`

Hash tables with constant time on average for lookup, insertion and
removal. There is a mode of its own for every combination of key mode and
value mode, 3 × 6 = 18 of them:

| | Modes |
|---|---|
| keys | `int`, `long int`, `string` |
| values | `int`, `long int`, `real`, `long real`, `bool`, `string` |
| name | `K_V_map`: `Int_int_map`, `String_int_map`, `Long_int_string_map`, `String_long_real_map` … |
| Java class | `a68j.jlib.IntIntMap`, `StringIntMap`, `LongIntStringMap`, … |

Underscores do not count in SUPPER mode names: `Stringintmap` is the same
as `String_int_map`.

Operations, for every map `m` of the mode `M` from `K` to `V`:

| | |
|---|---|
| `new_m`, e.g. `new_string_int_map` | a new empty map |
| `new_m_with_capacity (n)` | the same with room for n entries |
| `k AT m` | the **name** of the value for the key k. If k is missing, it is inserted with 0, 0.0, FALSE or "": `k AT m := v`, `k AT m +:= 1` |
| `k ELEM m` | the value for the key k; if k is missing, a runtime error |
| `m HAS k` | whether k is in the map |
| `m REMOVE k` | remove k; yields whether it was there |
| `SIZE m` | the number of entries |
| `CLEAR m` | remove all entries; yields m |
| `KEYS m` | the keys in the order of insertion, a row `[1 : SIZE m]` |
| `VALUES m` | the values in the same order |

Counting words (`tests/java/hashmaps-basic.a68`):

```
access Hashmaps (
   String_int_map count = new_string_int_map;
   [] string words = ("to", "be", "or", "not", "to", "be");
   for i from LWB words to UPB words do words[i] AT count +:= 1 od;
   [] string k = KEYS count;
   for i from LWB k to UPB k do
      puts (k[i] + " " + whole (k[i] ELEM count, 0) + "'n")
   od
)
```

**`AT` inserts**, like `map[k]` in C++. Only so does `k AT m +:= 1` work
the first time too. To only read, take `ELEM` or ask first with `HAS`.
The name that `AT` yields stays valid even when the map grows or other
keys are removed. If its own key is removed, the next use of the name
inserts it again.

**Order:** `KEYS` and `VALUES` yield the entries in the order in which the
keys were inserted. A key that was removed and inserted again goes to the
end. A program thus prints the same in every run, unlike with
`java.util.HashMap`.

**Cheap `HAS` + `ELEM`:** the map remembers the entry it found last.
After `m HAS k`, `k ELEM m` and `k AT m` cost no second lookup, so
`(m HAS k | k ELEM m | default)` looks up only once.

**Structure** (like the "compact dict" of CPython): the entries are in the
order of insertion in parallel arrays for keys, hash values and values,
all **unboxed**. String keys are kept as arrays of code points. Beside
them there is an index table with 2^k slots, at most half full, with
linear probing. A removed entry is only marked as dead and stays in the
index table as a tombstone until the table is rebuilt when it grows the
next time. A string key that is only a slice (`line[3:10] ELEM m`) is
hashed and compared in place and copied only when it is inserted.

**Errors:** `k ELEM m` for a missing key ends the program with
`runtime error: key 2 not in Int_string_map` (status 134).

### A character is not a string

In Algol 68 `"x"` is a `char`, not a `string`. As the argument of a
procedure it is widened to a `string` (rowing, strong), but not as the
operand of an operator, because an operand is only firm. So that
`"x" AT m` and `m HAS "x"` work all the same, `AT`, `ELEM`, `HAS` and
`REMOVE` also exist with `char` for string keys. Likewise `+:=`, `INDEX`
and `HAS` of the `Stringlist` exist with `char`.

### `Lists` and `Hashmaps` together: `access pub`

**A rule of ga68:** a program cannot access two modules that both declare
the priority of the same operator, even if the value is the same. Even if
the same module arrives on two ways, ga68 reports
`multiple declaration of priority for operator 'AT'`. I checked this with
ga68 on three tiny modules: `access Moda, Modb`, where `Modb` itself
contains `access pub Moda`, fails, and so does `access Modb, Modc`, which
both contain `access pub Moda`. a68j behaves the same.

That is why only `Lists` declares the priorities of `AT`, `INDEX`, `HAS`
and `REMOVE`. `Hashmaps` accesses `Lists` with **`access pub Lists`** and
so passes it on to its users. A program that needs both writes only
**`access Hashmaps`**. `access Lists, Hashmaps` is an error
(`tests/java/hashmaps-both.a68`). For modules of your own this means: a
module that should be usable together with `Lists` must not declare a
priority for these four operators. Instead it takes `access pub Lists` or
uses other names for its operators.

### Speed

`tests/java/hashmaps-speed.a68`: insert 1 000 000 `int` keys, read them
all, remove half of them, `HAS` 1 000 000 times, then
`whole (i MOD 50000, 0) AT w +:= 1` 200 000 times.

| | time with the start of the JVM |
|---|---|
| a68j with `Hashmaps`, until 31 Oct 2026 (boxed calls) | 0.80 s |
| a68j with `Hashmaps`, direct calls | 0.57 s (0.52 s without the start of the JVM) |
| the same in Java with `java.util.HashMap` (boxed) | 0.45 s |

The results are the same. Until 31 Oct 2026 the path of the calls made
almost all of the difference: every operator called a procedure, and every
formal hole went through a procedure value with an `Object[]` and boxed
arguments. Now a call goes directly to the Java method without boxing
(`docs/optimizations.md`, section 2). The rest of the difference is in the
table itself and in `whole` for the string keys.

### Where the module comes from

Like `Lists`: `tools/gen_hashmaps.py` generates `backend/lib/hashmaps.a68`
and the 18 classes `backend/src/main/java/a68j/jlib/*Map.java`. A new key
or value mode is one line in `KEYS` or `VALUES`. `backend/build.sh`
compiles `lists` before `hashmaps`, because `Hashmaps` accesses `Lists`.

## 5. Writing Java modules of your own

The recipe after which `Lists` is built:

1. **Java class:** a class that extends `a68j.rt.JavaObject`, with public
   **static** methods. The object is the first parameter. Errors are
   reported with `throw JavaObject.error("…")`.
2. **Algol 68 module:** it has a formal hole for every method. On top of
   them goes the public interface of operators and procedures:
   ```
   module Counters =
   def
       pub mode Counter = ref Counter_object;
       pub mode Counter_object = struct (int counter_java_object);
       proc Counter j_new = nest Java "my.Counter.create";
       proc (Counter) int j_next = nest Java "my.Counter.next";
       pub proc new_counter = Counter: j_new;
       pub op NEXT = (Counter c) int: j_next (c);
       skip
   fed
   ```
3. **Compile** it as a library jar:
   `-fnest-java -fjava-classpath=classes counters.a68 -d lib/libcounters.jar`.
   The jar of the Java class must be on the class path when the program
   runs.
4. **Use it:** a program writes only `access Counters ( … )` and needs no
   `-fnest-java` itself. So it stays valid GNU Algol 68. The Java class
   must be reachable:
   ```sh
   java -jar backend/a68j-backend.jar -I lib -fjava-classpath=classes -run usec.a68
   ```
   This example has been tried: `NEXT c` yields 3 the third time.

## 6. Tests

`tests/java/`, run by `tools/java_tests.sh [JAVA]`. Options are in the
first line as `{ a68j-options: … }`, the expected output and status in
`X.out`.

| Test | checks |
|---|---|
| `nest-java` | methods and fields of the JDK, conversion of numbers, strings and rows |
| `nest-java-direct` | direct calls: discarded results (also `long`), INT → `long`, CHAR, REAL → `double`, rows there and back, operators from `nest Java`, a call without arguments, the formal hole as a procedure value, the order of the arguments |
| `nest-java-error` | a method that does not fit: compile-time error |
| `nest-java-fn` | procedures as functional interfaces and back, generic types, names of rows as arrays, Java objects for plain values (section 1a) |
| `nest-java-fn-jump` | a jump out of a callback through `Arrays.sort`; a runtime error in a callback |
| `nest-java-fn-error` | a procedure that does not fit the interface: compile-time error |
| `nest-java-off` | without `-fnest-java`: the message of ga68 |
| `nest-java-classpath` | a class of your own, `tests/java/demo/Util.java`, through `-fjava-classpath` |
| `lists-basic` | all operations on `Intlist`, `is`, `nil`, `AT` |
| `lists-kinds` | the other five modes, copies in a `Stringlist` |
| `lists-speed` | 1 000 000 elements |
| `lists-error` | an index out of bounds |
| `lists-nil` | dereferencing a Java object |
| `hashmaps-basic` | all operations on `String_int_map`, slices as keys, names across growth and removal |
| `hashmaps-kinds` | all 18 modes |
| `hashmaps-lists` | `Lists` through `access pub`, `HAS` and `REMOVE` on lists, strings as keys |
| `hashmaps-both` | `access Lists, Hashmaps`: the message as in ga68 |
| `hashmaps-error` | a missing key with `ELEM` |
| `hashmaps-speed` | 1 000 000 keys |

Result on 1 Nov 2026: 19 of 19 with JDK 25 and JDK 28 ea+21.

## 7. Open points

- Typed entries also for public formal holes of a module
  (`pub proc (int) int f = nest Java "…"`); today they go through the
  boxed procedure value.
- Line numbers in error messages from Java methods.
- Instance methods and constructors (today only static methods and
  fields), so that JDK objects can be used without a wrapper class of
  their own.
- Done on 1 Nov 2026: procedures as parameters (functional interfaces),
  see section 1a.
