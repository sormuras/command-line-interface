package test;

import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;

import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** JUnit on a diet of air and love */
public interface JTest {

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface Test {}

  record Event(Method test, Duration executionTime, Optional<Throwable> error) {
    public Event {
      requireNonNull(test);
      requireNonNull(executionTime);
      requireNonNull(error);
    }
  }

  @FunctionalInterface
  interface Listener {
    void accept(Event e);
  }

  record Runner(List<Event> events) {
    public Runner {
      events = List.copyOf(events);
    }

    public void print(PrintStream out) {
      requireNonNull(out);
      int maxNameLength =
          events.stream().mapToInt(ev -> ev.test.getName().length()).max().orElse(0);
      var map = events.stream().collect(groupingBy(e -> e.test.getDeclaringClass().getSimpleName()));
      map.forEach((className, events) -> {
        out.println(className);
        for (var event : events) {
          var status = event.error.isPresent() ? "!!" : "ok";
          out.printf(
              "  [%s] %-" + maxNameLength + "s  %4d ms%n",
              status,
              event.test.getName(),
              event.executionTime.toMillis());
        }
        out.println();
      });
      out.println("=".repeat(maxNameLength + 16));
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

    public void verify() {
      if (events.stream().anyMatch(e -> e.error().isPresent())) throw new RuntimeException();
    }
  }

  static void runAllTests(JTest... tests) {
    requireNonNull(tests, "tests is null");
    var events = new ArrayList<Event>();
    for (var test : tests) {
      test.executeTests(events::add);
    }
    var runner = new Runner(events);
    runner.print(System.out);
    runner.verify();
  }

  default void runTests(String... args) {
    requireNonNull(args, "args is null");
    var events = new ArrayList<Event>();
    executeTests(events::add);
    var runner = new Runner(events);
    runner.print(System.out);
    runner.verify();
  }

  default void executeTests(Listener listener, String... args) {
    requireNonNull(listener, "listener is null");
    requireNonNull(args, "args is null");
    var names = new HashSet<>(asList(args));
    stream(getClass().getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(Test.class))
        .filter(method -> names.isEmpty() || names.contains(method.getName()))
        .forEach(
            method -> {
              var start = currentTimeMillis();
              Throwable cause;
              try {
                method.invoke(this);
                cause = null;
              } catch (InvocationTargetException ex) {
                cause = ex.getCause();
              } catch (Exception ex) {
                cause = ex;
              }
              var executionTime = Duration.ofMillis(currentTimeMillis() - start);
              listener.accept(new Event(method, executionTime, Optional.ofNullable(cause)));
            });
  }
}
