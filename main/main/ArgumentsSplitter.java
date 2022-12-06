package main;

import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.Boolean.parseBoolean;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

@FunctionalInterface
public interface ArgumentsSplitter<T> {

  T split(Stream<String> args);

  default T split(String... args) {
    return split(Stream.of(args));
  }

  default ArgumentsSplitter<T> with(UnaryOperator<String> preprocessor){
    return args -> split(args.map(preprocessor));
  }

  default ArgumentsSplitter<T> with(Function<String, Stream<String>> preprocessor) {
    return args -> split(args.flatMap(preprocessor));
  }

  static Object[] split(Schema schema, Stream<String> args) {
    return split(schema, false, args.collect(toCollection(ArrayDeque::new)));
  }

  private static Object[] split(Schema schema, boolean nested, ArrayDeque<String> pendingArguments) {
    requireNonNull(schema, "schema is null");
    schema.check();
    var requiredOptions =
        schema.stream().filter(Option::isRequired).collect(toCollection(ArrayDeque::new));
    var optionsByName = new HashMap<String, Option>();
    var workspace = new LinkedHashMap<String, Object>();
    var flagCount = schema.stream().filter(Option::isFlag).count();
    var flagPattern = flagCount == 0 ? null : Pattern.compile("^-[a-zA-Z]{1," + flagCount + "}$");
    for (var option : schema.iterable()) {
      for (var name : option.names()) {
        optionsByName.put(name, option);
      }
      workspace.put(option.name(), option.type().defaultValue());
    }

    while (true) {
      if (pendingArguments.isEmpty()) {
        if (requiredOptions.isEmpty()) return workspace.values().toArray();
        throw new IllegalArgumentException("Required option(s) missing: " + requiredOptions);
      }
      // acquire next argument
      var argument = pendingArguments.removeFirst();
      int separator = argument.indexOf('=');
      var noValue = separator == -1;
      var maybeName = noValue ? argument : argument.substring(0, separator);
      var maybeValue = noValue ? "" : argument.substring(separator + 1);
      // try well-known option first
      if (optionsByName.containsKey(maybeName)) {
        var option = optionsByName.get(maybeName);
        var name = option.name();
        workspace.put(
            name,
            switch (option.type()) {
              case FLAG -> noValue || parseBoolean(maybeValue);
              case KEY_VALUE -> {
                var value =
                    option.nestedSchema() != null
                        ? splitNested(pendingArguments, option)
                        : noValue ? pendingArguments.pop() : maybeValue;
                yield Optional.of(value);
              }
              case REPEATABLE -> {
                var value =
                    option.nestedSchema() != null
                        ? List.of(splitNested(pendingArguments, option))
                        : noValue
                            ? List.of(pendingArguments.pop())
                            : List.of(maybeValue.split(","));
                var elements = (List<?>) workspace.get(name);
                yield Stream.concat(elements.stream(), value.stream()).toList();
              }
              case VARARGS, REQUIRED -> throw new AssertionError("Unnamed name? " + name);
            });
        continue;
      }
      // maybe a combination of single letter flags?
      if (flagPattern != null && flagPattern.matcher(argument).matches()) {
        var flags = argument.substring(1).chars().mapToObj(c -> "-" + (char) c).toList();
        if (flags.stream().allMatch(optionsByName::containsKey)) {
          flags.forEach(flag -> workspace.put(optionsByName.get(flag).name(), true));
          continue;
        }
      }
      // try required option
      if (!requiredOptions.isEmpty()) {
        var requiredOption = requiredOptions.pop();
        workspace.put(requiredOption.name(), argument);
        continue;
      }
      // restore pending arguments deque
      pendingArguments.addFirst(argument);
      if (nested) return workspace.values().toArray();
      // try globbing all pending arguments into a varargs collector
      var varargsOption = schema.varargs();
      if (varargsOption.isPresent()) {
        workspace.put(varargsOption.get().name(), pendingArguments.toArray(String[]::new));
        return workspace.values().toArray();
      }
      throw new IllegalArgumentException("Unhandled arguments: " + pendingArguments);
    }
  }

  private static Object splitNested(ArrayDeque<String> pendingArguments, Option option) {
    return split(option.nestedSchema(), true, pendingArguments);
  }

}
