package com.money.manager.ex.scheduled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.money.manager.ex.Constants;
import com.money.manager.ex.domainmodel.RecurringTransaction;
import com.money.manager.ex.utils.MmxDate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class VirtualRecurringDeactivationTest {

    private RecurringTransaction virtualSchedule(String paymentDate, String dueDate,
                                                long paymentsLeft) {
        RecurringTransaction transaction = RecurringTransaction.createInstance();
        transaction.setRecurrence(Recurrence.MONTHLY);
        transaction.setRecurrenceMode(2);
        transaction.setPaymentDate(paymentDate);
        transaction.setDueDate(MmxDate.from(dueDate, "yyyy-MM-dd"));
        transaction.setPaymentsLeft(paymentsLeft);
        transaction.setVirtual(true);
        transaction.setVirtualFrequency("Weekly");
        transaction.setVirtualStartDate("2026-09-01");
        return transaction;
    }

    private LocalDate date(String value) {
        return LocalDate.parse(new MmxDate(value).toIsoDateString());
    }

    @Test
    public void deactivationAdvancesFiniteDatesAndConsumesElapsedOccurrences() {
        RecurringTransaction transaction = virtualSchedule("2026-09-30", "2026-09-30", 5);
        assertTrue(VirtualRecurringAllocation.deactivate(transaction,
            LocalDate.of(2026, 11, 20), "Weekly"));
        assertEquals(LocalDate.of(2026, 11, 30), date(transaction.getPaymentDateString()));
        assertEquals(LocalDate.of(2026, 11, 30), date(transaction.getDueDateString()));
        assertEquals(Long.valueOf(3), transaction.getPaymentsLeft());
        assertEquals(Integer.valueOf(0), transaction.getRecurrenceMode());
        assertFalse(transaction.isVirtual());
        assertNull(transaction.getVirtualStartDate());
        assertEquals("Weekly", transaction.getVirtualFrequency());
    }

    @Test
    public void deactivationKeepsUnlimitedPaymentCount() {
        RecurringTransaction transaction = virtualSchedule("2026-09-30", "2026-09-30",
            Constants.NOT_SET);
        assertTrue(VirtualRecurringAllocation.deactivate(transaction,
            LocalDate.of(2026, 11, 20), "Weekly"));
        assertEquals(LocalDate.of(2026, 11, 30), date(transaction.getPaymentDateString()));
        assertEquals(LocalDate.of(2026, 11, 30), date(transaction.getDueDateString()));
        assertEquals(Long.valueOf(Constants.NOT_SET), transaction.getPaymentsLeft());
    }

    @Test
    public void deactivationSkipsOccurrenceDueToday() {
        RecurringTransaction transaction = virtualSchedule("2026-11-20", "2026-11-20", 4);
        assertTrue(VirtualRecurringAllocation.deactivate(transaction,
            LocalDate.of(2026, 11, 20), "Weekly"));
        assertEquals(LocalDate.of(2026, 12, 20), date(transaction.getPaymentDateString()));
        assertEquals(LocalDate.of(2026, 12, 20), date(transaction.getDueDateString()));
        assertEquals(Long.valueOf(3), transaction.getPaymentsLeft());
    }

    @Test
    public void deactivationConsumesFinalElapsedOccurrenceWithoutRenewingSchedule() {
        RecurringTransaction transaction = virtualSchedule("2026-10-30", "2026-10-30", 1);
        assertTrue(VirtualRecurringAllocation.deactivate(transaction,
            LocalDate.of(2026, 11, 20), "Weekly"));
        assertEquals("2026-11-30", transaction.getPaymentDateString());
        assertEquals(LocalDate.of(2026, 11, 30), date(transaction.getDueDateString()));
        assertEquals(Long.valueOf(0), transaction.getPaymentsLeft());
        assertFalse(transaction.isVirtual());
        assertNull(transaction.getVirtualStartDate());
    }

    @Test
    public void alreadyExhaustedScheduleCanBeSwitchedOff() {
        RecurringTransaction transaction = virtualSchedule("2026-10-30", "2026-10-30", 0);
        assertTrue(VirtualRecurringAllocation.deactivate(transaction,
            LocalDate.of(2026, 11, 20), "Weekly"));
        assertEquals("2026-10-30", transaction.getPaymentDateString());
        assertEquals(Long.valueOf(0), transaction.getPaymentsLeft());
        assertFalse(transaction.isVirtual());
    }

    @Test
    public void futureOccurrenceIsNotAdvancedOrConsumed() {
        RecurringTransaction transaction = virtualSchedule("2026-11-30", "2026-11-30", 4);
        assertTrue(VirtualRecurringAllocation.deactivate(transaction,
            LocalDate.of(2026, 11, 20), "Weekly"));
        assertEquals("2026-11-30", transaction.getPaymentDateString());
        assertEquals(LocalDate.of(2026, 11, 30), date(transaction.getDueDateString()));
        assertEquals(Long.valueOf(4), transaction.getPaymentsLeft());
        assertFalse(transaction.isVirtual());
    }
}
