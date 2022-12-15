package main;

import java.util.List;
import java.util.Optional;

public sealed interface Value {

  record FlagValue(boolean value) implements Value {}

  record SingleValue(Optional<String> value) implements Value {}

  record RepeatableValue(List<String> value) implements Value {}

  record RequiredValue(String value) implements Value {}

  record VarargsValue(String... value) implements Value {}

  @SuppressWarnings("unchecked")
  static Value toValue(Object rawValue) {
    if (rawValue instanceof String value) return new RequiredValue(value);
    if (rawValue instanceof List<?> value) return new RepeatableValue((List<String>) value);
    if (rawValue instanceof Optional<?> value) return new SingleValue((Optional<String>) value);
    if (rawValue instanceof Boolean value) return new FlagValue(value);
    if (rawValue instanceof String[] value) return new VarargsValue(value);
    throw new IllegalArgumentException();
  }

  static Schema<List<Value>> toSchema(boolean pruned, Option... options) {
    return new Schema<>(
        List.of(options),
        values -> {
          var plain = values.stream().map(Value::toValue);
          return !pruned
              ? plain.toList()
              : plain
                  .filter(value -> !new FlagValue(false).equals(value))
                  .filter(value -> !new SingleValue(Optional.empty()).equals(value))
                  .filter(value -> !new RepeatableValue(List.of()).equals(value))
                  // do not filter required
                  .filter(value -> !new VarargsValue().equals(value))
                  .toList();
        });
  }
}
