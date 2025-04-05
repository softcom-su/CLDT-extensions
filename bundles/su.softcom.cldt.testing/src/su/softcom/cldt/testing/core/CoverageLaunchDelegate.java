package su.softcom.cldt.testing.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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

import su.softcom.cldt.core.CMakeCorePlugin;
import su.softcom.cldt.core.cmake.ICMakeProject;
import su.softcom.cldt.core.cmake.Target;
import su.softcom.cldt.testing.ui.CoverageResultView;
import su.softcom.cldt.testing.utils.CoverageUtils;

public class CoverageLaunchDelegate implements ILaunchConfigurationDelegate2, CoverageResultView.CoverageDataProvider {
	private static final ILog LOGGER = Platform.getLog(CoverageLaunchDelegate.class);
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final String CMAKE_CONFIGURE_BUILDER_ID = "su.softcom.cldt.core.builder.configure";

	private Map<String, Map<String, Object[]>> coverageData;

	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		String projectName = configuration.getAttribute("projectName", (String) null);
		if (projectName == null) {
			throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, "Failed to get project name"));
		}

		String targetName = configuration.getAttribute("targetName", (String) null);
		if (targetName == null) {
			throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, "Failed to get target name"));
		}

		List<String> analysisScope = configuration.getAttribute("analysisScope", List.of());

		ICMakeProject cmakeProject = CMakeCorePlugin.getDefault().getProject(projectName);
		IFolder buildFolder = cmakeProject.getBuildFolder();
		String buildFolderPath = buildFolder.getLocation().toOSString();

		Target target = cmakeProject.getTarget(targetName);
		String executablePath = getExecutablePath(target, buildFolderPath);
		if (executablePath == null) {
			throw new CoreException(
					new Status(IStatus.ERROR, PLUGIN_ID, "Executable not found for target: " + targetName));
		}

		CommandExecutor commandExecutor = new CommandExecutor();

		try {
			runTests(commandExecutor, buildFolderPath, executablePath);
			generateReport(commandExecutor, buildFolderPath, executablePath, analysisScope);
			updateUI(coverageData, analysisScope);
			refreshBuildFolder(buildFolder);
		} catch (IOException e) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID, "Launch failed", e));
			throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, "Launch failed", e));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID, "Launch interrupted", e));
			throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, "Launch interrupted", e));
		}
	}

	private void updateUI(Map<String, Map<String, Object[]>> coverageData, List<String> analysisScope) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			try {
				CoverageResultView view = (CoverageResultView) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
						.getActivePage().showView(CoverageResultView.ID);
				view.setDataProvider(this);
				view.setAnalysisScope(analysisScope);
				view.updateCoverageResults(coverageData, analysisScope);
			} catch (Exception e) {
				LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID, "Failed to update UI", e));
			}
		});
	}

	private String getExecutablePath(Target target, String buildFolderPath) {
		for (IFile ifile : target.getArtifacts()) {
			File file = ifile.getRawLocation().makeAbsolute().toFile();
			if (file.canExecute()) {
				return file.getAbsolutePath();
			}
		}

		File buildDir = new File(buildFolderPath);
		File[] executables = buildDir
				.listFiles(file -> file.isFile() && file.canExecute() && !file.getName().endsWith(".profraw"));
		return (executables != null && executables.length > 0) ? executables[0].getAbsolutePath() : null;
	}

	private void runTests(CommandExecutor commandExecutor, String buildDirPath, String executablePath)
			throws IOException, InterruptedException {
		String rawProfilePath = buildDirPath + "/coverage.profraw";

		File oldProfraw = new File(rawProfilePath);
		if (oldProfraw.exists()) {
			try {
				Files.delete(oldProfraw.toPath());
			} catch (IOException e) {
				LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "Failed to delete old profile data", e));
			}
		}

		List<String> testCommand = Arrays.asList(executablePath);
		commandExecutor.executeCommand(testCommand, Map.of("LLVM_PROFILE_FILE", rawProfilePath), null,
				new StringBuilder());

		File profrawFile = new File(rawProfilePath);
		if (!profrawFile.exists()) {
			throw new IOException("Profile raw data file was not generated: " + rawProfilePath);
		}
	}

	private void generateReport(CommandExecutor commandExecutor, String buildDirPath, String executablePath,
			List<String> analysisScope) throws IOException, InterruptedException {
		String rawProfilePath = buildDirPath + "/coverage.profraw";
		String profileDataPath = buildDirPath + "/coverage.profdata";
		String reportPath = buildDirPath + "/coverage_report.lcov";

		List<String> profdataCommand = Arrays.asList("llvm-profdata", "merge", "-sparse", rawProfilePath, "-o",
				profileDataPath);
		commandExecutor.executeCommand(profdataCommand, null, null, new StringBuilder());

		List<String> covCommand = Arrays.asList("llvm-cov", "export", executablePath,
				"-instr-profile=" + profileDataPath, "--format=lcov");
		commandExecutor.executeCommand(covCommand, null, reportPath, new StringBuilder());

		List<String> reportLines = Files.readAllLines(Paths.get(reportPath));
		coverageData = ReportParser.parseLcovReport(reportLines);
		coverageData.entrySet()
				.removeIf(entry -> !analysisScope.contains(CoverageUtils.removeFirstSegment(entry.getKey(), 4)));
	}

	private void refreshBuildFolder(IFolder buildFolder) throws CoreException {
		buildFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
	}

	@Override
	public Map<String, Map<String, Object[]>> getCoverageData() {
		return coverageData;
	}

	@Override
	public boolean buildForLaunch(ILaunchConfiguration configuration, String mode, IProgressMonitor monitor)
			throws CoreException {
		String projectName = configuration.getAttribute("projectName", (String) null);
		if (projectName == null) {
			throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, "Failed to get project name"));
		}
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		ICMakeProject cmakeProject = CMakeCorePlugin.getDefault().getProject(projectName);

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
}