package main;

import java.io.Serializable;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

@FunctionalInterface
public interface Converter<T,R> extends Function<T,R>, Serializable {
  R apply(T t);

  default <S> Converter<T, S> andThen(Function<? super R, ? extends S> converter) {
    requireNonNull(converter, "converter is null");
    return t -> converter.apply(apply(t));
  }

  default <S> Converter<S, R> compose(Function<? super S, ? extends T> converter) {
    requireNonNull(converter, "converter is null");
    return t -> apply(converter.apply(t));
  }
}