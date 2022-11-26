package test;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

final class Assertions {

    static void fail(String message)
    {
        assert false : message;
    }

    static void assertTrue(boolean actual) {
        assertEquals(true, actual);
    }

    static void assertTrue(boolean actual, String message) {
        assertEquals(true, actual, message);
    }

    static void assertFalse(boolean actual) {
        assertEquals(false, actual);
    }

    static <T> void assertEqualsOptional(T expected, Optional<T> actual) {
        assertEquals(expected, actual.orElseThrow());
    }

    static <T> void assertEquals(T expected, T actual) {
        assertEquals(expected, actual, "");
    }

    static <T> void assertEquals(T expected, T actual, String message) {
        assert Objects.equals(expected, actual) : expected + " != " + actual+(message.isEmpty() ? "" : ": "+message);
    }

    static <T> void assertArrayEquals(T[] expected, T[] actual) {
        assert Arrays.equals(expected, actual) : Arrays.toString(expected) + " != "+Arrays.toString(actual);
    }

    static <E extends RuntimeException> E assertThrows(Class<E> expected, Runnable test ) {
        try {
            test.run();
            fail("Expected a "+expected.getSimpleName()+" exception but nothing was thrown.");
            throw new IllegalStateException("unreachable");
        } catch (RuntimeException ex) {
            if (expected == ex.getClass()) {
                return expected.cast(ex);
            }
            assertEquals(expected, ex.getClass(), ex.getMessage());
            throw new IllegalStateException("unreachable");
        }
    }
}
