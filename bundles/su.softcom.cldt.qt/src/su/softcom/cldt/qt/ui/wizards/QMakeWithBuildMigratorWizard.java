package su.softcom.cldt.qt.ui.wizards;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.osgi.framework.Bundle;

import su.softcom.cldt.core.CMakeCorePlugin;
import su.softcom.cldt.core.CmakeProjectNature;
import su.softcom.cldt.internal.core.CMakeProject;
import su.softcom.cldt.qt.core.internal.ProjectBackupManager;
import su.softcom.cldt.qt.ui.internal.wizards.QMakeWithBuildMigratorWizardPage;

/**
 * Wizard for importing a Qt project into the IDE.
 */
public class QMakeWithBuildMigratorWizard extends Wizard implements IImportWizard {

	private QMakeWithBuildMigratorWizardPage page;

	public QMakeWithBuildMigratorWizard () {
		setWindowTitle("Import Qt Project");
	}

	/**
	 * Adds pages to the wizard.
	 */
	@Override
	public void addPages() {
		page = new QMakeWithBuildMigratorWizardPage();
		addPage(page);
	}

	/**
	 * Performs the finish operation for the wizard.
	 * 
	 * @return true if the operation was successful, false otherwise
	 */
	@Override
	public boolean performFinish() {
		try {
			String rootDirectory = page.getRootDirectory();
			String projectName = page.getProjectName();
			ProjectBackupManager backupManager = new ProjectBackupManager();

			try {
				backupManager.backupRootDirBeforeImport(Paths.get(rootDirectory));
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (rootDirectory == null || rootDirectory.isEmpty()) {
				showError("Please specify a valid root directory.");
				return false;
			}
			if (projectName == null || projectName.isEmpty()) {
				showError("Please specify a valid project name.");
				return false;
			}

			File rootDir = new File(rootDirectory);
			if (!rootDir.exists() || !rootDir.isDirectory()) {
				showError("The specified root directory does not exist or is not a directory.");
				return false;
			}

			String proFilePath = page.getProFilePath();
			if (proFilePath == null || proFilePath.isEmpty()) {
				showError("Please select a valid .pro file.");
				return false;
			}

			File outputDir = new File(rootDir, "build_results");
			if (!outputDir.exists() && !outputDir.mkdirs()) {
				showError("Failed to create output directory: " + outputDir.getAbsolutePath());
				return false;
			}

			String buildMigratorPath = findBuildMigratorPath();
			if (buildMigratorPath == null) {
				showError("Failed to find BuildMigrator.");
				return false;
			}

			executeCommand(buildMigratorPath, "--commands", "build", "--source_dir", rootDir.getAbsolutePath(),
					"--out_dir", outputDir.getAbsolutePath(), "--build_command", "qmake " + proFilePath,
					"--build_command", "make -C " + outputDir.getAbsolutePath() + "/_build/", "--log_provider",
					"CONSOLE");

			File logFile = new File(outputDir, "build_command_2.log");
			executeCommand(buildMigratorPath, "--commands", "parse", "--logs", logFile.getAbsolutePath(), "--log_type",
					"make", "--build_dirs", outputDir.getAbsolutePath(), "--working_dir", rootDir.getAbsolutePath(),
					"--platform", "linux", "--out_dir", outputDir.getAbsolutePath());

			executeCommand(buildMigratorPath, "--commands", "optimize", "generate", "--generator", "cmake", "--out_dir",
					outputDir.getAbsolutePath());

			File cmakeFile = new File(outputDir, "CMakeLists.txt");
			if (!cmakeFile.exists()) {
				showError("CMakeLists.txt was not generated.");
				return false;
			}

			IProject targetProject = page.getProject();
			if (!targetProject.exists()) {
				try {
					getContainer().run(false, true, monitor -> {
						try {
							targetProject.create(monitor);
							targetProject.open(monitor);
						} catch (CoreException e) {
							throw new InvocationTargetException(e);
						}
					});
				} catch (InvocationTargetException | InterruptedException e) {
					e.printStackTrace();
					return false;
				}
			}

			File buildResultDir = new File(rootDir, "build_results");
			if (!rootDir.exists() || !rootDir.isDirectory()) {
				System.err.println("The specified root directory does not exist.");
				return false;
			}

			Path projectPath = targetProject.getLocation().toFile().toPath();

			try {
				removeSourceFiles(buildResultDir, rootDir);
				copyBuildResultFiles(buildResultDir, projectPath);

				Files.walk(rootDir.toPath()).filter(path -> !path.startsWith(buildResultDir.toPath()))
						.forEach(sourcePath -> {
							try {
								Path relativePath = rootDir.toPath().relativize(sourcePath);
								Path targetPath = projectPath.resolve(relativePath);
								if (Files.isDirectory(sourcePath)) {
									Files.createDirectories(targetPath);
								} else {
									Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
						});

			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}

			try {
				targetProject.refreshLocal(IResource.DEPTH_INFINITE, null);
			} catch (CoreException e) {
				e.printStackTrace();
			}

			try {
				IProjectDescription projectDescription = targetProject.getDescription();
				projectDescription.setNatureIds(new String[] { CmakeProjectNature.ID });
				targetProject.setDescription(projectDescription, new NullProgressMonitor());
				CMakeProject cmakeProject = (CMakeProject) CMakeCorePlugin.eINSTANCE.getProject(targetProject);
				cmakeProject.setSource(page.getRootDirectory());
				cmakeProject.setBuildFolder(page.getBuildFolder().getName());
			} catch (CoreException e) {
				e.printStackTrace();
				return false;
			}

			try {
				backupManager.restoreRootDirAfterImport(Paths.get(rootDirectory));
			} catch (IOException e) {
				e.printStackTrace();
			}

			return true;
		} catch (Exception e) {
			e.printStackTrace();
			showError("An error occurred: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Removes files and directories from the root directory that match the
	 * structure in the "source" directory.
	 * 
	 * This method checks if the "source" directory exists within the specified
	 * build result directory. It then walks through all files and directories
	 * within "source" and compares them to the corresponding paths in the root
	 * directory. If a match is found, it deletes the files or directories from the
	 * root directory.
	 * 
	 * @param buildResultDir The directory containing the "source" directory that is
	 *                       to be checked.
	 * @param rootDir        The root directory where matching files and directories
	 *                       will be deleted.
	 */
	private void removeSourceFiles(File buildResultDir, File rootDir) {
		Path sourceDir = buildResultDir.toPath().resolve("source");

		if (Files.exists(sourceDir) && Files.isDirectory(sourceDir)) {
			try {
				Files.walk(sourceDir).forEach(sourcePath -> {
					try {
						if (sourcePath.equals(sourceDir)) {
							return;
						}

						Path relativePath = sourceDir.relativize(sourcePath);
						Path targetPath = rootDir.toPath().resolve(relativePath);

						if (Files.exists(targetPath)) {
							if (Files.isDirectory(targetPath)) {
								System.out.println("Removing directory from root: " + targetPath);
								try {
									deleteDirectory(targetPath);
								} catch (IOException e) {
									System.err.println("Error deleting: " + targetPath);
									e.printStackTrace();
								}
							} else {
								System.out.println("Removing file from root: " + targetPath);
								Files.delete(targetPath);
							}
						}
					} catch (IOException e) {
						System.err.println("Error processing: " + sourcePath);
						e.printStackTrace();
					}
				});
			} catch (IOException e) {
				System.err.println("Error walking through files: " + sourceDir);
				e.printStackTrace();
			}
		}
	}

	/**
	 * Copies the build result files to the project directory.
	 * 
	 * @param buildResultDir The directory containing the build results
	 * @param projectPath    The path to the project directory
	 * @throws IOException If an I/O error occurs during copying
	 */
	private void copyBuildResultFiles(File buildResultDir, Path projectPath) throws IOException {
		Path cmakeLists = buildResultDir.toPath().resolve("CMakeLists.txt");
		if (Files.exists(cmakeLists)) {
			Files.copy(cmakeLists, projectPath.resolve("CMakeLists.txt"), StandardCopyOption.REPLACE_EXISTING);
		}

		Path extensionCmake = buildResultDir.toPath().resolve("extensions.cmake");
		if (Files.exists(extensionCmake)) {
			Files.copy(extensionCmake, projectPath.resolve("extensions.cmake"), StandardCopyOption.REPLACE_EXISTING);
		}

		Path sourceDir = buildResultDir.toPath().resolve("source");

		if (Files.exists(sourceDir)) {
			Files.walk(sourceDir).forEach(sourcePath -> {
				try {
					Path relativePath = sourceDir.relativize(sourcePath);
					Path targetPath = projectPath.resolve("source").resolve(relativePath);
					if (Files.isDirectory(sourcePath)) {
						Files.createDirectories(targetPath);
					} else {
						Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		}
	}

	/**
	 * Deletes the specified directory and its contents recursively.
	 * 
	 * This method walks through the entire directory tree starting from the
	 * provided directory, sorts the paths in reverse order (to delete files and
	 * subdirectories before their parent directories), converts each path to a
	 * `File` object, and deletes them.
	 *
	 * @param directory The directory to be deleted.
	 * @throws IOException If an I/O error occurs during the directory deletion
	 *                     process.
	 */
	private void deleteDirectory(Path directory) throws IOException {
		Files.walk(directory).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
	}

	/**
	 * Finds the path to the BuildMigrator executable.
	 * 
	 * @return The path to the BuildMigrator executable or null if not found
	 */
	private String findBuildMigratorPath() {
		try {
			Bundle bundle = Platform.getBundle("su.softcom.cldt.qt");
			URL url = FileLocator.toFileURL(bundle.getEntry("/BuildMigrator/bin/build_migrator"));
			String pluginPath = url.getPath();
			File pluginDirectory = new File(pluginPath);
			
			return pluginDirectory.getPath();
		}catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Executes a system command.
	 * 
	 * @param command The command and its arguments to execute
	 * @throws IOException          If an I/O error occurs during execution
	 * @throws InterruptedException If the command execution is interrupted
	 */
	private void executeCommand(String... command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).start();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}
		}
		process.waitFor();
	}

	/**
	 * Shows an error dialog with the provided message.
	 * 
	 * @param message The error message to display
	 */
	private void showError(String message) {
		MessageDialog.openError(getShell(), "Error", message);
	}

	/**
	 * Initializes the wizard with the workbench and selection context.
	 * 
	 * @param workbench The workbench
	 * @param selection The selection context
	 */
	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
	}
}
