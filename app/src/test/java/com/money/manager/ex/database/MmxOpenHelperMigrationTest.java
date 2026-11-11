package com.money.manager.ex.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.money.manager.ex.MmexApplication;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

@RunWith(RobolectricTestRunner.class)
@Config(application = MmexApplication.class)
public class MmxOpenHelperMigrationTest {

    private MmexApplication context;
    private File databaseFile;
    private SupportSQLiteOpenHelper helper;

    @Before
    public void setUp() throws Exception {
        context = (MmexApplication) RuntimeEnvironment.getApplication();
        databaseFile = new File(context.getCacheDir(), "migration-v21-test.mmb");
        createV21SyntheticDatabase(databaseFile);
    }

    @After
    public void tearDown() {
        if (helper != null) helper.close();
        if (databaseFile != null) {
            File parent = databaseFile.getParentFile();
            File[] files = parent.listFiles((dir, name) -> name.startsWith("migration-v21-test"));
            if (files != null) {
                for (File file : files) file.delete();
            }
        }
    }

    @Test
    public void reproducesLegacySilentBackupFailureOnSyntheticV21() {
        File existingBackup = new File(databaseFile.getParentFile(), "migration-v21-test_v21.mmb");
        try {
            Files.write(existingBackup.toPath(), new byte[]{1, 2, 3, 4});
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        SupportSQLiteOpenHelper.Callback legacyCallback = new SupportSQLiteOpenHelper.Callback(22) {
            @Override
            public void onCreate(SupportSQLiteDatabase db) { }

            @Override
            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                try {
                    Files.copy(databaseFile.toPath(), existingBackup.toPath());
                } catch (Exception ignored) {
                    // The old callback logged the backup failure and returned normally.
                }
            }

            @Override
            public void onDowngrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) { }

            @Override
            public void onOpen(SupportSQLiteDatabase db) { }
        };
        SupportSQLiteOpenHelper.Configuration configuration =
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(databaseFile.getAbsolutePath())
                        .callback(legacyCallback)
                        .build();
        helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        SupportSQLiteDatabase db = helper.getWritableDatabase();

