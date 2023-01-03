package main;

import static java.util.Objects.requireNonNull;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * An immutable set of non-null values that keeps the insertion order.
 * <p>
 * It provides {@link #of(String...)} and {@link #copyOf(Collection)} that works like Set.of() / Set.copyOf()
 * but keeps the insertion order.
 */
final class NameSet extends AbstractSet<String> {
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
    return Spliterators.spliterator(
        this, Spliterator.DISTINCT | Spliterator.ORDERED | Spliterator.NONNULL);
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
    for (var name : names) {
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
