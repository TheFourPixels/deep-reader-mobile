# 🤖 AI Agent TDD Playbook: DeepReader Android MVP

**Role:** Autonomous Senior Android Engineer (Kotlin, Jetpack Compose, Material 3, Clean Architecture, Strict TDD)  
**Package:** `com.ergin.deepreader`  
**Primary Spec (Single Source of Truth):** `docs/mobile/ANDROID_APP_OPENSPEC.md` (v1.1.0)  
**Backend Contracts:** `docs/specs/02_openapi.yaml`, `docs/specs/03_ai_logic.md`, `docs/mobile/ANDROID_INTEGRATION_GUIDE.md`

---

## 0. Agent Operating Rules (Non-Negotiable Directives)

1. **Strict TDD Loop (`RED -> GREEN -> REFACTOR`):**
   - NEVER write production logic before writing a failing test that defines the expected behavior.
   - Run `./gradlew testDebugUnitTest` after every `RED` and `GREEN` step.
   - Proceed to the next task ONLY when all existing tests are green.
2. **Contract Freeze Compliance:**
   - Do NOT alter JSON field names (`snake_case` is mandatory) or enum literals defined in `docs/specs/02_openapi.yaml`.
   - Do NOT implement BPE tokenization (`jtokkit`) on the Android client; the client enforces **character length limits** (`selected_text <= 500` for Explain, `text <= 5000` for Translate, `current_sentence <= 2000`, `paragraph <= 4000`, `chapter_context <= 2000`, `sentences_before/after <= 1000` max 2 items) and **Viewport Spoiler Guardrail**.
3. **Mobile Design & Performance Constraints (`/mobile-design`):**
   - NEVER use `ScrollView` or `Column(Modifier.verticalScroll())` for book chapters. Always use `LazyColumn` with stable keys `key = { it.paragraphIndex }`.
   - Every interactive control (`IconButton`, floating toolbar action, retry button, copy `trace_id`) MUST have a minimum touch target of `48.dp × 48.dp` and `>= 8.dp` spacing.
   - Apply `Modifier.systemGestureExclusion()` on text selection handles near screen edges.
   - Use `FlowRow` for badges in `AnalysisBottomSheet` to support `fontScale = 2.0f` (200% font scaling).
   - Never hardcode or bundle external LLM/DeepL API keys in the Android app. Disable HTTP body logging when `!BuildConfig.DEBUG`.

---

## 1. Project Architecture & Package Tree

```text
com.ergin.deepreader
├── core/
│   ├── designsystem/   # Theme (Light/Sepia/Dark), ExtendedReaderColors (CompositionLocal), Typography
│   ├── database/       # Room (BookEntity, ReadingPositionEntity, AiCacheEntity, BookDao, PositionDao, LocalAiCacheDao)
│   ├── datastore/      # ReaderPreferencesDataStore (fontSp, readerTheme, targetLanguage)
│   └── network/        # DeepReaderApi (Retrofit), DTOs (@Serializable), NetworkErrorMapper, NetworkMonitor
├── domain/
│   ├── model/          # Book, ReadingPosition, ChapterContent, ParagraphBlock, SentenceSpan, AnalysisAction
│   ├── repository/     # BookRepository, AnalysisRepository, PreferencesRepository
│   └── usecase/        # ImportBookUseCase, RestoreReadingPositionUseCase, SaveReadingPositionUseCase,
│                       # EpubContextExtractorUseCase, ExplainFragmentUseCase, TranslateTextUseCase
└── feature/
    ├── library/        # LibraryScreen, LibraryViewModel, LibraryUiState
    ├── reader/         # ReaderScreen, ReaderViewModel, EpubParserEngine, SelectionFloatingToolbar
    └── analysis/       # AnalysisBottomSheet, AnalysisViewModel, AnalysisUiState
```

---

## Phase 0: Bootstrap & Gradle Configuration

### Checklist — Phase 0
- [ ] Configure `gradle/libs.versions.toml` with Kotlin 2.x, Compose BOM, Material 3, Hilt, Room + KSP, Retrofit 2 + `kotlinx-serialization-json`, OkHttp, DataStore Preferences, Coroutines.
- [ ] Add test dependencies: `junit`, `truth` (or `kotest-assertions`), `kotlinx-coroutines-test`, `turbine`, `mockk`, `okhttp3:mockwebserver`, `androidx.room:room-testing`, `androidx.compose.ui:ui-test-junit4`.
- [ ] Implement `ExtendedReaderColors` (`CompositionLocal`) supporting **Light**, **Sepia**, and **Dark** themes with WCAG AA (`>= 4.5:1`) contrast for all 9 `phenomenon_type` badges and `warningContainer` (`spoiler_alert`).
- [ ] Verify clean build: `./gradlew assembleDebug testDebugUnitTest`.

