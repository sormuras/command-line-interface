package main;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface Command<T> {

  List<? extends Option> options();

  default Stream<? extends Option> options(Predicate<OptionType> test) {
    return options().stream().filter(opt -> test.test(opt.type()));
  }

  T complete();

  interface Option {

    OptionType type();

    List<String> names();

    Class<?> valueType();

    /**
     * Adds or set the option value.
     *
     * @param value raw value to add or set for this option
     */
    void add(String value);

    Optional<Builder<?>> sub();

    void addSub(Object value);

    default String name() {
      return names().iterator().next();
    }
  }

  static <T> Builder<T> of(Supplier<T> finalizer) {
    return new Builder<>(List.of(), finalizer);
  }

  final class Builder<T> {

    private final List<OptionDescriptor<?>> options;
    private final Supplier<T> finalizer;

    private Builder(List<OptionDescriptor<?>> options, Supplier<T> finalizer) {
      requireNonNull(options, "options is null");
      requireNonNull(finalizer, "finalizer is null");
      this.options = options;
      this.finalizer = finalizer;
    }

    public Command<T> build() {
      var opts = options.stream().map(opt -> new OptionInstance<>(opt, new ArrayList<>())).toList();
      record Cmd<T>(List<? extends OptionInstance<?>> options, Supplier<T> done)
          implements Command<T> {
        @Override
        public T complete() {
          options.forEach(OptionInstance::complete);
          return done.get();
        }
      }
      checkDuplicates(opts);
      checkVarargs(opts);
      return new Cmd<>(opts, finalizer);
    }

    private Builder<T> add(OptionDescriptor<?> option) {
      return new Builder<>(Stream.concat(options.stream(), Stream.of(option)).toList(), finalizer);
    }

    private <V> Builder<T> add(
        OptionType type,
        String[] names,
        Class<V> of,
        Function<String, V> from,
        Consumer<List<V>> to,
        Builder<?> sub) {
      return add(new OptionDescriptor<>(type, List.of(names), of, from, to, sub));
    }

    public <V> Builder<T> addBranch(Builder<V> sub, Class<V> of, Consumer<V> to, String... names) {
      return add(OptionType.BRANCH, names, of, str -> null, fromList1(to, null), sub);
    }

    public Builder<T> addFlag(Consumer<Boolean> to, String... names) {
      return add(
          OptionType.FLAG, names, Boolean.class, Boolean::valueOf, fromList1(to, false), null);
    }

    public Builder<T> addSingle(Consumer<Optional<String>> to, String... names) {
      return addSingle(String.class, Function.identity(), to, names);
    }

    public <V> Builder<T> addSingle(
        Class<V> of, Function<String, V> from, Consumer<Optional<V>> to, String... names) {
      return addSingle(null, of, from, to, names);
    }

    public <V> Builder<T> addSingle(
        Builder<V> sub,
        Class<V> of,
        Function<String, V> from,
        Consumer<Optional<V>> to,
        String... names) {
      Consumer<List<V>> listToOptional =
          list -> to.accept(list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
      return add(OptionType.SINGLE, names, of, from, listToOptional, sub);
    }

    public Builder<T> addRequired(Consumer<String> to, String... names) {
      return addRequired(String.class, Function.identity(), to, names);
    }

    public <V> Builder<T> addRequired(
        Class<V> of, Function<String, V> from, Consumer<V> to, String... names) {
      return add(OptionType.REQUIRED, names, of, from, fromList1(to, null), null);
    }

    public Builder<T> addRepeatable(Consumer<List<String>> to, String... names) {
      return addRepeatable(String.class, Function.identity(), to, names);
    }

    public <V> Builder<T> addRepeatable(
        Class<V> of, Function<String, V> from, Consumer<List<V>> to, String... names) {
      return addRepeatable(null, of, from, to, names);
    }

    public <V> Builder<T> addRepeatable(
        Builder<V> sub,
        Class<V> of,
        Function<String, V> from,
        Consumer<List<V>> to,
        String... names) {
      return add(OptionType.REPEATABLE, names, of, from, to, sub);
    }

    public Builder<T> addVarargs(Consumer<String[]> to, String... names) {
      return addVarargs(String.class, Function.identity(), to, names);
    }

    public <V> Builder<T> addVarargs(
        Class<V> of, Function<String, V> from, Consumer<V[]> to, String... names) {
      Consumer<List<V>> listToArray =
          list -> to.accept(list.toArray(size -> (V[]) Array.newInstance(of, size)));
      return add(OptionType.VARARGS, names, of, from, listToArray, null);
    }

    private static <V> Consumer<List<V>> fromList1(Consumer<V> to, V defaultValue) {
      return values -> to.accept(values.isEmpty() ? defaultValue : values.get(0));
    }

    private static void checkDuplicates(List<? extends Option> options) {
      var optionsByName = new HashMap<String, Option>();
      for (var option : options) {
        var names = option.names();
        for (var name : names) {
          var otherOption = optionsByName.put(name, option);
          if (otherOption == option)
            throw new IllegalArgumentException(
                "option " + option + " declares duplicated name " + name);
          if (otherOption != null)
            throw new IllegalArgumentException(
                "options " + option + " and " + otherOption + " both declares name " + name);
        }
      }
    }

    private static void checkVarargs(List<? extends Option> options) {
      var varargs = options.stream().filter(opt -> opt.type().isVarargs()).toList();
      if (varargs.isEmpty()) return;
      if (varargs.size() > 1)
        throw new IllegalArgumentException("Too many varargs types specified: " + varargs);
      var positionals = options.stream().filter(opt -> opt.type().isPositional()).toList();
      if (!positionals.get(positionals.size() - 1).type().isVarargs())
        throw new IllegalArgumentException("varargs is not at last positional option: " + options);
    }

    private record OptionInstance<T>(OptionDescriptor<T> option, List<T> values) implements Option {
      @Override
      public OptionType type() {
        return option.type;
      }

      @Override
      public List<String> names() {
        return option.names();
      }

      @Override
      public Class<?> valueType() {
        return option.of();
      }

      @Override
      public void add(String value) throws IllegalStateException {
        values.add(option().from.apply(value));
      }

      void complete() {
        option().to.accept(values);
      }

      @Override
      public Optional<Builder<?>> sub() {
        return Optional.ofNullable(option().sub());
      }

      @Override
      public void addSub(Object value) {
        values.add(option().of().cast(value));
      }
    }

    private record OptionDescriptor<T>(
        OptionType type,
        List<String> names,
        Class<T> of,
        Function<String, T> from,
        Consumer<List<T>> to,
        Builder<?> sub) {

      public OptionDescriptor {
        requireNonNull(type, "type is null");
        requireNonNull(names, "names is null");
        requireNonNull(of, "of is null");
        requireNonNull(from, "from is null");
        if (names.isEmpty() && !type.isPositional())
          throw new IllegalArgumentException(
              "Option of type " + type + " must have at least one name");
      }
    }
  }
}
