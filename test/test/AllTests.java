package test;

class AllTests {

  public static void main(String[] args) {
    JTest.runAllTests(new CommandLineInterfaceTests(), new JarTests());
  }
}
