package test;

import static java.lang.invoke.MethodHandles.lookup;
import static test.Assertions.*;

import java.lang.annotation.RetentionPolicy;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import main.CommandLineInterface;
import main.CommandLineInterface.ArgumentsProcessor;
import main.CommandLineInterface.Cardinality;
import main.CommandLineInterface.Name;
import main.CommandLineInterface.Parser;

class CommandLineInterfaceTests implements JTest {

  public static void main(String... args) {
    new CommandLineInterfaceTests().runTests(args);
  }

  @Test
  void empty() {
    record Options() {}
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new CommandLineInterface.Parser<>(lookup(), Options.class));
    assertEquals("At least one option is expected", ex.getMessage());
  }

  @Test
  void varargs() {
    record Options(String... more) {
      static Options parse(String... args) {
        var parser = new Parser<>(lookup(), Options.class, ArgumentsProcessor.IDENTITY);
        return parser.parse(args);
      }
    }
    assertArrayEquals(new String[0], Options.parse().more());
    assertArrayEquals(new String[] {""}, Options.parse("").more());
    assertArrayEquals(new String[] {" "}, Options.parse(" ").more());
    assertArrayEquals(new String[] {"a"}, Options.parse("a").more());
    assertArrayEquals(new String[] {"b1", "b2"}, Options.parse("b1", "b2").more());
  }

  @Test
  void demo() throws Exception {
    record Options(
        @Name("-v") boolean verbose,
        Optional<String> __say,
        String thread_state,
        Optional<String> __time,
        List<String> __policies,
        String... names) {

      static final Parser<Options> PARSER = new Parser<>(lookup(), Options.class);

      Thread.State state() {
        return CommandLineInterface.findEnum(Thread.State.class, thread_state).orElseThrow();
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
    assertTrue(options.verbose);
    assertEquals("Hallo", options.__say.orElse("Hi"));
    assertEquals(Thread.State.NEW, options.state());
    assertEquals(TimeUnit.MINUTES, options.time());
    assertEquals(
        List.of(RetentionPolicy.RUNTIME, RetentionPolicy.CLASS, RetentionPolicy.SOURCE),
        options.policies());
    assertEquals(List.of("Joe", "Jim"), List.of(options.names));
    assertTrue(Options.PARSER.help().isEmpty(), "No @Help, no help()");
  }

  @Test
  void conventional() {
    record Options(boolean _flag, Optional<String> _key, List<String> _list, String... more) {
      static Options of(String... args) {
        return CommandLineInterface.parser(lookup(), Options.class).parse(args);
      }
    }
    var options = Options.of("-flag", "-key", "value", "-list", "a", "-list=b,o", "1", "2", "3");
    assertTrue(options._flag());
    assertEqualsOptional("value", options._key());
    assertEquals(List.of("a", "b", "o"), options._list());
    assertArrayEquals(new String[] {"1", "2", "3"}, options.more());
  }

  @Test
  void positional() {
    record Options(boolean a, String first, boolean b, String second, boolean c) {}
    var objects = CommandLineInterface.parser(lookup(), Options.class).parse("one", "two");
    assertEquals(new Options(false, "one", false, "two", false), objects);
  }

  @Test
  void cardinality() {
    record Options(@Name("-2") @Cardinality(2) List<String> take2) {}
    var parser = CommandLineInterface.parser(lookup(), Options.class);
    assertEquals(List.of("a", "b"), parser.parse("-2", "a", "b").take2());
    assertEquals(List.of("a", "b", "c", "d"), parser.parse("-2", "a", "b", "-2", "c", "d").take2());
  }

  @Test
  void flags() {
    record Options(boolean _f, boolean _h, boolean _z) {}
    var parser = CommandLineInterface.parser(lookup(), Options.class);
    var options = parser.parse("-zfh");
    assertEquals(true, options._f);
    assertEquals(true, options._h);
    assertEquals(true, options._z);
  }

  @Test
  void nested_keyValue() {
    record SubOptions(String dir, String file) {}
    record MainOptions(boolean __flag, Optional<SubOptions> __release, String... rest) {}
    var parser = CommandLineInterface.parser(lookup(), MainOptions.class);
    var options = parser.parse("--release dirX fileX --flag and the rest".split(" "));
    assertEquals(true, options.__flag);
    assertEquals("dirX", options.__release.orElseThrow().dir());
    assertEquals("fileX", options.__release.orElseThrow().file());
    assertEquals(List.of("and", "the", "rest"), List.of(options.rest));
  }

  @Test
  void nested_repeatable() {
    record SubOptions(String dir, String file) {}
    record MainOptions(boolean __flag, List<SubOptions> __release, String... rest) {}
    var parser = CommandLineInterface.parser(lookup(), MainOptions.class);
    var options =
        parser.parse("--release dirX fileX --flag --release dirY fileY and the rest".split(" "));
    assertEquals(true, options.__flag);
    assertEquals(2, options.__release.size());
    assertEquals("dirX", options.__release.get(0).dir());
    assertEquals("fileX", options.__release.get(0).file());
    assertEquals("dirY", options.__release.get(1).dir());
    assertEquals("fileY", options.__release.get(1).file());
    assertEquals(List.of("and", "the", "rest"), List.of(options.rest));
  }
}
