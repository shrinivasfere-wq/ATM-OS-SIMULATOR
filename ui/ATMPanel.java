package ui;

import controller.ATMController;
import javafx.application.Platform;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import process.OSProcess;

import java.util.concurrent.CompletableFuture;

public class ATMPanel extends VBox {

    private final String        atmId;
    private final String        color;
    private final ATMController controller;

    private String    currentAccId = "";
    private OSProcess sessionProc  = null;

    private Label         statusLabel;
    private TextField     accField, amountField, toAccField, pinChangField;
    private PasswordField pinField;
    private Label         screenMsg;
    private VBox          screenContent;

    // Session idle timer — fires CARD_TIMEOUT if user inactive for 30s
    private Timer   sessionTimer = null;
    private static final int SESSION_TIMEOUT_SEC = 120;

    private enum Step { IDLE, PIN, MENU, AMOUNT, CONFIRM, PROCESSING, RESULT }
    private Step   step        = Step.IDLE;
    private String pendingType = "";

    public ATMPanel(String atmId, String color, ATMController controller) {
        this.atmId      = atmId;
        this.color      = color;
        this.controller = controller;
        build();
    }

    private void build() {
        setSpacing(8);
        setPadding(new Insets(12));
        setStyle(String.format(
            "-fx-background-color: #1E293B; " +
            "-fx-border-color: %s; " +
            "-fx-border-radius: 16; -fx-background-radius: 16; " +
            "-fx-border-width: 0 0 3 0; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 16, 0, 0, 4);" +
            "-fx-min-width: 310;",
            color));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("●");
        dot.setTextFill(Color.web(color));
        dot.setFont(Font.font(14));
        Label title = new Label(atmId);
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        title.setTextFill(Color.web(color));
        statusLabel = new Label("STANDBY");
        statusLabel.setFont(Font.font("Segoe UI", 9));
        statusLabel.setTextFill(Color.web("#475569"));
        statusLabel.setPadding(new Insets(1, 6, 1, 6));
        statusLabel.setStyle(String.format(
            "-fx-background-color: %s1a; -fx-border-color: %s44; -fx-border-radius: 6; -fx-background-radius: 6;",
            color, color));
        header.getChildren().addAll(dot, title, statusLabel);

        VBox screen = new VBox(8);
        screen.setPadding(new Insets(12));
        screen.setMinHeight(260);
        screen.setStyle(String.format(
            "-fx-background-color: #0F172A; -fx-border-color: %s22; -fx-border-radius: 6; -fx-background-radius: 6;",
            color));
        screenContent = new VBox(8);
        screenContent.setAlignment(Pos.TOP_CENTER);
        screen.getChildren().add(screenContent);
        showIdle();

        getChildren().addAll(header, screen);
    }

    // ── IDLE ─────────────────────────────────────────────────────
    private void showIdle() {
        step = Step.IDLE;
        setStatus("STANDBY");
        screenContent.getChildren().clear();
        screenContent.setAlignment(Pos.TOP_CENTER);

        Label welcome = new Label("Welcome");
        welcome.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        welcome.setTextFill(Color.web(color));

        Label hint = new Label("Enter Account Number");
        hint.setFont(Font.font("Segoe UI", 10));
        hint.setTextFill(Color.web("#475569"));

        accField = new TextField();
        styleInput(accField, "Account ID  e.g. ACC001");

        Button proceed = new Button("PROCEED  →");
        styleBtn(proceed, color);
        proceed.setMaxWidth(Double.MAX_VALUE);
        proceed.setOnAction(e -> {
            String acc = accField.getText().trim().toUpperCase();
            if (!controller.accountExists(acc)) {
                flashMsg("Account not found: " + acc);
            } else if (controller.isLocked(acc)) {
                flashMsg("Account LOCKED — too many failed PIN attempts");
            } else {
                currentAccId = acc;
                showPin();
            }
        });

        screenMsg = new Label("");
        screenMsg.setFont(Font.font("Segoe UI", 10));
        screenMsg.setTextFill(Color.web("#EF4444"));
        screenMsg.setWrapText(true);

        screenContent.getChildren().addAll(welcome, hint, accField, proceed, screenMsg);
    }

