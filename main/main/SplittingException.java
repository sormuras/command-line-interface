package main;

import java.util.stream.Stream;

/**
 * Exception that occurs when splitting the arguments.
 *
 * @see Splitter#split(Stream)
 */
public class SplittingException extends RuntimeException {
  /**
   * Creates a splitting exception with a message and a cause.
   *
   * @param message a message.
   * @param cause a cause.
   */
  public SplittingException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a splitting exception with a message.
   *
   * @param message a message.
   */
  public SplittingException(String message) {
    super(message);
  }

  /**
   * Creates a splitting exception with a cause.
   *
   * @param cause a cause.
   */
  public SplittingException(Throwable cause) {
    super(cause);
  }
}
