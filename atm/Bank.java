package atm;

import java.util.*;

public class Bank {
    private static Bank instance;
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private int accountCounter = 1006;

private Bank() {
        accounts.put("ACC001", new Account("ACC001", "Akshat Doshi",  "1111", 50000.00));
        accounts.put("ACC002", new Account("ACC002", "Shrinivas Fere",   "2222", 25000.00));
        accounts.put("ACC003", new Account("ACC003", "Aditya Ghatol","3333", 10000.00));
        accounts.put("ACC004", new Account("ACC004", "Gaurav Bhendekar",  "4444", 18000.00));
        accounts.put("ACC005", new Account("ACC005", "Gangarde Aditya ",    "5555", 91000.00));
         accounts.put("ACC006", new Account("ACC006", "Harshita Gupta",    "6666", 62000.00));
    }

    public static synchronized Bank getInstance() {
        if (instance == null) instance = new Bank();
        return instance;
    }

    public Account getAccount(String id)       { return accounts.get(id); }
    public boolean accountExists(String id)    { return accounts.containsKey(id); }
    public Collection<Account> getAllAccounts(){ return accounts.values(); }

    public Account createAccount(String name, String pin, double initial) {
        String id = "ACC" + (accountCounter++);
        Account a = new Account(id, name, pin, initial);
        accounts.put(id, a);
        return a;
    }
}
