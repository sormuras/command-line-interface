import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.TreeSet;

public class generate {
  public static void main(String... args) throws Exception {
    var imports = new TreeSet<String>();
    var lines = new ArrayList<String>();

    try (var files = Files.newDirectoryStream(Path.of("main", "main"), "*.java")) {
      for (var file : files) {
        for (var line : Files.readAllLines(file)) {
          if (line.startsWith("package ")) continue;
          if (line.startsWith("import ")) {
            imports.add(line);
            continue;
          }
          lines.add("  " + line);
        }
      }
    }

    var source = new ArrayList<String>();
    source.add("// Generated on " + ZonedDateTime.now());
    source.addAll(imports);
    source.add("""
               public interface CommandLineInterface {
               """);
    source.addAll(lines);
    source.add("""
               }
               """);

    var target = Files.createDirectories(Path.of("generated"));
    Files.write(
        target.resolve("CommandLineInterface.java"),
        source,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
