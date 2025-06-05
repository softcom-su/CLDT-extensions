package su.softcom.cldt.testing.ui;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import su.softcom.cldt.testing.core.Activator;
import su.softcom.cldt.testing.core.CoverageSettingsManager;

public class CoveragePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	private static final String OPEN_VIEW_AUTO = "coverage.open_view_auto";
	private static final String GENERATE_REPORT = "coverage.generate_report";
	private static final String CLEAN_PROFILE_DATA = "coverage.clean_profile_data";
	private static final String INCLUDES = "coverage.includes";
	private static final String EXCLUDES = "coverage.excludes";
	private static final String LLVM_COV_PATH = "coverage.llvm_cov_path";
	private static final String ERROR_NO_STORE = "Error: Preference store is not available. Please check plugin initialization.";
	private static final String ERROR_NOT_INITIALIZED = "Plugin is not initialized. Preference store is unavailable.";

	private Button openViewAutoCheck;
	private Button generateReportCheck;
	private Button cleanProfileDataCheck;
	private Text includesText;
	private Text excludesText;
	private Text llvmCovPathText;

	public CoveragePreferencePage() {
	}

	@Override
	public void init(IWorkbench workbench) {
		Activator activator = Activator.getDefault();
		if (activator != null) {
			setPreferenceStore(activator.getPreferenceStore());
		} else {
			setErrorMessage(ERROR_NOT_INITIALIZED);
		}
	}

	@Override
	protected Control createContents(Composite parent) {
		if (getPreferenceStore() == null) {
			Label errorLabel = new Label(parent, SWT.NONE);
			errorLabel.setText(ERROR_NO_STORE);
			return errorLabel;
		}

		Composite mainComposite = new Composite(parent, SWT.NONE);
		mainComposite.setLayout(new GridLayout(1, false));

		createSessionManagementGroup(mainComposite);
		createCoverageScopeGroup(mainComposite);
		createGeneralGroup(mainComposite);

		return mainComposite;
	}

	private void createSessionManagementGroup(Composite parent) {
		Group sessionGroup = createGroup(parent, "Session Management", 2);
		IPreferenceStore store = getPreferenceStore();

		openViewAutoCheck = createCheckbox(sessionGroup, "Open Coverage View Automatically",
				store.getBoolean(OPEN_VIEW_AUTO));
		generateReportCheck = createCheckbox(sessionGroup, "Generate Report After Build",
				store.getBoolean(GENERATE_REPORT));
		cleanProfileDataCheck = createCheckbox(sessionGroup, "Clean Old Profile Data",
				store.getBoolean(CLEAN_PROFILE_DATA));
	}

	private void createCoverageScopeGroup(Composite parent) {
		Group scopeGroup = createGroup(parent, "Coverage Scope", 2);
		IPreferenceStore store = getPreferenceStore();

		includesText = createLabeledText(scopeGroup, "Includes:", store.getString(INCLUDES));
		excludesText = createLabeledText(scopeGroup, "Excludes:", store.getString(EXCLUDES));
	}

	private void createGeneralGroup(Composite parent) {
		Group generalGroup = createGroup(parent, "General", 3);
		IPreferenceStore store = getPreferenceStore();

		llvmCovPathText = createLabeledText(generalGroup, "LLVM-cov Path:", store.getString(LLVM_COV_PATH));
		createBrowseButton(generalGroup, llvmCovPathText, "Select llvm-cov executable");
	}

	private Group createGroup(Composite parent, String text, int columns) {
		Group group = new Group(parent, SWT.NONE);
		group.setLayout(new GridLayout(columns, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		group.setText(text);
		return group;
	}

	private Button createCheckbox(Group parent, String text, boolean selection) {
		Button checkbox = new Button(parent, SWT.CHECK);
		checkbox.setSelection(selection);
		checkbox.setText(text);
		checkbox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
		return checkbox;
	}

	private Text createLabeledText(Group parent, String labelText, String initialValue) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		Text textField = new Text(parent, SWT.BORDER);
		textField.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		textField.setText(initialValue);
		return textField;
	}

	private void createBrowseButton(Group parent, Text targetText, String dialogTitle) {
		Button browseButton = new Button(parent, SWT.PUSH);
		browseButton.setText("Browse...");
		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog dialog = new FileDialog(parent.getShell(), SWT.OPEN);
				dialog.setText(dialogTitle);
				String path = dialog.open();
				if (path != null) {
					targetText.setText(path);
				}
			}
		});
	}

	@Override
	protected void performDefaults() {
		IPreferenceStore store = getPreferenceStore();
		openViewAutoCheck.setSelection(store.getDefaultBoolean(OPEN_VIEW_AUTO));
		generateReportCheck.setSelection(store.getDefaultBoolean(GENERATE_REPORT));
		cleanProfileDataCheck.setSelection(store.getDefaultBoolean(CLEAN_PROFILE_DATA));
		includesText.setText(store.getDefaultString(INCLUDES));
		excludesText.setText(store.getDefaultString(EXCLUDES));
		llvmCovPathText.setText(store.getDefaultString(LLVM_COV_PATH));
		super.performDefaults();
	}

	@Override
	protected void performApply() {
		savePreferences();
		super.performApply();
	}

	@Override
	public boolean performOk() {
		savePreferences();
		return super.performOk();
	}

	private void savePreferences() {
		IPreferenceStore store = getPreferenceStore();
		store.setValue(OPEN_VIEW_AUTO, openViewAutoCheck.getSelection());
		store.setValue(GENERATE_REPORT, generateReportCheck.getSelection());
		store.setValue(CLEAN_PROFILE_DATA, cleanProfileDataCheck.getSelection());
		store.setValue(INCLUDES, includesText.getText());
		store.setValue(EXCLUDES, excludesText.getText());
		store.setValue(LLVM_COV_PATH, llvmCovPathText.getText());
		CoverageSettingsManager.notifySettingsChanged();
	}
}