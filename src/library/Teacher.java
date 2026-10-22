package library;

/**
 * INHERITANCE: a Teacher IS-A User with special privileges —
 * bulk borrowing, a longer loan period and no age restriction.
 */
public class Teacher extends User {
    private static final long serialVersionUID = 1L;

    public static final int BULK_LIMIT = 20;
    public static final int LOAN_DAYS = 30;   // extended borrowing period
    public static final int MAX_RENEWALS = 2;

    private String department;

    public Teacher(String schoolId, String username, String password, String fullName, String email,
                   String department) {
        super(schoolId, username, password, fullName, email);
        setDepartment(department);
    }

    public String getDepartment() { return department; }

    public void setDepartment(String department) {
        if (department == null || department.trim().isEmpty()) {
            throw new IllegalArgumentException("Department cannot be empty.");
        }
        this.department = department.trim();
    }

    @Override public Role getRole() { return Role.TEACHER; }
    @Override public int getBorrowLimit() { return BULK_LIMIT; }
    @Override public int getLoanPeriodDays() { return LOAN_DAYS; }
    @Override public int getMaxRenewals() { return MAX_RENEWALS; }

    /** Teachers can access every book regardless of age rating. */
    @Override
    public boolean canAccess(Book book) {
        return true;
    }

    @Override
    public String getProfileDetails() {
        return String.format("Department: %s | Limit: %d books (bulk), %d days", department, BULK_LIMIT, LOAN_DAYS);
    }
}
