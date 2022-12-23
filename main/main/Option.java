package main;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public record Option<T>(Type type, List<String> names, Class<T> valueType, Function<String, T> toValue, Consumer<List<T>> target, Command<?> subCommand) {
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
    if (names.isEmpty()) throw new IllegalArgumentException("no name defined");
  }

  public static Option<Boolean> ofFlag(Consumer<Boolean> target, String... names) {
    return new Option<>(Type.FLAG, List.of(names), Boolean.class, Boolean::valueOf, fromList1(target), null);
  }

  public static Option<String> ofSingle(Consumer<String> target, String... names) {
    return ofSingle(String.class, Function.identity(), target, names);
  }

  public static <T> Option<T> ofSingle(Class<T> valueType, Function<String, T> toValue, Consumer<T> target, String... names) {
    return new Option<>(Type.SINGLE, List.of(names), valueType, toValue, fromList1(target), null);
  }

  public static Option<String> ofRequired(Consumer<String> target, String... names) {
    return ofRequired(String.class, Function.identity(), target, names);
  }
  public static <T> Option<T> ofRequired(Class<T> valueType, Function<String, T> toValue, Consumer<T> target, String... names) {
    return new Option<>(Type.REQUIRED, List.of(names), valueType, toValue, fromList1(target), null);
  }

  public static Option<String> ofRepeatable(Consumer<List<String>> target, String... names) {
    return ofRepeatable(String.class, Function.identity(), target, names);
  }
  public static <T> Option<T> ofRepeatable(Class<T> valueType, Function<String, T> toValue, Consumer<List<T>> target, String... names) {
    return new Option<>(Type.REPEATABLE, List.of(names), valueType, toValue, target, null);
  }

  public static Option<String> ofVarargs(Consumer<List<String>> target, String... names) {
    return ofVarargs(String.class, Function.identity(), target, names);
  }
  public static <T> Option<T> ofVarargs(Class<T> valueType, Function<String, T> toValue, Consumer<List<T>> target, String... names) {
    return new Option<>(Type.VARARGS, List.of(names), valueType, toValue, target, null);
  }

  private static <T> Consumer<List<T>> fromList1(Consumer<T> target) {
    return values -> target.accept(values.get(0));
  }

  public T create(String value) {
    return toValue().apply(value);
  }

  public void pack(List<T> values) {
    target.accept(values);
  }

  Object defaultValue() {
    return type() == Type.VARARGS ? (Object[]) Array.newInstance(valueType(), 0) : type().defaultValue;
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
