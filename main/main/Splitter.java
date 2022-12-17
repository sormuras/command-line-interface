package main;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

@FunctionalInterface
public interface Splitter<T> {

  static <R extends Record> Splitter<R> of(Lookup lookup, Class<R> schema) {
    requireNonNull(schema, "schema is null");
    requireNonNull(lookup, "lookup is null");
    return of(RecordSchemaSupport.toSchema(lookup, schema));
  }

  static Splitter<Map<String, Value<?>>> of(Option<?>... options) {
    requireNonNull(options, "options is null");
    return of(Value.toSchema(options));
  }

  static <T> Splitter<T> of(Schema<T> schema) {
    Objects.requireNonNull(schema, "schema is null");
    return args -> {
      requireNonNull(args, "args is null");
      return schema.split(false, args.collect(toCollection(ArrayDeque::new)));
    };
  }

  T split(Stream<String> args);

  default T split(String... args) {
    requireNonNull(args, "args is null");
    return split(Arrays.stream(args));
  }

  default T split(List<String> args) {
    requireNonNull(args, "args is null");
    return split(args.stream());
  }

  /*
  Argument preprocessing
   */

  default Splitter<T> withEach(UnaryOperator<String> preprocessor) {
    requireNonNull(preprocessor, "preprocessor is null");
    return args -> split(args.map(preprocessor));
  }

  default Splitter<T> withExpand(Function<? super String, ? extends Stream<String>> preprocessor) {
    requireNonNull(preprocessor, "preprocessor is null");
    return args -> split(args.flatMap(preprocessor));
  }

  default Splitter<T> withAdjust(UnaryOperator<Stream<String>> preprocessor) {
    requireNonNull(preprocessor, "preprocessor is null");
    return args -> split(preprocessor.apply(args));
  }
}
