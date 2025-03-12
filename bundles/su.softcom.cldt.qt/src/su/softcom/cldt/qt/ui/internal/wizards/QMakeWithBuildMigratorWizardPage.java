package su.softcom.cldt.qt.ui.internal.wizards;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import su.softcom.cldt.qt.common.Messages;

import java.io.File;
import java.nio.file.Paths;

public class QMakeWithBuildMigratorWizardPage extends WizardPage {
	private File rootFile;
	private Text proFilePathText;
	private Text rootDirectoryText;
	private Text projectNameText;
	private Text buildDirectoryText;

	public QMakeWithBuildMigratorWizardPage() {
		super(Messages.qtImportPageTitleName);
		setTitle(Messages.qtImportPageTitleName);
		setDescription(Messages.qtImportPageDescription);
		setPageComplete(false);
	}

	@Override
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		setControl(container);

		Group mainSettingsGroup = new Group(container, SWT.NONE);
		mainSettingsGroup.setLayout(new GridLayout(4, false));
		mainSettingsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		mainSettingsGroup.setText(Messages.qtImportPageProjectGroupText);

		Label rootDirLabel = new Label(mainSettingsGroup, SWT.NONE);
		rootDirLabel.setText(Messages.qtImportPageProjectProjectRootDirLabel);
		rootDirectoryText = new Text(mainSettingsGroup, SWT.BORDER);
		rootDirectoryText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
		rootDirectoryText.addModifyListener(event -> {
			rootFile = Paths.get(rootDirectoryText.getText()).toFile();
			validateRootDir();
		});

		Button rootDirBrowse = new Button(mainSettingsGroup, SWT.FLAT);
		rootDirBrowse.setText(Messages.qtImportPageBrowseButtonText);
		rootDirBrowse.addListener(SWT.Selection, event -> {
			DirectoryDialog directoryDialog = new DirectoryDialog(getShell(), SWT.SHEET);
			String filepath = directoryDialog.open();
			if (filepath != null && !filepath.isBlank()) {
				rootDirectoryText.setText(filepath);
				String projectName = new File(filepath).getName();
				projectNameText.setText(createUniqueName(projectName));

				File projectFile = new File(filepath, "Project.pro");
				proFilePathText.setText(projectFile.exists() ? projectFile.getAbsolutePath() : "");
			}
		});

