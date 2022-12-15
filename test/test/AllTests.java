package test;

import test.api.JTest;
import test.jdk.JarTests;

class AllTests {
  public static void main(String[] args) {
    JTest.runAllTests(new AssortedTests(), new JarTests(), new PublishedTests(), new ValueTests());
  }
}
