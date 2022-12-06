package main;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableSet;

public record Option(Type type, Set<String> names, String help, Schema nestedSchema) {
    public enum Type {
        /**
         * An optional flag, like {@code --verbose}.
         */
        FLAG(false),
        /**
         * An optional key-value pair, like {@code --version 47.11}.
         */
        KEY_VALUE(Optional.empty()),
        /**
         * An optional and repeatable key, like {@code --with alpha --with omega}
         */
        REPEATABLE(List.of()),
        /**
         * A required positional option
         */
        REQUIRED(""),
        /**
         * A collection of all unhandled arguments.
         */
        VARARGS(new String[0]);

        private final Object defaultValue;

        Type(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        Object defaultValue() {
            return defaultValue;
        }

        static Type valueOf(Class<?> type) {
            if (type == Boolean.class || type == boolean.class) return FLAG;
            if (type == Optional.class) return KEY_VALUE;
            if (type == List.class) return REPEATABLE;
            if (type == String.class) return REQUIRED;
            if (type == String[].class) return VARARGS;
            throw new IllegalArgumentException("Unsupported value type: " + type);
        }
    }

    public Option {
        requireNonNull(type, "type is null");
        requireNonNull(names, "named is null");
        requireNonNull(help, "help is null");
        names = Collections.unmodifiableSet(new LinkedHashSet<>(names));
        if (names.isEmpty()) throw new IllegalArgumentException("no name defined");
    }

    public Option(Type type, String... names) {
        this(type, Stream.of(names).collect(toUnmodifiableSet()), "", null);
    }

    public Option withHelp(String text) {
        return new Option(type, names, text, nestedSchema);
    }

    String name() {
        return names.iterator().next();
    }

    boolean isVarargs() {
        return type == Type.VARARGS;
    }

    boolean isRequired() {
        return type == Type.REQUIRED;
    }

    boolean isFlag() {
        return type == Type.FLAG;
    }

    boolean isPositional() { return  type == Type.VARARGS || type == Type.REQUIRED; }
}
