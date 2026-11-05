package br.com.carteirainvest.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.junit.Assert.assertEquals
import java.math.BigDecimal
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migration-specific assertions are also exercised by Room's schema validation
 * when the generated version-2 schema is checked in.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class Migration1To2Test {

    private fun assertDecimalEquals(expected: String, actual: String) {
        assertEquals(0, BigDecimal(expected).compareTo(BigDecimal(actual)))
    }


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
                            dividendAmount TEXT,
                            date INTEGER NOT NULL,
                            notes TEXT NOT NULL,
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
                        (id, assetId, type, quantity, unitPrice, fees, dividendAmount, date, notes, createdAt)
                        VALUES ('buy-1', 'asset-1', 'BUY', '3', '100.10', '1.25', NULL, 1, '', 1)
                        """.trimIndent(),
                    )
                    db.execSQL("INSERT INTO asset_transactions (id, assetId, type, quantity, unitPrice, fees, dividendAmount, date, notes, createdAt) VALUES ('buy-2', 'asset-1', 'BUY', '2', '50', '0.50', NULL, 2, '', 2)")
                    db.execSQL("INSERT INTO asset_transactions (id, assetId, type, quantity, unitPrice, fees, dividendAmount, date, notes, createdAt) VALUES ('sell-1', 'asset-1', 'SELL', '4', '120', '2', NULL, 3, '', 3)")
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
                "SELECT principalAmount, remainingPrincipalAmount, annualRate, isMigrated " +
                    "FROM fixed_income_lots WHERE purchaseTransactionId = 'buy-1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertDecimalEquals("300.30", cursor.getString(0))
                assertDecimalEquals("0", cursor.getString(1))
                assertEquals("10.5", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
            }
            db.query("SELECT remainingQuantity, remainingPrincipalAmount FROM fixed_income_lots WHERE purchaseTransactionId = 'buy-2'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertDecimalEquals("1", cursor.getString(0))
                assertDecimalEquals("50", cursor.getString(1))
            }
            db.query("SELECT COUNT(*) FROM fixed_income_redemption_allocations").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM asset_transactions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
        } finally {
            helper.close()
        }
    }
}
