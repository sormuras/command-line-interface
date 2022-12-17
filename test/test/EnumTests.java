package test;

import main.Option;
import main.Schema;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static main.Option.Type.FLAG;
import static main.Option.Type.REPEATABLE;
import static main.Option.Type.REQUIRED;
import static main.Option.Type.SINGLE;
import static main.Option.Type.VARARGS;

import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertTrue;

class EnumTests {
  public static void main(String... args) {
    JTest.runTests(new EnumTests(), args);
  }


  // ---


  public static final class Parameter<T> {
    private final Option<?> option;

    private Parameter(Option<?> option) {
      this.option = option;
    }

    public static Parameter<Boolean> flag(String... names) {
      return new Parameter<>(Option.ofFlag(names));
    }

    public static Parameter<Optional<String>> single(String... names) {
      return new Parameter<>(Option.ofSingle(names));
    }

    public static Parameter<String> required(String... names) {
      return new Parameter<>(Option.ofRequired(names));
    }

    public static Parameter<List<String>> repeatable(String... names) {
      return new Parameter<>(Option.ofRepeatable(names));
    }

    public static Parameter<String[]> varargs(String... names) {
      return new Parameter<>(Option.ofVarargs(names));
    }

    @Override
    public String toString() {
      return "Parameter { type: " + option.type() + ", name: " +  option.names().iterator().next() + " }";
    }

    public Parameter<T> help(String helpText) {
      return new Parameter<>(option.withHelp(helpText));
    }

    @SuppressWarnings("unchecked")
    public T arg(DataMap dataMap) {
      requireNonNull(dataMap, "dataMap is null");
      var index = dataMap.indexMap.get(this);
      if (index == null) {
        throw new IllegalStateException("no data for parameter " + this);
      }
      return (T) dataMap.data.get(index);
    }
  }

  public static final class DataMap {
    private final List<Object> data;
    private final List<Parameter<?>> parameters;
    private final Map<Parameter<?>, Integer> indexMap;

    private DataMap(List<Object> data, List<Parameter<?>> parameters, Map<Parameter<?>, Integer> indexMap) {
      this.data = data;
      this.parameters = parameters;
      this.indexMap = indexMap;
    }

    private Object rawValue(int index, Option.Type expectedType) {
      var parameter = parameters.get(index);
      if (parameter.option.type() != expectedType) {
        throw new IllegalStateException(parameter.option.type() + " is not a " + expectedType);
      }
      return data.get(index);
    }

    public boolean flagAt(int index) {
      return (boolean) rawValue(index, FLAG);
    }

    @SuppressWarnings("unchecked")
    public Optional<String> singleAt(int index) {
      return (Optional<String>) rawValue(index, SINGLE);
    }

    public String requiredAt(int index) {
      return (String) rawValue(index, REQUIRED);
    }

    @SuppressWarnings("unchecked")
    public List<String> repeatableAt(int index) {
      return (List<String>) rawValue(index, REPEATABLE);
    }

    public String[] varargsAt(int index) {
      return (String[]) rawValue(index, VARARGS);
    }

    @Override
    public String toString() {
      return indexMap.entrySet().stream()
          .map(entry -> entry.getKey().option.names().iterator().next() + ": " + data.get(entry.getValue()))
          .collect(joining(", ", "{", "}"));
    }
  }

  public static Schema<DataMap> schemaParameter(Parameter<?>... parameters)  {
    requireNonNull(parameters, "parameters is null");
    var params = List.of(parameters);
    var options = params.stream().map(p -> p.option).toList();
    return new Schema<>(options, data -> new DataMap(data, params, range(0, params.size())
        .boxed()
        .collect(toMap(params::get, i -> i))));
  }


  // ---

  @Test
  void parameters() {
    var flag = Parameter.flag("-f", "--flag");
    var text = Parameter.single("-t", "--text");
    var r = Parameter.required("-r");
    var schema = EnumTests.schemaParameter(flag, text, r);
    var dataMap = Splitter.of(schema).split("-f", "value");

    assertTrue(dataMap.flagAt(0));
    assertTrue(dataMap.singleAt(1).isEmpty());
    assertEquals("value", dataMap.requiredAt(2));

    assertTrue(flag.arg(dataMap));
    assertTrue(text.arg(dataMap).isEmpty());
    assertEquals("value", r.arg(dataMap));
  }
}
