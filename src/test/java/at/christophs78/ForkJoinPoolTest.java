package at.christophs78;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class ForkJoinPoolTest
{

    private final AtomicInteger cnt = new AtomicInteger(1);

    private final ExecutorService executorService4CompletableFuture = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r);
        thread.setName("Sub-Sub-Task Service Executor Thread " + cnt.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });

    @Test
    public void testForkJoinPoolWithNestedExecutorService() {
//        ForkJoinPool forkJoinPool = new ForkJoinPool(2);
        ForkJoinPool forkJoinPool = new ForkJoinPool(2, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, false,
                0, 2, 1, pool -> true, 60, TimeUnit.MILLISECONDS);

        ForkJoinTask forkJoinTask = forkJoinPool.submit(new RecursiveMainWorkTaskRoot());
        forkJoinTask.join();

        debug("--------------------------------");
        debug("ActiveThreadCount: " + forkJoinPool.getActiveThreadCount());
        debug("PoolSize: " + forkJoinPool.getPoolSize());
        debug("StealCount: " + forkJoinPool.getStealCount());
    }

    private void debug(String s) {
        System.out.println(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + " - " + Thread.currentThread().getName() + " - " + s);
    }

    class RecursiveMainWorkTaskRoot extends RecursiveTask {

        @Override
        protected Object compute() {
            List<ForkJoinTask> forkJoinTasks = new ArrayList<>();

            for (int i=1; i<=1; i++) {
                ForkJoinTask forkJoinTask = new RecursiveMainWorkTask(i).fork();
                forkJoinTasks.add(forkJoinTask);
            }

            Collections.reverse(forkJoinTasks);
            forkJoinTasks.forEach(t -> t.join());

            return null;
        }
    }

    class RecursiveMainWorkTask extends RecursiveTask {

        int id;

        public RecursiveMainWorkTask(int id) {
            this.id = id;
        }

        @Override
        protected Object compute() {
            ExecutorService executorService = Executors.newFixedThreadPool(2);

            debug("Starting Task with id " + id);

            ForkJoinTask forkJoinTaskA = new RecursiveSubWorkTask(id + " - A").fork();
            ForkJoinTask forkJoinTaskB = new RecursiveSubWorkTask(id + " - B").fork();
            ForkJoinTask forkJoinTaskC = new RecursiveSubWorkTask(id + " - C").fork();
            ForkJoinTask forkJoinTaskD = new RecursiveSubWorkTask(id + " - D").fork();

            forkJoinTaskD.join();
            forkJoinTaskC.join();
            forkJoinTaskB.join();
            forkJoinTaskA.join();

            debug("Finished Task with id " + id);

            return null;
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
                }, executorService4CompletableFuture);

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
                }, executorService4CompletableFuture);

                CompletableFuture.allOf(completableFuture1, completableFuture2).get();

                debug("Finished Sub-Task with name " + name);
            }
            catch (InterruptedException | CancellationException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }

            return null;
        }
    }
}
