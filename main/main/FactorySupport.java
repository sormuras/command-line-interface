package main;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
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
import java.util.function.Predicate;
import java.util.stream.Stream;

class FactorySupport {
  private FactorySupport() {
    throw new AssertionError();
  }

  static <T> CommandLine.Factory<T> factory(Lookup lookup, Class<T> schema) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(schema, "schema is null");

    if (schema.isRecord()) return recordFactory(lookup, schema);
    if (schema.isInterface()) return proxyFactory(lookup, schema);
    return pojoFactory(lookup, schema);
  }

  /*
  Using Java POJOs as target types (requires getter/setters)
   */

  private static <T> CommandLine.Factory<T> pojoFactory(Lookup lookup, Class<T> schema) {
    Predicate<Field> filter = m -> !m.isSynthetic() && !Modifier.isStatic(m.getModifiers());
    // FIXME fields are not order as declared
    List<Field> properties = Stream.of(schema.getDeclaredFields()).filter(filter).toList();
    CommandLine.Builder<Object[], T> cmd =
        CommandLine.builder(
            () -> new Object[properties.size()],
            values -> newPojo(lookup, schema, properties, values));
    for (int i = 0; i < properties.size(); i++) {
      int index = i;
      Field f = properties.get(index);
      BiConsumer<Object[], Object> to = (values, value) -> values[index] = value;
      cmd = addOption(lookup, cmd, f, f.getName(), f.getType(), f.getGenericType(), to);
    }
    return cmd.build();
  }

  private static <T> T newPojo(
      Lookup lookup, Class<T> schema, List<Field> properties, Object[] values) {
    {
      try {
        MethodHandle noArgsConstructor = lookup.unreflectConstructor(schema.getConstructor());
        T target = schema.cast(noArgsConstructor.invoke());
        for (int i = 0; i < properties.size(); i++) {
          MethodHandle setter = lookup.unreflectSetter(properties.get(i));
          setter.invoke(target, values[i]);
        }
        return target;
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }
  }

  /*
  Using Java Records as target types
   */

  private static <T> CommandLine.Factory<T> recordFactory(Lookup lookup, Class<T> schema) {
    RecordComponent[] components = schema.getRecordComponents();
    CommandLine.Builder<Object[], T> cmd =
        CommandLine.builder(
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

  private static <T> CommandLine.Factory<T> proxyFactory(Lookup lookup, Class<T> schema) {
    Method[] methods = schema.getMethods();
    // FIXME methods are not order as declared
    CommandLine.Builder<Object[], T> cmd =
        CommandLine.builder(
            () -> new Object[methods.length], values -> newProxy(schema, List.of(methods), values));
    for (int i = 0; i < methods.length; i++) {
      Method m = methods[i];
      if (!m.isSynthetic() && !Modifier.isStatic(m.getModifiers())) {
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

  @SuppressWarnings("unchecked")
  private static <T> CommandLine.Builder<Object[], T> addOption(
      Lookup lookup,
      CommandLine.Builder<Object[], T> builder,
      AnnotatedElement source,
      String name,
      Class<?> type,
      Type genericType,
      BiConsumer<Object[], Object> to) {
    var optionType = OptionType.of(type);

    var handlesSource = source.getAnnotation(Name.class);
    var handles =
        handlesSource != null
            ? handlesSource.value()
            : optionType.isPositional() && !name.startsWith("-")
                ? new String[0]
                : new String[] {name.replace('_', '-')};
    var subCommandType = toSubCommandType(type, genericType);
    var valueType = valueTypeFrom(type, genericType);
    @SuppressWarnings("rawtypes")
    CommandLine.Factory subCommand = subCommandType == null ? null : factory(lookup, subCommandType);
    return addOption(
        lookup, builder, name, optionType, handles, valueType, to, subCommand);
  }

  private static <T, V> CommandLine.Builder<Object[], T> addOption(
      Lookup lookup,
      CommandLine.Builder<Object[], T> builder,
      String name,
      OptionType type,
      String[] names,
      Class<V> of,
      BiConsumer<Object[], Object> to,
      CommandLine.Factory<V> subCommand) {
    var valueConverter = valueConverter(lookup, of);
    return switch (type) {
      case SUB -> builder.addSub(name, of, to::accept, subCommand, names);
      case FLAG -> builder.addFlag(name, to::accept, names);
      case OPTIONAL -> subCommand == null
          ? builder.addOptional(name, of, valueConverter, to::accept, names)
          : builder.addOptional(name, of, to::accept, subCommand, names);
      case REPEATABLE -> subCommand == null
          ? builder.addRepeatable(name, of, valueConverter, to::accept, names)
          : builder.addRepeatable(name, of, to::accept, subCommand, names);
      case REQUIRED -> builder.addRequired(name, of, valueConverter, to::accept, names);
      case VARARGS -> builder.addVarargs(name, of, valueConverter, to::accept, names);
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

  @SuppressWarnings("unchecked")
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

  private static MethodHandle constructor(Lookup lookup, Class<?> schema, Class<?>... types) {
    try {
      return lookup.findConstructor(schema, MethodType.methodType(void.class, types));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  private static <T> T createRecord(Class<T> schema, Object[] values, Lookup lookup) {
    var components = schema.getRecordComponents();
    var types = Stream.of(components).map(RecordComponent::getType).toArray(Class[]::new);
    try {
      return schema.cast(
          constructor(lookup, schema, types).asFixedArity().invokeWithArguments(values));
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new UndeclaredThrowableException(e);
    }
  }
}
