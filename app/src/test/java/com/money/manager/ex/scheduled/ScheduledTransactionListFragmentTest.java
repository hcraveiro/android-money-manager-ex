package com.money.manager.ex.scheduled;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import com.money.manager.ex.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ScheduledTransactionListFragmentTest {

    private Menu createMenu() {
        Context context = RuntimeEnvironment.getApplication();
        Menu menu = new PopupMenu(context, new View(context)).getMenu();
        menu.add(Menu.NONE, R.id.menu_enter_next_occurrence, Menu.NONE, "Enter Next");
        menu.add(Menu.NONE, R.id.menu_skip_next_occurrence, Menu.NONE, "Skip");
        menu.add(Menu.NONE, R.id.menu_edit, Menu.NONE, "Edit");
        menu.add(Menu.NONE, R.id.menu_duplicate_transactions, Menu.NONE, "Duplicate");
        menu.add(Menu.NONE, R.id.menu_delete, Menu.NONE, "Delete");
        return menu;
    }

    private void assertActionState(Menu menu, int itemId, boolean available) {
        MenuItem item = menu.findItem(itemId);
        assertEquals(available, item.isVisible());
        assertEquals(available, item.isEnabled());
    }

    private void assertMenuState(Menu menu, boolean occurrenceActionsAvailable) {
        assertEquals(5, menu.size());
        assertActionState(menu, R.id.menu_enter_next_occurrence, occurrenceActionsAvailable);
        assertActionState(menu, R.id.menu_skip_next_occurrence, occurrenceActionsAvailable);
        assertActionState(menu, R.id.menu_edit, true);
        assertActionState(menu, R.id.menu_duplicate_transactions, true);
        assertActionState(menu, R.id.menu_delete, true);
    }

    @Test
    public void virtualScheduleHidesAndDisablesOccurrenceActionsOnly() {
        Menu menu = createMenu();
        ScheduledTransactionListFragment.configureOccurrenceActions(menu, true);
        assertMenuState(menu, false);
    }

    @Test
    public void nonvirtualScheduleRetainsAllActions() {
        Menu menu = createMenu();
        ScheduledTransactionListFragment.configureOccurrenceActions(menu, false);
        assertMenuState(menu, true);
    }

    @Test
    public void repeatedConfigurationRestoresNonvirtualActions() {
        Menu menu = createMenu();
        for (boolean isVirtual : new boolean[] {true, true, false, false, true, false}) {
            ScheduledTransactionListFragment.configureOccurrenceActions(menu, isVirtual);
            assertMenuState(menu, !isVirtual);
        }
    }
}
