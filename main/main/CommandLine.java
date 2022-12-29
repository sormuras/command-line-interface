package main;

import static java.util.Objects.requireNonNull;
import static main.OptionType.FLAG;
import static main.OptionType.OPTIONAL;
import static main.OptionType.REPEATABLE;
import static main.OptionType.REQUIRED;
import static main.OptionType.SUB;
import static main.OptionType.VARARGS;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A command line argument model with focus on converting a sequence of arguments to an object
 * representation as well as allowing to print a man page or validate the input.
 *
 * @param <T> target type of the object that represents the resulting command line arguments
 */
public interface CommandLine<T> {

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

    String name();

    OptionType type();

    List<String> handles();

    Class<?> of();

    Optional<? extends Factory<?>> sub();

    /**
     * Adds or sets the option raw value as extracted from the command line arguments.
     *
     * @param value raw value to add or set for this option
     */
    void add(String value);
  }

  /**
   * Creates new instances of a {@link CommandLine} state.
   *
   * @param <T> type of the command result value
   */
  @FunctionalInterface
  interface Factory<T> {

    /**
     * @return a fresh instance of a command with empty (initial) state
     */
    CommandLine<T> create();
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
      requireNonNull(init, "state is null");
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

    private CommandLine<T> createCommand() {
      A state = init.get();
      var optionValues = options.stream().map(OptionValue::empty).toList();
      record Instance<A, T>(List<? extends OptionValue<A, ?>> options, A state, Function<A, T> exit)
          implements CommandLine<T> {
        @Override
        public T complete() {
          options.forEach(opt -> opt.complete(state));
          return exit.apply(state);
        }
      }
      checkNoHandleCollisions(optionValues);
      checkVarargs(optionValues);
      return new Instance<>(optionValues, state, exit);
    }

    private Builder<A, T> add(OptionValue<A, ?> option) {
      return new Builder<>(Stream.concat(options.stream(), Stream.of(option)).toList(), init, exit);
    }

    private <V> Builder<A, T> add(
        String name,
        OptionType type,
        String[] handles,
        Class<V> of,
        Function<String, V> from,
        BiConsumer<A, List<V>> to,
        Factory<V> sub) {
      return add(
          new OptionValue<>(
              name, type, List.of(handles), of, from, to, Optional.ofNullable(sub), List.of()));
    }

    public <V> Builder<A, T> addSub(
        String name, Class<V> of, BiConsumer<A, V> to, Factory<V> from, String... handles) {
      return add(name, SUB, handles, of, str -> null, valueToList(to, null), from);
    }

    public Builder<A, T> addFlag(String name, BiConsumer<A, Boolean> to, String... handles) {
      return add(
          name, FLAG, handles, Boolean.class, Boolean::valueOf, valueToList(to, false), null);
    }

    public Builder<A, T> addOptional(
        String name, BiConsumer<A, Optional<String>> to, String... handles) {
      return addOptional(name, String.class, Function.identity(), to, handles);
    }

    public <V> Builder<A, T> addOptional(
        String name,
        Class<V> of,
        Function<String, V> from,
        BiConsumer<A, Optional<V>> to,
        String... handles) {
      return add(name, OPTIONAL, handles, of, from, optionalToList(to), null);
    }

    public <V> Builder<A, T> addOptional(
        String name,
        Class<V> of,
        BiConsumer<A, Optional<V>> to,
        Factory<V> from,
        String... handles) {
      return add(name, OPTIONAL, handles, of, str -> null, optionalToList(to), from);
    }

    public Builder<A, T> addRequired(String name, BiConsumer<A, String> to, String... handles) {
      return addRequired(name, String.class, Function.identity(), to, handles);
    }

    public <V> Builder<A, T> addRequired(
        String name,
        Class<V> of,
        Function<String, V> from,
        BiConsumer<A, V> to,
        String... handles) {
      return add(name, REQUIRED, handles, of, from, valueToList(to, null), null);
    }

    public Builder<A, T> addRepeatable(
        String name, BiConsumer<A, List<String>> to, String... handles) {
      return addRepeatable(name, String.class, Function.identity(), to, handles);
    }

    public <V> Builder<A, T> addRepeatable(
        String name,
        Class<V> of,
        Function<String, V> from,
        BiConsumer<A, List<V>> to,
        String... handles) {
      return add(name, REPEATABLE, handles, of, from, to, null);
    }

    public <V> Builder<A, T> addRepeatable(
        String name, Class<V> of, BiConsumer<A, List<V>> to, Factory<V> from, String... handles) {
      return add(name, REPEATABLE, handles, of, str -> null, to, from);
    }

    public Builder<A, T> addVarargs(String name, BiConsumer<A, String[]> to, String... handles) {
      return addVarargs(name, String.class, Function.identity(), to, handles);
    }

    public <V> Builder<A, T> addVarargs(
        String name,
        Class<V> of,
        Function<String, V> from,
        BiConsumer<A, V[]> to,
        String... handles) {
      return add(name, VARARGS, handles, of, from, arrayToList(of, to), null);
    }

    @SuppressWarnings("unchecked")
    private <V> BiConsumer<A, List<V>> arrayToList(Class<V> of, BiConsumer<A, V[]> to) {
      return (state, list) ->
          to.accept(state, list.toArray(size -> (V[]) Array.newInstance(of, size)));
    }

    private <V> BiConsumer<A, List<V>> valueToList(BiConsumer<A, V> to, V defaultValue) {
      return (state, list) -> to.accept(state, list.isEmpty() ? defaultValue : list.get(0));
    }

    private <V> BiConsumer<A, List<V>> optionalToList(BiConsumer<A, Optional<V>> to) {
      return (state, list) ->
          to.accept(state, list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
    }

    private static void checkNoHandleCollisions(List<? extends Option> options) {
      for (int i = 0; i < options.size(); i++) {
        var a = options.get(i);
        for (int j = i + 1; j < options.size(); j++) {
          var b = options.get(j);
          if (a.handles().stream().anyMatch(handle -> b.handles().contains(handle))) {
            throw new IllegalArgumentException(
                "options "
                    + a.name()
                    + " and "
                    + b.name()
                    + " both declares handle(s) "
                    + new ArrayList<>(a.handles()).retainAll(b.handles()));
          }
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
        String name,
        OptionType type,
        List<String> handles,
        Class<T> of,
        Function<String, T> from,
        BiConsumer<A, List<T>> to,
        Optional<Factory<T>> sub,
        List<T> values)
        implements Option {

      public OptionValue {
        requireNonNull(name, "name is null");
        requireNonNull(type, "type is null");
        requireNonNull(handles, "handles is null");
        requireNonNull(of, "of is null");
        requireNonNull(from, "from is null");
        if (handles.isEmpty() && !type.isPositional())
          throw new IllegalArgumentException(
              "Option of type " + type + " must have at least one handle");
      }

      @Override
      public void add(String value) {
        values.add(from.apply(value));
      }

      void complete(A target) {
        to.accept(target, values);
      }

      OptionValue<A, T> empty() {
        ArrayList<T> copy = new ArrayList<>();
        Optional<Factory<T>> linkedSub = sub.map(factory -> link(factory, copy));
        return new OptionValue<>(name, type, handles, of, from, to, linkedSub, copy);
      }

      /**
       * Links sub-command results, so they automatically end up in the values once they are
       * completed. This allows to not have an additional method in the public API which accepts non
       * {@link String} results.
       */
      private Factory<T> link(Factory<T> factory, List<T> values) {
        return () ->
            new CommandLine<>() {
              CommandLine<T> cmd = factory.create();

              @Override
              public List<? extends Option> options() {
                return cmd.options();
              }

              @Override
              public T complete() {
                T value = cmd.complete();
                values.add(of.cast(value));
                return value;
              }
            };
      }
    }
  }
}
