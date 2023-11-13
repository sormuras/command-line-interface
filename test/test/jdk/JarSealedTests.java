package test.jdk;

import main.Name;
import test.api.JTest;
import test.api.JTest.Test;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import static test.api.Assertions.assertEquals;

public class JarSealedTests {

  public static void main(String... args) {
    JTest.runTests(new JarSealedTests(), args);
  }


  /**
   * Records has an implicit @Name of:
   * lower case initial character (short name) and kebab-case (long name).
   * An empty array in @Name means it is not an option but command line parameters (often trailing)
   */
  sealed interface JarOpts {

    record Create() implements JarOpts {}
    @Name({"-i", "--generate-index"}) record GenerateIndex(String name) implements JarOpts {}
    @Name({"-t", "--list"})           record List() implements JarOpts {}
                                      record Update() implements JarOpts {}
    @Name({"-x", "--extract"})        record Extract() implements JarOpts {}
                                      record DescribeModule() implements JarOpts {}
    @Name({"-C"})                     record ChangeDirOption(String dir, Path path) implements JarOpts {}
                                      record File(Path jarFile) implements JarOpts {}

    // To be discussed.
    @Name("--release")                record Release(int version,
                                                     @Name({"-C"}) java.util.List<ChangeDirOption> options) implements JarOpts {}

    @Name({"-e", "--main-class"})     record Verbose(Path jarFile) implements JarOpts {}
    @Name({"-e", "--main-class"})     record MainClass(String name) implements JarOpts {}
                                      record Manifest(String name) implements JarOpts {}
    @Name({"-M", "--no-manifest"})    record NoManifest() implements JarOpts {}
    @Name("--module-version")         record ModuleVersion(String version) implements JarOpts {}
    @Name("--hash-modules")           record HashModules(String hash) implements JarOpts {}
    @Name({"-p", "--module-path"})    record ModulePath(String modulePath) implements JarOpts {}
    @Name({"-0", "--no-compress"})    record NoCompress() implements JarOpts {}
    @Name("--date")                   record Date(ZonedDateTime date) implements JarOpts {}
    @Name({"-?", "-h", "--help"})     record Help() implements JarOpts {}
    @Name("--help:compat")            record HelpCompat() implements JarOpts {}
    @Name("--help-extra")             record HelpExtra() implements JarOpts {}
    @Name("--version")                record Version() implements JarOpts {}
    @Name({})                         record Arg(Path path){}
  }

  /*

      @Name("--release")
    // To be discussed.
    record Release(int version, ReleaseChangeDirOptions options) implements JarOpts {

      @Name({"-C"}) sealed interface ReleaseChangeDirOptions {
        record ReleaseChangeDirOption(String dir, Path path) implements ReleaseChangeDirOptions {}
      }
    }

   */


  private static List<JarOpts> splitInput(String line) {
    // return Splitter.of(lookup(), JarOptions.class).split(line.split("\\s+"));
    return List.of();
  }

  @Test
  void example1() {
    var options = splitInput("--create --file classes.jar Foo.class Bar.class");
    var expected = List.of(
       new JarOpts.Create(),
       new JarOpts.File(Path.of("classes.jar")),
       new JarOpts.Arg(Path.of("Foo.class")),
       new JarOpts.Arg(Path.of("Bar.class"))
    );
    assertEquals(expected, options);
  }

  @Test
  void example2() {
    var options =
        splitInput("--create --date=\"2021-01-06T14:36:00+02:00\" --file=classes.jar Foo.class Bar.class");

    var expected = List.of(
            new JarOpts.Create(),
            new JarOpts.Date(ZonedDateTime.parse("2021-01-06T14:36:00+02:00")),
            new JarOpts.File(Path.of("classes.jar")),
            new JarOpts.Arg(Path.of("Foo.class")),
            new JarOpts.Arg(Path.of("Bar.class"))
    );
    assertEquals(expected, options);
  }

  @Test
  void example3() {
    var options = splitInput("--create --file classes.jar --manifest mymanifest -C foo/ .");

    var expected = List.of(
            new JarOpts.Create(),
            new JarOpts.File(Path.of("classes.jar")),
            new JarOpts.Manifest("mymanifest"),
            new JarOpts.ChangeDirOption("foo/", Path.of("."))
    );

    assertEquals(expected, options);
  }

  @Test
  void example4() {
    var options =
        splitInput(
            "--create --file foo.jar --main-class com.foo.Main --module-version 1.0 -C foo/classes"
                + " resources");

    var expected = List.of(
            new JarOpts.Create(),
            new JarOpts.File(Path.of("foo.jar")),
            new JarOpts.MainClass("com.foo.Main"),
            new JarOpts.ModuleVersion("1.0"),
            new JarOpts.ChangeDirOption("foo/classes", Path.of("resources"))
    );

    assertEquals(expected, options);
  }

  @Test
  void example5() {
    var options =
            splitInput(
                    """
                            --create --file foo.jar --main-class com.foo.Hello -C classes .
                            --release 10 -C classes-10 .""");

    var expected = List.of(
            new JarOpts.Create(),
            new JarOpts.File(Path.of("foo.jar")),
            new JarOpts.MainClass("com.foo.Hello"),
            new JarOpts.ChangeDirOption("classes", Path.of(".")),
            new JarOpts.Release(10, List.of(new JarOpts.ChangeDirOption("classes-10", Path.of("."))))
    );

    assertEquals(expected, options);
  }

}
