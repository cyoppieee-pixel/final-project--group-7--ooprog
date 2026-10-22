package library;

/**
 * INHERITANCE: the Librarian IS-A User acting as system administrator.
 * Librarians manage the library and do not borrow through the system.
 */
public class Librarian extends User {
    private static final long serialVersionUID = 1L;

    public Librarian(String schoolId, String username, String password, String fullName, String email) {
        super(schoolId, username, password, fullName, email);
    }

    @Override public Role getRole() { return Role.LIBRARIAN; }
    @Override public int getBorrowLimit() { return 0; }
    @Override public int getLoanPeriodDays() { return 0; }
    @Override public int getMaxRenewals() { return 0; }
    @Override public boolean canAccess(Book book) { return true; }

    @Override
    public String getProfileDetails() {
        return "Library Administrator | Full system access";
    }
}
