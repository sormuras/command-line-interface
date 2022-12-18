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

@FunctionalInterface
public interface ValueConverterResolver {
  Optional<Function<Object, ?>> valueConverter(Lookup lookup, Type valueType);

  default ValueConverterResolver or(ValueConverterResolver resolver) {
    requireNonNull(resolver, "resolver is null");
    return (lookup, valueType) -> valueConverter(lookup, valueType)
        .or(() -> resolver.valueConverter(lookup, valueType));
  }

  default ValueConverterResolver unwrap() {
    return (lookup, valueType) -> unwrap(lookup, valueType, this);
  }

  static ValueConverterResolver of(ValueConverterResolver resolver) {
    requireNonNull(resolver, "resolver is null");
    return resolver;
  }

  static ValueConverterResolver defaultValueConverter() {
    final class Default {
      private static final ValueConverterResolver DEFAULT_VALUE_CONVERTER =
          of(ValueConverterResolver::base)
              .or(ValueConverterResolver::reflected)
              .unwrap();
    }
    return Default.DEFAULT_VALUE_CONVERTER;
  }

  private static Optional<Function<Object, ?>> unwrap(Lookup lookup, Type valueType, ValueConverterResolver resolver) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(valueType, "valueType is null");
    requireNonNull(resolver, "resolver is null");
    if (valueType instanceof ParameterizedType parameterizedType) {
      var raw = (Class<?>) parameterizedType.getRawType();
      if (raw == Optional.class) {
        var actualTypeArgument = parameterizedType.getActualTypeArguments()[0];
        return resolver.valueConverter(lookup, actualTypeArgument).map(f -> arg -> ((Optional<?>) arg).map(f));
      }
      if (raw == List.class) {
        var actualTypeArgument = parameterizedType.getActualTypeArguments()[0];
        return resolver.valueConverter(lookup, actualTypeArgument).map(f -> arg -> ((List<?>) arg).stream().map(f).toList());
      }
    }
    if (valueType instanceof Class<?> clazz && Object[].class.isAssignableFrom(clazz)) {
      var componentType = clazz.getComponentType();
      return resolver.valueConverter(lookup, componentType)
          .map(f -> arg ->
              Arrays.stream(((Object[]) arg))
                  .map(f)
                  .toArray(size -> (Object[]) Array.newInstance(componentType, size)));
    }
    return resolver.valueConverter(lookup, valueType);
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
            MethodHandle mh = valueOfMethod(lookup, clazz);
            return Optional.of(arg -> {
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

  private static MethodHandle valueOfMethod(Lookup lookup, Class<?> valueType) {
    record Factory (String name, MethodType method) {}
    List<Factory> factories = List.of( //
        new Factory("valueOf", MethodType.methodType(valueType, String.class)), //
        new Factory("of", MethodType.methodType(valueType, String.class)), //
        new Factory("of", MethodType.methodType(valueType, String.class, String[].class)), //
        new Factory("parse", MethodType.methodType(valueType, String.class)),
        new Factory("parse", MethodType.methodType(valueType, CharSequence.class))
    );
    for (Factory factory : factories) {
      try {
        return lookup.findStatic(valueType, factory.name(), factory.method());
      } catch (NoSuchMethodException | IllegalAccessException e) {
        continue;  // try next
      }
    }
    throw new UnsupportedOperationException("Unsupported conversion from String to " + valueType);
  }
}
