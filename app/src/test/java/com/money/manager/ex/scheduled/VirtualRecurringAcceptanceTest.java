package com.money.manager.ex.scheduled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.Test;

/** Runs the same per-date, minor-unit acceptance contract as Desktop C++. */
public class VirtualRecurringAcceptanceTest {

    @Test
    public void sharedCrossClientAcceptanceContract() throws Exception {
        InputStream fixture = getClass().getClassLoader()
            .getResourceAsStream("virtual_recurring_acceptance.csv");
        assertNotNull("Canonical acceptance CSV was not staged by CI", fixture);

        int cases = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(fixture, StandardCharsets.UTF_8))) {
            assertEquals(
                "case_id,frequency,amount_minor,previous_due,due,today,activated,horizon,expected",
                reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] fields = line.split(",", -1);
                assertEquals("Incorrect number of fields in " + line, 9, fields.length);

                String name = fields[0];
                String frequency = fields[1];
                long amountMinorUnits = Long.parseLong(fields[2]);
                LocalDate previousDue = LocalDate.parse(fields[3]);
                LocalDate due = LocalDate.parse(fields[4]);
                LocalDate today = LocalDate.parse(fields[5]);
                LocalDate activated = fields[6].isEmpty()
                    ? null : LocalDate.parse(fields[6]);
                LocalDate horizon = LocalDate.parse(fields[7]);

                List<VirtualRecurringAllocation.Entry> actual =
                    VirtualRecurringAllocation.allocate(amountMinorUnits, previousDue,
                        due, today, activated, horizon, frequency);
                String[] expected = fields[8].isEmpty()
                    ? new String[0] : fields[8].split(";");
                assertEquals(name + ": entry count", expected.length, actual.size());
                for (int index = 0; index < expected.length; ++index) {
                    String[] parts = expected[index].split(":");
                    assertEquals(name + ": date at entry " + index,
                        LocalDate.parse(parts[0]), actual.get(index).date);
                    assertEquals(name + ": amount at entry " + index,
                        Long.parseLong(parts[1]), actual.get(index).minorUnits);
                }
                ++cases;
            }
        }
        assertTrue("Incomplete acceptance matrix", cases >= 20);
    }
}
