package test;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public final class Assertions {
  private Assertions() {
    throw new AssertionError();
  }

  public static void fail(String message) {
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
    fail(expected + " != " + actual + (message.isEmpty() ? "" : ": " + message));
  }

  public static <T> void assertArrayEquals(T[] expected, T[] actual) {
    if (Arrays.equals(expected, actual)) return;
    fail(Arrays.toString(expected) + " != " + Arrays.toString(actual));
  }

  public static <E extends RuntimeException> E assertThrows(Class<? extends E> expected, Runnable test) {
    try {
      test.run();
      fail("Expected a " + expected.getSimpleName() + " exception but nothing was thrown.");
      throw new Error("unreachable");
    } catch (RuntimeException ex) {
      if (expected == ex.getClass()) {
        return expected.cast(ex);
      }
      assertEquals(expected, ex.getClass(), ex.getMessage());
      throw new Error("unreachable");
    }
  }
}
