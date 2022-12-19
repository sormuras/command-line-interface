package main;

import static java.lang.Boolean.parseBoolean;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@FunctionalInterface
public interface Splitter<T> {

  static <R extends Record> Splitter<R> of(Lookup lookup, Class<R> schema) {
    requireNonNull(schema, "schema is null");
    requireNonNull(lookup, "lookup is null");
    return of(RecordSchemaSupport.toSchema(lookup, schema));
  }

  static <T> Splitter<T> of(Command<T> cmd) {
    Objects.requireNonNull(cmd, "schema is null");
    return args -> {
      requireNonNull(args, "args is null");
      return split(cmd, false, args.collect(toCollection(ArrayDeque::new)));
    };
  }

  T split(Stream<String> args);

  default T split(String... args) {
    requireNonNull(args, "args is null");
    return split(Stream.of(args));
  }

  default T split(List<String> args) {
    requireNonNull(args, "args is null");
    return split(args.stream());
  }

  /*
  Argument preprocessing
   */

  default Splitter<T> withEach(UnaryOperator<String> preprocessor) {
    requireNonNull(preprocessor, "preprocessor is null");
    return args -> split(args.map(preprocessor));
  }

  default Splitter<T> withExpand(Function<? super String, ? extends Stream<String>> preprocessor) {
    requireNonNull(preprocessor, "preprocessor is null");
    return args -> split(args.flatMap(preprocessor));
  }

  default Splitter<T> withAdjust(UnaryOperator<Stream<String>> preprocessor) {
    requireNonNull(preprocessor, "preprocessor is null");
    return args -> split(preprocessor.apply(args));
  }

  /*
  Implementation
   */

  private static <T> T split(Command<T> cmd, boolean nested, Deque<String> remainingArgs) {
    var options = cmd.options;
    var requiredOptions =
        options.stream().filter(Option::isRequired).collect(toCollection(ArrayDeque::new));
    var optionsByName = new HashMap<String, Option<?>>();
    var workspace = new LinkedHashMap<String, Object>();
    var flagCount = options.stream().filter(Option::isFlag).count();
    var flagPattern = flagCount == 0 ? null : Pattern.compile("^-[a-zA-Z]{1," + flagCount + "}$");
    for (var option : options) {
      for (var name : option.names()) {
        optionsByName.put(name, option);
      }
      workspace.put(option.name(), option.defaultValue());
    }

    boolean doubleDashMode = false;
    while (true) {
      if (remainingArgs.isEmpty()) {
        if (requiredOptions.isEmpty()) return cmd.create(workspace.values());
        throw new IllegalArgumentException("Required option(s) missing: " + requiredOptions);
      }
      // acquire next argument
      var argument = remainingArgs.removeFirst();
      int separator = argument.indexOf('=');
      var noValue = separator == -1;
      var maybeName = noValue ? argument : argument.substring(0, separator);
      if ("--".equals(maybeName)) {
        doubleDashMode = true;
        continue;
      }
      var maybeValue = noValue ? "" : unQuote(argument.substring(separator + 1));
      // try well-known option first
      if (!doubleDashMode && optionsByName.containsKey(maybeName)) {
        var option = optionsByName.get(maybeName);
        var name = option.name();
        if (option.type() == Option.Type.BRANCH) {
          workspace.put(name, split(option, remainingArgs));
          if (!remainingArgs.isEmpty())
            throw new IllegalArgumentException("Too many arguments: " + remainingArgs);
          return cmd.create(workspace.values());
        }
        var optionValue =
            switch (option.type()) {
              case FLAG -> noValue || parseBoolean(maybeValue);
              case SINGLE -> {
                var value =
                    option.subCommand() != null
                        ? split(option, remainingArgs)
                        : option.create(noValue ? remainingArgs.pop() : maybeValue);
                yield Optional.of(value);
              }
              case REPEATABLE -> {
                var value =
                    option.subCommand() != null
                        ? List.of(split(option, remainingArgs))
                        : noValue
                            ? List.of(option.create(remainingArgs.pop()))
                            : Stream.of(maybeValue.split(",")).map(option::create).toList();
                var elements = (List<?>) workspace.get(name);
                yield Stream.concat(elements.stream(), value.stream()).toList();
              }
              case BRANCH, VARARGS, REQUIRED -> throw new AssertionError("Unnamed name? " + name);
            };
        workspace.put(name, optionValue);
        continue; // with next argument
      }
      // maybe a combination of single letter flags?
      if (!doubleDashMode && flagPattern != null && flagPattern.matcher(argument).matches()) {
        var flags = argument.substring(1).chars().mapToObj(c -> "-" + (char) c).toList();
        if (flags.stream().allMatch(optionsByName::containsKey)) {
          flags.forEach(flag -> workspace.put(optionsByName.get(flag).name(), true));
          continue;
        }
      }
      // try required option
      if (!requiredOptions.isEmpty()) {
        var requiredOption = requiredOptions.pop();
        workspace.put(requiredOption.name(), requiredOption.create(argument));
        continue;
      }
      // restore pending arguments deque
      remainingArgs.addFirst(argument);
      if (nested) return cmd.create(workspace.values());
      // try globbing all pending arguments into a varargs collector
      var varargsOption = options.stream().filter(Option::isVarargs).findFirst().orElse(null);
      if (varargsOption != null) {
        workspace.put(
            varargsOption.name(),
            remainingArgs.stream()
                .map(varargsOption::create)
                .toArray(size -> (Object[]) Array.newInstance(varargsOption.valueType(), size)));
        return cmd.create(workspace.values());
      }
      throw new IllegalArgumentException("Unhandled arguments: " + remainingArgs);
    }
  }

  private static String unQuote(String str) {
    return str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"'
        ? str.substring(1, str.length() - 1)
        : str;
  }

  private static Object split(Option<?> option, Deque<String> pendingArguments) {
    return split(option.subCommand(), true, pendingArguments);
  }
}
