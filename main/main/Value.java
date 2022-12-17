package main;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public sealed interface Value<T> {

  Option<T> option();

  record FlagValue(Option<Boolean> option, boolean value) implements Value<Boolean> {}

  record SingleValue<T>(Option<T> option, Optional<T> value) implements Value<T> {}

  record RepeatableValue<T>(Option<T> option, List<T> value) implements Value<T> {}

  record RequiredValue<T>(Option<T> option, T value) implements Value<T> {}

  record VarargsValue<T>(Option<T> option, T... value) implements Value<T> {
    @SafeVarargs
    public VarargsValue {
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof VarargsValue<?> other
          && option.equals(other.option)
          && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(option, Arrays.hashCode(value));
    }
  }

  static Schema<Map<String, Value<?>>> toSchema(Option<?>... options) {
    var list = List.of(options);
    return new Schema<>(list, values -> evaluate(list, values));
  }

  private static Map<String, Value<?>> evaluate(List<? extends Option<?>> options, Collection<Object> collection) {
    assert options.size() != collection.size() : "size mismatch";
    var objects = List.copyOf(collection);
    var values = new LinkedHashMap<String, Value<?>>();
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
  private static <T> Value<T> evaluate(Option<T> option, Object object) {
    if (object instanceof Boolean value)
      return value /* == true */ ? (Value<T>) new FlagValue((Option<Boolean>)option, value) : null;
    if (object instanceof Optional<?> value)
      return value.isPresent() ? new SingleValue<>(option, (Optional<T>) value) : null;
    if (object instanceof List<?> value)
      return !value.isEmpty() ? new RepeatableValue<>(option, (List<T>) value) : null;
    if (object instanceof Object[] value)
      return value.length > 0 ? new VarargsValue<>(option, (T[]) value) : null;
    return new RequiredValue<>(option, (T) object);
  }
}
