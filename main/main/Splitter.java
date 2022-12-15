package main;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

@FunctionalInterface
public interface Splitter<T> {

  static <R extends Record> Splitter<R> of(Class<R> schema, MethodHandles.Lookup lookup) {
    return of(RecordSchemaSupport.toSchema(schema, lookup));
  }
  static Splitter<List<Value>> of(Option... options) {
    return of(true, options);
  }
  static Splitter<List<Value>> of(boolean pruned, Option... options) {
    return of(Value.toSchema(pruned, options));
  }

  static <X> Splitter<X> of(Schema<X> schema) {
    Objects.requireNonNull(schema, "schema is null");
    return args -> schema.split(false, args.collect(toCollection(ArrayDeque::new)));
  }

  T split(Stream<String> args);

  default T split(String... args) {
    return split(Stream.of(args));
  }

  default T split(Collection<String> args) {
    return split(args.stream());
  }

  /*
  Argument preprocessing
   */

  default Splitter<T> withEach(UnaryOperator<String> preprocessor) {
    return args -> split(args.map(preprocessor));
  }

  default Splitter<T> withExpand(Function<String, Stream<String>> preprocessor) {
    return args -> split(args.flatMap(preprocessor));
  }

  default Splitter<T> withAdjust(UnaryOperator<Stream<String>> preprocessor) {
    return args -> split(preprocessor.apply(args));
  }
}
