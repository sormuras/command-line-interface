package test.jdk;

import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertEqualsOptional;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import main.Option;
import main.Splitter;
import main.Value;
import test.api.JTest;
import test.api.JTest.Test;

public class JarValueTests {

  public static void main(String... args) {
    JTest.runTests(new JarValueTests(), args);
  }

  static final Option<?> CREATE_FLAG =
      Option.flag("-c", "--create").help("Create an archive");
  static final Option<?> FILE_OPTION = Option.single("-f", "--file").help("The archive file");
  static final Option<Path[]> FILES_OPTION = Option.varargs("files...")
      .map(filenames -> Arrays.stream(filenames).map(Path::of).toArray(Path[]::new));

  private static Map<String, Value<?>> splitInput(String line) {
    return Splitter.of(CREATE_FLAG, FILE_OPTION, FILES_OPTION).split(line.split("\\s+"));
  }

  @Test
  void example1() {
    var values = splitInput("--create --file classes.jar Foo.class Bar.class");
    assertEquals(Set.of("-c", "--create", "-f", "--file", "files..."), values.keySet());
    assertEqualsOptional("classes.jar", ((Value.SingleValue) values.get("-f")).value());
    assertEquals(
        new Value.VarargsValue(FILES_OPTION, Path.of("Foo.class"), Path.of("Bar.class")),
        values.get("files..."));
  }
}
