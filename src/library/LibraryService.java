package library;

import library.Book.BookCondition;
import library.Book.BookCopy;
import library.ExceptionHandler.AccountRestrictedException;
import library.ExceptionHandler.AgeRestrictionException;
import library.ExceptionHandler.AuthenticationException;
import library.ExceptionHandler.BookUnavailableException;
import library.ExceptionHandler.BorrowLimitExceededException;
import library.ExceptionHandler.BorrowingException;
import library.ExceptionHandler.DuplicateEntryException;
import library.ExceptionHandler.InvalidInputException;
import library.ExceptionHandler.LibraryException;
import library.ExceptionHandler.NotFoundException;
import library.ExceptionHandler.PaymentException;
import library.ExceptionHandler.RenewalNotAllowedException;
import library.Records.BorrowRequest;
import library.Records.DonationRequest;
import library.Records.Fine;
import library.Records.Loan;
import library.Records.RequestStatus;
import library.Records.Reservation;
import library.Records.Transaction;
import library.Records.TransactionType;
import library.User.AccountStatus;
import library.User.Role;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * All business logic of the system:
 *   Log In | Accounts file | Book Management | User Management | Search | Circulation &
 *   Reservations | Fines | Donations | Reports
 *
 * It is also the CONTEXT for both Strategy patterns (FineStrategy, SearchStrategy).
 * Methods only THROW exceptions; catching happens in ExceptionHandler.
 */
public class LibraryService {

    /** Every search filter available in the catalog (Strategy objects). */
    public static final List<SearchStrategy> SEARCH_STRATEGIES = Collections.unmodifiableList(Arrays.asList(
            new SearchStrategy.ByTitle(), new SearchStrategy.ByAuthor(), new SearchStrategy.ByGenre(),
            new SearchStrategy.ByIsbn(), new SearchStrategy.ByAgeRating(), new SearchStrategy.ByAvailability(),
            new SearchStrategy.ByDonatedTag()));

    private final LibraryDatabase db = LibraryDatabase.getInstance();   // Singleton
    private FineStrategy fineStrategy = new FineStrategy.Overdue();
    private SearchStrategy searchStrategy = SEARCH_STRATEGIES.get(0);

    private static String norm(String id) {
        return id == null ? "" : id.trim().toUpperCase();
    }

    // =====================================================================
    // LOG IN  (role -> username -> password; nothing is case-sensitive)
    // =====================================================================

    /** School IDs of the accounts currently listed in users.txt (only these can log in). */
    private final Set<String> fileAccounts = new HashSet<>();

