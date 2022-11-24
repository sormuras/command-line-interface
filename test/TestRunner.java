import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Optional;

import static java.util.Arrays.stream;

public interface TestRunner {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Test {}

    default void runTests() throws Exception{
        Object instance = getClass().getConstructor().newInstance();
        stream(getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(JarTests.Test.class))
                .forEach(method -> {
                    try {
                        method.invoke(instance);
                        System.out.println("✓ " + method.getName());
                    } catch (InvocationTargetException ex) {
                        System.out.println("❌ "+ method.getName() +": "+ex.getTargetException().getMessage());
                        ex.getTargetException().printStackTrace();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
    }

    default <T> void assertEquals(T expected, Optional<T> actual) {
        assertEquals(expected, actual.orElseThrow());
    }

    default <T> void assertEquals(T expected, T actual) {
        assert Objects.equals(expected, actual): expected+" != "+actual;
    }
}
