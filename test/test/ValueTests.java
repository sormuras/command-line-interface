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
    var splitter =
        Splitter.of(
            FLAG.option("-f", "--flag"), SINGLE.option("-t", "--text"), REQUIRED.option("-r"));

    assertEquals(
        List.of(new Value.FlagValue(true), new Value.RequiredValue("value")),
        splitter.split("-f", "value"));
  }
}
