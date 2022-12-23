package test;

import main.Command;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import static test.api.Assertions.assertEquals;

class ProgrammaticTests {

    public static void main(String... args) {
        JTest.runTests(new ProgrammaticTests(), args);
    }

    @Test
    void testProgrammaticAssembly() {
        class Options {
            boolean y;
            String x;

            public void setY(boolean y) {
                this.y = y;
            }

            public void setX(String x) {
                this.x = x;
            }
        }
        Options options = new Options();
        Command<Object> cmd = Command.of(() -> null)
                .addFlag(options::setY, "--f")
                .addRequired(options::setX);

        Splitter.of(cmd).split("--f", "hello");

        assertEquals(true, options.y);
        assertEquals("hello", options.x);
    }
}
