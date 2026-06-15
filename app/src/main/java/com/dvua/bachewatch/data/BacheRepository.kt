package com.dvua.bachewatch.data

import kotlinx.coroutines.flow.Flow

//Repo principal de los reportes
class BacheRepository(private val bacheDao: BacheDao) {

    // Lista local
    val allReports: Flow<List<BacheReport>> = bacheDao.getAllReports()

    suspend fun getReportById(id: Int): BacheReport? = bacheDao.getReportById(id)

    suspend fun insertReport(report: BacheReport): Long = bacheDao.insertReport(report)

    suspend fun updateReport(report: BacheReport) = bacheDao.updateReport(report)

    suspend fun deleteReportById(id: Int) = bacheDao.deleteReportById(id)
}
