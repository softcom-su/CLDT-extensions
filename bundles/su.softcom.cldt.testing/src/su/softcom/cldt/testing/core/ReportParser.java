package su.softcom.cldt.testing.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import su.softcom.cldt.testing.ui.CoverageDataManager;

public class ReportParser {
	private static final ILog LOGGER = Platform.getLog(ReportParser.class);
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
	private static final String FILE_PREFIX = "SF:";
	private static final String END_RECORD = "end_of_record";

	public static final String FUNCTION_EXECUTION_PREFIX = "FNDA:";

	private ReportParser() {
	}

	public record CoverageResult(Map<String, Map<String, Object[]>> fileCoverage,
			Map<String, List<LineCoverage>> lineCoverage, Map<String, List<LineCoverage>> nonFunctionLineCoverage,
			Map<String, List<BranchCoverage>> branchCoverage, Map<String, List<FunctionCoverage>> functionCoverage,
			Map<String, List<FunctionCoverage>> annotationFunctionCoverage) {
	}

	public record LineCoverage(int lineNumber, int executionCount) {
	}

	public record BranchCoverage(int lineNumber, boolean covered) {
	}

	public record FunctionCoverage(String name, List<String> mangledNames, int executionCount, int startLine,
			int endLine, int signatureLine, boolean isLambda) {
		public static FunctionCoverage create(String name, List<String> mangledNames, int executionCount, int startLine,
				int signatureLine, boolean isLambda) {
			return new FunctionCoverage(name, mangledNames, executionCount, startLine, -1, signatureLine, isLambda);
		}
	}

	public record MangledDemangledPair(String mangledName, String demangledName) {
	}

	public record FileContext(String currentFile, int totalLines, int coveredLines, int totalBranches,
			int coveredBranches, int totalFunctions, int coveredFunctions, List<LineCoverage> currentLineCoverage,
			List<LineCoverage> currentNonFunctionLineCoverage, List<LineCoverage> tempLineCoverage,
			List<BranchCoverage> currentBranchCoverage, List<FunctionCoverage> currentFunctionCoverage,
			List<FunctionCoverage> annotationFunctionCoverage,
			Map<Integer, List<MangledDemangledPair>> tempFunctionNames, List<String> fnExecutionLines,
			int maxLineNumber) {
		public FileContext reset(String file) {
			return new FileContext(file, 0, 0, 0, 0, 0, 0, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
					new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new HashMap<>(), new ArrayList<>(), 0);
		}
	}

	public static CoverageResult parseLcovReport(List<String> reportLines) {
		Map<String, Map<String, Object[]>> coverageResults = new HashMap<>();
		Map<String, List<LineCoverage>> lineCoverage = new HashMap<>();
		Map<String, List<LineCoverage>> nonFunctionLineCoverage = new HashMap<>();
		Map<String, List<BranchCoverage>> branchCoverage = new HashMap<>();
		Map<String, List<FunctionCoverage>> functionCoverage = new HashMap<>();
		Map<String, List<FunctionCoverage>> annotationFunctionCoverage = new HashMap<>();
		FileContext context = new FileContext(null, 0, 0, 0, 0, 0, 0, new ArrayList<>(), new ArrayList<>(),
				new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new HashMap<>(),
				new ArrayList<>(), 0);

		for (String line : reportLines) {
			String trimmedLine = line.trim();
			if (trimmedLine.startsWith(FILE_PREFIX) || trimmedLine.startsWith(END_RECORD)) {
				if (context.currentFile != null) {
					processFileContext(context, coverageResults, lineCoverage, nonFunctionLineCoverage, branchCoverage,
							functionCoverage, annotationFunctionCoverage);
				}
				if (trimmedLine.startsWith(FILE_PREFIX)) {
					context = context.reset(trimmedLine.substring(3));
				} else {
					context = context.reset(null);
				}
			} else {
				context = updateFileContext(context, trimmedLine);
			}
		}

		if (context.currentFile != null) {
			processFileContext(context, coverageResults, lineCoverage, nonFunctionLineCoverage, branchCoverage,
					functionCoverage, annotationFunctionCoverage);
		}

		CoverageResult result = new CoverageResult(coverageResults, lineCoverage, nonFunctionLineCoverage,
				branchCoverage, functionCoverage, annotationFunctionCoverage);
		CoverageDataManager.getInstance().setCoverageData(result, List.of());
		return result;
	}

