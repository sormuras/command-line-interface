package main;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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
 *       ConverterResolver.of(ConverterResolver::base)
 *           .or(ConverterResolver::reflected)
 *           .unwrap();
 * </pre>
 *
 * <code>ConverterResolver::base</code> is a resolver that associate the identity for the base
 * types, String, boolean, Boolean and records.
 * <p>
 * <code>ConverterResolver::reflected</code> is a resolver
 * that tries to find a static method factory to convert a type from String inside the type.
 * <p>
 * <code>or(resolver)</code> executes the first resolver and if there is no converter found,
 * executes the second converter.
 * <p>
 * <code>unwrap()</code> provides a resolver that unwrap Optional, List and array and
 * executes the resolver on the component type.
 */
@FunctionalInterface
public interface ConverterResolver {
  Optional<Function<Object, ?>> converter(Lookup lookup, Type valueType);

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

  static ConverterResolver defaultResolver() {
    final class Default {
      private static final ConverterResolver DEFAULT_RESOLVER =
          of(ConverterResolver::base)
              .or(ConverterResolver::reflected)
              .unwrap();
    }
    return Default.DEFAULT_RESOLVER;
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

  static Optional<Function<Object, ?>> base(Lookup lookup, Type valueType) {
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

  static Optional<Function<Object, ?>> reflected(Lookup lookup, Type valueType) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(valueType, "valueType is null");
    return Optional.of(valueType)
        .flatMap(type -> {
          if (type instanceof Class<?> clazz) {
            return valueOfMethod(lookup, clazz).map(mh -> arg -> {
              try {
                return clazz.cast(mh.invoke(arg));
              } catch (Throwable e) {
                return Optional.empty();
              }
            });
          }
          return Optional.empty();
      });
  }

  private static Optional<MethodHandle> valueOfMethod(Lookup lookup, Class<?> type) {
    record Factory (String name, MethodType method) {}
    var factories = List.of(
        new Factory("valueOf", MethodType.methodType(type, String.class)),
        new Factory("of", MethodType.methodType(type, String.class)),
        new Factory("of", MethodType.methodType(type, String.class, String[].class)),
        new Factory("parse", MethodType.methodType(type, String.class)),
        new Factory("parse", MethodType.methodType(type, CharSequence.class))
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
