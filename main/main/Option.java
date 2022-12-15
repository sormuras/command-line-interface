package main;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record Option(Type type, Set<String> names, String help, Schema<?> nestedSchema) {
  public enum Type {
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

    Object defaultValue() {
      return defaultValue;
    }

    static Type valueOf(Class<?> type) {
      if (type == Boolean.class || type == boolean.class) return FLAG;
      if (type == Optional.class) return SINGLE;
      if (type == List.class) return REPEATABLE;
      if (type == String.class) return REQUIRED;
      if (type == String[].class) return VARARGS;
      throw new IllegalArgumentException("Unsupported value type: " + type);
    }

    public Option option(String... names) {
      return new Option(this, names);
    }
  }

  public Option {
    requireNonNull(type, "type is null");
    requireNonNull(names, "names is null");
    requireNonNull(help, "help is null");
    names = Collections.unmodifiableSet(new LinkedHashSet<>(names));
    if (names.isEmpty()) throw new IllegalArgumentException("no name defined");
  }

  public Option(Type type, String... names) {
    this(type, checkDuplicates(names), "", null);
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

  public Option withHelp(String helpText) {
    requireNonNull(names, "helpText is null");
    if (!help.isEmpty()) {
      throw new IllegalStateException("option already has an help text");
    }
    return new Option(type, names, helpText, nestedSchema);
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
