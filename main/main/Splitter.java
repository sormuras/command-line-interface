package main;

import static java.lang.Boolean.parseBoolean;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import main.Command.Option;

@FunctionalInterface
public interface Splitter<T> {

  static <R> Splitter<R> of(Lookup lookup, Class<R> schema) {
    return of(FactorySupport.factory(lookup, schema));
  }

  static <X> Splitter<X> of(Command.Factory<X> cmd) {
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

  private static <T> T split(Command.Factory<T> cmd, boolean nested, Deque<String> remainingArgs) {
    var res = cmd.create();
    var options = res.options();
    var requiredOptions =
        res.options(OptionType::isRequired).collect(toCollection(ArrayDeque::new));
    var optionsByName = new HashMap<String, Option>();
    options.forEach(opt -> opt.names().forEach(name -> optionsByName.put(name, opt)));
    var flagCount = res.options(OptionType::isFlag).count();
    var flagPattern = flagCount == 0 ? null : Pattern.compile("^-[a-zA-Z]{1," + flagCount + "}$");

    boolean doubleDashMode = false;
    while (true) {
      if (remainingArgs.isEmpty()) {
        if (requiredOptions.isEmpty()) return res.complete();
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
        if (option.type() == OptionType.SUB) {
          split(option, remainingArgs);
          if (!remainingArgs.isEmpty())
            throw new IllegalArgumentException("Too many arguments: " + remainingArgs);
          return res.complete();
        }
        switch (option.type()) {
          case FLAG:
            option.add(noValue || parseBoolean(maybeValue) ? "true" : "false");
            break;
          case OPTIONAL:
            if (option.sub().isPresent()) {
              split(option, remainingArgs);
            } else option.add(noValue ? remainingArgs.pop() : maybeValue);
            break;
            // TODO handle named required (these are not positional but required and have a handle,
            // e.g. "-f file" if it must occur somewhere)
          case REPEATABLE:
            if (option.sub().isPresent()) {
              split(option, remainingArgs);
            } else
              Stream.of((noValue ? remainingArgs.pop() : maybeValue).split(","))
                  .forEach(option::add);
            break;
          default:
            throw new AssertionError("Unnamed name? " + name);
        }
        continue; // with next argument
      }
      // maybe a combination of single letter flags?
      if (!doubleDashMode && flagPattern != null && flagPattern.matcher(argument).matches()) {
        var flags = argument.substring(1).chars().mapToObj(c -> "-" + (char) c).toList();
        if (flags.stream().allMatch(optionsByName::containsKey)) {
          flags.forEach(flag -> optionsByName.get(flag).add("true"));
          continue;
        }
      }
      // try required option
      if (!requiredOptions.isEmpty()) {
        var requiredOption = requiredOptions.pop();
        requiredOption.add(argument);
        continue;
      }
      // restore pending arguments deque
      remainingArgs.addFirst(argument);
      if (nested) return res.complete();
      // try globbing all pending arguments into a varargs collector
      var varargsOption = res.options(OptionType::isVarargs).findFirst().orElse(null);
      if (varargsOption != null) {
        remainingArgs.forEach(varargsOption::add);
        return res.complete();
      }
      throw new IllegalArgumentException("Unhandled arguments: " + remainingArgs);
    }
  }

  private static String unQuote(String str) {
    return str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"'
        ? str.substring(1, str.length() - 1)
        : str;
  }

  private static void split(Option option, Deque<String> pendingArguments) {
    split(option.sub().get(), true, pendingArguments);
  }
}
