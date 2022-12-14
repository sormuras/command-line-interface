package test.jdk;

import static java.lang.invoke.MethodHandles.lookup;
import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertEqualsOptional;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import main.Name;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;
import test.jdk.JarRecordTests.JarOptions.ChangeDirOptions;
import test.jdk.JarRecordTests.JarOptions.ReleaseOptions;

public class JarRecordTests {

  public static void main(String... args) {
    JTest.runTests(new JarRecordTests(), args);
  }

  record JarOptions(
      @Name({"-c", "--create"}) boolean create,
      @Name({"-i", "--generate-index"}) Optional<String> generateIndex,
      @Name({"-t", "--list"}) boolean list,
      @Name({"-u", "--update"}) boolean update,
      @Name({"-x", "--extract"}) boolean extract,
      @Name({"-d", "--describe-module"}) boolean describeModule,
      @Name({"-C"}) Optional<ChangeDirOptions> changeDir,
      @Name({"-f", "--file"}) Optional<String> file,
      @Name("--release") Optional<ReleaseOptions> release,
      @Name({"-v", "--verbose"}) boolean verbose,
      @Name({"-e", "--main-class"}) Optional<String> mainClass,
      @Name({"-m", "--manifest"}) Optional<String> manifest,
      @Name({"-M", "--no-manifest"}) boolean noManifest,
      @Name("--module-version") Optional<String> moduleVersion,
      @Name("--hash-modules") Optional<String> hashModules,
      @Name({"-p", "--module-path"}) Optional<String> modulePath,
      @Name({"-0", "--no-compress"}) boolean noCompress,
      @Name("--date") Optional<ZonedDateTime> date,
      @Name({"-?", "-h", "--help"}) boolean help,
      @Name("--help:compat") boolean helpCompat,
      @Name("--help-extra") boolean helpExtra,
      @Name("--version") boolean version,
      Path... files) {

    record ChangeDirOptions(String dir, Path file) {}

    record ReleaseOptions(Integer version, @Name("-C") List<ChangeDirOptions> Cs) {}
  }

  private static JarOptions splitInput(String line) {
    return Splitter.of(lookup(), JarOptions.class).split(line.split("\\s+"));
  }

  @Test
  void example1() {
    var options = splitInput("--create --file classes.jar Foo.class Bar.class");
    assertEquals(true, options.create());
    assertEqualsOptional("classes.jar", options.file());
    assertEquals(List.of(Path.of("Foo.class"), Path.of( "Bar.class")), List.of(options.files()));
  }

  @Test
  void example2() {
    var options =
        splitInput(
            "--create --date=\"2021-01-06T14:36:00+02:00\" --file=classes.jar Foo.class Bar.class");
    assertEquals(true, options.create());
    assertEqualsOptional(ZonedDateTime.parse("2021-01-06T14:36:00+02:00"), options.date());
    assertEqualsOptional("classes.jar", options.file());
    assertEquals(List.of(Path.of("Foo.class"), Path.of("Bar.class")), List.of(options.files()));
  }

  @Test
  void example3() {
    var options = splitInput("--create --file classes.jar --manifest mymanifest -C foo/ .");
    assertEquals(true, options.create());
    assertEqualsOptional("classes.jar", options.file());
    assertEqualsOptional("mymanifest", options.manifest());
    assertEqualsOptional(new ChangeDirOptions("foo/", Path.of(".")), options.changeDir());
  }

  @Test
  void example4() {
    var options =
        splitInput(
            "--create --file foo.jar --main-class com.foo.Main --module-version 1.0 -C foo/classes"
                + " resources");
    assertEquals(true, options.create());
    assertEqualsOptional("foo.jar", options.file());
    assertEqualsOptional("com.foo.Main", options.mainClass());
    assertEqualsOptional("1.0", options.moduleVersion());
    var cOptions = options.changeDir().orElseThrow();
    assertEquals("foo/classes", cOptions.dir());
    assertEquals(Path.of("resources"), cOptions.file());
  }

  @Test
  void example5() {
    var options =
        splitInput(
            """
            --create --file foo.jar --main-class com.foo.Hello -C classes .
            --release 10 -C classes-10 .""");
    assertEquals(true, options.create());
    assertEqualsOptional("foo.jar", options.file());
    assertEqualsOptional("com.foo.Hello", options.mainClass());
    assertEqualsOptional(new ChangeDirOptions("classes", Path.of(".")), options.changeDir());
    assertEqualsOptional(
        new ReleaseOptions(10, List.of(new ChangeDirOptions("classes-10", Path.of(".")))),
        options.release());
  }
}
