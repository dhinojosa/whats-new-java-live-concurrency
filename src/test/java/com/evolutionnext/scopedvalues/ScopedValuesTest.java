package com.evolutionnext.scopedvalues;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.StructureViolationException;
import java.util.concurrent.StructuredTaskScope;

public class ScopedValuesTest {
    private static final ScopedValue<String> KEY = ScopedValue.newInstance();
    private static final ScopedValue<String> GREETING_KEY = ScopedValue.newInstance();
    private static final ScopedValue<String> FAREWELL_KEY = ScopedValue.newInstance();

    @Test
    void testScopeAndValue() {
        ScopedValue.where(KEY, "Hello").run(() ->
            printThreadAndKey("Inside of the scope"));

        printThreadAndKey("Outside of the scope");
    }

    @Test
    void testExtremeNesting() {
        ScopedValue.where(KEY, "Do").run(() ->
            ScopedValue.where(KEY, "Ra" + KEY.get()).run(() ->
                ScopedValue.where(KEY, "Me" + KEY.get()).run(() ->
                    ScopedValue.where(KEY, "Fa" + KEY.get()).run(() ->
                        ScopedValue.where(KEY, "So" + KEY.get()).run(() ->
                            ScopedValue.where(KEY, "La" + KEY.get()).run(() ->
                                ScopedValue.where(KEY, "Te" + KEY.get()).run(() ->
                                    printThreadAndKey("Inside the nest")
                                )))))));
        printThreadAndKey("Outside of the chain");
    }



    private static void printThreadAndKey(String label) {
        try {
            System.out.format("%s: %s contains key \"%s\"\n", label, Thread.currentThread(), KEY.get());
        } catch (NoSuchElementException e) {
            System.out.format("%s: %s has no key!\n", label, Thread.currentThread());
        }
    }



    /**
     * The following will not work. You should only use
     * Structured Concurrency or leave it in the same thread.
     */
    @Test
    void testScopeValueNotAvailableInVirtualThread() {
        ScopedValue.where(KEY, "Hello").run(() -> {
            try {
                //Where is it? It is not here, stand by for structured concurrency!
                Thread start = Thread
                    .ofVirtual()
                    .name("in-scope")
                    .start(() -> printThreadAndKey("Inside of virtual-thread"));
                start.join(Duration.ofSeconds(1));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        printThreadAndKey("Outside of the scope");
    }

    /**
     * Demonstrates the usage of nested scoped values and virtual threads.
     * <p>
     * The method sets a scoped value with the key "Hello" and value "KEY".
     * It then creates a virtual thread named "in-scope"
     * inside this scoped value.
     * Within the virtual thread, a method named `printThreadAndKey` is called to print the
     * current thread and the value associated with the scoped value key.
     * After exiting the virtual thread, the method
     * `printThreadAndKey` is called again to print the thread and key value,
     * this time outside the scope of the nested scoped value.
     */
    @Test
    void testScopeValueFirst() {
        ScopedValue.where(KEY, "Hello").run(() -> {
            try (var scope =
                     StructuredTaskScope.open(StructuredTaskScope.Joiner.anySuccessfulOrThrow())) {
                scope.fork(() -> {
                    printThreadAndKey("Inside fork");
                    return null;
                });
                scope.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            printThreadAndKey("Inside of the scope");
        });

        printThreadAndKey("Outside of the scope");
    }

    /**
     * The following will not work and will throw a StructureViolationException
     * This will occur when we `fork` the call. `ScopedValue` should come first,
     * and then the `StructuredTaskScope`
     */
    @Test
    void testScopeValueLast() {
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.anySuccessfulOrThrow())) {
            ScopedValue.where(KEY, "Hello").run(() -> {
                try {
                    scope.fork(() -> {
                        printThreadAndKey("Inside fork");
                        return null;
                    });
                    scope.join();
                    printThreadAndKey("Inside of the scope");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (StructureViolationException sve) {
            System.out.println("StructureViolationException: " + sve.getMessage());
        }

        printThreadAndKey("Outside of the scope");
    }

    private static Runnable printThreadAndGreetingKey() {
        return () -> {
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow())) {
                var result = scope.fork(() -> String.format("%s!", GREETING_KEY.get()));
                scope.join();
                System.out.println(result.get());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Test
    void whereAndGetTest() {
        var outerResult = ScopedValue.where(GREETING_KEY, "Hello").get(GREETING_KEY);
        System.out.println(outerResult);
    }

    @Test
    void whereAndCallTest() throws Exception {
        String outerResult = ScopedValue.where(GREETING_KEY, "Bon Jour").call(() -> {
            try (var scope = StructuredTaskScope.open()) {
                var result = scope.fork(() -> String.format("%s!", GREETING_KEY.get()));
                scope.join();
                return result.get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println(outerResult);
    }

    @Test
    void whereAndRunTest() {
        ScopedValue.where(GREETING_KEY, "Здравейте")
            .run(printThreadAndGreetingKey());
    }



    @Test
    void doubleWhereAndCallTest() throws Exception {
        String outerResult = ScopedValue.where(GREETING_KEY, "Γειά σου")
            .where(FAREWELL_KEY, "Antio sas").call(() -> {
                try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow())) {
                    var helloSubtask = scope.fork(() -> String.format("%s!", GREETING_KEY.get()));
                    var goodbyeSubtask = scope.fork(() -> String.format("%s!", FAREWELL_KEY.get()));
                    scope.join();
                    return String.format("%s %s", helloSubtask.get(), goodbyeSubtask.get());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        System.out.println(outerResult);
    }

    @Test
    void isBoundTest() throws Exception {
        String outerResult = ScopedValue.where(GREETING_KEY, "你好").call(() -> {
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow())) {
                var helloSubtask = scope.fork(() -> String.format("%s!", GREETING_KEY.get()));
                scope.join();
                GREETING_KEY.isBound();
                return String.format("%s", helloSubtask.get());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println(outerResult);
    }
}
