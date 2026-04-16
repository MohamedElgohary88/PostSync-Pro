package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.QaReport
import com.postsyncpro.domain.repository.ReportRepository
import java.io.File

class GenerateReportUseCase(private val reportRepository: ReportRepository) {
    suspend operator fun invoke(report: QaReport, outputFile: File) {
        reportRepository.generatePdfReport(report, outputFile)
    }
}
