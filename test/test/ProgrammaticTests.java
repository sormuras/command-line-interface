package test;

import static test.api.Assertions.assertEquals;
import static test.api.Assertions.assertFalse;
import static test.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import main.CommandLine;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

class ProgrammaticTests {

  public static void main(String... args) {
    JTest.runTests(new ProgrammaticTests(), args);
  }

  @Test
  void testProgrammaticAssembly() {
    class SubOptions {
      Path dir;

      void setDir(Path dir) {
        this.dir = dir;
      }
    }
    class Options {
      boolean y;
      String x;
      Optional<SubOptions> s;

      public void setY(boolean y) {
        this.y = y;
      }

      public void setX(String x) {
        this.x = x;
      }

      public void setS(Optional<SubOptions> s) {
        this.s = s;
      }
    }
    CommandLine.Factory<Options> cmd =
        CommandLine.builder(Options::new)
            .addFlag("y", Options::setY, "--f")
            .addRequired("x", Options::setX)
            .addOptional(
                "s",
                SubOptions.class,
                Options::setS,
                CommandLine.builder(SubOptions::new)
                    .addRequired("dir", Path.class, Path::of, SubOptions::setDir)
                    .build(),
                "-s")
            .build();

    Options options = Splitter.of(cmd).split("--f", "hello");

    assertTrue(options.y);
    assertEquals("hello", options.x);
    assertTrue(options.s.isEmpty());

    Options options2 = Splitter.of(cmd).split("-s", "filename", "world");

    assertFalse(options2.y);
    assertEquals("world", options2.x);
    assertEquals(Path.of("filename"), options2.s.orElseThrow().dir);
  }
}
