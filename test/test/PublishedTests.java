package test;

import main.ArgumentsSplitter;
import published.PublishedOptions;
import test.api.JTest;
import test.api.JTest.Test;

import java.lang.invoke.MethodHandles;

import static test.api.Assertions.assertFalse;
import static test.api.Assertions.assertTrue;

class PublishedTests {
  public static void main(String... args) {
    JTest.runTests(new PublishedTests(), args);
  }

  @Test
  void checkFlag() {
    var splitter = ArgumentsSplitter.toRecord(PublishedOptions.class, MethodHandles.publicLookup());
    assertFalse(splitter.split().__visible());
    assertTrue(splitter.split("--visible").__visible());
  }
}
