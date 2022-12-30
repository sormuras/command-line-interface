package main;

import java.util.Comparator;
import java.util.StringJoiner;

import static java.util.Objects.requireNonNull;

/**
 * Helper class that generate the help associated to a command line arguments.
 */
public class Manual {
  private Manual() {
    throw new AssertionError();
  }

  /**
   * Generate the help of a command line application.
   * This is a convenient method equivalent to
   * <pre>
   *   help(schema, 2)
   * </pre>
   *
   * @param schema the schema of the command line.
   * @return a string describing each argument of the command line.
   */
  public static String help(Schema<?> schema) {
    return help(schema, 2);
  }

  /**
   * Generate the help of a command line application.
   *
   * @param schema the schema of the command line.
   * @param indent the number of spaces when indenting.
   * @return a string describing each argument of the command line.
   */
  public static String help(Schema<?> schema, int indent) {
    requireNonNull(schema, "schema is null");
    if (indent < 0) throw new IllegalArgumentException("invalid indent " + indent);
    var joiner = new StringJoiner("\n");
    for (var option : schema.options.stream().sorted(Comparator.comparing(AbstractOption::name)).toList()) {
      var text = option.help();
      if (text.isEmpty()) continue;
      var suffix =
          switch (option.type()) {
            case BRANCH -> " (branch)";
            case FLAG -> " (flag)";
            case SINGLE -> " <value>";
            case REPEATABLE -> " <value> (repeatable)";
            case REQUIRED -> " (required)";
            case VARARGS -> "...";
          };
      var names = String.join(", ", option.names());
      joiner.add(names + suffix);
      joiner.add(text.indent(indent).stripTrailing());
    }
    return joiner.toString();
  }
}
