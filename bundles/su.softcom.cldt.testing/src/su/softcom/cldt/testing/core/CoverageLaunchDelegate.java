package su.softcom.cldt.testing.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate2;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbenchPage;
import su.softcom.cldt.core.CMakeCorePlugin;
import su.softcom.cldt.core.cmake.ICMakeProject;
import su.softcom.cldt.core.cmake.Target;
import su.softcom.cldt.testing.ui.CoverageResultView;

public class CoverageLaunchDelegate implements ILaunchConfigurationDelegate2, CoverageResultView.CoverageDataProvider {
	private static final ILog LOGGER = Platform.getLog(CoverageLaunchDelegate.class);
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final String CMAKE_CONFIGURE_BUILDER_ID = "su.softcom.cldt.core.builder.configure";
	private static final String PROFILE_RAW_FILE = "coverage.profraw";
	private static final String PROFILE_DATA_FILE = "coverage.profdata";
	private static final String REPORT_FILE = "coverage_report.lcov";

	private ReportParser.CoverageResult coverageData;

	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		IProject project = getProject(configuration);
		ICMakeProject cmakeProject = getCMakeProject(project);
		String targetName = getTargetName(configuration);
		List<String> analysisScope = getAnalysisScope(configuration);
		String coverageDataDir = getCoverageDataDir(project, cmakeProject);
		String executablePath = getExecutablePath(cmakeProject, targetName, coverageDataDir);
		IFolder buildFolder = cmakeProject.getBuildFolder();
		analysisScope = CoverageSettingsManager.filterAnalysisScope(analysisScope, project);

