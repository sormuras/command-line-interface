package test;

class AllTests implements JTest {

    public static void main(String[] args) {
        new AllTests().runAllTests( new CommandLineInterfaceTests(), new JarTests() );
    }
}
