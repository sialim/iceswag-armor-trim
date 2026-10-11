package net.serverplugins.trim;

import java.time.LocalDate;
import java.util.Set;

record Usage(long trims, LocalDate day, long flightMillis, Set<Integer> warnings) {
    Usage {
        warnings = Set.copyOf(warnings);
    }

    static Usage empty(LocalDate day) {
        return new Usage(0, day, 0, Set.of());
    }

    Usage onDay(LocalDate currentDay) {
        return currentDay.isAfter(day) ? empty(currentDay) : this;
    }
}
