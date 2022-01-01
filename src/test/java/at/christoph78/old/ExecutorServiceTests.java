package at.christoph78.old;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

@Disabled("initial attempts to reproduce this issue")
public class ExecutorServiceTests
{

    private final AtomicInteger cnt = new AtomicInteger(1);

    private final ExecutorService executorService4CompletableFuture = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r);
        thread.setName("Sub-Sub-Task Service Executor Thread " + cnt.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });

    @BeforeAll
    public static void startup() {
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(Level.INFO);
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(Level.INFO);
        }
        Logger.getLogger(ExecutorServiceTests.class.getName()).info("Hello Logging!");
        Logger.getLogger(ExecutorServiceTests.class.getName()).info("Hello Logging!");
    }

    @Test
    public void testExecutorService() throws Exception {
        ExecutorService executorService = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

        List<Callable<String>> tasks = new ArrayList<>();

        tasks.add(() -> {
            debug("starting task A - " + Thread.currentThread().getName());
            Thread.sleep(2000);
            debug("finished task A - " + Thread.currentThread().getName());
            return "A";
        });
        tasks.add(() -> {
            debug("starting task B - " + Thread.currentThread().getName());
            Thread.sleep(3000);
            debug("finished task B - " + Thread.currentThread().getName());
            return "B";
        });
        tasks.add(() -> {
            debug("starting task C - " + Thread.currentThread().getName());
            Thread.sleep(4000);
            debug("finished task C - " + Thread.currentThread().getName());
            return "C";
        });


        debug("STARTING... - " + Thread.currentThread().getName());
        List<Future<String>> futures = executorService.invokeAll(tasks);
        debug("FINISHED - " + Thread.currentThread().getName());

        for (Future f: futures) {
            debug("done: " + f.isDone() + ", result: " + f.get());
        }
    }

    @Test
    public void testForkJoinPoolWithNestedExecutorService() throws Exception {
        ForkJoinPool forkJoinPool = new ForkJoinPool(8);


        ForkJoinTask forkJoinTask1 = forkJoinPool.submit(new MainWorkTask(1));
        ForkJoinTask forkJoinTask2 = forkJoinPool.submit(new MainWorkTask(2));
        ForkJoinTask forkJoinTask3 = forkJoinPool.submit(new MainWorkTask(3));

//        Thread.sleep(2000);

        ForkJoinTask forkJoinTask4 = forkJoinPool.submit(new MainWorkTask(4));
        ForkJoinTask forkJoinTask5 = forkJoinPool.submit(new MainWorkTask(5));
        ForkJoinTask forkJoinTask6 = forkJoinPool.submit(new MainWorkTask(6));

//        Thread.sleep(100);
//
//        debug("active thread count: " + forkJoinPool.getActiveThreadCount());

        forkJoinTask1.join();
        forkJoinTask2.join();
        forkJoinTask3.join();
        forkJoinTask4.join();
        forkJoinTask5.join();
        forkJoinTask6.join();
    }

    @Test
    public void testForkJoinPoolWithNestedExecutorService_Recursive() throws Exception {
//        ForkJoinPool forkJoinPool = new ForkJoinPool(2);
        ForkJoinPool forkJoinPool = new ForkJoinPool(2, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, false,
                0, 2, 1, null, 60, TimeUnit.MILLISECONDS);

        ForkJoinTask forkJoinTask = forkJoinPool.submit(new RecursiveMainWorkTaskRoot());
        forkJoinTask.join();

        debug("--------------------------------");
        debug("ActiveThreadCount: " + forkJoinPool.getActiveThreadCount());
        debug("PoolSize: " + forkJoinPool.getPoolSize());
        debug("StealCount: " + forkJoinPool.getStealCount());
    }

    @Test
    public void testMainWorkTask() throws Exception {
        new MainWorkTask(1).run();
    }

    private void debug(String s) {
        System.out.println(LocalTime.now() + " - " + Thread.currentThread().getName() + " - " + s);
    }

    class MainWorkTask implements Runnable {

        int id;

        public MainWorkTask(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try {
//                ExecutorService executorService = Executors.newFixedThreadPool(2);
//                ExecutorService executorService = Executors.newFixedThreadPool(16);

                debug("Starting Task with id " + id);
//                Thread.sleep(1000);

//                Future subWorkTaskA = executorService.submit(new SubWorkTask(id + " - A"));
//                Future subWorkTaskB = executorService.submit(new SubWorkTask(id + " - B"));
//                Future subWorkTaskC = executorService.submit(new SubWorkTask(id + " - C"));
//                Future subWorkTaskD = executorService.submit(new SubWorkTask(id + " - D"));
//
//                subWorkTaskA.get();
//                subWorkTaskB.get();
//                subWorkTaskC.get();
//                subWorkTaskD.get();

                new SubWorkTask(id + " - A").run();

                debug("Finished Task with id " + id);
            }
//            catch (InterruptedException | CancellationException | ExecutionException ex) {
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    class RecursiveMainWorkTaskRoot extends java.util.concurrent.RecursiveTask {

        @Override
        protected Object compute() {
            List<ForkJoinTask> forkJoinTasks = new ArrayList<>();

            for (int i=1; i<=4; i++) {
                ForkJoinTask forkJoinTask = new RecursiveMainWorkTask(i).fork();
                forkJoinTasks.add(forkJoinTask);
            }

            try {
                Thread.sleep(4000);
            }
            catch (InterruptedException ex) {
                ;
            }

            debug("ActiveThreadCount: " + ForkJoinTask.getPool().getActiveThreadCount());
            debug("PoolSize: " + ForkJoinTask.getPool().getPoolSize());
            debug("StealCount: " + ForkJoinTask.getPool().getStealCount());

            Collections.reverse(forkJoinTasks);
            forkJoinTasks.forEach(t -> t.join());

            debug("ActiveThreadCount: " + ForkJoinTask.getPool().getActiveThreadCount());
            debug("PoolSize: " + ForkJoinTask.getPool().getPoolSize());
            debug("StealCount: " + ForkJoinTask.getPool().getStealCount());

            return null;
        }
    }

    class RecursiveMainWorkTask extends java.util.concurrent.RecursiveTask {

        int id;

        public RecursiveMainWorkTask(int id) {
            this.id = id;
        }

        @Override
        protected Object compute() {
            try {
                ExecutorService executorService = Executors.newFixedThreadPool(2);
//                ExecutorService executorService = Executors.newFixedThreadPool(16);

                debug("Starting Task with id " + id);
//                Thread.sleep(1000);

                ForkJoinTask forkJoinTaskA = new RecursiveSubWorkTask(id + " - A").fork();
                ForkJoinTask forkJoinTaskB = new RecursiveSubWorkTask(id + " - B").fork();
                ForkJoinTask forkJoinTaskC = new RecursiveSubWorkTask(id + " - C").fork();
                ForkJoinTask forkJoinTaskD = new RecursiveSubWorkTask(id + " - D").fork();

                forkJoinTaskD.join();
                forkJoinTaskC.join();
                forkJoinTaskB.join();
                forkJoinTaskA.join();

                debug("Finished Task with id " + id);
            }
//            catch (InterruptedException | CancellationException | ExecutionException ex) {
            catch (Exception ex) {
                ex.printStackTrace();
            }

            return null;
        }
    }

    class SubWorkTask implements Runnable {

        String name;

        public SubWorkTask(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            try {
                debug("Starting Sub-Task with name " + name);
//                Thread.sleep(1000);

                CompletableFuture completableFuture1 = CompletableFuture.supplyAsync(() -> {
                    try {
                        debug("Starting Sub-Sub-Task with name " + name + "-1");
                        Thread.sleep(1000);
                        debug("Finishing Sub-Sub-Task with name " + name + "-1");
                    }
                    catch (InterruptedException ex) {
                        ;
                    }
                    return null;
                } /*, executorService4CompletableFuture*/);

                CompletableFuture completableFuture2 = CompletableFuture.supplyAsync(() -> {
                    try {
                        debug("Starting Sub-Sub-Task with name " + name + "-2");
                        Thread.sleep(1000);
                        debug("Finishing Sub-Sub-Task with name " + name + "-2");
                    }
                    catch (InterruptedException ex) {
                        ;
                    }
                    return null;
                } /*, executorService4CompletableFuture*/);


                CompletableFuture completableFuture3 = CompletableFuture.supplyAsync(() -> {
                    try {
                        debug("Starting Sub-Sub-Task with name " + name + "-3");
                        Thread.sleep(1000);
                        debug("Finishing Sub-Sub-Task with name " + name + "-3");
                    }
                    catch (InterruptedException ex) {
                        ;
                    }
                    return null;
                } /*, executorService4CompletableFuture*/);

                CompletableFuture completableFuture4 = CompletableFuture.supplyAsync(() -> {
                    try {
                        debug("Starting Sub-Sub-Task with name " + name + "-4");
                        Thread.sleep(1000);
                        debug("Finishing Sub-Sub-Task with name " + name + "-4");
                    }
                    catch (InterruptedException ex) {
                        ;
                    }
                    return null;
                } /*, executorService4CompletableFuture*/);

                CompletableFuture.allOf(completableFuture1, completableFuture2, completableFuture3, completableFuture4).get(10, TimeUnit.SECONDS);

                debug("Finished Sub-Task with name " + name);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    class RecursiveSubWorkTask extends RecursiveTask {

        String name;

        public RecursiveSubWorkTask(String name) {
            this.name = name;
        }

        @Override
        protected Object compute() {
            try {
                debug("Starting Sub-Task with name " + name);
//                Thread.sleep(1000);

                CompletableFuture completableFuture1 = CompletableFuture.supplyAsync(() -> {
                    try {
                        debug("Starting Sub-Sub-Task with name " + name + "-1");
                        Thread.sleep(1000);
                        debug("Finishing Sub-Sub-Task with name " + name + "-1");
                    }
                    catch (InterruptedException ex) {
                        ;
                    }
                    return null;
                } /*, executorService4CompletableFuture*/);

                CompletableFuture completableFuture2 = CompletableFuture.supplyAsync(() -> {
                    try {
                        debug("Starting Sub-Sub-Task with name " + name + "-2");
                        Thread.sleep(1000);
                        debug("Finishing Sub-Sub-Task with name " + name + "-2");
                    }
                    catch (InterruptedException ex) {
                        ;
                    }
                    return null;
                } /*, executorService4CompletableFuture*/);


                CompletableFuture completableFuture3 = CompletableFuture.supplyAsync(() -> {
                    try {
                        debug("Starting Sub-Sub-Task with name " + name + "-3");
                        Thread.sleep(1000);
                        debug("Finishing Sub-Sub-Task with name " + name + "-3");
                    }
                    catch (InterruptedException ex) {
                        ;
                    }
                    return null;
                } /*, executorService4CompletableFuture*/);

                CompletableFuture completableFuture4 = CompletableFuture.supplyAsync(() -> {
                    try {
                        debug("Starting Sub-Sub-Task with name " + name + "-4");
                        Thread.sleep(1000);
                        debug("Finishing Sub-Sub-Task with name " + name + "-4");
                    }
                    catch (InterruptedException ex) {
                        ;
                    }
                    return null;
                } /*, executorService4CompletableFuture*/);

                CompletableFuture.allOf(completableFuture1, completableFuture2, completableFuture3, completableFuture4).get(10, TimeUnit.SECONDS);

                debug("Finished Sub-Task with name " + name);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

            return null;
        }
    }

//    class RecursiveTask extends RecursiveAction {
//
//        @Override
//        protected void compute() {
//
//        }
//    }
}
