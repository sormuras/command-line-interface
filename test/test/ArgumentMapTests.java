package test;

import main.Option;
import main.Splitter;

import test.api.JTest;
import test.api.JTest.Test;

import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertTrue;

class ArgumentMapTests {
  public static void main(String... args) {
    JTest.runTests(new ArgumentMapTests(), args);
  }

  @Test
  void argumentMap() {
    var flag = Option.flag("-f", "--flag");
    var text = Option.single("-t", "--text");
    var r = Option.required("-r");
    var argMap = Splitter.ofArgument(flag, text, r).split("-f", "value");

    assertTrue(flag.argument(argMap));
    assertTrue(text.argument(argMap).isEmpty());
    assertEquals("value", r.argument(argMap));

    assertTrue(argMap.argument(flag));
    assertTrue(argMap.argument(text).isEmpty());
    assertEquals("value", argMap.argument(r));
  }
}
