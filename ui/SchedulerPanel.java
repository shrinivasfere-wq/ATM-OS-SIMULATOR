package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import scheduler.CPUScheduler;
import scheduler.GanttEntry;
import scheduler.SchedulerProcess;

import java.util.*;

public class SchedulerPanel extends VBox {

    private final List<SchedulerProcess> processes = new ArrayList<>();

    // Input fields — ATM-themed labels
    private TextField nameField, arrivalField, burstField, priorityField, quantumField;
    private ComboBox<String> algoBox;

    // Display areas
    private final Canvas   ganttCanvas     = new Canvas(860, 70);
    private final TableView<SchedulerProcess> metricsTable = new TableView<>();
    private final Label    avgWaitLabel    = new Label();
    private final Label    avgTatLabel     = new Label();
    private final Label    ctxLabel        = new Label();
    private final TextArea comparisonArea  = new TextArea();
    private final ListView<String> processList = new ListView<>();

    // ATM transaction colors
    private static final String[] COLORS = {
        "#38BDF8","#F59E0B","#10B981","#818CF8","#FBBF24","#FB7185"
    };

    // ── ATM Transaction types with their burst times (mirrors SchedulerEngine) ──
    // BALANCE=1, DEPOSIT=3, WITHDRAW=4, TRANSFER=6
    private static final String[][] ATM_PRESETS = {
        // {name, arrival, burst, priority}  — mirrors real burst times
        {"ATM01-BALANCE",  "0", "1", "4"},
        {"ATM02-DEPOSIT",  "1", "3", "3"},
        {"ATM03-WITHDRAW", "2", "4", "2"},
        {"ATM01-TRANSFER", "3", "6", "1"},
        {"ATM02-BALANCE",  "4", "1", "4"},
        {"ATM03-DEPOSIT",  "5", "3", "3"},
    };

    public SchedulerPanel() { build(); }

    @SuppressWarnings("unchecked")
    private void build() {
        setSpacing(10);
        setPadding(new Insets(14));
        setStyle("-fx-background-color: #0F172A;");

        // ── Title ──────────────────────────────────────────────────
        Label title = new Label("ATM TRANSACTION SCHEDULER — FCFS / Round Robin / Priority");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        title.setTextFill(Color.web("#F59E0B"));

        Label subtitle = new Label(
            "Each ATM transaction is a PROCESS. Burst time = processing ticks needed. " +
            "TRANSFER(6) > WITHDRAW(4) > DEPOSIT(3) > BALANCE(1). " +
            "Priority: TRANSFER=1 (highest) → BALANCE=4 (lowest).");
        subtitle.setFont(Font.font("Segoe UI", 10));
        subtitle.setTextFill(Color.web("#64748B"));
        subtitle.setWrapText(true);

        // ── Preset buttons — ATM scenarios ─────────────────────────
        Label presetLbl = new Label("LOAD ATM SCENARIO:");
        presetLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        presetLbl.setTextFill(Color.web("#475569"));

        Button preset1 = atmPresetBtn("MIXED TRANSACTIONS", "#38BDF8");
        Button preset2 = atmPresetBtn("ALL TRANSFERS (HIGH LOAD)", "#F59E0B");
        Button preset3 = atmPresetBtn("3 ATMs SIMULTANEOUS", "#10B981");
        Button clearPBtn = atmPresetBtn("CLEAR", "#EF4444");

        preset1.setOnAction(e -> loadMixedPreset());
        preset2.setOnAction(e -> loadTransferPreset());
        preset3.setOnAction(e -> loadSimultaneousPreset());
        clearPBtn.setOnAction(e -> { processes.clear(); processList.getItems().clear(); });

        HBox presetRow = new HBox(8, presetLbl, preset1, preset2, preset3, clearPBtn);
        presetRow.setAlignment(Pos.CENTER_LEFT);
        presetRow.setPadding(new Insets(6, 10, 6, 10));
        presetRow.setStyle("-fx-background-color: #1E293B; -fx-border-color: #334155;" +
                           "-fx-border-radius: 6; -fx-background-radius: 6;");

        // ── Manual input row ───────────────────────────────────────
        HBox inputRow = new HBox(8);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.setStyle("-fx-background-color: #1E293B; -fx-border-color: #334155;" +
                          "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10;");

        // ATM-themed field labels
        Label nameLbl     = dimLabel("Transaction:");
        Label arrivalLbl  = dimLabel("Arrival tick:");
        Label burstLbl    = dimLabel("Burst (ticks):");
        Label priorityLbl = dimLabel("Priority:");

        nameField     = inputField("e.g. ATM01-WITHDRAW");
        arrivalField  = inputField("0");
        burstField    = inputField("4");
        priorityField = inputField("2");
        nameField.setPrefWidth(160);

        Button addBtn = actionBtn("+ ADD TXN", "#10B981");
        addBtn.setOnAction(e -> addProcess());

        Label algoLbl = dimLabel("Schedule by:");
        algoBox = new ComboBox<>();
        algoBox.getItems().addAll(
            "FCFS", "Round Robin", "Priority (Non-Preemptive)", "Priority (Preemptive)", "Compare ALL");
        algoBox.setValue("Round Robin");
        algoBox.setStyle("-fx-background-color: #1E293B; -fx-border-color: #334155;" +
                         "-fx-text-fill: #38BDF8; -fx-font-family: 'Segoe UI'; -fx-font-size: 11;");

        Label qLbl = dimLabel("Quantum:");
        quantumField = inputField("2");
        quantumField.setMaxWidth(50);

        Button runBtn = actionBtn("▶ RUN", "#38BDF8");
        runBtn.setOnAction(e -> runAlgorithm());

        inputRow.getChildren().addAll(
            nameLbl, nameField,
            arrivalLbl, arrivalField,
            burstLbl, burstField,
            priorityLbl, priorityField,
            addBtn,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            algoLbl, algoBox, qLbl, quantumField, runBtn);

        // ── Transaction queue list ─────────────────────────────────
        Label queueLbl = dimLabel("TRANSACTION QUEUE:");
        processList.setPrefHeight(90);
        processList.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155;" +
                             "-fx-border-radius:10; -fx-background-radius:10;" +
                             "-fx-font-family: 'Segoe UI'; -fx-font-size: 11; -fx-text-fill: #CBD5E1;");

