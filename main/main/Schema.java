package main;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public record Schema<T>(List<Option> options, Function<Collection<Object>, T> finalizer) {
  public Schema {
    checkCardinality(options);
    checkDuplicates(options);
    checkVarargs(options);
  }

  T create(Collection<Object> values) {
    return finalizer.apply(values);
  }

  Optional<Option> varargs() {
    return options.stream().filter(Option::isVarargs).findFirst();
  }

  static void checkDuplicates(List<Option> options) {
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

  static void checkVarargs(List<Option> options) {
    List<Option> varargs = options.stream().filter(Option::isVarargs).toList();
    if (varargs.isEmpty()) return;
    if (varargs.size() > 1)
      throw new IllegalArgumentException("Too many varargs types specified: " + varargs);
    var positionals = options.stream().filter(Option::isPositional).toList();
    if (!positionals.get(positionals.size() - 1).isVarargs())
      throw new IllegalArgumentException("varargs is not at last positional option: " + options);
  }

  static void checkCardinality(List<Option> options) {
    if (options.isEmpty()) throw new IllegalArgumentException("At least one option is expected");
  }
}
