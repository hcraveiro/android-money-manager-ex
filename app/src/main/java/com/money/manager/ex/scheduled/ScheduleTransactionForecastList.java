package com.money.manager.ex.scheduled;

import androidx.annotation.NonNull;

import com.money.manager.ex.currency.CurrencyService;
import com.money.manager.ex.domainmodel.RecurringTransaction;
import info.javaperformance.money.Money;
import com.money.manager.ex.utils.MmxDate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ScheduleTransactionForecastList
        extends ArrayList<RecurringTransaction> {

    public static class ScheduleTransactionCacheKey  {
        private final long categoryId;
        private final int year;
        private final int month;
        public ScheduleTransactionCacheKey(long categoryId, int year, int month) {
            this.categoryId = categoryId;
            this.year = year;
            this.month = month;
        }

        public ScheduleTransactionCacheKey(RecurringTransaction recurringTransaction) {
            this.categoryId = recurringTransaction.getCategoryId();
            this.year = recurringTransaction.getPaymentDateAsMmxDate().getYear();
            this.month = recurringTransaction.getPaymentDateAsMmxDate().getMonth();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ScheduleTransactionCacheKey tripleKey = (ScheduleTransactionCacheKey) o;
            return Objects.equals(categoryId, tripleKey.categoryId) && Objects.equals(year, tripleKey.year) && Objects.equals(month, tripleKey.month);
        }

        @Override
        public int hashCode() {
            return Objects.hash(categoryId, year, month);
        }

        @NonNull
        @Override
        public String toString() {
            return "{ category:" + categoryId + ", year:" + year + ", month:" + month + "}";
        }

    }

    private final HashMap<ScheduleTransactionCacheKey, Double> cacheForecastAmount = new HashMap<>();
    private final HashMap<Long, Long> accountCurrencyIds = new HashMap<>();
    private CurrencyService forecastCurrencyService;

    ScheduleTransactionForecastList() {
    }

    ScheduleTransactionForecastList(ArrayList<RecurringTransaction> recurringTransactionsList) {
        this.addAll(recurringTransactionsList);
    }

    public ArrayList<RecurringTransaction> getRecurringTransactionsList() {
        return this;
    }

    public ScheduleTransactionForecastList getRecurringTransactions(MmxDate start, MmxDate end) {
        return getRecurringTransactions(recurringTransaction -> recurringTransaction.getPaymentDate().compareTo(start.toDate()) >= 0 &&
                recurringTransaction.getPaymentDate().compareTo(end.toDate()) <= 0);
    }

    public ScheduleTransactionForecastList getRecurringTransactions(long categoryId) {
        return getRecurringTransactions(recurringTransaction -> recurringTransaction.getCategoryId() == categoryId);
    }

    public ScheduleTransactionForecastList getRecurringTransactions(long categoryId, MmxDate start, MmxDate end) {
        return getRecurringTransactions(recurringTransaction -> recurringTransaction.getPaymentDate().compareTo(start.toDate()) >= 0 &&
                recurringTransaction.getPaymentDate().compareTo(end.toDate()) <= 0 &&
                recurringTransaction.getCategoryId() == categoryId);
    }

    public ScheduleTransactionForecastList getRecurringTransactions(Predicate<RecurringTransaction> predicate) {
//        List list = this.stream().filter(predicate).collect(Collectors.toList());
        List<RecurringTransaction> list = this.parallelStream().filter(predicate).collect(Collectors.toList());
        ScheduleTransactionForecastList filtered =
            new ScheduleTransactionForecastList(new ArrayList<>(list));
        filtered.accountCurrencyIds.putAll(accountCurrencyIds);
        filtered.forecastCurrencyService = forecastCurrencyService;
        return filtered;
    }

    public void orderByDateAscending() {
        this.sort(Comparator.comparing(RecurringTransaction::getPaymentDate));
    }

    public void setAccountCurrency(Long accountId, long currencyId) {
        if (accountId != null) accountCurrencyIds.put(accountId, currencyId);
    }

    private double amountInBaseCurrency(RecurringTransaction transaction) {
        Money amount = transaction.getRealSignedAmount();
        Long sourceCurrency = accountCurrencyIds.get(transaction.getAccountId());
        if (forecastCurrencyService != null && sourceCurrency != null) {
            amount = forecastCurrencyService.doCurrencyExchange(
                forecastCurrencyService.getBaseCurrencyId(), amount, sourceCurrency);
        }
        return amount.toDouble();
    }

    public ScheduleTransactionForecastList populateCacheForCategory() {
        return populateCacheForCategory(null);
    }

    public ScheduleTransactionForecastList populateCacheForCategory(
            CurrencyService currencyService) {
        forecastCurrencyService = currencyService;
        cacheForecastAmount.clear();
        for (RecurringTransaction transaction : this) {
            ScheduleTransactionCacheKey key = new ScheduleTransactionCacheKey(transaction);
            cacheForecastAmount.merge(key, amountInBaseCurrency(transaction), Double::sum);
        }
        return this;
    }

    public Double getForecastAmountFromCache(long categoryId, int year, int month) {
        return cacheForecastAmount.getOrDefault(new ScheduleTransactionCacheKey(categoryId, year, month), 0.0);
    }

    public Double getTotalAmount() {
        if (this.size() == 0) return 0.0;

        double total = 0.0;
        for (RecurringTransaction transaction : this) {
            total += amountInBaseCurrency(transaction);
        }
        return total;
    }

}
