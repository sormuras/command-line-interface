package main;

import static java.lang.Boolean.parseBoolean;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@FunctionalInterface
public interface Splitter<T> {

  static <R extends Record> Splitter<R> of(Class<R> schema, MethodHandles.Lookup lookup) {
    return of(RecordSchemaSupport.toSchema(schema, lookup));
  }
  static Splitter<List<Value>> of(Option... options) {
    return of(true, options);
  }
  static Splitter<List<Value>> of(boolean pruned, Option... options) {
    return of(Value.toSchema(pruned, options));
  }

  static <X> Splitter<X> of(Schema<X> schema) {
    return args -> split(schema, args);
  }

  T split(Stream<String> args);

  default T split(String... args) {
    return split(Stream.of(args));
  }

  default T split(Collection<String> args) {
    return split(args.stream());
  }

  /*
  Argument preprocessing
   */

  default Splitter<T> withEach(UnaryOperator<String> preprocessor) {
    return args -> split(args.map(preprocessor));
  }

  default Splitter<T> withExpand(Function<String, Stream<String>> preprocessor) {
    return args -> split(args.flatMap(preprocessor));
  }

  default Splitter<T> withAdjust(UnaryOperator<Stream<String>> preprocessor) {
    return args -> split(preprocessor.apply(args));
  }

  /*
  Splitter Implementation
   */

  private static <X> X split(Schema<X> schema, Stream<String> args) {
    return split(schema, false, args.collect(toCollection(ArrayDeque::new)));
  }

  private static <X> X split(
      Schema<X> schema, boolean nested, ArrayDeque<String> pendingArguments) {
    requireNonNull(schema, "schema is null");
    var requiredOptions =
        schema.options().stream().filter(Option::isRequired).collect(toCollection(ArrayDeque::new));
    var optionsByName = new HashMap<String, Option>();
    var workspace = new LinkedHashMap<String, Object>();
    var flagCount = schema.options().stream().filter(Option::isFlag).count();
    var flagPattern = flagCount == 0 ? null : Pattern.compile("^-[a-zA-Z]{1," + flagCount + "}$");
    for (var option : schema.options()) {
      for (var name : option.names()) {
        optionsByName.put(name, option);
      }
      workspace.put(option.name(), option.type().defaultValue());
    }

    while (true) {
      if (pendingArguments.isEmpty()) {
        if (requiredOptions.isEmpty()) return schema.create(workspace.values());
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
      if (nested) return schema.create(workspace.values());
      // try globbing all pending arguments into a varargs collector
      var varargsOption = schema.varargs();
      if (varargsOption.isPresent()) {
        workspace.put(varargsOption.get().name(), pendingArguments.toArray(String[]::new));
        return schema.create(workspace.values());
      }
      throw new IllegalArgumentException("Unhandled arguments: " + pendingArguments);
    }
  }

  private static Object splitNested(ArrayDeque<String> pendingArguments, Option option) {
    return split(option.nestedSchema(), true, pendingArguments);
  }
}
