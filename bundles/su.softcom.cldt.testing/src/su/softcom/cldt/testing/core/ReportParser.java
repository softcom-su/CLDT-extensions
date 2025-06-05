package su.softcom.cldt.testing.core;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

public class ReportParser {
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final String PERCENTAGE_FORMAT = "%.2f%%";
	private static final String TOTAL_LINES_KEY = "totalLines";
	private static final String COVERED_LINES_KEY = "coveredLines";
	private static final String TOTAL_BRANCHES_KEY = "totalBranches";
	private static final String COVERED_BRANCHES_KEY = "coveredBranches";
	private static final String TOTAL_FUNCTIONS_KEY = "totalFunctions";
	private static final String COVERED_FUNCTIONS_KEY = "coveredFunctions";
	private static final String LINE_PREFIX = "DA:";
	private static final String BRANCH_PREFIX = "BRDA:";
	private static final String FUNCTION_PREFIX = "FN:";
	private static final String FUNCTION_EXECUTION_PREFIX = "FNDA:";
	private static final String FILE_PREFIX = "SF:";
	private static final String END_RECORD = "end_of_record";
	private static final String WARNING_INVALID_LINE = "Invalid line coverage format: %s";
	private static final String WARNING_INVALID_BRANCH = "Invalid branch coverage format: %s";
	private static final String WARNING_INVALID_FUNCTION = "Invalid function name format: %s";
	private static final String WARNING_INVALID_FUNCTION_EXEC = "Invalid function coverage format: %s";
	private static final String WARNING_READ_FILE = "Failed to read file for signature line detection: %s";
	private static final String INFO_PARSE_FUNCTION = "Parsing FN: %s, startLine=%d, functionName=%s, signatureLine=%d";
	private static final String INFO_DUPLICATE_FUNCTION = "Skipping duplicate function: %s, startLine=%d";
	private static final String INFO_PARSE_FUNCTION_EXEC = "Parsing FNDA: %s, functionName=%s, executionCount=%d, updated=%b";
	private static final String INFO_CLEAN_NAME = "Cleaning function name: raw=%s, cleaned=%s";
	private static final String INFO_SIGNATURE_LINE = "findSignatureLine: file=%s, bodyLine=%d, foundLine=%s, signatureLine=%d";
	private static final String INFO_FUNCTION_COVERAGE = "Function coverage for file %s: %s";
	private static final ILog LOGGER = Platform.getLog(ReportParser.class);

	private ReportParser() {
	}

	public static class CoverageResult {
		public Map<String, Map<String, Object[]>> fileCoverage;
		public Map<String, List<LineCoverage>> lineCoverage;
		public Map<String, List<LineCoverage>> nonFunctionLineCoverage;
		public Map<String, List<BranchCoverage>> branchCoverage;
		public Map<String, List<FunctionCoverage>> functionCoverage;

		public CoverageResult(Map<String, Map<String, Object[]>> fileCoverage,
				Map<String, List<LineCoverage>> lineCoverage, Map<String, List<LineCoverage>> nonFunctionLineCoverage,
				Map<String, List<BranchCoverage>> branchCoverage,
				Map<String, List<FunctionCoverage>> functionCoverage) {
			this.fileCoverage = fileCoverage;
			this.lineCoverage = lineCoverage;
			this.nonFunctionLineCoverage = nonFunctionLineCoverage;
			this.branchCoverage = branchCoverage;
			this.functionCoverage = functionCoverage;
		}
	}

	public static class LineCoverage {
		public int lineNumber;
		public int executionCount;

		public LineCoverage(int lineNumber, int executionCount) {
			this.lineNumber = lineNumber;
			this.executionCount = executionCount;
		}
	}

	public static class BranchCoverage {
		public int lineNumber;
		public boolean covered;

		public BranchCoverage(int lineNumber, boolean covered) {
			this.lineNumber = lineNumber;
			this.covered = covered;
		}
	}

	public static class FunctionCoverage {
		public String name;
		public int executionCount;
		public int startLine;
		public int endLine;
		public int signatureLine;

		public FunctionCoverage(String name, int executionCount, int startLine, int signatureLine) {
			this.name = name;
			this.executionCount = executionCount;
			this.startLine = startLine;
			this.endLine = -1;
			this.signatureLine = signatureLine;
		}

		@Override
		public String toString() {
			return String.format("{name=%s, startLine=%d, signatureLine=%d, executionCount=%d}", name, startLine,
					signatureLine, executionCount);
		}
	}

