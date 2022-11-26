# command-line-interface
Parses `main(String... args)` into `record`s.

### Principles
Options can be described by using Java types only.

- `boolean`/`Boolean` for flags
- `String` for any other value
  - `Optional<String>` for optional key-value pairs
  - `List<String>` for repeatable key-values 
  - `String...` for variadic values (last)
- structuring using `record`s
  - `_` in record component names becomes `-`

Optionally record components can be annotated

- `@Name` to specify names that aren't legal Java names or to bind multiple names to one record component
- `@Help` to provide a help description

Potential mapping to non-`String` or boolean types is meant to be done programmatically by user code after the input has been mapped to an options record.

### Usage
A `record` is defined for the tool, here unix's `wc` as an example:

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

A parser is created from the `WordCountOptions` definition and the command-line arguments are parsed:

```java
class Program {
  public static void main(String[] args) {
    CommandLineParser<WordCountOptions> parser = CommandLineParser.parser(MethodHandles.lookup(), WordCountOptions.class);
    WordCountOptions options = parser.parse(args);
    // use options instance
  }
}
```

### Patterns
Advanced command line options structures can be build in (at least) two ways.

- By using `Optional` or `List` or nested option `record`s:

```java
record JarOptions(
    // ...
    @Name("-C")                        Optional<ChangeDirOptions> changeDir,
    // ...
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
    var mainOptions = parser(lookup(), MainOptions.class).parse(args);
    var modeOptions = switch (mainOptions.mode()) {
      case "x" -> parser(lookup(), ModeXOptions.class).parse(mainOptions.rest());
      case "y" -> parser(lookup(), ModeYOptions.class).parse(mainOptions.rest());
    };      
  }
}
```

### Running Tests
How to run tests for this project.

On command line:
```shell
# run all tests
java build/build.java

# run single test class
java build/build.java JarTests

# run one or more methods of a single test class
java build/build.java JarTests example2 example4
```

In IDE:
- run as java application (`main` method)
- use name(s) of methods as arguments to limit

### Credits

This project is inspired by https://github.com/forax/argvester
