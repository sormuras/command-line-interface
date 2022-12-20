package test.unit;

import main.Option;
import main.Option.Type;
import main.Schema;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static test.api.Assertions.assertAll;
import static test.api.Assertions.assertArrayEquals;
import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertThrows;
import static test.api.Assertions.assertTrue;

public class OptionTests {
  public static void main(String... args) {
    JTest.runTests(new OptionTests(), args);
  }

  @Test
  void flag() {
    var option = Option.flag("--foo", "-f");
    assertAll(
        () -> assertEquals(Type.FLAG, option.type()),
        () -> assertEquals(List.of("--foo", "-f"), List.copyOf(option.names())),
        () -> assertEquals("", option.help()),
        () -> assertEquals(null, option.nestedSchema())
    );
  }

  @Test
  void flagPreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> Option.flag((String[]) null)),
        () -> assertThrows(NullPointerException.class, () -> Option.flag("foo", null)),
        () -> assertThrows(IllegalArgumentException.class, () -> Option.flag("foo", "foo")),
        () -> assertThrows(IllegalArgumentException.class, () -> Option.flag(""))
    );
  }

  @Test
  void single() {
    var option = Option.single("--foo", "-f");
    assertAll(
        () -> assertEquals(Type.SINGLE, option.type()),
        () -> assertEquals(List.of("--foo", "-f"), List.copyOf(option.names())),
        () -> assertEquals("", option.help()),
        () -> assertEquals(null, option.nestedSchema())
    );
  }

  @Test
  void singlePreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> Option.single((String[]) null)),
        () -> assertThrows(NullPointerException.class, () -> Option.single("foo", null)),
        () -> assertThrows(IllegalArgumentException.class, () -> Option.single("foo", "foo")),
        () -> assertThrows(IllegalArgumentException.class, () -> Option.single(""))
    );
  }

  @Test
  void required() {
    var option = Option.required("--foo", "-f");
    assertAll(
        () -> assertEquals(Type.REQUIRED, option.type()),
        () -> assertEquals(List.of("--foo", "-f"), List.copyOf(option.names())),
        () -> assertEquals("", option.help()),
        () -> assertEquals(null, option.nestedSchema())
    );
  }

  @Test
  void requiredPreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> Option.required((String[]) null)),
        () -> assertThrows(NullPointerException.class, () -> Option.required("foo", null)),
        () -> assertThrows(IllegalArgumentException.class, () -> Option.required("foo", "foo")),
        () -> assertThrows(IllegalArgumentException.class, () -> Option.required(""))
    );
  }

  @Test
  void repeatable() {
    var option = Option.repeatable("--foo", "-f");
    assertAll(
        () -> assertEquals(Type.REPEATABLE, option.type()),
        () -> assertEquals(List.of("--foo", "-f"), List.copyOf(option.names())),
        () -> assertEquals("", option.help()),
        () -> assertEquals(null, option.nestedSchema())
    );
  }

  @Test
  void repeatablePreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> Option.repeatable((String[]) null)),
        () -> assertThrows(NullPointerException.class, () -> Option.repeatable("foo", null)),
        () -> assertThrows(IllegalArgumentException.class, () -> Option.repeatable("foo", "foo")),
        () -> assertThrows(IllegalArgumentException.class, () -> Option.repeatable(""))
    );
  }

  @Test
  void varargs() {
    var option = Option.varargs("--foo", "-f");
    assertAll(
        () -> assertEquals(Type.VARARGS, option.type()),
        () -> assertEquals(List.of("--foo", "-f"), List.copyOf(option.names())),
        () -> assertEquals("", option.help()),
        () -> assertEquals(null, option.nestedSchema())
    );
  }

  @Test
  void varargsPreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> Option.varargs((String[]) null)),
        () -> assertThrows(NullPointerException.class, () -> Option.varargs("foo", null)),
        () -> assertThrows(IllegalArgumentException.class, () -> Option.varargs("foo", "foo")),
        () -> assertThrows(IllegalArgumentException.class, () -> Option.varargs(""))
    );
  }

  @Test
  void optionTypeSafe() {
    Option<Boolean> flag = Option.flag("a");
    Option<Optional<String>> single = Option.single("a");
    Option<String> required = Option.required("a");
    Option<List<String>> repeatable = Option.repeatable("a");
    Option<String[]> varargs = Option.varargs("a");

    assertAll(
        () -> assertTrue(flag != null),
        () -> assertTrue(single != null),
        () -> assertTrue(required != null),
        () -> assertTrue(repeatable != null),
        () -> assertTrue(varargs != null)
    );
  }

  @Test
  void optionToString() {
    assertAll(
        () -> assertEquals("FLAG[a, b]", Option.flag("a", "b").toString()),
        () -> assertEquals("SINGLE[a, b]", Option.single("a", "b").toString()),
        () -> assertEquals("REQUIRED[a, b]", Option.required("a", "b").toString()),
        () -> assertEquals("REPEATABLE[a, b]", Option.repeatable("a", "b").toString()),
        () -> assertEquals("VARARGS[a, b]", Option.varargs("a", "b").toString())
    );
  }

  @Test
  void mapTypeSafe() {
    Option<Boolean> flag = Option.flag("a").map(b -> !b);
    Option<Optional<String>> single = Option.single("a").map(opt -> opt.map(s -> "*" + s + "*"));
    Option<String> required = Option.required("a").map(s -> "*" + s + "*");
    Option<List<String>> repeatable = Option.repeatable("a").map(list -> list.stream().map(s -> "*" + s + "*").toList());
    Option<String[]> varargs = Option.varargs("a").map(array -> Arrays.stream(array).map(s -> "*" + s + "*").toArray(String[]::new));

    assertAll(
        () -> assertTrue(flag != null),
        () -> assertTrue(single != null),
        () -> assertTrue(required != null),
        () -> assertTrue(repeatable != null),
        () -> assertTrue(varargs != null)
    );
  }

  @Test
  void mapWithSplitter() {
    var flag = Option.flag("-f").map(b -> b);
    var single = Option.single("-s").map(opt -> opt.map(Integer::parseInt));
    var required = Option.required("r").map(s -> s.toUpperCase(Locale.ROOT));
    var repeatable = Option.repeatable("-p").map(list -> list.stream().map(s -> "*" + s + "*").toList());
    var varargs = Option.varargs("v").map(array -> Arrays.stream(array).map(s -> "*" + s + "*").toArray(String[]::new));

    var splitter = Splitter.ofArgument(flag, single, required, repeatable, varargs);
    var argumentMap = splitter.split("-f", "-s", "12", "-p", "bar", "-p", "baz", "buz", "biz", "booz");

    assertAll(
        () -> assertTrue(argumentMap.argument(flag)),
        () -> assertEquals(12, argumentMap.argument(single).orElseThrow()),
        () -> assertEquals("BUZ", argumentMap.argument(required)),
        () -> assertEquals(List.of("*bar*", "*baz*"), argumentMap.argument(repeatable)),
        () -> assertArrayEquals(new String[] { "*biz*", "*booz*" }, argumentMap.argument(varargs))
    );
  }

  @Test
  void mapPreconditions() {
    var flag = Option.flag("a");
    assertThrows(NullPointerException.class, () -> flag.map(null));
  }

  @Test
  void help() {
    assertEquals("help", Option.flag("a").help("help").help());
    assertEquals("help", Option.single("a").help("help").help());
    assertEquals("help", Option.required("a").help("help").help());
    assertEquals("help", Option.repeatable("a").help("help").help());
    assertEquals("help", Option.varargs("a").help("help").help());
  }

  @Test
  void helpPrecondition() {
    var flag1 = Option.flag("a");
    var flag2 = Option.flag("b").help("help");
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> flag1.help(null)),
        () -> assertThrows(IllegalStateException.class, () -> flag2.help("another help"))
    );
  }

  @Test
  void nestedSchema() {
    var schema = new Schema<>(List.of(Option.flag("flag")), x -> x);

    assertEquals(schema, Option.flag("a").nestedSchema(schema).nestedSchema());
    assertEquals(schema, Option.single("a").nestedSchema(schema).nestedSchema());
    assertEquals(schema, Option.required("a").nestedSchema(schema).nestedSchema());
    assertEquals(schema, Option.repeatable("a").nestedSchema(schema).nestedSchema());
    assertEquals(schema, Option.varargs("a").nestedSchema(schema).nestedSchema());
  }

  @Test
  void nestedSchemaPrecondition() {
    var flag1 = Option.flag("a");
    var flag2 = Option.flag("b").nestedSchema(new Schema<>(List.of(Option.flag("c")), x -> x));
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> flag1.nestedSchema(null)),
        () -> assertThrows(IllegalStateException.class, () -> flag2.nestedSchema(new Schema<>(List.of(Option.flag("d")), x -> x)))
    );
  }

  @Test
  void argumentWithSplitter() {
    var flag = Option.flag("-f");
    var single = Option.single("-s");
    var required = Option.required("r");
    var repeatable = Option.repeatable("-p");
    var varargs = Option.varargs("v");

    var splitter = Splitter.ofArgument(flag, single, required, repeatable, varargs);
    var argumentMap = splitter.split("-f", "-s", "12", "-p", "bar", "-p", "baz", "buz", "biz", "booz");

    assertAll(
        () -> assertTrue(argumentMap.argument(flag)),
        () -> assertEquals("12", argumentMap.argument(single).orElseThrow()),
        () -> assertEquals("buz", argumentMap.argument(required)),
        () -> assertEquals(List.of("bar", "baz"), argumentMap.argument(repeatable)),
        () -> assertArrayEquals(new String[] { "biz", "booz" }, argumentMap.argument(varargs))
    );
  }

  @Test
  void argumentPreconditions() {
    var flag = Option.flag("-f");
    assertThrows(NullPointerException.class, () -> flag.argument(null));
  }
}
