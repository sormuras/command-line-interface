package test;

import main.ConverterResolver;
import main.Name;
import main.Option;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.lang.invoke.MethodHandles.lookup;
import static main.ConverterResolver.stringConverter;
import static main.ConverterResolver.when;
import static test.api.Assertions.assertAll;
import static test.api.Assertions.assertEquals;

class ConverterTests {
  public static void main(String... args) {
    JTest.runTests(new ConverterTests(), args);
  }

  @Test
  void simple() {
    var version = Option.single("-v", "--version")
        .map(opt -> opt.map(Integer::parseInt));
    var rest = Option.varargs("rest")
        .map(array -> Arrays.stream(array).map(Path::of).toArray(Path[]::new));
    var splitter = Splitter.ofArgument(version, rest);

    var bag = splitter.split("--version", "12", "foo.txt");
    assertAll(
        () -> assertEquals(12, version.argument(bag).orElseThrow()),
        () -> assertEquals(List.of(Path.of("foo.txt")), List.of(rest.argument(bag)))
    );
  }

  @Test
  void userDefinedConverter() {
    record CmdLineOption(
      @Name({"-v", "--version"})
      Optional<Integer> version,
      Path... rest
    ) { }

    var resolver = ConverterResolver.of(ConverterResolver::basic)
        .or(when(Integer.class, stringConverter(Integer::parseInt)))
        .or(when(Path.class, stringConverter(Path::of)))
        .unwrap();
    var splitter = Splitter.of(lookup(), CmdLineOption.class, resolver);

    var cmdLineOption = splitter.split("--version", "12", "foo.txt");
    assertAll(
        () -> assertEquals(12, cmdLineOption.version.orElseThrow()),
        () -> assertEquals(List.of(Path.of("foo.txt")), List.of(cmdLineOption.rest))
    );
  }

  @Test
  void onlyEnumUserDefinedConverter() {
    enum Level { warning, error }

    record CmdLineOption(
        Optional<Level> __level,
        String... rest
    ) { }

    var resolver = ConverterResolver.of(ConverterResolver::basic)
        .or(ConverterResolver::enumerated)
        .unwrap();
    var splitter = Splitter.of(lookup(), CmdLineOption.class, resolver);

    var cmdLineOption = splitter.split("--level", "warning", "foo.txt");
    assertAll(
        () -> assertEquals(Level.warning, cmdLineOption.__level.orElseThrow()),
        () -> assertEquals(List.of("foo.txt"), List.of(cmdLineOption.rest))
    );
  }
}
