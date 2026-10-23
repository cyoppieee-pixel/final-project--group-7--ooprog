package library;

import library.Book.BookCondition;
import library.Book.BookCopy;
import library.ExceptionHandler.LibraryException;
import library.Records.BorrowRequest;
import library.Records.DonationRequest;
import library.Records.Fine;
import library.Records.Loan;
import library.Records.Reservation;
import library.User.AccountStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Librarian (Admin) menu — full access to every system function.
 */
public class LibrarianMenu extends Menu {

    public LibrarianMenu(Librarian librarian, LibraryService service) {
        super(librarian, service);
    }

    @Override
    protected String getTitle() {
        return "LIBRARIAN (ADMIN)";
    }

    @Override
    protected String[] getOptions() {
        return new String[] {
                item("Dashboard", "Total books, available books, loans, fines + your To Do list"),
                category("Book Management", "Add, Update, Remove Book, Manage Copies, Donations"),
                category("Circulation", "Issue Book, Return Book, Renew Book"),
                category("User Management", "View Users, Update Status, View History"),
                category("Finances/Fines", "Issue Fines, Assess Damage/Loss, Payments"),
                category("Reports/Filters", "Lost Books, Donated Books, Overdue List, etc.")
        };
    }

    @Override
    protected void onLogin() {
        showDashboard();
    }

    @Override
    protected void handle(int choice) throws LibraryException {
        switch (choice) {
            case 1:
                showDashboard();
                break;
            case 2:
                bookMenu();
                break;
            case 3:
                circulationMenu();
                break;
            case 4:
                userMenu();
                break;
            case 5:
                finesMenu();
                break;
            case 6:
                reportsMenu();
                break;
            default:
                warn("Unknown option.");
        }
    }

    // ----- pick helpers (choose by number, never type an ID) -----

    private Book pickBook(String question) {
        return pick(question, service.getAllBooks(), Menu::bookLabel, "There are no books in the catalog yet.");
    }

    private BookCopy pickCopy(String question) {
        Book book = pickBook("Which book is the copy from?");
        if (book == null)
            return null;
        return pick(question, book.getCopies(), c -> c.toString(), "This book has no copies.");
    }

    private User pickUser(String question) {
        return pick(question, service.getAllUsers(), Menu::userLabel, "No user accounts found.");
    }

    private Loan pickLoan(String question) {
        return pick(question, service.getAllActiveLoans(), this::loanLabel, "There are no books on loan right now.");
    }

    private void showDashboard() {
        subheader("Library Overview");
        list(service.getDashboardMetrics(), "");
        showNotifications();

        // "To do" list: tells the librarian exactly where to go next
        List<String> todo = new ArrayList<>();
        int requests = service.getPendingRequests().size();
        int donations = service.getPendingDonations().size();
        int overdue = service.getOverdueLoans().size();
        int returns = service.getReturnRequests().size();
        if (requests > 0)
            todo.add(requests + " borrow request(s) waiting  ->  [3] Circulation > Issue Book > Borrow Requests");
        if (returns > 0)
            todo.add(returns + " return request(s) waiting   ->  [3] Circulation > Return Book > Return Requests");
        if (donations > 0)
            todo.add(donations + " donation(s) to review      ->  [2] Book Management > Donations");
        if (overdue > 0)
            todo.add(overdue + " overdue loan(s)             ->  [5] Finances/Fines > Issue Fines > Overdue Fines");
        subheader("To Do");
        if (todo.isEmpty())
            info("Nothing needs your attention right now.");
        for (String t : todo)
            info("* " + t);
    }

    private void reportsMenu() {
        subMenu("Reports/Filters", new String[] {
                item("Search/Filter Catalog", "Find a title, or browse by genre/author/etc."),
                item("Overdue List", "Loans that missed their due date"),
                item("Lost Books", "Copies marked lost"),
                item("Damaged Books", "Copies returned damaged"),
                item("Donated Books", "Copies with the donated tag"),
                item("Unpaid Fines", "All outstanding student/teacher fines"),
                item("All Transactions", "Full history of borrows, returns, fines and payments"),
                item("Advance System Date", "Simulate days passing to test overdue loans")
        }, choice -> {
            switch (choice) {
                case 1:
                    searchCatalog();
                    break;
                case 2:
                    loanTable(service.getOverdueLoans(), db.today());
                    break;
                case 3:
                    list(service.getCopyReport(BookCondition.LOST), "No lost books.");
                    break;
                case 4:
                    list(service.getCopyReport(BookCondition.DAMAGED), "No damaged books.");
                    break;
                case 5:
                    list(service.getCopyReport(null), "No donated books.");
                    break;
                case 6:
                    fineTable(service.getAllUnpaidFines());
                    break;
                case 7:
                    list(service.getAllTransactions(), "No transactions.");
                    break;
                case 8:
                    advanceDate();
                    break;
                default:
                    break;
            }
        });
    }

