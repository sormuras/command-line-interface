package main;

import main.Command.Builder;

import static java.lang.String.format;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Uses {@link Record}s to derive a {@link Builder} from the {@link RecordComponent}s as well as
 * container for the result values.
 */
class RecordSchemaSupport {
  private RecordSchemaSupport() {
    throw new AssertionError();
  }

  static <T extends Record> Builder<T> toCommand(Lookup lookup, Class<T> commandType) {
    RecordComponent[] components = commandType.getRecordComponents();
    Object[] values = new Object[components.length];
    Builder<T> cmd = Command.of(() -> createRecord(commandType, values, lookup));
    for (int i = 0; i < components.length; i++) {
      int index = i;
      cmd = addOption(lookup, cmd, components[i], value -> values[index] = value);
    }
    return cmd;
  }

  private static <T> Builder<T> addOption(
          Lookup lookup, Builder<T> cmd, RecordComponent component, Consumer<Object> target) {
    var nameAnno = component.getAnnotation(Name.class);
    // TODO make name of positionals dependent; when component name starts with _ it gets a name,
    // otherwise not
    var names =
        nameAnno != null ? nameAnno.value() : new String[] {component.getName().replace('_', '-')};
    var type = OptionType.of(component.getType());
    var nestedSchema = toNestedSchema(component);
    var valueType = valueTypeFrom(component);
    return addOption(lookup, cmd, type, names, valueType, target, nestedSchema);
  }

  private static <T, V> Builder<T> addOption(
      Lookup lookup,
      Builder<T> cmd,
      OptionType type,
      String[] names,
      Class<V> valueType,
      Consumer<Object> target,
      Class<? extends Record> subCommandType) {
    var valueConverter = valueConverter(lookup, valueType);
    Builder<V> subCommand =
        subCommandType == null ? null : (Builder<V>) toCommand(lookup, subCommandType);
    return switch (type) {
      case BRANCH -> cmd.addBranch(subCommand, valueType, target::accept, names);
      case FLAG -> cmd.addFlag(target::accept, names);
      case SINGLE -> cmd.addSingle(subCommand, valueType, valueConverter, target::accept, names);
      case REPEATABLE -> cmd.addRepeatable(
          subCommand, valueType, valueConverter, target::accept, names);
      case REQUIRED -> cmd.addRequired(valueType, valueConverter, target::accept, names);
      case VARARGS -> cmd.addVarargs(valueType, valueConverter, target::accept, names);
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

  private static <T> Function<String, T> valueConverter(Lookup lookup, Class<T> valueType) {
    if (valueType == String.class || valueType.isRecord()) return (Function) Function.identity();
    MethodHandle mh = valueOfMethod(lookup, valueType);
    return arg -> {
      try {
        return valueType.cast(mh.invoke(arg));
      } catch (Throwable e) {
        throw new IllegalArgumentException(
            format("Not a valid value for type %s: %s", valueType.getSimpleName(), arg), e);
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
