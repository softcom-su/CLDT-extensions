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
	private static final ILog LOGGER = Platform.getLog(CoverageDataManager.class);
	private static final CoverageDataManager INSTANCE = new CoverageDataManager();
	private Map<String, List<ReportParser.LineCoverage>> lineCoverage;
	private Map<String, List<ReportParser.BranchCoverage>> branchCoverage;
	private Map<String, List<ReportParser.FunctionCoverage>> functionCoverage;
	private Map<String, List<ReportParser.FunctionCoverage>> annotationFunctionCoverage;
	private List<String> analysisScope;

	private CoverageDataManager() {
		clear();
	}

	public static CoverageDataManager getInstance() {
		return INSTANCE;
	}

	public void setCoverageData(ReportParser.CoverageResult coverageResult, List<String> analysisScope) {
		lineCoverage = coverageResult != null ? coverageResult.lineCoverage() : new HashMap<>();
		branchCoverage = coverageResult != null ? coverageResult.branchCoverage() : new HashMap<>();
		functionCoverage = coverageResult != null ? coverageResult.functionCoverage() : new HashMap<>();
		annotationFunctionCoverage = coverageResult != null ? coverageResult.annotationFunctionCoverage()
				: new HashMap<>();
		this.analysisScope = analysisScope != null ? List.copyOf(analysisScope) : List.of();
		if (coverageResult == null || coverageResult.lineCoverage().isEmpty()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "Coverage data is empty or null"));
		}
	}

	public void clear() {
		lineCoverage = new HashMap<>();
		branchCoverage = new HashMap<>();
		functionCoverage = new HashMap<>();
		annotationFunctionCoverage = new HashMap<>();
		analysisScope = List.of();
	}

	public Map<String, List<ReportParser.LineCoverage>> getCoverageData() {
		return lineCoverage;
	}

	public Map<String, List<ReportParser.BranchCoverage>> getBranchCoverage() {
		return branchCoverage;
	}

	public Map<String, List<ReportParser.FunctionCoverage>> getFunctionCoverage() {
		return functionCoverage;
	}

	public Map<String, List<ReportParser.FunctionCoverage>> getAnnotationFunctionCoverage() {
		return annotationFunctionCoverage;
	}

	public List<String> getAnalysisScope() {
		return analysisScope;
	}
}