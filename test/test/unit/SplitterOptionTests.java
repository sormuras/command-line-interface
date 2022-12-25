package test.unit;

import main.Option;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import java.util.List;
import java.util.stream.Stream;

import static java.lang.invoke.MethodHandles.lookup;
import static test.api.Assertions.assertAll;
import static test.api.Assertions.assertArrayEquals;
import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertFalse;
import static test.api.Assertions.assertThrows;
import static test.api.Assertions.assertTrue;

public class SplitterOptionTests {
  public static void main(String... args) {
    JTest.runTests(new SplitterOptionTests(), args);
  }

  @Test
  void splitterOfFlag() {
    var flag = Option.flag("-f");
    var splitter = Splitter.of(flag);
    assertAll(
        () -> assertTrue(splitter.split("-f").argument(flag)),
        () -> assertFalse(splitter.split().argument(flag))
    );
  }

  @Test
  void splitterOfFlag2() {
    var flag = Option.flag("-f", "--flag", "flag");
    var splitter = Splitter.of(flag);
    assertAll(
        () -> assertTrue(splitter.split("-f").argument(flag)),
        () -> assertTrue(splitter.split("--flag").argument(flag)),
        () -> assertTrue(splitter.split("flag").argument(flag))
    );
  }

  @Test
  void splitterOfSingle() {
    var single = Option.single("-s");
    var splitter = Splitter.of(single);
    assertAll(
        () -> assertEquals("value", splitter.split("-s", "value").argument(single).orElseThrow()),
        () -> assertTrue(splitter.split().argument(single).isEmpty())
    );
  }

  @Test
  void splitterOfSingle2() {
    var single = Option.single("-s", "--single", "single");
    var splitter = Splitter.of(single);
    assertAll(
        () -> assertEquals("value", splitter.split("-s", "value").argument(single).orElseThrow()),
        () -> assertEquals("value", splitter.split("--single", "value").argument(single).orElseThrow()),
        () -> assertEquals("value", splitter.split("single", "value").argument(single).orElseThrow())
    );
  }

  @Test
  void splitterOfRequire() {
    var required = Option.required("r");
    var splitter = Splitter.of(required);
    assertAll(
        () -> assertEquals("value", splitter.split("value").argument(required)),
        // FIXME, use a proper exception !
        () -> assertThrows(IllegalArgumentException.class, () -> splitter.split().argument(required)),
        // FIXME use a proper exception !
        () -> assertThrows(IllegalArgumentException.class, () -> splitter.split("v1", "v2").argument(required))
    );
  }

  @Test
  void splitterOfRepeatable() {
    var repeatable = Option.repeatable("-p", "--p", "p");
    var splitter = Splitter.of(repeatable);
    assertAll(
        () -> assertEquals(List.of("value"), splitter.split("-p", "value").argument(repeatable)),
        () -> assertEquals(List.of("v1", "v2"), splitter.split("-p", "v1", "-p", "v2").argument(repeatable)),
        () -> assertEquals(List.of(), splitter.split().argument(repeatable))
    );
  }

  @Test
  void splitterOfRepeatable2() {
    var repeatable = Option.repeatable("-p", "--p", "p");
    var splitter = Splitter.of(repeatable);
    assertAll(
        () -> assertEquals(List.of("value"), splitter.split("-p", "value").argument(repeatable)),
        () -> assertEquals(List.of("v1", "v2", "v3"), splitter.split("-p", "v1", "--p", "v2", "p", "v3").argument(repeatable))
    );
  }

  @Test
  void splitterOfVarargs() {
    var varargs = Option.varargs("name");
    var splitter = Splitter.of(varargs);
    assertAll(
        () -> assertArrayEquals(new String[0], splitter.split().argument(varargs)),
        () -> assertArrayEquals(new String[] { "a" }, splitter.split("a").argument(varargs)),
        () -> assertArrayEquals(new String[] { "a", "b" }, splitter.split("a", "b").argument(varargs))
    );
  }

