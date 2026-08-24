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
import kernel.Kernel;
import memory.MemoryManager;
import memory.PageEntry;

import java.util.List;

public class MemoryPanel extends VBox {

    private final Kernel        kernel  = Kernel.getInstance();
    private final MemoryManager mm;

    private final Canvas  frameCanvas  = new Canvas(680, 80);
    private final Canvas  mapCanvas    = new Canvas(680, 160);
    private final Label   usageLabel   = new Label("0 / 16 frames");
    private final Label   faultLabel   = new Label("Page faults: 0");
    private       int     faultCount   = 0;

    private final TableView<PageEntry> table = new TableView<>();

    public MemoryPanel() {
        mm = kernel.getMemoryManager();
        build();
    }

    @SuppressWarnings("unchecked")
    private void build() {
        setSpacing(10);
        setPadding(new Insets(14));
        setStyle("-fx-background-color: #0F172A;");

        // ── Header ──
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("MEMORY MANAGER — Page Table & Frame Allocation");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        title.setTextFill(Color.web("#38BDF8"));

        usageLabel.setFont(Font.font("Segoe UI", 12));
        usageLabel.setTextFill(Color.web("#F59E0B"));

        faultLabel.setFont(Font.font("Segoe UI", 12));
        faultLabel.setTextFill(Color.web("#EF4444"));

        Button pageFaultBtn = new Button("SIMULATE PAGE FAULT");
        styleBtn(pageFaultBtn, "#EF4444");
        pageFaultBtn.setOnAction(e -> {
            // Access random account to trigger page-fault simulation
            String[] ids = {"ACC001","ACC002","ACC003","ACC004","ACC005"};
            String id = ids[(int)(Math.random() * ids.length)];
            mm.free(id);  // evict first so access causes fault
            mm.access(id);
            faultCount++;
            refresh();
        });

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, usageLabel, faultLabel, pageFaultBtn);

        // ── Frame grid canvas ──
        VBox frameBox = card("#334155");
        Label flbl = dimLabel("PHYSICAL FRAME MAP  (16 frames, Frame 0 = kernel)");
        frameBox.getChildren().addAll(flbl, frameCanvas);

        // ── Page table ──
        VBox tableBox = card("#334155");
        Label tlbl = dimLabel("PAGE TABLE");
        buildTable();
        tableBox.getChildren().addAll(tlbl, table);

        getChildren().addAll(header, frameBox, tableBox);
        drawFrameGrid(List.of());
    }

    @SuppressWarnings("unchecked")
    private void buildTable() {
        table.setStyle("-fx-background-color: #0F172A; -fx-border-color: #334155;");
        table.setPrefHeight(200);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<PageEntry, String> c1 = col("Page #",    pe -> String.valueOf(pe.getPageNumber()));
        TableColumn<PageEntry, String> c2 = col("Frame #",   pe -> String.valueOf(pe.getFrameNumber()));
        TableColumn<PageEntry, String> c3 = col("Account",   pe -> pe.getAccountId());
        TableColumn<PageEntry, String> c4 = col("Valid",     pe -> pe.isValid() ? "YES" : "NO");
        TableColumn<PageEntry, String> c5 = col("Dirty bit", pe -> pe.isDirty() ? "DIRTY" : "CLEAN");
        table.getColumns().addAll(c1, c2, c3, c4, c5);
    }

    private TableColumn<PageEntry, String> col(String name,
            java.util.function.Function<PageEntry, String> getter) {
        TableColumn<PageEntry, String> c = new TableColumn<>(name);
        c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(getter.apply(d.getValue())));
        c.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 11;");
        return c;
    }

    public void refresh() {
        List<PageEntry> pages = mm.getAllPages();

        // Update labels
        usageLabel.setText(mm.getUsedFrames() + " / " + mm.getTotalFrames() + " frames  (" +
                           mm.getMemoryUsagePercent() + "%)");
        faultLabel.setText("Page faults simulated: " + faultCount);

        // Table
        table.getItems().setAll(pages);

        // Frame grid
        drawFrameGrid(pages);
    }

    private void drawFrameGrid(List<PageEntry> pages) {
        GraphicsContext gc = frameCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, frameCanvas.getWidth(), frameCanvas.getHeight());
        gc.setFill(Color.web("#0F172A"));
        gc.fillRect(0, 0, frameCanvas.getWidth(), frameCanvas.getHeight());

        int totalFrames = mm.getTotalFrames();
        double w = (frameCanvas.getWidth() - 40) / totalFrames;
        double h = 50;
        double y = 14;

        for (int i = 0; i < totalFrames; i++) {
            double x = 20 + i * w;
            boolean used = (i == 0); // frame 0 = kernel

            for (PageEntry pe : pages) {
                if (pe.getFrameNumber() == i && pe.isValid()) { used = true; break; }
            }

            Color fill  = i == 0 ? Color.web("#818CF8") : used ? Color.web("#38BDF833") : Color.web("#334155");
            Color stroke= i == 0 ? Color.web("#818CF8") : used ? Color.web("#38BDF8")    : Color.web("#334155");

            gc.setFill(fill);
            gc.fillRoundRect(x + 1, y, w - 2, h, 4, 4);
            gc.setStroke(stroke);
            gc.setLineWidth(0.5);
            gc.strokeRoundRect(x + 1, y, w - 2, h, 4, 4);

            gc.setFill(i == 0 ? Color.web("#818CF8") : used ? Color.web("#38BDF8") : Color.web("#475569"));
            gc.setFont(Font.font("Segoe UI", 9));
            gc.fillText(String.valueOf(i), x + w / 2 - 3, y + h / 2 + 3);
        }

        // Legend
        gc.setFill(Color.web("#475569"));
        gc.setFont(Font.font("Segoe UI", 9));
        gc.fillText("■ kernel", 20, y + h + 14);

        gc.setFill(Color.web("#38BDF8"));
        gc.fillText("■ used", 80, y + h + 14);

        gc.setFill(Color.web("#334155"));
        gc.fillText("■ free", 130, y + h + 14);
    }

    // ── Style helpers ─────────────────────────────────────────────
    private VBox card(String bg) {
        VBox b = new VBox(8);
        b.setPadding(new Insets(10));
        b.setStyle("-fx-background-color: #1E293B; -fx-border-color: #334155;" +
                   "-fx-border-radius: 8; -fx-background-radius: 8;");
        return b;
    }

    private Label dimLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web("#475569"));
        return l;
    }

    private void styleBtn(Button b, String c) {
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        b.setTextFill(Color.web(c));
        b.setStyle(String.format(
            "-fx-background-color: %s22; -fx-border-color: %s; " +
            "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;", c, c));
    }
}
