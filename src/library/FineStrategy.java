package library;

import library.Book.BookCondition;
import library.Records.Loan;

import java.time.LocalDate;

/**
 * STRATEGY PATTERN — fine calculation.
 *
 * Each kind of penalty (overdue, damage, loss) is its own class with its
 * own formula. LibraryService (the "context") holds one FineStrategy and
 * calls calculate() without knowing which formula it is running, so a new
 * fine type can be added without changing LibraryService.
 */
public interface FineStrategy {

    /** Short label used as the fine's reason, e.g. "Overdue fine". */
    String getName();

    /** Returns the amount to charge in PHP (0 means no fine). */
    double calculate(Loan loan, Book book, LocalDate today);

    /** Hook called after a fine from this strategy is recorded. Default: nothing. */
    default void afterAssessed(Loan loan, LocalDate today) {
    }

    // =====================================================================
    /** Overdue fine = (overdue days not yet charged) x daily rate. */
    class Overdue implements FineStrategy {
        public static final double DAILY_RATE = 5.00;

        @Override
        public String getName() { return "Overdue fine"; }

        @Override
        public double calculate(Loan loan, Book book, LocalDate today) {
            int unchargedDays = loan.getDaysOverdue(today) - loan.getFinedOverdueDays();
            return Math.max(0, unchargedDays) * DAILY_RATE;
        }

        /** Remember how many days were charged so the same day is never fined twice. */
        @Override
        public void afterAssessed(Loan loan, LocalDate today) {
            loan.setFinedOverdueDays(loan.getDaysOverdue(today));
        }
    }

    // =====================================================================
    /**
     * Damage fee based on the book's value and its condition on return:
     *  - returned DAMAGED                       -> 50% of book value
     *  - condition dropped 2+ levels (NEW->FAIR) -> 20% of book value
     *  - normal wear                            -> no fee
     */
    class Damage implements FineStrategy {
        public static final double DAMAGED_RATE = 0.50;
        public static final double WEAR_RATE = 0.20;

        @Override
        public String getName() { return "Damage fee"; }

        @Override
        public double calculate(Loan loan, Book book, LocalDate today) {
            BookCondition before = loan.getConditionAtBorrow();
            BookCondition after = loan.getConditionAtReturn();
            if (after == null || !after.isWorseThan(before)) {
                return 0;
            }
            if (after == BookCondition.DAMAGED) {
                return book.getValue() * DAMAGED_RATE;
            }
            return (after.ordinal() - before.ordinal() >= 2) ? book.getValue() * WEAR_RATE : 0;
        }
    }

    // =====================================================================
    /** Loss fee = full book value + a fixed processing fee. */
    class Loss implements FineStrategy {
        public static final double PROCESSING_FEE = 100.00;

        @Override
        public String getName() { return "Loss fee"; }

        @Override
        public double calculate(Loan loan, Book book, LocalDate today) {
            return book.getValue() + PROCESSING_FEE;
        }
    }
}