	public static CoverageResult parseLcovReport(List<String> reportLines) {
		Map<String, Map<String, Object[]>> coverageResults = new HashMap<>();
		Map<String, List<LineCoverage>> lineCoverage = new HashMap<>();
		Map<String, List<LineCoverage>> nonFunctionLineCoverage = new HashMap<>();
		Map<String, List<BranchCoverage>> branchCoverage = new HashMap<>();
		Map<String, List<FunctionCoverage>> functionCoverage = new HashMap<>();
		FileContext context = new FileContext();

		for (String line : reportLines) {
			String trimmedLine = line.trim();
			if (trimmedLine.startsWith(FILE_PREFIX) || trimmedLine.startsWith(END_RECORD)) {
				if (context.currentFile != null) {
					processFileContext(context, coverageResults, lineCoverage, nonFunctionLineCoverage, branchCoverage,
							functionCoverage);
				}
				if (trimmedLine.startsWith(FILE_PREFIX)) {
					context.reset(trimmedLine.substring(3));
				} else {
					context.currentFile = null;
				}
			} else {
				updateFileContext(context, trimmedLine);
			}
		}

		if (context.currentFile != null) {
			processFileContext(context, coverageResults, lineCoverage, nonFunctionLineCoverage, branchCoverage,
					functionCoverage);
		}

		return new CoverageResult(coverageResults, lineCoverage, nonFunctionLineCoverage, branchCoverage,
				functionCoverage);
	}

	private static void processFileContext(FileContext context, Map<String, Map<String, Object[]>> coverageResults,
			Map<String, List<LineCoverage>> lineCoverage, Map<String, List<LineCoverage>> nonFunctionLineCoverage,
			Map<String, List<BranchCoverage>> branchCoverage, Map<String, List<FunctionCoverage>> functionCoverage) {
		if (!context.currentFunctionCoverage.isEmpty()) {
			assignFunctionEndLines(context);
			distributeLineCoverage(context);
		} else {
			context.currentNonFunctionLineCoverage.addAll(context.tempLineCoverage);
		}
		coverageResults.put(context.currentFile, calculateCoverageForFile(context));
		lineCoverage.put(context.currentFile, context.currentLineCoverage);
		nonFunctionLineCoverage.put(context.currentFile, context.currentNonFunctionLineCoverage);
		branchCoverage.put(context.currentFile, context.currentBranchCoverage);
		functionCoverage.put(context.currentFile, context.currentFunctionCoverage);
		// Log function coverage
		LOGGER.log(new Status(IStatus.INFO, PLUGIN_ID,
				String.format(INFO_FUNCTION_COVERAGE, context.currentFile, context.currentFunctionCoverage)));
	}

	private static void assignFunctionEndLines(FileContext context) {
		context.currentFunctionCoverage.sort((f1, f2) -> Integer.compare(f1.startLine, f2.startLine));
		for (int i = 0; i < context.currentFunctionCoverage.size() - 1; i++) {
			FunctionCoverage current = context.currentFunctionCoverage.get(i);
			FunctionCoverage next = context.currentFunctionCoverage.get(i + 1);
			current.endLine = next.startLine - 1;
			if (!hasExecutableLines(context.tempLineCoverage, current.startLine, current.endLine)) {
				current.endLine = current.startLine;
			}
		}
		FunctionCoverage lastFunction = context.currentFunctionCoverage.get(context.currentFunctionCoverage.size() - 1);
		lastFunction.endLine = context.maxLineNumber;
		if (!hasExecutableLines(context.tempLineCoverage, lastFunction.startLine, lastFunction.endLine)) {
			lastFunction.endLine = lastFunction.startLine;
		}
	}

	private static boolean hasExecutableLines(List<LineCoverage> lines, int startLine, int endLine) {
		return lines.stream().anyMatch(lc -> lc.lineNumber > startLine && lc.lineNumber < endLine);
	}

	private static void distributeLineCoverage(FileContext context) {
		for (LineCoverage lineCov : context.tempLineCoverage) {
			boolean belongsToFunction = context.currentFunctionCoverage.stream()
					.anyMatch(f -> lineCov.lineNumber >= f.startLine && lineCov.lineNumber <= f.endLine);
			if (belongsToFunction) {
				context.currentLineCoverage.add(lineCov);
			} else {
				context.currentNonFunctionLineCoverage.add(lineCov);
			}
		}
	}

