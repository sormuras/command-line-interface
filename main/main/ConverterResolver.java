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
 * Find a converter that converts a String/boolean/record to a Java generic type.
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
 */
@FunctionalInterface
public interface ConverterResolver {
  interface TypeReference<R> {
    private Type extract() {
      var genericInterfaces = getClass().getGenericInterfaces();
      Type[] typeArguments;
      if (genericInterfaces.length == 1 &&
          genericInterfaces[0] instanceof  ParameterizedType parameterizedType &&
          (typeArguments = parameterizedType.getActualTypeArguments()).length == 1) {
        return typeArguments[0];
      }
      throw new IllegalStateException("the TypeReference is not created correctly " + Arrays.toString(genericInterfaces));
    }
  }

  Optional<Function<Object, ?>> converter(Lookup lookup, Type valueType);

  @SuppressWarnings("unchecked")
  default <R> Optional<Function<Object, R>> converter(Lookup lookup, TypeReference<R> typeReference) {
    Objects.requireNonNull(lookup, "lookup is null");
    Objects.requireNonNull(typeReference, "typeReference is null");
    return (Optional<Function<Object, R>>) (Optional<? extends Function<?,?>>) converter(lookup, typeReference.extract());
  }

  default ConverterResolver or(ConverterResolver resolver) {
    requireNonNull(resolver, "resolver is null");
    return (lookup, valueType) -> converter(lookup, valueType)
        .or(() -> resolver.converter(lookup, valueType));
  }

  default ConverterResolver unwrap() {
    return (lookup, valueType) -> unwrap(lookup, valueType, this);
  }

  static ConverterResolver of(ConverterResolver resolver) {
    requireNonNull(resolver, "resolver is null");
    return resolver;
  }

  static ConverterResolver when(Predicate<? super Class<?>> predicate, Function<Object, ?> converter) {
    requireNonNull(predicate, "predicate is null");
    requireNonNull(converter, "converter is null");
    return (lookup, valueType) -> Optional.<Function<Object, ?>>of(converter)
        .filter(__ -> valueType instanceof Class<?> clazz && predicate.test(clazz));
  }

  static ConverterResolver when(Class<?> valueType, Function<Object, ?> converter) {
    requireNonNull(valueType, "valueType is null");
    requireNonNull(converter, "converter is null");
    return when(valueType::equals, converter);
  }

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

  static <T> Function<Object, ?> converter(Function<? super T, ?> converter, Class<? extends T> type) {
    requireNonNull(converter, "converter is null");
    requireNonNull(converter, "type is null");
    return converter.compose(type::cast);
  }

  static Function<Object, ?> stringConverter(Function<? super String, ?> converter) {
    requireNonNull(converter, "converter is null");
    return converter(converter, String.class);
  }

  static Function<Object, ?> converter(MethodHandle mh) {
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
        return resolver.converter(lookup, actualTypeArgument).map(f -> arg -> ((Optional<?>) arg).map(f));
      }
      if (raw == List.class) {
        var actualTypeArgument = parameterizedType.getActualTypeArguments()[0];
        return resolver.converter(lookup, actualTypeArgument).map(f -> arg -> ((List<?>) arg).stream().map(f).toList());
      }
    }
    if (valueType instanceof Class<?> clazz && Object[].class.isAssignableFrom(clazz)) {
      var componentType = clazz.getComponentType();
      return resolver.converter(lookup, componentType)
          .map(f -> arg ->
              Arrays.stream(((Object[]) arg))
                  .map(f)
                  .toArray(size -> (Object[]) Array.newInstance(componentType, size)));
    }
    return resolver.converter(lookup, valueType);
  }

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

  @SuppressWarnings({"rawtypes", "unchecked"})
  static Optional<Function<Object, ?>> enumerated(Lookup lookup, Type valueType) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(valueType, "valueType is null");
    if (valueType instanceof Class<?> clazz && clazz.isEnum()) {
      return Optional.of(arg -> Enum.valueOf((Class) clazz, (String) arg));
    }
    return Optional.empty();
  }

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
