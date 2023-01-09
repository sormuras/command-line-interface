package main;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@FunctionalInterface
public interface Splitter<T> {

  static <R> Splitter<R> of(Lookup lookup, Class<R> schema) {
    return of(FactorySupport.factory(lookup, schema));
  }

  static <X> Splitter<X> of(CommandLine.Factory<X> cmd) {
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

  default Splitter<T> withSplitAssignment() {
    Pattern assign = Pattern.compile("^([-\\w]+)=(.*)$");
    return withExpand(
        arg -> {
          Matcher m = assign.matcher(arg);
          return m.matches() ? Stream.of(m.group(1), m.group(2)) : Stream.of(arg);
        });
  }

  default Splitter<T> withRemoveQuotes() {
    BiPredicate<String, Character> startsAndEndsWith =
        (str, c) -> str.charAt(0) == c && str.charAt(str.length() - 1) == c;
    return withEach(
        arg ->
            startsAndEndsWith.test(arg, '"') || startsAndEndsWith.test(arg, '\'')
                ? arg.substring(1, arg.length() - 1)
                : arg);
  }

  /*
  Implementation
   */

  private static <X> X split(
      CommandLine.Factory<X> cmd, boolean nested, Deque<String> remainingArgs) {
    var res = cmd.create();
    var options = res.options();
    var requiredOptions =
        res.options(OptionType::isRequired).collect(toCollection(ArrayDeque::new));
    var optionsByHandle = new HashMap<String, CommandLine.Option>();
    options.forEach(opt -> opt.handles().forEach(handle -> optionsByHandle.put(handle, opt)));
    var flagCount = res.options(OptionType::isFlag).count();
    var flagPattern = flagCount == 0 ? null : Pattern.compile("^-[a-zA-Z]{1," + flagCount + "}$");

    boolean doubleDashMode = false;
    while (true) {
      if (remainingArgs.isEmpty()) {
        if (requiredOptions.isEmpty()) return res.complete();
        throw new IllegalArgumentException("Required option(s) missing: " + requiredOptions);
      }
      // acquire next argument
      var handle = remainingArgs.removeFirst();
      if ("--".equals(handle)) {
        doubleDashMode = true;
        continue;
      }
      // try well-known option first
      if (!doubleDashMode && optionsByHandle.containsKey(handle)) {
        var option = optionsByHandle.get(handle);
        var name = option.name();
        if (option.type() == OptionType.SUB) {
          split(option, remainingArgs);
          if (!remainingArgs.isEmpty())
            throw new IllegalArgumentException("Too many arguments: " + remainingArgs);
          return res.complete();
        }
        switch (option.type()) {
          case FLAG:
            var maybeValue = remainingArgs.peekFirst();
            if ("true".equals(maybeValue) || "false".equals(maybeValue))
              option.add(remainingArgs.pop());
            else option.add("true");
            break;
          case OPTIONAL:
            if (option.sub().isPresent()) split(option, remainingArgs);
            else option.add(remainingArgs.pop());
            break;
          case REQUIRED:
            requiredOptions.remove(option);
            option.add(remainingArgs.pop());
            break;
          case REPEATABLE:
            if (option.sub().isPresent()) split(option, remainingArgs);
            else Stream.of(remainingArgs.pop().split(",")).forEach(option::add);
            break;
          default:
            throw new AssertionError("Unnamed name? " + name);
        }
        continue; // with next argument
      }
      // maybe a combination of single letter flags?
      if (!doubleDashMode && flagPattern != null && flagPattern.matcher(handle).matches()) {
        var flags = handle.substring(1).chars().mapToObj(c -> "-" + (char) c).toList();
        if (flags.stream().allMatch(optionsByHandle::containsKey)) {
          flags.forEach(flag -> optionsByHandle.get(flag).add("true"));
          continue;
        }
      }
      // try required option
      if (!requiredOptions.isEmpty()) {
        requiredOptions.pop().add(handle);
        continue;
      }
      // restore pending arguments deque
      remainingArgs.addFirst(handle);
      if (nested) return res.complete();
      // try globbing all pending arguments into a varargs collector
      var varargsOption = res.options(OptionType::isVarargs).findFirst().orElse(null);
      if (varargsOption != null) {
        remainingArgs.forEach(varargsOption::add);
        remainingArgs.clear();
        return res.complete();
      }
      throw new IllegalArgumentException("Unhandled arguments: " + remainingArgs);
    }
  }

  private static void split(CommandLine.Option option, Deque<String> remainingArgs) {
    split(option.sub().get(), option.type() != OptionType.SUB, remainingArgs);
  }
}