	private static void updateFileContext(FileContext context, String line) {
		Map<String, Integer> counters = updateCounters(line, context.totalLines, context.coveredLines,
				context.totalBranches, context.coveredBranches, context.totalFunctions, context.coveredFunctions);
		context.totalLines = counters.get(TOTAL_LINES_KEY);
		context.coveredLines = counters.get(COVERED_LINES_KEY);
		context.totalBranches = counters.get(TOTAL_BRANCHES_KEY);
		context.coveredBranches = counters.get(COVERED_BRANCHES_KEY);
		context.totalFunctions = counters.get(TOTAL_FUNCTIONS_KEY);
		context.coveredFunctions = counters.get(COVERED_FUNCTIONS_KEY);

		if (line.startsWith(LINE_PREFIX) && context.tempLineCoverage != null) {
			parseLineCoverage(context, line);
		} else if (line.startsWith(BRANCH_PREFIX) && context.currentBranchCoverage != null) {
			parseBranchCoverage(context, line);
		} else if (line.startsWith(FUNCTION_PREFIX) && context.currentFunctionCoverage != null) {
			parseFunctionCoverage(context, line);
		} else if (line.startsWith(FUNCTION_EXECUTION_PREFIX) && context.currentFunctionCoverage != null) {
			parseFunctionExecution(context, line);
		}
	}

	private static void parseLineCoverage(FileContext context, String line) {
		try {
			String[] parts = line.substring(3).split(",");
			if (parts.length == 2) {
				int lineNumber = Integer.parseInt(parts[0]);
				int executionCount = Integer.parseInt(parts[1]);
				if (executionCount >= 0) {
					context.tempLineCoverage.add(new LineCoverage(lineNumber, executionCount));
					context.maxLineNumber = Math.max(context.maxLineNumber, lineNumber);
				}
			}
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			logWarning(String.format(WARNING_INVALID_LINE, line), e);
		}
	}

	private static void parseBranchCoverage(FileContext context, String line) {
		try {
			String[] parts = line.substring(5).split(",");
			if (parts.length == 4) {
				int lineNumber = Integer.parseInt(parts[0]);
				int hits = Integer.parseInt(parts[3]);
				context.currentBranchCoverage.add(new BranchCoverage(lineNumber, hits > 0));
			}
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			logWarning(String.format(WARNING_INVALID_BRANCH, line), e);
		}
	}

