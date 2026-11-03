# Java from Algol 68: `nest Java` and the modules `Lists` and `Hashmaps`

As of 1 Nov 2026. An extension of a68j over ga68. Without the option
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
  the mode is looked up; with overloading, the cheapest fitting one, and
  a compile-time error if several are equally cheap (section 1a, "Which
  method"). Constructors, instance methods and fields
  of objects are written as method references, `"java.util.ArrayList::new"`,
  `"java.util.ArrayList::add"` and `"java.awt.Point::x"` (section 1b).
- **Other mode:** the static field is read, such as `Math.PI`, once, where
  the formal hole is elaborated. `"Class::field"` with a procedure mode
  reads it at every call and can also write it (section 1b). If reading
  the field initializes the class and its static initializer throws, that
  is a runtime error at the formal hole, as for a Java exception in a call
  (section 1, "Java exceptions").
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
| `char` | `int` (code point) or `char` (as a parameter `int` is preferred, see "Which method") |
| `bits` | `int` |
| `string` | `java.lang.String` (converted) **or** `a68j.rt.Row` (unchanged) |
| `[] int`, `[] long int`, `[] real`, `[] long real`, `[] bool` | `int[]`, `long[]`, `float[]`, `double[]`, `boolean[]` (copied) |
| `[] string` | `String[]` (copied) |
| any row | also `a68j.rt.Row` (unchanged, not copied) |
| `ref M` (a name) | `a68j.rt.Ref` or a subclass: an output parameter (an `IntCell` for `ref int`) or a Java object (`a68j.rt.JavaObject`); also a Java object of any class (section 1b) |
| `[] ref M` | an array of Java objects (section 1b) |
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
(`docs/optimizations.md`, section 2). So does a program that calls a
public formal hole of a module (`pub proc (int) int j_abs = nest Java
"…"`): the module class has a typed entry for it, which converts the
arguments and calls the Java method in the same way.

### Java exceptions

An exception that a Java method throws ends the program with a runtime
error, exit status 134, as for the runtime errors of Algol 68 itself. The
message gives the position in the Algol 68 source, the exception and its
causes:

```
prog.a68:7: runtime error: Java exception java.lang.NumberFormatException: For input string: "12x"
prog.a68:6: runtime error: Java exception java.lang.IllegalStateException: cannot load data.txt, caused by java.io.FileNotFoundException: data.txt
```

**Which position.**

- **A direct call** of the identifier or operator (see above): the
  position of the call, known when the program is compiled.
- **A call through the procedure value** (the bridge method), and a call
  of a Java functional object as an Algol 68 procedure (section 1a): the
  position of the Algol 68 code that called the procedure. The bridge does
  not know it, so the runtime looks it up on the stack when the exception
  happens. It looks for the innermost frame of a class compiled from
  Algol 68: every such class has the constant `A68_SOURCE`, its source
  file. The line is the last line number entry before the call, the
  line of the unit or call. If there is no such frame, the position is
  that of the formal hole, or none for a functional object.

So for a public formal hole of a module (`pub proc (string) int parse =
nest Java "…"`), the position is in the program that calls `parse`: the
typed entry of the module that the call goes to (section 5) finds it on
the stack in the same way. For a
hole that the module calls itself inside a public procedure, the position
is the call in the module, as for any runtime error in a procedure of a
module.

**The Java stack trace.** The message does not show where in the Java
code the exception was thrown. For the authors of Java modules the
runtime prints the Java stack trace of the exception and its causes after
the message if the environment variable `A68J_JAVA_TRACE` is set, to any
value. The runtime reads it when the program runs, so it works with
`-run` and for a program compiled to class files alike:

```
$ A68J_JAVA_TRACE=1 java -cp a68j-backend.jar:out:classes Prog
prog.a68:11: runtime error: Java exception java.lang.IllegalStateException: cannot load data.txt, caused by java.io.FileNotFoundException: data.txt
java.lang.IllegalStateException: cannot load data.txt
	at demo.Fail.wrapped(Fail.java:12)
	at Prog.program(prog.a68:11)
	...
```

The frames of the classes compiled from Algol 68 carry the source file
and line, as in the message. Without the variable the message stays as it
is. The variable concerns Java exceptions only, not the errors that Java
code reports itself with `JavaObject.error` (section 5).

**What passes through.** These are not Java exceptions, and they go on
through the Java method:

- a jump out of an Algol 68 procedure that Java calls back
  (`a68j.rt.Jump`, section 1a);
- the errors of the virtual machine (`VirtualMachineError`): a stack
  overflow ends the program with status 139 as elsewhere;
- the cancellation of a unit of a parallel clause (`-fpar-threads`,
  `docs/par-and-sema.md`). Its thread is interrupted. A unit that waits in
  a Java method then gets an `InterruptedException`, or a
  `ClosedByInterruptException` from a channel. In a unit thread this ends
  the unit and is not a runtime error, so the jump that cancelled the
  clause goes on. A Java method that catches the interruption and throws
  something else instead gives a runtime error.

**How it is compiled.** The call instruction alone is in an exception
handler (catch all); so is the `getstatic` that reads a static field for a
formal hole of a mode that is not a procedure, since it may run the static
initializer of the class. The handler calls `Rt.javaException` (direct calls)
or `Rt.javaExceptionOfCaller` (bridge and adapter methods) and throws
what they return. They return the exceptions that pass through; for any
other exception they print the message and halt. Nothing else changes on
the normal path, and an exception handler costs nothing as long as no
exception is thrown.

A Java method can also report an error itself with
`throw JavaObject.error("…")` (section 5). Its position is found on the
stack in the same way: the innermost Algol 68 code that called the
method. The bundled modules `Lists` and `Hashmaps` count as part of the
runtime here (their classes are in the package `a68j.lib`): a missing key
in `k ELEM m` is reported at the `ELEM` in the program, not in the source
of the module. The same holds for a Java exception in a call that one of
these modules makes.

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

puts (fixed (integrate (long_sin, long 0, long_pi, 100), 0, 6) + "'n");
[5] string names := ("Wirth", "van Wijngaarden", "Lindsey", "Koster", "Mailloux");
sort (names, (string a, b) int: UPB a - UPB b)
```

**Java objects as Algol 68 procedures.** The other way round, a Java result
(or field) whose type is a functional interface is an Algol 68 procedure:

```
proc (int) proc (int) int adder = nest Java "demo.Fns.adder";   { returns an IntUnaryOperator }
proc (int) int add10 = adder (10);
puts (whole (add10 (5), 0) + "'n")                               { 15 }
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
boxing. The type variables of a class are bound by type arguments in the
formal hole (section 1b, "Type arguments").

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
taken: the same representation costs nothing, widening (`int` → `long`),
copying (rows, names of rows, procedures) and a `char` as a Java `char`
(a code point cut to UTF-16) a little, Java objects more. So
`Arrays.sort(int[])` is preferred to `Arrays.sort(Object[])` for a
`ref [] int`, and `Character.isDigit(int)` to `isDigit(char)` for a
`char`, so that a character beyond U+FFFF stays whole.

If several overloads fit at the same lowest cost, the formal hole is
**ambiguous**, a compile-time error that names them. This happens with
a Java object (section 1b), which fits every parameter of a reference type
at the same cost, since its class is not known before the program runs:

```
proc (string, List) string join = nest Java "java.lang.String.join"
```

```
prog.a68:4:38: fatal error: Java formal hole 'java.lang.String.join' is
ambiguous for the mode proc (string, ref struct (int listjavaobject))
string: java.lang.String.join(CharSequence, CharSequence...) and
java.lang.String.join(CharSequence, Iterable) fit equally well
```

Taking one of them would be a guess, and the wrong one fails only when it
is called. To make the formal hole unique, give it a mode that fits only
one overload (`proc (string, [] string) string` fits only the varargs
`join`), or call a small Java method of your own that takes the type you
mean (with `-fjava-classpath`). Two candidates with the same parameters
count as one, such as a method of an interface and the same method of
`Object`.

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

## 1b. Objects of any Java class: constructors, instance methods and fields

A formal hole written as a **method reference** of Java names a
constructor or an instance method. Classes of the JDK and of libraries can
so be used without a wrapper class of their own (section 5):

```
mode Arraylist = ref Arraylist_object;
mode Arraylist_object = struct (int arraylist_java_object);
proc Arraylist new_list = nest Java "java.util.ArrayList::new";
proc (Arraylist, string) bool add = nest Java "java.util.ArrayList::add";
proc (Arraylist, int) string get = nest Java "java.util.ArrayList::get";
proc (Arraylist) int size = nest Java "java.util.ArrayList::size";

Arraylist l = new_list;
add (l, "Algol"); add (l, "68");
puts (get (l, 0) + " " + whole (size (l), 0) + "'n")       { Algol 2 }
```

What `"Class::name"` means, as in Java:

- **`Class::new`:** a public constructor of the class (not of an abstract
  class or an interface). Its parameters are those of the procedure mode,
  and the result is the new object.
- **`Class::name`:** a public instance method `name`, called on the
  **first parameter**, the others are its parameters; or a public static
  method `name` with all the parameters. The methods of `Object`
  (`toString`, `equals` …) also count for an interface.
- As for `"Class.member"` the cheapest fitting one is taken, and several
  equally cheap ones are a compile-time error (section 1a, "Which
  method"); a procedure mode is required. Nested classes are
  written with dots as in Java, e.g. `"java.util.Map.Entry::getKey"` for
  the class `java.util.Map$Entry`, and so also with `"Class.member"`.
- The object may be of other modes too, as far as they fit the class:
  `proc (string) int length = nest Java "java.lang.String::length"`.

**Fields.** If no method or constructor fits, `"Class::name"` is the
public field `name` of the class (also an inherited one). This goes beyond
Java, which has no field references. A procedure with the object as its
only parameter and the type of the field as its result reads the field; a
procedure with the object and a new value as parameters and no result
writes it, unless the field is `final`. For a static field the object is
left out:

```
mode Point = ref Point_object;
mode Point_object = struct (int point_java_object);
proc (int, int) Point new_point = nest Java "java.awt.Point::new";
proc (Point) int x = nest Java "java.awt.Point::x";
proc (Point, int) void set_x = nest Java "java.awt.Point::x";
proc int max_int = nest Java "java.lang.Integer::MAX_VALUE";

Point p = new_point (3, 4);
set_x (p, x (p) + 1);                                 { p.x = p.x + 1 }
```

- A method of the same name wins: a class with a field `size` and a method
  `size()` gives the method for `proc (C) int` and the field only for
  `proc (C, int) void`.
- The modes fit the type of the field as they fit a parameter or result
  (section 1), also for a field of a type variable (the `T value` of a
  `Box<T>` is an `Object`). A name of a row is no new value for a field of
  an array type: the field would keep the copy, and later assignments to
  the row would not reach it.
- Writing a `final` field is a compile-time error:
  `fatal error: no public Java method or field 'java.lang.Integer::MAX_VALUE' fits the mode proc (int) void`.
- A field of `nil` is a runtime error at the call:
  `runtime error: Java field java.awt.Point::x of NIL`.

**Type arguments.** Without them a generic class is a raw type, as in
Java: the `E` of `ArrayList<E>` is `Object`, so `add` takes any value and
`get` as a `string` makes `"5"` of an `Integer` 5 with `toString()`. Type
arguments in the formal hole, written as in Java, say what the type
variables stand for:

```
mode Stringlist = ref Stringlist_object;
mode Stringlist_object = struct (int stringlist_java_object);
proc Stringlist new_list = nest Java "java.util.ArrayList<String>::new";
proc (Stringlist, string) bool add = nest Java "java.util.ArrayList<String>::add";
proc (Stringlist, int) string get = nest Java "java.util.ArrayList<String>::get";
proc (Stringlist, proc (string) void) void for_each = nest Java "java.util.ArrayList<String>::forEach";
proc (Map, string) Stringlist find = nest Java "java.util.Map<String, java.util.List<String>>::get";
```

- **When the program is compiled** the type variables of the class, and
  those of its superclasses and superinterfaces, stand for the type
  arguments in the parameters and the result of the member, also inside
  other types: the `Consumer<? super E>` of `forEach` is a
  `Consumer<? super String>`, so `proc (string) void` fits it exactly.
  The modes must fit these types: `proc (Stringlist, int) bool add = nest
  Java "java.util.ArrayList<String>::add"` is a compile-time error, as
  `list.add(5)` is in Java for a `List<String>`. Of several overloads the
  cheapest is taken with these types.
- **When the program runs** a result whose type is a type variable is
  checked, as javac does with a `checkcast`: an `Integer` that came into
  an `ArrayList<String>` without type arguments is a runtime error at the
  call of `get`, not `"5"`:
  `runtime error: Java value of java.lang.Integer where a java.lang.String is expected`.
  This holds also for what Java passes to a procedure (the `forEach`
  above), and for what a functional interface yields (section 1a).
- **Which types:** a class of `java.lang` by its simple name (`String`,
  `Integer`), any other class by its full name, a nested class with dots
  (`java.util.Map.Entry<String, Integer>`); type arguments of type
  arguments; arrays, also of a primitive type (`java.util.ArrayList<int[]>`,
  whose elements are `[] int`), but not a primitive type alone (`Integer`
  for `int`); wildcards (`?`, `? extends T`, `? super T`) only inside
  another type argument (`java.util.HashMap<String, java.util.List<? extends
  Number>>`), since the class of the formal hole stands for an object.
  The number of type arguments and their bounds are checked:
  `fatal error: the Java class java.util.ArrayList has 1 type parameter, not 2 (in the Java formal hole 'java.util.ArrayList<String, String>')`.
- A **class without type parameters** binds those of its supertypes
  itself, as in Java: the field `value` of `T` is a `String` for a
  `class StringBox extends Box<String>`, also without type arguments.
- **Not covered:** the type variables of a generic method
  (`<T> List<T> nCopies (int n, T o)`, `Collections.<String>nCopies` in
  Java) stay their bounds; an array of a type variable (`E[]`) is not
  checked, since in fact it is often an `Object[]`. Type arguments need a
  method reference (`"Class<...>::member"`).

**The objects.** A Java object whose class does not extend
`a68j.rt.Ref` is an `a68j.rt.JavaHandle` in Algol 68 (a subclass of
`a68j.rt.JavaObject`), a name as in section 2. It wraps the object on the
way to Algol 68, and the Java method gets the object itself back, never
the handle. So:

- Java `null` is `nil`, both ways.
- `is` and `isnt` compare the objects, as `==` in Java: for a
  `StringBuilder` `b`, `append (b, "x") is b`.
- Any object goes where Java wants an `Object` or a type variable (the `E`
  of `ArrayList<E>`), e.g. `HashMap::put` and `HashMap::get` with
  `Arraylist` values. An object of the wrong class is noticed only when it
  is passed to a method that wants another class: a runtime error,
  `runtime error: Java object of java.lang.StringBuilder where a java.util.ArrayList is expected`.
- A method called on `nil` is a runtime error at the call:
  `runtime error: Java method java.util.ArrayList::size called on NIL`.
- Rows of names are arrays of Java objects:
  `proc ([] Builder) List as_list = nest Java "java.util.Arrays.asList"`,
  `proc (List) [] Builder to_row = nest Java "java.util.List::toArray"`.
- Dereferencing and assigning are runtime errors as in section 2; the
  message names the class of the object:
  `attempt to dereference a Java object (java.util.ArrayList)`.
- Java exceptions of constructors and methods are runtime errors as for
  static methods (section 1, "Java exceptions").

Only names of other modes than rows and strings can be Java objects; a
name of a row is passed as an array (section 1a).

**How it is compiled.** An instance method is called with
`invokevirtual`, or `invokeinterface` for an interface, on the class
named in the formal hole. Before the call the object is checked and taken
out of its handle (`JavaConv.receiver`); other parameters of a class type
with `JavaConv.javaObject`, `Object` parameters with `JavaConv.unwrap`.
For a constructor the converted arguments go into locals first, then
`new`, `dup`, the arguments and `invokespecial`. A field is read with
`getfield` or `getstatic` and written with `putfield` or `putstatic`, on
the class named in the formal hole; the object is checked by
`JavaConv.fieldReceiver`. A result whose class
does not extend `Ref` goes through `JavaConv.objRef`, which makes the
handle (`backend/.../jvm/GenJava.java`, `Target`). Type arguments are
parsed by `backend/.../jvm/JavaGenerics.java`; `Target.bound` puts what the
type variables stand for into the types of the parameters and the result,
while the call itself keeps the erased descriptor. A result narrower than
its erased type is checked by `JavaConv.checked` before it is converted.

**Speed.** `java.util.ArrayList` with 1 000 000 `add` and 5 000 000 `get`
of `int` elements (boxed as `Integer`): 0.36 to 0.54 s with the start of
the JVM; the same with the `Intlist` of section 3 (unboxed), 0.21 to
0.25 s.

Tests: `tests/java/nest-java-objects.a68`, `nest-java-objects-*.a68`,
`nest-java-fields.a68`, `nest-java-fields-*.a68`, `nest-java-generics.a68`
and `nest-java-generics-*.a68`.

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

Objects of other Java classes, such as those of the JDK, are wrapped in an
`a68j.rt.JavaHandle` (section 1b).

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
`prog.a68:5: runtime error: index 2 out of range [1:1] of Intlist`
(status 134). The position is that of the operator in the program; the
runtime finds it on the stack (section 1, "Java exceptions").

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
`prog.a68:6: runtime error: key 2 not in Int_string_map` (status 134).

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

For a class whose methods fit as they are, method references are enough
(section 1b). A class of your own is worth it when the operations should
be unboxed, or need more than one call of Java. The recipe after which
`Lists` is built:

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

The formal holes can also be public themselves, for example `pub proc
(Counter) int next = nest Java "my.Counter.next"`. The module class then
has a typed entry for each of them (`pub$i`, `docs/optimizations.md`,
section 2). A program calls it without boxing, as it calls a public
procedure with a routine text. The procedure value of the hole is still
the closure over the bridge method. `tests/java/modules/jholes.a68` is
such a module.

## 6. Tests

`tests/java/`, run by `tools/java_tests.sh [JAVA]`. Options are in the
first line as `{ a68j-options: … }`, environment variables for the run in
an optional second line `{ a68j-env: NAME=VALUE … }`, the expected output
and status in `X.out`. The script first compiles the modules `tests/java/modules/X.a68`
into `tests/java/lib/libX.jar` (with `-fnest-java`), for the programs
that access them (`-I tests/java/lib`).

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
| `nest-java-exception` | a Java exception in a direct call: runtime error at the call, status 134 |
| `nest-java-exception-value` | the same through the procedure value: the position of the call in the procedure that called it |
| `nest-java-exception-cause` | the causes of the exception in the message (`tests/java/demo/Fail.java`) |
| `nest-java-exception-fn` | a Java functional object that throws when it is called as an Algol 68 procedure |
| `nest-java-exception-callback` | a Java exception in a Java method called by a callback: the position of that inner call |
| `nest-java-exception-par` | a unit waiting in `Thread.sleep` when another unit jumps out of the parallel clause: the interruption ends the unit, the jump goes on |
| `nest-java-init-error` | the static initializer of a class throws when a formal hole reads a static field (`tests/java/demo/Broken.java`): runtime error at the formal hole, with the cause |
| `nest-java-trace` | `A68J_JAVA_TRACE=1`: the Java stack trace after the message (the test runner leaves out the frames of the JDK and of a68j, see `tools/java_tests.sh`) |
| `nest-java-objects` | constructors and instance methods of the JDK (section 1b): `ArrayList`, `Iterator`, `String`, `StringBuilder`, `HashMap`, `AbstractMap.SimpleEntry`, `Map.Entry`; `nil` for `null`, `is`, rows of objects, a `Consumer`, procedure values and an operator |
| `nest-java-objects-nil` | an instance method called on `nil` |
| `nest-java-objects-class` | an object of the wrong class, through a procedure value |
| `nest-java-objects-new` | a Java exception in a constructor |
| `nest-java-objects-deref` | dereferencing a Java object of the JDK |
| `nest-java-objects-error` | the constructor of an abstract class: compile-time error |
| `nest-java-fields` | fields through `"Class::field"` (`tests/java/demo/Counter.java`, `Box.java`, `java.awt.Point`): instance and static fields read and written, a `final` field read, `long`, `double` and object fields, `nil` both ways, a field of a type variable, an inherited field, a method that wins over a field of the same name, procedure values |
| `nest-java-fields-nil` | a field of `nil` |
| `nest-java-fields-error` | writing a `final` field: compile-time error |
| `nest-java-generics` | type arguments in formal holes: `ArrayList<String>` with `forEach`, `removeIf` and `sort` (callbacks of `String`s), a `TreeMap` whose values are lists with `computeIfAbsent` and `forEach`, `AbstractMap.SimpleEntry<String, Integer>::new` (parameters `K` and `V`), `ArrayList<int[]>` with rows as elements |
| `nest-java-generics-error` | an `Integer` in an `ArrayList<String>`, read by `get`: runtime error at the call |
| `nest-java-generics-callback` | the same `Integer` passed by `forEach` to a `proc (string) void`: runtime error at the call of `forEach` |
| `nest-java-generics-mode` | an `int` added to an `ArrayList<String>`: compile-time error |
| `nest-java-generics-subclass` | the field of `T` of `StringBox extends Box<String>` read as `int`: compile-time error |
| `nest-java-ambiguous` | `String.join` with a Java object as the second parameter: `join(CharSequence, CharSequence...)` and `join(CharSequence, Iterable)` fit equally well, compile-time error |
| `nest-java-char` | `Character.isDigit`, `getNumericValue` and `toUpperCase` with characters beyond U+FFFF: the overloads for `int` (code points) are taken |
| `nest-java-module` | public formal holes of a module (`tests/java/modules/jholes.a68`), called through their typed entries: numbers, strings, `char`, a name of a row, a procedure as an `IntUnaryOperator`, a discarded `long` result, a constructor, fields and an instance method, operators, procedure values of the holes; the program has no `-fnest-java` |
| `nest-java-module-exception` | a Java exception in a public formal hole of a module: the position of the call in the program |
| `nest-java-module-nil` | a field of `nil` through a public formal hole of a module |
| `lists-basic` | all operations on `Intlist`, `is`, `nil`, `AT` |
| `lists-kinds` | the other five modes, copies in a `Stringlist` |
| `lists-speed` | 1 000 000 elements |
| `lists-error` | an index out of bounds, with the position in the program |
| `lists-nil` | dereferencing a Java object |
| `hashmaps-basic` | all operations on `String_int_map`, slices as keys, names across growth and removal |
| `hashmaps-kinds` | all 18 modes |
| `hashmaps-lists` | `Lists` through `access pub`, `HAS` and `REMOVE` on lists, strings as keys |
| `hashmaps-both` | `access Lists, Hashmaps`: the message as in ga68 |
| `hashmaps-error` | a missing key with `ELEM`, with the position in the program |
| `hashmaps-speed` | 1 000 000 keys |
| `texts-hashmaps` | `Hashmaps`, `Genie_Transput` and `Texts` in one access clause ([texts.md](texts.md)) |
| `regex-a68g` | the module `Regex` on 74 patterns, compared with Algol 68 Genie ([regex.md](regex.md)) |
| `regex-unicode` | `Regex` with characters beyond U+FFFF, bounds and `nil` positions |

Result on 3 Nov 2026: 49 of 49 with JDK 25 and JDK 28 ea+21.

## 7. Open points

- See [java-open-points.md](java-open-points.md).
- Done on 1 Nov 2026: overloads that fit equally well are a compile-time
  error (they were taken in the order of `getMethods()`, so that
  `String.join` with a Java object took the varargs overload and failed
  when it was called), section 1a, "Which method"; procedures as
  parameters (functional interfaces), see section 1a; constructors and instance methods, section 1b; positions
  for the errors of `JavaObject.error`, section 1; fields of objects and
  static fields through `"Class::field"`, section 1b; typed entries for
  public formal holes of a module, section 5; type arguments in formal
  holes (generic element types), section 1b.
