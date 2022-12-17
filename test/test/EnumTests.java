package test;

import main.Option;
import main.Schema;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
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

  public static final class ArgumentBag<K> {
    private final List<? extends Option<?>> options;
    private final List<Object> data;
    private final Function<List<? extends Option<?>>, Map<K, Integer>> indexMapFactory;
    private Map<K, Integer> indexMap;  // lazy

    private ArgumentBag(List<? extends Option<?>> options, List<Object> data, Function<List<? extends Option<?>>, Map<K, Integer>> indexMapFactory) {
      this.options = options;
      this.data = data;
      this.indexMapFactory = indexMapFactory;
    }

    public int size() {
      return options.size();
    }

    private Object rawValue(int index, Option.Type expectedType) {
      var option = options.get(index);
      if (option.type() != expectedType) {
        throw new IllegalStateException(option.type() + " is not a " + expectedType);
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

    private Map<K, Integer> indexMap() {
      if (indexMap != null) {
        return indexMap;
      }
      return indexMap = indexMapFactory.apply(options);
    }

    private Object rawValue(K key, Option.Type expectedType) {
      requireNonNull(key, "key is null");
      var index = indexMap().get(key);
      if (index == null) {
        throw new IllegalStateException(key + " is not a valid key");
      }
      return rawValue(index, expectedType);
    }

    public boolean flag(K key) {
      return (boolean) rawValue(key, FLAG);
    }

    @SuppressWarnings("unchecked")
    public Optional<String> single(K key) {
      return (Optional<String>) rawValue(key, SINGLE);
    }

    public String required(K key) {
      return (String) rawValue(key, REQUIRED);
    }

    @SuppressWarnings("unchecked")
    public List<String> repeatable(K key) {
      return (List<String>) rawValue(key, REPEATABLE);
    }

    public String[] varargs(K key) {
      return (String[]) rawValue(key, VARARGS);
    }

    @Override
    public String toString() {
      return range(0, options.size())
          .mapToObj(i -> options.get(i).names().iterator().next() + ": " + data.get(i))
          .collect(Collectors.joining(", ", "{", "}"));
    }
  }

  public static Schema<ArgumentBag<String>> schemaArgument(Option<?>... options) {
    requireNonNull(options, "options is null");
    var opt = List.of(options);
    return new Schema<>(opt, data -> new ArgumentBag<>(opt, data,
        optionList -> range(0, optionList.size()).boxed().collect(toMap(i -> optionList.get(i).names().iterator().next(), i -> i))));
  }

  public interface Configuration<K> {
    Configuration<K> with(K key, Option<?> option);
  }

  public static <K> Schema<ArgumentBag<K>> schemaKeyed(Consumer<? super Configuration<K>> consumer)  {
    requireNonNull(consumer, "consumer is null");
    var keys = new ArrayList<K>();
    var options = new ArrayList<Option<?>>();
    consumer.accept(new Configuration<>() {
      @Override
      public Configuration<K> with(K key, Option<?> option) {
        requireNonNull(key, "key is null");
        requireNonNull(option, "option is null");
        keys.add(key);
        options.add(option);
        return this;
      }
    });
    return new Schema<>(options, data -> new ArgumentBag<>(options, data,
        optionList -> range(0, optionList.size()).boxed().collect(toMap(keys::get, i -> i))));
  }


  public static final class Parameter<T> {
    private final Option<?> option;

    private Parameter(Option<?> option) {
      this.option = option;
    }

    public static Parameter<Boolean> flag(String... names) {
      return new Parameter<>(FLAG.option(names));
    }

    public static Parameter<Optional<String>> single(String... names) {
      return new Parameter<>(SINGLE.option(names));
    }

    public static Parameter<String> required(String... names) {
      return new Parameter<>(REQUIRED.option(names));
    }

    public static Parameter<List<String>> repeatable(String... names) {
      return new Parameter<>(REPEATABLE.option(names));
    }

    public static Parameter<String[]> varargs(String... names) {
      return new Parameter<>(VARARGS.option(names));
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
      var value = dataMap.dataMap.get(this);
      if (value == null) {
        throw new IllegalStateException("no data for parameter " + this);
      }
      return (T) value;
    }
  }

  public static final class DataMap {
    private final Map<Parameter<?>, Object> dataMap;

    private DataMap(Map<Parameter<?>, Object> dataMap) {
      this.dataMap = dataMap;
    }

    @Override
    public String toString() {
      return dataMap.toString();
    }
  }

  public static <K> Schema<DataMap> schemaParameter(Parameter<?>... parameters)  {
    requireNonNull(parameters, "parameters is null");
    var params = List.of(parameters);
    var options = params.stream().map(p -> p.option).toList();
    return new Schema<>(options, data -> new DataMap(range(0, params.size())
        .boxed()
        .collect(toMap(params::get, data::get))));
  }


  // ---


  @Test
  void stringArguments() {
    var schema = EnumTests.schemaKeyed(conf -> conf
        .with("optionFlag", FLAG.option("-f", "--flag"))
        .with("optionText", SINGLE.option("-t", "--text"))
        .with("optionR", REQUIRED.option("-r")));
    var argumentBag = Splitter.of(schema).split("-f", "value");

    assertTrue(argumentBag.flag("optionFlag"));
    assertTrue(argumentBag.single("optionText").isEmpty());
    assertEquals("value", argumentBag.required("optionR"));
  }

  @Test
  void enumArguments() {
    enum Value { F, TEXT, R }

    var schema = schemaKeyed(conf -> conf
        .with(Value.F, FLAG.option("-f", "--flag"))
        .with(Value.TEXT, SINGLE.option("-t", "--text"))
        .with(Value.R, REQUIRED.option("-r")));
    var argumentBag = Splitter.of(schema).split("-f", "value");

    assertTrue(argumentBag.flag(Value.F));
    assertTrue(argumentBag.single(Value.TEXT).isEmpty());
    assertEquals("value", argumentBag.required(Value.R));
  }

  @Test
  void optionsArgument() {
    var schema = EnumTests.schemaArgument(
        FLAG.option("-f", "--flag"),
        SINGLE.option("-t", "--text"),
        REQUIRED.option("-r"));
    var argumentBag = Splitter.of(schema).split("-f", "value");

    assertTrue(argumentBag.flagAt(0));
    assertTrue(argumentBag.singleAt(1).isEmpty());
    assertEquals("value", argumentBag.requiredAt(2));

    assertTrue(argumentBag.flag("-f"));
    assertTrue(argumentBag.single("-t").isEmpty());
    assertEquals("value", argumentBag.required("-r"));
  }

  @Test
  void parameters() {
    var flag = Parameter.flag("-f", "--flag");
    var text = Parameter.single("-t", "--text");
    var r = Parameter.required("-r");
    var schema = EnumTests.schemaParameter(flag, text, r);
    var dataMap = Splitter.of(schema).split("-f", "value");

    assertTrue(flag.arg(dataMap));
    assertTrue(text.arg(dataMap).isEmpty());
    assertEquals("value", r.arg(dataMap));
  }
}
