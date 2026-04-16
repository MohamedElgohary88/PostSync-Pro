package com.postsyncpro.data.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object TestRuns : Table("test_runs") {
    val id = long("id").autoIncrement()
    val timestamp = varchar("timestamp", 64)
    val endpoint = varchar("endpoint", 512)
    val method = varchar("method", 16)
    val totalTests = integer("total_tests")
    val passedTests = integer("passed_tests")
    val failedTests = integer("failed_tests")
    val passRate = float("pass_rate")
    val avgResponseTimeMs = long("avg_response_time_ms")

    override val primaryKey = PrimaryKey(id)
}

object TestResults : Table("test_results") {
    val id = long("id").autoIncrement()
    val runId = long("run_id").references(TestRuns.id)
    val mutationType = varchar("mutation_type", 128)
    val testDescription = varchar("test_description", 1024)
    val httpStatus = integer("http_status")
    val passed = bool("passed")
    val responseTimeMs = long("response_time_ms")
    val errorMessage = text("error_message")

    override val primaryKey = PrimaryKey(id)
}

object AppDatabase {
    fun initDatabase() {
        val homeDir = System.getProperty("user.home")
        val dbDir = File(homeDir, ".postsyncpro")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        val dbPath = File(dbDir, "regression.db").absolutePath

        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")

        transaction {
            SchemaUtils.create(TestRuns, TestResults)
        }
    }
}
