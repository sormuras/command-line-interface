package main;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Uses {@link Record}s to derive a {@link Schema} from the {@link RecordComponent}s as well as
 * container for the result values.
 */
final class RecordSchemaSupport {
  private RecordSchemaSupport() {
    throw new AssertionError();
  }

  static <T extends Record> Schema<T> toSchema(Lookup lookup, Class<T> schema, ConverterResolver resolver) {
    return new Schema<>(
        Stream.of(schema.getRecordComponents()).map(component -> toOption(lookup, component, resolver)).toList(),
        (Function<? super List<Object>, ? extends T> & Serializable) values -> createRecord(schema, values, lookup));
  }

  private static Option<?> toOption(Lookup lookup, RecordComponent component, ConverterResolver resolver) {
    var nameAnno = getAnnotation(component, Name.class, Name::value);
    var helpAnno = getAnnotation(component, Help.class, Help::value);
    var names =
        nameAnno != null
            ? nameAnno
            : new String[] { component.getName().replace('_', '-') };
    var type = optionTypeFrom(component.getType());
    var help = helpAnno != null ? String.join("\n", helpAnno) : "";
    var nestedSchema = toNestedSchema(component);
    var converter = resolveConverter(lookup, component, resolver);
    var optionSchema = nestedSchema == null ? null: toSchema(lookup, nestedSchema, resolver);
    return AbstractOption.newOption(type, names, converter, help, optionSchema);
  }

  private static <A extends Annotation, V> String[] getAnnotation(RecordComponent component, Class<A> annotationClass, Function<A, String[]> value) {
    var annotations = component.getAnnotations();
    if (annotations.length == 0) {  // fast path
      return null;
    }
    var annotation = component.getAnnotation(annotationClass);
    if (annotation != null) {
      return value.apply(annotation);
    }
    // we look for an annotation named 'Name' or 'Help' but maybe in another package
    return (String[]) Arrays.stream(annotations)
        .filter(ann -> annotationClass.getSimpleName().equals(ann.annotationType().getSimpleName()))
        .mapMulti((ann, consumer) -> {
          try {
            consumer.accept((ann.annotationType().getMethod("value").invoke(ann)));
          } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException  e) {
            // skip the value
          }
        })
        .filter(v -> v instanceof String[])
        .reduce((v1, v2) -> { throw new IllegalStateException("more than one annotation named " + annotationClass.getSimpleName()); })
        .orElse(null);
  }

  private static Converter<Object, ?> resolveConverter(Lookup lookup, RecordComponent component, ConverterResolver resolver) {
    return resolver.resolve(lookup, component.getGenericType())
        .orElseThrow(() -> new UnsupportedOperationException("no converter for component " + component));
  }

  private static OptionType optionTypeFrom(Class<?> type) {
    if (type.isRecord()) return OptionType.BRANCH;
    if (type == Boolean.class || type == boolean.class) return OptionType.FLAG;
    if (type == Optional.class) return OptionType.SINGLE;
    if (type == List.class) return OptionType.REPEATABLE;
    if (type.isArray()) return OptionType.VARARGS;
    return OptionType.REQUIRED;
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
