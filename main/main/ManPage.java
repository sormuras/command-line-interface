package main;

public class ManPage {

  // TODO add Map<String,String> with the help texts as argument to the Manual instance in addition
  // to the Command

  public static String help(CommandLine.Factory<?> command) {
    return help(command, 2);
  }

  public static String help(CommandLine.Factory<?> command, int indent) {
    return "";
  }
}
