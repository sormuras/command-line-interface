package test;

import static java.lang.invoke.MethodHandles.lookup;
import static test.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import main.FactorySupport;
import main.Name;
import main.Splitter;
import test.api.JTest;
import test.api.JTest.Test;

class DoubleDashTests {

  public static void main(String... args) {
    JTest.runTests(new DoubleDashTests(), args);
  }

  @Test
  void doubleDashPicocliDemo_Record() {
    record DoubleDashDemo(
        @Name("-v") boolean verbose, @Name("-files") List<String> files, String... params) {}

    String[] args = {"-v", "--", "-files", "file1", "file2"};
    DoubleDashDemo obj = Splitter.of(lookup(), DoubleDashDemo.class).split(args);

    assertEquals(true, obj.verbose());
    assertEquals(List.of(), obj.files());
    assertEquals(List.of("-files", "file1", "file2"), List.of(obj.params()));
  }

  @Test
  void doubleDashPicocliDemo_Proxy() {
    interface DoubleDashDemo {
      @Name("-v")
      boolean verbose();

      @Name("-files")
      List<String> files();

      String[] params();
    }

    String[] args = {"-v", "--", "-files", "file1", "file2"};
    DoubleDashDemo obj = Splitter.of(lookup(), DoubleDashDemo.class).split(args);

    assertEquals(true, obj.verbose());
    assertEquals(List.of(), obj.files());
    assertEquals(List.of("-files", "file1", "file2"), List.of(obj.params()));
  }

  sealed interface DoubleDashDemoSI {
    @Name("-v")
    record Verbose() implements DoubleDashDemoSI {}
    @Name("-files")
    record Files(List<String> names) implements DoubleDashDemoSI {}

    record Params(String... params) implements DoubleDashDemoSI {}
  }

  @Test
  void doubleDashPococliDemo_Sealed() {

    String[] args = {"-v", "--", "-files", "file1", "file2"};
    Splitter<List<DoubleDashDemoSI>> splitter = Splitter.of(FactorySupport.sealedFactory(lookup(), DoubleDashDemoSI.class));

    List<DoubleDashDemoSI> values = splitter.split(args);

    assertEquals(2, values.size() );
    assertEquals(DoubleDashDemoSI.Verbose.class, values.get(0).getClass());
    assertEquals(DoubleDashDemoSI.Params.class, values.get(1).getClass());
    assertEquals(List.of("-files", "file1", "file2"), List.of(((DoubleDashDemoSI.Params)values.get(1)).params()));
  }

  public static class DoubleDashDemo {
    @Name("-v")
    boolean verbose;

    @Name("-files")
    List<String> files;

    String[] params;
  }

  @Test
  void doubleDashPicocliDemo_Pojo() {
    String[] args = {"-v", "--", "-files", "file1", "file2"};
    Splitter<DoubleDashDemo> splitter = Splitter.of(lookup(), DoubleDashDemo.class);
    DoubleDashDemo obj = splitter.split(args);

    assertEquals(true, obj.verbose);
    assertEquals(List.of(), obj.files);
    assertEquals(List.of("-files", "file1", "file2"), List.of(obj.params));
  }

  @Test
  void doubleDashRequired_Record() {
    record DoubleDashRequired(boolean __verbose, Optional<String> dir, String target) {}

    String[] args = {"--verbose", "--", "dir"};
    DoubleDashRequired obj = Splitter.of(lookup(), DoubleDashRequired.class).split(args);

    assertEquals(true, obj.__verbose());
    assertEquals(Optional.empty(), obj.dir());
    assertEquals("dir", obj.target());
  }

  @Test
  void doubleDashRequired_Proxy() {
    interface DoubleDashRequired {
      boolean __verbose();

      Optional<String> dir();

      String target();
    }

    String[] args = {"--verbose", "--", "dir"};
    DoubleDashRequired obj = Splitter.of(lookup(), DoubleDashRequired.class).split(args);

    assertEquals(true, obj.__verbose());
    assertEquals(Optional.empty(), obj.dir());
    assertEquals("dir", obj.target());
  }
}
