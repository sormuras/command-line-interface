# Arguments Splitter

An Arguments Splitter splits structured string arrays (`String[]`) into instances of user-defined option `record` types.
It can be used to model and handle command-line APIs of Java programs.

## Principles

Option structures are described using Java types.

- Optional flags are described by `boolean` and `Boolean` types
- Optional key-value pairs are described by the `Optional<String>` type
- Optional repeatable key-value options are described by the `List<String>` type
- Required (positional) options are described by the `String` type
- Variadic options are described by the `String...` type
 
Nested option structures can be described by custom `record` types.
Nested option structures can only be used for key-value pairs and repeatable key-value options.

By convention, a `_` character in record component names is mapped to `-` character on the command-line.

In addition to the above conventions record components can be annotated with:

- `@Name` in order to specify names that aren't legal Java names or to bind multiple names to one record component
- `@Help` in order to provide a help description

Potential mapping to non-`String` or boolean types is meant to be done programmatically by user code after the input has been mapped to an options record.

## Usage

An option `record` is defined for a tool, here unix's `wc` as an example:

```java
record WordCountOptions(
    @Name({"-c", "--bytes"})           boolean bytes,
    @Name({"-m", "--chars"})           boolean chars,
    @Name({"-l", "--lines"})           boolean lines,
    @Name("--files0-from")             Optional<String> files0From,
    @Name({"-L", "--max-line-length"}) boolean maxLineLength,
    @Name({"-w", "--words"})           boolean words,
    @Name("--help")                    boolean help,
    @Name("--version")                 boolean version,
    /* unnamed */                      String... files
) {}
```

A splitter is created for the `WordCountOptions` definition and command-line arguments are processed:

```java
class Program {
  public static void main(String[] args) {
    var splitter = ArgumentsSplitter.of(WordCountOptions.class, MethodHandles.lookup());
    WordCountOptions options = splitter.split(args);
    // use options instance...
  }
}
```

## Patterns

Advanced command-line options structures can be built in two or more ways.

- By using `Optional` or `List` of nested option `record`s:

```java
record JarOptions(
    //...
    @Name("-C")                        Optional<ChangeDirOptions> changeDir,
    //...
    @Name("--release")                 List<ReleaseOptions> releases
) {
    record ChangeDirOptions(String dir, String file) {}
    record ReleaseOptions(String version, List<ChangeDirOptions> changeDirs) {}
}
```

- By utilising variadic options to continue programmatically:

```java
class Program {
  record MainOptions(boolean __verbose, String dir, String mode, String... rest) {}
  record ModeXOptions(boolean __fast, List<String> __file) {}
  record ModeYOptions(Optional<String> __algorithm, String... files) {}

  public static void main(String... args) {
    var mainOptions = splitter(lookup(), MainOptions.class).split(args);
    var modeOptions = switch (mainOptions.mode()) {
      case "x" -> splitter(lookup(), ModeXOptions.class).split(mainOptions.rest());
      case "y" -> splitter(lookup(), ModeYOptions.class).split(mainOptions.rest());
    };      
  }
}
```

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
