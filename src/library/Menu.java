package library;

import library.Book.BookCopy;
import library.ExceptionHandler.AgeRestrictionException;
import library.ExceptionHandler.LibraryException;
import library.Records.Fine;
import library.Records.Loan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

/**
 * ABSTRACTION + POLYMORPHISM for the user interface.
 *
 * run() is a template: it prints the options, reads a choice and hands it
 * to handle(). Each role's menu (LibrarianMenu, StudentMenu, TeacherMenu)
 * extends this class and supplies its own title, options and handle().
 *
 * Every action goes through ExceptionHandler.run(), so one failed action
 * never crashes the program.
 *
 * This file also contains the shared console input/output helpers.
 */
public abstract class Menu {

    private static final Scanner SCANNER = new Scanner(System.in);
    private static final int WIDTH = 78;

    protected final User user;
    protected final LibraryService service;
    protected final LibraryDatabase db = LibraryDatabase.getInstance(); // Singleton

    protected Menu(User user, LibraryService service) {
        this.user = user;
        this.service = service;
    }

    protected abstract String getTitle();

    protected abstract String[] getOptions();

    protected abstract void handle(int choice) throws LibraryException;

    /** Called once right after login. */
    protected void onLogin() {
    }

    // ---------------------------------------------------------------------
    // Navigation: breadcrumb trail ("Main Menu > Book Management > Add Book")
    // and a pause after each action so results don't scroll away.
    // ---------------------------------------------------------------------

    private final List<String> trail = new ArrayList<>();
    private boolean skipPause;

    /** Current location, e.g. "Main Menu > Circulation". */
    protected String location() {
        StringBuilder sb = new StringBuilder("Main Menu");
        for (String step : trail)
            sb.append(" > ").append(step);
        return sb.toString();
    }

    /** Prints where the user is right now (shown above every screen). */
    protected void showLocation(String action) {
        subheader(action == null ? location() : location() + " > " + shortName(action));
    }

    /** "Renew Book - extend a due date" -> "Renew Book" (drops the menu hint). */
    private static String shortName(String option) {
        return option.split("\\s{2,}")[0].trim();
    }

    /**
     * Menu line for a CATEGORY (opens more choices), e.g. "Book Management > Add,
     * Update, Remove".
     */
    protected static String category(String name, String hint) {
        return String.format("%-22s  >  %s", name, hint);
    }

    /** Menu line for a single action with a short hint. */
    protected static String item(String name, String hint) {
        return String.format("%-22s     %s", name, hint);
    }

    /**
     * Called by an action that was cancelled, so we go straight back to the menu.
     */
    protected void cancelled() {
        info("Cancelled. Nothing was changed.");
        skipPause = true;
    }

    private void pauseAfterAction() {
        if (skipPause) {
            skipPause = false;
            return;
        }
        System.out.println();
        readLine("  Press Enter to go back to " + location() + "...");
    }

    public final void run() {
        onLogin();
        while (true) {
            header(getTitle() + "  |  " + user.getFullName() + "  |  " + db.today());
            info("You are here: Main Menu      (options marked > open more choices)");
            String[] options = getOptions();
            int choice = chooseOption(options, "Log out");
            if (choice == 0) {
                info("Goodbye, " + user.getFullName() + "!");
                return;
            }
            showLocation(options[choice - 1]);
            ExceptionHandler.run(() -> handle(choice));
            pauseAfterAction();
        }
    }

    /** Handler for a numbered submenu choice. */
    @FunctionalInterface
    protected interface ChoiceHandler {
        void handle(int choice) throws LibraryException;
    }

    protected void subMenu(String title, String[] options, ChoiceHandler handler) {
        subMenu(title, options, handler, null);
    }

