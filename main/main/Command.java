package main;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Command<T> {

  public interface Option {

    OptionType type();

    List<String> names();

    Class<?> valueType();

    /**
     * Adds or set the option value.
     *
     * @param value raw value to add or set for this option
     * @throws IllegalStateException when this method is called after {@link #complete()} was called
     *     once
     */
    void add(String value) throws IllegalStateException;

    void complete();

    Optional<Command<?>> sub();

    void addSub(Object value);

    default String name() {
      return names().iterator().next();
    }
  }

  public static <T> Command<T> of(Supplier<T> finalizer) {
    return new Command<>(List.of(), finalizer);
  }

  private final List<OptionDescriptor<?>> options;
  private final Supplier<T> finalizer;

  private Command(List<OptionDescriptor<?>> options, Supplier<T> finalizer) {
    requireNonNull(options, "options is null");
    requireNonNull(finalizer, "finalizer is null");
    this.options = options;
    this.finalizer = finalizer;
    checkDuplicates(options());
    checkVarargs(options());
  }

  public List<Option> options() {
    return options.stream()
        .map(opt -> (Option) new OptionInstance<>(opt, new ArrayList<>()))
        .toList();
  }

  private Command<T> add(OptionDescriptor<?> option) {
    return new Command<>(Stream.concat(options.stream(), Stream.of(option)).toList(), finalizer);
  }

  private <V> Command<T> add(
      OptionType type,
      String[] names,
      Class<V> of,
      Function<String, V> from,
      Consumer<List<V>> to,
      Command<?> sub) {
    return add(new OptionDescriptor<>(type, List.of(names), of, from, to, sub));
  }

  public <V> Command<T> addBranch(Command<V> sub, Class<V> of, Consumer<V> to, String... names) {
    return add(OptionType.BRANCH, names, of, str -> null, fromList1(to, null), sub);
  }

  public Command<T> addFlag(Consumer<Boolean> to, String... names) {
    return add(OptionType.FLAG, names, Boolean.class, Boolean::valueOf, fromList1(to, false), null);
  }

  public Command<T> addSingle(Consumer<Optional<String>> to, String... names) {
    return addSingle(String.class, Function.identity(), to, names);
  }

  public <V> Command<T> addSingle(
      Class<V> of, Function<String, V> from, Consumer<Optional<V>> to, String... names) {
    return addSingle(null, of, from, to, names);
  }

  public <V> Command<T> addSingle(
      Command<V> sub,
      Class<V> of,
      Function<String, V> from,
      Consumer<Optional<V>> to,
      String... names) {
    Consumer<List<V>> listToOptional =
        list -> to.accept(list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
    return add(OptionType.SINGLE, names, of, from, listToOptional, sub);
  }

  public Command<T> addRequired(Consumer<String> to, String... names) {
    return addRequired(String.class, Function.identity(), to, names);
  }

  public <V> Command<T> addRequired(
      Class<V> of, Function<String, V> from, Consumer<V> to, String... names) {
    return add(OptionType.REQUIRED, names, of, from, fromList1(to, null), null);
  }

  public Command<T> addRepeatable(Consumer<List<String>> to, String... names) {
    return addRepeatable(String.class, Function.identity(), to, names);
  }

  public <V> Command<T> addRepeatable(
      Class<V> of, Function<String, V> from, Consumer<List<V>> to, String... names) {
    return addRepeatable(null, of, from, to, names);
  }

  public <V> Command<T> addRepeatable(
      Command<V> sub,
      Class<V> of,
      Function<String, V> from,
      Consumer<List<V>> to,
      String... names) {
    return add(OptionType.REPEATABLE, names, of, from, to, sub);
  }

  public Command<T> addVarargs(Consumer<String[]> to, String... names) {
    return addVarargs(String.class, Function.identity(), to, names);
  }

  public <V> Command<T> addVarargs(
      Class<V> of, Function<String, V> from, Consumer<V[]> to, String... names) {
    Consumer<List<V>> listToArray =
        list -> to.accept(list.toArray(size -> (V[]) Array.newInstance(of, size)));
    return add(OptionType.VARARGS, names, of, from, listToArray, null);
  }

  private static <V> Consumer<List<V>> fromList1(Consumer<V> to, V defaultValue) {
    return values -> to.accept(values.isEmpty() ? defaultValue : values.get(0));
  }

  private static void checkDuplicates(List<Option> options) {
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

  private static void checkVarargs(List<Option> options) {
    var varargs = options.stream().filter(opt -> opt.type().isVarargs()).toList();
    if (varargs.isEmpty()) return;
    if (varargs.size() > 1)
      throw new IllegalArgumentException("Too many varargs types specified: " + varargs);
    var positionals = options.stream().filter(opt -> opt.type().isPositional()).toList();
    if (!positionals.get(positionals.size() - 1).type().isVarargs())
      throw new IllegalArgumentException("varargs is not at last positional option: " + options);
  }

  public T complete() {
    return finalizer.get();
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

    @Override
    public void complete() {
      option().to.accept(values);
    }

    @Override
    public Optional<Command<?>> sub() {
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
      Command<?> sub) {

    public OptionDescriptor {
      requireNonNull(type, "type is null");
      requireNonNull(names, "names is null");
      requireNonNull(of, "of is null");
      requireNonNull(from, "from is null");
      if (names.isEmpty() && !type.isPositional()) throw new IllegalArgumentException("Option of type "+type+" must have at least one name");
    }
  }
}