---

## Phase 1: Local Storage, Single-Pass SHA-256 Import & Library (FT-1 – FT-6, FT-30, FT-31)

### Step 1.1: Single-Pass File Import & ZIP/OPF Validator (`EpubFileImporter`)
- [ ] **[RED]** Create `src/test/.../EpubFileImporterTest.kt`:
  - `copyAndHash_validEpubStream_writesStagingFileAndReturnsExpectedSha256()`
  - `copyAndHash_missingContainerXmlOrOpf_deletesStagingFileAndThrowsInvalidFormat()`
  - `copyAndHash_ioExceptionDuringRead_cleansUpPartialStagingFile()`
- [ ] **[GREEN]** Implement `EpubFileImporter` using `DigestInputStream(rawStream, MessageDigest.getInstance("SHA-256"))` with an `8192`-byte buffer and ZIP entry validation (`META-INF/container.xml` + `.opf`).
- [ ] **[REFACTOR]** Extract `FileStorageGateway` interface for pure JVM testing.

### Step 1.2: Room Schema & Deduplication Queries (`BookDao`)
- [ ] **[RED]** Create `src/androidTest/.../BookDaoTest.kt` (In-Memory Room):
  - `insertAndObserveBooks_orderedByLastOpenedDescThenAddedAtDesc()`
  - `getBookById_matchingSha256_returnsBookEntity()`
  - `findBooksByTitleAndAuthor_caseAndWhitespaceInsensitive_returnsMatches()` (FT-6.3)
- [ ] **[GREEN]** Implement `@Entity(tableName = "books") data class BookEntity` and `BookDao`.
- [ ] **[REFACTOR]** Add index `@Index(value = ["title", "author"])`.

### Step 1.3: `ImportBookUseCase` & `LibraryViewModel`
- [ ] **[RED]** Create `src/test/.../ImportBookUseCaseTest.kt`:
  - `invoke_newValidEpub_movesStagingToBooksDirAndInsertsToDb()`
  - `invoke_duplicateSha256_deletesStagingAndReturnsExactDuplicate()` (FT-6.1, FT-6.2)
  - `invoke_differentSha256SameTitleAndAuthor_returnsMetadataCollisionWarning()` (FT-6.3)
  - `invoke_corruptedOrMissingOpfMetadata_fallsBackToFileNameAndStillDeduplicatesBySha256()` (FT-6.3)
- [ ] **[GREEN]** Implement `ImportBookUseCase` and `BookRepositoryImpl`.
- [ ] **[RED]** Create `src/test/.../LibraryViewModelTest.kt` (using `Turbine`):
  - `uiState_whenNoBooks_emitsEmptyState()`
  - `onImportStarted_emitsImportingStateAndBlocksConcurrentImports()`
  - `onBookClicked_whenLocalFileDeleted_emitsFileMissingErrorWithoutCrash()` (FT-31)
- [ ] **[GREEN]** Implement `LibraryViewModel` and `LibraryScreen` (`Empty` state with `56.dp` primary CTA in thumb zone, `Importing` state, `Content` adaptive `LazyVerticalGrid`, `FileMissingError` dialog).

---

## Phase 2: EPUB Parser, Reader Engine & Resilient Positioning (FT-7 – FT-29, FT-32, FT-33)

### Step 2.1: XHTML Chapter Parser & Sentence Tokenizer (`EpubParserEngine`)
- [ ] **[RED]** Create `src/test/.../EpubParserEngineTest.kt`:
  - `parseChapter_validXhtml_extractsOrderedParagraphBlocksWithXhtmlIds()`
  - `tokenizeSentences_handlesAbbreviationsQuotesAndEllipsisCorrectly()`
  - `parseChapter_malformedNodeOrBrokenImage_skipsCorruptedElementAndParsesRemainingText()` (FT-33)
- [ ] **[GREEN]** Implement `EpubParserEngine` producing `ChapterContent(chapterIndex, title, paragraphs: List<ParagraphBlock>)` and `SentenceSpan(startOffset, endOffset, epubCfi)`.
- [ ] **[REFACTOR]** Ensure all parsing runs on `Dispatchers.IO`.

