package main;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.Boolean.parseBoolean;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;

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
    var varargs = options.stream().filter(AbstractOption::isVarargs).toList();
    if (varargs.isEmpty()) return;
    if (varargs.size() > 1)
      throw new IllegalArgumentException("Too many varargs types specified: " + varargs);
    var positionals = options.stream().filter(AbstractOption::isPositional).toList();
    if (!AbstractOption.isVarargs(positionals.get(positionals.size() - 1)))
      throw new IllegalArgumentException("varargs is not at last positional option: " + options);
  }

  T split(boolean nested, ArrayDeque<String> pendingArguments) {
    var requiredOptions =
        options.stream().filter(AbstractOption::isRequired).collect(toCollection(ArrayDeque::new));
    var optionalOptionByName = optionalOptionByName(options);
    var workspace = new Workspace(options);
    var flagCount = options.stream().filter(AbstractOption::isFlag).count();
    var flagPattern = flagCount == 0 ? null : Pattern.compile("^-[a-zA-Z]{1," + flagCount + "}$");

    var doubleDashMode = false;
    while (true) {
      if (pendingArguments.isEmpty()) {
        if (requiredOptions.isEmpty()) return workspace.create(finalizer);
        throw new SplittingException("Required option(s) missing: " + requiredOptions);
      }
      // acquire next argument
      var argument = pendingArguments.removeFirst();
      if ("--".equals(argument)) {
        doubleDashMode = true;
        continue;
      }
      var separator = argument.indexOf('=');
      var longForm = separator == -1;
      var argumentName = longForm ? argument : argument.substring(0, separator);
      var shortFormValue = longForm ? null : unQuote(argument.substring(separator + 1));
      // try well-known option first
      if (!doubleDashMode && optionalOptionByName.containsKey(argumentName)) {
        var option = optionalOptionByName.get(argumentName);
        if (option.type() == OptionType.BRANCH) {
          workspace.set(option, splitNested(pendingArguments, option));
          if (!pendingArguments.isEmpty())
            throw new SplittingException("Too many arguments: " + pendingArguments);
          return workspace.create(finalizer);
        }
        var optionValue =
            switch (option.type()) {
              case FLAG -> longForm || parseBoolean(shortFormValue);
              case SINGLE -> {
                var value =
                    option.nestedSchema() != null
                        ? splitNested(pendingArguments, option)
                        : longForm ? nextArgument(pendingArguments, option) : shortFormValue;
                yield Optional.of(value);
              }
              case REPEATABLE -> {
                var value =
                    option.nestedSchema() != null
                        ? Stream.of(splitNested(pendingArguments, option))
                        : longForm
                            ? Stream.of(nextArgument(pendingArguments, option))
                            : Arrays.stream(shortFormValue.split(","));
                var elements = (List<?>) workspace.get(option);
                yield Stream.concat(elements.stream(), value).toList();
              }
              case BRANCH, VARARGS, REQUIRED -> throw new AssertionError("" + option);
            };
        workspace.set(option, optionValue);
        continue; // with next argument
      }
      // maybe a combination of single letter flags?
      if (!doubleDashMode && flagPattern != null && flagPattern.matcher(argument).matches()) {
        var flags = argument.substring(1).chars().mapToObj(c -> "-" + (char) c).toList();
        if (flags.stream().allMatch(optionalOptionByName::containsKey)) {
          flags.forEach(flag -> {
            var option = optionalOptionByName.get(flag);
            workspace.set(option, true);
          });
          continue;
        }
      }
      // try required option
      if (!requiredOptions.isEmpty()) {
        var requiredOption = requiredOptions.removeFirst();
        workspace.set(requiredOption, argument);
        continue;
      }
      // restore pending arguments deque
      pendingArguments.addFirst(argument);
      if (nested) return workspace.create(finalizer);
      // try globbing all pending arguments into a varargs collector
      var varargsOption = options.stream().filter(AbstractOption::isVarargs).findFirst().orElse(null);
      if (varargsOption != null) {
        workspace.set(varargsOption, pendingArguments.toArray(String[]::new));
        return workspace.create(finalizer);
      }
      throw new SplittingException("Unhandled arguments: " + pendingArguments);
    }
  }

  private static HashMap<String, Option<?>> optionalOptionByName(List<Option<?>> options) {
    var optionalOptionByName = new HashMap<String, Option<?>>();
    for (var option : options) {
      if (AbstractOption.isPositional(option)) {
        continue;  // skip positional option
      }
      for (var name : option.names()) {
        optionalOptionByName.put(name, option);
      }
    }
    return optionalOptionByName;
  }

  private static String nextArgument(ArrayDeque<String> pendingArguments, Option<?> option) {
    if (pendingArguments.isEmpty()) {
      throw new SplittingException("no argument available for option " + option);
    }
    return pendingArguments.removeFirst();
  }

  private static String unQuote(String str) {
    return str.charAt(0) == '"' && str.charAt(str.length()-1) == '"' ? str.substring(1, str.length()-1) : str;
  }

  private static Object splitNested(ArrayDeque<String> pendingArguments, Option<?> option) {
    return option.nestedSchema().split(true, pendingArguments);
  }

  private static final class Workspace {
    private final List<Option<?>> options;
    private final IdentityHashMap<Option<?>, Integer> indexMap;
    private final Object[] array;

    private Workspace(List<Option<?>> options) {
      this.options = options;
      indexMap = range(0, options.size()).boxed().collect(toMap(options::get, i -> i, (_1, _2) -> { throw null; }, IdentityHashMap::new));
      var array = new Object[options.size()];
      Arrays.setAll(array, i -> AbstractOption.defaultValue(options.get(i)));
      this.array = array;
    }

    Object get(Option<?> option) {
      return array[indexMap.get(option)];
    }

    void set(Option<?> option, Object value) {
      array[indexMap.get(option)] = value;
    }

    private static Object convert(Option<?> option, Object value) {
      try {
        return AbstractOption.applyConverter(option, value);
      } catch(RuntimeException e) {
        throw new SplittingException("error while calling converter for option " + option, e);
      }
    }

    <T> T create(Function<? super List<Object>, ? extends T> finalizer) {
      var values = range(0, options.size())
          .mapToObj(i -> convert(options.get(i), array[i]))
          .toList();
      return finalizer.apply(values);
    }
  }
}
