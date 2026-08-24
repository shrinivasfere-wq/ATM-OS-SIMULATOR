package sync;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * DeadlockDetector — fixed + Banker's Algorithm added.
 *
 * Fixes:
 *  - Duplicate waitingFor map removed — reads directly from SemaphoreManager
 *  - recordWait/clearWait now delegate to SemaphoreManager (single source of truth)
 *
 * Features:
 *  1. Real DFS cycle detection on wait-graph
 *  2. Animated step-by-step deadlock simulation
 *  3. Banker's Algorithm (deadlock avoidance) — safe state check
 *  4. Deadlock + resolved counters
 */
public class DeadlockDetector {

    private final SemaphoreManager semManager;

    private final AtomicInteger deadlocksDetected = new AtomicInteger(0);
    private final AtomicInteger deadlocksResolved = new AtomicInteger(0);

    public DeadlockDetector(SemaphoreManager sm) {
        this.semManager = sm;
    }

    // ── Getters for UI ────────────────────────────────────────────
    public int getDeadlocksDetected() { return deadlocksDetected.get(); }
    public int getDeadlocksResolved() { return deadlocksResolved.get(); }

    // FIX 5: Read waitingFor directly from SemaphoreManager — no duplicate map
    public Map<String, String> getWaitingFor() {
        return semManager.getWaitingFor();
    }

    // ── Real DFS Cycle Detection ──────────────────────────────────
    public List<String> detectCycle() {
        Map<String, String> waitingFor = semManager.getWaitingFor();
        Map<String, String> holderOf   = new HashMap<>();

        semManager.getLocks().forEach((res, lock) -> {
            if (lock.isLocked() && lock.getHolder() != null)
                holderOf.put(res, lock.getHolder());
        });

        for (String startProc : new ArrayList<>(waitingFor.keySet())) {
            List<String> path    = new ArrayList<>();
            Set<String>  visited = new HashSet<>();
            String cur = startProc;

            while (cur != null && !visited.contains(cur)) {
                visited.add(cur);
                path.add(cur);
                String waitRes = waitingFor.get(cur);
                if (waitRes == null) { cur = null; break; }
                cur = holderOf.get(waitRes);
            }

            if (cur != null && path.contains(cur)) {
                return new ArrayList<>(path.subList(path.indexOf(cur), path.size()));
            }
        }
        return Collections.emptyList();
    }

    // ══════════════════════════════════════════════════════════════
    // BANKER'S ALGORITHM — Deadlock Avoidance
    // ══════════════════════════════════════════════════════════════
    /**
     * Banker's Algorithm checks if a system state is SAFE.
     *
     * Given:
     *   - n processes, m resource types
     *   - allocation[i][j] = units of resource j held by process i
     *   - max[i][j]        = max units of resource j process i may ever need
     *   - available[j]     = currently available units of resource j
     *
     * Algorithm:
     *   need[i][j] = max[i][j] - allocation[i][j]
     *   Find a process whose need can be satisfied by available resources.
     *   Simulate it finishing → release its allocation → repeat.
     *   If all processes finish → SAFE state. Otherwise → UNSAFE (potential deadlock).
     *
     * @return BankersResult with safe flag, safe sequence, and step log
     */
    public BankersResult runBankersAlgorithm(
            String[] processes,
            String[] resources,
            int[][] allocation,
            int[][] max,
            int[]   available) {

        int n = processes.length;
        int m = resources.length;

        // Calculate need matrix
        int[][] need = new int[n][m];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++)
                need[i][j] = max[i][j] - allocation[i][j];

        // Work = copy of available
        int[] work = Arrays.copyOf(available, m);

        // Finish[i] = false initially
        boolean[] finish = new boolean[n];

        List<String> safeSequence = new ArrayList<>();
        List<String> steps        = new ArrayList<>();

        steps.add("━━━ BANKER'S ALGORITHM — SAFE STATE CHECK ━━━");
        steps.add("");
        steps.add("  Resources: " + Arrays.toString(resources));
        steps.add("  Available: " + Arrays.toString(available));
        steps.add("");
        steps.add("  %-12s %-20s %-20s %-20s".formatted("Process", "Allocation", "Max", "Need"));
        steps.add("  " + "─".repeat(74));
        for (int i = 0; i < n; i++) {
            steps.add("  %-12s %-20s %-20s %-20s".formatted(
                processes[i],
                Arrays.toString(allocation[i]),
                Arrays.toString(max[i]),
                Arrays.toString(need[i])
            ));
        }
        steps.add("");
        steps.add("━━━ FINDING SAFE SEQUENCE ━━━━━━━━━━━━━━━━━━━");

        int count = 0;
        while (count < n) {
            boolean found = false;
            for (int i = 0; i < n; i++) {
                if (finish[i]) continue;

                // Check if need[i] <= work
                boolean canRun = true;
                for (int j = 0; j < m; j++) {
                    if (need[i][j] > work[j]) { canRun = false; break; }
                }

                if (canRun) {
                    steps.add("  ✔ " + processes[i] + " can proceed");
                    steps.add("    Need " + Arrays.toString(need[i])
                            + " ≤ Available " + Arrays.toString(work));

                    // Simulate process finishing → release allocation
                    for (int j = 0; j < m; j++) work[j] += allocation[i][j];
                    finish[i] = true;
                    safeSequence.add(processes[i]);
                    count++;
                    found = true;

                    steps.add("    After release → Available: " + Arrays.toString(work));
                    steps.add("");
                }
            }
            if (!found) break;  // no process could proceed → unsafe
        }

