package main;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;

public final class ArgumentMap {
  private final LinkedHashMap<Option<?>, Object> argumentMap;

  private ArgumentMap(LinkedHashMap<Option<?>, Object> argumentMap) {
    this.argumentMap = argumentMap;
  }

  @SuppressWarnings("unchecked")
  public <T> T argument(Option<T> option) {
    Objects.requireNonNull(option, "option is null");
    var argument = argumentMap.get(option);
    if (argument == null) {
      throw new IllegalStateException("no argument for parameter " + this);
    }
    return (T) argument;
  }

  // TODO add more methods
  // because we ask in Option.map() to produce a value of an equivalent type
  // we know that we have records, boolean, Optional, List, array or any other values

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