package main;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class Option<T> {
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

  public Type type() {
    return type;
  }

  public Set<String> names() {
    return Collections.unmodifiableSet(names);
  }

  public String help() {
    return help;
  }

  public Schema<?> nestedSchema() {
    return nestedSchema;
  }

  public static Option<Boolean> flag(String... names) {
    return new Option<>(Type.FLAG, names, object -> (Boolean) object, "", null);
  }

  @SuppressWarnings("unchecked")
  public static Option<Optional<String>> single(String... names) {
    return new Option<>(Type.SINGLE, names, object -> (Optional<String>) object, "", null);
  }

  public static Option<String> required(String... names) {
    return new Option<>(Type.REQUIRED, names, object -> (String) object, "", null);
  }

  @SuppressWarnings("unchecked")
  public static Option<List<String>> repeatable(String... names) {
    return new Option<>(Type.REPEATABLE, names, object -> (List<String>) object, "", null);
  }

  public static Option<String[]> varargs(String... names) {
    return new Option<>(Type.VARARGS, names, object -> (String[]) object, "", null);
  }

  @Override
  public String toString() {
    return type + names.toString();
  }

  // the return type must be an equivalent type (Boolean -> Boolean, Optional -> Optional, etc)
  public <U> Option<U> map(Function<? super T, ? extends U> mapper) {
    requireNonNull(mapper, "mapper is null");
    return new Option<>(type, names, toValue.andThen(mapper), help, nestedSchema);
  }

  public Option<T> help(String helpText) {
    requireNonNull(names, "helpText is null");
    if (!help.isEmpty()) {
      throw new IllegalStateException("option already has an help text");
    }
    return new Option<>(type, names, toValue, helpText, nestedSchema);
  }

  public Option<?> nestedSchema(Schema<?> nestedSchema) {
    requireNonNull(nestedSchema, "nestedSchema is null");
    if (this.nestedSchema != null) {
      throw new IllegalStateException("a nested schema is already specified");
    }
    return new Option<>(type, names, toValue, help, nestedSchema);
  }

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
