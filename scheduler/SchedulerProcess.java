package scheduler;

public class SchedulerProcess {
    private String name;
    private int arrivalTime;
    private int burstTime;
    private int remainingTime;
    private int priority;       // lower = higher priority
    private int startTime   = -1;
    private int finishTime  = -1;
    private int waitingTime = 0;

    public SchedulerProcess(String name, int arrivalTime, int burstTime, int priority) {
        this.name          = name;
        this.arrivalTime   = arrivalTime;
        this.burstTime     = burstTime;
        this.remainingTime = burstTime;
        this.priority      = priority;
    }

    // ── Getters ──────────────────────────────────────────────────
    public String getName()          { return name; }
    public int    getArrivalTime()   { return arrivalTime; }
    public int    getBurstTime()     { return burstTime; }
    public int    getRemainingTime() { return remainingTime; }
    public int    getPriority()      { return priority; }
    public int    getStartTime()     { return startTime; }
    public int    getFinishTime()    { return finishTime; }
    public int    getWaitingTime()   { return waitingTime; }

    // ── Setters ──────────────────────────────────────────────────
    public void setRemainingTime(int t) { this.remainingTime = t; }
    public void setStartTime(int t)     { if (startTime == -1) this.startTime = t; }
    public void setFinishTime(int t)    { this.finishTime = t; }
    public void setWaitingTime(int t)   { this.waitingTime = t; }

    // ── Derived metrics ──────────────────────────────────────────
    public int getTurnaroundTime() {
        return finishTime - arrivalTime;
    }

    public boolean isDone() {
        return remainingTime <= 0;
    }

    @Override
    public String toString() {
        return String.format("%-6s | Arrival=%-2d | Burst=%-2d | Priority=%-2d",
                             name, arrivalTime, burstTime, priority);
    }
}
