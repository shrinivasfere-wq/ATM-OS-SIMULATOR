package ui;

import controller.ATMController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import kernel.Kernel;
import monitor.SystemMonitor;
import scheduler.SchedulerEngine;
import scheduler.SchedulingAlgorithm;
import scheduler.Transaction;
import sync.DeadlockDetector;
import kernel.InterruptHandler;

import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class DashboardApp extends Application {

    private final Kernel          kernel     = Kernel.getInstance();
    private final SchedulerEngine scheduler  = SchedulerEngine.getInstance();
    private final SystemMonitor   monitor    = SystemMonitor.getInstance();
    private final ATMController   controller = new ATMController();

    // Header labels
    private Label tickLabel, queueLabel, cpuLabel, memLabel, algoLabel, processLabel;

    // Scheduler panel widgets
    private Label            runningLabel;
    private ProgressBar      cpuBar;
    private ListView<String> queueListView;
    private Canvas           ganttCanvas;
    private Canvas           cpuChartCanvas;
    private TextArea         logArea;
    private Label            completedLabel, avgWaitLabel, avgTatLabel;
    private TextArea         balanceArea;

    // ── Deadlock panel widgets ────────────────────────────────────
    private Label    dlDetectedLabel, dlResolvedLabel, dlStatusLabel;
    private TextArea dlLogArea;
    private TextArea dlWaitGraphArea;
    private Button   dlSimBtn;
    private volatile boolean dlSimRunning = false;

    // ── Banker's Algorithm widgets ────────────────────────────────
    private TextArea bankersOutputArea;
    private Button   bankersRunBtn;
    // Preset scenario fields (3 processes, 3 resource types)
    private TextField[][] bankersAlloc = new TextField[3][3];
    private TextField[][] bankersMax   = new TextField[3][3];
    private TextField[]   bankersAvail = new TextField[3];

    // Other tab panels
    private MemoryPanel    memoryPanel;
    private SchedulerPanel schedulerPanel;
    private RacePanel      racePanel;
    private InterruptPanel interruptPanel;

    private boolean autoSim      = false;
    private Timer   autoTimer;
    private int     autoSimCount = 0;  // tracks transactions for POWER_FAILURE

    // ══════════════════════════════════════════════════════════════
    @Override
    public void start(Stage stage) {
        kernel.start();
        scheduler.start();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0F172A;");
        root.setTop(buildHeader());
        root.setCenter(buildTabPane());

        Scene scene = new Scene(root, 1440, 900);
        stage.setTitle("ATM-OS Simulator — Unified Dashboard");
        stage.setScene(scene);
        stage.show();
        stage.setOnCloseRequest(e -> {
            scheduler.stop();
            if (autoTimer != null) autoTimer.cancel();
            Platform.exit();
        });

        startUIRefresh();
    }

    // ══════════════════════════════════════════════════════════════
    // HEADER
    // ══════════════════════════════════════════════════════════════
    private HBox buildHeader() {
        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 20, 10, 20));
        header.setStyle("-fx-background-color: #0F172A; -fx-border-color: #38BDF822; -fx-border-width: 0 0 2 0; -fx-padding: 12 24 12 24; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 8, 0, 0, 2);");

        Label title = new Label("ATM-OS SIMULATOR");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#F1F5F9"));

        Label live = badge("● LIVE", "#10B981");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        tickLabel    = statLabel("TICK: 0");
        queueLabel   = statLabel("QUEUE: 0");
        cpuLabel     = statLabel("CPU: 0%");
        memLabel     = statLabel("MEM: 0%");
        processLabel = statLabel("PROC: 1");
        algoLabel    = statLabel("  RR  ");

        ComboBox<String> algoBox = new ComboBox<>();
        algoBox.getItems().addAll("FCFS", "Round Robin", "Priority");
        algoBox.setValue("Round Robin");
        algoBox.setStyle("-fx-background-color: #1E293B; -fx-border-color: #334155; " +
                         "-fx-text-fill: #38BDF8; -fx-font-family: 'Segoe UI'; -fx-font-size: 10;");
        algoBox.setOnAction(e -> {
            switch (algoBox.getValue()) {
                case "FCFS"        -> scheduler.setAlgorithm(SchedulingAlgorithm.FCFS);
                case "Round Robin" -> scheduler.setAlgorithm(SchedulingAlgorithm.ROUND_ROBIN);
                case "Priority"    -> scheduler.setAlgorithm(SchedulingAlgorithm.PRIORITY);
            }
            algoLabel.setText("ALGO: " + algoBox.getValue().split(" ")[0]);
        });

        Button autoBtn = new Button("▶ AUTO SIM");
        styleBtn(autoBtn, "#10B981");
        autoBtn.setOnAction(e -> {
            autoSim = !autoSim;
            autoBtn.setText(autoSim ? "⏸ STOP SIM" : "▶ AUTO SIM");
            if (autoSim) startAutoSim();
            else if (autoTimer != null) autoTimer.cancel();
        });

        header.getChildren().addAll(title, live, spacer,
            tickLabel, queueLabel, cpuLabel, memLabel, processLabel, algoLabel,
            new Separator(Orientation.VERTICAL),
            algoBox, autoBtn);
        return header;
    }

    // ══════════════════════════════════════════════════════════════
    // TAB PANE
    // ══════════════════════════════════════════════════════════════
    private TabPane buildTabPane() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color: #0F172A; -fx-tab-min-width: 170; -fx-tab-max-width: 220; -fx-font-family: 'Segoe UI'; -fx-font-size: 12;");
        tabs.getTabs().addAll(
            tab("  ATM TERMINALS",      buildLiveTab()),
            tab("  DEADLOCK & SAFETY",  buildDeadlockTab()),
            tab("  MEMORY MANAGER",     buildMemoryTab()),
            tab("  SCHEDULER SIM",      buildCpuSimTab()),
            tab("  RACE CONDITION",     buildRaceTab()),
            tab("  INTERRUPTS",         buildInterruptTab())
        );
        return tabs;
    }

    private Tab tab(String title, javafx.scene.Node content) {
        Tab t = new Tab(title);
        t.setContent(content);
        return t;
    }

    // ══════════════════════════════════════════════════════════════
    // TAB 1 — ATM + Scheduler (split view)
    // ══════════════════════════════════════════════════════════════
    private SplitPane buildLiveTab() {
        VBox leftPane = new VBox(10);
        leftPane.setPadding(new Insets(12));
        leftPane.setStyle("-fx-background-color: #0F172A;");

        ATMPanel atm1 = new ATMPanel("ATM-01", "#38BDF8", controller);
        ATMPanel atm2 = new ATMPanel("ATM-02", "#F59E0B", controller);
        ATMPanel atm3 = new ATMPanel("ATM-03", "#10B981", controller);

        HBox atmRow = new HBox(10, atm1, atm2, atm3);
        HBox.setHgrow(atm1, Priority.ALWAYS);
        HBox.setHgrow(atm2, Priority.ALWAYS);
        HBox.setHgrow(atm3, Priority.ALWAYS);
        VBox.setVgrow(atmRow, Priority.ALWAYS);
        leftPane.getChildren().addAll(atmRow, buildBalanceBar());

        ScrollPane leftScroll = new ScrollPane(leftPane);
        leftScroll.setFitToWidth(true);
        leftScroll.setStyle("-fx-background: #0F172A; -fx-background-color: #0F172A;");

        ScrollPane rightScroll = new ScrollPane(buildSchedulerPanel());
        rightScroll.setFitToWidth(true);
        rightScroll.setStyle("-fx-background: #0F172A; -fx-background-color: #0F172A;");

        SplitPane split = new SplitPane(leftScroll, rightScroll);
        split.setDividerPositions(0.55);
        split.setStyle("-fx-background-color: #0F172A;");
        return split;
    }

    // ── Scheduler Monitor panel (right side of Tab 1) ─────────────
    private VBox buildSchedulerPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12));
        panel.setStyle("-fx-background-color: #0F172A;");

        Label title = new Label("SCHEDULER MONITOR");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        title.setTextFill(Color.web("#38BDF8"));

        VBox execCard = makeCard("#38BDF8");
        execCard.getChildren().add(dimLabel("EXECUTING:"));
        runningLabel = new Label("CPU IDLE");
        runningLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        runningLabel.setTextFill(Color.web("#64748B"));
        runningLabel.setPadding(new Insets(12, 14, 12, 14));
        runningLabel.setMaxWidth(Double.MAX_VALUE);
        runningLabel.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155;" +
                              "-fx-border-radius: 10; -fx-background-radius: 10;" +
                              "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 6, 0, 0, 1);");
        cpuBar = new ProgressBar(0);
        cpuBar.setMaxWidth(Double.MAX_VALUE);
        cpuBar.setStyle("-fx-accent: #38BDF8; -fx-background-color: #334155; -fx-pref-height: 8;");
        execCard.getChildren().addAll(runningLabel, cpuBar);

        VBox queueCard = makeCard("#F59E0B");
        queueCard.getChildren().add(dimLabel("READY QUEUE:"));
        queueListView = new ListView<>();
        queueListView.setPrefHeight(90);
        queueListView.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155;" +
                               "-fx-border-radius: 10; -fx-background-radius: 10;" +
                               "-fx-font-family: 'Segoe UI'; -fx-font-size: 11;");
        queueCard.getChildren().add(queueListView);

        VBox ganttCard = makeCard("#F59E0B");
        ganttCard.getChildren().add(sectionLabel("GANTT CHART — last 50 ticks", "#F59E0B"));
        ganttCanvas = new Canvas(560, 36);
        ganttCard.getChildren().add(ganttCanvas);

        VBox cpuChartCard = makeCard("#818CF8");
        cpuChartCard.getChildren().add(sectionLabel("CPU UTILIZATION HISTORY", "#818CF8"));
        cpuChartCanvas = new Canvas(560, 70);
        cpuChartCard.getChildren().add(cpuChartCanvas);

        completedLabel = new Label("0");
        avgWaitLabel   = new Label("0T");
        avgTatLabel    = new Label("0ms");
        HBox metricsRow = new HBox(10,
            buildMetricBox("COMPLETED", completedLabel, "#10B981"),
            buildMetricBox("AVG WAIT",  avgWaitLabel,   "#FBBF24"),
            buildMetricBox("AVG TAT",   avgTatLabel,    "#38BDF8")
        );
        metricsRow.getChildren().forEach(n -> HBox.setHgrow((javafx.scene.Node) n, Priority.ALWAYS));

        VBox logCard = makeCard("#F59E0B");
        logCard.getChildren().add(sectionLabel("TRANSACTION JOURNAL (WAL)", "#F59E0B"));
        logArea = new TextArea();
        logArea.setPrefHeight(200);
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setStyle("-fx-control-inner-background: #0F172A; -fx-text-fill: #94A3B8;" +
                         "-fx-font-family: 'Segoe UI'; -fx-font-size: 11;");
        logCard.getChildren().add(logArea);

        panel.getChildren().addAll(title, execCard, queueCard,
                                   ganttCard, cpuChartCard, metricsRow, logCard);
        return panel;
    }

    // ══════════════════════════════════════════════════════════════
    // TAB 2 — DEADLOCK DEMO  (full dedicated tab)
    // ══════════════════════════════════════════════════════════════
    private ScrollPane buildDeadlockTab() {
        VBox page = new VBox(12);
        page.setPadding(new Insets(16));
        page.setStyle("-fx-background-color: #0F172A;");

        // ── Title ──────────────────────────────────────────────────
        Label title = new Label("🔒  DEADLOCK DETECTION & RESOLUTION");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        title.setTextFill(Color.web("#EF4444"));

        Label subtitle = new Label(
            "Demonstrates circular-wait between two concurrent TRANSFER transactions using real semaphores.");
        subtitle.setFont(Font.font("Segoe UI", 11));
        subtitle.setTextFill(Color.web("#64748B"));
        subtitle.setWrapText(true);

        // ── Counter row ────────────────────────────────────────────
        dlDetectedLabel = bigCountLabel("0", "#EF4444");
        dlResolvedLabel = bigCountLabel("0", "#10B981");

        HBox counterRow = new HBox(16,
            buildCounterBox("DEADLOCKS DETECTED", dlDetectedLabel, "#EF4444"),
            buildCounterBox("DEADLOCKS RESOLVED", dlResolvedLabel, "#10B981"),
            buildStatusBox()
        );
        counterRow.getChildren().forEach(n -> HBox.setHgrow((javafx.scene.Node) n, Priority.ALWAYS));

        // ── Wait-graph display ─────────────────────────────────────
        VBox wgCard = makeCard("#F97316");
        wgCard.getChildren().add(sectionLabel("LIVE WAIT-GRAPH  (Process → Resource → Holder)", "#F97316"));
        dlWaitGraphArea = new TextArea();
        dlWaitGraphArea.setPrefHeight(80);
        dlWaitGraphArea.setEditable(false);
        dlWaitGraphArea.setStyle("-fx-control-inner-background: #0F172A; -fx-text-fill: #CBD5E1;" +
                                 "-fx-font-family: 'Segoe UI'; -fx-font-size: 12;");
        dlWaitGraphArea.setText("No waits detected — system stable.");
        wgCard.getChildren().add(dlWaitGraphArea);

        // ── Control buttons ────────────────────────────────────────
        dlSimBtn = new Button("▶  RUN DEADLOCK SIMULATION");
        dlSimBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        dlSimBtn.setTextFill(Color.web("#EF4444"));
        dlSimBtn.setMaxWidth(Double.MAX_VALUE);
        dlSimBtn.setStyle(
            "-fx-background-color: #EF4444; -fx-border-color: transparent; " +
            "-fx-border-radius: 20; -fx-background-radius: 20; -fx-cursor: hand; -fx-padding: 12 24;" +
            "-fx-effect: dropshadow(gaussian,rgba(239,68,68,0.4),12,0,0,3);");
        dlSimBtn.setTextFill(javafx.scene.paint.Color.web("#FFFFFF"));

        Button clearBtn = new Button("⬛  CLEAR LOG");
        clearBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        clearBtn.setTextFill(Color.web("#475569"));
        clearBtn.setStyle("-fx-background-color: #47556922; -fx-border-color: #475569; " +
                          "-fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> {
            if (dlLogArea != null) dlLogArea.clear();
        });

        HBox btnRow = new HBox(10, dlSimBtn, clearBtn);
        HBox.setHgrow(dlSimBtn, Priority.ALWAYS);
        dlSimBtn.setOnAction(e -> runDeadlockSimulation());

        // ── Explanation cards ──────────────────────────────────────
        HBox explainRow = new HBox(12,
            buildExplainCard("🔄 CIRCULAR WAIT",
                "Process A holds R1, needs R2.\nProcess B holds R2, needs R1.\nNeither can proceed → deadlock.",
                "#F97316"),
            buildExplainCard("🔍 DETECTION",
                "DFS traversal of the wait-graph.\nIf a cycle is found, deadlock exists.\nO(V+E) time complexity.",
                "#EF4444"),
            buildExplainCard("⚡ RESOLUTION",
                "Victim selection: kill the\nlower-priority/younger process.\nRelease all its locks.",
                "#10B981"),
            buildExplainCard("🛡 PREVENTION",
                "Lock ordering: always acquire\nresources in alphabetical order.\nPrevents circular wait.",
                "#38BDF8")
        );
        explainRow.getChildren().forEach(n -> HBox.setHgrow((javafx.scene.Node) n, Priority.ALWAYS));

        // ── Animated log ───────────────────────────────────────────
        VBox logCard = makeCard("#EF4444");
        logCard.getChildren().add(sectionLabel("SIMULATION LOG  (step-by-step)", "#EF4444"));
        dlLogArea = new TextArea();
        dlLogArea.setPrefHeight(340);
        dlLogArea.setEditable(false);
        dlLogArea.setWrapText(false);
        dlLogArea.setStyle("-fx-control-inner-background: #0F172A; -fx-text-fill: #CBD5E1;" +
                           "-fx-font-family: 'Segoe UI'; -fx-font-size: 12;");
        dlLogArea.setText("Press  ▶ RUN DEADLOCK SIMULATION  to start.\n\n" +
                          "Watch the log animate step by step.\n" +
                          "The wait-graph above updates in real time.\n");
        logCard.getChildren().add(dlLogArea);

        // ══════════════════════════════════════════════════════════
        // BANKER'S ALGORITHM SECTION
        // ══════════════════════════════════════════════════════════
        VBox bankersSection = buildBankersSection();

        page.getChildren().addAll(
            title, subtitle,
            counterRow,
            wgCard,
            btnRow,
            explainRow,
            logCard,
            bankersSection
        );

        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #0F172A; -fx-background-color: #0F172A;");
        return sp;
    }

    // ── Run the animated simulation ───────────────────────────────
    private void runDeadlockSimulation() {
        if (dlSimRunning) return;
        dlSimRunning = true;
        dlSimBtn.setDisable(true);
        dlSimBtn.setText("⏳  SIMULATION RUNNING...");
        dlLogArea.clear();

        DeadlockDetector dd = kernel.getDeadlockDetector();
        dd.simulateDeadlock(line ->
            Platform.runLater(() -> {
                dlLogArea.appendText(line + "\n");
                dlLogArea.setScrollTop(Double.MAX_VALUE);
                // Update counters live
                dlDetectedLabel.setText(String.valueOf(dd.getDeadlocksDetected()));
                dlResolvedLabel.setText(String.valueOf(dd.getDeadlocksResolved()));
                // Update status
                if (line.contains("DEADLOCK DETECTED")) {
                    dlStatusLabel.setText("⚠  DEADLOCK ACTIVE");
                    dlStatusLabel.setTextFill(Color.web("#EF4444"));
                } else if (line.contains("SIMULATION COMPLETE")) {
                    dlStatusLabel.setText("✔  RESOLVED");
                    dlStatusLabel.setTextFill(Color.web("#10B981"));
                    dlSimRunning = false;
                    dlSimBtn.setDisable(false);
                    dlSimBtn.setText("▶  RUN DEADLOCK SIMULATION");
                }
            })
        );
    }


    // ══════════════════════════════════════════════════════════════
    // BANKER'S ALGORITHM UI SECTION
    // ══════════════════════════════════════════════════════════════
    private VBox buildBankersSection() {
        VBox section = new VBox(12);
        section.setPadding(new Insets(0));

        // ── Divider label ──────────────────────────────────────────
        Label divider = new Label("━━━━━━━━━━━━━━━━━━━━  BANKER'S ALGORITHM — DEADLOCK AVOIDANCE  ━━━━━━━━━━━━━━━━━━━━");
        divider.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        divider.setTextFill(Color.web("#38BDF8"));

        Label desc = new Label(
            "Banker's Algorithm (Deadlock Avoidance): checks if granting resources keeps the system in a SAFE STATE. " +
            "In the ATM context — ATM-01/02/03 are processes. Resources are account locks, semaphores, and memory frames. " +
            "If a safe sequence exists, all ATM transactions can complete without deadlock.");
        desc.setFont(Font.font("Segoe UI", 10));
        desc.setTextFill(Color.web("#64748B"));
        desc.setWrapText(true);

        // ── Concept cards ──────────────────────────────────────────
        HBox conceptRow = new HBox(12,
            buildExplainCard("📊 ALLOCATION",
                "Resources currently\nheld by each process.\nCannot exceed Max.", "#38BDF8"),
            buildExplainCard("📈 MAX NEED",
                "Maximum resources\na process may ever\nrequest in its lifetime.", "#F59E0B"),
            buildExplainCard("🔢 NEED = MAX - ALLOC",
                "Remaining resources\nstill needed by each\nprocess to complete.", "#818CF8"),
            buildExplainCard("✅ SAFE SEQUENCE",
                "Order in which processes\ncan execute & release\nwithout deadlock.", "#10B981")
        );
        conceptRow.getChildren().forEach(n -> HBox.setHgrow((javafx.scene.Node) n, Priority.ALWAYS));

        // ── Preset buttons ─────────────────────────────────────────
        Label presetLbl = new Label("LOAD PRESET SCENARIO:");
        presetLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        presetLbl.setTextFill(Color.web("#475569"));

        Button safePreset   = presetBtn("✔ SAFE STATE EXAMPLE",   "#10B981");
        Button unsafePreset = presetBtn("✘ UNSAFE STATE EXAMPLE", "#EF4444");
        Button clearPreset  = presetBtn("CLEAR",                   "#475569");

        safePreset.setOnAction(e -> loadSafePreset());
        unsafePreset.setOnAction(e -> loadUnsafePreset());
        clearPreset.setOnAction(e -> clearBankersFields());

        HBox presetRow = new HBox(10, presetLbl, safePreset, unsafePreset, clearPreset);
        presetRow.setAlignment(Pos.CENTER_LEFT);

        // ── Input grid ─────────────────────────────────────────────
        VBox inputCard = makeCard("#38BDF8");
        inputCard.getChildren().add(sectionLabel(
            "ATM PROCESSES  |  ATM-01  ATM-02  ATM-03  ×  RESOURCES: ACCOUNT_LOCK  SEMAPHORE  MEMORY_FRAME", "#38BDF8"));

        // Headers
        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);

        String[] procNames = {"ATM-01", "ATM-02", "ATM-03"};
        String[] resNames  = {"ACC_LOCK", "SEM", "MEM"};

        // Column headers
        grid.add(boldLabel("Process"),        0, 0);
        grid.add(boldLabel("Alloc  A B C"),   1, 0);
        grid.add(boldLabel("Max    A B C"),   4, 0);

        for (int i = 0; i < 3; i++) {
            grid.add(boldLabel(procNames[i]), 0, i + 1);
            for (int j = 0; j < 3; j++) {
                bankersAlloc[i][j] = bankersField();
                bankersMax[i][j]   = bankersField();
                grid.add(bankersAlloc[i][j], j + 1, i + 1);
                grid.add(bankersMax[i][j],   j + 4, i + 1);
            }
        }

        // Available row
        grid.add(boldLabel("Available:"), 0, 4);
        for (int j = 0; j < 3; j++) {
            bankersAvail[j] = bankersField();
            grid.add(bankersAvail[j], j + 1, 4);
        }

        // Resource type labels
        for (int j = 0; j < 3; j++) {
            grid.add(dimLabel(resNames[j]), j + 1, 5);
            grid.add(dimLabel(resNames[j]), j + 4, 5);
        }

        inputCard.getChildren().addAll(presetRow, grid);

        // ── Run button ─────────────────────────────────────────────
        bankersRunBtn = new Button("▶  RUN BANKER'S ALGORITHM — CHECK SAFE STATE");
        bankersRunBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        bankersRunBtn.setTextFill(Color.web("#38BDF8"));
        bankersRunBtn.setMaxWidth(Double.MAX_VALUE);
        bankersRunBtn.setStyle(
            "-fx-background-color: #38BDF8; -fx-border-color: transparent; " +
            "-fx-border-radius: 20; -fx-background-radius: 20; -fx-cursor: hand; -fx-padding: 12 24;" +
            "-fx-effect: dropshadow(gaussian,rgba(56,189,248,0.4),12,0,0,3);");
        bankersRunBtn.setTextFill(javafx.scene.paint.Color.web("#0F172A"));
        bankersRunBtn.setOnAction(e -> runBankersAlgorithm());

        // ── Output area ────────────────────────────────────────────
        VBox outputCard = makeCard("#38BDF8");
        outputCard.getChildren().add(sectionLabel("BANKER'S ALGORITHM OUTPUT", "#38BDF8"));
        bankersOutputArea = new TextArea();
        bankersOutputArea.setPrefHeight(280);
        bankersOutputArea.setEditable(false);
        bankersOutputArea.setWrapText(false);
        bankersOutputArea.setStyle(
            "-fx-control-inner-background: #0F172A; -fx-text-fill: #CBD5E1;" +
            "-fx-font-family: 'Segoe UI'; -fx-font-size: 12;");
        bankersOutputArea.setText(
            "Load a preset or enter values manually, then click RUN.\n\n" +
            "SAFE STATE   → A safe sequence exists, no deadlock possible.\n" +
            "UNSAFE STATE → No safe sequence found, deadlock may occur.\n");
        outputCard.getChildren().add(bankersOutputArea);

        loadSafePreset(); // load default preset on startup

        section.getChildren().addAll(divider, desc, conceptRow, inputCard,
                                     bankersRunBtn, outputCard);
        return section;
    }

    // ── Banker's preset loaders ───────────────────────────────────
    /** Classic safe state example from OS textbooks */
    private void loadSafePreset() {
        // Allocation
        int[][] alloc = {{0,1,0},{2,0,0},{3,0,2}};
        // Max
        int[][] max   = {{7,5,3},{3,2,2},{9,0,2}};
        // Available
        int[]   avail = {3,3,2};
        fillBankersFields(alloc, max, avail);
        bankersOutputArea.setText("Safe preset loaded (classic ATM scenario).\nATM-01 holds 1 semaphore, ATM-02 holds 2 account locks.\nClick RUN to find the safe execution sequence.\n");
    }

    /** Unsafe state — no safe sequence exists */
    private void loadUnsafePreset() {
        int[][] alloc = {{0,1,0},{2,0,0},{3,0,2}};
        int[][] max   = {{7,5,3},{3,2,2},{9,0,2}};
        int[]   avail = {0,0,0};  // no resources available → unsafe
        fillBankersFields(alloc, max, avail);
        bankersOutputArea.setText("Unsafe preset loaded — Available resources = 0.\nNo ATM process can proceed → deadlock possible.\nClick RUN to confirm UNSAFE state.\n");
    }

    private void fillBankersFields(int[][] alloc, int[][] max, int[] avail) {
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                bankersAlloc[i][j].setText(String.valueOf(alloc[i][j]));
                bankersMax[i][j].setText(String.valueOf(max[i][j]));
            }
        for (int j = 0; j < 3; j++)
            bankersAvail[j].setText(String.valueOf(avail[j]));
    }

    private void clearBankersFields() {
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                bankersAlloc[i][j].clear();
                bankersMax[i][j].clear();
            }
        for (int j = 0; j < 3; j++) bankersAvail[j].clear();
        bankersOutputArea.clear();
    }

    // ── Run Banker's Algorithm ────────────────────────────────────
    private void runBankersAlgorithm() {
        try {
            int[][] alloc = new int[3][3];
            int[][] max   = new int[3][3];
            int[]   avail = new int[3];

            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3; j++) {
                    alloc[i][j] = parseOrZero(bankersAlloc[i][j].getText());
                    max[i][j]   = parseOrZero(bankersMax[i][j].getText());
                }
            for (int j = 0; j < 3; j++)
                avail[j] = parseOrZero(bankersAvail[j].getText());

            // Validate: allocation cannot exceed max
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3; j++)
                    if (alloc[i][j] > max[i][j]) {
                        bankersOutputArea.setText(
                            "ERROR: ATM-0" + (i+1) + " Allocation[" + j + "] = " + alloc[i][j] +
                            " exceeds Max[" + j + "] = " + max[i][j] +
                            "\nAn ATM cannot hold more resources than its declared maximum need.");
                        bankersOutputArea.setStyle(
                            "-fx-control-inner-background: #1a0000; -fx-text-fill: #EF4444;" +
                            "-fx-font-family: 'Segoe UI'; -fx-font-size: 11;");
                        return;
                    }

            String[] procs = {"ATM-01", "ATM-02", "ATM-03"};
            String[] res   = {"ACC_LOCK", "SEM", "MEM"};

            sync.DeadlockDetector.BankersResult result =
                kernel.getDeadlockDetector().runBankersAlgorithm(procs, res, alloc, max, avail);

            bankersOutputArea.setText(String.join("\n", result.steps));

            if (result.safe) {
                bankersOutputArea.setStyle(
                    "-fx-control-inner-background: #001a00; -fx-text-fill: #10B981;" +
                    "-fx-font-family: 'Segoe UI'; -fx-font-size: 11;");
                dlStatusLabel.setText("✔  SAFE STATE");
                dlStatusLabel.setTextFill(Color.web("#10B981"));
            } else {
                bankersOutputArea.setStyle(
                    "-fx-control-inner-background: #1a0000; -fx-text-fill: #EF4444;" +
                    "-fx-font-family: 'Segoe UI'; -fx-font-size: 11;");
                dlStatusLabel.setText("⚠  UNSAFE STATE");
                dlStatusLabel.setTextFill(Color.web("#EF4444"));
            }

        } catch (Exception ex) {
            bankersOutputArea.setText("ERROR: " + ex.getMessage() + "\nCheck all fields have valid numbers.");
        }
    }

    private int parseOrZero(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // ── Banker's helper widgets ───────────────────────────────────
    private TextField bankersField() {
        TextField tf = new TextField("0");
        tf.setPrefWidth(42);
        tf.setMaxWidth(42);
        tf.setAlignment(Pos.CENTER);
        tf.setStyle(
            "-fx-background-color: #0F172A; -fx-border-color: #38BDF844; -fx-border-radius: 8;" +
            "-fx-background-radius: 8; -fx-text-fill: #38BDF8; -fx-font-family: 'Segoe UI';" +
            "-fx-font-size: 12;");
        return tf;
    }

    private Button presetBtn(String text, String color) {
        Button b = new Button(text);
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        b.setTextFill(Color.web(color));
        b.setStyle(String.format(
            "-fx-background-color: %s22; -fx-border-color: %s55;" +
            "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;", color, color));
        return b;
    }

    private Label boldLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        l.setTextFill(Color.web("#CBD5E1"));
        return l;
    }

    // ── Counter box helper ────────────────────────────────────────
    private VBox buildCounterBox(String label, Label valueLabel, String color) {
        VBox box = makeCard(color);
        box.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        lbl.setTextFill(Color.web("#475569"));
        box.getChildren().addAll(lbl, valueLabel);
        return box;
    }

    private Label bigCountLabel(String text, String color) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 36));
        l.setTextFill(Color.web(color));
        return l;
    }

    // ── Status box ────────────────────────────────────────────────
    private VBox buildStatusBox() {
        VBox box = makeCard("#64748B");
        box.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label("STATUS");
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        lbl.setTextFill(Color.web("#475569"));
        dlStatusLabel = new Label("● SYSTEM STABLE");
        dlStatusLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        dlStatusLabel.setTextFill(Color.web("#10B981"));
        box.getChildren().addAll(lbl, dlStatusLabel);
        return box;
    }

    // ── Explanation card ──────────────────────────────────────────
    private VBox buildExplainCard(String title, String body, String color) {
        VBox box = makeCard(color);
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

    // ══════════════════════════════════════════════════════════════
    // OTHER TABS
    // ══════════════════════════════════════════════════════════════
    private ScrollPane buildMemoryTab() {
        memoryPanel = new MemoryPanel();
        ScrollPane sp = new ScrollPane(memoryPanel);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #0F172A; -fx-background-color: #0F172A;");
        return sp;
    }

    private ScrollPane buildCpuSimTab() {
        schedulerPanel = new SchedulerPanel();
        ScrollPane sp = new ScrollPane(schedulerPanel);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #0F172A; -fx-background-color: #0F172A;");
        return sp;
    }

    private ScrollPane buildRaceTab() {
        racePanel = new RacePanel();
        ScrollPane sp = new ScrollPane(racePanel);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #0F172A; -fx-background-color: #0F172A;");
        return sp;
    }

    private ScrollPane buildInterruptTab() {
        interruptPanel = new InterruptPanel();
        ScrollPane sp = new ScrollPane(interruptPanel);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #0F172A; -fx-background-color: #0F172A;");
        return sp;
    }

    // ══════════════════════════════════════════════════════════════
    // BALANCE BAR
    // ══════════════════════════════════════════════════════════════
    private HBox buildBalanceBar() {
        HBox bar = new HBox(14);
        bar.setPadding(new Insets(8, 14, 8, 14));
        bar.setStyle("-fx-background-color: #1E293B; -fx-border-color: #334155;" +
                     "-fx-border-radius: 12; -fx-background-radius: 12;" +
                     "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.2),6,0,0,2);");
        bar.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label("LIVE BALANCES:");
        lbl.setFont(Font.font("Segoe UI", 9));
        lbl.setTextFill(Color.web("#475569"));
        balanceArea = new TextArea();
        balanceArea.setPrefHeight(30);
        balanceArea.setEditable(false);
        balanceArea.setStyle("-fx-control-inner-background: #1E293B; -fx-text-fill: #F59E0B;" +
                             "-fx-font-family: 'Segoe UI'; -fx-font-size: 12; -fx-border-color: transparent;" +
                             "-fx-font-weight: bold;");
        HBox.setHgrow(balanceArea, Priority.ALWAYS);
        bar.getChildren().addAll(lbl, balanceArea);
        return bar;
    }

    // ══════════════════════════════════════════════════════════════
    // UI REFRESH (500ms)
    // ══════════════════════════════════════════════════════════════
    private void startUIRefresh() {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() { Platform.runLater(DashboardApp.this::refreshUI); }
        }, 500, 500);
    }

    private void refreshUI() {
        // Header
        tickLabel.setText("TICK: " + monitor.getTick());
        queueLabel.setText("QUEUE: " + monitor.getQueueSize());
        cpuLabel.setText(String.format("CPU: %.0f%%", monitor.getCpuUtilization()));
        memLabel.setText("MEM: " + kernel.getMemoryUsagePercent() + "%");
        processLabel.setText("PROC: " + kernel.getProcessManager().getActiveProcesses().size());
        cpuBar.setProgress(monitor.getCpuUtilization() / 100.0);

        // Running transaction
        Transaction running = scheduler.getRunning();
        if (running != null) {
            runningLabel.setText(String.format("▶ %s | %s | ACC:%s | Rs.%.0f | Burst:%d/%d | PRI:%d",
                running.getId(), running.getType(), running.getAccountId(),
                running.getAmount(), running.getRemainingBurst(),
                running.getBurstTime(), running.getPriority()));
            runningLabel.setTextFill(Color.web("#38BDF8"));
            runningLabel.setStyle("-fx-background-color: #38BDF80d; -fx-border-color: #38BDF844;" +
                                  "-fx-border-radius: 8; -fx-background-radius: 8;");
        } else {
            runningLabel.setText("  No transaction running");
            runningLabel.setTextFill(Color.web("#475569"));
            runningLabel.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155;" +
                                  "-fx-border-radius: 8; -fx-background-radius: 8;");
        }

        // Ready queue
        queueListView.getItems().clear();
        for (Transaction t : scheduler.getReadyQueue()) {
            queueListView.getItems().add(String.format("%s | %s | %s | PRI:%d | Wait:%dT",
                t.getId(), t.getAtmId(), t.getType(), t.getPriority(), t.getWaitTime()));
        }

        drawGantt();
        drawCpuChart();

        completedLabel.setText(String.valueOf(monitor.getCompletedCount()));
        avgWaitLabel.setText(String.format("%.0fT",  monitor.getAvgWaitTime()));
        avgTatLabel.setText(String.format("%.0fms", monitor.getAvgTurnaround()));

        // WAL log
        List<String> journal = kernel.getTransactionLog().getLastN(40);
        logArea.setText(String.join("\n", journal));

        // Live balances
        StringBuilder bals = new StringBuilder();
        controller.getBank().getAllAccounts().forEach(acc ->
            bals.append(String.format("[%s %s: Rs.%.0f]  ",
                acc.getAccountId(), acc.getHolderName().split(" ")[0], acc.getBalance())));
        balanceArea.setText(bals.toString());

        // Deadlock tab — live wait-graph update
        refreshDeadlockPanel();

        if (memoryPanel != null) memoryPanel.refresh();
    }

    private void refreshDeadlockPanel() {
        if (dlWaitGraphArea == null) return;
        DeadlockDetector dd = kernel.getDeadlockDetector();

        // Update counters
        dlDetectedLabel.setText(String.valueOf(dd.getDeadlocksDetected()));
        dlResolvedLabel.setText(String.valueOf(dd.getDeadlocksResolved()));

        // Build wait-graph text
        Map<String, String> waits = dd.getWaitingFor();
        var locks = kernel.getSemaphoreManager().getLocks();

        if (waits.isEmpty()) {
            dlWaitGraphArea.setText("No process is waiting — system stable.");
        } else {
            StringBuilder sb = new StringBuilder();
            waits.forEach((proc, res) -> {
                var lock = locks.get(res);
                String holder = (lock != null && lock.isLocked()) ? lock.getHolder() : "FREE";
                sb.append(String.format("  %-30s  →  %-25s  (held by: %s)\n", proc, res, holder));
            });

            // Run cycle detection
            List<String> cycle = dd.detectCycle();
            if (!cycle.isEmpty()) {
                sb.append("\n  ⚠ CYCLE DETECTED: ");
                sb.append(String.join(" → ", cycle));
                sb.append(" → (back to start)");
                dlWaitGraphArea.setStyle("-fx-control-inner-background: #1a0000; -fx-text-fill: #EF4444;" +
                                        "-fx-font-family: 'Segoe UI'; -fx-font-size: 11;");
            } else {
                dlWaitGraphArea.setStyle("-fx-control-inner-background: #0F172A; -fx-text-fill: #F97316;" +
                                        "-fx-font-family: 'Segoe UI'; -fx-font-size: 11;");
            }
            dlWaitGraphArea.setText(sb.toString());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CANVAS DRAWING
    // ══════════════════════════════════════════════════════════════
    private void drawGantt() {
        List<Transaction> gantt = scheduler.getGanttList();
        GraphicsContext gc = ganttCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, ganttCanvas.getWidth(), ganttCanvas.getHeight());
        gc.setFill(Color.web("#0F172A"));
        gc.fillRect(0, 0, ganttCanvas.getWidth(), ganttCanvas.getHeight());
        if (gantt.isEmpty()) return;

        double blockW = ganttCanvas.getWidth() / 50.0;
        List<Transaction> slice = gantt.subList(Math.max(0, gantt.size() - 50), gantt.size());

        for (int i = 0; i < slice.size(); i++) {
            Transaction t = slice.get(i);
            double x = i * blockW;
            if (t == null) {
                gc.setFill(Color.web("#334155"));
                gc.fillRect(x + 0.5, 1, blockW - 1, 34);
                continue;
            }
            String col = switch (t.getAtmId()) {
                case "ATM-01" -> "#38BDF8";
                case "ATM-02" -> "#F59E0B";
                case "ATM-03" -> "#10B981";
                default       -> "#818CF8";
            };
            gc.setFill(Color.web(col + "66"));
            gc.fillRect(x + 0.5, 1, blockW - 1, 34);
            gc.setStroke(Color.web(col));
            gc.setLineWidth(0.5);
            gc.strokeRect(x + 0.5, 1, blockW - 1, 34);
            if (blockW > 18) {
                gc.setFill(Color.web(col));
                gc.setFont(Font.font("Segoe UI", 9));
                gc.fillText(t.getAtmId().substring(4), x + 3, 22);
            }
        }
    }

    private void drawCpuChart() {
        List<Double> hist = monitor.getCpuHistory();
        GraphicsContext gc = cpuChartCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, cpuChartCanvas.getWidth(), cpuChartCanvas.getHeight());
        gc.setFill(Color.web("#0F172A"));
        gc.fillRect(0, 0, cpuChartCanvas.getWidth(), cpuChartCanvas.getHeight());
        if (hist.isEmpty()) return;

        double w = cpuChartCanvas.getWidth(), h = cpuChartCanvas.getHeight();
        double step = w / Math.max(hist.size(), 60);

        gc.setStroke(Color.web("#334155")); gc.setLineWidth(0.5);
        for (int y = 0; y <= 4; y++) {
            double yy = h - (y / 4.0) * h;
            gc.strokeLine(0, yy, w, yy);
        }
        gc.setFill(Color.web("#818CF822"));
        gc.beginPath(); gc.moveTo(0, h);
        for (int i = 0; i < hist.size(); i++)
            gc.lineTo(i * step, h - (hist.get(i) / 100.0) * h);
        gc.lineTo((hist.size() - 1) * step, h); gc.closePath(); gc.fill();

        gc.setStroke(Color.web("#818CF8")); gc.setLineWidth(1.5);
        gc.beginPath();
        for (int i = 0; i < hist.size(); i++) {
            double x = i * step, y = h - (hist.get(i) / 100.0) * h;
            if (i == 0) gc.moveTo(x, y); else gc.lineTo(x, y);
        }
        gc.stroke();
    }

    // ══════════════════════════════════════════════════════════════
    // AUTO SIM
    // ══════════════════════════════════════════════════════════════
    private void startAutoSim() {
        autoTimer = new Timer(true);
        String[] accs  = {"ACC001","ACC002","ACC003","ACC004","ACC005"};
        String[] atms  = {"ATM-01","ATM-02","ATM-03"};
        String[] types = {"WITHDRAW","DEPOSIT","BALANCE","TRANSFER"};
        java.util.Random rand = new java.util.Random();

        autoTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                String atm  = atms[rand.nextInt(3)];
                String acc  = accs[rand.nextInt(5)];
                String type = types[rand.nextInt(4)];
                double amt  = (rand.nextInt(20) + 1) * 500.0;
                controller.submitAutoTransaction(atm, acc, type, amt);
                autoSimCount++;
                // POWER_FAILURE interrupt — fires every 10 auto-sim transactions
                // Simulates power fluctuation under sustained ATM load
                if (autoSimCount % 10 == 0) {
    kernel.getInterruptHandler().trigger(
        InterruptHandler.InterruptType.POWER_FAILURE,
        line -> {}
    );
}
            }
        }, 0, 900);
    }

    // ══════════════════════════════════════════════════════════════
    // STYLE HELPERS
    // ══════════════════════════════════════════════════════════════
    private VBox makeCard(String accentColor) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setStyle(String.format(
            "-fx-background-color: #1E293B; " +
            "-fx-border-color: %s; " +
            "-fx-border-width: 0 0 0 3; " +
            "-fx-border-radius: 0 12 12 0; " +
            "-fx-background-radius: 12; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 12, 0, 0, 3);",
            accentColor));
        return box;
    }

    private VBox buildMetricBox(String label, Label val, String clr) {
        VBox b = new VBox(6);
        b.setPadding(new Insets(14, 16, 14, 16));
        b.setAlignment(Pos.CENTER_LEFT);
        b.setStyle(String.format(
            "-fx-background-color: #1E293B; -fx-background-radius: 12;" +
            "-fx-border-color: #334155; -fx-border-width: 1; -fx-border-radius: 12;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 2);"));
        Label lbl = new Label(label);
        lbl.setFont(Font.font("Segoe UI", 10));
        lbl.setTextFill(Color.web("#64748B"));
        val.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        val.setTextFill(Color.web(clr));
        b.getChildren().addAll(lbl, val);
        return b;
    }

    private Label sectionLabel(String text, String color) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        l.setTextFill(Color.web(color));
        l.setPadding(new Insets(0, 0, 4, 0));
        return l;
    }

    private Label dimLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", 11));
        l.setTextFill(Color.web("#64748B"));
        return l;
    }

    private Label statLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        l.setTextFill(Color.web("#94A3B8"));
        l.setPadding(new Insets(3, 10, 3, 10));
        l.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 6;");
        return l;
    }

    private Label badge(String text, String color) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web(color));
        l.setPadding(new Insets(2, 8, 2, 8));
        l.setStyle(String.format(
            "-fx-background-color: %s1a; -fx-border-color: %s55;" +
            "-fx-border-radius: 8; -fx-background-radius: 8;", color, color));
        return l;
    }

    private void styleBtn(Button b, String color) {
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        b.setTextFill(Color.web("#F1F5F9"));
        b.setPadding(new Insets(7, 18, 7, 18));
        b.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: transparent; " +
            "-fx-border-radius: 20; -fx-background-radius: 20; -fx-cursor: hand;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 6, 0, 0, 2);",
            color));
    }

    public static void main(String[] args) { launch(args); }
}
