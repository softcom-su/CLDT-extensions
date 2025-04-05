package su.softcom.cldt.testing.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportParser {

	private static final String PERCENTAGE_FORMAT = "%.2f%%";
	private static final String TOTAL_LINES_KEY = "totalLines";
	private static final String COVERED_LINES_KEY = "coveredLines";
	private static final String TOTAL_BRANCHES_KEY = "totalBranches";
	private static final String COVERED_BRANCHES_KEY = "coveredBranches";
	private static final String TOTAL_FUNCTIONS_KEY = "totalFunctions";
	private static final String COVERED_FUNCTIONS_KEY = "coveredFunctions";

	private ReportParser() {
	}

	public static Map<String, Map<String, Object[]>> parseLcovReport(List<String> reportLines) {
		Map<String, Map<String, Object[]>> coverageResults = new HashMap<>();
		String currentFile = null;

		int totalLines = 0;
		int coveredLines = 0;
		int totalBranches = 0;
		int coveredBranches = 0;
		int totalFunctions = 0;
		int coveredFunctions = 0;

		for (String line : reportLines) {
			String trimmedLine = line.trim();
			if (trimmedLine.startsWith("SF:") || trimmedLine.startsWith("end_of_record")) {
				if (currentFile != null) {
					coverageResults.put(currentFile, calculateCoverageForFile(currentFile, coveredLines, totalLines,
							coveredBranches, totalBranches, coveredFunctions, totalFunctions));
				}
				currentFile = trimmedLine.startsWith("SF:") ? trimmedLine.substring(3) : null;
				totalLines = coveredLines = totalBranches = coveredBranches = totalFunctions = coveredFunctions = 0;
			} else {
				Map<String, Integer> counters = updateCounters(trimmedLine, totalLines, coveredLines, totalBranches,
						coveredBranches, totalFunctions, coveredFunctions);
				totalLines = counters.get(TOTAL_LINES_KEY);
				coveredLines = counters.get(COVERED_LINES_KEY);
				totalBranches = counters.get(TOTAL_BRANCHES_KEY);
				coveredBranches = counters.get(COVERED_BRANCHES_KEY);
				totalFunctions = counters.get(TOTAL_FUNCTIONS_KEY);
				coveredFunctions = counters.get(COVERED_FUNCTIONS_KEY);
			}
		}

		if (currentFile != null) {
			coverageResults.put(currentFile, calculateCoverageForFile(currentFile, coveredLines, totalLines,
					coveredBranches, totalBranches, coveredFunctions, totalFunctions));
		}

		return coverageResults;
	}

	private static Map<String, Integer> updateCounters(String line, int totalLines, int coveredLines, int totalBranches,
			int coveredBranches, int totalFunctions, int coveredFunctions) {
		Map<String, Integer> counters = new HashMap<>();
		counters.put(TOTAL_LINES_KEY, totalLines);
		counters.put(COVERED_LINES_KEY, coveredLines);
		counters.put(TOTAL_BRANCHES_KEY, totalBranches);
		counters.put(COVERED_BRANCHES_KEY, coveredBranches);
		counters.put(TOTAL_FUNCTIONS_KEY, totalFunctions);
		counters.put(COVERED_FUNCTIONS_KEY, coveredFunctions);
		if (line.startsWith("DA:")) {
			String[] parts = line.substring(3).split(",");
			if (parts.length == 2) {
				int executionCount = Integer.parseInt(parts[1]);
				counters.put(TOTAL_LINES_KEY, totalLines + 1);
				counters.put(COVERED_LINES_KEY, executionCount > 0 ? coveredLines + 1 : coveredLines);
			}
		} else if (line.startsWith("BRDA:")) {
			counters.put(TOTAL_BRANCHES_KEY, totalBranches + 1);
			if (!line.endsWith("-")) {
				String[] parts = line.split(",");
				int hits = Integer.parseInt(parts[3]);
				if (hits > 0) {
					counters.put(COVERED_BRANCHES_KEY, coveredBranches + 1);
				}
			}
		} else if (line.startsWith("FNF:")) {
			counters.put(TOTAL_FUNCTIONS_KEY, Integer.parseInt(line.substring(4)));
		} else if (line.startsWith("FNH:")) {
			counters.put(COVERED_FUNCTIONS_KEY, Integer.parseInt(line.substring(4)));
		}

		return counters;
	}

	private static Map<String, Object[]> calculateCoverageForFile(String file, int coveredLines, int totalLines,
			int coveredBranches, int totalBranches, int coveredFunctions, int totalFunctions) {
		Map<String, Object[]> result = new HashMap<>();

		double lineCoverage = (totalLines > 0) ? (100.0 * coveredLines / totalLines) : 0.0;
		result.put(Messages.ReportParser_9, new Object[] { file, String.format(PERCENTAGE_FORMAT, lineCoverage),
				coveredLines, totalLines - coveredLines, totalLines });

		double branchCoverage = (totalBranches > 0) ? (100.0 * coveredBranches / totalBranches) : 0.0;
		result.put(Messages.ReportParser_11, new Object[] { file, String.format(PERCENTAGE_FORMAT, branchCoverage),
				coveredBranches, totalBranches - coveredBranches, totalBranches });

		double functionCoverage = (totalFunctions > 0) ? (100.0 * coveredFunctions / totalFunctions) : 0.0;
		result.put(Messages.ReportParser_13, new Object[] { file, String.format(PERCENTAGE_FORMAT, functionCoverage),
				coveredFunctions, totalFunctions - coveredFunctions, totalFunctions });

		return result;
	}
}