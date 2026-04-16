package com.postsyncpro.pro

import com.postsyncpro.presentation.screen.scheduler.*
import com.postsyncpro.presentation.screen.customfuzz.*
import com.postsyncpro.presentation.screen.notifications.*
import com.postsyncpro.presentation.screen.issuetracker.*
import com.postsyncpro.presentation.screen.apimonitor.*
import com.postsyncpro.presentation.screen.multienv.*
import com.postsyncpro.presentation.screen.cicdexport.*
import com.postsyncpro.presentation.screen.testhistory.*
import com.postsyncpro.presentation.screen.brandedreport.*
import com.postsyncpro.presentation.screen.smarttests.*
import kotlin.test.*

// ═══════════════════════════════════════════════════════════════
//  SCHEDULER VIEWMODEL TESTS
// ═══════════════════════════════════════════════════════════════

class SchedulerViewModelTest {

    private lateinit var vm: SchedulerViewModel

    @BeforeTest
    fun setup() { vm = SchedulerViewModel() }

    @AfterTest
    fun teardown() { vm.onCleared() }

    @Test
    fun `initial state has empty jobs`() {
        assertEquals(0, vm.state.value.jobs.size)
        assertFalse(vm.state.value.isCreating)
        assertNull(vm.state.value.editingJob)
    }

    @Test
    fun `startCreating sets isCreating and creates editing job`() {
        vm.startCreating()
        assertTrue(vm.state.value.isCreating)
        assertNotNull(vm.state.value.editingJob)
    }

    @Test
    fun `cancelCreating resets state`() {
        vm.startCreating()
        vm.cancelCreating()
        assertFalse(vm.state.value.isCreating)
        assertNull(vm.state.value.editingJob)
    }

    @Test
    fun `saveJob adds new job to list`() {
        vm.startCreating()
        val job = vm.state.value.editingJob!!.copy(name = "Nightly Tests")
        vm.updateEditingJob(job)
        vm.saveJob()

        assertEquals(1, vm.state.value.jobs.size)
        assertEquals("Nightly Tests", vm.state.value.jobs[0].name)
        assertFalse(vm.state.value.isCreating)
        assertNull(vm.state.value.editingJob)
    }

    @Test
    fun `saveJob without editingJob does nothing`() {
        vm.saveJob()
        assertEquals(0, vm.state.value.jobs.size)
    }

    @Test
    fun `saveJob updates existing job`() {
        vm.startCreating()
        vm.updateEditingJob(vm.state.value.editingJob!!.copy(name = "First"))
        vm.saveJob()
        val jobId = vm.state.value.jobs[0].id

        // Edit the same job
        val existingJob = vm.state.value.jobs[0].copy(name = "Updated")
        vm.startCreating()
        vm.updateEditingJob(existingJob)
        vm.saveJob()

        assertEquals(1, vm.state.value.jobs.size)
        assertEquals("Updated", vm.state.value.jobs[0].name)
    }

    @Test
    fun `deleteJob removes job from list`() {
        vm.startCreating()
        vm.updateEditingJob(vm.state.value.editingJob!!.copy(name = "To Delete"))
        vm.saveJob()
        val jobId = vm.state.value.jobs[0].id

        vm.deleteJob(jobId)
        assertEquals(0, vm.state.value.jobs.size)
    }

    @Test
    fun `deleteJob clears selectedJobId if selected`() {
        vm.startCreating()
        vm.saveJob()
        val jobId = vm.state.value.jobs[0].id
        vm.selectJob(jobId)

        vm.deleteJob(jobId)
        assertNull(vm.state.value.selectedJobId)
    }

    @Test
    fun `toggleJobEnabled flips enabled state`() {
        vm.startCreating()
        vm.saveJob()
        val jobId = vm.state.value.jobs[0].id
        assertTrue(vm.state.value.jobs[0].isEnabled)

        vm.toggleJobEnabled(jobId)
        assertFalse(vm.state.value.jobs[0].isEnabled)

        vm.toggleJobEnabled(jobId)
        assertTrue(vm.state.value.jobs[0].isEnabled)
    }

    @Test
    fun `selectJob sets selectedJobId`() {
        vm.selectJob("test-id")
        assertEquals("test-id", vm.state.value.selectedJobId)
    }

    @Test
    fun `selectJob with null clears selection`() {
        vm.selectJob("test-id")
        vm.selectJob(null)
        assertNull(vm.state.value.selectedJobId)
    }