		Label projectNameLabel = new Label(mainSettingsGroup, SWT.NONE);
		projectNameLabel.setText(Messages.qtImportPageProjectNameLabel);
		projectNameText = new Text(mainSettingsGroup, SWT.BORDER);
		projectNameText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 3, 1));
		projectNameText.addModifyListener(event -> validate());

		Group buildDirGroup = new Group(container, SWT.NONE);
		buildDirGroup.setLayout(new GridLayout(4, false));
		buildDirGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		buildDirGroup.setText(Messages.qtImportPageBuildSettingsText);

		Button defaultBuildDirectoryButton = new Button(buildDirGroup, SWT.CHECK);
		defaultBuildDirectoryButton.setSelection(true);
		defaultBuildDirectoryButton.setText(Messages.qtImportPageSetDefaultBuildDirectory);
		defaultBuildDirectoryButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 4, 1));

		Label buildDirLabel = new Label(buildDirGroup, SWT.NONE);
		buildDirLabel.setText(Messages.qtImportPageBuildLabelText);
		buildDirectoryText = new Text(buildDirGroup, SWT.BORDER);
		buildDirectoryText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
		buildDirectoryText.setEnabled(false);
		buildDirectoryText.setText("build");
		buildDirectoryText.addModifyListener(event -> validate());

		defaultBuildDirectoryButton.addListener(SWT.Selection, event -> {
			boolean enabled = !defaultBuildDirectoryButton.getSelection();
			buildDirectoryText.setEnabled(enabled);
			buildDirectoryText.setText(enabled ? "" : "build");
		});

		Group proSettingsGroup = new Group(container, SWT.NONE);
		proSettingsGroup.setLayout(new GridLayout(4, false));
		proSettingsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		proSettingsGroup.setText(Messages.qtImportExtra);

		Composite proFileContainer = new Composite(proSettingsGroup, SWT.NONE);
		proFileContainer.setLayout(new GridLayout(2, false));
		proFileContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Label lblProFile = new Label(proFileContainer, SWT.NONE);
		lblProFile.setText(Messages.qtImportProjectPro);
		proFilePathText = new Text(proFileContainer, SWT.BORDER);
		proFilePathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Button browseButton = new Button(proSettingsGroup, SWT.PUSH);
		browseButton.setText(Messages.qtImportPageBrowseButtonText);
		browseButton.addListener(SWT.Selection, e -> {
			FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
			dialog.setFilterExtensions(new String[] { "*.pro" });
			String selectedFile = dialog.open();
			if (selectedFile != null) {
				proFilePathText.setText(selectedFile);
			}
		});

		setPageComplete(false);
	}

	/**
	 * Returns the selected root directory path.
	 *
	 * @return The root directory path.
	 */
	public String getRootDirectory() {
		return rootDirectoryText.getText();
	}

	/**
	 * Returns the project name entered by the user.
	 *
	 * @return The project name.
	 */
	public String getProjectName() {
		return projectNameText.getText();
	}

	/**
	 * Returns the project object for the entered project name.
	 *
	 * @return The IProject object.
	 */
	public IProject getProject() {
		return ResourcesPlugin.getWorkspace().getRoot().getProject(projectNameText.getText());
	}

	/**
	 * Returns the root directory file.
	 *
	 * @return The root directory file.
	 */
	public File getRootFile() {
		return Paths.get(rootDirectoryText.getText()).toFile();
	}

	/**
	 * Returns the path of the .pro file.
	 *
	 * @return The .pro file path.
	 */
	public String getProFilePath() {
		return proFilePathText.getText();
	}

	/**
	 * Returns the build folder for the project.
	 *
	 * @return The IFolder object representing the build folder.
	 */
	public IFolder getBuildFolder() {
		IProject project = getProject();
		return project.getFolder("build");
	}

	/**
	 * Creates a unique project name by appending a number if the project with the
	 * given name already exists.
	 *
	 * @param oldName The original project name.
	 * @return A unique project name.
	 */
	private String createUniqueName(String oldName) {
		String newName = oldName;
		int newNumber = 1;
		while (ResourcesPlugin.getWorkspace().getRoot().getProject(newName).exists()) {
			newName = oldName + "_" + newNumber;
			newNumber += 1;
		}
		return newName;
	}

	/**
	 * Validates the root directory selected by the user.
	 *
	 * @return True if the root directory exists, otherwise false.
	 */
	private boolean validateRootDir() {
		if (rootFile == null || !rootFile.exists()) {
			setErrorMessage(Messages.qtImportPageErrorDirectoryDoesNotExist);
			setPageComplete(false);
			return false;
		} else {
			setErrorMessage(null);
			setPageComplete(true);
			return true;
		}
	}

	/**
	 * Validates the build path changed by the user.
	 *
	 * @return True if the build path is not blank, otherwise false.
	 */
	private boolean validateBuildDirectory() {
		if (buildDirectoryText.getText().isBlank()) {
			setErrorMessage(Messages.qtImportPageErrorBlankBuildDirectory);
			setPageComplete(false);
			return false;
		} else {
			setErrorMessage(null);
			setPageComplete(true);
			return true;
		}
	}

	/**
	 * Validates the project name entered by the user.
	 *
	 * @return True if the project name is not empty and the project already exists, otherwise false.
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
		setErrorMessage(null);
		setPageComplete(true);
		return true;
	}

	/**
	 * Performs general validation on the user inputs and updates the page status
	 * accordingly.
	 */
	private void validate() {
		setMessage(null);
		validateRootDir();
		validateBuildDirectory();
		validateProjectName();
	}
}
