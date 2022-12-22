package main;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * An Option is a description of one of more arguments of the command line. It is a unitary piece of
 * a {@link Schema}. Option are immutable and can be shared between several schemas.
 *
 * <p>An option have a {@link #type()}, case-sensitive {@link #names()} and optionally a {@link
 * #help() help text}, a {@link #map(Function) conversion function} and {@link #nestedSchema() a
 * nested schema}.
 *
 * <p>There two kinds of options, optional options with three sub-categories {@link
 * Option.Type#FLAG}, {@link Option.Type#SINGLE} and {@link Option.Type#REPEATABLE} and positional
 * options with two sub-categories {@link Option.Type#REQUIRED} and {@link Option.Type#VARARGS}.
 *
 * <p>&nbsp;
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
 * @param <T> type of the argument described by this Option.
 */
public sealed interface Option<T> {
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

    private final Object defaultValue;

    Type(Object defaultValue) {
      this.defaultValue = defaultValue;
    }
  }

  record Branch<T>(List<String> names, UnaryOperator<T> toValue, String help, Schema<T> nestedSchema) implements Option<T> {
    public Branch {
      requireNonNull(names, "names is null");
      requireNonNull(toValue, "toValue is null");
      requireNonNull(help, "help null");
      requireNonNull(nestedSchema, "nestedSchema null");
      names = checkDuplicates(List.copyOf(names));
    }

    @Override
    public String toString() {
      return "BRANCH" + names.toString();
    }

    @Override
    public Branch<T> help(String helpText) {
      requireNonNull(names, "helpText is null");
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

    /*@Override
    @SuppressWarnings("unchecked")
    public <U> Option<U> map(Function<? super T, ? extends U> conversion) {
      requireNonNull(conversion, "conversion is null");
      return new Branch<>(names, v -> conversion.apply(toValue.apply((T) v)), help, (Schema<U>) nestedSchema);
    }*/

    public Branch<T> convert(UnaryOperator<T> mapper) {
      requireNonNull(mapper, "mapper is null");
      return new Branch<>(names, v -> mapper.apply(toValue.apply(v)), help, nestedSchema);
    }

    @Override
    public Branch<T> defaultValue(T value) {
      return convert(v -> v == null? value: v);
    }
  }

  record Flag(List<String> names, UnaryOperator<Boolean> toValue, String help, Schema<?> nestedSchema) implements Option<Boolean> {
    public Flag {
      requireNonNull(names, "names is null");
      requireNonNull(toValue, "toValue is null");
      requireNonNull(help, "help null");
      names = checkDuplicates(List.copyOf(names));
    }

    @Override
    public String toString() {
      return "FLAG" + names.toString();
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

    /*@Override
    @SuppressWarnings("unchecked")
    public <U> Option<U> map(Function<? super Boolean, ? extends U> conversion) {
      requireNonNull(conversion, "conversion is null");
      return (Option<U>) convert(v -> (Boolean) conversion.apply(v));
    }*/

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

  record Single<T>(List<String> names, Function<? super Optional<String>, ? extends Optional<T>> toValue, String help, Schema<?> nestedSchema) implements Option<Optional<T>> {
    public Single {
      requireNonNull(names, "names is null");
      requireNonNull(toValue, "toValue is null");
      requireNonNull(help, "help null");
      names = checkDuplicates(List.copyOf(names));
    }

    @Override
    public String toString() {
      return "SINGLE" + names.toString();
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

    /*@Override
    @SuppressWarnings("unchecked")
    public <U> Option<U> map(Function<? super Optional<T>, ? extends U> conversion) {
      requireNonNull(conversion, "conversion is null");
      return (Option<U>) new Single<>(names, toValue.andThen(v -> (Optional<?>) conversion.apply(v)), help, nestedSchema);
    }*/

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

  record Repeatable<T>(List<String> names, Function<? super List<String>, ? extends List<T>> toValue, String help, Schema<?> nestedSchema) implements Option<List<T>> {
    public Repeatable {
      requireNonNull(names, "names is null");
      requireNonNull(toValue, "toValue is null");
      requireNonNull(help, "help null");
      names = checkDuplicates(List.copyOf(names));
    }

    @Override
    public String toString() {
      return "REPEATABLE" + names.toString();
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

    /*@Override
    @SuppressWarnings("unchecked")
    public <U> Option<U> map(Function<? super List<T>, ? extends U> conversion) {
      requireNonNull(conversion, "conversion is null");
      return (Option<U>) new Repeatable<>(names, toValue.andThen(v -> (List<?>) conversion.apply(v)), help, nestedSchema);
    }*/

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

  record Required<T>(List<String> names, Function<? super String, ? extends T> toValue, String help, Schema<?> nestedSchema) implements Option<T> {
    public Required {
      requireNonNull(names, "names is null");
      requireNonNull(toValue, "toValue is null");
      requireNonNull(help, "help null");
      names = checkDuplicates(List.copyOf(names));
    }

    @Override
    public String toString() {
      return "REQUIRED" + names.toString();
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

    /*@Override
    public <U> Option<U> map(Function<? super T, ? extends U> conversion) {
      requireNonNull(conversion, "conversion is null");
      return convert(conversion);
    }*/

    public <U> Required<U> convert(Function<? super T, ? extends U> mapper) {
      requireNonNull(mapper, "mapper is null");
      return new Required<>(names, toValue.andThen(mapper), help, nestedSchema);
    }

    @Override
    public Required<T> defaultValue(T value) {
      return convert(v -> v == null ? value : v);
    }
  }

  record Varargs<T>(List<String> names, Function<? super String[], ? extends T[]> toValue, String help, Schema<?> nestedSchema) implements Option<T[]> {
    public Varargs {
      requireNonNull(names, "names is null");
      requireNonNull(toValue, "toValue is null");
      requireNonNull(help, "help null");
      names = checkDuplicates(List.copyOf(names));
    }

    @Override
    public String toString() {
      return "VARARGS" + names.toString();
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

    /*@Override
    @SuppressWarnings("unchecked")
    public <U> Option<U> map(Function<? super T[], ? extends U> conversion) {
      requireNonNull(conversion, "conversion is null");
      return (Option<U>) new Varargs<>(names, toValue.andThen(v -> (Object[]) conversion.apply(v)), help, nestedSchema);
    }*/

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

  private static List<String> checkDuplicates(List<String> names) {
    if (names.isEmpty()) {
      throw new IllegalArgumentException("no name defined");
    }
    var set = new LinkedHashSet<String>();
    for(var name: names) {
      if (name.isEmpty()) {
        throw new IllegalArgumentException("one name is empty");
      }
      if (!set.add(name)) {
        throw new IllegalArgumentException("duplicate names " + name);
      }
    }
    return names;
  }

  /**
   * Returns the type of the option.
   * @return the type of the option.
   */
  default Type type() {
    if (this instanceof Branch<?>) {
      return Type.BRANCH;
    }
    if (this instanceof Flag) {
      return Type.FLAG;
    }
    if (this instanceof Single<?>) {
      return Type.SINGLE;
    }
    if (this instanceof Repeatable<?>) {
      return Type.REPEATABLE;
    }
    if (this instanceof Required<?>) {
      return Type.REQUIRED;
    }
    if (this instanceof Option.Varargs<?>) {
      return Type.VARARGS;
    }
    throw new AssertionError();
  }

  /**
   * Returns the names of the options.
   * @return the named of the options.
   */
  List<String> names();

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
    return new Branch<>(Arrays.asList(names), value -> value, "", null);
  }

  /**
   * Creates an option of type {@link Type#FLAG} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#FLAG} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Flag flag(String... names) {
    return new Flag(Arrays.asList(names), value -> value, "", null);
  }

  /**
   * Creates an option of type {@link Type#SINGLE} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#SINGLE} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Single<String> single(String... names) {
    return new Single<>(Arrays.asList(names), value -> value, "", null);
  }

  /**
   * Creates an option of type {@link Type#REPEATABLE} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#REPEATABLE} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Repeatable<String> repeatable(String... names) {
    return new Repeatable<>(Arrays.asList(names), value -> value, "", null);
  }

  /**
   * Creates an option of type {@link Type#REQUIRED} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#REQUIRED} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Required<String> required(String... names) {
    return new Required<>(Arrays.asList(names), value -> value, "", null);
  }

  /**
   * Creates an option of type {@link Type#VARARGS} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#VARARGS} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  static Varargs<String> varargs(String... names) {
    return new Varargs<>(Arrays.asList(names), value -> value, "", null);
  }

  /**
   * Returns a string representation of the option.
   * @return a string representation of the option using its {@link #type()} and its {@link #names()}.
   */
  @Override
  String toString();

  /**
   * Returns a new option configured with the conversion function.
   *
   * <p>The return type of the conversion function must be an equivalent type of the type of the option.
   * If the option is a {@link Type#FLAG}, the return type must be a {@code Boolean},
   * if the option is a {@link Type#SINGLE}, the return type must be any {@code Optional}s,
   * if the option is a {@link Type#REPEATABLE}, the return type must be any {@code List}s,
   * if the option is a {@link Type#REQUIRED}, the return type must be any {@code Object}s,
   * if the option is a {@link Type#VARARGS}, the return type must be any arrays of objects ({@code Object[]}).
   *
   * <p>This restriction is not enforced but may result in {@link ClassCastException} later if not followed.
   *
   * @param conversion a conversion function
   * @return a new option configured with the conversion function.
   * @param <U> type of the return value of the conversion function
   */
  //<U> Option<U> map(Function<? super T, ? extends U> conversion);

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

  // FIXME the following methods are too visible
  @SuppressWarnings("unchecked")
  default T defaultValue() {
    return (T) type().defaultValue;
  }

  @SuppressWarnings("unchecked")
  default T apply(Object arg) {
    if (this instanceof Branch<T> branch) {
      return (T) branch.toValue.apply((T) arg);
    }
    if (this instanceof Flag flag) {
      return (T) flag.toValue.apply((Boolean) arg);
    }
    if (this instanceof Single<?> single) {
      return (T) single.toValue.apply((Optional<String>) arg);
    }
    if (this instanceof Repeatable<?> repeatable) {
      return (T) repeatable.toValue.apply((List<String>) arg);
    }
    if (this instanceof Required<T> required) {
      return required.toValue.apply((String) arg);
    }
    if (this instanceof Option.Varargs<?> varargs) {
      return (T) varargs.toValue.apply((String[]) arg);
    }
    throw new AssertionError();
  }

  default String name() {
    return names().iterator().next();
  }

  default boolean isVarargs() {
    return type() == Type.VARARGS;
  }

  default boolean isRequired() {
    return type() == Type.REQUIRED;
  }

  default boolean isFlag() {
    return type() == Type.FLAG;
  }

  default boolean isPositional() {
    return type() == Type.VARARGS || type() == Type.REQUIRED;
  }

  static Option<?> newOption(Type type, String[] names, Function<Object, ?> converter, String help, Schema<?> schema) {
    var nameList = Arrays.asList(names);
    return switch (type) {
      case BRANCH -> newBranch(nameList, converter, help, schema);
      case FLAG -> new Flag(nameList, v -> (Boolean) converter.apply(v), help, schema);
      case SINGLE -> new Single<>(nameList, v -> (Optional<?>) converter.apply(v), help, schema);
      case REPEATABLE -> new Repeatable<>(nameList, v -> (List<?>) converter.apply(v), help, schema);
      case REQUIRED -> new Required<>(nameList, converter, help, schema);
      case VARARGS -> new Varargs<>(nameList, v -> (Object[]) converter.apply(v), help, schema);
    };
  }

  @SuppressWarnings("unchecked")  // need to capture the Schema type
  private static <T> Branch<T> newBranch(List<String> nameList, Function<Object, ?> converter, String help, Schema<T> schema) {
    return new Branch<>(nameList, v -> (T) converter.apply(v), help, schema);
  }
}