	private static void parseFunctionCoverage(FileContext context, String line) {
		try {
			String[] parts = line.substring(3).split(",");
			if (parts.length >= 2) {
				int startLine = Integer.parseInt(parts[0]);
				String functionName = cleanRawName(parts[1]);
				// Check for duplicate functions with same startLine and name
				boolean exists = context.currentFunctionCoverage.stream()
						.anyMatch(f -> f.startLine == startLine && f.name.equals(functionName));
				if (exists) {
					LOGGER.log(new Status(IStatus.INFO, PLUGIN_ID,
							String.format(INFO_DUPLICATE_FUNCTION, functionName, startLine)));
					return;
				}
				int signatureLine = findSignatureLine(context.currentFile, startLine);
				context.currentFunctionCoverage.add(new FunctionCoverage(functionName, 0, startLine, signatureLine));
				LOGGER.log(new Status(IStatus.INFO, PLUGIN_ID,
						String.format(INFO_PARSE_FUNCTION, line, startLine, functionName, signatureLine)));
			}
		} catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
			logWarning(String.format(WARNING_INVALID_FUNCTION, line), e);
		}
	}

	private static void parseFunctionExecution(FileContext context, String line) {
		try {
			String[] parts = line.substring(5).split(",");
			if (parts.length >= 2) {
				int executionCount = Integer.parseInt(parts[0]);
				String functionName = cleanRawName(parts[1]);
				boolean updated = context.currentFunctionCoverage.stream().filter(f -> f.name.equals(functionName))
						.findFirst().map(f -> {
							f.executionCount = executionCount;
							return true;
						}).orElse(false);
				LOGGER.log(new Status(IStatus.INFO, PLUGIN_ID,
						String.format(INFO_PARSE_FUNCTION_EXEC, line, functionName, executionCount, updated)));
				if (!updated) {
					logWarning(String.format("No function found for FNDA: %s", line), null);
				}
			}
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			logWarning(String.format(WARNING_INVALID_FUNCTION_EXEC, line), e);
		}
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

		try {
			if (line.startsWith("LF:")) {
				counters.put(TOTAL_LINES_KEY, Integer.parseInt(line.substring(3)));
			} else if (line.startsWith("LH:")) {
				counters.put(COVERED_LINES_KEY, Integer.parseInt(line.substring(3)));
			} else if (line.startsWith("BRF:")) {
				counters.put(TOTAL_BRANCHES_KEY, Integer.parseInt(line.substring(4)));
			} else if (line.startsWith("BRH:")) {
				counters.put(COVERED_BRANCHES_KEY, Integer.parseInt(line.substring(4)));
			} else if (line.startsWith("FNF:")) {
				counters.put(TOTAL_FUNCTIONS_KEY, Integer.parseInt(line.substring(4)));
			} else if (line.startsWith("FNH:")) {
				counters.put(COVERED_FUNCTIONS_KEY, Integer.parseInt(line.substring(4)));
			}
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			logWarning("Invalid counter format: " + line, e);
		}

		return counters;
	}

	private static Map<String, Object[]> calculateCoverageForFile(FileContext context) {
		Map<String, Object[]> result = new HashMap<>();
		double lineCoverage = context.totalLines > 0 ? (100.0 * context.coveredLines / context.totalLines) : 0.0;
		result.put(Messages.ReportParser_9,
				new Object[] { context.currentFile, String.format(PERCENTAGE_FORMAT, lineCoverage),
						context.coveredLines, context.totalLines - context.coveredLines, context.totalLines });

		double branchCoverage = context.totalBranches > 0 ? (100.0 * context.coveredBranches / context.totalBranches)
				: 0.0;
		result.put(Messages.ReportParser_11,
				new Object[] { context.currentFile, String.format(PERCENTAGE_FORMAT, branchCoverage),
						context.coveredBranches, context.totalBranches - context.coveredBranches,
						context.totalBranches });

		double functionCoverage = context.totalFunctions > 0
				? (100.0 * context.coveredFunctions / context.totalFunctions)
				: 0.0;
		result.put(Messages.ReportParser_13,
				new Object[] { context.currentFile, String.format(PERCENTAGE_FORMAT, functionCoverage),
						context.coveredFunctions, context.totalFunctions - context.coveredFunctions,
						context.totalFunctions });

		return result;
	}

	private static void logWarning(String message, Throwable cause) {
		LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, message, cause));
	}

	private static int findSignatureLine(String filePath, int bodyLine) {
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			String line;
			int currentLine = 0;
			String prevLine = null;
			boolean foundBody = false;
			while ((line = reader.readLine()) != null) {
				currentLine++;
				String trimmed = line.trim();
				if (currentLine == bodyLine && trimmed.startsWith("{")) {
					foundBody = true;
					if (prevLine != null && !prevLine.trim().isEmpty()) {
						LOGGER.log(new Status(IStatus.INFO, PLUGIN_ID,
								String.format(INFO_SIGNATURE_LINE, filePath, bodyLine, prevLine, currentLine - 1)));
						return currentLine - 1;
					}
				} else if (currentLine >= bodyLine && !foundBody) {
					if (trimmed.contains(")") && !trimmed.startsWith("//") && !trimmed.startsWith("/*")) {
						LOGGER.log(new Status(IStatus.INFO, PLUGIN_ID,
								String.format(INFO_SIGNATURE_LINE, filePath, bodyLine, line, currentLine)));
						return currentLine;
					}
				}
				prevLine = line;
			}
			if (!foundBody) {
				try (BufferedReader reader2 = new BufferedReader(new FileReader(filePath))) {
					while ((line = reader2.readLine()) != null) {
						currentLine++;
						if (currentLine == bodyLine) {
							if (line.trim().contains(")") && !line.trim().startsWith("//")) {
								LOGGER.log(new Status(IStatus.INFO, PLUGIN_ID,
										String.format(INFO_SIGNATURE_LINE, filePath, bodyLine, line, bodyLine)));
								return bodyLine;
							}
							break;
						}
					}
				}
			}
		} catch (IOException e) {
			logWarning(String.format(WARNING_READ_FILE, filePath), e);
		}
		LOGGER.log(new Status(IStatus.INFO, PLUGIN_ID,
				String.format(INFO_SIGNATURE_LINE, filePath, bodyLine, "none", bodyLine)));
		return bodyLine;
	}

	private static String cleanRawName(String rawName) {
		String cleaned = rawName.trim();
		if (cleaned.startsWith("_") && cleaned.length() > 1) {
			cleaned = cleaned.substring(1);
		}
		cleaned = cleaned.replaceAll("@\\d+$", "");
		LOGGER.log(new Status(IStatus.INFO, PLUGIN_ID, String.format(INFO_CLEAN_NAME, rawName, cleaned)));
		return cleaned;
	}

	private static class FileContext {
		String currentFile;
		int totalLines;
		int coveredLines;
		int totalBranches;
		int coveredBranches;
		int totalFunctions;
		int coveredFunctions;
		List<LineCoverage> currentLineCoverage;
		List<LineCoverage> currentNonFunctionLineCoverage;
		List<LineCoverage> tempLineCoverage;
		List<BranchCoverage> currentBranchCoverage;
		List<FunctionCoverage> currentFunctionCoverage;
		int maxLineNumber;

		void reset(String file) {
			currentFile = file;
			totalLines = coveredLines = totalBranches = coveredBranches = totalFunctions = coveredFunctions = 0;
			currentLineCoverage = new ArrayList<>();
			currentNonFunctionLineCoverage = new ArrayList<>();
			tempLineCoverage = new ArrayList<>();
			currentBranchCoverage = new ArrayList<>();
			currentFunctionCoverage = new ArrayList<>();
			maxLineNumber = 0;
		}
	}
}