package test.jdk;

import static test.api.Assertions.assertAll;
import static test.api.Assertions.assertArrayEquals;
import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertTrue;

import java.nio.file.Path;
import main.Option;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

public class JarOptionTests {

  public static void main(String... args) {
    JTest.runTests(new JarOptionTests(), args);
  }

  @Test
  void example1() {
    var createFlag = Option.flag("-c", "--create").help("Create an archive");
    var fileSingle = Option.single("-f", "--file").help("The archive file");
    var filesVarargs = Option.varargs("files...").convert(Path::of, Path[]::new);

    var line = "--create --file classes.jar Foo.class Bar.class";
    var argumentMap = Splitter.of(createFlag, fileSingle, filesVarargs).split(line.split("\\s+"));
    assertAll(
        () -> assertTrue(argumentMap.argument(createFlag)),
        () -> assertEquals("classes.jar", argumentMap.argument(fileSingle).orElseThrow()),
        () ->
            assertArrayEquals(
                new Path[] {Path.of("Foo.class"), Path.of("Bar.class")},
                argumentMap.argument(filesVarargs)));
  }
}
