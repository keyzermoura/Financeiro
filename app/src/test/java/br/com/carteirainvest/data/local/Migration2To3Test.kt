package br.com.carteirainvest.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Migration2To3Test {

    @Test
    fun migrationPreservesManualCashEntriesAndAddsNullableUniqueSourceLink() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE wallets (id TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL(
                        """
                        CREATE TABLE cash_transactions (
                            id TEXT NOT NULL PRIMARY KEY,
                            walletId TEXT NOT NULL,
                            type TEXT NOT NULL,
                            amount TEXT NOT NULL,
                            date INTEGER NOT NULL,
                            description TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            FOREIGN KEY(walletId) REFERENCES wallets(id)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX index_cash_transactions_walletId_date " +
                            "ON cash_transactions(walletId, date)",
                    )
                    db.execSQL("INSERT INTO wallets(id) VALUES ('wallet-1')")
                    db.execSQL(
                        """
                        INSERT INTO cash_transactions
                            (id, walletId, type, amount, date, description, createdAt)
                        VALUES
                            ('manual-1', 'wallet-1', 'DEPOSIT', '125.50', 20000,
                             'Depósito manual', 99)
                        """.trimIndent(),
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val db = helper.writableDatabase

        try {
            MIGRATION_2_3.migrate(db)

            db.query(
                """
                SELECT id, walletId, type, amount, date, description, createdAt, sourceTransactionId
                FROM cash_transactions WHERE id = 'manual-1'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("manual-1", cursor.getString(0))
                assertEquals("wallet-1", cursor.getString(1))
                assertEquals("DEPOSIT", cursor.getString(2))
                assertEquals("125.50", cursor.getString(3))
                assertEquals(20000L, cursor.getLong(4))
                assertEquals("Depósito manual", cursor.getString(5))
                assertEquals(99L, cursor.getLong(6))
                assertTrue(cursor.isNull(7))
            }

            db.execSQL(
                """
                INSERT INTO cash_transactions
                    (id, walletId, type, amount, date, description, createdAt, sourceTransactionId)
                VALUES
                    ('auto-1', 'wallet-1', 'WITHDRAWAL', '10.00', 20001,
                     'Compra XYZ', 100, 'asset-tx-1')
                """.trimIndent(),
            )

            val duplicateError = runCatching {
                db.execSQL(
                    """
                    INSERT INTO cash_transactions
                        (id, walletId, type, amount, date, description, createdAt, sourceTransactionId)
                    VALUES
                        ('auto-2', 'wallet-1', 'WITHDRAWAL', '10.00', 20001,
                         'Compra XYZ duplicada', 101, 'asset-tx-1')
                    """.trimIndent(),
                )
            }.exceptionOrNull()
            assertTrue("O índice deve impedir sourceTransactionId duplicado", duplicateError is SQLiteConstraintException)

            db.query("SELECT COUNT(*) FROM cash_transactions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
        } finally {
            helper.close()
        }
    }
}
