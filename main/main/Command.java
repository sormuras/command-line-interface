package main;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface Command<T> {

  /**
   * @return lists all options of a command in their declaration order
   */
  List<? extends Option> options();

  /**
   * @param test filter that returns true for those {@link OptionType}s to keep
   * @return lists all options of a command of the given type in their declaration order
   */
  default Stream<? extends Option> options(Predicate<OptionType> test) {
    return options().stream().filter(opt -> test.test(opt.type()));
  }

  /**
   * Completes the handling of command options. This is called when all {@link Option#add(String)}
   * calls have been made.
   *
   * @return the result value of the command
   */
  T complete();

  interface Option {

    OptionType type();

    List<String> names();

    Class<?> of();

    /**
     * Adds or set the option value.
     *
     * @param value raw value to add or set for this option
     */
    void add(String value);

    Optional<Factory<?>> sub();

    void addSub(Object value);

    default String name() {
      return names().iterator().next();
    }
  }

  /**
   * Creates new instances of a {@link Command} state.
   *
   * @param <T> type of the command result value
   */
  @FunctionalInterface
  interface Factory<T> {

    /**
     * @return a fresh instance of a command with empty (initial) state
     */
    Command<T> create();
  }

  /**
   * Create a new builder where aggregation and result state are the same.
   *
   * @param init creates a new instance of the result value
   * @return a builder to add options to the command
   * @param <T> type of the aggregation and command result state
   */
  static <T> Builder<T, T> builder(Supplier<T> init) {
    return builder(init, Function.identity());
  }

  /**
   * Create a new builder with dedicated intermediate state.
   *
   * @param init creates a new instance of the aggregation state, must be a pure function
   * @param exit transforms the aggregation state into the final result, must be a pure function
   * @return a builder to add options to the command
   * @param <A> type of the aggregation state
   * @param <T> type of the command result value
   */
  static <A, T> Builder<A, T> builder(Supplier<A> init, Function<A, T> exit) {
    return new Builder<>(List.of(), init, exit);
  }

  /**
   * Allows to programmatically compose a command. Once all options have been added a {@link
   * Factory} is created using {@link #build()}.
   *
   * @param <A> type of the aggregation state (the intermediate value used to collect option values)
   * @param <T> type of the result value created from the aggregation state
   */
  final class Builder<A, T> {

    private final List<OptionValue<A, ?>> options;
    private final Supplier<A> init;
    private final Function<A, T> exit;

    private Builder(List<OptionValue<A, ?>> options, Supplier<A> init, Function<A, T> exit) {
      requireNonNull(init, "init is null");
      requireNonNull(exit, "exit is null");
      this.options = options;
      this.init = init;
      this.exit = exit;
    }

    public Factory<T> build() {
      // this is to hide the create method from the Builder class even if it is implemented here
      // the copy is made so that the builder instance used can be changed further without
      // affecting the factory
      return new Builder<>(List.copyOf(options), init, exit)::createCommand;
    }

    private Command<T> createCommand() {
      var opts = options.stream().map(OptionValue::empty).toList();
      record Instance<A, T>(List<? extends OptionValue<A, ?>> options, A init, Function<A, T> done)
          implements Command<T> {
        @Override
        public T complete() {
          options.forEach(opt -> opt.complete(init));
          return done.apply(init);
        }
      }
      checkDuplicates(opts);
      checkVarargs(opts);
      return new Instance<>(opts, init.get(), exit);
    }

    private Builder<A, T> add(OptionValue<A, ?> option) {
      return new Builder<>(Stream.concat(options.stream(), Stream.of(option)).toList(), init, exit);
    }

    private <V> Builder<A, T> add(
        OptionType type,
        String[] names,
        Class<V> of,
        Function<String, V> from,
        BiConsumer<A, List<V>> to,
        Factory<?> sub) {
      return add(
          new OptionValue<>(
              type, List.of(names), of, from, to, Optional.ofNullable(sub), List.of()));
    }

    public <V> Builder<A, T> addBranch(
        Factory<V> sub, Class<V> of, BiConsumer<A, V> to, String... names) {
      return add(OptionType.BRANCH, names, of, str -> null, fromList1(to, null), sub);
    }

    public Builder<A, T> addFlag(BiConsumer<A, Boolean> to, String... names) {
      return add(
          OptionType.FLAG, names, Boolean.class, Boolean::valueOf, fromList1(to, false), null);
    }

    public Builder<A, T> addSingle(BiConsumer<A, Optional<String>> to, String... names) {
      return addSingle(String.class, Function.identity(), to, names);
    }

    public <V> Builder<A, T> addSingle(
        Class<V> of, Function<String, V> from, BiConsumer<A, Optional<V>> to, String... names) {
      return addSingle(null, of, from, to, names);
    }

    public <V> Builder<A, T> addSingle(
        Factory<V> sub,
        Class<V> of,
        Function<String, V> from,
        BiConsumer<A, Optional<V>> to,
        String... names) {
      BiConsumer<A, List<V>> listToOptional =
          (agg, list) ->
              to.accept(agg, list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
      return add(OptionType.SINGLE, names, of, from, listToOptional, sub);
    }

    public Builder<A, T> addRequired(BiConsumer<A, String> to, String... names) {
      return addRequired(String.class, Function.identity(), to, names);
    }

    public <V> Builder<A, T> addRequired(
        Class<V> of, Function<String, V> from, BiConsumer<A, V> to, String... names) {
      return add(OptionType.REQUIRED, names, of, from, fromList1(to, null), null);
    }

    public Builder<A, T> addRepeatable(BiConsumer<A, List<String>> to, String... names) {
      return addRepeatable(String.class, Function.identity(), to, names);
    }

    public <V> Builder<A, T> addRepeatable(
        Class<V> of, Function<String, V> from, BiConsumer<A, List<V>> to, String... names) {
      return addRepeatable(null, of, from, to, names);
    }

    public <V> Builder<A, T> addRepeatable(
        Factory<V> sub,
        Class<V> of,
        Function<String, V> from,
        BiConsumer<A, List<V>> to,
        String... names) {
      return add(OptionType.REPEATABLE, names, of, from, to, sub);
    }

    public Builder<A, T> addVarargs(BiConsumer<A, String[]> to, String... names) {
      return addVarargs(String.class, Function.identity(), to, names);
    }

    public <V> Builder<A, T> addVarargs(
        Class<V> of, Function<String, V> from, BiConsumer<A, V[]> to, String... names) {
      BiConsumer<A, List<V>> listToArray =
          (agg, list) -> to.accept(agg, list.toArray(size -> (V[]) Array.newInstance(of, size)));
      return add(OptionType.VARARGS, names, of, from, listToArray, null);
    }

    private static <A, V> BiConsumer<A, List<V>> fromList1(BiConsumer<A, V> to, V defaultValue) {
      return (agg, values) -> to.accept(agg, values.isEmpty() ? defaultValue : values.get(0));
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

    private record OptionValue<A, T>(
        OptionType type,
        List<String> names,
        Class<T> of,
        Function<String, T> from,
        BiConsumer<A, List<T>> to,
        Optional<Factory<?>> sub,
        List<T> values)
        implements Option {

      public OptionValue {
        requireNonNull(type, "type is null");
        requireNonNull(names, "names is null");
        requireNonNull(of, "of is null");
        requireNonNull(from, "from is null");
        if (names.isEmpty() && !type.isPositional())
          throw new IllegalArgumentException(
              "Option of type " + type + " must have at least one name");
      }

      OptionValue<A, T> empty() {
        return new OptionValue<>(type, names, of, from, to, sub, new ArrayList<>());
      }

      @Override
      public void add(String value) {
        values.add(from.apply(value));
      }

      void complete(A target) {
        to.accept(target, values);
      }

      @Override
      public void addSub(Object value) {
        values.add(of.cast(value));
      }
    }
  }
}
