package com.postsyncpro.presentation.screen.cicdexport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PipelineProvider { GITHUB_ACTIONS, GITLAB_CI, JENKINS, CIRCLE_CI }
enum class TriggerType { ON_PUSH, ON_PR, SCHEDULED, MANUAL }

data class CiCdConfig(
    val provider: PipelineProvider = PipelineProvider.GITHUB_ACTIONS,
    val includeFuzz: Boolean = true,
    val includeRegression: Boolean = true,
    val includeChain: Boolean = false,
    val includeCoverage: Boolean = true,
    val dockerSupport: Boolean = false,
    val trigger: TriggerType = TriggerType.ON_PUSH,
    val scheduleCron: String = "0 0 * * *"
)

data class CiCdExportState(
    val config: CiCdConfig = CiCdConfig(),
    val generatedConfig: String? = null,
    val isCopied: Boolean = false
)

class CiCdExportViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(CiCdExportState())
    val state: StateFlow<CiCdExportState> = _state.asStateFlow()

    fun updateConfig(config: CiCdConfig) {
        _state.value = _state.value.copy(config = config, generatedConfig = null, isCopied = false)
    }

    fun generateConfig() {
        val config = _state.value.config
        val yaml = when (config.provider) {
            PipelineProvider.GITHUB_ACTIONS -> generateGitHubActions(config)
            PipelineProvider.GITLAB_CI -> generateGitLabCi(config)
            PipelineProvider.JENKINS -> generateJenkinsfile(config)
            PipelineProvider.CIRCLE_CI -> generateCircleCi(config)
        }
        _state.value = _state.value.copy(generatedConfig = yaml, isCopied = false)
    }

    fun copyToClipboard() {
        val config = _state.value.generatedConfig ?: return
        try {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(java.awt.datatransfer.StringSelection(config), null)
            _state.value = _state.value.copy(isCopied = true)
        } catch (_: Exception) {}
    }

    private fun generateGitHubActions(config: CiCdConfig): String {
        val trigger = when (config.trigger) {
            TriggerType.ON_PUSH -> "on:\n  push:\n    branches: [ main, develop ]"
            TriggerType.ON_PR -> "on:\n  pull_request:\n    branches: [ main ]"
            TriggerType.SCHEDULED -> "on:\n  schedule:\n    - cron: '${config.scheduleCron}'"
            TriggerType.MANUAL -> "on:\n  workflow_dispatch:"
        }
        val steps = buildList {
            add("      - uses: actions/checkout@v4")
            add("      - name: Set up JDK 17\n        uses: actions/setup-java@v4\n        with:\n          java-version: '17'\n          distribution: 'temurin'")
            if (config.dockerSupport) add("      - name: Build Docker Image\n        run: docker build -t postsync-pro-tests .")
            if (config.includeFuzz) add("      - name: Run Fuzz Tests\n        run: ./gradlew fuzzTest")
            if (config.includeRegression) add("      - name: Run Regression Tests\n        run: ./gradlew regressionTest")
            if (config.includeChain) add("      - name: Run Chain Tests\n        run: ./gradlew chainTest")
            if (config.includeCoverage) add("      - name: Coverage Analysis\n        run: ./gradlew coverageReport")
        }
        return "name: PostSync Pro QA Pipeline\n\n$trigger\n\njobs:\n  qa-tests:\n    runs-on: ubuntu-latest\n    steps:\n${steps.joinToString("\n")}\n\n      - name: Upload Results\n        uses: actions/upload-artifact@v4\n        with:\n          name: qa-reports\n          path: build/reports/"
    }

    private fun generateGitLabCi(config: CiCdConfig): String {
        val stages = mutableListOf<String>()
        val jobs = buildString {
            if (config.includeFuzz) {
                stages.add("fuzz")
                append("fuzz-test:\n  stage: fuzz\n  script:\n    - ./gradlew fuzzTest\n  artifacts:\n    paths:\n      - build/reports/\n\n")
            }
            if (config.includeRegression) {
                stages.add("regression")
                append("regression-test:\n  stage: regression\n  script:\n    - ./gradlew regressionTest\n\n")
            }
            if (config.includeChain) {
                stages.add("chain")
                append("chain-test:\n  stage: chain\n  script:\n    - ./gradlew chainTest\n\n")
            }
            if (config.includeCoverage) {
                stages.add("coverage")
                append("coverage:\n  stage: coverage\n  script:\n    - ./gradlew coverageReport\n")
            }
        }
        val image = if (config.dockerSupport) "image: postsync-pro:latest\n\n" else "image: eclipse-temurin:17\n\n"
        return "${image}stages:\n${stages.joinToString("\n") { "  - $it" }}\n\n$jobs"
    }

    private fun generateJenkinsfile(config: CiCdConfig): String {
        val stages = buildString {
            if (config.includeFuzz) append("        stage('Fuzz Tests') {\n            steps {\n                sh './gradlew fuzzTest'\n            }\n        }\n")
            if (config.includeRegression) append("        stage('Regression Tests') {\n            steps {\n                sh './gradlew regressionTest'\n            }\n        }\n")
            if (config.includeChain) append("        stage('Chain Tests') {\n            steps {\n                sh './gradlew chainTest'\n            }\n        }\n")
            if (config.includeCoverage) append("        stage('Coverage Report') {\n            steps {\n                sh './gradlew coverageReport'\n            }\n        }\n")
        }
        val agent = if (config.dockerSupport) "    agent {\n        docker { image 'postsync-pro:latest' }\n    }" else "    agent any"
        return "pipeline {\n$agent\n    stages {\n$stages    }\n    post {\n        always {\n            archiveArtifacts artifacts: 'build/reports/**'\n        }\n    }\n}"
    }

    private fun generateCircleCi(config: CiCdConfig): String {
        val jobs = buildString {
            if (config.includeFuzz) append("  fuzz-test:\n    docker:\n      - image: cimg/openjdk:17.0\n    steps:\n      - checkout\n      - run: ./gradlew fuzzTest\n\n")
            if (config.includeRegression) append("  regression-test:\n    docker:\n      - image: cimg/openjdk:17.0\n    steps:\n      - checkout\n      - run: ./gradlew regressionTest\n\n")
        }
        val workflowJobs = buildList {
            if (config.includeFuzz) add("          - fuzz-test")
            if (config.includeRegression) add("          - regression-test")
        }
        return "version: 2.1\n\njobs:\n$jobs\nworkflows:\n  qa-pipeline:\n    jobs:\n${workflowJobs.joinToString("\n")}"
    }

    fun onCleared() { scope.cancel() }
}
