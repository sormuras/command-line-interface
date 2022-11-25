import static java.lang.invoke.MethodHandles.lookup;

import java.lang.annotation.RetentionPolicy;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CommandLineInterfaceTests implements TestRunner {

  public static void main(String[] args) throws Exception {
    new CommandLineInterfaceTests().runTests();
  }

  @Test
  void empty() {
    record Options() {}
    try {
      new CommandLineInterface.Parser<Options>(lookup(), Options.class);
      throw new AssertionError();
    } catch (IllegalArgumentException expected) {
      assert "At least one option expected".equals(expected.getMessage());
    }
  }

  @Test
  void varargs() {
    record Options(String... more) implements CommandLineInterface {
      static Options parse(String... args) {
        var parser =
            new CommandLineInterface.Parser<>(lookup(), Options.class, ArgumentsProcessor.IDENTITY);
        return parser.parse(args);
      }
    }
    assert Arrays.equals(new String[0], Options.parse().more());
    assert Arrays.equals(new String[] {""}, Options.parse("").more());
    assert Arrays.equals(new String[] {" "}, Options.parse(" ").more());
    assert Arrays.equals(new String[] {"a"}, Options.parse("a").more());
    assert Arrays.equals(new String[] {"b1", "b2"}, Options.parse("b1", "b2").more());
  }

  @Test
  void demo() throws Exception {
    record Options(
        @Name("-v") boolean verbose,
        Optional<String> __say,
        String thread_state,
        Optional<String> __time,
        List<String> __policies,
        String... names)
        implements CommandLineInterface {

      static final Parser<Options> PARSER = new Parser<>(lookup(), Options.class);

      Thread.State state() {
        return findEnum(Thread.State.class, thread_state).orElseThrow();
      }

      TimeUnit time() {
        return __time.map(TimeUnit::valueOf).orElse(TimeUnit.NANOSECONDS);
      }

      List<RetentionPolicy> policies() {
        return __policies.stream().map(RetentionPolicy::valueOf).toList();
      }
    }

    var file =
        Files.writeString(
            Files.createTempFile("demo-", ".txt"),
            """
            # single-line comments are ignored

            --policies=CLASS

            """);

    var options =
        Options.PARSER.parse(
            """
            --policies
              RUNTIME
            -v
            NEW
            @%s
            --say
              Hallo
            --time=MINUTES
            --policies=SOURCE
            Joe
            Jim"""
                .formatted(file)
                .lines());
    assert options.verbose;
    assert "Hallo".equals(options.__say.orElse("Hi"));
    assert Thread.State.NEW == options.state();
    assert TimeUnit.MINUTES == options.time();
    assert List.of(RetentionPolicy.RUNTIME, RetentionPolicy.CLASS, RetentionPolicy.SOURCE)
        .equals(options.policies());
    assert List.of("Joe", "Jim").equals(List.of(options.names));
    assert Options.PARSER.help().isEmpty() : "No @Help, no help()";
  }

  @Test
  void conventional() {
    record Options(boolean _flag, Optional<String> _key, List<String> _list, String... more) {
      static Options of(String... args) {
        return CommandLineInterface.parser(lookup(), Options.class).parse(args);
      }
    }
    var options = Options.of("-flag", "-key", "value", "-list", "a", "-list=o", "1", "2", "3");
    assert options._flag();
    assert Optional.of("value").equals(options._key());
    assert List.of("a", "o").equals(options._list());
    assert Arrays.equals(new String[] {"1", "2", "3"}, options.more());
  }

  @Test
  void positional() {
    record Options(boolean a, String first, boolean b, String second, boolean c) {}
    var objects = CommandLineInterface.parser(lookup(), Options.class).parse("one", "two");
    assert new Options(false, "one", false, "two", false).equals(objects);
  }

  @Test
  void cardinality() {
    record CardOptions(@Name("-2") @Cardinality(2) List<String> take2) implements CommandLineInterface {}
    var parser = CommandLineInterface.parser(lookup(), CardOptions.class);
    var options = parser.parse("-2", "a", "b");
    assert List.of("a", "b").equals(parser.parse("-2", "a", "b").take2());
    assert List.of("a", "b", "c", "d").equals(parser.parse("-2", "a", "b", "-2", "c", "d").take2());
  }

  @Test
  void flags() {
    record FlagOptions(boolean _f, boolean _h, boolean _z) {}
    var parser = CommandLineInterface.parser(lookup(), FlagOptions.class);
    var options = parser.parse("-zfh");
    assertEquals(true, options._f);
    assertEquals(true, options._h);
    assertEquals(true, options._z);
  }
}
