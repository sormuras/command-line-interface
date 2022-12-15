package main;

import java.util.Comparator;
import java.util.StringJoiner;

import static java.util.Objects.requireNonNull;

public class Manual {

  public static String help(Schema<?> schema) {
    return help(schema, 2);
  }

  public static String help(Schema<?> schema, int indent) {
    requireNonNull(schema, "schema is null");
    if (indent < 0) throw new IllegalArgumentException("invalid indent " + indent);
    var joiner = new StringJoiner("\n");
    for (var option : schema.options().stream().sorted(Comparator.comparing(Option::name)).toList()) {
      var text = option.help();
      if (text.isEmpty()) continue;
      var suffix =
          switch (option.type()) {
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
