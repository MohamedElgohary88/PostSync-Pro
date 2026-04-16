package com.postsyncpro.di

import com.postsyncpro.data.network.AiApiClient
import com.postsyncpro.data.repository.*
import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.*
import com.postsyncpro.domain.usecase.*
import com.postsyncpro.presentation.screen.onboarding.OnboardingViewModel
import com.postsyncpro.presentation.screen.sync.SyncViewModel
import com.postsyncpro.presentation.screen.validator.ValidatorViewModel
import com.postsyncpro.presentation.screen.fuzz.FuzzViewModel
import com.postsyncpro.presentation.screen.report.ReportViewModel
import com.postsyncpro.presentation.screen.mockserver.MockServerViewModel
import com.postsyncpro.presentation.screen.testchain.TestChainViewModel
import com.postsyncpro.presentation.screen.coverage.CoverageViewModel
import com.postsyncpro.presentation.screen.breakingchange.BreakingChangeViewModel
import com.postsyncpro.presentation.screen.regression.RegressionViewModel
import com.postsyncpro.presentation.screen.dependencygraph.DependencyGraphViewModel
import com.postsyncpro.presentation.screen.autoflow.AutoFlowViewModel
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module

val appModule = module {
    // Network
    single { AiApiClient.create() }

    // Repositories
    single<PostmanSyncRepository> { PostmanSyncRepositoryImpl() }
    single<ValidationRepository> { ValidationRepositoryImpl() }
    single<SettingsRepository> { SettingsRepositoryImpl() }
    single { FuzzTestRepositoryImpl(AiApiClient.createForFuzzTesting()) as FuzzTestRepository }
    single<ReportRepository> { PdfReportRepositoryImpl() }
    single<MockServerRepository> { MockServerRepositoryImpl() }
    single<TestChainRepository> { TestChainRepositoryImpl(AiApiClient.create()) }
    single<com.postsyncpro.domain.repository.RegressionRepository> { RegressionRepositoryImpl() }

    // AI Repository - loads saved config from settings on creation
    factory<AiAnalyzerRepository> {
        val settingsRepo = get<SettingsRepository>()
        val config: AiConfig = runBlocking { settingsRepo.loadAiConfig() }
            ?: AiConfig(mode = AiMode.CLOUD, cloudConfig = CloudAiConfig(provider = CloudProvider.GEMINI, apiKey = ""))

        when (config.mode) {
            AiMode.CLOUD -> {
                val cloudConfig = config.cloudConfig
                    ?: CloudAiConfig(provider = CloudProvider.GEMINI, apiKey = "")
                val savedKey = runBlocking { settingsRepo.loadApiKey(cloudConfig.provider.name) }
                val effectiveConfig = if (savedKey != null && cloudConfig.apiKey.isBlank()) {
                    cloudConfig.copy(apiKey = savedKey)
                } else {
                    cloudConfig
                }
                CloudAiAnalyzerImpl(effectiveConfig, get())
            }
            AiMode.LOCAL -> LocalAiAnalyzerImpl(
                config.localConfig ?: LocalAiConfig(modelPath = "", modelName = "")
            )
        }
    }

    // Use Cases
    factory { MergeCollectionsUseCase(get()) }
    factory { ExtractRulesUseCase(get()) }
    factory { ValidateSrsUseCase(get()) }
    factory { RunFuzzTestsUseCase(get()) }
    factory { DetectSystemProfileUseCase() }
    factory { ManageSettingsUseCase(get()) }
    // New use cases
    factory { GenerateReportUseCase(get()) }
    factory { SwaggerDiffUseCase() }
    factory { AnalyzeDependenciesUseCase() }
    factory { AnalyzeCoverageUseCase() }
    factory { RegressionAnalysisUseCase(get()) }
    factory { AutoFlowUseCase() }

    // ViewModels
    factory { OnboardingViewModel(get(), get()) }
    factory { SyncViewModel(get()) }
    factory { ValidatorViewModel(get(), get(), get()) }
    factory { FuzzViewModel(get(), get()) }
    // New ViewModels
    factory { ReportViewModel(get()) }
    factory { MockServerViewModel(get(), get()) }
    factory { TestChainViewModel(get(), get()) }
    factory { CoverageViewModel(get(), get(), get(), get()) }
    factory { BreakingChangeViewModel(get(), get()) }
    factory { RegressionViewModel(get()) }
    factory { DependencyGraphViewModel(get(), get()) }
    factory { AutoFlowViewModel(get(), get(), get()) }
}