	private static void processFileContext(FileContext context, Map<String, Map<String, Object[]>> coverageResults,
			Map<String, List<LineCoverage>> lineCoverage, Map<String, List<LineCoverage>> nonFunctionLineCoverage,
			Map<String, List<BranchCoverage>> branchCoverage, Map<String, List<FunctionCoverage>> functionCoverage,
			Map<String, List<FunctionCoverage>> annotationFunctionCoverage) {
		FunctionCoverageAnalyzer.processFunctionCoverage(context);

		coverageResults.put(context.currentFile, calculateCoverageForFile(context));
		lineCoverage.put(context.currentFile, context.currentLineCoverage);
		nonFunctionLineCoverage.put(context.currentFile, context.currentNonFunctionLineCoverage);
		branchCoverage.put(context.currentFile, context.currentBranchCoverage);
		functionCoverage.put(context.currentFile, context.currentFunctionCoverage);
		annotationFunctionCoverage.put(context.currentFile, context.annotationFunctionCoverage);
	}

	private static FileContext updateFileContext(FileContext context, String line) {
		Map<String, Integer> counters = updateCounters(line, context.totalLines, context.coveredLines,
				context.totalBranches, context.coveredBranches, context.totalFunctions, context.coveredFunctions);
		FileContext updatedContext = new FileContext(context.currentFile, counters.get(TOTAL_LINES_KEY),
				counters.get(COVERED_LINES_KEY), counters.get(TOTAL_BRANCHES_KEY), counters.get(COVERED_BRANCHES_KEY),
				counters.get(TOTAL_FUNCTIONS_KEY), counters.get(COVERED_FUNCTIONS_KEY), context.currentLineCoverage,
				context.currentNonFunctionLineCoverage, context.tempLineCoverage, context.currentBranchCoverage,
				context.currentFunctionCoverage, context.annotationFunctionCoverage, context.tempFunctionNames,
				context.fnExecutionLines, context.maxLineNumber);

		if (line.startsWith(LINE_PREFIX) && updatedContext.tempLineCoverage != null) {
			return parseLineCoverage(updatedContext, line);
		} else if (line.startsWith(BRANCH_PREFIX) && updatedContext.currentBranchCoverage != null) {
			return parseBranchCoverage(updatedContext, line);
		} else if (line.startsWith(FUNCTION_PREFIX) && updatedContext.currentFunctionCoverage != null) {
			return parseFunctionCoverage(updatedContext, line);
		} else if (line.startsWith(FUNCTION_EXECUTION_PREFIX) && updatedContext.currentFunctionCoverage != null) {
			return parseFunctionExecution(updatedContext, line);
		}
		return updatedContext;
	}

	private static FileContext parseLineCoverage(FileContext context, String line) {
		try {
			String[] parts = line.substring(3).split(",");
			if (parts.length == 2) {
				int lineNumber = Integer.parseInt(parts[0]);
				int executionCount = Integer.parseInt(parts[1]);
				if (executionCount >= 0) {
					List<LineCoverage> newTempLineCoverage = new ArrayList<>(context.tempLineCoverage);
					newTempLineCoverage.add(new LineCoverage(lineNumber, executionCount));
					return new FileContext(context.currentFile, context.totalLines, context.coveredLines,
							context.totalBranches, context.coveredBranches, context.totalFunctions,
							context.coveredFunctions, context.currentLineCoverage,
							context.currentNonFunctionLineCoverage, newTempLineCoverage, context.currentBranchCoverage,
							context.currentFunctionCoverage, context.annotationFunctionCoverage,
							context.tempFunctionNames, context.fnExecutionLines,
							Math.max(context.maxLineNumber, lineNumber));
				}
			}
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			LOGGER.log(
					new Status(IStatus.WARNING, PLUGIN_ID, String.format("Invalid line coverage format: %s", line), e));
		}
		return context;
	}

