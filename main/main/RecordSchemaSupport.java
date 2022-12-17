package main;

import main.Option.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.String.format;
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
            ? new LinkedHashSet<>(Arrays.asList(nameAnno.value()))
            : Set.of(component.getName().replace('_', '-'));
    var type = optionTypeFrom(component.getType());
    var help = helpAnno != null ? String.join("\n", helpAnno.value()) : "";
    var nestedSchema = toNestedSchema(component);
    var valueType = valueTypeFrom(component);
    return toOption(lookup, type, names, valueType, help, nestedSchema);
  }

  private static <T> Option<T> toOption(Lookup lookup, Type type, Set<String> names, Class<T> valueType, String help, Class<? extends Record> nestedSchema) {
    var valueConverter = valueConverter(lookup, valueType);
    var optionSchema = nestedSchema == null ? null: toSchema(lookup, nestedSchema);
    return new Option<>(type, names, valueType, valueConverter, help, optionSchema);
  }

  private static Type optionTypeFrom(Class<?> type) {
    if (type.isRecord()) return BRANCH;
    if (type == Boolean.class || type == boolean.class) return FLAG;
    if (type == Optional.class) return SINGLE;
    if (type == List.class) return REPEATABLE;
    if (type.isArray()) return VARARGS;
    return REQUIRED;
  }

  private static Class<?> valueTypeFrom(RecordComponent component) {
    Class<?> type = component.getType();
    if (type.isRecord()) return type;
    if (type == Boolean.class || type == boolean.class) return Boolean.class;
    if (type.isArray()) return type.getComponentType();
    if (type == List.class || type == Optional.class)
      return (Class<?>) ((ParameterizedType) component.getGenericType()).getActualTypeArguments()[0];
    return type;
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

  private static <T> Function<String, T> valueConverter(Lookup lookup, Class<T> valueType) {
    if (valueType == String.class || valueType.isRecord())
      return (Function) Function.identity();
    MethodHandle mh = valueOfMethod(lookup, valueType);
    return arg -> {
      try {
        return valueType.cast(mh.invoke(arg));
      } catch (Throwable e) {
        throw new IllegalArgumentException(format("Not a valid value for type %s: %s", valueType.getSimpleName(), arg), e);
      }
    };
  }

  private static <T> MethodHandle valueOfMethod(Lookup lookup, Class<T> valueType) {
    record Factory (String name, MethodType method) {};
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
        // try next
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
