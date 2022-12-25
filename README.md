# Arguments Splitter

An Arguments [Splitter](main/main/Splitter.java) splits structured string arrays (`String[]`) into different values
following a [Schema](main/main/Schema.java). A schema is defined by a list of [Option](main/main/Option.java)s
specifying how to transform the strings into argument values.

There are two pre-defined schemas, a record based schema that uses convention oven configuration and
an options based schema that offers a programmatic API.

## Record based schema

Defining the Unix's `wc` as an example:
```java
record WordCountOptions(
    @Name({"-c", "--bytes"})           boolean bytes,
    @Name({"-m", "--chars"})           boolean chars,
    @Name({"-l", "--lines"})           boolean lines,
    @Name("--files0-from")             Optional<File> files0From,
    @Name({"-w", "--words"})           boolean words,
    /* unnamed */                      File... files
) {}

var splitter = Splitter.of(MethodHandles.lookup(), WordCountOptions.class);
WordCountOptions options = splitter.split(args);
// use options instance ...
```

Option structures are described using Java types.

- Optional flag options are described by `boolean` and `Boolean` types
- Optional single key-value options are described by the `Optional<String>` type
- Optional repeatable key-value options are described by the `List<String>` type
- Positional required options are described by the `String` type
- Variadic options are described by the `String...` type
 
Nested option structures are single key-value option or repeatable key-value options
described by custom `record` types.

By convention, a `_` character in record component names is mapped to `-` character on the command-line.

In addition to the above conventions record components can be annotated with:

- `@Name` in order to specify names that aren't legal Java names or to bind multiple names to one record component
- `@Help` in order to provide a help description

Mapping to non-`String` or boolean types is done through a [converter resolver](main/main/ConverterResolver.java).

## Options based schema

Defining the Unix's `wc` using the type-safe programmatic API:
```java
var bytes = Option.flag("-c", "--bytes");
var lines = Option.flag("-l", "--lines");
var files0From = Option.single("--files0-from").convert(Path::of);
var words = Option.flag("-w", "--words");
var files = Options.varargs("files").convert(Path::of);

var splitter = Splitter.of(bytes, lines, files0From, words, files);
ArgumentMap argumentMap = splitter.split(args);
// use argumentMap instance ...
```

An option is an immutable class with one or more names, a value converter, and optionally a help text
and a nested schema.

## Build

How to build this project and run tests.

Setup JDK 17 or later, and on the command-line run:

```shell
# run all tests
java build/build.java

# run single test class
java build/build.java JarTests

# run one or more methods of a single test class
java build/build.java JarTests example2 example4
```

In an IDE:

- run as java application (`main` method)
- use name(s) of methods as arguments to limit

## Credits

This project is inspired by https://github.com/forax/argvester
