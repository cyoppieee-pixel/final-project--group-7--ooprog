package library;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import library.LibraryDatabase.Identifiable;

/**
 * ABSTRACTION: User is abstract — you can never create a plain "User",
 * only a Student, Teacher or Librarian.
 *
 * ENCAPSULATION: all fields are private. The School ID is only used
 * internally as the record key and is never shown on screen; the password
 * has no getter at all — it can only be checked with checkPassword().
 *
 * INHERITANCE / POLYMORPHISM: each subclass overrides the abstract methods
 * (borrow limit, loan period, age access ...) so the rest of the system
 * treats every user the same way and still gets role-specific behaviour.
 *
 * Accounts are created from the users.txt file (see LibraryDatabase).
 */

public abstract class User implements Identifiable, Serializable {
    private static final long serialVersionUID = 2L;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,30}$");

    public enum Role { LIBRARIAN, TEACHER, STUDENT }

    public enum AccountStatus { ACTIVE, SUSPENDED, GRADUATED }

    private final String schoolId;      // hidden: internal key only
    private String username;            // stored in lowercase (login is not case-sensitive)
    private String password;            // compared ignoring case
    private String fullName;
    private String email;
    private AccountStatus status;
    private final ArrayList<String> notifications = new ArrayList<>();

    protected User(String schoolId, String username, String password, String fullName, String email) {
        if (schoolId == null || schoolId.trim().isEmpty()) {
            throw new IllegalArgumentException("School ID is required.");
        }
        this.schoolId = schoolId.trim().toUpperCase();
        setUsername(username);
        setPassword(password);
        setFullName(fullName);
        setEmail(email);
        this.status = AccountStatus.ACTIVE;
    }

    /** Internal record key (the hidden School ID). Never printed. */
    @Override
    public String getId() { return schoolId; }

    public String getUsername() { return username; }

    public void setUsername(String username) {
        String clean = username == null ? "" : username.trim().toLowerCase();
        if (!USERNAME_PATTERN.matcher(clean).matches()) {
            throw new IllegalArgumentException("Username must be 3-30 letters, numbers, dots, dashes or underscores.");
        }
        this.username = clean;
    }

    public void setPassword(String password) {
        if (password == null || password.trim().length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters.");
        }
        this.password = password.trim();
    }

    /** Login check — NOT case-sensitive. */
    public boolean checkPassword(String attempt) {
        return attempt != null && password.equalsIgnoreCase(attempt.trim());
    }

    public String getFullName() { return fullName; }

    public void setFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be empty.");
        }
        this.fullName = fullName.trim();
    }

    public String getEmail() { return email; }

    public void setEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Invalid email address: " + email);
        }
        this.email = email.trim();
    }

    public AccountStatus getStatus() { return status; }

    public void setStatus(AccountStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null.");
        }
        this.status = status;
    }

    public boolean isActive() { return status == AccountStatus.ACTIVE; }

    public void addNotification(String message) { notifications.add(message); }

    public List<String> getNotifications() { return Collections.unmodifiableList(notifications); }

    public void clearNotifications() { notifications.clear(); }

    // ----- Abstract behaviour each role must define (polymorphism) -----

    public abstract Role getRole();

    /** Maximum number of books this user may hold at once. */
    public abstract int getBorrowLimit();

    /** Number of days a loan lasts (also the renewal extension). */
    public abstract int getLoanPeriodDays();

    /** Maximum number of times a single loan may be renewed. */
    public abstract int getMaxRenewals();

    /** Age-rating validation: may this user see/borrow the book? */
    public abstract boolean canAccess(Book book);

    /** Role-specific profile line shown on dashboards. */
    public abstract String getProfileDetails();

    @Override
    public String toString() {
        return String.format("%s | %s (%s) - %s", username, fullName, getRole(), status);
    }
}
