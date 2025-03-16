package su.softcom.cldt.testing.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportParser {

	public static Map<String, Map<String, Object[]>> parseLcovReport(List<String> reportLines) {
		Map<String, Map<String, Object[]>> coverageResults = new HashMap<>();
		String currentFile = null;

		int totalLines = 0, coveredLines = 0;
		int totalBranches = 0, coveredBranches = 0;
		int totalFunctions = 0, coveredFunctions = 0;

		for (String line : reportLines) {
			line = line.trim();
			if (line.startsWith("SF:")) {
				if (currentFile != null) {
					coverageResults.put(currentFile, calculateCoverageForFile(currentFile, coveredLines, totalLines,
							coveredBranches, totalBranches, coveredFunctions, totalFunctions));
				}
				currentFile = line.substring(3);
				totalLines = coveredLines = totalBranches = coveredBranches = totalFunctions = coveredFunctions = 0;
			} else if (line.startsWith("DA:")) {
				String[] parts = line.substring(3).split(",");
				if (parts.length == 2) {
					int executionCount = Integer.parseInt(parts[1]);
					totalLines++;
					if (executionCount > 0) {
						coveredLines++;
					}
				}
			} else if (line.startsWith("BRDA:")) {
				totalBranches++;
				if (!line.endsWith("-")) {
					String[] parts = line.split(",");
					int hits = Integer.parseInt(parts[3]);
					if (hits > 0) {
						coveredBranches++;
					}
				}
			} else if (line.startsWith("FNF:")) {
				totalFunctions = Integer.parseInt(line.substring(4));
			} else if (line.startsWith("FNH:")) {
				coveredFunctions = Integer.parseInt(line.substring(4));
			} else if (line.startsWith("end_of_record")) {
				if (currentFile != null) {
					coverageResults.put(currentFile, calculateCoverageForFile(currentFile, coveredLines, totalLines,
							coveredBranches, totalBranches, coveredFunctions, totalFunctions));
					currentFile = null;
				}
			}
		}

		if (currentFile != null) {
			coverageResults.put(currentFile, calculateCoverageForFile(currentFile, coveredLines, totalLines,
					coveredBranches, totalBranches, coveredFunctions, totalFunctions));
		}

		return coverageResults;
	}

	private static Map<String, Object[]> calculateCoverageForFile(String file, int coveredLines, int totalLines,
			int coveredBranches, int totalBranches, int coveredFunctions, int totalFunctions) {
		Map<String, Object[]> result = new HashMap<>();

		double lineCoverage = (totalLines > 0) ? (100.0 * coveredLines / totalLines) : 0.0;
		result.put(Messages.ReportParser_9, new Object[] { file, String.format("%.2f%%", lineCoverage), coveredLines,
				totalLines - coveredLines, totalLines });

		double branchCoverage = (totalBranches > 0) ? (100.0 * coveredBranches / totalBranches) : 0.0;
		result.put(Messages.ReportParser_11,
				new Object[] { file, String.format("%.2f%%", branchCoverage), coveredBranches,
						totalBranches - coveredBranches, totalBranches });

		double functionCoverage = (totalFunctions > 0) ? (100.0 * coveredFunctions / totalFunctions) : 0.0;
		result.put(Messages.ReportParser_13, new Object[] { file, String.format("%.2f%%", functionCoverage),
				coveredFunctions, totalFunctions - coveredFunctions, totalFunctions });

		return result;
	}
}