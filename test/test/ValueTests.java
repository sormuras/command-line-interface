package test;

import static test.api.Assertions.assertEquals;

import java.util.List;
import main.ArgumentsSplitter;
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
    var splitter = ArgumentsSplitter.toValues(new Option(Option.Type.FLAG, "-f", "--flag"));

    assertEquals(List.of(new Value.FlagValue(true)), splitter.split("-f"));
  }
}
