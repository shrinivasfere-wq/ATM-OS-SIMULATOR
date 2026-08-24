package fs;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TransactionLog {
    private final List<String> journal = Collections.synchronizedList(new ArrayList<>());
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void writeAheadLog(String accountId, String type, double amount) {
        add(String.format("[WAL-BEGIN] | %s | Acc:%-8s | Type:%-12s | Rs.%.2f",
            now(), accountId, type, amount));
    }

    public void commitLog(String accountId, String type, double amount, double newBalance) {
        add(String.format("[COMMITTED] | %s | Acc:%-8s | %-12s | Rs.%.2f | Bal:Rs.%.2f",
            now(), accountId, type, amount, newBalance));
    }

    public void rollbackLog(String accountId, String reason) {
        add(String.format("[ROLLBACK ] | %s | Acc:%-8s | Reason:%s",
            now(), accountId, reason));
    }

    public void logInterrupt(String type, String msg) {
        add(String.format("[INTERRUPT] | %s | %-16s | %s", now(), type, msg));
    }

    private void add(String entry) {
        journal.add(0, entry);
        if (journal.size() > 200) journal.remove(journal.size() - 1);
    }

    public List<String> getJournal() { return Collections.unmodifiableList(journal); }

    public List<String> getLastN(int n) {
        int to = Math.min(n, journal.size());
        return new ArrayList<>(journal.subList(0, to));
    }

    private String now() { return LocalDateTime.now().format(FMT); }
}
