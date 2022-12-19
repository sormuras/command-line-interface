package test;

import test.api.JTest;
import test.jdk.JarRecordTests;
import test.jdk.JarValueTests;
import test.unit.ArgumentMapTests;
import test.unit.ConverterResolverTests;
import test.unit.OptionTests;
import test.unit.SchemaTests;
import test.unit.SplitterOptionTests;

class AllTests {
  public static void main(String[] args) {
    JTest.runAllTests(
        // examples
        new AssortedTests(),
        new PublishedTests(),
        new ValueTests(),
        new BranchTests(),
        new DoubleDashTests(),
        new ConverterTests(),
        // jdk examples
        new JarRecordTests(),
        new JarValueTests(),
        // unit tests
        new ArgumentMapTests(),
        new ConverterResolverTests(),
        new OptionTests(),
        new SchemaTests(),
        new SplitterOptionTests()
    );
  }
}
