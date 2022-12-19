package test;

import test.api.JTest;
import test.jdk.JarRecordTests;
import test.jdk.JarValueTests;
import test.unit.ConverterResolverTests;
import test.unit.OptionTests;

class AllTests {
  public static void main(String[] args) {
    JTest.runAllTests(
        // examples
        new AssortedTests(),
        new PublishedTests(),
        new ValueTests(),
        new BranchTests(),
        new ArgumentMapTests(),
        new DoubleDashTests(),
        new ConverterTests(),
        // jdk examples
        new JarRecordTests(),
        new JarValueTests(),
        // unit tests
        new ConverterResolverTests(),
        new OptionTests()
    );
  }
}
