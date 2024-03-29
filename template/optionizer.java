import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;

public class optionizer {
  // <insert content here>


  // specific code below

  private static String name(Option<?> option) {
    return option.names().iterator().next().replace('-', '_');
  }

  private static Optional<String> methodRef(ConverterResolver.ConverterMirror mirror) {
    if (mirror.implClass.equals(ConverterResolver.class.getName())) {
      return switch(mirror.implMethodName()) {
        case "basic" -> Optional.empty();
        case "enumerated" -> Optional.of(mirror.captures().get(0) + "::valueOf");
        case "reflected", "unwrap" -> methodRef((ConverterResolver.ConverterMirror) mirror.captures().get(0));
        default -> throw new AssertionError("unknown mirror inside ConverterResolver");
      };
    }
    return Optional.of(mirror.implClass() + "::" + mirror.implMethodName);
  }

  private static Converter<?,?> converter(Option<?> option) {
    return switch (option.type()) {
      case BRANCH -> ((Option.Branch<?>) option).converter;
      case FLAG -> ((Option.Flag) option).converter;
      case SINGLE -> ((Option.Single<?>) option).converter;
      case REPEATABLE -> ((Option.Repeatable<?>) option).converter;
      case REQUIRED -> ((Option.Required<?>) option).converter;
      case VARARGS -> ((Option.Varargs<?>) option).converter;
    };
  }

  private static String appendConvert(Optional<ConverterResolver.ConverterMirror> mirror, String expr) {
    return mirror.flatMap(m -> methodRef(m)).map(ref -> expr + ".convert(%s)".formatted(ref)).orElse(expr);
  }

  private static Optional<Class<?>> findRecordClass(ClassLoader classLoader, Schema<?> schema) {
    var mirror = ConverterResolver.ConverterMirror.of(MethodHandles.lookup(), schema.finalizer);
    if (mirror.isEmpty()) {
      return Optional.empty();
    }
    var captures = mirror.orElseThrow().captures();
    if (captures.isEmpty()) {
      return Optional.empty();
    }
    var schemaClassName = (String) captures.get(0);
    try {
      return Optional.of(classLoader.loadClass(schemaClassName));
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  private static final IdentityHashMap<Schema<?>, String> SCHEMA_MAP = new IdentityHashMap<>();

  private static String schemaName(Schema<?> schema) {
    var size = SCHEMA_MAP.size();
    return SCHEMA_MAP.computeIfAbsent(schema, k -> "schema" + size);
  }

  private static String finalizer(ClassLoader classloader, Schema<?> schema) {
    var recordClass = findRecordClass(classloader, schema);
    if (recordClass.isEmpty()) {
      return "/*FIXME*/";
    }
    var clazz = recordClass.orElseThrow();
    var recordComponents = clazz.getRecordComponents();
    return "list -> new " + clazz.getSimpleName() +
        IntStream.range(0, recordComponents.length)
            .mapToObj(i -> "(" + recordComponents[i].getGenericType().getTypeName() + ") list.get(" + i + ")")
            .collect(Collectors.joining(", ", "(", ")"));
  }

  private static void generateNestedSchema(ClassLoader classloader, Schema<?> schema, Lookup lookup, ConverterResolver resolver, PrintStream out) {
    // scan nested schema first
    for(var option: schema.options) {
      var nestedSchema = option.nestedSchema();
      if (nestedSchema != null) {
        generateNestedSchema(classloader, nestedSchema, lookup, resolver, out);
      }
    }

    // print schema
    for(var option: schema.options) {
      var names = option.names().stream().map(n -> "\"" + n + "\"").collect(Collectors.joining(", ", "(", ")"));
      var expr = "Option." + option.type().name().toLowerCase(Locale.ROOT) + names;
      if (!option.help().isEmpty()) {
        expr = expr + ".help(\"%s\")".formatted(option.help());
      }
      expr = appendConvert(ConverterResolver.ConverterMirror.of(lookup, converter(option)), expr);
      if (option.nestedSchema() != null) {
        expr = expr + ".nestedSchema(%s)".formatted(schemaName(option.nestedSchema()));
      }
      var text = "var " + name(option) + " = " + expr + ';';
      out.print(text.indent(4));
    }

    var schemaText = """
        var %s = new Schema<>(
            List.of(%s),
            %s
            );\
        """
        .formatted(
            schemaName(schema),
            schema.options.stream().map(optionizer::name).collect(Collectors.joining(", ")),
            finalizer(classloader, schema));
    System.out.println(schemaText.indent(4));
  }

  private static void generateClassFile(ClassLoader classloader, Schema<?> schema, Lookup lookup, ConverterResolver resolver, PrintStream out) {
    System.out.println("// generated by the optionizer");
    var options = schema.options;
    out.print("""
        import CommandLineInterface.Option;
        
        class Main {
          public static void main(String[] args) {
        """);

    generateNestedSchema(classloader, schema, lookup, resolver, out);

    out.print("""
          }
        }
        """);
  }

  // test with: java ./generated/optionizer.java classes/test/test/AssortedTests\$7Options.class
  public static void main(String... args) throws Exception {
    record ArgParameters(
        @Help("an optional output file (not implemented yet !)")
        Optional<Path> output,
        @Help("a class file (a .class file)")
        String fileName
    ) {}

    var lookup = MethodHandles.lookup();
    var splitter = Splitter.of(lookup, ArgParameters.class);
    ArgParameters argParameters;
    try {
      argParameters = splitter.split(args);
    } catch(SplittingException e) {
      System.err.println("error " + e.getMessage());
      System.err.println("help : ");
      System.err.println(Manual.help(splitter.schema()));
      System.exit(1);
      return;
    }

    var path = Path.of(argParameters.fileName);
    var bytecode = Files.readAllBytes(path);
    var clazz = new ClassLoader() {
      Class<?> define(byte[] bytecode) {
        return super.defineClass(null, bytecode, 0, bytecode.length);
      }
    }.define(bytecode);

    var packagePath = Path.of(clazz.getPackageName().replace('.', '/'));
    var nameCount = packagePath.getNameCount();
    var root = path;
    for(var i = 0; i <= nameCount; i++) {
      root = root.getParent();
    }

    var classLoader = new URLClassLoader(new URL[] { root.toUri().toURL() });
    try {
      clazz = classLoader.loadClass(clazz.getName());
    } catch(ClassNotFoundException e) {
      // the class is not part of a classpath, dependencies will not be resolved
    }

    if (!clazz.isRecord()) {
      throw new IllegalStateException("class " + argParameters.fileName + "is not a record");
    }

    var resolver = ConverterResolver.defaultResolver();
    var schema = Splitter.of(lookup, clazz.asSubclass(Record.class), resolver).schema();

    generateClassFile(clazz.getClassLoader(), schema, lookup, resolver, System.out);
  }
}