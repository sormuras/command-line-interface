package test;

import main.Option;
import main.Schema;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import java.util.ArrayList;
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
    private final List<Option> options;
    private final List<Object> data;
    private final Function<List<Option>, Map<K, Integer>> indexMapFactory;
    private Map<K, Integer> indexMap;  // lazy

    private ArgumentBag(List<Option> options, List<Object> data, Function<List<Option>, Map<K, Integer>> indexMapFactory) {
      this.options = options;
      this.data = data;
      this.indexMapFactory = indexMapFactory;
    }

    private Object rawValue(int index, Option.Type expectedType) {
      var option = options.get(index);
      if (option.type() != expectedType) {
        throw new IllegalStateException(option.type() + " is not a " + expectedType);
      }
      return data.get(index);
    }

    public boolean flag(int index) {
      return (boolean) rawValue(index, FLAG);
    }

    @SuppressWarnings("unchecked")
    public Optional<String> single(int index) {
      return (Optional<String>) rawValue(index, SINGLE);
    }

    public String required(int index) {
      return (String) rawValue(index, REQUIRED);
    }

    @SuppressWarnings("unchecked")
    public List<String> repeatable(int index) {
      return (List<String>) rawValue(index, REPEATABLE);
    }

    public String[] varargs(int index) {
      return (String[]) rawValue(index, VARARGS);
    }

    private Map<K, Integer> indexMap() {
      if (indexMap != null) {
        return indexMap;
      }
      return indexMap = indexMapFactory.apply(options);
    }

    private Object rawValue(K name, Option.Type expectedType) {
      var index = indexMap().get(name);
      if (index == null) {
        throw new IllegalStateException(name + " is not a valid name");
      }
      return rawValue(index, expectedType);
    }

    public boolean flag(K name) {
      return (boolean) rawValue(name, FLAG);
    }

    @SuppressWarnings("unchecked")
    public Optional<String> single(K name) {
      return (Optional<String>) rawValue(name, SINGLE);
    }

    public String required(K name) {
      return (String) rawValue(name, REQUIRED);
    }

    @SuppressWarnings("unchecked")
    public List<String> repeatable(K name) {
      return (List<String>) rawValue(name, REPEATABLE);
    }

    public String[] varargs(K name) {
      return (String[]) rawValue(name, VARARGS);
    }

    @Override
    public String toString() {
      return range(0, options.size())
          .mapToObj(i -> options.get(i).names().iterator().next() + ": " + data.get(i))
          .collect(Collectors.joining(", ", "{", "}"));
    }
  }

  public static Schema<ArgumentBag<String>> schemaArgument(Option... options) {
    requireNonNull(options, "options is null");
    var opt = List.of(options);
    return new Schema<>(opt, data -> new ArgumentBag<>(opt, data,
        optionList -> range(0, optionList.size()).boxed().collect(toMap(i -> optionList.get(i).names().iterator().next(), i -> i))));
  }

  public interface Configuration<K> {
    Configuration<K> with(K key, Option option);
  }

  public static <K> Schema<ArgumentBag<K>> schemaKeyed(Consumer<? super Configuration<K>> consumer)  {
    requireNonNull(consumer);
    var keys = new ArrayList<K>();
    var options = new ArrayList<Option>();
    consumer.accept(new Configuration<>() {
      @Override
      public Configuration<K> with(K key, Option option) {
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

    assertTrue(argumentBag.flag(0));
    assertTrue(argumentBag.single(1).isEmpty());
    assertEquals("value", argumentBag.required(2));

    assertTrue(argumentBag.flag("-f"));
    assertTrue(argumentBag.single("-t").isEmpty());
    assertEquals("value", argumentBag.required("-r"));
  }
}