    // =====================================================================
    // Book Management (CRUD)
    // =====================================================================

    private void bookMenu() {
        subMenu("Book Management", new String[] {
                item("Add Book", "Add a new title (and optional copies)"),
                item("View All Books", "Catalog overview"),
                item("View Book Details", "Full details, copies on the shelf, and waitlist"),
                item("Update Book", "Change title, author, genre, year, value..."),
                item("Remove Book", "Delete a title and all its copies"),
                category("Manage Copies", "Add, update rack/condition, or remove one copy"),
                category("Donations", "Review, approve or reject donated books")
        }, choice -> {
            switch (choice) {
                case 1:
                    addBook();
                    break;
                case 2:
                    bookTable(service.getAllBooks());
                    break;
                case 3:
                    viewBookDetails();
                    break;
                case 4:
                    updateBook();
                    break;
                case 5:
                    removeBook();
                    break;
                case 6:
                    copiesMenu();
                    break;
                case 7:
                    donationsMenu();
                    break;
                default:
                    break;
            }
        });
    }

    private void viewBookDetails() throws LibraryException {
        Book book = pickBook("Which book do you want to inspect?");
        if (book == null)
            return;
        subheader("Book Details: " + book.getTitle());
        info(book.toString());
        info("Copies:");
        if (book.getCopies().isEmpty())
            info("  (no copies yet)");
        for (BookCopy c : book.getCopies())
            info("  " + c);
        info("Waitlist: " + service.getQueue(book.getId()).size() + " user(s)");
    }

    private void addBook() throws LibraryException {
        Book book = service.addBook(
                readRequired("Title: "),
                readRequired("Author: "),
                readRequired("Genre/Subject: "),
                readYear("Year: ", false),
                readBookIsbn("ISBN: ", null),
                readInt("Age rating (0 = all ages, max 18): ", 0, 18),
                readDouble("Book value (PHP): ", 0));
        success("Added book. Generated book ID: " + book.getId());

        int copies = readInt("How many copies to add now? (0-50): ", 0, 50);
        if (copies > 0) {
            String rack = readRequired("Rack number: ");
            BookCondition condition = readCondition("Condition", false, false);
            for (int i = 0; i < copies; i++) {
                BookCopy c = service.addCopy(book.getId(), rack, condition, false);
                success("Copy " + c.getCopyId() + " shelved at rack " + c.getRackNumber());
            }
        }
    }

    private void updateBook() throws LibraryException {
        Book book = pickBook("Which book do you want to update?");
        if (book == null)
            return;
        info("Current: " + book);
        info("Press Enter to keep a value.");
        Book updated = service.updateBook(book.getId(),
                readOptional("New title: "),
                readOptional("New author: "),
                readOptional("New genre/subject: "),
                readYear("New year: ", true),
                readBookIsbn("New ISBN: ", book),
                readOptionalInt("New age rating (0-18): ", 0, 18),
                readOptionalDouble("New value (PHP): ", 0));
        success("Updated: " + updated);
    }

    /**
     * ISBN for a catalog book, checked as soon as it is typed: format first, then
     * whether another book already uses it. current = the book being updated
     * (optional field, its own ISBN is fine), or null when adding a new book.
     */
    private String readBookIsbn(String prompt, Book current) {
        return readChecked(prompt, current != null, raw -> {
            String isbn = Book.normalizeIsbn(raw);
            Book other = service.findByIsbn(isbn);
            if (other != null && (current == null || !other.getId().equals(current.getId()))) {
                throw new IllegalArgumentException("ISBN " + isbn + " is already used by \"" + other.getTitle()
                        + "\"." + (current == null ? " Add a copy to that book instead." : ""));
            }
            return isbn;
        });
    }

    private void removeBook() throws LibraryException {
        Book book = pickBook("Which book do you want to remove permanently?");
        if (book == null)
            return;
        if (confirm("Permanently remove \"" + book.getTitle() + "\" and all its copies?")) {
            service.removeBook(book.getId());
            success("Removed " + book.getId() + ".");
        }
    }