        // ── Burst time reference card ──────────────────────────────
        HBox burstRef = new HBox(20);
        burstRef.setPadding(new Insets(6, 12, 6, 12));
        burstRef.setStyle("-fx-background-color: #1E293B; -fx-border-color: #334155;" +
                          "-fx-border-radius: 6; -fx-background-radius: 6;");
        burstRef.setAlignment(Pos.CENTER_LEFT);
        burstRef.getChildren().addAll(
            dimLabel("BURST REFERENCE:"),
            colorBadge("BALANCE = 1 tick",  "#38BDF8"),
            colorBadge("DEPOSIT = 3 ticks", "#10B981"),
            colorBadge("WITHDRAW = 4 ticks","#F59E0B"),
            colorBadge("TRANSFER = 6 ticks","#818CF8")
        );

        // ── Gantt canvas ───────────────────────────────────────────
        VBox ganttBox = card();
        Label gLbl = sectionLabel("GANTT CHART — ATM Transaction Scheduling", "#F59E0B");
        ganttBox.getChildren().addAll(gLbl, ganttCanvas);

        // ── Metrics ────────────────────────────────────────────────
        HBox metricsRow = new HBox(12);
        avgWaitLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        avgWaitLabel.setTextFill(Color.web("#FBBF24"));
        avgTatLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        avgTatLabel.setTextFill(Color.web("#38BDF8"));
        ctxLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        ctxLabel.setTextFill(Color.web("#10B981"));
        metricsRow.getChildren().addAll(
            statBox("AVG WAIT TIME (ticks)", avgWaitLabel, "#FBBF24"),
            statBox("AVG TURNAROUND (ticks)", avgTatLabel, "#38BDF8"),
            statBox("CONTEXT SWITCHES",       ctxLabel,    "#10B981"));

        // ── Per-transaction metrics table ──────────────────────────
        buildMetricsTable();

        // ── Comparison ────────────────────────────────────────────
        VBox compBox = card();
        compBox.getChildren().add(sectionLabel("ALGORITHM COMPARISON — Which is best for ATM?", "#818CF8"));
        comparisonArea.setPrefHeight(130);
        comparisonArea.setEditable(false);
        comparisonArea.setStyle("-fx-control-inner-background:#0F172A; -fx-text-fill:#CBD5E1;" +
                                "-fx-font-family:'Segoe UI'; -fx-font-size:11;");
        compBox.getChildren().add(comparisonArea);

