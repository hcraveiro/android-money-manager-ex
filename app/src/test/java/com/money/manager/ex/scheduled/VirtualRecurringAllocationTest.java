package com.money.manager.ex.scheduled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.Test;

public class VirtualRecurringAllocationTest {
    private static long sum(List<VirtualRecurringAllocation.Entry> entries) {
        long sum = 0;
        for (VirtualRecurringAllocation.Entry entry : entries) sum += entry.minorUnits;
        return sum;
    }

    @Test
    public void weeklyDistributionIsAnchoredToDueDate() {
        LocalDate due = LocalDate.of(2026, 11, 30);
        LocalDate previous = LocalDate.of(2026, 10, 31);
        List<VirtualRecurringAllocation.Entry> entries =
            VirtualRecurringAllocation.allocate(3000, previous, due,
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20),
                due, "Weekly");
        assertEquals(2, entries.size());
        assertEquals(LocalDate.of(2026, 11, 23), entries.get(0).date);
        assertEquals(300, entries.get(0).minorUnits);
        assertEquals(LocalDate.of(2026, 11, 30), entries.get(1).date);
        assertEquals(700, entries.get(1).minorUnits);
    }

    @Test
    public void activationDateProratesOnlyFutureDays() {
        LocalDate due = LocalDate.of(2026, 11, 30);
        List<VirtualRecurringAllocation.Entry> entries =
            VirtualRecurringAllocation.allocate(3000, LocalDate.of(2026, 10, 31),
                due, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 25),
                due, "Daily");
        assertEquals(5, entries.size());
        assertEquals(500, sum(entries));
    }

    @Test
    public void signedRemaindersRemainExactForFullCycle() {
        for (int days : new int[] {28, 29, 30, 31, 365, 366}) {
            LocalDate previous = LocalDate.of(2024, 1, 1);
            LocalDate due = previous.plusDays(days);
            for (long minorUnits : new long[] {1, 99, 3001, -3001}) {
                assertEquals(minorUnits, sum(VirtualRecurringAllocation.allocate(
                    minorUnits, previous, due, previous, null, due, "Daily")));
            }
        }
    }

    @Test
    public void reportHorizonExcludesLaterDays() {
        LocalDate previous = LocalDate.of(2026, 10, 31);
        List<VirtualRecurringAllocation.Entry> entries =
            VirtualRecurringAllocation.allocate(3000, previous,
                LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 25), "Weekly");
        assertEquals(500, sum(entries));
        assertTrue(entries.stream().noneMatch(x ->
            x.date.isAfter(LocalDate.of(2026, 11, 25))));
    }

    @Test
    public void invalidFrequencyIsRejected() {
        assertTrue(VirtualRecurringAllocation.isValidFrequency(
            Recurrence.MONTHLY, "Daily"));
        assertTrue(VirtualRecurringAllocation.isValidFrequency(
            Recurrence.ANNUALLY, "Quarterly"));
        assertTrue(!VirtualRecurringAllocation.isValidFrequency(
            Recurrence.MONTHLY, "Monthly"));
        assertTrue(!VirtualRecurringAllocation.isValidFrequency(
            Recurrence.WEEKLY, "Weekly"));
        assertTrue(!VirtualRecurringAllocation.isValidFrequency(
            Recurrence.DAILY, "Daily"));
    }

    @Test
    public void fullWeeklyCyclePreservesDueDateAnchors() {
        LocalDate due = LocalDate.of(2026, 11, 30);
        LocalDate previous = LocalDate.of(2026, 10, 31);
        List<VirtualRecurringAllocation.Entry> entries =
            VirtualRecurringAllocation.allocate(3000, previous, due,
                previous, previous, due, "Weekly");
        assertEquals(5, entries.size());
        assertEquals(LocalDate.of(2026, 11, 2), entries.get(0).date);
        assertEquals(200, entries.get(0).minorUnits);
        for (int i = 1; i < entries.size(); ++i) {
            assertEquals(LocalDate.of(2026, 11, 2 + 7 * i), entries.get(i).date);
            assertEquals(700, entries.get(i).minorUnits);
        }
        assertEquals(3000, sum(entries));
    }

    @Test
    public void yearlyQuarterlyCyclePreservesExactMinorUnits() {
        LocalDate due = LocalDate.of(2026, 11, 30);
        LocalDate previous = LocalDate.of(2025, 11, 30);
        for (long amount : new long[] {1, 120001, -120001}) {
            List<VirtualRecurringAllocation.Entry> entries =
                VirtualRecurringAllocation.allocate(amount, previous, due,
                    previous, previous, due, "Quarterly");
            assertEquals(amount, sum(entries));
            assertEquals(LocalDate.of(2026, 11, 30), entries.get(entries.size() - 1).date);
        }
    }

    @Test
    public void monthEndNominalAdvancementRemainsAnchored() {
        assertEquals(LocalDate.of(2026, 12, 31),
            VirtualRecurringAllocation.nextNominalDue(
                LocalDate.of(2026, 11, 30), Recurrence.MONTHLY_LAST_DAY));
        assertEquals(LocalDate.of(2024, 3, 31),
            VirtualRecurringAllocation.nextNominalDue(
                LocalDate.of(2024, 2, 29), Recurrence.MONTHLY_LAST_DAY));
    }

    @Test
    public void lastBusinessDayUsesActualWeekdayBoundaries() {
        LocalDate due = LocalDate.of(2026, 1, 30);
        assertEquals(LocalDate.of(2025, 12, 31),
            VirtualRecurringAllocation.previousNominalDue(
                due, Recurrence.MONTHLY_LAST_BUSINESS_DAY));
        assertEquals(LocalDate.of(2026, 2, 27),
            VirtualRecurringAllocation.nextNominalDue(
                due, Recurrence.MONTHLY_LAST_BUSINESS_DAY));

        List<VirtualRecurringAllocation.Entry> entries =
            VirtualRecurringAllocation.allocate(3100, LocalDate.of(2025, 12, 31),
                due, LocalDate.of(2025, 12, 31), null, due, "Daily");
        assertEquals(3100, sum(entries));
        assertEquals(30, entries.size());
    }

    @Test
    public void monthEndHandlesLeapYears() {
        assertEquals(LocalDate.of(2024, 2, 29),
            VirtualRecurringAllocation.previousNominalDue(
                LocalDate.of(2024, 3, 31), Recurrence.MONTHLY));
        assertEquals(LocalDate.of(2023, 2, 28),
            VirtualRecurringAllocation.previousNominalDue(
                LocalDate.of(2023, 3, 31), Recurrence.MONTHLY));
    }
}
