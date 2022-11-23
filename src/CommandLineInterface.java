import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Stream;

@SuppressWarnings("rawtypes")
public interface CommandLineInterface {

  static <R extends Record> R parse(Lookup lookup, Class<R> schema, String... args) {
    return parser(lookup, schema).parse(args);
  }

  static <R extends Record> Parser<R> parser(Lookup lookup, Class<R> schema) {
    return new Parser<>(lookup, schema);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.RECORD_COMPONENT)
  @interface Name {
    String[] value();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.RECORD_COMPONENT)
  @interface Help {
    String[] value();
  }

  record Option(Type type, Class<? extends Enum> enumType, Set<String> names, String help) implements Comparable<Option> {

    interface ToEnum {

      static ToEnum identity() {
        return (cls, val) -> val;
      }

      Object toEnum(Class<? extends Enum> type, Object value);
    }

    @SuppressWarnings("unchecked")
    enum Type {
      /** An optional flag, like {@code --verbose}. */
      FLAG(false, ToEnum.identity()),
      /** An optional key-value pair, like {@code --version 47.11}. */
      KEY_VALUE(Optional.empty(), (cls, val) -> ((Optional<String>) val).map(name -> Enum.valueOf(cls, name))),
      /** An optional and repeatable key, like {@code --with alpha --with omega} */
      REPEATABLE(List.of(), (cls, names) -> ((List<String>)names).stream().map(name -> Enum.valueOf(cls, name)).toList()),
      /** A required positional option */
      REQUIRED("", (cls, name) -> Enum.valueOf(cls, (String) name)),
      /** A collection of all unhandled arguments. */
      VARARGS(new String[0], ToEnum.identity());

      private final Object defaultValue;
      private final ToEnum toEnum;

      Type(Object defaultValue, ToEnum toEnum) {
        this.defaultValue = defaultValue;
        this.toEnum = toEnum;
      }

      public Object getDefaultValue() {
        return defaultValue;
      }

      public ToEnum getToEnum() {
        return toEnum;
      }

      static Type valueOf(Class<?> type) {
        if (type == Boolean.class || type == boolean.class) return FLAG;
        if (type == Optional.class) return KEY_VALUE;
        if (type == List.class) return REPEATABLE;
        if (type == String.class || type.isEnum()) return REQUIRED ;
        if (type == String[].class || type.isArray() && type.getComponentType().isEnum()) return VARARGS;
        throw new IllegalArgumentException("Unsupported value type: " + type);
      }
    }

    private static Class<?> getEnumType(java.lang.reflect.Type type) {
      if (type instanceof Class<?> rawType) {
        if (rawType.isEnum()) return rawType;
        if (rawType.isArray() && rawType.getComponentType().isEnum()) return rawType.getComponentType();
      }
      if (type instanceof ParameterizedType genericType) {
        Class<?> rawType = (Class<?>) genericType.getRawType();
        Class<?> elementType = (Class<?>) genericType.getActualTypeArguments()[0];
        if (rawType == List.class && elementType.isEnum()) return elementType;
        if (rawType == Optional.class && elementType.isEnum()) return elementType;
      }
      return null;
    }

    public static Option of(RecordComponent component) {
      return new Option(
              Type.valueOf(component.getType()),
              (Class<? extends Enum>) getEnumType(component.getGenericType()),
              component.isAnnotationPresent(Name.class)
                      ? new LinkedHashSet<>(List.of(component.getAnnotation(Name.class).value()))
                      : Set.of(component.getName().replace('_', '-')),
              component.isAnnotationPresent(Help.class)
                      ? String.join("\n", component.getAnnotation(Help.class).value())
                      : ""
      );
    }

    String name() {
      return names.iterator().next();
    }

    boolean isVarargs() {
      return type == Type.VARARGS;
    }

    boolean isRequired() {
      return type == Type.REQUIRED;
    }

    @Override
    public int compareTo(Option o) {
      return names.toString().compareTo(o.names.toString());
    }
  }



  record Parser<R extends Record>(Lookup lookup, Class<R> schema, List<Option> options) {

    public Parser(Lookup lookup, Class<R> schema) {
      this(lookup, schema, Stream.of(schema.getRecordComponents()).map(Option::of).toList());
    }

