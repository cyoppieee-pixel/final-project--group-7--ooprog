package library;

import library.ExceptionHandler.DuplicateEntryException;
import library.ExceptionHandler.LibraryException;
import library.ExceptionHandler.NotFoundException;
import library.Records.BorrowRequest;
import library.Records.DonationRequest;
import library.Records.Fine;
import library.Records.Loan;
import library.Records.Reservation;
import library.Records.Transaction;
import library.Records.TransactionType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;

/**
 * SINGLETON PATTERN
 * -----------------
 * There must be exactly ONE library database shared by every part of the
 * program; otherwise a book borrowed in one place would still look
 * available somewhere else.
 *
 *  - the constructor is private, so no other class can call "new LibraryDatabase()"
 *  - the only instance is kept in a private static field
 *  - getInstance() creates it on first use (lazy initialization) and always
 *    returns that same object; it is synchronized so it is thread-safe.
 *
 * This file also holds the generic CRUD Repository used for storage.
 */
public final class LibraryDatabase {

    private static final String DATA_FILE = "library_data.dat";
    private static LibraryDatabase instance;

    private DataStore store = new DataStore();

    private LibraryDatabase() {
    }

    public static synchronized LibraryDatabase getInstance() {
        if (instance == null) {
            instance = new LibraryDatabase();
        }
        return instance;
    }

    // =====================================================================
    // CRUD storage (ABSTRACTION: services use the Repository interface,
    // not the HashMap behind it)
    // =====================================================================

    /** Anything stored in a Repository must expose a unique ID. */
    public interface Identifiable {
        String getId();
    }

    /** Generic CRUD contract: Create, Read, Update, Delete. */
    public interface Repository<T extends Identifiable> {
        void add(T item) throws DuplicateEntryException;              // CREATE
        T findById(String id) throws NotFoundException;               // READ
        T findOrNull(String id);                                      // READ (no exception)
        List<T> findAll();                                            // READ
        List<T> findWhere(Predicate<T> condition);                    // READ (filtered)
        void update(T item) throws NotFoundException;                 // UPDATE
        void delete(String id) throws NotFoundException;              // DELETE
        boolean exists(String id);
    }

    /** Concrete storage backed by a LinkedHashMap (keeps insertion order). IDs are case-insensitive. */
    public static class InMemoryRepository<T extends Identifiable> implements Repository<T>, Serializable {
        private static final long serialVersionUID = 1L;

        private final String entityName;
        private final LinkedHashMap<String, T> storage = new LinkedHashMap<>();

        public InMemoryRepository(String entityName) {
            this.entityName = entityName;
        }

        private static String key(String id) {
            return id == null ? "" : id.trim().toUpperCase();
        }

        @Override
        public void add(T item) throws DuplicateEntryException {
            if (storage.containsKey(key(item.getId()))) {
                throw new DuplicateEntryException(entityName + " with ID '" + item.getId() + "' already exists.");
            }
            storage.put(key(item.getId()), item);
        }

        @Override
        public T findById(String id) throws NotFoundException {
            T item = storage.get(key(id));
            if (item == null) {
                throw new NotFoundException(entityName, id);
            }
            return item;
        }

        @Override
        public T findOrNull(String id) {
            return storage.get(key(id));
        }

        @Override
        public List<T> findAll() {
            return new ArrayList<>(storage.values());
        }

        @Override
        public List<T> findWhere(Predicate<T> condition) {
            List<T> result = new ArrayList<>();
            for (T item : storage.values()) {
                if (condition.test(item)) result.add(item);
            }
            return result;
        }

        @Override
        public void update(T item) throws NotFoundException {
            if (!storage.containsKey(key(item.getId()))) {
                throw new NotFoundException(entityName, item.getId());
            }
            storage.put(key(item.getId()), item);
        }

        @Override
        public void delete(String id) throws NotFoundException {
            if (storage.remove(key(id)) == null) {
                throw new NotFoundException(entityName, id);
            }
        }

        @Override
        public boolean exists(String id) {
            return storage.containsKey(key(id));
        }
    }

    /** Everything that gets saved to disk, bundled in one serializable object. */
    private static class DataStore implements Serializable {
        private static final long serialVersionUID = 1L;

        private final InMemoryRepository<Book> books = new InMemoryRepository<>("Book");
        private final InMemoryRepository<User> users = new InMemoryRepository<>("User");
        private final InMemoryRepository<Loan> loans = new InMemoryRepository<>("Loan");
        private final InMemoryRepository<Reservation> reservations = new InMemoryRepository<>("Reservation");
        private final InMemoryRepository<Fine> fines = new InMemoryRepository<>("Fine");
        private final InMemoryRepository<BorrowRequest> borrowRequests = new InMemoryRepository<>("Borrow request");
        private final InMemoryRepository<DonationRequest> donations = new InMemoryRepository<>("Donation");
        private final InMemoryRepository<Transaction> transactions = new InMemoryRepository<>("Transaction");
        private final HashMap<String, Integer> idCounters = new HashMap<>();
        private int dayOffset = 0;
    }

