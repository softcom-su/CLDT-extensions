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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FunctionCoverageAnalyzerTest {

	@Mock
	private ReportParser.FileContext context;

	@Mock
	private CoverageDataManager coverageDataManager;

	@Mock
	private ILog logger;

	@Mock
	private List<ReportParser.FunctionCoverage> currentFunctionCoverage;

	@Mock
	private List<ReportParser.LineCoverage> currentLineCoverage;

	@Mock
	private List<ReportParser.LineCoverage> currentNonFunctionLineCoverage;

	@Mock
	private List<ReportParser.FunctionCoverage> annotationFunctionCoverage;

	private AutoCloseable closeable;
	private MockedStatic<CoverageDataManager> coverageDataManagerMock;
	private MockedStatic<FunctionNameProcessor> functionNameProcessorMock;
	private MockedStatic<Platform> platformMock;
	private MockedStatic<FunctionCoverageAnalyzer> functionCoverageAnalyzerMock;

	@BeforeEach
	void setUp() {
		closeable = MockitoAnnotations.openMocks(this);

		platformMock = mockStatic(Platform.class);
		platformMock.when(() -> Platform.getLog(FunctionCoverageAnalyzer.class)).thenReturn(logger);

		coverageDataManagerMock = mockStatic(CoverageDataManager.class);
		coverageDataManagerMock.when(CoverageDataManager::getInstance).thenReturn(coverageDataManager);

		functionNameProcessorMock = mockStatic(FunctionNameProcessor.class);
		functionCoverageAnalyzerMock = mockStatic(FunctionCoverageAnalyzer.class, CALLS_REAL_METHODS);

		functionCoverageAnalyzerMock
				.when(() -> FunctionCoverageAnalyzer.parseFunctionBoundaries(anyString(), anyList(), anyList()))
				.thenReturn(List.of());

		when(context.currentFunctionCoverage()).thenReturn(currentFunctionCoverage);
		when(context.currentLineCoverage()).thenReturn(currentLineCoverage);
		when(context.currentNonFunctionLineCoverage()).thenReturn(currentNonFunctionLineCoverage);
		when(context.annotationFunctionCoverage()).thenReturn(annotationFunctionCoverage);
		when(context.currentFile()).thenReturn("/path/to/file.c");
	}

	@AfterEach
	void tearDown() throws Exception {
		closeable.close();
		platformMock.close();
		coverageDataManagerMock.close();
		functionNameProcessorMock.close();
		functionCoverageAnalyzerMock.close();
	}

	@Test
	void testProcessFunctionCoverage_EmptyFunctionNames() {
		setupEmptyFunctionNamesContext();
		FunctionCoverageAnalyzer.processFunctionCoverage(context);
		verifyEmptyFunctionNamesCoverage();
	}

	@Test
	void testProcessFunctionCoverage_InvalidFunctionExecutionFormat() {
		setupInvalidFunctionExecutionContext();
		FunctionCoverageAnalyzer.processFunctionCoverage(context);
		verifyInvalidFunctionExecutionCoverage();
	}

	@Test
	void testProcessFunctionCoverage_ValidFunctionWithoutBoundaries() {
		setupValidFunctionContext();
		FunctionCoverageAnalyzer.processFunctionCoverage(context);
		verifyValidFunctionCoverage();
	}

	@Test
	void testProcessFunctionCoverage_LambdaFunctionWithoutBoundaries() {
		setupLambdaFunctionContext();
		FunctionCoverageAnalyzer.processFunctionCoverage(context);
		verifyLambdaFunctionCoverage();
	}

	@Test
	void testProcessFunctionCoverage_MultipleFunctionsWithoutBoundaries() {
		setupMultipleFunctionsContext();
		FunctionCoverageAnalyzer.processFunctionCoverage(context);
		verifyMultipleFunctionsCoverage();
	}

	private void setupEmptyFunctionNamesContext() {
		Map<Integer, List<ReportParser.MangledDemangledPair>> tempFunctionNames = new HashMap<>();
		List<String> fnExecutionLines = List.of();
		List<ReportParser.LineCoverage> tempLineCoverage = List.of(new ReportParser.LineCoverage(1, 10));

		when(context.tempFunctionNames()).thenReturn(tempFunctionNames);
		when(context.fnExecutionLines()).thenReturn(fnExecutionLines);
		when(context.tempLineCoverage()).thenReturn(tempLineCoverage);
		when(currentFunctionCoverage.iterator()).thenReturn(List.<ReportParser.FunctionCoverage>of().iterator());
	}

	private void verifyEmptyFunctionNamesCoverage() {
		verify(currentFunctionCoverage, never()).add(any());
		ArgumentCaptor<ReportParser.LineCoverage> lineCaptor = ArgumentCaptor.forClass(ReportParser.LineCoverage.class);
		verify(currentNonFunctionLineCoverage).add(lineCaptor.capture());
		assertEquals(1, lineCaptor.getValue().lineNumber());
		assertEquals(10, lineCaptor.getValue().executionCount());
		verify(currentLineCoverage, never()).add(any());
		verify(logger, never()).log(any());
	}

	private void setupInvalidFunctionExecutionContext() {
		Map<Integer, List<ReportParser.MangledDemangledPair>> tempFunctionNames = new HashMap<>();
		tempFunctionNames.put(1, List.of(new ReportParser.MangledDemangledPair("_Zmain", "main")));
		List<String> fnExecutionLines = List.of("FNDA:invalid,_Zmain");
		List<ReportParser.LineCoverage> tempLineCoverage = List.of(new ReportParser.LineCoverage(1, 10));

		when(context.tempFunctionNames()).thenReturn(tempFunctionNames);
		when(context.fnExecutionLines()).thenReturn(fnExecutionLines);
		when(context.tempLineCoverage()).thenReturn(tempLineCoverage);

		functionNameProcessorMock.when(() -> FunctionNameProcessor.findCommonFunctionName(anyList()))
				.thenReturn("main");
		functionNameProcessorMock.when(() -> FunctionNameProcessor.isLambdaFunction("main")).thenReturn(false);

		setupFunctionCoverageMock(
				List.of(new ReportParser.FunctionCoverage("main", List.of("_Zmain"), 0, 1, -1, 1, false)));
	}

	private void verifyInvalidFunctionExecutionCoverage() {
		ArgumentCaptor<ReportParser.FunctionCoverage> functionCaptor = ArgumentCaptor
				.forClass(ReportParser.FunctionCoverage.class);
		verify(currentFunctionCoverage).add(functionCaptor.capture());
		ReportParser.FunctionCoverage capturedFunction = functionCaptor.getValue();
		assertEquals("main", capturedFunction.name());
		assertEquals(List.of("_Zmain"), capturedFunction.mangledNames());
		assertEquals(0, capturedFunction.executionCount());
		assertEquals(1, capturedFunction.startLine());
		assertEquals(-1, capturedFunction.endLine());
		assertEquals(1, capturedFunction.signatureLine());
		assertFalse(capturedFunction.isLambda());

		ArgumentCaptor<ReportParser.LineCoverage> lineCaptor = ArgumentCaptor.forClass(ReportParser.LineCoverage.class);
		verify(currentNonFunctionLineCoverage).add(lineCaptor.capture());
		assertEquals(1, lineCaptor.getValue().lineNumber());
		assertEquals(10, lineCaptor.getValue().executionCount());
		verify(currentLineCoverage, never()).add(any());
	}

	private void setupValidFunctionContext() {
		Map<Integer, List<ReportParser.MangledDemangledPair>> tempFunctionNames = new HashMap<>();
		tempFunctionNames.put(1, List.of(new ReportParser.MangledDemangledPair("_Zmain", "")));
		List<String> fnExecutionLines = List.of("FNDA:10,_Zmain");
		List<ReportParser.LineCoverage> tempLineCoverage = List.of(new ReportParser.LineCoverage(1, 10),
				new ReportParser.LineCoverage(2, 0));

		when(context.tempFunctionNames()).thenReturn(tempFunctionNames);
		when(context.fnExecutionLines()).thenReturn(fnExecutionLines);
		when(context.tempLineCoverage()).thenReturn(tempLineCoverage);

		functionNameProcessorMock.when(() -> FunctionNameProcessor.findCommonFunctionName(anyList()))
				.thenReturn("main");
		functionNameProcessorMock.when(() -> FunctionNameProcessor.isLambdaFunction("main")).thenReturn(false);

		setupFunctionCoverageMock(
				List.of(new ReportParser.FunctionCoverage("main", List.of("_Zmain"), 0, 1, -1, 1, false)));
	}

	private void verifyValidFunctionCoverage() {
		ArgumentCaptor<ReportParser.FunctionCoverage> functionCaptor = ArgumentCaptor
				.forClass(ReportParser.FunctionCoverage.class);
		verify(currentFunctionCoverage).add(functionCaptor.capture());
		ReportParser.FunctionCoverage capturedFunction = functionCaptor.getValue();
		assertEquals("main", capturedFunction.name());
		assertEquals(List.of("_Zmain"), capturedFunction.mangledNames());
		assertEquals(0, capturedFunction.executionCount());
		assertEquals(1, capturedFunction.startLine());
		assertEquals(-1, capturedFunction.endLine());
		assertEquals(1, capturedFunction.signatureLine());
		assertFalse(capturedFunction.isLambda());

		ArgumentCaptor<ReportParser.LineCoverage> lineCaptor = ArgumentCaptor.forClass(ReportParser.LineCoverage.class);
		verify(currentNonFunctionLineCoverage, times(2)).add(lineCaptor.capture());
		List<ReportParser.LineCoverage> capturedLines = lineCaptor.getAllValues();
		assertEquals(1, capturedLines.get(0).lineNumber());
		assertEquals(10, capturedLines.get(0).executionCount());
		assertEquals(2, capturedLines.get(1).lineNumber());
		assertEquals(0, capturedLines.get(1).executionCount());
		verify(currentLineCoverage, never()).add(any());
	}

	private void setupLambdaFunctionContext() {
		Map<Integer, List<ReportParser.MangledDemangledPair>> tempFunctionNames = new HashMap<>();
		tempFunctionNames.put(1, List.of(new ReportParser.MangledDemangledPair("_Zlambda", "lambda")));
		List<String> fnExecutionLines = List.of("FNDA:5,_Zlambda");
		List<ReportParser.LineCoverage> tempLineCoverage = List.of(new ReportParser.LineCoverage(1, 5));

		when(context.tempFunctionNames()).thenReturn(tempFunctionNames);
		when(context.fnExecutionLines()).thenReturn(fnExecutionLines);
		when(context.tempLineCoverage()).thenReturn(tempLineCoverage);

		functionNameProcessorMock.when(() -> FunctionNameProcessor.findCommonFunctionName(anyList()))
				.thenReturn("lambda");
		functionNameProcessorMock.when(() -> FunctionNameProcessor.isLambdaFunction("lambda")).thenReturn(true);

		setupFunctionCoverageMock(
				List.of(new ReportParser.FunctionCoverage("lambda", List.of("_Zlambda"), 0, 1, -1, 1, true)));
	}

	private void verifyLambdaFunctionCoverage() {
		ArgumentCaptor<ReportParser.FunctionCoverage> functionCaptor = ArgumentCaptor
				.forClass(ReportParser.FunctionCoverage.class);
		verify(currentFunctionCoverage).add(functionCaptor.capture());
		ReportParser.FunctionCoverage capturedFunction = functionCaptor.getValue();
		assertEquals("lambda", capturedFunction.name());
		assertEquals(List.of("_Zlambda"), capturedFunction.mangledNames());
		assertEquals(0, capturedFunction.executionCount());
		assertEquals(1, capturedFunction.startLine());
		assertEquals(-1, capturedFunction.endLine());
		assertEquals(1, capturedFunction.signatureLine());
		assertTrue(capturedFunction.isLambda());

		ArgumentCaptor<ReportParser.LineCoverage> lineCaptor = ArgumentCaptor.forClass(ReportParser.LineCoverage.class);
		verify(currentNonFunctionLineCoverage).add(lineCaptor.capture());
		assertEquals(1, lineCaptor.getValue().lineNumber());
		assertEquals(5, lineCaptor.getValue().executionCount());
		verify(currentLineCoverage, never()).add(any());
	}

	private void setupMultipleFunctionsContext() {
		Map<Integer, List<ReportParser.MangledDemangledPair>> tempFunctionNames = new HashMap<>();
		tempFunctionNames.put(1, List.of(new ReportParser.MangledDemangledPair("_Zmain", "main")));
		tempFunctionNames.put(3, List.of(new ReportParser.MangledDemangledPair("_Zfunc", "func")));
		List<String> fnExecutionLines = List.of("FNDA:10,_Zmain", "FNDA:5,_Zfunc");
		List<ReportParser.LineCoverage> tempLineCoverage = List.of(new ReportParser.LineCoverage(1, 10),
				new ReportParser.LineCoverage(3, 5));

		when(context.tempFunctionNames()).thenReturn(tempFunctionNames);
		when(context.fnExecutionLines()).thenReturn(fnExecutionLines);
		when(context.tempLineCoverage()).thenReturn(tempLineCoverage);

		functionNameProcessorMock.when(() -> FunctionNameProcessor.findCommonFunctionName(anyList()))
				.thenAnswer(invocation -> {
					List<String> names = invocation.getArgument(0);
					return names.get(0);
				});
		functionNameProcessorMock.when(() -> FunctionNameProcessor.isLambdaFunction(anyString())).thenReturn(false);

		setupFunctionCoverageMock(
				List.of(new ReportParser.FunctionCoverage("main", List.of("_Zmain"), 0, 1, -1, 1, false),
						new ReportParser.FunctionCoverage("func", List.of("_Zfunc"), 0, 3, -1, 3, false)));
	}

	private void verifyMultipleFunctionsCoverage() {
		ArgumentCaptor<ReportParser.FunctionCoverage> functionCaptor = ArgumentCaptor
				.forClass(ReportParser.FunctionCoverage.class);
		verify(currentFunctionCoverage, times(2)).add(functionCaptor.capture());
		List<ReportParser.FunctionCoverage> capturedFunctions = functionCaptor.getAllValues();

		assertEquals("main", capturedFunctions.get(0).name());
		assertEquals(List.of("_Zmain"), capturedFunctions.get(0).mangledNames());
		assertEquals(0, capturedFunctions.get(0).executionCount());
		assertEquals(1, capturedFunctions.get(0).startLine());
		assertEquals(-1, capturedFunctions.get(0).endLine());
		assertEquals(1, capturedFunctions.get(0).signatureLine());
		assertFalse(capturedFunctions.get(0).isLambda());

		assertEquals("func", capturedFunctions.get(1).name());
		assertEquals(List.of("_Zfunc"), capturedFunctions.get(1).mangledNames());
		assertEquals(0, capturedFunctions.get(1).executionCount());
		assertEquals(3, capturedFunctions.get(1).startLine());
		assertEquals(-1, capturedFunctions.get(1).endLine());
		assertEquals(3, capturedFunctions.get(1).signatureLine());
		assertFalse(capturedFunctions.get(1).isLambda());

		ArgumentCaptor<ReportParser.LineCoverage> lineCaptor = ArgumentCaptor.forClass(ReportParser.LineCoverage.class);
		verify(currentNonFunctionLineCoverage, times(2)).add(lineCaptor.capture());
		List<ReportParser.LineCoverage> capturedLines = lineCaptor.getAllValues();
		assertEquals(1, capturedLines.get(0).lineNumber());
		assertEquals(10, capturedLines.get(0).executionCount());
		assertEquals(3, capturedLines.get(1).lineNumber());
		assertEquals(5, capturedLines.get(1).executionCount());
		verify(currentLineCoverage, never()).add(any());
	}

	private void setupFunctionCoverageMock(List<ReportParser.FunctionCoverage> functions) {
		List<ReportParser.FunctionCoverage> functionsCopy = new ArrayList<>(functions);
		List<ReportParser.FunctionCoverage> newFunctions = new ArrayList<>();

		doAnswer(invocation -> {
			functionsCopy.clear();
			return null;
		}).when(currentFunctionCoverage).clear();

		doAnswer(invocation -> {
			newFunctions.add(invocation.getArgument(0));
			return null;
		}).when(currentFunctionCoverage).add(any(ReportParser.FunctionCoverage.class));

		doAnswer(invocation -> new ArrayList<>(functionsCopy).iterator()).when(currentFunctionCoverage).iterator();

		when(currentFunctionCoverage.size()).thenAnswer(invocation -> functionsCopy.size());
		when(currentFunctionCoverage.get(anyInt())).thenAnswer(invocation -> {
			int index = invocation.getArgument(0);
			return index < functionsCopy.size() ? functionsCopy.get(index) : null;
		});
	}
}