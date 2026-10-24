import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.input.KeyCode;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

// Single-window expense tracker, built exactly to DESIGN.md. No other screens.
public class ExpenseTracker extends Application {
    // Fixed category colors; custom categories cycle through the extras in order.
    private static final Map<String, String> CATEGORY_COLORS = Map.of(
            "Food", "#E07856", "Transport", "#3E7CB1", "Bills", "#7D6BA8",
            "Shopping", "#C96A8A", "Entertainment", "#7BA05B", "Other", "#8A94A6");
    private static final List<String> CUSTOM_COLORS =
            List.of("#4A9B8E", "#B07D4F", "#5B7FA6", "#A67BA8");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy");

    private ExpenseStore store;
    private TableView<Expense> table;
    private ComboBox<String> monthBox;
    private ComboBox<String> categoryBox;
    private Label monthLabel;
    private Label totalLabel;
    private Label countLabel;
    private Label statusCount;
    private VBox breakdownBox;
    private VBox pieCard;
    private PieChart pieChart;

    // Form fields; editingIndex is -1 in Add mode, else the store index being edited.
    private Label formTitle;
    private HBox amountBox;
    private HBox editRow;
    private TextField amountField;
    private ComboBox<String> categoryField;
    private DatePicker dateField;
    private TextField noteField;
    private Button saveButton;
    private Button deleteButton;
    private Button cancelButton;
    private Label errorLabel;
    private int editingIndex = -1;

    @Override
    public void start(Stage stage) throws IOException {
        store = new ExpenseStore(Path.of("expenses.csv"));
        store.load();
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        VBox form = formPanel();
        VBox center = tableSection();
        VBox right = rightPanel();
        BorderPane.setMargin(form, new Insets(16, 8, 16, 16));
        BorderPane.setMargin(center, new Insets(16, 8, 16, 8));
        BorderPane.setMargin(right, new Insets(16, 16, 16, 8));
        root.setTop(topSection());
        root.setLeft(form);
        root.setCenter(center);
        root.setRight(right);
        root.setBottom(statusBar());
        Scene scene = new Scene(root, 1120, 700);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        scene.setOnKeyPressed(e -> {
            // Esc cancels an edit and returns the form to blank Add mode.
            if (e.getCode() == KeyCode.ESCAPE && editingIndex >= 0) resetForm();
        });
        stage.setTitle("Expense Tracker");
        stage.setMinWidth(1000);
        stage.setMinHeight(620);
        stage.setScene(scene);
        refreshFilterOptions();
        refresh();
        stage.show();
    }

