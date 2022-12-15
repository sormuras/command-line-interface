package test;

import static main.Option.Type.FLAG;
import static main.Option.Type.REQUIRED;
import static main.Option.Type.SINGLE;
import static test.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
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

    assertEquals(
        List.of(
            new Value.FlagValue(flag, true),
            new Value.SingleValue(text, Optional.empty()),
            new Value.RequiredValue(required, "value")),
        Splitter.of(false, flag, text, required).split("-f", "value"));

    assertEquals(
        List.of(
            new Value.FlagValue(flag, true),
            // pruned
            new Value.RequiredValue(required, "value")),
        Splitter.of(true, flag, text, required).split("-f", "value"));
  }
}
