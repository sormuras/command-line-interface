package main;

import java.util.ArrayDeque;
import java.util.ArrayList;
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

/**
 * Schema used to split the command line into arguments.
 * The arguments are described using {@link Option}s, the {@link Splitter} splits command line arguments
 * following the recipe defined by the options.
 * <p>
 * The option list should follow these rules:
 * <ul>
 *   <li>there is at least one option
 *   <li>no two option share the same name
 *   <li>there is only zero or one varargs
 *   <li>the varargs should appear after all required.
 * </ul>
 * <p>
 * The schema also specify a finalizer function that is called with the list of arguments decoded by
 * the {@link Splitter} so the arguments can be bundled into a more user-friendly class.
 * <p>
 * This class is deigned to be immutable so the finalizer should not do any side effects.
 *
 * @param <T> the type of the value bundling the command line arguments.
 * @see Splitter
 */
public class Schema<T> {
  final List<Option<?>> options;
  private final Function<? super List<Object>, ? extends T> finalizer;

  /**
   * Create a schema from a list of options and a finalizer function.
   *
   * @param options the options.
   * @param finalizer a side effect free function.
   * @throws IllegalArgumentException if the list of options is empty, if the same option is specified twice,
   * if at least two options share the same name, if there are more than one varargs, if the varargs is
   * not specified after the required option.
   */
  public Schema(List<? extends Option<?>> options, Function<? super List<Object>, ? extends T> finalizer) {
    requireNonNull(options, "options is null");
    requireNonNull(finalizer, "finalizer is null");
    var opts = List.<Option<?>>copyOf(options);
    checkCardinality(opts);
    checkDuplicates(opts);
    checkVarargs(opts);
    this.options = opts;
    this.finalizer = finalizer;
  }

  private static void checkCardinality(List<Option<?>> options) {
    if (options.isEmpty()) throw new IllegalArgumentException("At least one option is expected");
  }

  private static void checkDuplicates(List<Option<?>> options) {
    var optionsByName = new HashMap<String, Option<?>>();
    for (var option : options) {
      var names = option.names();
      for (var name : names) {
        var otherOption = optionsByName.put(name, option);
        if (otherOption != null)
          throw new IllegalArgumentException(
              "options " + option + " and " + otherOption + " both declares name " + name);
      }
    }
  }

  private static void checkVarargs(List<Option<?>> options) {
    var varargs = options.stream().filter(Schema::isVarargsOption).toList();
    if (varargs.isEmpty()) return;
    if (varargs.size() > 1)
      throw new IllegalArgumentException("Too many varargs types specified: " + varargs);
    var positionals = options.stream().filter(Schema::isPositionalOption).toList();
    if (!isVarargsOption(positionals.get(positionals.size() - 1)))
      throw new IllegalArgumentException("varargs is not at last positional option: " + options);
  }

