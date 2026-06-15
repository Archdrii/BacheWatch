package com.dvua.bachewatch.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

//Dao con las consultas básicas y así

@Dao

interface BacheDao {
    @Query("SELECT * FROM bache_reports ORDER BY createdAt DESC")
    fun getAllReports(): Flow<List<BacheReport>>

    @Query("SELECT * FROM bache_reports WHERE id = :id LIMIT 1")
    suspend fun getReportById(id: Int): BacheReport?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: BacheReport): Long

    @Update
    suspend fun updateReport(report: BacheReport)

    @Query("DELETE FROM bache_reports WHERE id = :id")
    suspend fun deleteReportById(id: Int)
}
