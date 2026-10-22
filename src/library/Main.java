package library;

import library.Book.BookCondition;
import library.BorrowerMenu.StudentMenu;
import library.BorrowerMenu.TeacherMenu;
import library.ExceptionHandler.LibraryException;
import library.User.Role;

// Montenegro, Navarette, Torres, Vidallon

/**
 * School Library Management System — OOPROG Finals, Group 7 (BSCS C2B).
 *
 * Accounts are read from users.txt (programmers only).
 * Run with "--reset" to ignore the save file and start from sample books.
 */

public class Main {

    private static final int MAX_LOGIN_ATTEMPTS = 3;

    public static void main(String[] args) {
        LibraryDatabase db = LibraryDatabase.getInstance();   // Singleton
        boolean reset = args.length > 0 && args[0].equalsIgnoreCase("--reset");
        boolean loaded = !reset && ExceptionHandler.loadData(db);

        LibraryService service = new LibraryService();
        if (!loaded) {
            ExceptionHandler.run(() -> seedSampleBooks(service));
        }
        ExceptionHandler.importAccounts(service, db);
        ExceptionHandler.runProgram(() -> mainMenu(service, db), db);
    }

    /** Welcome screen: choose how to log in. */
    private static void mainMenu(LibraryService service, LibraryDatabase db) {
        Role[] roles = Role.values();
        while (true) {
            Menu.header("SCHOOL LIBRARY MANAGEMENT SYSTEM");
            Menu.info("Welcome! Please choose how you want to log in.");
            System.out.println();
            for (int i = 0; i < roles.length; i++) {
                System.out.printf("  [%d] Log in as %s%n", i + 1, capitalize(roles[i].name()));
            }
            System.out.println("  [0] Exit");
            int choice = Menu.readInt("Choose an option: ", 0, roles.length);
            if (choice == 0) {
                Menu.info("Thank you for using the library system. Goodbye!");
                return;
            }
            login(service, roles[choice - 1]);
            ExceptionHandler.saveData(db);
        }
    }

    /** Username + password, up to 3 tries. Nothing here is case-sensitive. */
    private static void login(LibraryService service, Role role) {
        Menu.subheader(capitalize(role.name()) + " Log In");
        for (int attempt = 1; attempt <= MAX_LOGIN_ATTEMPTS; attempt++) {
            String username = Menu.readLine("Username: ");
            String password = Menu.readPassword("Password: ");

            User[] loggedIn = new User[1];
            if (ExceptionHandler.run(() -> loggedIn[0] = service.login(role, username, password))) {
                User user = loggedIn[0];
                Menu.success("Login successful. Welcome, " + user.getFullName() + "!");
                createMenu(user, service).run();
                return;
            }
            int left = MAX_LOGIN_ATTEMPTS - attempt;
            if (left > 0) {
                Menu.warn(left + " attempt(s) left.");
            }
        }
        Menu.warn("Too many failed attempts. Returning to the main menu.");
    }

    private static String capitalize(String word) {
        return word.charAt(0) + word.substring(1).toLowerCase();
    }

    /**
     * POLYMORPHISM: the variable type is Menu, but the object created
     * depends on the logged-in user's actual class.
     */
    private static Menu createMenu(User user, LibraryService service) {
        if (user instanceof Librarian) {
            return new LibrarianMenu((Librarian) user, service);
        } else if (user instanceof Teacher) {
            return new TeacherMenu((Teacher) user, service);
        } else if (user instanceof Student) {
            return new StudentMenu((Student) user, service);
        }
        throw new IllegalStateException("Unsupported user type: " + user.getClass().getSimpleName());
    }

    /** Sample books loaded the first time the program runs. */
    private static void seedSampleBooks(LibraryService s) throws LibraryException {
        Book b1 = s.addBook("Noli Me Tangere", "Jose Rizal", "Filipino Literature", 1887, "978-971-0-00001-1", 13, 350);
        s.addCopy(b1.getId(), "A-01", BookCondition.GOOD, false);
        s.addCopy(b1.getId(), "A-01", BookCondition.NEW, false);

        Book b2 = s.addBook("El Filibusterismo", "Jose Rizal", "Filipino Literature", 1891, "978-971-0-00002-8", 13, 350);
        s.addCopy(b2.getId(), "A-02", BookCondition.GOOD, false);

        Book b3 = s.addBook("The Very Hungry Caterpillar", "Eric Carle", "Children's Fiction", 1969, "978-0-399-22690-8", 0, 450);
        s.addCopy(b3.getId(), "C-01", BookCondition.FAIR, false);
        s.addCopy(b3.getId(), "C-01", BookCondition.GOOD, true);

        Book b4 = s.addBook("Charlotte's Web", "E. B. White", "Children's Fiction", 1952, "978-0-06-440055-8", 7, 420);
        s.addCopy(b4.getId(), "C-02", BookCondition.GOOD, false);

        Book b5 = s.addBook("The Hunger Games", "Suzanne Collins", "Young Adult Fiction", 2008, "978-0-439-02352-8", 13, 550);
        s.addCopy(b5.getId(), "Y-01", BookCondition.NEW, false);

        Book b6 = s.addBook("1984", "George Orwell", "Classic Fiction", 1949, "978-0-452-28423-4", 16, 480);
        s.addCopy(b6.getId(), "F-03", BookCondition.GOOD, false);

        Book b7 = s.addBook("General Chemistry", "Darrell Ebbing", "Science", 2016, "978-1-305-58034-3", 15, 2500);
        s.addCopy(b7.getId(), "S-04", BookCondition.NEW, false);
        s.addCopy(b7.getId(), "S-04", BookCondition.GOOD, false);
        s.addCopy(b7.getId(), "S-04", BookCondition.GOOD, false);

        Book b8 = s.addBook("A Brief History of Time", "Stephen Hawking", "Science", 1988, "978-0-553-38016-3", 12, 650);
        s.addCopy(b8.getId(), "S-01", BookCondition.GOOD, true);
    }
}