    public Parser {
      var names = options.stream().flatMap(option -> option.names().stream()).toList();
      var duplicates = new ArrayList<>(names);
      Set.copyOf(names).forEach(duplicates::remove);
      if (!duplicates.isEmpty()) {
        throw new IllegalArgumentException("Duplicate option key name(s) detected: " + duplicates);
      }
      var mores = options.stream().filter(option -> option.type() == Option.Type.VARARGS).toList();
      if (mores.size() > 1) {
        throw new IllegalArgumentException("Too many MoreOption instances found: " + mores);
      }
      if (mores.size() == 1 && !(options.get(options.size() - 1).isVarargs())) {
        throw new IllegalArgumentException("MoreOption not at last index: " + options);
      }
    }

    public List<Object> list(String... args) {
      var arguments = new ArrayDeque<>(List.of(args));
      var requireds = new ArrayDeque<>(options.stream().filter(Option::isRequired).toList());
      var optionsByName = new HashMap<String, Option>();
      var workspace = new LinkedHashMap<String, Object>();
      options.forEach(option -> option.names.forEach(name -> optionsByName.put(name, option)));
      options.forEach(option -> workspace.put(option.name(), option.type().getDefaultValue()));

      while (true) {
        if (arguments.isEmpty()) {
          if (!requireds.isEmpty()) throw new IllegalArgumentException("Required options missing: " + requireds);
          return List.copyOf(workspace.values());
        }
        var argument = arguments.removeFirst();

        int separator = argument.indexOf('=');
        var pop = separator == -1;
        var maybeName = pop ? argument : argument.substring(0, separator);

        // handle unnamed option types?
        if (!optionsByName.containsKey(maybeName)) {
          if (!requireds.isEmpty()) {
            var requiredOption = requireds.pop();
            workspace.put(requiredOption.name(), argument);
            continue;
          }
          // put back first varargs value
          arguments.addFirst(argument);
          var varargsOption = options.get(options.size() - 1);
          if (varargsOption.isVarargs()) {
            workspace.put(varargsOption.name(), arguments.toArray(String[]::new));
            return List.copyOf(workspace.values());
          }
          throw new IllegalArgumentException("Unhandled arguments: " + arguments);
        }
        var option = optionsByName.get(maybeName);
        var name = option.name();
        switch (option.type()) {
          case FLAG -> workspace.put(name, true);
          case KEY_VALUE -> workspace.put(name, Optional.of(pop ? arguments.pop() : argument.substring(separator + 1)));
          case REPEATABLE -> {
            var element = pop ? arguments.pop() : argument.substring(separator + 1);
            var value = workspace.get(name);
            @SuppressWarnings("unchecked")
            var elements = Stream.concat(((List<String>) value).stream(), Stream.of(element)).toList();
            workspace.put(name, elements);
          }
          default -> throw new IllegalStateException("Programming error");
        }
      }
    }

    public R parse(String... args) {
      try {
        var components = schema.getRecordComponents();
        var classes = Stream.of(components).map(RecordComponent::getType).toArray(Class[]::new);
        var constructor =
            lookup.findConstructor(schema, MethodType.methodType(void.class, classes));
        var objects = list(args).toArray();
        for (int i = 0; i < components.length; i++) {
          Option option = options.get(i);
          if (option.enumType() != null) {
            objects[i] = option.type().getToEnum().toEnum(option.enumType(), objects[i]);
          }
        }
        var arguments = constructor.isVarargsCollector() ? spreadLastArgument(objects) : objects;
        @SuppressWarnings("unchecked")
        R instance = (R) constructor.invokeWithArguments(arguments);
        return instance;
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Throwable e) {
        throw new UndeclaredThrowableException(e);
      }
    }

    private static Object[] spreadLastArgument(Object[] source) {
      var head = source.length - 1;
      var last = source[head];
      var tail = Array.getLength(last);
      var target = new Object[head + tail];
      System.arraycopy(source, 0, target, 0, head);
      //noinspection SuspiciousSystemArraycopy
      System.arraycopy(last, 0, target, head, tail);
      return target;
    }

    public String help() {
      return help(2);
    }

    public String help(int indent) {
      var joiner = new StringJoiner("\n");
      for (var option : options.stream().sorted().toList()) {
        var text = option.help();
        //if (text.isEmpty()) continue;
        var suffix = switch (option.type()) {
          case FLAG -> " (flag)";
          case KEY_VALUE -> " <value>";
          case REPEATABLE -> " <value> (repeatable)";
          case REQUIRED -> " (required)";
          case VARARGS -> "...";
        };
        var names = String.join(", ", option.names());
        joiner.add(names + suffix + (option.enumType() == null ? "" : " "+ List.of(option.enumType().getEnumConstants())));
        joiner.add(text.indent(indent).stripTrailing());
      }
      return joiner.toString();
    }
  }
}
