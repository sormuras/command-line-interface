package test;

import static test.api.Assertions.assertFalse;
import static test.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import main.Splitter;
import published.PublishedOptions;
import test.api.JTest;
import test.api.JTest.Test;

class PublishedTests {
  public static void main(String... args) {
    JTest.runTests(new PublishedTests(), args);
  }

  @Test
  void checkFlag() {
    var splitter = Splitter.of(MethodHandles.publicLookup(), PublishedOptions.class);
    assertFalse(splitter.split().__visible());
    assertTrue(splitter.split("--visible").__visible());
  }
}
