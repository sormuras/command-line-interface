package test;

import static java.lang.invoke.MethodHandles.lookup;
import static test.api.Assertions.assertArrayEquals;
import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertEqualsOptional;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

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

    var splitter = Splitter.of(lookup(), Main.class);

    assertEquals(
        new Main(false, new Help("hello", new Detail("world")), null, null),
        splitter.split("help", "hello", "detail", "world"));

    assertEquals(
        new Main(false, new Help("hello", null), null, null), //
        splitter.split("help", "hello"));

    assertEquals(
        new Main(true, null, "foo.jar", "foo.bar"),
        splitter.split("verbose", "foo.jar", "foo.bar"));

    assertEquals(
        new Main(false, null, "foo.jar", "foo.bar"), //
        splitter.split("foo.jar", "foo.bar"));
  }

  @Test
  void testToolOptions() {
    record Flag (){}
    record TreeToolOptions(Flag __help, Optional<String> __mode, String... paths) {}
    record DukeToolOptions(Flag __help, TreeToolOptions tree) {}

    var splitter = Splitter.of(lookup(), DukeToolOptions.class);
    DukeToolOptions options = splitter.split("tree", "--mode", "print", "foo.bar");

    assertEqualsOptional("print", options.tree().__mode());
    assertArrayEquals(new String[] {"foo.bar" }, options.tree().paths());

    assertEquals(new Flag(), splitter.split("--help").__help());
    assertEquals(new Flag(), splitter.split("tree", "--help").tree().__help());
  }
}
