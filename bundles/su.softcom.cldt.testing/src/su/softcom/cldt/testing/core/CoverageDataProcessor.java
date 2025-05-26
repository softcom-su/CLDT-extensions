package su.softcom.cldt.testing.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import su.softcom.cldt.testing.ui.Messages;
import su.softcom.cldt.testing.ui.CoverageDataManager;
import su.softcom.cldt.testing.ui.CoverageNode;
import su.softcom.cldt.testing.utils.CoverageUtils;

public class CoverageDataProcessor {
	private static final ILog LOGGER = Platform.getLog(CoverageDataProcessor.class);
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final String LINE_COUNTERS = Messages.CoverageResultView_1;
	private static final String BRANCH_COUNTERS = Messages.CoverageResultView_2;
	private static final String FUNCTION_COUNTERS = Messages.CoverageResultView_3;
	private static final String WARNING_NO_COUNTER_DATA = "No counter data for file: %s, counter: %s";
	private static final String WARNING_DEMANGLE_FAILED = "Failed to demangle name: %s, error=%s";

	private String selectedCounter;

	public CoverageDataProcessor(String selectedCounter) {
		this.selectedCounter = selectedCounter;
	}

	public void setSelectedCounter(String selectedCounter) {
		this.selectedCounter = selectedCounter;
	}

	public String getSelectedCounter() {
		return selectedCounter;
	}

