package br.com.carteirainvest.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Migration-specific assertions are also exercised by Room's schema validation
 * when the generated version-2 schema is checked in.
 */
@RunWith(AndroidJUnit4::class)
class Migration1To2Test {

    @Test
    fun migrationCreatesCashAndLotTablesWithoutInventingCashHistory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE wallets (id TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL(
                        """
                        CREATE TABLE assets (
                            id TEXT NOT NULL PRIMARY KEY,
                            type TEXT NOT NULL,
                            annualRate TEXT,
                            maturityDate INTEGER
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE asset_transactions (
                            id TEXT NOT NULL PRIMARY KEY,
                            assetId TEXT NOT NULL,
                            type TEXT NOT NULL,
                            quantity TEXT NOT NULL,
                            unitPrice TEXT NOT NULL,
                            fees TEXT NOT NULL,
                            date INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("INSERT INTO wallets(id) VALUES ('wallet-1')")
                    db.execSQL(
                        "INSERT INTO assets(id, type, annualRate, maturityDate) " +
                            "VALUES ('asset-1', 'FIXED_INCOME', '10.5', NULL)",
                    )
                    db.execSQL(
                        """
                        INSERT INTO asset_transactions
                        (id, assetId, type, quantity, unitPrice, fees, date, createdAt)
                        VALUES ('buy-1', 'asset-1', 'BUY', '3', '100.10', '1.25', 1, 1)
                        """.trimIndent(),
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val db = helper.writableDatabase

        try {
            MIGRATION_1_2.migrate(db)

            db.query("SELECT COUNT(*) FROM cash_transactions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query(
                "SELECT principalAmount, remainingPrincipalAmount, annualRate " +
                    "FROM fixed_income_lots WHERE purchaseTransactionId = 'buy-1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("300.30", cursor.getString(0))
                assertEquals("300.30", cursor.getString(1))
                assertEquals("10.5", cursor.getString(2))
            }
            db.query("SELECT COUNT(*) FROM asset_transactions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            helper.close()
        }
    }
}
