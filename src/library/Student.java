package library;

/**
 * INHERITANCE: a Student IS-A User with extra attributes
 * (grade level, homeroom, age) and stricter borrowing rules.
 */
public class Student extends User {
    private static final long serialVersionUID = 1L;

    public static final int MAX_BOOKS = 3;
    public static final int LOAN_DAYS = 14;   // standard two-week checkout
    public static final int MAX_RENEWALS = 1;

    private int gradeLevel;
    private String homeroom;
    private int age;

    public Student(String schoolId, String username, String password, String fullName, String email,
                   int gradeLevel, String homeroom, int age) {
        super(schoolId, username, password, fullName, email);
        setGradeLevel(gradeLevel);
        setHomeroom(homeroom);
        setAge(age);
    }

    public int getGradeLevel() { return gradeLevel; }

    public void setGradeLevel(int gradeLevel) {
        if (gradeLevel < 1 || gradeLevel > 12) {
            throw new IllegalArgumentException("Grade level must be between 1 and 12.");
        }
        this.gradeLevel = gradeLevel;
    }

    public String getHomeroom() { return homeroom; }

    public void setHomeroom(String homeroom) {
        if (homeroom == null || homeroom.trim().isEmpty()) {
            throw new IllegalArgumentException("Homeroom cannot be empty.");
        }
        this.homeroom = homeroom.trim();
    }

    public int getAge() { return age; }

    public void setAge(int age) {
        if (age < 5 || age > 25) {
            throw new IllegalArgumentException("Student age must be between 5 and 25.");
        }
        this.age = age;
    }

    @Override public Role getRole() { return Role.STUDENT; }
    @Override public int getBorrowLimit() { return MAX_BOOKS; }
    @Override public int getLoanPeriodDays() { return LOAN_DAYS; }
    @Override public int getMaxRenewals() { return MAX_RENEWALS; }

    /** Students only see/borrow books whose age rating fits their age. */
    @Override
    public boolean canAccess(Book book) {
        return age >= book.getAgeRating();
    }

    @Override
    public String getProfileDetails() {
        return String.format("Grade %d | Homeroom: %s | Age: %d | Limit: %d books, %d days",
                gradeLevel, homeroom, age, MAX_BOOKS, LOAN_DAYS);
    }
}
