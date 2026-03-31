package com.example.portmonnai.data.local

import androidx.room.*
import com.example.portmonnai.domain.model.AssetType
import com.example.portmonnai.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val symbol: String,
    val type: AssetType
)

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["assetId"])],
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: String,
    val type: TransactionType,
    val quantity: Double,
    val priceAtDate: Double,
    val date: Long,
    val fees: Double
)

@Dao
interface PortfolioDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAsset(asset: AssetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM assets")
    fun getAllAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets")
    suspend fun getAllAssetsOnce(): List<AssetEntity>

    @Query("SELECT * FROM transactions WHERE assetId = :assetId")
    fun getTransactionsForAsset(assetId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>
    
    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM assets WHERE id = :assetId")
    suspend fun deleteAsset(assetId: String)

    @Query("DELETE FROM assets")
    suspend fun deleteAllAssets()

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add index on assetId (added in v5 without a version bump — fixed here)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transactions_assetId ON transactions (assetId)"
        )
    }
}

@Database(entities = [AssetEntity::class, TransactionEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao
}
