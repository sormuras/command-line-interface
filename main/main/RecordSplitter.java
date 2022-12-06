package main;

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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Uses {@link Record}s to derive a {@link Schema} from the {@link RecordComponent}s
 * as well as container for the result values.
 */
public record RecordSplitter<R extends Record>(Lookup lookup, Class<R> schema, ArgumentsSplitter<Object[]> splitter) implements ArgumentsSplitter<R> {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.RECORD_COMPONENT)
    public @interface Name {
        String[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.RECORD_COMPONENT)
    public @interface Help {
        String[] value();
    }

    public static <R extends Record> ArgumentsSplitter<R> of(Lookup lookup, Class<R> schema) {
        return new RecordSplitter<>(lookup, schema, args -> ArgumentsSplitter.split(toSchema(schema), args));
    }

    @Override
    public R split(Stream<String> args) {
        requireNonNull(args, "args is null");
        var arguments = splitter.split(args);
        return createRecord(lookup, schema, arguments);
    }
    
    private static Schema toSchema(Class<? extends Record> schema) {
        if (schema == null) return null;
        var recordComponents = schema.getRecordComponents();
        if (recordComponents == null)
            throw new IllegalArgumentException("the schema is not a record");
        return new Schema(Arrays.stream(recordComponents).map(RecordSplitter::toOption));
    }

    private static Option toOption(RecordComponent component) {
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
        return new Option(type, names, help, toSchema(nestedSchema));
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

    private static <T extends Record> T createRecord(Lookup lookup, Class<T> schema, Object[] args) {
        RecordComponent[] components = schema.getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            Class<? extends Record> nestedSchema = toNestedSchema(component);
            if (nestedSchema != null) {
                args[i] = repack(args[i], rec -> createRecord(lookup, nestedSchema, rec) );
            }
        }
        try {
            return schema.cast(constructor(lookup, schema).asFixedArity().invokeWithArguments(args));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    private static Object repack(Object value, Function<Object[], Record> toRecord) {
        Function<Object, Record> c = e -> toRecord.apply((Object[])e);
        if (value instanceof List<?> list)
            return list.stream().map(c).toList();
        if (value instanceof Optional<?> optional)
            return optional.map(c);
        return c.apply(value);
    }
}
