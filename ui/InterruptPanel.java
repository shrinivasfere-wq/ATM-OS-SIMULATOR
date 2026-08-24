package ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import kernel.InterruptHandler;
import kernel.Kernel;

import java.util.Timer;
import java.util.TimerTask;

public class InterruptPanel extends VBox {

    private final Kernel           kernel   = Kernel.getInstance();
    private final TextArea         isrLog   = new TextArea();

    // ── Live stats labels ──────────────────────────────────────────
    private Label totalLabel, networkLabel, wrongPinLabel, timeoutLabel;
    private Label cashJamLabel, powerLabel, invalidLabel;

    // ── Manual trigger count ───────────────────────────────────────
    private int manualTriggered = 0;
    private Label lastTypeLabel;

    public InterruptPanel() { build(); }

    private void build() {
        setSpacing(12);
        setPadding(new Insets(14));
        setStyle("-fx-background-color: #0F172A;");

        // ── Title ──────────────────────────────────────────────────
        Label title = new Label("⚑  INTERRUPT HANDLER — Wired to Real ATM Events");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        title.setTextFill(Color.web("#EF4444"));

        // ── How it works explanation ───────────────────────────────
        Label howTitle = sectionLabel("HOW INTERRUPTS WORK IN THIS ATM PROJECT", "#F59E0B");

        HBox howRow = new HBox(12,
            howCard("🔑 WRONG_PIN",
                "Fires automatically when\nyou enter a wrong PIN in\nthe ATM terminal.\nAfter 3 fails → account locked.",
                "#EF4444"),
            howCard("⏱ CARD_TIMEOUT",
                "AUTO: Fires after 120s of\nidle on ATM menu.\nCard ejected, PCB terminated,\nmemory page freed.",
                "#FB7185"),
            howCard("📡 NETWORK_FAILURE",
                "8% random chance during\nany transaction. Transaction\nis aborted + WAL ROLLBACK\nissued automatically.",
                "#F59E0B"),
            howCard("💸 CASH_JAM",
                "AUTO: Fires when withdraw\namount > Rs.20,000.\nCash dispenser limit\nexceeded — jammed.",
                "#FBBF24"),
            howCard("⚡ POWER_FAILURE",
                "AUTO: Fires every 10th\nauto-sim transaction.\nWAL flushed, all txns\nrolled back.",
                "#F97316"),
            howCard("💰 INVALID_AMOUNT",
                "AUTO: Fires when user\nenters 0 or negative\namount in ATM panel.\nTrap raised → menu.",
                "#38BDF8")
        );
        howRow.getChildren().forEach(n -> HBox.setHgrow((javafx.scene.Node) n, Priority.ALWAYS));

        // ── Live stats from real ATM events ───────────────────────
        VBox statsCard = new VBox(8);
        statsCard.setPadding(new Insets(10));
        statsCard.setStyle("-fx-background-color: #1E293B; -fx-border-color: #EF444433;" +
                           "-fx-border-radius: 8; -fx-background-radius: 8;");

        Label statsTitle = sectionLabel(
            "LIVE INTERRUPT COUNTERS — Auto-updated from ATM transactions", "#EF4444");

        totalLabel    = bigStatLabel("0", "#EF4444");
        networkLabel  = bigStatLabel("0", "#F59E0B");
        wrongPinLabel = bigStatLabel("0", "#FB7185");
        timeoutLabel  = bigStatLabel("0", "#F97316");
        cashJamLabel  = bigStatLabel("0", "#FBBF24");
        powerLabel    = bigStatLabel("0", "#818CF8");
        invalidLabel  = bigStatLabel("0", "#38BDF8");

        HBox statsRow1 = new HBox(10,
            statBox("TOTAL INTERRUPTS",    totalLabel,    "#EF4444"),
            statBox("NETWORK FAILURES",    networkLabel,  "#F59E0B"),
            statBox("WRONG PIN TRAPS",     wrongPinLabel, "#FB7185"),
            statBox("SESSION TIMEOUTS",    timeoutLabel,  "#F97316")
        );
        statsRow1.getChildren().forEach(n -> HBox.setHgrow((javafx.scene.Node) n, Priority.ALWAYS));

        HBox statsRow2 = new HBox(10,
            statBox("CASH JAMS",           cashJamLabel,  "#FBBF24"),
            statBox("POWER FAILURES",      powerLabel,    "#818CF8"),
            statBox("INVALID AMOUNTS",     invalidLabel,  "#38BDF8")
        );
        statsRow2.getChildren().forEach(n -> HBox.setHgrow((javafx.scene.Node) n, Priority.ALWAYS));
        VBox statsRow = new VBox(6, statsRow1, statsRow2);

        Label statsHint = new Label(
            "💡 Auto-fires: WRONG_PIN (every bad PIN entry) | NETWORK_FAILURE (8% per transaction) | " +
            "CARD_TIMEOUT (120s session idle) | CASH_JAM (withdraw > Rs.20,000) | " +
            "POWER_FAILURE (every 10 auto-sim transactions) | INVALID_AMOUNT (amount ≤ 0 input)");
        statsHint.setFont(Font.font("Segoe UI", 10));
        statsHint.setTextFill(Color.web("#475569"));
        statsHint.setWrapText(true);

        statsCard.getChildren().addAll(statsTitle, statsRow, statsHint);

        // ── Manual trigger panel ───────────────────────────────────
        VBox triggerCard = new VBox(10);
        triggerCard.setPadding(new Insets(12));
        triggerCard.setStyle("-fx-background-color: #1E293B; -fx-border-color: #334155;" +
                             "-fx-border-radius: 14; -fx-background-radius: 14;" +
                             "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.25),10,0,0,3);");

        Label trigTitle = sectionLabel(
            "MANUAL ISR TRIGGER — Simulate any interrupt for demonstration", "#64748B");

        // Last triggered label
        lastTypeLabel = new Label("None triggered manually yet");
        lastTypeLabel.setFont(Font.font("Segoe UI", 11));
        lastTypeLabel.setTextFill(Color.web("#475569"));

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);

