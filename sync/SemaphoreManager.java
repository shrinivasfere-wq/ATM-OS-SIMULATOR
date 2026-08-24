package sync;

import java.util.*;

/**
 * SemaphoreManager — fixed version.
 *
 * Fixes:
 *  1. release() now verifies the caller is the actual holder before unlocking
 *  2. waitingFor map is the single source of truth (DeadlockDetector reads from here)
 *  3. All public methods synchronized for thread safety
 */
public class SemaphoreManager {
    private final Map<String, ResourceLock> locks = new LinkedHashMap<>();

    // Single waitingFor map — DeadlockDetector will read this directly
    // processName → resource it is waiting for
    private final Map<String, String> waitingFor =
        Collections.synchronizedMap(new LinkedHashMap<>());

    public SemaphoreManager() {
        registerResource("ACCOUNT_DB");
        registerResource("CASH_DISPENSER");
        registerResource("CARD_READER");
        registerResource("NETWORK_CHANNEL");
        registerResource("JOURNAL_LOG");
    }

    public synchronized void registerResource(String resource) {
        locks.putIfAbsent(resource, new ResourceLock(resource));
    }

    public synchronized boolean acquire(String resource, String processName) {
        ResourceLock lock = locks.computeIfAbsent(resource, ResourceLock::new);
        if (!lock.isLocked()) {
            lock.lock(processName);
            waitingFor.remove(processName);   // no longer waiting
            return true;
        } else {
            waitingFor.put(processName, resource);  // record wait
            return false;
        }
    }

    // FIX 3: verify holder before releasing — only the actual holder can release
    public synchronized void release(String resource, String processName) {
        ResourceLock lock = locks.get(resource);
        if (lock != null && lock.isLocked()) {
            // Only release if the caller actually holds this lock
            if (processName.equals(lock.getHolder())) {
                lock.unlock();
                waitingFor.remove(processName);
            }
            // If caller is not the holder, silently ignore (prevents lock theft)
        }
    }

    // Called by DeadlockDetector simulation — force-release for victim process
    public synchronized void forceRelease(String resource) {
        ResourceLock lock = locks.get(resource);
        if (lock != null) {
            String holder = lock.getHolder();
            lock.unlock();
            if (holder != null) waitingFor.remove(holder);
        }
    }

    public synchronized boolean detectDeadlock() {
        for (String p1 : new ArrayList<>(waitingFor.keySet())) {
            String r1 = waitingFor.get(p1);
            ResourceLock lock1 = locks.get(r1);
            if (lock1 == null) continue;
            String p2 = lock1.getHolder();
            if (p2 != null && waitingFor.containsKey(p2)) {
                String r2 = waitingFor.get(p2);
                ResourceLock lock2 = locks.get(r2);
                if (lock2 != null && p1.equals(lock2.getHolder())) return true;
            }
        }
        return false;
    }

    // Expose waitingFor for DeadlockDetector to read (eliminates duplicate map)
    public Map<String, String> getWaitingFor() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(waitingFor));
    }

    public Map<String, ResourceLock> getLocks() {
        return Collections.unmodifiableMap(locks);
    }

    // ── Inner class ──────────────────────────────────────────────
    public static class ResourceLock {
        private final String name;
        private boolean locked      = false;
        private String  holder      = null;
        private String  lastHolder  = "-";
        private int     acquireCount = 0;

        public ResourceLock(String name) { this.name = name; }

        public void lock(String h) {
            locked = true; holder = h;
            lastHolder = h; acquireCount++;
        }
        public void unlock()             { locked = false; holder = null; }
        public boolean isLocked()        { return locked; }
        public String  getHolder()       { return holder; }
        public String  getLastHolder()   { return lastHolder; }
        public int     getAcquireCount() { return acquireCount; }
        public String  getName()         { return name; }
    }
}
