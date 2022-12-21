package main;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
 * <p>
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
public class Option<T> {
  /**
   * Type of {@link Option}.
   */
  public enum Type {
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

  private final Type type;
  private final LinkedHashSet<String> names;
  private final Function<Object, T> toValue;
  private final String help;
  private final Schema<?> nestedSchema;

  private Option(Type type, LinkedHashSet<String> names,  Function<Object, T> toValue, String help, Schema<?> nestedSchema) {
    requireNonNull(type, "type is null");
    requireNonNull(names, "names is null");
    requireNonNull(toValue, "toValue is null");
    requireNonNull(help, "help is null");
    if (names.isEmpty()) throw new IllegalArgumentException("no name defined");
    this.type = type;
    this.names = names;
    this.toValue = toValue;
    this.help = help;
    this.nestedSchema = nestedSchema;
  }

  Option(Type type, String[] names,  Function<Object, T> toValue, String help, Schema<?> nestedSchema) {
    this(type, checkDuplicates(names), toValue, help, nestedSchema);
  }

  private static LinkedHashSet<String> checkDuplicates(String... names) {
    requireNonNull(names, "names is null");
    var set = new LinkedHashSet<String>();
    for(var name: names) {
      requireNonNull(name, "one name is null");
      if (name.isEmpty()) {
        throw new IllegalArgumentException("one name is empty");
      }
      if (!set.add(name)) {
        throw new IllegalArgumentException("duplicate names " + name);
      }
    }
    return set;
  }

  /**
   * Returns the type of the option.
   * @return the type of the option.
   */
  public Type type() {
    return type;
  }

  /**
   * Returns the names of the options.
   * @return the named of the options.
   */
  public Set<String> names() {
    return Collections.unmodifiableSet(names);
  }

  /**
   * Returns the text of the help message.
   * @return the text of the help message or an empty string if no text is set.
   */
  public String help() {
    return help;
  }

  /**
   * Returns the nested schema if it exists.
   * @return the nested schema or {@code null} otherwise.
   */
  public Schema<?> nestedSchema() {
    return nestedSchema;
  }

  /**
   * Creates an option of type {@link Type#FLAG} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#FLAG} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  public static Option<Boolean> flag(String... names) {
    return new Option<>(Type.FLAG, names, object -> (Boolean) object, "", null);
  }

  /**
   * Creates an option of type {@link Type#SINGLE} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#SINGLE} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  @SuppressWarnings("unchecked")
  public static Option<Optional<String>> single(String... names) {
    return new Option<>(Type.SINGLE, names, object -> (Optional<String>) object, "", null);
  }

  /**
   * Creates an option of type {@link Type#REQUIRED} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#REQUIRED} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  public static Option<String> required(String... names) {
    return new Option<>(Type.REQUIRED, names, object -> (String) object, "", null);
  }

  /**
   * Creates an option of type {@link Type#REPEATABLE} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#REPEATABLE} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  @SuppressWarnings("unchecked")
  public static Option<List<String>> repeatable(String... names) {
    return new Option<>(Type.REPEATABLE, names, object -> (List<String>) object, "", null);
  }

  /**
   * Creates an option of type {@link Type#VARARGS} with names.
   *
   * @param names the names of the options.
   * @return a new {@link Type#VARARGS} option.
   * @throws IllegalArgumentException is there are duplicated names.
   */
  public static Option<String[]> varargs(String... names) {
    return new Option<>(Type.VARARGS, names, object -> (String[]) object, "", null);
  }

  /**
   * Returns a string representation of the option.
   * @return a string representation of the option using its {@link #type()} and its {@link #names()}.
   */
  @Override
  public String toString() {
    return type + names.toString();
  }

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
  public <U> Option<U> map(Function<? super T, ? extends U> conversion) {
    requireNonNull(conversion, "conversion is null");
    return new Option<>(type, names, toValue.andThen(conversion), help, nestedSchema);
  }

  /**
   * Returns a new option with the help text.
   *
   * @param helpText the help text of the new option.
   * @return a new option with the help text.
   * @throws IllegalStateException if the option already has a help text set.
   */
  public Option<T> help(String helpText) {
    requireNonNull(names, "helpText is null");
    if (!help.isEmpty()) {
      throw new IllegalStateException("option already has an help text");
    }
    return new Option<>(type, names, toValue, helpText, nestedSchema);
  }

  /**
   * Returns a new option with the nested schema.
   *
   * @param nestedSchema the nested schema of the new option.
   * @return a new option with the nested schema.
   * @throws IllegalStateException if the option already has a nested schema set.
   */
  public Option<?> nestedSchema(Schema<?> nestedSchema) {
    requireNonNull(nestedSchema, "nestedSchema is null");
    if (this.nestedSchema != null) {
      throw new IllegalStateException("a nested schema is already set");
    }
    return new Option<>(type, names, toValue, help, nestedSchema);
  }

  /**
   * Returns the value of the argument associated with the current option.
   *
   * @param argumentMap an argument map produced by {@link Splitter#split(Stream)}.
   * @return the value of the argument associated with the current option.
   * @throws IllegalStateException if there is no argument value associated with the current option
   * in the {@link ArgumentMap}.
   */
  public T argument(ArgumentMap argumentMap) {
    requireNonNull(argumentMap, "dataMap is null");
    return argumentMap.argument(this);
  }

  @SuppressWarnings("unchecked")
  T defaultValue() {
    return (T) type.defaultValue;
  }

  T apply(Object arg) {
    return toValue.apply(arg);
  }

  String name() {
    return names.iterator().next();
  }

  boolean isVarargs() {
    return type == Type.VARARGS;
  }

  boolean isRequired() {
    return type == Type.REQUIRED;
  }

  boolean isFlag() {
    return type == Type.FLAG;
  }

  boolean isPositional() {
    return type == Type.VARARGS || type == Type.REQUIRED;
  }
}
