package main;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allow to specify {@link Option#names() the names} of an option defined as a record component.
 * <p>
 * By default, the name of the option is derived from the name of the record component,
 * with the '_' replaced by '-'.
 * <pre>
 *  record Command (
 *    boolean __verbose
 *  }
 *  </pre>
 *
 * This annotation allows to specify a name thos is not a vid Java identifier
 * <pre>
 *  record Command (
 *    &#064;Name("-v")
 *    boolean verbose
 *  }
 * </pre>
 *
 * or several names
 * <pre>
 *  record Command (
 *    &#064;Name({ "-v", "--verbose" })
 *    boolean verbose
 *  }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Name {
  /**
   * Returns the option names.
   * @return the option names.
   */
  String[] value();
}