	private String demangle(String mangledName) {
		if (mangledName == null || mangledName.isEmpty()) {
			return mangledName;
		}

		try {
			ProcessBuilder pb = new ProcessBuilder("c++filt", mangledName);
			pb.redirectErrorStream(true);
			Process process = pb.start();
			StringBuilder output = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line);
				}
			}
			int exitCode = process.waitFor();
			if (exitCode == 0) {
				return output.toString().trim();
			} else {
				LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
						String.format(WARNING_DEMANGLE_FAILED, mangledName, "Non-zero exit code: " + exitCode)));
				return mangledName;
			}
		} catch (Exception e) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					String.format(WARNING_DEMANGLE_FAILED, mangledName, e.getMessage()), e));
			return mangledName;
		}
	}

	public List<CoverageNode> buildCoverageTree(ReportParser.CoverageResult coverageResults,
			List<String> analysisScope) {
		if (analysisScope == null || analysisScope.isEmpty()) {
			return new ArrayList<>();
		}

		List<CoverageNode> rootNodes = new ArrayList<>();
		Map<String, CoverageNode> folderNodes = new HashMap<>();

		processFiles(coverageResults, analysisScope, rootNodes, folderNodes);
		aggregateFolderData(folderNodes.values());

		return rootNodes;
	}

	private void processFiles(ReportParser.CoverageResult coverageResults, List<String> analysisScope,
			List<CoverageNode> rootNodes, Map<String, CoverageNode> folderNodes) {
		CoverageDataManager dataManager = CoverageDataManager.getInstance();
		for (Map.Entry<String, Map<String, Object[]>> entry : coverageResults.fileCoverage.entrySet()) {
			String filePath = entry.getKey();
			String filteredPath = CoverageUtils.removeFirstSegment(filePath, 4);
			if (!analysisScope.contains(filteredPath)) {
				continue;
			}

			Map<String, Object[]> counters = entry.getValue();
			Object[] counterData = getCounterData(counters, filePath);
			if (counterData == null) {
				continue;
			}

			List<ReportParser.FunctionCoverage> functions = dataManager.getFunctionCoverage().get(filePath);
			List<ReportParser.LineCoverage> lines = dataManager.getCoverageData().get(filePath);
			List<ReportParser.BranchCoverage> branches = dataManager.getBranchCoverage().get(filePath);

			createFileNode(filteredPath, counterData, rootNodes, folderNodes, functions, lines, branches);
		}
	}

	private Object[] getCounterData(Map<String, Object[]> counters, String filePath) {
		Object[] counterData;
		if (selectedCounter.equals(LINE_COUNTERS)) {
			counterData = counters.get(LINE_COUNTERS);
		} else if (selectedCounter.equals(BRANCH_COUNTERS)) {
			counterData = counters.get(BRANCH_COUNTERS);
		} else if (selectedCounter.equals(FUNCTION_COUNTERS)) {
			counterData = counters.get(FUNCTION_COUNTERS);
		} else {
			counterData = counters.get(selectedCounter);
		}
		if (counterData == null) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					String.format(WARNING_NO_COUNTER_DATA, filePath, selectedCounter)));
			return null;
		}
		return counterData;
	}

	private CoverageNode createFileNode(String filteredPath, Object[] counterData, List<CoverageNode> rootNodes,
			Map<String, CoverageNode> folderNodes, List<ReportParser.FunctionCoverage> functions,
			List<ReportParser.LineCoverage> lines, List<ReportParser.BranchCoverage> branches) {
		String[] pathSegments = filteredPath.split("/");
		CoverageNode parent = null;
		StringBuilder currentPath = new StringBuilder();

		for (int i = 0; i < pathSegments.length - 1; i++) {
			String segment = pathSegments[i];
			currentPath.append(segment).append("/");
			String folderPath = currentPath.toString();
			CoverageNode folderNode = folderNodes.computeIfAbsent(folderPath,
					k -> new CoverageNode(segment, CoverageNode.NodeType.FOLDER));
			if (parent == null && !rootNodes.contains(folderNode)) {
				rootNodes.add(folderNode);
			} else if (parent != null && !parent.getChildren().contains(folderNode)) {
				parent.addChild(folderNode);
			}
			parent = folderNode;
		}

		String fileName = pathSegments[pathSegments.length - 1];
		CoverageNode fileNode = new CoverageNode(fileName, CoverageNode.NodeType.FILE);
		fileNode.setCoverageData(counterData);

		if (functions != null) {
			for (ReportParser.FunctionCoverage function : functions) {
				String simpleFunctionName = function.name;
				String mangledName = function.mangledNames != null && !function.mangledNames.isEmpty()
						? function.mangledNames.get(0)
						: simpleFunctionName;
				String displayName = demangle(mangledName);
				displayName = DemangledNameUtils.extractCleanFunctionName(displayName);
				Object[] functionData = calculateFunctionData(function, lines, branches, displayName);
				CoverageNode functionNode = new CoverageNode(displayName, CoverageNode.NodeType.FUNCTION);
				functionNode.setCoverageData(functionData);
				fileNode.addChild(functionNode);
			}
		}

		if (parent != null) {
			parent.addChild(fileNode);
		} else {
			rootNodes.add(fileNode);
		}
		return fileNode;
	}

	private Object[] calculateFunctionData(ReportParser.FunctionCoverage function,
			List<ReportParser.LineCoverage> functionLines, List<ReportParser.BranchCoverage> branches,
			String simpleFunctionName) {
		if (selectedCounter.equals(LINE_COUNTERS)) {
			int functionCoveredLines = 0;
			int functionTotalLines = 0;

			for (ReportParser.LineCoverage line : functionLines) {
				if (line.lineNumber >= function.startLine && line.lineNumber <= function.endLine) {
					functionTotalLines++;
					if (line.executionCount > 0) {
						functionCoveredLines++;
					}
				}
			}

			if (functionTotalLines == 0 && function.executionCount > 0) {
				functionTotalLines = 1;
				functionCoveredLines = 1;
			}

			double percentage = functionTotalLines > 0 ? (100.0 * functionCoveredLines / functionTotalLines) : 0.0;
			return new Object[] { simpleFunctionName, String.format("%.2f%%", percentage), functionCoveredLines,
					functionTotalLines - functionCoveredLines, functionTotalLines };
		} else if (selectedCounter.equals(BRANCH_COUNTERS)) {
			int functionTotalBranches = 0;
			int coveredBranches = 0;

			for (ReportParser.BranchCoverage branch : branches) {
				if (branch.lineNumber >= function.startLine && branch.lineNumber <= function.endLine) {
					functionTotalBranches++;
					if (branch.covered) {
						coveredBranches++;
					}
				}
			}

			double percentage = functionTotalBranches > 0 ? (100.0 * coveredBranches / functionTotalBranches) : 0.0;
			return new Object[] { simpleFunctionName, String.format("%.2f%%", percentage), coveredBranches,
					functionTotalBranches - coveredBranches, functionTotalBranches };
		} else if (selectedCounter.equals(FUNCTION_COUNTERS)) {
			int functionTotalFunctions = 1;
			int functionCoveredFunctions = function.executionCount > 0 ? 1 : 0;
			double percentage = functionTotalFunctions > 0 ? (100.0 * functionCoveredFunctions / functionTotalFunctions)
					: 0.0;
			return new Object[] { simpleFunctionName, String.format("%.2f%%", percentage), functionCoveredFunctions,
					functionTotalFunctions - functionCoveredFunctions, functionTotalFunctions };
		}

		return new Object[] { simpleFunctionName, "0.00%", 0, 0, 0 };
	}

	private void aggregateFolderData(Iterable<CoverageNode> folderNodes) {
		for (CoverageNode folderNode : folderNodes) {
			int covered = 0;
			int total = 0;
			for (CoverageNode child : folderNode.getChildren()) {
				if (child.getType() == CoverageNode.NodeType.FOLDER) {
					aggregateFolderData(List.of(child));
				}
				if (child.getType() == CoverageNode.NodeType.FOLDER || child.getType() == CoverageNode.NodeType.FILE) {
					Object[] childData = child.getCoverageData();
					if (childData != null && childData.length >= 5) {
						covered += ((Number) childData[2]).intValue();
						total += ((Number) childData[4]).intValue();
					}
				}
			}
			if (total > 0) {
				double percentage = 100.0 * covered / total;
				folderNode.setCoverageData(new Object[] { folderNode.getName(), String.format("%.2f%%", percentage),
						covered, total - covered, total });
			}
		}
	}
}