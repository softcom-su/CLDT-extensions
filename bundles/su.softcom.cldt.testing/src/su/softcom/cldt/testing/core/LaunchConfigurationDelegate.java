package su.softcom.cldt.testing.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.ui.PlatformUI;

import su.softcom.cldt.testing.ui.CoverageResultView;
import su.softcom.cldt.testing.ui.CoverageTab;
import su.softcom.cldt.testing.utils.CoverageUtils;

public class LaunchConfigurationDelegate extends org.eclipse.debug.core.model.LaunchConfigurationDelegate
		implements CoverageResultView.CoverageDataProvider {

	private Map<String, Map<String, Object[]>> coverageData;
	private List<String> analysisScope = CoverageTab.analysisScope;

	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {

		String projectName = CoverageTab.iproject;
		if (projectName == null) {
			throw new CoreException(new Status(IStatus.ERROR, "su.softcom.cldt.testing", "Failed to get project name"));
		}

		String testProjectPath = Platform.getLocation().toString() + "/" + projectName;
		String buildDirPath = testProjectPath + "/build";

		CommandExecutor commandExecutor = new CommandExecutor();

		try {
			setupProject(commandExecutor, testProjectPath, buildDirPath);

			runTests(commandExecutor, buildDirPath);

			generateReport(commandExecutor, buildDirPath);

			updateUI();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setupProject(CommandExecutor commandExecutor, String testProjectPath, String buildDirPath)
			throws IOException, InterruptedException {
		commandExecutor.executeCommand("mkdir -p \"" + buildDirPath + "\"");
		commandExecutor.executeCommand("cmake \"" + testProjectPath + "\" -B \"" + buildDirPath + "\"");
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

		commandExecutor.executeCommand("LLVM_PROFILE_FILE=\"" + rawProfilePath + "\" \"" + testExecutablePath + "\"");
	}

	private void generateReport(CommandExecutor commandExecutor, String buildDirPath)
			throws IOException, InterruptedException {
		String rawProfilePath = buildDirPath + "/coverage.profraw";
		String profileDataPath = buildDirPath + "/coverage.profdata";
		String reportPath = buildDirPath + "/coverage_report.lcov";

		String testExecutablePath = findExecutable(buildDirPath);
		if (testExecutablePath == null) {
			throw new IOException("Executable file not found in " + buildDirPath);
		}

		commandExecutor.executeCommand(
				"llvm-profdata merge -sparse \"" + rawProfilePath + "\" -o \"" + profileDataPath + "\"");
		commandExecutor.executeCommand("llvm-cov export \"" + testExecutablePath + "\" -instr-profile=\""
				+ profileDataPath + "\" --format=lcov > \"" + reportPath + "\"");

		List<String> reportLines = Files.readAllLines(Paths.get(reportPath));
		coverageData = ReportParser.parseLcovReport(reportLines);
		coverageData.entrySet()
				.removeIf(entry -> analysisScope.contains(CoverageUtils.removeFirstSegment(entry.getKey(), 4)));
	}

	private void updateUI() {
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			try {
				CoverageResultView view = (CoverageResultView) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
						.getActivePage().showView(CoverageResultView.ID);
				view.setDataProvider(this);
				view.updateCoverageResults(coverageData);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	@Override
	public Map<String, Map<String, Object[]>> getCoverageData() {
		return coverageData;
	}
}