package library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import library.ExceptionHandler.LibraryException;
import library.Records.BorrowRequest;
import library.Records.DonationRequest;
import library.Records.Fine;
import library.Records.Loan;
import library.Records.Reservation;

/**
 * INHERITANCE: shared menu for every user who borrows books.
 * StudentMenu and TeacherMenu (nested below) extend it and only override
 * what differs: the title and how a borrow request is made (POLYMORPHISM).
 */
public abstract class BorrowerMenu extends Menu {

    protected BorrowerMenu(User user, LibraryService service) {
        super(user, service);
    }

    /** Label for the borrow option, e.g. "Borrow Book" or "Bulk Borrow Request". */
    protected abstract String getBorrowLabel();

    /** Role-specific borrow flow. */
    protected abstract void requestBorrow() throws LibraryException;

    @Override
    protected String[] getOptions() {
        return new String[]{
                category("Dashboard", "Borrowed books, account fines, history"),
                item("Search/Filter Catalog", "Find a book, or show all books available to you"),
                item(getBorrowLabel(), "Pick from books on the shelf"),
                category("Reserve Book", "Join a waitlist, see or cancel your reservations"),
                category("Renew / Return Book", "Extend a due date, or return a book"),
                item("Donate Book", "Give a book to the library")
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
            case 1:
                subMenu("Dashboard", new String[]{"My Fines / Pay Fine", "Borrowing History"}, c -> {
                    if (c == 1) manageFines();
                    if (c == 2) list(service.getHistory(user), "No transactions yet.");
                }, this::showDashboard);
                break;
            case 2: searchCatalog(); break;
            case 3: requestBorrow(); break;
            case 4:
                subMenu("Reserve Book", new String[]{"Reserve a Book", "My Reservations (see / cancel)"}, c -> {
                    if (c == 1) reserveBook();
                    if (c == 2) manageReservations();
                });
                break;
            case 5:
                subMenu("Renew / Return Book", new String[]{"Renew Book", "Return Book"}, c -> {
                    if (c == 1) renewBook();
                    if (c == 2) returnBook();
                });
                break;
            case 6: donateBook(); break;
            default: warn("Unknown option.");
        }
    }

    /** Books with a free copy right now (what can be borrowed). */
    protected List<Book> borrowableBooks() {
        List<Book> result = new ArrayList<>();
        for (Book b : service.browseAll(user)) {
            if (b.isAvailable()) result.add(b);
        }
        return result;
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
        // Only books with NO available copies can be reserved (otherwise borrow directly)
        List<Book> reservable = new ArrayList<>();
        for (Book b : service.browseAll(user)) {
            if (!b.isAvailable()) reservable.add(b);
        }
        Book book = pick("Which book would you like to reserve?", reservable, Menu::bookLabel,
                "All books in your catalog currently have copies on the shelf! You can borrow them directly.");
        if (book == null) return;
        Reservation r = service.reserveBook(user, book.getId());
        success("Reserved! " + r.getId() + " - you are #" + service.getQueuePosition(r)
                + " in line. You will be notified when a copy is returned.");
    }

    protected void renewBook() throws LibraryException {
        List<Loan> loans = service.getActiveLoans(user.getId());
        Loan picked = pick("Which loan do you want to renew?", loans, this::loanLabel, "You have no borrowed books.");
        if (picked == null) return;
        Loan loan = service.renewBook(user, picked.getId());
        success("Renewed. New due date: " + loan.getDueDate());
    }

    protected void returnBook() throws LibraryException {
        List<Loan> returnable = new ArrayList<>();
        for (Loan l : service.getActiveLoans(user.getId())) {
            if (!l.isReturnRequested()) returnable.add(l);
        }
        if (returnable.isEmpty()) {
            info(service.getActiveLoans(user.getId()).isEmpty()
                    ? (service.getPendingRequestsFor(user.getId()).isEmpty()
                        ? "You have no borrowed books."
                        : "You have no borrowed books yet - your borrow request is still waiting for the librarian.")
                    : "You already asked to return all your books. Please hand them to the librarian.");
            return;
        }
        Loan loan = pick("Which book are you returning?", returnable, this::loanLabel, "");
        if (loan == null) return;
        service.requestReturn(user, loan.getId());
        success("Return request sent for " + titleOf(loan.getBookId()) + ".");
        info("Next step: hand the book to the librarian. They'll check its condition and confirm the return.");
        info("Until then it still counts as borrowed (and late fees still apply after the due date).");
    }

    protected void donateBook() throws LibraryException {
        info("Fill in the book's details. The librarian will check and shelve it.");
        DonationRequest d = service.submitDonation(user,
                readRequired("Title: "),
                readRequired("Author: "),
                readRequired("Genre/Subject: "),
                readYear("Year published: ", false),
                readChecked("ISBN: ", false, Book::normalizeIsbn),
                readInt("Suggested age rating (0 = all ages, max 18): ", 0, 18),
                readDouble("Estimated value (PHP): ", 0));
        success("Donation " + d.getId() + " logged. The librarian has been alerted to tag and shelve it.");
    }

    protected void manageReservations() throws LibraryException {
        List<Reservation> reservations = service.getUserReservations(user.getId());
        Reservation r = pick("Which reservation do you want to cancel?", reservations,
                res -> res + " | #" + service.getQueuePosition(res) + " in line", "You have no active reservations.");
        if (r == null) return;
        if (confirm("Cancel this reservation?")) {
            service.cancelReservation(user, r.getId());
            success("Reservation " + r.getId() + " cancelled.");
        }
    }

    protected void manageFines() throws LibraryException {
        subheader("My Fines");
        fineTable(service.getFinesForUser(user.getId()));
        List<Fine> unpaid = service.getUnpaidFines(user.getId());
        if (unpaid.isEmpty()) return;
        info(String.format("Total unpaid: PHP %.2f", service.getTotalUnpaid(user.getId())));
        Fine chosen = pick("Which fine do you want to pay?", unpaid, Menu::fineLabel, "");
        if (chosen == null) return;
        double change = service.payFine(user, chosen.getId(), readDouble("Amount to pay (PHP): ", 0));
        success(String.format("Payment recorded. Change: PHP %.2f", change));
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
            Book chosen = pick("Which book do you want to borrow?", borrowableBooks(), Menu::bookLabel,
                    "There are no available books right now. Try reserving one instead!");
            if (chosen == null) return;
            printRequest(service.submitBorrowRequest(user, Collections.singletonList(chosen.getId())));
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
            List<Book> chosen = pickMany("Which books do you want to borrow?", borrowableBooks(), Menu::bookLabel,
                    "There are no available books right now.");
            if (chosen.isEmpty()) return;
            List<String> ids = new ArrayList<>();
            for (Book b : chosen) ids.add(b.getId());
            printRequest(service.submitBorrowRequest(user, ids));
        }
    }
}