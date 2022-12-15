package main;

import static java.util.Objects.requireNonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Uses {@link Record}s to derive a {@link Schema} from the {@link RecordComponent}s as well as
 * container for the result values.
 */
public record RecordSchemaSupport() {

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.RECORD_COMPONENT)
  public @interface Help {
    String[] value();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.RECORD_COMPONENT)
  public @interface Name {
    String[] value();
  }

  static <T extends Record> Schema<T> toSchema(Class<T> schema, Lookup lookup) {
    if (schema == null) return null;
    return new Schema<>(
        Stream.of(schema.getRecordComponents()).map(comp -> toOption(comp, lookup)).toList(),
        components -> createRecord(schema, components, lookup));
  }

  private static Option toOption(RecordComponent component, Lookup lookup) {
    requireNonNull(component, "component is null");
    var nameAnno = component.getAnnotation(Name.class);
    var helpAnno = component.getAnnotation(Help.class);
    var names =
        nameAnno != null
            ? new LinkedHashSet<>(Arrays.asList(nameAnno.value()))
            : Set.of(component.getName().replace('_', '-'));
    var type = Option.Type.valueOf(component.getType());
    var help = helpAnno != null ? String.join("\n", helpAnno.value()) : "";
    var nestedSchema = toNestedSchema(component);
    return new Option(type, names, help, toSchema(nestedSchema, lookup));
  }

  private static Class<? extends Record> toNestedSchema(RecordComponent component) {
    return (component.getGenericType() instanceof ParameterizedType paramType
            && paramType.getActualTypeArguments()[0] instanceof Class<?> nestedType
            && nestedType.isRecord())
        ? nestedType.asSubclass(Record.class)
        : null;
  }

  private static MethodHandle constructor(Class<?> schema, Lookup lookup) {
    var components = schema.getRecordComponents();
    var types = Stream.of(components).map(RecordComponent::getType).toArray(Class[]::new);
    try {
      return lookup.findConstructor(schema, MethodType.methodType(void.class, types));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  private static <T extends Record> T createRecord(
          Class<T> schema, Collection<Object> values, Lookup lookup) {
    try {
      return schema.cast(
          constructor(schema, lookup).asFixedArity().invokeWithArguments(values.toArray()));
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new UndeclaredThrowableException(e);
    }
  }
}
