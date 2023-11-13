package test;

import test.api.JTest;
import test.jdk.JarRecordTests;
import test.jdk.JarOptionTests;
import test.jdk.JarSealedTests;
import test.unit.ArgumentMapTests;
import test.unit.ConverterResolverTests;
import test.unit.OptionTests;
import test.unit.SchemaTests;
import test.unit.SplitterOptionTests;

class AllTests {
  public static void main(String[] args) {
    JTest.runTestSuites(args,
        // examples
        AssortedTests::main,
        PublishedTests::main,
        BranchTests::main,
        DoubleDashTests::main,
        ConverterTests::main,
        // jdk examples
        JarRecordTests::main,
        JarOptionTests::main,
        // JarSealedTests::main, 
        // unit tests
        ArgumentMapTests::main,
        ConverterResolverTests::main,
        OptionTests::main,
        SchemaTests::main,
        SplitterOptionTests::main
    );
  }
}
