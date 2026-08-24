package controller;

import atm.Account;
import atm.Bank;
import fs.TransactionLog;
import kernel.Kernel;
import process.OSProcess;
import scheduler.SchedulerEngine;
import scheduler.Transaction;
import scheduler.TransactionType;
import sync.SemaphoreManager;
import kernel.InterruptHandler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ATMController {
    private final Kernel          kernel    = Kernel.getInstance();
    private final Bank            bank      = Bank.getInstance();
    private final SchedulerEngine scheduler = SchedulerEngine.getInstance();

    // ── Auth ─────────────────────────────────────────────────────
    public boolean accountExists(String id)           { return bank.accountExists(id); }
    public boolean validatePin(String id, String pin) {
        Account a = bank.getAccount(id);
        return a != null && a.verifyPin(pin);
    }

    // ── Called by ATMPanel — fires interrupt on wrong PIN ─────────
    public boolean validatePinWithInterrupt(String id, String pin, Consumer<String> out) {
        Account a = bank.getAccount(id);
        if (a == null) return false;
        boolean ok = a.verifyPin(pin);
        if (!ok) {
            kernel.getInterruptHandler().onWrongPin(id, a.getFailedAttempts(), out);
        }
        return ok;
    }

    // ── Called by ATMPanel — fires INVALID_AMOUNT interrupt ───────
    public void fireInvalidAmountInterrupt(String atmId, double amount, Consumer<String> out) {
        kernel.getInterruptHandler().onInvalidAmount(atmId, amount, out);
    }

    // ── Called by ATMPanel — fires CARD_TIMEOUT interrupt ─────────
    public void fireTimeoutInterrupt(String atmId, String accountId, Consumer<String> out) {
        kernel.getInterruptHandler().onSessionTimeout(atmId, accountId, out);
    }
    public boolean isLocked(String id) {
        Account a = bank.getAccount(id);
        return a != null && a.isLocked();
    }

    public int getFailedAttempts(String id) {
        Account a = bank.getAccount(id);
        return a != null ? a.getFailedAttempts() : 0;
    }

    // ── Session ──────────────────────────────────────────────────
    public OSProcess openSession(String atmId, String accountId) {
        kernel.getMemoryManager().allocate(accountId);
        return kernel.getProcessManager()
                     .createProcess("atm_session_" + atmId + "_" + accountId, 2, accountId);
    }

    public void closeSession(String accountId, OSProcess proc) {
        kernel.getMemoryManager().free(accountId);
        kernel.getProcessManager().terminateProcess(proc);
    }

    // ── Submit through Scheduler (used by ATMPanel UI) ────────────
    public CompletableFuture<String> submitTransaction(
            String atmId, String accountId,
            String type, double amount,
            String targetAccountId) {

        TransactionType ttype = switch (type) {
            case "WITHDRAW" -> TransactionType.WITHDRAW;
            case "DEPOSIT"  -> TransactionType.DEPOSIT;
            case "TRANSFER" -> TransactionType.TRANSFER;
            default         -> TransactionType.BALANCE;
        };

        int priority = switch (ttype) {
            case TRANSFER -> 1;
            case WITHDRAW -> 2;
            case DEPOSIT  -> 3;
            case BALANCE  -> 4;
        };

        Transaction txn = new Transaction(atmId, accountId, ttype, amount, priority);
        if (targetAccountId != null) txn.setTargetAccountId(targetAccountId);
        return scheduler.submitWithCallback(txn, t -> executeBanking(t));
    }

    private String executeBanking(Transaction txn) {
        return switch (txn.getType()) {
            // NETWORK_FAILURE: 8% random chance aborts transaction
            case WITHDRAW -> withdrawWithInterrupt(txn.getAccountId(), txn.getAmount(), line -> {});
            case DEPOSIT  -> deposit(txn.getAccountId(),  txn.getAmount());
            case TRANSFER -> transfer(txn.getAccountId(), txn.getTargetAccountId(), txn.getAmount());
            case BALANCE  -> getBalance(txn.getAccountId());
        };
    }

    // ── Submit with interrupt wiring (used by ATMPanel submitViaScheduler) ──
    public java.util.concurrent.CompletableFuture<String> submitTransactionWithInterrupt(
            String atmId, String accountId,
            String type, double amount,
            String targetAccountId) {
        // Same as submitTransaction but executeBanking already uses withdrawWithInterrupt
        return submitTransaction(atmId, accountId, type, amount, targetAccountId);
    }

    // ── Direct banking operations ─────────────────────────────────
    public String withdraw(String accountId, double amount) {
        Account acc = bank.getAccount(accountId);
        if (acc == null) return "FAIL: Account not found";

        SemaphoreManager sm = kernel.getSemaphoreManager();
        String res = "ACCOUNT_" + accountId;
        OSProcess txnProc = kernel.getProcessManager()
            .createProcess("txn_withdraw_" + accountId, 3, accountId);

        sm.acquire(res, txnProc.getName());
        TransactionLog log = kernel.getTransactionLog();
        log.writeAheadLog(accountId, "WITHDRAW", amount);
        kernel.getMemoryManager().markDirty(accountId);

        String result;
        if (acc.withdraw(amount)) {
            log.commitLog(accountId, "WITHDRAW", amount, acc.getBalance());
            result = "OK: Withdrew Rs." + String.format("%.2f", amount)
                   + " | Bal: Rs." + String.format("%.2f", acc.getBalance());
        } else {
            log.rollbackLog(accountId, "Insufficient funds");
            result = "FAIL: Insufficient funds";
        }

        sm.release(res, txnProc.getName());
        kernel.getProcessManager().terminateProcess(txnProc);
        return result;
    }

    // ── Random network failure during withdraw (8% chance) ────────
    public String withdrawWithInterrupt(String accountId, double amount, Consumer<String> interruptOut) {
        InterruptHandler ih = kernel.getInterruptHandler();
        if (ih.shouldFireRandom()) {
            ih.onNetworkFailure(accountId, interruptOut);
            kernel.getTransactionLog().rollbackLog(accountId, "Network failure interrupt");
            return "FAIL: Network failure — transaction aborted (see Interrupt tab)";
        }
        return withdraw(accountId, amount);
    }

    public String deposit(String accountId, double amount) {
        Account acc = bank.getAccount(accountId);
        if (acc == null) return "FAIL: Account not found";

        SemaphoreManager sm = kernel.getSemaphoreManager();
        String res = "ACCOUNT_" + accountId;
        OSProcess txnProc = kernel.getProcessManager()
            .createProcess("txn_deposit_" + accountId, 3, accountId);

        sm.acquire(res, txnProc.getName());
        TransactionLog log = kernel.getTransactionLog();
        log.writeAheadLog(accountId, "DEPOSIT", amount);
        kernel.getMemoryManager().markDirty(accountId);
        acc.deposit(amount);
        log.commitLog(accountId, "DEPOSIT", amount, acc.getBalance());

        sm.release(res, txnProc.getName());
        kernel.getProcessManager().terminateProcess(txnProc);
        return "OK: Deposited Rs." + String.format("%.2f", amount)
             + " | Bal: Rs." + String.format("%.2f", acc.getBalance());
    }

    public String transfer(String fromId, String toId, double amount) {
        Account from = bank.getAccount(fromId);
        Account to   = bank.getAccount(toId);
        if (from == null) return "FAIL: Source account not found";
        if (to   == null) return "FAIL: Target account not found";
        if (fromId.equals(toId)) return "FAIL: Cannot transfer to same account";

        SemaphoreManager sm = kernel.getSemaphoreManager();

        // Alphabetical lock ordering — deadlock prevention for real transfers
        String lock1 = "ACCOUNT_" + (fromId.compareTo(toId) < 0 ? fromId : toId);
        String lock2 = "ACCOUNT_" + (fromId.compareTo(toId) < 0 ? toId   : fromId);

        OSProcess txnProc = kernel.getProcessManager()
            .createProcess("txn_transfer_" + fromId, 4, fromId);

        sm.acquire(lock1, txnProc.getName());

        // FIX 4: timeout on second lock — max 10 retries (3 seconds), then abort
        int retries = 0;
        boolean gotLock2 = false;
        while (retries < 10) {
            if (sm.acquire(lock2, txnProc.getName())) { gotLock2 = true; break; }
            txnProc.setState("BLOCKED");
            try { Thread.sleep(300); } catch (Exception e) { break; }
            retries++;
        }

        if (!gotLock2) {
            sm.release(lock1, txnProc.getName());
            kernel.getProcessManager().terminateProcess(txnProc);
            kernel.getTransactionLog().rollbackLog(fromId, "Could not acquire lock2 — timeout");
            return "FAIL: Transfer timeout — resource busy";
        }

        TransactionLog log = kernel.getTransactionLog();
        log.writeAheadLog(fromId, "TRANSFER->" + toId, amount);
        kernel.getMemoryManager().markDirty(fromId);
        kernel.getMemoryManager().markDirty(toId);

        String result;
        if (from.transfer(to, amount)) {
            log.commitLog(fromId, "TRANSFER", amount, from.getBalance());
            result = "OK: Transferred Rs." + String.format("%.2f", amount)
                   + " to " + toId + " | Bal: Rs." + String.format("%.2f", from.getBalance());
        } else {
            log.rollbackLog(fromId, "Insufficient funds for transfer");
            result = "FAIL: Insufficient funds";
        }

        sm.release(lock2, txnProc.getName());
        sm.release(lock1, txnProc.getName());
        kernel.getProcessManager().terminateProcess(txnProc);
        return result;
    }

    public String getBalance(String accountId) {
        Account acc = bank.getAccount(accountId);
        if (acc == null) return "FAIL: Account not found";
        kernel.getMemoryManager().access(accountId);
        return "OK: Balance = Rs." + String.format("%.2f", acc.getBalance());
    }

    public double getRawBalance(String accountId) {
        Account acc = bank.getAccount(accountId);
        return acc != null ? acc.getBalance() : -1;
    }

    public java.util.List<String> getMiniStatement(String accountId) {
        Account acc = bank.getAccount(accountId);
        return acc != null ? acc.getLastTransactions(5) : java.util.Collections.emptyList();
    }

    public boolean changePin(String accountId, String newPin) {
        Account acc = bank.getAccount(accountId);
        if (acc == null) return false;
        acc.changePin(newPin);
        kernel.getTransactionLog().writeAheadLog(accountId, "PIN_CHANGE", 0);
        return true;
    }

    // ── Auto-sim ──────────────────────────────────────────────────
    public void submitAutoTransaction(String atmId, String accountId,
                                      String type, double amount) {
        String toId = null;
        if (type.equals("TRANSFER")) {
            java.util.List<String> ids = new java.util.ArrayList<>(
                bank.getAllAccounts().stream()
                    .map(a -> a.getAccountId())
                    .filter(id -> !id.equals(accountId))
                    .toList());
            if (!ids.isEmpty())
                toId = ids.get((int)(Math.random() * ids.size()));
            else return;
        }
        final String finalToId = toId;
        new Thread(() ->
            submitTransaction(atmId, accountId, type, amount, finalToId),
        "autoSim-" + atmId).start();
    }

    public Kernel getKernel() { return kernel; }
    public Bank   getBank()   { return bank; }
}
