package main;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public record Option<T>(Type type, Set<String> names, Class<T> valueType, Function<String, T> toValue, String help, Command<?> subCommand) {
  public enum Type {
    BRANCH(null),
    /** An optional flag, like {@code --verbose}. */
    FLAG(false),
    /** An optional key-value pair, like {@code --version 47.11}. */
    SINGLE(Optional.empty()),
    /** An optional and repeatable key, like {@code --with alpha --with omega} */
    REPEATABLE(List.of()),
    /** A required positional option */
    REQUIRED(""),
    /** A collection of all unhandled arguments. */
    VARARGS(new String[0]);

    private final Object defaultValue;

    Type(Object defaultValue) {
      this.defaultValue = defaultValue;
    }
  }

  public Option {
    requireNonNull(type, "type is null");
    requireNonNull(names, "names is null");
    requireNonNull(valueType, "valueType is null");
    requireNonNull(toValue, "toValue is null");
    requireNonNull(help, "help is null");
    names = Collections.unmodifiableSet(new LinkedHashSet<>(names));
    if (names.isEmpty()) throw new IllegalArgumentException("no name defined");
  }

  public static Option<String> of(Type type, String... names) {
    return new Option<>(type, checkDuplicates(names), String.class, Function.identity(), "", null);
  }

  public static Option<Boolean> ofFlag(String... names) {
    return new Option<>(Type.FLAG, checkDuplicates(names), Boolean.class, Boolean::valueOf, "", null);
  }

  public static Option<String> ofSingle(String... names) {
    return ofSingle(String.class, Function.identity(), names);
  }
  public static <T> Option<T> ofSingle(Class<T> valueType, Function<String, T> toValue, String... names) {
    return new Option<>(Type.SINGLE, checkDuplicates(names), valueType, toValue, "", null);
  }

  public static Option<String> ofRequired(String... names) {
    return ofRequired(String.class, Function.identity(), names);
  }
  public static <T> Option<T> ofRequired(Class<T> valueType, Function<String, T> toValue, String... names) {
    return new Option<>(Type.REQUIRED, checkDuplicates(names), valueType, toValue, "", null);
  }

  public static Option<String> ofRepeatable(String... names) {
    return ofRepeatable(String.class, Function.identity(), names);
  }
  public static <T> Option<T> ofRepeatable(Class<T> valueType, Function<String, T> toValue, String... names) {
    return new Option<>(Type.REPEATABLE, checkDuplicates(names), valueType, toValue, "", null);
  }

  public static Option<String> ofVarargs(String... names) {
    return ofVarargs(String.class, Function.identity(), names);
  }
  public static <T> Option<T> ofVarargs(Class<T> valueType, Function<String, T> toValue, String... names) {
    return new Option<>(Type.VARARGS, checkDuplicates(names), valueType, toValue, "", null);
  }

  public T create(String value) {
    return toValue().apply(value);
  }

  Object defaultValue() {
    return type() == Type.VARARGS ? (Object[]) Array.newInstance(valueType(), 0) : type().defaultValue;
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

  public Option<?> withHelp(String helpText) {
    requireNonNull(names, "helpText is null");
    if (!help.isEmpty()) {
      throw new IllegalStateException("option already has an help text");
    }
    return new Option<>(type, names, valueType, toValue, helpText, subCommand);
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
