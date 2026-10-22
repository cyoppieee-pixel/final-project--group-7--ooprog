package library;

import library.LibraryDatabase.Identifiable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A catalog title (Title, Author, Genre, Year, ISBN, Age Rating).
 * COMPOSITION: a Book owns its physical BookCopy objects.
 * ENCAPSULATION: setters validate every field.
 */
public class Book implements Identifiable, Serializable {
    private static final long serialVersionUID = 1L;

    /** Physical condition. Declaration order matters: higher = worse. */
    public enum BookCondition {
        NEW, GOOD, FAIR, DAMAGED, LOST;

        public boolean isWorseThan(BookCondition other) {
            return this.ordinal() > other.ordinal();
        }
    }

    private final String bookId;
    private String title;
    private String author;
    private String genre;
    private int year;
    private String isbn;
    private int ageRating;      // minimum age; 0 = all ages
    private double value;       // replacement value in PHP (used for damage/loss fees)
    private final ArrayList<BookCopy> copies = new ArrayList<>();

    public Book(String bookId, String title, String author, String genre,
                int year, String isbn, int ageRating, double value) {
        this.bookId = bookId;
        setTitle(title);
        setAuthor(author);
        setGenre(genre);
        setYear(year);
        setIsbn(isbn);
        setAgeRating(ageRating);
        setValue(value);
    }

    @Override
    public String getId() { return bookId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = requireText(title, "Title"); }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = requireText(author, "Author"); }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = requireText(genre, "Genre/Subject"); }

    public int getYear() { return year; }

    public void setYear(int year) {
        int maxYear = LocalDate.now().getYear() + 1;
        if (year < 1450 || year > maxYear) {
            throw new IllegalArgumentException("Year must be between 1450 and " + maxYear + ".");
        }
        this.year = year;
    }

    public String getIsbn() { return isbn; }

    public void setIsbn(String isbn) {
        if (isbn == null) {
            throw new IllegalArgumentException("ISBN is required.");
        }
        String digits = isbn.replaceAll("[-\\s]", "").toUpperCase();
        if (!digits.matches("\\d{9}[\\dX]") && !digits.matches("\\d{13}")) {
            throw new IllegalArgumentException("ISBN must have 10 or 13 digits: " + isbn);
        }
        this.isbn = digits;
    }

    public int getAgeRating() { return ageRating; }

    public void setAgeRating(int ageRating) {
        if (ageRating < 0 || ageRating > 18) {
            throw new IllegalArgumentException("Age rating must be between 0 and 18.");
        }
        this.ageRating = ageRating;
    }

    public String getAgeRatingLabel() { return ageRating == 0 ? "All" : ageRating + "+"; }

    public double getValue() { return value; }

    public void setValue(double value) {
        if (value < 0) {
            throw new IllegalArgumentException("Book value cannot be negative.");
        }
        this.value = value;
    }

    // ----- Copy management -----

    public void addCopy(BookCopy copy) { copies.add(copy); }

    public boolean removeCopy(String copyId) {
        return copies.removeIf(c -> c.getCopyId().equalsIgnoreCase(copyId));
    }

    public List<BookCopy> getCopies() { return Collections.unmodifiableList(copies); }

    public BookCopy findCopy(String copyId) {
        for (BookCopy c : copies) {
            if (c.getCopyId().equalsIgnoreCase(copyId.trim())) return c;
        }
        return null;
    }

    public BookCopy findAvailableCopy() {
        for (BookCopy c : copies) {
            if (c.isBorrowable()) return c;
        }
        return null;
    }

    public int getAvailableCopyCount() {
        int count = 0;
        for (BookCopy c : copies) {
            if (c.isBorrowable()) count++;
        }
        return count;
    }

    public boolean isAvailable() { return getAvailableCopyCount() > 0; }

    public boolean hasDonatedCopy() {
        for (BookCopy c : copies) {
            if (c.isDonated()) return true;
        }
        return false;
    }

    public boolean hasCopyOnLoan() {
        for (BookCopy c : copies) {
            if (c.isOnLoan()) return true;
        }
        return false;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty.");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return String.format("%s - \"%s\" by %s (%d) | %s | ISBN %s | Age %s | PHP %.2f",
                bookId, title, author, year, genre, isbn, getAgeRatingLabel(), value);
    }

    // =====================================================================

    /** One physical copy (Copy ID, Rack Number, Condition, Donated Tag). */
    public static class BookCopy implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String copyId;
        private final String bookId;
        private String rackNumber;
        private BookCondition condition;
        private final boolean donated;
        private boolean onLoan;

        public BookCopy(String copyId, String bookId, String rackNumber, BookCondition condition, boolean donated) {
            this.copyId = copyId;
            this.bookId = bookId;
            setRackNumber(rackNumber);
            setCondition(condition);
            this.donated = donated;
        }

        public String getCopyId() { return copyId; }
        public String getBookId() { return bookId; }
        public String getRackNumber() { return rackNumber; }

        public void setRackNumber(String rackNumber) {
            if (rackNumber == null || rackNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Rack number cannot be empty.");
            }
            this.rackNumber = rackNumber.trim().toUpperCase();
        }

        public BookCondition getCondition() { return condition; }

        public void setCondition(BookCondition condition) {
            if (condition == null) {
                throw new IllegalArgumentException("Condition cannot be null.");
            }
            this.condition = condition;
        }

        public boolean isDonated() { return donated; }
        public boolean isOnLoan() { return onLoan; }
        public void setOnLoan(boolean onLoan) { this.onLoan = onLoan; }

        /** A copy can be lent out only if it is on the shelf and usable. */
        public boolean isBorrowable() {
            return !onLoan && condition != BookCondition.DAMAGED && condition != BookCondition.LOST;
        }

        public String getStatusLabel() {
            if (condition == BookCondition.LOST) return "LOST";
            if (onLoan) return "ON LOAN";
            if (condition == BookCondition.DAMAGED) return "UNAVAILABLE (DAMAGED)";
            return "AVAILABLE";
        }

        @Override
        public String toString() {
            return String.format("%s | Rack %s | %-7s | %-21s%s",
                    copyId, rackNumber, condition, getStatusLabel(), donated ? " | DONATED" : "");
        }
    }
}
