package library;

import library.Book.BookCondition;
import library.Book.BookCopy;
import library.ExceptionHandler.LibraryException;
import library.Records.BorrowRequest;
import library.Records.Fine;
import library.Records.Loan;
import library.Records.Reservation;
import library.User.AccountStatus;

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
        return new String[]{
                "View Dashboard",
                "Reports / Filter Metrics",
                "Book Management",
                "User Management",
                "Circulation (Issue / Return / Renew)",
                "Fines & Damage/Loss Assessment",
                "Donations",
                "Search Catalog",
                "Advance System Date (simulate days passing)"
        };
    }

    @Override
    protected void onLogin() {
        showDashboard();
    }

    @Override
    protected void handle(int choice) throws LibraryException {
        switch (choice) {
            case 1: showDashboard(); break;
            case 2: reportsMenu(); break;
            case 3: bookMenu(); break;
            case 4: userMenu(); break;
            case 5: circulationMenu(); break;
            case 6: finesMenu(); break;
            case 7: donationsMenu(); break;
            case 8: searchCatalog(); break;
            case 9: advanceDate(); break;
            default: warn("Unknown option.");
        }
    }

    private void showDashboard() {
        subheader("Library Overview");
        list(service.getDashboardMetrics(), "");
        showNotifications();
    }

    private void reportsMenu() {
        subMenu("Reports / Filter Metrics", new String[]{
                "Overdue list", "Lost books", "Damaged books", "Donated books",
                "Unpaid fines", "Pending borrow requests", "All transactions"
        }, choice -> {
            switch (choice) {
                case 1: loanTable(service.getOverdueLoans(), db.today()); break;
                case 2: list(service.getCopyReport(BookCondition.LOST), "No lost books."); break;
                case 3: list(service.getCopyReport(BookCondition.DAMAGED), "No damaged books."); break;
                case 4: list(service.getCopyReport(null), "No donated books."); break;
                case 5: fineTable(service.getAllUnpaidFines()); break;
                case 6: list(service.getPendingRequests(), "No pending requests."); break;
                case 7: list(service.getAllTransactions(), "No transactions."); break;
                default: break;
            }
        });
    }

    // =====================================================================
    // Book Management (CRUD)
    // =====================================================================

    private void bookMenu() {
        subMenu("Book Management", new String[]{
                "Add Book", "View All Books", "View Book Details", "Update Book", "Remove Book",
                "Add Copy (Copy ID + Rack)", "Update Copy (Rack / Condition)", "Remove Copy"
        }, choice -> {
            switch (choice) {
                case 1: addBook(); break;
                case 2: bookTable(service.getAllBooks()); break;
                case 3: showBookDetails(readRequired("Book ID: ")); break;
                case 4: updateBook(); break;
                case 5: removeBook(); break;
                case 6: addCopy(); break;
                case 7: updateCopy(); break;
                case 8: removeCopy(); break;
                default: break;
            }
        });
    }

    private void addBook() throws LibraryException {
        Book book = service.addBook(
                readRequired("Title: "),
                readRequired("Author: "),
                readRequired("Genre/Subject: "),
                readInt("Year: ", 1450, 9999),
                readRequired("ISBN: "),
                readInt("Age rating (0 = all ages, max 18): ", 0, 18),
                readDouble("Book value (PHP): ", 0));
        success("Added book. Generated book ID: " + book.getId());

        int copies = readInt("How many copies to add now? (0-50): ", 0, 50);
        if (copies > 0) {
            String rack = readRequired("Rack number: ");
            BookCondition condition = readEnum("Condition", BookCondition.class, false);
            for (int i = 0; i < copies; i++) {
                BookCopy c = service.addCopy(book.getId(), rack, condition, false);
                success("Copy " + c.getCopyId() + " shelved at rack " + c.getRackNumber());
            }
        }
    }

    private void updateBook() throws LibraryException {
        String id = readRequired("Book ID to update: ");
        info("Current: " + service.getBook(id));
        info("Press Enter to keep a value.");
        Book updated = service.updateBook(id,
                readOptional("New title: "),
                readOptional("New author: "),
                readOptional("New genre/subject: "),
                readOptionalInt("New year: ", 1450, 9999),
                readOptional("New ISBN: "),
                readOptionalInt("New age rating (0-18): ", 0, 18),
                readOptionalDouble("New value (PHP): ", 0));
        success("Updated: " + updated);
    }

    private void removeBook() throws LibraryException {
        Book book = service.getBook(readRequired("Book ID to remove: "));
        if (confirm("Permanently remove \"" + book.getTitle() + "\" and all its copies?")) {
            service.removeBook(book.getId());
            success("Removed " + book.getId() + ".");
        }
    }

    private void addCopy() throws LibraryException {
        BookCopy c = service.addCopy(
                readRequired("Book ID: "),
                readRequired("Rack number: "),
                readEnum("Condition", BookCondition.class, false),
                confirm("Apply Donated Tag?"));
        success("Added copy " + c);
    }

    private void updateCopy() throws LibraryException {
        String copyId = readRequired("Copy ID: ");
        info("Current: " + service.getCopy(copyId));
        BookCopy c = service.updateCopy(copyId,
                readOptional("New rack number (Enter = keep): "),
                readEnum("New condition", BookCondition.class, true));
        success("Updated: " + c);
    }

    private void removeCopy() throws LibraryException {
        String copyId = readRequired("Copy ID to remove: ");
        if (confirm("Remove copy " + copyId + "?")) {
            service.removeCopy(copyId);
            success("Copy removed.");
        }
    }

    // =====================================================================
    // User Management (CRUD)
    // =====================================================================

    private void userMenu() {
        info("Accounts are added or changed by the programmers in " + db.getAccountFileName() + ".");
        subMenu("User Management", new String[]{
                "View All Users", "View User Profile", "Update Account Status", "View User History"
        }, choice -> {
            switch (choice) {
                case 1: list(service.getAllUsers(), "No users."); break;
                case 2: viewUser(); break;
                case 3: updateStatus(); break;
                case 4: list(service.getHistory(service.getUser(readRequired("Username: "))), "No history."); break;
                default: break;
            }
        });
    }

    private void viewUser() throws LibraryException {
        User u = service.getUser(readRequired("Username: "));
        info(u.toString());
        info("Email: " + u.getEmail());
        info(u.getProfileDetails());
        info("Active loans:");
        loanTable(service.getActiveLoans(u.getId()), db.today());
        info(String.format("Unpaid fines: PHP %.2f", service.getTotalUnpaid(u.getId())));
    }

    private void updateStatus() throws LibraryException {
        User u = service.updateStatus(readRequired("Username: "), readEnum("New status", AccountStatus.class, false));
        success("Status updated: " + u);
    }

    // =====================================================================
    // Circulation
    // =====================================================================

    private void circulationMenu() {
        subMenu("Circulation", new String[]{
                "Process Pending Borrow Requests", "Issue Book Directly", "Return Book (condition check)",
                "Renew Book", "View All Active Loans", "View Reservation Queue for a Book"
        }, choice -> {
            switch (choice) {
                case 1: processRequests(); break;
                case 2: issueBook(); break;
                case 3: returnBook(); break;
                case 4: renewBook(); break;
                case 5: loanTable(service.getAllActiveLoans(), db.today()); break;
                case 6: showQueue(); break;
                default: break;
            }
        });
    }

    private void processRequests() throws LibraryException {
        List<BorrowRequest> pending = service.getPendingRequests();
        if (pending.isEmpty()) {
            info("No pending borrow requests.");
            return;
        }
        list(pending, "");
        String id = readRequired("Request ID to process: ");
        if (confirm("Approve and issue the books?")) {
            List<Loan> loans = service.approveRequest(id);
            success("Issued " + loans.size() + " book(s):");
            loanTable(loans, db.today());
        } else {
            service.rejectRequest(id, readRequired("Reason for rejection: "));
            success("Request rejected; the user has been notified.");
        }
    }

    private void issueBook() throws LibraryException {
        Loan loan = service.issueBook(readRequired("Borrower username: "), readRequired("Book ID: "));
        success("Issued copy " + loan.getCopyId() + " (" + loan.getId() + "). Due " + loan.getDueDate());
    }

    private void returnBook() throws LibraryException {
        String copyId = readRequired("Copy ID being returned: ");
        info("Condition when borrowed: " + service.getCopy(copyId).getCondition());
        List<Fine> fines = service.returnBook(copyId,
                readEnum("Condition on return (inspect the book)", BookCondition.class, false));
        success("Return processed.");
        if (fines.isEmpty()) {
            info("No fines - returned on time and in good shape.");
        } else {
            warn("Fines issued:");
            fineTable(fines);
        }
    }

    private void renewBook() throws LibraryException {
        Loan loan = service.renewBook(user, readRequired("Loan ID: "));
        success("Renewed. New due date: " + loan.getDueDate());
    }

    private void showQueue() throws LibraryException {
        Book book = service.getBook(readRequired("Book ID: "));
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
    // Fines
    // =====================================================================

    private void finesMenu() {
        subMenu("Fines & Damage/Loss Assessment", new String[]{
                "Issue Overdue Fines (daily rate)", "Issue Manual Fine",
                "Assess Loss (mark borrowed book as lost)", "View Unpaid Fines", "Record Fine Payment"
        }, choice -> {
            switch (choice) {
                case 1: issueOverdueFines(); break;
                case 2: issueManualFine(); break;
                case 3: assessLoss(); break;
                case 4: fineTable(service.getAllUnpaidFines()); break;
                case 5: recordPayment(); break;
                default: break;
            }
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
        Fine f = service.issueManualFine(
                readRequired("Username: "),
                readDouble("Amount (PHP): ", 0.01),
                readRequired("Reason: "));
        success("Issued " + f);
    }

    private void assessLoss() throws LibraryException {
        loanTable(service.getAllActiveLoans(), db.today());
        String loanId = readRequired("Loan ID of the lost book: ");
        if (confirm("Mark this book as LOST and charge the loss fee?")) {
            List<Fine> fines = service.markLost(loanId);
            success("Marked as lost. Fines issued:");
            fineTable(fines);
        }
    }

    private void recordPayment() throws LibraryException {
        User payer = service.getUser(readRequired("Username: "));
        List<Fine> unpaid = service.getUnpaidFines(payer.getId());
        if (unpaid.isEmpty()) {
            info("This user has no unpaid fines.");
            return;
        }
        fineTable(unpaid);
        double change = service.payFine(payer, readRequired("Fine ID: "), readDouble("Amount received (PHP): ", 0));
        success(String.format("Payment recorded. Change: PHP %.2f", change));
    }

    // =====================================================================
    // Donations
    // =====================================================================

    private void donationsMenu() {
        subMenu("Donations", new String[]{
                "View Pending Donations", "Approve Donation (apply Donated Tag + Copy ID)",
                "Reject Donation", "View All Donations"
        }, choice -> {
            switch (choice) {
                case 1: list(service.getPendingDonations(), "No pending donations."); break;
                case 2: approveDonation(); break;
                case 3: rejectDonation(); break;
                case 4: list(service.getAllDonations(), "No donations yet."); break;
                default: break;
            }
        });
    }

    private void approveDonation() throws LibraryException {
        if (service.getPendingDonations().isEmpty()) {
            info("No pending donations.");
            return;
        }
        list(service.getPendingDonations(), "");
        BookCopy copy = service.approveDonation(
                readRequired("Donation ID: "),
                readRequired("Rack number: "),
                readEnum("Condition", BookCondition.class, false));
        success("Donated copy added: " + copy);
    }

    private void rejectDonation() throws LibraryException {
        service.rejectDonation(readRequired("Donation ID: "), readRequired("Reason: "));
        success("Donation rejected; the donor has been notified.");
    }

    // =====================================================================
    // Simulation
    // =====================================================================

    private void advanceDate() {
        db.advanceDays(readInt("Advance the system date by how many days? (1-365): ", 1, 365));
        success("System date is now " + db.today() + ". Overdue loans: " + service.getOverdueLoans().size());
    }
}
