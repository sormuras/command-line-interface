package main;

import main.AbstractOption.NameSet;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * An Option is a description of one of more arguments of the command line.
 * Option are immutable classes and can be shared between several {@link Schema schemas}.
 *
 * <p>There two kinds of options, optional options with three sub-categories {@link #flag(String...) FLAG},
 * {@link #single(String...) SINGLE} and {@link #repeatable(String...) REPETABLE} and positional
 * options with two sub-categories {@link #required(String...) REQUIRED} and
 * {@link #varargs(String...) VARARGS}.
 *
 * <p>&nbsp;
 *
 * <table>
 *   <caption>Overview of the different options</caption>
 *   <tr>
 *     <th>Option type</th><th>positional</th><th>arguments ?</th><th>Java type</th><th>default value</th>
 *   </tr><tr>
 *     <td>FLAG</td><td>no</td><td>no argument</td><td>boolean or Boolean</td><td>false</td>
 *  </tr><tr>
 *     <td>SINGLE</td><td>no</td><td>one argument</td><td>java.util.Optional</td><td>Optional.&lt;String&gt;empty()</td>
 *  </tr><tr>
 *     <td>REPEATABLE</td><td>no</td><td>many arguments</td><td>java.util.List</td><td>List.&lt;String&gt;of()</td>
 *  </tr><tr>
 *    <td>REQUIRED</td><td>yes</td><td>an argument</td><td>any Object</td><td>null</td>
 *  </tr><tr>
 *    <td>VARARGS</td><td>yes</td><td>many arguments</td><td>any object array</td><td>new String[0]</td>
 *  </tr>
 * </table>
 *
 * <p>Options are created using factory methods, {@link #flag(String...)}, {@link #single(String...)},
 * {@link #repeatable(String...)}, {@link #required(String...)} or {@link #varargs(String...)}.
 *
 * <p>Options have a {@link #type() type}, case-sensitive {@link #names() names} and optionally
 * a {@link #help() help text}, a {@link #defaultValue(Object) default value} and
 * {@link #nestedSchema() a nested schema}.
 *
 * <p>The argument(s) of an option can be converted using Options specific {@code convert()} methods,
 * {@link Flag#convert(UnaryOperator)}, {@link Single#convert(Function)}, {@link Repeatable#convert(Function)},
 * {@link Required#convert(Function)} and {@link Varargs#convert(Function, IntFunction)}.
 *
 * <p>Options are grouped into a {@link Schema#Schema(List, Function) schema} that is used to create a
 * {@link Splitter#of(Schema) Splitter} that parses the command line.
 * The value of argument(s) of an option are retrieved using {@link #argument(ArgumentMap)}.
 *
 * @param <T> type of the argument(s) described by this Option.
 */
public sealed interface Option<T> permits AbstractOption {
  /**
   * Type of {@link Option}.
   */
  enum Type {
    BRANCH(null),
    /** An optional flag, like {@code --verbose}. */
    FLAG(false),
    /** An optional key-value pair, like {@code --version 47.11}. */
    SINGLE(Optional.empty()),
    /** An optional and repeatable key, like {@code --with alpha --with omega} */
    REPEATABLE(List.of()),
    /** A required positional option */
    REQUIRED(null),
    /** A collection of all unhandled arguments. */
    VARARGS(new String[0]);

    final Object defaultValue;

    Type(Object defaultValue) {
      this.defaultValue = defaultValue;
    }
  }

  /**
   * An option that branch to a nested schema.
   *
   * @param <T> the type of the argument corresponding to that option.
   */
  final class Branch<T> extends AbstractOption<T> {
    final UnaryOperator<T> toValue;

    /**
     * Create a branch option with several names, a conversion function, a help text and a nested schema
     *
     * @param names names of the option.
     * @param toValue a conversion function.
     * @param help a help text.
     * @param nestedSchema a nested schema or null.
     * @throws IllegalArgumentException if a name is empty
     *
     * @see #branch(String...)
     */
    public Branch(Set<String> names, UnaryOperator<T> toValue, String help, Schema<?> nestedSchema) {
      super(Type.BRANCH, names, help, nestedSchema);
      this.toValue = requireNonNull(toValue, "toValue is null");
    }

    @Override
    public Branch<T> help(String helpText) {
      requireNonNull(helpText, "helpText is null");
      if (!help.isEmpty()) {
        throw new IllegalStateException("option already has an help text");
      }
      return new Branch<>(names, toValue, helpText, nestedSchema);
    }

    @Override
    public Branch<T> nestedSchema(Schema<?> nestedSchema) {
      requireNonNull(nestedSchema, "nestedSchema is null");
      throw new IllegalStateException("a nested schema is already set");
    }

    /**
     * Returns a new option that converts the argument to a value of the same type.
     *
     * @param mapper the function to apply to do the conversion.
     * @return a new option that converts the argument to a value of the same type.
     */
    public Branch<T> convert(UnaryOperator<T> mapper) {
      requireNonNull(mapper, "mapper is null");
      return new Branch<>(names, v -> mapper.apply(toValue.apply(v)), help, nestedSchema);
    }

    @Override
    public Branch<T> defaultValue(T value) {
      return convert(v -> v == null? value: v);
    }
  }

  /**
   * A boolean optional option.
   */
  final class Flag extends AbstractOption<Boolean> {
    final UnaryOperator<Boolean> toValue;

    /**
     * Create a flag option with several names, a conversion function, a help text and a nested schema
     *
     * @param names names of the option.
     * @param toValue a conversion function.
     * @param help a help text.
     * @param nestedSchema a nested schema or null.
     * @throws IllegalArgumentException if a name is empty
     *
     * @see #flag(String...)
     */
    public Flag(Set<String> names, UnaryOperator<Boolean> toValue, String help, Schema<?> nestedSchema) {
      super(Type.FLAG, names, help, nestedSchema);
      this.toValue = requireNonNull(toValue, "toValue is null");
    }

    @Override
    public Flag help(String helpText) {
      requireNonNull(names, "helpText is null");
      if (!help.isEmpty()) {
        throw new IllegalStateException("option already has an help text");
      }
      return new Flag(names, toValue, helpText, nestedSchema);
    }

    @Override
    public Flag nestedSchema(Schema<?> nestedSchema) {
      requireNonNull(nestedSchema, "nestedSchema is null");
      if (this.nestedSchema != null) {
        throw new IllegalStateException("a nested schema is already set");
      }
      return new Flag(names, toValue, help, nestedSchema);
    }

    /**
     * Returns a new option that converts the argument to a boolean value.
     *
     * @param mapper the function to apply to do the conversion.
     * @return a new option that converts the argument to a boolean value.
     */
    public Flag convert(UnaryOperator<Boolean> mapper) {
      requireNonNull(mapper, "mapper is null");
      return new Flag(names, v -> mapper.apply(toValue.apply(v)), help, nestedSchema);
    }

    @Override
    public Flag defaultValue(Boolean value) {
      requireNonNull(value, "value is null");
      return convert(v -> !v && value);
    }
  }

  /**
   * An optional key/value option.
   */
  final class Single<T> extends AbstractOption<Optional<T>> {
    final Function<? super Optional<String>, ? extends Optional<T>> toValue;

    /**
     * Create a single option with several names, a conversion function, a help text and a nested schema
     *
     * @param names names of the option.
     * @param toValue a conversion function.
     * @param help a help text.
     * @param nestedSchema a nested schema or null.
     * @throws IllegalArgumentException if a name is empty
     *
     * @see #single(String...)
     */
    public Single(Set<String> names, Function<? super Optional<String>, ? extends Optional<T>> toValue, String help, Schema<?> nestedSchema) {
      super(Type.SINGLE, names, help, nestedSchema);
      this.toValue = requireNonNull(toValue, "toValue is null");
    }

    @Override
    public Single<T> help(String helpText) {
      requireNonNull(names, "helpText is null");
      if (!help.isEmpty()) {
        throw new IllegalStateException("option already has an help text");
      }
      return new Single<>(names, toValue, helpText, nestedSchema);
    }

    @Override
    public Single<T> nestedSchema(Schema<?> nestedSchema) {
      requireNonNull(nestedSchema, "nestedSchema is null");
      if (this.nestedSchema != null) {
        throw new IllegalStateException("a nested schema is already set");
      }
      return new Single<>(names, toValue, help, nestedSchema);
    }

    /**
     * Returns a new option that converts the argument if it exists to another value.
     *
     * @param mapper the function to apply to do the conversion.
     * @return a new option that converts the argument if it exists to another value.
     */
    public <U> Single<U> convert(Function<? super T, ? extends U> mapper) {
      requireNonNull(mapper, "mapper is null");
      return new Single<>(names, toValue.andThen(v -> v.map(mapper)), help, nestedSchema);
    }

    @Override
    public Single<T> defaultValue(Optional<T> value) {
      requireNonNull(value, "value is null");
      return new Single<>(names, toValue.andThen(v -> v.or(() -> value)), help, nestedSchema);
    }
  }

  /**
   * An optional repeatable key/value option.
   *
   * @param <T> the type of the argument corresponding to that option.
   */
  final class Repeatable<T> extends AbstractOption<List<T>> {
    final Function<? super List<String>, ? extends List<T>> toValue;

    /**
     * Create a repeatable option with several names, a conversion function, a help text and a nested schema
     *
     * @param names names of the option.
     * @param toValue a conversion function.
     * @param help a help text.
     * @param nestedSchema a nested schema or null.
     * @throws IllegalArgumentException if a name is empty
     *
     * @see #repeatable(String...)
     */
    public Repeatable(Set<String> names, Function<? super List<String>, ? extends List<T>> toValue, String help, Schema<?> nestedSchema) {
      super(Type.REPEATABLE, names, help, nestedSchema);
      this.toValue = requireNonNull(toValue, "toValue is null");
    }

    @Override
    public Repeatable<T> help(String helpText) {
      requireNonNull(names, "helpText is null");
      if (!help.isEmpty()) {
        throw new IllegalStateException("option already has an help text");
      }
      return new Repeatable<>(names, toValue, helpText, nestedSchema);
    }

    @Override
    public Repeatable<T> nestedSchema(Schema<?> nestedSchema) {
      requireNonNull(nestedSchema, "nestedSchema is null");
      if (this.nestedSchema != null) {
        throw new IllegalStateException("a nested schema is already set");
      }
      return new Repeatable<>(names, toValue, help, nestedSchema);
    }

    /**
     * Returns a new option that converts each argument to another value.
     *
     * @param mapper the function to apply to do the conversion.
     * @return a new option that converts each argument to another value.
     */
    public <U> Repeatable<U> convert(Function<? super T, ? extends U> mapper) {
      requireNonNull(mapper, "mapper is null");
      return new Repeatable<>(names, toValue.andThen(list -> list.stream().<U>map(mapper).toList()), help, nestedSchema);
    }

    @Override
    public Repeatable<T> defaultValue(List<T> value) {
      requireNonNull(value, "value is null");
      return new Repeatable<>(names, toValue.andThen(list -> list.isEmpty()? value: list), help, nestedSchema);
    }
  }

  /**
   * A required positional option.
   *
   * @param <T> the type of the argument corresponding to that option.
   */
  final class Required<T> extends AbstractOption<T> {
    final Function<? super String, ? extends T> toValue;

    /**
     * Create a required option with several names, a conversion function, a help text and a nested schema
     *
     * @param names names of the option.
     * @param toValue a conversion function.
     * @param help a help text.
     * @param nestedSchema a nested schema or null.
     * @throws IllegalArgumentException if a name is empty
     *
     * @see #required(String...)
     */
    public Required(Set<String> names, Function<? super String, ? extends T> toValue, String help, Schema<?> nestedSchema) {
      super(Type.REQUIRED, names, help, nestedSchema);
      this.toValue = requireNonNull(toValue, "toValue is null");
    }

    @Override
    public Required<T> help(String helpText) {
      requireNonNull(names, "helpText is null");
      if (!help.isEmpty()) {
        throw new IllegalStateException("option already has an help text");
      }
      return new Required<>(names, toValue, helpText, nestedSchema);
    }

    @Override
    public Required<T> nestedSchema(Schema<?> nestedSchema) {
      requireNonNull(nestedSchema, "nestedSchema is null");
      if (this.nestedSchema != null) {
        throw new IllegalStateException("a nested schema is already set");
      }
      return new Required<>(names, toValue, help, nestedSchema);
    }

    /**
     * Returns a new option that converts the argument to another value.
     *
     * @param mapper the function to apply to do the conversion.
     * @return a new option that converts the argument to another value.
     */
    public <U> Required<U> convert(Function<? super T, ? extends U> mapper) {
      requireNonNull(mapper, "mapper is null");
      return new Required<>(names, toValue.andThen(mapper), help, nestedSchema);
    }

    @Override
    public Required<T> defaultValue(T value) {
      return convert(v -> v == null ? value : v);
    }
  }

  /**
   * An option corresponding to the rest of the positional arguments..
   *
   * @param <T> the type of the argument corresponding to that option.
   */
  final class Varargs<T> extends AbstractOption<T[]> {
    final Function<? super String[], ? extends T[]> toValue;

    /**
     * Create a varargs option with several names, a conversion function, a help text and a nested schema
     *
     * @param names names of the option.
     * @param toValue a conversion function.
     * @param help a help text.
     * @param nestedSchema a nested schema or null.
     * @throws IllegalArgumentException if a name is empty
     *
     * @see #varargs(String...)
     */
    public Varargs(Set<String> names, Function<? super String[], ? extends T[]> toValue, String help, Schema<?> nestedSchema) {
      super(Type.VARARGS, names, help, nestedSchema);
      this.toValue = requireNonNull(toValue, "toValue is null");
      requireNonNull(help, "help null");
      names = NameSet.copyOf(names);
    }

    @Override
    public Varargs<T> help(String helpText) {
      requireNonNull(names, "helpText is null");
      if (!help.isEmpty()) {
        throw new IllegalStateException("option already has an help text");
      }
      return new Varargs<>(names, toValue, helpText, nestedSchema);
    }

    @Override
    public Varargs<T> nestedSchema(Schema<?> nestedSchema) {
      requireNonNull(nestedSchema, "nestedSchema is null");
      if (this.nestedSchema != null) {
        throw new IllegalStateException("a nested schema is already set");
      }
      return new Varargs<>(names, toValue, help, nestedSchema);
    }

    /**
     * Returns a new option that converts each argument to another value.
     *
     * @param mapper the function to apply to do the conversion.
     * @param generator an array generator
     * @return a new option that converts each argument to another value.
     */
    public <U> Varargs<U> convert(Function<? super T, ? extends U> mapper, IntFunction<U[]> generator) {
      requireNonNull(mapper, "mapper is null");
      return new Varargs<>(names, toValue.andThen(v -> Arrays.stream(v).map(mapper).toArray(generator)), help, nestedSchema);
    }

    @Override
    public Varargs<T> defaultValue(T[] value) {
      requireNonNull(value, "value is null");
      return new Varargs<>(names, toValue.andThen(v -> v.length == 0 ? value : v), help, nestedSchema);
    }
  }

  /**
   * Returns the type of the option.
   * @return the type of the option.
   */
  Type type();

  /**
   * Returns the names of the options.
   * @return the named of the options.
   */
  Set<String> names();

  /**
   * Returns the text of the help message.
   * @return the text of the help message or an empty string if no text is set.
   */
  String help();

  /**
   * Returns the nested schema if it exists.
   * @return the nested schema or {@code null} otherwise.
   */
  Schema<?> nestedSchema();

  /**
   * Creates an option of type {@link Type#BRANCH} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#BRANCH} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static <T extends Record> Branch<T> branch(String... names) {
    return new Branch<>(NameSet.of(names), value -> value, "", null);
  }

  /**
   * Creates an option of type {@link Type#FLAG} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#FLAG} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Flag flag(String... names) {
    return new Flag(NameSet.of(names), value -> value, "", null);
  }

  /**
   * Creates an option of type {@link Type#SINGLE} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#SINGLE} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Single<String> single(String... names) {
    return new Single<>(NameSet.of(names), value -> value, "", null);
  }

  /**
   * Creates an option of type {@link Type#REPEATABLE} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#REPEATABLE} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Repeatable<String> repeatable(String... names) {
    return new Repeatable<>(NameSet.of(names), value -> value, "", null);
  }

  /**
   * Creates an option of type {@link Type#REQUIRED} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#REQUIRED} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Required<String> required(String... names) {
    return new Required<>(NameSet.of(names), value -> value, "", null);
  }

  /**
   * Creates an option of type {@link Type#VARARGS} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#VARARGS} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Varargs<String> varargs(String... names) {
    return new Varargs<>(NameSet.of(names), value -> value, "", null);
  }

  /**
   * Returns a string representation of the option.
   * @return a string representation of the option using its {@link #type()} and its {@link #names()}.
   */
  @Override
  String toString();

  /**
   * Returns a new option configured with a default value.
   *
   * @param value the default value
   * @return a new option configured with a default value.
   */
  Option<T> defaultValue(T value);

  /**
   * Returns a new option with the help text.
   *
   * @param helpText the help text of the new option.
   * @return a new option with the help text.
   * @throws IllegalStateException if the option already has a help text set.
   */
  Option<T> help(String helpText);

  /**
   * Returns a new option with the nested schema.
   *
   * @param nestedSchema the nested schema of the new option.
   * @return a new option with the nested schema.
   * @throws IllegalStateException if the option already has a nested schema set.
   */
  Option<?> nestedSchema(Schema<?> nestedSchema);

  /**
   * Returns the value of the argument associated with the current option.
   *
   * @param argumentMap an argument map produced by {@link Splitter#split(Stream)}.
   * @return the value of the argument associated with the current option.
   * @throws IllegalStateException if there is no argument value associated with the current option
   * in the {@link ArgumentMap}.
   */
  default T argument(ArgumentMap argumentMap) {
    requireNonNull(argumentMap, "dataMap is null");
    return argumentMap.argument(this);
  }
}
