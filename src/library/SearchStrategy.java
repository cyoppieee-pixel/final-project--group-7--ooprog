package library;

/**
 * STRATEGY PATTERN — catalog search/filter.
 *
 * Each filter (title, author, genre, ISBN, age rating, availability,
 * donated tag) is a separate strategy class. LibraryService (the context)
 * swaps the strategy at runtime based on what the user picks in the menu.
 */
public interface SearchStrategy {

    String getName();

    /** Hint shown when asking for the search value. */
    String getPrompt();

    boolean matches(Book book, String criteria);

    // =====================================================================
    class ByTitle implements SearchStrategy {
        @Override public String getName() { return "Title"; }
        @Override public String getPrompt() { return "Enter title keyword: "; }

        @Override
        public boolean matches(Book book, String criteria) {
            return book.getTitle().toLowerCase().contains(criteria.toLowerCase());
        }
    }

    class ByAuthor implements SearchStrategy {
        @Override public String getName() { return "Author"; }
        @Override public String getPrompt() { return "Enter author name: "; }

        @Override
        public boolean matches(Book book, String criteria) {
            return book.getAuthor().toLowerCase().contains(criteria.toLowerCase());
        }
    }

    class ByGenre implements SearchStrategy {
        @Override public String getName() { return "Genre/Subject"; }
        @Override public String getPrompt() { return "Enter genre or subject: "; }

        @Override
        public boolean matches(Book book, String criteria) {
            return book.getGenre().toLowerCase().contains(criteria.toLowerCase());
        }
    }

    class ByIsbn implements SearchStrategy {
        @Override public String getName() { return "ISBN"; }
        @Override public String getPrompt() { return "Enter ISBN (dashes optional): "; }

        @Override
        public boolean matches(Book book, String criteria) {
            return book.getIsbn().contains(criteria.replaceAll("[-\\s]", "").toUpperCase());
        }
    }

    /** Shows books suitable for readers of the given age (age rating <= age). */
    class ByAgeRating implements SearchStrategy {
        @Override public String getName() { return "Age Rating"; }
        @Override public String getPrompt() { return "Show books suitable for age (0-18): "; }

        @Override
        public boolean matches(Book book, String criteria) {
            Integer age = ExceptionHandler.parseInt(criteria);
            if (age == null || age < 0) {
                throw new IllegalArgumentException("Age must be a whole number of 0 or more, got: '" + criteria + "'");
            }
            return book.getAgeRating() <= age;
        }
    }

    class ByAvailability implements SearchStrategy {
        @Override public String getName() { return "Availability Status"; }
        @Override public String getPrompt() { return "Show (A)vailable or (U)navailable books? "; }

        @Override
        public boolean matches(Book book, String criteria) {
            String c = criteria.trim().toUpperCase();
            if (c.startsWith("A")) return book.isAvailable();
            if (c.startsWith("U")) return !book.isAvailable();
            throw new IllegalArgumentException("Type A for available or U for unavailable.");
        }
    }

    class ByDonatedTag implements SearchStrategy {
        @Override public String getName() { return "Donated Tag"; }
        @Override public String getPrompt() { return "Show donated books? (Y)es / (N)o: "; }

        @Override
        public boolean matches(Book book, String criteria) {
            String c = criteria.trim().toUpperCase();
            if (c.startsWith("Y")) return book.hasDonatedCopy();
            if (c.startsWith("N")) return !book.hasDonatedCopy();
            throw new IllegalArgumentException("Type Y or N.");
        }
    }
}
