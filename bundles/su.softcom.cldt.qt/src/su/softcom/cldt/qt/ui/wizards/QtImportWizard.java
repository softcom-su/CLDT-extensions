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
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.net.URL;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.eclipse.ui.wizards.datatransfer.FileSystemStructureProvider;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;
import org.osgi.framework.Bundle;

import su.softcom.cldt.core.cmake.CMakeParser;
import su.softcom.cldt.core.cmake.CMakeParser.UnexpectedTokenException;
import su.softcom.cldt.core.cmake.CMakeRoot;
import su.softcom.cldt.core.cmake.ICMakeProject;
import su.softcom.cldt.core.CMakeCorePlugin;
import su.softcom.cldt.core.CmakeProjectNature;
import su.softcom.cldt.core.cmake.CommandNode;
import su.softcom.cldt.internal.core.CMakeProject;
import su.softcom.cldt.qt.core.QtCorePlugin;
import su.softcom.cldt.qt.core.QtNature;
import su.softcom.cldt.qt.core.QtProject;
import su.softcom.cldt.qt.core.QtProject.AutoSetting;
import su.softcom.cldt.qt.core.internal.QtAutoSettingsVisitor;
import su.softcom.cldt.qt.core.internal.ProjectBackupManager;
import su.softcom.cldt.qt.ui.internal.views.QtImportConsoleView;
import su.softcom.cldt.qt.ui.internal.wizards.QtImportWizardPage;

import su.softcom.cldt.qt.common.Messages;

public class QtImportWizard extends Wizard implements IImportWizard {

    private QtImportWizardPage page;
    private QtImportConsoleView consoleView;

