package su.softcom.cldt.testing.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import su.softcom.cldt.testing.ui.CoverageNode;
import su.softcom.cldt.testing.ui.Messages;
import su.softcom.cldt.testing.utils.CoverageUtils;

public class CoverageDataProcessor {
	private static final ILog LOGGER = Platform.getLog(CoverageDataProcessor.class);
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final String LINE_COUNTERS = Messages.CoverageResultView_1;
	private static final String BRANCH_COUNTERS = Messages.CoverageResultView_2;
	private static final String FUNCTION_COUNTERS = Messages.CoverageResultView_3;
	private static final String GLOBAL_CLASS = "Global";
	private static final String MAIN_METHOD = "main";
	private static final String WARNING_NO_COUNTER_DATA = "No counter data for file: %s, counter: %s";
	private static final String WARNING_DEMANGLE_FAILED = "Failed to demangle name: %s";

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
		for (Map.Entry<String, Map<String, Object[]>> entry : coverageResults.fileCoverage.entrySet()) {
			String filePath = entry.getKey();
			String filteredPath = CoverageUtils.removeFirstSegment(filePath, 4);
			if (!analysisScope.contains(filteredPath)) {
				continue;
			}

			Map<String, Object[]> counters = entry.getValue();
			Object[] counterData = getCounterData(counters, filePath, coverageResults);
			if (counterData == null) {
				continue;
			}

			CoverageNode fileNode = createFileNode(filteredPath, counterData, rootNodes, folderNodes);
			processMethods(fileNode, filePath, coverageResults);
		}
	}

	private Object[] getCounterData(Map<String, Object[]> counters, String filePath,
			ReportParser.CoverageResult coverageResults) {
		Object[] counterData = counters.get(selectedCounter);
		if (counterData == null) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					String.format(WARNING_NO_COUNTER_DATA, filePath, selectedCounter)));
			return null;
		}

		if (FUNCTION_COUNTERS.equals(selectedCounter)) {
			List<ReportParser.MethodCoverage> methods = coverageResults.methodCoverage.getOrDefault(filePath,
					new ArrayList<>());
			int totalFunctions = methods.size();
			int coveredFunctions = (int) methods.stream().filter(m -> m.executionCount > 0).count();
			double percentage = totalFunctions > 0 ? (100.0 * coveredFunctions / totalFunctions) : 0.0;
			return new Object[] { filePath, String.format("%.2f%%", percentage), coveredFunctions,
					totalFunctions - coveredFunctions, totalFunctions };
		}

		if (BRANCH_COUNTERS.equals(selectedCounter)) {
			List<ReportParser.BranchCoverage> branches = coverageResults.branchCoverage.getOrDefault(filePath,
					new ArrayList<>());
			int totalBranches = branches.size();
			int coveredBranches = (int) branches.stream().filter(b -> b.covered).count();
			double percentage = totalBranches > 0 ? (100.0 * coveredBranches / totalBranches) : 0.0;
			return new Object[] { filePath, String.format("%.2f%%", percentage), coveredBranches,
					totalBranches - coveredBranches, totalBranches };
		}

		return counterData;
	}

	private CoverageNode createFileNode(String filteredPath, Object[] counterData, List<CoverageNode> rootNodes,
			Map<String, CoverageNode> folderNodes) {
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
		if (parent != null) {
			parent.addChild(fileNode);
		} else {
			rootNodes.add(fileNode);
		}
		return fileNode;
	}

	private void processMethods(CoverageNode fileNode, String filePath, ReportParser.CoverageResult coverageResults) {
		List<ReportParser.MethodCoverage> methods = coverageResults.methodCoverage.getOrDefault(filePath,
				new ArrayList<>());
		List<ReportParser.LineCoverage> methodLines = coverageResults.lineCoverage.getOrDefault(filePath,
				new ArrayList<>());
		List<ReportParser.LineCoverage> nonMethodLines = coverageResults.nonMethodLineCoverage.getOrDefault(filePath,
				new ArrayList<>());
		List<ReportParser.BranchCoverage> branches = coverageResults.branchCoverage.getOrDefault(filePath,
				new ArrayList<>());
		Map<String, CoverageNode> classNodes = new HashMap<>();

		for (ReportParser.MethodCoverage method : methods) {
			String demangledName = demangleCppName(method.name);
			String className = extractClassName(demangledName);
			String simpleMethodName = extractMethodName(demangledName);
			Object[] methodData = calculateMethodData(method, methodLines, branches, simpleMethodName);

			if (GLOBAL_CLASS.equals(className) || MAIN_METHOD.equals(method.name)
					|| MAIN_METHOD.equals(simpleMethodName)) {
				CoverageNode methodNode = new CoverageNode(simpleMethodName, CoverageNode.NodeType.METHOD);
				methodNode.setCoverageData(methodData);
				fileNode.addChild(methodNode);
			} else {
				CoverageNode classNode = classNodes.computeIfAbsent(className,
						k -> new CoverageNode(className, CoverageNode.NodeType.CLASS));
				if (!fileNode.getChildren().contains(classNode)) {
					fileNode.addChild(classNode);
				}
				CoverageNode methodNode = new CoverageNode(simpleMethodName, CoverageNode.NodeType.METHOD);
				methodNode.setCoverageData(methodData);
				classNode.addChild(methodNode);
			}
		}

		aggregateClassData(classNodes, nonMethodLines, branches, methods);
	}

	private Object[] calculateMethodData(ReportParser.MethodCoverage method,
			List<ReportParser.LineCoverage> methodLines, List<ReportParser.BranchCoverage> branches,
			String simpleMethodName) {
		if (LINE_COUNTERS.equals(selectedCounter)) {
			int methodCoveredLines = 0;
			int methodTotalLines = 0;
			for (ReportParser.LineCoverage line : methodLines) {
				if (line.lineNumber >= method.startLine && line.lineNumber <= method.endLine) {
					methodTotalLines++;
					if (line.executionCount > 0) {
						methodCoveredLines++;
					}
				}
			}
			if (methodTotalLines == 0 && method.executionCount > 0) {
				methodTotalLines = 1;
				methodCoveredLines = 1;
			}
			double percentage = methodTotalLines > 0 ? (100.0 * methodCoveredLines / methodTotalLines) : 0.0;
			return new Object[] { simpleMethodName, String.format("%.2f%%", percentage), methodCoveredLines,
					methodTotalLines - methodCoveredLines, methodTotalLines };
		} else if (FUNCTION_COUNTERS.equals(selectedCounter)) {
			int methodTotalFunctions = 1;
			int methodCoveredFunctions = method.executionCount > 0 ? 1 : 0;
			double percentage = methodTotalFunctions > 0 ? (100.0 * methodCoveredFunctions / methodTotalFunctions)
					: 0.0;
			return new Object[] { simpleMethodName, String.format("%.2f%%", percentage), methodCoveredFunctions,
					methodTotalFunctions - methodCoveredFunctions, methodTotalFunctions };
		} else if (BRANCH_COUNTERS.equals(selectedCounter)) {
			int methodTotalBranches = 0;
			int coveredBranches = 0;
			for (ReportParser.BranchCoverage branch : branches) {
				if (branch.lineNumber >= method.startLine && branch.lineNumber <= method.endLine) {
					methodTotalBranches++;
					if (branch.covered) {
						coveredBranches++;
					}
				}
			}
			double percentage = methodTotalBranches > 0 ? (100.0 * coveredBranches / methodTotalBranches) : 0.0;
			return new Object[] { simpleMethodName, String.format("%.2f%%", percentage), coveredBranches,
					methodTotalBranches - coveredBranches, methodTotalBranches };
		}
		return new Object[] { simpleMethodName, "0.00%", 0, 0, 0 };
	}

	private void aggregateClassData(Map<String, CoverageNode> classNodes,
			List<ReportParser.LineCoverage> nonMethodLines, List<ReportParser.BranchCoverage> branches,
			List<ReportParser.MethodCoverage> methods) {
		for (CoverageNode classNode : classNodes.values()) {
			if (LINE_COUNTERS.equals(selectedCounter)) {
				int classCoveredLines = calculateClassCoveredLines(classNode, nonMethodLines);
				int classTotalLines = calculateClassTotalLines(classNode, nonMethodLines);
				setClassCoverageData(classNode, classCoveredLines, classTotalLines);
			} else if (BRANCH_COUNTERS.equals(selectedCounter)) {
				int classCoveredBranches = calculateClassCoveredBranches(classNode, branches, methods);
				int classTotalBranches = calculateClassTotalBranches(classNode, branches, methods);
				setClassCoverageData(classNode, classCoveredBranches, classTotalBranches);
			} else if (FUNCTION_COUNTERS.equals(selectedCounter)) {
				int classCoveredFunctions = calculateClassCoveredFunctions(classNode);
				int classTotalFunctions = calculateClassTotalFunctions(classNode);
				setClassCoverageData(classNode, classCoveredFunctions, classTotalFunctions);
			}
		}
	}

	private int calculateClassCoveredLines(CoverageNode classNode, List<ReportParser.LineCoverage> nonMethodLines) {
		int covered = 0;
		for (CoverageNode methodNode : classNode.getChildren()) {
			Object[] methodData = methodNode.getCoverageData();
			if (methodData != null && methodData.length >= 5) {
				covered += ((Number) methodData[2]).intValue();
			}
		}
		for (ReportParser.LineCoverage line : nonMethodLines) {
			if (line.executionCount > 0) {
				covered++;
			}
		}
		return covered;
	}

	private int calculateClassTotalLines(CoverageNode classNode, List<ReportParser.LineCoverage> nonMethodLines) {
		int total = nonMethodLines.size();
		for (CoverageNode methodNode : classNode.getChildren()) {
			Object[] methodData = methodNode.getCoverageData();
			if (methodData != null && methodData.length >= 5) {
				total += ((Number) methodData[4]).intValue();
			}
		}
		return total;
	}

	private int calculateClassCoveredBranches(CoverageNode classNode, List<ReportParser.BranchCoverage> branches,
			List<ReportParser.MethodCoverage> methods) {
		int covered = 0;
		for (CoverageNode methodNode : classNode.getChildren()) {
			Object[] methodData = methodNode.getCoverageData();
			if (methodData != null && methodData.length >= 5) {
				covered += ((Number) methodData[2]).intValue();
			}
		}
		for (ReportParser.BranchCoverage branch : branches) {
			if (!isBranchInMethod(branch, methods)) {
				if (branch.covered) {
					covered++;
				}
			}
		}
		return covered;
	}

	private int calculateClassTotalBranches(CoverageNode classNode, List<ReportParser.BranchCoverage> branches,
			List<ReportParser.MethodCoverage> methods) {
		int total = 0;
		for (CoverageNode methodNode : classNode.getChildren()) {
			Object[] methodData = methodNode.getCoverageData();
			if (methodData != null && methodData.length >= 5) {
				total += ((Number) methodData[4]).intValue();
			}
		}
		for (ReportParser.BranchCoverage branch : branches) {
			if (!isBranchInMethod(branch, methods)) {
				total++;
			}
		}
		return total;
	}

	private int calculateClassCoveredFunctions(CoverageNode classNode) {
		int covered = 0;
		for (CoverageNode methodNode : classNode.getChildren()) {
			Object[] methodData = methodNode.getCoverageData();
			if (methodData != null && methodData.length >= 5) {
				covered += ((Number) methodData[2]).intValue();
			}
		}
		return covered;
	}

	private int calculateClassTotalFunctions(CoverageNode classNode) {
		int total = 0;
		for (CoverageNode methodNode : classNode.getChildren()) {
			Object[] methodData = methodNode.getCoverageData();
			if (methodData != null && methodData.length >= 5) {
				total += ((Number) methodData[4]).intValue();
			}
		}
		return total;
	}

	private boolean isBranchInMethod(ReportParser.BranchCoverage branch, List<ReportParser.MethodCoverage> methods) {
		for (ReportParser.MethodCoverage method : methods) {
			if (branch.lineNumber >= method.startLine && branch.lineNumber <= method.endLine) {
				return true;
			}
		}
		return false;
	}

	private void setClassCoverageData(CoverageNode classNode, int covered, int total) {
		double percentage = total > 0 ? (100.0 * covered / total) : 0.0;
		classNode.setCoverageData(new Object[] { classNode.getName(), String.format("%.2f%%", percentage), covered,
				total - covered, total });
	}

	private void aggregateFolderData(Iterable<CoverageNode> folderNodes) {
		for (CoverageNode folderNode : folderNodes) {
			int covered = 0;
			int total = 0;
			for (CoverageNode child : folderNode.getChildren()) {
				aggregateFolderData(List.of(child));
				Object[] childData = child.getCoverageData();
				if (childData != null && childData.length >= 5) {
					covered += ((Number) childData[2]).intValue();
					total += ((Number) childData[4]).intValue();
				}
			}
			if (total > 0) {
				double percentage = 100.0 * covered / total;
				folderNode.setCoverageData(new Object[] { folderNode.getName(), String.format("%.2f%%", percentage),
						covered, total - covered, total });
			}
		}
	}

	private String demangleCppName(String mangledName) {
		if (!mangledName.startsWith("_Z")) {
			return mangledName;
		}

		try {
			StringBuilder demangled = new StringBuilder();
			int pos = mangledName.startsWith("_ZNK") ? 4 : mangledName.startsWith("_ZN") ? 3 : 2;
			if (mangledName.startsWith("_ZNK")) {
				demangled.append("const ");
			}

			StringBuilder lengthStr = new StringBuilder();
			while (pos < mangledName.length() && Character.isDigit(mangledName.charAt(pos))) {
				lengthStr.append(mangledName.charAt(pos));
				pos++;
			}

			if (lengthStr.length() == 0) {
				return mangledName;
			}

			int classLength = Integer.parseInt(lengthStr.toString());
			if (pos + classLength > mangledName.length()) {
				return mangledName;
			}

			String className = mangledName.substring(pos, pos + classLength);
			pos += classLength;

			lengthStr = new StringBuilder();
			while (pos < mangledName.length() && Character.isDigit(mangledName.charAt(pos))) {
				lengthStr.append(mangledName.charAt(pos));
				pos++;
			}

			if (lengthStr.length() == 0) {
				return className + "::" + mangledName;
			}

			int methodLength = Integer.parseInt(lengthStr.toString());
			if (pos + methodLength > mangledName.length()) {
				return className + "::" + mangledName;
			}

			String methodName = mangledName.substring(pos, pos + methodLength);
			return className + "::" + methodName;
		} catch (Exception e) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, String.format(WARNING_DEMANGLE_FAILED, mangledName), e));
			return mangledName;
		}
	}

	private String extractClassName(String methodName) {
		int separatorIndex = methodName.indexOf("::");
		if (separatorIndex != -1) {
			String classPart = methodName.substring(0, separatorIndex);
			return classPart.startsWith("const ") ? classPart.substring(6) : classPart;
		}
		return GLOBAL_CLASS;
	}

	private String extractMethodName(String methodName) {
		int separatorIndex = methodName.indexOf("::");
		return separatorIndex != -1 && separatorIndex + 2 < methodName.length()
				? methodName.substring(separatorIndex + 2)
				: methodName;
	}
}