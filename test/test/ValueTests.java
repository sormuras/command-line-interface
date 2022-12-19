package test;

import static test.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;

import main.Option;
import main.Value;
import test.api.JTest;
import test.api.JTest.Test;

class ValueTests {
  public static void main(String... args) {
    JTest.runTests(new ValueTests(), args);
  }

  @Test
  void test() {
    var flag = Option.ofFlag("-f", "--flag");
    var text = Option.ofSingle( "-t", "--text");
    var required = Option.ofRequired(Path.class, Path::of, "-r");

    Map<String, Value<?>> values = Value.splitter(flag, text, required).split("-f", "value");
    assertEquals(new Value.FlagValue(flag, true), values.get("-f"));
    assertEquals(new Value.FlagValue(flag, true), values.get("--flag"));
    assertEquals(null, values.get("-t"));
    assertEquals(null, values.get("--text"));
    assertEquals(new Value.RequiredValue(required, Path.of("value")), values.get("-r"));
  }
}
