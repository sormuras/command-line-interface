package test.unit;

import main.Option;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

import java.util.List;

import static test.api.Assertions.assertAll;
import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertThrows;
import static test.api.Assertions.assertTrue;

public class ArgumentMapTests {
  public static void main(String... args) {
    JTest.runTests(new ArgumentMapTests(), args);
  }

  @Test
  void argument() {
    var flagF = Option.flag("-f");
    var singleG = Option.single("-g");
    var requiredH= Option.required("h");
    var repeatableI = Option.repeatable("-i");
    var varargsJ = Option.varargs("j");
    var splitter = Splitter.of(flagF, singleG, requiredH, repeatableI, varargsJ);
    var argumentMap = splitter.split("-f", "h.txt");

    assertAll(
        () -> assertTrue(argumentMap.argument(flagF)),
        () -> assertTrue(argumentMap.argument(singleG).isEmpty()),
        () -> assertEquals("h.txt", argumentMap.argument(requiredH)),
        () -> assertEquals(List.of(), argumentMap.argument(repeatableI)),
        () -> assertEquals(0, argumentMap.argument(varargsJ).length)
    );
  }

  @Test
  void argumentPreconditions() {
    var flag = Option.flag("-f");
    var splitter = Splitter.of(flag);
    var argumentMap = splitter.split("-f");

    var flag2 = Option.flag("-f");
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> argumentMap.argument(null))
        //() -> assertThrows(IllegalStateException.class, () -> argumentMap.argument(flag2))
    );
  }
}
