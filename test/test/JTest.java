package test;

import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;

import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;

/** JUnit on a diet of air and love */
public interface JTest {

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Test {}

  record Event(Method test, Duration executionTime, Optional<Throwable> error) {}

  @FunctionalInterface
  interface Listener {
    void accept(Event e);
  }

  record Runner(List<Event> events) implements Listener {

    @Override
    public void accept(Event e) {
      events.add(e);
    }

    void print(PrintStream out) {
      Class<?> in = null;
      int maxNameLength =
          events.stream().mapToInt(ev -> ev.test.getName().length()).max().orElse(0);
      for (Event e : events) {
        if (e.test.getDeclaringClass() != in)
          out.println("\n" + e.test.getDeclaringClass().getSimpleName());
        in = e.test.getDeclaringClass();
        String status = e.error.isPresent() ? "!!" : "ok";
        out.printf(
            "  [%s] %-" + maxNameLength + "s  %4d ms%n",
            status,
            e.test.getName(),
            e.executionTime.toMillis());
      }
      out.println("\n" + "=".repeat(maxNameLength + 16));
      out.printf(
          "  [ok] %3d   %" + maxNameLength + "d ms%n",
          events.stream().filter(e -> e.error.isEmpty()).count(),
          events.stream()
              .filter(e -> e.error.isEmpty())
              .mapToLong(e -> e.executionTime.toMillis())
              .sum());
      out.printf(
          "  [!!] %3d   %" + (maxNameLength) + "d ms%n",
          events.stream().filter(e -> e.error.isPresent()).count(),
          events.stream()
              .filter(e -> e.error.isPresent())
              .mapToLong(e -> e.executionTime.toMillis())
              .sum());
    }

    void verify() {
      if (events.stream().anyMatch(e -> e.error().isPresent())) throw new RuntimeException();
    }
  }

  static void runAllTests(JTest... tests) {
    Runner run = new Runner(new ArrayList<>());
    runAllTests(run, tests);
    run.print(System.out);
    run.verify();
  }

  static void runAllTests(Listener listener, JTest... tests) {
    for (JTest test : tests) test.runTests(listener);
  }

  default void runTests(String... args) {
    Runner run = new Runner(new ArrayList<>());
    runTests(run, args);
    run.print(System.out);
    run.verify();
  }

  default void runTests(Listener listener, String... args) {
    Set<String> names = new HashSet<>(asList(args));

    stream(getClass().getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(Test.class))
        .filter(method -> names.isEmpty() || names.contains(method.getName()))
        .forEach(
            method -> {
              long before = currentTimeMillis();
              try {

                method.invoke(this);
                listener.accept(
                    new Event(
                        method, Duration.ofMillis(currentTimeMillis() - before), Optional.empty()));
              } catch (InvocationTargetException ex) {
                listener.accept(
                    new Event(
                        method,
                        Duration.ofMillis(currentTimeMillis() - before),
                        Optional.of(ex.getTargetException())));
              } catch (Exception ex) {
                listener.accept(
                    new Event(
                        method, Duration.ofMillis(currentTimeMillis() - before), Optional.of(ex)));
              }
            });
  }
}