        InterruptHandler.InterruptType[] types = InterruptHandler.InterruptType.values();
        String[] colors = {"#FB7185","#EF4444","#F59E0B","#FBBF24","#F97316","#38BDF8"};
        String[] icons  = {"⏱","🔑","📡","💸","⚡","💰"};
        String[] labels = {
            "CARD_TIMEOUT\n(hardware timer ISR)",
            "WRONG_PIN\n(software trap)",
            "NETWORK_FAILURE\n(I/O interrupt)",
            "CASH_JAM\n(hardware fault)",
            "POWER_FAILURE\n(critical ISR)",
            "INVALID_AMOUNT\n(software trap)"
        };

        for (int i = 0; i < types.length; i++) {
            final InterruptHandler.InterruptType t = types[i];
            final String clr  = colors[i];
            final String icon = icons[i];
            Button b = interruptBtn(icon + "  " + t.name(), clr);
            b.setOnAction(e -> fireManualInterrupt(t, clr));
            Label lbl = new Label(labels[i]);
            lbl.setFont(Font.font("Segoe UI", 9));
            lbl.setTextFill(Color.web("#475569"));
            lbl.setWrapText(true);
            VBox cell = new VBox(4, b, lbl);
            cell.setAlignment(Pos.TOP_CENTER);
            b.setMaxWidth(Double.MAX_VALUE);
            grid.add(cell, i % 3, i / 3);
        }
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(33.33);
        grid.getColumnConstraints().addAll(cc, cc, cc);

        Button randomBtn = new Button("⚡  RANDOM INTERRUPT");
        randomBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        randomBtn.setTextFill(Color.web("#818CF8"));
        randomBtn.setMaxWidth(Double.MAX_VALUE);
        randomBtn.setStyle("-fx-background-color: #818CF822; -fx-border-color: #818CF8;" +
                           "-fx-border-radius: 10; -fx-background-radius: 10; -fx-cursor: hand;");
        randomBtn.setOnAction(e -> {
            InterruptHandler.InterruptType[] t = InterruptHandler.InterruptType.values();
            fireManualInterrupt(t[(int)(Math.random() * t.length)], "#818CF8");
        });

        triggerCard.getChildren().addAll(trigTitle, lastTypeLabel, grid, randomBtn);

        // ── ISR log ────────────────────────────────────────────────
        VBox logCard = new VBox(6);
        logCard.setStyle("-fx-background-color: #1E293B; -fx-border-color: #EF444433;" +
                         "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10;");

        HBox logHeader = new HBox(10);
        logHeader.setAlignment(Pos.CENTER_LEFT);
        Label logTitle = sectionLabel("ISR EXECUTION LOG — Real + Manual interrupts combined", "#EF4444");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button clearBtn = new Button("CLEAR LOG");
        clearBtn.setFont(Font.font("Segoe UI", 10));
        clearBtn.setTextFill(Color.web("#64748B"));
        clearBtn.setStyle("-fx-background-color: #334155; -fx-border-color: #334155;" +
                          "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> isrLog.clear());
        logHeader.getChildren().addAll(logTitle, spacer, clearBtn);

        isrLog.setEditable(false);
        isrLog.setPrefHeight(260);
        isrLog.setWrapText(true);
        isrLog.setStyle("-fx-control-inner-background: #0F172A; -fx-text-fill: #FB7185;" +
                        "-fx-font-family: 'Segoe UI'; -fx-font-size: 11;");
        isrLog.setText(
            "Interrupt log will show entries here from:\n" +
            "  1. Wrong PIN entries in ATM terminals\n" +
            "  2. Network failures during transactions (random 8%)\n" +
            "  3. Manual triggers above\n" +
            "  4. Auto-sim transactions\n\n" +
            "Start by doing a transaction in the ATM + SCHEDULER tab,\n" +
            "or click any button above to manually fire an ISR.\n");

        logCard.getChildren().addAll(logHeader, isrLog);

        getChildren().addAll(title, howRow, statsCard, triggerCard, logCard);

        // ── Start live stats refresh ───────────────────────────────
        startStatsRefresh();
    }

