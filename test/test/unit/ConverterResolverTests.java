package test.unit;

import main.Converter;
import main.ConverterResolver;
import main.ConverterResolver.ConverterMirror;
import main.ConverterResolver.TypeReference;
import test.api.JTest;
import test.api.JTest.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.Runtime.Version;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static java.lang.invoke.MethodHandles.lookup;
import static test.api.Assertions.assertAll;
import static test.api.Assertions.assertArrayEquals;
import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertThrows;
import static test.api.Assertions.assertTrue;
import static test.api.Assertions.fail;

public class ConverterResolverTests {
  public static void main(String... args) {
    JTest.runTests(new ConverterResolverTests(), args);
  }

  @Test
  void resolverOf() {
    var resolver = ConverterResolver.of((lookup, valueType) -> {
      assertAll(
          () -> assertEquals(lookup().lookupClass(), lookup.lookupClass()),
          () -> assertEquals(String.class, valueType)
      );
      return Optional.of(x -> x);
    });
    var converter = resolver.resolve(lookup(), String.class);
    assertEquals("foo", converter.orElseThrow().apply("foo"));
  }

  @Test
  void resolverOfPreconditions() {
    assertThrows(NullPointerException.class, () -> ConverterResolver.of(null));
  }

  @Test
  void resolveWithTypeReferenceString() {
    var resolver = ConverterResolver.of((lookup, valueType) -> {
      assertEquals(String.class, valueType);
      return Optional.empty();
    });
    var typeReference = new TypeReference<String>() {};
    var converter = resolver.resolve(lookup(), typeReference);
    assertTrue(converter.isEmpty());
  }

  @Test
  void resolveWithTypeReferenceOptional() {
    var resolver = ConverterResolver.of((lookup, valueType) -> {
      assertAll(
          () -> assertTrue(valueType instanceof ParameterizedType),
          () -> assertTrue(valueType instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() == Optional.class),
          () -> assertEquals(String.class, valueType instanceof ParameterizedType parameterizedType ? parameterizedType.getActualTypeArguments()[0] : null)
      );
      return Optional.empty();
    });
    var typeReference = new TypeReference<Optional<String>>() {};
    var converter = resolver.resolve(lookup(), typeReference);
    assertTrue(converter.isEmpty());
  }

