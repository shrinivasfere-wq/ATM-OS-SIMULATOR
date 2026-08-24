package ui;

import atm.Account;
import atm.Bank;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RacePanel extends VBox {

    private final Bank bank = Bank.getInstance();

    // ── Account selector ──────────────────────────────────────────
    private ComboBox<String> accountSelector;
    private TextField        withdrawAmountField;
    private TextField        numAtmsField;

    // ── Unsafe side ───────────────────────────────────────────────
    private final TextArea unsafeLog    = logArea("#EF4444");
    private final Label    unsafeResult = bigLabel("?", "#EF4444");
    private final Label    unsafeExpected = infoLabel("Expected: ?");

    // ── Safe side ─────────────────────────────────────────────────
    private final TextArea safeLog      = logArea("#10B981");
    private final Label    safeResult   = bigLabel("?", "#10B981");
    private final Label    safeExpected = infoLabel("Expected: ?");

    // ── Live balance display ───────────────────────────────────────
    private Label liveBalanceLabel;

    public RacePanel() { build(); }

    private void build() {
        setSpacing(14);
        setPadding(new Insets(14));
        setStyle("-fx-background-color: #0F172A;");

        // ── Title ──────────────────────────────────────────────────
        Label title = new Label("⚡  RACE CONDITION DEMO — Real ATM Account, Real Threads");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        title.setTextFill(Color.web("#EF4444"));

        Label desc = new Label(
            "Uses REAL account balances from the ATM system. " +
            "Multiple ATM threads withdraw simultaneously from the same account. " +
            "WITHOUT mutex: threads overwrite each other's writes (money disappears). " +
            "WITH mutex: only one thread enters the critical section at a time.");
        desc.setFont(Font.font("Segoe UI", 10));
        desc.setTextFill(Color.web("#64748B"));
        desc.setWrapText(true);

        // ── Configuration panel ────────────────────────────────────
        VBox configCard = new VBox(10);
        configCard.setPadding(new Insets(12));
        configCard.setStyle("-fx-background-color: #1E293B; -fx-border-color: #F59E0B;" +
                            "-fx-border-width: 0 0 0 3; -fx-border-radius: 0 14 14 0; -fx-background-radius: 14;" +
                            "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.25),10,0,0,3);");

        Label configTitle = sectionLabel("CONFIGURE SCENARIO", "#F59E0B");

        HBox configRow = new HBox(16);
        configRow.setAlignment(Pos.CENTER_LEFT);

        // Account selector — real accounts from Bank
        Label accLbl = dimLabel("Account:");
        accountSelector = new ComboBox<>();
        bank.getAllAccounts().forEach(acc ->
            accountSelector.getItems().add(
                acc.getAccountId() + " — " + acc.getHolderName() +
                " (Rs." + String.format("%.0f", acc.getBalance()) + ")")
        );
        accountSelector.setValue(accountSelector.getItems().get(0));
        accountSelector.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155;" +
                                 "-fx-text-fill: #F59E0B; -fx-font-family: 'Segoe UI'; -fx-font-size: 11;");
        accountSelector.setPrefWidth(310);
        // Update live balance on selection change
        accountSelector.setOnAction(e -> updateLiveBalance());

        // Withdraw amount
        Label amtLbl = dimLabel("Each ATM withdraws:");
        withdrawAmountField = new TextField("5000");
        styleField(withdrawAmountField, 90);

        // Number of ATMs
        Label numLbl = dimLabel("Number of ATMs:");
        numAtmsField = new TextField("3");
        styleField(numAtmsField, 50);

        // Live balance display
        liveBalanceLabel = new Label();
        liveBalanceLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        liveBalanceLabel.setTextFill(Color.web("#38BDF8"));
        updateLiveBalance();

        configRow.getChildren().addAll(
            accLbl, accountSelector,
            amtLbl, withdrawAmountField,
            numLbl, numAtmsField,
            liveBalanceLabel
        );

        configCard.getChildren().addAll(configTitle, configRow);

        // ── Theory explanation cards ───────────────────────────────
        HBox theoryRow = new HBox(12,
            theoryCard("🔴 RACE CONDITION",
                "Thread A reads balance Rs.10000\nThread B reads balance Rs.10000\n" +
                "Thread A writes Rs.8000\nThread B also writes Rs.8000\n" +
                "→ Rs.2000 LOST! Both thought they were first.",
                "#EF4444"),
            theoryCard("🟢 MUTEX SOLUTION",
                "Thread A acquires lock → reads Rs.10000\n" +
                "Thread A writes Rs.8000 → releases lock\n" +
                "Thread B acquires lock → reads Rs.8000\n" +
                "Thread B writes Rs.6000 → releases lock\n" +
                "→ Correct! Each ATM sees updated balance.",
                "#10B981"),
            theoryCard("🔵 CRITICAL SECTION",
                "The read-check-write sequence is the\n" +
                "CRITICAL SECTION — only one thread\n" +
                "should execute it at a time.\n" +
                "Java: synchronized block protects it.",
                "#38BDF8")
        );
        theoryRow.getChildren().forEach(n -> HBox.setHgrow((javafx.scene.Node) n, Priority.ALWAYS));

        // ── Side by side simulation panels ────────────────────────
        HBox simRow = new HBox(12);

        VBox leftSide  = buildSimSide("WITHOUT MUTEX — RACE CONDITION",
            "#EF4444", unsafeLog, unsafeResult, unsafeExpected, false);
        VBox rightSide = buildSimSide("WITH MUTEX — THREAD SAFE",
            "#10B981", safeLog, safeResult, safeExpected, true);

        HBox.setHgrow(leftSide,  Priority.ALWAYS);
        HBox.setHgrow(rightSide, Priority.ALWAYS);
        simRow.getChildren().addAll(leftSide, rightSide);

        // ── Run both button ────────────────────────────────────────
        Button runBothBtn = new Button("▶▶  RUN BOTH SIMULTANEOUSLY — SEE THE DIFFERENCE");
        runBothBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        runBothBtn.setTextFill(Color.web("#F59E0B"));
        runBothBtn.setMaxWidth(Double.MAX_VALUE);
        runBothBtn.setStyle(
            "-fx-background-color: #F59E0B22; -fx-border-color: #F59E0B;" +
            "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10;");
        runBothBtn.setOnAction(e -> runBoth(runBothBtn));

        getChildren().addAll(title, desc, configCard, theoryRow, runBothBtn, simRow);
    }

    // ── Build one simulation side ──────────────────────────────────
    private VBox buildSimSide(String titleText, String clr,
                              TextArea log, Label result, Label expected,
                              boolean useMutex) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle(String.format(
            "-fx-background-color: #1E293B; -fx-border-color: %s33;" +
            "-fx-border-radius: 10; -fx-background-radius: 10;", clr));

        Label titleLbl = new Label(titleText);
        titleLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        titleLbl.setTextFill(Color.web(clr));

        HBox resultRow = new HBox(20);
        resultRow.setAlignment(Pos.CENTER_LEFT);

        VBox finalBalBox = new VBox(2);
        Label finalLbl = new Label("FINAL BALANCE");
        finalLbl.setFont(Font.font("Segoe UI", 9));
        finalLbl.setTextFill(Color.web("#475569"));
        finalBalBox.getChildren().addAll(finalLbl, result);

        finalBalBox.setAlignment(Pos.CENTER_LEFT);
        resultRow.getChildren().addAll(finalBalBox, expected);

        Button runBtn = new Button("▶ RUN " + (useMutex ? "SAFE" : "UNSAFE"));
        runBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        runBtn.setTextFill(Color.web(clr));
        runBtn.setMaxWidth(Double.MAX_VALUE);
        runBtn.setStyle(String.format(
            "-fx-background-color: %s22; -fx-border-color: %s;" +
            "-fx-border-radius: 10; -fx-background-radius: 10; -fx-cursor: hand;", clr, clr));

        runBtn.setOnAction(e -> {
            runBtn.setDisable(true);
            log.clear();
            result.setText("...");
            expected.setText("");
            if (useMutex) runSafe(log, result, expected, runBtn);
            else          runUnsafe(log, result, expected, runBtn);
        });

        Button resetBtn = new Button("RESET");
        resetBtn.setFont(Font.font("Segoe UI", 10));
        resetBtn.setTextFill(Color.web("#64748B"));
        resetBtn.setStyle("-fx-background-color: #334155; -fx-border-color: #334155;" +
                          "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;");
        resetBtn.setOnAction(e -> {
            log.clear();
            result.setText("?");
            expected.setText("");
            runBtn.setDisable(false);
        });

        HBox btnRow = new HBox(8, runBtn, resetBtn);
        HBox.setHgrow(runBtn, Priority.ALWAYS);
        box.getChildren().addAll(titleLbl, resultRow, log, btnRow);
        return box;
    }

    // ── Get selected real account ─────────────────────────────────
    private Account getSelectedAccount() {
        String selected = accountSelector.getValue();
        if (selected == null) return null;
        String accId = selected.split(" ")[0]; // "ACC001 — Alice..."
        return bank.getAccount(accId);
    }

    private int getWithdrawAmount() {
        try { return Math.max(1, Integer.parseInt(withdrawAmountField.getText().trim())); }
        catch (Exception e) { return 5000; }
    }

    private int getNumAtms() {
        try { return Math.max(2, Math.min(5, Integer.parseInt(numAtmsField.getText().trim()))); }
        catch (Exception e) { return 3; }
    }

    private void updateLiveBalance() {
        Account acc = getSelectedAccount();
        if (acc != null && liveBalanceLabel != null) {
            liveBalanceLabel.setText("Live Balance: Rs." + String.format("%.2f", acc.getBalance()));
        }
    }

    // ── UNSAFE simulation — real account, no lock ─────────────────
    private void runUnsafe(TextArea log, Label result, Label expected, Button btn) {
        Account acc = getSelectedAccount();
        if (acc == null) { appendLog(log, "ERROR: No account selected."); return; }

        int withdrawAmt = getWithdrawAmount();
        int numAtms     = getNumAtms();

        // Snapshot real balance BEFORE — using a plain int[] (no sync) to simulate race
        double realBalanceBefore = acc.getBalance();
        int[]  racyBalance       = {(int) realBalanceBefore};
        double expectedFinal     = Math.max(0, realBalanceBefore - (withdrawAmt * numAtms));

        Platform.runLater(() -> {
            expected.setText("  Expected: Rs." + String.format("%.0f", expectedFinal) +
                             " | Starting: Rs." + String.format("%.0f", realBalanceBefore));
            expected.setTextFill(Color.web("#64748B"));
        });

        new Thread(() -> {
            CountDownLatch latch = new CountDownLatch(numAtms);
            ExecutorService pool = Executors.newFixedThreadPool(numAtms);

            appendLog(log, "Account: " + acc.getAccountId() + " — " + acc.getHolderName());
            appendLog(log, "Real balance before: Rs." + String.format("%.0f", realBalanceBefore));
            appendLog(log, "Each ATM withdraws: Rs." + withdrawAmt);
            appendLog(log, "─".repeat(45));
            appendLog(log, "⚠ NO MUTEX — All ATMs read simultaneously");
            appendLog(log, "");

            for (int i = 1; i <= numAtms; i++) {
                final int atmNum = i;
                pool.submit(() -> {
                    try {
                        String name = "ATM-0" + atmNum;
                        // Step 1: READ (all threads read around the same time)
                        int read = racyBalance[0];
                        appendLog(log, "[" + name + "] READ  balance = Rs." + read);

                        // Delay simulates processing time — other threads read SAME value
                        Thread.sleep(40 + atmNum * 20L);

                        // Step 2: CHECK and WRITE (without lock — overwrites each other)
                        if (read >= withdrawAmt) {
                            int newBal = read - withdrawAmt;
                            Thread.sleep(20); // tiny delay — another thread may write here
                            racyBalance[0] = newBal; // ← RACE: overwrites other thread's write
                            appendLog(log, "[" + name + "] WROTE Rs." + newBal +
                                "  ← may overwrite another ATM's update!");
                        } else {
                            appendLog(log, "[" + name + "] DECLINED — insufficient (read Rs." + read + ")");
                        }
                    } catch (InterruptedException ignored) {}
                    finally { latch.countDown(); }
                });
            }

            try { latch.await(); } catch (InterruptedException ignored) {}
            pool.shutdown();

            int finalBal   = racyBalance[0];
            int lostMoney  = (int)realBalanceBefore - finalBal;
            int correctFinal = Math.max(0, (int)realBalanceBefore - (withdrawAmt * numAtms));
            boolean correct = (finalBal == correctFinal);

            appendLog(log, "");
            appendLog(log, "─".repeat(45));
            appendLog(log, "Expected: Rs." + correctFinal);
            appendLog(log, "Actual  : Rs." + finalBal);
            if (!correct) {
                appendLog(log, "🔴 RACE CONDITION! Rs." + Math.abs(finalBal - correctFinal) +
                    " discrepancy!");
                appendLog(log, "   Some ATM withdrawals were overwritten.");
            } else {
                appendLog(log, "✓ No race this run (timing-dependent, run again)");
            }

            Platform.runLater(() -> {
                result.setText("Rs." + finalBal);
                result.setTextFill(Color.web(correct ? "#F59E0B" : "#EF4444"));
                expected.setTextFill(Color.web(correct ? "#64748B" : "#EF4444"));
                btn.setDisable(false);
                updateLiveBalance();
            });
        }, "unsafe-sim").start();
    }

    // ── SAFE simulation — real account, real synchronized lock ────
    private void runSafe(TextArea log, Label result, Label expected, Button btn) {
        Account acc = getSelectedAccount();
        if (acc == null) { appendLog(log, "ERROR: No account selected."); return; }

        int withdrawAmt = getWithdrawAmount();
        int numAtms     = getNumAtms();

        double realBalanceBefore = acc.getBalance();
        double expectedFinal     = Math.max(0, realBalanceBefore - (withdrawAmt * numAtms));

        Platform.runLater(() -> {
            expected.setText("  Expected: Rs." + String.format("%.0f", expectedFinal) +
                             " | Starting: Rs." + String.format("%.0f", realBalanceBefore));
            expected.setTextFill(Color.web("#64748B"));
        });

        // The SAME Account object is the mutex — this is exactly how real banking works
        new Thread(() -> {
            CountDownLatch latch = new CountDownLatch(numAtms);
            ExecutorService pool = Executors.newFixedThreadPool(numAtms);

            appendLog(log, "Account: " + acc.getAccountId() + " — " + acc.getHolderName());
            appendLog(log, "Real balance before: Rs." + String.format("%.0f", realBalanceBefore));
            appendLog(log, "Each ATM withdraws: Rs." + withdrawAmt);
            appendLog(log, "─".repeat(45));
            appendLog(log, "✅ MUTEX ACTIVE — synchronized on Account object");
            appendLog(log, "");

            for (int i = 1; i <= numAtms; i++) {
                final int atmNum = i;
                pool.submit(() -> {
                    try {
                        String name = "ATM-0" + atmNum;
                        Thread.sleep(atmNum * 10L);
                        appendLog(log, "[" + name + "] Requesting lock on account...");

                        // synchronized on the REAL Account object — same mutex for all threads
                        synchronized (acc) {
                            appendLog(log, "[" + name + "] ✔ LOCK ACQUIRED — entering critical section");
                            double cur = acc.getBalance();
                            appendLog(log, "[" + name + "] READ  balance = Rs." + String.format("%.0f", cur));
                            Thread.sleep(60); // simulate processing

                            if (cur >= withdrawAmt) {
                                // Call real account.withdraw() — updates actual balance
                                acc.withdraw(withdrawAmt);
                                appendLog(log, "[" + name + "] WITHDREW Rs." + withdrawAmt +
                                    "  → New balance: Rs." + String.format("%.0f", acc.getBalance()));
                            } else {
                                appendLog(log, "[" + name + "] DECLINED — insufficient (Rs." +
                                    String.format("%.0f", cur) + ")");
                            }
                            appendLog(log, "[" + name + "] LOCK RELEASED");
                        }
                    } catch (InterruptedException ignored) {}
                    finally { latch.countDown(); }
                });
            }

            try { latch.await(); } catch (InterruptedException ignored) {}
            pool.shutdown();

            double finalBal = acc.getBalance();
            boolean correct = Math.abs(finalBal - expectedFinal) < 0.01
                              || finalBal == 0; // could have hit zero legitimately

            appendLog(log, "");
            appendLog(log, "─".repeat(45));
            appendLog(log, "Expected: Rs." + String.format("%.0f", expectedFinal));
            appendLog(log, "Actual  : Rs." + String.format("%.2f", finalBal));
            appendLog(log, correct
                ? "✅ CORRECT — Mutex prevented race condition"
                : "⚠ Check: balance may differ if acc was used elsewhere");

            Platform.runLater(() -> {
                result.setText("Rs." + String.format("%.0f", finalBal));
                result.setTextFill(Color.web("#10B981"));
                btn.setDisable(false);
                updateLiveBalance();
            });
        }, "safe-sim").start();
    }

    // ── Run both simultaneously ────────────────────────────────────
    private void runBoth(Button runBothBtn) {
        runBothBtn.setDisable(true);
        unsafeLog.clear(); safeLog.clear();
        unsafeResult.setText("..."); safeResult.setText("...");
        unsafeExpected.setText(""); safeExpected.setText("");

        // Both run at same time — unsafe one uses racy int[], safe one uses real Account
        runUnsafe(unsafeLog, unsafeResult, unsafeExpected, new Button());
        runSafe(safeLog,   safeResult,   safeExpected,   new Button());

        // Re-enable after delay
        new Thread(() -> {
            try { Thread.sleep(4000); }
            catch (InterruptedException ignored) {}
            Platform.runLater(() -> runBothBtn.setDisable(false));
        }).start();
    }

    // ── Theory card ───────────────────────────────────────────────
    private VBox theoryCard(String title, String body, String color) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle(String.format(
            "-fx-background-color: #1E293B; -fx-border-color: %s;" +
            "-fx-border-width: 0 0 0 3; -fx-border-radius: 0 14 14 0; -fx-background-radius: 14;" +
            "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.25),10,0,0,3);", color));
        Label t = new Label(title);
        t.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        t.setTextFill(Color.web(color));
        Label b = new Label(body);
        b.setFont(Font.font("Segoe UI", 10));
        b.setTextFill(Color.web("#CBD5E1"));
        b.setWrapText(true);
        box.getChildren().addAll(t, b);
        return box;
    }

    // ── Helpers ───────────────────────────────────────────────────
    private void appendLog(TextArea log, String line) {
        Platform.runLater(() -> log.appendText(line + "\n"));
        try { Thread.sleep(70); } catch (InterruptedException ignored) {}
    }

    private TextArea logArea(String accentColor) {
        TextArea ta = new TextArea();
        ta.setEditable(false);
        ta.setPrefHeight(260);
        ta.setWrapText(false);
        ta.setStyle(String.format(
            "-fx-control-inner-background:#0F172A; -fx-text-fill:#CBD5E1;" +
            "-fx-font-family:'Segoe UI'; -fx-font-size:11; " +
            "-fx-border-color:%s; -fx-border-width:0 0 0 3; -fx-border-radius:0 10 10 0;", accentColor));
        return ta;
    }

    private Label bigLabel(String text, String clr) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        l.setTextFill(Color.web(clr));
        return l;
    }

    private Label infoLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", 10));
        l.setTextFill(Color.web("#64748B"));
        l.setWrapText(true);
        return l;
    }

    private Label sectionLabel(String text, String color) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web(color));
        return l;
    }

    private Label dimLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", 10));
        l.setTextFill(Color.web("#64748B"));
        return l;
    }

    private void styleField(TextField f, double width) {
        f.setPrefWidth(width);
        f.setStyle("-fx-background-color:#0F172A; -fx-border-color:#334155; -fx-border-radius:4;" +
                   "-fx-background-radius:4; -fx-text-fill:#F1F5F9; -fx-font-family:'Segoe UI';" +
                   "-fx-font-size:12;");
    }
}
