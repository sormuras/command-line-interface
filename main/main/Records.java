package main;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Uses {@link Record}s to derive a {@link Schema} from the {@link RecordComponent}s
 * as well as container for the result values.
 */
record Records() {

    public static <T extends Record> Schema<T> toSchema(Lookup lookup, Class<T> schema) {
        if (schema == null) return null;
        return new Schema<>(Stream.of(schema.getRecordComponents()).map(comp -> toOption(lookup, comp)).toList(),
                components -> createRecord(lookup, schema, components )
        );
    }

    private static Option toOption(Lookup lookup, RecordComponent component) {
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
        return new Option(type, names, help, toSchema(lookup, nestedSchema));
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

    private static <T extends Record> T createRecord(Lookup lookup, Class<T> schema, Collection<Object> values) {
        try {
            return schema.cast(constructor(lookup, schema).asFixedArity().invokeWithArguments(values.toArray()));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
    }
}
