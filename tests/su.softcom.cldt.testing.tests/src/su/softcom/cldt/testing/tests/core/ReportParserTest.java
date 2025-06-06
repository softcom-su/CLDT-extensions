package su.softcom.cldt.testing.tests.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

import su.softcom.cldt.testing.core.FunctionCoverageAnalyzer;
import su.softcom.cldt.testing.core.FunctionNameProcessor;
import su.softcom.cldt.testing.core.ReportParser;
import su.softcom.cldt.testing.ui.CoverageDataManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ReportParserTest {

	@Mock
	private ILog logger;

	@Mock
	private CoverageDataManager coverageDataManager;

	private AutoCloseable closeable;
	private MockedStatic<Platform> platformMock;
	private MockedStatic<CoverageDataManager> coverageDataManagerMock;

	@BeforeEach
	void setUp() {
		closeable = MockitoAnnotations.openMocks(this);

		platformMock = mockStatic(Platform.class);
		platformMock.when(() -> Platform.getLog(ReportParser.class)).thenReturn(logger);
		platformMock.when(() -> Platform.getLog(FunctionNameProcessor.class)).thenReturn(logger);
		platformMock.when(() -> Platform.getLog(FunctionCoverageAnalyzer.class)).thenReturn(logger);

		coverageDataManagerMock = mockStatic(CoverageDataManager.class);
		coverageDataManagerMock.when(CoverageDataManager::getInstance).thenReturn(coverageDataManager);
	}

	@AfterEach
	void tearDown() throws Exception {
		closeable.close();
		platformMock.close();
		coverageDataManagerMock.close();
	}

	@Test
	void testParseLcovReport_ValidSingleFile() {
		List<String> reportLines = Arrays.asList("SF:/path/to/file.c", "DA:1,10", "DA:2,0", "LF:2", "LH:1",
				"end_of_record");

		ReportParser.CoverageResult result = ReportParser.parseLcovReport(reportLines);

		Map<String, Map<String, Object[]>> coverageResults = result.fileCoverage();
		assertEquals(1, coverageResults.size());
		Map<String, Object[]> fileCoverage = coverageResults.get("/path/to/file.c");
		assertNotNull(fileCoverage);
		assertEquals("50.00%", fileCoverage.get("Lines")[1]);
	}

	@Test
	void testParseLcovReport_MultipleFiles() {
		List<String> reportLines = Arrays.asList("SF:/path/to/file1.c", "DA:1,10", "LF:1", "LH:1", "end_of_record",
				"SF:/path/to/file2.c", "DA:1,0", "LF:1", "LH:0", "end_of_record");

		ReportParser.CoverageResult result = ReportParser.parseLcovReport(reportLines);

		Map<String, Map<String, Object[]>> coverageResults = result.fileCoverage();
		assertEquals(2, coverageResults.size());
		assertEquals("100.00%", coverageResults.get("/path/to/file1.c").get("Lines")[1]);
		assertEquals("0.00%", coverageResults.get("/path/to/file2.c").get("Lines")[1]);
	}

	@Test
	void testParseLcovReport_EmptyReport() {
		List<String> reportLines = Collections.emptyList();
		ReportParser.CoverageResult result = ReportParser.parseLcovReport(reportLines);

		assertTrue(result.fileCoverage().isEmpty());
		assertTrue(result.lineCoverage().isEmpty());
		assertTrue(result.branchCoverage().isEmpty());
		assertTrue(result.functionCoverage().isEmpty());
	}

	@Test
	void testParseLcovReport_InvalidLineCoverageFormat() {
		List<String> reportLines = Arrays.asList("SF:/path/to/file.c", "DA:invalid,10", "LF:1", "LH:0",
				"end_of_record");

		ReportParser.CoverageResult result = ReportParser.parseLcovReport(reportLines);

		Map<String, Map<String, Object[]>> coverageResults = result.fileCoverage();
		assertEquals(1, coverageResults.size());
		assertEquals("0.00%", coverageResults.get("/path/to/file.c").get("Lines")[1]);
		assertTrue(result.lineCoverage().get("/path/to/file.c").isEmpty());
	}

	@Test
	void testParseLcovReport_InvalidBranchCoverageFormat() {
		List<String> reportLines = Arrays.asList("SF:/path/to/file.c", "BRDA:invalid,0,0,1", "BRF:1", "BRH:0",
				"end_of_record");

		ReportParser.CoverageResult result = ReportParser.parseLcovReport(reportLines);

		Map<String, Map<String, Object[]>> coverageResults = result.fileCoverage();
		assertEquals(1, coverageResults.size());
		assertEquals("0.00%", coverageResults.get("/path/to/file.c").get("Branches")[1]);
		assertTrue(result.branchCoverage().get("/path/to/file.c").isEmpty());
	}

	@Test
	void testParseLcovReport_SkippedBranchCoverageInvalidHits() {
		List<String> reportLines = Arrays.asList("SF:/path/to/file.c", "BRDA:1,0,0,-", "BRF:1", "BRH:0",
				"end_of_record");

		ReportParser.CoverageResult result = ReportParser.parseLcovReport(reportLines);

		Map<String, Map<String, Object[]>> coverageResults = result.fileCoverage();
		assertEquals(1, coverageResults.size());
		assertEquals("0.00%", coverageResults.get("/path/to/file.c").get("Branches")[1]);
		assertTrue(result.branchCoverage().get("/path/to/file.c").isEmpty());
	}

	@Test
	void testParseLcovReport_InvalidFunctionCoverageFormat() {
		List<String> reportLines = Arrays.asList("SF:/path/to/file.c", "FN:invalid,main", "FNF:1", "FNH:0",
				"end_of_record");

		try (MockedStatic<FunctionNameProcessor> functionNameProcessorMock = mockStatic(FunctionNameProcessor.class)) {
			functionNameProcessorMock.when(() -> FunctionNameProcessor.demangle(anyString()))
					.thenReturn("main_demangled");
			ReportParser.CoverageResult result = ReportParser.parseLcovReport(reportLines);

			Map<String, Map<String, Object[]>> coverageResults = result.fileCoverage();
			assertEquals(1, coverageResults.size());
			assertEquals("0.00%", coverageResults.get("/path/to/file.c").get("Functions")[1]);
			assertTrue(result.functionCoverage().get("/path/to/file.c").isEmpty());
		}
	}

	@Test
	void testParseLcovReport_InvalidCounterFormat() {
		List<String> reportLines = Arrays.asList("SF:/path/to/file.c", "LF:invalid", "LH:0", "end_of_record");

		ReportParser.CoverageResult result = ReportParser.parseLcovReport(reportLines);

		Map<String, Map<String, Object[]>> coverageResults = result.fileCoverage();
		assertEquals(1, coverageResults.size());
		assertEquals("0.00%", coverageResults.get("/path/to/file.c").get("Lines")[1]);
	}

	@Test
	void testCoverageDataManagerInteraction() {
		List<String> reportLines = Arrays.asList("SF:/path/to/file.c", "DA:1,10", "end_of_record");

		ReportParser.CoverageResult result = ReportParser.parseLcovReport(reportLines);

		ArgumentCaptor<ReportParser.CoverageResult> resultCaptor = ArgumentCaptor
				.forClass(ReportParser.CoverageResult.class);
		verify(coverageDataManager).setCoverageData(resultCaptor.capture(), eq(List.of()));
		assertNotNull(resultCaptor.getValue());
		assertEquals(result, resultCaptor.getValue());
	}
}