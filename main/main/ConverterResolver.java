package main;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.invoke.MethodType.methodType;
import static java.util.Objects.requireNonNull;

/**
 * Find a converter (a conversion function) that converts a String/boolean/record
 * to a Java generic type.
 *
 * <p>This API proposes a toolkit to build your own revolver by composition.
 *
 * <p>For example, the default resolver is defined as such
 *
 * <pre>
 *   ConverterResolver defaultResolver =
 *       ConverterResolver.of(ConverterResolver::basic)
 *           .or(ConverterResolver::enumerated)
 *           .or(ConverterResolver::reflected)
 *           .unwrap();
 * </pre>
 *
 * {@code ConverterResolver::base} is a resolver that associate the identity for the base
 * types, String, boolean, Boolean and records.
 *
 * <p>{@code ConverterResolver::enumerated} is a resolver that calls {@code Enum.valueOf}
 * if the type is an enum
 *
 * <p>{@code ConverterResolver::reflected} is a resolver that tries to find a static method
 * factory to convert a type from String inside the type.
 *
 * <p>{@code or(resolver)} executes the first resolver and if there is no converter found,
 * executes the second converter.
 *
 * <p>{@code unwrap()} provides a resolver that unwrap Optional, List and array and executes
 * the resolver on the component type.
 *
 * <p>A converter resolver is used by {@link Splitter#of(Lookup, Class, ConverterResolver)}
 * that specify how the value of an option is converted to the type of the corresponding record component.
 */
@FunctionalInterface
public interface ConverterResolver {
  /**
   * A class used to send a generic Java {@link Type} to a converter resolver.
   * @param <R> the Java type.
   *
   * @see #resolve(Lookup, TypeReference)
   */
  interface TypeReference<R> {
    private Type extract() {
      var genericInterfaces = getClass().getGenericInterfaces();
      if (genericInterfaces.length == 1
          && genericInterfaces[0] instanceof ParameterizedType parameterizedType
          && parameterizedType.getRawType() == TypeReference.class) {
        return parameterizedType.getActualTypeArguments()[0];
      }
      throw new IllegalStateException("the TypeReference is malformed " + Arrays.toString(genericInterfaces));
    }
  }

  /**
   * Returns a converter (a conversion function) for a Java type.
   *
   * @param lookup the lookup used if reflection is involved to find the converter.
   * @param type a generic java type
   * @return a converter (a conversion function) for a specific type or Optional.empty() if no converter is found.
   */
  Optional<Function<Object, ?>> resolve(Lookup lookup, Type type);

  /**
   * Returns a converter (a conversion function) for a Java type specified as type argument of a type reference.
   *
   * @param lookup the lookup used if reflection is involved to find the converter.
   * @param typeReference the type reference used to extract the Java generic type from.
   * @return a converter (a conversion function) for a Java type specified as type argument of a type reference.
   * @param <R> the generic Java type.
   */
  @SuppressWarnings("unchecked")
  default <R> Optional<Function<Object, R>> resolve(Lookup lookup, TypeReference<R> typeReference) {
    Objects.requireNonNull(lookup, "lookup is null");
    Objects.requireNonNull(typeReference, "typeReference is null");
    return (Optional<Function<Object, R>>) (Optional<? extends Function<?,?>>) resolve(lookup, typeReference.extract());
  }

  /**
   * Returns a resolver that will first try to find the converter (a conversion function) from the current resolver
   * and if not available from the resolver taken as parameter.
   *
   * @param resolver a second resolver.
   * @return a new resolver that will resolve using the current resolver and the second resolver otherwise.
   */
  default ConverterResolver or(ConverterResolver resolver) {
    requireNonNull(resolver, "resolver is null");
    return (lookup, valueType) -> resolve(lookup, valueType)
        .or(() -> resolver.resolve(lookup, valueType));
  }

  /**
   * Returns a new resolver that will unwrap Optional, List or array and calls the current resolver
   * with the component type.
   *
   * @return a new resolver that will unwrap Optional, List or array and calls the current resolver
   * with the component type.
   */
  default ConverterResolver unwrap() {
    return (lookup, valueType) -> unwrap(lookup, valueType, this);
  }

