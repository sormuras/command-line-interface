package main;

import main.Option.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static main.Option.Type.BRANCH;
import static main.Option.Type.FLAG;
import static main.Option.Type.REPEATABLE;
import static main.Option.Type.REQUIRED;
import static main.Option.Type.SINGLE;
import static main.Option.Type.VARARGS;

/**
 * Uses {@link Record}s to derive a {@link Schema} from the {@link RecordComponent}s as well as
 * container for the result values.
 */
class RecordSchemaSupport {
  private RecordSchemaSupport() {
    throw new AssertionError();
  }

  static <T extends Record> Schema<T> toSchema(Lookup lookup, Class<T> schema) {
    return new Schema<>(
        Stream.of(schema.getRecordComponents()).map(comp -> toOption(lookup, comp)).toList(),
        values -> createRecord(schema, values, lookup));
  }

  private static Option<?> toOption(Lookup lookup, RecordComponent component) {
    var nameAnno = component.getAnnotation(Name.class);
    var helpAnno = component.getAnnotation(Help.class);
    var names =
        nameAnno != null
            ? nameAnno.value()
            : new String[] { component.getName().replace('_', '-') };
    var type = optionTypeFrom(component.getType());
    var help = helpAnno != null ? String.join("\n", helpAnno.value()) : "";
    var nestedSchema = toNestedSchema(component);
    var valueConverter = valueConverter(lookup, component.getGenericType());
    var optionSchema = nestedSchema == null ? null: toSchema(lookup, nestedSchema);
    return new Option<>(type, names, valueConverter, help, optionSchema);
  }

  private static Type optionTypeFrom(Class<?> type) {
    if (type.isRecord()) return BRANCH;
    if (type == Boolean.class || type == boolean.class) return FLAG;
    if (type == Optional.class) return SINGLE;
    if (type == List.class) return REPEATABLE;
    if (type.isArray()) return VARARGS;
    return REQUIRED;
  }

  private static Class<? extends Record> toNestedSchema(RecordComponent component) {
    if (component.getType().isRecord())
      return component.getType().asSubclass(Record.class);
    return (component.getGenericType() instanceof ParameterizedType paramType
            && paramType.getActualTypeArguments()[0] instanceof Class<?> nestedType
            && nestedType.isRecord())
        ? nestedType.asSubclass(Record.class)
        : null;
  }

  private static Function<Object, ?> valueConverter(Lookup lookup, java.lang.reflect.Type valueType) {
    if (valueType instanceof ParameterizedType parameterizedType) {
      var raw = (Class<?>) parameterizedType.getRawType();
      if (raw == Optional.class) {
        var f = valueConverter(lookup, parameterizedType.getActualTypeArguments()[0]);
        return arg -> ((Optional<?>) arg).map(f);
      }
      if (raw == List.class) {
        var f = valueConverter(lookup, parameterizedType.getActualTypeArguments()[0]);
        return arg -> ((List<?>) arg).stream().map(f).toList();
      }
    } else if (valueType instanceof Class<?> clazz) {
      if (Object[].class.isAssignableFrom(clazz)) {
        var componentType = clazz.getComponentType();
        var f = valueConverter(lookup, componentType);
        return arg -> Arrays.stream(((Object[]) arg)).map(f).toArray(size -> (Object[]) Array.newInstance(componentType, size));
      }
      if (clazz == String.class || clazz == Boolean.class || clazz == boolean.class || clazz.isRecord()) {
        return Function.identity();
      }
      MethodHandle mh = valueOfMethod(lookup, clazz);
      return arg -> {
        try {
          return clazz.cast(mh.invoke(arg));
        } catch (Throwable e) {
          throw new IllegalArgumentException(
              "Not a valid value for type " + clazz.getName() + ": " + arg + " (" + arg.getClass().getName() + ")", e);
        }
      };
    }
    throw new UnsupportedOperationException("unsupported value type " + valueType);
  }

  private static <T> MethodHandle valueOfMethod(Lookup lookup, Class<T> valueType) {
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

  private static MethodHandle constructor(Lookup lookup, Class<?> schema) {
    var components = schema.getRecordComponents();
    var types = Stream.of(components).map(RecordComponent::getType).toArray(Class[]::new);
    try {
      return lookup.findConstructor(schema, MethodType.methodType(void.class, types));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  private static <T extends Record> T createRecord(
          Class<T> schema, List<Object> values, Lookup lookup) {
    try {
      return schema.cast(
          constructor(lookup, schema).asFixedArity().invokeWithArguments(values.toArray()));
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new UndeclaredThrowableException(e);
    }
  }
}
