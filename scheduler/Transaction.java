package scheduler;

import process.ProcessState;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class Transaction {
    private static final AtomicInteger counter = new AtomicInteger(1);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String id;
    private final String atmId;
    private final String accountId;
    private String targetAccountId;
    private final TransactionType type;
    private final double amount;
    private final int priority;
    private final int burstTime;

    private ProcessState state = ProcessState.NEW;
    private int remainingBurst;
    private int quantumUsed = 0;

    private long arrivalTime;
    private long startTime = -1;
    private long endTime   = -1;
    private long waitTime  = 0;   // in ticks
    private long turnaround = 0;  // in ms

    private String result    = "";
    private final String timestamp;

    public Transaction(String atmId, String accountId, TransactionType type, double amount, int priority) {
        this.id         = String.format("TXN-%04d", counter.getAndIncrement());
        this.atmId      = atmId;
        this.accountId  = accountId;
        this.type       = type;
        this.amount     = amount;
        this.priority   = priority;
        this.burstTime  = switch (type) {
            case WITHDRAW -> 4; case DEPOSIT -> 3;
            case BALANCE  -> 1; case TRANSFER -> 6;
        };
        this.remainingBurst = burstTime;
        this.arrivalTime    = System.currentTimeMillis();
        this.timestamp      = LocalTime.now().format(FMT);
    }

    public String          getId()              { return id; }
    public String          getAtmId()           { return atmId; }
    public String          getAccountId()       { return accountId; }
    public String          getTargetAccountId() { return targetAccountId; }
    public TransactionType getType()            { return type; }
    public double          getAmount()          { return amount; }
    public int             getPriority()        { return priority; }
    public int             getBurstTime()       { return burstTime; }
    public int             getRemainingBurst()  { return remainingBurst; }
    public int             getQuantumUsed()     { return quantumUsed; }
    public ProcessState    getState()           { return state; }
    public long            getArrivalTime()     { return arrivalTime; }
    public long            getStartTime()       { return startTime; }
    public long getEndTime() {
    return endTime;
}
    public long            getWaitTime()        { return waitTime; }
    public long            getTurnaround()      { return turnaround; }
    public String          getResult()          { return result; }
    public String          getTimestamp()       { return timestamp; }

    public void setTargetAccountId(String t){ this.targetAccountId = t; }
    public void setState(ProcessState s)    { this.state = s; }
    public void setResult(String r)         { this.result = r; }
    public void setStartTime(long t)        { this.startTime = t; }
    public void setEndTime(long t)          { this.endTime = t; }
    public void setWaitTime(long t)         { this.waitTime = t; }
    public void setTurnaround(long t)       { this.turnaround = t; }

    public void decrementBurst()   { if (remainingBurst > 0) remainingBurst--; }
    public void incrementQuantum() { quantumUsed++; }
    public void resetQuantum()     { quantumUsed = 0; }
    public boolean isDone()        { return remainingBurst <= 0; }
}
