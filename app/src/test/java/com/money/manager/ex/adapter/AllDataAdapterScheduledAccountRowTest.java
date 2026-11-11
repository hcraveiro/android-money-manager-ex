package com.money.manager.ex.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.MatrixCursor;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.money.manager.ex.Constants;
import com.money.manager.ex.MmexApplication;
import com.money.manager.ex.R;
import com.money.manager.ex.currency.CurrencyService;
import com.money.manager.ex.database.QueryBillDeposits;
import com.money.manager.ex.view.RobotoView;

import java.util.LinkedHashMap;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AllDataAdapterScheduledAccountRowTest {
    private Context context;
    private ListView parent;
    private int previousFont;
    private MockedStatic<MmexApplication> application;
    private MockedConstruction<CurrencyService> currencies;

    @Before
    public void setUp() {
        context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(),
                androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar);
        parent = new ListView(context);
        previousFont = RobotoView.getUserFont();
        RobotoView.setUserFont(-1);
        MmexApplication app = mock(MmexApplication.class);
        when(app.getAppLocale()).thenReturn(Locale.US);
        application = mockStatic(MmexApplication.class);
        application.when(MmexApplication::getApp).thenReturn(app);
        // Leave XML/theme text sizes intact, independent of application startup.
        application.when(MmexApplication::getTextSize).thenReturn(0f);
        // Keep bindView real; isolate only currency formatting and its database/DI dependencies.
        currencies = mockConstruction(CurrencyService.class, (service, construction) ->
                when(service.getCurrencyFormatted(anyLong(), any())).thenReturn("-30.00"));
    }

    @After
    public void tearDown() {
        if (currencies != null) currencies.close();
        if (application != null) application.close();
        RobotoView.setUserFont(previousFont);
    }

    @Test
    public void scheduledBindingHidesAccountForEmptyAndNonemptyNames() {
        AllDataAdapter adapter = adapter(AllDataAdapter.TypeCursor.RECURRINGTRANSACTION);
        for (String name : new String[] {"", "Sample account"}) {
            for (boolean virtual : new boolean[] {false, true}) {
                try (MatrixCursor cursor = transaction(adapter, name, virtual)) {
                    View row = adapter.newView(context, cursor, parent);
                    adapter.bindView(row, context, cursor);
                    assertEquals(View.GONE, account(row).getVisibility());
                    assertEquals(View.VISIBLE, row.findViewById(R.id.textViewBalance).getVisibility());
                    String label = adapter.getVirtualLabel(cursor);
                    assertEquals(adapter.getPayeeDisplayText("Sample payee", label).toString(),
                            ((TextView) row.findViewById(R.id.textViewPayee)).getText().toString());
                }
            }
        }
    }

    @Test
    public void ungroupedAllDataStillShowsAccountIncludingEmptyName() {
        AllDataAdapter adapter = adapter(AllDataAdapter.TypeCursor.ALLDATA);
        for (String name : new String[] {"", "Sample account"}) {
            try (MatrixCursor cursor = transaction(adapter, name, false)) {
                View row = adapter.newView(context, cursor, parent);
                adapter.bindView(row, context, cursor);
                assertEquals(View.VISIBLE, account(row).getVisibility());
                assertEquals(name, account(row).getText().toString());
                adapter.setShowAccountName(true);
                adapter.bindView(row, context, cursor);
                assertEquals(View.GONE, account(row).getVisibility());
                adapter.setShowAccountName(false);
                adapter.setAccountId(1);
                adapter.bindView(row, context, cursor);
                assertEquals(View.GONE, account(row).getVisibility());
                adapter.setAccountId(Constants.NOT_SET);
            }
        }
    }

    @Test
    public void recycledAllDataRowHidesAccountWhenBoundAsScheduled() {
        AllDataAdapter allData = adapter(AllDataAdapter.TypeCursor.ALLDATA);
        AllDataAdapter scheduled = adapter(AllDataAdapter.TypeCursor.RECURRINGTRANSACTION);
        try (MatrixCursor ordinary = transaction(allData, "Sample account", false);
             MatrixCursor recurring = transaction(scheduled, "Other account", true)) {
            View row = allData.newView(context, ordinary, parent);
            allData.bindView(row, context, ordinary);
            assertEquals(View.VISIBLE, account(row).getVisibility());
            scheduled.bindView(row, context, recurring);
            assertEquals(View.GONE, account(row).getVisibility());
            allData.bindView(row, context, ordinary);
            assertEquals(View.VISIBLE, account(row).getVisibility());
            assertEquals("Sample account", account(row).getText().toString());
            scheduled.bindView(row, context, recurring);
            assertEquals(View.GONE, account(row).getVisibility());
        }
    }

    @Test
    public void boundScheduledRowsHaveNoExtraAccountLineAndEqualVirtualRealHeight() {
        AllDataAdapter scheduled = adapter(AllDataAdapter.TypeCursor.RECURRINGTRANSACTION);
        int regularHeight = -1;
        for (String name : new String[] {"", "Sample account"}) {
            for (boolean virtual : new boolean[] {false, true}) {
                try (MatrixCursor cursor = transaction(scheduled, name, virtual)) {
                    View row = scheduled.newView(context, cursor, parent);
                    scheduled.bindView(row, context, cursor);
                    assertEquals(View.GONE, account(row).getVisibility());
                    int compactHeight = measure(row);
                    assertTrue(compactHeight > 0);
                    if (regularHeight < 0) regularHeight = compactHeight;
                    assertEquals(regularHeight, compactHeight);
                    // Reproduce the old visibility defect on the very same fully bound layout.
                    account(row).setText(name);
                    account(row).setVisibility(View.VISIBLE);
                    assertTrue("An empty visible account also adds a line",
                            measure(row) > compactHeight);
                    scheduled.bindView(row, context, cursor);
                    assertEquals(View.GONE, account(row).getVisibility());
                    assertEquals(compactHeight, measure(row));
                }
            }
        }
    }

    private AllDataAdapter adapter(AllDataAdapter.TypeCursor type) {
        // Match Scheduled's actual defaults: ungrouped and no selected account.
        return new AllDataAdapter(context, null, type);
    }

    private TextView account(View row) {
        return row.findViewById(R.id.textViewTransactionAccountName);
    }

    private int measure(View row) {
        int width = Math.round(360 * context.getResources().getDisplayMetrics().density);
        row.requestLayout();
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, width, row.getMeasuredHeight());
        return row.getMeasuredHeight();
    }

    private MatrixCursor transaction(AllDataAdapter adapter, String name, boolean virtual) {
        // Key by column name: Scheduled aliases ACCOUNTNAME and TOACCOUNTNAME,
        // and CURRENCYID and TOCURRENCYID, to the same query columns.
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("_id", 1);
        values.put(adapter.ID, 1);
        values.put(adapter.DATE, "2026-11-11");
        values.put(adapter.ACCOUNTID, 1);
        values.put(adapter.TOACCOUNTID, Constants.NOT_SET);
        values.put(adapter.STATUS, "R");
        values.put(adapter.TRANSACTIONTYPE, "Withdrawal");
        values.put(adapter.AMOUNT, -30.0);
        values.put(adapter.TOAMOUNT, -30.0);
        values.put(adapter.CURRENCYID, 1);
        values.put(adapter.TOCURRENCYID, 1);
        values.put(adapter.PAYEE, "Sample payee");
        values.put(adapter.ACCOUNTNAME, name);
        values.put(adapter.TOACCOUNTNAME, name);
        values.put(adapter.CATEGORY, "Sample category");
        values.put(adapter.CATEGID, Constants.NOT_SET);
        values.put(adapter.SPLITTED, 0);
        values.put(adapter.ATTACHMENTCOUNT, 0);
        values.put(adapter.TAGS, "");
        values.put(adapter.NOTES, "");
        values.put(adapter.COLOR, 0);
        if (adapter.ISSTOCKLINKED != null) values.put(adapter.ISSTOCKLINKED, 0);
        values.put(QueryBillDeposits.DAYSLEFT, 0);
        values.put(QueryBillDeposits.ISVIRTUAL, virtual ? 1 : 0);
        values.put(QueryBillDeposits.VIRTUALFREQUENCY, "Weekly");
        MatrixCursor cursor = new MatrixCursor(values.keySet().toArray(new String[0]));
        cursor.addRow(values.values().toArray());
        cursor.moveToFirst();
        return cursor;
    }
}
