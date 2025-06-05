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
	private static final String TOTAL_METHODS_KEY = "totalMethods";
	private static final String COVERED_METHODS_KEY = "coveredMethods";
	private static final String LINE_PREFIX = "DA:";
	private static final String BRANCH_PREFIX = "BRDA:";
	private static final String METHOD_PREFIX = "FN:";
	private static final String METHOD_EXECUTION_PREFIX = "FNDA:";
	private static final String FILE_PREFIX = "SF:";
	private static final String END_RECORD = "end_of_record";
	private static final String WARNING_INVALID_LINE = "Invalid line coverage format: %s";
	private static final String WARNING_INVALID_BRANCH = "Invalid branch coverage format: %s";
	private static final String WARNING_INVALID_METHOD = "Invalid method name format: %s";
	private static final String WARNING_INVALID_METHOD_EXEC = "Invalid method coverage format: %s";
	private static final String WARNING_READ_FILE = "Failed to read file for signature line detection: %s";
	private static final ILog LOGGER = Platform.getLog(ReportParser.class);

	private ReportParser() {
	}

	public static class CoverageResult {
		public Map<String, Map<String, Object[]>> fileCoverage;
		public Map<String, List<LineCoverage>> lineCoverage;
		public Map<String, List<LineCoverage>> nonMethodLineCoverage;
		public Map<String, List<BranchCoverage>> branchCoverage;
		public Map<String, List<MethodCoverage>> methodCoverage;

		public CoverageResult(Map<String, Map<String, Object[]>> fileCoverage,
				Map<String, List<LineCoverage>> lineCoverage, Map<String, List<LineCoverage>> nonMethodLineCoverage) {
			this(fileCoverage, lineCoverage, nonMethodLineCoverage, new HashMap<>(), new HashMap<>());
		}

		public CoverageResult(Map<String, Map<String, Object[]>> fileCoverage,
				Map<String, List<LineCoverage>> lineCoverage, Map<String, List<LineCoverage>> nonMethodLineCoverage,
				Map<String, List<MethodCoverage>> methodCoverage, Map<String, List<BranchCoverage>> branchCoverage) {
			this.fileCoverage = fileCoverage;
			this.lineCoverage = lineCoverage;
			this.nonMethodLineCoverage = nonMethodLineCoverage;
			this.methodCoverage = methodCoverage;
			this.branchCoverage = branchCoverage;
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

	public static class MethodCoverage {
		public String name;
		public int executionCount;
		public int startLine;
		public int endLine;
		public int signatureLine;

		public MethodCoverage(String name, int executionCount, int startLine, int signatureLine) {
			this.name = name;
			this.executionCount = executionCount;
			this.startLine = startLine;
			this.endLine = -1;
			this.signatureLine = signatureLine;
		}
	}

	public static CoverageResult parseLcovReport(List<String> reportLines) {
		Map<String, Map<String, Object[]>> coverageResults = new HashMap<>();
		Map<String, List<LineCoverage>> lineCoverage = new HashMap<>();
		Map<String, List<LineCoverage>> nonMethodLineCoverage = new HashMap<>();
		Map<String, List<BranchCoverage>> branchCoverage = new HashMap<>();
		Map<String, List<MethodCoverage>> methodCoverage = new HashMap<>();
		FileContext context = new FileContext();

		for (String line : reportLines) {
			String trimmedLine = line.trim();
			if (trimmedLine.startsWith(FILE_PREFIX) || trimmedLine.startsWith(END_RECORD)) {
				if (context.currentFile != null) {
					processFileContext(context, coverageResults, lineCoverage, nonMethodLineCoverage, branchCoverage,
							methodCoverage);
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
			processFileContext(context, coverageResults, lineCoverage, nonMethodLineCoverage, branchCoverage,
					methodCoverage);
		}

		return new CoverageResult(coverageResults, lineCoverage, nonMethodLineCoverage, methodCoverage, branchCoverage);
	}

	private static void processFileContext(FileContext context, Map<String, Map<String, Object[]>> coverageResults,
			Map<String, List<LineCoverage>> lineCoverage, Map<String, List<LineCoverage>> nonMethodLineCoverage,
			Map<String, List<BranchCoverage>> branchCoverage, Map<String, List<MethodCoverage>> methodCoverage) {
		if (!context.currentMethodCoverage.isEmpty()) {
			assignMethodEndLines(context);
			distributeLineCoverage(context);
			context.totalLines = context.coveredLines = 0;
			calculateLineCoverage(context);
		} else {
			context.currentNonMethodLineCoverage.addAll(context.tempLineCoverage);
			context.totalLines = context.coveredLines = 0;
			calculateLineCoverage(context);
		}
		coverageResults.put(context.currentFile, calculateCoverageForFile(context));
		lineCoverage.put(context.currentFile, context.currentLineCoverage);
		nonMethodLineCoverage.put(context.currentFile, context.currentNonMethodLineCoverage);
		branchCoverage.put(context.currentFile, context.currentBranchCoverage);
		methodCoverage.put(context.currentFile, context.currentMethodCoverage);
	}

	private static void assignMethodEndLines(FileContext context) {
		context.currentMethodCoverage.sort((m1, m2) -> Integer.compare(m1.startLine, m2.startLine));
		for (int i = 0; i < context.currentMethodCoverage.size() - 1; i++) {
			MethodCoverage current = context.currentMethodCoverage.get(i);
			MethodCoverage next = context.currentMethodCoverage.get(i + 1);
			current.endLine = next.startLine - 1;
			if (!hasExecutableLines(context.tempLineCoverage, current.startLine, current.endLine)) {
				current.endLine = current.startLine;
			}
		}
		MethodCoverage lastMethod = context.currentMethodCoverage.get(context.currentMethodCoverage.size() - 1);
		lastMethod.endLine = context.maxLineNumber;
		if (!hasExecutableLines(context.tempLineCoverage, lastMethod.startLine, lastMethod.endLine)) {
			lastMethod.endLine = lastMethod.startLine;
		}
	}

	private static boolean hasExecutableLines(List<LineCoverage> lines, int startLine, int endLine) {
		return lines.stream().anyMatch(lc -> lc.lineNumber > startLine && lc.lineNumber < endLine);
	}

	private static void distributeLineCoverage(FileContext context) {
		for (LineCoverage lineCov : context.tempLineCoverage) {
			boolean belongsToMethod = context.currentMethodCoverage.stream()
					.anyMatch(m -> lineCov.lineNumber >= m.startLine && lineCov.lineNumber <= m.endLine);
			if (belongsToMethod) {
				context.currentLineCoverage.add(lineCov);
			} else {
				context.currentNonMethodLineCoverage.add(lineCov);
			}
		}
	}

	private static void calculateLineCoverage(FileContext context) {
		List<LineCoverage> allLines = new ArrayList<>(context.currentLineCoverage);
		allLines.addAll(context.currentNonMethodLineCoverage);
		for (LineCoverage lineCov : allLines) {
			context.totalLines++;
			if (lineCov.executionCount > 0) {
				context.coveredLines++;
			}
		}
	}

	private static void updateFileContext(FileContext context, String line) {
		Map<String, Integer> counters = updateCounters(line, context.totalLines, context.coveredLines,
				context.totalBranches, context.coveredBranches, context.totalMethods, context.coveredMethods);
		context.totalLines = counters.get(TOTAL_LINES_KEY);
		context.coveredLines = counters.get(COVERED_LINES_KEY);
		context.totalBranches = counters.get(TOTAL_BRANCHES_KEY);
		context.coveredBranches = counters.get(COVERED_BRANCHES_KEY);
		context.totalMethods = counters.get(TOTAL_METHODS_KEY);
		context.coveredMethods = counters.get(COVERED_METHODS_KEY);

		if (line.startsWith(LINE_PREFIX) && context.tempLineCoverage != null) {
			parseLineCoverage(context, line);
		} else if (line.startsWith(BRANCH_PREFIX) && context.currentBranchCoverage != null) {
			parseBranchCoverage(context, line);
		} else if (line.startsWith(METHOD_PREFIX) && context.currentMethodCoverage != null) {
			parseMethodCoverage(context, line);
		} else if (line.startsWith(METHOD_EXECUTION_PREFIX) && context.currentMethodCoverage != null) {
			parseMethodExecution(context, line);
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

	private static void parseMethodCoverage(FileContext context, String line) {
		try {
			String[] parts = line.substring(3).split(",");
			if (parts.length >= 2) {
				int startLine = Integer.parseInt(parts[0]);
				String mangledName = parts[1];
				String methodName = CoverageDataProcessor.demangleCppName(mangledName, context.currentFile);
				int signatureLine = findSignatureLine(context.currentFile, startLine);
				context.currentMethodCoverage.add(new MethodCoverage(methodName, 0, startLine, signatureLine));
			}
		} catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
			logWarning(String.format(WARNING_INVALID_METHOD, line), e);
		}
	}

	private static void parseMethodExecution(FileContext context, String line) {
		try {
			String[] parts = line.substring(5).split(",");
			if (parts.length >= 2) {
				int executionCount = Integer.parseInt(parts[0]);
				String methodName = CoverageDataProcessor.demangleCppName(parts[1], context.currentFile);
				context.currentMethodCoverage.stream().filter(m -> m.name.equals(methodName)).findFirst()
						.ifPresent(m -> m.executionCount = executionCount);
			}
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			logWarning(String.format(WARNING_INVALID_METHOD_EXEC, line), e);
		}
	}

	private static Map<String, Integer> updateCounters(String line, int totalLines, int coveredLines, int totalBranches,
			int coveredBranches, int totalMethods, int coveredMethods) {
		Map<String, Integer> counters = new HashMap<>();
		counters.put(TOTAL_LINES_KEY, totalLines);
		counters.put(COVERED_LINES_KEY, coveredLines);
		counters.put(TOTAL_BRANCHES_KEY, totalBranches);
		counters.put(COVERED_BRANCHES_KEY, coveredBranches);
		counters.put(TOTAL_METHODS_KEY, totalMethods);
		counters.put(COVERED_METHODS_KEY, coveredMethods);

		try {
			if (line.startsWith(LINE_PREFIX)) {
				String[] parts = line.substring(3).split(",");
				if (parts.length == 2) {
					int executionCount = Integer.parseInt(parts[1]);
					if (executionCount >= 0) {
						counters.put(TOTAL_LINES_KEY, totalLines + 1);
						counters.put(COVERED_LINES_KEY, executionCount > 0 ? coveredLines + 1 : coveredLines);
					}
				}
			} else if (line.startsWith(BRANCH_PREFIX)) {
				counters.put(TOTAL_BRANCHES_KEY, totalBranches + 1);
				if (!line.endsWith("-")) {
					String[] parts = line.split(",");
					int hits = Integer.parseInt(parts[3]);
					if (hits > 0) {
						counters.put(COVERED_BRANCHES_KEY, coveredBranches + 1);
					}
				}
			} else if (line.startsWith("FNF:")) {
				counters.put(TOTAL_METHODS_KEY, Integer.parseInt(line.substring(4)));
			} else if (line.startsWith("FNH:")) {
				counters.put(COVERED_METHODS_KEY, Integer.parseInt(line.substring(4)));
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

		double methodCoverage = context.totalMethods > 0 ? (100.0 * context.coveredMethods / context.totalMethods)
				: 0.0;
		result.put(Messages.ReportParser_13,
				new Object[] { context.currentFile, String.format(PERCENTAGE_FORMAT, methodCoverage),
						context.coveredMethods, context.totalMethods - context.coveredMethods, context.totalMethods });

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
			while ((line = reader.readLine()) != null) {
				currentLine++;
				if (currentLine == bodyLine && line.trim().startsWith("{")) {
					return prevLine != null ? bodyLine - 1 : bodyLine;
				}
				prevLine = line;
			}
		} catch (IOException e) {
			logWarning(String.format(WARNING_READ_FILE, filePath), e);
		}
		return bodyLine;
	}

	private static class FileContext {
		String currentFile;
		int totalLines;
		int coveredLines;
		int totalBranches;
		int coveredBranches;
		int totalMethods;
		int coveredMethods;
		List<LineCoverage> currentLineCoverage;
		List<LineCoverage> currentNonMethodLineCoverage;
		List<LineCoverage> tempLineCoverage;
		List<BranchCoverage> currentBranchCoverage;
		List<MethodCoverage> currentMethodCoverage;
		int maxLineNumber;

		void reset(String file) {
			currentFile = file;
			totalLines = coveredLines = totalBranches = coveredBranches = totalMethods = coveredMethods = 0;
			currentLineCoverage = new ArrayList<>();
			currentNonMethodLineCoverage = new ArrayList<>();
			tempLineCoverage = new ArrayList<>();
			currentBranchCoverage = new ArrayList<>();
			currentMethodCoverage = new ArrayList<>();
			maxLineNumber = 0;
		}
	}
}