package main;

import java.util.List;
import java.util.Optional;

public sealed interface Value {

    record FlagValue(boolean value) implements Value {}

    record OptionalValue(Optional<String> value) implements Value {}

    record RepeatableValue(List<String> value) implements Value {}

    record RequiredValue(String value) implements Value {}

    record VarargsValue(String... value) implements Value {}

    @SuppressWarnings("unchecked")
    static Value toValue(Object rawValue) {
        if (rawValue instanceof String value) return new RequiredValue(value);
        if (rawValue instanceof List<?> value) return new RepeatableValue((List<String>) value);
        if (rawValue instanceof Optional<?> value) return new OptionalValue((Optional<String>) value);
        if (rawValue instanceof Boolean value) return new FlagValue(value);
        if (rawValue instanceof String[] value) return new VarargsValue(value);
        throw new IllegalArgumentException();
    }

    static Schema<List<Value>> toSchema(Option... options) {
        return new Schema<>(List.of(options), values -> values.stream().map(Value::toValue).toList());
    }
}