  /**
   * Returns the resolver taken as parameter.
   * This allows to type a lambda from right to left.
   * <p>
   * This does not compile
   * <pre>
   *   var resolver = (lookup, valueType) ->  ....
   * </pre>
   * But this does
   * <pre>
   *   var resolver = ConverterResolver.Of((lookup, valueType) -> ...);
   * </pre>
   *
   * @param resolver the resolver.
   * @return the resolver taken as parameter.
   */
  static ConverterResolver of(ConverterResolver resolver) {
    requireNonNull(resolver, "resolver is null");
    return resolver;
  }

  /**
   * Returns a resolver that will call the converter if the predicate is true.
   *
   * @param predicate a predicate on the valueType (as a {@code java.lang.Class})
   * @param converter the converter to call
   * @return a resolver that will call the converter if the predicate is true.
   */
  static ConverterResolver when(Predicate<? super Class<?>> predicate, Function<Object, ?> converter) {
    requireNonNull(predicate, "predicate is null");
    requireNonNull(converter, "converter is null");
    return (lookup, valueType) -> Optional.<Function<Object, ?>>of(converter)
        .filter(__ -> valueType instanceof Class<?> clazz && predicate.test(clazz));
  }

  /**
   * Returns a resolver that will call the converter if the valueType is the one taken as parameter.
   * <p>
   * The implementation is equivalent to
   * <pre>
   *   when(valueType::equals, converter)
   * </pre>
   *
   * @param valueType the value type to check
   * @param converter the converter to call
   * @return a resolver that will call the converter if the valueType is the one taken as parameter.
   */
  static ConverterResolver when(Class<?> valueType, Function<Object, ?> converter) {
    requireNonNull(valueType, "valueType is null");
    requireNonNull(converter, "converter is null");
    return when(valueType::equals, converter);
  }

  /**
   * Return the default resolver.
   * The default resolver first try to unwrap Optional, Liat or any object array then
   * calls {@link #basic(Lookup, Type)}, {@link #enumerated(Lookup, Type)} and then {@link #reflected(Lookup, Type)}.
   *
   * <p>The implementation is equivalent to
   * <pre>
   *   ConverterResolver.of(ConverterResolver::basic)
   *       .or(ConverterResolver::enumerated)
   *       .or(ConverterResolver::reflected)
   *       .unwrap();
   * </pre>
   *
   * @return the default resolver.
   */
  static ConverterResolver defaultResolver() {
    final class Default {
      private static final ConverterResolver DEFAULT_RESOLVER =
          of(ConverterResolver::basic)
              .or(ConverterResolver::enumerated)
              .or(ConverterResolver::reflected)
              .unwrap();
    }
    return Default.DEFAULT_RESOLVER;
  }

  /**
   * Returns a new converter from a conversion function typed as {@code type}.
   *
   * @param converter a conversion function
   * @param type the type of the parameter of the conversion function.
   * @return a new converter from a conversion function typed as {@code type}.
   * @param <T> type of the conversion function parameter.
   */
  static <T> Function<Object, ?> converter(Function<? super T, ?> converter, Class<? extends T> type) {
    requireNonNull(converter, "converter is null");
    requireNonNull(type, "type is null");
    return converter.compose(type::cast);
  }

  /**
   * Returns a new converter from a conversion function typed as String.
   * <p>
   * This implementation is equivalent to
   * <pre>
   *   converter(converter, String.class)
   * </pre>
   *
   * @param converter a conversion function.
   * @return a new converter from a conversion function typed as String..
   */
  static Function<Object, ?> stringConverter(Function<? super String, ?> converter) {
    requireNonNull(converter, "converter is null");
    return converter(converter, String.class);
  }

  private static Function<Object, ?> converter(MethodHandle mh) {
    Objects.requireNonNull(mh, "mh is null");
    return arg -> {
      try {
        return mh.invoke(arg);
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Throwable e) {
        throw new UndeclaredThrowableException(e);
      }
    };
  }

