package test;

import main.ConverterResolver;
import main.ConverterResolver.TypeReference;
import main.Name;
import main.Option;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static java.lang.invoke.MethodHandles.lookup;
import static main.ConverterResolver.stringConverter;
import static main.ConverterResolver.when;
import static test.api.Assertions.assertAll;
import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertFalse;

class ConverterTests {
  public static void main(String... args) {
    JTest.runTests(new ConverterTests(), args);
  }

  @Test
  void simple() {
    var version = Option.single("-v", "--version")
        .convert(Integer::parseInt);
    var rest = Option.varargs("rest")
        .convert(Path::of, Path[]::new);
    var splitter = Splitter.of(version, rest);

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
    enum Level { warning }

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

  @Test
  void optionsWithUserDefinedConverter() {
    var resolver = ConverterResolver.of(ConverterResolver::basic)
        .or(when(Integer.class, stringConverter(Integer::parseInt)))
        .or(when(Path.class, stringConverter(Path::of)))
        .unwrap();
    var lookup = lookup();


    var flag = new Option.Flag(List.of("--flag"),
        resolver.resolve(lookup, new TypeReference<Boolean>() {}).orElseThrow()::apply);
    var version = new Option.Single<>(List.of("-v", "--version"),
        resolver.resolve(lookup, new TypeReference<Optional<Integer>>() {}).orElseThrow());
    var input = new Option.Repeatable<>(List.of("--input"),
        resolver.resolve(lookup, new TypeReference<List<String>>() {}).orElseThrow());
    var message = new Option.Required<>(List.of("message"),
        resolver.resolve(lookup, new TypeReference<String>() {}).orElseThrow());
    var rest = new Option.Varargs<>(List.of("rest"),
        resolver.resolve(lookup, new TypeReference<Path[]>() {}).orElseThrow());
    var splitter = Splitter.of(flag, version, input, message, rest);

    var bag = splitter.split("--version", "12", "hello", "foo.txt");
    assertAll(
        () -> assertFalse(flag.argument(bag)),
        () -> assertEquals(12, version.argument(bag).orElseThrow()),
        () -> assertEquals(List.of(), input.argument(bag)),
        () -> assertEquals("hello", message.argument(bag)),
        () -> assertEquals(List.of(Path.of("foo.txt")), List.of(rest.argument(bag)))
    );
  }

  @Test
  void optionsWithTheDefaultConverter() {
    var resolver = ConverterResolver.defaultResolver();
    var lookup = lookup();

    var flag = new Option.Flag(List.of("--flag"),
        resolver.resolve(lookup, new TypeReference<Boolean>() {}).orElseThrow()::apply);
    var version = new Option.Single<>(List.of("-v", "--version"),
        resolver.resolve(lookup, new TypeReference<Optional<Integer>>() {}).orElseThrow());
    var input = new Option.Repeatable<>(List.of("--input"),
        resolver.resolve(lookup, new TypeReference<List<String>>() {}).orElseThrow());
    var message = new Option.Required<>(List.of("message"),
        resolver.resolve(lookup, new TypeReference<String>() {}).orElseThrow());
    var rest = new Option.Varargs<>(List.of("rest"),
        resolver.resolve(lookup, new TypeReference<Path[]>() {}).orElseThrow());
    var splitter = Splitter.of(flag, version, input, message, rest);

    var bag = splitter.split("--version", "12", "hello", "foo.txt");
    assertAll(
        () -> assertFalse(flag.argument(bag)),
        () -> assertEquals(12, version.argument(bag).orElseThrow()),
        () -> assertEquals(List.of(), input.argument(bag)),
        () -> assertEquals("hello", message.argument(bag)),
        () -> assertEquals(List.of(Path.of("foo.txt")), List.of(rest.argument(bag)))
    );
  }
}
