package su.softcom.cldt.testing.tests.core;

import org.eclipse.core.runtime.*;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import su.softcom.cldt.testing.core.CommandExecutor;
import su.softcom.cldt.testing.core.CoverageLaunchDelegate;
import su.softcom.cldt.testing.core.CoverageSettingsManager;
import su.softcom.cldt.testing.core.ReportParser;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CoverageLaunchDelegateTest {

	private static final String EXECUTABLE_PATH = "test";

	private CoverageLaunchDelegate delegate;
	@Mock
	private ILaunchConfiguration configuration;
	@Mock
	private ILog logger;
	@Mock
	private ILaunch launch;
	@Mock
	private IProgressMonitor monitor;

	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		MockitoAnnotations.openMocks(this);
		delegate = new CoverageLaunchDelegate();
		tempDir = Files.createTempDirectory("testCoverage");
		try (MockedStatic<Platform> platform = mockStatic(Platform.class)) {
			platform.when(() -> Platform.getLog(CoverageLaunchDelegate.class)).thenReturn(logger);
		}
	}

	@AfterEach
	void tearDown() throws IOException {
		if (tempDir != null && Files.exists(tempDir)) {
			List<IOException> errors = new ArrayList<>();
			Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.delete(path);
				} catch (IOException e) {
					errors.add(new IOException("Failed to delete path: " + path, e));
				}
			});
			if (!errors.isEmpty()) {
				throw new RuntimeException("Failed to delete some paths: " + errors.size() + " errors occurred",
						errors.get(0));
			}
		}
	}

	@Test
	void testGetProjectWithInvalidName() throws Exception {
		when(configuration.getAttribute("projectName", (String) null)).thenReturn(null);
		Method getProject = getPrivateMethod("getProject", ILaunchConfiguration.class);
		InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
				() -> getProject.invoke(delegate, configuration));
		assertTrue(thrown.getCause() instanceof CoreException);
		assertEquals("Failed to get project name", thrown.getCause().getMessage());
	}

	@Test
	void testRunTestsWithProfrawFile() throws Exception {
		String coverageDataDir = tempDir.toString();
		String executablePath = tempDir.resolve(EXECUTABLE_PATH).toString();
		CommandExecutor executor = mock(CommandExecutor.class);
		Files.createFile(tempDir.resolve("coverage.profraw"));
		Method runTests = getPrivateMethod("runTests", CommandExecutor.class, String.class, String.class);
		runTests.invoke(delegate, executor, coverageDataDir, executablePath);
		verify(executor).executeCommand(eq(Arrays.asList(executablePath)),
				eq(Map.of("LLVM_PROFILE_FILE", coverageDataDir + "/coverage.profraw")), eq(null),
				any(StringBuilder.class));
	}

	@Test
	void testRunTestsWithoutProfrawFile() throws Exception {
		String coverageDataDir = tempDir.toString();
		String executablePath = tempDir.resolve(EXECUTABLE_PATH).toString();
		CommandExecutor executor = mock(CommandExecutor.class);
		Method runTests = getPrivateMethod("runTests", CommandExecutor.class, String.class, String.class);
		InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
				() -> runTests.invoke(delegate, executor, coverageDataDir, executablePath));
		assertTrue(thrown.getCause() instanceof IOException);
		assertEquals("Profile raw data file was not generated: " + coverageDataDir + "/coverage.profraw",
				thrown.getCause().getMessage());
	}

	@Test
	void testGenerateProfileData() throws Exception {
		String rawProfilePath = tempDir.resolve("coverage.profraw").toString();
		String profileDataPath = tempDir.resolve("coverage.profdata").toString();
		CommandExecutor executor = mock(CommandExecutor.class);
		Files.createFile(tempDir.resolve("coverage.profraw"));
		Files.createFile(tempDir.resolve("coverage.profdata"));
		try (MockedStatic<CoverageSettingsManager> settings = mockStatic(CoverageSettingsManager.class)) {
			settings.when(() -> CoverageSettingsManager.getLlvmProfdataCommand()).thenReturn("llvm-profdata");
			Method generateProfileData = getPrivateMethod("generateProfileData", CommandExecutor.class, String.class,
					String.class);
			generateProfileData.invoke(delegate, executor, rawProfilePath, profileDataPath);
			verify(executor).executeCommand(
					eq(Arrays.asList("llvm-profdata", "merge", "-sparse", rawProfilePath, "-o", profileDataPath)),
					eq(null), eq(null), any(StringBuilder.class));
		}
	}

	@Test
	void testGenerateLcovReport() throws Exception {
		String executablePath = tempDir.resolve(EXECUTABLE_PATH).toString();
		String profileDataPath = tempDir.resolve("coverage.profdata").toString();
		String reportPath = tempDir.resolve("coverage_report.lcov").toString();
		List<String> analysisScope = List.of("test.c");
		CommandExecutor executor = mock(CommandExecutor.class);
		Files.createFile(tempDir.resolve("coverage_report.lcov"));
		try (MockedStatic<CoverageSettingsManager> settings = mockStatic(CoverageSettingsManager.class);
				MockedStatic<ReportParser> parser = mockStatic(ReportParser.class)) {
			settings.when(() -> CoverageSettingsManager.getLlvmCovCommand()).thenReturn("llvm-cov");
			parser.when(() -> ReportParser.parseLcovReport(anyList()))
					.thenReturn(new ReportParser.CoverageResult(new HashMap<>(), new HashMap<>(), new HashMap<>(),
							new HashMap<>(), new HashMap<>(), new HashMap<>()));
			Method generateLcovReport = getPrivateMethod("generateLcovReport", CommandExecutor.class, String.class,
					String.class, String.class, List.class);
			generateLcovReport.invoke(delegate, executor, executablePath, profileDataPath, reportPath, analysisScope);
			verify(executor).executeCommand(eq(Arrays.asList("llvm-cov", "export", executablePath,
					"-instr-profile=" + profileDataPath, "--format=lcov")), eq(null), eq(reportPath),
					any(StringBuilder.class));
		}
	}

	@Test
	void testFilterLcovReport() throws Exception {
		List<String> reportLines = List.of("SF:/path/test.c", "DA:1,1", "end_of_record", "SF:/path/other.c", "DA:1,0",
				"end_of_record");
		List<String> analysisScope = List.of("test.c");
		Method filterLcovReport = getPrivateMethod("filterLcovReport", List.class, List.class);
		@SuppressWarnings("unchecked")
		List<String> result = (List<String>) filterLcovReport.invoke(delegate, reportLines, analysisScope);
		assertEquals(3, result.size());
		assertEquals("SF:/path/test.c", result.get(0));
		assertEquals("DA:1,1", result.get(1));
		assertEquals("end_of_record", result.get(2));
	}

	@Test
	void testGetCoverageData() {
		ReportParser.CoverageResult coverageResult = new ReportParser.CoverageResult(new HashMap<>(), new HashMap<>(),
				new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
		setField(delegate, "coverageData", coverageResult);
		Map<String, Map<String, Object[]>> result = delegate.getCoverageData();
		assertNotNull(result);
		assertTrue(result.isEmpty());
		setField(delegate, "coverageData", null);
		assertNull(delegate.getCoverageData());
	}

	@Test
	void testGetFullCoverageData() {
		ReportParser.CoverageResult coverageResult = new ReportParser.CoverageResult(new HashMap<>(), new HashMap<>(),
				new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
		setField(delegate, "coverageData", coverageResult);
		ReportParser.CoverageResult result = delegate.getFullCoverageData();
		assertNotNull(result);
		assertTrue(result.fileCoverage().isEmpty());
		setField(delegate, "coverageData", null);
		result = delegate.getFullCoverageData();
		assertNotNull(result);
		assertTrue(result.fileCoverage().isEmpty());
	}

	private Method getPrivateMethod(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
		Method method = CoverageLaunchDelegate.class.getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);
		return method;
	}

	private void setField(Object target, String fieldName, Object value) {
		try {
			java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}