package com.postsyncpro.domain.repository

import com.postsyncpro.domain.model.QaReport
import java.io.File

interface ReportRepository {
    suspend fun generatePdfReport(report: QaReport, outputFile: File)
}