		executeLaunch(coverageDataDir, executablePath, analysisScope, project, buildFolder);
	}

	private IProject getProject(ILaunchConfiguration configuration) throws CoreException {
		String projectName = configuration.getAttribute("projectName", (String) null);
		if (projectName == null) {
			throw newCoreException("Failed to get project name", IStatus.ERROR);
		}
		return ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
	}

	private ICMakeProject getCMakeProject(IProject project) throws CoreException {
		ICMakeProject cmakeProject = CMakeCorePlugin.getDefault().getProject(project.getName());
		if (cmakeProject == null) {
			throw newCoreException("Failed to get CMake project for: " + project.getName(), IStatus.ERROR);
		}
		return cmakeProject;
	}

	private String getTargetName(ILaunchConfiguration configuration) throws CoreException {
		String targetName = configuration.getAttribute("targetName", (String) null);
		if (targetName == null) {
			throw newCoreException("Failed to get target name", IStatus.ERROR);
		}
		return targetName;
	}

	private List<String> getAnalysisScope(ILaunchConfiguration configuration) throws CoreException {
		return configuration.getAttribute("analysisScope", List.of());
	}

	private String getCoverageDataDir(IProject project, ICMakeProject cmakeProject) throws CoreException {
		String coverageDataDir = CoveragePropertySettings.getCoverageDataDirProperty(project);
		if (coverageDataDir.isEmpty()) {
			return cmakeProject.getBuildFolder().getLocation().toOSString();
		}
		try {
			Path projectPath = Paths.get(project.getLocation().toOSString()).toAbsolutePath().normalize();
			return projectPath.resolve(coverageDataDir).normalize().toString();
		} catch (Exception e) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					"Failed to resolve coverage data directory: " + coverageDataDir, e));
			return cmakeProject.getBuildFolder().getLocation().toOSString();
		}
	}

	private String getExecutablePath(ICMakeProject cmakeProject, String targetName, String coverageDataDir)
			throws CoreException {
		Target target = cmakeProject.getTarget(targetName);
		if (target == null) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "Target not found in CMake project: " + targetName));
		}

		String executablePath = findExecutablePath(target, coverageDataDir);
		if (executablePath == null) {
			throw newCoreException("Executable not found for target: " + targetName, IStatus.ERROR);
		}
		return executablePath;
	}

	private String findExecutablePath(Target target, String coverageDataDir) {
		if (target != null) {
			for (IFile file : target.getArtifacts()) {
				File fileObj = file.getRawLocation().makeAbsolute().toFile();
				if (fileObj.canExecute()) {
					return fileObj.getAbsolutePath();
				}
			}
		}

		File buildDir = new File(coverageDataDir);
		File[] executables = buildDir
				.listFiles(file -> file.isFile() && file.canExecute() && !file.getName().endsWith(".profraw"));
		return executables != null && executables.length > 0 ? executables[0].getAbsolutePath() : null;
	}

	private void executeLaunch(String coverageDataDir, String executablePath, List<String> analysisScope,
			IProject project, IFolder buildFolder) throws CoreException {
		CommandExecutor commandExecutor = new CommandExecutor();
		try {
			if (CoveragePreferenceSettings.isCleanProfileData()) {
				CoverageSettingsManager.cleanProfileData(coverageDataDir);
			}
			runTests(commandExecutor, coverageDataDir, executablePath);
			generateReport(commandExecutor, coverageDataDir, executablePath, analysisScope);
			refreshCoverageDataDir(project, coverageDataDir);
			if (CoveragePreferenceSettings.isOpenViewAuto()) {
				openCoverageView();
			}
			updateCoverageViewData(coverageData, analysisScope, project);
			refreshBuildFolder(buildFolder);
		} catch (IOException e) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID, "Launch failed", e));
			throw newCoreException("Launch failed", IStatus.ERROR, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID, "Launch interrupted", e));
			throw newCoreException("Launch interrupted", IStatus.ERROR, e);
		}
	}

	private void runTests(CommandExecutor commandExecutor, String coverageDataDir, String executablePath)
			throws IOException, InterruptedException {
		String rawProfilePath = Paths.get(coverageDataDir, PROFILE_RAW_FILE).toString();
		List<String> testCommand = Arrays.asList(executablePath);
		StringBuilder output = new StringBuilder();
		commandExecutor.executeCommand(testCommand, Map.of("LLVM_PROFILE_FILE", rawProfilePath), null, output);

		File profrawFile = new File(rawProfilePath);
		if (!profrawFile.exists()) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID,
					"Profile raw data file was not generated: " + rawProfilePath + ", Output: " + output));
			throw new IOException("Profile raw data file was not generated: " + rawProfilePath);
		}
	}

	private void generateReport(CommandExecutor commandExecutor, String coverageDataDir, String executablePath,
			List<String> analysisScope) throws IOException, InterruptedException {
		String rawProfilePath = Paths.get(coverageDataDir, PROFILE_RAW_FILE).toString();
		String profileDataPath = Paths.get(coverageDataDir, PROFILE_DATA_FILE).toString();
		generateProfileData(commandExecutor, rawProfilePath, profileDataPath);

		if (CoveragePreferenceSettings.isGenerateReportAfterBuild() && !analysisScope.isEmpty()) {
			String reportPath = Paths.get(coverageDataDir, REPORT_FILE).toString();
			generateLcovReport(commandExecutor, executablePath, profileDataPath, reportPath, analysisScope);
		} else {
			coverageData = new ReportParser.CoverageResult(new HashMap<>(), new HashMap<>(), new HashMap<>(),
					new HashMap<>(), new HashMap<>(), new HashMap<>());
		}
	}

	private void generateProfileData(CommandExecutor commandExecutor, String rawProfilePath, String profileDataPath)
			throws IOException, InterruptedException {
		List<String> profdataCommand = Arrays.asList(CoverageSettingsManager.getLlvmProfdataCommand(), "merge",
				"-sparse", rawProfilePath, "-o", profileDataPath);
		StringBuilder output = new StringBuilder();
		try {
			commandExecutor.executeCommand(profdataCommand, null, null, output);
		} catch (IOException e) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID, "Failed to execute llvm-profdata: " + output, e));
			throw e;
		}

		File profdataFile = new File(profileDataPath);
		if (!profdataFile.exists()) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID,
					"Profile data file was not generated: " + profileDataPath + ", Output: " + output));
			throw new IOException("Profile data file was not generated: " + profileDataPath);
		}
	}

	private void generateLcovReport(CommandExecutor commandExecutor, String executablePath, String profileDataPath,
			String reportPath, List<String> analysisScope) throws IOException, InterruptedException {
		List<String> covCommand = Arrays.asList(CoverageSettingsManager.getLlvmCovCommand(), "export", executablePath,
				"-instr-profile=" + profileDataPath, "--format=lcov");
		StringBuilder output = new StringBuilder();
		try {
			commandExecutor.executeCommand(covCommand, null, reportPath, output);
		} catch (IOException e) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID,
					"Failed to execute llvm-cov: " + output + ", command: " + covCommand, e));
			throw new IOException("LCOV report generation failed", e);
		}

		File reportFile = new File(reportPath);
		if (!reportFile.exists()) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID, "LCOV report was not generated: " + reportPath));
			throw new IOException("LCOV report was not generated: " + reportPath);
		}

		List<String> reportLines = Files.readAllLines(Paths.get(reportPath));
		List<String> filteredReportLines = filterLcovReport(reportLines, analysisScope);
		coverageData = ReportParser.parseLcovReport(filteredReportLines);
		if (coverageData == null || coverageData.fileCoverage().isEmpty()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					"LCOV report is empty or invalid after filtering: " + reportPath));
			coverageData = new ReportParser.CoverageResult(new HashMap<>(), new HashMap<>(), new HashMap<>(),
					new HashMap<>(), new HashMap<>(), new HashMap<>());
		}
	}

	private List<String> filterLcovReport(List<String> reportLines, List<String> analysisScope) {
		List<String> filteredLines = new ArrayList<>();
		boolean includeRecord = false;

		for (String line : reportLines) {
			if (line.startsWith("SF:")) {
				final String filePath = line.substring(3).trim();
				includeRecord = analysisScope.stream()
						.anyMatch(scopePath -> filePath.equals(scopePath) || filePath.endsWith(scopePath));
			} else if (line.equals("end_of_record")) {
				if (includeRecord) {
					filteredLines.add(line);
				}
				includeRecord = false;
			}

			if (includeRecord) {
				filteredLines.add(line);
			}
		}

		return filteredLines;
	}

	private void refreshBuildFolder(IFolder buildFolder) throws CoreException {
		buildFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
	}

	private void refreshCoverageDataDir(IProject project, String coverageDataDir) throws CoreException {
		String absoluteCoverageDir = CoveragePropertySettings.toAbsolutePath(project, coverageDataDir);
		Path coveragePath = Paths.get(absoluteCoverageDir);
		Path projectPath = Paths.get(project.getLocation().toOSString());
		if (coveragePath.startsWith(projectPath)) {
			String relativePath = projectPath.relativize(coveragePath).toString();
			IFolder coverageFolder = project.getFolder(relativePath);
			if (coverageFolder.exists()) {
				coverageFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
			} else {
				LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
						"Coverage data directory does not exist in project: " + relativePath));
			}
		} else {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					"Coverage data directory is outside project: " + absoluteCoverageDir));
		}
	}

	private void openCoverageView() {
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			try {
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				CoverageResultView view = (CoverageResultView) page.showView(CoverageResultView.ID);
				view.setDataProvider(this);
			} catch (Exception e) {
				LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID, "Failed to open coverage view", e));
			}
		});
	}

	private void updateCoverageViewData(ReportParser.CoverageResult coverageData, List<String> analysisScope,
			IProject project) {
		List<String> updatedAnalysisScope = CoverageSettingsManager.filterAnalysisScope(analysisScope, project);

		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			try {
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				CoverageResultView view = (CoverageResultView) page.findView(CoverageResultView.ID);
				if (view == null) {
					return;
				}
				view.setDataProvider(this);
				view.setProject(project);
				view.updateCoverageResults(coverageData != null ? coverageData
						: new ReportParser.CoverageResult(new HashMap<>(), new HashMap<>(), new HashMap<>(),
								new HashMap<>(), new HashMap<>(), new HashMap<>()),
						updatedAnalysisScope);
				view.setAnalysisScope(updatedAnalysisScope);
			} catch (Exception e) {
				LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID, "Failed to update coverage view data", e));
			}
		});
	}

	@Override
	public Map<String, Map<String, Object[]>> getCoverageData() {
		return coverageData != null ? coverageData.fileCoverage() : null;
	}

	@Override
	public ReportParser.CoverageResult getFullCoverageData() {
		return coverageData != null ? new ReportParser.CoverageResult(new HashMap<>(coverageData.fileCoverage()),
				new HashMap<>(coverageData.lineCoverage()), new HashMap<>(coverageData.nonFunctionLineCoverage()),
				new HashMap<>(coverageData.branchCoverage()), new HashMap<>(coverageData.functionCoverage()),
				new HashMap<>(coverageData.annotationFunctionCoverage()))
				: new ReportParser.CoverageResult(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
						new HashMap<>(), new HashMap<>());
	}

	@Override
	public boolean buildForLaunch(ILaunchConfiguration configuration, String mode, IProgressMonitor monitor)
			throws CoreException {
		IProject project = getProject(configuration);
		ICMakeProject cmakeProject = getCMakeProject(project);

		Map<String, String> buildArgs = new HashMap<>(cmakeProject.getBuildArguments());
		buildArgs.put("CMAKE_CXX_FLAGS", "-fprofile-instr-generate -fcoverage-mapping");
		buildArgs.put("CMAKE_C_FLAGS", "-fprofile-instr-generate -fcoverage-mapping");
		cmakeProject.setBuildArguments(buildArgs);

		project.build(IncrementalProjectBuilder.FULL_BUILD, CMAKE_CONFIGURE_BUILDER_ID, null, monitor);
		project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		return false;
	}

	@Override
	public boolean preLaunchCheck(ILaunchConfiguration configuration, String mode, IProgressMonitor monitor)
			throws CoreException {
		return true;
	}

	@Override
	public boolean finalLaunchCheck(ILaunchConfiguration configuration, String mode, IProgressMonitor monitor)
			throws CoreException {
		return true;
	}

	@Override
	public ILaunch getLaunch(ILaunchConfiguration configuration, String mode) throws CoreException {
		return null;
	}

	private CoreException newCoreException(String message, int severity) {
		return new CoreException(new Status(severity, PLUGIN_ID, message));
	}

	private CoreException newCoreException(String message, int severity, Throwable cause) {
		return new CoreException(new Status(severity, PLUGIN_ID, message, cause));
	}
}