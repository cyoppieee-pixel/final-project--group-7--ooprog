package library;

import library.Book.BookCondition;
import library.LibraryDatabase.Identifiable;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records kept by the library: loans, reservations, fines, borrow requests,
 * donation requests and the transaction history (plus their status enums).
 * Each record is ENCAPSULATED: fields are private and change only through
 * methods.
 */
public final class Records {

    private Records() {
    }

    /**
     * Records store the hidden School ID; this turns it into the username
     * for display, so School IDs never appear on screen.
     */
    public static String userLabel(String userId) {
        User u = LibraryDatabase.getInstance().getUsers().findOrNull(userId); // Singleton
        return u == null ? "(removed)" : u.getUsername();
    }

    public enum LoanStatus {
        ACTIVE, RETURNED, LOST
    }

    public enum RequestStatus {
        PENDING, APPROVED, REJECTED
    }

    public enum TransactionType {
        BORROW_REQUEST, BORROW, RETURN, RENEW, RESERVE, CANCEL_RESERVATION, FINE, PAYMENT, DONATION, LOST,
        RETURN_REQUEST
    }

    // =====================================================================
    /** A borrowing record linking a user to one physical copy. */
    public static class Loan implements Identifiable, Serializable {
        private static final long serialVersionUID = 1L;

        private final String loanId, userId, bookId, copyId;
        private final LocalDate borrowDate;
        private LocalDate dueDate;
        private LocalDate returnDate;
        private int renewCount;
        private LoanStatus status = LoanStatus.ACTIVE;
        private final BookCondition conditionAtBorrow;
        private BookCondition conditionAtReturn;
        private int finedOverdueDays; // overdue days already charged (prevents double-charging)
        private LocalDate returnRequestedOn; // set when the borrower asks to return; librarian confirms

        public Loan(String loanId, String userId, String bookId, String copyId,
                LocalDate borrowDate, LocalDate dueDate, BookCondition conditionAtBorrow) {
            this.loanId = loanId;
            this.userId = userId;
            this.bookId = bookId;
            this.copyId = copyId;
            this.borrowDate = borrowDate;
            this.dueDate = dueDate;
            this.conditionAtBorrow = conditionAtBorrow;
        }

        @Override
        public String getId() {
            return loanId;
        }

        public String getUserId() {
            return userId;
        }

        public String getBookId() {
            return bookId;
        }

        public String getCopyId() {
            return copyId;
        }

        public LocalDate getBorrowDate() {
            return borrowDate;
        }

        public LocalDate getDueDate() {
            return dueDate;
        }

        public int getRenewCount() {
            return renewCount;
        }

        public LoanStatus getStatus() {
            return status;
        }

        public BookCondition getConditionAtBorrow() {
            return conditionAtBorrow;
        }

        public BookCondition getConditionAtReturn() {
            return conditionAtReturn;
        }

        public int getFinedOverdueDays() {
            return finedOverdueDays;
        }

        public void setFinedOverdueDays(int days) {
            this.finedOverdueDays = days;
        }

        public boolean isActive() {
            return status == LoanStatus.ACTIVE;
        }

        public boolean isOverdue(LocalDate today) {
            return status == LoanStatus.ACTIVE && today.isAfter(dueDate);
        }

        public boolean isReturnRequested() {
            return returnRequestedOn != null;
        }

        public LocalDate getReturnRequestedOn() {
            return returnRequestedOn;
        }

        public void requestReturn(LocalDate date) {
            this.returnRequestedOn = date;
        }

        /**
         * Days past the due date, measured to the return date or to 'today' if still
         * out.
         */
        public int getDaysOverdue(LocalDate today) {
            LocalDate end = (returnDate != null) ? returnDate : today;
            return (int) Math.max(0, ChronoUnit.DAYS.between(dueDate, end));
        }

        public void renew(int extraDays) {
            dueDate = dueDate.plusDays(extraDays);
            renewCount++;
        }

        public void markReturned(LocalDate date, BookCondition condition) {
            returnDate = date;
            conditionAtReturn = condition;
            status = LoanStatus.RETURNED;
        }

        public void markLost(LocalDate date) {
            returnDate = date;
            conditionAtReturn = BookCondition.LOST;
            status = LoanStatus.LOST;
        }

        @Override
        public String toString() {
            return String.format("%s | User %s | Book %s | Copy %s | Borrowed %s | Due %s | Renewed %dx | %s",
                    loanId, userLabel(userId), bookId, copyId, borrowDate, dueDate, renewCount, status);
        }
    }

    // =====================================================================
    /**
     * A waitlist entry for a book with no available copies. Queue order = creation
     * order.
     */
    public static class Reservation implements Identifiable, Serializable {
        private static final long serialVersionUID = 1L;

        private final String reservationId, userId, bookId;
        private final LocalDate reservedOn;
        private boolean active = true;

        public Reservation(String reservationId, String userId, String bookId, LocalDate reservedOn) {
            this.reservationId = reservationId;
            this.userId = userId;
            this.bookId = bookId;
            this.reservedOn = reservedOn;
        }

        @Override
        public String getId() {
            return reservationId;
        }

        public String getUserId() {
            return userId;
        }

        public String getBookId() {
            return bookId;
        }

        public boolean isActive() {
            return active;
        }

        public void close() {
            active = false;
        }

