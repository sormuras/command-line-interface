package test.jdk;

import static main.Option.Type.FLAG;
import static main.Option.Type.SINGLE;
import static main.Option.Type.VARARGS;
import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertEqualsOptional;
import static test.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import main.Option;
import main.Splitter;
import main.Value;
import test.api.JTest;
import test.api.JTest.Test;

public class JarValueTests {

  public static void main(String... args) {
    JTest.runTests(new JarValueTests(), args);
  }

  static final Option CREATE_FLAG = FLAG.option("-c", "--create").withHelp("Create an archive");
  static final Option FILE_OPTION = SINGLE.option("-f", "--file").withHelp("The archive file");
  static final Option[] OPTIONS = {CREATE_FLAG, FILE_OPTION, VARARGS.option("files...")};

  private static List<Value> splitInput(String line) {
    return Splitter.of(true, OPTIONS).split(line.split("\\s+"));
  }

  @Test
  void example1() {
    var values = splitInput("--create --file classes.jar Foo.class Bar.class");
    assertEquals(3, values.size());
    var map = values.stream().collect(Collectors.toMap(Value::option, Function.identity()));
    assertTrue(map.containsKey(CREATE_FLAG));
    assertEqualsOptional("classes.jar", ((Value.SingleValue) map.get(FILE_OPTION)).value());
    assertTrue(values.contains(new Value.VarargsValue(OPTIONS[2], "Foo.class", "Bar.class")));
  }
}
