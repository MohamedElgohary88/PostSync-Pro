package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.ReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.awt.Color
import java.io.File
import java.time.format.DateTimeFormatter

class PdfReportRepositoryImpl : ReportRepository {

    companion object {
        private val BRAND_ORANGE = Color(0xFF, 0x6C, 0x37)
        private val BRAND_DARK = Color(0x1C, 0x1C, 0x1E)
        private val COLOR_GREEN = Color(0x2E, 0xCC, 0x71)
        private val COLOR_YELLOW = Color(0xF3, 0x9C, 0x12)
        private val COLOR_RED = Color(0xE7, 0x4C, 0x3C)
        private val ROW_ALT_BG = Color(0xF7, 0xF7, 0xF8)
        private val TABLE_HEADER_BG = Color(0x2C, 0x2C, 0x2E)
        private val TABLE_HEADER_FG = Color.WHITE
        private val TEXT_SECONDARY = Color(0x6E, 0x6E, 0x72)

        private const val MARGIN_LEFT = 50f
        private const val MARGIN_RIGHT = 50f
        private const val MARGIN_TOP = 50f
        private const val MARGIN_BOTTOM = 60f
        private const val LINE_HEIGHT = 16f
        private const val SECTION_GAP = 28f
        private const val TABLE_ROW_HEIGHT = 22f
        private const val TABLE_HEADER_HEIGHT = 26f
        private const val FONT_SIZE_TITLE = 22f
        private const val FONT_SIZE_SUBTITLE = 12f
        private const val FONT_SIZE_SECTION = 14f
        private const val FONT_SIZE_BODY = 10f
        private const val FONT_SIZE_TABLE = 9f
        private const val FONT_SIZE_FOOTER = 8f
        private const val FONT_SIZE_SCORE = 48f
    }

    private lateinit var fontHelvetica: PDType1Font
    private lateinit var fontHelveticaBold: PDType1Font

    override suspend fun generatePdfReport(report: QaReport, outputFile: File) = withContext(Dispatchers.IO) {
        fontHelvetica = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        fontHelveticaBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

        val document = PDDocument()
        try {
            val pageContext = PageContext(document)
            pageContext.addNewPage()

            drawHeader(pageContext, report)
            drawExecutiveSummary(pageContext, report)
            drawSrsComplianceSection(pageContext, report.srsCompliance)
            drawFuzzSummarySection(pageContext, report.fuzzSummary)
            drawEndpointDetailsSection(pageContext, report.endpointDetails)
            drawFailedTestsAppendix(pageContext, report.endpointDetails)

            // Draw page numbers on all pages
            drawPageNumbers(document)

            document.save(outputFile)
        } finally {
            document.close()
        }
    }

    // ── Page management ────────────────────────────────────────────

    private inner class PageContext(val document: PDDocument) {
        var currentPage: PDPage? = null
        var contentStream: PDPageContentStream? = null
        var yPosition: Float = 0f
        var pageNumber: Int = 0
        private val pageWidth = PDRectangle.A4.width
        val usableWidth: Float = pageWidth - MARGIN_LEFT - MARGIN_RIGHT

        fun addNewPage(): PDPageContentStream {
            contentStream?.close()
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            currentPage = page
            pageNumber++
            yPosition = PDRectangle.A4.height - MARGIN_TOP
            val cs = PDPageContentStream(document, page)
            contentStream = cs
            return cs
        }

        fun ensureSpace(needed: Float): PDPageContentStream {
            if (yPosition - needed < MARGIN_BOTTOM) {
                return addNewPage()
            }
            return contentStream!!
        }
    }

    // ── Header ─────────────────────────────────────────────────────

