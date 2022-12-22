package test.api;

import static java.lang.System.currentTimeMillis;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;

import java.io.PrintStream;
import java.lang.StackWalker.Option;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** JUnit on a diet of air and love */
public final class JTest {
  private JTest() {
    throw new AssertionError();
  }

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Test {}

  public record Event(Method test, Duration executionTime, Optional<Throwable> error) {
    public Event {
      requireNonNull(test);
      requireNonNull(executionTime);
      requireNonNull(error);
    }
  }

  @FunctionalInterface
  public interface Listener {
    void accept(Event e);
  }

  public record Runner(List<Event> events) {
    public Runner {
      events = List.copyOf(events);
    }

    public void print(PrintStream out) {
      requireNonNull(out);
      int maxNameLength =
          events.stream().mapToInt(ev -> ev.test.getName().length()).max().orElse(0);
      var map =
          events.stream().collect(groupingBy(e -> e.test.getDeclaringClass().getSimpleName()));
      map.forEach(
          (className, events) -> {
            out.println(className);
            for (var event : events) {
              var status = event.error.isPresent() ? "!!" : "ok";
              var error = event.error().map(Runner::briefError).orElse("");
              out.printf(
                  "  [%s] %-" + maxNameLength + "s  %4d ms %s%n",
                  status,
                  event.test.getName(),
                  event.executionTime.toMillis(),
                  error);
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

    private static String briefError(Throwable error) {
      var message = "  " + error.getMessage();
      var endOfLine = message.indexOf('\n');
      var shortMessage = endOfLine != -1 ? message.substring(0, endOfLine) + "..." : message;
      var location =
          stream(error.getStackTrace())
              .filter(element -> !element.getClassName().equals(Assertions.class.getName()))
              .findFirst()
              .map(element -> " at " + element)
              .orElse("");
      return shortMessage + location;
    }

    public void verify() {
      var verifyError = new AssertionError();
      for (var event : events) {
        event.error.ifPresent(verifyError::addSuppressed);
      }
      if (verifyError.getSuppressed().length != 0) {
        throw verifyError;
      }
    }
  }

  private static final class LookupAccess {
    private static final StackWalker STACK_WALKER =
        StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);

    static Lookup privateLookup(Class<?> callerClass) {
      try {
        return MethodHandles.privateLookupIn(callerClass, MethodHandles.lookup());
      } catch (IllegalAccessException e) {
        throw (IllegalAccessError) new IllegalAccessError().initCause(e);
      }
    }
  }

  public static void runTestSuites(Class<?>... testClasses) {
    var callerClass = LookupAccess.STACK_WALKER.getCallerClass();
    runTestSuites(LookupAccess.privateLookup(callerClass), testClasses);
  }

  private static final ThreadLocal<ArrayList<Event>> EVENTS_LOCAL = new ThreadLocal<>();

  public static void runTestSuites(Lookup lookup, Class<?>... testClasses) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(testClasses, "testClasses is null");
    runWithARunner(__ -> {
      for (var testClass : testClasses) {
        runMainMethod(lookup, testClass);
      }
    });
  }

  private static void runWithARunner(Consumer<List<Event>> consumer) {
    var events = EVENTS_LOCAL.get();
    if (events != null) {
      // there is already a runner
      consumer.accept(events);
      return;
    }

    events = new ArrayList<Event>();
    EVENTS_LOCAL.set(events);
    try {
      consumer.accept(events);
    } finally {
      EVENTS_LOCAL.remove();
    }
    var runner = new Runner(events);
    runner.print(System.out);
    runner.verify();
  }

  private static void runMainMethod(Lookup lookup, Class<?> testClass, String... args) {
    MethodHandle main;
    try {
      main = lookup.findStatic(testClass, "main", methodType(void.class, String[].class));
    } catch (NoSuchMethodException e) {
      throw (NoSuchMethodError) new NoSuchMethodError().initCause(e);
    } catch (IllegalAccessException e) {
      throw (IllegalAccessError) new IllegalAccessError().initCause(e);
    }

    try {
      main.invokeExact(args);
    } catch (Error | RuntimeException e) {
      throw e;
    } catch (Throwable t) {
      throw new AssertionError("main method throws an exception", t);
    }
  }

  public static void runTests(Object test, String... args) {
    var callerClass = LookupAccess.STACK_WALKER.getCallerClass();
    runTests(LookupAccess.privateLookup(callerClass), test, args);
  }

  public static void runTests(Lookup lookup, Object test, String... args) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(test, "test is null");
    requireNonNull(args, "args is null");
    runWithARunner(events -> executeTests(lookup, test, events::add, args));
  }

  private static void executeTests(Lookup lookup, Object test, Listener listener, String... args) {
    var names = new HashSet<>(asList(args));
    stream(test.getClass().getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(Test.class))
        .filter(method -> names.isEmpty() || names.contains(method.getName()))
        .forEach(
            method -> {
              MethodHandle mh;
              try {
                //method.setAccessible(true);
                mh = lookup.unreflect(method);
              } catch (IllegalAccessException /*| InaccessibleObjectException*/ e) {
                throw (IllegalAccessError) new IllegalAccessError().initCause(e);
              }
              var start = currentTimeMillis();
              Throwable cause;
              try {
                mh.invoke(test);
                cause = null;
              } catch (Throwable ex) {
                cause = ex;
              }
              var executionTime = Duration.ofMillis(currentTimeMillis() - start);
              listener.accept(new Event(method, executionTime, Optional.ofNullable(cause)));
            });
  }
}
