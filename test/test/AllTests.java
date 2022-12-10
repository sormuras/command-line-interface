package test;

import test.api.JTest;

class AllTests {
  public static void main(String[] args) {
    JTest.runAllTests(new AssortedTests(), new JarTests(), new PublishedTests(), new OptionTests());
  }
}
