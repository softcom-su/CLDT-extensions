package su.softcom.cldt.testing.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import su.softcom.cldt.testing.core.ReportParser;

public class CoverageDataManager {
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final String WARNING_EMPTY_DATA = "Coverage data is empty or null";
	private static final CoverageDataManager INSTANCE = new CoverageDataManager();
	private static final ILog LOGGER = Platform.getLog(CoverageDataManager.class);
	private Map<String, List<ReportParser.LineCoverage>> lineCoverage;
	private Map<String, List<ReportParser.BranchCoverage>> branchCoverage;
	private Map<String, List<ReportParser.MethodCoverage>> methodCoverage;
	private List<String> analysisScope;

	private CoverageDataManager() {
		clear();
	}

	public static CoverageDataManager getInstance() {
		return INSTANCE;
	}

	public void setCoverageData(ReportParser.CoverageResult coverageResult, List<String> analysisScope) {
		lineCoverage = coverageResult != null ? coverageResult.lineCoverage : new HashMap<>();
		branchCoverage = coverageResult != null ? coverageResult.branchCoverage : new HashMap<>();
		methodCoverage = coverageResult != null ? coverageResult.methodCoverage : new HashMap<>();
		this.analysisScope = analysisScope != null ? List.copyOf(analysisScope) : List.of();
		if (coverageResult == null || coverageResult.lineCoverage.isEmpty()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, WARNING_EMPTY_DATA));
		}
	}

	public void clear() {
		lineCoverage = new HashMap<>();
		branchCoverage = new HashMap<>();
		methodCoverage = new HashMap<>();
		analysisScope = List.of();
	}

	public Map<String, List<ReportParser.LineCoverage>> getCoverageData() {
		return lineCoverage;
	}

	public Map<String, List<ReportParser.BranchCoverage>> getBranchCoverage() {
		return branchCoverage;
	}

	public Map<String, List<ReportParser.MethodCoverage>> getMethodCoverage() {
		return methodCoverage;
	}

	public List<String> getAnalysisScope() {
		return analysisScope;
	}
}