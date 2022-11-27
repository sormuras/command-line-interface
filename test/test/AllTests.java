package test;

import test.api.JTest;

import static java.lang.invoke.MethodHandles.lookup;

class AllTests {
  public static void main(String[] args) {
    JTest.runAllTests(lookup(), new AssortedTests(), new JarTests(), new PublishedTests());
  }
}
