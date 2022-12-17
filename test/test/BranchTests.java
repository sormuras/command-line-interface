package test;

import static test.api.Assertions.assertEquals;

import java.lang.invoke.MethodHandles;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

class BranchTests {
  public static void main(String... args) {
    JTest.runTests(new BranchTests(), args);
  }

  @Test
  void test() {
    record Detail(String level) {}
    record Help(String topic, Detail detail) {}
    record Main(boolean verbose, Help help, String source, String target) {}

    var splitter = Splitter.of(MethodHandles.lookup(), Main.class);

    assertEquals(
        new Main(false, new Help("hello", new Detail("world")), null, null),
        splitter.split("help", "hello", "detail", "world"));

    assertEquals(new Main(false, new Help("hello", null), null, null), //
            splitter.split("help", "hello"));

    assertEquals(
        new Main(true, null, "foo.jar", "foo.bar"),
        splitter.split("verbose", "foo.jar", "foo.bar"));

    assertEquals(new Main(false, null, "foo.jar", "foo.bar"), //
            splitter.split("foo.jar", "foo.bar"));
  }
}
