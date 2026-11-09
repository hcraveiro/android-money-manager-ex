package com.money.manager.ex.scheduled;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Exact minor-unit allocation of a virtual recurrence within one nominal cycle.
 * This class has no database side effects and never posts transactions.
 */
public final class VirtualRecurringAllocation {
    private VirtualRecurringAllocation() {}

    public static final class Entry {
        public final LocalDate date;
        public final long minorUnits;

        public Entry(LocalDate date, long minorUnits) {
            this.date = date;
            this.minorUnits = minorUnits;
        }
    }

    public static LocalDate previousNominalDue(LocalDate due, Recurrence recurrence) {
        switch (recurrence) {
            case WEEKLY:
                return due.minusWeeks(1);
            case MONTHLY:
            case MONTHLY_LAST_DAY:
                return previousMonth(due, 1);
            case MONTHLY_LAST_BUSINESS_DAY:
                return lastBusinessDayOfMonth(due.minusMonths(1));
            case QUARTERLY:
                return previousMonth(due, 3);
            case ANNUALLY:
                return previousMonth(due, 12);
            default:
                throw new IllegalArgumentException("Unsupported nominal recurrence: " + recurrence);
        }
    }

    private static LocalDate lastBusinessDayOfMonth(LocalDate date) {
        LocalDate candidate = date.withDayOfMonth(date.lengthOfMonth());
        while (candidate.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
            || candidate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    // Calculate the next supported nominal occurrence without creating a payment.
    public static LocalDate nextNominalDue(LocalDate due, Recurrence recurrence) {
        switch (recurrence) {
            case WEEKLY:
                return due.plusWeeks(1);
            case MONTHLY:
                return due.plusMonths(1);
            case MONTHLY_LAST_DAY: {
                LocalDate nextMonth = due.plusMonths(1);
                return nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
            }
            case MONTHLY_LAST_BUSINESS_DAY:
                return lastBusinessDayOfMonth(due.plusMonths(1));
            case QUARTERLY:
                return due.plusMonths(3);
            case ANNUALLY:
                return due.plusYears(1);
            default:
                throw new IllegalArgumentException("Unsupported nominal recurrence: " + recurrence);
        }
    }

    private static LocalDate previousMonth(LocalDate due, int months) {
        LocalDate previous = due.minusMonths(months);
        // Keep end-of-month schedules anchored at the prior month end.
        if (due.getDayOfMonth() == due.lengthOfMonth()) {
            previous = previous.withDayOfMonth(previous.lengthOfMonth());
        }
        return previous;
    }

    public static boolean isValidFrequency(Recurrence recurrence, String frequency) {
        int nominal;
        switch (recurrence) {
            case WEEKLY: nominal = 1; break;
            case MONTHLY:
            case MONTHLY_LAST_DAY:
            case MONTHLY_LAST_BUSINESS_DAY: nominal = 2; break;
            case QUARTERLY: nominal = 3; break;
            case ANNUALLY: nominal = 4; break;
            default: return false;
        }
        int cadence;
        if ("Daily".equals(frequency)) cadence = 0;
        else if ("Weekly".equals(frequency)) cadence = 1;
        else if ("Monthly".equals(frequency)) cadence = 2;
        else if ("Quarterly".equals(frequency)) cadence = 3;
        else return false;
        return cadence < nominal;
    }

    private static LocalDate bucketEnd(LocalDate date, LocalDate due, String frequency) {
        switch (frequency) {
            case "Daily":
                return date;
            case "Weekly": {
                long remaining = ChronoUnit.DAYS.between(date, due);
                return due.minusDays((remaining / 7) * 7);
            }
            case "Monthly":
            case "Quarterly": {
                int months = "Monthly".equals(frequency) ? 1 : 3;
                LocalDate end = due;
                for (int i = months; i <= 24; i += months) {
                    LocalDate candidate = due.minusMonths(i);
                    if (candidate.isBefore(date)) break;
                    end = candidate;
                }
                return end;
            }
            default:
                throw new IllegalArgumentException("Invalid virtual frequency: " + frequency);
        }
    }

    public static List<Entry> allocate(long amountMinorUnits, LocalDate previousDue,
                                       LocalDate due, LocalDate today,
                                       LocalDate activated, LocalDate horizon,
                                       String frequency) {
        List<Entry> result = new ArrayList<>();
        int cycleDays = (int) ChronoUnit.DAYS.between(previousDue, due);
        if (cycleDays <= 0 || cycleDays > 400) return result;

        LocalDate first = previousDue.plusDays(1);
        LocalDate earliest = today.plusDays(1);
        if (activated != null && !activated.isBefore(today))
            earliest = activated.plusDays(1);
        if (earliest.isAfter(first)) first = earliest;

        LocalDate last = due.isBefore(horizon) ? due : horizon;
        if (last.isBefore(first)) return result;

        Map<LocalDate, Long> grouped = new TreeMap<>();
        long quotient = amountMinorUnits / cycleDays;
        long remainder = amountMinorUnits % cycleDays;
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            int offset = (int) ChronoUnit.DAYS.between(previousDue.plusDays(1), day);
            long dailyAmount = quotient
                + ((offset + 1) * remainder) / cycleDays
                - (offset * remainder) / cycleDays;
            LocalDate bucket = bucketEnd(day, due, frequency);
            if (bucket.isAfter(last)) bucket = last;
            grouped.merge(bucket, dailyAmount, Long::sum);
        }
        for (Map.Entry<LocalDate, Long> item : grouped.entrySet()) {
            if (item.getValue() != 0)
                result.add(new Entry(item.getKey(), item.getValue()));
        }
        return result;
    }
}