    /**
     * intro (optional) is shown once when the category is opened, e.g. the
     * dashboard summary.
     */
    protected void subMenu(String title, String[] options, ChoiceHandler handler, Runnable intro) {
        if (intro != null)
            intro.run();
        trail.add(shortName(title));
        boolean first = true; // the caller already printed the location once
        try {
            while (true) {
                if (!first)
                    showLocation(null);
                first = false;
                int choice = chooseOption(options,
                        "Back to " + (trail.size() > 1 ? trail.get(trail.size() - 2) : "Main Menu"));
                if (choice == 0) {
                    return;
                }
                showLocation(options[choice - 1]);
                ExceptionHandler.run(() -> handler.handle(choice));
                pauseAfterAction();
            }
        } finally {
            trail.remove(trail.size() - 1);
            skipPause = true; // leaving a submenu needs no extra "Press Enter"
        }
    }

    private static int chooseOption(String[] options, String zeroLabel) {
        for (int i = 0; i < options.length; i++) {
            System.out.printf("  [%d] %s%n", i + 1, options[i]);
        }
        System.out.println("  [0] " + zeroLabel);
        return readInt("Choose an option: ", 0, options.length);
    }

    // ---------------------------------------------------------------------
    // Pick lists: users choose by NUMBER instead of typing/remembering IDs.
    // ---------------------------------------------------------------------

    /**
     * Shows a numbered list and returns the chosen item, or null if the user
     * enters 0 (cancel) or there is nothing to choose from.
     */
    protected <T> T pick(String question, List<T> items, Function<T, String> label, String emptyMessage) {
        if (items.isEmpty()) {
            info(emptyMessage);
            skipPause = false;
            return null;
        }
        info(question);
        for (int i = 0; i < items.size(); i++) {
            System.out.printf("  [%d] %s%n", i + 1, label.apply(items.get(i)));
        }
        System.out.println("  [0] Cancel");
        int choice = readInt("Choose a number: ", 0, items.size());
        if (choice == 0) {
            cancelled();
            return null;
        }
        return items.get(choice - 1);
    }

    /**
     * Like pick(), but several numbers can be chosen at once ("1,3,4"). Empty list
     * = cancelled.
     */
    protected <T> List<T> pickMany(String question, List<T> items, Function<T, String> label, String emptyMessage) {
        List<T> chosen = new ArrayList<>();
        if (items.isEmpty()) {
            info(emptyMessage);
            return chosen;
        }
        info(question);
        for (int i = 0; i < items.size(); i++) {
            System.out.printf("  [%d] %s%n", i + 1, label.apply(items.get(i)));
        }
        System.out.println("  [0] Cancel");
        while (true) {
            String raw = readLine("Choose one or more numbers separated by commas (e.g. 1,3): ");
            if (raw.equals("0") || raw.isEmpty()) {
                cancelled();
                return chosen;
            }
            chosen.clear();
            boolean ok = true;
            for (String part : raw.split("[,\\s]+")) {
                if (part.isEmpty())
                    continue;
                Integer n = ExceptionHandler.parseInt(part);
                if (n == null || n < 1 || n > items.size()) {
                    warn("'" + part + "' is not a number from 1 to " + items.size() + ".");
                    ok = false;
                    break;
                }
                if (!chosen.contains(items.get(n - 1)))
                    chosen.add(items.get(n - 1));
            }
            if (ok && !chosen.isEmpty())
                return chosen;
        }
    }

    // ----- ready-made labels so every list looks the same -----

    protected static String bookLabel(Book b) {
        return String.format("%-8s %-30s %-18s Age %-4s %d/%d available%s",
                b.getId(), cut(b.getTitle(), 30), cut(b.getAuthor(), 18), b.getAgeRatingLabel(),
                b.getAvailableCopyCount(), b.getCopies().size(), b.hasDonatedCopy() ? " (donated)" : "");
    }

    protected String titleOf(String bookId) {
        try {
            return "\"" + cut(service.getBook(bookId).getTitle(), 30) + "\"";
        } catch (LibraryException e) {
            return bookId;
        }
    }

    protected String loanLabel(Loan l) {
        LocalDate today = db.today();
        String status = l.isOverdue(today) ? "OVERDUE by " + l.getDaysOverdue(today) + " day(s)"
                : "due " + l.getDueDate();
        return String.format("%-32s borrowed by %-10s copy %-8s %s (renewed %dx)%s",
                titleOf(l.getBookId()), Records.userLabel(l.getUserId()), l.getCopyId(), status, l.getRenewCount(),
                l.isReturnRequested() ? "  [return requested " + l.getReturnRequestedOn() + "]" : "");
    }

