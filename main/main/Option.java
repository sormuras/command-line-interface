package main;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
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
  private final Set<String> names;
  private final Function<Object, T> toValue;
  private final String help;
  private final Schema<?> nestedSchema;

  public Option(Type type, Set<String> names,  Function<Object, T> toValue, String help, Schema<?> nestedSchema) {
    requireNonNull(type, "type is null");
    requireNonNull(names, "names is null");
    requireNonNull(toValue, "toValue is null");
    requireNonNull(help, "help is null");
    names = Collections.unmodifiableSet(new LinkedHashSet<>(names));
    if (names.isEmpty()) throw new IllegalArgumentException("no name defined");
    this.type = type;
    this.names = names;
    this.toValue = toValue;
    this.help = help;
    this.nestedSchema = nestedSchema;
  }

  public Type type() {
    return type;
  }

  public Set<String> names() {
    return names;
  }

  public String help() {
    return help;
  }

  public Schema<?> nestedSchema() {
    return nestedSchema;
  }

  public static Option<Boolean> ofFlag(String... names) {
    return new Option<>(Type.FLAG, checkDuplicates(names), object -> (Boolean) object, "", null);
  }

  @SuppressWarnings("unchecked")
  public static Option<Optional<String>> ofSingle(String... names) {
    return new Option<>(Type.SINGLE, checkDuplicates(names), object -> (Optional<String>) object, "", null);
  }

  public static Option<String> ofRequired(String... names) {
    return new Option<>(Type.REQUIRED, checkDuplicates(names), object -> (String) object, "", null);
  }

  @SuppressWarnings("unchecked")
  public static Option<List<String>> ofRepeatable(String... names) {
    return new Option<>(Type.REPEATABLE, checkDuplicates(names), object -> (List<String>) object, "", null);
  }

  public static Option<String[]> ofVarargs(String... names) {
    return new Option<>(Type.VARARGS, checkDuplicates(names), object -> (String[]) object, "", null);
  }

  private static Set<String> checkDuplicates(String... names) {
    requireNonNull(names, "names is null");
    var set = new LinkedHashSet<String>();
    for(var name: names) {
      if (!set.add(name)) {
        throw new IllegalArgumentException("duplicate names " + name);
      }
    }
    return set;
  }

  // the return type must be an equivalent type (Boolean -> Boolean, Optional -> Optional, etc)
  public <U> Option<U> map(Function<? super T, ? extends U> mapper) {
    requireNonNull(mapper, "mapper is null");
    return new Option<>(type, names, toValue.andThen(v -> requireEquivalentType(mapper.apply(v))), help, nestedSchema);
  }

  @SuppressWarnings("unchecked")
  private <V> V requireEquivalentType(V value) {
    Objects.requireNonNull(value, "value is null");
    return (V) switch (type) {
      case BRANCH -> (Record) value;
      case FLAG -> (Boolean) value;
      case SINGLE -> (Optional<?>) value;
      case REPEATABLE -> (List<?>) value;
      case REQUIRED -> value;
      case VARARGS -> (Object[]) value;
    };
  }

  public Option<T> withHelp(String helpText) {
    requireNonNull(names, "helpText is null");
    if (!help.isEmpty()) {
      throw new IllegalStateException("option already has an help text");
    }
    return new Option<>(type, names, toValue, helpText, nestedSchema);
  }

  public T argument(ArgumentMap argumentMap) {
    requireNonNull(argumentMap, "dataMap is null");
    return argumentMap.argument(this);
  }

  T defaultValue() {
    // a required option should not trigger a call to toValue()
    var value = type.defaultValue;
    return value == null? null: toValue.apply(value);
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
