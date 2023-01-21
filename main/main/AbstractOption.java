package main;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Base class for all {@link Option}s.
 * <p>
 * It implements the basic accessors {@link #type()}, {@link #names()}, {@link #help()} and
 * {@link #nestedSchema()}. And provides helper methods for {@link Schema#split(boolean, ArrayDeque)}.
 *
 * @param <T> type of the argument of the option.
 */
abstract sealed class AbstractOption<T>
    implements Option<T>
    permits Option.Branch, Option.Flag, Option.Single, Option.Repeatable, Option.Required, Option.Varargs {
  final OptionType type;
  final Set<String> names;
  final String help;
  final Schema<?> nestedSchema;

  AbstractOption(OptionType type, Set<String> names, String help, Schema<?> nestedSchema) {
    requireNonNull(type, "type is null");
    requireNonNull(names, "names is null");
    requireNonNull(help, "help null");
    this.type = type;
    this.names = NameSet.copyOf(names);
    this.help = help;
    this.nestedSchema = nestedSchema;
  }

  @Override
  public final OptionType type() {
    return type;
  }

  @Override
  public final Set<String> names() {
    return names;
  }

  @Override
  public final String help() {
    return help;
  }

  @Override
  public final Schema<?> nestedSchema() {
    return nestedSchema;
  }

  @Override
  public final String toString() {
    return type + names.toString();
  }


  // helper methods

  @SuppressWarnings("unchecked")
  static <T> Option<?> newOption(OptionType type, String[] names, Converter<Object, ?> converter, String help, Schema<?> schema) {
    var nameSet = NameSet.of(names);
    return switch (type) {
      case BRANCH -> new Branch<>(nameSet, (Converter<T,T>) converter, help, schema);
      case FLAG -> new Flag(nameSet, (Converter<Boolean,Boolean>) (Converter<?,?>) converter, help);
      case SINGLE -> new Single<T>(nameSet, (Converter<Optional<?>, ? extends Optional<T>>) (Converter<?,?>) converter, help, schema);
      case REPEATABLE -> new Repeatable<T>(nameSet, (Converter<List<?>, ? extends List<T>>) (Converter<?,?>) converter, help, schema);
      case REQUIRED -> new Required<T>(nameSet, (Converter<? super String, ? extends T>) converter, help);
      case VARARGS -> new Varargs<T>(nameSet, (Converter<? super String[], ? extends T[]>) (Converter<?,?>) converter, help);
    };
  }

  @SuppressWarnings("unchecked")
  static <T> T defaultValue(Option<T> option) {
    return (T) option.type().defaultValue;
  }

  @SuppressWarnings("unchecked")
  static <T> T applyConverter(Option<T> option, Object arg) {
    if (option instanceof Option.Branch<T> branch) {
      return branch.converter.apply((T) arg);
    }
    if (option instanceof Option.Flag flag) {
      return (T) flag.converter.apply((Boolean) arg);
    }
    if (option instanceof Option.Single<?> single) {
      return (T) single.converter.apply((Optional<String>) arg);
    }
    if (option instanceof Option.Repeatable<?> repeatable) {
      return (T) repeatable.converter.apply((List<String>) arg);
    }
    if (option instanceof Option.Required<T> required) {
      return required.converter.apply((String) arg);
    }
    if (option instanceof Option.Varargs<?> varargs) {
      return (T) varargs.converter.apply((String[]) arg);
    }
    throw new AssertionError();
  }

  static boolean isVarargs(Option<?> option) {
    return option.type() == OptionType.VARARGS;
  }

  static boolean isRequired(Option<?> option) {
    return option.type() == OptionType.REQUIRED;
  }

  static boolean isFlag(Option<?> option) {
    return option.type() == OptionType.FLAG;
  }

  static boolean isPositional(Option<?> option) {
    return isRequired(option) || isVarargs(option);
  }
}