  T split(boolean nested, ArrayDeque<String> pendingArguments) {
    var requiredOptions =
        options.stream().filter(Schema::isRequiredOption).collect(toCollection(ArrayDeque::new));
    var optionsByName = new HashMap<String, Option<?>>();
    var workspace = new LinkedHashMap<String, Object>();
    var flagCount = options.stream().filter(Schema::isFlagOption).count();
    var flagPattern = flagCount == 0 ? null : Pattern.compile("^-[a-zA-Z]{1," + flagCount + "}$");
    for (var option : options) {
      for (var name : option.names()) {
        optionsByName.put(name, option);
      }
      workspace.put(optionName(option), optionDefaultValue(option));
    }

    boolean doubleDashMode = false;
    while (true) {
      if (pendingArguments.isEmpty()) {
        if (requiredOptions.isEmpty()) return create(workspace, optionsByName);
        throw new IllegalArgumentException("Required option(s) missing: " + requiredOptions);
      }
      // acquire next argument
      var argument = pendingArguments.removeFirst();
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
        var name = optionName(option);
        if (option.type() == Option.Type.BRANCH) {
          workspace.put(name, splitNested(pendingArguments, option));
          if (!pendingArguments.isEmpty())
            throw new IllegalArgumentException("Too many arguments: " + pendingArguments);
          return create(workspace, optionsByName);
        }
        var optionValue =
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
                        : Stream.of(maybeValue.split(",")).toList();
                var elements = (List<?>) workspace.get(name);
                yield Stream.concat(elements.stream(), value.stream()).toList();
              }
              case BRANCH, VARARGS, REQUIRED -> throw new AssertionError("Unnamed name? " + name);
            };
        workspace.put( name, optionValue);
        continue; // with next argument
      }
      // maybe a combination of single letter flags?
      if (!doubleDashMode && flagPattern != null && flagPattern.matcher(argument).matches()) {
        var flags = argument.substring(1).chars().mapToObj(c -> "-" + (char) c).toList();
        if (flags.stream().allMatch(optionsByName::containsKey)) {
          flags.forEach(flag -> {
            var option = optionsByName.get(flag);
            workspace.put(optionName(option),true);
          });
          continue;
        }
      }
      // try required option
      if (!requiredOptions.isEmpty()) {
        var requiredOption = requiredOptions.pop();
        workspace.put(optionName(requiredOption), argument);
        continue;
      }
      // restore pending arguments deque
      pendingArguments.addFirst(argument);
      if (nested) return create(workspace, optionsByName);
      // try globbing all pending arguments into a varargs collector
      var varargsOption = options.stream().filter(Schema::isVarargsOption).findFirst().orElse(null);
      if (varargsOption != null) {
        workspace.put(optionName(varargsOption), pendingArguments.toArray(String[]::new));
        return create(workspace, optionsByName);
      }
      throw new IllegalArgumentException("Unhandled arguments: " + pendingArguments);
    }
  }

  private static String unQuote(String str) {
    return str.charAt(0) == '"' && str.charAt(str.length()-1) == '"' ? str.substring(1, str.length()-1) : str;
  }

  private static Object splitNested(ArrayDeque<String> pendingArguments, Option<?> option) {
    return option.nestedSchema().split(true, pendingArguments);
  }

  private T create(LinkedHashMap<String, Object> workspace, HashMap<String, Option<?>> optionsByName) {
    var values = new ArrayList<>();
    workspace.forEach((optionName, value) -> {
      var option = optionsByName.get(optionName);
      values.add(optionApply(option, value));
    });
    return finalizer.apply(values);
  }


  // helper methods

  @SuppressWarnings("unchecked")
  private static <T> T optionDefaultValue(Option<T> option) {
    return (T) option.type().defaultValue;
  }

  @SuppressWarnings("unchecked")
  private static <T> T optionApply(Option<T> option, Object arg) {
    if (option instanceof Option.Branch<T> branch) {
      return branch.toValue.apply((T) arg);
    }
    if (option instanceof Option.Flag flag) {
      return (T) flag.toValue.apply((Boolean) arg);
    }
    if (option instanceof Option.Single<?> single) {
      return (T) single.toValue.apply((Optional<String>) arg);
    }
    if (option instanceof Option.Repeatable<?> repeatable) {
      return (T) repeatable.toValue.apply((List<String>) arg);
    }
    if (option instanceof Option.Required<T> required) {
      return required.toValue.apply((String) arg);
    }
    if (option instanceof Option.Varargs<?> varargs) {
      return (T) varargs.toValue.apply((String[]) arg);
    }
    throw new AssertionError();
  }

  private static String optionName(Option<?> option) {
    return option.names().iterator().next();
  }

  private static boolean isVarargsOption(Option<?> option) {
    return option instanceof Option.Varargs<?>;
  }

  private static boolean isRequiredOption(Option<?> option) {
    return option instanceof Option.Required<?>;
  }

  private static boolean isFlagOption(Option<?> option) {
    return option instanceof Option.Flag;
  }

  private static boolean isPositionalOption(Option<?> option) {
    return option instanceof Option.Required<?> || option instanceof Option.Varargs<?>;
  }
}
