package test.api;

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
      throw fail("Expected a " + expected.getName() + " exception but " + ex.getClass().getName() + " was thrown.");
    }
  }

  public static void assertAll(Runnable... tests) {
    var error = new AssertionError();
    for(var test: tests) {
      try {
        test.run();
      } catch(RuntimeException | AssertionError e) {
        error.addSuppressed(e);
      }
    }
    if (error.getSuppressed().length != 0) {
      throw error;
    }
  }
}
