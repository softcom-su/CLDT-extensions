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

import org.eclipse.swt.widgets.Display;
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
        ProjectBackupManager backupManager = new ProjectBackupManager();
        File outputDir = null;
        try {
            String rootDirectory = page.getQmakeRootDirectory();
            String projectName = page.getQmakeProjectName();
            consoleView.logMessage(Messages.QtImportWizard_13 + rootDirectory);
            try {
                backupManager.backupRootDirBeforeImport(Paths.get(rootDirectory));
            } catch (IOException e) {
                consoleView.logError(Messages.QtImportWizard_14 + e.getMessage());
                e.printStackTrace();
                return false;
            }
            if (rootDirectory == null || rootDirectory.isEmpty()) {
                consoleView.logError(Messages.QtImportWizard_15);
                showError(Messages.QtImportWizard_141);
                return false;
            }
            if (projectName == null || projectName.isEmpty()) {
                consoleView.logError(Messages.QtImportWizard_17);
                showError(Messages.QtImportWizard_142);
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
            outputDir = new File(rootDir, "build_results");
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                consoleView.logError(Messages.QtImportWizard_143 + outputDir.getAbsolutePath());
                showError(Messages.QtImportWizard_143 + outputDir.getAbsolutePath());
                try {
                    backupManager.restoreRootDirAfterImport(Paths.get(rootDirectory));
                } catch (IOException e) {
                    consoleView.logError(Messages.QtImportWizard_16 + e.getMessage());
                }
                return false;
            }
            String buildMigratorPath = findBuildMigratorPath();
            if (buildMigratorPath == null) {
                consoleView.logError(Messages.QtImportWizard_26);
                showError(Messages.QtImportWizard_26);
                try {
                    backupManager.restoreRootDirAfterImport(Paths.get(rootDirectory));
                } catch (IOException e) {
                    consoleView.logError(Messages.QtImportWizard_16 + e.getMessage());
                }
                return false;
            }
            consoleView.logMessage(Messages.QtImportWizard_28);
            // Process subdirectories for .pro and .pri files
            processSubdirectories(rootDir, buildMigratorPath, outputDir);
            // Process the main .pro file
            if (!executeCommand(buildMigratorPath, "--commands", "build", "--source_dir", rootDir.getAbsolutePath(),
                    "--out_dir", outputDir.getAbsolutePath(), "--build_command", "qmake -d " + proFilePath,
                    "--log_provider", "CONSOLE")) {
                try {
                    backupManager.restoreRootDirAfterImport(Paths.get(rootDirectory));
                } catch (IOException e) {
                    consoleView.logError(Messages.QtImportWizard_16 + e.getMessage());
                }
                return false;
            }
            File logFile = new File(outputDir, "build_command_1.log");
            consoleView.logMessage(Messages.QtImportWizard_41);
            if (!executeCommand(buildMigratorPath, "--commands", "parse", "--logs", logFile.getAbsolutePath(), "--log_type",
                    "qmake", "--build_dirs", outputDir.getAbsolutePath(), "--working_dir", rootDir.getAbsolutePath(),
                    "--platform", "linux", "--out_dir", outputDir.getAbsolutePath())) {
                try {
                    backupManager.restoreRootDirAfterImport(Paths.get(rootDirectory));
                } catch (IOException e) {
                    consoleView.logError(Messages.QtImportWizard_16 + e.getMessage());
                }
                return false;
            }
            consoleView.logMessage(Messages.QtImportWizard_144);
            if (!executeCommand(buildMigratorPath, "--commands", "optimize", "generate", "--generator", "cmake", "--out_dir",
                    outputDir.getAbsolutePath())) {
                try {
                    backupManager.restoreRootDirAfterImport(Paths.get(rootDirectory));
                } catch (IOException e) {
                    consoleView.logError(Messages.QtImportWizard_16 + e.getMessage());
                }
                return false;
            }
            File cmakeFile = new File(outputDir, "CMakeLists.txt");
            if (!cmakeFile.exists()) {
                consoleView.logError(Messages.QtImportWizard_60);
                showError(Messages.QtImportWizard_60);
                try {
                    backupManager.restoreRootDirAfterImport(Paths.get(rootDirectory));
                } catch (IOException e) {
                    consoleView.logError(Messages.QtImportWizard_16 + e.getMessage());
                }
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
                    try {
                        backupManager.restoreRootDirAfterImport(Paths.get(rootDirectory));
                    } catch (IOException ex) {
                        consoleView.logError(Messages.QtImportWizard_16 + ex.getMessage());
                    }
                    return false;
                }
            }
            File buildResultDir = new File(rootDir, "build_results");
            if (!rootDir.exists() || !rootDir.isDirectory()) {
                consoleView.logError(Messages.QtImportWizard_66);
                try {
                    backupManager.restoreRootDirAfterImport(Paths.get(rootDirectory));
                } catch (IOException e) {
                    consoleView.logError(Messages.QtImportWizard_16 + e.getMessage());
                }
                return false;
            }
            Path projectPath = targetProject.getLocation().toFile().toPath();
            try {
                consoleView.logMessage(Messages.QtImportWizard_67);
                Path cmakeLists = buildResultDir.toPath().resolve("CMakeLists.txt");
                if (Files.exists(cmakeLists)) {
                    Files.copy(cmakeLists, rootDir.toPath().resolve("CMakeLists.txt"), StandardCopyOption.REPLACE_EXISTING);
                    consoleView.logMessage(Messages.QtImportWizard_42 + rootDir.getAbsolutePath());
                }
                Path extensionCmake = buildResultDir.toPath().resolve("extensions.cmake");
                if (Files.exists(extensionCmake)) {
                    Files.copy(extensionCmake, rootDir.toPath().resolve("extensions.cmake"), StandardCopyOption.REPLACE_EXISTING);
                    consoleView.logMessage(Messages.QtImportWizard_45 + rootDir.getAbsolutePath());
                }
                consoleView.logMessage(Messages.QtImportWizard_46);
                Files.walk(rootDir.toPath()).forEach(sourcePath -> {
                    try {
                        Path relativePath = rootDir.toPath().relativize(sourcePath);
                        Path targetPath = projectPath.resolve(relativePath);
                        if (!sourcePath.toString().contains("build_results")) {
                            if (Files.isDirectory(sourcePath)) {
                                Files.createDirectories(targetPath);
                            } else {
                                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                consoleView.logMessage(Messages.QtImportWizard_48 + sourcePath + Messages.QtImportWizard_49 + targetPath);
                            }
                        }
                    } catch (IOException e) {
                        consoleView.logError(Messages.QtImportWizard_70 + sourcePath + ": " + e.getMessage());
                    }
                });
            } catch (IOException e) {
                consoleView.logError(Messages.QtImportWizard_72 + e.getMessage());
                e.printStackTrace();
                try {
                    backupManager.restoreRootDirAfterImport(Paths.get(rootDirectory));
                } catch (IOException ex) {
                    consoleView.logError(Messages.QtImportWizard_16 + ex.getMessage());
                }
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
                try {
                    backupManager.restoreRootDirAfterImport(Paths.get(rootDirectory));
                } catch (IOException ex) {
                    consoleView.logError(Messages.QtImportWizard_16 + ex.getMessage());
                }
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
            if (outputDir != null) {
                try {
                    backupManager.restoreRootDirAfterImport(Paths.get(page.getQmakeRootDirectory()));
                } catch (IOException ex) {
                    consoleView.logError(Messages.QtImportWizard_16 + ex.getMessage());
                }
            }
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
                new InputStreamReader(project.getFile("CMakeLists.txt").getContents()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                cmakeListsContentBuilder.append(line);
            }
        } catch (Exception e) {
            consoleView.logError(Messages.QtImportWizard_82 + e.getMessage());
            Platform.getLog(getClass()).warn(Messages.QtImportWizard_145, e);
            return null;
        }
        try {
            parser = new CMakeParser(cmakeListsContentBuilder.toString(), false);
            cmakeTree = parser.parse();
        } catch (IOException | UnexpectedTokenException | CoreException e) {
            consoleView.logError(Messages.QtImportWizard_84 + e.getMessage());
            Platform.getLog(getClass()).warn(Messages.QtImportWizard_146, e);
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
     * Deletes a directory and its contents recursively.
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            consoleView.logMessage(Messages.QtImportWizard_54 + directory);
            return;
        }
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            consoleView.logMessage(Messages.QtImportWizard_55 + path);
                        } catch (IOException e) {
                            consoleView.logError(Messages.QtImportWizard_56 + path + ": " + e.getMessage());
                            throw new RuntimeException(e);
                        }
                    });
                return; // Exit if deletion succeeds
            } catch (RuntimeException e) {
                consoleView.logError(Messages.QtImportWizard_58 + attempt + Messages.QtImportWizard_59 + e.getCause().getMessage());
                if (attempt == maxRetries) {
                    throw new IOException(Messages.QtImportWizard_61 + maxRetries + Messages.QtImportWizard_65, e.getCause());
                }
            }
        }
    }

    /**
     * Locates the BuildMigrator executable path.
     */
    private String findBuildMigratorPath() {
        try {
            Bundle bundle = Platform.getBundle("su.softcom.cldt.qt");
            URL url = FileLocator.toFileURL(bundle.getEntry("/BuildMigrator/bin/build_migrator"));
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
     * Processes subdirectories for .pro and .pri files, running BuildMigrator and handling CMakeLists.txt.
     */
    private void processSubdirectories(File rootDir, String buildMigratorPath, File outputDir) {
        try {
            String mainProFilePath = page.getQmakeProFilePath();
            Path mainProFile = mainProFilePath != null ? Paths.get(mainProFilePath) : null;
            Files.walk(rootDir.toPath())
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        Files.list(dir).filter(path -> {
                            String fileName = path.getFileName().toString();
                            // Exclude the main .pro file
                            return (fileName.endsWith(".pro") || fileName.endsWith(".pri"))
                                    && (mainProFile == null || !path.equals(mainProFile));
                        }).forEach(file -> {
                            try {
                                 String fileName = file.getFileName().toString();
                                 boolean isPriFile = fileName.endsWith(".pri");
                                 Path proFile = file;
                                 if (isPriFile) {
                                     String proFileName = fileName.replace(".pri", ".pro");
                                     proFile = dir.resolve(proFileName);
                                     Files.copy(file, proFile, StandardCopyOption.REPLACE_EXISTING);
                                     consoleView.logMessage(Messages.QtImportWizard_85 + proFile);
                                 }
                                 File subOutputDir = new File(outputDir, dir.toFile().getName());
                                 if (!subOutputDir.exists() && !subOutputDir.mkdirs()) {
                                     consoleView.logError(Messages.QtImportWizard_86 + subOutputDir.getAbsolutePath());
                                     return;
                                 }
                                 consoleView.logMessage(Messages.QtImportWizard_138 + fileName + Messages.QtImportWizard_139 + dir);
                                 if (!executeCommand(buildMigratorPath, "--commands", "build", "--source_dir", dir.toFile().getAbsolutePath(),
                                         "--out_dir", subOutputDir.getAbsolutePath(), "--build_command", "qmake -d " + proFile,
                                         "--log_provider", "CONSOLE")) {
                                     consoleView.logError(Messages.QtImportWizard_97 + proFile);
                                     if (isPriFile) {
                                         Files.deleteIfExists(proFile);
                                     }
                                     return;
                                 }
                                 File subLogFile = new File(subOutputDir, "build_command_1.log");
                                 if (!executeCommand(buildMigratorPath, "--commands", "parse", "--logs", subLogFile.getAbsolutePath(),
                                         "--log_type", "qmake", "--build_dirs", subOutputDir.getAbsolutePath(),
                                         "--working_dir", dir.toFile().getAbsolutePath(), "--platform", "linux",
                                         "--out_dir", subOutputDir.getAbsolutePath())) {
                                     consoleView.logError(Messages.QtImportWizard_112 + proFile);
                                     if (isPriFile) {
                                         Files.deleteIfExists(proFile);
                                     }
                                     return;
                                 }
                                 if (!executeCommand(buildMigratorPath, "--commands", "optimize", "generate", "--generator", "cmake",
                                         "--out_dir", subOutputDir.getAbsolutePath())) {
                                     consoleView.logError(Messages.QtImportWizard_121 + proFile);
                                     if (isPriFile) {
                                         Files.deleteIfExists(proFile);
                                     }
                                     return;
                                 }
                                 Path cmakeLists = subOutputDir.toPath().resolve("CMakeLists.txt");
                                if (Files.exists(cmakeLists)) {
                                    String targetCmakeName = isPriFile ? fileName.replace(".pri", ".cmake") : "CMakeLists.txt";
                                    Path targetCmakePath = dir.resolve(targetCmakeName);
                                    Files.copy(cmakeLists, targetCmakePath, StandardCopyOption.REPLACE_EXISTING);
                                    consoleView.logMessage(Messages.QtImportWizard_126 + targetCmakePath);
                                }
                                Path extensionsCmake = subOutputDir.toPath().resolve("extensions.cmake");
                                if (Files.exists(extensionsCmake)) {
                                    Path targetExtensionsPath = dir.resolve("extensions.cmake");
                                    Files.copy(extensionsCmake, targetExtensionsPath, StandardCopyOption.REPLACE_EXISTING);
                                    consoleView.logMessage(Messages.QtImportWizard_129 + targetExtensionsPath);
                                }
                                 if (isPriFile) {
                                     Files.deleteIfExists(proFile);
                                     consoleView.logMessage(Messages.QtImportWizard_130 + proFile);
                                 }
                             } catch (IOException e) {
                                 consoleView.logError(Messages.QtImportWizard_131 + file + ": " + e.getMessage());
                             }
                         });
                     } catch (IOException e) {
                         consoleView.logError(Messages.QtImportWizard_133 + dir + ": " + e.getMessage());
                     }
                 });
        } catch (IOException e) {
            consoleView.logError(Messages.QtImportWizard_135 + e.getMessage());
        }
    }

    /**
     * Executes a command and logs its output.
     */
    private boolean executeCommand(String... command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            consoleView.logMessage(Messages.QtImportWizard_111 + String.join(" ", command));
            Process process = processBuilder.start();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = stdoutReader.readLine()) != null) {
                        String finalLine = line;
                        Display.getDefault().asyncExec(() -> {
                            consoleView.logMessage(finalLine);
                        });
                    }
                } catch (IOException e) {
                    String errorMessage = Messages.QtImportWizard_147 + e.getMessage();
                    Display.getDefault().asyncExec(() -> {
                        consoleView.logError(errorMessage);
                    });
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = stderrReader.readLine()) != null) {
                        String finalLine = line;
                        Display.getDefault().asyncExec(() -> {
                            if (finalLine.contains("INFO")) {
                                consoleView.logMessage(finalLine);
                            } else if (finalLine.contains("WARNING")) {
                                consoleView.logMessage(finalLine);
                            } else if (finalLine.contains("ERROR") || finalLine.contains("CRITICAL")) {
                                consoleView.logError(finalLine);
                            } else {
                                consoleView.logMessage(finalLine);
                            }
                        });
                    }
                } catch (IOException e) {
                    String errorMessage = Messages.QtImportWizard_114 + e.getMessage();
                    Display.getDefault().asyncExec(() -> {
                        consoleView.logError(errorMessage);
                    });
                }
            });

            stdoutThread.start();
            stderrThread.start();

            int exitCode = process.waitFor();
            
            stdoutThread.join();
            stderrThread.join();

            if (exitCode != 0) {
                Display.getDefault().asyncExec(() -> {
                    consoleView.logError(Messages.QtImportWizard_117 + exitCode);
                });
                return false;
            } else {
                Display.getDefault().asyncExec(() -> {
                    consoleView.logMessage(Messages.QtImportWizard_118);
                });
                return true;
            }
        } catch (IOException | InterruptedException e) {
            String errorMessage = Messages.QtImportWizard_115 + String.join(" ", command) + ": " + e.getMessage();
            Display.getDefault().asyncExec(() -> {
                consoleView.logError(errorMessage);
            });
            return false;
        }
    }

    /**
     * Displays an error dialog.
     */
    private void showError(String message) {
        MessageDialog.openError(getShell(), Messages.QtImportWizard_0, message);
    }
}