        getChildren().addAll(title, subtitle, presetRow, inputRow,
                             queueLbl, processList, burstRef,
                             ganttBox, metricsRow, metricsTable, compBox);
    }

    // ── ATM Preset Loaders ────────────────────────────────────────
    /** Mixed real ATM transaction types — typical busy ATM scenario */
    private void loadMixedPreset() {
        processes.clear(); processList.getItems().clear();
        String[][] txns = {
            {"ATM01-WITHDRAW", "0", "4", "2"},
            {"ATM02-DEPOSIT",  "1", "3", "3"},
            {"ATM03-TRANSFER", "2", "6", "1"},
            {"ATM01-BALANCE",  "3", "1", "4"},
            {"ATM02-WITHDRAW", "4", "4", "2"},
            {"ATM03-DEPOSIT",  "5", "3", "3"},
        };
        addPresetRows(txns);
    }

    /** All TRANSFER transactions — highest burst, tests preemption clearly */
    private void loadTransferPreset() {
        processes.clear(); processList.getItems().clear();
        String[][] txns = {
            {"ATM01-TRANSFER", "0", "6", "1"},
            {"ATM02-TRANSFER", "1", "6", "1"},
            {"ATM03-TRANSFER", "2", "6", "1"},
            {"ATM01-BALANCE",  "3", "1", "4"},
            {"ATM02-DEPOSIT",  "4", "3", "3"},
        };
        addPresetRows(txns);
    }

    /** Three ATMs submitting simultaneously — tests queue & waiting */
    private void loadSimultaneousPreset() {
        processes.clear(); processList.getItems().clear();
        String[][] txns = {
            {"ATM01-TRANSFER", "0", "6", "1"},
            {"ATM02-WITHDRAW", "0", "4", "2"},
            {"ATM03-DEPOSIT",  "0", "3", "3"},
            {"ATM01-BALANCE",  "0", "1", "4"},
            {"ATM02-TRANSFER", "2", "6", "1"},
            {"ATM03-WITHDRAW", "3", "4", "2"},
        };
        addPresetRows(txns);
    }

    private void addPresetRows(String[][] txns) {
        for (String[] t : txns) {
            String name = t[0];
            int arrival  = Integer.parseInt(t[1]);
            int burst    = Integer.parseInt(t[2]);
            int priority = Integer.parseInt(t[3]);
            processes.add(new SchedulerProcess(name, arrival, burst, priority));
            processList.getItems().add(
                String.format("%-22s  Arrival=%-3d  Burst=%-3d ticks  Priority=%d",
                    name, arrival, burst, priority));
        }
    }

    // ── Manual add ────────────────────────────────────────────────
    private void addProcess() {
        try {
            String name = nameField.getText().trim();
            if (name.isEmpty()) name = "ATM0" + (processes.size() % 3 + 1)
                                     + "-TXN" + (processes.size() + 1);
            int arrival  = parseInt(arrivalField.getText(), 0);
            int burst    = parseInt(burstField.getText(), 4);
            int priority = parseInt(priorityField.getText(), 2);
            processes.add(new SchedulerProcess(name, arrival, burst, priority));
            processList.getItems().add(
                String.format("%-22s  Arrival=%-3d  Burst=%-3d ticks  Priority=%d",
                    name, arrival, burst, priority));
            nameField.clear(); arrivalField.clear(); burstField.clear(); priorityField.clear();
        } catch (Exception ex) {
            processList.getItems().add("ERROR: " + ex.getMessage());
        }
    }

    // ── Run algorithm ─────────────────────────────────────────────
    private void runAlgorithm() {
        if (processes.isEmpty()) {
            comparisonArea.setText("No transactions added. Load a preset or add manually.");
            return;
        }
        String algo   = algoBox.getValue();
        int    quantum = parseInt(quantumField.getText(), 2);

        if (algo.equals("Compare ALL")) { runComparison(quantum); return; }

        List<SchedulerProcess> copy = deepCopy(processes);
        List<GanttEntry> gantt = switch (algo) {
            case "FCFS"                      -> CPUScheduler.fcfs(copy);
            case "Round Robin"               -> CPUScheduler.roundRobin(copy, quantum);
            case "Priority (Non-Preemptive)" -> CPUScheduler.priority(copy);
            case "Priority (Preemptive)"     -> CPUScheduler.preemptivePriority(copy);
            default                          -> new ArrayList<>();
        };

        drawGantt(gantt);

        double avgWT  = copy.stream().mapToInt(SchedulerProcess::getWaitingTime).average().orElse(0);
        double avgTAT = copy.stream().mapToInt(SchedulerProcess::getTurnaroundTime).average().orElse(0);
        int    ctxSw  = countContextSwitches(gantt);

        avgWaitLabel.setText(String.format("%.2f", avgWT));
        avgTatLabel.setText(String.format("%.2f",  avgTAT));
        ctxLabel.setText(String.valueOf(ctxSw));
        metricsTable.getItems().setAll(copy);

        comparisonArea.setText("Algorithm: " + algo + "\n" +
            "→ Avg Wait: " + String.format("%.2f", avgWT) + " ticks  " +
            "Avg TAT: " + String.format("%.2f", avgTAT) + " ticks  " +
            "Context Switches: " + ctxSw + "\n\n" +
            getAlgoExplanation(algo));
    }

    private String getAlgoExplanation(String algo) {
        return switch (algo) {
            case "FCFS" ->
                "FCFS: Transactions served in arrival order.\n" +
                "A long TRANSFER blocks shorter BALANCE/DEPOSIT behind it.\n" +
                "Simple but can cause high wait times (convoy effect).";
            case "Round Robin" ->
                "Round Robin: Each transaction gets a fixed time quantum.\n" +
                "Prevents any single transaction from hogging the CPU.\n" +
                "Good fairness — all 3 ATMs get CPU time regularly.";
            case "Priority (Non-Preemptive)" ->
                "Priority: TRANSFER(1) > WITHDRAW(2) > DEPOSIT(3) > BALANCE(4).\n" +
                "High-value transactions execute first.\n" +
                "Non-preemptive: current transaction finishes before switching.";
            case "Priority (Preemptive)" ->
                "Preemptive Priority: Higher priority transaction interrupts current.\n" +
                "TRANSFER always jumps queue immediately when it arrives.\n" +
                "Best response for urgent transactions, but more context switches.";
            default -> "";
        };
    }

    // ── Compare ALL ───────────────────────────────────────────────
    private void runComparison(int quantum) {
        String[] algos  = {"FCFS","RR","PRIORITY","PREEMPTIVE"};
        String[] labels = {
            "FCFS",
            "Round Robin (Q=" + quantum + ")",
            "Priority (Non-Preemptive)",
            "Priority (Preemptive)"
        };
        StringBuilder sb = new StringBuilder();
        sb.append("ATM TRANSACTION SCHEDULING — ALGORITHM COMPARISON\n");
        sb.append("=".repeat(62)).append("\n");
        sb.append(String.format("%-32s  %-10s  %-10s  %-8s%n",
            "ALGORITHM", "Avg Wait", "Avg TAT", "Ctx Sw"));
        sb.append("-".repeat(62)).append("\n");

        int bestIdx = 0; double bestWT = Double.MAX_VALUE;
        for (int i = 0; i < algos.length; i++) {
            List<SchedulerProcess> copy = deepCopy(processes);
            List<GanttEntry> gantt = switch (algos[i]) {
                case "FCFS"       -> CPUScheduler.fcfs(copy);
                case "RR"         -> CPUScheduler.roundRobin(copy, quantum);
                case "PRIORITY"   -> CPUScheduler.priority(copy);
                case "PREEMPTIVE" -> CPUScheduler.preemptivePriority(copy);
                default           -> new ArrayList<>();
            };
            double avgWT  = copy.stream().mapToInt(SchedulerProcess::getWaitingTime).average().orElse(0);
            double avgTAT = copy.stream().mapToInt(SchedulerProcess::getTurnaroundTime).average().orElse(0);
            int    ctxSw  = countContextSwitches(gantt);
            sb.append(String.format("%-32s  %-10.2f  %-10.2f  %-8d%n",
                labels[i], avgWT, avgTAT, ctxSw));
            if (avgWT < bestWT) { bestWT = avgWT; bestIdx = i; }
        }
        sb.append("\n✔ BEST for ATM (lowest avg wait): ").append(labels[bestIdx]);
        sb.append("\n\nFor an ATM system, Priority Scheduling is ideal —\n");
        sb.append("high-value TRANSFER transactions are served first,\n");
        sb.append("while BALANCE queries (low-risk) wait briefly.");
        comparisonArea.setText(sb.toString());

        // Draw Gantt for best algorithm
        List<SchedulerProcess> bestCopy = deepCopy(processes);
        List<GanttEntry> bestGantt = switch (algos[bestIdx]) {
            case "FCFS"       -> CPUScheduler.fcfs(bestCopy);
            case "RR"         -> CPUScheduler.roundRobin(bestCopy, quantum);
            case "PRIORITY"   -> CPUScheduler.priority(bestCopy);
            case "PREEMPTIVE" -> CPUScheduler.preemptivePriority(bestCopy);
            default           -> new ArrayList<>();
        };
        drawGantt(bestGantt);
        metricsTable.getItems().setAll(bestCopy);
    }

    // ── Gantt drawing ─────────────────────────────────────────────
    private void drawGantt(List<GanttEntry> gantt) {
        GraphicsContext gc = ganttCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, ganttCanvas.getWidth(), ganttCanvas.getHeight());
        gc.setFill(Color.web("#0F172A"));
        gc.fillRect(0, 0, ganttCanvas.getWidth(), ganttCanvas.getHeight());
        if (gantt.isEmpty()) return;

        int totalTime = gantt.get(gantt.size() - 1).getEndTime();
        if (totalTime == 0) return;
        double scale = (ganttCanvas.getWidth() - 40) / totalTime;
        double blockH = 40, y = 10;

        // Assign colors based on ATM / transaction type
        Map<String, String> colorMap = new HashMap<>();
        int colorIdx = 0;
        for (GanttEntry e : gantt) {
            if (!e.getProcessName().equals("IDLE") && !colorMap.containsKey(e.getProcessName())) {
                // Color by ATM terminal
                String col;
                String name = e.getProcessName();
                if      (name.startsWith("ATM01")) col = "#38BDF8";
                else if (name.startsWith("ATM02")) col = "#F59E0B";
                else if (name.startsWith("ATM03")) col = "#10B981";
                else    col = COLORS[colorIdx++ % COLORS.length];
                colorMap.put(name, col);
            }
        }

        for (GanttEntry e : gantt) {
            double x = 20 + e.getStartTime() * scale;
            double w = e.getDuration() * scale;
            boolean idle = e.getProcessName().equals("IDLE");
            String clr = idle ? "#334155" : colorMap.getOrDefault(e.getProcessName(), "#38BDF8");

            gc.setFill(Color.web(clr + (idle ? "" : "55")));
            gc.fillRoundRect(x, y, w - 1, blockH, 4, 4);
            gc.setStroke(Color.web(clr));
            gc.setLineWidth(0.5);
            gc.strokeRoundRect(x, y, w - 1, blockH, 4, 4);

            if (w > 20) {
                gc.setFill(Color.web(clr));
                gc.setFont(Font.font("Segoe UI", 9));
                // Short label: just the transaction type part
                String label = e.getProcessName().contains("-")
                    ? e.getProcessName().substring(e.getProcessName().lastIndexOf('-') + 1)
                    : e.getProcessName();
                gc.fillText(label, x + 3, y + blockH / 2 + 4);
            }

            gc.setFill(Color.web("#64748B"));
            gc.setFont(Font.font("Segoe UI", 8));
            gc.fillText(String.valueOf(e.getStartTime()), x, y + blockH + 12);
        }
        GanttEntry last = gantt.get(gantt.size() - 1);
        double lx = 20 + last.getEndTime() * scale;
        gc.setFill(Color.web("#64748B"));
        gc.setFont(Font.font("Segoe UI", 8));
        gc.fillText(String.valueOf(last.getEndTime()), lx, y + blockH + 12);
    }

    // ── Metrics table ─────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private void buildMetricsTable() {
        metricsTable.setStyle("-fx-background-color:#0F172A; -fx-border-color:#334155;");
        metricsTable.setPrefHeight(160);
        metricsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        metricsTable.getColumns().addAll(
            tcol("Transaction",   p -> p.getName()),
            tcol("Arrival",       p -> String.valueOf(p.getArrivalTime())),
            tcol("Burst (ticks)", p -> String.valueOf(p.getBurstTime())),
            tcol("Finish",        p -> p.getFinishTime() == -1 ? "-" : String.valueOf(p.getFinishTime())),
            tcol("Wait (ticks)",  p -> String.valueOf(p.getWaitingTime())),
            tcol("Turnaround",    p -> p.getFinishTime() == -1 ? "-" : String.valueOf(p.getTurnaroundTime()))
        );
    }

    private TableColumn<SchedulerProcess, String> tcol(String name,
            java.util.function.Function<SchedulerProcess, String> getter) {
        TableColumn<SchedulerProcess, String> c = new TableColumn<>(name);
        c.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(getter.apply(d.getValue())));
        c.setStyle("-fx-font-family:'Segoe UI'; -fx-font-size:11;");
        return c;
    }

    // ── Helpers ───────────────────────────────────────────────────
    private List<SchedulerProcess> deepCopy(List<SchedulerProcess> src) {
        List<SchedulerProcess> copy = new ArrayList<>();
        for (SchedulerProcess p : src)
            copy.add(new SchedulerProcess(p.getName(), p.getArrivalTime(),
                                          p.getBurstTime(), p.getPriority()));
        return copy;
    }

    private int countContextSwitches(List<GanttEntry> gantt) {
        int sw = 0;
        for (int i = 1; i < gantt.size(); i++)
            if (!gantt.get(i).getProcessName().equals(gantt.get(i-1).getProcessName())) sw++;
        return sw;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private Label colorBadge(String text, String color) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web(color));
        l.setPadding(new Insets(2, 8, 2, 8));
        l.setStyle(String.format(
            "-fx-background-color: %s1a; -fx-border-color: %s55;" +
            "-fx-border-radius: 8; -fx-background-radius: 8;", color, color));
        return l;
    }

    private TextField inputField(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.setMaxWidth(120);
        f.setStyle("-fx-background-color:#0F172A; -fx-border-color:#334155; -fx-border-radius:4;" +
                   "-fx-background-radius:4; -fx-text-fill:#F1F5F9; -fx-font-family:'Segoe UI';" +
                   "-fx-font-size:11;");
        return f;
    }

    private Button actionBtn(String text, String clr) {
        Button b = new Button(text);
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        b.setTextFill(Color.web(clr));
        b.setStyle(String.format("-fx-background-color:%s22; -fx-border-color:%s;" +
                                 "-fx-border-radius:4; -fx-background-radius:4; -fx-cursor:hand;", clr, clr));
        return b;
    }

    private Button atmPresetBtn(String text, String clr) {
        Button b = new Button(text);
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        b.setTextFill(Color.web(clr));
        b.setStyle(String.format(
            "-fx-background-color:%s1a; -fx-border-color:%s55;" +
            "-fx-border-radius:4; -fx-background-radius:4; -fx-cursor:hand;", clr, clr));
        return b;
    }

    private Label dimLabel(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", 10));
        l.setTextFill(Color.web("#64748B"));
        return l;
    }

    private Label sectionLabel(String t, String c) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web(c));
        return l;
    }

    private VBox card() {
        VBox b = new VBox(8);
        b.setPadding(new Insets(10));
        b.setStyle("-fx-background-color:#1E293B; -fx-border-color:#334155;" +
                   "-fx-border-radius:8; -fx-background-radius:8;");
        return b;
    }

    private VBox statBox(String label, Label val, String clr) {
        VBox b = new VBox(4);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setPadding(new Insets(10, 16, 10, 16));
        b.setStyle(String.format("-fx-background-color:#1E293B; -fx-border-color:#334155;" +
                                 "-fx-border-radius:14; -fx-background-radius:14;" +
                                 "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.2),8,0,0,2);"));
        Label lbl = new Label(label);
        lbl.setFont(Font.font("Segoe UI", 9));
        lbl.setTextFill(Color.web("#475569"));
        val.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        val.setTextFill(Color.web(clr));
        b.getChildren().addAll(lbl, val);
        HBox.setHgrow(b, Priority.ALWAYS);
        return b;
    }
}
