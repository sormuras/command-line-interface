package main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Stream.concat;

public final class Schema {

    private final List<Option> options;

    public Schema(Stream<Option> options) {
        this(options.toArray(Option[]::new));
    }

    public Schema(Option...options) {
        this.options = new ArrayList<>(asList(options));
    }

    public Stream<Option> stream() {
        return options.stream();
    }

    public Iterable<Option> iterable() {
        return options;
    }

    Optional<Option> varargs() {
        return options.stream().filter(Option::isVarargs).findFirst();
    }

    public Schema add(Option option) {
        return new Schema(concat(options.stream(), Stream.of(option)));
    }

    public void check() {
        checkCardinality();
        checkDuplicates();
        checkVarargs();
    }

     void checkDuplicates() {
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

     void checkVarargs() {
         List<Option> varargs = options.stream().filter(Option::isVarargs).toList();
         if (varargs.isEmpty()) return;
         if (varargs.size() > 1)
            throw new IllegalArgumentException("Too many varargs types specified: " + varargs);
         var positionals = options.stream().filter(Option::isPositional).toList();
        if (!positionals.get(positionals.size()-1).isVarargs())
            throw new IllegalArgumentException("varargs is not at last positional option: " + options);
    }

     void checkCardinality() {
        if (options.isEmpty()) throw new IllegalArgumentException("At least one option is expected");
    }
}
