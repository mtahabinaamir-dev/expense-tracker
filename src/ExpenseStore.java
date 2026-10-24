import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Loads, holds, and saves expenses.csv. One row per line: date,category,amount,note.
public class ExpenseStore {
    // Preset categories, in the order they appear in the category filter.
    public static final List<String> PRESETS =
            List.of("Food", "Transport", "Bills", "Shopping", "Entertainment", "Other");

    private final Path file;
    private final List<Expense> expenses = new ArrayList<>();

    public ExpenseStore(Path file) {
        this.file = file;
    }

    public List<Expense> expenses() {
        return expenses;
    }

    // Missing file means an empty list, not an error.
    public void load() throws IOException {
        expenses.clear();
        if (!Files.exists(file)) return;
        for (String line : Files.readAllLines(file)) {
            Expense e = parseLine(line);
            if (e != null) expenses.add(e);
        }
    }

    // Rewrites the whole file on every change; the file is the only storage.
    public void save() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Expense e : expenses) {
            lines.add(e.date() + "," + csvField(e.category()) + ","
                    + String.format(Locale.ROOT, "%.2f", e.amount()) + "," + csvField(e.note()));
        }
        Files.write(file, lines);
    }

    public void add(Expense e) {
        expenses.add(e);
    }

    public void replace(int index, Expense e) {
        expenses.set(index, e);
    }

    public void remove(int index) {
        expenses.remove(index);
    }

    // Months present in the data, newest first, for the month filter.
    public List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>();
        for (Expense e : expenses) {
            YearMonth m = YearMonth.from(e.date());
            if (!months.contains(m)) months.add(m);
        }
        months.sort((a, b) -> b.compareTo(a));
        return months;
    }

    // Custom categories present in the data, in first-appearance order.
    public List<String> customCategories() {
        List<String> customs = new ArrayList<>();
        for (Expense e : expenses) {
            if (!PRESETS.contains(e.category()) && !customs.contains(e.category())) {
                customs.add(e.category());
            }
        }
        return customs;
    }

    // Totals per category over the given expenses, highest amount first.
    public static Map<String, Double> totalsByCategory(List<Expense> list) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (Expense e : list) totals.merge(e.category(), e.amount(), Double::sum);
        List<Map.Entry<String, Double>> entries = new ArrayList<>(totals.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        Map<String, Double> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : entries) sorted.put(entry.getKey(), entry.getValue());
        return sorted;
    }

    // Parses one CSV line; returns null for malformed lines, which are skipped.
    private static Expense parseLine(String line) {
        String[] fields = parseCsv(line);
        if (fields == null || fields.length != 4) return null;
        LocalDate date;
        double amount;
        try {
            date = LocalDate.parse(fields[0].trim());
            amount = Double.parseDouble(fields[2].trim());
        } catch (RuntimeException ex) {
            return null;
        }
        String category = fields[1].trim();
        if (category.isEmpty() || amount <= 0 || !Double.isFinite(amount)) return null;
        return new Expense(date, category, amount, fields[3]);
    }

    // Minimal CSV parser: quoted fields, doubled quotes, commas inside quotes.
    private static String[] parseCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (inQuotes) return null;
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    // Quotes a field only when it contains a comma, quote, or newline.
    private static String csvField(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
