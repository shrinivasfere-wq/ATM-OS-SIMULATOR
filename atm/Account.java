package atm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Account {
    private final String accountId;
    private final String holderName;
    private String pin;
    // FIX 2: volatile balance + all mutating methods synchronized
    private volatile double balance;
    private final List<String> transactionHistory =
        Collections.synchronizedList(new ArrayList<>());
    private volatile int     failedPinAttempts = 0;
    private volatile boolean locked            = false;

    public Account(String accountId, String holderName, String pin, double initialBalance) {
        this.accountId  = accountId;
        this.holderName = holderName;
        this.pin        = pin;
        this.balance    = initialBalance;
    }

    public synchronized boolean verifyPin(String input) {
        if (locked) return false;
        if (pin.equals(input)) {
            failedPinAttempts = 0;
            return true;
        }
        failedPinAttempts++;
        if (failedPinAttempts >= 3) locked = true;
        return false;
    }

    // synchronized — prevents two threads reading same balance simultaneously
    public synchronized boolean withdraw(double amount) {
        if (amount <= 0 || amount > balance) return false;
        balance -= amount;
        addHistory("WITHDRAW  Rs." + String.format("%.2f", amount)
                 + "  | Bal: Rs." + String.format("%.2f", balance));
        return true;
    }

    public synchronized void deposit(double amount) {
        balance += amount;
        addHistory("DEPOSIT   Rs." + String.format("%.2f", amount)
                 + "  | Bal: Rs." + String.format("%.2f", balance));
    }

    public synchronized boolean transfer(Account target, double amount) {
        if (!withdraw(amount)) return false;
        target.deposit(amount);
        addHistory("TRANSFER  Rs." + String.format("%.2f", amount)
                 + " -> " + target.getAccountId());
        return true;
    }

    public synchronized void changePin(String newPin) {
        this.pin = newPin;
        addHistory("PIN CHANGED");
    }

    private void addHistory(String entry) {
        String ts = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));
        transactionHistory.add("[" + ts + "]  " + entry);
    }

    public List<String> getLastTransactions(int n) {
        synchronized (transactionHistory) {
            int from = Math.max(0, transactionHistory.size() - n);
            return new ArrayList<>(transactionHistory.subList(from, transactionHistory.size()));
        }
    }

    public String  getAccountId()      { return accountId; }
    public String  getHolderName()     { return holderName; }
    public double  getBalance()        { return balance; }
    public boolean isLocked()          { return locked; }
    public int     getFailedAttempts() { return failedPinAttempts; }
}
