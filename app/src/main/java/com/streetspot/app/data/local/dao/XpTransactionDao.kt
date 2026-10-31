package com.streetspot.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.streetspot.app.data.model.XpTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface XpTransactionDao {
    @Query("SELECT * FROM xp_transactions WHERE spotId = :spotId ORDER BY createdAt, id")
    fun observeForSpot(spotId: String): Flow<List<XpTransaction>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_transactions")
    fun observeTotalXp(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_transactions")
    suspend fun getTotalXp(): Int

    @Query("SELECT * FROM xp_transactions WHERE spotId = :spotId ORDER BY createdAt, id")
    suspend fun findForSpot(spotId: String): List<XpTransaction>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transactions: List<XpTransaction>)

    @Query("DELETE FROM xp_transactions")
    suspend fun deleteAll()
}
