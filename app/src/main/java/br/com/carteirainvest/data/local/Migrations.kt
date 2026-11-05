package br.com.carteirainvest.data.local

import android.content.ContentValues
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.math.BigDecimal
import java.math.RoundingMode

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
                isMigrated INTEGER NOT NULL,
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

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS fixed_income_redemption_allocations (
                id TEXT NOT NULL,
                redemptionTransactionId TEXT NOT NULL,
                lotId TEXT NOT NULL,
                quantity TEXT NOT NULL,
                principalAmount TEXT NOT NULL,
                redemptionAmount TEXT NOT NULL,
                fees TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(redemptionTransactionId) REFERENCES asset_transactions(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(lotId) REFERENCES fixed_income_lots(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_fixed_income_redemption_allocations_redemptionTransactionId " +
                "ON fixed_income_redemption_allocations(redemptionTransactionId)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_fixed_income_redemption_allocations_lotId " +
                "ON fixed_income_redemption_allocations(lotId)",
        )

        migrateLegacyFixedIncomePurchases(database)
    }
}

private data class LegacyLotState(
    val id: String,
    val assetId: String,
    val purchaseTransactionId: String,
    val originalQuantity: BigDecimal,
    var remainingQuantity: BigDecimal,
    val principalAmount: BigDecimal,
    var remainingPrincipalAmount: BigDecimal,
    val acquisitionFees: BigDecimal,
    val annualRate: String?,
    val maturityDate: Long?,
    val createdAt: Long,
)

private fun migrateLegacyFixedIncomePurchases(database: SupportSQLiteDatabase) {
    database.query(
        """
        SELECT t.id, t.assetId, t.type, t.quantity, t.unitPrice, t.fees, t.date,
               t.createdAt, a.annualRate, a.maturityDate
        FROM asset_transactions AS t
        INNER JOIN assets AS a ON a.id = t.assetId
        WHERE a.type = 'FIXED_INCOME' AND t.type IN ('BUY', 'SELL')
        ORDER BY t.assetId ASC, t.date ASC, t.createdAt ASC, t.id ASC
        """.trimIndent(),
    ).use { cursor ->
        val lotsByAsset = mutableMapOf<String, MutableList<LegacyLotState>>()
        val indexes = (0 until cursor.columnCount).associateBy { cursor.getColumnName(it) }

        while (cursor.moveToNext()) {
            fun string(name: String) = cursor.getString(indexes.getValue(name))
            fun long(name: String) = cursor.getLong(indexes.getValue(name))
            fun nullableLong(name: String): Long? =
                indexes.getValue(name).let { if (cursor.isNull(it)) null else cursor.getLong(it) }
            fun nullableString(name: String): String? =
                indexes.getValue(name).let { if (cursor.isNull(it)) null else cursor.getString(it) }

            val transactionId = string("id")
            val assetId = string("assetId")
            val quantity = BigDecimal(string("quantity"))
            val lots = lotsByAsset.getOrPut(assetId) { mutableListOf() }

            if (string("type") == "BUY") {
                val principal = quantity.multiply(BigDecimal(string("unitPrice")))
                val lot = LegacyLotState(
                    id = "legacy-lot-$transactionId",
                    assetId = assetId,
                    purchaseTransactionId = transactionId,
                    originalQuantity = quantity,
                    remainingQuantity = quantity,
                    principalAmount = principal,
                    remainingPrincipalAmount = principal,
                    acquisitionFees = BigDecimal(string("fees")),
                    annualRate = nullableString("annualRate"),
                    maturityDate = nullableLong("maturityDate"),
                    createdAt = long("createdAt"),
                )
                lots += lot
                insertLegacyLot(database, lot)
            } else {
                val salePrice = BigDecimal(string("unitPrice"))
                val saleFees = BigDecimal(string("fees"))
                var quantityToAllocate = quantity
                var quantityAllocated = BigDecimal.ZERO

                for (lot in lots) {
                    if (quantityToAllocate.signum() <= 0) break
                    if (lot.remainingQuantity.signum() <= 0) continue

                    val allocated = quantityToAllocate.min(lot.remainingQuantity)
                    val principalAllocated = if (allocated.compareTo(lot.remainingQuantity) == 0) {
                        lot.remainingPrincipalAmount
                    } else {
                        lot.remainingPrincipalAmount.multiply(allocated)
                            .divide(lot.remainingQuantity, 16, RoundingMode.HALF_EVEN)
                    }
                    val saleFraction = allocated.divide(quantity, 16, RoundingMode.HALF_EVEN)
                    val redemptionAmount = allocated.multiply(salePrice)
                    val allocatedFees = saleFees.multiply(saleFraction)

                    val allocationValues = ContentValues().apply {
                        put("id", "legacy-allocation-$transactionId-${lot.id}")
                        put("redemptionTransactionId", transactionId)
                        put("lotId", lot.id)
                        put("quantity", allocated.toPlainString())
                        put("principalAmount", principalAllocated.toPlainString())
                        put("redemptionAmount", redemptionAmount.toPlainString())
                        put("fees", allocatedFees.toPlainString())
                        put("createdAt", long("createdAt"))
                    }
                    database.insert("fixed_income_redemption_allocations", 0, allocationValues)

                    lot.remainingQuantity = lot.remainingQuantity.subtract(allocated)
                    lot.remainingPrincipalAmount =
                        lot.remainingPrincipalAmount.subtract(principalAllocated)
                    updateLegacyLotBalance(database, lot)
                    quantityToAllocate = quantityToAllocate.subtract(allocated)
                }
                // If the legacy history contains an out-of-order/oversized sale,
                // keep the original transaction untouched. The missing allocation
                // is detectable by comparing allocation quantities with the SELL.
            }
        }
    }
}

private fun insertLegacyLot(database: SupportSQLiteDatabase, lot: LegacyLotState) {
    val values = ContentValues().apply {
        put("id", lot.id)
        put("assetId", lot.assetId)
        put("purchaseTransactionId", lot.purchaseTransactionId)
        put("originalQuantity", lot.originalQuantity.toPlainString())
        put("remainingQuantity", lot.remainingQuantity.toPlainString())
        put("principalAmount", lot.principalAmount.toPlainString())
        put("remainingPrincipalAmount", lot.remainingPrincipalAmount.toPlainString())
        put("acquisitionFees", lot.acquisitionFees.toPlainString())
        if (lot.annualRate == null) putNull("annualRate") else put("annualRate", lot.annualRate)
        if (lot.maturityDate == null) putNull("maturityDate") else put("maturityDate", lot.maturityDate)
        put("createdAt", lot.createdAt)
        put("isMigrated", 1)
    }
    database.insert("fixed_income_lots", 0, values)
}

private fun updateLegacyLotBalance(database: SupportSQLiteDatabase, lot: LegacyLotState) {
    val values = ContentValues().apply {
        put("remainingQuantity", lot.remainingQuantity.toPlainString())
        put("remainingPrincipalAmount", lot.remainingPrincipalAmount.toPlainString())
    }
    database.update(
        "fixed_income_lots",
        0,
        values,
        "id = ?",
        arrayOf(lot.id),
    )
}
