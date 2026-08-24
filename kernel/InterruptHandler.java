package kernel;

import fs.TransactionLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * InterruptHandler — wired to real ATM events.
 *
 * Each interrupt type maps to a real ATM situation:
 *  WRONG_PIN       → fired when PIN validation fails 3 times
 *  CARD_TIMEOUT    → fired when a session has been idle too long
 *  NETWORK_FAILURE → fired randomly during transactions (10% chance)
 *  CASH_JAM        → fired when a withdraw exceeds cash limit
 *  POWER_FAILURE   → fired during auto-sim stress (simulates power cut)
 *  INVALID_AMOUNT  → fired when amount <= 0 or non-numeric input
 */
public class InterruptHandler {
    private final TransactionLog log;

    // Interrupt counters — visible to UI
    private volatile int totalInterrupts     = 0;
    private volatile int networkFailures     = 0;
    private volatile int wrongPinInterrupts  = 0;
    private volatile int timeoutInterrupts   = 0;
    private volatile int cashJamCount        = 0;
    private volatile int powerFailureCount   = 0;
    private volatile int invalidAmountCount  = 0;

    // Internal ISR log — InterruptPanel reads this every 500ms
    private final List<String> isrLog = new CopyOnWriteArrayList<>();
    private volatile int lastLogIndex = 0;   // tracks what InterruptPanel has already read

    public enum InterruptType {
        CARD_TIMEOUT, WRONG_PIN, NETWORK_FAILURE,
        CASH_JAM, POWER_FAILURE, INVALID_AMOUNT
    }

    public InterruptHandler(TransactionLog log) {
        this.log = log;
    }

    // ── Core trigger — called by both UI buttons AND real ATM events ──
    public void trigger(InterruptType type, Consumer<String> out) {
        totalInterrupts++;
        // Wrap out so every line also goes to internal isrLog
        Consumer<String> wrappedOut = line -> {
            if (line != null) isrLog.add(line);
            if (out != null) out.accept(line);
        };
        triggerInternal(type, wrappedOut);
    }

    private void triggerInternal(InterruptType type, Consumer<String> out) {
        String msg = switch (type) {
            case CARD_TIMEOUT    -> "Session timed out — card ejected by hardware timer ISR";
            case WRONG_PIN       -> "Software trap: Invalid PIN — account may be locked";
            case NETWORK_FAILURE -> "I/O interrupt: Bank server unreachable — transaction aborted";
            case CASH_JAM        -> "Hardware interrupt: Cash dispenser jammed — maintenance required";
            case POWER_FAILURE   -> "Critical interrupt: Power fluctuation — safe shutdown initiated";
            case INVALID_AMOUNT  -> "Software trap: Invalid amount — must be > 0 and numeric";
        };

        log.logInterrupt(type.name(), msg);

        out.accept("[INTERRUPT] ── " + type.name() + " ──────────────────────");
        out.accept("[ISR] " + msg);
        out.accept("[ISR] Interrupt Service Routine executing...");

        switch (type) {
            case CARD_TIMEOUT -> {
                timeoutInterrupts++;
                out.accept("[ISR] Hardware timer expired. Ejecting card.");
                out.accept("[ISR] Session PCB marked TERMINATED.");
                out.accept("[ISR] Memory page freed. Semaphore released.");
            }
            case WRONG_PIN -> {
                wrongPinInterrupts++;
                out.accept("[ISR] PIN mismatch detected.");
                out.accept("[ISR] Failed attempt counter incremented.");
                out.accept("[ISR] After 3 failures → account will be LOCKED.");
            }
            case NETWORK_FAILURE -> {
                networkFailures++;
                for (int i = 1; i <= 3; i++) {
                    out.accept("[ISR] Retry " + i + "/3 to bank server... TIMEOUT");
                    try { Thread.sleep(300); } catch (InterruptedException e) { break; }
                }
                out.accept("[ISR] All retries exhausted.");
                out.accept("[ISR] WAL ROLLBACK issued — transaction aborted safely.");
            }
            case CASH_JAM -> {
                cashJamCount++;
                out.accept("[ISR] Cash dispenser motor halted.");
                out.accept("[ISR] Maintenance alert sent to branch.");
                out.accept("[ISR] ATM marked OUT OF SERVICE.");
            }
            case POWER_FAILURE -> {
                powerFailureCount++;
                out.accept("[ISR] Power fluctuation detected by hardware sensor.");
                out.accept("[ISR] Flushing WAL journal to persistent storage...");
                out.accept("[ISR] All active transactions rolled back.");
                out.accept("[ISR] System entering safe shutdown state.");
            }
            case INVALID_AMOUNT -> {
                invalidAmountCount++;
                out.accept("[ISR] Amount validation failed.");
                out.accept("[ISR] Trap raised — returning control to ATM menu.");
            }
        }
        out.accept("[ISR] Handler complete. Returning to normal operation.");
        out.accept("");
    }

