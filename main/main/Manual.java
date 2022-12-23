package main;

import java.util.Comparator;
import java.util.StringJoiner;

import static java.util.Objects.requireNonNull;

public class Manual {

    //TODO add Map<String,String> with the help texts as argument to the Manual instance in addition to the Command

  public static String help(Command<?> command) {
    return help(command, 2);
  }

  public static String help(Command<?> command, int indent) {
    return "";
  }
}