  private static Optional<Function<Object, ?>> unwrap(Lookup lookup, Type valueType, ConverterResolver resolver) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(valueType, "valueType is null");
    requireNonNull(resolver, "resolver is null");
    if (valueType instanceof ParameterizedType parameterizedType) {
      var raw = (Class<?>) parameterizedType.getRawType();
      if (raw == Optional.class) {
        var actualTypeArgument = parameterizedType.getActualTypeArguments()[0];
        return resolver.resolve(lookup, actualTypeArgument).map(f -> arg -> ((Optional<?>) arg).map(f));
      }
      if (raw == List.class) {
        var actualTypeArgument = parameterizedType.getActualTypeArguments()[0];
        return resolver.resolve(lookup, actualTypeArgument).map(f -> arg -> ((List<?>) arg).stream().map(f).toList());
      }
    }
    if (valueType instanceof Class<?> clazz && Object[].class.isAssignableFrom(clazz)) {
      var componentType = clazz.getComponentType();
      return resolver.resolve(lookup, componentType)
          .map(f -> arg ->
              Arrays.stream(((Object[]) arg))
                  .map(f)
                  .toArray(size -> (Object[]) Array.newInstance(componentType, size)));
    }
    return resolver.resolve(lookup, valueType);
  }

  /**
   * Returns the function identity for the types String, Boolean, boolean or Record.
   *
   * @param lookup an unused lookup
   * @param valueType the type of the return type of the function
   * @return the function identity for the types String, Boolean, boolean or Record.
   */
  static Optional<Function<Object, ?>> basic(Lookup lookup, Type valueType) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(valueType, "valueType is null");
    return Optional.of(valueType)
        .flatMap(type -> {
          if (type instanceof Class<?> clazz) {
            if (clazz == String.class || clazz == Boolean.class || clazz == boolean.class || clazz.isRecord()) {
              return Optional.of(Function.identity());
            }
          }
          return Optional.empty();
        });
  }

  /**
   * Returns the function {@code Enum.valueOf} for any enums.
   *
   * @param lookup an unused lookup
   * @param valueType the type of the return type of the function
   * @return the function {@code Enum.valueOf} for any enums.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  static Optional<Function<Object, ?>> enumerated(Lookup lookup, Type valueType) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(valueType, "valueType is null");
    if (valueType instanceof Class<?> clazz && clazz.isEnum()) {
      return Optional.of(arg -> Enum.valueOf((Class) clazz, (String) arg));
    }
    return Optional.empty();
  }

  /**
   * Returns the function that parses a String to a value type.
   * <p>
   * This implementation tries the static methods (in that order)
   * <pre>
   *   valueType ValueType.valueOf(String)
   *   valueType ValueType.of(String)
   *   valueType ValueType.of(String, String...)
   *   valueType ValueType.parse(String)
   *   valueType ValueType.parse(CharSequence)
   * </pre>
   *
   * @param lookup the lookup used to try to find the functions
   * @param valueType the type of the return type of the function
   * @return the function that parses a String to a value type if available.
   */
  static Optional<Function<Object, ?>> reflected(Lookup lookup, Type valueType) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(valueType, "valueType is null");
    return Optional.of(valueType)
        .flatMap(type -> {
            if (valueType instanceof Class<?> clazz) {
              return valueOfMethod(lookup, clazz).map(ConverterResolver::converter);
            }
            return Optional.empty();
        });
  }

  private static Optional<MethodHandle> valueOfMethod(Lookup lookup, Class<?> type) {
    record Factory (String name, MethodType method) {}
    var factories = List.of(
        new Factory("valueOf", methodType(type, String.class)),
        new Factory("of", methodType(type, String.class)),
        new Factory("of", methodType(type, String.class, String[].class)),
        new Factory("parse", methodType(type, String.class)),
        new Factory("parse", methodType(type, CharSequence.class))
        );
    for (var factory : factories) {
      MethodHandle mh;
      try {
        mh = lookup.findStatic(type, factory.name(), factory.method());
      } catch (NoSuchMethodException | IllegalAccessException e) {
        continue;  // does not exist, try next
      }
      // we only allow X.of(String, String...) with a varargs, not X.of(String, String[])
      if (factory.name().equals("of") &&
          mh.type().parameterType(mh.type().parameterCount() - 1).isArray() &&
          !mh.isVarargsCollector()) {
        continue; // try next
      }
      return Optional.of(mh);
    }
    return Optional.empty();
  }
}
