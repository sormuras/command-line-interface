package test.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public final class Assertions {
  private Assertions() {
    throw new AssertionError();
  }

  private static String format(String expected, String actual, String message) {
    return "expected: `"
        + expected
        + "` but was: `"
        + actual
        + "` "
        + (message.isEmpty() ? "" : ": " + message);
  }

  public static AssertionError fail(String message) {
    throw new AssertionError(message);
  }

  public static AssertionError fail(String message, Throwable cause) {
    throw (AssertionError) new AssertionError(message).initCause(cause);
  }

  public static void assertTrue(boolean actual) {
    assertEquals(true, actual);
  }

  public static void assertTrue(boolean actual, String message) {
    assertEquals(true, actual, message);
  }

  public static void assertFalse(boolean actual) {
    assertEquals(false, actual);
  }

  public static <T> void assertEqualsOptional(T expected, Optional<? extends T> actual) {
    assertEquals(expected, actual.orElseThrow());
  }

  public static <T> void assertEquals(T expected, T actual) {
    assertEquals(expected, actual, "");
  }

  public static <T> void assertEquals(T expected, T actual, String message) {
    if (Objects.equals(expected, actual)) return;
    throw fail(format(String.valueOf(expected), String.valueOf(actual), message));
  }

  public static <T> void assertArrayEquals(T[] expected, T[] actual) {
    if (Arrays.equals(expected, actual)) return;
    throw fail(format(Arrays.toString(expected), Arrays.toString(actual), ""));
  }

  public static <E extends RuntimeException> E assertThrows(
      Class<? extends E> expected, Runnable test) {
    try {
      test.run();
      throw fail("Expected a " + expected.getName() + " exception but nothing was thrown.");
    } catch (RuntimeException ex) {
      if (expected.isInstance(ex)) {
        return expected.cast(ex);
      }
      throw fail("Expected a " + expected.getName() + " exception but " + ex.getClass().getName() + " was thrown.", ex);
    }
  }

  public static void assertAll(Runnable... tests) {
    var errors = new ArrayList<Throwable>();
    for(var test: tests) {
      try {
        test.run();
      } catch(RuntimeException | AssertionError e) {
        errors.add(e);
      }
    }
    if (!errors.isEmpty()) {
      var message = errors.size() == 1 ? errors.get(0).getMessage() : "multiple errors";
      var error = new AssertionError(message);
      errors.forEach(error::addSuppressed);
      throw error;
    }
  }
}
