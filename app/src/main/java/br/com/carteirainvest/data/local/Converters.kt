package br.com.carteirainvest.data.local

import androidx.room.TypeConverter
import br.com.carteirainvest.domain.model.AlertType
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.CashTransactionType
import br.com.carteirainvest.domain.model.QuoteSource
import br.com.carteirainvest.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? =
        value?.let { runCatching { BigDecimal(it) }.getOrNull() }

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun assetTypeToString(value: AssetType): String = value.name

    @TypeConverter
    fun stringToAssetType(value: String): AssetType =
        runCatching { AssetType.valueOf(value) }.getOrDefault(AssetType.STOCK)

    @TypeConverter
    fun cashTransactionTypeToString(value: CashTransactionType): String = value.name

    @TypeConverter
    fun stringToCashTransactionType(value: String): CashTransactionType =
        CashTransactionType.valueOf(value)

    @TypeConverter
    fun transactionTypeToString(value: TransactionType): String = value.name

    @TypeConverter
    fun stringToTransactionType(value: String): TransactionType =
        runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.BUY)

    @TypeConverter
    fun quoteSourceToString(value: QuoteSource): String = value.name

    @TypeConverter
    fun stringToQuoteSource(value: String): QuoteSource =
        runCatching { QuoteSource.valueOf(value) }.getOrDefault(QuoteSource.EQUITY)

    @TypeConverter
    fun alertTypeToString(value: AlertType): String = value.name

    @TypeConverter
    fun stringToAlertType(value: String): AlertType =
        runCatching { AlertType.valueOf(value) }.getOrDefault(AlertType.PRICE_CHANGE)
}
