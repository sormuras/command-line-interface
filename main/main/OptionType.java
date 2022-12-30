package main;

import java.util.List;
import java.util.Optional;

/** Type of {@link Option}. */
public enum OptionType {
  BRANCH(null),
  /** An optional flag, like {@code --verbose}, {@code quiet=true} or {@code quiet=false}. */
  FLAG(false),
  /** An optional key-value pair, like {@code --version 47.11} or {@code version=47.11}. */
  SINGLE(Optional.empty()),
  /**
   * An optional and repeatable key, like {@code --with alpha --with omega} or {@code
   * --with=alpha,omega}
   */
  REPEATABLE(List.of()),
  /** A required positional option */
  REQUIRED(null),
  /** An array of all unhandled arguments. */
  VARARGS(new String[0]);

  final Object defaultValue;

  OptionType(Object defaultValue) {
    this.defaultValue = defaultValue;
  }
}
