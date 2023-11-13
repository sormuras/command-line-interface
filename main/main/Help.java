package main;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allow to specify {@link Option#help()} the help text} of an option defined as a record component.
 * <p>
 * This annotation allows to specify the help text as a string
 * <pre>
 *  record Command (
 *    &#064;Help("this is an hep text")
 *    boolean verbose
 *  }
 * </pre>
 *
 * or using several strings that will be joined using a space.
 * <pre>
 *  record Command (
 *    &#064;Help({ "this is an hep text", "on several parts"})
 *    boolean verbose
 *  }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.TYPE})
public @interface Help {
  /**
   * Returns the option help texts.
   * @return the option help texts.
   */
  String[] value();
}
