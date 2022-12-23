package test;

import test.api.JTest;
import test.jdk.JarRecordTests;

class AllTests {
  public static void main(String[] args) {
    JTest.runAllTests(
        new AssortedTests(),
        new PublishedTests(),
        new BranchTests(),
        new DoubleDashTests(),
        new ProgrammaticTests(),
        // jdk examples
        new JarRecordTests());
  }
}
