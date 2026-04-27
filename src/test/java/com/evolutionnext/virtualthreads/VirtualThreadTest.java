package com.evolutionnext.virtualthreads;

import com.sun.management.HotSpotDiagnosticMXBean;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.concurrent.*;

public class VirtualThreadTest {

    private Recording recording;
    private String testName;

    @BeforeEach
    void setUp(TestInfo testInfo) throws IOException, ParseException {
        testName = testInfo.getTestClass().map(Class::getSimpleName).orElse("UnknownClass")
                   + "_" +
                   testInfo.getTestMethod().map(Method::getName).orElse("UnknownMethod");
        Configuration profile = Configuration.getConfiguration("profile");
        recording = new Recording(profile);
        recording.enable("jdk.VirtualThreadStart");
        recording.enable("jdk.VirtualThreadEnd");
        recording.enable("jdk.ExecutionSample");
        recording.enable("jdk.ObjectAllocationInNewTLAB");
        recording.enable("jdk.ObjectAllocationOutsideTLAB");
        recording.setName(testName);
        recording.start();
    }

    @Test
    void testThreadOldWay() throws InterruptedException {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1200);
                    System.out.println("Running inside of a thread");
                } catch (InterruptedException e) {
                    System.err.println("Thread interrupted");
                }
            }
        });
        t.start();
        t.join(); //wait here until t is done
    }
    @Test
    void testVirtualThreadUnstarted() throws InterruptedException {
        Thread unstartedThread = Thread.ofVirtual().unstarted(
            () -> {
                System.out.printf("Starting Thread in %s", Thread.currentThread());
                try {
                    System.out.println("Runnable class: " + this.getClass());
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

        System.out.println("Starting Thread");
        unstartedThread.start();

        System.out.println("Waiting for Thread to finish");
        unstartedThread.join();
    }

    @Test
    void testVirtualThreadStarted() throws InterruptedException {
        Thread startedThread = Thread.ofVirtual().start(() -> {
            System.out.printf("Starting Thread in %s", Thread.currentThread());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        System.out.println("Waiting for Thread to finish");
        startedThread.join();
    }

    @Test
    void testVirtualThreadNamedAndStarted() throws InterruptedException {
        Thread startedThread = Thread.ofVirtual().name("custom-virtual-thread").start(() -> {
            System.out.printf("Starting Thread in %s", Thread.currentThread());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        System.out.println("Waiting for Thread to finish");
        startedThread.join();
    }

    @Test
    void testVirtualThreadInTryWithResources() {
        try (ExecutorService executorService =
                 Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Long> future = executorService.submit(() -> {
                Thread currentThread = Thread.currentThread();
                System.out.printf("Thread %s in the future", currentThread);
                System.out.format("Is our thread virtual? %b\n", currentThread.isVirtual());
                return 10L;
            });

            System.out.println("Submitted");
            System.out.println(future.get(4, TimeUnit.SECONDS));

        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testVirtualThreadWithAPoolFactory() throws ExecutionException, InterruptedException {
        ThreadFactory tf =
            Thread.ofVirtual().name("virtual-factory").factory();

        Thread thread = tf.newThread(() -> {
            System.out.format("Running in Thread: %s\n",
                Thread.currentThread());
            System.out.format("Is our thread virtual? %b\n",
                Thread.currentThread().isVirtual());
        });

        thread.start();
        thread.join();
    }

    @Test
    void testVirtualThreadWithAPoolFactoryAndExecutorService() throws ExecutionException, InterruptedException {
        ThreadFactory tf =
            Thread.ofVirtual().name("virtual-with-executors").factory();
        try (ExecutorService executorService = Executors.newThreadPerTaskExecutor(tf)) {
            Future<Integer> future = executorService.submit(() -> {
                System.out.format("Running in Thread: %s\n",
                    Thread.currentThread());
                System.out.format("Is our thread virtual? %b\n",
                    Thread.currentThread().isVirtual());
                return 100;
            });
            System.out.println(future.get());
        }
    }


    @Test
    void testComposingVirtualThreads() throws InterruptedException {
        Thread startedThread = Thread.ofVirtual().start(() -> {
            final var city = WeatherStation.getCity();
            Thread innerThread = Thread.ofVirtual().start(() -> {
                final var temperature = WeatherStation.getTemperature(city);
                System.out.printf("The temperature for %s is %d", city, temperature);
            });
            try {
                innerThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("Waiting for Thread to finish");
        startedThread.join();
    }

    @AfterEach
    void tearDown() throws Exception {
        Path path = Paths.get(String.format("%s.jfr", testName));
        Path hprofPath = Paths.get(String.format("%s.hprof", testName));

        recording.stop();
        recording.dump(path);
        recording.close();

        boolean deleted = hprofPath.toFile().delete();
        HotSpotDiagnosticMXBean mxBean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        mxBean.dumpHeap(String.valueOf(hprofPath.toFile()), true);
    }
}
