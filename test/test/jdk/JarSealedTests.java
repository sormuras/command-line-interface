package test.jdk;

import static test.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import test.api.JTest;
import test.api.JTest.Test;

/** Not using Splitter here, for the time being. */
public class JarSealedTests {

  public static void main(String... args) {
    JTest.runTests(new JarSealedTests(), args);
  }

  sealed interface Option {
    record CreateArchive() implements Option {}

    record File(Path jarFile) implements Option {}

    record Verbose() implements Option {}

    record Date(ZonedDateTime date) implements Option {}

    record Help() implements Option {}

    record ShowVersionAndExit() implements Option {}

    record Arg(Path path) implements Option {}

    static Option parse(String token, Scanner scanner) {
      return switch (token) {
        case "-c", "--create" -> new CreateArchive();
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
    static Options of(String line) {
      if (line.isBlank()) return new Options(List.of());
      var options = new ArrayList<Option>();
      var delimiter = "\u0000";
      var source = String.join(delimiter, line.split("\\s+"));
      var scanner = new Scanner(source).useDelimiter(delimiter);
      while (scanner.hasNext()) {
        var token = scanner.next();
        var parsed = Option.parse(token, scanner);
        options.add(parsed);
      }
      return new Options(List.copyOf(options));
    }
  }

  @Test
  void example1() {
    var options = Options.of("--create --file classes.jar Foo.class Bar.class");
    var expected =
        List.of(
            new Option.CreateArchive(),
            new Option.File(Path.of("classes.jar")),
            new Option.Arg(Path.of("Foo.class")),
            new Option.Arg(Path.of("Bar.class")));
    assertEquals(expected, options.arguments());
  }

  @Test
  void example2() {
    var options =
        Options.of(
            """
            --create
            --date 2021-01-06T14:36:00+02:00
            --file classes.jar
            Foo.class
            Bar.class
            """);

    var expected =
        List.of(
            new Option.CreateArchive(),
            new Option.Date(ZonedDateTime.parse("2021-01-06T14:36:00+02:00")),
            new Option.File(Path.of("classes.jar")),
            new Option.Arg(Path.of("Foo.class")),
            new Option.Arg(Path.of("Bar.class")));
    assertEquals(expected, options.arguments());
  }
}
