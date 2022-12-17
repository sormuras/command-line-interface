package test.jdk;

import static main.Option.Type.FLAG;
import static main.Option.Type.SINGLE;
import static main.Option.Type.VARARGS;
import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertEqualsOptional;

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

  static final Option<?> CREATE_FLAG = FLAG.option("-c", "--create").withHelp("Create an archive");
  static final Option<?> FILE_OPTION = SINGLE.option("-f", "--file").withHelp("The archive file");
  static final Option<?>[] OPTIONS = {CREATE_FLAG, FILE_OPTION, VARARGS.option("files...")};

  private static Map<String, Value> splitInput(String line) {
    return Splitter.of(OPTIONS).split(line.split("\\s+"));
  }

  @Test
  void example1() {
    var values = splitInput("--create --file classes.jar Foo.class Bar.class");
    assertEquals(Set.of("-c", "--create", "-f", "--file", "files..."), values.keySet());
    assertEqualsOptional("classes.jar", ((Value.SingleValue) values.get("-f")).value());
    assertEquals(
        new Value.VarargsValue(OPTIONS[2], "Foo.class", "Bar.class"), values.get("files..."));
  }
}
