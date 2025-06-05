package su.softcom.cldt.testing.ui;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
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
import su.softcom.cldt.testing.core.CoveragePropertySettings;

public class CoveragePropertyPage extends PropertyPage {
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final ILog LOGGER = Platform.getLog(CoveragePropertyPage.class);

	private Text coverageDataDirText;
	private IProject project;

	public CoveragePropertyPage() {
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite mainComposite = new Composite(parent, SWT.NONE);
		mainComposite.setLayout(new GridLayout(1, false));

		project = ((IResource) getElement()).getProject();
		createProfileDataGroup(mainComposite);

		return mainComposite;
	}

	private void createProfileDataGroup(Composite parent) {
		Group profileGroup = createGroup(parent, Messages.CoveragePropertyPage_4, 3);
		String coverageDataDir = initializeCoverageDataDir();
		coverageDataDirText = createLabeledText(profileGroup, Messages.CoveragePropertyPage_5, coverageDataDir);
		coverageDataDirText.setToolTipText(Messages.CoveragePropertyPage_6);
		createBrowseButton(profileGroup, Messages.CoveragePropertyPage_7);
	}

	private String initializeCoverageDataDir() {
		String coverageDataDir = CoveragePropertySettings.getDefaultCoverageDataDir(project);
		try {
			String storedValue = CoveragePropertySettings.getCoverageDataDirProperty(project);
			if (!storedValue.isEmpty()) {
				return CoveragePropertySettings.toRelativePath(project, storedValue);
			}
		} catch (CoreException e) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					String.format("Failed to read coverage data directory in UI, using default: %s", coverageDataDir),
					e));
		}
		return coverageDataDir;
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
		browseButton.setText(Messages.CoveragePropertyPage_12);
		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dialog = new DirectoryDialog(parent.getShell());
				dialog.setText(dialogTitle);
				dialog.setFilterPath(project.getLocation().toOSString());
				String absolutePath = dialog.open();
				if (absolutePath != null) {
					String relativePath = CoveragePropertySettings.toRelativePath(project, absolutePath);
					coverageDataDirText.setText(relativePath);
				}
			}
		});
	}

	@Override
	protected void performDefaults() {
		coverageDataDirText.setText(CoveragePropertySettings.getDefaultCoverageDataDir(project));
		super.performDefaults();
	}

	@Override
	public boolean performOk() {
		String coverageDataDir = coverageDataDirText.getText();

		try {
			String errorMessage = CoveragePropertySettings.validateAndSaveSettings(project, coverageDataDir);
			if (errorMessage != null) {
				setErrorMessage(errorMessage);
				return false;
			}
		} catch (CoreException e) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID,
					String.format("Failed to save project properties: %s", e.getMessage()), e));
			setErrorMessage("Failed to save project properties in UI");
			return false;
		}

		setErrorMessage(null);
		return true;
	}
}