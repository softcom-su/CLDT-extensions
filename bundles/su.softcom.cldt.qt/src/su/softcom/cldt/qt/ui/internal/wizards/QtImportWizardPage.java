package su.softcom.cldt.qt.ui.internal.wizards;

import java.io.File;
import java.nio.file.Paths;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

import su.softcom.cldt.qt.common.Messages;


public class QtImportWizardPage extends WizardPage implements IWizardPage {
    
    private File rootFile;
    private IProject project;
    private Text rootDirText;
    private Text buildDirText;
    private Text projectNameText;
    private File qmakeRootFile;
    private Text qmakeRootDirectoryText;
    private Text qmakeProjectNameText;
    private Text qmakeBuildDirectoryText;
    private Text qmakeProFilePathText;

    /**
     * Constructs a new wizard page with the given name.
     * 
     * @param pageName The name of the page.
     */
    public QtImportWizardPage(String pageName) {
        super(pageName);
        setTitle(pageName);
        setDescription(Messages.qtImportPageDescription);
        setPageComplete(false);
    }

    /**
     * Creates the UI controls for the wizard page.
     * 
     * @param parent The parent composite.
     */
    @Override
    public void createControl(Composite parent) {
        Composite common = new Composite(parent, SWT.NONE);
        common.setLayout(new GridLayout(1, false));
        setControl(common);

        TabFolder tabFolder = new TabFolder(common, SWT.NONE);
        tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        TabItem mainTab = new TabItem(tabFolder, SWT.NONE);
        mainTab.setText(Messages.qtImportFirstTab);

        Composite mainComposite = new Composite(tabFolder, SWT.NONE);
        mainComposite.setLayout(new GridLayout(1, false));
        mainTab.setControl(mainComposite);

        Group mainSettingsGroup = new Group(mainComposite, SWT.NONE);
        mainSettingsGroup.setLayout(new GridLayout(4, false));
        mainSettingsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        mainSettingsGroup.setText(Messages.qtImportPageProjectGroupText);

        Label rootDirLabel = new Label(mainSettingsGroup, SWT.NONE);
        rootDirLabel.setText(Messages.qtImportPageProjectProjectRootDirLabel);

        rootDirText = new Text(mainSettingsGroup, SWT.BORDER);
        rootDirText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
        rootDirText.addModifyListener(event -> {
            rootFile = Paths.get(rootDirText.getText()).toFile();
            validatePage();
        });

        Button rootDirBrowse = new Button(mainSettingsGroup, SWT.FLAT);
        rootDirBrowse.setText(Messages.qtImportPageBrowseButtonText);
        rootDirBrowse.addListener(SWT.Selection, event -> {
            DirectoryDialog directoryDialog = new DirectoryDialog(getShell(), SWT.SHEET);
            String filepath = directoryDialog.open();
            if (filepath != null && !filepath.isBlank()) {
                rootDirText.setText(filepath);
                int filePathSeparatorIndex = filepath.lastIndexOf(File.separator);
                String newName = filePathSeparatorIndex != -1
                        ? filepath.substring(filePathSeparatorIndex + 1)
                        : filepath;
                projectNameText.setText(createUniqueName(newName));
            }
        });

        Label projectNameLabel = new Label(mainSettingsGroup, SWT.NONE);
        projectNameLabel.setText(Messages.qtImportPageProjectNameLabel);

        projectNameText = new Text(mainSettingsGroup, SWT.BORDER);
        projectNameText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 3, 1));
        projectNameText.addModifyListener(event -> validatePage());

        Group buildDirGroup = new Group(mainComposite, SWT.NONE);
        buildDirGroup.setLayout(new GridLayout(4, false));
        buildDirGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        buildDirGroup.setText(Messages.qtImportPageBuildSettingsText);

        Button defaultBuildDirectoryButton = new Button(buildDirGroup, SWT.CHECK);
        defaultBuildDirectoryButton.setSelection(true);
        defaultBuildDirectoryButton.setText(Messages.qtImportPageSetDefaultBuildDirectory);
        defaultBuildDirectoryButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 4, 1));

        Label buildDirLabel = new Label(buildDirGroup, SWT.NONE);
        buildDirLabel.setText(Messages.qtImportPageBuildLabelText);

        buildDirText = new Text(buildDirGroup, SWT.BORDER);
        buildDirText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
        buildDirText.setEnabled(false);
        buildDirText.setText("build");
        buildDirText.addModifyListener(event -> validatePage());

        defaultBuildDirectoryButton.addListener(SWT.Selection, event -> {
            if (defaultBuildDirectoryButton.getSelection()) {
                buildDirText.setEnabled(false);
                buildDirText.setText("build");
            } else {
                buildDirText.setEnabled(true);
                buildDirText.setText("");
            }
        });

        TabItem qmakeTab = new TabItem(tabFolder, SWT.NONE);
        qmakeTab.setText(Messages.qtImportSecondTab);

        Composite qmakeComposite = new Composite(tabFolder, SWT.NONE);
        qmakeComposite.setLayout(new GridLayout(1, false));
        qmakeTab.setControl(qmakeComposite);

        Group qmakeMainSettingsGroup = new Group(qmakeComposite, SWT.NONE);
        qmakeMainSettingsGroup.setLayout(new GridLayout(4, false));
        qmakeMainSettingsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        qmakeMainSettingsGroup.setText(Messages.qtImportPageProjectGroupText);

        Label qmakeRootDirLabel = new Label(qmakeMainSettingsGroup, SWT.NONE);
        qmakeRootDirLabel.setText(Messages.qtImportPageProjectProjectRootDirLabel);

        qmakeRootDirectoryText = new Text(qmakeMainSettingsGroup, SWT.BORDER);
        qmakeRootDirectoryText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
        qmakeRootDirectoryText.addModifyListener(event -> {
            qmakeRootFile = Paths.get(qmakeRootDirectoryText.getText()).toFile();
            validatePage();
        });

        Button qmakeRootDirBrowse = new Button(qmakeMainSettingsGroup, SWT.FLAT);
        qmakeRootDirBrowse.setText(Messages.qtImportPageBrowseButtonText);
        qmakeRootDirBrowse.addListener(SWT.Selection, event -> {
            DirectoryDialog directoryDialog = new DirectoryDialog(getShell(), SWT.SHEET);
            String filepath = directoryDialog.open();
            if (filepath != null && !filepath.isBlank()) {
                qmakeRootDirectoryText.setText(filepath);
                String projectName = new File(filepath).getName();
                qmakeProjectNameText.setText(createUniqueName(projectName));
                
                File dir = new File(filepath);
                File[] proFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pro"));
                
                if (proFiles != null && proFiles.length == 1) {
                    qmakeProFilePathText.setText(proFiles[0].getAbsolutePath());
                } else {
                    FileDialog fileDialog = new FileDialog(getShell(), SWT.OPEN);
                    fileDialog.setFilterPath(filepath);
                    fileDialog.setFilterExtensions(new String[] { "pro", "*.pro", "Pro", "*.Pro", "PRO", "*.PRO" });
                    fileDialog.setText("Select Qt .pro File");
                    String selectedFile = fileDialog.open();
                    if (selectedFile != null) {
                        qmakeProFilePathText.setText(selectedFile);
                    } else {
                        qmakeProFilePathText.setText("");
                    }
                }
            }
        });

        Label qmakeProjectNameLabel = new Label(qmakeMainSettingsGroup, SWT.NONE);
        qmakeProjectNameLabel.setText(Messages.qtImportPageProjectNameLabel);

        qmakeProjectNameText = new Text(qmakeMainSettingsGroup, SWT.BORDER);
        qmakeProjectNameText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 3, 1));
        qmakeProjectNameText.addModifyListener(event -> validatePage());

        Group qmakeBuildDirGroup = new Group(qmakeComposite, SWT.NONE);
        qmakeBuildDirGroup.setLayout(new GridLayout(4, false));
        qmakeBuildDirGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        qmakeBuildDirGroup.setText(Messages.qtImportPageBuildSettingsText);

        Button qmakeDefaultBuildDirectoryButton = new Button(qmakeBuildDirGroup, SWT.CHECK);
        qmakeDefaultBuildDirectoryButton.setSelection(true);
        qmakeDefaultBuildDirectoryButton.setText(Messages.qtImportPageSetDefaultBuildDirectory);
        qmakeDefaultBuildDirectoryButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 4, 1));

        Label qmakeBuildDirLabel = new Label(qmakeBuildDirGroup, SWT.NONE);
        qmakeBuildDirLabel.setText(Messages.qtImportPageBuildLabelText);

        qmakeBuildDirectoryText = new Text(qmakeBuildDirGroup, SWT.BORDER);
        qmakeBuildDirectoryText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
        qmakeBuildDirectoryText.setEnabled(false);
        qmakeBuildDirectoryText.setText("build");
        qmakeBuildDirectoryText.addModifyListener(event -> validatePage());

        qmakeDefaultBuildDirectoryButton.addListener(SWT.Selection, event -> {
            boolean enabled = !qmakeDefaultBuildDirectoryButton.getSelection();
            qmakeBuildDirectoryText.setEnabled(enabled);
            qmakeBuildDirectoryText.setText(enabled ? "" : "build");
        });

        Group qmakeProSettingsGroup = new Group(qmakeComposite, SWT.NONE);
        qmakeProSettingsGroup.setLayout(new GridLayout(4, false));
        qmakeProSettingsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        qmakeProSettingsGroup.setText(Messages.qtImportExtra);

        Label qmakeProFileLabel = new Label(qmakeProSettingsGroup, SWT.NONE);
        qmakeProFileLabel.setText(Messages.qtImportProjectPro);

        qmakeProFilePathText = new Text(qmakeProSettingsGroup, SWT.BORDER);
        qmakeProFilePathText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
        qmakeProFilePathText.addModifyListener(event -> validatePage());

        Button qmakeBrowseButton = new Button(qmakeProSettingsGroup, SWT.PUSH);
        qmakeBrowseButton.setText(Messages.qtImportPageBrowseButtonText);
        qmakeBrowseButton.addListener(SWT.Selection, event -> {
            FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
            dialog.setFilterExtensions(new String[] { "*.pro" });
            String selectedFile = dialog.open();
            if (selectedFile != null) {
                qmakeProFilePathText.setText(selectedFile);
            }
        });
    }

    /**
     * Returns the project.
     */
    public IProject getProject() {
        return project;
    }

    /**
     * Returns the root file for CMake import.
     */
    public File getRootFile() {
        return rootFile;
    }

    /**
     * Returns the build folder for CMake import.
     */
    public IFolder getBuildFolder() {
        return project.getFolder(buildDirText.getText());
    }

    /**
     * Returns the QMake root directory.
     */
    public String getQmakeRootDirectory() {
        return qmakeRootDirectoryText.getText();
    }

    /**
     * Returns the QMake project name.
     */
    public String getQmakeProjectName() {
        return qmakeProjectNameText.getText();
    }

    /**
     * Returns the QMake root file.
     */
    public File getQmakeRootFile() {
        return qmakeRootFile;
    }

    /**
     * Returns the QMake .pro file path.
     */
    public String getQmakeProFilePath() {
        return qmakeProFilePathText.getText();
    }

    /**
     * Returns the QMake build folder.
     */
    public IFolder getQmakeBuildFolder() {
        return getProject().getFolder(qmakeBuildDirectoryText.getText());
    }

    /**
     * Validates the page input.
     */
    private void validatePage() {
        boolean cmakeValid = false;
        boolean qmakeValid = false;

        if (!rootDirText.getText().isBlank()) {
            if (!validateRootDirectory()) {
                return;
            }
            if (!validateProjectName()) {
                return;
            }
            if (!validateCMakeLists()) {
                return;
            }
            if (!validateBuildDirectory()) {
                return;
            }
            cmakeValid = true;
        }

        if (!qmakeRootDirectoryText.getText().isBlank()) {
            if (!validateQmakeRootDirectory()) {
                return;
            }
            if (!validateQmakeProjectName()) {
                return;
            }
            if (!validateQmakeBuildDirectory()) {
                return;
            }
            if (!qmakeProFilePathText.getText().isBlank() && !new File(qmakeProFilePathText.getText()).exists()) {
                setErrorMessage("Ошибка: указанный .pro файл не существует");
                setPageComplete(false);
                return;
            }
            qmakeValid = true;
        }

        if (cmakeValid || qmakeValid) {
            setErrorMessage(null);
            setMessage(null);
            setPageComplete(true);
            project = ResourcesPlugin.getWorkspace().getRoot().getProject(
                cmakeValid ? projectNameText.getText() : qmakeProjectNameText.getText());
        } else {
            setErrorMessage(Messages.qtImportPageErrorNoProFile);
            setPageComplete(false);
        }
    }

    /**
     * Validates the CMake build directory.
     */
    private boolean validateBuildDirectory() {
        if (buildDirText.getText().isBlank()) {
            setErrorMessage(Messages.qtImportPageErrorBlankBuildDirectory);
            setPageComplete(false);
            return false;
        }
        return true;
    }

    /**
     * Validates the CMake root directory.
     */
    private boolean validateRootDirectory() {
        if (rootFile == null || !rootFile.exists()) {
            setErrorMessage(Messages.qtImportPageErrorDirectoryDoesNotExist);
            setPageComplete(false);
            return false;
        }
        return true;
    }

    /**
     * Validates the presence of CMakeLists.txt.
     */
    private boolean validateCMakeLists() {
        if (rootFile != null && !Paths.get(rootFile.getPath().toString(), "CMakeLists.txt").toFile().exists()) {
            setErrorMessage(Messages.qtImportPageErrorNoCMakeLists);
            setPageComplete(false);
            return false;
        }
        return true;
    }

    /**
     * Validates the CMake project name.
     */
    private boolean validateProjectName() {
        if (projectNameText.getText().isBlank()) {
            setErrorMessage(Messages.qtImportPageErrorBlankProjectName);
            setPageComplete(false);
            return false;
        }
        if (ResourcesPlugin.getWorkspace().getRoot().getProject(projectNameText.getText()).exists()) {
            setErrorMessage(Messages.qtImportPageErrorProjectAlreadyExists);
            setPageComplete(false);
            return false;
        }
        return true;
    }

    /**
     * Validates the QMake root directory.
     */
    private boolean validateQmakeRootDirectory() {
        if (qmakeRootFile == null || !qmakeRootFile.exists()) {
            setErrorMessage(Messages.qtImportPageErrorDirectoryDoesNotExist);
            setPageComplete(false);
            return false;
        }
        return true;
    }

    /**
     * Validates the QMake project name.
     */
    private boolean validateQmakeProjectName() {
        if (qmakeProjectNameText.getText().isBlank()) {
            setErrorMessage(Messages.qtImportPageErrorBlankProjectName);
            setPageComplete(false);
            return false;
        }
        if (ResourcesPlugin.getWorkspace().getRoot().getProject(qmakeProjectNameText.getText()).exists()) {
            setErrorMessage(Messages.qtImportPageErrorProjectAlreadyExists);
            setPageComplete(false);
            return false;
        }
        return true;
    }

    /**
     * Validates the QMake build directory.
     */
    private boolean validateQmakeBuildDirectory() {
        if (qmakeBuildDirectoryText.getText().isBlank()) {
            setErrorMessage(Messages.qtImportPageErrorBlankBuildDirectory);
            setPageComplete(false);
            return false;
        }
        return true;
    }

    /**
     * Generates a unique project name.
     */
    private String createUniqueName(String oldName) {
        String newName = oldName;
        int newNumber = 1;
        while (ResourcesPlugin.getWorkspace().getRoot().getProject(newName).exists()) {
            newName = oldName + "_" + newNumber;
            ++newNumber;
        }
        return newName;
    }
}