import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class generate {
  private record FileContent(Set<String> imports, List<String> lines) {}

  private static FileContent gatherAllFiles() throws IOException {
    var imports = new TreeSet<String>();
    var lines = new ArrayList<String>();
    try (var files = Files.newDirectoryStream(Path.of("main", "main"), "*.java")) {
      for (var file : files) {
        var topLevel = true;
        var insideComment = false;
        for (var line : Files.readAllLines(file)) {
          if (line.startsWith("package ")) continue;
          if (line.startsWith("import ")) {
            imports.add(line);
            continue;
          }
          if (topLevel) {
            if (line.contains("/*")) {
              insideComment = true;
            }
            if (line.contains("*/")) {
              insideComment = false;
            }
            boolean classDeclaration;
            if (!insideComment &&
                ((classDeclaration = line.contains("class")) || line.contains("interface") || line.contains("enum") || line.contains("record"))) {
              topLevel = false;
              if (classDeclaration) {
                line = "static " + line;
              }
            }
          }
          lines.add("  " + line);
        }
      }
    }
    return new FileContent(imports, lines);
  }

  private static FileContent applyTemplate(List<String> template, FileContent content) {
    var imports = new TreeSet<>(content.imports());
    var lines = new ArrayList<String>();
    for (var line : template) {
      if (line.startsWith("package ")) continue;
      if (line.startsWith("import ")) {
        imports.add(line);
        continue;
      }
      if (line.contains("<insert content here>")) {
        lines.addAll(content.lines());
        continue;
      }
      lines.add(line);
    }
    return new FileContent(imports, lines);
  }

  private static void writeFile(FileContent content, Path file) throws IOException{
    var source = new ArrayList<String>();
    source.add("// Generated on " + ZonedDateTime.now());
    source.addAll(content.imports());
    source.add("");
    source.addAll(content.lines());

    Files.write(
        file,
        source,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  public static void main(String... args) throws IOException {
    var template = """
        public final class CommandLineInterface {
          private CommandLineInterface() {
            throw new AssertionError();
          }
          
          // <insert content here>
        }
        """;

    var content  = gatherAllFiles();
    var target = Files.createDirectories(Path.of("generated"));

    var commandLineInterface = applyTemplate(template.lines().toList(), content);
    writeFile(commandLineInterface, target.resolve("CommandLineInterface.java"));

    var optionizer = applyTemplate(Files.readAllLines(Path.of("template", "optionizer.java")), content);
    writeFile(optionizer, target.resolve("optionizer.java"));
  }
}