    /**
     * Initializes the wizard with workbench and selection.
     * 
     * @param workbench The workbench instance.
     * @param selection The current selection.
     */
    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
    }

    /**
     * Adds the wizard page.
     */
    @Override
    public void addPages() {
        page = new QtImportWizardPage(Messages.qtImportPageTitleName);
        addPage(page);
    }

    /**
     * Performs the finish action, opening the console and executing import.
     */
    @Override
    public boolean performFinish() {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            IWorkbenchPage activePage = window.getActivePage();
            consoleView = (QtImportConsoleView) activePage.showView(QtImportConsoleView.ID);
        } catch (PartInitException e) {
            MessageDialog.openError(getShell(), Messages.QtImportWizard_0, Messages.QtImportWizard_1 + e.getMessage());
            e.printStackTrace();
            return false;
        }
        consoleView.clear();
        if (!page.getQmakeRootDirectory().isBlank()) {
            return performQMakeFinish();
        }
        return performCMakeFinish();
    }

    /**
     * Executes the CMake project import logic.
     */
    private boolean performCMakeFinish() {
        consoleView.logMessage(Messages.QtImportWizard_2);
        IProject targetProject = page.getProject();
        if (!targetProject.exists()) {
            try {
                getContainer().run(false, true, monitor -> {
                    try {
                        consoleView.logMessage(Messages.QtImportWizard_3 + targetProject.getName());
                        targetProject.create(monitor);
                        targetProject.open(monitor);
                    } catch (CoreException e) {
                        consoleView.logError(Messages.QtImportWizard_4 + e.getMessage());
                        throw new InvocationTargetException(e);
                    }
                });
            } catch (InvocationTargetException | InterruptedException e) {
                consoleView.logError(Messages.QtImportWizard_5 + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        ImportOperation operation = new ImportOperation(targetProject.getFullPath().addTrailingSeparator(),
                page.getRootFile(), FileSystemStructureProvider.INSTANCE, path -> IOverwriteQuery.NO);
        operation.setCreateContainerStructure(false);
        try {
            consoleView.logMessage(Messages.QtImportWizard_6 + page.getRootFile().getAbsolutePath());
            getContainer().run(false, true, operation);
        } catch (InvocationTargetException | InterruptedException e) {
            consoleView.logError(Messages.QtImportWizard_7 + e.getMessage());
            e.printStackTrace();
            return false;
        }
        try {
            IProjectDescription projectDescription = targetProject.getDescription();
            projectDescription.setNatureIds(new String[] { CmakeProjectNature.ID, QtNature.ID });
            consoleView.logMessage(Messages.QtImportWizard_8);
            targetProject.setDescription(projectDescription, new NullProgressMonitor());
        } catch (CoreException e) {
            consoleView.logError(Messages.QtImportWizard_9 + e.getMessage());
            e.printStackTrace();
            return false;
        }
        consoleView.logMessage(Messages.QtImportWizard_10);
        CMakeRoot cmakeTree = parseCMakeLists(targetProject);
        setProjectSettings(targetProject, cmakeTree);
        consoleView.logMessage(Messages.QtImportWizard_11);
        return true;
    }

    /**
     * Executes the QMake project import with conversion to CMake.
     */
    private boolean performQMakeFinish() {
        consoleView.logMessage(Messages.QtImportWizard_12);
        try {
            String rootDirectory = page.getQmakeRootDirectory();
            String projectName = page.getQmakeProjectName();
            ProjectBackupManager backupManager = new ProjectBackupManager();
            consoleView.logMessage(Messages.QtImportWizard_13 + rootDirectory);
            try {
                backupManager.backupRootDirBeforeImport(Paths.get(rootDirectory));
            } catch (IOException e) {
                consoleView.logError(Messages.QtImportWizard_14 + e.getMessage());
                e.printStackTrace();
            }
            if (rootDirectory == null || rootDirectory.isEmpty()) {
                consoleView.logError(Messages.QtImportWizard_15);
                showError("Please specify a valid root directory."); //$NON-NLS-1$
                return false;
            }
            if (projectName == null || projectName.isEmpty()) {
                consoleView.logError(Messages.QtImportWizard_17);
                showError("Please specify a valid project name."); //$NON-NLS-1$
                return false;
            }
            File rootDir = new File(rootDirectory);
            if (!rootDir.exists() || !rootDir.isDirectory()) {
                consoleView.logError(Messages.QtImportWizard_19);
                showError(Messages.QtImportWizard_20);
                return false;
            }
            String proFilePath = page.getQmakeProFilePath();
            if (proFilePath == null || proFilePath.isEmpty()) {
                consoleView.logError(Messages.QtImportWizard_21);
                showError(Messages.QtImportWizard_22);
                return false;
            }
            File outputDir = new File(rootDir, "build_results"); //$NON-NLS-1$
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                consoleView.logError(Messages.QtImportWizard_24 + outputDir.getAbsolutePath());
                showError("Failed to create output directory: " + outputDir.getAbsolutePath()); //$NON-NLS-1$
                return false;
            }
            String buildMigratorPath = findBuildMigratorPath();
            if (buildMigratorPath == null) {
                consoleView.logError(Messages.QtImportWizard_26);
                showError("Failed to find BuildMigrator."); //$NON-NLS-1$
                return false;
            }
            consoleView.logMessage(Messages.QtImportWizard_28);
            executeCommand(buildMigratorPath, "--commands", "build", "--source_dir", rootDir.getAbsolutePath(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    "--out_dir", outputDir.getAbsolutePath(), "--build_command", "qmake " + proFilePath, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    "--build_command", "make -C " + outputDir.getAbsolutePath() + "/_build/", "--log_provider", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    "CONSOLE"); //$NON-NLS-1$
            File logFile = new File(outputDir, "build_command_2.log"); //$NON-NLS-1$
            consoleView.logMessage(Messages.QtImportWizard_41);
            executeCommand(buildMigratorPath, "--commands", "parse", "--logs", logFile.getAbsolutePath(), "--log_type", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    "make", "--build_dirs", outputDir.getAbsolutePath(), "--working_dir", rootDir.getAbsolutePath(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    "--platform", "linux", "--out_dir", outputDir.getAbsolutePath()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            consoleView.logMessage("Запуск BuildMigrator для генерации CMakeLists.txt..."); //$NON-NLS-1$
            executeCommand(buildMigratorPath, "--commands", "optimize", "generate", "--generator", "cmake", "--out_dir", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                    outputDir.getAbsolutePath());
            File cmakeFile = new File(outputDir, "CMakeLists.txt"); //$NON-NLS-1$
            if (!cmakeFile.exists()) {
                consoleView.logError(Messages.QtImportWizard_60);
                showError("CMakeLists.txt was not generated."); //$NON-NLS-1$
                return false;
            }
            IProject targetProject = page.getProject();
            if (!targetProject.exists()) {
                try {
                    getContainer().run(false, true, monitor -> {
                        try {
                            consoleView.logMessage(Messages.QtImportWizard_62 + projectName);
                            targetProject.create(monitor);
                            targetProject.open(monitor);
                        } catch (CoreException e) {
                            consoleView.logError(Messages.QtImportWizard_63 + e.getMessage());
                            throw new InvocationTargetException(e);
                        }
                    });
                } catch (InvocationTargetException | InterruptedException e) {
                    consoleView.logError(Messages.QtImportWizard_64 + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }
            File buildResultDir = new File(rootDir, "build_results"); //$NON-NLS-1$
            if (!rootDir.exists() || !rootDir.isDirectory()) {
                consoleView.logError(Messages.QtImportWizard_66);
                return false;
            }
            Path projectPath = targetProject.getLocation().toFile().toPath();
            try {
                consoleView.logMessage(Messages.QtImportWizard_67);
                removeSourceFiles(buildResultDir, rootDir);
                consoleView.logMessage(Messages.QtImportWizard_68);
                copyBuildResultFiles(buildResultDir, projectPath);
                consoleView.logMessage(Messages.QtImportWizard_69);
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
                                consoleView.logError(Messages.QtImportWizard_70 + sourcePath + ": " + e.getMessage()); //$NON-NLS-2$
                            }
                        });
            } catch (IOException e) {
                consoleView.logError(Messages.QtImportWizard_72 + e.getMessage());
                e.printStackTrace();
                return false;
            }
            try {
                consoleView.logMessage(Messages.QtImportWizard_73);
                targetProject.refreshLocal(IResource.DEPTH_INFINITE, null);
            } catch (CoreException e) {
                consoleView.logError(Messages.QtImportWizard_74 + e.getMessage());
                e.printStackTrace();
            }
            try {
                IProjectDescription projectDescription = targetProject.getDescription();
                projectDescription.setNatureIds(new String[] { CmakeProjectNature.ID });
                consoleView.logMessage(Messages.QtImportWizard_75);
                targetProject.setDescription(projectDescription, new NullProgressMonitor());
                CMakeProject cmakeProject = (CMakeProject) CMakeCorePlugin.eINSTANCE.getProject(targetProject);
                cmakeProject.setSource(page.getQmakeRootDirectory());
                cmakeProject.setBuildFolder(page.getQmakeBuildFolder().getName());
            } catch (CoreException e) {
                consoleView.logError(Messages.QtImportWizard_76 + e.getMessage());
                e.printStackTrace();
                return false;
            }
            consoleView.logMessage(Messages.QtImportWizard_77);
            try {
                backupManager.restoreRootDirAfterImport(Paths.get(rootDirectory));
            } catch (IOException e) {
                consoleView.logError(Messages.QtImportWizard_78 + e.getMessage());
                e.printStackTrace();
            }
            consoleView.logMessage(Messages.QtImportWizard_79);
            return true;
        } catch (Exception e) {
            consoleView.logError(Messages.QtImportWizard_80 + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Parses CMakeLists.txt and returns the AST.
     */
    private CMakeRoot parseCMakeLists(IProject project) {
        CMakeParser parser;
        CMakeRoot cmakeTree;
        StringBuilder cmakeListsContentBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(project.getFile("CMakeLists.txt").getContents()))) { //$NON-NLS-1$
            String line;
            while ((line = reader.readLine()) != null) {
                cmakeListsContentBuilder.append(line);
            }
        } catch (Exception e) {
            consoleView.logError(Messages.QtImportWizard_82 + e.getMessage());
            Platform.getLog(getClass()).warn("Unable to get the root CMakeLists.txt content", e); //$NON-NLS-1$
            return null;
        }
        try {
            parser = new CMakeParser(cmakeListsContentBuilder.toString(), false);
            cmakeTree = parser.parse();
        } catch (IOException | UnexpectedTokenException | CoreException e) {
            consoleView.logError(Messages.QtImportWizard_84 + e.getMessage());
            Platform.getLog(getClass()).warn("Unable to parse the root CMakeLists.txt", e); //$NON-NLS-1$
            return null;
        }
        return cmakeTree;
    }

    /**
     * Configures Qt dependencies and AUTO settings for the CMake project.
     */
    private void setProjectSettings(IProject project, CMakeRoot cmakeTree) {
        QtProject qtProject = QtCorePlugin.getInstance().getProject(project);
        ICMakeProject cmakeProject = qtProject.getCMakeProject();
        cmakeProject.setBuildFolder(page.getBuildFolder().getName());
        Map<AutoSetting, Boolean> autoSettings = new EnumMap<>(AutoSetting.class);
        Map<AutoSetting, CommandNode> autoSettingNodes = new EnumMap<>(AutoSetting.class);
        cmakeTree.accept(new QtAutoSettingsVisitor(autoSettingNodes));
        for (var setting : Arrays.asList(AutoSetting.values())) {
            autoSettings.put(setting, autoSettingNodes.containsKey(setting));
        }
        qtProject.setAutoSettingsAndValues(autoSettings);
    }

    /**
     * Removes matching files and directories from the root directory.
     */
    private void removeSourceFiles(File buildResultDir, File rootDir) {
        Path sourceDir = buildResultDir.toPath().resolve("source"); //$NON-NLS-1$
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
                                consoleView.logMessage(Messages.QtImportWizard_87 + targetPath);
                                try {
                                    deleteDirectory(targetPath);
                                } catch (IOException e) {
                                    consoleView.logError(Messages.QtImportWizard_88 + targetPath + ": " + e.getMessage()); //$NON-NLS-2$
                                }
                            } else {
                                consoleView.logMessage(Messages.QtImportWizard_90 + targetPath);
                                Files.delete(targetPath);
                            }
                        }
                    } catch (IOException e) {
                        consoleView.logError(Messages.QtImportWizard_91 + sourcePath + ": " + e.getMessage()); //$NON-NLS-2$
                    }
                });
            } catch (IOException e) {
                consoleView.logError(Messages.QtImportWizard_93 + sourceDir + ": " + e.getMessage()); //$NON-NLS-2$
            }
        }
    }

    /**
     * Copies build result files to the project directory.
     */
    private void copyBuildResultFiles(File buildResultDir, Path projectPath) throws IOException {
        Path cmakeLists = buildResultDir.toPath().resolve("CMakeLists.txt"); //$NON-NLS-1$
        if (Files.exists(cmakeLists)) {
            Files.copy(cmakeLists, projectPath.resolve("CMakeLists.txt"), StandardCopyOption.REPLACE_EXISTING); //$NON-NLS-1$
            consoleView.logMessage(Messages.QtImportWizard_97);
        }
        Path extensionCmake = buildResultDir.toPath().resolve("extensions.cmake"); //$NON-NLS-1$
        if (Files.exists(extensionCmake)) {
            Files.copy(extensionCmake, projectPath.resolve("extensions.cmake"), StandardCopyOption.REPLACE_EXISTING); //$NON-NLS-1$
            consoleView.logMessage(Messages.QtImportWizard_100);
        }
        Path sourceDir = buildResultDir.toPath().resolve("source"); //$NON-NLS-1$
        if (Files.exists(sourceDir)) {
            Files.walk(sourceDir).forEach(sourcePath -> {
                try {
                    Path relativePath = sourceDir.relativize(sourcePath);
                    Path targetPath = projectPath.resolve("source").resolve(relativePath); //$NON-NLS-1$
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        consoleView.logMessage(Messages.QtImportWizard_103 + relativePath + Messages.QtImportWizard_16);
                    }
                } catch (IOException e) {
                    consoleView.logError(Messages.QtImportWizard_105 + sourcePath + ": " + e.getMessage()); //$NON-NLS-2$
                }
            });
        }
    }

    /**
     * Deletes a directory and its contents recursively.
     */
    private void deleteDirectory(Path directory) throws IOException {
        Files.walk(directory).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }

    /**
     * Locates the BuildMigrator executable path.
     */
    private String findBuildMigratorPath() {
        try {
            Bundle bundle = Platform.getBundle("su.softcom.cldt.qt"); //$NON-NLS-1$
            URL url = FileLocator.toFileURL(bundle.getEntry("/BuildMigrator/bin/build_migrator")); //$NON-NLS-1$
            String pluginPath = url.getPath();
            consoleView.logMessage(Messages.QtImportWizard_109 + pluginPath);
            return pluginPath;
        } catch (IOException e) {
            consoleView.logError(Messages.QtImportWizard_110 + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Executes a command and logs its output.
     */
    private void executeCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        consoleView.logMessage(Messages.QtImportWizard_111 + String.join(" ", command)); //$NON-NLS-2$
        Process process = processBuilder.start();
        try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                consoleView.logMessage(line);
            }
        }
        try (BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = stderrReader.readLine()) != null) {
                if (line.contains("INFO")) { //$NON-NLS-1$
                    consoleView.logMessage(line);
                } else if (line.contains("WARNING")) { //$NON-NLS-1$
                    consoleView.logMessage(line);
                } else if (line.contains("ERROR") || line.contains("CRITICAL")) { //$NON-NLS-1$ //$NON-NLS-2$
                    consoleView.logError(line);
                } else {
                    consoleView.logMessage(line);
                }
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            consoleView.logError(Messages.QtImportWizard_117 + exitCode);
        } else {
            consoleView.logMessage(Messages.QtImportWizard_118);
        }
    }

    /**
     * Displays an error dialog.
     */
    private void showError(String message) {
        MessageDialog.openError(getShell(), "Error", message); //$NON-NLS-1$
    }
}