        assertEquals(22, db.getVersion());
        assertEquals(21, scalarInt(db,
                "SELECT CAST(INFOVALUE AS INTEGER) FROM INFOTABLE_V1 WHERE INFONAME='DATAVERSION'"));
        boolean missingVirtualColumn = false;
        try {
            db.query("SELECT ISVIRTUAL FROM BILLSDEPOSITS_V1 LIMIT 0");
        } catch (SQLiteException expected) {
            missingVirtualColumn = true;
        }
        assertTrue("Legacy callback must reproduce the missing-column failure", missingVirtualColumn);
    }

    @Test
    public void migratesV21PreservesRowsAndReopensAsV22() {
        File existingBackup = new File(databaseFile.getParentFile(), "migration-v21-test_v21.mmb");
        byte[] sentinel = new byte[]{1, 2, 3, 4};
        try {
            Files.write(existingBackup.toPath(), sentinel);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        SQLiteDatabase source = SQLiteDatabase.openDatabase(
                databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        String scheduleColumns = "BDID, ACCOUNTID, TOACCOUNTID, PAYEEID, TRANSCODE, TRANSAMOUNT, "
                + "STATUS, TRANSACTIONNUMBER, NOTES, CATEGID, TRANSDATE, FOLLOWUPID, TOTRANSAMOUNT, "
                + "REPEATS, NEXTOCCURRENCEDATE, NUMOCCURRENCES, COLOR";
        List<String> schedulesBefore = snapshot(source,
                "SELECT " + scheduleColumns + " FROM BILLSDEPOSITS_V1 ORDER BY BDID");
        List<String> transactionsBefore = snapshot(source,
                "SELECT * FROM CHECKINGACCOUNT_V1 ORDER BY TRANSID");
        source.close();

        SupportSQLiteDatabase db = openDatabase();
        assertV22(db);
        assertEquals(schedulesBefore, snapshot(db,
                "SELECT " + scheduleColumns + " FROM BILLSDEPOSITS_V1 ORDER BY BDID"));
        assertEquals(transactionsBefore, snapshot(db, "SELECT * FROM CHECKINGACCOUNT_V1 ORDER BY TRANSID"));
        assertEquals(7, scalarInt(db, "SELECT COUNT(*) FROM BILLSDEPOSITS_V1"));
        assertEquals(5, scalarInt(db, "SELECT COUNT(*) FROM CHECKINGACCOUNT_V1"));
        assertEquals(7, scalarInt(db, "SELECT COUNT(*) FROM BILLSDEPOSITS_V1 WHERE ISVIRTUAL=0"));
        assertEquals(0, scalarInt(db, "SELECT COUNT(*) FROM BILLSDEPOSITS_V1 WHERE VIRTUALFREQUENCY IS NOT NULL"));
        assertTrue(new File(databaseFile.getParentFile(), "migration-v21-test_v21_1.mmb").isFile());
        assertEquals(4, existingBackup.length());
        helper.close();
        helper = null;

        db = openDatabase();
        assertV22(db);
        assertEquals(7, scalarInt(db, "SELECT COUNT(*) FROM BILLSDEPOSITS_V1"));
        assertEquals(5, scalarInt(db, "SELECT COUNT(*) FROM CHECKINGACCOUNT_V1"));
    }

    @Test
    public void failedMigrationRollsBackAndDoesNotAdvanceUserVersion() {
        SQLiteDatabase legacyDb = SQLiteDatabase.openDatabase(
                databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
        legacyDb.execSQL("DROP TABLE INFOTABLE_V1");
        legacyDb.close();

        try {
            openDatabase();
            fail("Migration without INFOTABLE_V1 must fail");
        } catch (RuntimeException expected) {
            // The open helper must report the migration error instead of marking v22 complete.
        } finally {
            if (helper != null) helper.close();
            helper = null;
        }

        SQLiteDatabase afterFailure = SQLiteDatabase.openDatabase(
                databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        assertEquals(21, afterFailure.getVersion());
        assertEquals(7, scalarInt(afterFailure, "SELECT COUNT(*) FROM BILLSDEPOSITS_V1"));
        assertEquals(5, scalarInt(afterFailure, "SELECT COUNT(*) FROM CHECKINGACCOUNT_V1"));
        assertTrue(!hasColumn(afterFailure, "ISVIRTUAL"));
        afterFailure.close();
    }

    @Test
    public void repairsInterruptedUpgradeWithUserVersion22AndLegacySchema() throws Exception {
        SQLiteDatabase legacyDb = SQLiteDatabase.openDatabase(
                databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
        assertEquals(21, legacyDb.getVersion());
        legacyDb.setVersion(22);
        legacyDb.close();

        SupportSQLiteDatabase db = openDatabase();
        assertV22(db);
        assertEquals(7, scalarInt(db, "SELECT COUNT(*) FROM BILLSDEPOSITS_V1"));
        assertEquals(5, scalarInt(db, "SELECT COUNT(*) FROM CHECKINGACCOUNT_V1"));
        assertEquals(7, scalarInt(db, "SELECT COUNT(*) FROM BILLSDEPOSITS_V1 WHERE ISVIRTUAL=0"));
    }

    private SupportSQLiteDatabase openDatabase() {
        MmxOpenHelper callback = new MmxOpenHelper(context, databaseFile.getAbsolutePath(), false);
        SupportSQLiteOpenHelper.Configuration configuration =
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(databaseFile.getAbsolutePath())
                        .callback(callback)
                        .build();
        helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        return helper.getWritableDatabase();
    }

    private void assertV22(SupportSQLiteDatabase db) {
        assertEquals(22, db.getVersion());
        assertEquals(22, scalarInt(db,
                "SELECT CAST(INFOVALUE AS INTEGER) FROM INFOTABLE_V1 WHERE INFONAME='DATAVERSION'"));
        for (String column : new String[]{"ISVIRTUAL", "VIRTUALFREQUENCY", "VIRTUALSTARTDATE"}) {
            try (Cursor cursor = db.query("SELECT " + column + " FROM BILLSDEPOSITS_V1 LIMIT 0")) {
                assertNotNull(cursor);
            }
        }
    }

    private int scalarInt(SupportSQLiteDatabase db, String sql) {
        try (Cursor cursor = db.query(sql)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private int scalarInt(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private boolean hasColumn(SQLiteDatabase db, String columnName) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(BILLSDEPOSITS_V1)", null)) {
            while (cursor.moveToNext()) {
                if (columnName.equalsIgnoreCase(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> snapshot(android.database.Cursor cursor) {
        List<String> rows = new ArrayList<>();
        try (android.database.Cursor result = cursor) {
            while (result.moveToNext()) {
                StringBuilder row = new StringBuilder();
                for (int column = 0; column < result.getColumnCount(); column++) {
                    if (column > 0) row.append('|');
                    row.append(result.isNull(column) ? "<null>" : result.getString(column));
                }
                rows.add(row.toString());
            }
        }
        return rows;
    }

    private List<String> snapshot(SQLiteDatabase db, String sql) {
        return snapshot(db.rawQuery(sql, null));
    }

    private List<String> snapshot(SupportSQLiteDatabase db, String sql) {
        return snapshot(db.query(sql));
    }

    private void createV21SyntheticDatabase(File destination) {
        if (destination.exists() && !destination.delete()) {
            throw new AssertionError("Could not replace synthetic v21 test database");
        }
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(destination, null);
        db.beginTransaction();
        try {
            db.execSQL("CREATE TABLE INFOTABLE_V1 (INFOID INTEGER PRIMARY KEY, INFONAME TEXT, INFOVALUE TEXT)");
            db.execSQL("INSERT INTO INFOTABLE_V1 VALUES (1, 'DATAVERSION', '21')");
            db.execSQL("CREATE TABLE BILLSDEPOSITS_V1 ("
                    + "BDID INTEGER PRIMARY KEY, ACCOUNTID INTEGER, TOACCOUNTID INTEGER, PAYEEID INTEGER, "
                    + "TRANSCODE TEXT, TRANSAMOUNT REAL, STATUS TEXT, TRANSACTIONNUMBER TEXT, NOTES TEXT, "
                    + "CATEGID INTEGER, TRANSDATE TEXT, FOLLOWUPID INTEGER, TOTRANSAMOUNT REAL, REPEATS INTEGER, "
                    + "NEXTOCCURRENCEDATE TEXT, NUMOCCURRENCES INTEGER, COLOR INTEGER)");
            db.execSQL("CREATE TABLE CHECKINGACCOUNT_V1 ("
                    + "TRANSID INTEGER PRIMARY KEY, ACCOUNTID INTEGER, TRANSAMOUNT REAL, TRANSDATE TEXT, NOTES TEXT)");

            String scheduledInsert = "INSERT INTO BILLSDEPOSITS_V1 VALUES "
                    + "(?, 1, NULL, ?, ?, ?, 'R', '', ?, ?, '2026-11-30', NULL, NULL, 3, '2026-11-30', 5, 0)";
            for (int id = 1; id <= 7; id++) {
                String type = id == 3 ? "Deposit" : "Withdrawal";
                double amount = id == 1 ? -30.0 : id == 2 ? -120.0 : id == 3 ? 91.0 : -10.0 - id;
                db.execSQL(scheduledInsert, new Object[]{id, id, type, amount, "Synthetic schedule " + id, id});
            }
            for (int id = 1; id <= 5; id++) {
                db.execSQL("INSERT INTO CHECKINGACCOUNT_V1 VALUES (?, 1, ?, '2026-11-01', ?)",
                        new Object[]{id, -5.0 * id, "Synthetic transaction " + id});
            }
            db.setVersion(21);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }
}