    // ── PIN ──────────────────────────────────────────────────────
    private void showPin() {
        step = Step.PIN;
        setStatus("ACTIVE");
        screenContent.getChildren().clear();

        Label lbl = new Label("ENTER PIN");
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lbl.setTextFill(Color.web(color));

        pinField = new PasswordField();
        styleInput(pinField, "PIN");

        HBox btns = new HBox(8);
        Button ok   = new Button("CONFIRM"); styleBtn(ok, color);
        Button back = new Button("CANCEL");  styleBtn(back, "#EF4444");
        ok.setOnAction(e -> {
            if (controller.validatePinWithInterrupt(currentAccId,
                    pinField.getText(), line -> {})) {
                sessionProc = controller.openSession(atmId, currentAccId);
                startSessionTimer();   // CARD_TIMEOUT timer starts here
                showMenu();
            } else {
                int fails = controller.getFailedAttempts(currentAccId);
                if (fails >= 3) {
                    flashMsg("Account LOCKED after 3 failed attempts!");
                } else {
                    flashMsg("Wrong PIN! " + (3 - fails) + " attempt(s) remaining.");
                }
                pinField.clear();
            }
        });
        back.setOnAction(e -> showIdle());
        btns.getChildren().addAll(ok, back);
        HBox.setHgrow(ok, Priority.ALWAYS);
        HBox.setHgrow(back, Priority.ALWAYS);

        screenMsg = new Label("");
        screenMsg.setFont(Font.font("Segoe UI", 10));
        screenMsg.setTextFill(Color.web("#EF4444"));

        screenContent.getChildren().addAll(lbl, pinField, btns, screenMsg);
    }

    // ── MENU ─────────────────────────────────────────────────────
    private void showMenu() {
        step = Step.MENU;
        screenContent.getChildren().clear();
        screenContent.setAlignment(Pos.TOP_CENTER);

        double bal = controller.getRawBalance(currentAccId);
        Label balLbl = new Label(String.format("Balance: Rs.%.2f", bal));
        balLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        balLbl.setTextFill(Color.web(color));

        Label lbl = new Label("SELECT TRANSACTION");
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        lbl.setTextFill(Color.web("#64748B"));

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);

        Button wb = txnBtn("WITHDRAW",  "#FBBF24"); wb.setMaxWidth(Double.MAX_VALUE);
        Button db = txnBtn("DEPOSIT",   "#10B981"); db.setMaxWidth(Double.MAX_VALUE);
        Button bb = txnBtn("BALANCE",   "#38BDF8"); bb.setMaxWidth(Double.MAX_VALUE);
        Button tb = txnBtn("TRANSFER",  "#818CF8"); tb.setMaxWidth(Double.MAX_VALUE);
        Button mb = txnBtn("STATEMENT", "#F59E0B"); mb.setMaxWidth(Double.MAX_VALUE);
        Button pb = txnBtn("CHG PIN",   "#64748B"); pb.setMaxWidth(Double.MAX_VALUE);

        wb.setOnAction(e -> { pendingType = "WITHDRAW"; showAmount(); });
        db.setOnAction(e -> { pendingType = "DEPOSIT";  showAmount(); });
        bb.setOnAction(e -> { pendingType = "BALANCE";  submitViaScheduler(0, null); });
        tb.setOnAction(e -> { pendingType = "TRANSFER"; showAmount(); });
        mb.setOnAction(e -> showMiniStatement());
        pb.setOnAction(e -> showChangePin());

        grid.add(wb, 0, 0); grid.add(db, 1, 0);
        grid.add(bb, 0, 1); grid.add(tb, 1, 1);
        grid.add(mb, 0, 2); grid.add(pb, 1, 2);
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(50);
        grid.getColumnConstraints().addAll(cc, new ColumnConstraints());
        grid.getColumnConstraints().get(1).setPercentWidth(50);

