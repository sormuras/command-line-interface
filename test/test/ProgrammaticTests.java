package test;

import main.Command;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertFalse;
import static test.api.Assertions.assertTrue;

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
        Command.Factory<Options> cmd = Command.of(Options::new)
                .addFlag(Options::setY, "--f")
                .addRequired(Options::setX)
                .build();

        Options options = Splitter.of(cmd).split("--f", "hello");

        assertTrue( options.y);
        assertEquals("hello", options.x);

        Options options2 = Splitter.of(cmd).split("world");

        assertFalse(options2.y);
        assertEquals("world", options2.x);
    }
}
