import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

class CommandLineInterfaceTests {

  public static void main(String[] args) {
    new CommandLineInterfaceTests().all();
  }

  void all() {
    empty();
    conventional();
    demo();
    positional();
  }

  void empty() {
    record Empty() {}
    var empty = new CommandLineInterface.Parser<>(MethodHandles.lookup(), Empty.class).parse();
    assert 0 == empty.hashCode();
  }

  void demo() {
    record Options(
        @Name("-v") boolean verbose,
        Optional<String> __say,
        String thread_state,
        Optional<String> __time,
        List<String> __policies,
        String... names)
        implements CommandLineInterface {

      static final Parser<Options> PARSER = new Parser<>(MethodHandles.lookup(), Options.class);

      TimeUnit time() {
        return __time.map(TimeUnit::valueOf).orElse(TimeUnit.NANOSECONDS);
      }

      List<RetentionPolicy> policies() {
        return __policies.stream().map(RetentionPolicy::valueOf).toList();
      }
    }

    var options =
        Options.PARSER.parse(
            """
            --policies
              RUNTIME
            -v
            NEW
            --say
              Hallo
            --time=MINUTES
            --policies=SOURCE
            Joe
            Jim"""
                .lines());
    assert options.verbose;
    assert "Hallo".equals(options.__say.orElse("Hi"));
    assert Thread.State.NEW == Thread.State.valueOf(options.thread_state);
    assert TimeUnit.MINUTES == options.time();
    assert List.of(RetentionPolicy.RUNTIME, RetentionPolicy.SOURCE).equals(options.policies());
    assert List.of("Joe", "Jim").equals(List.of(options.names));
    assert Options.PARSER.help().isEmpty() : "No @Help, no help()";
  }

  void conventional() {
    record Options(boolean _flag, Optional<String> _key, List<String> _list, String... more) {
      static Options of(String... args) {
        return CommandLineInterface.parse(MethodHandles.lookup(), Options.class, args);
      }
    }
    var options = Options.of("-flag", "-key", "value", "-list", "a", "-list=o", "1", "2", "3");
    assert options._flag();
    assert Optional.of("value").equals(options._key());
    assert List.of("a", "o").equals(options._list());
    assert Arrays.equals(new String[] {"1", "2", "3"}, options.more());
  }

  void positional() {
    record Options(boolean a, String first, boolean b, String second, boolean c) {}
    var objects = CommandLineInterface.parse(MethodHandles.lookup(), Options.class, "one", "two");
    assert new Options(false, "one", false, "two", false).equals(objects);
  }
}
