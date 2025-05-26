package su.softcom.cldt.testing.core;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;

import su.softcom.cldt.core.CMakeCorePlugin;
import su.softcom.cldt.core.cmake.ICMakeProject;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CoveragePropertySettings {
	private static final String PLUGIN_ID = Activator.PLUGIN_ID;
	private static final QualifiedName COVERAGE_DATA_DIR = new QualifiedName(PLUGIN_ID, "coverage.data_dir");
	private static final String DEFAULT_BUILD_DIR = "build";
	private static final String WARNING_DEFAULT_BUILD_DIR = "Failed to get default build folder, using '%s'";
	private static final String WARNING_RELATIVE_PATH = "Failed to convert to relative path: %s";
	private static final String WARNING_ABSOLUTE_PATH = "Failed to convert to absolute path: %s";
	private static final String ERROR_INVALID_DIR = "Coverage Data Directory does not exist or is not a directory";
	private static final String ERROR_DIR_OUTSIDE_PROJECT = "Coverage Data Directory must be inside the project";

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
			logWarning(String.format(WARNING_DEFAULT_BUILD_DIR, DEFAULT_BUILD_DIR), e);
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
			logWarning(String.format(WARNING_RELATIVE_PATH, absolutePath), e);
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
			logWarning(String.format(WARNING_ABSOLUTE_PATH, relativePath), e);
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
				return ERROR_INVALID_DIR;
			}
			Path projectPath = Paths.get(project.getLocation().toOSString()).toAbsolutePath().normalize();
			Path dirPath = dir.toPath().toAbsolutePath().normalize();
			if (!dirPath.startsWith(projectPath)) {
				return ERROR_DIR_OUTSIDE_PROJECT;
			}
		}
		return null;
	}

	private static void logWarning(String message, Throwable cause) {
		Activator.getDefault().getLog().log(new Status(IStatus.WARNING, PLUGIN_ID, message, cause));
	}
}