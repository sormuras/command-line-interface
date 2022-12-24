package main;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
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
class ReflectSupport {
  private ReflectSupport() {
    throw new AssertionError();
  }

  static <T> Command.Factory<T> factory(Lookup lookup, Class<T> schema) {
    return schema.isRecord() ? recordFactory(lookup, schema) : proxyFactory(lookup, schema);
  }

  /*
  Using Java Records as target types
   */

  private static <T> Command.Factory<T> recordFactory(Lookup lookup, Class<T> schema) {
    requireNonNull(schema, "schema is null");
    requireNonNull(lookup, "lookup is null");
    if (!schema.isRecord()) throw new IllegalArgumentException("schema must be a record");

    RecordComponent[] components = schema.getRecordComponents();
    Command.Builder<Object[], T> cmd =
        Command.builder(
            () -> new Object[components.length], values -> createRecord(schema, values, lookup));
    for (int i = 0; i < components.length; i++) {
      int index = i;
      RecordComponent cmp = components[index];
      BiConsumer<Object[], Object> to = (values, value) -> values[index] = value;
      cmd = addOption(lookup, cmd, cmp, cmp.getName(), cmp.getType(), cmp.getGenericType(), to);
    }
    return cmd.build();
  }

  /*
  Using Java Proxies as target types
   */

  private static <T> Command.Factory<T> proxyFactory(Lookup lookup, Class<T> schema) {
    requireNonNull(schema, "schema is null");
    requireNonNull(lookup, "lookup is null");
    if (!schema.isInterface()) throw new IllegalArgumentException("schema must be an interface");

    Method[] methods = schema.getMethods();
    Builder<Object[], T> cmd =
        Command.builder(
            () -> new Object[methods.length], values -> newProxy(schema, List.of(methods), values));
    for (int i = 0; i < methods.length; i++) {
      Method m = methods[i];
      if (!Modifier.isStatic(m.getModifiers())) {
        int index = i;
        BiConsumer<Object[], Object> to = (values, value) -> values[index] = value;
        cmd =
            addOption(lookup, cmd, m, m.getName(), m.getReturnType(), m.getGenericReturnType(), to);
      }
    }
    return cmd.build();
  }

  @SuppressWarnings("unchecked")
  private static <T> T newProxy(Class<T> of, List<Method> methods, Object[] values) {
    return (T)
        Proxy.newProxyInstance(
            of.getClassLoader(),
            new Class[] {of},
            (proxy, method, args) -> values[methods.indexOf(method)]);
  }

  /*
  Shared methods...
   */

  private static <T> Builder<Object[], T> addOption(
      Lookup lookup,
      Builder<Object[], T> builder,
      AnnotatedElement component,
      String name,
      Class<?> type,
      Type genericType,
      BiConsumer<Object[], Object> to) {
    var nameAnno = component.getAnnotation(Name.class);
    // TODO make name of positionals dependent; when component name starts with _ it gets a name,
    // otherwise not
    var names = nameAnno != null ? nameAnno.value() : new String[] {name.replace('_', '-')};
    var optionType = OptionType.of(type);
    var subCommandType = toSubCommandType(type, genericType);
    var valueType = valueTypeFrom(type, genericType);
    Command.Factory<?> subCommand = subCommandType == null ? null : factory(lookup, subCommandType);
    return addOption(
        lookup, builder, optionType, names, valueType, to, (Command.Factory) subCommand);
  }

  private static <T, V> Builder<Object[], T> addOption(
      Lookup lookup,
      Builder<Object[], T> builder,
      OptionType type,
      String[] names,
      Class<V> of,
      BiConsumer<Object[], Object> to,
      Command.Factory<V> subCommand) {
    var valueConverter = valueConverter(lookup, of);
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

  private static Class<?> valueTypeFrom(Class<?> type, Type genericType) {
    if (type.isRecord()) return type;
    if (type == Boolean.class || type == boolean.class) return Boolean.class;
    if (type.isArray()) return type.getComponentType();
    if (type == List.class || type == Optional.class) {
      return (Class<?>) ((ParameterizedType) genericType).getActualTypeArguments()[0];
    }
    return type;
  }

  private static Class<? extends Record> toSubCommandType(Class<?> type, Type genericType) {
    if (type.isRecord()) return type.asSubclass(Record.class);
    return (genericType instanceof ParameterizedType paramType
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

  private static <T> T createRecord(Class<T> schema, Object[] values, Lookup lookup) {
    try {
      return schema.cast(constructor(lookup, schema).asFixedArity().invokeWithArguments(values));
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new UndeclaredThrowableException(e);
    }
  }
}
