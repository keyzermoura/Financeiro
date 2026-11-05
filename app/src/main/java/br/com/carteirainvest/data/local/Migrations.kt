package br.com.carteirainvest.data.local

import android.content.ContentValues
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.math.BigDecimal

/**
 * Adds wallet cash movements and fixed-income application lots.
 *
 * Existing cash history cannot be reconstructed reliably because version 1
 * did not store deposits or withdrawals. The opening balance is therefore
 * intentionally entered by the user after upgrading.
 *
 * Existing fixed-income BUY transactions are migrated one-for-one into lots.
 * BigDecimal multiplication is performed in Kotlin (not SQLite) to preserve
 * the decimal values stored as TEXT.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cash_transactions (
                id TEXT NOT NULL,
                walletId TEXT NOT NULL,
                type TEXT NOT NULL,
                amount TEXT NOT NULL,
                date INTEGER NOT NULL,
                description TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(walletId) REFERENCES wallets(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_cash_transactions_walletId_date " +
                "ON cash_transactions(walletId, date)",
        )

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS fixed_income_lots (
                id TEXT NOT NULL,
                assetId TEXT NOT NULL,
                purchaseTransactionId TEXT NOT NULL,
                originalQuantity TEXT NOT NULL,
                remainingQuantity TEXT NOT NULL,
                principalAmount TEXT NOT NULL,
                remainingPrincipalAmount TEXT NOT NULL,
                acquisitionFees TEXT NOT NULL,
                annualRate TEXT,
                maturityDate INTEGER,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(assetId) REFERENCES assets(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(purchaseTransactionId) REFERENCES asset_transactions(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_fixed_income_lots_purchaseTransactionId " +
                "ON fixed_income_lots(purchaseTransactionId)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_fixed_income_lots_assetId " +
                "ON fixed_income_lots(assetId)",
        )

        migrateLegacyFixedIncomePurchases(database)
    }
}

private fun migrateLegacyFixedIncomePurchases(database: SupportSQLiteDatabase) {
    database.query(
        """
        SELECT t.id, t.assetId, t.quantity, t.unitPrice, t.fees, t.createdAt,
               a.annualRate, a.maturityDate
        FROM asset_transactions AS t
        INNER JOIN assets AS a ON a.id = t.assetId
        WHERE a.type = 'FIXED_INCOME' AND t.type = 'BUY'
        ORDER BY t.date ASC, t.createdAt ASC, t.id ASC
        """.trimIndent(),
    ).use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow("id")
        val assetIdIndex = cursor.getColumnIndexOrThrow("assetId")
        val quantityIndex = cursor.getColumnIndexOrThrow("quantity")
        val unitPriceIndex = cursor.getColumnIndexOrThrow("unitPrice")
        val feesIndex = cursor.getColumnIndexOrThrow("fees")
        val createdAtIndex = cursor.getColumnIndexOrThrow("createdAt")
        val annualRateIndex = cursor.getColumnIndexOrThrow("annualRate")
        val maturityDateIndex = cursor.getColumnIndexOrThrow("maturityDate")

        while (cursor.moveToNext()) {
            val quantity = BigDecimal(cursor.getString(quantityIndex))
            val unitPrice = BigDecimal(cursor.getString(unitPriceIndex))
            val principal = quantity.multiply(unitPrice)
            val values = ContentValues().apply {
                put("id", "legacy-lot-${cursor.getString(idIndex)}")
                put("assetId", cursor.getString(assetIdIndex))
                put("purchaseTransactionId", cursor.getString(idIndex))
                put("originalQuantity", quantity.toPlainString())
                put("remainingQuantity", quantity.toPlainString())
                put("principalAmount", principal.toPlainString())
                put("remainingPrincipalAmount", principal.toPlainString())
                put("acquisitionFees", cursor.getString(feesIndex))
                if (cursor.isNull(annualRateIndex)) {
                    putNull("annualRate")
                } else {
                    put("annualRate", cursor.getString(annualRateIndex))
                }
                if (cursor.isNull(maturityDateIndex)) {
                    putNull("maturityDate")
                } else {
                    put("maturityDate", cursor.getLong(maturityDateIndex))
                }
                put("createdAt", cursor.getLong(createdAtIndex))
            }
            database.insert("fixed_income_lots", 0, values)
        }
    }
}