### Step 2.2: Reading Position Fallback Chain (`RestoreReadingPositionUseCase` & `SaveReadingPositionUseCase`)
- [ ] **[RED]** Create `src/test/.../ReadingPositionUseCaseTest.kt`:
  - `restore_firstTimeOpeningBook_returnsStartOfBook(0, 0, 0)` (FT-8)
  - `restore_validEpubCfi_resolvesExactChapterParagraphAndCharOffset()` (FT-15.1, FT-15.3)
  - `restore_invalidCfi_fallsBackToSavedChapterParagraphAndCharOffset()` (FT-15.4)
  - `restore_paragraphIndexOutOfBounds_coercesToNearestValidParagraph()` (FT-15.4)
  - `restore_completelyUnrecoverablePosition_opensFromBeginningWithoutTechnicalError()` (FT-15.4)
  - `restore_neverUsesScrollProgressPercentageAsPositionAnchor()` (FT-15.5)
  - `save_whenDatabaseThrowsException_catchesErrorAndKeepsLastSavedPosition()` (FT-21, FT-32)
- [ ] **[GREEN]** Implement `ReadingPositionEntity`, `PositionDao`, `RestoreReadingPositionUseCase`, and `SaveReadingPositionUseCase`.

### Step 2.3: `ReaderViewModel` & `ReaderScreen`
- [ ] **[RED]** Create `src/test/.../ReaderViewModelTest.kt`:
  - `onScrollChanged_debounces500msBeforeSavingPosition()`
  - `chapterNavigation_atFirstChapter_disablesPreviousButton()` (FT-13)
  - `chapterNavigation_atLastChapter_disablesNextButton()` (FT-13)
  - `onFontSizeChanged_updatesDataStoreAndPreservesLogicalParagraphAnchor()` (FT-22, FT-23)
  - `onBookOpened_activatesReturnHighlightFor2000msThenClears()`
- [ ] **[GREEN]** Implement `ReaderViewModel` and `ReaderScreen` using `LazyColumn(state = lazyListState, key = { it.paragraphIndex })`, `BottomAppBar` controls in the thumb zone, and `ModalNavigationDrawer` for TOC.

---

## Phase 3: Context Extractor & Strict Spoiler Guardrail (FT-64 – FT-87)

### Step 3.1: `EpubContextExtractorUseCase` (100% Unit Test Coverage)
- [ ] **[RED]** Create `src/test/.../EpubContextExtractorUseCaseTest.kt`:
  - `extract_midParagraphAllVisible_returnsUpTo2BeforeAndUpTo2After()` (FT-66, FT-67.1)
  - `extract_selectionNearBottomOfScreen_excludesSentencesWhereBottomYExceedsViewportBottomY()` (**Strict Spoiler Guardrail**, FT-67.1, FT-70)
  - `extract_paragraphPartiallyBelowViewport_truncatesParagraphAtLastVisibleSentence()` (FT-67.3, FT-70)
  - `extract_atStartOfChapter_returnsEmptySentencesBeforeWithoutPlaceholders()` (FT-66, FT-78)
  - `extract_atEndOfChapter_returnsEmptySentencesAfterAndNeverReadsNextChapter()` (FT-67.2, FT-79, FT-83)
  - `extract_selectedTextOver500Chars_returnsSelectionTooLongForAiButAllowsTranslateUpTo5000()` (FT-39, FT-40)
  - `extract_selectedTextOver5000Chars_returnsSelectionTooLongForBoth()`
  - `extract_longSurroundingFields_truncatesParagraphTo4000AndChapterContextTo2000WithoutTouchingCore()` (FT-77)
  - `extract_unpunctuatedTextBlock_fallsBackToVisibleWindowAroundSelection()` (FT-85, FT-86)
- [ ] **[GREEN]** Implement `EpubContextExtractorUseCase` operating on `ViewportLayoutSnapshot` (`viewportBottomY` + `ParagraphLayoutInfo` from `onTextLayout` and `Modifier.onGloballyPositioned`).
- [ ] **[REFACTOR]** Verify zero Android UI dependencies inside `EpubContextExtractorUseCase`.

---

## Phase 4: Network Layer, Local Cache & `AnalysisViewModel` (FT-41 – FT-63)

