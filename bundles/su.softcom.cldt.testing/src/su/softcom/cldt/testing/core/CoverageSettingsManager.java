package su.softcom.cldt.testing.core;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import su.softcom.cldt.testing.utils.CoverageUtils;

public class CoverageSettingsManager {
	private static final ILog LOGGER = Platform.getLog(CoverageSettingsManager.class);
	private static final String PLUGIN_ID = Activator.PLUGIN_ID;
	private static final List<String> PROFILE_FILES = Arrays.asList("coverage.profraw", "coverage.profdata",
			"coverage_report.lcov");
	private static final String DEFAULT_LLVM_COV = "llvm-cov";
	private static final String DEFAULT_LLVM_PROFDATA = "llvm-profdata";
	private static final List<Runnable> settingsChangeListeners = new ArrayList<>();

	private CoverageSettingsManager() {
	}

	public static void cleanProfileData(String coverageDataDir) {
		for (String fileName : PROFILE_FILES) {
			Path filePath = Paths.get(coverageDataDir, fileName);
			File file = filePath.toFile();
			if (file.exists() && !file.delete()) {
				LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
						String.format("Failed to delete profile file: %s", filePath)));
			}
		}
	}

	public static String getLlvmCovCommand() {
		String llvmCovPath = CoveragePreferenceSettings.getLlvmCovPath();
		if (llvmCovPath.isEmpty()) {
			return DEFAULT_LLVM_COV;
		}
		try {
			Path resolvedPath = Paths.get(llvmCovPath).toAbsolutePath().normalize();
			return resolvedPath.toString();
		} catch (Exception e) {
			LOGGER.log(
					new Status(IStatus.WARNING, PLUGIN_ID, String.format("Invalid llvm-cov path: %s", llvmCovPath), e));
			return DEFAULT_LLVM_COV;
		}
	}

	public static String getLlvmProfdataCommand() {
		String llvmProfdataPath = CoveragePreferenceSettings.getLlvmProfdataPath();
		if (llvmProfdataPath.isEmpty()) {
			return DEFAULT_LLVM_PROFDATA;
		}
		try {
			Path resolvedPath = Paths.get(llvmProfdataPath).toAbsolutePath().normalize();
			return resolvedPath.toString();
		} catch (Exception e) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					String.format("Invalid llvm-profdata path: %s", llvmProfdataPath), e));
			return DEFAULT_LLVM_PROFDATA;
		}
	}

	public static List<String> filterAnalysisScope(List<String> analysisScope, IProject project) {
		if (analysisScope == null) {
			return List.of();
		}

		List<String> includePatterns = parsePatterns(CoveragePreferenceSettings.getIncludes());
		List<String> excludePatterns = parsePatterns(CoveragePreferenceSettings.getExcludes());

		return analysisScope.stream().filter(path -> isPathIncluded(path, includePatterns, excludePatterns))
				.collect(Collectors.toList());
	}

	private static boolean isPathIncluded(String path, List<String> includePatterns, List<String> excludePatterns) {
		boolean matchesInclude = includePatterns.isEmpty()
				|| includePatterns.stream().anyMatch(pattern -> CoverageUtils.matchesPattern(path, pattern));
		boolean notExcluded = excludePatterns.stream()
				.noneMatch(pattern -> CoverageUtils.matchesPattern(path, pattern));
		return matchesInclude && notExcluded;
	}

	private static List<String> parsePatterns(String patternString) {
		if (patternString == null || patternString.trim().isEmpty()) {
			return List.of();
		}
		return Arrays.stream(patternString.split(";")).map(String::trim).filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
	}

	public static void addSettingsChangeListener(Runnable listener) {
		settingsChangeListeners.add(listener);
	}

	public static void removeSettingsChangeListener(Runnable listener) {
		settingsChangeListeners.remove(listener);
	}

	public static void notifySettingsChanged() {
		for (Runnable listener : new ArrayList<>(settingsChangeListeners)) {
			listener.run();
		}
	}
}