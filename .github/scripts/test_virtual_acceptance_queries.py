#!/usr/bin/env python3
"""Validate Android's real SQL report queries against an isolated v22 .mmb file."""

import sqlite3
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "acceptance-fixtures" / "tests"
sys.path.insert(0, str(FIXTURES))
from build_virtual_recurring_acceptance import build_database  # noqa: E402


def run_query(connection, relative_path):
    sql = (ROOT / relative_path).read_text(encoding="utf-8")
    return connection.execute(sql).fetchall()


def main():
    with tempfile.TemporaryDirectory(prefix="android-mmex-sql-") as folder:
        database_path = build_database(Path(folder) / "synthetic-v22.mmb")
        with sqlite3.connect(database_path) as database:
            database.row_factory = sqlite3.Row
            bills = run_query(
                database, "app/src/main/res/raw/query_billdeposits.sql"
            )
            assert len(bills) == 7, f"Expected 7 schedules, found {len(bills)}"
            virtual = [row for row in bills if row["ISVIRTUAL"] == 1]
            assert len(virtual) == 6
            assert all(row["TRANSCODE"] != "Transfer" for row in virtual)
            by_id = {row["BDID"]: row for row in bills}
            assert by_id[920]["AMOUNT"] == -30
            assert by_id[920]["VIRTUALFREQUENCY"] == "Weekly"
            assert by_id[920]["VIRTUALSTARTDATE"] == "2026-11-20"
            assert by_id[922]["AMOUNT"] == 91
            assert by_id[923]["CURRENCYID"] == 1
            assert by_id[924]["ISVIRTUAL"] == 0

            account_rows = run_query(
                database, "app/src/main/res/raw/query_account_bills.sql"
            )
            accounts = {row["ACCOUNTID"]: row for row in account_rows}
            assert abs(accounts[901]["TOTALBASECONVRATE"] - 1055.0) < 1e-8
            assert abs(accounts[902]["TOTALBASECONVRATE"] - 280.8) < 1e-8
            assert abs(accounts[903]["TOTALBASECONVRATE"] - 90.0) < 1e-8

            posted = run_query(
                database, "app/src/main/res/raw/query_alldata.sql"
            )
            assert len(posted) == 5
            future = [row for row in posted if row["Date"][:10] > "2026-11-09"]
            assert {row["ID"] for row in future} == {911, 912}
            assert database.execute(
                "SELECT COUNT(*) FROM CHECKINGACCOUNT_V1"
            ).fetchone()[0] == 5
            assert database.execute("PRAGMA integrity_check").fetchone()[0] == "ok"

        print("Android SQL acceptance: 7 schedules, 6 Virtual, 5 posted,"
              " correct account filters, currencies and future rows")


if __name__ == "__main__":
    main()
