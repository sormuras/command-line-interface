package main;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Splits the command line arguments.
 * All the implementations of this interface must be thread-safe.
 *
 * <p>A Splitter is created from a schema using {@link #of(Schema)}.
 *    Once configured the Splitter can be used to split arguments using {@link #split(Stream)}.
 * <pre>
 *   Schema&lt;X&gt; schema = ...
 *   Splitter&lt;X&gt; splitter = Splitter.of(schema);
 *   X result = splitter.split(args);
 * </pre>
 *
 * <h2>Using a record to define a schema</h2>
 * <p>There is a convenient method {@link #of(Lookup, Class) of(lookup, class)} that uses a record
 * both as a schema and also as a storage for the arguments.
 * <pre>
 *   record Command(
 *     &#064;Name("-v")  boolean verbose,
 *     Optional&lt;Level&gt; __level,
 *     List&lt;String&gt; __data,
 *     Path destination,
 *     Path... files) {}
 *
 *   Splitter&lt;Command&gt; splitter = Splitter.of(MethodHandles.lookup(), Command.class);
 *   Command command = splitter.split(args);
 * </pre>
 * Each component of the record is transformed to an {@link Option}. The component type determines
 * the option type, a boolean/Boolean is a {@link Option.Flag Flag}, an Optional is a {@link Option.Single Single},
 * a List is a {@link Option.Repeatable Repeatable}, an array is a {@link Option.Varargs Varargs},
 * a record is a {@link main.Option.Branch Branch} otherwise it's a {@link Option.Required Required}.
 * <p>
 * The annotations &#064;{@link Name} and &#064;{@link Help} specify respectively
 * {@link Option#names() several names} and a {@link Option#help() help text} of an option.
 * <p>
 * The conversion from strings are handled by the {@link ConverterResolver#defaultResolver() default resolver}.
 * <p>&nbsp;
 *
 * <h2>Using a list of options to define a schema</h2>
 * <p>There is a convenient method {@link #of(Option[]) of(options...)} that uses an array of options as schema
 * and store the resulting arguments in an {@link ArgumentMap}.
 * <pre>
 *   var verbose = Option.flag("-v");
 *   var level = Option.single("--level").convert(Level::valueOf);
 *   var data = Option.repeatable("--data");
 *   var destination = Option.required("destination").convert(Path::of);
 *   var files = Option.varargs("files").convert(Path::of, Path[]::new);
 *
 *   Splitter&lt;ArgumentMap&gt; splitter = Splitter.of(verbose, level, data, destination, files);
 *   ArgumentMap argumentMap = splitter.split(args);
 * </pre>
 * This example defines the same schema as the record above but using the programmatic API.
 * <p>&nbsp;
 *
 * <h2>Argument pre-processing</h2>
 * <p>The méthodes {@link #withEach(UnaryOperator)} and {@link #withExpand(Function)} allows to
 * pre-process the arguments and respectively modify an argument or expand it into several arguments.
 *
 * @param <T> the type of the class bundling all the arguments extracted from the command line.
 */
@FunctionalInterface
public interface Splitter<T> {

  /**
   * Returns a splitter configured from a record class and using the {@link ConverterResolver#defaultResolver() default resolver}.
   * The result of the method {@link #split(Stream)} is an instance of the record class created using
   * the lookup. The lookup is also used to find the conversion functions if needed.
   *
   * @param lookup a lookup object.
   * @param schema a record class defining the schema.
   * @return a splitter configured from a record class.
   * @throws IllegalArgumentException if the record is not a valid schema.
   *
   * @param <R> the type of the record.
   */
  static <R extends Record> Splitter<R> of(Lookup lookup, Class<R> schema) {
    return of(lookup, schema, ConverterResolver.defaultResolver());
  }

  /**
   * Returns a splitter configured from a record class and a conversion function resolver.
   * The result of the method {@link #split(Stream)} is an instance of the record class created using
   * the lookup. The lookup is also used by the resolver to find the conversion functions if needed.
   *
   * @param lookup a lookup object.
   * @param schema a record class defining the schema.
   * @param resolver a conversion function resolver
   * @return a splitter configured from a record class.
   * @throws IllegalArgumentException if the record is not a valid schema.
   *
   * @param <R> the type of the record.
   */
  static <R extends Record> Splitter<R> of(Lookup lookup, Class<R> schema, ConverterResolver resolver) {
    requireNonNull(schema, "schema is null");
    requireNonNull(lookup, "lookup is null");
    requireNonNull(resolver, "resolver is null");
    return of(RecordSchemaSupport.toSchema(lookup, schema, resolver));
  }

  /**
   * Returns a splitter configured from the options.
   * The result of the method {@link #split(Stream)} is an instance of {@link ArgumentMap}.
   *
   * @param options the options defining the schema.
   * @return a splitter configured from the options.
   */
  static Splitter<ArgumentMap> of(Option<?>... options)  {
    requireNonNull(options, "options is null");
    return of(ArgumentMap.toSchema(options));
  }

  /**
   * Returns a splitter configured from a schema.
   *
   * @param schema the schema used to configure the splitter.
   * @return a splitter configured from a schema.
   *
   * @param <T> type of the return type of the method {@link #split(Stream)}.
   */
  static <T> Splitter<T> of(Schema<T> schema) {
    Objects.requireNonNull(schema, "schema is null");
    return args -> {
      requireNonNull(args, "args is null");
      return schema.split(false, args.collect(toCollection(ArrayDeque::new)));
    };
  }

  /**
   * Splits the command line argument into different values following the recipe of the {@link Schema}
   * used to create this splitter.
   *
   * @param args the command line arguments.
   * @return an object gathering the values of the arguments.
   */
  T split(Stream<String> args);

  /**
   * Splits the command line argument into different values following the recipe of the {@link Schema}
   * used to create this splitter.
   * This is a convenient method equivalent to
   * <pre>
   *   split(Arrays.stream(args))
   * </pre>
   *
   * @param args the command line arguments.
   * @return an object gathering the values of the arguments.
   */
  default T split(String... args) {
    requireNonNull(args, "args is null");
    return split(Arrays.stream(args));
  }

  /**
   * Splits the command line argument into different values following the recipe of the {@link Schema}
   * used to create this splitter.
   * This is a convenient method equivalent to
   * <pre>
   *   split(args.stream())
   * </pre>
   *
   * @param args the command line arguments.
   * @return an object gathering the values of the arguments.
   */
  default T split(List<String> args) {
    requireNonNull(args, "args is null");
    return split(args.stream());
  }

  /*
  Argument preprocessing
   */

  /**
   * Returns a splitter that will call the preprocessor on all arguments of the command line
   * when {@link #split(Stream)} is called.
   *
   * @param preprocessor an argument pre-processor.
   * @return a splitter that will call the preprocessor on all arguments of the command line.
   *
   * @see #split(Stream)
   */
  default Splitter<T> withEach(UnaryOperator<String> preprocessor) {
    requireNonNull(preprocessor, "preprocessor is null");
    return args -> split(args.map(preprocessor));
  }

  /**
   * Returns a splitter that will call the preprocessor on all arguments of the command line
   * when {@link #split(Stream)} is called.
   *
   * @param preprocessor an argument pre-processor that can expand each argument into multiple arguments.
   * @return a splitter that will call the preprocessor on all arguments of the command line.
   *
   * @see #split(Stream)
   */
  default Splitter<T> withExpand(Function<? super String, ? extends Stream<String>> preprocessor) {
    requireNonNull(preprocessor, "preprocessor is null");
    return args -> split(args.flatMap(preprocessor));
  }
}
