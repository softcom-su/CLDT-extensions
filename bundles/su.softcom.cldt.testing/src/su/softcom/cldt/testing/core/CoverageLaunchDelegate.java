package su.softcom.cldt.testing.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate2;
import org.eclipse.ui.PlatformUI;

import su.softcom.cldt.core.CMakeCorePlugin;
import su.softcom.cldt.core.cmake.ICMakeProject;
import su.softcom.cldt.testing.ui.CoverageResultView;
import su.softcom.cldt.testing.ui.CoverageTab;
import su.softcom.cldt.testing.utils.CoverageUtils;

public class CoverageLaunchDelegate implements ILaunchConfigurationDelegate2, CoverageResultView.CoverageDataProvider {

	private static final ILog LOGGER = Platform.getLog(CoverageLaunchDelegate.class);

	private Map<String, Map<String, Object[]>> coverageData;
	private List<String> analysisScope = CoverageTab.analysisScope;

	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		String projectName = CoverageTab.iproject;
		if (projectName == null) {
			throw new CoreException(new Status(IStatus.ERROR, "su.softcom.cldt.testing", "Failed to get project name"));
		}

		ICMakeProject cmakeProject = CMakeCorePlugin.getDefault().getProject(projectName);
		IProject project = cmakeProject.getProject();
		String projectPath = project.getLocation().toOSString();
		IFolder buildFolder = cmakeProject.getBuildFolder();
		String buildFolderPath = buildFolder.getLocation().toOSString();

		CommandExecutor commandExecutor = new CommandExecutor();

		try {
			setupProject(commandExecutor, cmakeProject, projectPath, buildFolder);
			runTests(commandExecutor, buildFolderPath);
			generateReport(commandExecutor, buildFolderPath);
			updateUI();
			refreshBuildFolder(buildFolder);
		} catch (Exception e) {
			LOGGER.log(new Status(IStatus.ERROR, "su.softcom.cldt.testing", "Launch failed", e));
			throw new CoreException(new Status(IStatus.ERROR, "su.softcom.cldt.testing", "Launch failed", e));
		}
	}

	private void setupProject(CommandExecutor commandExecutor, ICMakeProject cmakeProject, String testProjectPath,
			IFolder buildFolder) throws IOException, InterruptedException, CoreException {
		if (!buildFolder.exists()) {
			buildFolder.create(true, true, null);
			LOGGER.log(new Status(IStatus.INFO, "su.softcom.cldt.testing",
					"Created build folder: " + buildFolder.getLocation().toOSString()));
		}

		String cmakePath = cmakeProject.getCmakeInstance().getPath().toOSString();
		String buildDirPath = buildFolder.getLocation().toOSString();

		List<String> cmakeCommand = Arrays.asList(cmakePath, testProjectPath, "-B", buildDirPath,
				"-DCMAKE_CXX_FLAGS=-fprofile-instr-generate -fcoverage-mapping",
				"-DCMAKE_C_FLAGS=-fprofile-instr-generate -fcoverage-mapping");
		commandExecutor.executeCommand(cmakeCommand);

		List<String> buildCommand = Arrays.asList("cmake", "--build", buildDirPath);
		commandExecutor.executeCommand(buildCommand);

		String executablePath = findExecutable(buildDirPath);
		if (executablePath == null) {
			throw new IOException("No executable found after build in " + buildDirPath);
		}
	}

	private String findExecutable(String buildDirPath) {
		File buildDir = new File(buildDirPath);
		File[] executables = buildDir
				.listFiles(file -> file.isFile() && file.canExecute() && !file.getName().endsWith(".profraw"));

		if (executables != null && executables.length > 0) {
			return executables[0].getAbsolutePath();
		}
		return null;
	}

	private void runTests(CommandExecutor commandExecutor, String buildDirPath)
			throws IOException, InterruptedException {
		String rawProfilePath = buildDirPath + "/coverage.profraw";
		String testExecutablePath = findExecutable(buildDirPath);

		if (testExecutablePath == null) {
			throw new IOException("Executable file not found in " + buildDirPath);
		}

		File oldProfraw = new File(rawProfilePath);
		if (oldProfraw.exists()) {
			oldProfraw.delete();
		}

		List<String> testCommand = Arrays.asList(testExecutablePath);
		commandExecutor.executeCommandWithEnv(testCommand, "LLVM_PROFILE_FILE", rawProfilePath);

		File profrawFile = new File(rawProfilePath);
		if (!profrawFile.exists()) {
			throw new IOException("Profile raw data file was not generated after test execution: " + rawProfilePath
					+ ". Check if the executable is compiled with -fprofile-instr-generate and runs correctly.");
		}
	}

	private void generateReport(CommandExecutor commandExecutor, String buildDirPath)
			throws IOException, InterruptedException {
		String rawProfilePath = buildDirPath + "/coverage.profraw";
		String profileDataPath = buildDirPath + "/coverage.profdata";
		String reportPath = buildDirPath + "/coverage_report.lcov";

		File profrawFile = new File(rawProfilePath);
		if (!profrawFile.exists()) {
			throw new IOException("Profile raw data file not found: " + rawProfilePath + ". Check test execution.");
		}

		String testExecutablePath = findExecutable(buildDirPath);
		if (testExecutablePath == null) {
			throw new IOException("Executable file not found in " + buildDirPath);
		}

		List<String> profdataCommand = Arrays.asList("llvm-profdata", "merge", "-sparse", rawProfilePath, "-o",
				profileDataPath);
		commandExecutor.executeCommand(profdataCommand);

		File profdataFile = new File(profileDataPath);
		if (!profdataFile.exists()) {
			throw new IOException("Profile data file was not generated: " + profileDataPath);
		}

		List<String> covCommand = Arrays.asList("llvm-cov", "export", testExecutablePath,
				"-instr-profile=" + profileDataPath, "--format=lcov");
		commandExecutor.executeCommandWithRedirect(covCommand, reportPath);

		List<String> reportLines = Files.readAllLines(Paths.get(reportPath));
		coverageData = ReportParser.parseLcovReport(reportLines);
		coverageData.entrySet()
				.removeIf(entry -> !analysisScope.contains(CoverageUtils.removeFirstSegment(entry.getKey(), 4)));
	}

	private void updateUI() {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			try {
				CoverageResultView view = (CoverageResultView) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
						.getActivePage().showView(CoverageResultView.ID);
				view.setDataProvider(this);
				view.updateCoverageResults(coverageData);
			} catch (Exception e) {
				LOGGER.log(new Status(IStatus.ERROR, "su.softcom.cldt.testing", "Failed to update UI", e));
			}
		});
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
		String projectName = CoverageTab.iproject;
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
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