    protected static String fineLabel(Fine f) {
        return String.format("PHP %8.2f  %-35s user %-10s %s",
                f.getAmount(), cut(f.getReason(), 35), Records.userLabel(f.getUserId()),
                f.isPaid() ? "PAID" : "UNPAID");
    }

    protected static String userLabel(User u) {
        return String.format("%-12s %-22s %-9s %s", u.getUsername(), cut(u.getFullName(), 22), u.getRole(),
                u.getStatus());
    }

    // =====================================================================
    // Features shared by every role
    // =====================================================================

    /** Search/Filter Catalog — the user picks a Strategy at runtime. */
    protected void searchCatalog() throws LibraryException {
        List<SearchStrategy> strategies = LibraryService.SEARCH_STRATEGIES;
        for (int i = 0; i < strategies.size(); i++) {
            System.out.printf("  [%d] By %s%n", i + 1, strategies.get(i).getName());
        }
        System.out.printf("  [%d] Show all books available to me%n", strategies.size() + 1);
        int choice = readInt("Filter: ", 1, strategies.size() + 1);

        List<Book> results;
        if (choice == strategies.size() + 1) {
            results = service.browseAll(user);
        } else {
            SearchStrategy chosen = strategies.get(choice - 1);
            service.setSearchStrategy(chosen); // swap strategy at runtime
            results = service.search(readLine(chosen.getPrompt()), user);
        }
        info(results.size() + " result(s):");
        bookTable(results);
    }

    /** Compatibility helper kept for older menu calls. */
    protected void showBookDetails(String bookId) throws LibraryException {
        Book book = service.getBook(bookId);
        if (!user.canAccess(book)) {
            throw new AgeRestrictionException("This book is outside your age range.");
        }
        subheader("Book Details");
        info(book.toString());
        info("Copies:");
        for (BookCopy c : book.getCopies()) {
            info("  " + c);
        }
        info("Waitlist: " + service.getQueue(book.getId()).size() + " user(s)");
    }

    protected void showNotifications() {
        List<String> notes = user.getNotifications();
        if (!notes.isEmpty()) {
            subheader("Notifications (" + notes.size() + ")");
            for (String n : notes) {
                info("* " + n);
            }
            user.clearNotifications();
        }
    }

    // =====================================================================
    // Console INPUT helpers (bad numbers never crash the program)
    // =====================================================================

    public static String readLine(String prompt) {
        System.out.print(prompt);
        return SCANNER.nextLine().trim();
    }

    /** Reads the password with the typed characters visible on screen. */
    public static String readPassword(String prompt) {
        return readLine(prompt);
    }