    public User login(Role role, String username, String password) throws LibraryException {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new AuthenticationException("Please enter both your username and password.");
        }
        User user = findByUsername(username);
        // same message for every mistake, so nobody can guess which part was wrong
        if (user == null || user.getRole() != role || !user.checkPassword(password)) {
            throw new AuthenticationException("Invalid username or password.");
        }
        if (user.getStatus() == AccountStatus.GRADUATED) {
            throw new AuthenticationException("This account belongs to a graduated user and is closed.");
        }
        return user;
    }

    /** Case-insensitive username lookup among the accounts in users.txt. */
    private User findByUsername(String username) {
        String wanted = username.trim().toLowerCase();
        for (User u : db.getUsers().findAll()) {
            if (fileAccounts.contains(u.getId()) && u.getUsername().equals(wanted)) {
                return u;
            }
        }
        return null;
    }

    // =====================================================================
    // ACCOUNTS FILE IMPORT (accounts are created/changed only through users.txt)
    // =====================================================================

    public void beginAccountImport() {
        fileAccounts.clear();
    }

    /**
     * Reads one line of users.txt and creates the account, or updates it if
     * the School ID already exists (name, email, username, password ...).
     */
    public void importAccount(String line) throws LibraryException {
        String[] f = line.split("\\|", -1);
        for (int i = 0; i < f.length; i++) {
            f[i] = f[i].trim();
        }
        Role role = null;
        for (Role r : Role.values()) {
            if (r.name().equalsIgnoreCase(f[0])) role = r;
        }
        if (role == null) {
            throw new InvalidInputException("Unknown role '" + f[0] + "' (use LIBRARIAN, TEACHER or STUDENT).");
        }
        int expected = (role == Role.STUDENT) ? 9 : (role == Role.TEACHER) ? 7 : 6;
        if (f.length != expected) {
            throw new InvalidInputException(role + " lines need " + expected + " fields, found " + f.length + ".");
        }
        String id = norm(f[1]);
        String username = f[2].toLowerCase();
        if (id.isEmpty()) {
            throw new InvalidInputException("School ID is missing.");
        }
        if (fileAccounts.contains(id)) {
            throw new DuplicateEntryException("A School ID appears more than once in the file.");
        }
        User sameName = findByUsername(username);
        if (sameName != null && !sameName.getId().equals(id)) {
            throw new DuplicateEntryException("Username '" + username + "' is already used by another account.");
        }

        Integer grade = null, age = null;
        if (role == Role.STUDENT) {
            grade = ExceptionHandler.parseInt(f[6]);
            age = ExceptionHandler.parseInt(f[8]);
            if (grade == null || age == null) {
                throw new InvalidInputException("Grade level and age must be whole numbers.");
            }
        }
        final int g = grade == null ? 0 : grade, a = age == null ? 0 : age;

        User existing = db.getUsers().findOrNull(id);
        if (existing == null) {
            User created;
            switch (role) {
                case LIBRARIAN: created = ExceptionHandler.tryCreate(() -> new Librarian(id, f[2], f[3], f[4], f[5])); break;
                case TEACHER:   created = ExceptionHandler.tryCreate(() -> new Teacher(id, f[2], f[3], f[4], f[5], f[6])); break;
                default:        created = ExceptionHandler.tryCreate(() -> new Student(id, f[2], f[3], f[4], f[5], g, f[7], a)); break;
            }
            db.getUsers().add(created);
        } else {
            if (existing.getRole() != role) {
                throw new InvalidInputException("This School ID already belongs to a " + existing.getRole()
                        + " account; the role cannot be changed.");
            }
            ExceptionHandler.tryUpdate(() -> {
                existing.setUsername(f[2]);
                existing.setPassword(f[3]);
                existing.setFullName(f[4]);
                existing.setEmail(f[5]);
                if (existing instanceof Teacher) {
                    ((Teacher) existing).setDepartment(f[6]);
                } else if (existing instanceof Student) {
                    Student st = (Student) existing;   // downcast after an instanceof check
                    st.setGradeLevel(g);
                    st.setHomeroom(f[7]);
                    st.setAge(a);
                }
            });
            db.getUsers().update(existing);
        }
        fileAccounts.add(id);
    }

    // =====================================================================
    // BOOK MANAGEMENT (CRUD)
    // =====================================================================

    /** CREATE a book. */
    public Book addBook(String title, String author, String genre, int year,
                        String isbn, int ageRating, double value) throws LibraryException {
        Book book = ExceptionHandler.tryCreate(
                () -> new Book(db.nextId("BK"), title, author, genre, year, isbn, ageRating, value));
        if (findByIsbn(book.getIsbn()) != null) {
            throw new DuplicateEntryException("A book with ISBN " + book.getIsbn()
                    + " already exists. Add a copy to it instead.");
        }
        db.getBooks().add(book);
        return book;
    }

    /** CREATE a physical copy (assigns Copy ID + rack). */
    public BookCopy addCopy(String bookId, String rack, BookCondition condition, boolean donated)
            throws LibraryException {
        Book book = getBook(bookId);
        if (condition == BookCondition.LOST) {
            throw new InvalidInputException("A new copy cannot be added as LOST.");
        }
        BookCopy copy = ExceptionHandler.tryCreate(
                () -> new BookCopy(db.nextId("CP"), book.getId(), rack, condition, donated));
        book.addCopy(copy);
        return copy;
    }

    /** READ */
    public Book getBook(String bookId) throws NotFoundException {
        return db.getBooks().findById(bookId);
    }

    public List<Book> getAllBooks() {
        return db.getBooks().findAll();
    }

    public Book findByIsbn(String isbn) {
        String digits = isbn.replaceAll("[-\\s]", "").toUpperCase();
        List<Book> matches = db.getBooks().findWhere(b -> b.getIsbn().equals(digits));
        return matches.isEmpty() ? null : matches.get(0);
    }

    public Book getBookByCopyId(String copyId) throws NotFoundException {
        for (Book b : db.getBooks().findAll()) {
            if (b.findCopy(copyId) != null) return b;
        }
        throw new NotFoundException("Book copy", copyId);
    }

    public BookCopy getCopy(String copyId) throws NotFoundException {
        return getBookByCopyId(copyId).findCopy(copyId);
    }

    /** UPDATE a book. Any parameter passed as null keeps its current value. */
    public Book updateBook(String bookId, String title, String author, String genre, Integer year,
                           String isbn, Integer ageRating, Double value) throws LibraryException {
        Book book = getBook(bookId);
        if (isbn != null) {
            Book other = findByIsbn(isbn);
            if (other != null && !other.getId().equals(book.getId())) {
                throw new DuplicateEntryException("ISBN already used by " + other.getId() + ".");
            }
        }
        ExceptionHandler.tryUpdate(() -> {
            if (title != null) book.setTitle(title);
            if (author != null) book.setAuthor(author);
            if (genre != null) book.setGenre(genre);
            if (year != null) book.setYear(year);
            if (isbn != null) book.setIsbn(isbn);
            if (ageRating != null) book.setAgeRating(ageRating);
            if (value != null) book.setValue(value);
        });
        db.getBooks().update(book);
        return book;
    }

    /** UPDATE a copy's rack or condition. */
    public BookCopy updateCopy(String copyId, String rack, BookCondition condition) throws LibraryException {
        BookCopy copy = getCopy(copyId);
        if (copy.isOnLoan() && condition != null) {
            throw new InvalidInputException("The condition of a copy on loan is recorded when it is returned.");
        }
        ExceptionHandler.tryUpdate(() -> {
            if (rack != null) copy.setRackNumber(rack);
            if (condition != null) copy.setCondition(condition);
        });
        return copy;
    }

    /** DELETE a book and all its copies. */
    public Book removeBook(String bookId) throws LibraryException {
        Book book = getBook(bookId);
        if (book.hasCopyOnLoan()) {
            throw new LibraryException("Cannot remove \"" + book.getTitle() + "\": a copy is still on loan.");
        }
        for (Reservation r : getQueue(book.getId())) {
            r.close();
        }
        db.getBooks().delete(bookId);
        return book;
    }

    /** DELETE one copy. */
    public BookCopy removeCopy(String copyId) throws LibraryException {
        Book book = getBookByCopyId(copyId);
        BookCopy copy = book.findCopy(copyId);
        if (copy.isOnLoan()) {
            throw new LibraryException("Copy " + copy.getCopyId() + " is on loan and cannot be removed.");
        }
        book.removeCopy(copyId);
        return copy;
    }

    // =====================================================================
    // USER MANAGEMENT (librarian side; accounts themselves come from users.txt)
    // =====================================================================

    /** READ — finds an account by username (not case-sensitive). */
    public User getUser(String username) throws NotFoundException {
        User u = (username == null) ? null : findByUsername(username);
        if (u == null) {
            throw new NotFoundException("No account with username '" + username + "'.");
        }
        return u;
    }

    /** Internal lookup by the hidden School ID stored in records. */
    private User getUserById(String schoolId) throws NotFoundException {
        return db.getUsers().findById(schoolId);
    }

    /** Accounts currently listed in users.txt. */
    public List<User> getAllUsers() {
        return db.getUsers().findWhere(u -> fileAccounts.contains(u.getId()));
    }

    public List<Transaction> getHistory(User user) {
        return db.getTransactions().findWhere(t -> t.getUserId().equals(user.getId()));
    }

    /** UPDATE — the librarian can suspend, reactivate or graduate an account. */
    public User updateStatus(String username, AccountStatus status) throws LibraryException {
        User u = getUser(username);
        if (u instanceof Librarian && status != AccountStatus.ACTIVE) {
            throw new InvalidInputException("Librarian accounts cannot be suspended or graduated.");
        }
        u.setStatus(status);
        u.addNotification("Your account status was changed to " + status + ".");
        return u;
    }

    private void notifyUser(String userId, String message) {
        User u = db.getUsers().findOrNull(userId);
        if (u != null) {
            u.addNotification(message);
        }
    }

    // =====================================================================
    // SEARCH (Strategy pattern context)
    // =====================================================================

    public void setSearchStrategy(SearchStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Search strategy cannot be null.");
        }
        this.searchStrategy = strategy;
    }

    public List<Book> search(String criteria, User viewer) throws LibraryException {
        if (criteria == null || criteria.trim().isEmpty()) {
            throw new InvalidInputException("Search value cannot be empty.");
        }
        SearchStrategy strategy = searchStrategy;
        return ExceptionHandler.tryCreate(() -> {
            List<Book> results = new ArrayList<>();
            for (Book b : db.getBooks().findAll()) {
                // polymorphism: canAccess() hides age-restricted books from students
                if (viewer.canAccess(b) && strategy.matches(b, criteria.trim())) {
                    results.add(b);
                }
            }
            return results;
        });
    }

    public List<Book> browseAll(User viewer) {
        return db.getBooks().findWhere(viewer::canAccess);
    }

    // =====================================================================
    // FINES (Strategy pattern context)
    // =====================================================================

    public void setFineStrategy(FineStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Fine strategy cannot be null.");
        }
        this.fineStrategy = strategy;
    }

    /**
     * Calculates and records a fine with the current strategy.
     * @return the new Fine, or null if there is nothing to charge
     */
    public Fine assessFine(Loan loan, Book book) throws LibraryException {
        double amount = fineStrategy.calculate(loan, book, db.today());
        if (amount <= 0) {
            return null;
        }
        Fine fine = new Fine(db.nextId("FN"), loan.getUserId(), loan.getId(), amount,
                fineStrategy.getName() + " - \"" + book.getTitle() + "\" (" + loan.getCopyId() + ")", db.today());
        db.getFines().add(fine);
        fineStrategy.afterAssessed(loan, db.today());
        db.log(loan.getUserId(), TransactionType.FINE, fine.getReason(), fine.getAmount());
        notifyUser(loan.getUserId(), String.format("A fine of PHP %.2f was issued: %s", fine.getAmount(), fine.getReason()));
        return fine;
    }

    /** "Issue Fines": charges the daily rate on every currently overdue loan. */
    public List<Fine> issueOverdueFines() throws LibraryException {
        setFineStrategy(new FineStrategy.Overdue());
        List<Fine> issued = new ArrayList<>();
        for (Loan loan : getOverdueLoans()) {
            addIfNotNull(issued, assessFine(loan, getBook(loan.getBookId())));
        }
        return issued;
    }

    public Fine issueManualFine(String username, double amount, String reason) throws LibraryException {
        User u = getUser(username);
        if (amount <= 0) {
            throw new InvalidInputException("Fine amount must be greater than zero.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidInputException("A reason is required.");
        }
        Fine fine = new Fine(db.nextId("FN"), u.getId(), null, amount, reason.trim(), db.today());
        db.getFines().add(fine);
        db.log(u.getId(), TransactionType.FINE, fine.getReason(), fine.getAmount());
        u.addNotification(String.format("A fine of PHP %.2f was issued: %s", fine.getAmount(), fine.getReason()));
        return fine;
    }

    /** Pays one fine in full. @return the change to give back */
    public double payFine(User payer, String fineId, double amountTendered) throws LibraryException {
        Fine fine = db.getFines().findById(fineId);
        if (!fine.getUserId().equals(payer.getId())) {
            throw new PaymentException("Fine " + fine.getId() + " does not belong to " + payer.getUsername() + ".");
        }
        if (fine.isPaid()) {
            throw new PaymentException("Fine " + fine.getId() + " has already been paid.");
        }
        if (amountTendered < fine.getAmount()) {
            throw new PaymentException(String.format("Insufficient payment: PHP %.2f tendered, PHP %.2f due.",
                    amountTendered, fine.getAmount()));
        }
        fine.markPaid(db.today());
        db.log(fine.getUserId(), TransactionType.PAYMENT, "Paid " + fine.getId() + ": " + fine.getReason(), fine.getAmount());
        return Math.round((amountTendered - fine.getAmount()) * 100.0) / 100.0;
    }

    public List<Fine> getFinesForUser(String userId) {
        String id = norm(userId);
        return db.getFines().findWhere(f -> f.getUserId().equals(id));
    }

    public List<Fine> getUnpaidFines(String userId) {
        String id = norm(userId);
        return db.getFines().findWhere(f -> !f.isPaid() && f.getUserId().equals(id));
    }

    public List<Fine> getAllUnpaidFines() {
        return db.getFines().findWhere(f -> !f.isPaid());
    }

    public double getTotalUnpaid(String userId) {
        double total = 0;
        for (Fine f : getUnpaidFines(userId)) total += f.getAmount();
        return total;
    }

    private static void addIfNotNull(List<Fine> list, Fine fine) {
        if (fine != null) list.add(fine);
    }

    // =====================================================================
    // CIRCULATION — borrowing rules
    // =====================================================================

    private void checkEligibility(User user) throws AccountRestrictedException {
        if (!user.isActive()) {
            throw new AccountRestrictedException("Account is " + user.getStatus() + "; borrowing is disabled.");
        }
        if (user.getBorrowLimit() == 0) {
            throw new AccountRestrictedException(user.getRole() + " accounts cannot borrow books.");
        }
        double owed = getTotalUnpaid(user.getId());
        if (owed > 0) {
            throw new AccountRestrictedException(String.format("Unpaid fines of PHP %.2f must be settled before borrowing.", owed));
        }
    }

    private void checkAgeRating(User user, Book book) throws AgeRestrictionException {
        if (!user.canAccess(book)) {
            throw new AgeRestrictionException("\"" + book.getTitle() + "\" is rated " + book.getAgeRatingLabel()
                    + " and is outside your age range.");
        }
    }

    private void checkNotAlreadyBorrowing(User user, Book book) throws BorrowingException {
        if (!db.getLoans().findWhere(l -> l.isActive() && l.getUserId().equals(user.getId())
                && l.getBookId().equals(book.getId())).isEmpty()) {
            throw new BorrowingException("You already have a copy of \"" + book.getTitle() + "\".");
        }
    }

    private void checkNotAlreadyRequested(User user, Book book) throws BorrowingException {
        for (BorrowRequest r : getPendingRequestsFor(user.getId())) {
            if (r.getBookIds().contains(book.getId())) {
                throw new BorrowingException("\"" + book.getTitle() + "\" is already in your pending request " + r.getId() + ".");
            }
        }
    }

    /** Available copies minus the people ahead of this user in the waitlist. */
    private boolean isAvailableFor(User user, Book book) {
        int available = book.getAvailableCopyCount();
        if (available == 0) return false;
        List<Reservation> queue = getQueue(book.getId());
        int ahead = queue.size();
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getUserId().equals(user.getId())) {
                ahead = i;
                break;
            }
        }
        return available > ahead;
    }

    private void checkAvailable(User user, Book book) throws BookUnavailableException {
        if (!isAvailableFor(user, book)) {
            String reason = book.isAvailable() ? "the available copies are held for users on the waitlist"
                    : "no copies are currently available";
            throw new BookUnavailableException("\"" + book.getTitle() + "\" cannot be borrowed: " + reason
                    + ". You can reserve it instead.");
        }
    }

    // =====================================================================
    // CIRCULATION — borrow requests (Student / Teacher)
    // =====================================================================

    /** Books held + books in pending requests (both count toward the limit). */
    public int getCommittedCount(String userId) {
        int count = getActiveLoans(userId).size();
        for (BorrowRequest r : getPendingRequestsFor(userId)) count += r.getBookIds().size();
        return count;
    }

    public BorrowRequest submitBorrowRequest(User user, List<String> bookIds) throws LibraryException {
        if (bookIds == null || bookIds.isEmpty()) {
            throw new InvalidInputException("No books were selected.");
        }
        Set<String> unique = new HashSet<>();
        List<String> ids = new ArrayList<>();
        for (String raw : bookIds) {
            String id = norm(raw);
            if (!unique.add(id)) {
                throw new InvalidInputException("Book " + id + " was entered more than once.");
            }
            ids.add(id);
        }
        checkEligibility(user);
        int committed = getCommittedCount(user.getId());
        if (committed + ids.size() > user.getBorrowLimit()) {
            throw new BorrowLimitExceededException(String.format("%s limit is %d books. You hold/requested %d and asked for %d more.",
                    user.getRole(), user.getBorrowLimit(), committed, ids.size()));
        }
        for (String id : ids) {
            Book book = getBook(id);
            checkAgeRating(user, book);
            checkNotAlreadyBorrowing(user, book);
            checkNotAlreadyRequested(user, book);
            checkAvailable(user, book);
        }
        BorrowRequest request = new BorrowRequest(db.nextId("BR"), user.getId(), ids, db.today());
        db.getBorrowRequests().add(request);
        db.log(user.getId(), TransactionType.BORROW_REQUEST, "Requested " + ids.size() + " book(s): " + ids, 0);
        return request;
    }

    public List<BorrowRequest> getPendingRequests() {
        return db.getBorrowRequests().findWhere(BorrowRequest::isPending);
    }

    public List<BorrowRequest> getPendingRequestsFor(String userId) {
        String id = norm(userId);
        return db.getBorrowRequests().findWhere(r -> r.isPending() && r.getUserId().equals(id));
    }

    /** Librarian approves a request: all books are issued, or none (validated first). */
    public List<Loan> approveRequest(String requestId) throws LibraryException {
        BorrowRequest request = db.getBorrowRequests().findById(requestId);
        if (!request.isPending()) {
            throw new InvalidInputException("Request " + request.getId() + " was already " + request.getStatus() + ".");
        }
        User user = getUserById(request.getUserId());
        checkEligibility(user);
        if (getActiveLoans(user.getId()).size() + request.getBookIds().size() > user.getBorrowLimit()) {
            throw new BorrowLimitExceededException("Approving would exceed " + user.getId() + "'s limit of "
                    + user.getBorrowLimit() + " books.");
        }
        List<Book> books = new ArrayList<>();
        for (String id : request.getBookIds()) {
            Book book = getBook(id);
            checkAgeRating(user, book);
            checkNotAlreadyBorrowing(user, book);
            checkAvailable(user, book);
            books.add(book);
        }
        List<Loan> loans = new ArrayList<>();
        for (Book book : books) {
            loans.add(createLoan(user, book));
        }
        request.setStatus(RequestStatus.APPROVED);
        user.addNotification("Your borrow request " + request.getId() + " was approved. Due date: "
                + loans.get(0).getDueDate() + ".");
        return loans;
    }

    public BorrowRequest rejectRequest(String requestId, String reason) throws LibraryException {
        BorrowRequest request = db.getBorrowRequests().findById(requestId);
        if (!request.isPending()) {
            throw new InvalidInputException("Request " + request.getId() + " was already " + request.getStatus() + ".");
        }
        request.setStatus(RequestStatus.REJECTED);
        notifyUser(request.getUserId(), "Your borrow request " + request.getId() + " was rejected: " + reason);
        return request;
    }

    // =====================================================================
    // CIRCULATION — issue, return, loss, renewal (Librarian)
    // =====================================================================

    public Loan issueBook(String username, String bookId) throws LibraryException {
        User user = getUser(username);
        Book book = getBook(bookId);
        checkEligibility(user);
        if (getCommittedCount(user.getId()) + 1 > user.getBorrowLimit()) {
            throw new BorrowLimitExceededException(user.getId() + " has reached the " + user.getRole()
                    + " limit of " + user.getBorrowLimit() + " books (including pending requests).");
        }
        checkAgeRating(user, book);
        checkNotAlreadyBorrowing(user, book);
        checkAvailable(user, book);
        return createLoan(user, book);
    }

    private Loan createLoan(User user, Book book) throws LibraryException {
        BookCopy copy = book.findAvailableCopy();
        copy.setOnLoan(true);
        LocalDate today = db.today();
        Loan loan = new Loan(db.nextId("LN"), user.getId(), book.getId(), copy.getCopyId(),
                today, today.plusDays(user.getLoanPeriodDays()), copy.getCondition());
        db.getLoans().add(loan);
        for (Reservation r : getQueue(book.getId())) {   // borrowing fulfils the user's reservation
            if (r.getUserId().equals(user.getId())) r.close();
        }
        db.log(user.getId(), TransactionType.BORROW, "Borrowed \"" + book.getTitle() + "\" (" + copy.getCopyId()
                + "), due " + loan.getDueDate(), 0);
        return loan;
    }

    /** Processes a return, records the condition and applies overdue/damage fines. */
    public List<Fine> returnBook(String copyId, BookCondition returnCondition) throws LibraryException {
        if (returnCondition == BookCondition.LOST) {
            throw new InvalidInputException("A returned book cannot be LOST. Use 'Assess Loss' instead.");
        }
        List<Loan> matches = db.getLoans().findWhere(l -> l.isActive() && l.getCopyId().equals(norm(copyId)));
        if (matches.isEmpty()) {
            throw new NotFoundException("No active loan found for copy '" + copyId + "'.");
        }
        Loan loan = matches.get(0);
        Book book = getBook(loan.getBookId());
        BookCopy copy = book.findCopy(loan.getCopyId());

        loan.markReturned(db.today(), returnCondition);
        copy.setCondition(returnCondition);
        copy.setOnLoan(false);
        db.log(loan.getUserId(), TransactionType.RETURN, "Returned \"" + book.getTitle() + "\" ("
                + copy.getCopyId() + ") in " + returnCondition + " condition", 0);

        // STRATEGY PATTERN in action: same method, different formulas
        List<Fine> fines = new ArrayList<>();
        setFineStrategy(new FineStrategy.Overdue());
        addIfNotNull(fines, assessFine(loan, book));
        setFineStrategy(new FineStrategy.Damage());
        addIfNotNull(fines, assessFine(loan, book));

        notifyWaitlist(book);
        return fines;
    }

    /** "Assess Damage/Losses" for a borrowed book that will never come back. */
    public List<Fine> markLost(String loanId) throws LibraryException {
        Loan loan = db.getLoans().findById(loanId);
        if (!loan.isActive()) {
            throw new InvalidInputException("Loan " + loan.getId() + " is already " + loan.getStatus() + ".");
        }
        Book book = getBook(loan.getBookId());
        BookCopy copy = book.findCopy(loan.getCopyId());
        loan.markLost(db.today());
        copy.setCondition(BookCondition.LOST);
        copy.setOnLoan(false);
        db.log(loan.getUserId(), TransactionType.LOST, "Reported lost: \"" + book.getTitle() + "\" (" + copy.getCopyId() + ")", 0);

        List<Fine> fines = new ArrayList<>();
        setFineStrategy(new FineStrategy.Overdue());
        addIfNotNull(fines, assessFine(loan, book));
        setFineStrategy(new FineStrategy.Loss());
        addIfNotNull(fines, assessFine(loan, book));
        return fines;
    }

    public Loan renewBook(User requester, String loanId) throws LibraryException {
        Loan loan = db.getLoans().findById(loanId);
        if (!loan.isActive()) {
            throw new RenewalNotAllowedException("Loan " + loan.getId() + " is not active.");
        }
        if (requester.getRole() != Role.LIBRARIAN && !loan.getUserId().equals(requester.getId())) {
            throw new RenewalNotAllowedException("Loan " + loan.getId() + " does not belong to you.");
        }
        User borrower = getUserById(loan.getUserId());
        if (!borrower.isActive()) {
            throw new AccountRestrictedException("Account " + borrower.getId() + " is " + borrower.getStatus() + ".");
        }
        if (loan.isOverdue(db.today())) {
            throw new RenewalNotAllowedException("Overdue books cannot be renewed. Please return it and settle the fine.");
        }
        if (loan.getRenewCount() >= borrower.getMaxRenewals()) {
            throw new RenewalNotAllowedException("Renewal limit reached (" + borrower.getMaxRenewals()
                    + " for a " + borrower.getRole() + ").");
        }
        if (!getQueue(loan.getBookId()).isEmpty()) {
            throw new RenewalNotAllowedException("Other users have reserved this book, so it cannot be renewed.");
        }
        loan.renew(borrower.getLoanPeriodDays());
        db.log(borrower.getId(), TransactionType.RENEW, "Renewed loan " + loan.getId() + ", new due date " + loan.getDueDate(), 0);
        return loan;
    }

    public List<Loan> getActiveLoans(String userId) {
        String id = norm(userId);
        return db.getLoans().findWhere(l -> l.isActive() && l.getUserId().equals(id));
    }

    public List<Loan> getAllActiveLoans() {
        return db.getLoans().findWhere(Loan::isActive);
    }

    public List<Loan> getOverdueLoans() {
        LocalDate today = db.today();
        return db.getLoans().findWhere(l -> l.isOverdue(today));
    }

    // =====================================================================
    // RESERVATIONS (waitlist + queue)
    // =====================================================================

    public Reservation reserveBook(User user, String bookId) throws LibraryException {
        if (!user.isActive()) {
            throw new AccountRestrictedException("Account is " + user.getStatus() + "; reservations are disabled.");
        }
        if (user.getBorrowLimit() == 0) {
            throw new AccountRestrictedException(user.getRole() + " accounts cannot reserve books.");
        }
        Book book = getBook(bookId);
        checkAgeRating(user, book);
        checkNotAlreadyBorrowing(user, book);
        for (Reservation r : getQueue(book.getId())) {
            if (r.getUserId().equals(user.getId())) {
                throw new DuplicateEntryException("You already reserved \"" + book.getTitle() + "\" (" + r.getId() + ").");
            }
        }
        if (isAvailableFor(user, book)) {
            throw new BorrowingException("\"" + book.getTitle() + "\" is available right now. Submit a borrow request instead.");
        }
        Reservation r = new Reservation(db.nextId("RS"), user.getId(), book.getId(), db.today());
        db.getReservations().add(r);
        db.log(user.getId(), TransactionType.RESERVE, "Reserved \"" + book.getTitle() + "\" - queue position "
                + getQueue(book.getId()).size(), 0);
        return r;
    }

    public Reservation cancelReservation(User user, String reservationId) throws LibraryException {
        Reservation r = db.getReservations().findById(reservationId);
        if (!r.isActive()) {
            throw new InvalidInputException("Reservation " + r.getId() + " is already closed.");
        }
        if (user.getRole() != Role.LIBRARIAN && !r.getUserId().equals(user.getId())) {
            throw new InvalidInputException("Reservation " + r.getId() + " does not belong to you.");
        }
        r.close();
        db.log(r.getUserId(), TransactionType.CANCEL_RESERVATION, "Cancelled reservation " + r.getId(), 0);
        return r;
    }

    /** Active reservations for a book, first come first served. */
    public List<Reservation> getQueue(String bookId) {
        String id = norm(bookId);
        return db.getReservations().findWhere(r -> r.isActive() && r.getBookId().equals(id));
    }

    public List<Reservation> getUserReservations(String userId) {
        String id = norm(userId);
        return db.getReservations().findWhere(r -> r.isActive() && r.getUserId().equals(id));
    }

    public int getQueuePosition(Reservation reservation) {
        List<Reservation> queue = getQueue(reservation.getBookId());
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId().equals(reservation.getId())) return i + 1;
        }
        return -1;
    }

    /** Notifies the next people in line when copies become available. */
    private void notifyWaitlist(Book book) {
        List<Reservation> queue = getQueue(book.getId());
        int available = book.getAvailableCopyCount();
        for (int i = 0; i < queue.size() && i < available; i++) {
            notifyUser(queue.get(i).getUserId(), "Good news! \"" + book.getTitle() + "\" (" + book.getId()
                    + ") you reserved is now available. Submit a borrow request.");
        }
    }

    // =====================================================================
    // DONATIONS
    // =====================================================================

    public DonationRequest submitDonation(User donor, String title, String author, String genre,
                                          int year, String isbn, int ageRating, double value) throws LibraryException {
        if (!donor.isActive()) {
            throw new InvalidInputException("Only active accounts can log donations.");
        }
        // reuse Book's validation rules without saving anything
        Book details = ExceptionHandler.tryCreate(() -> new Book("TEMP", title, author, genre, year, isbn, ageRating, value));
        DonationRequest d = new DonationRequest(db.nextId("DN"), donor.getId(), details, db.today());
        db.getDonations().add(d);
        db.log(donor.getId(), TransactionType.DONATION, "Logged donation " + d.getId() + ": \"" + d.getTitle()
                + "\" (awaiting librarian)", 0);
        return d;
    }

    public List<DonationRequest> getPendingDonations() {
        return db.getDonations().findWhere(DonationRequest::isPending);
    }

    public List<DonationRequest> getAllDonations() {
        return db.getDonations().findAll();
    }

    /** Applies the Donated Tag and assigns a Copy ID (adds to the existing title if the ISBN exists). */
    public BookCopy approveDonation(String donationId, String rack, BookCondition condition) throws LibraryException {
        DonationRequest d = db.getDonations().findById(donationId);
        if (!d.isPending()) {
            throw new InvalidInputException("Donation " + d.getId() + " was already " + d.getStatus() + ".");
        }
        Book book = findByIsbn(d.getIsbn());
        if (book == null) {
            book = addBook(d.getTitle(), d.getAuthor(), d.getGenre(), d.getYear(), d.getIsbn(), d.getAgeRating(), d.getValue());
        }
        BookCopy copy = addCopy(book.getId(), rack, condition, true);
        d.setStatus(RequestStatus.APPROVED);
        db.log(d.getDonorId(), TransactionType.DONATION, "Donation " + d.getId() + " accepted as " + copy.getCopyId(), 0);
        notifyUser(d.getDonorId(), "Thank you! Your donated book \"" + d.getTitle() + "\" is now in the catalog as "
                + copy.getCopyId() + ".");
        notifyWaitlist(book);
        return copy;
    }

    public DonationRequest rejectDonation(String donationId, String reason) throws LibraryException {
        DonationRequest d = db.getDonations().findById(donationId);
        if (!d.isPending()) {
            throw new InvalidInputException("Donation " + d.getId() + " was already " + d.getStatus() + ".");
        }
        d.setStatus(RequestStatus.REJECTED);
        notifyUser(d.getDonorId(), "Your donation \"" + d.getTitle() + "\" was not accepted: " + reason);
        return d;
    }

    // =====================================================================
    // REPORTS (librarian dashboard + filter metrics)
    // =====================================================================

    public List<String> getDashboardMetrics() {
        int titles = 0, copies = 0, available = 0, onLoan = 0, damaged = 0, lost = 0, donated = 0;
        for (Book b : db.getBooks().findAll()) {
            titles++;
            for (BookCopy c : b.getCopies()) {
                copies++;
                if (c.isBorrowable()) available++;
                if (c.isOnLoan()) onLoan++;
                if (c.getCondition() == BookCondition.DAMAGED) damaged++;
                if (c.getCondition() == BookCondition.LOST) lost++;
                if (c.isDonated()) donated++;
            }
        }
        int students = 0, teachers = 0, inactive = 0;
        for (User u : db.getUsers().findAll()) {
            if (u.getRole() == Role.STUDENT) students++;
            if (u.getRole() == Role.TEACHER) teachers++;
            if (!u.isActive()) inactive++;
        }
        double unpaid = 0;
        for (Fine f : getAllUnpaidFines()) unpaid += f.getAmount();

        List<String> lines = new ArrayList<>();
        lines.add("System date        : " + db.today());
        lines.add(String.format("Catalog            : %d titles, %d copies", titles, copies));
        lines.add(String.format("Copies             : %d available | %d on loan | %d damaged | %d lost | %d donated",
                available, onLoan, damaged, lost, donated));
        lines.add(String.format("Users              : %d students | %d teachers | %d not active", students, teachers, inactive));
        lines.add(String.format("Active loans       : %d (%d overdue)", getAllActiveLoans().size(), getOverdueLoans().size()));
        lines.add(String.format("Pending requests   : %d borrow | %d donations",
                getPendingRequests().size(), getPendingDonations().size()));
        lines.add(String.format("Unpaid fines       : PHP %.2f", unpaid));
        return lines;
    }

    /** Copies filtered by condition (LOST / DAMAGED reports) or by donated tag (condition = null). */
    public List<String> getCopyReport(BookCondition condition) {
        List<String> lines = new ArrayList<>();
        for (Book b : db.getBooks().findAll()) {
            for (BookCopy c : b.getCopies()) {
                boolean match = (condition == null) ? c.isDonated() : c.getCondition() == condition;
                if (match) {
                    lines.add(c.getCopyId() + " | " + b.getTitle() + " | Rack " + c.getRackNumber()
                            + " | " + c.getCondition() + " | " + c.getStatusLabel());
                }
            }
        }
        return lines;
    }

    public List<Transaction> getAllTransactions() {
        return db.getTransactions().findAll();
    }
}
