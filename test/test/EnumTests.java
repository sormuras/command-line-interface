package test;

import main.Option;
import main.Schema;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableMap;
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

  public record Result(Option.Type type, Object rawValue) {
    public Result {
      requireNonNull(type, "type is null");
      requireNonNull(rawValue, "rawValue is null");
    }

    private Object checkRawValueType(Option.Type expectedType) {
      if (type != expectedType) {
        throw new IllegalStateException(type + " is not a " + expectedType);
      }
      return rawValue;
    }

    public boolean flag() {
      return (boolean) checkRawValueType(FLAG);
    }

    @SuppressWarnings("unchecked")
    public Optional<String> single() {
      return (Optional<String>) checkRawValueType(SINGLE);
    }

    public String required() {
      return (String) checkRawValueType(REQUIRED);
    }

    @SuppressWarnings("unchecked")
    public List<String> repeatable() {
      return (List<String>) checkRawValueType(REPEATABLE);
    }

    public String[] varargs() {
      return (String[]) checkRawValueType(VARARGS);
    }
  }

  public interface Configuration<K> {
    Configuration<K> with(K key, Option option);
  }

  static <K> Schema<Map<K, Result>> schemaMap(Consumer<? super Configuration<K>> consumer)  {
    requireNonNull(consumer);
    var entryList = new ArrayList<Map.Entry<K, Option>>();
    consumer.accept(new Configuration<>() {
      @Override
      public Configuration<K> with(K key, Option option) {
        entryList.add(Map.entry(key, option));
        return this;
      }
    });
    @SuppressWarnings("unchecked")
    var entries = (Entry<K, Option>[]) entryList.toArray(Entry<?,?>[]::new);
    return schemaMap(Map.ofEntries(entries));
  }

  static <T> Schema<Map<T, Result>> schemaMap(Map<? extends T, Option> optionMap)  {
    requireNonNull(optionMap, "optionMap is null");
    var keys = optionMap.keySet().stream().toList();
    var options = optionMap.values().stream().toList();
    return new Schema<>(options, data ->
        range(0, options.size())
            .boxed()
            .collect(toUnmodifiableMap(keys::get, i -> new Result(options.get(i).type(), data.get(i)))));
  }

  public static final class ArgumentBag<K> {
    private record Argument(Option option, Object rawValue) { }

    private final Map<K, Argument> argumentMap;

    private ArgumentBag(Map<K, Argument> argumentMap) {
      this.argumentMap = argumentMap;
    }

    private Object rawValue(K key, Option.Type expectedType) {
      requireNonNull(key, "key is null");
      var argument = argumentMap.get(key);
      if (argument == null) {
        throw new IllegalStateException("unknown key " + key);
      }
      if (argument.option.type() != expectedType) {
        throw new IllegalStateException(argument.option.type() + " is not a " + expectedType);
      }
      return argument.rawValue;
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
      return argumentMap.toString();
    }
  }

  static <K> Schema<ArgumentBag<K>> schemaBag(Consumer<? super Configuration<K>> consumer)  {
    requireNonNull(consumer);
    var entryList = new ArrayList<Map.Entry<K, Option>>();
    consumer.accept(new Configuration<>() {
      @Override
      public Configuration<K> with(K key, Option option) {
        entryList.add(Map.entry(key, option));
        return this;
      }
    });
    @SuppressWarnings("unchecked")
    var entries = (Entry<K, Option>[]) entryList.toArray(Entry<?,?>[]::new);
    return schemaBag(Map.ofEntries(entries));
  }

  static <K> Schema<ArgumentBag<K>> schemaBag(Map<? extends K, Option> optionMap) {
    requireNonNull(optionMap, "optionMap is null");
    var keys = optionMap.keySet().stream().toList();
    var options = optionMap.values().stream().toList();
    return new Schema<>(options, data ->
        new ArgumentBag<>(range(0, options.size())
            .boxed()
            .collect(toUnmodifiableMap(keys::get, i -> new ArgumentBag.Argument(options.get(i), data.get(i))))));
  }


  // ---

  @Test
  void enumMap() {
    enum Value { F, TEXT, R }

    var optionMap = Map.of(
        Value.F, FLAG.option("-f", "--flag"),
        Value.TEXT, SINGLE.option("-t", "--text"),
        Value.R, REQUIRED.option("-r")
    );

    var schema = schemaMap(optionMap);
    var resultMap = Splitter.of(schema).split("-f", "value");

    assertTrue(resultMap.get(Value.F).flag());
    assertTrue(resultMap.get(Value.TEXT).single().isEmpty());
    assertEquals("value", resultMap.get(Value.R).required());
}



  @Test
  void enumConf() {
    enum Value { F, TEXT, R }

    var schema = schemaMap(conf -> conf
        .with(Value.F, FLAG.option("-f", "--flag"))
        .with(Value.TEXT, SINGLE.option("-t", "--text"))
        .with(Value.R, REQUIRED.option("-r")));
    var resultMap = Splitter.of(schema).split("-f", "value");

    assertTrue(resultMap.get(Value.F).flag());
    assertTrue(resultMap.get(Value.TEXT).single().isEmpty());
    assertEquals("value", resultMap.get(Value.R).required());
  }

  @Test
  void stringConf() {
    var schema = EnumTests.schemaMap(conf -> conf
        .with("flag", FLAG.option("-f", "--flag"))
        .with("text", SINGLE.option("-t", "--text"))
        .with("r", REQUIRED.option("-r")));
    var resultMap = Splitter.of(schema).split("-f", "value");

    var result = new Object() {
      boolean flag = resultMap.get("flag").flag();
      String text = resultMap.get("text").single().orElse("");
      String r = resultMap.get("r").required();
    };

    assertTrue(result.flag);
    assertEquals("", result.text);
    assertEquals("value", result.r);
  }

  @Test
  void enumConfBag() {
    enum Value { F, TEXT, R }

    var schema = EnumTests.schemaBag(conf -> conf
        .with(Value.F, FLAG.option("-f", "--flag"))
        .with(Value.TEXT, SINGLE.option("-t", "--text"))
        .with(Value.R, REQUIRED.option("-r")));
    var argumentBag = Splitter.of(schema).split("-f", "value");

    var result = new Object() {
      boolean flag = argumentBag.flag(Value.F);
      String text = argumentBag.single(Value.TEXT).orElse("");
      String r = argumentBag.required(Value.R);
    };

    assertTrue(result.flag);
    assertEquals("", result.text);
    assertEquals("value", result.r);
  }

  @Test
  void stringConfBag() {
    var schema = EnumTests.schemaBag(conf -> conf
        .with("flag", FLAG.option("-f", "--flag"))
        .with("text", SINGLE.option("-t", "--text"))
        .with("r", REQUIRED.option("-r")));
    var argumentBag = Splitter.of(schema).split("-f", "value");

    var result = new Object() {
      boolean flag = argumentBag.flag("flag");
      String text = argumentBag.single("text").orElse("");
      String r = argumentBag.required("r");
    };

    assertTrue(result.flag);
    assertEquals("", result.text);
    assertEquals("value", result.r);
  }
}
