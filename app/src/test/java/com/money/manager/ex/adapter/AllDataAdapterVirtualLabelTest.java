package com.money.manager.ex.adapter;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.MatrixCursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.money.manager.ex.R;
import com.money.manager.ex.database.QueryBillDeposits;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AllDataAdapterVirtualLabelTest {
    private final Context context = RuntimeEnvironment.getApplication();

    private MatrixCursor schedule(int virtual, String frequency) {
        MatrixCursor cursor = new MatrixCursor(new String[] {
                QueryBillDeposits.ISVIRTUAL, QueryBillDeposits.VIRTUALFREQUENCY});
        cursor.addRow(new Object[] {virtual, frequency});
        cursor.moveToFirst();
        return cursor;
    }

    @Test
    public void virtualFrequenciesUseLocalizedResources() {
        AllDataAdapter adapter = new AllDataAdapter(context, null,
                AllDataAdapter.TypeCursor.RECURRINGTRANSACTION);
        String[] frequencies = {"Daily", "Weekly", "Monthly", "Quarterly"};
        int[] resources = {R.string.daily, R.string.weekly, R.string.monthly, R.string.quarterly};
        for (int i = 0; i < frequencies.length; i++) {
            try (MatrixCursor cursor = schedule(1, frequencies[i])) {
                assertEquals(context.getString(R.string.virtual_frequency_label,
                                context.getString(R.string.virtual_label),
                                context.getString(resources[i])),
                        adapter.getVirtualLabel(cursor));
            }
        }
    }

    @Test
    public void missingOrUnknownFrequencyShowsVirtualOnly() {
        AllDataAdapter adapter = new AllDataAdapter(context, null,
                AllDataAdapter.TypeCursor.RECURRINGTRANSACTION);
        for (String frequency : new String[] {null, "", "Unknown"}) {
            try (MatrixCursor cursor = schedule(1, frequency)) {
                assertEquals(context.getString(R.string.virtual_label), adapter.getVirtualLabel(cursor));
            }
        }
    }

    @Test
    public void recycledRealScheduleHidesAndClearsLabel() {
        AllDataAdapter adapter = new AllDataAdapter(context, null,
                AllDataAdapter.TypeCursor.RECURRINGTRANSACTION);
        for (int virtual : new int[] {1, 0, 1, 0}) {
            try (MatrixCursor cursor = schedule(virtual, "Weekly")) {
                assertEquals(virtual == 1
                                ? context.getString(R.string.virtual_frequency_label,
                                        context.getString(R.string.virtual_label),
                                        context.getString(R.string.weekly))
                                : "",
                        adapter.getVirtualLabel(cursor));
            }
        }
    }

    @Test
    public void ordinaryTransactionsNeedNoVirtualColumnsAndClearRecycledLabel() {
        AllDataAdapter adapter = new AllDataAdapter(context, null, AllDataAdapter.TypeCursor.ALLDATA);
        try (MatrixCursor cursor = new MatrixCursor(new String[] {"ID"})) {
            cursor.addRow(new Object[] {1});
            cursor.moveToFirst();
            assertEquals("", adapter.getVirtualLabel(cursor));
        }
    }

    @Test
    public void virtualTagDoesNotIncreaseScheduledRowHeight() {
        int regularHeight = measureSampleRow(false);
        assertEquals(regularHeight, measureSampleRow(true));
    }

    private int measureSampleRow(boolean virtual) {
        View row = LayoutInflater.from(context).inflate(R.layout.item_alldata_account, null, false);
        int widthPx = Math.round(360 * context.getResources().getDisplayMetrics().density);
        row.setLayoutParams(new ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((TextView) row.findViewById(R.id.textViewMonth)).setText("Nov");
        ((TextView) row.findViewById(R.id.textViewDay)).setText("03");
        ((TextView) row.findViewById(R.id.textViewYear)).setText("2026");
        ((TextView) row.findViewById(R.id.textViewCategorySub)).setText("Automobile:Maintenance");
        ((TextView) row.findViewById(R.id.textViewAmount)).setText("€ -300.00");
        ((TextView) row.findViewById(R.id.textViewStatus)).setText("Reconciled");
        TextView balance = row.findViewById(R.id.textViewBalance);
        balance.setText("8 days overdue!");
        balance.setVisibility(View.VISIBLE);

        AllDataAdapter adapter = new AllDataAdapter(context, null,
                AllDataAdapter.TypeCursor.RECURRINGTRANSACTION);
        try (MatrixCursor cursor = schedule(virtual ? 1 : 0, "Weekly")) {
            String label = adapter.getVirtualLabel(cursor);
            ((TextView) row.findViewById(R.id.textViewPayee)).setText(
                    adapter.getPayeeDisplayText("Renault", label));
        }

        int width = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST);
        row.measure(width, height);
        return row.getMeasuredHeight();
    }
}
