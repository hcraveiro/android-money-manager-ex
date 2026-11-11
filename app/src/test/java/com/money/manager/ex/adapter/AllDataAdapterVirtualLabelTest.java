package com.money.manager.ex.adapter;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.MatrixCursor;
import android.view.View;
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
        TextView label = new TextView(context);
        String[] frequencies = {"Daily", "Weekly", "Monthly", "Quarterly"};
        int[] resources = {R.string.daily, R.string.weekly, R.string.monthly, R.string.quarterly};
        for (int i = 0; i < frequencies.length; i++) {
            try (MatrixCursor cursor = schedule(1, frequencies[i])) {
                adapter.bindVirtualLabel(label, cursor);
                assertEquals(View.VISIBLE, label.getVisibility());
                assertEquals(context.getString(R.string.virtual_frequency_label,
                        context.getString(R.string.virtual_label), context.getString(resources[i])),
                        label.getText().toString());
            }
        }
    }

    @Test
    public void missingOrUnknownFrequencyShowsVirtualOnly() {
        AllDataAdapter adapter = new AllDataAdapter(context, null,
                AllDataAdapter.TypeCursor.RECURRINGTRANSACTION);
        TextView label = new TextView(context);
        for (String frequency : new String[] {null, "", "Unknown"}) {
            try (MatrixCursor cursor = schedule(1, frequency)) {
                adapter.bindVirtualLabel(label, cursor);
                assertEquals(View.VISIBLE, label.getVisibility());
                assertEquals(context.getString(R.string.virtual_label), label.getText().toString());
            }
        }
    }

    @Test
    public void recycledRealScheduleHidesAndClearsLabel() {
        AllDataAdapter adapter = new AllDataAdapter(context, null,
                AllDataAdapter.TypeCursor.RECURRINGTRANSACTION);
        TextView label = new TextView(context);
        for (int virtual : new int[] {1, 0, 1, 0}) {
            try (MatrixCursor cursor = schedule(virtual, "Weekly")) {
                adapter.bindVirtualLabel(label, cursor);
                assertEquals(virtual == 1 ? View.VISIBLE : View.GONE, label.getVisibility());
                if (virtual == 0) assertEquals("", label.getText().toString());
            }
        }
    }

    @Test
    public void ordinaryTransactionsNeedNoVirtualColumnsAndClearRecycledLabel() {
        AllDataAdapter adapter = new AllDataAdapter(context, null, AllDataAdapter.TypeCursor.ALLDATA);
        TextView label = new TextView(context);
        label.setText("stale label");
        label.setVisibility(View.VISIBLE);
        try (MatrixCursor cursor = new MatrixCursor(new String[] {"ID"})) {
            cursor.addRow(new Object[] {1});
            cursor.moveToFirst();
            adapter.bindVirtualLabel(label, cursor);
            assertEquals(View.GONE, label.getVisibility());
            assertEquals("", label.getText().toString());
        }
    }
}
