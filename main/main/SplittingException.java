package main;

import java.io.Serial;
import java.util.stream.Stream;

/**
 * Exception that occurs when splitting the arguments.
 *
 * @see Splitter#split(Stream)
 */
public final class SplittingException extends RuntimeException {

  @Serial private static final long serialVersionUID = 6958903301611893552L;

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
