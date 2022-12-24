package main;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;

import static java.util.Objects.requireNonNull;

abstract sealed class AbstractOption<T>
    implements Option<T>
    permits Option.Branch, Option.Flag, Option.Single, Option.Repeatable, Option.Required, Option.Varargs {
  final Type type;
  final Set<String> names;
  final String help;
  final Schema<?> nestedSchema;

  AbstractOption(Type type, Set<String> names, String help, Schema<?> nestedSchema) {
    requireNonNull(type, "type is null");
    requireNonNull(names, "names is null");
    requireNonNull(help, "help null");
    this.type = type;
    this.names = NameSet.copyOf(names);
    this.help = help;
    this.nestedSchema = nestedSchema;
  }

  @Override
  public final Type type() {
    return type;
  }

  @Override
  public final Set<String> names() {
    return names;
  }

  @Override
  public final String help() {
    return help;
  }

  @Override
  public final Schema<?> nestedSchema() {
    return nestedSchema;
  }

  @Override
  public final String toString() {
    return type + names.toString();
  }

  static final class NameSet extends AbstractSet<String> {
    private final LinkedHashSet<String> set;

    private NameSet(LinkedHashSet<String> set) {
      this.set = set;
    }

    @Override
    public int size() {
      return set.size();
    }

    @Override
    public Iterator<String> iterator() {
      var iterator = set.iterator();
      return new Iterator<>() {
        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public String next() {
          return iterator.next();
        }
      };
    }

    @Override
    public boolean contains(Object o) {
      requireNonNull(o, "o is null");
      return set.contains(o);
    }

    @Override
    public Spliterator<String> spliterator() {
      return Spliterators.spliterator(this, Spliterator.DISTINCT | Spliterator.ORDERED | Spliterator.NONNULL);
    }

    public static Set<String> of(String... names) {
      requireNonNull(names, "names is null");
      return copyOf(Arrays.asList(names));
    }

    public static Set<String> copyOf(Collection<? extends String> names) {
      requireNonNull(names, "names is null");
      if (names instanceof NameSet nameSet) {
        return nameSet;
      }
      if (names.isEmpty()) {
        throw new IllegalArgumentException("names is empty");
      }
      var set = new LinkedHashSet<String>();
      for(var name: names) {
        requireNonNull(name, "name is null");
        if (name.isEmpty()) {
          throw new IllegalArgumentException("one name is empty");
        }
        if (!set.add(name)) {
          throw new IllegalArgumentException("duplicate names " + name);
        }
      }
      return new NameSet(set);
    }
  }
}