    private void copiesMenu() {
        subMenu("Manage Copies", new String[] {
                "Add Copy (to an existing title)",
                "Update Copy (rack or condition)",
                "Remove Copy"
        }, choice -> {
            switch (choice) {
                case 1:
                    addCopy();
                    break;
                case 2:
                    updateCopy();
                    break;
                case 3:
                    removeCopy();
                    break;
                default:
                    break;
            }
        });
    }

    private void addCopy() throws LibraryException {
        Book book = pickBook("Which book are you adding a copy to?");
        if (book == null)
            return;
        BookCopy c = service.addCopy(
                book.getId(),
                readRequired("Rack number: "),
                readCondition("Condition", false, false),
                confirm("Apply Donated Tag?"));
        success("Added copy " + c);
    }

    private void updateCopy() throws LibraryException {
        BookCopy copy = pickCopy("Which copy do you want to update?");
        if (copy == null)
            return;
        info("Current: " + copy);
        String rack = readOptional("New rack number (Enter = keep): ");
        BookCondition condition = null;
        if (copy.isOnLoan()) {
            info("This copy is on loan, so its condition is recorded when it is returned.");
        } else {
            condition = readEnum("New condition", BookCondition.class, true);
        }
        BookCopy c = service.updateCopy(copy.getCopyId(), rack, condition);
        success("Updated: " + c);
    }

    private void removeCopy() throws LibraryException {
        BookCopy copy = pickCopy("Which copy do you want to remove?");
        if (copy == null)
            return;
        if (confirm("Remove copy " + copy.getCopyId() + "?")) {
            service.removeCopy(copy.getCopyId());
            success("Copy removed.");
        }
    }

    // =====================================================================
    // User Management (CRUD)
    // =====================================================================

    private void userMenu() {
        info("Accounts are added or changed by editing " + db.getAccountFileName() + ".");
        subMenu("User Management", new String[] {
                item("View All Users", "Accounts currently listed in " + db.getAccountFileName()),
                item("View User Profile", "Full profile, active loans and unpaid fines"),
                item("Update Account Status", "Suspend, reactivate or graduate an account"),
                item("View User History", "Transaction log for one user")
        }, choice -> {
            switch (choice) {
                case 1:
                    list(service.getAllUsers(), "No users.");
                    break;
                case 2:
                    viewUser();
                    break;
                case 3:
                    updateStatus();
                    break;
                case 4:
                    viewHistory();
                    break;
                default:
                    break;
            }
        });
    }

    private void viewUser() throws LibraryException {
        User u = pickUser("Which user do you want to view?");
        if (u == null)
            return;
        info(u.toString());
        info("Email: " + u.getEmail());
        info(u.getProfileDetails());
        info("Active loans:");
        loanTable(service.getActiveLoans(u.getId()), db.today());
        info(String.format("Unpaid fines: PHP %.2f", service.getTotalUnpaid(u.getId())));
    }

    private void updateStatus() throws LibraryException {
        User u = pickUser("Which user's status do you want to change?");
        if (u == null)
            return;
        info("Current status of " + u.getUsername() + " is " + u.getStatus());
        AccountStatus next = readEnum("New status", AccountStatus.class, false);
        if (next == null)
            return;
        User updated = service.updateStatus(u.getUsername(), next);
        success("Status updated: " + updated);
    }

    private void viewHistory() {
        User u = pickUser("Which user's history do you want to see?");
        if (u == null)
            return;
        list(service.getHistory(u), "No history for " + u.getUsername() + ".");
    }

    // =====================================================================
    // Circulation
    // =====================================================================

    private void circulationMenu() {
        subMenu("Circulation", new String[] {
                category("Issue Book", "Process student/teacher borrow requests, or issue directly"),
                category("Return Book", "Confirm return requests from users, or inspect & return directly"),
                item("Renew Book", "Extend an active loan"),
                item("View All Active Loans", "Everything currently checked out"),
                item("View Waitlist for a Book", "Queue of users waiting for a copy")
        }, choice -> {
            switch (choice) {
                case 1:
                    issueMenu();
                    break;
                case 2:
                    returnMenu();
                    break;
                case 3:
                    renewBook();
                    break;
                case 4:
                    loanTable(service.getAllActiveLoans(), db.today());
                    break;
                case 5:
                    showQueue();
                    break;
                default:
                    break;
            }
        });
    }

