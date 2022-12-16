package test;

import main.Option;
import main.Schema;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Consumer;

import static java.util.Collections.unmodifiableMap;
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
    var map = new LinkedHashMap<K, Option>();
    consumer.accept(new Configuration<>() {
      @Override
      public Configuration<K> with(K key, Option option) {
        var result = map.putIfAbsent(key, option);
        if (result != null) {
          throw new IllegalStateException("duplicate key " + key);
        }
        return this;
      }
    });
    return schemaMap(map);
  }

  static <T> Schema<Map<T, Result>> schemaMap(Map<? extends T, Option> optionMap)  {
    requireNonNull(optionMap, "optionMap is null");
    if (!optionMap.entrySet().spliterator().hasCharacteristics(Spliterator.ORDERED)) {
      throw new IllegalArgumentException("the optionMap is not ordered");
    }
    var keys = optionMap.keySet().stream().toList();
    var options = optionMap.values().stream().toList();
    return new Schema<>(options, data ->
        unmodifiableMap(range(0, options.size())
            .boxed()
            .collect(toMap(keys::get, i -> new Result(options.get(i).type(), data.get(i)), (_1, _2) -> { throw null;}, LinkedHashMap::new))));
  }

  public static final class ArgumentBag<K> {
    private record Argument(Option option, Object rawValue) { }

    private final LinkedHashMap<K, Argument> argumentMap;

    private ArgumentBag(LinkedHashMap<K, Argument> argumentMap) {
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
    var map = new LinkedHashMap<K, Option>();
    consumer.accept(new Configuration<>() {
      @Override
      public Configuration<K> with(K key, Option option) {
        var result = map.putIfAbsent(key, option);
        if (result != null) {
          throw new IllegalStateException("duplicate key " + key);
        }
        return this;
      }
    });
    return schemaBag(map);
  }

  static <K> Schema<ArgumentBag<K>> schemaBag(Map<? extends K, Option> optionMap) {
    requireNonNull(optionMap, "optionMap is null");
    if (!optionMap.entrySet().spliterator().hasCharacteristics(Spliterator.ORDERED)) {
      throw new IllegalArgumentException("the optionMap is not ordered");
    }
    var keys = optionMap.keySet().stream().toList();
    var options = optionMap.values().stream().toList();
    return new Schema<>(options, data ->
        new ArgumentBag<>(range(0, options.size())
            .boxed()
            .collect(toMap(keys::get, i -> new ArgumentBag.Argument(options.get(i), data.get(i)), (_1, _2) -> { throw null;}, LinkedHashMap::new))));
  }


  // ---

  @Test
  void enumMap() {
    enum Value { F, TEXT, R }

    var optionMap = new LinkedHashMap<Value, Option>() {{
        put(Value.F, FLAG.option("-f", "--flag"));
        put(Value.TEXT, SINGLE.option("-t", "--text"));
        put(Value.R, REQUIRED.option("-r"));
    }};

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
