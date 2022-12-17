package test;

import main.Name;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import java.util.List;
import java.util.Optional;

import static java.lang.invoke.MethodHandles.lookup;
import static test.api.Assertions.assertEquals;

class DoubleDashTests {

    public static void main(String... args) {
        JTest.runTests(new DoubleDashTests(), args);
    }

    @Test
    void doubleDashPicocliDemo() {
        record DoubleDashDemo(
                @Name("-v") boolean verbose,
                @Name("-files") List<String> files,
                String...params
        ) {}

        String[] args = { "-v", "--", "-files", "file1", "file2" };
        DoubleDashDemo obj = Splitter.of(lookup(), DoubleDashDemo.class).split(args);

        assertEquals(true, obj.verbose());
        assertEquals(List.of(), obj.files());
        assertEquals(List.of("-files", "file1", "file2"), List.of(obj.params()));
    }

    @Test
    void doubleDashRequired() {
        record DoubleDashRequired(boolean __verbose, Optional<String> dir, String target) {}

        String[] args = {"--verbose", "--", "dir"};
        DoubleDashRequired obj = Splitter.of(lookup(), DoubleDashRequired.class).split(args);

        assertEquals(true, obj.__verbose());
        assertEquals(Optional.empty(), obj.dir());
        assertEquals("dir", obj.target());
    }
}
