package com.ergin.deepreader.feature.analysis

import app.cash.turbine.test
import com.ergin.deepreader.core.network.model.ExplainRequestDto
import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.core.network.model.SurroundingContextDto
import com.ergin.deepreader.core.network.model.TranslateRequestDto
import com.ergin.deepreader.core.network.model.TranslateResponseDto
import com.ergin.deepreader.domain.model.AnalysisResult
import com.ergin.deepreader.domain.usecase.ExplainFragmentUseCase
import com.ergin.deepreader.domain.usecase.TranslateTextUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var explainFragmentUseCase: ExplainFragmentUseCase
    private lateinit var translateTextUseCase: TranslateTextUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        explainFragmentUseCase = mockk()
        translateTextUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun explain_whileLoading_ignoresDuplicateClicksAndExecutesSingleNetworkCall() = runTest {
        val request = createSampleExplainRequest()
        val response = ExplainResponseDto("text", "meaning", "metaphor", "exp", "literary", false, false, 1000)

        coEvery { explainFragmentUseCase(request, true) } returns AnalysisResult.ExplainSuccess(response)

        val viewModel = AnalysisViewModel(explainFragmentUseCase, translateTextUseCase)

        viewModel.explain(request, isOnline = true)
        viewModel.explain(request, isOnline = true) // Duplicate click while loading

        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is AnalysisUiState.SuccessExplain)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { explainFragmentUseCase(request, true) }
    }

    @Test
    fun reopenLastResult_withinSameSession_restoresStateWithoutNetworkCall() = runTest {
        val request = createSampleExplainRequest()
        val response = ExplainResponseDto("text", "meaning", "metaphor", "exp", "literary", false, false, 1000)

        coEvery { explainFragmentUseCase(request, true) } returns AnalysisResult.ExplainSuccess(response)

        val viewModel = AnalysisViewModel(explainFragmentUseCase, translateTextUseCase)

        viewModel.explain(request, isOnline = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDismissSheet()
        viewModel.onReopenLastResult()

        viewModel.uiState.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is AnalysisUiState.SuccessExplain)
            cancelAndIgnoreRemainingEvents()
        }

        // Only 1 initial call, no new network call on reopen
        coVerify(exactly = 1) { explainFragmentUseCase(request, true) }
    }

    private fun createSampleExplainRequest() = ExplainRequestDto(
        bookId = "sha123",
        selectedText = "clocks were striking thirteen",
        currentSentence = "It was a bright cold day in April, and the clocks were striking thirteen.",
        surroundingContext = SurroundingContextDto()
    )
}