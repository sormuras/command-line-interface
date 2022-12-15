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
import java.util.stream.Stream;

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

  private static Option toOption(Lookup lookup, RecordComponent component) {
    var nameAnno = component.getAnnotation(Name.class);
    var helpAnno = component.getAnnotation(Help.class);
    var names =
        nameAnno != null
            ? new LinkedHashSet<>(Arrays.asList(nameAnno.value()))
            : Set.of(component.getName().replace('_', '-'));
    var type = optionTypeFrom(component.getType());
    var help = helpAnno != null ? String.join("\n", helpAnno.value()) : "";
    var nestedSchema = toNestedSchema(component);
    return new Option(type, names, help, nestedSchema == null ? null: toSchema(lookup, nestedSchema));
  }

  private static Type optionTypeFrom(Class<?> type) {
    if (type == Boolean.class || type == boolean.class) return FLAG;
    if (type == Optional.class) return SINGLE;
    if (type == List.class) return REPEATABLE;
    if (type == String.class) return REQUIRED;
    if (type == String[].class) return VARARGS;
    throw new IllegalArgumentException("Unsupported value type: " + type);
  }

  private static Class<? extends Record> toNestedSchema(RecordComponent component) {
    return (component.getGenericType() instanceof ParameterizedType paramType
            && paramType.getActualTypeArguments()[0] instanceof Class<?> nestedType
            && nestedType.isRecord())
        ? nestedType.asSubclass(Record.class)
        : null;
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
