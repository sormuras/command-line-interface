package test;

import static main.Option.Type.FLAG;
import static main.Option.Type.REQUIRED;
import static main.Option.Type.SINGLE;
import static test.api.Assertions.assertEquals;

import java.util.Map;
import main.Splitter;
import main.Value;
import test.api.JTest;
import test.api.JTest.Test;

class ValueTests {
  public static void main(String... args) {
    JTest.runTests(new ValueTests(), args);
  }

  @Test
  void test() {
    var flag = FLAG.option("-f", "--flag");
    var text = SINGLE.option("-t", "--text");
    var required = REQUIRED.option("-r");

    Map<String, Value> values = Splitter.of(flag, text, required).split("-f", "value");
    assertEquals(new Value.FlagValue(flag, true), values.get("-f"));
    assertEquals(new Value.FlagValue(flag, true), values.get("--flag"));
    assertEquals(null, values.get("-t"));
    assertEquals(null, values.get("--text"));
    assertEquals(new Value.RequiredValue(required, "value"), values.get("-r"));
  }
}
