package main;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public sealed interface Value {

    record FlagValue(boolean value) implements Value {}

    record OptionalValue(Optional<String> value) implements Value {}

    record RepeatableValue(List<String> value) implements Value {}

    record RequiredValue(String value) implements Value {}

    record VarargsValue(String... value) implements Value {}

    static ArgumentsSplitter<List<Value>> splitter(ArgumentsSplitter<Object[]> splitter) {
        record Splitter(ArgumentsSplitter<Object[]> splitter) implements ArgumentsSplitter<List<Value>> {

            @Override
            public List<Value> split(Stream<String> args) {
                return Stream.of(splitter.split(args)).map(Splitter::toValue).toList();
            }

            @SuppressWarnings("unchecked")
            private static Value toValue(Object rawValue) {
                if (rawValue instanceof String value) return new RequiredValue(value);
                if (rawValue instanceof List<?> value) return new RepeatableValue((List<String>) value);
                if (rawValue instanceof Optional<?> value) return new OptionalValue((Optional<String>) value);
                if (rawValue instanceof Boolean value) return new FlagValue(value);
                if (rawValue instanceof String[] value) return new VarargsValue(value);
                throw new IllegalArgumentException();
            }
        }
        return new Splitter(splitter);
    }
}