    private fun drawHeader(ctx: PageContext, report: QaReport) {
        val cs = ctx.contentStream!!

        // Orange accent bar at top
        cs.setNonStrokingColor(BRAND_ORANGE)
        cs.addRect(0f, PDRectangle.A4.height - 6f, PDRectangle.A4.width, 6f)
        cs.fill()

        // Title
        ctx.yPosition -= 10f
        cs.beginText()
        cs.setFont(fontHelveticaBold, FONT_SIZE_TITLE)
        cs.setNonStrokingColor(BRAND_DARK)
        cs.newLineAtOffset(MARGIN_LEFT, ctx.yPosition)
        cs.showText("PostSync Pro \u2014 QA Certificate")
        cs.endText()

        ctx.yPosition -= 20f

        // Date and project name
        val dateStr = report.generatedAt.format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm"))
        cs.beginText()
        cs.setFont(fontHelvetica, FONT_SIZE_SUBTITLE)
        cs.setNonStrokingColor(TEXT_SECONDARY)
        cs.newLineAtOffset(MARGIN_LEFT, ctx.yPosition)
        cs.showText("Generated: $dateStr")
        cs.endText()

        ctx.yPosition -= LINE_HEIGHT
        cs.beginText()
        cs.setFont(fontHelvetica, FONT_SIZE_SUBTITLE)
        cs.newLineAtOffset(MARGIN_LEFT, ctx.yPosition)
        cs.showText("Project: ${report.projectName}")
        cs.endText()

        ctx.yPosition -= 10f

        // Divider line
        cs.setStrokingColor(BRAND_ORANGE)
        cs.setLineWidth(1.5f)
        cs.moveTo(MARGIN_LEFT, ctx.yPosition)
        cs.lineTo(PDRectangle.A4.width - MARGIN_RIGHT, ctx.yPosition)
        cs.stroke()

        ctx.yPosition -= SECTION_GAP
    }

    // ── Executive Summary ──────────────────────────────────────────

    private fun drawExecutiveSummary(ctx: PageContext, report: QaReport) {
        val cs = ctx.contentStream!!
        val score = report.healthScore
        val scorePercent = (score * 100).toInt()
        val scoreColor = when {
            score > 0.8f -> COLOR_GREEN
            score > 0.5f -> COLOR_YELLOW
            else -> COLOR_RED
        }

        drawSectionTitle(cs, ctx, "Executive Summary")

        // Score background rounded rect
        val boxWidth = 160f
        val boxHeight = 80f
        val boxX = MARGIN_LEFT + (ctx.usableWidth - boxWidth) / 2
        val boxY = ctx.yPosition - boxHeight

        cs.setNonStrokingColor(scoreColor.let { Color(it.red, it.green, it.blue, 20) })
        cs.addRect(boxX, boxY, boxWidth, boxHeight)
        cs.fill()

        // Score border
        cs.setStrokingColor(scoreColor)
        cs.setLineWidth(2f)
        cs.addRect(boxX, boxY, boxWidth, boxHeight)
        cs.stroke()

        // Score text
        val scoreText = "$scorePercent%"
        val scoreTextWidth = fontHelveticaBold.getStringWidth(scoreText) / 1000f * FONT_SIZE_SCORE
        cs.beginText()
        cs.setFont(fontHelveticaBold, FONT_SIZE_SCORE)
        cs.setNonStrokingColor(scoreColor)
        cs.newLineAtOffset(boxX + (boxWidth - scoreTextWidth) / 2, boxY + 24f)
        cs.showText(scoreText)
        cs.endText()

        // Label below score
        val label = "Health Score"
        val labelWidth = fontHelvetica.getStringWidth(label) / 1000f * FONT_SIZE_BODY
        cs.beginText()
        cs.setFont(fontHelvetica, FONT_SIZE_BODY)
        cs.setNonStrokingColor(TEXT_SECONDARY)
        cs.newLineAtOffset(boxX + (boxWidth - labelWidth) / 2, boxY + 8f)
        cs.showText(label)
        cs.endText()

        ctx.yPosition = boxY - SECTION_GAP
    }

    // ── SRS Compliance ─────────────────────────────────────────────

    private fun drawSrsComplianceSection(ctx: PageContext, srs: SrsComplianceMetrics) {
        ctx.ensureSpace(TABLE_HEADER_HEIGHT + TABLE_ROW_HEIGHT * 2 + SECTION_GAP + 30f)
        val cs = ctx.contentStream!!

        drawSectionTitle(cs, ctx, "SRS Compliance")

        val headers = listOf("Total Fields", "Matched", "Missing", "Conflicts", "Compliance %")
        val compliancePercent = if (srs.totalSrsFields > 0) {
            "%.1f%%".format(srs.matchedFields.toFloat() / srs.totalSrsFields * 100)
        } else "N/A"
        val row = listOf(
            srs.totalSrsFields.toString(),
            srs.matchedFields.toString(),
            srs.missingInApi.toString(),
            srs.conflicts.toString(),
            compliancePercent
        )

        val colWidths = distributeColumnWidths(headers.size, ctx.usableWidth)
        drawTableHeader(cs, ctx, headers, colWidths)
        drawTableRow(cs, ctx, row, colWidths, even = true)

        ctx.yPosition -= SECTION_GAP
    }

    // ── Fuzz Summary ───────────────────────────────────────────────