    // Header with filters plus the totals strip, stacked at the top.
    private VBox topSection() {
        Label title = new Label("Expense Tracker");
        title.getStyleClass().add("app-title");
        monthBox = new ComboBox<>();
        monthBox.setOnAction(e -> refresh());
        categoryBox = new ComboBox<>();
        categoryBox.setOnAction(e -> refresh());
        Button clear = new Button("Clear");
        clear.getStyleClass().add("ghost-btn");
        clear.setOnAction(e -> {
            monthBox.setValue("All months");
            categoryBox.setValue("All categories");
            refresh();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, spacer, monthBox, categoryBox, clear);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        monthLabel = new Label();
        monthLabel.getStyleClass().add("hero-label");
        totalLabel = new Label();
        totalLabel.getStyleClass().add("total");
        countLabel = new Label();
        countLabel.getStyleClass().add("hero-count");
        VBox totalBlock = new VBox(2, monthLabel, totalLabel);
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        HBox totals = new HBox(10, totalBlock, spacer2, countLabel);
        totals.getStyleClass().add("totals-strip");
        totals.setAlignment(Pos.CENTER_LEFT);
        Region accent = new Region();
        accent.getStyleClass().add("hero-accent");
        return new VBox(header, totals, accent);
    }

    // One form for both Add and Edit; clicking a table row loads it for editing.
    private VBox formPanel() {
        formTitle = new Label("Add Expense");
        formTitle.getStyleClass().add("section-title");
        Region tick = new Region();
        tick.getStyleClass().add("section-tick");
        VBox titleBlock = new VBox(8, formTitle, tick);
        Label amountLabel = new Label("Amount");
        amountLabel.getStyleClass().add("field-label");
        amountField = new TextField();
        amountField.setPromptText("0.00");
        Label prefix = new Label("Rs.");
        prefix.getStyleClass().add("muted");
        amountBox = new HBox(6, prefix, amountField);
        amountBox.getStyleClass().add("amount-input");
        amountBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(amountField, Priority.ALWAYS);
        amountField.focusedProperty().addListener((obs, oldV, focused) -> {
            // Navy border on the container while the inner field has focus.
            if (focused) amountBox.getStyleClass().add("input-focused");
            else amountBox.getStyleClass().remove("input-focused");
        });
        VBox amountGroup = new VBox(6, amountLabel, amountBox);
        Label categoryLabel = new Label("Category");
        categoryLabel.getStyleClass().add("field-label");
        categoryField = new ComboBox<>();
        categoryField.setEditable(true);
        categoryField.setItems(FXCollections.observableArrayList(ExpenseStore.PRESETS));
        categoryField.setValue("Food");
        VBox categoryGroup = new VBox(6, categoryLabel, categoryField);
        Label dateLabel = new Label("Date");
        dateLabel.getStyleClass().add("field-label");
        dateField = new DatePicker(LocalDate.now());
        VBox dateGroup = new VBox(6, dateLabel, dateField);
        Label noteLabel = new Label("Note");
        noteLabel.getStyleClass().add("field-label");
        noteField = new TextField();
        noteField.setPromptText("Optional");
        VBox noteGroup = new VBox(6, noteLabel, noteField);
        saveButton = new Button("Add Expense");
        saveButton.getStyleClass().add("primary-btn");
        saveButton.setDefaultButton(true);
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setOnAction(e -> saveExpense());
        HBox primaryRow = new HBox(saveButton);
        HBox.setHgrow(saveButton, Priority.ALWAYS);
        deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("danger-btn");
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        deleteButton.setOnAction(e -> deleteExpense());
        cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("ghost-btn");
        cancelButton.setMaxWidth(Double.MAX_VALUE);
        cancelButton.setOnAction(e -> resetForm());
        editRow = new HBox(8, deleteButton, cancelButton);
        HBox.setHgrow(deleteButton, Priority.ALWAYS);
        HBox.setHgrow(cancelButton, Priority.ALWAYS);
        editRow.setVisible(false);
        editRow.setManaged(false);
        errorLabel = new Label();
        errorLabel.getStyleClass().add("error");
        VBox buttonsBlock = new VBox(10, primaryRow, editRow, errorLabel);
        VBox form = new VBox(14, titleBlock, amountGroup, categoryGroup,
                dateGroup, noteGroup, buttonsBlock);
        form.getStyleClass().add("panel");
        form.setPrefWidth(300);
        form.setMinWidth(300);
        return form;
    }

    private VBox tableSection() {
        table = new TableView<>();
        Label placeholder = new Label("No expenses yet — add your first one on the left.");
        placeholder.getStyleClass().add("muted");
        table.setPlaceholder(placeholder);
        table.setFixedCellSize(40);
        TableColumn<Expense, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().date()));
        dateCol.setCellFactory(c -> new TableCell<>() {
            // Shows dates as "12 Oct 2026".
            @Override
            protected void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? null : DATE_FMT.format(d));
            }
        });
        dateCol.setPrefWidth(110);
        dateCol.setMinWidth(110);
        dateCol.setMaxWidth(110);
        TableColumn<Expense, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().category()));
        catCol.setCellFactory(c -> new TableCell<>() {
            // Color dot tying the row to its pie slice, then the category name.
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setGraphic(null);
                } else {
                    Circle dot = new Circle(6, Color.web(colorFor(s)));
                    HBox cell = new HBox(8, dot, new Label(s));
                    cell.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(cell);
                }
            }
        });
        catCol.setPrefWidth(150);
        catCol.setMinWidth(150);
        catCol.setMaxWidth(150);
        TableColumn<Expense, String> noteCol = new TableColumn<>("Note");
        noteCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().note()));
        noteCol.setCellFactory(c -> new TableCell<>() {
            // Note column is muted; only the Note column, not the whole table.
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (!getStyleClass().contains("note-cell")) getStyleClass().add("note-cell");
                setText(empty ? null : s);
            }
        });
        TableColumn<Expense, Double> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().amount()));
        amountCol.setCellFactory(c -> new TableCell<>() {
            // Amounts bold ink, right-aligned, thousands separators, 2 decimals.
            @Override
            protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                if (!getStyleClass().contains("amount-cell")) getStyleClass().add("amount-cell");
                setText(empty || v == null ? null : fmt(v));
            }
        });
        amountCol.setPrefWidth(100);
        amountCol.setMinWidth(100);
        amountCol.setMaxWidth(100);
        table.getColumns().addAll(List.of(dateCol, catCol, noteCol, amountCol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setOnMouseClicked(e -> {
            Expense selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) loadForEdit(selected);
        });
        VBox box = new VBox(table);
        box.getStyleClass().addAll("panel", "table-card");
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    // Breakdown list card above the pie chart card; both share the same totals.
    private VBox rightPanel() {
        Label breakdownTitle = new Label("By category");
        breakdownTitle.getStyleClass().add("section-title");
        Region tick = new Region();
        tick.getStyleClass().add("section-tick");
        VBox titleBlock = new VBox(8, breakdownTitle, tick);
        breakdownBox = new VBox(0);
        VBox breakdownCard = new VBox(16, titleBlock, breakdownBox);
        breakdownCard.getStyleClass().add("panel");
        pieChart = new PieChart();
        pieChart.setLegendVisible(false);
        pieChart.setLabelsVisible(true);
        pieChart.setPrefSize(200, 200);
        pieCard = new VBox(pieChart);
        pieCard.getStyleClass().addAll("panel", "pie-card");
        pieCard.setAlignment(Pos.CENTER);
        VBox.setVgrow(pieChart, Priority.ALWAYS);
        VBox panel = new VBox(16, breakdownCard, pieCard);
        panel.setPrefWidth(300);
        panel.setMinWidth(300);
        return panel;
    }

    private HBox statusBar() {
        Label fileLabel = new Label("Data file: expenses.csv");
        fileLabel.getStyleClass().add("status-left");
        statusCount = new Label();
        statusCount.getStyleClass().add("status-right");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(fileLabel, spacer, statusCount);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // Rebuilds filter options from the data, keeping the selection when possible.
    private void refreshFilterOptions() {
        String month = monthBox.getValue();
        List<String> months = new ArrayList<>();
        months.add("All months");
        for (YearMonth m : store.months()) months.add(m.toString());
        monthBox.setItems(FXCollections.observableArrayList(months));
        if (month != null && months.contains(month)) {
            monthBox.setValue(month);
        } else if (months.contains(YearMonth.now().toString())) {
            monthBox.setValue(YearMonth.now().toString());
        } else {
            monthBox.setValue("All months");
        }
        String category = categoryBox.getValue();
        List<String> categories = new ArrayList<>();
        categories.add("All categories");
        categories.addAll(ExpenseStore.PRESETS);
        for (String c : store.customCategories()) {
            if (!categories.contains(c)) categories.add(c);
        }
        categoryBox.setItems(FXCollections.observableArrayList(categories));
        categoryBox.setValue(category != null && categories.contains(category)
                ? category : "All categories");
    }

    // Applies the filters to the table, totals, breakdown, and pie at once.
    private void refresh() {
        if (monthBox.getValue() == null || categoryBox.getValue() == null) return;
        List<Expense> filtered = new ArrayList<>();
        for (Expense e : store.expenses()) {
            boolean monthOk = monthBox.getValue().equals("All months")
                    || YearMonth.from(e.date()).toString().equals(monthBox.getValue());
            boolean catOk = categoryBox.getValue().equals("All categories")
                    || e.category().equals(categoryBox.getValue());
            if (monthOk && catOk) filtered.add(e);
        }
        filtered.sort((a, b) -> b.date().compareTo(a.date()));
        table.setItems(FXCollections.observableArrayList(filtered));
        double total = 0;
        for (Expense e : filtered) total += e.amount();
        monthLabel.setText(monthBox.getValue().equals("All months") ? "All months"
                : MONTH_FMT.format(YearMonth.parse(monthBox.getValue())));
        totalLabel.setText("Total Rs. " + fmt(total));
        countLabel.setText(filtered.size() + (filtered.size() == 1 ? " expense" : " expenses"));
        Map<String, Double> byCategory = ExpenseStore.totalsByCategory(filtered);
        breakdownBox.getChildren().clear();
        if (byCategory.isEmpty()) {
            Label empty = new Label("No categories in this view.");
            empty.getStyleClass().add("muted");
            breakdownBox.getChildren().add(empty);
        }
        boolean first = true;
        for (Map.Entry<String, Double> entry : byCategory.entrySet()) {
            if (!first) {
                Region divider = new Region();
                divider.getStyleClass().add("row-divider");
                divider.setMaxWidth(Double.MAX_VALUE);
                breakdownBox.getChildren().add(divider);
            }
            first = false;
            double fraction = total == 0 ? 0 : entry.getValue() / total;
            Circle dot = new Circle(6, Color.web(colorFor(entry.getKey())));
            Label name = new Label(entry.getKey());
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label amount = new Label("Rs. " + fmt(entry.getValue()));
            amount.getStyleClass().add("breakdown-amount");
            Label separator = new Label("·");
            separator.getStyleClass().add("breakdown-share");
            Label shareLabel = new Label(String.format(Locale.ROOT, "%.1f%%", fraction * 100));
            shareLabel.getStyleClass().add("breakdown-share");
            HBox right = new HBox(4, amount, separator, shareLabel);
            HBox line = new HBox(8, dot, name, spacer, right);
            line.setAlignment(Pos.CENTER_LEFT);
            Region track = new Region();
            track.getStyleClass().add("share-track");
            track.setMaxWidth(Double.MAX_VALUE);
            Region fill = new Region();
            fill.getStyleClass().add("share-fill");
            fill.setStyle("-fx-background-color: " + colorFor(entry.getKey()) + ";");
            fill.prefWidthProperty().bind(track.widthProperty().multiply(fraction));
            StackPane bar = new StackPane(track, fill);
            bar.setAlignment(Pos.CENTER_LEFT);
            VBox row = new VBox(5, line, bar);
            row.setPadding(new Insets(7, 0, 7, 0));
            breakdownBox.getChildren().add(row);
        }
        List<String> pieOrder = new ArrayList<>(ExpenseStore.PRESETS);
        for (String c : store.customCategories()) {
            if (!pieOrder.contains(c)) pieOrder.add(c);
        }
        List<PieChart.Data> slices = new ArrayList<>();
        for (String category : pieOrder) {
            Double value = byCategory.get(category);
            if (value == null) continue;
            double fraction = total == 0 ? 0 : value / total;
            PieChart.Data slice = new PieChart.Data(
                    String.format(Locale.ROOT, "%.1f%%", fraction * 100), value);
            String color = colorFor(category);
            slice.nodeProperty().addListener((obs, oldNode, node) -> {
                // Slice colors match the breakdown dots; white separator between slices.
                if (node != null) node.setStyle("-fx-pie-color: " + color
                        + "; -fx-border-color: white; -fx-border-width: 2;");
            });
            slices.add(slice);
        }
        pieChart.setData(FXCollections.observableArrayList(slices));
        if (filtered.isEmpty()) {
            Label empty = new Label("No data for this period.");
            empty.getStyleClass().add("muted");
            pieCard.getChildren().setAll(empty);
        } else {
            pieCard.getChildren().setAll(pieChart);
        }
        int all = store.expenses().size();
        statusCount.setText(all + (all == 1 ? " expense" : " expenses"));
    }

    // Clears red borders from the form fields; called on every save attempt and reset.
    private void clearFieldErrors() {
        amountBox.getStyleClass().remove("input-error");
        categoryField.getStyleClass().remove("input-error");
        dateField.getStyleClass().remove("input-error");
    }

    private void saveExpense() {
        errorLabel.setText("");
        clearFieldErrors();
        String raw = amountField.getText().replace(",", "").replace(" ", "").trim();
        double amount = 0;
        boolean amountOk = raw.matches("\\d+(\\.\\d{1,2})?");
        if (amountOk) {
            try {
                amount = Double.parseDouble(raw);
                amountOk = amount > 0;
            } catch (NumberFormatException ex) {
                amountOk = false;
            }
        }
        if (!amountOk) {
            errorLabel.setText("Enter an amount greater than 0.");
            amountBox.getStyleClass().add("input-error");
            return;
        }
        String category = categoryField.getEditor().getText().trim();
        if (category.isEmpty()) {
            errorLabel.setText("Choose or type a category.");
            categoryField.getStyleClass().add("input-error");
            return;
        }
        LocalDate date = dateField.getValue();
        if (date == null) {
            errorLabel.setText("Pick a date.");
            dateField.getStyleClass().add("input-error");
            return;
        }
        Expense expense = new Expense(date, category, amount, noteField.getText().trim());
        try {
            if (editingIndex >= 0) store.replace(editingIndex, expense);
            else store.add(expense);
            store.save();
        } catch (IOException ex) {
            errorLabel.setText("Could not save expenses.csv.");
            return;
        }
        resetForm();
        refreshFilterOptions();
        refresh();
    }

    private void deleteExpense() {
        if (editingIndex < 0) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this expense?");
        confirm.setHeaderText(null);
        confirm.getDialogPane().getStylesheets().add(
                getClass().getResource("style.css").toExternalForm());
        Button okButton = (Button) confirm.getDialogPane().lookupButton(ButtonType.OK);
        okButton.getStyleClass().add("danger-solid-btn");
        Button cancelButton2 = (Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton2.getStyleClass().add("ghost-btn");
        if (confirm.showAndWait().orElse(null) != ButtonType.OK) return;
        try {
            store.remove(editingIndex);
            store.save();
        } catch (IOException ex) {
            errorLabel.setText("Could not save expenses.csv.");
            return;
        }
        resetForm();
        refreshFilterOptions();
        refresh();
    }

    // Loads a clicked row into the form in Edit mode.
    private void loadForEdit(Expense selected) {
        editingIndex = store.expenses().indexOf(selected);
        formTitle.setText("Edit Expense");
        amountField.setText(String.format(Locale.ROOT, "%.2f", selected.amount()));
        categoryField.setValue(selected.category());
        dateField.setValue(selected.date());
        noteField.setText(selected.note());
        saveButton.setText("Save Changes");
        editRow.setVisible(true);
        editRow.setManaged(true);
        errorLabel.setText("");
    }

    // Returns the form to blank Add mode.
    private void resetForm() {
        editingIndex = -1;
        table.getSelectionModel().clearSelection();
        formTitle.setText("Add Expense");
        amountField.clear();
        categoryField.setValue("Food");
        dateField.setValue(LocalDate.now());
        noteField.clear();
        saveButton.setText("Add Expense");
        editRow.setVisible(false);
        editRow.setManaged(false);
        clearFieldErrors();
        errorLabel.setText("");
    }

    // Fixed color for presets; custom categories cycle through the extras in order.
    private String colorFor(String category) {
        if (CATEGORY_COLORS.containsKey(category)) return CATEGORY_COLORS.get(category);
        List<String> customs = store.customCategories();
        int index = customs.indexOf(category);
        return CUSTOM_COLORS.get(index < 0 ? 0 : index % CUSTOM_COLORS.size());
    }

    // Thousands separators with exactly 2 decimals, everywhere amounts appear.
    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%,.2f", value);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
