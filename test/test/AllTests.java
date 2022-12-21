package test;

import test.api.JTest;
import test.jdk.JarRecordTests;
import test.jdk.JarOptionTests;
import test.unit.ArgumentMapTests;
import test.unit.ConverterResolverTests;
import test.unit.OptionTests;
import test.unit.SchemaTests;
import test.unit.SplitterOptionTests;

class AllTests {
  public static void main(String[] args) {
    JTest.runTestSuites(
        // examples
        AssortedTests.class,
        PublishedTests.class,
        BranchTests.class,
        DoubleDashTests.class,
        ConverterTests.class,
        // jdk examples
        JarRecordTests.class,
        JarOptionTests.class,
        // unit tests
        ArgumentMapTests.class,
        ConverterResolverTests.class,
        OptionTests.class,
        SchemaTests.class,
        SplitterOptionTests.class
    );
  }
}
