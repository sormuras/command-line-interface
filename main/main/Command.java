package main;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class Command<T> {

  final List<? extends Option<?>> options;
  private final Supplier<T> finalizer;

  public Command(List<? extends Option<?>> options, Supplier<T> finalizer) {
    requireNonNull(options, "options is null");
    requireNonNull(finalizer, "finalizer is null");
    var opts = List.copyOf(options);
    checkCardinality(opts);
    checkDuplicates(opts);
    checkVarargs(opts);
    this.options = opts;
    this.finalizer = finalizer;
  }

  private static void checkCardinality(List<? extends Option<?>> options) {
    if (options.isEmpty()) throw new IllegalArgumentException("At least one option is expected");
  }

  private static void checkDuplicates(List<? extends Option<?>> options) {
    var optionsByName = new HashMap<String, Option<?>>();
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

  private static void checkVarargs(List<? extends Option<?>> options) {
    List<? extends Option<?>> varargs = options.stream().filter(Option::isVarargs).toList();
    if (varargs.isEmpty()) return;
    if (varargs.size() > 1)
      throw new IllegalArgumentException("Too many varargs types specified: " + varargs);
    var positionals = options.stream().filter(Option::isPositional).toList();
    if (!positionals.get(positionals.size() - 1).isVarargs())
      throw new IllegalArgumentException("varargs is not at last positional option: " + options);
  }
  public T create() {
    return finalizer.get();
  }

  public Command<T> addFlag(Consumer<Boolean> to, String...names) {

    return this;
  }
}
