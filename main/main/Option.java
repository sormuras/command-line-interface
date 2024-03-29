package main;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

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
 * {@link Flag#convert(Converter)}, {@link Single#convert(Converter)}, {@link Repeatable#convert(Converter)},
 * {@link Required#convert(Converter)} and {@link Varargs#convert(Converter, IntFunction)}.
 *
 * <p>Options are grouped into a {@link Schema#Schema(List, java.util.function.Function) schema} that is used to create a
 * {@link Splitter#of(Schema) Splitter} that parses the command line.
 *
 * @param <T> type of the argument(s) described by this Option.
 */
public sealed interface Option<T> permits AbstractOption {

  /**
   * An option that branch to a nested schema.
   *
   * @param <T> the type of the argument corresponding to that option.
   */
  final class Branch<T> extends AbstractOption<T> {
    final Converter<T,T> converter;

    Branch(Set<String> names, Converter<T,T> converter, String help, Schema<?> nestedSchema) {
      super(OptionType.BRANCH, names, help, nestedSchema);
      this.converter = requireNonNull(converter, "converter is null");
      requireNonNull(nestedSchema, "schema is null");
    }

    /**
     * Create a branch option with several names, a conversion function and a nested schema.
     *
     * @param names names of the option.
     * @param converter a conversion function.
     * @param nestedSchema the nested schema
     * @throws IllegalArgumentException if a name is empty or if names are duplicated.
     */
    public Branch(List<String> names, Converter<T,T> converter, Schema<T> nestedSchema) {
      this(NameSet.copyOf(names), converter, "", nestedSchema);
    }

    @Override
    public Branch<T> help(String helpText) {
      requireNonNull(helpText, "helpText is null");
      if (!help.isEmpty()) {
        throw new IllegalStateException("option already has an help text");
      }
      return new Branch<>(names, converter, helpText, nestedSchema);
    }

    /**
     * Returns a new option that converts the argument to a value of the same type.
     *
     * @param mapper the function to apply to do the conversion.
     * @return a new option that converts the argument to a value of the same type.
     */
    public Branch<T> convert(Converter<? super T, ? extends T> mapper) {
      requireNonNull(mapper, "mapper is null");
      return new Branch<>(names, converter.andThen(mapper), help, nestedSchema);
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
    final Converter<Boolean, Boolean> converter;

    Flag(Set<String> names, Converter<Boolean, Boolean> converter, String help) {
      super(OptionType.FLAG, names, help, null);
      this.converter = requireNonNull(converter, "converter is null");
    }

    /**
     * Create a flag option with several names, a conversion function, a help text and a nested schema
     *
     * @param names names of the option.
     * @param converter a conversion function.
     * @throws IllegalArgumentException if a name is empty or if names are duplicated.
     *
     * @see #flag(String...)
     */
    public Flag(List<String> names, Converter<Boolean,Boolean> converter) {
      this(NameSet.copyOf(names), converter, "");
    }

    @Override
    public Flag help(String helpText) {
      requireNonNull(names, "helpText is null");
      if (!help.isEmpty()) {
        throw new IllegalStateException("option already has an help text");
      }
      return new Flag(names, converter, helpText);
    }

    /**
     * Returns a new option that converts the argument to a boolean value.
     *
     * @param mapper the function to apply to do the conversion.
     * @return a new option that converts the argument to a boolean value.
     */
    public Flag convert(Converter<Boolean,Boolean> mapper) {
      requireNonNull(mapper, "mapper is null");
      return new Flag(names, converter.andThen(mapper), help);
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
    final Converter<Optional<?>, ? extends Optional<T>> converter;

    Single(Set<String> names, Converter<Optional<?>, ? extends Optional<T>> converter, String help, Schema<?> nestedSchema) {
      super(OptionType.SINGLE, names, help, nestedSchema);
      this.converter = requireNonNull(converter, "converter is null");
    }

    /**
     * Create a single option with several names, a conversion function, a help text and a nested schema
     *
     * @param names names of the option.
     * @param converter a conversion function.
     * @throws IllegalArgumentException if a name is empty or if names are duplicated.
     *
     * @see #single(String...)
     */
    @SuppressWarnings("unchecked")
    public Single(List<String> names, Converter<? super Optional<String>, ? extends Optional<T>> converter) {
      this(NameSet.copyOf(names), (Converter<Optional<?>, ? extends Optional<T>>) converter, "", null);
    }

    @Override
    public Single<T> help(String helpText) {
      requireNonNull(names, "helpText is null");
      if (!help.isEmpty()) {
        throw new IllegalStateException("option already has an help text");
      }
      return new Single<>(names, converter, helpText, nestedSchema);
    }

    /**
     * Returns a new option with the nested schema.
     * The newly created option has no defined conversion.
     *
     * @param nestedSchema the nested schema of the new option.
     * @return a new option with the nested schema.
     * @throws IllegalStateException if the option already has a nested schema set.
     */
    @SuppressWarnings("unchecked")
    public <U> Single<U> nestedSchema(Schema<U> nestedSchema) {
      requireNonNull(nestedSchema, "nestedSchema is null");
      if (this.nestedSchema != null) {
        throw new IllegalStateException("a nested schema is already set");
      }
      return new Single<>(names, opt -> (Optional<U>) opt, help, nestedSchema);
    }

    /**
     * Returns a new option that converts the argument if it exists to another value.
     *
     * @param mapper the function to apply to do the conversion.
     * @return a new option that converts the argument if it exists to another value.
     */
    public <U> Single<U> convert(Converter<? super T, ? extends U> mapper) {
      requireNonNull(mapper, "mapper is null");
      return new Single<>(names, converter.andThen(v -> v.map(mapper)), help, nestedSchema);
    }

    @Override
    public Single<T> defaultValue(Optional<T> value) {
      requireNonNull(value, "value is null");
      return new Single<>(names, converter.andThen(v -> v.or(() -> value)), help, nestedSchema);
    }
  }

  /**
   * An optional repeatable key/value option.
   *
   * @param <T> the type of the argument corresponding to that option.
   */
  final class Repeatable<T> extends AbstractOption<List<T>> {
    final Converter<List<?>, ? extends List<T>> converter;

    Repeatable(Set<String> names, Converter<List<?>, ? extends List<T>> converter, String help, Schema<?> nestedSchema) {
      super(OptionType.REPEATABLE, names, help, nestedSchema);
      this.converter = requireNonNull(converter, "converter is null");
    }

    /**
     * Create a repeatable option with several names, a conversion function, a help text and a nested schema
     *
     * @param names names of the option.
     * @param converter a conversion function.
     * @throws IllegalArgumentException if a name is empty or if names are duplicated.
     *
     * @see #repeatable(String...)
     */
    @SuppressWarnings("unchecked")
    public Repeatable(List<String> names, Converter<? super List<String>, ? extends List<T>> converter) {
      this(NameSet.copyOf(names), (Converter<List<?>, ? extends List<T>>) converter, "", null);
    }

    @Override
    public Repeatable<T> help(String helpText) {
      requireNonNull(names, "helpText is null");
      if (!help.isEmpty()) {
        throw new IllegalStateException("option already has an help text");
      }
      return new Repeatable<>(names, converter, helpText, nestedSchema);
    }

    /**
     * Returns a new option with the nested schema.
     * The newly created option has no defined conversion.
     *
     * @param nestedSchema the nested schema of the new option.
     * @return a new option with the nested schema.
     * @throws IllegalStateException if the option already has a nested schema set.
     */
    @SuppressWarnings("unchecked")
    public <U> Repeatable<U> nestedSchema(Schema<U> nestedSchema) {
      requireNonNull(nestedSchema, "nestedSchema is null");
      if (this.nestedSchema != null) {
        throw new IllegalStateException("a nested schema is already set");
      }
      return new Repeatable<>(names, x -> (List<U>) x, help, nestedSchema);
    }

    /**
     * Returns a new option that converts each argument to another value.
     *
     * @param mapper the function to apply to do the conversion.
     * @return a new option that converts each argument to another value.
     */
    public <U> Repeatable<U> convert(Converter<? super T, ? extends U> mapper) {
      requireNonNull(mapper, "mapper is null");
      return new Repeatable<>(names, converter.andThen(list -> list.stream().<U>map(mapper).toList()), help, nestedSchema);
    }

    @Override
    public Repeatable<T> defaultValue(List<T> value) {
      requireNonNull(value, "value is null");
      return new Repeatable<>(names, converter.andThen(list -> list.isEmpty()? value: list), help, nestedSchema);
    }
  }

  /**
   * A required positional option.
   *
   * @param <T> the type of the argument corresponding to that option.
   */
  final class Required<T> extends AbstractOption<T> {
    final Converter<? super String, ? extends T> converter;

    Required(Set<String> names, Converter<? super String, ? extends T> converter, String help) {
      super(OptionType.REQUIRED, names, help, null);
      this.converter = requireNonNull(converter, "converter is null");
    }

    /**
     * Create a required option with several names, a conversion function, a help text and a nested schema
     *
     * @param names names of the option.
     * @param converter a conversion function.
     * @throws IllegalArgumentException if a name is empty or if names are duplicated.
     *
     * @see #required(String...)
     */
    public Required(List<String> names, Converter<? super String, ? extends T> converter) {
      this(NameSet.copyOf(names), converter, "");
    }

    @Override
    public Required<T> help(String helpText) {
      requireNonNull(names, "helpText is null");
      if (!help.isEmpty()) {
        throw new IllegalStateException("option already has an help text");
      }
      return new Required<>(names, converter, helpText);
    }

    /**
     * Returns a new option that converts the argument to another value.
     *
     * @param mapper the function to apply to do the conversion.
     * @return a new option that converts the argument to another value.
     */
    public <U> Required<U> convert(Converter<? super T, ? extends U> mapper) {
      requireNonNull(mapper, "mapper is null");
      return new Required<>(names, converter.andThen(mapper), help);
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
    final Converter<? super String[], ? extends T[]> converter;

    Varargs(Set<String> names, Converter<? super String[], ? extends T[]> converter, String help) {
      super(OptionType.VARARGS, names, help, null);
      this.converter = requireNonNull(converter, "converter is null");
    }

    /**
     * Create a varargs option with several names, a conversion function, a help text and a nested schema
     *
     * @param names names of the option.
     * @param converter a conversion function.
     * @throws IllegalArgumentException if a name is empty or if names are duplicated.
     *
     * @see #varargs(String...)
     */
    public Varargs(List<String> names, Converter<? super String[], ? extends T[]> converter) {
      this(NameSet.copyOf(names), converter, "");
    }

    @Override
    public Varargs<T> help(String helpText) {
      requireNonNull(names, "helpText is null");
      if (!help.isEmpty()) {
        throw new IllegalStateException("option already has an help text");
      }
      return new Varargs<>(names, converter, helpText);
    }

    /**
     * Returns a new option that converts each argument to another value.
     *
     * @param mapper the function to apply to do the conversion.
     * @param generator an array generator
     * @return a new option that converts each argument to another value.
     */
    public <U> Varargs<U> convert(Converter<? super T, ? extends U> mapper, IntFunction<U[]> generator) {
      requireNonNull(mapper, "mapper is null");
      return new Varargs<>(names, converter.andThen(v -> Arrays.stream(v).map(mapper).toArray(generator)), help);
    }

    @Override
    public Varargs<T> defaultValue(T[] value) {
      requireNonNull(value, "value is null");
      return new Varargs<>(names, converter.andThen(v -> v.length == 0 ? value : v), help);
    }
  }

  /**
   * Returns the type of the option.
   * @return the type of the option.
   */
  OptionType type();

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
   * Creates an option of type {@link OptionType#FLAG} with names.
   *
   * @param names the names of the options.
   * @return a new {@link OptionType#FLAG} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Flag flag(String... names) {
    return new Flag(NameSet.of(names), value -> value, "");
  }

  /**
   * Creates an option of type {@link OptionType#SINGLE} with names.
   *
   * @param names the names of the options.
   * @return a new {@link OptionType#SINGLE} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  @SuppressWarnings("unchecked")
  static Single<String> single(String... names) {
    return new Single<>(NameSet.of(names), value -> (Optional<String>) value, "", null);
  }

  /**
   * Creates an option of type {@link OptionType#REPEATABLE} with names.
   *
   * @param names the names of the options.
   * @return a new {@link OptionType#REPEATABLE} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  @SuppressWarnings("unchecked")
  static Repeatable<String> repeatable(String... names) {
    return new Repeatable<>(NameSet.of(names), value -> (List<String>) value, "", null);
  }

  /**
   * Creates an option of type {@link OptionType#REQUIRED} with names.
   *
   * @param names the names of the options.
   * @return a new {@link OptionType#REQUIRED} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Required<String> required(String... names) {
    return new Required<>(NameSet.of(names), value -> value, "");
  }

  /**
   * Creates an option of type {@link OptionType#VARARGS} with names.
   *
   * @param names the names of the options.
   * @return a new {@link OptionType#VARARGS} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Varargs<String> varargs(String... names) {
    return new Varargs<>(NameSet.of(names), value -> value, "");
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
}