    private fun drawFuzzSummarySection(ctx: PageContext, fuzz: FuzzSummaryMetrics) {
        ctx.ensureSpace(TABLE_HEADER_HEIGHT + TABLE_ROW_HEIGHT * 2 + SECTION_GAP + 30f)
        val cs = ctx.contentStream!!

        drawSectionTitle(cs, ctx, "Fuzz Testing Summary")

        val headers = listOf("Total Tests", "Passed", "Failed", "Pass Rate", "Avg Response (ms)")
        val passRate = if (fuzz.totalTests > 0) {
            "%.1f%%".format(fuzz.passed.toFloat() / fuzz.totalTests * 100)
        } else "N/A"
        val row = listOf(
            fuzz.totalTests.toString(),
            fuzz.passed.toString(),
            fuzz.failed.toString(),
            passRate,
            fuzz.averageResponseTimeMs.toString()
        )

        val colWidths = distributeColumnWidths(headers.size, ctx.usableWidth)
        drawTableHeader(cs, ctx, headers, colWidths)
        drawTableRow(cs, ctx, row, colWidths, even = true)

        ctx.yPosition -= SECTION_GAP
    }

    // ── Endpoint Details ───────────────────────────────────────────

    private fun drawEndpointDetailsSection(ctx: PageContext, endpoints: List<EndpointReportDetail>) {
        if (endpoints.isEmpty()) return

        ctx.ensureSpace(TABLE_HEADER_HEIGHT + TABLE_ROW_HEIGHT + SECTION_GAP + 30f)
        val cs = ctx.contentStream!!

        drawSectionTitle(cs, ctx, "Endpoint Details")

        val headers = listOf("Method", "Path", "SRS Score", "Fuzz Pass Rate")
        val colWidths = floatArrayOf(60f, ctx.usableWidth - 60f - 80f - 90f, 80f, 90f)

        drawTableHeader(ctx.contentStream!!, ctx, headers, colWidths)

        endpoints.forEachIndexed { index, ep ->
            val stream = ctx.ensureSpace(TABLE_ROW_HEIGHT + 10f)
            val fuzzRate = if (ep.fuzzPassRate > 0f) {
                "%.1f%%".format(ep.fuzzPassRate * 100)
            } else "N/A"
            val row = listOf(
                ep.method,
                truncateText(ep.path, colWidths[1] - 10f, fontHelvetica, FONT_SIZE_TABLE),
                "%.1f%%".format(ep.srsScore * 100),
                fuzzRate
            )
            drawTableRow(stream, ctx, row, colWidths, even = index % 2 == 0)
        }

        ctx.yPosition -= SECTION_GAP
    }

    // ── Failed Tests Appendix ──────────────────────────────────────

    private fun drawFailedTestsAppendix(ctx: PageContext, endpoints: List<EndpointReportDetail>) {
        val failures = endpoints.flatMap { ep ->
            ep.failedTests.map { test -> Triple(ep, test.mutationType, test) }
        }
        if (failures.isEmpty()) return

        ctx.ensureSpace(TABLE_HEADER_HEIGHT + TABLE_ROW_HEIGHT + SECTION_GAP + 30f)
        val cs = ctx.contentStream!!

        drawSectionTitle(cs, ctx, "Failed Tests Appendix")

        val headers = listOf("Endpoint", "Mutation Type", "Expected", "Actual")
        val colWidths = floatArrayOf(
            ctx.usableWidth * 0.30f,
            ctx.usableWidth * 0.28f,
            ctx.usableWidth * 0.21f,
            ctx.usableWidth * 0.21f
        )

        drawTableHeader(ctx.contentStream!!, ctx, headers, colWidths)

        failures.forEachIndexed { index, (ep, _, test) ->
            val stream = ctx.ensureSpace(TABLE_ROW_HEIGHT + 10f)
            val endpointLabel = "${ep.method} ${ep.path}"
            val row = listOf(
                truncateText(endpointLabel, colWidths[0] - 10f, fontHelvetica, FONT_SIZE_TABLE),
                truncateText(test.mutationType, colWidths[1] - 10f, fontHelvetica, FONT_SIZE_TABLE),
                test.expectedStatus.toString(),
                test.actualStatus.toString()
            )
            drawTableRow(stream, ctx, row, colWidths, even = index % 2 == 0)
        }

        ctx.yPosition -= SECTION_GAP
    }

    // ── Drawing helpers ────────────────────────────────────────────

