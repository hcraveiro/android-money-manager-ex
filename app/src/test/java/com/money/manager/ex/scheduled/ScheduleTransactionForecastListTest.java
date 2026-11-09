package com.money.manager.ex.scheduled;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.money.manager.ex.core.TransactionTypes;
import com.money.manager.ex.currency.CurrencyService;
import com.money.manager.ex.domainmodel.RecurringTransaction;
import com.money.manager.ex.utils.MmxDate;
import info.javaperformance.money.Money;
import info.javaperformance.money.MoneyFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ScheduleTransactionForecastListTest {

    private RecurringTransaction schedule(long accountId, TransactionTypes type,
                                          double amount) {
        RecurringTransaction transaction = RecurringTransaction.createInstance();
        transaction.setAccountId(accountId);
        transaction.setCategoryId(10L);
        transaction.setTransactionType(type);
        transaction.setStatus("");
        transaction.setPaymentDate("2026-11-30");
        transaction.setAmount(MoneyFactory.fromDouble(amount));
        transaction.setVirtual(true);
        return transaction;
    }

    @Test
    public void virtualForecastCacheUsesBaseCurrencyWithoutLosingSigns() {
        CurrencyService currencyService = mock(CurrencyService.class);
        when(currencyService.getBaseCurrencyId()).thenReturn(1L);
        when(currencyService.doCurrencyExchange(
            eq(1L), any(Money.class), eq(2L))).thenAnswer(
                invocation -> ((Money) invocation.getArgument(1)).multiply(2));

        ScheduleTransactionForecastList list = new ScheduleTransactionForecastList();
        list.setAccountCurrency(100L, 2L);
        list.setAccountCurrency(200L, 1L);
        list.add(schedule(100L, TransactionTypes.Withdrawal, 5));
        list.add(schedule(200L, TransactionTypes.Deposit, 3));

        list.populateCacheForCategory(currencyService);
        MmxDate dueDate = new MmxDate("2026-11-30");
        assertEquals(-7.0, list.getForecastAmountFromCache(
            10L, dueDate.getYear(), dueDate.getMonth()), 0.000001);
        assertEquals(-7.0, list.getTotalAmount(), 0.000001);
        assertEquals(-7.0, list.getRecurringTransactions(10L).getTotalAmount(), 0.000001);
    }
}