    public void triggerRandom(Consumer<String> out) {
        InterruptType[] types = InterruptType.values();
        trigger(types[(int)(Math.random() * types.length)], out);
    }

    // ── Called by ATMController when wrong PIN entered ────────────
    public void onWrongPin(String accountId, int attempts, Consumer<String> out) {
        // Always increment total counter so InterruptPanel counter updates
        totalInterrupts++;
        wrongPinInterrupts++;

        // Always write to WAL log
        log.logInterrupt("WRONG_PIN_ATTEMPT",
            "Account: " + accountId + " | Attempt: " + attempts + "/3");

        isrLog.add("[INTERRUPT] WRONG_PIN ── Attempt " + attempts + "/3 ──");
        out.accept("[INTERRUPT] WRONG_PIN ── Attempt " + attempts + "/3 ──");
        out.accept("[ISR] Account: " + accountId);

        if (attempts >= 3) {
            out.accept("[ISR] 3 failures reached — account LOCKED by OS");
            out.accept("[ISR] Software trap: WRONG_PIN ISR executing...");
            out.accept("[ISR] PIN mismatch confirmed — lockout enforced");
            out.accept("[ISR] Account " + accountId + " marked LOCKED in Bank");
            out.accept("[ISR] Further transactions blocked until admin unlock");
            out.accept("[ISR] Handler complete. Returning to ATM idle state.");
            out.accept("");
        } else {
            out.accept("[ISR] Warning: " + (3 - attempts) + " attempt(s) remaining");
            out.accept("[ISR] Account will be LOCKED after 3 failures");
            out.accept("");
        }
    }

    // ── Called by ATMController on session idle timeout ───────────
    public void onSessionTimeout(String atmId, String accountId, Consumer<String> out) {
        out.accept("[ATM EVENT] Session timeout on " + atmId + " for " + accountId);
        trigger(InterruptType.CARD_TIMEOUT, out);
    }

    // ── Called by ATMController on network error ──────────────────
    public void onNetworkFailure(String accountId, Consumer<String> out) {
        out.accept("[ATM EVENT] Network failure during transaction for " + accountId);
        trigger(InterruptType.NETWORK_FAILURE, out);
    }

    // ── Called by ATMController on invalid amount input ───────────
    public void onInvalidAmount(String atmId, double amount, Consumer<String> out) {
        out.accept("[ATM EVENT] Invalid amount Rs." + amount + " on " + atmId);
        trigger(InterruptType.INVALID_AMOUNT, out);
    }

    // ── New ISR log lines since last poll (called by InterruptPanel) ──
    public List<String> pollNewLogLines() {
        if (lastLogIndex >= isrLog.size()) return Collections.emptyList();
        List<String> newLines = new ArrayList<>(isrLog.subList(lastLogIndex, isrLog.size()));
        lastLogIndex = isrLog.size();
        return newLines;
    }

    public List<String> getFullLog() {
        return Collections.unmodifiableList(isrLog);
    }

    // ── Random interrupt during auto-sim (called by SchedulerEngine) ──
    public boolean shouldFireRandom() {
        return Math.random() < 0.08; // 8% chance per transaction
    }

    // ── Getters for InterruptPanel stats ─────────────────────────
    public int getTotalInterrupts()    { return totalInterrupts; }
    public int getNetworkFailures()    { return networkFailures; }
    public int getWrongPinInterrupts() { return wrongPinInterrupts; }
    public int getTimeoutInterrupts()  { return timeoutInterrupts; }
    public int getCashJamCount()       { return cashJamCount; }
    public int getPowerFailureCount()  { return powerFailureCount; }
    public int getInvalidAmountCount() { return invalidAmountCount; }
}