    private void issueMenu() {
        subMenu("Issue Book", new String[] {
                item("Borrow Requests", "Waiting requests submitted by students/teachers"),
                item("Issue Directly", "Hand a copy straight to a user")
        }, choice -> {
            if (choice == 1)
                processRequests();
            if (choice == 2)
                issueBookDirect();
        });
    }

    private void processRequests() throws LibraryException {
        List<BorrowRequest> pending = service.getPendingRequests();
        BorrowRequest request = pick("Which borrow request do you want to process?", pending, r -> {
            StringBuilder sb = new StringBuilder(r.toString()).append(" -> ");
            for (String bid : r.getBookIds())
                sb.append(titleOf(bid)).append(", ");
            return sb.toString().replaceAll(", $", "");
        }, "No pending borrow requests.");
        if (request == null)
            return;
        if (confirm("Approve and issue the books?")) {
            List<Loan> loans = service.approveRequest(request.getId());
            success("Issued " + loans.size() + " book(s):");
            loanTable(loans, db.today());
        } else {
            service.rejectRequest(request.getId(), readRequired("Reason for rejection: "));
            success("Request rejected; the user has been notified.");
        }
    }

    private void issueBookDirect() throws LibraryException {
        User borrower = pickUser("Who is borrowing the book?");
        if (borrower == null)
            return;
        List<Book> borrowable = new ArrayList<>();
        for (Book b : service.browseAll(borrower)) {
            if (b.isAvailable())
                borrowable.add(b);
        }
        Book book = pick("Which book are they borrowing?", borrowable, Menu::bookLabel,
                "There are no available books suitable for this user.");
        if (book == null)
            return;
        Loan loan = service.issueBook(borrower.getUsername(), book.getId());
        success("Issued copy " + loan.getCopyId() + " (" + loan.getId() + "). Due " + loan.getDueDate());
    }

    private void returnMenu() {
        subMenu("Return Book", new String[] {
                item("Return Requests", "Books handed in by students/teachers waiting for your check"),
                item("Direct Return", "Return a book that had no return request filed")
        }, choice -> {
            if (choice == 1)
                processReturnRequests();
            if (choice == 2)
                returnBookDirect();
        });
    }

    private void processReturnRequests() throws LibraryException {
        List<Loan> requests = service.getReturnRequests();
        Loan loan = pick("Which return are you confirming?", requests, this::loanLabel,
                "No return requests waiting. Users can submit one under 'Renew / Return Book > Return Book'.");
        if (loan == null)
            return;
        confirmReturn(loan);
    }

    private void returnBookDirect() throws LibraryException {
        Loan loan = pickLoan("Which borrowed book is being returned?");
        if (loan == null)
            return;
        confirmReturn(loan);
    }

    private void confirmReturn(Loan loan) throws LibraryException {
        BookCopy copy = service.getCopy(loan.getCopyId());
        info("Returning: " + titleOf(loan.getBookId()) + " (Copy " + copy.getCopyId() + ")");
        info("Borrowed by: " + Records.userLabel(loan.getUserId()));
        info("Condition when borrowed: " + loan.getConditionAtBorrow());
        BookCondition condition = readCondition("Condition on return (inspect the book now)", false, false);
        if (condition == null)
            return;
        List<Fine> fines = service.returnBook(copy.getCopyId(), condition);
        success("Return processed. Copy " + copy.getCopyId() + " is back on the shelf.");
        if (fines.isEmpty()) {
            info("No fines - returned on time and in good shape.");
        } else {
            warn("Fines issued:");
            fineTable(fines);
        }
    }

    private void renewBook() throws LibraryException {
        Loan loan = pickLoan("Which loan do you want to renew?");
        if (loan == null)
            return;
        Loan renewed = service.renewBook(user, loan.getId());
        success("Renewed. New due date: " + renewed.getDueDate());
    }

    private void showQueue() throws LibraryException {
        Book book = pickBook("Which book's waitlist do you want to inspect?");
        if (book == null)
            return;
        List<Reservation> queue = service.getQueue(book.getId());
        info("Waitlist for \"" + book.getTitle() + "\":");
        if (queue.isEmpty()) {
            info("Nobody is waiting.");
        }
        for (int i = 0; i < queue.size(); i++) {
            info("#" + (i + 1) + "  " + queue.get(i));
        }
    }

    // =====================================================================
    // Finances & Fines
    // =====================================================================