    @Test
    fun `updateEditingJob updates job properties`() {
        vm.startCreating()
        val job = vm.state.value.editingJob!!.copy(
            name = "Updated",
            testType = TestType.FUZZ,
            interval = ScheduleInterval.WEEKLY
        )
        vm.updateEditingJob(job)
        assertEquals("Updated", vm.state.value.editingJob?.name)
        assertEquals(TestType.FUZZ, vm.state.value.editingJob?.testType)
        assertEquals(ScheduleInterval.WEEKLY, vm.state.value.editingJob?.interval)
    }

    @Test
    fun `saved job has nextRun set`() {
        vm.startCreating()
        vm.saveJob()
        assertNotNull(vm.state.value.jobs[0].nextRun)
    }

    @Test
    fun `all TestType values exist`() {
        val types = TestType.entries
        assertEquals(4, types.size)
        assertTrue(TestType.REGRESSION in types)
        assertTrue(TestType.FUZZ in types)
        assertTrue(TestType.CHAIN in types)
        assertTrue(TestType.COVERAGE in types)
    }

    @Test
    fun `all ScheduleInterval values exist`() {
        val intervals = ScheduleInterval.entries
        assertEquals(6, intervals.size)
    }
}

// ═══════════════════════════════════════════════════════════════
//  CUSTOM FUZZ RULES VIEWMODEL TESTS
// ═══════════════════════════════════════════════════════════════

class CustomFuzzRulesViewModelTest {

    private lateinit var vm: CustomFuzzRulesViewModel

    @BeforeTest
    fun setup() { vm = CustomFuzzRulesViewModel() }

    @AfterTest
    fun teardown() { vm.onCleared() }

    @Test
    fun `initial state has no rules`() {
        assertEquals(0, vm.state.value.rules.size)
        assertFalse(vm.state.value.isCreating)
    }

    @Test
    fun `startCreating initializes editing`() {
        vm.startCreating()
        assertTrue(vm.state.value.isCreating)
        assertNotNull(vm.state.value.editingRule)
    }

    @Test
    fun `saveRule adds to list`() {
        vm.startCreating()
        vm.updateEditingRule(vm.state.value.editingRule!!.copy(name = "SQL Test", mutationType = MutationType.SQL_INJECTION))
        vm.saveRule()

        assertEquals(1, vm.state.value.rules.size)
        assertEquals("SQL Test", vm.state.value.rules[0].name)
        assertEquals(MutationType.SQL_INJECTION, vm.state.value.rules[0].mutationType)
    }

    @Test
    fun `saveRule without editing does nothing`() {
        vm.saveRule()
        assertEquals(0, vm.state.value.rules.size)
    }

    @Test
    fun `deleteRule removes from list`() {
        vm.startCreating()
        vm.saveRule()
        val ruleId = vm.state.value.rules[0].id
        vm.deleteRule(ruleId)
        assertEquals(0, vm.state.value.rules.size)
    }

    @Test
    fun `toggleRule flips enabled state`() {
        vm.startCreating()
        vm.saveRule()
        val ruleId = vm.state.value.rules[0].id
        assertTrue(vm.state.value.rules[0].isEnabled)

        vm.toggleRule(ruleId)
        assertFalse(vm.state.value.rules[0].isEnabled)
    }

    @Test
    fun `selectRule sets selectedRuleId`() {
        vm.selectRule("rule-1")
        assertEquals("rule-1", vm.state.value.selectedRuleId)
    }

    @Test
    fun `all MutationType values exist`() {
        assertEquals(6, MutationType.entries.size)
    }

    @Test
    fun `all RuleSeverity values exist`() {
        assertEquals(3, RuleSeverity.entries.size)
    }

    @Test
    fun `saveRule updates existing rule`() {
        vm.startCreating()
        vm.saveRule()
        val rule = vm.state.value.rules[0].copy(name = "Updated Rule")
        vm.startCreating()
        vm.updateEditingRule(rule)
        vm.saveRule()

        assertEquals(1, vm.state.value.rules.size)
        assertEquals("Updated Rule", vm.state.value.rules[0].name)
    }
}

// ═══════════════════════════════════════════════════════════════
//  NOTIFICATIONS VIEWMODEL TESTS
// ═══════════════════════════════════════════════════════════════

class NotificationsViewModelTest {

    private lateinit var vm: NotificationsViewModel

    @BeforeTest
    fun setup() { vm = NotificationsViewModel() }

    @AfterTest
    fun teardown() { vm.onCleared() }

