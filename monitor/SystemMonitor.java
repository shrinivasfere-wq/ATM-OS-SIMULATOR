package monitor;

import scheduler.Transaction;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class SystemMonitor {
    private static SystemMonitor instance;

    private volatile int tick = 0;
    private volatile int queueSize = 0;
    private volatile double cpuUtilization = 0.0;
    private volatile int completedCount = 0;
    private volatile Transaction running = null;

    private final List<Transaction> recentCompleted = new CopyOnWriteArrayList<>();
    private final List<Double> cpuHistory = new CopyOnWriteArrayList<>();

    private SystemMonitor() {}

    public static synchronized SystemMonitor getInstance() {
        if (instance == null) instance = new SystemMonitor();
        return instance;
    }

    public void addCompleted(Transaction t) {
        recentCompleted.add(0, t);
        if (recentCompleted.size() > 100) recentCompleted.remove(recentCompleted.size() - 1);
    }

    public void addCpuSample(double val) {
        cpuHistory.add(val);
        if (cpuHistory.size() > 60) cpuHistory.remove(0);
    }

    public void setTick(int t)                  { this.tick = t; addCpuSample(cpuUtilization); }
    public void setQueueSize(int s)             { this.queueSize = s; }
    public void setCpuUtilization(double u)     { this.cpuUtilization = u; }
    public void setCompletedCount(int c)        { this.completedCount = c; }
    public void setRunning(Transaction r)       { this.running = r; }

    public int getTick()                        { return tick; }
    public int getQueueSize()                   { return queueSize; }
    public double getCpuUtilization()           { return cpuUtilization; }
    public int getCompletedCount()              { return completedCount; }
    public Transaction getRunning()             { return running; }
    public List<Transaction> getRecentCompleted() { return recentCompleted; }
    public List<Double> getCpuHistory()         { return new ArrayList<>(cpuHistory); }

    public double getAvgWaitTime() {
        if (recentCompleted.isEmpty()) return 0;
        return recentCompleted.stream().mapToLong(Transaction::getWaitTime).average().orElse(0);
    }
    public double getAvgTurnaround() {
        if (recentCompleted.isEmpty()) return 0;
        return recentCompleted.stream().mapToLong(Transaction::getTurnaround).average().orElse(0);
    }
}