    public Repository<Book> getBooks() { return store.books; }
    public Repository<User> getUsers() { return store.users; }
    public Repository<Loan> getLoans() { return store.loans; }
    public Repository<Reservation> getReservations() { return store.reservations; }
    public Repository<Fine> getFines() { return store.fines; }
    public Repository<BorrowRequest> getBorrowRequests() { return store.borrowRequests; }
    public Repository<DonationRequest> getDonations() { return store.donations; }
    public Repository<Transaction> getTransactions() { return store.transactions; }

    // ----- ID generation (letters + numbers, e.g. BK-0001) -----

    public synchronized String nextId(String prefix) {
        int next = store.idCounters.getOrDefault(prefix, 0) + 1;
        store.idCounters.put(prefix, next);
        return String.format("%s-%04d", prefix, next);
    }

    // ----- Simulated system clock (can be advanced to test overdue books) -----

    public LocalDate today() {
        return LocalDate.now().plusDays(store.dayOffset);
    }

    public void advanceDays(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("Days to advance must be at least 1.");
        }
        store.dayOffset += days;
    }

    // ----- Transaction history -----

    public void log(String userId, TransactionType type, String description, double amount) throws LibraryException {
        store.transactions.add(new Transaction(nextId("TX"), userId, type, description, amount, today()));
    }

    // ----- Persistence: errors are passed up to ExceptionHandler -----

    public void save() throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            out.writeObject(store);
        }
    }

    /** @return true if saved data was found and loaded, false if there is no save file */
    public boolean load() throws IOException, ClassNotFoundException {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            return false;
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            Object data = in.readObject();
            if (!(data instanceof DataStore)) {
                throw new IOException("Save file is corrupted or from another program.");
            }
            store = (DataStore) data;
            return true;
        }
    }

    public void reset() {
        store = new DataStore();
    }

    public String getDataFileName() {
        return DATA_FILE;
    }

    // =====================================================================
    // ACCOUNTS FILE (programmers only)
    // School IDs, usernames and passwords live in users.txt. The program
    // only READS this file — accounts are added or changed by editing it,
    // never from inside the program, and School IDs are never displayed.
    // =====================================================================

    private static final String ACCOUNTS_FILE = "users.txt";

    private static final String DEFAULT_ACCOUNTS =
            "# ==========================================================================\n"
          + "# SCHOOL LIBRARY MANAGEMENT SYSTEM - USER ACCOUNTS (programmers only)\n"
          + "# --------------------------------------------------------------------------\n"
          + "# Add, change or remove accounts here, then restart the program.\n"
          + "# School IDs are never shown in the program; they only identify the record.\n"
          + "# Usernames and passwords are NOT case-sensitive when logging in.\n"
          + "# Removing a line disables that account (its history is kept).\n"
          + "#\n"
          + "# Format (fields separated by | ):\n"
          + "#   LIBRARIAN | School ID | Username | Password | Full Name | Email\n"
          + "#   TEACHER   | School ID | Username | Password | Full Name | Email | Department\n"
          + "#   STUDENT   | School ID | Username | Password | Full Name | Email | Grade (1-12) | Homeroom | Age\n"
          + "# ==========================================================================\n"
          + "\n"
          + "LIBRARIAN | LIB-001 | admin  | admin123   | Maria Santos   | msantos@school.edu.ph\n"
          + "\n"
          + "TEACHER   | T-1001  | jreyes | teacher123 | Jose Reyes     | jreyes@school.edu.ph | Science\n"
          + "TEACHER   | T-1002  | acruz  | teacher123 | Ana Cruz       | acruz@school.edu.ph  | English\n"
          + "\n"
          + "STUDENT   | S-2001  | juan   | student123 | Juan Dela Cruz | jdelacruz@student.school.edu.ph | 5  | 5-Sampaguita | 10\n"
          + "STUDENT   | S-2002  | bea    | student123 | Bea Garcia     | bgarcia@student.school.edu.ph   | 11 | 11-STEM A    | 16\n"
          + "STUDENT   | S-2003  | paolo  | student123 | Paolo Lim      | plim@student.school.edu.ph      | 8  | 8-Rizal      | 13\n";

    /** Reads users.txt, creating it with the sample accounts if it does not exist yet. */
    public List<String> readAccountFile() throws IOException {
        Path path = Paths.get(ACCOUNTS_FILE);
        if (!Files.exists(path)) {
            Files.write(path, DEFAULT_ACCOUNTS.getBytes(StandardCharsets.UTF_8));
        }
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    public String getAccountFileName() {
        return ACCOUNTS_FILE;
    }
}
