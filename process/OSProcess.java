package process;

import java.util.concurrent.atomic.AtomicInteger;

public class OSProcess {
    // FIX 1: AtomicInteger — thread-safe PID generation, no race condition
    private static final AtomicInteger nextPid = new AtomicInteger(1);

    private final int    pid;
    private       String name;
    private volatile String state;   // READY, RUNNING, BLOCKED, TERMINATED
    private final int    priority;
    private volatile long cpuTime;
    private final long   startTime;
    private final String owner;

    public OSProcess(String name, int priority, String owner) {
        this.pid       = nextPid.getAndIncrement();  // atomic — safe under concurrency
        this.name      = name;
        this.state     = "READY";
        this.priority  = priority;
        this.cpuTime   = 0;
        this.startTime = System.currentTimeMillis();
        this.owner     = owner;
    }

    public synchronized void setState(String state) { this.state = state; }
    public synchronized void addCpuTime(long ms)    { this.cpuTime += ms; }
    public synchronized void terminate()            { this.state = "TERMINATED"; }

    public int    getPid()       { return pid; }
    public String getName()      { return name; }
    public String getState()     { return state; }
    public int    getPriority()  { return priority; }
    public long   getCpuTime()   { return cpuTime; }
    public String getOwner()     { return owner; }
    public long   getUptime()    { return System.currentTimeMillis() - startTime; }

    @Override
    public String toString() {
        return String.format("PID=%-3d | %-22s | %-10s | Priority=%-2d | CPU=%dms",
                             pid, name, state, priority, cpuTime);
    }
}
