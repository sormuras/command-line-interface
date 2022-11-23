import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
            @Name("-v") boolean verbose, @Name("--say") Optional<String> greet, String... names)
        implements CommandLineInterface {
      static Parser<Options> parser() {
        return new Parser<>(MethodHandles.lookup(), Options.class);
      }
    }

    var parser = Options.parser();

    var options = parser.parse("-v", "--say=Hallo", "Joe", "Jim");
    assert options.verbose;
    assert "Hallo".equals(options.greet.orElse("Hi"));
    assert List.of("Joe", "Jim").equals(List.of(options.names));
    assert parser.help().isEmpty() : "No @Help, no help()";
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