    @Test
    fun `initial state has no channels`() {
        assertEquals(0, vm.state.value.channels.size)
        assertFalse(vm.state.value.isAdding)
    }

    @Test
    fun `startAdding creates editing channel`() {
        vm.startAdding()
        assertTrue(vm.state.value.isAdding)
        assertNotNull(vm.state.value.editingChannel)
    }

    @Test
    fun `saveChannel adds to list`() {
        vm.startAdding()
        vm.updateEditing(vm.state.value.editingChannel!!.copy(
            provider = NotificationProvider.SLACK,
            webhookUrl = "https://hooks.slack.com/test"
        ))
        vm.saveChannel()

        assertEquals(1, vm.state.value.channels.size)
        assertEquals(NotificationProvider.SLACK, vm.state.value.channels[0].provider)
    }

    @Test
    fun `deleteChannel removes from list`() {
        vm.startAdding()
        vm.saveChannel()
        val id = vm.state.value.channels[0].id
        vm.deleteChannel(id)
        assertEquals(0, vm.state.value.channels.size)
    }

    @Test
    fun `toggleChannel flips enabled`() {
        vm.startAdding()
        vm.saveChannel()
        val id = vm.state.value.channels[0].id
        assertTrue(vm.state.value.channels[0].isEnabled)

        vm.toggleChannel(id)
        assertFalse(vm.state.value.channels[0].isEnabled)
    }

    @Test
    fun `all NotificationProvider values exist`() {
        assertEquals(4, NotificationProvider.entries.size)
        assertTrue(NotificationProvider.SLACK in NotificationProvider.entries)
        assertTrue(NotificationProvider.TEAMS in NotificationProvider.entries)
        assertTrue(NotificationProvider.DISCORD in NotificationProvider.entries)
        assertTrue(NotificationProvider.EMAIL in NotificationProvider.entries)
    }

    @Test
    fun `default notification settings`() {
        val channel = NotificationChannel()
        assertTrue(channel.onRegression)
        assertTrue(channel.onPassRateDrop)
        assertFalse(channel.onScheduleComplete)
        assertTrue(channel.onBreakingChange)
        assertEquals(80, channel.threshold)
    }
}

// ═══════════════════════════════════════════════════════════════
//  CI/CD EXPORT VIEWMODEL TESTS
// ═══════════════════════════════════════════════════════════════

class CiCdExportViewModelTest {

    private lateinit var vm: CiCdExportViewModel

    @BeforeTest
    fun setup() { vm = CiCdExportViewModel() }

    @AfterTest
    fun teardown() { vm.onCleared() }

    @Test
    fun `initial state has default config`() {
        assertNotNull(vm.state.value.config)
        assertFalse(vm.state.value.isCopied)
    }

    @Test
    fun `generateConfig produces non-empty output`() {
        vm.generateConfig()
        assertTrue(vm.state.value.generatedConfig!!.isNotEmpty())
    }

    @Test
    fun `all CiCdProvider values exist`() {
        assertEquals(4, PipelineProvider.entries.size)
    }
}

// ═══════════════════════════════════════════════════════════════
//  MULTI-ENV VIEWMODEL TESTS
// ═══════════════════════════════════════════════════════════════

class MultiEnvViewModelTest {

    private lateinit var vm: MultiEnvViewModel

    @BeforeTest
    fun setup() { vm = MultiEnvViewModel() }

    @AfterTest
    fun teardown() { vm.onCleared() }

    @Test
    fun `initial state has empty environments`() {
        assertTrue(vm.state.value.environments.isEmpty())
    }

    @Test
    fun `startAdding creates editing environment`() {
        vm.startAdding()
        assertTrue(vm.state.value.isAdding)
        assertNotNull(vm.state.value.editingEnv)
    }

    @Test
    fun `saveEnvironment adds to list`() {
        val initialCount = vm.state.value.environments.size
        vm.startAdding()
        vm.saveEnvironment()
        assertTrue(vm.state.value.environments.size >= initialCount)
    }

    @Test
    fun `saveEnvironment and deleteEnvironment work together`() {
        vm.startAdding()
        vm.saveEnvironment()
        val id = vm.state.value.environments.first().id
        assertEquals(1, vm.state.value.environments.size)
        vm.deleteEnvironment(id)
        assertEquals(0, vm.state.value.environments.size)
    }
}

// ═══════════════════════════════════════════════════════════════
//  ISSUE TRACKER VIEWMODEL TESTS
// ═══════════════════════════════════════════════════════════════

