package com.ergin.deepreader.feature.analysis

import com.ergin.deepreader.core.network.NetworkConnectivityObserver
import com.ergin.deepreader.domain.model.AnalysisResult
import com.ergin.deepreader.domain.model.DomainError
import com.ergin.deepreader.domain.model.ExplainAnalysis
import com.ergin.deepreader.domain.model.ExplainRequest
import com.ergin.deepreader.domain.model.SurroundingContext
import com.ergin.deepreader.domain.usecase.ExplainFragmentUseCase
import com.ergin.deepreader.domain.usecase.TranslateTextUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private val explainFragmentUseCase: ExplainFragmentUseCase = mockk(relaxed = true)
    private val translateTextUseCase: TranslateTextUseCase = mockk(relaxed = true)
    private val networkObserver: NetworkConnectivityObserver = mockk(relaxed = true)

    private lateinit var viewModel: AnalysisViewModel

    private val explainRequest = ExplainRequest(
        bookId = "sha1",
        selectedText = "selected",
        currentSentence = "Sentence",
        surroundingContext = SurroundingContext()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { networkObserver.isCurrentlyOnline() } returns true

        viewModel = AnalysisViewModel(
            explainFragmentUseCase = explainFragmentUseCase,
            translateTextUseCase = translateTextUseCase,
            networkObserver = networkObserver
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun explain_success_updatesUiStateAndHasLastSuccessfulResult() = runTest(testDispatcher) {
        val analysis = ExplainAnalysis(
            selectedText = "selected",
            contextualMeaning = "Meaning",
            phenomenonType = "metaphor",
            explanation = "Explanation",
            stylisticColoring = "literary",
            spoilerAlert = false,
            cached = false,
            processingTimeMs = 100L
        )
        coEvery { explainFragmentUseCase(any(), any()) } returns AnalysisResult.ExplainSuccess(analysis)

        viewModel.explain(explainRequest, isOnline = true)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.sheetState is AnalysisSheetState.SuccessExplain)
        assertTrue(state.hasLastSuccessfulResult)
    }

    @Test
    fun retryLastRequest_redispatchesLastExplainRequest() = runTest(testDispatcher) {
        coEvery { explainFragmentUseCase(any(), any()) } returns AnalysisResult.Failure(
            DomainError.Network("EXPLAIN")
        )

        viewModel.explain(explainRequest, isOnline = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.sheetState is AnalysisSheetState.Error)

        viewModel.retryLastRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { explainFragmentUseCase(explainRequest, any()) }
    }

    @Test
    fun fallbackToTranslate_dispatchesTranslateForLastExplainText() = runTest(testDispatcher) {
        coEvery { explainFragmentUseCase(any(), any()) } returns AnalysisResult.Failure(
            DomainError.ServerError("LLM_PROVIDER_ERROR", "Busy", "t1")
        )

        viewModel.explain(explainRequest, isOnline = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.fallbackToTranslate()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            translateTextUseCase(
                match { it.text == "selected" && it.targetLanguage == "ru" },
                any()
            )
        }
    }
}