    private fun drawSectionTitle(cs: PDPageContentStream, ctx: PageContext, title: String) {
        // Orange left accent
        cs.setNonStrokingColor(BRAND_ORANGE)
        cs.addRect(MARGIN_LEFT, ctx.yPosition - 2f, 4f, 16f)
        cs.fill()

        cs.beginText()
        cs.setFont(fontHelveticaBold, FONT_SIZE_SECTION)
        cs.setNonStrokingColor(BRAND_DARK)
        cs.newLineAtOffset(MARGIN_LEFT + 12f, ctx.yPosition)
        cs.showText(title)
        cs.endText()

        ctx.yPosition -= SECTION_GAP
    }

    private fun drawTableHeader(cs: PDPageContentStream, ctx: PageContext, headers: List<String>, colWidths: FloatArray) {
        val y = ctx.yPosition

        // Header background
        cs.setNonStrokingColor(TABLE_HEADER_BG)
        cs.addRect(MARGIN_LEFT, y - TABLE_HEADER_HEIGHT + 4f, ctx.usableWidth, TABLE_HEADER_HEIGHT)
        cs.fill()

        var xOffset = MARGIN_LEFT + 6f
        cs.beginText()
        cs.setFont(fontHelveticaBold, FONT_SIZE_TABLE)
        cs.setNonStrokingColor(TABLE_HEADER_FG)
        cs.newLineAtOffset(xOffset, y - TABLE_HEADER_HEIGHT + 12f)
        cs.showText(headers[0])

        for (i in 1 until headers.size) {
            cs.newLineAtOffset(colWidths[i - 1], 0f)
            cs.showText(headers[i])
        }
        cs.endText()

        ctx.yPosition -= TABLE_HEADER_HEIGHT
    }

    private fun drawTableRow(cs: PDPageContentStream, ctx: PageContext, values: List<String>, colWidths: FloatArray, even: Boolean) {
        val y = ctx.yPosition

        // Alternating row background
        if (even) {
            cs.setNonStrokingColor(ROW_ALT_BG)
            cs.addRect(MARGIN_LEFT, y - TABLE_ROW_HEIGHT + 4f, ctx.usableWidth, TABLE_ROW_HEIGHT)
            cs.fill()
        }

        var xOffset = MARGIN_LEFT + 6f
        cs.beginText()
        cs.setFont(fontHelvetica, FONT_SIZE_TABLE)
        cs.setNonStrokingColor(BRAND_DARK)
        cs.newLineAtOffset(xOffset, y - TABLE_ROW_HEIGHT + 10f)
        cs.showText(values.getOrElse(0) { "" })

        for (i in 1 until values.size) {
            cs.newLineAtOffset(colWidths[i - 1], 0f)
            cs.showText(values.getOrElse(i) { "" })
        }
        cs.endText()

        ctx.yPosition -= TABLE_ROW_HEIGHT
    }

    private fun drawPageNumbers(document: PDDocument) {
        val totalPages = document.numberOfPages
        for (i in 0 until totalPages) {
            val page = document.getPage(i)
            val cs = PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)
            val text = "Page ${i + 1} of $totalPages"
            val textWidth = fontHelvetica.getStringWidth(text) / 1000f * FONT_SIZE_FOOTER
            cs.beginText()
            cs.setFont(fontHelvetica, FONT_SIZE_FOOTER)
            cs.setNonStrokingColor(TEXT_SECONDARY)
            cs.newLineAtOffset((PDRectangle.A4.width - textWidth) / 2, 30f)
            cs.showText(text)
            cs.endText()

            // Bottom accent line
            cs.setStrokingColor(BRAND_ORANGE)
            cs.setLineWidth(0.5f)
            cs.moveTo(MARGIN_LEFT, 42f)
            cs.lineTo(PDRectangle.A4.width - MARGIN_RIGHT, 42f)
            cs.stroke()

            cs.close()
        }
    }

    private fun distributeColumnWidths(count: Int, totalWidth: Float): FloatArray {
        val w = totalWidth / count
        return FloatArray(count) { w }
    }

    private fun truncateText(text: String, maxWidth: Float, font: PDType1Font, fontSize: Float): String {
        val fullWidth = font.getStringWidth(text) / 1000f * fontSize
        if (fullWidth <= maxWidth) return text

        var truncated = text
        while (truncated.isNotEmpty()) {
            truncated = truncated.dropLast(1)
            val w = font.getStringWidth("$truncated...") / 1000f * fontSize
            if (w <= maxWidth) return "$truncated..."
        }
        return "..."
    }
}
