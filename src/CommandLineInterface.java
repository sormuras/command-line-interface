import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Stream;

public interface CommandLineInterface {

  static <R extends Record> R parse(Lookup lookup, Class<R> schema, String... args) {
    return parser(lookup, schema).parse(args);
  }

  static <R extends Record> Parser<R> parser(Lookup lookup, Class<R> schema) {
    return new Parser<>(lookup, schema);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.RECORD_COMPONENT)
  @interface Option {
    String[] value();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.RECORD_COMPONENT)
  @interface Help {
    String[] value();
  }

  record Key(Set<String> names, Value value, String help) implements Comparable<Key> {

    public static Key of(RecordComponent component) {
      return new Key(
          component.isAnnotationPresent(Option.class)
              ? new LinkedHashSet<>(List.of(component.getAnnotation(Option.class).value()))
              : Set.of(component.getName().replace('_', '-')),
          Value.of(component.getType()),
          component.isAnnotationPresent(Help.class)
              ? String.join("\n", component.getAnnotation(Help.class).value())
              : "");
    }

    Key with(Value value) {
      return new Key(names, value, help);
    }

    @Override
    public int compareTo(Key o) {
      return names.toString().compareTo(o.names.toString());
    }
  }

  sealed interface Value {
    static Value of(Class<?> type) {
      if (type.equals(Boolean.class) || type.equals(boolean.class)) return new FlagValue();
      if (type.equals(Optional.class)) return new PairValue();
      if (type.equals(List.class)) return new ListValue();
      if (type.equals(String[].class)) return new MoreValue();
      throw new IllegalArgumentException("Unsupported value type: " + type);
    }

    Object value();

    /** An optional flag, like {@code --verbose}. */
    record FlagValue(Boolean value) implements Value {
      public FlagValue() {
        this(false);
      }
    }

    /** An optional key-value pair, like {@code --version 47.11}. */
    record PairValue(Optional<String> value) implements Value {
      public PairValue() {
        this(Optional.empty());
      }
    }

    /** An optional and repeatable key, like {@code --with alpha --with omega} */
    record ListValue(List<String> value) implements Value {
      public ListValue() {
        this(List.of());
      }
    }

    /** A collection of all unhandled arguments. */
    record MoreValue(String... value) implements Value {}
  }

  record Parser<R extends Record>(Lookup lookup, Class<R> schema, List<Key> options) {

    public Parser(Lookup lookup, Class<R> schema) {
      this(lookup, schema, Stream.of(schema.getRecordComponents()).map(Key::of).toList());
    }

    public Parser {
      var names = options.stream().flatMap(option -> option.names().stream()).toList();
      var duplicates = new ArrayList<>(names);
      Set.copyOf(names).forEach(duplicates::remove);
      if (!duplicates.isEmpty()) {
        throw new IllegalArgumentException("Duplicate option key name(s) detected: " + duplicates);
      }
      var mores =
          options.stream().filter(option -> option.value() instanceof Value.MoreValue).toList();
      if (mores.size() > 1) {
        throw new IllegalArgumentException("Too many MoreOption instances found: " + mores);
      }
      if (mores.size() == 1
          && !(options.get(options.size() - 1).value() instanceof Value.MoreValue)) {
        throw new IllegalArgumentException("MoreOption not at last index: " + options);
      }
    }

    public List<Key> list(String... args) {
      var arguments = new ArrayDeque<>(List.of(args));
      var workspace = new ArrayList<>(options);
      with_next_argument:
      while (!arguments.isEmpty()) {
        var argument = arguments.removeFirst();
        /* parse flags first */ for (int index = 0; index < workspace.size(); index++) {
          var option = workspace.get(index);
          if (!option.names().contains(argument)) continue;
          if (option.value() instanceof Value.FlagValue) {
            workspace.set(index, option.with(new Value.FlagValue(true)));
            continue with_next_argument;
          }
        }
        /* parse key-value pairs */ {
          int separator = argument.indexOf('=');
          var pop = separator == -1;
          var key = pop ? argument : argument.substring(0, separator);
          var val = separator + 1;
          for (int index = 0; index < workspace.size(); index++) {
            var option = workspace.get(index);
            if (!option.names().contains(key)) continue;
            if (option.value() instanceof Value.PairValue) {
              var value = pop ? arguments.pop() : argument.substring(val);
              workspace.set(index, option.with(new Value.PairValue(Optional.of(value))));
              continue with_next_argument;
            }
            if (option.value() instanceof Value.ListValue list) {
              var value = pop ? arguments.pop() : argument.substring(val);
              var elements = Stream.concat(list.value().stream(), Stream.of(value)).toList();
              workspace.set(index, option.with(new Value.ListValue(elements)));
              continue with_next_argument;
            }
          }
          // restore argument because first unhandled option marks the beginning of the variadic
          // rest
          arguments.addFirst(argument);
          break;
        }
      }
      if (arguments.isEmpty()) return List.copyOf(workspace);
      var lastIndex = options.size() - 1;
      var lastOption = options.get(lastIndex);
      if (lastOption.value() instanceof Value.MoreValue) {
        workspace.set(
            lastIndex, lastOption.with(new Value.MoreValue(arguments.toArray(String[]::new))));
        return List.copyOf(workspace);
      }
      throw new UnsupportedOperationException("Unhandled arguments: " + arguments);
    }

    public R parse(String... args) {
      try {
        var components = schema.getRecordComponents();
        var classes = Stream.of(components).map(RecordComponent::getType).toArray(Class[]::new);
        var constructor =
            lookup.findConstructor(schema, MethodType.methodType(void.class, classes));
        var objects = list(args).stream().map(Key::value).map(Value::value).toArray(Object[]::new);
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
        if (text.isEmpty()) continue;
        var suffix = "";
        if (option.value() instanceof Value.FlagValue) suffix = " (flag)";
        if (option.value() instanceof Value.PairValue) suffix = " <value>";
        if (option.value() instanceof Value.ListValue) suffix = " <value> (repeatable)";
        if (option.value() instanceof Value.MoreValue) suffix = "...";
        var names = String.join(", ", option.names());
        joiner.add(names + suffix);
        joiner.add(text.indent(indent).stripTrailing());
      }
      return joiner.toString();
    }
  }
}