	private static FileContext parseBranchCoverage(FileContext context, String line) {
		try {
			String[] parts = line.substring(5).split(",");
			if (parts.length == 4) {
				int lineNumber = Integer.parseInt(parts[0]);

				if (parts[3].equals("-")) {
					LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
							String.format("Skipping branch coverage with invalid hits value: %s", line)));
					return context;
				}
				int hits = Integer.parseInt(parts[3]);
				List<BranchCoverage> newBranchCoverage = new ArrayList<>(context.currentBranchCoverage);
				newBranchCoverage.add(new BranchCoverage(lineNumber, hits > 0));
				return new FileContext(context.currentFile, context.totalLines, context.coveredLines,
						context.totalBranches, context.coveredBranches, context.totalFunctions,
						context.coveredFunctions, context.currentLineCoverage, context.currentNonFunctionLineCoverage,
						context.tempLineCoverage, newBranchCoverage, context.currentFunctionCoverage,
						context.annotationFunctionCoverage, context.tempFunctionNames, context.fnExecutionLines,
						context.maxLineNumber);
			}
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, String.format("Invalid branch coverage format: %s", line),
					e));
		}
		return context;
	}

	private static FileContext parseFunctionCoverage(FileContext context, String line) {
		try {
			String[] parts = line.substring(3).split(",");
			if (parts.length >= 2) {
				int startLine = Integer.parseInt(parts[0]);
				String mangledName = parts[1];
				String demangledName = FunctionNameProcessor.demangle(mangledName);
				Map<Integer, List<MangledDemangledPair>> newTempFunctionNames = new HashMap<>(
						context.tempFunctionNames);
				newTempFunctionNames.computeIfAbsent(startLine, k -> new ArrayList<>())
						.add(new MangledDemangledPair(mangledName, demangledName));
				return new FileContext(context.currentFile, context.totalLines, context.coveredLines,
						context.totalBranches, context.coveredBranches, context.totalFunctions,
						context.coveredFunctions, context.currentLineCoverage, context.currentNonFunctionLineCoverage,
						context.tempLineCoverage, context.currentBranchCoverage, context.currentFunctionCoverage,
						context.annotationFunctionCoverage, newTempFunctionNames, context.fnExecutionLines,
						context.maxLineNumber);
			}
		} catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
			LOGGER.log(
					new Status(IStatus.WARNING, PLUGIN_ID, String.format("Invalid function name format: %s", line), e));
		}
		return context;
	}

	private static FileContext parseFunctionExecution(FileContext context, String line) {
		List<String> newFnExecutionLines = new ArrayList<>(context.fnExecutionLines);
		newFnExecutionLines.add(line);
		return new FileContext(context.currentFile, context.totalLines, context.coveredLines, context.totalBranches,
				context.coveredBranches, context.totalFunctions, context.coveredFunctions, context.currentLineCoverage,
				context.currentNonFunctionLineCoverage, context.tempLineCoverage, context.currentBranchCoverage,
				context.currentFunctionCoverage, context.annotationFunctionCoverage, context.tempFunctionNames,
				newFnExecutionLines, context.maxLineNumber);
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
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "Invalid counter format: " + line, e));
		}

		return counters;
	}

	private static Map<String, Object[]> calculateCoverageForFile(FileContext context) {
		Map<String, Object[]> result = new HashMap<>();
		double lineCoverage = context.totalLines > 0 ? (100.0 * context.coveredLines / context.totalLines) : 0.0;
		result.put("Lines", new Object[] { context.currentFile, String.format(PERCENTAGE_FORMAT, lineCoverage),
				context.coveredLines, context.totalLines - context.coveredLines, context.totalLines });

		double branchCoverage = context.totalBranches > 0 ? (100.0 * context.coveredBranches / context.totalBranches)
				: 0.0;
		result.put("Branches", new Object[] { context.currentFile, String.format(PERCENTAGE_FORMAT, branchCoverage),
				context.coveredBranches, context.totalBranches - context.coveredBranches, context.totalBranches });

		double functionCoverage = context.totalFunctions > 0
				? (100.0 * context.coveredFunctions / context.totalFunctions)
				: 0.0;
		result.put("Functions", new Object[] { context.currentFile, String.format(PERCENTAGE_FORMAT, functionCoverage),
				context.coveredFunctions, context.totalFunctions - context.coveredFunctions, context.totalFunctions });

		return result;
	}
}