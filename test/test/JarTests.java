package test;

import static java.lang.invoke.MethodHandles.lookup;
import static test.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import main.CommandLineInterface;
import main.CommandLineInterface.Name;

class JarTests implements JTest {

  public static void main(String... args) {
    new JarTests().runTests(args);
  }

  record JarCOptions(String dir, String file) {}

  record JarReleaseOptions(String version, @Name("-C") List<JarCOptions> Cs) {}

  record JarOptions(
      @Name({"-c", "--create"}) boolean create,
      @Name({"-i", "--generate-index"}) Optional<String> generateIndex,
      @Name({"-t", "--list"}) boolean list,
      @Name({"-u", "--update"}) boolean update,
      @Name({"-x", "--extract"}) boolean extract,
      @Name({"-d", "--describe-module"}) boolean describeModule,
      @Name({"-C"}) Optional<JarCOptions> changeDir,
      @Name({"-f", "--file"}) Optional<String> file,
      @Name("--release") Optional<JarReleaseOptions> release,
      @Name({"-v", "--verbose"}) boolean verbose,
      @Name({"-e", "--main-class"}) Optional<String> mainClass,
      @Name({"-m", "--manifest"}) Optional<String> manifest,
      @Name({"-M", "--no-manifest"}) boolean noManifest,
      @Name("--module-version") Optional<String> moduleVersion,
      @Name("--hash-modules") Optional<String> hashModules,
      @Name({"-p", "--module-path"}) Optional<String> modulePath,
      @Name({"-0", "--no-compress"}) boolean noCompress,
      @Name("--date") Optional<String> date,
      @Name({"-?", "-h", "--help"}) boolean help,
      @Name("--help:compat") boolean helpCompat,
      @Name("--help-extra") boolean helpExtra,
      @Name("--version") boolean version,
      String... files) {}

  private static JarOptions parseInput(String line) {
    return CommandLineInterface.parser(lookup(), JarOptions.class).parse(line.split("\\s+"));
  }

  @Test
  void example1() {
    JarOptions options = parseInput("--create --file classes.jar Foo.class Bar.class");
    assertEquals(true, options.create());
    Assertions.assertEqualsOptional("classes.jar", options.file());
    assertEquals(List.of("Foo.class", "Bar.class"), List.of(options.files()));
  }

  @Test
  void example2() {
    JarOptions options =
        parseInput(
            "--create --date=\"2021-01-06T14:36:00+02:00\" --file=classes.jar Foo.class Bar.class");
    assertEquals(true, options.create());
    Assertions.assertEqualsOptional("\"2021-01-06T14:36:00+02:00\"", options.date());
    Assertions.assertEqualsOptional("classes.jar", options.file());
    assertEquals(List.of("Foo.class", "Bar.class"), List.of(options.files()));
  }

  @Test
  void example3() {
    JarOptions options = parseInput("--create --file classes.jar --manifest mymanifest -C foo/ .");
    assertEquals(true, options.create());
    Assertions.assertEqualsOptional("classes.jar", options.file());
    Assertions.assertEqualsOptional("mymanifest", options.manifest());
    Assertions.assertEqualsOptional(new JarCOptions("foo/", "."), options.changeDir());
  }

  @Test
  void example4() {
    JarOptions options =
        parseInput(
            "--create --file foo.jar --main-class com.foo.Main --module-version 1.0 -C foo/classes"
                + " resources");
    assertEquals(true, options.create());
    Assertions.assertEqualsOptional("foo.jar", options.file());
    Assertions.assertEqualsOptional("com.foo.Main", options.mainClass());
    Assertions.assertEqualsOptional("1.0", options.moduleVersion());
    JarCOptions cOptions = options.changeDir().orElseThrow();
    assertEquals("foo/classes", cOptions.dir());
    assertEquals("resources", cOptions.file());
  }

  @Test
  void example5() {
    JarOptions options =
        parseInput(
            "--create --file foo.jar --main-class com.foo.Hello -C classes . --release 10 -C"
                + " classes-10 .");
    assertEquals(true, options.create());
    Assertions.assertEqualsOptional("foo.jar", options.file());
    Assertions.assertEqualsOptional("com.foo.Hello", options.mainClass());
    Assertions.assertEqualsOptional(new JarCOptions("classes", "."), options.changeDir());
    Assertions.assertEqualsOptional(
        new JarReleaseOptions("10", List.of(new JarCOptions("classes-10", "."))),
        options.release());
  }
}
