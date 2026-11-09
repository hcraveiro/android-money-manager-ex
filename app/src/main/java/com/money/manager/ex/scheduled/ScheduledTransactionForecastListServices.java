/*
 * Copyright (C) 2012-2024 The Android Money Manager Ex Project Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.money.manager.ex.scheduled;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;

import com.money.manager.ex.core.TransactionTypes;
import com.money.manager.ex.currency.CurrencyService;
import com.money.manager.ex.domainmodel.Currency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import info.javaperformance.money.MoneyFactory;
import com.money.manager.ex.database.QueryBillDeposits;
import com.money.manager.ex.domainmodel.RecurringTransaction;
import com.money.manager.ex.servicelayer.RecurringTransactionService;
import com.money.manager.ex.utils.MmxDate;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import timber.log.Timber;

/*
 * Scheduled Transaction Services
 * create scheduled transaction forecast from single recurring transaction
 */
public class ScheduledTransactionForecastListServices {

//    private int monthInAdvance = 12;
    private MmxDate mDateTo;
    private ScheduleTransactionForecastList scheduleTransactionForecastList = null;
    private Boolean isReady;

    private static ScheduledTransactionForecastListServices mInstance = null;

    public static ScheduledTransactionForecastListServices getInstance() {
        if ( mInstance == null ) {
            mInstance = new ScheduledTransactionForecastListServices();
        }
        return mInstance;
    }

    public static void destroyInstance() {
        Timber.d("ScheduledTransactionForecastListServices: destroy instance");
        mInstance = null;
    }

    public Boolean isReady() {
        return isReady && (scheduleTransactionForecastList != null);
    }

    public ScheduledTransactionForecastListServices() {
        isReady = false;
        setMonthInAdvance(12); // default 12 months
    }

    public ScheduledTransactionForecastListServices setMonthInAdvance(int months) {
        return setDateTo(new MmxDate().addMonth(months));
    }

    public ScheduledTransactionForecastListServices setDateTo(MmxDate dateTo) {
        if (mDateTo != null && mDateTo.toIsoDateString().equals(dateTo.toIsoDateString())) {
            return this;
        }
        Timber.d("ScheduledTransactionForecastListServices: start new DateTo [%s] invalidating cache.", dateTo.toIsoDateString());
        mDateTo = dateTo;
        isReady = false;
        // invalidate scheduleTransactionForecastList
        scheduleTransactionForecastList = null;
        return this;
    }


    public CompletableFuture createScheduledTransactionForecastAsync(Context context, Function f) {
        return CompletableFuture.supplyAsync(() -> {
            Timber.d("ScheduledTransactionForecastListServices: Start compute forecast.");
            isReady = false;
            createScheduledTransactionForecast(context);
            scheduleTransactionForecastList.populateCacheForCategory(
                new CurrencyService(context));
            isReady = true;
            Timber.d("ScheduledTransactionForecastListServices: End compute forecast.");
            return scheduleTransactionForecastList;
        }).thenApply(f);

    }

    public ScheduleTransactionForecastList createScheduledTransactionForecast (Context context) {
        scheduleTransactionForecastList = new ScheduleTransactionForecastList();
        if (mDateTo == null) return scheduleTransactionForecastList;

        QueryBillDeposits billDeposits = new QueryBillDeposits(context);

        try (Cursor cursor = context.getContentResolver().query(billDeposits.getUri(),
                billDeposits.getAllColumns(),
                null,
                null,
                QueryBillDeposits.NEXTOCCURRENCEDATE)) {

            if (cursor == null || cursor.getCount() == 0)
                return scheduleTransactionForecastList; // is empty

            CurrencyService currencyService = new CurrencyService(context);
            LocalDate today = LocalDate.parse(new MmxDate().toIsoDateString());
            LocalDate horizon = LocalDate.parse(mDateTo.toIsoDateString());
            while (cursor.moveToNext()) {
                @SuppressLint("Range") RecurringTransactionService recurringTransactionService = new RecurringTransactionService(cursor.getLong(cursor.getColumnIndexOrThrow(QueryBillDeposits.BDID)), context);
                RecurringTransaction rx = recurringTransactionService.getSimulatedTransactionAsClone();
                if (rx == null) continue;
                long accountCurrencyId = cursor.getLong(
                    cursor.getColumnIndexOrThrow(QueryBillDeposits.CURRENCYID));
                scheduleTransactionForecastList.setAccountCurrency(
                    rx.getAccountId(), accountCurrencyId);
                if (rx.isVirtual()) {
                    if (rx.getTransactionType() == TransactionTypes.Transfer
                        || !VirtualRecurringAllocation.isValidFrequency(
                            rx.getRecurrence(), rx.getVirtualFrequency())) continue;

                    Currency currency = currencyService.getCurrency(accountCurrencyId);
                    if (currency == null || currency.getScale() == null
                        || currency.getScale() <= 0) continue;
                    int scale = currency.getScale();
                    long minorUnits = BigDecimal.valueOf(rx.getAmount().toDouble())
                        .multiply(BigDecimal.valueOf(scale))
                        .setScale(0, RoundingMode.HALF_UP).longValue();
                    String startDate = rx.getVirtualStartDate();
                    LocalDate activated = startDate == null || startDate.isEmpty()
                        ? today : LocalDate.parse(startDate);
                    Long paymentsLeft = rx.getPaymentsLeft();
                    LocalDate nextDue = LocalDate.parse(
                        rx.getPaymentDateAsMmxDate().toIsoDateString());
                    List<VirtualRecurringAllocation.Entry> entries =
                        VirtualRecurringAllocation.project(minorUnits, nextDue,
                            rx.getRecurrence(), paymentsLeft == null ? -1L : paymentsLeft,
                            today, activated, horizon, rx.getVirtualFrequency());
                    for (VirtualRecurringAllocation.Entry entry : entries) {
                        // An in-memory clone preserves account, category, currency and type.
                        RecurringTransaction projection = new RecurringTransaction();
                        projection.getContentValues().putAll(rx.getContentValues());
                        projection.setPaymentDate(entry.date.toString());
                        projection.setAmount(MoneyFactory.fromDouble(
                            entry.minorUnits / (double) scale));
                        scheduleTransactionForecastList.add(projection);
                    }
                    continue;
                }
                Timber.d("Recurring Transaction: %s", rx.toString());
                if (rx.getPaymentDate().after(mDateTo.toDate())) {
                    // first occurrence of this transaction is over cash flow visibility
                    continue;
                }
                scheduleTransactionForecastList.add(rx); // add first entry of series
                while (recurringTransactionService.simulateMoveNext() && recurringTransactionService.getSimulatedTransaction().getPaymentDate().before(mDateTo.toDate())) {
                    RecurringTransaction rx2 = recurringTransactionService.getSimulatedTransactionAsClone();
                    // Timber.d("Recurring Transaction Occurrence: " + rx2.toString());
                    scheduleTransactionForecastList.add(rx2);
                }

            }
        } catch (Exception e) {
            Timber.d(e);
        }

        scheduleTransactionForecastList.orderByDateAscending();
        return scheduleTransactionForecastList;
    }

    public ScheduleTransactionForecastList getRecurringTransactions() {
        return scheduleTransactionForecastList;
    }

}
