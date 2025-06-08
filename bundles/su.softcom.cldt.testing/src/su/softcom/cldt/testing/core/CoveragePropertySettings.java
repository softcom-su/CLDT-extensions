package su.softcom.cldt.testing.core;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;

import su.softcom.cldt.core.CMakeCorePlugin;
import su.softcom.cldt.core.cmake.ICMakeProject;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CoveragePropertySettings {
	private static final ILog LOGGER = Platform.getLog(CoveragePropertySettings.class);
	private static final String PLUGIN_ID = Activator.PLUGIN_ID;
	private static final QualifiedName COVERAGE_DATA_DIR = new QualifiedName(PLUGIN_ID, "coverage.data_dir");
	private static final String DEFAULT_BUILD_DIR = "build";

	private CoveragePropertySettings() {
	}

	public static String getCoverageDataDirProperty(IProject project) throws CoreException {
		String value = project.getPersistentProperty(COVERAGE_DATA_DIR);
		return value != null ? value : "";
	}

	public static void setCoverageDataDir(IProject project, String coverageDataDir) throws CoreException {
		project.setPersistentProperty(COVERAGE_DATA_DIR, coverageDataDir);
	}

	public static String getDefaultCoverageDataDir(IProject project) {
		try {
			ICMakeProject cmakeProject = CMakeCorePlugin.getDefault().getProject(project.getName());
			String absolutePath = cmakeProject.getBuildFolder().getLocation().toOSString();
			return toRelativePath(project, absolutePath);
		} catch (Exception e) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					String.format("Failed to get default build folder, using '%s'", DEFAULT_BUILD_DIR), e));
			return DEFAULT_BUILD_DIR;
		}
	}

	public static String toRelativePath(IProject project, String absolutePath) {
		try {
			Path projectPath = Paths.get(project.getLocation().toOSString()).toAbsolutePath().normalize();
			Path absPath = Paths.get(absolutePath).toAbsolutePath().normalize();
			if (absPath.startsWith(projectPath)) {
				return projectPath.relativize(absPath).toString().replace('\\', '/');
			}
		} catch (Exception e) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					String.format("Failed to convert to relative path: %s", absolutePath), e));
		}
		return absolutePath;
	}

	public static String toAbsolutePath(IProject project, String relativePath) {
		if (relativePath.isEmpty()) {
			return getDefaultCoverageDataDir(project);
		}
		try {
			Path projectPath = Paths.get(project.getLocation().toOSString()).toAbsolutePath().normalize();
			return projectPath.resolve(relativePath).normalize().toString();
		} catch (Exception e) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					String.format("Failed to convert to absolute path: %s", relativePath), e));
			return relativePath;
		}
	}

	public static String validateAndSaveSettings(IProject project, String coverageDataDir) throws CoreException {
		String validationError = validateCoverageDataDir(project, coverageDataDir);
		if (validationError != null) {
			return validationError;
		}

		setCoverageDataDir(project, coverageDataDir);
		CoverageSettingsManager.notifySettingsChanged();
		return null;
	}

	private static String validateCoverageDataDir(IProject project, String coverageDataDir) {
		if (!coverageDataDir.isEmpty()) {
			File dir = new File(toAbsolutePath(project, coverageDataDir));
			if (!dir.exists() || !dir.isDirectory()) {
				return "Coverage Data Directory does not exist or is not a directory";
			}
			Path projectPath = Paths.get(project.getLocation().toOSString()).toAbsolutePath().normalize();
			Path dirPath = dir.toPath().toAbsolutePath().normalize();
			if (!dirPath.startsWith(projectPath)) {
				return "Coverage Data Directory must be inside the project";
			}
		}
		return null;
	}
}