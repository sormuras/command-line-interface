package main;

public class Manual {

  // TODO add Map<String,String> with the help texts as argument to the Manual instance in addition
  // to the Command

  public static String help(Command.Factory<?> command) {
    return help(command, 2);
  }

  public static String help(Command.Factory<?> command, int indent) {
    return "";
  }
}
