package test.jdk;

import static test.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Stream;
import test.api.JTest;
import test.api.JTest.Test;

/** Not using Splitter here, for the time being. */
public class JarSealedTests {

  public static void main(String... args) {
    JTest.runTests(new JarSealedTests(), args);
  }

  sealed interface Option {
    enum Mode {
      CREATE,
      UPDATE,
      LIST;

      Operation toOption() {
        return new Operation(this);
      }
    }

    record Operation(Mode mode) implements Option {}

    record File(Path jarFile) implements Option {}

    record Verbose() implements Option {}

    record Date(ZonedDateTime date) implements Option {}

    record Help() implements Option {}

    record ShowVersionAndExit() implements Option {}

    record Arg(Path path) implements Option {}

    static Option parse(String token, Scanner scanner) {
      return switch (token) {
        case "-c", "--create" -> new Operation(Mode.CREATE);
        case "-l", "--list" -> new Operation(Mode.LIST);
        case "-u", "--update" -> new Operation(Mode.UPDATE);
        case "--verbose", "-v" -> new Verbose();
        case "--help", "-h", "-?", "/?" -> new Help();
        case "--version" -> new ShowVersionAndExit();
        case "--release" -> throw new UnsupportedOperationException();
        case "--file", "-f" -> new File(Path.of(scanner.next()));
        case "--date" -> new Date(ZonedDateTime.parse(scanner.next()));
        default -> new Arg(Path.of(token));
      };
    }
  }

  record Options(List<Option> arguments) {
    Options() {
      this(List.of());
    }

    Options add(Option option) {
      if (option instanceof Option.Operation) {
        if (operation().isPresent()) throw new IllegalStateException("Mode already present");
      }
      return new Options(Stream.concat(arguments.stream(), Stream.of(option)).toList());
    }

    Options parse(String line) {
      if (line.isBlank()) return this;
      var delimiter = "\u0000";
      var source = String.join(delimiter, line.split("\\s+"));
      var scanner = new Scanner(source).useDelimiter(delimiter);
      var options = this;
      while (scanner.hasNext()) {
        var token = scanner.next();
        var parsed = Option.parse(token, scanner);
        options = options.add(parsed);
      }
      return options;
    }

    Optional<Option.Operation> operation() {
      return arguments.stream()
          .filter(Option.Operation.class::isInstance)
          .map(Option.Operation.class::cast)
          .findAny();
    }
  }

  @Test
  void example1() {
    var actual = new Options().parse("--create --file classes.jar Foo.class Bar.class");
    var expected =
        new Options(
            List.of(
                Option.Mode.CREATE.toOption(),
                new Option.File(Path.of("classes.jar")),
                new Option.Arg(Path.of("Foo.class")),
                new Option.Arg(Path.of("Bar.class"))
            )
        );
    assertEquals(expected, actual);
  }

  @Test
  void example2() {
    var actual =
        new Options()
            .parse(
                """
                --update
                --date 2021-01-06T14:36:00+02:00
                --file classes.jar
                Foo.class
                Bar.class
                """);
    var expected =
        new Options()
            .add(new Option.Operation(Option.Mode.UPDATE))
            .add(new Option.Date(ZonedDateTime.parse("2021-01-06T14:36:00+02:00")))
            .add(new Option.File(Path.of("classes.jar")))
            .add(new Option.Arg(Path.of("Foo.class")))
            .add(new Option.Arg(Path.of("Bar.class")));
    assertEquals(expected, actual);
  }
}