class IssueTrackerViewModelTest {

    private lateinit var vm: IssueTrackerViewModel

    @BeforeTest
    fun setup() { vm = IssueTrackerViewModel() }

    @AfterTest
    fun teardown() { vm.onCleared() }

    @Test
    fun `initial state is not connected`() {
        assertNotNull(vm.state.value.config)
    }

    @Test
    fun `all IssueProvider values exist`() {
        assertEquals(3, IssueProvider.entries.size)
    }
}

// ═══════════════════════════════════════════════════════════════
//  API MONITOR VIEWMODEL TESTS
// ═══════════════════════════════════════════════════════════════

class ApiMonitorViewModelTest {

    private lateinit var vm: ApiMonitorViewModel

    @BeforeTest
    fun setup() { vm = ApiMonitorViewModel() }

    @AfterTest
    fun teardown() { vm.onCleared() }

    @Test
    fun `initial state has empty endpoints`() {
        assertTrue(vm.state.value.endpoints.isEmpty())
    }

    @Test
    fun `startAdding initializes editing`() {
        vm.startAdding()
        assertTrue(vm.state.value.isAdding)
        assertNotNull(vm.state.value.editingEndpoint)
    }

    @Test
    fun `saveEndpoint adds and deleteEndpoint removes`() {
        vm.startAdding()
        vm.saveEndpoint()
        val id = vm.state.value.endpoints.first().id
        assertEquals(1, vm.state.value.endpoints.size)
        vm.deleteEndpoint(id)
        assertEquals(0, vm.state.value.endpoints.size)
    }
}

// ═══════════════════════════════════════════════════════════════
//  TEST HISTORY VIEWMODEL TESTS
// ═══════════════════════════════════════════════════════════════

class TestHistoryViewModelTest {

    private lateinit var vm: TestHistoryViewModel

    @BeforeTest
    fun setup() { vm = TestHistoryViewModel() }

    @AfterTest
    fun teardown() { vm.onCleared() }

    @Test
    fun `initial state has sample runs`() {
        assertTrue(vm.state.value.runs.isNotEmpty())
        assertTrue(vm.state.value.totalRuns > 0)
    }

    @Test
    fun `average pass rate is between 0 and 100`() {
        assertTrue(vm.state.value.avgPassRate in 0.0..100.0)
    }

    @Test
    fun `best run rate is at least as high as worst`() {
        assertTrue(vm.state.value.bestRunRate >= vm.state.value.worstRunRate)
    }

    @Test
    fun `setFilter changes filter`() {
        vm.setFilter(TestFilter.FUZZ)
        assertEquals(TestFilter.FUZZ, vm.state.value.filter)
    }
}

// ═══════════════════════════════════════════════════════════════
//  BRANDED REPORT VIEWMODEL TESTS
// ═══════════════════════════════════════════════════════════════

class BrandedReportViewModelTest {

    private lateinit var vm: BrandedReportViewModel

    @BeforeTest
    fun setup() { vm = BrandedReportViewModel() }

    @AfterTest
    fun teardown() { vm.onCleared() }

    @Test
    fun `initial state has default config`() {
        assertNotNull(vm.state.value.config)
        assertFalse(vm.state.value.showPreview)
    }

    @Test
    fun `togglePreview flips state`() {
        vm.togglePreview()
        assertTrue(vm.state.value.showPreview)
        vm.togglePreview()
        assertFalse(vm.state.value.showPreview)
    }
}

// ═══════════════════════════════════════════════════════════════
//  SMART TESTS VIEWMODEL TESTS
// ═══════════════════════════════════════════════════════════════

class SmartTestsViewModelTest {

    private lateinit var vm: SmartTestsViewModel

    @BeforeTest
    fun setup() { vm = SmartTestsViewModel() }

    @AfterTest
    fun teardown() { vm.onCleared() }

    @Test
    fun `initial state has no scenarios`() {
        assertEquals(0, vm.state.value.scenarios.size)
        assertFalse(vm.state.value.isGenerating)
    }

    @Test
    fun `setCategory changes filter`() {
        vm.setCategory(TestCategory.SECURITY)
        assertEquals(TestCategory.SECURITY, vm.state.value.selectedCategory)
    }

    @Test
    fun `setCategory to null shows all`() {
        vm.setCategory(TestCategory.SECURITY)
        vm.setCategory(null)
        assertNull(vm.state.value.selectedCategory)
    }
}
