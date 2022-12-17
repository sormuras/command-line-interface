package main;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public sealed interface Value {

  Option<?> option();

  record FlagValue(Option<?> option, boolean value) implements Value {}

  record SingleValue(Option<?> option, Optional<String> value) implements Value {}

  record RepeatableValue(Option<?> option, List<String> value) implements Value {}

  record RequiredValue(Option<?> option, String value) implements Value {}

  record VarargsValue(Option<?> option, String... value) implements Value {
    @Override
    public boolean equals(Object obj) {
      return obj instanceof VarargsValue other
          && option.equals(other.option)
          && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(option, Arrays.hashCode(value));
    }
  }

  static Schema<Map<String, Value>> toSchema(Option<?>... options) {
    var list = List.of(options);
    return new Schema<>(list, values -> evaluate(list, values));
  }

  private static Map<String, Value> evaluate(List<? extends Option<?>> options, Collection<Object> collection) {
    assert options.size() != collection.size() : "size mismatch";
    var objects = List.copyOf(collection);
    var values = new LinkedHashMap<String, Value>();
    for (int i = 0; i < options.size(); i++) {
      var option = options.get(i);
      var object = objects.get(i);
      var value = evaluate(option, object);
      if (value == null) continue;
      option.names().forEach(name -> values.put(name, value));
    }
    return values;
  }

  @SuppressWarnings("unchecked")
  private static Value evaluate(Option<?> option, Object object) {
    if (object instanceof Boolean value)
      return value /* == true */ ? new FlagValue(option, value) : null;
    if (object instanceof Optional<?> value)
      return value.isPresent() ? new SingleValue(option, (Optional<String>) value) : null;
    if (object instanceof List<?> value)
      return !value.isEmpty() ? new RepeatableValue(option, (List<String>) value) : null;
    if (object instanceof String value) return new RequiredValue(option, value); // always!
    if (object instanceof String[] value)
      return value.length > 0 ? new VarargsValue(option, value) : null;
    throw new AssertionError("option type not handled: " + option.getClass().getName());
  }
}
