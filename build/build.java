import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.spi.ToolProvider;

class build {
  public static void main(String... args) throws Exception {
    tool("javac                       --module main --module-source-path . -d classes");
    tool("javac --module-path classes --module test --module-source-path . -d classes");
    java("      --module-path classes --module test/test.AllTests");
  }

  static void tool(String line) {
    var words = line.trim().split("\\s+");
    System.out.println(List.of(words));
    var name = words[0];
    var args = Arrays.copyOfRange(words, 1, words.length);
    var code = ToolProvider.findFirst(name).orElseThrow().run(System.out, System.err, args);
    if (code != 0) throw new RuntimeException();
  }

  static void java(String line) throws Exception {
    var java = Path.of(System.getProperty("java.home"), "bin", "java" /*.exe*/);
    var process = new ProcessBuilder(java.toString());
    process.command().addAll(List.of(line.trim().split("\\s+")));
    System.out.println(process.command());
    var code = process.inheritIO().start().waitFor();
    if (code != 0) throw new RuntimeException();
  }
}
