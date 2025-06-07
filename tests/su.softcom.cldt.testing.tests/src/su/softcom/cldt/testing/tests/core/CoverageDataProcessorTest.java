package su.softcom.cldt.testing.tests.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
	private static final String TEST_FOLDER_PATH = "folder/test.c";
	private static final String TEST_FUNCTION_NAME = "testFunction";

	private CoverageDataProcessor processor;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		processor = new CoverageDataProcessor("Lines");
	}

	@Test
	void testSetAndGetSelectedCounter() {
		processor.setSelectedCounter("Branches");
		assertEquals("Branches", processor.getSelectedCounter());
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
			fileCoverage.put("src/test.c", Map.of("Lines", new Object[] { TEST_FILE_PATH, "50.00%", 5, 5, 10 }));
			ReportParser.CoverageResult coverageResult = new ReportParser.CoverageResult(fileCoverage, new HashMap<>(),
					new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
			List<String> analysisScope = List.of(TEST_FILE_PATH);

			List<CoverageNode> result = processor.buildCoverageTree(coverageResult, analysisScope);

			assertFalse(result.isEmpty());
			assertEquals(1, result.size());
			assertEquals(TEST_FILE_PATH, result.get(0).getName());
			assertEquals(CoverageNode.NodeType.FILE, result.get(0).getType());
		}
	}

	@Test
	void testBuildCoverageTreeWithFolderStructure() throws Exception {
		try (MockedStatic<CoverageUtils> utils = mockStatic(CoverageUtils.class)) {
			utils.when(() -> CoverageUtils.removeFirstSegment("src/folder/test.c", 4)).thenReturn(TEST_FOLDER_PATH);

			Map<String, Map<String, Object[]>> fileCoverage = new HashMap<>();
			fileCoverage.put("src/folder/test.c", Map.of("Lines", new Object[] { TEST_FILE_PATH, "50.00%", 5, 5, 10 }));
			ReportParser.CoverageResult coverageResult = new ReportParser.CoverageResult(fileCoverage, new HashMap<>(),
					new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
			List<String> analysisScope = List.of(TEST_FOLDER_PATH);

			List<CoverageNode> result = processor.buildCoverageTree(coverageResult, analysisScope);

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
		processor.setSelectedCounter("Line Counters");
		Map<String, Object[]> counters = new HashMap<>();
		counters.put("Lines", new Object[] { TEST_FILE_PATH, "50.00%", 5, 5, 10 });

		Method getCounterData = getPrivateMethod("getCounterData", Map.class, String.class);
		Object[] result = (Object[]) getCounterData.invoke(processor, counters, TEST_FILE_PATH);

		assertNotNull(result);
		assertEquals("50.00%", result[1]);
	}

	@Test
	void testGetCounterDataWithFunctionFallback() throws Exception {
		processor.setSelectedCounter("Счетчики функций");
		Map<String, Object[]> counters = new HashMap<>();
		counters.put("Functions", new Object[] { TEST_FILE_PATH, "100.00%", 1, 0, 1 });

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
		processor.setSelectedCounter("Lines");
		ReportParser.FunctionCoverage function = ReportParser.FunctionCoverage.create(TEST_FUNCTION_NAME, List.of(), 2,
				-1, 1, false);
		List<ReportParser.LineCoverage> lines = List.of(new ReportParser.LineCoverage(2, 1),
				new ReportParser.LineCoverage(3, 0));

		Method calculateFunctionData = getPrivateMethod("calculateFunctionData", ReportParser.FunctionCoverage.class,
				List.class, List.class, String.class);
		Object[] result = (Object[]) calculateFunctionData.invoke(processor, function, lines, new ArrayList<>(),
				TEST_FUNCTION_NAME);

		assertEquals(TEST_FUNCTION_NAME, result[0]);
		assertEquals("100.00%", result[1]);
		assertEquals(1, result[2]);
		assertEquals(0, result[3]);
		assertEquals(1, result[4]);
	}

	@Test
	void testCalculateFunctionDataForLinesWithFallback() throws Exception {
		processor.setSelectedCounter("Lines");
		ReportParser.FunctionCoverage function = ReportParser.FunctionCoverage.create(TEST_FUNCTION_NAME, List.of(), 2,
				-1, 1, false);
		List<ReportParser.LineCoverage> lines = List.of();

		Method calculateFunctionData = getPrivateMethod("calculateFunctionData", ReportParser.FunctionCoverage.class,
				List.class, List.class, String.class);
		Object[] result = (Object[]) calculateFunctionData.invoke(processor, function, lines, new ArrayList<>(),
				TEST_FUNCTION_NAME);

		assertEquals(TEST_FUNCTION_NAME, result[0]);
		assertEquals("100.00%", result[1]);
		assertEquals(1, result[2]);
		assertEquals(0, result[3]);
		assertEquals(1, result[4]);
	}

	@Test
	void testCalculateFunctionDataForBranches() throws Exception {
		processor.setSelectedCounter("Branches");
		ReportParser.FunctionCoverage function = ReportParser.FunctionCoverage.create(TEST_FUNCTION_NAME, List.of(), 2,
				-1, 0, false);
		List<ReportParser.BranchCoverage> branches = List.of(new ReportParser.BranchCoverage(2, true),
				new ReportParser.BranchCoverage(3, false));

		Method calculateFunctionData = getPrivateMethod("calculateFunctionData", ReportParser.FunctionCoverage.class,
				List.class, List.class, String.class);
		Object[] result = (Object[]) calculateFunctionData.invoke(processor, function, new ArrayList<>(), branches,
				TEST_FUNCTION_NAME);

		assertEquals(TEST_FUNCTION_NAME, result[0]);
		assertEquals("0.00%", result[1]);
		assertEquals(0, result[2]);
		assertEquals(0, result[3]);
		assertEquals(0, result[4]);
	}

	@Test
	void testCalculateFunctionDataForFunctions() throws Exception {
		processor.setSelectedCounter("Functions");
		ReportParser.FunctionCoverage function = ReportParser.FunctionCoverage.create(TEST_FUNCTION_NAME, List.of(), 1,
				1, 1, false);

		Method calculateFunctionData = getPrivateMethod("calculateFunctionData", ReportParser.FunctionCoverage.class,
				List.class, List.class, String.class);
		Object[] result = (Object[]) calculateFunctionData.invoke(processor, function, new ArrayList<>(),
				new ArrayList<>(), TEST_FUNCTION_NAME);

		assertEquals(TEST_FUNCTION_NAME, result[0]);
		assertEquals("100.00%", result[1]);
		assertEquals(1, result[2]);
		assertEquals(0, result[3]);
		assertEquals(1, result[4]);
	}

	@Test
	void testCalculateFunctionDataWithInvalidCounter() throws Exception {
		processor.setSelectedCounter("Invalid");
		ReportParser.FunctionCoverage function = ReportParser.FunctionCoverage.create(TEST_FUNCTION_NAME, List.of(), 1,
				1, 1, false);

		Method calculateFunctionData = getPrivateMethod("calculateFunctionData", ReportParser.FunctionCoverage.class,
				List.class, List.class, String.class);
		Object[] result = (Object[]) calculateFunctionData.invoke(processor, function, new ArrayList<>(),
				new ArrayList<>(), TEST_FUNCTION_NAME);

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