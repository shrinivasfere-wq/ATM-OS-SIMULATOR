package scheduler;

import kernel.Kernel;
import monitor.SystemMonitor;
import process.ProcessState;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Live ATM transaction tick loop.
 * Transactions are submitted here, queued, scheduled, then executed
 * when their burst completes — result returned via CompletableFuture.
 */
public class SchedulerEngine {
    private static SchedulerEngine instance;

    private final BlockingDeque<Transaction> readyQueue = new LinkedBlockingDeque<>();
    private volatile Transaction running = null;
    private volatile SchedulingAlgorithm algorithm = SchedulingAlgorithm.ROUND_ROBIN;
    private static final int QUANTUM = 3;

    private final SystemMonitor monitor = SystemMonitor.getInstance();
    private final AtomicBoolean active  = new AtomicBoolean(false);
    private ScheduledExecutorService tickExecutor;

    // Each transaction may have a callback that actually runs the banking op
    private final Map<String, Function<Transaction, String>> callbacks =
        new ConcurrentHashMap<>();
    // Futures resolved when transaction completes
    private final Map<String, CompletableFuture<String>> futures =
        new ConcurrentHashMap<>();

    private final List<Transaction> completedList =
        Collections.synchronizedList(new ArrayList<>());
    private final List<Transaction> ganttList =
        Collections.synchronizedList(new ArrayList<>());

    private int tick         = 0;
    private int cpuBusyTicks = 0;

    private SchedulerEngine() {}

    public static synchronized SchedulerEngine getInstance() {
        if (instance == null) instance = new SchedulerEngine();
        return instance;
    }

    // ── Start / Stop ────────────────────────────────────────────
    public void start() {
        active.set(true);
        tickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SchedulerTick");
            t.setDaemon(true);
            return t;
        });
        tickExecutor.scheduleAtFixedRate(this::tick, 0, 500, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        active.set(false);
        if (tickExecutor != null) tickExecutor.shutdownNow();
    }

    // ── Submit (fire-and-forget, used by auto-sim) ───────────────
    public void submit(Transaction txn) {
        txn.setState(ProcessState.READY);
        readyQueue.addLast(txn);
        monitor.setQueueSize(readyQueue.size());
    }

    // ── Submit with callback + future (used by ATM UI) ──────────
    public CompletableFuture<String> submitWithCallback(
            Transaction txn,
            Function<Transaction, String> executor) {

        CompletableFuture<String> future = new CompletableFuture<>();
        callbacks.put(txn.getId(), executor);
        futures.put(txn.getId(), future);
        txn.setState(ProcessState.READY);
        readyQueue.addLast(txn);
        monitor.setQueueSize(readyQueue.size());
        return future;
    }

    // ── Tick ─────────────────────────────────────────────────────
    private synchronized void tick() {
        tick++;
        readyQueue.forEach(t -> t.setWaitTime(t.getWaitTime() + 1));

        if (running != null) {
            running.decrementBurst();
            cpuBusyTicks++;

            if (running.isDone()) {
                finalizeTransaction(running);
                running = null;
            } else if (algorithm == SchedulingAlgorithm.ROUND_ROBIN) {
                running.incrementQuantum();
                if (running.getQuantumUsed() >= QUANTUM) {
                    running.resetQuantum();
                    running.setState(ProcessState.READY);
                    readyQueue.addLast(running);
                    running = null;
                }
            }
        }

        if (running == null && !readyQueue.isEmpty()) {
            running = pickNext();
            if (running != null) {
                running.setState(ProcessState.RUNNING);
                if (running.getStartTime() < 0)
                    running.setStartTime(System.currentTimeMillis());
            }
        }

        // Gantt snapshot
        ganttList.add(running);
        if (ganttList.size() > 60) ganttList.remove(0);

        monitor.setRunning(running);
        monitor.setQueueSize(readyQueue.size());
        monitor.setCpuUtilization(tick > 0 ? (cpuBusyTicks * 100.0 / tick) : 0);
        monitor.setCompletedCount(completedList.size());
        monitor.setTick(tick);
    }

    // ── Pick next based on algorithm ─────────────────────────────
    private Transaction pickNext() {
        if (readyQueue.isEmpty()) return null;
        return switch (algorithm) {
            case FCFS, ROUND_ROBIN -> readyQueue.pollFirst();
            case PRIORITY -> {
                Transaction best = null;
                for (Transaction t : readyQueue)
                    if (best == null || t.getPriority() < best.getPriority()) best = t;
                readyQueue.remove(best);
                yield best;
            }
        };
    }

    // ── Finalize: run the real banking op, resolve future ────────
    private void finalizeTransaction(Transaction txn) {
        txn.setEndTime(System.currentTimeMillis());
        txn.setTurnaround(txn.getEndTime() - txn.getArrivalTime());
        txn.setState(ProcessState.TERMINATED);

        // Run the real banking operation if a callback was registered
        String result = "OK: Processed";
        Function<Transaction, String> cb = callbacks.remove(txn.getId());
        if (cb != null) {
            try {
                result = cb.apply(txn);
            } catch (Exception ex) {
                result = "FAIL: " + ex.getMessage();
            }
        } else {
            // Auto-sim path: use kernel directly
            Kernel kernel = Kernel.getInstance();
            if (kernel.getTransactionLog() != null) {
                kernel.getTransactionLog().commitLog(
                    txn.getAccountId(), txn.getType().toString(),
                    txn.getAmount(), 0);
            }
        }

        txn.setResult(result);

        // Resolve future if one exists
        CompletableFuture<String> future = futures.remove(txn.getId());
        if (future != null) future.complete(result);

        completedList.add(0, txn);
        if (completedList.size() > 100) completedList.remove(completedList.size() - 1);
        monitor.addCompleted(txn);
    }

    // ── Getters ──────────────────────────────────────────────────
    public List<Transaction>   getReadyQueue()    { return new ArrayList<>(readyQueue); }
    public Transaction         getRunning()        { return running; }
    public List<Transaction>   getCompleted()      { return new ArrayList<>(completedList); }
    public List<Transaction>   getGanttList()      { return new ArrayList<>(ganttList); }
    public SchedulingAlgorithm getAlgorithm()      { return algorithm; }
    public void setAlgorithm(SchedulingAlgorithm a){ this.algorithm = a; }
    public int  getTick()                          { return tick; }
}
