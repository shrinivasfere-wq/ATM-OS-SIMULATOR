package kernel;

import process.ProcessManager;
import memory.MemoryManager;
import sync.SemaphoreManager;
import sync.DeadlockDetector;
import fs.TransactionLog;

public class Kernel {
    private static Kernel instance;

    private ProcessManager   processManager;
    private MemoryManager    memoryManager;
    private SemaphoreManager semaphoreManager;
    private TransactionLog   transactionLog;
    private InterruptHandler interruptHandler;
    private DeadlockDetector deadlockDetector;

    private Kernel() {}

    public static synchronized Kernel getInstance() {
        if (instance == null) instance = new Kernel();
        return instance;
    }

    public void start() {
        processManager   = new ProcessManager();
        memoryManager    = new MemoryManager();
        semaphoreManager = new SemaphoreManager();
        transactionLog   = new TransactionLog();
        interruptHandler = new InterruptHandler(transactionLog);
        deadlockDetector = new DeadlockDetector(semaphoreManager);
    }

    public int getCpuUsagePercent() {
        if (processManager == null) return 0;
        long active = processManager.getAllProcesses().stream()
                          .filter(p -> p.getState().equals("RUNNING")).count();
        return (int) Math.min(active * 15 + 5, 99);
    }

    public int getMemoryUsagePercent() {
        return memoryManager == null ? 0 : memoryManager.getMemoryUsagePercent();
    }

    public ProcessManager   getProcessManager()   { return processManager; }
    public MemoryManager    getMemoryManager()     { return memoryManager; }
    public SemaphoreManager getSemaphoreManager()  { return semaphoreManager; }
    public TransactionLog   getTransactionLog()    { return transactionLog; }
    public InterruptHandler getInterruptHandler()  { return interruptHandler; }
    public DeadlockDetector getDeadlockDetector()  { return deadlockDetector; }
}
