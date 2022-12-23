package main;

import java.util.List;
import java.util.Optional;

public enum OptionType {
  /**
   * A branch to enter a sub-command, all remaining arguments are expected to belong to the
   * sub-command
   */
  BRANCH,
  /** An optional flag, like {@code --verbose}. */
  FLAG,
  /** An optional key-value pair, like {@code --version 47.11}. */
  SINGLE,
  /** An optional and repeatable key, like {@code --with alpha --with omega} */
  REPEATABLE,
  /** A required positional option */
  REQUIRED,
  /** A collection of all unhandled arguments. */
  VARARGS;

  public static OptionType of(Class<?> valueType) {
    if (valueType.isRecord()) return BRANCH;
    if (valueType == Boolean.class || valueType == boolean.class) return FLAG;
    if (valueType == Optional.class) return SINGLE;
    if (valueType == List.class) return REPEATABLE;
    if (valueType.isArray()) return VARARGS;
    return REQUIRED;
  }

  public boolean isVarargs() {
    return this == OptionType.VARARGS;
  }

  public boolean isRequired() {
    return this == OptionType.REQUIRED;
  }

  public boolean isFlag() {
    return this == OptionType.FLAG;
  }

  public boolean isPositional() {
    return this == OptionType.VARARGS || this == OptionType.REQUIRED;
  }
}
