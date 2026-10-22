package library;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * EXCEPTION HANDLING — everything in ONE file.
 *
 * Part 1: the custom exception hierarchy (nested classes).
 *
 *   LibraryException
 *    ├── NotFoundException
 *    ├── DuplicateEntryException
 *    ├── InvalidInputException
 *    ├── AuthenticationException
 *    ├── PaymentException
 *    └── BorrowingException
 *         ├── BorrowLimitExceededException
 *         ├── AgeRestrictionException
 *         ├── BookUnavailableException
 *         ├── AccountRestrictedException
 *         └── RenewalNotAllowedException
 *
 * Part 2: every try/catch in the program. The other classes only THROW
 * exceptions; the catching is done here by these static helper methods.
 */
public final class ExceptionHandler {

    private ExceptionHandler() {
    }

    // =====================================================================
    // PART 1 — Custom exceptions
    // =====================================================================

    /** Base checked exception for every business-rule violation. */
    public static class LibraryException extends Exception {
        private static final long serialVersionUID = 1L;
        public LibraryException(String message) { super(message); }
        public LibraryException(String message, Throwable cause) { super(message, cause); }
    }

    /** A book, copy, user, loan, fine, etc. does not exist. */
    public static class NotFoundException extends LibraryException {
        private static final long serialVersionUID = 1L;
        public NotFoundException(String entity, String id) { super(entity + " with ID '" + id + "' was not found."); }
        public NotFoundException(String message) { super(message); }
    }

    /** A record with the same ID/ISBN already exists. */
    public static class DuplicateEntryException extends LibraryException {
        private static final long serialVersionUID = 1L;
        public DuplicateEntryException(String message) { super(message); }
    }

    /** User-supplied data failed validation. */
    public static class InvalidInputException extends LibraryException {
        private static final long serialVersionUID = 1L;
        public InvalidInputException(String message) { super(message); }
        public InvalidInputException(String message, Throwable cause) { super(message, cause); }
    }

    /** A School ID cannot log in. */
    public static class AuthenticationException extends LibraryException {
        private static final long serialVersionUID = 1L;
        public AuthenticationException(String message) { super(message); }
    }

    /** Invalid fine payment (wrong account, already paid, not enough money). */
    public static class PaymentException extends LibraryException {
        private static final long serialVersionUID = 1L;
        public PaymentException(String message) { super(message); }
    }

    /** Parent of all circulation-rule violations. */
    public static class BorrowingException extends LibraryException {
        private static final long serialVersionUID = 1L;
        public BorrowingException(String message) { super(message); }
    }

    /** Student: max 3 books. Teacher: bulk limit. */
    public static class BorrowLimitExceededException extends BorrowingException {
        private static final long serialVersionUID = 1L;
        public BorrowLimitExceededException(String message) { super(message); }
    }

    /** A student tried to access a book above their age range. */
    public static class AgeRestrictionException extends BorrowingException {
        private static final long serialVersionUID = 1L;
        public AgeRestrictionException(String message) { super(message); }
    }

    /** No copy can be lent out (all borrowed, damaged, lost or held for the waitlist). */
    public static class BookUnavailableException extends BorrowingException {
        private static final long serialVersionUID = 1L;
        public BookUnavailableException(String message) { super(message); }
    }

    /** Suspended/graduated account, or unpaid fines. */
    public static class AccountRestrictedException extends BorrowingException {
        private static final long serialVersionUID = 1L;
        public AccountRestrictedException(String message) { super(message); }
    }

    /** Loan is overdue, reserved by others, or out of renewals. */
    public static class RenewalNotAllowedException extends BorrowingException {
        private static final long serialVersionUID = 1L;
        public RenewalNotAllowedException(String message) { super(message); }
    }

    // =====================================================================
    // PART 2 — Catching
    // =====================================================================

    /** An action that may throw a checked LibraryException (used with lambdas). */
    @FunctionalInterface
    public interface Action {
        void run() throws LibraryException;
    }

    /**
     * Runs one menu action. Any error is shown to the user and the program
     * keeps going. The most specific exception types are caught first.
     *
     * @return true if the action finished without errors
     */
    public static boolean run(Action action) {
        try {
            action.run();
            return true;
        } catch (BorrowingException e) {
            printError("Borrowing rule - " + e.getClass().getSimpleName(), e);
        } catch (LibraryException e) {
            printError(e.getClass().getSimpleName(), e);
        } catch (IllegalArgumentException | IllegalStateException e) {   // multi-catch
            printError(e.getClass().getSimpleName(), e);
        }
        return false;
    }

    private static void printError(String label, Exception e) {
        System.out.println("  [ERROR - " + label + "] " + e.getMessage());
    }

    /**
     * Creates an object whose constructor validates its input. An
     * IllegalArgumentException is converted into a checked
     * InvalidInputException, keeping the original as its cause (exception chaining).
     */
    public static <T> T tryCreate(Supplier<T> creator) throws InvalidInputException {
        try {
            return creator.get();
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(e.getMessage(), e);
        }
    }

    /** Same as tryCreate, for code that changes an object through its validating setters. */
    public static void tryUpdate(Runnable change) throws InvalidInputException {
        try {
            change.run();
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(e.getMessage(), e);
        }
    }

    /** @return the number, or null if the text is not a whole number */
    public static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** @return the number, or null if the text is not a valid amount */
    public static Double parseDouble(String text) {
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Loads the save file. @return true if saved data was loaded */
    public static boolean loadData(LibraryDatabase db) {
        try {
            return db.load();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Could not read saved data (" + e.getMessage() + "). Starting with sample data.");
            db.reset();
            return false;
        }
    }

    /**
     * Loads every account from users.txt. A bad line is reported (with its
     * line number) and skipped; the rest of the file still loads.
     */
    public static void importAccounts(LibraryService service, LibraryDatabase db) {
        List<String> lines;
        try {
            lines = db.readAccountFile();
        } catch (IOException e) {
            System.out.println("Warning: could not read " + db.getAccountFileName() + " (" + e.getMessage()
                    + "). Nobody can log in until the file is fixed.");
            return;
        }
        service.beginAccountImport();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                service.importAccount(line);
            } catch (LibraryException e) {
                System.out.println("  [ACCOUNT FILE] " + db.getAccountFileName() + " line " + (i + 1) + " skipped: "
                        + e.getMessage());
            }
        }
    }

    public static void saveData(LibraryDatabase db) {
        try {
            db.save();
        } catch (IOException e) {
            System.out.println("Warning: could not save data to " + db.getDataFileName() + ": " + e.getMessage());
        }
    }

    /** Runs the whole program; 'finally' guarantees the data is saved even if input ends unexpectedly. */
    public static void runProgram(Runnable program, LibraryDatabase db) {
        try {
            program.run();
        } catch (NoSuchElementException e) {
            System.out.println();
            System.out.println("Input closed. Exiting.");
        } finally {
            saveData(db);
        }
    }
}
