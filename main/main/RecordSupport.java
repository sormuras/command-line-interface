package main;

import static java.lang.String.format;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import main.Command.Builder;

/**
 * Uses {@link Record}s to derive a {@link Builder} from the {@link RecordComponent}s as well as
 * container for the result values.
 */
class RecordSupport {
  private RecordSupport() {
    throw new AssertionError();
  }

  static <T extends Record> Command.Factory<T> factory(Lookup lookup, Class<T> of) {
    RecordComponent[] components = of.getRecordComponents();
    Command.Builder<Object[], T> cmd =
        Command.builder(
            () -> new Object[components.length], values -> createRecord(of, values, lookup));
    for (int i = 0; i < components.length; i++) {
      int index = i;
      cmd = addOption(lookup, cmd, components[index], (values, value) -> values[index] = value);
    }
    return cmd.build();
  }

  private static <T> Builder<Object[], T> addOption(
      Lookup lookup,
      Builder<Object[], T> builder,
      RecordComponent component,
      BiConsumer<Object[], Object> to) {
    var nameAnno = component.getAnnotation(Name.class);
    // TODO make name of positionals dependent; when component name starts with _ it gets a name,
    // otherwise not
    var names =
        nameAnno != null ? nameAnno.value() : new String[] {component.getName().replace('_', '-')};
    var type = OptionType.of(component.getType());
    var nestedSchema = toNestedSchema(component);
    var valueType = valueTypeFrom(component);
    return addOption(lookup, builder, type, names, valueType, to, nestedSchema);
  }

  private static <T, V> Builder<Object[], T> addOption(
      Lookup lookup,
      Builder<Object[], T> builder,
      OptionType type,
      String[] names,
      Class<V> of,
      BiConsumer<Object[], Object> to,
      Class<? extends Record> subCommandType) {
    var valueConverter = valueConverter(lookup, of);
    Command.Factory<V> subCommand =
        subCommandType == null ? null : (Command.Factory<V>) factory(lookup, subCommandType);
    return switch (type) {
      case SUB -> builder.addSub(of, to::accept, subCommand, names);
      case FLAG -> builder.addFlag(to::accept, names);
      case OPTIONAL -> subCommand == null
          ? builder.addOptional(of, valueConverter, to::accept, names)
          : builder.addOptional(of, to::accept, subCommand, names);
      case REPEATABLE -> subCommand == null
          ? builder.addRepeatable(of, valueConverter, to::accept, names)
          : builder.addRepeatable(of, to::accept, subCommand, names);
      case REQUIRED -> builder.addRequired(of, valueConverter, to::accept, names);
      case VARARGS -> builder.addVarargs(of, valueConverter, to::accept, names);
    };
  }

  private static Class<?> valueTypeFrom(RecordComponent component) {
    Class<?> type = component.getType();
    if (type.isRecord()) return type;
    if (type == Boolean.class || type == boolean.class) return Boolean.class;
    if (type.isArray()) return type.getComponentType();
    if (type == List.class || type == Optional.class)
      return (Class<?>)
          ((ParameterizedType) component.getGenericType()).getActualTypeArguments()[0];
    return type;
  }

  private static Class<? extends Record> toNestedSchema(RecordComponent component) {
    if (component.getType().isRecord()) return component.getType().asSubclass(Record.class);
    return (component.getGenericType() instanceof ParameterizedType paramType
            && paramType.getActualTypeArguments()[0] instanceof Class<?> nestedType
            && nestedType.isRecord())
        ? nestedType.asSubclass(Record.class)
        : null;
  }

  private static <T> Function<String, T> valueConverter(Lookup lookup, Class<T> of) {
    if (of == String.class || of.isRecord()) return (Function) Function.identity();
    MethodHandle mh = valueOfMethod(lookup, of);
    return arg -> {
      try {
        return of.cast(mh.invoke(arg));
      } catch (Throwable e) {
        throw new IllegalArgumentException(
            format("Not a valid value for type %s: %s", of.getSimpleName(), arg), e);
      }
    };
  }

  private static <T> MethodHandle valueOfMethod(Lookup lookup, Class<T> valueType) {
    record Factory(String name, MethodType method) {}
    List<Factory> factories =
        List.of( //
            new Factory("valueOf", MethodType.methodType(valueType, String.class)), //
            new Factory("of", MethodType.methodType(valueType, String.class)), //
            new Factory("of", MethodType.methodType(valueType, String.class, String[].class)), //
            new Factory("parse", MethodType.methodType(valueType, String.class)),
            new Factory("parse", MethodType.methodType(valueType, CharSequence.class)));
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
      Class<T> schema, Object[] values, Lookup lookup) {
    try {
      return schema.cast(constructor(lookup, schema).asFixedArity().invokeWithArguments(values));
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new UndeclaredThrowableException(e);
    }
  }
}
