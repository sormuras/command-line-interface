package test;

import static test.Assertions.assertFalse;
import static test.Assertions.assertTrue;

import main.ArgumentsSplitter;
import published.PublishedOptions;
import test.JTest.Test;

class PublishedTests {
  public static void main(String... args) {
    JTest.runTests(new PublishedTests(), args);
  }

  @Test
  void checkFlag() {
    var splitter = ArgumentsSplitter.of(PublishedOptions.class);
    assertFalse(splitter.split().__visible());
    assertTrue(splitter.split("--visible").__visible());
  }
}