    public static String readRequired(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (!value.isEmpty())
                return value;
            warn("This field is required.");
        }
    }

    /** Returns null when the user presses Enter ("keep current value"). */
    public static String readOptional(String prompt) {
        String value = readLine(prompt);
        return value.isEmpty() ? null : value;
    }

    public static int readInt(String prompt, int min, int max) {
        while (true) {
            Integer value = readOptionalInt(prompt, min, max);
            if (value != null)
                return value;
            warn("A number is required.");
        }
    }

    public static Integer readOptionalInt(String prompt, int min, int max) {
        while (true) {
            String raw = readLine(prompt);
            if (raw.isEmpty())
                return null;
            Integer value = ExceptionHandler.parseInt(raw);
            if (value == null) {
                warn("'" + raw + "' is not a valid whole number.");
            } else if (value < min || value > max) {
                warn("Please enter a number from " + min + " to " + max + ".");
            } else {
                return value;
            }
        }
    }

    public static double readDouble(String prompt, double min) {
        while (true) {
            Double value = readOptionalDouble(prompt, min);
            if (value != null)
                return value;
            warn("An amount is required.");
        }
    }

    public static Double readOptionalDouble(String prompt, double min) {
        while (true) {
            String raw = readLine(prompt);
            if (raw.isEmpty())
                return null;
            Double value = ExceptionHandler.parseDouble(raw);
            if (value == null) {
                warn("'" + raw + "' is not a valid amount.");
            } else if (value < min) {
                warn("Please enter an amount of at least " + min + ".");
            } else {
                return value;
            }
        }
    }

    /**
     * Shows a numbered list of enum constants and returns the chosen one (null on
     * Enter if optional).
     */
    public static <E extends Enum<E>> E readEnum(String label, Class<E> type, boolean optional) {
        E[] values = type.getEnumConstants();
        StringBuilder sb = new StringBuilder(label).append(" [");
        for (int i = 0; i < values.length; i++) {
            sb.append(i + 1).append("=").append(values[i]).append(i < values.length - 1 ? ", " : "");
        }
        sb.append(optional ? ", Enter=keep]: " : "]: ");
        Integer choice = optional ? readOptionalInt(sb.toString(), 1, values.length)
                : Integer.valueOf(readInt(sb.toString(), 1, values.length));
        return choice == null ? null : values[choice - 1];
    }

    public static boolean confirm(String prompt) {
        while (true) {
            String raw = readLine(prompt + " (y/n): ").toLowerCase();
            if (raw.equals("y") || raw.equals("yes"))
                return true;
            if (raw.equals("n") || raw.equals("no"))
                return false;
            warn("Please answer y or n.");
        }
    }

    // =====================================================================
    // Console OUTPUT helpers
    // =====================================================================

    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++)
            sb.append(c);
        return sb.toString();
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    public static void header(String title) {
        System.out.println();
        System.out.println(repeat('=', WIDTH));
        System.out.println("  " + title);
        System.out.println(repeat('=', WIDTH));
    }

    public static void subheader(String title) {
        System.out.println();
        System.out.println("--- " + title + " " + repeat('-', Math.max(0, WIDTH - title.length() - 5)));
    }

    public static void success(String message) {
        System.out.println("  [OK] " + message);
    }

    public static void info(String message) {
        System.out.println("  " + message);
    }

    public static void warn(String message) {
        System.out.println("  [!] " + message);
    }

    public static void list(List<?> items, String emptyMessage) {
        if (items.isEmpty()) {
            info(emptyMessage);
        }
        for (Object item : items) {
            info(String.valueOf(item));
        }
    }

    public static void bookTable(List<Book> books) {
        if (books.isEmpty()) {
            info("No books found.");
            return;
        }
        System.out.printf("  %-8s %-28s %-17s %-19s %-4s %s%n", "ID", "Title", "Author", "Genre", "Age", "Available");
        System.out.println("  " + repeat('-', WIDTH - 2));
        for (Book b : books) {
            System.out.printf("  %-8s %-28s %-17s %-19s %-4s %d/%d%s%n",
                    b.getId(), cut(b.getTitle(), 28), cut(b.getAuthor(), 17), cut(b.getGenre(), 19),
                    b.getAgeRatingLabel(), b.getAvailableCopyCount(), b.getCopies().size(),
                    b.hasDonatedCopy() ? " (donated)" : "");
        }
    }

    public static void loanTable(List<Loan> loans, LocalDate today) {
        if (loans.isEmpty()) {
            info("No loans.");
            return;
        }
        System.out.printf("  %-8s %-10s %-8s %-8s %-11s %-11s %-7s %s%n",
                "Loan", "User", "Book", "Copy", "Borrowed", "Due", "Renews", "Status");
        System.out.println("  " + repeat('-', WIDTH - 2));
        for (Loan l : loans) {
            String status = l.isOverdue(today) ? "OVERDUE " + l.getDaysOverdue(today) + "d" : l.getStatus().toString();
            System.out.printf("  %-8s %-10s %-8s %-8s %-11s %-11s %-7d %s%n", l.getId(),
                    Records.userLabel(l.getUserId()), l.getBookId(),
                    l.getCopyId(), l.getBorrowDate(), l.getDueDate(), l.getRenewCount(), status);
        }
    }

    public static void fineTable(List<Fine> fines) {
        list(fines, "No fines.");
    }
}
