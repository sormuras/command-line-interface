package main;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;

/**
 * An immutable associative table that links an {@link Option} to its value (an argument).
 *
 * <p>Calling {@link Splitter#of(Option[])} with some options returns a splitter of type
 * Splitter&lt;ArgumentMap&gt; configured with the options.
 * On this splitter, calling {@link Splitter#split(Stream)} with a stream of the command line arguments
 * returns an argument map containing the value (the argument) of each option.
 *  <pre>
 *   var flagF = Option.flag("-f");
 *   var splitter = Splitter.of(flagFJ);
 *   var argumentMap = splitter.split("-f");
 *
 *   var flagFValue = argumentMap.argument(flagF);
 *   System.out.println(flagFValue);  // true
 *   </pre>
 */
public final class ArgumentMap {
  private final LinkedHashMap<Option<?>, Object> argumentMap;

  private ArgumentMap(LinkedHashMap<Option<?>, Object> argumentMap) {
    this.argumentMap = argumentMap;
  }

  /**
   * Returns the value (the argument) of an option.
   *
   * @param option the option from which we need the corresponding argument
   * @return the argument of the option.
   * @param <T> the type of the argument
   * @throws IllegalStateException if the option has no argument because the {@link Splitter}
   * that generates this map was not configured with that option.
   */
  @SuppressWarnings("unchecked")
  public <T> T argument(Option<T> option) {
    Objects.requireNonNull(option, "option is null");
    var argument = argumentMap.get(option);
    if (argument == null) {
      throw new IllegalStateException("no argument for option " + this);
    }
    return (T) argument;
  }

  // TODO add more methods
  // because we ask in Option.map() to produce a value of an equivalent type
  // we know that we have records, boolean, Optional, List, array or any other values

  /**
   * Returns a string representation the arguments associated with each option.
   * @return a string representation the arguments associated with each option.
   */
  @Override
  public String toString() {
    return argumentMap.toString();
  }

  static Schema<ArgumentMap> toSchema(Option<?>... options)  {
    var opt = List.of(options);
    return new Schema<>(opt, data -> new ArgumentMap(range(0, opt.size())
        .boxed()
        .collect(toMap(opt::get, data::get, (_1, _2) -> { throw null; }, LinkedHashMap::new))));
  }
}