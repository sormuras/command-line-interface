package main;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.Boolean.parseBoolean;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

public class Schema<T> {
  final List<Option> options;
  private final Function<? super List<Object>, ? extends T> finalizer;

  public Schema(List<? extends Option> options, Function<? super List<Object>, ? extends T> finalizer) {
    requireNonNull(options, "options is null");
    requireNonNull(finalizer, "finalizer is null");
    var opts = List.<Option>copyOf(options);
    checkCardinality(opts);
    checkDuplicates(opts);
    checkVarargs(opts);
    this.options = opts;
    this.finalizer = finalizer;
  }

  private static void checkCardinality(List<Option> options) {
    if (options.isEmpty()) throw new IllegalArgumentException("At least one option is expected");
  }

  private static void checkDuplicates(List<Option> options) {
    var optionsByName = new HashMap<String, Option>();
    for (var option : options) {
      var names = option.names();
      for (var name : names) {
        var otherOption = optionsByName.put(name, option);
        if (otherOption == option)
          throw new IllegalArgumentException(
              "option " + option + " declares duplicated name " + name);
        if (otherOption != null)
          throw new IllegalArgumentException(
              "options " + option + " and " + otherOption + " both declares name " + name);
      }
    }
  }

  private static void checkVarargs(List<Option> options) {
    List<Option> varargs = options.stream().filter(Option::isVarargs).toList();
    if (varargs.isEmpty()) return;
    if (varargs.size() > 1)
      throw new IllegalArgumentException("Too many varargs types specified: " + varargs);
    var positionals = options.stream().filter(Option::isPositional).toList();
    if (!positionals.get(positionals.size() - 1).isVarargs())
      throw new IllegalArgumentException("varargs is not at last positional option: " + options);
  }

  T split(boolean nested, ArrayDeque<String> pendingArguments) {
    var requiredOptions =
        options.stream().filter(Option::isRequired).collect(toCollection(ArrayDeque::new));
    var optionsByName = new HashMap<String, Option>();
    var workspace = new LinkedHashMap<String, Object>();
    var flagCount = options.stream().filter(Option::isFlag).count();
    var flagPattern = flagCount == 0 ? null : Pattern.compile("^-[a-zA-Z]{1," + flagCount + "}$");
    for (var option : options) {
      for (var name : option.names()) {
        optionsByName.put(name, option);
      }
      workspace.put(option.name(), option.type().defaultValue());
    }

    while (true) {
      if (pendingArguments.isEmpty()) {
        if (requiredOptions.isEmpty()) return create(workspace.values());
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
              case SINGLE -> {
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
      if (nested) return create(workspace.values());
      // try globbing all pending arguments into a varargs collector
      var varargsOption = options.stream().filter(Option::isVarargs).findFirst();
      if (varargsOption.isPresent()) {
        workspace.put(varargsOption.get().name(), pendingArguments.toArray(String[]::new));
        return create(workspace.values());
      }
      throw new IllegalArgumentException("Unhandled arguments: " + pendingArguments);
    }
  }

  private Object splitNested(ArrayDeque<String> pendingArguments, Option option) {
    return option.nestedSchema().split(true, pendingArguments);
  }

  private T create(Collection<Object> values) {
    return finalizer.apply(List.copyOf(values));
  }
}