        @Override
        public String toString() {
            return String.format("%s | User %s | Book %s | Reserved %s | %s",
                    reservationId, userLabel(userId), bookId, reservedOn, active ? "WAITING" : "CLOSED");
        }
    }

    // =====================================================================
    /** A financial penalty linked to a user account (and usually to a loan). */
    public static class Fine implements Identifiable, Serializable {
        private static final long serialVersionUID = 1L;

        private final String fineId, userId, loanId, reason;
        private final double amount;
        private final LocalDate issuedDate;
        private boolean paid;
        private LocalDate paidDate;

        public Fine(String fineId, String userId, String loanId, double amount, String reason, LocalDate issuedDate) {
            if (amount <= 0) {
                throw new IllegalArgumentException("Fine amount must be greater than zero.");
            }
            this.fineId = fineId;
            this.userId = userId;
            this.loanId = loanId;
            this.amount = Math.round(amount * 100.0) / 100.0;
            this.reason = reason;
            this.issuedDate = issuedDate;
        }

        @Override
        public String getId() {
            return fineId;
        }

        public String getUserId() {
            return userId;
        }

        public String getLoanId() {
            return loanId;
        }

        public double getAmount() {
            return amount;
        }

        public String getReason() {
            return reason;
        }

        public boolean isPaid() {
            return paid;
        }

        public void markPaid(LocalDate date) {
            paid = true;
            paidDate = date;
        }

        @Override
        public String toString() {
            return String.format("%s | User %s | PHP %8.2f | %s | Issued %s | %s",
                    fineId, userLabel(userId), amount, reason, issuedDate, paid ? "PAID " + paidDate : "UNPAID");
        }
    }

    // =====================================================================
    /**
     * A student's borrow request or a teacher's bulk request, waiting for the
     * librarian.
     */
    public static class BorrowRequest implements Identifiable, Serializable {
        private static final long serialVersionUID = 1L;

        private final String requestId, userId;
        private final ArrayList<String> bookIds;
        private final LocalDate requestDate;
        private RequestStatus status = RequestStatus.PENDING;

        public BorrowRequest(String requestId, String userId, List<String> bookIds, LocalDate requestDate) {
            this.requestId = requestId;
            this.userId = userId;
            this.bookIds = new ArrayList<>(bookIds);
            this.requestDate = requestDate;
        }

        @Override
        public String getId() {
            return requestId;
        }

        public String getUserId() {
            return userId;
        }

        public List<String> getBookIds() {
            return Collections.unmodifiableList(bookIds);
        }

        public RequestStatus getStatus() {
            return status;
        }

        public void setStatus(RequestStatus status) {
            this.status = status;
        }

        public boolean isPending() {
            return status == RequestStatus.PENDING;
        }

        @Override
        public String toString() {
            return String.format("%s | User %s | Books %s | Requested %s | %s",
                    requestId, userLabel(userId), bookIds, requestDate, status);
        }
    }

    // =====================================================================
    /**
     * A donated book logged by a user, waiting for the librarian to tag and shelve
     * it.
     */
    public static class DonationRequest implements Identifiable, Serializable {
        private static final long serialVersionUID = 1L;

        private final String donationId, donorId, title, author, genre, isbn;
        private final int year, ageRating;
        private final double value;
        private final LocalDate dateLogged;
        private RequestStatus status = RequestStatus.PENDING;

        public DonationRequest(String donationId, String donorId, Book details, LocalDate dateLogged) {
            this.donationId = donationId;
            this.donorId = donorId;
            this.title = details.getTitle();
            this.author = details.getAuthor();
            this.genre = details.getGenre();
            this.year = details.getYear();
            this.isbn = details.getIsbn();
            this.ageRating = details.getAgeRating();
            this.value = details.getValue();
            this.dateLogged = dateLogged;
        }

        @Override
        public String getId() {
            return donationId;
        }

        public String getDonorId() {
            return donorId;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }

        public String getGenre() {
            return genre;
        }

        public int getYear() {
            return year;
        }

        public String getIsbn() {
            return isbn;
        }

        public int getAgeRating() {
            return ageRating;
        }

        public double getValue() {
            return value;
        }

        public RequestStatus getStatus() {
            return status;
        }

        public void setStatus(RequestStatus status) {
            this.status = status;
        }

        public boolean isPending() {
            return status == RequestStatus.PENDING;
        }

        @Override
        public String toString() {
            return String.format("%s | Donor %s | \"%s\" by %s (%d) | ISBN %s | Logged %s | %s",
                    donationId, userLabel(donorId), title, author, year, isbn, dateLogged, status);
        }
    }

    // =====================================================================
    /** One entry in a user's transaction history. */
    public static class Transaction implements Identifiable, Serializable {
        private static final long serialVersionUID = 1L;

        private final String transactionId, userId, description;
        private final TransactionType type;
        private final double amount;
        private final LocalDate date;

        public Transaction(String transactionId, String userId, TransactionType type,
                String description, double amount, LocalDate date) {
            this.transactionId = transactionId;
            this.userId = userId;
            this.type = type;
            this.description = description;
            this.amount = amount;
            this.date = date;
        }

        @Override
        public String getId() {
            return transactionId;
        }

        public String getUserId() {
            return userId;
        }

        @Override
        public String toString() {
            String money = amount != 0 ? String.format(" | PHP %.2f", amount) : "";
            return String.format("%s | %s | %-10s | %-18s | %s%s", transactionId, date, userLabel(userId), type,
                    description, money);
        }
    }
}
