package test;

import main.ArgumentsSplitter;
import main.Option;
import main.Schema;
import main.Value;
import test.api.JTest;
import test.api.JTest.Test;

import java.util.List;

import static test.api.Assertions.assertEquals;

class ValueTests {
    public static void main(String... args) {
        JTest.runTests(new ValueTests(), args);
    }

    @Test
    void test() {
        Schema schema = new Schema(new Option(Option.Type.FLAG, "-f", "--flag"));
        var splitter = Value.splitter(args -> ArgumentsSplitter.split(schema, args));

        assertEquals(List.of(new Value.FlagValue(true)), splitter.split("-f"));
    }
}
