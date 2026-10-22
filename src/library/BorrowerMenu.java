package library;

import library.ExceptionHandler.InvalidInputException;
import library.ExceptionHandler.LibraryException;
import library.Records.BorrowRequest;
import library.Records.DonationRequest;
import library.Records.Fine;
import library.Records.Loan;
import library.Records.Reservation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * INHERITANCE: shared menu for every user who borrows books.
 * StudentMenu and TeacherMenu (nested below) extend it and only override
 * what differs: the title and how a borrow request is made (POLYMORPHISM).
 */
public abstract class BorrowerMenu extends Menu {

    protected BorrowerMenu(User user, LibraryService service) {
        super(user, service);
    }

    /** Label for option 4, e.g. "Borrow Book" or "Bulk Borrow Request". */
    protected abstract String getBorrowLabel();

    /** Role-specific borrow flow. */
    protected abstract void requestBorrow() throws LibraryException;

    @Override
    protected String[] getOptions() {
        return new String[]{
                "View Dashboard",
                "Search/Filter Catalog",
                "View Book Details",
                getBorrowLabel(),
                "Reserve Book",
                "Renew Book",
                "Donate Book",
                "My Reservations",
                "Borrowing History",
                "My Fines / Pay Fine"
        };
    }

    @Override
    protected void onLogin() {
        if (!user.isActive()) {
            warn("Your account is " + user.getStatus() + ". Borrowing is disabled; please see the librarian.");
        }
        showDashboard();
    }

    @Override
    protected void handle(int choice) throws LibraryException {
        switch (choice) {
            case 1: showDashboard(); break;
            case 2: searchCatalog(); break;
            case 3: showBookDetails(readRequired("Book ID: ")); break;
            case 4: requestBorrow(); break;
            case 5: reserveBook(); break;
            case 6: renewBook(); break;
            case 7: donateBook(); break;
            case 8: manageReservations(); break;
            case 9: list(service.getHistory(user), "No transactions yet."); break;
            case 10: manageFines(); break;
            default: warn("Unknown option.");
        }
    }

    protected void showDashboard() {
        subheader("Dashboard");
        info(user.toString());
        info(user.getProfileDetails());   // polymorphic call
        showNotifications();

        List<Loan> loans = service.getActiveLoans(user.getId());
        subheader("Active Borrowings (" + loans.size() + "/" + user.getBorrowLimit() + ")");
        loanTable(loans, db.today());

        List<BorrowRequest> pending = service.getPendingRequestsFor(user.getId());
        if (!pending.isEmpty()) {
            subheader("Pending Borrow Requests");
            list(pending, "");
        }

        List<Reservation> reservations = service.getUserReservations(user.getId());
        subheader("Pending Reservations");
        if (reservations.isEmpty()) {
            info("None.");
        }
        for (Reservation r : reservations) {
            info(r.getId() + " | Book " + r.getBookId() + " | Queue position #" + service.getQueuePosition(r));
        }

        double owed = service.getTotalUnpaid(user.getId());
        subheader("Account Fines");
        info(owed > 0 ? String.format("Unpaid: PHP %.2f (borrowing blocked until paid)", owed) : "No unpaid fines.");
    }

    protected void reserveBook() throws LibraryException {
        Reservation r = service.reserveBook(user, readRequired("Book ID to reserve: "));
        success("Reserved! " + r.getId() + " - you are #" + service.getQueuePosition(r)
                + " in line. You will be notified when a copy is returned.");
    }

    protected void renewBook() throws LibraryException {
        List<Loan> loans = service.getActiveLoans(user.getId());
        if (loans.isEmpty()) {
            info("You have no borrowed books.");
            return;
        }
        loanTable(loans, db.today());
        Loan loan = service.renewBook(user, readRequired("Loan ID to renew: "));
        success("Renewed. New due date: " + loan.getDueDate());
    }

    protected void donateBook() throws LibraryException {
        subheader("Donate a Book");
        DonationRequest d = service.submitDonation(user,
                readRequired("Title: "),
                readRequired("Author: "),
                readRequired("Genre/Subject: "),
                readInt("Year published: ", 1450, 9999),
                readRequired("ISBN: "),
                readInt("Suggested age rating (0 = all ages, max 18): ", 0, 18),
                readDouble("Estimated value (PHP): ", 0));
        success("Donation " + d.getId() + " logged. The librarian has been alerted to tag and shelve it.");
    }

    protected void manageReservations() throws LibraryException {
        List<Reservation> reservations = service.getUserReservations(user.getId());
        if (reservations.isEmpty()) {
            info("You have no active reservations.");
            return;
        }
        for (Reservation r : reservations) {
            info(r + " | #" + service.getQueuePosition(r) + " in line");
        }
        if (confirm("Cancel a reservation?")) {
            Reservation r = service.cancelReservation(user, readRequired("Reservation ID: "));
            success("Reservation " + r.getId() + " cancelled.");
        }
    }

    protected void manageFines() throws LibraryException {
        subheader("My Fines");
        fineTable(service.getFinesForUser(user.getId()));
        List<Fine> unpaid = service.getUnpaidFines(user.getId());
        if (unpaid.isEmpty()) {
            return;
        }
        info(String.format("Total unpaid: PHP %.2f", service.getTotalUnpaid(user.getId())));
        if (confirm("Pay a fine now?")) {
            double change = service.payFine(user, readRequired("Fine ID: "), readDouble("Amount to pay (PHP): ", 0));
            success(String.format("Payment recorded. Change: PHP %.2f", change));
        }
    }

    protected void printRequest(BorrowRequest request) throws LibraryException {
        success("Borrow request " + request.getId() + " submitted. Waiting for the librarian to issue:");
        for (String id : request.getBookIds()) {
            Book b = service.getBook(id);
            info("  - " + b.getId() + " \"" + b.getTitle() + "\"");
        }
    }

    // =====================================================================
    /** Student: borrows one available book per request (max 3 held), age-filtered catalog. */
    public static class StudentMenu extends BorrowerMenu {

        public StudentMenu(Student student, LibraryService service) {
            super(student, service);
        }

        @Override
        protected String getTitle() { return "STUDENT PORTAL"; }

        @Override
        protected String getBorrowLabel() { return "Borrow Book"; }

        @Override
        protected void requestBorrow() throws LibraryException {
            info("You hold/requested " + service.getCommittedCount(user.getId()) + " of " + user.getBorrowLimit() + " books.");
            String bookId = readRequired("Book ID to borrow: ");
            printRequest(service.submitBorrowRequest(user, Collections.singletonList(bookId)));
        }
    }

    // =====================================================================
    /** Teacher: bulk borrow request for several books at once. */
    public static class TeacherMenu extends BorrowerMenu {

        public TeacherMenu(Teacher teacher, LibraryService service) {
            super(teacher, service);
        }

        @Override
        protected String getTitle() { return "TEACHER PORTAL"; }

        @Override
        protected String getBorrowLabel() { return "Bulk Borrow Request"; }

        @Override
        protected void requestBorrow() throws LibraryException {
            info("You hold/requested " + service.getCommittedCount(user.getId()) + " of " + user.getBorrowLimit() + " books.");
            String raw = readRequired("Book IDs separated by commas (e.g. BK-0001, BK-0007): ");
            List<String> ids = new ArrayList<>();
            for (String part : raw.split(",")) {
                if (!part.trim().isEmpty()) ids.add(part.trim());
            }
            if (ids.isEmpty()) {
                throw new InvalidInputException("No valid book IDs were entered.");
            }
            printRequest(service.submitBorrowRequest(user, ids));
        }
    }
}
