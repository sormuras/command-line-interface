import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public interface CommandLineInterface {

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

  record Option(Type type, Set<String> names, String help) implements Comparable<Option> {
    enum Type {
      /** An optional flag, like {@code --verbose}. */
      FLAG(false),
      /** An optional key-value pair, like {@code --version 47.11}. */
      KEY_VALUE(Optional.empty()),
      /** An optional and repeatable key, like {@code --with alpha --with omega} */
      REPEATABLE(List.of()),
      /** A required positional option */
      REQUIRED(""),
      /** A collection of all unhandled arguments. */
      VARARGS(new String[0]);

      final Object defaultValue;

      Type(Object defaultValue) {
        this.defaultValue = defaultValue;
      }

      Object getDefaultValue() {
        return defaultValue;
      }

      static Type valueOf(Class<?> type) {
        if (type == Boolean.class || type == boolean.class) return FLAG;
        if (type == Optional.class) return KEY_VALUE;
        if (type == List.class) return REPEATABLE;
        if (type == String.class) return REQUIRED;
        if (type == String[].class) return VARARGS;
        throw new IllegalArgumentException("Unsupported value type: " + type);
      }
    }

    public static <R extends Record> List<Option> scan(Class<R> schema) {
      return Stream.of(schema.getRecordComponents()).map(Option::of).toList();
    }

    public static Option of(RecordComponent component) {
      return new Option(
          Type.valueOf(component.getType()),
          component.isAnnotationPresent(Name.class)
              ? new LinkedHashSet<>(List.of(component.getAnnotation(Name.class).value()))
              : Set.of(component.getName().replace('_', '-')),
          component.isAnnotationPresent(Help.class)
              ? String.join("\n", component.getAnnotation(Help.class).value())
              : "");
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

  record Parser<R extends Record>(
      Lookup lookup, Class<R> schema, List<Option> options, ArgumentsProcessor processor) {

    public Parser(Lookup lookup, Class<R> schema) {
      this(lookup, schema, ArgumentsProcessor.DEFAULT);
    }

    public Parser(Lookup lookup, Class<R> schema, ArgumentsProcessor processor) {
      this(lookup, schema, Option.scan(schema), processor);
    }

    public Parser {
      if (options.isEmpty()) throw new IllegalArgumentException("At least one option expected");
      var names = options.stream().flatMap(option -> option.names().stream()).toList();
      var duplicates = new ArrayList<>(names);
      Set.copyOf(names).forEach(duplicates::remove);
      if (!duplicates.isEmpty()) {
        throw new IllegalArgumentException("Duplicate option key name(s) detected: " + duplicates);
      }
      var varargs =
          options.stream().filter(option -> option.type() == Option.Type.VARARGS).toList();
      if (varargs.size() > 1) {
        throw new IllegalArgumentException("Too many varargs types specified: " + varargs);
      }
      if (varargs.size() == 1 && !(options.get(options.size() - 1).isVarargs())) {
        throw new IllegalArgumentException("MoreOption not at last index: " + options);
      }
    }

    private Object[] parse(ArrayDeque<String> pendingArguments) {
      var requiredOptions = new ArrayDeque<>(options.stream().filter(Option::isRequired).toList());
      var optionsByName = new HashMap<String, Option>();
      var workspace = new LinkedHashMap<String, Object>();
      options.forEach(option -> option.names.forEach(name -> optionsByName.put(name, option)));
      options.forEach(option -> workspace.put(option.name(), option.type().getDefaultValue()));

      while (true) {
        if (pendingArguments.isEmpty()) {
          if (requiredOptions.isEmpty()) return workspace.values().toArray();
          throw new IllegalArgumentException("Required option(s) missing: " + requiredOptions);
        }
        // acquire next argument
        var argument = pendingArguments.removeFirst();
        int separator = argument.indexOf('=');
        var pop = separator == -1;
        var maybeName = pop ? argument : argument.substring(0, separator);
        // try well-known option first
        if (optionsByName.containsKey(maybeName)) {
          var option = optionsByName.get(maybeName);
          var name = option.name();
          switch (option.type()) {
            case FLAG -> workspace.put(name, true);
            case KEY_VALUE -> {
              var value = pop ? pendingArguments.pop() : argument.substring(separator + 1);
              workspace.put(name, Optional.of(value));
            }
            case REPEATABLE -> {
              var value = pop ? pendingArguments.pop() : argument.substring(separator + 1);
              @SuppressWarnings("unchecked")
              var elements = (List<String>) workspace.get(name);
              workspace.put(name, Stream.concat(elements.stream(), Stream.of(value)).toList());
            }
            default -> throw new IllegalStateException("Programming error");
          }
          continue;
        }
        // try required option
        if (!requiredOptions.isEmpty()) {
          var requiredOption = requiredOptions.pop();
          workspace.put(requiredOption.name(), argument);
          continue;
        }
        // restore pending arguments deque
        pendingArguments.addFirst(argument);
        // try globbing all pending arguments into a varargs collector
        var varargsOption = options.get(options.size() - 1);
        if (varargsOption.isVarargs()) {
          workspace.put(varargsOption.name(), pendingArguments.toArray(String[]::new));
          return workspace.values().toArray();
        }
        throw new IllegalArgumentException("Unhandled arguments: " + pendingArguments);
      }
    }

    public R parse(String... args) {
      return parse(Stream.of(args));
    }

    public R parse(Stream<String> args) {
      try {
        var components = schema.getRecordComponents();
        var types = Stream.of(components).map(RecordComponent::getType).toArray(Class[]::new);
        var constructor = lookup.findConstructor(schema, MethodType.methodType(void.class, types));
        var objects = parse(new ArrayDeque<>(processor.apply(args).toList()));
        var arguments = constructor.isVarargsCollector() ? spreadVarargs(objects) : objects;
        @SuppressWarnings("unchecked")
        R instance = (R) constructor.invokeWithArguments(arguments);
        return instance;
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Throwable e) {
        throw new UndeclaredThrowableException(e);
      }
    }

    // From [ ..., x, ["1", "2", "3"]] to [..., x, "1", "2", "3"]
    private static Object[] spreadVarargs(Object[] source) {
      var head = source.length - 1;
      var last = (String[]) source[head];
      var tail = Array.getLength(last);
      var target = new Object[head + tail];
      System.arraycopy(source, 0, target, 0, head);
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
        var suffix =
            switch (option.type()) {
              case FLAG -> " (flag)";
              case KEY_VALUE -> " <value>";
              case REPEATABLE -> " <value> (repeatable)";
              case REQUIRED -> " (required)";
              case VARARGS -> "...";
            };
        var names = String.join(", ", option.names());
        joiner.add(names + suffix);
        joiner.add(text.indent(indent).stripTrailing());
      }
      return joiner.toString();
    }
  }

  interface ArgumentsProcessor extends UnaryOperator<Stream<String>> {
    ArgumentsProcessor IDENTITY = arguments -> arguments;
    ArgumentsProcessor TRIM = arguments -> arguments.map(String::trim);
    ArgumentsProcessor PRUNE = arguments -> arguments.filter(argument -> !argument.isEmpty());
    ArgumentsProcessor NORMALIZE = TRIM.andThen(PRUNE);
    ArgumentsProcessor EXPAND = ArgumentsProcessor::expandAtFileArguments;
    ArgumentsProcessor DEFAULT = NORMALIZE.andThen(EXPAND);

    static Stream<String> expandAtFileArguments(Stream<String> source) {
      var arguments = new ArrayList<String>();
      for (var argument : source.toList()) {
        if (argument.startsWith("@") && !(argument.startsWith("@@"))) {
          var file = Path.of(argument.substring(1));
          var list = expandArgumentsFile(file);
          arguments.addAll(list);
          continue;
        }
        arguments.add(argument);
      }
      return arguments.stream();
    }

    static List<String> expandArgumentsFile(Path file) {
      if (Files.notExists(file)) throw new RuntimeException("Arguments file not found: " + file);
      var arguments = new ArrayList<String>();
      try {
        for (var line : Files.readAllLines(file)) {
          line = line.strip();
          if (line.isEmpty()) continue;
          if (line.startsWith("#")) continue;
          if (line.startsWith("@") && !line.startsWith("@@")) {
            throw new IllegalArgumentException("Expand arguments file not allowed: " + line);
          }
          arguments.add(line);
        }
      } catch (RuntimeException exception) {
        throw exception;
      } catch (Exception exception) {
        throw new RuntimeException("Read all lines from file failed: " + file, exception);
      }
      return List.copyOf(arguments);
    }

    default ArgumentsProcessor andThen(ArgumentsProcessor after) {
      return stream -> after.apply(apply(stream));
    }
  }
}