  @Test
  void splitterOfPositionalPrecondition() {
    var requires1 = Option.required("-r1");
    var requires2 = Option.required("-r2");
    var varargs = Option.varargs("name");

    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> Splitter.of(varargs, requires1)),
        () -> assertThrows(IllegalArgumentException.class, () -> Splitter.of(requires1, varargs, requires2))
    );

  }

  @Test
  void splitterOfRequireAndFlag() {
    var flag1 = Option.flag("-flag1");
    var flag2 = Option.flag("-flag2");
    var required1 = Option.required("required1");
    var required2 = Option.required("required2");
    var splitter = Splitter.of(flag1, required1, flag2, required2);
    var argumentMap = splitter.split("-flag1", "-flag2", "r1.txt", "r2.txt");
    assertAll(
        () -> assertTrue(argumentMap.argument(flag1)),
        () -> assertTrue(argumentMap.argument(flag2)),
        () -> assertEquals("r1.txt", argumentMap.argument(required1)),
        () -> assertEquals("r2.txt", argumentMap.argument(required2))
    );
  }

  //@Test
  void splitterOfRequireFlagAndVarargs() {
    var flag1 = Option.flag("-flag1");
    var flag2 = Option.flag("-flag2");
    var required1 = Option.required("required1");
    var required2 = Option.required("required2");
    var varargs = Option.required("varargs");
    var splitter = Splitter.of(flag1, required1, flag2, required2, varargs);
    var argumentMap = splitter.split("-flag1", "-flag2", "r1.txt", "r2.txt", "r3.txt", "r4.txt");
    assertAll(
        () -> assertTrue(argumentMap.argument(flag1)),
        () -> assertTrue(argumentMap.argument(flag2)),
        () -> assertEquals("r1.txt", argumentMap.argument(required1)),
        () -> assertEquals("r2.txt", argumentMap.argument(required2)),
        () -> assertEquals(List.of("r3.txt", "r4.txt"), List.of(argumentMap.argument(varargs)))
    );
  }

  @Test
  void splitterOfRequireAndSingle() {
    var single1 = Option.single("-single1");
    var single2 = Option.single("-single2");
    var required1 = Option.required("required1");
    var required2 = Option.required("required2");
    var splitter = Splitter.of(single1, required1, single2, required2);
    var argumentMap = splitter.split("-single1", "foo", "-single2", "bar", "r1.txt", "r2.txt");
    assertAll(
        () -> assertEquals("foo", argumentMap.argument(single1).orElseThrow()),
        () -> assertEquals("bar", argumentMap.argument(single2).orElseThrow()),
        () -> assertEquals("r1.txt", argumentMap.argument(required1)),
        () -> assertEquals("r2.txt", argumentMap.argument(required2))
    );
  }

  //@Test
  void splitterOfRequireSingleAndVarargs() {
    var single1 = Option.single("-single1");
    var single2 = Option.single("-single2");
    var required1 = Option.required("required1");
    var required2 = Option.required("required2");
    var varargs = Option.required("varargs");
    var splitter = Splitter.of(single2, required1, single1, required2, varargs);
    var argumentMap = splitter.split("-single1", "foo", "-single2", "bar", "r1.txt", "r2.txt", "r3.txt", "r4.txt");
    assertAll(
        () -> assertEquals("foo", argumentMap.argument(single1).orElseThrow()),
        () -> assertEquals("bar", argumentMap.argument(single2).orElseThrow()),
        () -> assertEquals("r1.txt", argumentMap.argument(required1)),
        () -> assertEquals("r2.txt", argumentMap.argument(required2)),
        () -> assertEquals(List.of("r3.txt", "r4.txt"), List.of(argumentMap.argument(varargs)))
    );
  }

  @Test
  void splitterOfRequireAndRepeatable() {
    var repeatable1 = Option.repeatable("-repeatable1");
    var repeatable2 = Option.repeatable("-repeatable2");
    var required1 = Option.required("required1");
    var required2 = Option.required("required2");
    var splitter = Splitter.of(repeatable1, required1, required2, repeatable2);
    var argumentMap = splitter.split("-repeatable1", "foo", "-repeatable2", "bar", "-repeatable1", "baz", "r1.txt", "r2.txt");
    assertAll(
        () -> assertEquals(List.of("foo", "baz"), argumentMap.argument(repeatable1)),
        () -> assertEquals(List.of("bar"), argumentMap.argument(repeatable2)),
        () -> assertEquals("r1.txt", argumentMap.argument(required1)),
        () -> assertEquals("r2.txt", argumentMap.argument(required2))
    );
  }

  //@Test
  void splitterOfRequireRepeatableAndVarargs() {
    var repeatable1 = Option.repeatable("-repeatable1");
    var repeatable2 = Option.repeatable("-repeatable2");
    var required1 = Option.required("required1");
    var required2 = Option.required("required2");
    var varargs = Option.required("varargs");
    var splitter = Splitter.of(repeatable1, required1, required2, repeatable2, varargs);
    var argumentMap = splitter.split("-repeatable1", "foo", "-repeatable2", "bar", "-repeatable2", "baz", "r1.txt", "r2.txt", "r3.txt", "r4.txt");
    assertAll(
        () -> assertEquals(List.of("foo"), argumentMap.argument(repeatable1)),
        () -> assertEquals(List.of("bar", "baz"), argumentMap.argument(repeatable2)),
        () -> assertEquals("r1.txt", argumentMap.argument(required1)),
        () -> assertEquals("r2.txt", argumentMap.argument(required2)),
        () -> assertEquals(List.of("r3.txt", "r4.txt"), List.of(argumentMap.argument(varargs)))
    );
  }

  @Test
  void splitterOfAllOptionWithoutVarargs() {
    var flag1 = Option.flag("-flag1");
    var flag2 = Option.flag("-flag2");
    var single1 = Option.single("-single1");
    var single2 = Option.single("-single2");
    var repeatable1 = Option.repeatable("-repeatable1");
    var repeatable2 = Option.repeatable("-repeatable2");
    var required1 = Option.required("required1");
    var required2 = Option.required("required2");
    var splitter = Splitter.of(single1, repeatable1, required1, flag2, required2, repeatable2, flag1, single2);
    var argumentMap = splitter.split("-single1", "foo", "-flag1", "-repeatable1", "bar", "-repeatable2", "baz", "-single2", "biz", "-repeatable2", "buz", "-flag2", "r1.txt", "r2.txt");
    assertAll(
        () -> assertTrue(argumentMap.argument(flag1)),
        () -> assertTrue(argumentMap.argument(flag2)),
        () -> assertEquals("foo",argumentMap.argument(single1).orElseThrow()),
        () -> assertEquals("biz", argumentMap.argument(single2).orElseThrow()),
        () -> assertEquals(List.of("bar"), argumentMap.argument(repeatable1)),
        () -> assertEquals(List.of("baz", "buz"), argumentMap.argument(repeatable2)),
        () -> assertEquals("r1.txt", argumentMap.argument(required1)),
        () -> assertEquals("r2.txt", argumentMap.argument(required2))
    );
  }

  //@Test
  void splitterOfAllOptionWithVarargs() {
    var flag1 = Option.flag("-flag1");
    var flag2 = Option.flag("-flag2");
    var single1 = Option.single("-single1");
    var single2 = Option.single("-single2");
    var repeatable1 = Option.repeatable("-repeatable1");
    var repeatable2 = Option.repeatable("-repeatable2");
    var required1 = Option.required("required1");
    var required2 = Option.required("required2");
    var varargs = Option.required("varargs");
    var splitter = Splitter.of(single1, repeatable1, required1, flag2, required2, repeatable2, varargs, flag1, single2);
    var argumentMap = splitter.split("-single1", "foo", "-flag1", "-repeatable1", "bar", "-repeatable2", "baz", "-single2", "biz", "-repeatable2", "buz", "-flag2", "r1.txt", "r2.txt", "r3.txt", "r4.txt");
    assertAll(
        () -> assertTrue(argumentMap.argument(flag1)),
        () -> assertTrue(argumentMap.argument(flag2)),
        () -> assertEquals("foo",argumentMap.argument(single1).orElseThrow()),
        () -> assertEquals("biz", argumentMap.argument(single2).orElseThrow()),
        () -> assertEquals(List.of("bar"), argumentMap.argument(repeatable1)),
        () -> assertEquals(List.of("baz", "buz"), argumentMap.argument(repeatable2)),
        () -> assertEquals("r1.txt", argumentMap.argument(required1)),
        () -> assertEquals("r2.txt", argumentMap.argument(required2)),
        () -> assertEquals(List.of("r3.txt", "r4.txt"), List.of(argumentMap.argument(varargs)))
    );
  }

  @Test
  void splitterOfAllOptionListArgsWithoutVarargs() {
    var flag1 = Option.flag("-flag1");
    var flag2 = Option.flag("-flag2");
    var single1 = Option.single("-single1");
    var single2 = Option.single("-single2");
    var repeatable1 = Option.repeatable("-repeatable1");
    var repeatable2 = Option.repeatable("-repeatable2");
    var required1 = Option.required("required1");
    var required2 = Option.required("required2");
    var splitter = Splitter.of(single1, repeatable1, required1, flag2, required2, repeatable2, flag1, single2);
    var argumentMap = splitter.split(List.of("-single1", "foo", "-flag1", "-repeatable1", "bar", "-repeatable2", "baz", "-single2", "biz", "-repeatable2", "buz", "-flag2", "r1.txt", "r2.txt"));
    assertAll(
        () -> assertTrue(argumentMap.argument(flag1)),
        () -> assertTrue(argumentMap.argument(flag2)),
        () -> assertEquals("foo",argumentMap.argument(single1).orElseThrow()),
        () -> assertEquals("biz", argumentMap.argument(single2).orElseThrow()),
        () -> assertEquals(List.of("bar"), argumentMap.argument(repeatable1)),
        () -> assertEquals(List.of("baz", "buz"), argumentMap.argument(repeatable2)),
        () -> assertEquals("r1.txt", argumentMap.argument(required1)),
        () -> assertEquals("r2.txt", argumentMap.argument(required2))
    );
  }

  //@Test
  void splitterOfAllOptionListArgsWithVarargs() {
    var flag1 = Option.flag("-flag1");
    var flag2 = Option.flag("-flag2");
    var single1 = Option.single("-single1");
    var single2 = Option.single("-single2");
    var repeatable1 = Option.repeatable("-repeatable1");
    var repeatable2 = Option.repeatable("-repeatable2");
    var required1 = Option.required("required1");
    var required2 = Option.required("required2");
    var varargs = Option.required("varargs");
    var splitter = Splitter.of(single1, repeatable1, required1, flag2, required2, repeatable2, varargs, flag1, single2);
    var argumentMap = splitter.split(List.of("-single1", "foo", "-flag1", "-repeatable1", "bar", "-repeatable2", "baz", "-single2", "biz", "-repeatable2", "buz", "-flag2", "r1.txt", "r2.txt", "r3.txt", "r4.txt"));
    assertAll(
        () -> assertTrue(argumentMap.argument(flag1)),
        () -> assertTrue(argumentMap.argument(flag2)),
        () -> assertEquals("foo",argumentMap.argument(single1).orElseThrow()),
        () -> assertEquals("biz", argumentMap.argument(single2).orElseThrow()),
        () -> assertEquals(List.of("bar"), argumentMap.argument(repeatable1)),
        () -> assertEquals(List.of("baz", "buz"), argumentMap.argument(repeatable2)),
        () -> assertEquals("r1.txt", argumentMap.argument(required1)),
        () -> assertEquals("r2.txt", argumentMap.argument(required2)),
        () -> assertEquals(List.of("r3.txt", "r4.txt"), List.of(argumentMap.argument(varargs)))
    );
  }

  @Test
  void splitterOfAllNonPositional() {
    var flag1 = Option.flag("-flag1");
    var flag2 = Option.flag("-flag2");
    var single1 = Option.single("-single1");
    var single2 = Option.single("-single2");
    var repeatable1 = Option.repeatable("-repeatable1");
    var repeatable2 = Option.repeatable("-repeatable2");
    var splitter = Splitter.of(single1, repeatable1, flag2, repeatable2, flag1, single2);
    var argumentMap = splitter.split("-single1", "foo", "-flag1", "-repeatable1", "bar", "-repeatable2", "baz", "-single2", "biz", "-repeatable1", "buz", "-flag2");
    assertAll(
        () -> assertTrue(argumentMap.argument(flag1)),
        () -> assertTrue(argumentMap.argument(flag2)),
        () -> assertEquals("foo",argumentMap.argument(single1).orElseThrow()),
        () -> assertEquals("biz", argumentMap.argument(single2).orElseThrow()),
        () -> assertEquals(List.of("bar", "buz"), argumentMap.argument(repeatable1)),
        () -> assertEquals(List.of("baz"), argumentMap.argument(repeatable2))
    );
  }

  //@Test
  void splitterOfAllNonPositionalWithVarargs() {
    var flag1 = Option.flag("-flag1");
    var flag2 = Option.flag("-flag2");
    var single1 = Option.single("-single1");
    var single2 = Option.single("-single2");
    var repeatable1 = Option.repeatable("-repeatable1");
    var repeatable2 = Option.repeatable("-repeatable2");
    var varargs = Option.required("varargs");
    var splitter = Splitter.of(varargs, single1, repeatable1, flag2, repeatable2, flag1, single2);
    var argumentMap = splitter.split("-single1", "foo", "-flag1", "-repeatable1", "bar", "-repeatable2", "baz", "-single2", "biz", "-repeatable1", "buz", "-flag2", "r3.txt", "r4.txt");
    assertAll(
        () -> assertTrue(argumentMap.argument(flag1)),
        () -> assertTrue(argumentMap.argument(flag2)),
        () -> assertEquals("foo",argumentMap.argument(single1).orElseThrow()),
        () -> assertEquals("biz", argumentMap.argument(single2).orElseThrow()),
        () -> assertEquals(List.of("bar", "buz"), argumentMap.argument(repeatable1)),
        () -> assertEquals(List.of("baz"), argumentMap.argument(repeatable2)),
        () -> assertEquals(List.of("r3.txt", "r4.txt"), List.of(argumentMap.argument(varargs)))
    );
  }

  @Test
  void splitterOfOptionMapConversions() {
    var flag = Option.flag("-flag").convert(b -> !b);
    var single = Option.single("-single").convert(String::length);
    var repeatable = Option.repeatable("-repeatable").convert(String::length);
    var required = Option.required("required").convert(String::length);
    var varargs = Option.varargs("varargs").convert(String::length, Integer[]::new);

    assertAll(
        () -> assertFalse(Splitter.of(flag).split("-flag").argument(flag)),
        () -> assertEquals(5, Splitter.of(single).split("-single", "value").argument(single).orElseThrow()),
        () -> assertEquals(List.of(5), Splitter.of(repeatable).split("-repeatable", "value").argument(repeatable)),
        () -> assertEquals(7, Splitter.of(required).split("foo.txt").argument(required)),
        () -> assertArrayEquals(new Integer[] { 7 }, Splitter.of(varargs).split("foo.txt").argument(varargs))
    );
  }

  @Test
  void splitterOfOptionsPreconditions() {
    var required = Option.required("foo");
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> Splitter.of((Option<?>[]) null)),
        () -> assertThrows(NullPointerException.class, () -> Splitter.of(required, null))
    );
  }

  @Test
  void splitterOfRecordPreconditions() {
    var lookup = lookup();
    record Schema(String name) {}

    assertAll(
        () -> assertThrows(NullPointerException.class, () -> Splitter.of(null, Schema.class)),
        () -> assertThrows(NullPointerException.class, () -> Splitter.of(lookup, null)),
        () -> assertThrows(NullPointerException.class, () -> Splitter.of(lookup, Schema.class, null))
    );
  }

  @Test
  void splitPreconditions() {
    var required = Option.required("foo");
    var splitter = Splitter.of(required);

    assertAll(
        () -> assertThrows(NullPointerException.class, () -> splitter.split((List<String>) null)),
        () -> assertThrows(NullPointerException.class, () -> splitter.split((String[]) null)),
        () -> assertThrows(NullPointerException.class, () -> splitter.split((Stream<String>) null))
    );
  }
}