  @Test
  void resolveWithTypeReferencePreconditions() {
    var resolver = ConverterResolver.of((lookup, valueType) -> Optional.empty());
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> resolver.resolve(null, new TypeReference<Integer>() {})),
        () -> assertThrows(NullPointerException.class, () -> resolver.resolve(lookup(), (TypeReference<?>) null))
    );
  }

  @Test
  void typeReferenceBadUsageMoreThanOneInterface() {
    var resolver = ConverterResolver.of((lookup, valueType) -> Optional.empty());
    interface AnotherInterface {}
    class BadTypeReference implements AnotherInterface, TypeReference<String> {}

    assertThrows(IllegalStateException.class, () -> resolver.resolve(lookup(), new BadTypeReference()));
  }

  @Test
  void typeReferenceBadUsageInheritance() {
    var resolver = ConverterResolver.of((lookup, valueType) -> Optional.empty());
    interface BadInterface extends TypeReference<String> {}

    assertThrows(IllegalStateException.class, () -> resolver.resolve(lookup(), new BadInterface() {}));
  }


  @Test
  void resolverOr() {
    var resolver1 = ConverterResolver.of((lookup, valueType) -> Optional.of(x -> "foo"));
    var resolver2 = ConverterResolver.of((lookup, valueType) -> Optional.of(x -> "bar"));
    var resolver = resolver1.or(resolver2);
    assertEquals("foo", resolver.resolve(lookup(), String.class).orElseThrow().apply(""));
  }

  @Test
  void resolverOrWithEmpty() {
    var resolver1 = ConverterResolver.of((lookup, valueType) -> Optional.empty());
    var resolver2 = ConverterResolver.of((lookup, valueType) -> Optional.of(x -> "bar"));
    var resolver = resolver1.or(resolver2);
    assertEquals("bar", resolver.resolve(lookup(), String.class).orElseThrow().apply(""));
  }

  @Test
  void resolverOptionalUnwrap() {
    var resolver = ConverterResolver.of((lookup, valueType) -> {
      if (valueType == Integer.class) {
        return Optional.of(arg -> Integer.parseInt((String) arg));
      }
      return Optional.empty();
    });
    var unwrappedResolver = resolver.unwrap();

    assertEquals(77, unwrappedResolver.resolve(lookup(), new TypeReference<Optional<Integer>>() {}).orElseThrow().apply(Optional.of("77")).orElseThrow());
  }

  @Test
  void resolverListUnwrap() {
    var resolver = ConverterResolver.of((lookup, valueType) -> {
      if (valueType == Integer.class) {
        return Optional.of(arg -> Integer.parseInt((String) arg));
      }
      return Optional.empty();
    });
    var unwrappedResolver = resolver.unwrap();

    assertEquals(List.of(3, 4), unwrappedResolver.resolve(lookup(), new TypeReference<List<Integer>>() {}).orElseThrow().apply(List.of("3", "4")));
  }

  @Test
  void resolverArrayUnwrap() {
    var resolver = ConverterResolver.of((lookup, valueType) -> {
      if (valueType == Integer.class) {
        return Optional.of(arg -> Integer.parseInt((String) arg));
      }
      return Optional.empty();
    });
    var unwrappedResolver = resolver.unwrap();

    assertArrayEquals(new Integer[]{ 3, 4 }, unwrappedResolver.resolve(lookup(), new TypeReference<Integer[]>() {}).orElseThrow().apply(new String[] { "3", "4" }));
  }

  @Test
  void resolverBasicUnwrap() {
    var resolver = ConverterResolver.of((lookup, valueType) -> {
      if (valueType == Integer.class) {
        return Optional.of(arg -> Integer.parseInt((String) arg));
      }
      return Optional.empty();
    });
    var unwrappedResolver = resolver.unwrap();

    assertEquals(101, unwrappedResolver.resolve(lookup(), new TypeReference<Integer>() {}).orElseThrow().apply("101"));
  }

  @Test
  void resolverWhenPredicate() {
    var resolver = ConverterResolver.when(Integer.class::equals, arg -> Integer.parseInt((String) arg));

    assertAll(
        () -> assertEquals(42, resolver.resolve(lookup(), Integer.class).orElseThrow().apply("42")),
        () -> assertTrue(resolver.resolve(lookup(), String.class).isEmpty())
    );
  }

  @Test
  void resolverWhenPredicatePreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> ConverterResolver.when((Predicate<Type>) null, x -> x)),
        () -> assertThrows(NullPointerException.class, () -> ConverterResolver.when(String.class::equals, null))
    );
  }

  @Test
  void resolverWhenClass() {
    var resolver = ConverterResolver.when(Integer.class, arg -> Integer.parseInt((String) arg));

    assertAll(
        () -> assertEquals(42, resolver.resolve(lookup(), Integer.class).orElseThrow().apply("42")),
        () -> assertTrue(resolver.resolve(lookup(), String.class).isEmpty())
    );
  }

  @Test
  void resolverWhenClassPreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> ConverterResolver.when((Class<?>) null, x -> x)),
        () -> assertThrows(NullPointerException.class, () -> ConverterResolver.when(String.class, null))
    );
  }


  @Test
  void converterFunctionAndType() {
    var converter = ConverterResolver.methodHandle(Integer::parseInt, String.class);
    assertEquals(123, converter.apply("123"));
  }

  @Test
  void converterFunctionAndTypePreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> ConverterResolver.methodHandle(null, String.class)),
        () -> assertThrows(NullPointerException.class, () -> ConverterResolver.methodHandle(x -> x, null))
    );
  }

  @Test
  void stringConverterFunction() {
    var converter = ConverterResolver.stringConverter(Integer::parseInt);
    assertEquals(123, converter.apply("123"));
  }

  @Test
  void stringConverterFunctionPreconditions() {
    assertThrows(NullPointerException.class, () -> ConverterResolver.stringConverter(null));
  }


  @Test
  void resolverBasic() {
    record Person(String name, int age) {}
    assertAll(
        () -> assertEquals(true, ConverterResolver.basic(lookup(), boolean.class).orElseThrow().apply(true)),
        () -> assertEquals(false, ConverterResolver.basic(lookup(), boolean.class).orElseThrow().apply(false)),
        () -> assertEquals(true, ConverterResolver.basic(lookup(), Boolean.class).orElseThrow().apply(true)),
        () -> assertEquals(false, ConverterResolver.basic(lookup(), Boolean.class).orElseThrow().apply(false)),
        () -> assertEquals("foo", ConverterResolver.basic(lookup(), String.class).orElseThrow().apply("foo")),
        () -> assertEquals(new Person("Bob", 12), ConverterResolver.basic(lookup(), String.class).orElseThrow().apply(new Person("Bob", 12))),

        () -> assertTrue(ConverterResolver.basic(lookup(), Integer.class).isEmpty()),
        () -> assertTrue(ConverterResolver.basic(lookup(), Path.class).isEmpty())
    );
  }

  @Test
  void resolverBasicPreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> ConverterResolver.basic(null, boolean.class)),
        () -> assertThrows(NullPointerException.class, () -> ConverterResolver.basic(lookup(), null))
    );
  }

  @Test
  void resolverEnumerated() {
    enum Level { info, error }
    assertAll(
        () -> assertEquals(Level.info, ConverterResolver.enumerated(lookup(), Level.class).orElseThrow().apply("info")),
        () -> assertEquals(Level.error, ConverterResolver.enumerated(lookup(), Level.class).orElseThrow().apply("error")),

        () -> assertTrue(ConverterResolver.enumerated(lookup(), String.class).isEmpty()),
        () -> assertTrue(ConverterResolver.enumerated(lookup(), Path.class).isEmpty())
    );
  }

  @Test
  void resolverEnumeratedPreconditions() {
    enum Level {}
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> ConverterResolver.enumerated(null, Level.class)),
        () -> assertThrows(NullPointerException.class, () -> ConverterResolver.enumerated(lookup(), null))
    );
  }


  @Test
  void resolverReflectedValueOfString() {
    record Data(String text) {
      @SuppressWarnings("unused")
      public static Data valueOf(String text) {
        return new Data(text);
      }
    }
    assertEquals(new Data("foo"), ConverterResolver.reflected(lookup(), Data.class).orElseThrow().apply("foo"));
  }

  @Test
  void resolverReflectedOfString() {
    record Data(String text) {
      @SuppressWarnings("unused")
      public static Data of(String text) {
        return new Data(text);
      }
    }
    assertEquals(new Data("foo"), ConverterResolver.reflected(lookup(), Data.class).orElseThrow().apply("foo"));
  }

  @Test
  void resolverReflectedOfStringVarargs() {
    record Data(String text) {
      @SuppressWarnings("unused")
      public static Data of(String text, String... rest) {
        assertEquals(0, rest.length);
        return new Data(text);
      }
    }
    assertEquals(new Data("foo"), ConverterResolver.reflected(lookup(), Data.class).orElseThrow().apply("foo"));
  }

  @Test
  void resolverReflectedOfStringNotVarargs() {
    record Data(String text) {
      @SuppressWarnings("unused")
      public static Data of(String text, String[] rest) {
        throw fail("should not be called");
      }
    }
    assertTrue(ConverterResolver.reflected(lookup(), Data.class).isEmpty());
  }

  @Test
  void resolverReflectedParseString() {
    record Data(String text) {
      @SuppressWarnings("unused")
      public static Data parse(String text) {
        return new Data(text);
      }
    }
    assertEquals(new Data("foo"), ConverterResolver.reflected(lookup(), Data.class).orElseThrow().apply("foo"));
  }

  @Test
  void resolverReflectedParseCharSequence() {
    record Data(String text) {
      @SuppressWarnings("unused")
      public static Data parse(CharSequence text) {
        return new Data(text.toString());
      }
    }
    assertEquals(new Data("foo"), ConverterResolver.reflected(lookup(), Data.class).orElseThrow().apply("foo"));
  }

  @Test
  void resolverReflectedExceptionTransparency() {
    record Data(String text) {
      @SuppressWarnings("unused")
      public static Data of(String text) {
        throw new UncheckedIOException(new IOException(text));
      }
    }
    assertThrows(UncheckedIOException.class, () -> ConverterResolver.reflected(lookup(), Data.class).orElseThrow().apply("foo"));
  }

  @Test
  void resolverReflectedCheckedExceptionAreWrapped() {
    record Data(String text) {
      @SuppressWarnings("unused")
      public static Data of(String text) throws IOException {
        throw new IOException(text);
      }
    }
    assertThrows(UndeclaredThrowableException.class, () -> ConverterResolver.reflected(lookup(), Data.class).orElseThrow().apply("foo"));
  }

  @Test
  void resolverReflectedNotAClass() {
    var resolver = ConverterResolver.of(ConverterResolver::reflected);
    assertTrue(resolver.resolve(lookup(), new TypeReference<List<String>>() {}).isEmpty());
  }

  @Test
  void resolverReflectedPreconditions() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> ConverterResolver.reflected(null, Path.class)),
        () -> assertThrows(NullPointerException.class, () -> ConverterResolver.enumerated(lookup(), null))
    );
  }


  @Test
  void resolverDefault() {
    var resolver = ConverterResolver.defaultResolver();

    record Person(String name, int age) {}
    enum Level { info, error }

    assertAll(
        // basic
        () -> assertEquals(true, resolver.resolve(lookup(), boolean.class).orElseThrow().apply(true)),
        () -> assertEquals(false, resolver.resolve(lookup(), boolean.class).orElseThrow().apply(false)),
        () -> assertEquals(true, resolver.resolve(lookup(), Boolean.class).orElseThrow().apply(true)),
        () -> assertEquals(false, resolver.resolve(lookup(), Boolean.class).orElseThrow().apply(false)),
        () -> assertEquals("foo", resolver.resolve(lookup(), String.class).orElseThrow().apply("foo")),
        () -> assertEquals(new Person("Bob", 12), resolver.resolve(lookup(), String.class).orElseThrow().apply(new Person("Bob", 12))),

        // enumerated
        () -> assertEquals(Level.info, resolver.resolve(lookup(), Level.class).orElseThrow().apply("info")),
        () -> assertEquals(Level.error, resolver.resolve(lookup(), Level.class).orElseThrow().apply("error")),

        // reflected
        () -> assertEquals((byte) 42, resolver.resolve(lookup(), Byte.class).orElseThrow().apply("42")),  // Byte.valueOf(String)
        () -> assertEquals(ClassDesc.of("java.lang.String"), resolver.resolve(lookup(), ClassDesc.class).orElseThrow().apply("java.lang.String")),  // ClassDesc.of(String)
        () -> assertEquals(Path.of("foo.txt"), resolver.resolve(lookup(), Path.class).orElseThrow().apply("foo.txt")),  // Path.of(String, String...)
        () -> assertEquals(Version.parse("17"), resolver.resolve(lookup(), Version.class).orElseThrow().apply("17")),  // Version.parse(String)
        () -> assertEquals(LocalDate.of(2007, 12, 3), resolver.resolve(lookup(), LocalDate.class).orElseThrow().apply("2007-12-03"))  // LocaleDate.parse(CharSequence)
    );
  }

  @Test
  void resolverDefaultNotFound() {
    var resolver = ConverterResolver.defaultResolver();

    final class Empty {}

    assertTrue(resolver.resolve(lookup(), Empty.class).isEmpty());
  }

  @Test
  void mirrorBasic() {
    var resolver = ConverterResolver.defaultResolver();

    var lookup = MethodHandles.lookup();
    var converter = resolver.resolve(lookup, String.class).orElseThrow();

    var mirror = ConverterMirror.of(lookup, converter).orElseThrow();
    var expected = new ConverterMirror(ConverterResolver.class.getName(), "basic", List.of());
    assertEquals(expected, mirror);
  }

  @Test
  void mirrorReflectedPathOf() {
    var resolver = ConverterResolver.defaultResolver();

    var lookup = MethodHandles.lookup();
    var converter = resolver.resolve(lookup, Path.class).orElseThrow();

    var mirror = ConverterMirror.of(lookup, converter).orElseThrow();
    var expected = new ConverterMirror(ConverterResolver.class.getName(), "reflected",
        List.of(new ConverterMirror(Path.class.getName(), "of", List.of())));
    assertEquals(expected, mirror);
  }

  @Test
  void mirrorEnum() {
    enum Foo {}
    var resolver = ConverterResolver.defaultResolver();

    var lookup = MethodHandles.lookup();
    var converter = resolver.resolve(lookup, Foo.class).orElseThrow();

    var mirror = ConverterMirror.of(lookup, converter).orElseThrow();
    var expected = new ConverterMirror(ConverterResolver.class.getName(), "enumerated", List.of(Foo.class.getName()));
    assertEquals(expected, mirror);
  }

  @Test
  void mirrorUnwrapOptionalOfString() {
    var resolver = ConverterResolver.defaultResolver();

    var lookup = MethodHandles.lookup();
    var converter = resolver.resolve(lookup, new TypeReference<Optional<String>>() {}).orElseThrow();

    var mirror = ConverterMirror.of(lookup, converter).orElseThrow();
    var expected = new ConverterMirror(ConverterResolver.class.getName(), "unwrap",
        List.of(new ConverterMirror(ConverterResolver.class.getName(), "basic", List.of())));
    assertEquals(expected, mirror);
  }

  @Test
  void mirrorUnwrapListOfString() {
    var resolver = ConverterResolver.defaultResolver();

    var lookup = MethodHandles.lookup();
    var converter = resolver.resolve(lookup, new TypeReference<List<String>>() {}).orElseThrow();

    var mirror = ConverterMirror.of(lookup, converter).orElseThrow();
    var expected = new ConverterMirror(ConverterResolver.class.getName(), "unwrap",
        List.of(new ConverterMirror(ConverterResolver.class.getName(), "basic", List.of())));
    assertEquals(expected, mirror);
  }

  @Test
  void mirrorUnwrapArrayOfString() {
    var resolver = ConverterResolver.defaultResolver();

    var lookup = MethodHandles.lookup();
    var converter = resolver.resolve(lookup, String[].class).orElseThrow();

    var mirror = ConverterMirror.of(lookup, converter).orElseThrow();
    var expected = new ConverterMirror(ConverterResolver.class.getName(), "unwrap",
        List.of(new ConverterMirror(ConverterResolver.class.getName(), "basic", List.of()), String.class.getName()));
    assertEquals(expected, mirror);
  }

  @Test
  void mirrorUserDefinedLambda() {
    Converter<?,?> converter = o -> "*" + o + "*";

    var mirror = ConverterMirror.of(MethodHandles.lookup(), converter).orElseThrow();
    var expected = new ConverterMirror(ConverterResolverTests.class.getName(), "mirrorUserDefinedLambda", List.of());
    assertEquals(expected, mirror);
  }

  @Test
  void mirrorUserDefinedMethodReference() {
    Converter<String,Integer> converter = Integer::parseInt;

    var mirror = ConverterMirror.of(MethodHandles.lookup(), converter).orElseThrow();
    var expected = new ConverterMirror(Integer.class.getName(), "parseInt", List.of());
    assertEquals(expected, mirror);
  }

  @Test
  void mirrorUserDefinedAnonymousClass() {
    var converter = new Converter<String, String>() {
      @Override
      public String apply(String s) {
        return s;
      }
    };

    var mirror = ConverterMirror.of(MethodHandles.lookup(), converter);
    assertTrue(mirror.isEmpty());
  }
}