        boolean safe = (count == n);
        steps.add("━━━ RESULT ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        if (safe) {
            steps.add("  ✔ SAFE STATE");
            steps.add("  Safe Sequence: " + String.join(" → ", safeSequence));
            steps.add("  No deadlock will occur if resources are");
            steps.add("  allocated in this order.");
        } else {
            steps.add("  ✘ UNSAFE STATE — DEADLOCK MAY OCCUR");
            steps.add("  Completed: " + safeSequence);
            steps.add("  Could not find a safe sequence for all processes.");
        }

        return new BankersResult(safe, safeSequence, steps);
    }

    // ── Result record ─────────────────────────────────────────────
    public static class BankersResult {
        public final boolean      safe;
        public final List<String> safeSequence;
        public final List<String> steps;

        public BankersResult(boolean safe, List<String> safeSequence, List<String> steps) {
            this.safe         = safe;
            this.safeSequence = safeSequence;
            this.steps        = steps;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DEADLOCK SIMULATION (animated, for presentation)
    // ══════════════════════════════════════════════════════════════
    public void simulateDeadlock(Consumer<String> out) {
        new Thread(() -> {
            try { runSimulation(out); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "DeadlockSimThread").start();
    }

    private void runSimulation(Consumer<String> out) throws InterruptedException {
        String procA = "TXN_Transfer_A->B";
        String procB = "TXN_Transfer_B->A";
        String r1    = "ACCOUNT_ACC001";
        String r2    = "ACCOUNT_ACC002";

        semManager.registerResource(r1);
        semManager.registerResource(r2);
        semManager.forceRelease(r1);
        semManager.forceRelease(r2);

        emit(out, "");
        emit(out, "╔══════════════════════════════════════════════════╗");
        emit(out, "║       DEADLOCK SIMULATION — OS CONCEPT           ║");
        emit(out, "╚══════════════════════════════════════════════════╝");
        emit(out, "");
        emit(out, "SCENARIO: Two concurrent TRANSFER transactions");
        emit(out, "  " + procA + "  needs ACC001 then ACC002");
        emit(out, "  " + procB + "  needs ACC002 then ACC001");
        emit(out, "");
        sleep(800);

        emit(out, "━━━ STEP 1 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        emit(out, "  " + procA + "  requests " + r1);
        semManager.acquire(r1, procA);
        emit(out, "  ✔ GRANTED  →  " + r1 + "  held by  " + procA);
        sleep(900);

        emit(out, "");
        emit(out, "━━━ STEP 2 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        emit(out, "  " + procB + "  requests " + r2);
        semManager.acquire(r2, procB);
        emit(out, "  ✔ GRANTED  →  " + r2 + "  held by  " + procB);
        sleep(900);

        emit(out, "");
        emit(out, "━━━ STEP 3 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        emit(out, "  " + procA + "  requests " + r2);
        emit(out, "  ✘ BLOCKED! " + r2 + "  held by  " + procB);
        semManager.acquire(r2, procA);   // fails — records wait in SemaphoreManager
        emit(out, "  ⏳ " + procA + "  WAITING...");
        sleep(900);

        emit(out, "");
        emit(out, "━━━ STEP 4 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        emit(out, "  " + procB + "  requests " + r1);
        emit(out, "  ✘ BLOCKED! " + r1 + "  held by  " + procA);
        semManager.acquire(r1, procB);   // fails — records wait in SemaphoreManager
        emit(out, "  ⏳ " + procB + "  WAITING...");
        sleep(900);

        emit(out, "");
        emit(out, "━━━ CYCLE DETECTION (DFS on Wait-Graph) ━━━━━━━━━━━");
        emit(out, "  " + procA + "  →  waits for →  " + r2);
        sleep(300);
        emit(out, "  " + r2    + "  held by →  " + procB);
        sleep(300);
        emit(out, "  " + procB + "  →  waits for →  " + r1);
        sleep(300);
        emit(out, "  " + r1    + "  held by →  " + procA + "  ← CYCLE!");
        sleep(600);

        List<String> cycle = detectCycle();
        deadlocksDetected.incrementAndGet();

        emit(out, "");
        emit(out, "  ⚠⚠⚠  DEADLOCK DETECTED!  ⚠⚠⚠");
        emit(out, "  Cycle: " + String.join(" → ", cycle) + " → (back)");
        sleep(1000);

        emit(out, "");
        emit(out, "━━━ RESOLUTION: VICTIM SELECTION ━━━━━━━━━━━━━━━━━━");
        emit(out, "  Kill younger process: " + procB);
        sleep(600);
        semManager.forceRelease(r2);
        emit(out, "  ✔ " + r2 + "  released");
        sleep(400);
        semManager.acquire(r2, procA);
        emit(out, "  ✔ " + procA + "  acquired " + r2 + "  — can proceed");
        sleep(400);

        semManager.forceRelease(r1);
        semManager.forceRelease(r2);
        deadlocksResolved.incrementAndGet();

        emit(out, "");
        emit(out, "  ✔ All locks freed. System stable.");
        emit(out, "  ✔ Detected: " + deadlocksDetected.get()
                + "   Resolved: " + deadlocksResolved.get());
        emit(out, "");
        emit(out, "╔══════════════════════════════════════════════════╗");
        emit(out, "║           SIMULATION COMPLETE  ✓                 ║");
        emit(out, "╚══════════════════════════════════════════════════╝");
    }

    private void emit(Consumer<String> out, String line) { out.accept(line); }
    private void sleep(long ms) throws InterruptedException { Thread.sleep(ms); }
}
