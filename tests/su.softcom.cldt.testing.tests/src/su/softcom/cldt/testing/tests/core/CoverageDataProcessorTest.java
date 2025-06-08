package su.softcom.cldt.testing.tests.core;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import su.softcom.cldt.testing.core.CoverageDataProcessor;
import su.softcom.cldt.testing.core.ReportParser;
import su.softcom.cldt.testing.ui.CoverageNode;
import su.softcom.cldt.testing.utils.CoverageUtils;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CoverageDataProcessorTest {

	private static final String TEST_FILE_PATH = "test.c";
	private static final String TEST_FUNCTION_NAME = "testFunction";
	private static final String LINE_COUNTERS = "Line Counters";
	private static final String BRANCH_COUNTERS = "Branch Counters";
	private static final String FUNCTION_COUNTERS = "Function Counters";

	private CoverageDataProcessor processor;
	@Mock
	private ILog log;
	private MockedStatic<Platform> mockedPlatform;
	private AutoCloseable closeable;

	@BeforeEach
	void setUp() {
		closeable = MockitoAnnotations.openMocks(this);
		mockedPlatform = mockStatic(Platform.class);
		mockedPlatform.when(() -> Platform.getLog(any(Class.class))).thenReturn(log);
		processor = new CoverageDataProcessor(LINE_COUNTERS);
		assertEquals(LINE_COUNTERS, processor.getSelectedCounter());
	}

	@AfterEach
	void tearDown() throws Exception {
		if (closeable != null) {
			closeable.close();
		}
		if (mockedPlatform != null) {
			mockedPlatform.close();
		}
	}

	@Test
	void testSetAndGetSelectedCounter() {
		processor.setSelectedCounter(BRANCH_COUNTERS);
		assertEquals(BRANCH_COUNTERS, processor.getSelectedCounter());
	}

	@Test
	void testBuildCoverageTreeWithEmptyScope() {
		ReportParser.CoverageResult coverageResult = new ReportParser.CoverageResult(new HashMap<>(), new HashMap<>(),
				new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());

		List<CoverageNode> result = processor.buildCoverageTree(coverageResult, null);
		assertTrue(result.isEmpty());

		result = processor.buildCoverageTree(coverageResult, List.of());
		assertTrue(result.isEmpty());
	}

	@Test
	void testBuildCoverageTreeWithSingleFile() throws Exception {
		try (MockedStatic<CoverageUtils> utils = mockStatic(CoverageUtils.class)) {
			utils.when(() -> CoverageUtils.removeFirstSegment("src/test.c", 4)).thenReturn(TEST_FILE_PATH);

			Map<String, Map<String, Object[]>> fileCoverage = new HashMap<>();
			fileCoverage.put("src/test.c", Map.of(LINE_COUNTERS, new Object[] { TEST_FILE_PATH, "50.00%", 5, 5, 10 }));
			ReportParser.CoverageResult coverageResult = new ReportParser.CoverageResult(fileCoverage, new HashMap<>(),
					new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
			List<String> analysisScope = List.of(TEST_FILE_PATH);

			List<CoverageNode> result = processor.buildCoverageTree(coverageResult, analysisScope);

			assertNotNull(result);
			assertFalse(result.isEmpty());
			assertEquals(1, result.size());
			assertEquals(TEST_FILE_PATH, result.get(0).getName());
			assertEquals(CoverageNode.NodeType.FILE, result.get(0).getType());
		}
	}

	@Test
	void testBuildCoverageTreeWithFolderStructure() throws Exception {
		try (MockedStatic<CoverageUtils> utils = mockStatic(CoverageUtils.class)) {
			utils.when(() -> CoverageUtils.removeFirstSegment("src/folder/test.c", 4)).thenReturn("folder/test.c");

			Map<String, Map<String, Object[]>> fileCoverage = new HashMap<>();
			fileCoverage.put("src/folder/test.c",
					Map.of(LINE_COUNTERS, new Object[] { TEST_FILE_PATH, "50.00%", 5, 5, 10 }));
			ReportParser.CoverageResult coverageResult = new ReportParser.CoverageResult(fileCoverage, new HashMap<>(),
					new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
			List<String> analysisScope = List.of("folder/test.c");

			List<CoverageNode> result = processor.buildCoverageTree(coverageResult, analysisScope);

			assertNotNull(result);
			assertEquals(1, result.size());
			assertEquals("folder", result.get(0).getName());
			assertEquals(CoverageNode.NodeType.FOLDER, result.get(0).getType());
			assertEquals(1, result.get(0).getChildren().size());
			assertEquals(TEST_FILE_PATH, result.get(0).getChildren().get(0).getName());
			assertEquals(CoverageNode.NodeType.FILE, result.get(0).getChildren().get(0).getType());
		}
	}

	@Test
	void testGetCounterDataForLines() throws Exception {
		processor.setSelectedCounter(LINE_COUNTERS);
		Map<String, Object[]> counters = new HashMap<>();
		counters.put(LINE_COUNTERS, new Object[] { TEST_FILE_PATH, "50.00%", 5, 5, 10 });

		Method getCounterData = getPrivateMethod("getCounterData", Map.class, String.class);
		Object[] result = (Object[]) getCounterData.invoke(processor, counters, TEST_FILE_PATH);

		assertNotNull(result);
		assertEquals("50.00%", result[1]);
	}

	@Test
	void testGetCounterDataWithFunctionFallback() throws Exception {
		processor.setSelectedCounter(FUNCTION_COUNTERS);
		Map<String, Object[]> counters = new HashMap<>();
		counters.put(FUNCTION_COUNTERS, new Object[] { TEST_FILE_PATH, "100.00%", 1, 0, 1 });

		Method getCounterData = getPrivateMethod("getCounterData", Map.class, String.class);
		Object[] result = (Object[]) getCounterData.invoke(processor, counters, TEST_FILE_PATH);

		assertNotNull(result);
		assertEquals("100.00%", result[1]);
	}

	@Test
	void testGetCounterDataWithInvalidCounter() throws Exception {
		processor.setSelectedCounter("Invalid");
		Map<String, Object[]> counters = new HashMap<>();

		Method getCounterData = getPrivateMethod("getCounterData", Map.class, String.class);
		Object[] result = (Object[]) getCounterData.invoke(processor, counters, TEST_FILE_PATH);

		assertNull(result);
	}

	@Test
	void testCalculateFunctionDataForLines() throws Exception {
		processor.setSelectedCounter(LINE_COUNTERS);
		assertEquals(LINE_COUNTERS, processor.getSelectedCounter());
		ReportParser.FunctionCoverage fc = ReportParser.FunctionCoverage.create(TEST_FUNCTION_NAME, List.of(), 1, 2, 0,
				false);
		List<ReportParser.LineCoverage> lines = List.of(new ReportParser.LineCoverage(2, 1),
				new ReportParser.LineCoverage(3, 0));

		Method calculateFunctionData = getPrivateMethod("calculateFunctionData", ReportParser.FunctionCoverage.class,
				List.class, List.class, String.class);
		Object[] result = (Object[]) calculateFunctionData.invoke(processor, fc, lines, new ArrayList<>(),
				TEST_FUNCTION_NAME);

		assertEquals(TEST_FUNCTION_NAME, result[0]);
		assertEquals("100.00%", result[1]);
		assertEquals(1, result[2]);
		assertEquals(0, result[3]);
		assertEquals(1, result[4]);
	}

	@Test
	void testCalculateFunctionDataForLinesWithFallback() throws Exception {
		processor.setSelectedCounter(LINE_COUNTERS);
		assertEquals(LINE_COUNTERS, processor.getSelectedCounter());
		ReportParser.FunctionCoverage fc = ReportParser.FunctionCoverage.create(TEST_FUNCTION_NAME, List.of(), 1, 2, 0,
				false);
		List<ReportParser.LineCoverage> lines = List.of();

		Method calculateFunctionData = getPrivateMethod("calculateFunctionData", ReportParser.FunctionCoverage.class,
				List.class, List.class, String.class);
		Object[] result = (Object[]) calculateFunctionData.invoke(processor, fc, lines, new ArrayList<>(),
				TEST_FUNCTION_NAME);

		assertEquals(TEST_FUNCTION_NAME, result[0]);
		assertEquals("100.00%", result[1]);
		assertEquals(1, result[2]);
		assertEquals(0, result[3]);
		assertEquals(1, result[4]);
	}

	@Test
	void testCalculateFunctionDataForBranches() throws Exception {
		processor.setSelectedCounter(BRANCH_COUNTERS);
		assertEquals(BRANCH_COUNTERS, processor.getSelectedCounter());
		ReportParser.FunctionCoverage fc = new ReportParser.FunctionCoverage(TEST_FUNCTION_NAME, List.of(), 1, 2, 3, 0,
				false);
		List<ReportParser.BranchCoverage> branches = List.of(new ReportParser.BranchCoverage(2, true),
				new ReportParser.BranchCoverage(3, false));

		Method calculateFunctionData = getPrivateMethod("calculateFunctionData", ReportParser.FunctionCoverage.class,
				List.class, List.class, String.class);
		Object[] result = (Object[]) calculateFunctionData.invoke(processor, fc, new ArrayList<>(), branches,
				TEST_FUNCTION_NAME);

		assertEquals(TEST_FUNCTION_NAME, result[0]);
		assertEquals("50.00%", result[1]);
		assertEquals(1, result[2]);
		assertEquals(1, result[3]);
		assertEquals(2, result[4]);
	}

	@Test
	void testCalculateFunctionDataForFunctions() throws Exception {
		processor.setSelectedCounter(FUNCTION_COUNTERS);
		assertEquals(FUNCTION_COUNTERS, processor.getSelectedCounter());
		ReportParser.FunctionCoverage fc = ReportParser.FunctionCoverage.create(TEST_FUNCTION_NAME, List.of(), 1, 1, 0,
				false);

		Method calculateFunctionData = getPrivateMethod("calculateFunctionData", ReportParser.FunctionCoverage.class,
				List.class, List.class, String.class);
		Object[] result = (Object[]) calculateFunctionData.invoke(processor, fc, new ArrayList<>(), new ArrayList<>(),
				TEST_FUNCTION_NAME);

		assertEquals(TEST_FUNCTION_NAME, result[0]);
		assertEquals("100.00%", result[1]);
		assertEquals(1, result[2]);
		assertEquals(0, result[3]);
		assertEquals(1, result[4]);
	}

	@Test
	void testCalculateFunctionDataWithInvalidCounter() throws Exception {
		processor.setSelectedCounter("Invalid");
		ReportParser.FunctionCoverage fc = ReportParser.FunctionCoverage.create(TEST_FUNCTION_NAME, List.of(), 1, 1, 0,
				false);

		Method calculateFunctionData = getPrivateMethod("calculateFunctionData", ReportParser.FunctionCoverage.class,
				List.class, List.class, String.class);
		Object[] result = (Object[]) calculateFunctionData.invoke(processor, fc, new ArrayList<>(), new ArrayList<>(),
				TEST_FUNCTION_NAME);

		assertEquals(TEST_FUNCTION_NAME, result[0]);
		assertEquals("0.00%", result[1]);
		assertEquals(0, result[2]);
		assertEquals(0, result[3]);
		assertEquals(0, result[4]);
	}

	@Test
	void testAggregateFolderData() throws Exception {
		CoverageNode folderNode = new CoverageNode("folder", CoverageNode.NodeType.FOLDER);
		CoverageNode fileNode = new CoverageNode(TEST_FILE_PATH, CoverageNode.NodeType.FILE);
		fileNode.setCoverageData(new Object[] { TEST_FILE_PATH, "50.00%", 5, 5, 10 });
		folderNode.addChild(fileNode);

		Method aggregateFolderData = getPrivateMethod("aggregateFolderData", Iterable.class);
		aggregateFolderData.invoke(processor, List.of(folderNode));

		Object[] result = folderNode.getCoverageData();
		assertNotNull(result);
		assertEquals("folder", result[0]);
		assertEquals("50.00%", result[1]);
		assertEquals(5, result[2]);
		assertEquals(5, result[3]);
		assertEquals(10, result[4]);
	}

	private Method getPrivateMethod(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
		Method method = CoverageDataProcessor.class.getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);
		return method;
	}
}