    private void finesMenu() {
        subMenu("Finances/Fines", new String[] {
                category("Issue Fines", "Overdue daily rate, manual fines"),
                item("Assess Damage/Loss", "Mark a borrowed book as lost and charge the fee"),
                item("View Unpaid Fines", "List all outstanding fines across the library"),
                item("Record Fine Payment", "Accept payment and compute change")
        }, choice -> {
            switch (choice) {
                case 1:
                    issueFinesMenu();
                    break;
                case 2:
                    assessLoss();
                    break;
                case 3:
                    fineTable(service.getAllUnpaidFines());
                    break;
                case 4:
                    recordPayment();
                    break;
                default:
                    break;
            }
        });
    }

    private void issueFinesMenu() {
        subMenu("Issue Fines", new String[] {
                item("Overdue Fines", "Charge PHP 5.00/day on all currently overdue loans"),
                item("Manual Fine", "Charge a fine to a specific user")
        }, choice -> {
            if (choice == 1)
                issueOverdueFines();
            if (choice == 2)
                issueManualFine();
        });
    }

    private void issueOverdueFines() throws LibraryException {
        if (service.getOverdueLoans().isEmpty()) {
            info("No overdue loans today.");
            return;
        }
        List<Fine> issued = service.issueOverdueFines();
        success(issued.size() + " overdue fine(s) issued.");
        fineTable(issued);
    }

    private void issueManualFine() throws LibraryException {
        User target = pickUser("Which user are you fining?");
        if (target == null)
            return;
        Fine f = service.issueManualFine(
                target.getUsername(),
                readDouble("Amount (PHP): ", 0.01),
                readRequired("Reason: "));
        success("Issued " + f);
    }

    private void assessLoss() throws LibraryException {
        Loan loan = pickLoan("Which active loan is lost?");
        if (loan == null)
            return;
        if (confirm("Mark \"" + titleOf(loan.getBookId()) + "\" as LOST and charge the replacement fee?")) {
            List<Fine> fines = service.markLost(loan.getId());
            success("Marked as lost. Fines issued:");
            fineTable(fines);
        }
    }

    private void recordPayment() throws LibraryException {
        List<User> withFines = new ArrayList<>();
        for (User u : service.getAllUsers()) {
            if (service.getTotalUnpaid(u.getId()) > 0)
                withFines.add(u);
        }
        User payer = pick("Which user is paying a fine?", withFines,
                u -> String.format("%-12s %-22s owes PHP %.2f", u.getUsername(), u.getFullName(),
                        service.getTotalUnpaid(u.getId())),
                "Nobody currently has unpaid fines.");
        if (payer == null)
            return;

        List<Fine> unpaid = service.getUnpaidFines(payer.getId());
        Fine fine = pick("Which fine are they paying?", unpaid, Menu::fineLabel, "");
        if (fine == null)
            return;
        double change = service.payFine(payer, fine.getId(), readDouble("Amount received (PHP): ", 0));
        success(String.format("Payment recorded. Change: PHP %.2f", change));
    }

    // =====================================================================
    // Donations
    // =====================================================================

    private void donationsMenu() {
        subMenu("Donations", new String[] {
                item("Review Pending Donations", "Approve (shelve) or reject books offered by users"),
                item("View All Donations", "History of all donated books")
        }, choice -> {
            switch (choice) {
                case 1:
                    reviewDonations();
                    break;
                case 2:
                    list(service.getAllDonations(), "No donations yet.");
                    break;
                default:
                    break;
            }
        });
    }

    private void reviewDonations() throws LibraryException {
        List<DonationRequest> pending = service.getPendingDonations();
        DonationRequest d = pick("Which donation do you want to review?", pending, r -> r.toString(),
                "No pending donations.");
        if (d == null)
            return;
        info("Donation details: " + d);
        if (confirm("Accept this donation and add it to the catalog?")) {
            BookCopy copy = service.approveDonation(
                    d.getId(),
                    readRequired("Rack number: "),
                    readCondition("Condition", false, false));
            success("Donated copy added: " + copy);
        } else {
            service.rejectDonation(d.getId(), readRequired("Reason for rejection: "));
            success("Donation rejected; the donor has been notified.");
        }
    }

    // =====================================================================
    // Simulation
    // =====================================================================

    private void advanceDate() {
        db.advanceDays(readInt("Advance the system date by how many days? (1-365): ", 1, 365));
        success("System date is now " + db.today() + ". Overdue loans: " + service.getOverdueLoans().size());
    }
}
