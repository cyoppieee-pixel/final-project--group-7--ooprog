# School Library Management System
OOPROG Finals – Group 7 (BSCS C2B) · Console-based Java program (Java 8 or newer) · 15 source files

## How to run

From the `LibrarySystem` folder:

```
javac -d out src/library/*.java
java -cp out library.Main
```

Add `--reset` to start over with the sample data: `java -cp out library.Main --reset`

**NetBeans / IntelliJ / VS Code:** create a Java project, copy the `src/library` folder into its source folder, and run `library.Main`.

Data is saved automatically to `library_data.dat` when you log out or exit.

## Logging in

1. Choose **Log in as Librarian / Teacher / Student**.
2. Enter your **username** and **password**. Neither is case-sensitive (`JUAN` / `Student123` works).
3. After 3 wrong tries you're sent back to the main menu. The error never says whether the username or the password was wrong.
4. The password is visible while typing, in both real terminals and IDE consoles like NetBeans.

## Accounts file: `users.txt` (programmers only)

All accounts live in `users.txt` next to the program. The program **only reads** this file; there is no screen for adding or editing accounts, and **School IDs never appear anywhere in the program**. Records are linked by the hidden School ID, and screens show the username instead.

```
LIBRARIAN | School ID | Username | Password | Full Name | Email
TEACHER   | School ID | Username | Password | Full Name | Email | Department
STUDENT   | School ID | Username | Password | Full Name | Email | Grade | Homeroom | Age
```

- **Add** an account by adding a line. **Change** a username, password, name, etc. by editing its line; the School ID keeps its loans and fines attached. **Remove** a line to disable that account (its history is kept).
- Restart the program after editing. A bad line (wrong number of fields, duplicate username, invalid email, ...) is skipped with a message showing its line number.
- If `users.txt` is missing, it is recreated with the sample accounts below.

| Role | Username | Password |
|---|---|---|
| Librarian | admin | admin123 |
| Teacher | jreyes, acruz | teacher123 |
| Student (age 10) | juan | student123 |
| Student (age 16) | bea | student123 |
| Student (age 13) | paolo | student123 |

The librarian can still suspend, reactivate or graduate accounts (**User Management → Update Account Status**). That's library policy, so it's kept in the program instead of in the file.

Demo tip: log in as the librarian and use **Advance System Date** to make loans overdue, then try **Issue Overdue Fines**, renewals and returns.

## The 15 files

| File | What's inside |
|---|---|
| `Main.java` | Program start, role-based login (3 attempts), sample books |
| `ExceptionHandler.java` | **All** custom exceptions + **all** try/catch blocks |
| `LibraryDatabase.java` | **Singleton** database + generic CRUD `Repository` + reads `users.txt` |
| `LibraryService.java` | All business logic; context for both strategies |
| `FineStrategy.java` | **Strategy**: `Overdue`, `Damage`, `Loss` |
| `SearchStrategy.java` | **Strategy**: `ByTitle`, `ByAuthor`, `ByGenre`, `ByIsbn`, `ByAgeRating`, `ByAvailability`, `ByDonatedTag` |
| `User.java` | Abstract base class: hidden School ID, username, password (+ `Role`, `AccountStatus` enums) |
| `Student.java`, `Teacher.java`, `Librarian.java` | Subclasses of `User` |
| `Book.java` | Book + nested `BookCopy` + `BookCondition` enum |
| `Records.java` | `Loan`, `Reservation`, `Fine`, `BorrowRequest`, `DonationRequest`, `Transaction` |
| `Menu.java` | Abstract menu + console input/output helpers |
| `BorrowerMenu.java` | Shared borrower menu + nested `StudentMenu`, `TeacherMenu` |
| `LibrarianMenu.java` | Admin menu |

## Where each requirement is implemented

### CRUD
| Entity | Create | Read | Update | Delete |
|---|---|---|---|---|
| Books / copies | `addBook`, `addCopy` | `getBook`, `getAllBooks`, `search` | `updateBook`, `updateCopy` | `removeBook`, `removeCopy` |
| Users | `importAccount` (from `users.txt`) | `getUser`, `getAllUsers`, `getHistory` | `importAccount` (edited line), `updateStatus` | remove the line from `users.txt` |
| Circulation | `submitBorrowRequest`, `issueBook`, `reserveBook` | `getActiveLoans`, `getQueue` | `returnBook`, `renewBook`, `markLost` | `cancelReservation` |

All storage goes through the generic `Repository<T>` interface (`add`, `findById`, `findAll`, `update`, `delete`).

### Four pillars of OOP
- **Encapsulation**: private fields with validating setters (`Book.setIsbn`, `User.setEmail`, `Student.setGradeLevel`). The password has no getter; it can only be checked with `User.checkPassword()`.
- **Inheritance**: `Student`/`Teacher`/`Librarian` extend `User`; `StudentMenu`/`TeacherMenu` extend `BorrowerMenu`, which extends `Menu`; all exceptions extend `LibraryException`.
- **Polymorphism**: `getBorrowLimit()`, `getLoanPeriodDays()`, `getMaxRenewals()` and `canAccess(Book)` differ per role. `Main.createMenu` returns a `Menu` whose real type depends on the user. Strategies are used through their interfaces.
- **Abstraction**: abstract classes `User`, `Menu`, `BorrowerMenu`; interfaces `Repository`, `Identifiable`, `FineStrategy`, `SearchStrategy`.

### Design patterns
- **Singleton**: `LibraryDatabase` has a private constructor, a private static instance, and a synchronized `getInstance()`.
- **Strategy**: `LibraryService.setFineStrategy(...)` → `assessFine(...)` (overdue PHP 5/day, damage 50%/20% of value, loss = value + PHP 100). `LibraryService.setSearchStrategy(...)` → `search(...)`.

### Exception handling (all in `ExceptionHandler.java`)
- Custom checked hierarchy: `LibraryException` → `NotFoundException`, `DuplicateEntryException`, `InvalidInputException`, `AuthenticationException`, `PaymentException`, `BorrowingException` → (`BorrowLimitExceededException`, `AgeRestrictionException`, `BookUnavailableException`, `AccountRestrictedException`, `RenewalNotAllowedException`).
- `run()`: catches every menu action's error (specific → general order, plus multi-catch) so the program never crashes.
- `tryCreate()` / `tryUpdate()`: convert `IllegalArgumentException` into `InvalidInputException` (exception chaining).
- `parseInt()` / `parseDouble()`: catch `NumberFormatException` from bad keyboard input.
- `importAccounts()`: reads `users.txt`; a bad line is reported and skipped.
- `loadData()` / `saveData()` / `runProgram()`: `IOException | ClassNotFoundException` multi-catch and `finally` to always save.
- Other classes only `throw`; there are no `catch` blocks anywhere else.
