package su.softcom.cldt.testing.utils;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import su.softcom.cldt.testing.core.ReportParser;

public class CoverageUtils {
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final String WARNING_INVALID_INPUT = "Invalid input: path=%s, pattern=%s";
	private static final String WARNING_INVALID_PATTERN = "Invalid pattern syntax: %s, Error: %s";
	private static final ILog LOGGER = Platform.getLog(CoverageUtils.class);

	private CoverageUtils() {
	}

	public static String removeFirstSegment(String path, int number) {
		if (path == null || path.isEmpty()) {
			throw new IllegalArgumentException("Path cannot be null or empty");
		}
		String result = path.startsWith("/") ? path.substring(1) : path;
		for (int i = 0; i < number; i++) {
			int firstSlashIndex = result.indexOf('/');
			if (firstSlashIndex != -1) {
				result = result.substring(firstSlashIndex + 1);
			}
		}
		return result;
	}

	public static List<ReportParser.LineCoverage> findCoverageForFile(String filePath,
			Map<String, List<ReportParser.LineCoverage>> coverageData) {
		return findCoverage(filePath, coverageData);
	}

	public static List<ReportParser.BranchCoverage> findBranchCoverageForFile(String filePath,
			Map<String, List<ReportParser.BranchCoverage>> branchCoverage) {
		return findCoverage(filePath, branchCoverage);
	}

	public static List<ReportParser.MethodCoverage> findMethodCoverageForFile(String filePath,
			Map<String, List<ReportParser.MethodCoverage>> methodCoverage) {
		return findCoverage(filePath, methodCoverage);
	}

	private static <T> List<T> findCoverage(String filePath, Map<String, List<T>> coverageData) {
		List<T> result = coverageData.get(filePath);
		if (result != null) {
			return result;
		}
		for (Map.Entry<String, List<T>> entry : coverageData.entrySet()) {
			if (entry.getKey().endsWith(filePath)) {
				return entry.getValue();
			}
		}
		return null;
	}

	public static boolean matchesPattern(String path, String pattern) {
		if (path == null || pattern == null) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, String.format(WARNING_INVALID_INPUT, path, pattern)));
			return false;
		}
		if (pattern.equals("*")) {
			return true;
		}
		String normalizedPath = path.replace('\\', '/');
		String normalizedPattern = pattern.replace('\\', '/');
		try {
			String regexPattern = "^" + normalizedPattern.replace("*", ".*") + "$";
			return Pattern.compile(regexPattern).matcher(normalizedPath).matches();
		} catch (PatternSyntaxException e) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					String.format(WARNING_INVALID_PATTERN, normalizedPattern, e.getMessage())));
			return false;
		}
	}
}