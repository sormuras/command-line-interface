package test;

import static test.Assertions.assertFalse;
import static test.Assertions.assertTrue;

import main.ArgumentsSplitter;
import published.PublishedOptions;

public class PublishedTests implements JTest {
  public static void main(String... args) {
    new PublishedTests().runTests(args);
  }

  @Test
  void checkFlag() {
    var splitter = ArgumentsSplitter.of(PublishedOptions.class);
    assertFalse(splitter.split().__visible());
    assertTrue(splitter.split("--visible").__visible());
  }
}
