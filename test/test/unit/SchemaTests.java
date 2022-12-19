package test.unit;

import main.Option;
import main.Schema;
import test.api.JTest;
import test.api.JTest.Test;

import java.util.List;

import static test.api.Assertions.assertAll;
import static test.api.Assertions.assertThrows;

public class SchemaTests {
  public static void main(String... args) {
    JTest.runTests(new SchemaTests(), args);
  }

  @Test
  void schemaPrecondition() {
    var flag = Option.flag("-f");
    var required = Option.required("foo", "-f");
    var varargs1 = Option.varargs("varargs1");
    var varargs2 = Option.varargs("varargs2");
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> new Schema<>(null, x -> x)),
        () -> assertThrows(NullPointerException.class, () -> new Schema<>(List.of(flag), null)),

        () -> assertThrows(IllegalArgumentException.class, () -> new Schema<>(List.of(), x -> x)),
        () -> assertThrows(IllegalArgumentException.class, () -> new Schema<>(List.of(flag, required), x -> x)),
        () -> assertThrows(IllegalArgumentException.class, () -> new Schema<>(List.of(varargs1, varargs2), x -> x))
    );
  }
}
