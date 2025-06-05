package su.softcom.cldt.testing.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import su.softcom.cldt.testing.ui.Messages;
import su.softcom.cldt.testing.ui.CoverageNode;
import su.softcom.cldt.testing.utils.CoverageUtils;

public class CoverageDataProcessor {
	private static final ILog LOGGER = Platform.getLog(CoverageDataProcessor.class);
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final String LINE_COUNTERS = Messages.CoverageResultView_1;
	private static final String BRANCH_COUNTERS = Messages.CoverageResultView_2;
	private static final String WARNING_NO_COUNTER_DATA = "No counter data for file: %s, counter: %s";

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
			Object[] counterData = getCounterData(counters, filePath);
			if (counterData == null) {
				continue;
			}

			createFileNode(filteredPath, counterData, rootNodes, folderNodes);
		}
	}

	private Object[] getCounterData(Map<String, Object[]> counters, String filePath) {
		Object[] counterData;
		if (selectedCounter.equals(LINE_COUNTERS)) {
			counterData = counters.get(LINE_COUNTERS);
		} else if (selectedCounter.equals(BRANCH_COUNTERS)) {
			counterData = counters.get(BRANCH_COUNTERS);
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
}