    // ── Fire a manual interrupt ────────────────────────────────────
    private void fireManualInterrupt(InterruptHandler.InterruptType type, String clr) {
        manualTriggered++;
        lastTypeLabel.setText("Last: " + type.name() + " (manual trigger #" + manualTriggered + ")");
        lastTypeLabel.setTextFill(Color.web(clr));

        isrLog.appendText("═".repeat(55) + "\n");

        new Thread(() ->
            kernel.getInterruptHandler().trigger(type,
                line -> Platform.runLater(() -> isrLog.appendText(line + "\n"))
            )
        , "ISR-manual-" + type.name()).start();
    }

    // ── Refresh live stats every 500ms ────────────────────────────
    private void startStatsRefresh() {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                Platform.runLater(() -> {
                    InterruptHandler ih = kernel.getInterruptHandler();
                    if (ih == null) return;

                    // ── Update counters ──────────────────────────
                    totalLabel.setText(String.valueOf(ih.getTotalInterrupts()));
                    networkLabel.setText(String.valueOf(ih.getNetworkFailures()));
                    wrongPinLabel.setText(String.valueOf(ih.getWrongPinInterrupts()));
                    timeoutLabel.setText(String.valueOf(ih.getTimeoutInterrupts()));
                    if (cashJamLabel  != null) cashJamLabel.setText(String.valueOf(ih.getCashJamCount()));
                    if (powerLabel    != null) powerLabel.setText(String.valueOf(ih.getPowerFailureCount()));
                    if (invalidLabel  != null) invalidLabel.setText(String.valueOf(ih.getInvalidAmountCount()));

                    // ── Poll new ISR log lines from real ATM events ──
                    // This is what makes wrong PIN entries appear automatically
                    java.util.List<String> newLines = ih.pollNewLogLines();
                    if (!newLines.isEmpty()) {
                        for (String line : newLines) {
                            isrLog.appendText(line + "\n");
                        }
                        isrLog.setScrollTop(Double.MAX_VALUE);
                    }
                });
            }
        }, 500, 500);
    }

    // ── Style helpers ──────────────────────────────────────────────
    private VBox howCard(String title, String body, String color) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));
        box.setStyle(String.format(
            "-fx-background-color: #1E293B; -fx-border-color: %s;" +
            "-fx-border-width: 0 0 3 0; -fx-border-radius: 12; -fx-background-radius: 12;" +
            "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.2),8,0,0,2);", color));
        Label t = new Label(title);
        t.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        t.setTextFill(Color.web(color));
        Label b = new Label(body);
        b.setFont(Font.font("Segoe UI", 9));
        b.setTextFill(Color.web("#CBD5E1"));
        b.setWrapText(true);
        box.getChildren().addAll(t, b);
        return box;
    }

    private VBox statBox(String label, Label val, String clr) {
        VBox b = new VBox(4);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setPadding(new Insets(10, 14, 10, 14));
        b.setStyle(String.format(
            "-fx-background-color: #1E293B; -fx-border-color: %s;" +
            "-fx-border-width: 0 0 3 0; -fx-border-radius: 12; -fx-background-radius: 12;" +
            "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.2),8,0,0,2);", clr));
        Label lbl = new Label(label);
        lbl.setFont(Font.font("Segoe UI", 9));
        lbl.setTextFill(Color.web("#475569"));
        b.getChildren().addAll(lbl, val);
        HBox.setHgrow(b, Priority.ALWAYS);
        return b;
    }

    private Label bigStatLabel(String text, String color) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 30));
        l.setTextFill(Color.web(color));
        return l;
    }

    private Label sectionLabel(String text, String color) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web(color));
        return l;
    }

    private Button interruptBtn(String text, String clr) {
        Button b = new Button(text);
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        b.setTextFill(Color.web(clr));
        b.setPadding(new javafx.geometry.Insets(10, 14, 10, 14));
        b.setStyle(String.format(
            "-fx-background-color: %s22; -fx-border-color: %s;" +
            "-fx-border-radius: 12; -fx-background-radius: 12; -fx-cursor: hand;",
            clr, clr));
        return b;
    }
}