### Step 4.1: OpenAPI DTOs, Retrofit & Error Mapping (`NetworkRepositoryTest`)
- [ ] **[RED]** Create `src/test/.../NetworkRepositoryTest.kt` (with `MockWebServer`):
  - `explain_serializesSnakeCaseRequestAndParses200Response()`
  - `translate_withNullSourceLanguage_parsesDetectedSourceLanguageAndTranslation()`
  - `errorResponses_mapsAll8OpenApiErrorCodesAndExtractsTraceId()` (`INVALID_INPUT`, `TEXT_TOO_LONG`, `EMPTY_AI_RESPONSE`, `RATE_LIMIT_EXCEEDED`, `LLM_PROVIDER_ERROR`, `TRANSLATION_FAILED`, `EXTERNAL_PROVIDER_TIMEOUT`, `INTERNAL_ERROR`)
- [ ] **[GREEN]** Implement DTOs (`SurroundingContextDto`, `ExplainRequestDto`, `ExplainResponseDto`, `TranslateRequestDto`, `TranslateResponseDto`, `ErrorResponseDto`, `HealthResponseDto`), `DeepReaderApi`, and `NetworkRepositoryImpl`.

### Step 4.2: Local Offline Cache & UseCases (`ExplainFragmentUseCase` & `TranslateTextUseCase`)
- [ ] **[RED]** Create `src/test/.../ExplainAndTranslateUseCaseTest.kt`:
  - `invoke_whenOfflineAndPresentInRoomCache_returnsCachedSuccessImmediately()`
  - `invoke_whenOfflineAndNotInCache_doesNotCallNetworkAndReturnsOfflineStateWithActionSpecificMessage()` (FT-27, FT-41.4, FT-55)
  - `invoke_whenOnlineAnd200Ok_savesResultToRoomCacheAndReturnsSuccess()`
- [ ] **[GREEN]** Implement `AiCacheEntity`, `LocalAiCacheDao`, `ExplainFragmentUseCase`, and `TranslateTextUseCase`.

### Step 4.3: Idempotency, Session Reopen & State Machine (`AnalysisViewModelTest`)
- [ ] **[RED]** Create `src/test/.../AnalysisViewModelTest.kt` (using `Turbine`):
  - `onActionClicked_whileLoading_ignoresDuplicateClicksAndExecutesSingleNetworkCall()` (FT-46)
  - `onReopenLastResult_withinSameSession_restoresBottomSheetStateWithoutNetworkCall()` (FT-61)
  - `onLlmProviderError502_exposesSwitchToTranslateAction()`
- [ ] **[GREEN]** Implement `AnalysisViewModel` and `AnalysisUiState`.

---

## Phase 5: Mobile UI, Selection Toolbar, Bottom Sheet & Accessibility (`/mobile-design`)

### Step 5.1: Compose UI & Accessibility Tests (`ReaderAndAnalysisUiTest`)
- [ ] **[RED]** Create `src/androidTest/.../ReaderAndAnalysisUiTest.kt`:
  - `selectionToolbar_whenSelectedTextIs600Chars_showsInlineAiLimitHintWithoutClearingSelectionAndKeepsTranslateEnabled()` (FT-37, FT-40)
  - `analysisBottomSheet_whenSpoilerAlertIsTrue_hidesExplanationBehindWarningAccordionUntilClicked()`
  - `analysisBottomSheet_at200PercentFontScale_wrapsBadgesInFlowRowWithoutClipping()`
  - `errorState_clickingCopyTraceId_copiesTraceIdToClipboard()`
  - `backNavigationHierarchy_dismissesSheetFirst_thenClearsSelection_thenClosesDrawer_thenReturnsToLibrary()` (Section 6.1)
- [ ] **[GREEN]** Implement `SelectionFloatingToolbar` (with `Modifier.systemGestureExclusion()`, `WindowInsets.safeDrawing` clamping, `48.dp` targets) and `AnalysisBottomSheet` (with auto-scroll of `LazyColumn` above the sheet per FT-42, Shimmer skeleton, `FlowRow` badges, and `WarningContainer` spoiler accordion).
- [ ] **[REFACTOR]** Verify TalkBack `liveRegion = LiveRegionMode.Polite` announcements and `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` (Reduce Motion) handling.

---

## 6. Final Verification Gate (Run Before Declaring Task Complete)

- [ ] `./gradlew testDebugUnitTest` passes with 0 failures.
- [ ] `./gradlew connectedDebugAndroidTest` (or Robolectric UI suite) passes all 15 acceptance scenarios from Section 7 of `docs/mobile/ANDROID_APP_OPENSPEC.md`.
- [ ] `./gradlew lintDebug` reports no high-severity accessibility or performance warnings.
- [ ] Confirm no API keys or secrets exist in source code or `BuildConfig`, and HTTP body logging is gated behind `BuildConfig.DEBUG`.
