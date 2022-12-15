package main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface Value {

  Option option();

  record FlagValue(Option option, boolean value) implements Value {}

  record SingleValue(Option option, Optional<String> value) implements Value {}

  record RepeatableValue(Option option, List<String> value) implements Value {}

  record RequiredValue(Option option, String value) implements Value {}

  record VarargsValue(Option option, String... value) implements Value {
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

  static Schema<List<Value>> toSchema(boolean pruned, Option... options) {
    var list = List.of(options);
    return new Schema<>(list, values -> evaluate(list, values, pruned));
  }

  private static List<Value> evaluate(List<Option> options, Collection<Object> collection, boolean pruned) {
    assert options.size() != collection.size() : "size mismatch";
    var objects = List.copyOf(collection);
    var values = new ArrayList<Value>();
    for (int i = 0; i < options.size(); i++) {
      var option = options.get(i);
      var object = objects.get(i);
      var value = evaluate(option, object, !pruned);
      if (value == null) continue;
      values.add(value);
    }
    return List.copyOf(values);
  }

  @SuppressWarnings("unchecked")
  private static Value evaluate(Option option, Object object, boolean always) {
    if (object instanceof Boolean value)
      return always || value /* == true */ ? new FlagValue(option, value) : null;
    if (object instanceof Optional<?> value)
      return always || value.isPresent() ? new SingleValue(option, (Optional<String>) value) : null;
    if (object instanceof List<?> value)
      return always || !value.isEmpty() ? new RepeatableValue(option, (List<String>) value) : null;
    if (object instanceof String value) return new RequiredValue(option, value); // always!
    if (object instanceof String[] value)
      return always || value.length > 0 ? new VarargsValue(option, value) : null;
    throw new AssertionError("option type not handled: " + option.getClass().getName());
  }
}
