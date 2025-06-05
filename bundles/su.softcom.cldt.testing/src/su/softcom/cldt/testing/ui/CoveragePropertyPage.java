package su.softcom.cldt.testing.ui;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PropertyPage;
import su.softcom.cldt.testing.core.Activator;
import su.softcom.cldt.testing.core.CoverageProjectSettings;

public class CoveragePropertyPage extends PropertyPage {
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final String WARNING_READ_DIR = "Failed to read coverage data directory in UI, using default: %s";
	private static final String WARNING_READ_EXCLUDES = "Failed to read project excludes in UI, using empty string";
	private static final String ERROR_SAVE_PROPERTIES = "Failed to save project properties: %s";
	private Text coverageDataDirText;
	private Text projectExcludesText;
	private IProject project;

	public CoveragePropertyPage() {
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite mainComposite = new Composite(parent, SWT.NONE);
		mainComposite.setLayout(new GridLayout(1, false));

		project = ((IResource) getElement()).getProject();
		createProfileDataGroup(mainComposite);
		createCoverageScopeGroup(mainComposite);

		return mainComposite;
	}

	private void createProfileDataGroup(Composite parent) {
		Group profileGroup = createGroup(parent, "Profile Data", 3);
		String coverageDataDir = initializeCoverageDataDir();
		coverageDataDirText = createLabeledText(profileGroup, "Coverage Data Directory:", coverageDataDir);
		coverageDataDirText.setToolTipText("Relative path to directory for coverage data (e.g., build, custom_build)");
		createBrowseButton(profileGroup, "Select Coverage Data Directory");
	}

	private String initializeCoverageDataDir() {
		String coverageDataDir = CoverageProjectSettings.getDefaultCoverageDataDir(project);
		try {
			String storedValue = CoverageProjectSettings.getCoverageDataDirProperty(project);
			if (!storedValue.isEmpty()) {
				return CoverageProjectSettings.toRelativePath(project, storedValue);
			}
		} catch (CoreException e) {
			Activator.getDefault().getLog()
					.log(new Status(IStatus.WARNING, PLUGIN_ID, String.format(WARNING_READ_DIR, coverageDataDir), e));
		}
		return coverageDataDir;
	}

	private void createCoverageScopeGroup(Composite parent) {
		Group scopeGroup = createGroup(parent, "Coverage Scope", 2);
		String projectExcludes = initializeProjectExcludes();
		projectExcludesText = createLabeledText(scopeGroup, "Project-Specific Excludes:", projectExcludes);
		projectExcludesText
				.setToolTipText("Semicolon-separated patterns to exclude from coverage (e.g., experiments/*;temp/*)");
	}

	private String initializeProjectExcludes() {
		try {
			return CoverageProjectSettings.getProjectExcludes(project);
		} catch (CoreException e) {
			Activator.getDefault().getLog().log(new Status(IStatus.WARNING, PLUGIN_ID, WARNING_READ_EXCLUDES, e));
			return "";
		}
	}

	private Group createGroup(Composite parent, String text, int columns) {
		Group group = new Group(parent, SWT.NONE);
		group.setLayout(new GridLayout(columns, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		group.setText(text);
		return group;
	}

	private Text createLabeledText(Group parent, String labelText, String initialValue) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		Text textField = new Text(parent, SWT.BORDER);
		textField.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		textField.setText(initialValue);
		return textField;
	}

	private void createBrowseButton(Group parent, String dialogTitle) {
		Button browseButton = new Button(parent, SWT.PUSH);
		browseButton.setText("Browse...");
		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dialog = new DirectoryDialog(parent.getShell());
				dialog.setText(dialogTitle);
				dialog.setFilterPath(project.getLocation().toOSString());
				String absolutePath = dialog.open();
				if (absolutePath != null) {
					String relativePath = CoverageProjectSettings.toRelativePath(project, absolutePath);
					coverageDataDirText.setText(relativePath);
				}
			}
		});
	}

	@Override
	protected void performDefaults() {
		coverageDataDirText.setText(CoverageProjectSettings.getDefaultCoverageDataDir(project));
		projectExcludesText.setText("");
		super.performDefaults();
	}

	@Override
	public boolean performOk() {
		String coverageDataDir = coverageDataDirText.getText();
		String projectExcludes = projectExcludesText.getText();

		try {
			String errorMessage = CoverageProjectSettings.validateAndSaveSettings(project, coverageDataDir,
					projectExcludes);
			if (errorMessage != null) {
				setErrorMessage(errorMessage);
				return false;
			}
		} catch (CoreException e) {
			Activator.getDefault().getLog()
					.log(new Status(IStatus.ERROR, PLUGIN_ID, "Failed to save project properties in UI", e));
			setErrorMessage(String.format(ERROR_SAVE_PROPERTIES, e.getMessage()));
			return false;
		}

		setErrorMessage(null);
		return true;
	}
}