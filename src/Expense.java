import java.time.LocalDate;

// One expense row: date, category, amount in Rs., optional note.
public record Expense(LocalDate date, String category, double amount, String note) {
}