        Button logoutBtn = new Button("← LOGOUT"); styleBtn(logoutBtn, "#475569");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> logout());

        screenContent.getChildren().addAll(balLbl, lbl, grid, logoutBtn);
    }

    // ── AMOUNT ───────────────────────────────────────────────────
    private void showAmount() {
        step = Step.AMOUNT;
        screenContent.getChildren().clear();
        screenContent.setAlignment(Pos.TOP_CENTER);

        Label lbl = new Label(pendingType);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lbl.setTextFill(Color.web(color));

        amountField = new TextField();
        styleInput(amountField, "Amount (Rs.)");

        toAccField = new TextField();
        styleInput(toAccField, "Target Account ID");
        toAccField.setVisible(pendingType.equals("TRANSFER"));
        toAccField.setManaged(pendingType.equals("TRANSFER"));

        HBox btns = new HBox(8);
        Button ok   = new Button("NEXT"); styleBtn(ok, color);
        Button back = new Button("BACK"); styleBtn(back, "#475569");
        ok.setOnAction(e -> showConfirm());
        back.setOnAction(e -> showMenu());
        btns.getChildren().addAll(ok, back);
        HBox.setHgrow(ok, Priority.ALWAYS);
        HBox.setHgrow(back, Priority.ALWAYS);

        screenMsg = new Label("");
        screenMsg.setFont(Font.font("Segoe UI", 10));
        screenMsg.setTextFill(Color.web("#EF4444"));

        screenContent.getChildren().addAll(lbl, amountField, toAccField, btns, screenMsg);
    }

    // ── CONFIRM ──────────────────────────────────────────────────
    private void showConfirm() {
        step = Step.CONFIRM;
        screenContent.getChildren().clear();
        screenContent.setAlignment(Pos.TOP_CENTER);

        double amt = 0;
        try { if (amountField != null) amt = Double.parseDouble(amountField.getText()); }
        catch (Exception ignored) {}

        if (amt <= 0) {
            // Fire INVALID_AMOUNT interrupt — wired to real ATM event
            controller.fireInvalidAmountInterrupt(atmId, amt, line -> {});
            flashMsg("INTERRUPT: Invalid amount — must be > 0");
            showAmount();
            return;
        }

        Label lbl = new Label("CONFIRM");
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        lbl.setTextFill(Color.web(color));

        String toInfo = pendingType.equals("TRANSFER") && toAccField != null
                        ? "\nTo: " + toAccField.getText().trim().toUpperCase() : "";
        Label detail = new Label("Acc: " + currentAccId + "\nType: " + pendingType +
                                  "\nAmt: Rs." + String.format("%.2f", amt) + toInfo);
        detail.setFont(Font.font("Segoe UI", 11));
        detail.setTextFill(Color.web("#CBD5E1"));

        final double finalAmt = amt;
        final String toAcc = (pendingType.equals("TRANSFER") && toAccField != null)
                              ? toAccField.getText().trim().toUpperCase() : null;

        HBox btns = new HBox(8);
        Button ok     = new Button("SUBMIT"); styleBtn(ok, "#10B981");
        Button cancel = new Button("CANCEL"); styleBtn(cancel, "#EF4444");

        // ← KEY CHANGE: submit through scheduler, not directly
        ok.setOnAction(e -> submitViaScheduler(finalAmt, toAcc));
        cancel.setOnAction(e -> showMenu());
        btns.getChildren().addAll(ok, cancel);
        HBox.setHgrow(ok, Priority.ALWAYS);
        HBox.setHgrow(cancel, Priority.ALWAYS);

        screenContent.getChildren().addAll(lbl, detail, btns);
    }

    // ── SUBMIT VIA SCHEDULER ─────────────────────────────────────
    // Sends the transaction into the scheduler queue, shows PROCESSING,
    // then updates the ATM screen once the future resolves.
    private static final double CASH_LIMIT = 20000.0;

    private void submitViaScheduler(double amount, String toAcc) {
        // CASH_JAM interrupt — withdraw exceeds single-transaction cash limit
        if ("WITHDRAW".equals(pendingType) && amount > CASH_LIMIT) {
            controller.fireInvalidAmountInterrupt(atmId, amount, line -> {});
            // Fire CASH_JAM specifically for amount over limit
            controller.getKernel().getInterruptHandler()
                .trigger(kernel.InterruptHandler.InterruptType.CASH_JAM, line -> {});
            showResult("FAIL: Amount Rs." + String.format("%.0f", amount) +
                " exceeds single withdrawal limit of Rs." +
                String.format("%.0f", CASH_LIMIT) + " — CASH_JAM interrupt fired", false);
            return;
        }

        showProcessing(amount);

        // Use interrupt-wired withdraw (8% network failure chance)
        CompletableFuture<String> future =
            controller.submitTransactionWithInterrupt(atmId, currentAccId, pendingType, amount, toAcc);

        future.thenAccept(result ->
            Platform.runLater(() -> showResult(result, result.startsWith("OK")))
        );
    }

    // ── PROCESSING screen (shown while in scheduler queue) ───────
    private void showProcessing(double amount) {
        step = Step.PROCESSING;
        setStatus("QUEUED");
        screenContent.getChildren().clear();
        screenContent.setAlignment(Pos.CENTER);

        Label icon = new Label("⏳");
        icon.setFont(Font.font(24));

        Label msg = new Label("QUEUED IN SCHEDULER");
        msg.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        msg.setTextFill(Color.web(color));

        Label sub = new Label("Watch the Scheduler Monitor →\nTransaction will execute per algorithm.");
        sub.setFont(Font.font("Segoe UI", 9));
        sub.setTextFill(Color.web("#475569"));
        sub.setWrapText(true);
        sub.setAlignment(Pos.CENTER);

        Label type = new Label(pendingType + (amount > 0
    ? "  Rs." + String.format("%.2f", amount) : ""));
        type.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        type.setTextFill(Color.web("#F59E0B"));

        screenContent.getChildren().addAll(icon, msg, type, sub);
    }


    // ── RESULT ───────────────────────────────────────────────────
    private void showResult(String msg, boolean ok) {
        step = Step.RESULT;
        setStatus(ok ? "ACTIVE" : "ERROR");
        screenContent.getChildren().clear();
        screenContent.setAlignment(Pos.CENTER);

        Label icon = new Label(ok ? "✓" : "✗");
        icon.setFont(Font.font("Segoe UI", FontWeight.BOLD, 48));
        icon.setTextFill(Color.web(ok ? "#10B981" : "#EF4444"));

        Label txt = new Label(msg.replaceFirst("^(OK|FAIL): ?", ""));
        txt.setFont(Font.font("Segoe UI", 11));
        txt.setTextFill(Color.web(ok ? "#10B981" : "#EF4444"));
        txt.setWrapText(true);
        txt.setMaxWidth(260);
        txt.setAlignment(Pos.CENTER);

        if (ok) {
            double newBal = controller.getRawBalance(currentAccId);
            Label balLbl = new Label(String.format("Balance: Rs.%.2f", newBal));
            balLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            balLbl.setTextFill(Color.web(color));
            screenContent.getChildren().addAll(icon, txt, balLbl);
        } else {
            screenContent.getChildren().addAll(icon, txt);
        }

        Button again = new Button("NEW TRANSACTION"); styleBtn(again, color);
        again.setMaxWidth(Double.MAX_VALUE);
        again.setOnAction(e -> showMenu());
        screenContent.getChildren().add(again);
    }

    // ── MINI STATEMENT ───────────────────────────────────────────
    private void showMiniStatement() {
        screenContent.getChildren().clear();
        screenContent.setAlignment(Pos.TOP_LEFT);

        Label lbl = new Label("LAST 5 TRANSACTIONS");
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        lbl.setTextFill(Color.web(color));

        var txns = controller.getMiniStatement(currentAccId);
        VBox list = new VBox(4);
        if (txns.isEmpty()) {
            Label empty = new Label("No transactions yet.");
            empty.setFont(Font.font("Segoe UI", 10));
            empty.setTextFill(Color.web("#64748B"));
            list.getChildren().add(empty);
        } else {
            for (String t : txns) {
                Label line = new Label(t);
                line.setFont(Font.font("Segoe UI", 9));
                line.setTextFill(Color.web("#CBD5E1"));
                line.setWrapText(true);
                list.getChildren().add(line);
            }
        }

        Button back = new Button("← BACK"); styleBtn(back, "#475569");
        back.setMaxWidth(Double.MAX_VALUE);
        back.setOnAction(e -> showMenu());

        screenContent.getChildren().addAll(lbl, list, back);
    }

    // ── CHANGE PIN ───────────────────────────────────────────────
    private void showChangePin() {
        screenContent.getChildren().clear();
        screenContent.setAlignment(Pos.TOP_CENTER);

        Label lbl = new Label("CHANGE PIN");
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lbl.setTextFill(Color.web(color));

        pinChangField = new TextField();
        styleInput(pinChangField, "New 4-digit PIN");

        HBox btns = new HBox(8);
        Button ok   = new Button("CONFIRM"); styleBtn(ok, color);
        Button back = new Button("BACK");    styleBtn(back, "#475569");
        ok.setOnAction(e -> {
            String newPin = pinChangField.getText().trim();
            if (!newPin.matches("\\d{4}")) {
                flashMsg("PIN must be exactly 4 digits.");
            } else {
                controller.changePin(currentAccId, newPin);
                showResult("OK: PIN changed successfully.", true);
            }
        });
        back.setOnAction(e -> showMenu());
        btns.getChildren().addAll(ok, back);
        HBox.setHgrow(ok, Priority.ALWAYS);
        HBox.setHgrow(back, Priority.ALWAYS);

        screenMsg = new Label("");
        screenMsg.setFont(Font.font("Segoe UI", 10));
        screenMsg.setTextFill(Color.web("#EF4444"));

        screenContent.getChildren().addAll(lbl, pinChangField, btns, screenMsg);
    }

    // ── LOGOUT ───────────────────────────────────────────────────
    // ── Session idle timer — CARD_TIMEOUT if inactive 30 seconds ──
    private void startSessionTimer() {
        cancelSessionTimer();
        sessionTimer = new Timer(true);
        String accId  = currentAccId;
        String atmIdL = atmId;
        sessionTimer.schedule(new TimerTask() {
            public void run() {
                Platform.runLater(() -> {
                    if (step != Step.IDLE && step != Step.PROCESSING) {
                        // Fire CARD_TIMEOUT interrupt
                        controller.fireTimeoutInterrupt(atmIdL, accId, line -> {});
                        showResult("CARD_TIMEOUT: Session expired after " +
                            SESSION_TIMEOUT_SEC + "s inactivity — card ejected", false);
                        // Auto logout after showing result
                        new Timer(true).schedule(new TimerTask() {
                            public void run() {
                                Platform.runLater(() -> logout());
                            }
                        }, 2500);
                    }
                });
            }
        }, SESSION_TIMEOUT_SEC * 1000L);
    }

    private void cancelSessionTimer() {
        if (sessionTimer != null) { sessionTimer.cancel(); sessionTimer = null; }
    }

    private void logout() {
        cancelSessionTimer();
        if (sessionProc != null) {
            controller.closeSession(currentAccId, sessionProc);
            sessionProc = null;
        }
        currentAccId = "";
        showIdle();
    }

    // ── Helpers ──────────────────────────────────────────────────
    private void flashMsg(String msg) {
        if (screenMsg != null) screenMsg.setText(msg);
    }

    private void setStatus(String s) {
        statusLabel.setText(s);
        boolean isActive = !s.equals("STANDBY");
        String c = isActive ? color : "#475569";
        statusLabel.setStyle(String.format(
            "-fx-background-color: %s1a; -fx-border-color: %s44;" +
            "-fx-border-radius: 6; -fx-background-radius: 6;", c, c));
        statusLabel.setTextFill(Color.web(c));
    }

    private void styleInput(TextField tf, String prompt) {
        tf.setPromptText(prompt);
        tf.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155; -fx-border-radius: 8;" +
                    "-fx-background-radius: 8; -fx-text-fill: #F1F5F9; -fx-font-family: 'Segoe UI';" +
                    "-fx-font-size: 12;");
        tf.setMaxWidth(Double.MAX_VALUE);
    }

    private void styleInput(PasswordField tf, String prompt) {
        tf.setPromptText(prompt);
        tf.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155; -fx-border-radius: 8;" +
                    "-fx-background-radius: 8; -fx-text-fill: #F1F5F9; -fx-font-family: 'Segoe UI';" +
                    "-fx-font-size: 12;");
        tf.setMaxWidth(Double.MAX_VALUE);
    }

    private void styleBtn(Button b, String clr) {
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        b.setTextFill(Color.web(clr));
        b.setStyle(String.format(
            "-fx-background-color: %s1a; -fx-border-color: %s55;" +
            "-fx-border-radius: 10; -fx-background-radius: 10; -fx-cursor: hand;", clr, clr));
    }

    private Button txnBtn(String text, String clr) {
        Button b = new Button(text);
        styleBtn(b, clr);
        return b;
    }
}
