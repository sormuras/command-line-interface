package main;

import java.util.Set;

import static java.util.Objects.requireNonNull;

abstract sealed class AbstractOption<T>
    implements Option<T>
    permits Option.Branch, Option.Flag, Option.Single, Option.Repeatable, Option.Required, Option.Varargs {
  final Type type;
  final Set<String> names;
  final String help;
  final Schema<?> nestedSchema;

  AbstractOption(Type type, Set<String> names, String help, Schema<?> nestedSchema) {
    requireNonNull(type, "type is null");
    requireNonNull(names, "names is null");
    requireNonNull(help, "help null");
    this.type = type;
    this.names = NameSet.copyOf(names);
    this.help = help;
    this.nestedSchema = nestedSchema;
  }

  @Override
  public final Type type() {
    return type;
  }

  @Override
  public final Set<String> names() {
    return names;
  }

  @Override
  public final String help() {
    return help;
  }

  @Override
  public final Schema<?> nestedSchema() {
    return nestedSchema;
  }

  @Override
  public final String toString() {
    return type + names.toString();
  }
}
