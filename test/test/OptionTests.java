package test;

import main.ArgumentsSplitter.Option;
import main.ArgumentsSplitter.Option.Type;
import main.ArgumentsSplitter.Schema;
import test.OptionTests.Value.FlagValue;
import test.OptionTests.Value.KeyValue;
import test.OptionTests.Value.RepeatableValue;
import test.OptionTests.Value.RequiredValue;
import test.OptionTests.Value.VarargsValue;
import test.api.JTest;
import test.api.JTest.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static test.api.Assertions.assertEquals;

public class OptionTests {
  public static void main(String... args) {
    JTest.runTests(new OptionTests(), args);
  }

  sealed interface Value {
    record FlagValue(boolean value) implements Value {}
    record KeyValue(Optional<String> value) implements Value {}
    record RepeatableValue(List<String> values) implements Value {}
    record RequiredValue(String value) implements Value {}
    record VarargsValue(String... values) implements Value {
      @Override
      public boolean equals(Object obj) {
        return obj instanceof VarargsValue varargs && Arrays.equals(values, varargs.values);
      }

      @Override
      public int hashCode() {
        return Arrays.hashCode(values);
      }

      @Override
      public String toString() {
        return "VarargsValue[values=" + Arrays.toString(values) + "]";
      }
    }

    private static Value toValue(Object rawValue) {
      if (rawValue instanceof String value) return new RequiredValue(value);
      if (rawValue instanceof List<?> value) return new RepeatableValue((List<String>) value);
      if (rawValue instanceof Optional<?> value) return new KeyValue((Optional<String>) value);
      if (rawValue instanceof Boolean value) return new FlagValue(value);
      if (rawValue instanceof String[] value) return new VarargsValue(value);
      throw new AssertionError();
    }

    static Schema<List<Value>> schema(Option... options) {
      return new Schema<>(List.of(options), results -> Arrays.stream(results).map(Value::toValue).toList());
    }
  }

  public static Option option(Type type, String... names) {
    return new Option(type, new LinkedHashSet<>(List.of(names)), "", null);
  }

  @Test
  void values() {
    var schema = Value.schema(
        option(Option.Type.FLAG, "-f", "--flag"),
        option(Type.KEY_VALUE, "--opt"),
        option(Type.REPEATABLE, "what"),
        option(Type.REQUIRED, "filename"),
        option(Type.VARARGS, "rest")
        );

    assertEquals(List.of(new FlagValue(true), new KeyValue(Optional.empty()), new RepeatableValue(List.of()), new RequiredValue("output.txt"), new VarargsValue()),
        schema.split(List.of("-f", "output.txt")));
    assertEquals(List.of(new FlagValue(false), new KeyValue(Optional.of("blah")), new RepeatableValue(List.of()), new RequiredValue("output.txt"), new VarargsValue()),
        schema.split(List.of("--opt", "blah", "output.txt")));
    assertEquals(List.of(new FlagValue(false), new KeyValue(Optional.empty()), new RepeatableValue(List.of("what")), new RequiredValue("output.txt"), new VarargsValue()),
        schema.split(List.of("what", "what", "output.txt")));
    assertEquals(List.of(new FlagValue(false), new KeyValue(Optional.empty()), new RepeatableValue(List.of()), new RequiredValue("output.txt"), new VarargsValue()),
        schema.split(List.of("output.txt")));
    assertEquals(List.of(new FlagValue(false), new KeyValue(Optional.empty()), new RepeatableValue(List.of()), new RequiredValue("output.txt"), new VarargsValue("foo", "bar", "baz")),
        schema.split(List.of("output.txt", "foo", "bar", "baz")));
  }
}
